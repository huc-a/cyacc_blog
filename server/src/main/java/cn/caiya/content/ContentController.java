package cn.caiya.content;

import cn.caiya.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContentController {
  private final ContentService service;

  public ContentController(ContentService service) {
    this.service = service;
  }

  public record Version(@NotNull @PositiveOrZero Long lockVersion) {}

  @GetMapping("/api/public/contents")
  public PublicViews.Page<PublicViews.ContentItem> publicList(
      @RequestParam(defaultValue = "") String type,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Long tagId,
      @RequestParam(required = false) java.time.LocalDate date,
      @RequestParam(defaultValue = "false") boolean home,
      @RequestParam(required = false) Long categoryId) {
    return service.publicList(
        new ContentQuery(true, type, "all", q, tagId, categoryId, date, home, page, size));
  }

  @GetMapping("/api/public/contents/{slug}")
  public PublicViews.ContentDetail publicDetail(@PathVariable String slug) {
    return service.publicDetail(slug);
  }

  @GetMapping("/api/admin/contents")
  public Map<String, Object> adminList(
      @RequestParam(defaultValue = "") String type,
      @RequestParam(defaultValue = "all") String status,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Long tagId,
      @RequestParam(required = false) Long categoryId) {
    return service.list(
        new ContentQuery(false, type, status, q, tagId, categoryId, null, false, page, size));
  }

  @GetMapping("/api/admin/contents/{id}")
  public Map<String, Object> detail(@PathVariable long id) {
    return service.detail(id, false);
  }

  @GetMapping("/api/admin/contents/{id}/revisions")
  public List<Map<String, Object>> history(@PathVariable long id) {
    return service.history(id);
  }

  @PostMapping("/api/admin/contents")
  public Map<String, Object> create(@Valid @RequestBody ContentInput input) {
    return service.create(input);
  }

  /**
   * Batch-imports Markdown files as draft articles. Each file is imported in its own transaction;
   * per-file failures are reported, not fatal.
   */
  @PostMapping("/api/admin/contents/import")
  public Map<String, Object> importMarkdown(
      @RequestParam("files") List<org.springframework.web.multipart.MultipartFile> files) {
    if (files.isEmpty() || files.size() > 20) throw new ApiException(400, "一次导入 1–20 个文件");
    var items = new ArrayList<Map<String, Object>>();
    var failed = new ArrayList<Map<String, Object>>();
    for (var file : files) {
      String name = file.getOriginalFilename() == null ? "未命名" : file.getOriginalFilename();
      try {
        if (!name.toLowerCase().matches(".*\\.(md|markdown)$"))
          throw new ApiException(400, "只支持 .md 文件");
        var created = service.importMarkdown(name, file.getBytes());
        items.add(
            Map.of(
                "id", created.get("id"),
                "title", created.get("title"),
                "slug", created.get("slug")));
      } catch (ApiException e) {
        failed.add(Map.of("file", name, "reason", e.getMessage()));
      } catch (java.io.IOException e) {
        failed.add(Map.of("file", name, "reason", "文件读取失败"));
      }
    }
    return Map.of("created", items.size(), "items", items, "failed", failed);
  }

  @PutMapping("/api/admin/contents/{id}")
  public Map<String, Object> save(@PathVariable long id, @Valid @RequestBody ContentInput input) {
    return service.save(id, input);
  }

  @PostMapping("/api/admin/contents/{id}/publish")
  public Map<String, Object> publish(@PathVariable long id, @Valid @RequestBody Version version) {
    return service.publish(id, version.lockVersion());
  }

  @PostMapping("/api/admin/contents/{id}/{action:offline|trash|restore}")
  public Map<String, Object> transition(
      @PathVariable long id, @PathVariable String action, @Valid @RequestBody Version version) {
    return service.transition(id, version.lockVersion(), action);
  }
}
