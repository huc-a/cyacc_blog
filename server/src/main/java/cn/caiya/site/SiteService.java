package cn.caiya.site;

import java.util.*;
import cn.caiya.common.*;
import cn.caiya.media.MediaMapper;
import cn.caiya.system.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteService {
    private final SiteMapper mapper;private final MediaMapper media;private final AuditService audit;private final ObjectMapper json;
    public SiteService(SiteMapper mapper,MediaMapper media,AuditService audit,ObjectMapper json) {this.mapper=mapper;this.media=media;this.audit=audit;this.json=json;}
    public Map<String,Object> read() {
        var row=mapper.read();try{return Map.of("settings",json.readValue(row.get("settings").toString(),SiteSettings.class),"lockVersion",row.get("lock_version"));}
        catch(JsonProcessingException e) {throw new IllegalStateException("Invalid site settings schema",e);}
    }
    @Transactional public Map<String,Object> save(SiteSettings settings,long version) {
        SafeUrls.check(settings.github());
        if(new HashSet<>(settings.homeModules()).size()!=settings.homeModules().size()) throw new ApiException(400,"首页栏目不能重复");
        if(settings.backgroundAssetId()!=null && media.lock(settings.backgroundAssetId())==null) throw new ApiException(400,"背景图片不存在");
        if(settings.avatarAssetId()!=null && media.lock(settings.avatarAssetId())==null) throw new ApiException(400,"头像图片不存在");
        try {if(mapper.save(json.writeValueAsString(settings),version)!=1) throw ApiException.conflict();}
        catch(JsonProcessingException e) {throw new ApiException(400,"配置格式无效");}
        audit.record("site.update","site",0);return read();
    }
}
