package cn.caiya.content;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaxonomyMapper {
  @Select(
      "SELECT t.*, (SELECT count(*) FROM revision_tag rt JOIN content c ON"
          + " c.published_revision_id=rt.revision_id WHERE rt.tag_id=t.id AND"
          + " c.publish_status='published' AND c.deleted_at IS NULL) AS count FROM tag t ORDER BY"
          + " name,id")
  List<Map<String, Object>> tags();

  @Insert("INSERT INTO tag(name,slug) VALUES(#{name},#{slug})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void createTag(Map<String, Object> v);

  @Select("SELECT * FROM tag WHERE name=#{name} ORDER BY id LIMIT 1")
  Map<String, Object> findTagByName(String name);

  @Select("SELECT count(*) FROM tag WHERE slug=#{slug} AND id<>#{excludeId}")
  long tagSlugCount(@Param("slug") String slug, @Param("excludeId") long excludeId);

  @Update("UPDATE tag SET slug=#{slug} WHERE id=#{id}")
  int updateTagSlug(Map<String, Object> v);

  @Select(
      "SELECT t.*, (SELECT count(*) FROM revision_tag rt JOIN content c ON"
          + " c.published_revision_id=rt.revision_id WHERE rt.tag_id=t.id AND"
          + " c.publish_status='published' AND c.deleted_at IS NULL) AS count FROM tag t WHERE"
          + " (#{q}='' OR t.name ILIKE #{pattern} ESCAPE '!' OR t.slug ILIKE #{pattern} ESCAPE '!')"
          + " ORDER BY name,id LIMIT #{size} OFFSET #{offset}")
  List<Map<String, Object>> tagsPaged(
      @Param("q") String q,
      @Param("pattern") String pattern,
      @Param("size") int size,
      @Param("offset") int offset);

  @Select(
      "SELECT count(*) FROM tag t WHERE (#{q}='' OR t.name ILIKE #{pattern} ESCAPE '!' OR t.slug"
          + " ILIKE #{pattern} ESCAPE '!')")
  long tagsCount(@Param("q") String q, @Param("pattern") String pattern);

  @Update("UPDATE tag SET name=#{name} WHERE id=#{id}")
  int updateTag(Map<String, Object> v);

  @Delete("DELETE FROM tag WHERE id=#{id}")
  int deleteTag(long id);

  @Select(
      "SELECT c.*, (SELECT count(*) FROM content_revision r JOIN content x ON"
          + " x.published_revision_id=r.id WHERE r.category_id=c.id AND"
          + " x.publish_status='published' AND x.deleted_at IS NULL) AS count FROM category c ORDER"
          + " BY sort_order,name,id")
  List<Map<String, Object>> categories();

  @Select("SELECT id FROM category WHERE id=#{id}")
  Long category(long id);

  @Select("SELECT count(*) FROM content_revision WHERE category_id=#{id}")
  long categoryReferences(long id);

  @Insert("INSERT INTO category(name,slug,sort_order) VALUES(#{name},#{slug},#{sortOrder})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void createCategory(Map<String, Object> v);

  @Select("SELECT count(*) FROM category WHERE slug=#{slug} AND id<>#{excludeId}")
  long categorySlugCount(@Param("slug") String slug, @Param("excludeId") long excludeId);

  @Update("UPDATE category SET slug=#{slug} WHERE id=#{id}")
  int updateCategorySlug(Map<String, Object> v);

  @Select(
      "SELECT c.*, (SELECT count(*) FROM content_revision r JOIN content x ON"
          + " x.published_revision_id=r.id WHERE r.category_id=c.id AND"
          + " x.publish_status='published' AND x.deleted_at IS NULL) AS count FROM category c WHERE"
          + " (#{q}='' OR c.name ILIKE #{pattern} ESCAPE '!' OR c.slug ILIKE #{pattern} ESCAPE '!')"
          + " ORDER BY sort_order,name,id LIMIT #{size} OFFSET #{offset}")
  List<Map<String, Object>> categoriesPaged(
      @Param("q") String q,
      @Param("pattern") String pattern,
      @Param("size") int size,
      @Param("offset") int offset);

  @Select(
      "SELECT count(*) FROM category c WHERE (#{q}='' OR c.name ILIKE #{pattern} ESCAPE '!' OR"
          + " c.slug ILIKE #{pattern} ESCAPE '!')")
  long categoriesCount(@Param("q") String q, @Param("pattern") String pattern);

  @Update(
      "UPDATE category SET name=#{name},sort_order=#{sortOrder},updated_at=now() WHERE id=#{id}")
  int updateCategory(Map<String, Object> v);

  @Delete(
      "DELETE FROM category c WHERE c.id=#{id} AND NOT EXISTS (SELECT 1 FROM content_revision r"
          + " WHERE r.category_id=c.id)")
  int deleteCategory(long id);
}
