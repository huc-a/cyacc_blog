package cn.caiya.analytics;

import cn.caiya.common.ApiException;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
  private final String base, website, token;
  private final ObjectMapper json;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public AnalyticsService(
      @Value("${app.umami-url}") String base,
      @Value("${app.umami-api-url:}") String apiBase,
      @Value("${app.umami-website-id}") String website,
      @Value("${app.umami-token}") String token,
      ObjectMapper json) {
    this.base =
        apiBase.isBlank()
            ? (base.isBlank() ? "" : base.replaceAll("/+$", "") + "/api")
            : apiBase.replaceAll("/+$", "");
    this.website = website;
    this.token = token;
    this.json = json;
  }

  public Map<String, Object> overview(LocalDate start, LocalDate end) {
    if (start.isAfter(end) || java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365)
      throw new ApiException(400, "日期范围须为 1 至 366 天");
    if (base.isBlank() || website.isBlank() || token.isBlank())
      return Map.of(
          "status",
          "unconfigured",
          "message",
          !base.isBlank() && !website.isBlank() && token.isBlank()
              ? "尚未配置 Umami API 密钥，暂时无法读取统计。访问采集可独立运行。"
              : "尚未连接 Umami，请在部署环境配置统计服务。",
          "start",
          start,
          "end",
          end);
    try {
      UUID.fromString(website);
      long from = start.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
      long to =
          end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() - 1;
      String range = "?startAt=" + from + "&endAt=" + to;
      var result = new LinkedHashMap<String, Object>();
      result.put("status", "available");
      result.put("start", start);
      result.put("end", end);
      var stats = get("stats" + range + "&compare=prev");
      boolean modern = stats.path("pageviews").isNumber();
      result.put(
          "metricUnits",
          Map.of(
              "url",
              modern ? "visitors" : "pageviews",
              "sessions",
              modern ? "visitors" : "sessions"));
      result.put("overview", normalizeStats(stats));
      result.put("trend", series(get("pageviews" + range + "&unit=day&timezone=Asia%2FShanghai")));
      for (String metric : List.of("referrer", "device", "browser", "url")) {
        String dimension = modern && metric.equals("url") ? "path" : metric;
        var rows = get("metrics" + range + "&type=" + dimension);
        if (!rows.isArray()) throw new IOException("Invalid analytics metrics");
        result.put(metric, rows);
      }
      result.put(
          "hourly", series(get("pageviews" + range + "&unit=hour&timezone=Asia%2FShanghai")));
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return unavailable(start, end);
    } catch (IOException | IllegalArgumentException e) {
      return unavailable(start, end);
    }
  }

  private Map<String, Object> unavailable(LocalDate start, LocalDate end) {
    return Map.of(
        "status", "unavailable", "message", "统计服务暂时不可用，请检查连接和凭据后重试。", "start", start, "end", end);
  }

  // Keep the blog API stable across Umami v2 and v3 (including Cloud).
  private JsonNode normalizeStats(JsonNode source) throws IOException {
    var result = json.createObjectNode();
    for (String key : List.of("pageviews", "visitors", "visits")) {
      var metric = source.path(key);
      var value = metric.isNumber() ? metric : metric.path("value");
      if (!value.isNumber()) throw new IOException("Invalid analytics summary");
      var normalized = result.putObject(key);
      normalized.set("value", value);
      var previous = metric.isNumber() ? source.path("comparison").path(key) : metric.path("prev");
      if (previous.isNumber()) normalized.set("prev", previous);
    }
    return result;
  }

  private JsonNode series(JsonNode value) throws IOException {
    if (!value.path("pageviews").isArray() || !value.path("sessions").isArray())
      throw new IOException("Invalid analytics series");
    return value;
  }

  private JsonNode get(String path) throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create(base + "/websites/" + website + "/" + path))
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (var body = response.body()) {
      if (response.statusCode() != 200) throw new IOException("Analytics upstream unavailable");
      byte[] bytes = body.readNBytes(2_000_001);
      if (bytes.length > 2_000_000) throw new IOException("Analytics response too large");
      var node = json.readTree(bytes);
      if (node == null || (!node.isObject() && !node.isArray()))
        throw new IOException("Invalid analytics response");
      return node;
    }
  }
}
