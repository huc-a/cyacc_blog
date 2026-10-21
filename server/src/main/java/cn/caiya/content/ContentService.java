package cn.caiya.content;

import java.util.*;
import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.caiya.common.Views.number;

@Service
public class ContentService {
    private final ContentMapper mapper;
    private final AuditService audit;
    public ContentService(ContentMapper mapper,AuditService audit) {this.mapper=mapper;this.audit=audit;}
    public static void type(String type) {
        if(!Set.of("article","work","tutorial","website","deal").contains(type)) throw new ApiException(400,"内容类型无效");
    }
    public Map<String,Object> list(boolean publicOnly,String type,String status,String q,int page,int size) {
        if(!type.isEmpty()) type(type);
        if(!Set.of("all","draft","published","offline","trash").contains(status) || q.length()>200 || page<1 || page>100000 || size<1 || size>50)
            throw new ApiException(400,"筛选或分页参数无效");
        var query=new HashMap<String,Object>();
        query.put("publicOnly",publicOnly);query.put("type",type);query.put("status",status);query.put("q",q);
        query.put("search","%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        query.put("size",size);query.put("offset",(page-1)*size);
        var rows=mapper.list(query).stream().map(Views::camel).toList();
        if(publicOnly) rows.forEach(this::removePrivateFields);
        return Map.of("items",rows,"total",mapper.count(query),"page",page,"size",size);
    }
    private void removePrivateFields(Map<String,Object> row) {
        for(String key:List.of("lockVersion","revisionState","deletedAt","updatedAt")) row.remove(key);
    }
    public Map<String,Object> publicDetail(String slug) {
        var id=mapper.publicId(slug);if(id==null) throw new ApiException(404,"内容不存在或尚未发布");
        return detail(id,true);
    }
    public Map<String,Object> detail(long id,boolean publicOnly) {
        var c=mapper.identity(id);if(c==null) throw new ApiException(404,"内容不存在");
        if(publicOnly && (c.get("deleted_at")!=null || !"published".equals(c.get("publish_status")))) throw new ApiException(404,"内容不存在或尚未发布");
        Object revisionId=publicOnly?c.get("published_revision_id"):c.getOrDefault("working_revision_id",c.get("published_revision_id"));
        if(revisionId==null) throw new ApiException(404,"内容版本不存在");
        long rid=((Number)revisionId).longValue();var r=mapper.revision(rid);
        var out=Views.camel(c);out.remove("workingRevisionId");out.remove("publishedRevisionId");
        out.put("revisionId",rid);out.put("revisionNo",r.get("revision_no"));out.put("revisionState",r.get("revision_state"));
        out.put("title",r.get("title"));out.put("summary",r.get("summary"));out.put("bodyMarkdown",mapper.body(rid));
        if(publicOnly) removePrivateFields(out);
        return out;
    }
    @Transactional
    public Map<String,Object> create(ContentInput input) {
        type(input.type());
        if(!"article".equals(input.type())) throw new ApiException(400,"此阶段仅开放文章类型");
        var c=new HashMap<String,Object>();c.put("type",input.type());c.put("slug",input.slug());mapper.insert(c);
        long id=number(c,"id");long rid=makeRevision(id,input,1);
        mapper.working(id,rid);audit.record("content.create",input.type(),id);
        return detail(id,false);
    }
    private long makeRevision(long id,ContentInput input,int no) {
        var r=revisionValues(input);r.put("contentId",id);r.put("type",input.type());r.put("revisionNo",no);mapper.insertRevision(r);
        long rid=number(r,"id");mapper.bodySave(rid,input.bodyMarkdown());return rid;
    }
    private Map<String,Object> revisionValues(ContentInput input) {
        var r=new HashMap<String,Object>();r.put("title",input.title().trim());r.put("summary",input.summary());r.put("changeNote",input.changeNote());return r;
    }
    private Map<String,Object> locked(long id,long version,boolean allowTrash) {
        var c=mapper.lock(id);if(c==null) throw new ApiException(404,"内容不存在");
        if(number(c,"lock_version")!=version) throw ApiException.conflict();
        if(!allowTrash && c.get("deleted_at")!=null) throw new ApiException(409,"请先从回收站恢复内容");
        return c;
    }
    @Transactional
    public Map<String,Object> save(long id,ContentInput input) {
        var c=locked(id,input.lockVersion(),false);
        if(!input.type().equals(c.get("content_type")) || !input.slug().equals(c.get("slug"))) throw new ApiException(400,"内容类型和地址创建后不能修改");
        if(c.get("working_revision_id")==null) {
            long rid=makeRevision(id,input,mapper.nextRevision(id));mapper.working(id,rid);
        } else {
            long rid=number(c,"working_revision_id");var r=revisionValues(input);r.put("id",rid);
            if(mapper.updateRevision(r)!=1) throw ApiException.conflict();mapper.bodySave(rid,input.bodyMarkdown());
        }
        if(mapper.touch(id,input.lockVersion())!=1) throw ApiException.conflict();
        audit.record("content.save",input.type(),id);return detail(id,false);
    }
    @Transactional
    public Map<String,Object> publish(long id,long version) {
        var c=locked(id,version,false);Object working=c.get("working_revision_id");
        if(working==null) {
            if(c.get("published_revision_id")==null || "published".equals(c.get("publish_status"))) throw new ApiException(400,"没有待发布的修改");
            mapper.publish(id,number(c,"published_revision_id"));
        } else {
            long rid=((Number)working).longValue();var r=mapper.revision(rid);var body=mapper.body(rid);
            if(r.get("title").toString().isBlank() || body==null || body.isBlank()) throw new ApiException(400,"发布前请填写标题和正文");
            if(mapper.freeze(rid)!=1) throw ApiException.conflict();mapper.publish(id,rid);
        }
        if(mapper.touch(id,version)!=1) throw ApiException.conflict();audit.record("content.publish",(String)c.get("content_type"),id);
        return detail(id,false);
    }
    @Transactional
    public Map<String,Object> transition(long id,long version,String action) {
        var c=locked(id,version,action.equals("restore"));
        switch(action) {
            case "offline" -> {if(c.get("published_revision_id")==null) throw new ApiException(400,"草稿无需下线");mapper.offline(id);}
            case "trash" -> mapper.trash(id);
            case "restore" -> {if(c.get("deleted_at")==null) throw new ApiException(400,"内容不在回收站");mapper.restore(id);}
            default -> throw new ApiException(400,"操作无效");
        }
        if(mapper.touch(id,version)!=1) throw ApiException.conflict();audit.record("content."+action,(String)c.get("content_type"),id);
        return detail(id,false);
    }
    public List<Map<String,Object>> history(long id) {
        if(mapper.identity(id)==null) throw new ApiException(404,"内容不存在");
        return mapper.history(id).stream().map(Views::camel).toList();
    }
}
