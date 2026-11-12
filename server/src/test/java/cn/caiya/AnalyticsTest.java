package cn.caiya;

import static org.assertj.core.api.Assertions.*;

import cn.caiya.analytics.AnalyticsService;
import cn.caiya.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnalyticsTest {
  @Test
  void unconfiguredAndInvalidRangeAreDifferent() {
    var service = new AnalyticsService("", "", "", "", new ObjectMapper());
    var today = LocalDate.of(2026, 10, 21);
    assertThat(service.overview(today, today).get("status")).isEqualTo("unconfigured");
    assertThatThrownBy(() -> service.overview(today, today.minusDays(1)))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.overview(today.minusDays(367), today))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void realHttpAdapterDistinguishesEmptyFromUpstreamFailure() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/websites/",
        exchange -> {
          if (!"Bearer fixture-token"
              .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
          }
          String path = exchange.getRequestURI().getPath();
          byte[] body =
              (path.endsWith("stats")
                      ? "{\"pageviews\":{\"value\":0,\"prev\":2},\"visitors\":{\"value\":0},\"visits\":{\"value\":0}}"
                      : path.endsWith("pageviews") ? "{\"pageviews\":[],\"sessions\":[]}" : "[]")
                  .getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    String url = "http://127.0.0.1:" + server.getAddress().getPort();
    var today = LocalDate.of(2026, 10, 21);
    String id = "11111111-1111-1111-1111-111111111111";
    try {
      var ok =
          new AnalyticsService(url, "", id, "fixture-token", new ObjectMapper())
              .overview(today, today);
      assertThat(ok.get("status")).isEqualTo("available");
      assertThat(ok.toString()).doesNotContain("fixture-token");
      assertThat(ok.get("metricUnits"))
          .isEqualTo(Map.of("url", "pageviews", "sessions", "sessions"));
      assertThat(((JsonNode) ok.get("overview")).path("pageviews").path("prev").asInt())
          .isEqualTo(2);
      var failure =
          new AnalyticsService(url, "", id, "wrong", new ObjectMapper()).overview(today, today);
      assertThat(failure.get("status")).isEqualTo("unavailable");
      assertThat(failure).doesNotContainKey("overview");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void cloudUsesExplicitApiBaseAndNormalizesV3SummaryAndPageDimension() throws Exception {
    var requests = new ArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/eu/websites/",
        exchange -> {
          requests.add(exchange.getRequestURI().toString());
          if (!"Bearer cloud-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
          }
          String path = exchange.getRequestURI().getPath();
          String response =
              path.endsWith("stats")
                  ? "{\"pageviews\":12,\"visitors\":3,\"visits\":4,\"comparison\":{\"pageviews\":5,\"visitors\":2,\"visits\":2}}"
                  : path.endsWith("pageviews")
                      ? "{\"pageviews\":[{\"x\":\"2026-10-21\",\"y\":12}],\"sessions\":[{\"x\":\"2026-10-21\",\"y\":4}]}"
                      : "[{\"x\":\"/\",\"y\":12}]";
          byte[] body = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var date = LocalDate.of(2026, 10, 21);
      var result =
          new AnalyticsService(
                  "http://unused.invalid",
                  "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/eu/",
                  "11111111-1111-1111-1111-111111111111",
                  "cloud-key",
                  new ObjectMapper())
              .overview(date, date);
      assertThat(result.get("status")).isEqualTo("available");
      assertThat(result.get("metricUnits"))
          .isEqualTo(Map.of("url", "visitors", "sessions", "visitors"));
      var overview = (JsonNode) result.get("overview");
      assertThat(overview.path("pageviews").path("value").asInt()).isEqualTo(12);
      assertThat(overview.path("visitors").path("value").asInt()).isEqualTo(3);
      assertThat(overview.path("visits").path("prev").asInt()).isEqualTo(2);
      assertThat(((JsonNode) result.get("url")).get(0).path("y").asInt()).isEqualTo(12);
      assertThat(requests)
          .hasSize(7)
          .allMatch(p -> p.startsWith("/v1/eu/websites/"))
          .noneMatch(p -> p.contains("type=url"));
      assertThat(requests)
          .anyMatch(p -> p.contains("type=path"))
          .anyMatch(p -> p.contains("compare=prev"));
      assertThat(requests)
          .allMatch(p -> p.contains("startAt=1792512000000") && p.contains("endAt=1792598399999"));
      assertThat(result.toString()).doesNotContain("cloud-key");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void malformedSuccessfulResponseIsUnavailableRatherThanFakeZero() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/websites/",
        exchange -> {
          byte[] body = "{}".getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var date = LocalDate.of(2026, 10, 21);
      var result =
          new AnalyticsService(
                  "http://127.0.0.1:" + server.getAddress().getPort(),
                  "",
                  "11111111-1111-1111-1111-111111111111",
                  "fixture",
                  new ObjectMapper())
              .overview(date, date);
      assertThat(result.get("status")).isEqualTo("unavailable");
      assertThat(result).doesNotContainKey("overview");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void missingCloudKeyExplainsThatCollectionIsIndependent() {
    var date = LocalDate.of(2026, 10, 21);
    var result =
        new AnalyticsService(
                "",
                "https://api.umami.is/v1",
                "11111111-1111-1111-1111-111111111111",
                "",
                new ObjectMapper())
            .overview(date, date);
    assertThat(result.get("status")).isEqualTo("unconfigured");
    assertThat(result.get("message").toString()).contains("API 密钥", "独立运行");
  }
}
