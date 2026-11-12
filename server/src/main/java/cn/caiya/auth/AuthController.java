package cn.caiya.auth;

import java.security.Principal;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthMapper mapper;
  private final CaptchaService captcha;
  private final LoginProtection protection;
  private final boolean trustForwardedHeaders;

  public AuthController(
      AuthMapper mapper,
      CaptchaService captcha,
      LoginProtection protection,
      @org.springframework.beans.factory.annotation.Value(
              "${app.login.trust-forwarded-headers:false}")
          boolean trustForwardedHeaders) {
    this.mapper = mapper;
    this.captcha = captcha;
    this.protection = protection;
    this.trustForwardedHeaders = trustForwardedHeaders;
  }

  @GetMapping("/captcha")
  public org.springframework.http.ResponseEntity<?> captcha(
      jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
    int retry =
        protection.request(LoginProtectionFilter.source(request, trustForwardedHeaders), true);
    if (retry > 0)
      return org.springframework.http.ResponseEntity.status(429)
          .header("Retry-After", Integer.toString(retry))
          .header("Cache-Control", "no-store")
          .body(Map.of("message", "验证码刷新过于频繁，请稍后再试", "retryAfter", retry));
    return org.springframework.http.ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(captcha.issue(request.getSession()));
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public Map<String, Object> me(Principal p) {
    var user = mapper.find(p.getName());
    var out = new java.util.HashMap<String, Object>();
    out.put("username", p.getName());
    out.put("displayName", user.get("display_name"));
    out.put("lockVersion", user.get("lock_version"));
    out.put("avatarAssetId", user.get("avatar_asset_id"));
    return out;
  }
}
