package cn.caiya;

import cn.caiya.auth.AuthMapper;
import cn.caiya.media.MediaService;
import cn.caiya.site.SiteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @WithMockUser(username="admin",roles="ADMIN") @Transactional
class AdminAppearanceIntegrationTest extends DatabaseTestSupport {
    @Autowired SiteService site; @Autowired MediaService media; @Autowired AuthMapper accounts;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    long upload() throws Exception {return ((Number)media.upload(new MockMultipartFile("file","custom.gif","image/gif",GifFixtures.animation(2))).get("id")).longValue();}
    long version(Map<String,Object> value) {return ((Number)value.get("lockVersion")).longValue();}
    String body(Object value) throws Exception {return json.writeValueAsString(value);}

    @Test void policeRecordAndLoginWallpaperPreservePublicMediaRulesAndSettingsLocks() throws Exception {
        long id=upload();var original=site.read();
        var values=json.convertValue(original.get("settings"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        mvc.perform(get("/api/public/media/"+id)).andExpect(status().isNotFound());
        values.put("policeNumber","桂公网安备45010302001234号");values.put("loginBackgroundAssetId",id);
        var request=Map.of("settings",values,"lockVersion",original.get("lockVersion"));
        mvc.perform(put("/api/admin/site").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(request))).andExpect(status().isOk());
        mvc.perform(get("/api/public/site")).andExpect(jsonPath("$.policeNumber").value(values.get("policeNumber"))).andExpect(jsonPath("$.loginBackgroundAssetId").value(id));
        mvc.perform(get("/api/public/media/"+id)).andExpect(status().isOk()).andExpect(content().contentType("image/gif"));
        mvc.perform(delete("/api/admin/media/"+id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/site").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(request))).andExpect(status().isConflict());
        for(Object invalid:List.of(-1L,Long.MAX_VALUE)) {
            values.put("loginBackgroundAssetId",invalid);
            mvc.perform(put("/api/admin/site").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(Map.of("settings",values,"lockVersion",version(site.read()))))).andExpect(status().isBadRequest());
        }
        values.put("loginBackgroundAssetId",null);values.put("policeNumber","not-a-record");
        mvc.perform(put("/api/admin/site").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(Map.of("settings",values,"lockVersion",version(site.read()))))).andExpect(status().isBadRequest());
        values.put("policeNumber","");
        mvc.perform(put("/api/admin/site").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(Map.of("settings",values,"lockVersion",version(site.read()))))).andExpect(status().isOk());
        mvc.perform(get("/api/public/media/"+id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/media/"+id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/media/"+id+"/file")).andExpect(status().isNotFound());
    }

    @Test void administratorAvatarIsPrivateReferencedAndProtectedByOptimisticLock() throws Exception {
        long id=upload(), version=((Number)accounts.find("admin").get("lock_version")).longValue();
        var request=new HashMap<String,Object>(Map.of("displayName","头像测试","avatarAssetId",id,"lockVersion",version));
        mvc.perform(put("/api/admin/account/profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(request))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.avatarAssetId").value(id)).andExpect(jsonPath("$.displayName").value("头像测试")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/media/"+id+"/file")).andExpect(status().isOk());
        mvc.perform(get("/api/public/media/"+id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/media/"+id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isConflict());
        mvc.perform(put("/api/admin/account/profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(request))).andExpect(status().isConflict());
        request.put("lockVersion",version+1);
        for(long invalid:List.of(-1L,Long.MAX_VALUE)) {
            request.put("avatarAssetId",invalid);
            mvc.perform(put("/api/admin/account/profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(request))).andExpect(status().isBadRequest());
        }
        request.put("avatarAssetId",null);
        mvc.perform(put("/api/admin/account/profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json").content(body(request))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(jsonPath("$.avatarAssetId").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(delete("/api/admin/media/"+id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/media/"+id+"/file")).andExpect(status().isNotFound());
    }
}
