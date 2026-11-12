package cn.caiya.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/account")
public class AccountController {
  private final AccountService service;
  private final SessionRegistry registry;

  public AccountController(AccountService service, SessionRegistry registry) {
    this.service = service;
    this.registry = registry;
  }

  public record Password(
      @NotNull @Size(max = 72) String currentPassword,
      @NotNull @Size(min = 12, max = 72) String newPassword,
      @NotNull @PositiveOrZero Long lockVersion) {}

  public record Profile(
      @NotBlank @Size(max = 80) String displayName,
      @Positive Long avatarAssetId,
      @NotNull @PositiveOrZero Long lockVersion) {}

  @PostMapping("/password")
  public void password(
      @Valid @RequestBody Password input, Principal principal, HttpServletRequest request) {
    service.password(
        principal.getName(), input.currentPassword(), input.newPassword(), input.lockVersion());
    for (Object p : registry.getAllPrincipals())
      if (p instanceof UserDetails u && u.getUsername().equals(principal.getName()))
        registry.getAllSessions(p, false).forEach(s -> s.expireNow());
    var session = request.getSession(false);
    if (session != null) session.invalidate();
  }

  @PutMapping("/profile")
  public void profile(@Valid @RequestBody Profile input, Principal principal) {
    service.profile(
        principal.getName(), input.displayName(), input.avatarAssetId(), input.lockVersion());
  }
}
