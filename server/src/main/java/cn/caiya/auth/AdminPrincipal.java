package cn.caiya.auth;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class AdminPrincipal extends User {
  private final long securityVersion;

  public AdminPrincipal(Map<String, Object> account) {
    super(
        account.get("username").toString(),
        account.get("password_hash").toString(),
        Boolean.TRUE.equals(account.get("enabled")),
        true,
        true,
        true,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    securityVersion = ((Number) account.get("security_version")).longValue();
  }

  public long securityVersion() {
    return securityVersion;
  }
}
