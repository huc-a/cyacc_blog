package cn.caiya.content;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TaxonomyMapper {
    @Select("SELECT k.*, (SELECT count(*) FROM content c JOIN content_revision r ON r.id=c.published_revision_id WHERE r.category_id=k.id AND c.publish_status='published' AND c.deleted_at IS NULL) AS count FROM category k ORDER BY sort_order,id") List<Map<String,Object>> categories();
    @Select("SELECT t.*, (SELECT count(*) FROM revision_tag rt JOIN content c ON c.published_revision_id=rt.revision_id WHERE rt.tag_id=t.id AND c.publish_status='published' AND c.deleted_at IS NULL) AS count FROM tag t ORDER BY name,id") List<Map<String,Object>> tags();
    @Insert("INSERT INTO category(content_type,name,slug,sort_order) VALUES(#{type},#{name},#{slug},#{sortOrder})") @Options(useGeneratedKeys=true,keyProperty="id") void createCategory(Map<String,Object> v);
    @Insert("INSERT INTO tag(name,slug) VALUES(#{name},#{slug})") @Options(useGeneratedKeys=true,keyProperty="id") void createTag(Map<String,Object> v);
    @Update("UPDATE category SET name=#{name},sort_order=#{sortOrder},updated_at=now() WHERE id=#{id}") int updateCategory(Map<String,Object> v);
    @Update("UPDATE tag SET name=#{name} WHERE id=#{id}") int updateTag(Map<String,Object> v);
    @Delete("DELETE FROM category WHERE id=#{id}") int deleteCategory(long id);
    @Delete("DELETE FROM tag WHERE id=#{id}") int deleteTag(long id);
}
