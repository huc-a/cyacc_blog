package cn.caiya.system;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditMapper mapper;
  private final int retentionDays;

  public AuditService(
      AuditMapper mapper, @Value("${app.audit.retention-days:180}") int retentionDays) {
    this.mapper = mapper;
    this.retentionDays = retentionDays;
  }

  public void record(String action, String type, long id) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    mapper.append(auth == null ? null : auth.getName(), action, type, id, UUID.randomUUID());
  }

  /**
   * The audit log is append-only for operators; retention is enforced here so it cannot grow
   * forever.
   */
  @Scheduled(cron = "0 30 3 * * *")
  public void purgeExpired() {
    if (retentionDays < 1) return;
    try {
      int removed = mapper.purge(java.time.OffsetDateTime.now().minusDays(retentionDays));
      if (removed > 0)
        org.slf4j.LoggerFactory.getLogger(AuditService.class)
            .info("Purged {} audit log rows older than {} days", removed, retentionDays);
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(AuditService.class).warn("Audit log purge failed", e);
    }
  }
}
