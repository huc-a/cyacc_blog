package cn.caiya;

import cn.caiya.common.ApiException;
import cn.caiya.content.*;
import cn.caiya.system.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CoreIntegrationTest extends DatabaseTestSupport {
    @Autowired ContentService service;
    @Autowired ContentMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tx;
    @Autowired cn.caiya.media.MediaService media;
    @Autowired cn.caiya.media.MediaMapper mediaMapper;
    @MockitoSpyBean AuditService audit;
    String slug() {return "test-"+UUID.randomUUID();}
    ContentInput input(String slug,String title,String body,long version) {return new ContentInput("article",slug,title,"摘要 "+title,body,version,null);}
    long n(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
    Map<String,Object> create(String slug) {return service.create(input(slug,"原始标题","# 原始正文",0));}
    @AfterEach void resetSpy() {reset(audit);}

    @Test void migrationsCreateConstraintsOnRealPostgres() {
        assertThat(jdbc.queryForObject("SELECT version()",String.class)).contains("PostgreSQL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version='1'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname=current_user",Boolean.class)).isFalse();
    }

    @Test @WithMockUser(username="admin",roles="ADMIN")
    void draftIsolationAndPublishSwitchAndHistory() throws Exception {
        String slug=slug();var draft=create(slug);long id=n(draft,"id");
        mvc.perform(get("/api/public/contents/"+slug)).andExpect(status().isNotFound());
        assertThat(service.list(true,"article","all",slug,1,10).get("total")).isEqualTo(0L);
        var published=service.publish(id,0);long oldRevision=n(published,"revisionId");
        var changed=service.save(id,input(slug,"独特新标题-"+slug,"新正文",n(published,"lockVersion")));
        assertThat(service.publicDetail(slug).get("title")).isEqualTo("原始标题");
        assertThat(service.publicDetail(slug).get("bodyMarkdown")).isEqualTo("# 原始正文");
        assertThat(service.list(true,"article","all","独特新标题-"+slug,1,10).get("total")).isEqualTo(0L);
        assertThat(n(changed,"revisionId")).isNotEqualTo(oldRevision);
        assertThat(service.history(id)).hasSize(2);
        service.publish(id,n(changed,"lockVersion"));
        assertThat(service.publicDetail(slug).get("title")).isEqualTo("独特新标题-"+slug);
        assertThat(service.publicDetail(slug)).doesNotContainKeys("lockVersion","workingRevisionId","deletedAt");
        assertThat(mapper.body(oldRevision)).isEqualTo("# 原始正文");
    }

    @Test void invalidPublishAndFailureRollbackAreAtomic() {
        String slug=slug();var empty=service.create(input(slug,"","",0));long id=n(empty,"id");
        assertThatThrownBy(()->service.publish(id,0)).isInstanceOf(ApiException.class);
        var ready=service.save(id,input(slug,"待发布","完整正文",0));
        doThrow(new IllegalStateException("simulated audit storage failure")).when(audit).record("content.publish","article",id);
        assertThatThrownBy(()->service.publish(id,n(ready,"lockVersion"))).isInstanceOf(IllegalStateException.class);
        assertThat(mapper.revision(n(ready,"revisionId")).get("revision_state")).isEqualTo("editing");
        assertThat(mapper.identity(id).get("publish_status")).isEqualTo("draft");
        assertThat(mapper.identity(id).get("published_revision_id")).isNull();
        assertThat(service.detail(id,false).get("lockVersion")).isEqualTo(ready.get("lockVersion"));
    }

    @Test void frozenRowsAndCrossContentPointersAreRejected() {
        var a=create(slug());var b=create(slug());long id=n(a,"id"),rid=n(a,"revisionId");service.publish(id,0);
        assertThatThrownBy(()->jdbc.update("UPDATE article_detail SET body_markdown='tampered' WHERE revision_id=?",rid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE content_revision SET title='tampered' WHERE id=?",rid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var template=new TransactionTemplate(tx);
        assertThatThrownBy(()->template.execute(s->{jdbc.update("UPDATE content SET published_revision_id=? WHERE id=?",n(b,"revisionId"),id);return null;})).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(n(mapper.identity(id),"published_revision_id")).isEqualTo(rid);
        assertThatThrownBy(()->jdbc.update("INSERT INTO content_revision(content_id,content_type,revision_no) VALUES(?,'article',2)",n(b,"id"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO content_revision(content_id,content_type,revision_no) VALUES(?,'work',2)",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void offlineTrashRestoreNeverExposeUnpublishedContent() throws Exception {
        String slug=slug();var a=create(slug);long id=n(a,"id");var p=service.publish(id,0);
        var off=service.transition(id,n(p,"lockVersion"),"offline");
        mvc.perform(get("/api/public/contents/"+slug)).andExpect(status().isNotFound());
        p=service.publish(id,n(off,"lockVersion"));
        var trash=service.transition(id,n(p,"lockVersion"),"trash");
        assertThat(mapper.identity(id)).containsEntry("slug",slug);
        assertThatThrownBy(()->create(slug)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(get("/api/public/contents/"+slug)).andExpect(status().isNotFound());
        var restored=service.transition(id,n(trash,"lockVersion"),"restore");
        assertThat(restored.get("publishStatus")).isEqualTo("offline");
        mvc.perform(get("/api/public/contents/"+slug)).andExpect(status().isNotFound());
        service.publish(id,n(restored,"lockVersion"));
        mvc.perform(get("/api/public/contents/"+slug)).andExpect(status().isOk());
    }

    @Test void concurrentSavesCannotOverwriteEachOther() throws Exception {
        String slug=slug();var a=create(slug);long id=n(a,"id");
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            List<Future<String>> results=new ArrayList<>();
            for(String title:List.of("tab-a","tab-b")) results.add(pool.submit(()->{barrier.await();try{service.save(id,input(slug,title,title,0));return "saved";}catch(ApiException e){return "conflict-"+e.status;}}));
            assertThat(List.of(results.get(0).get(10,TimeUnit.SECONDS),results.get(1).get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","conflict-409");
            assertThat(service.detail(id,false).get("lockVersion")).isEqualTo(1L);
        } finally {pool.shutdownNow();}
    }

    @Test void authorizationAndCsrfAreSeparateRequirements() throws Exception {
        mvc.perform(get("/api/admin/contents")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/contents").with(csrf()).contentType("application/json").content(json.writeValueAsString(input(slug(),"标题","正文",0)))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/contents").with(user("admin").roles("ADMIN")).contentType("application/json").content(json.writeValueAsString(input(slug(),"标题","正文",0)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/contents").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(input(slug(),"标题","正文",0)))).andExpect(status().isOk());
        mvc.perform(get("/api/public/contents").param("size","10000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/contents").param("q","' OR 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void realLoginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
        var initial=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)initial.getRequest().getSession();String oldId=session.getId();
        var token=json.readTree(initial.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN",token).param("username","admin").param("password","Test-only-password-42"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
        assertThat(session.getId()).isNotEqualTo(oldId);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var fresh=mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        token=json.readTree(fresh.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN",token)).andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/admin/contents").cookie(new jakarta.servlet.http.Cookie("JSESSIONID",session.getId()))).andExpect(status().isUnauthorized());
    }

    @Test void failedLoginsAreRateLimitedWithGenericErrors() throws Exception {
        for(int i=0;i<10;i++) {
            mvc.perform(post("/api/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.7");return r;})
                .param("username","unknown-admin").param("password","wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("账号或密码错误"));
        }
        mvc.perform(post("/api/auth/login").with(csrf()).with(r->{r.setRemoteAddr("192.0.2.7");return r;})
            .param("username","unknown-admin").param("password","wrong"))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
    }

    @Test void failedMediaAuditRollsBackFileAndDatabaseTogether() throws Exception {
        var buffer=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",buffer);
        long before=mediaMapper.count();
        doThrow(new IllegalStateException("simulated audit failure")).when(audit).record(eq("media.upload"),eq("media"),anyLong());
        assertThatThrownBy(()->media.upload(new org.springframework.mock.web.MockMultipartFile("file","rollback.png","image/png",buffer.toByteArray()))).isInstanceOf(IllegalStateException.class);
        assertThat(mediaMapper.count()).isEqualTo(before);
        var root=java.nio.file.Path.of("..",".local","test-uploads",SCHEMA);
        try(var files=java.nio.file.Files.list(root)) {assertThat(files.count()).isEqualTo(before);}
    }
}
