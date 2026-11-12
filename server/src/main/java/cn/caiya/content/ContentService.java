package cn.caiya.content;

import static cn.caiya.common.Views.number;

import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {
  private final ContentMapper mapper;
  private final AuditService audit;
  private final DetailService details;
  private final cn.caiya.site.SiteService site;
  private final TaxonomyMapper taxonomyMapper;

  public ContentService(
      ContentMapper mapper,
      AuditService audit,
      DetailService details,
      cn.caiya.site.SiteService site,
      TaxonomyMapper taxonomyMapper) {
    this.mapper = mapper;
    this.audit = audit;
    this.details = details;
    this.site = site;
    this.taxonomyMapper = taxonomyMapper;
  }

  public static void type(String type) {
    if (!Set.of("article", "work", "tutorial", "deal").contains(type))
      throw new ApiException(400, "内容类型无效");
  }

  /** Admin/internal list: full row maps, including private bookkeeping fields. */
  public Map<String, Object> list(ContentQuery input) {
    var query = mapperQuery(input);
    var rows = mapper.list(query).stream().map(Views::camel).toList();
    return Map.of(
        "items", rows, "total", mapper.count(query), "page", input.page(), "size", input.size());
  }

  /** Public list: whitelisted DTO items only (see PublicViews). */
  public PublicViews.Page<PublicViews.ContentItem> publicList(ContentQuery input) {
    var query = mapperQuery(input);
    var rows = mapper.list(query).stream().map(Views::camel).map(this::publicItem).toList();
    return new PublicViews.Page<>(rows, mapper.count(query), input.page(), input.size());
  }

  private Map<String, Object> mapperQuery(ContentQuery input) {
    if (!input.type().isEmpty()) type(input.type());
    if (!Set.of("all", "draft", "published", "offline", "trash").contains(input.status())
        || input.q().length() > 200
        || input.page() < 1
        || input.page() > 100000
        || input.size() < 1
        || input.size() > 50) throw new ApiException(400, "筛选或分页参数无效");
    var query = new HashMap<String, Object>();
    query.put("publicOnly", input.publicOnly());
    query.put("type", input.type());
    query.put("status", input.status());
    query.put("q", input.q());
    query.put(
        "search", "%" + input.q().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    query.put("size", input.size());
    query.put("offset", (input.page() - 1) * input.size());
    query.put("tagId", input.tagId());
    query.put("date", input.date());
    query.put("categoryId", input.categoryId());
    query.put("home", input.home());
    if (input.home()) {
      // Read the configured home modules once and pass them as a plain
      // IN list, instead of parsing the site-setting JSONB per row.
      var settings = (cn.caiya.site.SiteSettings) site.read().get("settings");
      query.put("homeTypes", settings.homeModules());
    }
    return query;
  }

  private PublicViews.ContentItem publicItem(Map<String, Object> row) {
    @SuppressWarnings("unchecked")
    var tagNames = (List<String>) row.getOrDefault("tagNames", List.of());
    return new PublicViews.ContentItem(
        number(row, "id"),
        (String) row.get("contentType"),
        (String) row.get("slug"),
        (String) row.get("publishStatus"),
        (String) row.get("firstPublishedAt"),
        (String) row.get("lastPublishedAt"),
        number(row, "revisionId"),
        number(row, "revisionNo"),
        (String) row.get("title"),
        (String) row.get("summary"),
        row.get("coverAssetId") == null ? null : number(row, "coverAssetId"),
        tagNames,
        row.get("categoryId") == null ? null : number(row, "categoryId"),
        (String) row.get("categoryName"));
  }

  private PublicViews.Neighbor neighbor(Map<String, Object> row) {
    if (row == null) return null;
    var camel = Views.camel(row);
    return new PublicViews.Neighbor(
        number(camel, "id"),
        (String) camel.get("contentType"),
        (String) camel.get("slug"),
        (String) camel.get("title"));
  }

  @SuppressWarnings("unchecked")
  public PublicViews.ContentDetail publicDetail(String slug) {
    var id = mapper.publicId(slug);
    if (id == null) throw new ApiException(404, "内容不存在或尚未发布");
    var out = detail(id, true);
    var navigation =
        new PublicViews.Navigation(
            neighbor(mapper.neighbor(id, false)), neighbor(mapper.neighbor(id, true)));
    return new PublicViews.ContentDetail(
        number(out, "id"),
        (String) out.get("contentType"),
        (String) out.get("slug"),
        (String) out.get("publishStatus"),
        (String) out.get("createdAt"),
        (String) out.get("firstPublishedAt"),
        (String) out.get("lastPublishedAt"),
        number(out, "revisionId"),
        number(out, "revisionNo"),
        (String) out.get("title"),
        (String) out.get("summary"),
        (String) out.get("bodyMarkdown"),
        out.get("coverAssetId") == null ? null : number(out, "coverAssetId"),
        out.get("categoryId") == null ? null : number(out, "categoryId"),
        (Map<String, Object>) out.get("details"),
        (List<Map<String, Object>>) out.get("tags"),
        (List<Long>) out.get("tagIds"),
        (List<Long>) out.get("galleryAssetIds"),
        navigation);
  }

  private void removePrivateFields(Map<String, Object> row) {
    for (String key : List.of("lockVersion", "revisionState", "deletedAt", "updatedAt"))
      row.remove(key);
  }

  public Map<String, Object> detail(long id, boolean publicOnly) {
    var c = mapper.identity(id);
    if (c == null) throw new ApiException(404, "内容不存在");
    if (publicOnly && (c.get("deleted_at") != null || !"published".equals(c.get("publish_status"))))
      throw new ApiException(404, "内容不存在或尚未发布");
    Object revisionId =
        publicOnly
            ? c.get("published_revision_id")
            : c.getOrDefault("working_revision_id", c.get("published_revision_id"));
    if (revisionId == null) throw new ApiException(404, "内容版本不存在");
    long rid = ((Number) revisionId).longValue();
    var r = mapper.revision(rid);
    var out = Views.camel(c);
    out.remove("workingRevisionId");
    out.remove("publishedRevisionId");
    out.put("revisionId", rid);
    out.put("revisionNo", r.get("revision_no"));
    out.put("revisionState", r.get("revision_state"));
    out.put("title", r.get("title"));
    out.put("summary", r.get("summary"));
    out.put("coverAssetId", r.get("cover_asset_id"));
    out.put("categoryId", r.get("category_id"));
    out.putAll(details.read(rid, c.get("content_type").toString()));
    if (publicOnly) removePrivateFields(out);
    return out;
  }

  @Transactional
  public Map<String, Object> create(ContentInput input) {
    type(input.type());
    details.validate(input);
    boolean autoSlug = input.slug() == null || input.slug().isBlank();
    var c = new HashMap<String, Object>();
    c.put("type", input.type());
    // A throwaway unique slug satisfies NOT NULL/UNIQUE until the row id
    // exists; the visible slug is then assigned numerically below.
    c.put("slug", autoSlug ? "auto-" + java.util.UUID.randomUUID() : input.slug());
    mapper.insert(c);
    long id = number(c, "id");
    if (autoSlug) mapper.updateSlug(id, nextNumericSlug(id));
    long rid = makeRevision(id, input, 1);
    mapper.working(id, rid);
    audit.record("content.create", input.type(), id);
    return detail(id, false);
  }

  /** Next free numeric slug, starting from the content's own id and counting up. */
  private String nextNumericSlug(long id) {
    long candidate = id;
    while (mapper.slugCount(Long.toString(candidate), id) > 0) candidate++;
    return Long.toString(candidate);
  }

  /**
   * Imports one Markdown file as a draft article. Supports a small YAML front-matter subset (title,
   * tags); the title falls back to the first level-1 heading, then to the file name. Unknown tag
   * names are created.
   */
  @Transactional
  public Map<String, Object> importMarkdown(String filename, byte[] bytes) {
    if (bytes.length > 1024 * 1024) throw new ApiException(400, "文件超过 1 MB");
    String text =
        new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("^\uFEFF", "");
    String title = null;
    List<String> tagNames = List.of();
    String body = text;
    if (text.startsWith("---")) {
      int end = text.indexOf("\n---", 3);
      if (end > 0) {
        String front = text.substring(3, end);
        body = text.substring(end + 4).stripLeading();
        var parsedTags = new ArrayList<String>();
        for (String line : front.split("\n")) {
          String trimmed = line.trim();
          if (trimmed.startsWith("title:")) title = unquote(trimmed.substring(6).trim());
          if (trimmed.startsWith("tags:")) {
            String raw = trimmed.substring(5).trim().replaceAll("^\\[|\\]$", "");
            for (String name : raw.split(",")) {
              String n = unquote(name.trim());
              if (!n.isEmpty() && n.length() <= 60) parsedTags.add(n);
            }
          }
        }
        tagNames = parsedTags;
      }
    }
    if (title == null || title.isBlank()) {
      for (String line : body.split("\n")) {
        if (line.startsWith("# ")) {
          title = line.substring(2).trim();
          break;
        }
      }
    }
    if (title == null || title.isBlank()) {
      title = filename.replaceFirst("(?i)\\.(md|markdown)$", "").trim();
    }
    if (title.isBlank() || title.length() > 200) throw new ApiException(400, "无法确定文章标题或标题过长");
    String summary = "";
    for (String line : body.split("\n")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
        summary = trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
        break;
      }
    }
    var tagIds = new ArrayList<Long>();
    for (String name : tagNames) tagIds.add(ensureTag(name));
    var input =
        new ContentInput(
            "article",
            null,
            title,
            summary,
            body,
            0L,
            "Markdown 导入",
            tagIds,
            null,
            null,
            List.of(),
            ContentDetails.defaults());
    var created = create(input);
    audit.record("content.import", "article", number(created, "id"));
    return created;
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  /** Finds a tag by exact name or creates it with an automatic numeric slug. */
  private long ensureTag(String name) {
    var existing = taxonomyMapper.findTagByName(name);
    if (existing != null) return Views.number(existing, "id");
    var v = new HashMap<String, Object>();
    v.put("name", name);
    v.put("slug", "auto-" + java.util.UUID.randomUUID());
    taxonomyMapper.createTag(v);
    long id = Views.number(v, "id");
    long candidate = id;
    while (taxonomyMapper.tagSlugCount(Long.toString(candidate), id) > 0) candidate++;
    var update = new HashMap<String, Object>();
    update.put("id", id);
    update.put("slug", Long.toString(candidate));
    taxonomyMapper.updateTagSlug(update);
    return id;
  }

  private long makeRevision(long id, ContentInput input, int no) {
    var r = revisionValues(input);
    r.put("contentId", id);
    r.put("type", input.type());
    r.put("revisionNo", no);
    mapper.insertRevision(r);
    long rid = number(r, "id");
    details.save(rid, input);
    return rid;
  }

  private Map<String, Object> revisionValues(ContentInput input) {
    var r = new HashMap<String, Object>();
    r.put("title", input.title().trim());
    r.put("summary", input.summary());
    r.put("changeNote", input.changeNote());
    r.put("categoryId", input.categoryId());
    return r;
  }

  private Map<String, Object> locked(long id, long version, boolean allowTrash) {
    var c = mapper.lock(id);
    if (c == null) throw new ApiException(404, "内容不存在");
    if (number(c, "lock_version") != version) throw ApiException.conflict();
    if (!allowTrash && c.get("deleted_at") != null) throw new ApiException(409, "请先从回收站恢复内容");
    return c;
  }

  @Transactional
  public Map<String, Object> save(long id, ContentInput input) {
    var c = locked(id, input.lockVersion(), false);
    if (!input.type().equals(c.get("content_type")) || !input.slug().equals(c.get("slug")))
      throw new ApiException(400, "内容类型和地址创建后不能修改");
    if (c.get("working_revision_id") == null) {
      long rid = makeRevision(id, input, mapper.nextRevision(id));
      mapper.working(id, rid);
    } else {
      long rid = number(c, "working_revision_id");
      var r = revisionValues(input);
      r.put("id", rid);
      if (mapper.updateRevision(r) != 1) throw ApiException.conflict();
      details.save(rid, input);
    }
    if (mapper.touch(id, input.lockVersion()) != 1) throw ApiException.conflict();
    audit.record("content.save", input.type(), id);
    return detail(id, false);
  }

  @Transactional
  public Map<String, Object> publish(long id, long version) {
    var c = locked(id, version, false);
    Object working = c.get("working_revision_id");
    if (working == null) {
      if (c.get("published_revision_id") == null || "published".equals(c.get("publish_status")))
        throw new ApiException(400, "没有待发布的修改");
      mapper.publish(id, number(c, "published_revision_id"));
    } else {
      long rid = ((Number) working).longValue();
      var r = mapper.revision(rid);
      details.validatePublish(rid, c.get("content_type").toString(), r.get("title").toString());
      if (mapper.freeze(rid) != 1) throw ApiException.conflict();
      mapper.publish(id, rid);
    }
    if (mapper.touch(id, version) != 1) throw ApiException.conflict();
    audit.record("content.publish", (String) c.get("content_type"), id);
    return detail(id, false);
  }

  @Transactional
  public Map<String, Object> transition(long id, long version, String action) {
    var c = locked(id, version, action.equals("restore"));
    switch (action) {
      case "offline" -> {
        if (c.get("published_revision_id") == null) throw new ApiException(400, "草稿无需下线");
        mapper.offline(id);
      }
      case "trash" -> mapper.trash(id);
      case "restore" -> {
        if (c.get("deleted_at") == null) throw new ApiException(400, "内容不在回收站");
        mapper.restore(id);
      }
      default -> throw new ApiException(400, "操作无效");
    }
    if (mapper.touch(id, version) != 1) throw ApiException.conflict();
    audit.record("content." + action, (String) c.get("content_type"), id);
    return detail(id, false);
  }

  public List<Map<String, Object>> history(long id) {
    if (mapper.identity(id) == null) throw new ApiException(404, "内容不存在");
    return mapper.history(id).stream().map(Views::camel).toList();
  }
}
