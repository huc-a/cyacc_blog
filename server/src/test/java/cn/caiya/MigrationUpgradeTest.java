package cn.caiya;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MigrationUpgradeTest {
    @Test void upgradingV7RestoresHistoricalArticleCategoriesAndFrozenProtection() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:55432/caiya_test");
        if(!url.split("\\?")[0].endsWith("_test")) throw new IllegalStateException("Requires isolated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","caiya"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("7").load().migrate();
        String schemaUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        try(var connection=DriverManager.getConnection(schemaUrl,user,password);var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO tag(name,slug) VALUES('教程','tutorials-tag')");
            long tagId;try(var rows=sql.executeQuery("SELECT id FROM tag WHERE slug='tutorials-tag'")){rows.next();tagId=rows.getLong(1);}
            sql.executeUpdate("INSERT INTO content(content_type,slug) VALUES('article','v7-category-restore')");
            long contentId;try(var rows=sql.executeQuery("SELECT id FROM content WHERE slug='v7-category-restore'")){rows.next();contentId=rows.getLong(1);}
            try(var rows=sql.executeQuery("INSERT INTO content_revision(content_id,content_type,revision_no,title,summary) VALUES("+contentId+",'article',1,'Frozen article','Summary') RETURNING id")){rows.next();long revisionId=rows.getLong(1);
                sql.executeUpdate("INSERT INTO article_detail(revision_id,body_markdown) VALUES("+revisionId+",'Body')");
                sql.executeUpdate("INSERT INTO revision_tag(revision_id,tag_id) VALUES("+revisionId+","+tagId+")");
                sql.executeUpdate("UPDATE content_revision SET revision_state='frozen',frozen_at=now() WHERE id="+revisionId);
                sql.executeUpdate("UPDATE content SET publish_status='published',published_revision_id="+revisionId+",first_published_at=now(),last_published_at=now() WHERE id="+contentId);
            }
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
            try(var rows=sql.executeQuery("SELECT r.category_id,c.name FROM content_revision r JOIN category c ON c.id=r.category_id WHERE r.content_id="+contentId)) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(2)).isEqualTo("教程");assertThat(rows.next()).isFalse();
            }
            try(var rows=sql.executeQuery("SELECT count(*) FROM category")){rows.next();assertThat(rows.getInt(1)).isEqualTo(3);}
            try(var rows=sql.executeQuery("SELECT value_jsonb->>'defaultCoverAssetId' FROM site_setting WHERE setting_key='site.public'")){rows.next();assertThat(rows.getString(1)).isNull();}
            long revisionId;try(var rows=sql.executeQuery("SELECT published_revision_id FROM content WHERE id="+contentId)){rows.next();revisionId=rows.getLong(1);}
            assertThatThrownBy(()->sql.executeUpdate("UPDATE content_revision SET title='Tampered' WHERE id="+revisionId)).isInstanceOf(SQLException.class);
        } finally {try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {sql.execute("DROP SCHEMA "+schema+" CASCADE");}}
    }

    @Test void upgradingV4PreservesAccountsAndAddsLoginProtection() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:55432/caiya_test");
        if(!url.split("\\?")[0].endsWith("_test")) throw new IllegalStateException("Requires isolated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","caiya"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("4").load().migrate();
        try(var connection=DriverManager.getConnection(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO admin_user(username,password_hash,display_name,enabled,lock_version) VALUES('existing-admin','preserved-hash','原管理员',true,7),('disabled-admin','other-hash','已停用',false,3)");
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
            try(var rows=sql.executeQuery("SELECT username,password_hash,enabled,lock_version,security_version FROM admin_user ORDER BY id")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("existing-admin");assertThat(rows.getString(2)).isEqualTo("preserved-hash");
                assertThat(rows.getBoolean(3)).isTrue();assertThat(rows.getLong(4)).isEqualTo(7);assertThat(rows.getLong(5)).isZero();
                assertThat(rows.next()).isTrue();assertThat(rows.getBoolean(3)).isFalse();assertThat(rows.getLong(4)).isEqualTo(3);
            }
            try(var rows=sql.executeQuery("SELECT count(*) FROM login_throttle")) {rows.next();assertThat(rows.getLong(1)).isZero();}
        } finally {try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {sql.execute("DROP SCHEMA "+schema+" CASCADE");}}
    }
    @Test void upgradingV2PreservesPersonalSettingsAndInvalidatesStaleEdits() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:55432/caiya_test");
        if(!url.split("\\?")[0].endsWith("_test")) throw new IllegalStateException("Requires isolated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","caiya"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("2").load().migrate();
        String schemaUrl=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        try(var connection=DriverManager.getConnection(schemaUrl,user,password);var sql=connection.createStatement()) {
            sql.executeUpdate("UPDATE site_setting SET value_jsonb=value_jsonb || '{\"author\":\"原有作者\",\"email\":\"keep@example.com\",\"background\":\"blue\"}'::jsonb,lock_version=7 WHERE setting_key='site.public'");
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("3").load().migrate();
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
    @Test void upgradingV3PreservesImagesContactsAndAdministrator() throws Exception {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:55432/caiya_test");
        if(!url.split("\\?")[0].endsWith("_test")) throw new IllegalStateException("Requires isolated test database");
        String user=System.getenv().getOrDefault("TEST_DB_USER","caiya"),password=System.getenv().getOrDefault("TEST_DB_PASSWORD","");
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("3").load().migrate();
        try(var connection=DriverManager.getConnection(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);var sql=connection.createStatement()) {
            sql.executeUpdate("INSERT INTO admin_user(username,password_hash,display_name,lock_version) VALUES('before-upgrade','test-only-hash','原管理员',4)");
            sql.executeUpdate("INSERT INTO media_asset(storage_key,original_name,mime_type,size_bytes,width,height,sha256) VALUES('keep.png','keep.png','image/png',10,1,1,repeat('a',64))");
            sql.executeUpdate("UPDATE site_setting SET value_jsonb=value_jsonb || '{\"avatarAssetId\":1,\"backgroundAssetId\":1,\"icpNumber\":\"桂ICP备2026024037号\",\"email\":\"keep@example.com\"}'::jsonb,lock_version=9");
            Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
            try(var rows=sql.executeQuery("SELECT value_jsonb::text,lock_version,schema_version FROM site_setting WHERE setting_key='site.public'")) {
                assertThat(rows.next()).isTrue();var value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(rows.getString(1));
                assertThat(value.path("avatarAssetId").asInt()).isEqualTo(1);assertThat(value.path("backgroundAssetId").asInt()).isEqualTo(1);
                assertThat(value.path("icpNumber").asText()).isEqualTo("桂ICP备2026024037号");assertThat(value.path("email").asText()).isEqualTo("keep@example.com");
                assertThat(value.path("policeNumber").asText()).isEmpty();assertThat(value.get("loginBackgroundAssetId").isNull()).isTrue();
                assertThat(rows.getInt(2)).isEqualTo(10);assertThat(rows.getInt(3)).isEqualTo(3);
            }
            try(var rows=sql.executeQuery("SELECT display_name,lock_version,avatar_asset_id FROM admin_user WHERE username='before-upgrade'")) {
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("原管理员");assertThat(rows.getInt(2)).isEqualTo(5);assertThat(rows.getObject(3)).isNull();
            }
        } finally {try(var connection=DriverManager.getConnection(url,user,password);var sql=connection.createStatement()) {sql.execute("DROP SCHEMA "+schema+" CASCADE");}}
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
