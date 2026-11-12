package cn.caiya;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class DatabaseTestSupport {
  static final String SCHEMA = "test_" + UUID.randomUUID().toString().replace("-", "");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    String url =
        System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://127.0.0.1:55432/caiya_test");
    if (!url.split("\\?")[0].endsWith("_test"))
      throw new IllegalStateException("Tests require a dedicated database ending in _test");
    registry.add(
        "spring.datasource.url",
        () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
    registry.add(
        "spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "caiya"));
    registry.add(
        "spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    registry.add("spring.flyway.user", () -> System.getenv().getOrDefault("TEST_DB_USER", "caiya"));
    registry.add(
        "spring.flyway.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    registry.add("spring.flyway.default-schema", () -> SCHEMA);
    registry.add("app.admin-password", () -> "Test-only-password-42");
    registry.add(
        "app.media-dir",
        () ->
            java.nio.file.Path.of("..", ".local", "test-uploads", SCHEMA)
                .toAbsolutePath()
                .normalize()
                .toString());
  }
}
