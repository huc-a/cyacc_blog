package cn.caiya;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class BlogApplication {
  public static void main(String[] args) {
    if (java.util.Arrays.asList(args).contains("--migrate-only")) {
      org.flywaydb.core.Flyway.configure()
          .dataSource(
              required("DB_URL"), required("MIGRATION_USER"), required("MIGRATION_PASSWORD"))
          .locations("classpath:db/migration")
          .load()
          .migrate();
      return;
    }
    SpringApplication.run(BlogApplication.class, args);
  }

  private static String required(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing environment variable: " + key);
    return value;
  }
}
