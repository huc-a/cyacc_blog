package cn.caiya.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Central cache policy (replaces the security framework's blanket no-store): public read APIs get a
 * short shared cache, public media files are immutable (UUID storage names, content never changes),
 * everything else under /api stays no-store. Controllers may still override with explicit headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachePolicyFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String method = request.getMethod();
    if ("GET".equals(method) || "HEAD".equals(method)) {
      String uri = request.getRequestURI();
      if (uri.startsWith("/api/public/media/")) {
        response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
      } else if (uri.startsWith("/api/public/")) {
        response.setHeader("Cache-Control", "public, max-age=60, stale-while-revalidate=300");
      } else if (uri.startsWith("/api/")) {
        response.setHeader("Cache-Control", "no-store");
      }
    }
    chain.doFilter(request, response);
  }
}
