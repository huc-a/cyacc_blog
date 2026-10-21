package cn.caiya.analytics;

import java.time.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) {this.service=service;}
    @GetMapping("/overview") public Map<String,Object> overview(@RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end) {
        var to=end==null?LocalDate.now(ZoneId.of("Asia/Shanghai")):end;
        return service.overview(start==null?to.minusDays(29):start,to);
    }
}
