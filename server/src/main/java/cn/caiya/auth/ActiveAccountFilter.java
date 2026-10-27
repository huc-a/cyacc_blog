package cn.caiya.auth;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Also rejects sessions authenticated concurrently with a password reset or account suspension. */
final class ActiveAccountFilter extends OncePerRequestFilter {
    private final AuthMapper mapper;
    ActiveAccountFilter(AuthMapper mapper) { this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws IOException,ServletException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal principal) {
            var state = mapper.state(principal.getUsername());
            if (state == null || !Boolean.TRUE.equals(state.get("enabled")) ||
                    ((Number)state.get("security_version")).longValue() != principal.securityVersion()) {
                SecurityContextHolder.clearContext();
                var session = req.getSession(false);
                if (session != null) session.invalidate();
                SecurityConfig.jsonError(res,401,"会话已失效，请重新登录");
                return;
            }
        }
        chain.doFilter(req,res);
    }
}
