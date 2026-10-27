package cn.caiya;

import cn.caiya.auth.*;
import cn.caiya.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AdminAccountsIntegrationTest extends DatabaseTestSupport {
    @Autowired AuthMapper mapper;@Autowired AdminAccountService service;@Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired SessionRegistry sessions;
    final String password="Account-test-secret-42";
    String name() {return "account-"+UUID.randomUUID();}
    String add() {String name=name();mapper.create(name,encoder.encode(password));return name;}
    long id(String username) {return Views.number(mapper.find(username),"id");}
    MockHttpSession login(String username,String secret) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf())
            .with(r->{r.setRemoteAddr("test-"+UUID.randomUUID());return r;}).param("username",username).param("password",secret))
            .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }
    @Test void everyNewAccountHasSameAuthorityAndResponsesNeverExposeSecrets() throws Exception {
        String operator=add(),created=name();var session=login(operator,password);
        mvc.perform(post("/api/admin/accounts").session(session).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",created,"displayName","第二位管理员","password",password))))
            .andExpect(status().isCreated());
        var second=login(created,password);
        var response=mvc.perform(get("/api/admin/accounts").session(second).param("size","50"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("password", "securityVersion", "$2a$").contains("第二位管理员");
        mvc.perform(get("/api/admin/site").session(second)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/accounts").session(second).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username",name(),"displayName","同等权限","password",password))))
            .andExpect(status().isCreated());
        assertThat(encoder.matches(password,mapper.find(created).get("password_hash").toString())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_log WHERE action='account.create' AND target_id=?",Integer.class,id(created))).isEqualTo(1);
    }
    @Test void anonymousCsrfValidationDuplicateAndSelfChangesAreRejected() throws Exception {
        String operator=add();var session=login(operator,password);
        String body=json.writeValueAsString(Map.of("username",operator,"displayName","duplicate","password",password));
        mvc.perform(get("/api/admin/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/accounts").session(session).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/accounts").session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/accounts").session(session).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("username","bad name","displayName","test","password",password)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/accounts").session(session).param("size","999")).andExpect(status().isBadRequest());
        assertThatThrownBy(()->service.enabled(operator,id(operator),false,0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.resetPassword(operator,id(operator),password,0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.create(operator,name(),"test","密".repeat(25))).isInstanceOf(ApiException.class);
    }
    @Test void disablingAndResettingInvalidateSessionsEvenWithoutRegistryEntry() throws Exception {
        String operator=add(),target=add();var oldSession=login(target,password);
        sessions.removeSessionInformation(oldSession.getId());
        service.enabled(operator,id(target),false,0);
        mvc.perform(get("/api/admin/accounts").session(oldSession)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username",target).param("password",password)).andExpect(status().isUnauthorized());
        service.enabled(operator,id(target),true,1);
        var beforeReset=login(target,password);sessions.removeSessionInformation(beforeReset.getId());
        String next="Reset-test-password-43";service.resetPassword(operator,id(target),next,2);
        mvc.perform(get("/api/admin/accounts").session(beforeReset)).andExpect(status().isUnauthorized());
        login(target,next);
        assertThat(Views.number(mapper.find(target),"security_version")).isEqualTo(3);
    }
    @Test void staleVersionCannotUndoNewStateOrPassword() {
        String operator=add(),target=add();service.enabled(operator,id(target),false,0);
        assertThatThrownBy(()->service.enabled(operator,id(target),true,0)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.resetPassword(operator,id(target),"New-test-password-42",0)).isInstanceOf(ApiException.class);
        assertThat(mapper.find(target).get("enabled")).isEqualTo(false);
        assertThat(encoder.matches(password,mapper.find(target).get("password_hash").toString())).isTrue();
    }
    @Test void concurrentMutualSuspensionLeavesAnEnabledOperator() throws Exception {
        String first=add(),second=add();long firstId=id(first),secondId=id(second);
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await();try {service.enabled(first,secondId,false,0);return "ok";}catch(ApiException e){return "denied";}});
            var b=pool.submit(()->{barrier.await();try {service.enabled(second,firstId,false,0);return "ok";}catch(ApiException e){return "denied";}});
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("ok","denied");
            assertThat(List.of(mapper.find(first).get("enabled"),mapper.find(second).get("enabled"))).containsExactlyInAnyOrder(true,false);
        } finally {pool.shutdownNow();}
    }
}
