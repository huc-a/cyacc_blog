package cn.caiya;

import cn.caiya.analytics.AnalyticsService;
import cn.caiya.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AnalyticsTest {
    @Test void unconfiguredAndInvalidRangeAreDifferent() {
        var service=new AnalyticsService("","","",new ObjectMapper());var today=LocalDate.of(2026,10,21);
        assertThat(service.overview(today,today).get("status")).isEqualTo("unconfigured");
        assertThatThrownBy(()->service.overview(today,today.minusDays(1))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.overview(today.minusDays(367),today)).isInstanceOf(ApiException.class);
    }
    @Test void realHttpAdapterDistinguishesEmptyFromUpstreamFailure() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/websites/",exchange->{
            if(!"Bearer fixture-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {exchange.sendResponseHeaders(401,-1);exchange.close();return;}
            byte[] body=(exchange.getRequestURI().getPath().endsWith("stats")?"{\"pageviews\":{\"value\":0}}":"[]").getBytes();
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();String url="http://127.0.0.1:"+server.getAddress().getPort();var today=LocalDate.of(2026,10,21);String id="11111111-1111-1111-1111-111111111111";
        try {
            var ok=new AnalyticsService(url,id,"fixture-token",new ObjectMapper()).overview(today,today);
            assertThat(ok.get("status")).isEqualTo("available");assertThat(ok.toString()).doesNotContain("fixture-token");
            var failure=new AnalyticsService(url,id,"wrong",new ObjectMapper()).overview(today,today);
            assertThat(failure.get("status")).isEqualTo("unavailable");assertThat(failure).doesNotContainKey("overview");
        } finally {server.stop(0);}
    }
}
