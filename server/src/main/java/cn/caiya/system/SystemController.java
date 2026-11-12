package cn.caiya.system;

import java.util.*;
import cn.caiya.common.*;
import cn.caiya.content.ContentService;
import org.springframework.web.bind.annotation.*;

@RestController
public class SystemController {
    private final SystemMapper mapper;private final AuditMapper logs;private final ContentService contents;
    public SystemController(SystemMapper mapper,AuditMapper logs,ContentService contents) {this.mapper=mapper;this.logs=logs;this.contents=contents;}
    public record PublicSummary(Counts counts,List<Day> calendar) {
        public record Counts(long contents,long tags) {}
        public record Day(String date) {}
    }
    @GetMapping("/api/public/summary") public PublicSummary summary() {
        var counts=mapper.publicCounts();
        var calendar=mapper.calendar().stream().map(row->{
            Object value=row.get("date");
            String date=value instanceof java.sql.Date d?d.toLocalDate().toString():String.valueOf(value);
            return new PublicSummary.Day(date);
        }).toList();
        return new PublicSummary(new PublicSummary.Counts(
            ((Number) counts.get("contents")).longValue(),((Number) counts.get("tags")).longValue()),calendar);
    }
    @GetMapping("/api/admin/dashboard") public Map<String,Object> dashboard() {
        return Map.of("counts",mapper.counts().stream().map(Views::camel).toList(),"drafts",contents.list(new cn.caiya.content.ContentQuery(false,"","draft","",null,null,null,false,1,5)),"activity",logs.list(8,0).stream().map(Views::camel).toList());
    }
    @GetMapping("/api/admin/logs") public Map<String,Object> logs(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        if(page<1||page>100000||size<1||size>50) throw new ApiException(400,"分页参数无效");
        return Map.of("items",logs.list(size,(page-1)*size).stream().map(Views::camel).toList(),"total",mapper.logCount(),"page",page,"size",size);
    }
}
