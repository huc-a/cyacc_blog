package cn.caiya.analytics;

import java.util.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.io.IOException;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import cn.caiya.common.ApiException;

@Service
public class AnalyticsService {
    private final String base,website,token;private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public AnalyticsService(@Value("${app.umami-url}") String base,@Value("${app.umami-website-id}") String website,@Value("${app.umami-token}") String token,ObjectMapper json) {
        this.base=base.replaceAll("/+$","");this.website=website;this.token=token;this.json=json;
    }
    public Map<String,Object> overview(LocalDate start,LocalDate end) {
        if(start.isAfter(end)||java.time.temporal.ChronoUnit.DAYS.between(start,end)>365) throw new ApiException(400,"日期范围须为 1 至 366 天");
        if(base.isBlank()||website.isBlank()||token.isBlank()) return Map.of("status","unconfigured","message","尚未连接 Umami，请在部署环境配置统计服务。","start",start,"end",end);
        try {
            UUID.fromString(website);
            long from=start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            long to=end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()-1;
            String range="?startAt="+from+"&endAt="+to;
            var result=new LinkedHashMap<String,Object>();result.put("status","available");result.put("start",start);result.put("end",end);
            result.put("overview",get("stats"+range));result.put("trend",get("pageviews"+range+"&unit=day&timezone=Asia%2FShanghai"));
            for(String metric:List.of("referrer","device","browser","url")) result.put(metric,get("metrics"+range+"&type="+metric));
            result.put("hourly",get("pageviews"+range+"&unit=hour&timezone=Asia%2FShanghai"));
            return result;
        } catch(InterruptedException e) {Thread.currentThread().interrupt();return unavailable(start,end);}
        catch(IOException|IllegalArgumentException e) {return unavailable(start,end);}
    }
    private Map<String,Object> unavailable(LocalDate start,LocalDate end) {return Map.of("status","unavailable","message","统计服务暂时不可用，请检查连接和凭据后重试。","start",start,"end",end);}
    private JsonNode get(String path) throws IOException,InterruptedException {
        var request=HttpRequest.newBuilder(URI.create(base+"/api/websites/"+website+"/"+path)).header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(5)).GET().build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            if(response.statusCode()!=200) throw new IOException("Analytics upstream unavailable");
            byte[] bytes=body.readNBytes(2_000_001);if(bytes.length>2_000_000) throw new IOException("Analytics response too large");
            var node=json.readTree(bytes);if(node==null||(!node.isObject()&&!node.isArray())) throw new IOException("Invalid analytics response");return node;
        }
    }
}
