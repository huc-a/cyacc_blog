package cn.caiya;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.caiya.auth.AuthMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.login.captcha-always-required=false")
@AutoConfigureMockMvc
class AccountIntegrationTest extends DatabaseTestSupport {
  @Autowired AuthMapper mapper;
  @Autowired PasswordEncoder encoder;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  MockHttpSession login(String username, String password) throws Exception {
    var response =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("username", username)
                    .param("password", password))
            .andExpect(status().isOk())
            .andReturn();
    return (MockHttpSession) response.getRequest().getSession();
  }

  @Test
  void passwordChangeRequiresCurrentSecretAndRevokesOtherSessions() throws Exception {
    String username = "test-" + UUID.randomUUID(),
        old = "Only-for-this-test-42",
        next = "Changed-test-secret-43";
    mapper.create(username, encoder.encode(old));
    var first = login(username, old);
    var second = login(username, old);
    mvc.perform(
            post("/api/admin/account/password")
                .session(first)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", "wrong", "newPassword", next, "lockVersion", 0))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/admin/account/password")
                .session(first)
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", old, "newPassword", next, "lockVersion", 0))))
        .andExpect(status().isOk());
    assertThat(first.isInvalid()).isTrue();
    mvc.perform(get("/api/admin/contents").session(second)).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login").with(csrf()).param("username", username).param("password", old))
        .andExpect(status().isUnauthorized());
    var fresh = login(username, next);
    mvc.perform(get("/api/auth/me").session(fresh))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockVersion").value(1));
    assertThat(mapper.find(username).get("password_hash")).isNotEqualTo(next);
  }
}
