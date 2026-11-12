package cn.caiya.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.caiya.DatabaseTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.login.captcha-always-required=true")
@AutoConfigureMockMvc
class LoginCaptchaRequiredIntegrationTest extends DatabaseTestSupport {
  @Autowired MockMvc mvc;
  @Autowired AuthMapper users;
  @Autowired PasswordEncoder encoder;
  private final String password = "Captcha-required-test-password-42";

  private String account() {
    String username = "captcha-required-" + UUID.randomUUID();
    users.create(username, encoder.encode(password));
    return username;
  }

  private MockHttpSession challenge() {
    var session = new MockHttpSession();
    session.setAttribute(
        CaptchaService.ATTRIBUTE,
        new CaptchaService.Challenge(
            CaptchaService.digest("AB234"), Instant.now().plusSeconds(120)));
    return session;
  }

  @Test
  void missingOrIncorrectCaptchaIsRejectedBeforeValidCredentialsCanAuthenticate() throws Exception {
    String username = account();
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("username", username)
                .param("password", password))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.captchaRequired").value(true));
    mvc.perform(
            post("/api/auth/login")
                .session(challenge())
                .with(csrf())
                .param("username", username)
                .param("password", password)
                .param("captcha", "WRONG"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.captchaRequired").value(true));
    mvc.perform(
            post("/api/auth/login")
                .session(challenge())
                .with(csrf())
                .param("username", username)
                .param("password", password)
                .param("captcha", "AB234"))
        .andExpect(status().isOk());
  }
}
