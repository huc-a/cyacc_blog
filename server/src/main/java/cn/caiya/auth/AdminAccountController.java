package cn.caiya.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
@Validated
public class AdminAccountController {
  private final AdminAccountService service;

  public AdminAccountController(AdminAccountService service) {
    this.service = service;
  }

  public record Create(
      @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{2,59}") String username,
      @NotBlank @Size(max = 80) String displayName,
      @NotNull @Size(min = 12, max = 72) String password) {}

  public record State(@NotNull Boolean enabled, @NotNull @PositiveOrZero Long lockVersion) {}

  public record Reset(
      @NotNull @Size(min = 12, max = 72) String newPassword,
      @NotNull @PositiveOrZero Long lockVersion) {}

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return service.list(page, size);
  }

  @PostMapping
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public void create(@Valid @RequestBody Create input, Principal p) {
    service.create(p.getName(), input.username(), input.displayName(), input.password());
  }

  @PutMapping("/{id}/state")
  public void state(@PathVariable @Positive long id, @Valid @RequestBody State input, Principal p) {
    service.enabled(p.getName(), id, input.enabled(), input.lockVersion());
  }

  @PostMapping("/{id}/password")
  public void reset(@PathVariable @Positive long id, @Valid @RequestBody Reset input, Principal p) {
    service.resetPassword(p.getName(), id, input.newPassword(), input.lockVersion());
  }
}
