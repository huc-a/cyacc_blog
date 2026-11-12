package cn.caiya;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.caiya.common.*;
import cn.caiya.content.*;
import cn.caiya.content.ContentQuery;
import cn.caiya.media.*;
import cn.caiya.site.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class ContentAndMediaIntegrationTest extends DatabaseTestSupport {
  @Autowired ContentService contents;
  @Autowired ContentMapper contentMapper;
  @Autowired DetailMapper detailMapper;
  @Autowired MediaService media;
  @Autowired MediaMapper mediaMapper;
  @Autowired TaxonomyMapper taxonomy;
  @Autowired SiteService site;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  String slug() {
    return "test-" + UUID.randomUUID();
  }

  long n(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  ContentDetails details(Map<String, Object> values) {
    return json.convertValue(values, ContentDetails.class);
  }

  ContentDetails completeDetails() {
    return details(
        Map.of(
            "sourceUrl",
            "https://example.com/source",
            "demoUrl",
            "https://example.com/demo",
            "benefitText",
            "免费资源",
            "requirementsMarkdown",
            "仅供个人学习",
            "techStack",
            List.of("Java", "Vue"),
            "chapters",
            List.of(
                Map.of(
                    "chapterKey",
                    UUID.randomUUID(),
                    "slug",
                    "start",
                    "title",
                    "第一章",
                    "bodyMarkdown",
                    "章节正文"))));
  }

  ContentInput input(String type, String slug, String title, long version, ContentDetails d) {
    return new ContentInput(
        type,
        slug,
        title,
        "摘要 " + title,
        "# 正文 " + title,
        version,
        null,
        List.of(),
        null,
        List.of(),
        d);
  }

  private static final java.util.concurrent.atomic.AtomicInteger PNG_VARIANT =
      new java.util.concurrent.atomic.AtomicInteger();

  byte[] png() throws IOException {
    // Each upload must be a distinct image: identical bytes are now
    // deduplicated by sha256 into a single shared asset.
    var image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, PNG_VARIANT.incrementAndGet());
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    return output.toByteArray();
  }

  long upload() throws IOException {
    return n(media.upload(new MockMultipartFile("file", "test.png", "image/png", png())), "id");
  }

  @Test
  void articleCategoriesAreReturnedFilteredAndProtectedWhileReferenced() throws Exception {
    String slug = slug(), categorySlug = "category-" + slug;
    var category = new HashMap<String, Object>();
    category.put("name", "Category " + slug);
    category.put("slug", categorySlug);
    category.put("sortOrder", 10);
    taxonomy.createCategory(category);
    long categoryId = n(category, "id");
    var input =
        new ContentInput(
            "article",
            slug,
            "Categorized article",
            "Summary",
            "Body",
            0L,
            null,
            List.of(),
            categoryId,
            null,
            List.of(),
            null);
    var draft = contents.create(input);
    long contentId = n(draft, "id");
    var saved = contents.save(contentId, input);
    var published = contents.publish(contentId, n(saved, "lockVersion"));
    assertThat(published.get("categoryId")).isEqualTo(categoryId);
    assertThat(
            contents
                .list(
                    new ContentQuery(
                        true, "article", "all", "", null, categoryId, null, false, 1, 10))
                .get("total"))
        .isEqualTo(1L);
    mvc.perform(get("/api/public/taxonomy"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.categories[?(@.id == " + categoryId + ")].name").value("Category " + slug));
    mvc.perform(
            get("/api/public/contents")
                .param("type", "article")
                .param("categoryId", Long.toString(categoryId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].categoryName").value("Category " + slug));
    assertThat(taxonomy.categoryReferences(categoryId)).isPositive();
    mvc.perform(delete("/api/admin/taxonomy/categories/" + categoryId).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void readingNeighborsUsePublicTitlesAndStableOrderWithinTheSameType() throws Exception {
    // Isolate the visible article sequence inside this rolled-back test transaction.
    jdbc.update(
        "UPDATE content SET publish_status='offline' WHERE content_type='article' AND"
            + " publish_status='published'");
    var sequence = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 7; i++) {
      String type = i == 4 ? "work" : "article";
      var item = contents.create(input(type, slug(), "公开标题 " + i, 0, completeDetails()));
      if (i != 1) item = contents.publish(n(item, "id"), n(item, "lockVersion"));
      jdbc.update(
          "UPDATE content SET last_published_at='2099-01-01T00:00:00Z' WHERE id=?", n(item, "id"));
      sequence.add(item);
    }
    var previous = sequence.get(0);
    var current = sequence.get(2);
    var next = sequence.get(6);
    contents.transition(n(sequence.get(3), "id"), n(sequence.get(3), "lockVersion"), "offline");
    contents.transition(n(sequence.get(5), "id"), n(sequence.get(5), "lockVersion"), "trash");
    previous =
        contents.save(
            n(previous, "id"),
            input(
                "article",
                previous.get("slug").toString(),
                "尚未发布的标题",
                n(previous, "lockVersion"),
                completeDetails()));
    String path = "/api/public/contents/" + current.get("slug");
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.navigation.previous.slug").value(previous.get("slug")))
        .andExpect(jsonPath("$.navigation.previous.title").value("公开标题 0"))
        .andExpect(jsonPath("$.navigation.next.slug").value(next.get("slug")))
        .andExpect(jsonPath("$.navigation.next.contentType").value("article"))
        .andExpect(jsonPath("$.navigation.previous.lockVersion").doesNotExist());
    mvc.perform(get("/api/public/contents/" + previous.get("slug")))
        .andExpect(jsonPath("$.navigation.previous").value(org.hamcrest.Matchers.nullValue()));
    mvc.perform(get("/api/public/contents/" + next.get("slug")))
        .andExpect(jsonPath("$.navigation.next").value(org.hamcrest.Matchers.nullValue()));
    contents.transition(n(previous, "id"), n(previous, "lockVersion"), "offline");
    contents.transition(n(next, "id"), n(next, "lockVersion"), "trash");
    mvc.perform(get(path))
        .andExpect(jsonPath("$.navigation.previous").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.navigation.next").value(org.hamcrest.Matchers.nullValue()));
    contents.transition(n(current, "id"), n(current, "lockVersion"), "offline");
    for (var item : List.of(current, sequence.get(1), sequence.get(5)))
      mvc.perform(get("/api/public/contents/" + item.get("slug"))).andExpect(status().isNotFound());
  }

  @Test
  @SuppressWarnings("unchecked")
  void listTagsFollowPublishedRevisionWithoutLeakingWorkingTags() {
    String slug = slug();
    var oldTag =
        new HashMap<String, Object>(
            Map.of("name", "published-" + slug, "slug", "published-" + slug));
    taxonomy.createTag(oldTag);
    var nextTag =
        new HashMap<String, Object>(Map.of("name", "working-" + slug, "slug", "working-" + slug));
    taxonomy.createTag(nextTag);
    var input =
        new ContentInput(
            "article",
            slug,
            slug,
            "摘要",
            "正文",
            0L,
            null,
            List.of(n(oldTag, "id")),
            null,
            List.of(),
            null);
    var item = contents.create(input);
    long id = n(item, "id");
    var published = contents.publish(id, 0);
    var changed =
        contents.save(
            id,
            new ContentInput(
                "article",
                slug,
                slug,
                "摘要",
                "新正文",
                n(published, "lockVersion"),
                null,
                List.of(n(nextTag, "id")),
                null,
                List.of(),
                null));
    var visible =
        (List<Map<String, Object>>)
            contents
                .list(
                    new ContentQuery(true, "article", "all", slug, null, null, null, false, 1, 10))
                .get("items");
    assertThat(visible).hasSize(1);
    assertThat((List<String>) visible.get(0).get("tagNames"))
        .containsExactly(oldTag.get("name").toString());
    var working =
        (List<Map<String, Object>>)
            contents
                .list(
                    new ContentQuery(false, "article", "all", slug, null, null, null, false, 1, 10))
                .get("items");
    assertThat((List<String>) working.get(0).get("tagNames"))
        .containsExactly(nextTag.get("name").toString());
    contents.publish(id, n(changed, "lockVersion"));
    visible =
        (List<Map<String, Object>>)
            contents
                .list(
                    new ContentQuery(true, "article", "all", slug, null, null, null, false, 1, 10))
                .get("items");
    assertThat((List<String>) visible.get(0).get("tagNames"))
        .containsExactly(nextTag.get("name").toString());
  }

  @Test
  void gifAvatarPreservesAnimationAndUsesSiteReferenceProtection() throws Exception {
    var original = site.read();
    var asset =
        media.upload(
            new MockMultipartFile("file", "avatar.gif", "image/gif", GifFixtures.animation(2)));
    long id = n(asset, "id");
    mvc.perform(get("/api/public/media/" + id)).andExpect(status().isNotFound());
    var values =
        json.convertValue(
            original.get("settings"),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    values.put("avatarAssetId", id);
    values.put("icpNumber", "桂ICP备2026024037号");
    values.put("navTopOpacity", 0);
    values.put("navScrolledOpacity", 0.95);
    var changed =
        site.save(json.convertValue(values, SiteSettings.class), n(original, "lockVersion"));
    try {
      mvc.perform(get("/api/public/media/" + id))
          .andExpect(status().isOk())
          .andExpect(content().contentType("image/gif"));
      assertThatThrownBy(() -> media.delete(id)).isInstanceOf(ApiException.class);
      var bytes = java.nio.file.Files.readAllBytes(media.path(mediaMapper.find(id)));
      assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1))
          .doesNotContain("untrusted metadata");
      try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
        var reader = ImageIO.getImageReaders(stream).next();
        reader.setInput(stream);
        try {
          assertThat(reader.getNumImages(true)).isEqualTo(2);
          assertThat(reader.read(0).getRGB(0, 0) & 0xffffff).isEqualTo(0xff0000);
          assertThat(reader.read(1).getRGB(0, 0) & 0xffffff).isEqualTo(0x0000ff);
          var first =
              (javax.imageio.metadata.IIOMetadataNode)
                  reader.getImageMetadata(0).getAsTree("javax_imageio_gif_image_1.0");
          var control =
              (javax.imageio.metadata.IIOMetadataNode)
                  first.getElementsByTagName("GraphicControlExtension").item(0);
          assertThat(control.getAttribute("delayTime")).isEqualTo("7");
          assertThat(control.getAttribute("disposalMethod")).isEqualTo("restoreToBackgroundColor");
          var loop =
              (javax.imageio.metadata.IIOMetadataNode)
                  first.getElementsByTagName("ApplicationExtension").item(0);
          assertThat(loop.getAttribute("applicationID")).isEqualTo("NETSCAPE");
          assertThat((byte[]) loop.getUserObject()).containsExactly(1, 0, 0);
          var tree =
              (javax.imageio.metadata.IIOMetadataNode)
                  reader.getImageMetadata(1).getAsTree("javax_imageio_gif_image_1.0");
          assertThat(
                  ((javax.imageio.metadata.IIOMetadataNode)
                          tree.getElementsByTagName("GraphicControlExtension").item(0))
                      .getAttribute("delayTime"))
              .isEqualTo("13");
        } finally {
          reader.dispose();
        }
      }
    } finally {
      site.save((SiteSettings) original.get("settings"), n(changed, "lockVersion"));
    }
    assertThat(mediaMapper.isPublic(id)).isFalse();
    media.delete(id);
  }

  @Test
  void gifBoundsAndInvalidAvatarSettingsAreRejected() throws Exception {
    mvc.perform(
            multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "fake.gif", "image/gif", png()))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/admin/media")
                .file(
                    new MockMultipartFile(
                        "file", "many.gif", "image/gif", GifFixtures.animation(201)))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    var original = site.read();
    var values =
        json.convertValue(
            original.get("settings"),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    values.put("avatarAssetId", Long.MAX_VALUE);
    mvc.perform(
            put("/api/admin/site")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("settings", values, "lockVersion", n(original, "lockVersion")))))
        .andExpect(status().isBadRequest());
    values.put("avatarAssetId", null);
    values.put("navTopOpacity", 1.5);
    mvc.perform(
            put("/api/admin/site")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("settings", values, "lockVersion", n(original, "lockVersion")))))
        .andExpect(status().isBadRequest());
  }

  @ParameterizedTest
  @ValueSource(strings = {"work", "tutorial", "deal"})
  void everyTypePreservesItsPublishedSnapshot(String type) {
    String slug = slug();
    var a = contents.create(input(type, slug, "旧标题", 0, completeDetails()));
    long id = n(a, "id");
    var p = contents.publish(id, 0);
    var old = contents.publicDetail(slug);
    var changed =
        contents.save(id, input(type, slug, "新标题", n(p, "lockVersion"), completeDetails()));
    assertThat(contents.publicDetail(slug)).isEqualTo(old);
    contents.publish(id, n(changed, "lockVersion"));
    assertThat(contents.publicDetail(slug).title()).isEqualTo("新标题");
    assertThat(contentMapper.revision(n(p, "revisionId")).get("revision_state"))
        .isEqualTo("frozen");
  }

  @Test
  void chapterKeysAndOrderAreVersionedAndFrozen() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    String slug = slug();
    var chapters =
        List.of(
            Map.of("chapterKey", first, "slug", "one", "title", "一", "bodyMarkdown", "正文一"),
            Map.of("chapterKey", second, "slug", "two", "title", "二", "bodyMarkdown", "正文二"));
    var a =
        contents.create(input("tutorial", slug, "教程", 0, details(Map.of("chapters", chapters))));
    long id = n(a, "id");
    var p = contents.publish(id, 0);
    var reordered = List.of(chapters.get(1), chapters.get(0));
    var changed =
        contents.save(
            id,
            input(
                "tutorial",
                slug,
                "教程二",
                n(p, "lockVersion"),
                details(Map.of("chapters", reordered))));
    assertThat(detailMapper.chapters(n(p, "revisionId")).get(0).get("chapter_key"))
        .isEqualTo(first);
    assertThat(detailMapper.chapters(n(changed, "revisionId")).get(0).get("chapter_key"))
        .isEqualTo(second);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE tutorial_chapter SET title='changed' WHERE tutorial_revision_id=?",
                    n(p, "revisionId")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                contents.save(
                    id,
                    input(
                        "tutorial",
                        slug,
                        "重复章节",
                        n(changed, "lockVersion"),
                        details(Map.of("chapters", List.of(chapters.get(0), chapters.get(0)))))))
        .isInstanceOf(ApiException.class);
    var incomplete =
        contents.create(input("tutorial", slug(), "空教程", 0, ContentDetails.defaults()));
    assertThatThrownBy(() -> contents.publish(n(incomplete, "id"), 0))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void tagsCoverAndMediaStayIsolated() throws Exception {
    long cover = upload();
    String tagName = "hidden-" + slug();
    var tag = new HashMap<String, Object>(Map.of("name", tagName, "slug", slug()));
    taxonomy.createTag(tag);
    String slug = slug();
    var a = contents.create(input("article", slug, "旧标题", 0, ContentDetails.defaults()));
    long id = n(a, "id");
    var p = contents.publish(id, 0);
    var next =
        new ContentInput(
            "article",
            slug,
            "新标题",
            "新摘要",
            "![图](/api/public/media/" + cover + ")",
            n(p, "lockVersion"),
            null,
            List.of(n(tag, "id")),
            cover,
            List.of(),
            null);
    var changed = contents.save(id, next);
    assertThat(contents.publicDetail(slug).coverAssetId()).isNull();
    assertThat(
            contents
                .list(new ContentQuery(true, "", "all", tagName, null, null, null, false, 1, 10))
                .get("total"))
        .isEqualTo(0L);
    assertThat(mediaMapper.isPublic(cover)).isFalse();
    assertThatThrownBy(() -> media.delete(cover)).isInstanceOf(ApiException.class);
    contents.publish(id, n(changed, "lockVersion"));
    assertThat(
            contents
                .list(new ContentQuery(true, "", "all", tagName, null, null, null, false, 1, 10))
                .get("total"))
        .isEqualTo(1L);
    assertThat(
            contents
                .list(
                    new ContentQuery(
                        true, "article", "all", "", n(tag, "id"), null, null, false, 1, 10))
                .get("total"))
        .isEqualTo(1L);
    assertThat(mediaMapper.isPublic(cover)).isTrue();
    mvc.perform(get("/api/public/media/" + cover))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
    assertThatThrownBy(() -> taxonomy.deleteTag(n(tag, "id")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "DELETE FROM revision_tag WHERE revision_id=?", n(changed, "revisionId")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void uploadsValidateContentAndReferenceDeletionAndSettingsConflict() throws Exception {
    mvc.perform(
            multipart("/api/admin/media")
                .file(
                    new MockMultipartFile(
                        "file", "bad.svg", "image/svg+xml", "<svg onload='x'/>".getBytes()))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/admin/media")
                .file(
                    new MockMultipartFile("file", "fake.png", "image/png", "not a png".getBytes()))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/admin/media")
                .file(new MockMultipartFile("file", "wrong.jpg", "image/jpeg", png()))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            multipart("/api/admin/media")
                .file(
                    new MockMultipartFile(
                        "file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                .with(csrf()))
        .andExpect(status().isBadRequest());
    long asset = upload();
    var settings = site.read();
    @SuppressWarnings("unchecked")
    var changed = json.convertValue(settings.get("settings"), Map.class);
    changed.put("backgroundAssetId", asset);
    var saved =
        site.save(json.convertValue(changed, SiteSettings.class), n(settings, "lockVersion"));
    assertThatThrownBy(
            () ->
                site.save(
                    json.convertValue(changed, SiteSettings.class), n(settings, "lockVersion")))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> media.delete(asset)).isInstanceOf(ApiException.class);
    assertThat(mediaMapper.isPublic(asset)).isTrue();
    changed.put("backgroundAssetId", null);
    site.save(json.convertValue(changed, SiteSettings.class), n(saved, "lockVersion"));
    var row = mediaMapper.find(asset);
    media.delete(asset);
    assertThat(java.nio.file.Files.exists(media.path(row))).isFalse();
    changed.put("script", "alert(1)");
    mvc.perform(
            put("/api/admin/site")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of("settings", changed, "lockVersion", n(saved, "lockVersion") + 1))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void blankSlugGetsNextNumericSlug() {
    var first = contents.create(input("article", null, "自动地址一", 0, ContentDetails.defaults()));
    var second = contents.create(input("article", null, "自动地址二", 0, ContentDetails.defaults()));
    String firstSlug = (String) first.get("slug"), secondSlug = (String) second.get("slug");
    assertThat(firstSlug).matches("[0-9]+");
    assertThat(secondSlug).matches("[0-9]+");
    assertThat(Long.parseLong(secondSlug)).isGreaterThan(Long.parseLong(firstSlug));
  }

  @Test
  void taxonomyCreateWithoutSlugAssignsNumericSlugAndPages() throws Exception {
    String name = "分页标签" + slug();
    var created =
        json.readValue(
            mvc.perform(
                    post("/api/admin/taxonomy/tags")
                        .with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Map.class);
    assertThat((String) created.get("slug")).matches("[0-9]+");
    var page =
        json.readValue(
            mvc.perform(get("/api/admin/taxonomy/tags/paged").param("q", name))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Map.class);
    assertThat(((Number) page.get("total")).longValue()).isEqualTo(1L);
    assertThat((List<?>) page.get("items")).hasSize(1);
  }

  @Test
  void markdownImportCreatesDraftArticleWithFrontMatter() throws Exception {
    String md = "---\ntitle: 导入的文章\ntags: [生活, 导入测试]\n---\n# 导入的文章\n\n这是导入的正文第一段。\n";
    var result =
        json.readValue(
            mvc.perform(
                    multipart("/api/admin/contents/import")
                        .file(
                            new MockMultipartFile(
                                "files",
                                "imported.md",
                                "text/markdown",
                                md.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Map.class);
    assertThat(((Number) result.get("created")).intValue()).isEqualTo(1);
    assertThat((List<?>) result.get("failed")).isEmpty();
    @SuppressWarnings("unchecked")
    var item = (Map<String, Object>) ((List<?>) result.get("items")).get(0);
    assertThat(item.get("title")).isEqualTo("导入的文章");
    assertThat((String) item.get("slug")).matches("[0-9]+");
    var stored = contents.detail(((Number) item.get("id")).longValue(), false);
    assertThat(stored.get("publishStatus")).isEqualTo("draft");
    assertThat((String) stored.get("bodyMarkdown")).contains("这是导入的正文第一段");
  }

  void identicalUploadIsDeduplicatedByContentHash() throws Exception {
    byte[] bytes = png();
    var first = media.upload(new MockMultipartFile("file", "dup.png", "image/png", bytes));
    var second = media.upload(new MockMultipartFile("file", "dup-again.png", "image/png", bytes));
    assertThat(n(second, "id")).isEqualTo(n(first, "id"));
  }

  @Test
  void unsafeLinksDatesAndIncompletePublicationAreRejected() {
    assertThatThrownBy(
            () -> contents.create(input("website", slug(), "网站", 0, ContentDetails.defaults())))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                contents.create(
                    input(
                        "deal",
                        slug(),
                        "活动",
                        0,
                        details(
                            Map.of(
                                "startsAt",
                                "2026-10-21T00:00:00Z",
                                "endsAt",
                                "2026-10-20T00:00:00Z")))))
        .isInstanceOf(ApiException.class);
    for (String type : List.of("deal")) {
      var draft = contents.create(input(type, slug(), "未完整", 0, ContentDetails.defaults()));
      assertThatThrownBy(() -> contents.publish(n(draft, "id"), 0))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void dealValidityPrioritizesStartAndExpiryOverManualVerification() {
    var service = new DetailService(null);
    var d = new HashMap<String, Object>();
    d.put("verification", Map.of("result", "valid"));
    d.put("startsAt", Instant.now().plusSeconds(3600).toString());
    assertThat(service.validity(d)).isEqualTo("upcoming");
    d.remove("startsAt");
    d.put("endsAt", Instant.now().minusSeconds(60).toString());
    assertThat(service.validity(d)).isEqualTo("expired");
    d.remove("endsAt");
    assertThat(service.validity(d)).isEqualTo("valid");
    d.put("verification", Map.of("result", "invalid"));
    assertThat(service.validity(d)).isEqualTo("invalid");
  }

  @Test
  void homepageModulesFilterFeedWithoutHidingSectionPages() {
    String title = "home-filter-" + slug();
    var work = contents.create(input("work", slug(), title, 0, completeDetails()));
    contents.publish(n(work, "id"), 0);
    var original = site.read();
    var values =
        json.convertValue(
            original.get("settings"),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    values.put("homeModules", List.of("article"));
    var updated =
        site.save(json.convertValue(values, SiteSettings.class), n(original, "lockVersion"));
    try {
      assertThat(
              contents
                  .list(new ContentQuery(true, "", "all", title, null, null, null, true, 1, 10))
                  .get("total"))
          .isEqualTo(0L);
      assertThat(
              contents
                  .list(
                      new ContentQuery(true, "work", "all", title, null, null, null, false, 1, 10))
                  .get("total"))
          .isEqualTo(1L);
    } finally {
      site.save((SiteSettings) original.get("settings"), n(updated, "lockVersion"));
    }
  }

  @Test
  void verificationIsBoundToPublishedRevisionAndRespectsDates() throws Exception {
    String slug = slug();
    var a = contents.create(input("deal", slug, "活动", 0, completeDetails()));
    long id = n(a, "id");
    var p = contents.publish(id, 0);
    mvc.perform(
            post("/api/admin/contents/" + id + "/verify")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "revisionId",
                            n(p, "revisionId"),
                            "result",
                            "valid",
                            "evidenceUrl",
                            "https://example.com",
                            "note",
                            "已人工确认"))))
        .andExpect(status().isOk());
    assertThat(contents.publicDetail(slug).details().get("validity")).isEqualTo("valid");
    var changed =
        contents.save(id, input("deal", slug, "新活动", n(p, "lockVersion"), completeDetails()));
    contents.publish(id, n(changed, "lockVersion"));
    assertThat(contents.publicDetail(slug).details().get("validity")).isEqualTo("unknown");
    mvc.perform(
            post("/api/admin/contents/" + id + "/verify")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "revisionId",
                            n(p, "revisionId"),
                            "result",
                            "valid",
                            "evidenceUrl",
                            "",
                            "note",
                            ""))))
        .andExpect(status().isConflict());
  }
}
