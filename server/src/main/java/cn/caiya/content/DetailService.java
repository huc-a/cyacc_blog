package cn.caiya.content;

import java.util.*;
import java.util.regex.Pattern;
import java.time.*;
import cn.caiya.common.*;
import org.springframework.stereotype.Service;

@Service
public class DetailService {
    private final DetailMapper mapper;
    private static final Pattern MEDIA=Pattern.compile("/api/public/media/([0-9]+)(?![0-9])");
    public DetailService(DetailMapper mapper) {this.mapper=mapper;}
    public void validate(ContentInput input) {
        var d=input.details();
        // Categories are an article-only concept (see V8); other content types
        // organise through tags and their own detail fields instead.
        if(input.categoryId()!=null && !"article".equals(input.type())) throw new ApiException(400,"分类仅适用于文章");
        if(input.categoryId()!=null && !mapper.categoryExists(input.categoryId())) throw new ApiException(400,"内容分类不存在");
        for(var url:List.of(d.demoUrl(),d.sourceUrl(),d.claimUrl())) SafeUrls.check(url);
        if(d.startsAt()!=null && d.endsAt()!=null && !d.endsAt().isAfter(d.startsAt())) throw new ApiException(400,"截止时间必须晚于开始时间");
        var keys=new HashSet<UUID>();var slugs=new HashSet<String>();
        for(var c:d.chapters()) if(!keys.add(c.chapterKey())||!slugs.add(c.slug())) throw new ApiException(400,"章节地址和标识不能重复");
        long length=input.bodyMarkdown().length()+d.chapters().stream().mapToLong(c->c.bodyMarkdown().length()).sum();
        if(length>2_000_000) throw new ApiException(400,"正文和章节总长度超过限制");
    }
    public void save(long rid,ContentInput input) {
        validate(input);mapper.metadata(rid,input);mapper.save(rid,input);
        mapper.clearTags(rid);for(long tag:new LinkedHashSet<>(input.tagIds())) mapper.tag(rid,tag);
        mapper.clearMedia(rid);
        if(input.type().equals("tutorial")) {
            mapper.clearChapters(rid);int order=0;
            for(var chapter:input.details().chapters()) mapper.chapter(rid,chapter,order++);
        }
        int order=0;for(long asset:new LinkedHashSet<>(input.galleryAssetIds())) mapper.media(rid,asset,"gallery",order++);
        var bodies=new ArrayList<String>();bodies.add(input.bodyMarkdown());
        bodies.add(input.details().prerequisitesMarkdown());bodies.add(input.details().requirementsMarkdown());
        input.details().chapters().forEach(c->bodies.add(c.bodyMarkdown()));
        var assets=new LinkedHashSet<Long>();
        for(String body:bodies) {var matcher=MEDIA.matcher(body);while(matcher.find()) {try{assets.add(Long.parseLong(matcher.group(1)));}catch(NumberFormatException e){throw new ApiException(400,"媒体编号无效");}}}
        for(long asset:assets) mapper.media(rid,asset,"body",0);
    }
    public Map<String,Object> read(long rid,String type) {
        var raw=mapper.read(rid,type);if(raw==null) throw new ApiException(409,"内容详情缺失");
        var d=Views.camel(raw);d.remove("revisionId");d.remove("contentType");
        Object body=d.remove(type.equals("tutorial")?"introMarkdown":"bodyMarkdown");
        if(type.equals("tutorial")) d.put("chapters",mapper.chapters(rid).stream().map(Views::camel).toList());
        if(type.equals("deal")) {var verification=mapper.verification(rid);d.put("verification",verification==null?null:Views.camel(verification));d.put("validity",validity(d));}
        var tags=mapper.tags(rid);
        var result=new HashMap<String,Object>();result.put("bodyMarkdown",body==null?"":body);result.put("details",d);result.put("tags",tags);result.put("tagIds",tags.stream().map(t->t.get("id")).toList());result.put("galleryAssetIds",mapper.gallery(rid));result.put("categoryId",mapper.categoryId(rid));return result;
    }
    public String validity(Map<String,Object> d) {
        var now=Instant.now();
        if(d.get("startsAt")!=null && Instant.parse(d.get("startsAt").toString()).isAfter(now)) return "upcoming";
        if(d.get("endsAt")!=null && !Instant.parse(d.get("endsAt").toString()).isAfter(now)) return "expired";
        if(d.get("verification") instanceof Map<?,?> v) return switch(v.get("result").toString()) {case "valid"->"valid";case "invalid"->"invalid";default->"unknown";};
        return "unknown";
    }
    public void validatePublish(long rid,String type,String title) {
        var result=read(rid,type);
        if(title.isBlank() || result.get("bodyMarkdown").toString().isBlank()) throw new ApiException(400,"发布前请填写标题和正文");
        @SuppressWarnings("unchecked") var d=(Map<String,Object>)result.get("details");
        if(type.equals("deal") && (d.get("sourceUrl").toString().isBlank() || d.get("benefitText").toString().isBlank() || d.get("requirementsMarkdown").toString().isBlank()))
            throw new ApiException(400,"请填写活动来源、优惠内容和适用条件");
        if(type.equals("tutorial")) {
            var chapters=mapper.chapters(rid);
            if(chapters.isEmpty() || chapters.stream().anyMatch(c->c.get("title").toString().isBlank()||c.get("body_markdown").toString().isBlank())) throw new ApiException(400,"教程至少需要一章，且每章标题和正文完整");
        }
    }
}
