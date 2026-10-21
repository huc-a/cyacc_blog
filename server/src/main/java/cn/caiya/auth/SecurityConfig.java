package cn.caiya.auth;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean org.springframework.security.core.session.SessionRegistry sessionRegistry() {return new org.springframework.security.core.session.SessionRegistryImpl();}
    @Bean org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents() {return new org.springframework.security.web.session.HttpSessionEventPublisher();}
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean UserDetailsService users(AuthMapper mapper) {
        return name -> {
            var u=mapper.find(name);
            if(u==null) throw new UsernameNotFoundException("账号或密码错误");
            return User.withUsername(name).password((String)u.get("password_hash")).roles("ADMIN")
                .disabled(!Boolean.TRUE.equals(u.get("enabled"))).build();
        };
    }
    @Bean ApplicationRunner initialize(AuthMapper mapper, PasswordEncoder encoder,
            @Value("${app.admin-username}") String username, @Value("${app.admin-password}") String password) {
        return args -> {
            if(mapper.count()==0) {
                if(password.length()<12) throw new IllegalStateException("首次启动必须设置至少 12 位的 ADMIN_PASSWORD");
                mapper.create(username,encoder.encode(password));
            }
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http,AuthMapper mapper,org.springframework.security.core.session.SessionRegistry registry) throws Exception {
        var csrf=new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http.csrf(c->c.csrfTokenRepository(csrf))
            .authorizeHttpRequests(a->a.requestMatchers("/api/public/**","/api/auth/csrf","/api/auth/login","/error").permitAll().anyRequest().hasRole("ADMIN"))
            .requestCache(c->c.disable())
            .sessionManagement(s->s.maximumSessions(5).sessionRegistry(registry).expiredSessionStrategy(e->jsonError(e.getResponse(),401,"会话已失效，请重新登录")))
            .formLogin(f->f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth)->{mapper.login(auth.getName());res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"authenticated\":true}");})
                .failureHandler((req,res,e)->jsonError(res,401,"账号或密码错误")))
            .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->jsonError(res,401,"请先登录"))
                .accessDeniedHandler((req,res,x)->jsonError(res,403,"权限不足或安全令牌已失效，请刷新页面")))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .addFilterBefore(new LoginRateLimit(),UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    static void jsonError(HttpServletResponse res,int status,String message) throws IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");res.getWriter().write("{\"message\":\""+message+"\"}");
    }
    static class LoginRateLimit extends OncePerRequestFilter {
        record Window(long start,int count) {}
        private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
        protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
            if(req.getMethod().equals("POST") && req.getRequestURI().equals(req.getContextPath()+"/api/auth/login")) {
                long now=System.currentTimeMillis();
                if(attempts.size()>10000) attempts.entrySet().removeIf(e->now-e.getValue().start>60000);
                if(attempts.size()>=10000 && !attempts.containsKey(req.getRemoteAddr())) {jsonError(res,429,"登录尝试过于频繁，请稍后再试");return;}
                var w=attempts.compute(req.getRemoteAddr(),(k,v)->v==null||now-v.start>60000?new Window(now,1):new Window(v.start,v.count+1));
                if(w.count>10) {res.setHeader("Retry-After","60");jsonError(res,429,"登录尝试过于频繁，请稍后再试");return;}
            }
            chain.doFilter(req,res);
        }
    }
}
