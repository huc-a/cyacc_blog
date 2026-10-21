package cn.caiya;

import cn.caiya.common.*;
import cn.caiya.content.*;
import cn.caiya.media.*;
import cn.caiya.site.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.io.*;
import java.time.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @WithMockUser(username="admin",roles="ADMIN")
class ContentAndMediaIntegrationTest extends DatabaseTestSupport {
    @Autowired ContentService contents;@Autowired ContentMapper contentMapper;@Autowired DetailMapper detailMapper;
    @Autowired MediaService media;@Autowired MediaMapper mediaMapper;@Autowired TaxonomyMapper taxonomy;
    @Autowired SiteService site;@Autowired ObjectMapper json;@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;
    String slug() {return "test-"+UUID.randomUUID();}
    long n(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
    ContentDetails details(Map<String,Object> values) {return json.convertValue(values,ContentDetails.class);}
    ContentDetails completeDetails() {
        return details(Map.of("websiteUrl","https://example.com","sourceUrl","https://example.com/source","demoUrl","https://example.com/demo",
            "benefitText","免费资源","requirementsMarkdown","仅供个人学习","techStack",List.of("Java","Vue"),
            "chapters",List.of(Map.of("chapterKey",UUID.randomUUID(),"slug","start","title","第一章","bodyMarkdown","章节正文"))));
    }
    ContentInput input(String type,String slug,String title,long version,ContentDetails d) {
        return new ContentInput(type,slug,title,"摘要 "+title,"# 正文 "+title,version,null,null,List.of(),null,List.of(),d);
    }
    byte[] png() throws IOException {
        var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
    }
    long upload() throws IOException {return n(media.upload(new MockMultipartFile("file","test.png","image/png",png())),"id");}

    @ParameterizedTest @ValueSource(strings={"work","tutorial","website","deal"})
    void everyTypePreservesItsPublishedSnapshot(String type) {
        String slug=slug();var a=contents.create(input(type,slug,"旧标题",0,completeDetails()));long id=n(a,"id");
        var p=contents.publish(id,0);var old=contents.publicDetail(slug);
        var changed=contents.save(id,input(type,slug,"新标题",n(p,"lockVersion"),completeDetails()));
        assertThat(contents.publicDetail(slug)).isEqualTo(old);
        contents.publish(id,n(changed,"lockVersion"));assertThat(contents.publicDetail(slug).get("title")).isEqualTo("新标题");
        assertThat(contentMapper.revision(n(p,"revisionId")).get("revision_state")).isEqualTo("frozen");
    }

    @Test void chapterKeysAndOrderAreVersionedAndFrozen() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();String slug=slug();
        var chapters=List.of(Map.of("chapterKey",first,"slug","one","title","一","bodyMarkdown","正文一"),Map.of("chapterKey",second,"slug","two","title","二","bodyMarkdown","正文二"));
        var a=contents.create(input("tutorial",slug,"教程",0,details(Map.of("chapters",chapters))));long id=n(a,"id");var p=contents.publish(id,0);
        var reordered=List.of(chapters.get(1),chapters.get(0));var changed=contents.save(id,input("tutorial",slug,"教程二",n(p,"lockVersion"),details(Map.of("chapters",reordered))));
        assertThat(detailMapper.chapters(n(p,"revisionId")).get(0).get("chapter_key")).isEqualTo(first);
        assertThat(detailMapper.chapters(n(changed,"revisionId")).get(0).get("chapter_key")).isEqualTo(second);
        assertThatThrownBy(()->jdbc.update("UPDATE tutorial_chapter SET title='changed' WHERE tutorial_revision_id=?",n(p,"revisionId"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->contents.save(id,input("tutorial",slug,"重复章节",n(changed,"lockVersion"),details(Map.of("chapters",List.of(chapters.get(0),chapters.get(0))))))).isInstanceOf(ApiException.class);
        var incomplete=contents.create(input("tutorial",slug(),"空教程",0,ContentDetails.defaults()));
        assertThatThrownBy(()->contents.publish(n(incomplete,"id"),0)).isInstanceOf(ApiException.class);
    }

    @Test void categoryTagsCoverAndMediaStayIsolated() throws Exception {
        long cover=upload();String tagName="hidden-"+slug();var tag=new HashMap<String,Object>(Map.of("name",tagName,"slug",slug()));taxonomy.createTag(tag);
        var cat=new HashMap<String,Object>(Map.of("name","分类-"+slug(),"slug",slug(),"type","article","sortOrder",0));taxonomy.createCategory(cat);
        String slug=slug();var a=contents.create(input("article",slug,"旧标题",0,ContentDetails.defaults()));long id=n(a,"id");var p=contents.publish(id,0);
        var next=new ContentInput("article",slug,"新标题","新摘要","![图](/api/public/media/"+cover+")",n(p,"lockVersion"),null,n(cat,"id"),List.of(n(tag,"id")),cover,List.of(),null);
        var changed=contents.save(id,next);
        assertThat(contents.publicDetail(slug).get("coverAssetId")).isNull();
        assertThat(contents.list(true,"","all",tagName,1,10).get("total")).isEqualTo(0L);
        assertThat(mediaMapper.isPublic(cover)).isFalse();
        assertThatThrownBy(()->media.delete(cover)).isInstanceOf(ApiException.class);
        contents.publish(id,n(changed,"lockVersion"));
        assertThat(contents.list(true,"","all",tagName,1,10).get("total")).isEqualTo(1L);
        assertThat(contents.list(true,"article","all","",1,10,n(cat,"id"),n(tag,"id"),null).get("total")).isEqualTo(1L);
        assertThat(mediaMapper.isPublic(cover)).isTrue();
        mvc.perform(get("/api/public/media/"+cover)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        assertThatThrownBy(()->taxonomy.deleteTag(n(tag,"id"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM revision_tag WHERE revision_id=?",n(changed,"revisionId"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void uploadsValidateContentAndReferenceDeletionAndSettingsConflict() throws Exception {
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file","bad.svg","image/svg+xml","<svg onload='x'/>".getBytes())).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file","fake.png","image/png","not a png".getBytes())).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file","wrong.jpg","image/jpeg",png())).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/admin/media").file(new MockMultipartFile("file","big.png","image/png",new byte[5*1024*1024+1])).with(csrf())).andExpect(status().isBadRequest());
        long asset=upload();var settings=site.read();
        @SuppressWarnings("unchecked") var changed=json.convertValue(settings.get("settings"),Map.class);changed.put("backgroundAssetId",asset);
        var saved=site.save(json.convertValue(changed,SiteSettings.class),n(settings,"lockVersion"));
        assertThatThrownBy(()->site.save(json.convertValue(changed,SiteSettings.class),n(settings,"lockVersion"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->media.delete(asset)).isInstanceOf(ApiException.class);
        assertThat(mediaMapper.isPublic(asset)).isTrue();
        changed.put("backgroundAssetId",null);site.save(json.convertValue(changed,SiteSettings.class),n(saved,"lockVersion"));
        var row=mediaMapper.find(asset);media.delete(asset);assertThat(java.nio.file.Files.exists(media.path(row))).isFalse();
        changed.put("script","alert(1)");
        mvc.perform(put("/api/admin/site").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("settings",changed,"lockVersion",n(saved,"lockVersion")+1)))).andExpect(status().isBadRequest());
    }

    @Test void unsafeLinksDatesAndIncompletePublicationAreRejected() {
        assertThatThrownBy(()->contents.create(input("website",slug(),"网站",0,details(Map.of("websiteUrl","javascript:alert(1)"))))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->contents.create(input("deal",slug(),"活动",0,details(Map.of("startsAt","2026-10-21T00:00:00Z","endsAt","2026-10-20T00:00:00Z"))))).isInstanceOf(ApiException.class);
        for(String type:List.of("website","deal")) {var draft=contents.create(input(type,slug(),"未完整",0,ContentDetails.defaults()));assertThatThrownBy(()->contents.publish(n(draft,"id"),0)).isInstanceOf(ApiException.class);}
    }

    @Test void dealValidityPrioritizesStartAndExpiryOverManualVerification() {
        var service=new DetailService(null);var d=new HashMap<String,Object>();d.put("verification",Map.of("result","valid"));
        d.put("startsAt",Instant.now().plusSeconds(3600).toString());assertThat(service.validity(d)).isEqualTo("upcoming");
        d.remove("startsAt");d.put("endsAt",Instant.now().minusSeconds(60).toString());assertThat(service.validity(d)).isEqualTo("expired");
        d.remove("endsAt");assertThat(service.validity(d)).isEqualTo("valid");
        d.put("verification",Map.of("result","invalid"));assertThat(service.validity(d)).isEqualTo("invalid");
    }

    @Test void verificationIsBoundToPublishedRevisionAndRespectsDates() throws Exception {
        String slug=slug();var a=contents.create(input("deal",slug,"活动",0,completeDetails()));long id=n(a,"id");var p=contents.publish(id,0);
        mvc.perform(post("/api/admin/contents/"+id+"/verify").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("revisionId",n(p,"revisionId"),"result","valid","evidenceUrl","https://example.com","note","已人工确认")))).andExpect(status().isOk());
        assertThat(((Map<?,?>)contents.publicDetail(slug).get("details")).get("validity")).isEqualTo("valid");
        var changed=contents.save(id,input("deal",slug,"新活动",n(p,"lockVersion"),completeDetails()));contents.publish(id,n(changed,"lockVersion"));
        assertThat(((Map<?,?>)contents.publicDetail(slug).get("details")).get("validity")).isEqualTo("unknown");
        mvc.perform(post("/api/admin/contents/"+id+"/verify").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("revisionId",n(p,"revisionId"),"result","valid","evidenceUrl","","note","")))).andExpect(status().isConflict());
    }
}
