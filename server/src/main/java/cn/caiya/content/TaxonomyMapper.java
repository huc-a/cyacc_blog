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

  @Update(
      "UPDATE category SET name=#{name},sort_order=#{sortOrder},updated_at=now() WHERE id=#{id}")
  int updateCategory(Map<String, Object> v);

  @Delete(
      "DELETE FROM category c WHERE c.id=#{id} AND NOT EXISTS (SELECT 1 FROM content_revision r"
          + " WHERE r.category_id=c.id)")
  int deleteCategory(long id);
}
