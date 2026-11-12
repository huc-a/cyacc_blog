package cn.caiya.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginProtection {
  static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);

  record AccountState(int failures, Instant started, Instant lockedUntil) {}

  record SourceState(Instant started, int count, Instant failureStarted, int failures) {}

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final Object[] locks = new Object[64];
  private final ConcurrentHashMap<String, SourceState> sources = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, SourceState> images = new ConcurrentHashMap<>();
  private Instant lastCleanup = Instant.EPOCH;

  @org.springframework.beans.factory.annotation.Autowired
  public LoginProtection(JdbcTemplate jdbc) {
    this(jdbc, Clock.systemUTC());
  }

  LoginProtection(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
    Arrays.setAll(locks, i -> new Object());
  }

  Object lock(String username) {
    return locks[(username.hashCode() & Integer.MAX_VALUE) % locks.length];
  }

  Instant now() {
    return clock.instant();
  }

  static String key(String username) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(username.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  AccountState account(String username) {
    var rows =
        jdbc.query(
            "SELECT failure_count,window_started_at,locked_until FROM login_throttle WHERE"
                + " account_key=?",
            (r, n) ->
                new AccountState(
                    r.getInt(1),
                    r.getTimestamp(2).toInstant(),
                    r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant()),
            key(username));
    return rows.isEmpty() ? null : rows.get(0);
  }

  int retryAfter(AccountState state) {
    if (state == null || state.lockedUntil() == null || !state.lockedUntil().isAfter(now()))
      return 0;
    return (int)
        Math.max(1, (Duration.between(now(), state.lockedUntil()).toMillis() + 999) / 1000);
  }

  boolean captchaRequired(AccountState state, String source) {
    var s = sources.get(source);
    Instant now = now();
    return (state != null
            && state.started().plus(FAILURE_WINDOW).isAfter(now)
            && state.failures() >= 3)
        || (s != null && s.failureStarted().plus(FAILURE_WINDOW).isAfter(now) && s.failures() >= 3);
  }

  int request(String source, boolean image) {
    Instant now = now();
    var map = image ? images : sources;
    int limit = image ? 30 : 10;
    if (map.size() >= 10000)
      map.entrySet().removeIf(e -> !e.getValue().started().plus(FAILURE_WINDOW).isAfter(now));
    if (map.size() >= 10000 && !map.containsKey(source)) return 60;
    var state =
        map.compute(
            source,
            (k, v) -> {
              if (v == null) return new SourceState(now, 1, now, 0);
              boolean expired = !v.started().plusSeconds(60).isAfter(now);
              boolean oldFailures = !v.failureStarted().plus(FAILURE_WINDOW).isAfter(now);
              return new SourceState(
                  expired ? now : v.started(),
                  expired ? 1 : Math.min(v.count() + 1, limit + 1),
                  oldFailures ? now : v.failureStarted(),
                  oldFailures ? 0 : v.failures());
            });
    return state.count() > limit
        ? (int)
            Math.max(
                1, (Duration.between(now, state.started().plusSeconds(60)).toMillis() + 999) / 1000)
        : 0;
  }

  synchronized boolean capacity(String username, AccountState state) {
    Instant now = now();
    if (!lastCleanup.plusSeconds(60).isAfter(now)) {
      jdbc.update(
          "DELETE FROM login_throttle WHERE window_started_at < ? AND (locked_until IS NULL OR"
              + " locked_until <= ?)",
          Timestamp.from(now.minus(Duration.ofDays(1))),
          Timestamp.from(now));
      lastCleanup = now;
    }
    return state != null
        || jdbc.queryForObject("SELECT count(*) FROM login_throttle", Integer.class) < 10000;
  }

  void failed(String username, String source) {
    Instant now = now();
    jdbc.update(
        """
INSERT INTO login_throttle(account_key,failure_count,window_started_at) VALUES(?,1,?)
ON CONFLICT(account_key) DO UPDATE SET
  failure_count=CASE WHEN login_throttle.window_started_at<=? OR login_throttle.locked_until<=? THEN 1 ELSE LEAST(5,login_throttle.failure_count+1) END,
  window_started_at=CASE WHEN login_throttle.window_started_at<=? OR login_throttle.locked_until<=? THEN EXCLUDED.window_started_at ELSE login_throttle.window_started_at END,
  locked_until=CASE WHEN login_throttle.window_started_at<=? OR login_throttle.locked_until<=? THEN NULL
    WHEN login_throttle.failure_count>=4 THEN CAST(? AS timestamptz) ELSE NULL END
""",
        key(username),
        Timestamp.from(now),
        Timestamp.from(now.minus(FAILURE_WINDOW)),
        Timestamp.from(now),
        Timestamp.from(now.minus(FAILURE_WINDOW)),
        Timestamp.from(now),
        Timestamp.from(now.minus(FAILURE_WINDOW)),
        Timestamp.from(now),
        Timestamp.from(now.plus(FAILURE_WINDOW)));
    sources.computeIfPresent(
        source,
        (k, v) ->
            new SourceState(
                v.started(), v.count(), v.failureStarted(), Math.min(5, v.failures() + 1)));
  }

  void succeeded(String username) {
    jdbc.update("DELETE FROM login_throttle WHERE account_key=?", key(username));
  }
}
