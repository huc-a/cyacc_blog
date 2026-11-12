package cn.caiya.content;

import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class TaxonomyController {
  private final TaxonomyMapper mapper;
  private final AuditService audit;

  public TaxonomyController(TaxonomyMapper mapper, AuditService audit) {
    this.mapper = mapper;
    this.audit = audit;
  }

  // Slug is optional on create: when omitted, the next numeric slug is
  // assigned automatically (see assignSlug). It never changes afterwards.
  public record Input(
      @NotBlank @Size(max = 60) String name,
      @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug) {}

  public record CategoryInput(
      @NotBlank @Size(max = 60) String name,
      @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
      @Min(0) @Max(10000) int sortOrder) {}

  // Typed views with camelCase fields; the mapper rows previously leaked
  // raw snake_case column names (created_at, sort_order) to the API.
  public record TagView(long id, String name, String slug, long count) {}

  public record CategoryView(long id, String name, String slug, int sortOrder, long count) {}

  public record TaxonomyView(List<TagView> tags, List<CategoryView> categories) {}

  @GetMapping({"/api/public/taxonomy", "/api/admin/taxonomy"})
  public TaxonomyView list() {
    var tags =
        mapper.tags().stream()
            .map(
                row ->
                    new TagView(
                        Views.number(row, "id"),
                        (String) row.get("name"),
                        (String) row.get("slug"),
                        Views.number(row, "count")))
            .toList();
    var categories =
        mapper.categories().stream()
            .map(
                row ->
                    new CategoryView(
                        Views.number(row, "id"),
                        (String) row.get("name"),
                        (String) row.get("slug"),
                        (int) Views.number(row, "sort_order"),
                        Views.number(row, "count")))
            .toList();
    return new TaxonomyView(tags, categories);
  }

  @GetMapping("/api/admin/taxonomy/tags/paged")
  public PublicViews.Page<TagView> tagsPaged(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String q) {
    checkPage(page, size, q);
    String pattern = likePattern(q);
    var items =
        mapper.tagsPaged(q, pattern, size, (page - 1) * size).stream()
            .map(
                row ->
                    new TagView(
                        Views.number(row, "id"),
                        (String) row.get("name"),
                        (String) row.get("slug"),
                        Views.number(row, "count")))
            .toList();
    return new PublicViews.Page<>(items, mapper.tagsCount(q, pattern), page, size);
  }

  @GetMapping("/api/admin/taxonomy/categories/paged")
  public PublicViews.Page<CategoryView> categoriesPaged(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "") String q) {
    checkPage(page, size, q);
    String pattern = likePattern(q);
    var items =
        mapper.categoriesPaged(q, pattern, size, (page - 1) * size).stream()
            .map(
                row ->
                    new CategoryView(
                        Views.number(row, "id"),
                        (String) row.get("name"),
                        (String) row.get("slug"),
                        (int) Views.number(row, "sort_order"),
                        Views.number(row, "count")))
            .toList();
    return new PublicViews.Page<>(items, mapper.categoriesCount(q, pattern), page, size);
  }

  private static void checkPage(int page, int size, String q) {
    if (page < 1 || size < 1 || size > 100 || q.length() > 100)
      throw new ApiException(400, "分页或搜索参数无效");
  }

  private static String likePattern(String q) {
    return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  @PostMapping("/api/admin/taxonomy/tags")
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody Input input) {
    var v = values(input);
    boolean autoSlug = v.get("slug") == null;
    if (autoSlug) v.put("slug", "auto-" + java.util.UUID.randomUUID());
    mapper.createTag(v);
    long id = Views.number(v, "id");
    if (autoSlug) {
      long candidate = id;
      while (mapper.tagSlugCount(Long.toString(candidate), id) > 0) candidate++;
      v.put("slug", Long.toString(candidate));
      mapper.updateTagSlug(v);
    }
    audit.record("taxonomy.create", "tags", id);
    return v;
  }

  @PutMapping("/api/admin/taxonomy/tags/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable long id, @Valid @RequestBody Input input) {
    var v = values(input);
    v.put("id", id);
    if (mapper.updateTag(v) != 1) throw new ApiException(404, "Tag not found");
    audit.record("taxonomy.update", "tags", id);
    return v;
  }

  @DeleteMapping("/api/admin/taxonomy/tags/{id}")
  @Transactional
  public void delete(@PathVariable long id) {
    if (mapper.deleteTag(id) != 1) throw new ApiException(404, "Tag not found");
    audit.record("taxonomy.delete", "tags", id);
  }

  @GetMapping("/api/admin/taxonomy/categories")
  public Map<String, Object> categories() {
    return Map.of("categories", mapper.categories());
  }

  @PostMapping("/api/admin/taxonomy/categories")
  @Transactional
  public Map<String, Object> createCategory(@Valid @RequestBody CategoryInput input) {
    var v = categoryValues(input);
    boolean autoSlug = v.get("slug") == null;
    if (autoSlug) v.put("slug", "auto-" + java.util.UUID.randomUUID());
    mapper.createCategory(v);
    long id = Views.number(v, "id");
    if (autoSlug) {
      long candidate = id;
      while (mapper.categorySlugCount(Long.toString(candidate), id) > 0) candidate++;
      v.put("slug", Long.toString(candidate));
      mapper.updateCategorySlug(v);
    }
    audit.record("taxonomy.category.create", "category", id);
    return v;
  }

  @PutMapping("/api/admin/taxonomy/categories/{id}")
  @Transactional
  public Map<String, Object> updateCategory(
      @PathVariable long id, @Valid @RequestBody CategoryInput input) {
    var v = categoryValues(input);
    v.put("id", id);
    if (mapper.updateCategory(v) != 1) throw new ApiException(404, "分类不存在");
    audit.record("taxonomy.category.update", "category", id);
    return v;
  }

  @DeleteMapping("/api/admin/taxonomy/categories/{id}")
  @Transactional
  public void deleteCategory(@PathVariable long id) {
    if (mapper.category(id) == null) throw new ApiException(404, "分类不存在");
    if (mapper.categoryReferences(id) > 0) throw new ApiException(409, "该分类仍被内容引用，请先调整内容分类");
    if (mapper.deleteCategory(id) != 1) throw new ApiException(409, "该分类仍被内容引用，请先调整内容分类");
    audit.record("taxonomy.category.delete", "category", id);
  }

  private Map<String, Object> values(Input input) {
    var value = new HashMap<String, Object>();
    value.put("name", input.name().trim());
    value.put("slug", input.slug() == null || input.slug().isBlank() ? null : input.slug());
    return value;
  }

  private Map<String, Object> categoryValues(CategoryInput input) {
    var value = new HashMap<String, Object>();
    value.put("name", input.name().trim());
    value.put("slug", input.slug() == null || input.slug().isBlank() ? null : input.slug());
    value.put("sortOrder", input.sortOrder());
    return value;
  }
}
