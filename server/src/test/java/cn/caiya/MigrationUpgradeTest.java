package cn.caiya;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationUpgradeTest {
    @Test void upgradingV2PreservesPersonalSettingsAndInvalidatesStaleEdits() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:55432/caiya_test");
        if(!url.split("\\?")[0].endsWith("_test")) throw new IllegalStateException("Requires isolated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","caiya"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("2").load().migrate();
        String schemaUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        try(var connection=DriverManager.getConnection(schemaUrl,user,password);var sql=connection.createStatement()) {
            sql.executeUpdate("UPDATE site_setting SET value_jsonb=value_jsonb || '{\"author\":\"原有作者\",\"email\":\"keep@example.com\",\"background\":\"blue\"}'::jsonb,lock_version=7 WHERE setting_key='site.public'");
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
            try(var result=sql.executeQuery("SELECT value_jsonb::text,lock_version,schema_version FROM site_setting WHERE setting_key='site.public'")) {
                assertThat(result.next()).isTrue();
                var value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getString(1));
                assertThat(value.path("author").asText()).isEqualTo("原有作者");
                assertThat(value.path("email").asText()).isEqualTo("keep@example.com");
                assertThat(value.path("background").asText()).isEqualTo("blue");
                assertThat(value.has("avatarAssetId") && value.get("avatarAssetId").isNull()).isTrue();
                assertThat(value.path("navTopOpacity").asDouble()).isZero();
                assertThat(value.path("navScrolledOpacity").asDouble()).isEqualTo(.92);
                assertThat(value.path("icpNumber").asText()).isEmpty();
                assertThat(result.getInt(2)).isEqualTo(8);assertThat(result.getInt(3)).isEqualTo(2);
            }
        } finally {
            try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {sql.execute("DROP SCHEMA "+schema+" CASCADE");}
        }
    }
    @Test void upgradingV1KeepsPublishedArticleAndAddsNewSchema() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:55432/caiya_test");
        if(!url.split("\\?")[0].endsWith("_test")) throw new IllegalStateException("Requires isolated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","caiya"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("1").load().migrate();
        String schemaUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        try(var connection=DriverManager.getConnection(schemaUrl,user,password);var sql=connection.createStatement()) {
            connection.setAutoCommit(false);
            sql.executeUpdate("INSERT INTO content(content_type,slug) VALUES('article','upgrade-article')");
            sql.executeUpdate("INSERT INTO content_revision(content_id,content_type,revision_no,title,summary) VALUES(1,'article',1,'Keep title','Keep summary')");
            sql.executeUpdate("INSERT INTO article_detail(revision_id,body_markdown) VALUES(1,'Keep published body')");
            sql.executeUpdate("UPDATE content_revision SET revision_state='frozen',frozen_at=now() WHERE id=1");
            sql.executeUpdate("UPDATE content SET publish_status='published',published_revision_id=1,first_published_at=now(),last_published_at=now() WHERE id=1");
            connection.commit();
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
            try(var result=sql.executeQuery("SELECT a.body_markdown FROM content c JOIN article_detail a ON a.revision_id=c.published_revision_id WHERE c.slug='upgrade-article'")) {assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("Keep published body");}
            try(var result=sql.executeQuery("SELECT count(*) FROM site_setting")) {result.next();assertThat(result.getInt(1)).isEqualTo(1);}
            connection.commit();
        } finally {
            try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {sql.execute("DROP SCHEMA "+schema+" CASCADE");}
        }
    }
}
