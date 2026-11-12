package cn.caiya.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.caiya.DatabaseTestSupport;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;

@SpringBootTest(properties = "app.login.captcha-always-required=false")
@AutoConfigureMockMvc
class LoginProtectionIntegrationTest extends DatabaseTestSupport {
  @Autowired MockMvc mvc;
  @Autowired AuthMapper users;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;
  @Autowired LoginProtection protection;
  @Autowired CaptchaService captcha;
  final String secret = "Guard-test-password-42";

  String unique() {
    return "guard-" + UUID.randomUUID();
  }

  String account() {
    String name = unique();
    users.create(name, encoder.encode(secret));
    return name;
  }

  MockHttpSession challenge() {
    var session = new MockHttpSession();
    session.setAttribute(
        CaptchaService.ATTRIBUTE,
        new CaptchaService.Challenge(
            CaptchaService.digest("AB234"), Instant.now().plusSeconds(120)));
    return session;
  }

  ResultActions attempt(
      String name, String password, String ip, MockHttpSession session, String answer)
      throws Exception {
    var request =
        post("/api/auth/login")
            .session(session)
            .with(csrf())
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                })
            .param("username", name)
            .param("password", password);
    if (answer != null) request.param("captcha", answer);
    return mvc.perform(request);
  }

  @Test
  void anIncorrectSubmittedCaptchaCannotAuthenticateBeforeRiskThreshold() throws Exception {
    String name = account();
    attempt(name, secret, unique(), challenge(), "WRONG")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.captchaRequired").value(true));
    attempt(name, secret, unique(), challenge(), "AB234").andExpect(status().isOk());
    assertThat(protection.account(name)).isNull();
  }

  @Test
  void threeFailuresRequireSessionBoundCaptchaEvenFromANewAddress() throws Exception {
    String name = account(), ip = unique();
    for (int n = 0; n < 3; n++)
      attempt(name, "wrong", ip, new MockHttpSession(), null)
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.captchaRequired").value(n == 2));
    attempt(name, secret, unique(), new MockHttpSession(), "").andExpect(status().isBadRequest());
    var solved = challenge();
    attempt(name, secret, unique(), solved, "WRONG").andExpect(status().isBadRequest());
    attempt(name, secret, unique(), solved, "AB234").andExpect(status().isBadRequest());
    attempt(name, secret, unique(), challenge(), "ab234").andExpect(status().isOk());
    assertThat(protection.account(name)).isNull();
  }

  @Test
  void lockoutPersistsAcrossServiceInstancesAndExpiresWithoutPermanentLocking() throws Exception {
    String name = account();
    for (int n = 0; n < 5; n++)
      attempt(name, "wrong", unique(), challenge(), "AB234")
          .andExpect(n == 4 ? status().isTooManyRequests() : status().isUnauthorized());
    attempt(name, secret, unique(), challenge(), "AB234")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
    var restarted = new LoginProtection(jdbc);
    assertThat(restarted.retryAfter(restarted.account(name))).isBetween(899, 900);
    assertThat(
            new LoginProtection(jdbc, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(16)))
                .retryAfter(restarted.account(name)))
        .isZero();
    jdbc.update(
        "UPDATE login_throttle SET locked_until=?,window_started_at=? WHERE account_key=?",
        Timestamp.from(Instant.now().minusSeconds(1)),
        Timestamp.from(Instant.now().minusSeconds(1000)),
        LoginProtection.key(name));
    attempt(name, secret, unique(), challenge(), "AB234").andExpect(status().isOk());
  }

  @Test
  void unknownAccountsHaveIdenticalFailureCaptchaAndLockoutResponses() throws Exception {
    String known = account(), unknown = unique();
    for (int n = 0; n < 5; n++) {
      var first = attempt(known, "wrong", unique(), challenge(), "AB234").andReturn().getResponse();
      var second =
          attempt(unknown, "wrong", unique(), challenge(), "AB234").andReturn().getResponse();
      assertThat(first.getStatus()).isEqualTo(second.getStatus());
      assertThat(first.getContentAsString()).isEqualTo(second.getContentAsString());
    }
  }

  @Test
  void sourceLimitResistsAccountSprayingAndForwardedHeaderSpoofing() throws Exception {
    String ip = unique();
    for (int n = 0; n < 10; n++)
      attempt(unique(), "wrong", ip, new MockHttpSession(), null)
          .andExpect(n < 3 ? status().isUnauthorized() : status().isBadRequest());
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    })
                .header("X-Forwarded-For", "192.0.2.99")
                .param("username", unique())
                .param("password", secret))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
    attempt(unique(), "wrong", unique(), new MockHttpSession(), null)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void parallelRequestsCannotPassTheFifthCredentialFailure() throws Exception {
    String name = account();
    var pool = Executors.newFixedThreadPool(8);
    var start = new CountDownLatch(1);
    try {
      var results = new ArrayList<Future<Integer>>();
      for (int n = 0; n < 8; n++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return attempt(name, "wrong", unique(), challenge(), "AB234")
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      var statuses = new ArrayList<Integer>();
      for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
      assertThat(statuses.stream().filter(s -> s == 401).count()).isEqualTo(4);
      assertThat(statuses.stream().filter(s -> s == 429).count()).isEqualTo(4);
      assertThat(protection.account(name).failures()).isEqualTo(5);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void captchaImagesDoNotExposeAnswerAndRefreshIsThrottled() throws Exception {
    String ip = unique();
    var session = new MockHttpSession();
    var result =
        mvc.perform(
                get("/api/auth/captcha")
                    .session(session)
                    .with(
                        r -> {
                          r.setRemoteAddr(ip);
                          return r;
                        }))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.answer").doesNotExist())
            .andReturn();
    var payload =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString());
    byte[] png = Base64.getDecoder().decode(payload.get("image").asText().split(",")[1]);
    var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
    assertThat(image.getWidth()).isEqualTo(190);
    assertThat(image.getHeight()).isEqualTo(64);
    Object old = session.getAttribute(CaptchaService.ATTRIBUTE);
    captcha.issue(session);
    assertThat(session.getAttribute(CaptchaService.ATTRIBUTE)).isNotSameAs(old);
    for (int n = 1; n < 30; n++)
      mvc.perform(
              get("/api/auth/captcha")
                  .session(session)
                  .with(
                      r -> {
                        r.setRemoteAddr(ip);
                        return r;
                      }))
          .andExpect(status().isOk());
    mvc.perform(
            get("/api/auth/captcha")
                .session(session)
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    }))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void captchaExpiresIsOneUseAndCannotBeSharedAcrossSessions() {
    var session = challenge();
    assertThat(captcha.consume(new MockHttpSession(), "AB234")).isFalse();
    var later = new CaptchaService(Clock.offset(Clock.systemUTC(), Duration.ofSeconds(121)));
    assertThat(later.consume(session, "AB234")).isFalse();
    assertThat(captcha.consume(session, "AB234")).isFalse();
    session = challenge();
    assertThat(captcha.consume(session, "AB234")).isTrue();
    assertThat(captcha.consume(session, "AB234")).isFalse();
  }
}
