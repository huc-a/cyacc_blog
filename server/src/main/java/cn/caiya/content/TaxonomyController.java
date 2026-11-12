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

  public record Input(
      @NotBlank @Size(max = 60) String name,
      @NotNull @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug) {}

  public record CategoryInput(
      @NotBlank @Size(max = 60) String name,
      @NotNull @Size(max = 80) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
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

  @PostMapping("/api/admin/taxonomy/tags")
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody Input input) {
    var v = values(input);
    mapper.createTag(v);
    audit.record("taxonomy.create", "tags", Views.number(v, "id"));
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
    mapper.createCategory(v);
    audit.record("taxonomy.category.create", "category", Views.number(v, "id"));
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
    value.put("slug", input.slug());
    return value;
  }

  private Map<String, Object> categoryValues(CategoryInput input) {
    var value = new HashMap<String, Object>();
    value.put("name", input.name().trim());
    value.put("slug", input.slug());
    value.put("sortOrder", input.sortOrder());
    return value;
  }
}
