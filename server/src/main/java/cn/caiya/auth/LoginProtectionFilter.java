package cn.caiya.auth;

import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

final class LoginProtectionFilter extends OncePerRequestFilter {
    private final LoginProtection protection;
    private final CaptchaService captcha;
    private final ObjectMapper json;
    private final boolean captchaAlwaysRequired;
    LoginProtectionFilter(LoginProtection protection,CaptchaService captcha,ObjectMapper json,boolean captchaAlwaysRequired) {this.protection=protection;this.captcha=captcha;this.json=json;this.captchaAlwaysRequired=captchaAlwaysRequired;}
    static String username(HttpServletRequest req) {String value=req.getParameter("username");return value==null?"":value.trim();}
    static void error(ObjectMapper json,HttpServletResponse res,int status,String message,boolean captcha,int retry) throws IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");res.setHeader("Cache-Control","no-store");
        if(retry>0) res.setHeader("Retry-After",Integer.toString(retry));
        json.writeValue(res.getWriter(),Map.of("message",message,"captchaRequired",captcha,"retryAfter",retry));
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
        if(!req.getMethod().equals("POST")||!req.getRequestURI().equals(req.getContextPath()+"/api/auth/login")) {chain.doFilter(req,res);return;}
        String source=req.getRemoteAddr(),username=username(req);
        int retry=protection.request(source,false);
        if(retry>0) {error(json,res,429,"登录尝试过于频繁，请稍后再试",false,retry);return;}
        if(username.isEmpty()||username.length()>60) {error(json,res,401,"账号或密码错误",false,0);return;}
        synchronized(protection.lock(username)) {
            var state=protection.account(username);retry=protection.retryAfter(state);
            if(retry>0) {error(json,res,429,"登录尝试过于频繁，请稍后再试",true,retry);return;}
            if(!protection.capacity(username,state)) {error(json,res,429,"登录尝试过于频繁，请稍后再试",false,60);return;}
            boolean captchaSubmitted=req.getParameterMap().containsKey("captcha");
            if((captchaAlwaysRequired||captchaSubmitted||protection.captchaRequired(state,source))&&!captcha.consume(req.getSession(false),req.getParameter("captcha"))) {
                error(json,res,400,"请输入正确的图片验证码，点击图片可重新获取",true,0);return;
            }
            chain.doFilter(req,res);
        }
    }
}
