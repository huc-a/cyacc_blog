package cn.caiya.auth;

import cn.caiya.common.*;
import cn.caiya.media.MediaMapper;
import cn.caiya.system.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final AuthMapper mapper;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final MediaMapper media;

  public AccountService(
      AuthMapper mapper, PasswordEncoder encoder, AuditService audit, MediaMapper media) {
    this.mapper = mapper;
    this.encoder = encoder;
    this.audit = audit;
    this.media = media;
  }

  @Transactional
  public void password(String username, String current, String next, long version) {
    validatePassword(next);
    var user = mapper.find(username);
    if (user == null || !encoder.matches(current, user.get("password_hash").toString()))
      throw new ApiException(400, "当前密码不正确");
    if (mapper.password(username, encoder.encode(next), version) != 1)
      throw ApiException.conflict();
    audit.record("account.password", "account", Views.number(user, "id"));
  }

  static void validatePassword(String password) {
    if (password == null
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "密码至少 12 位，且 UTF-8 编码不能超过 72 字节");
  }

  @Transactional
  public void profile(String username, String name, Long avatarAssetId, long version) {
    var user = mapper.find(username);
    if (avatarAssetId != null && media.lock(avatarAssetId) == null)
      throw new ApiException(400, "管理员头像不存在");
    if (mapper.profile(username, name.trim(), avatarAssetId, version) != 1)
      throw ApiException.conflict();
    audit.record("account.profile", "account", Views.number(user, "id"));
  }
}
