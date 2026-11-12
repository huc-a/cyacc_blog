package cn.caiya.content;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DetailMapper {
  Map<String, Object> read(@Param("id") long id);

  void save(@Param("id") long id, @Param("input") ContentInput input);

  @Update(
      "UPDATE content_revision SET"
          + " cover_asset_id=#{input.coverAssetId},category_id=#{input.categoryId} WHERE id=#{id}"
          + " AND revision_state='editing'")
  void metadata(@Param("id") long id, @Param("input") ContentInput input);

  @Delete("DELETE FROM revision_tag WHERE revision_id=#{id}")
  void clearTags(long id);

  @Insert("INSERT INTO revision_tag(revision_id,tag_id) VALUES(#{id},#{tagId})")
  void tag(@Param("id") long id, @Param("tagId") long tagId);

  @Select(
      "SELECT t.id,t.name,t.slug FROM tag t JOIN revision_tag rt ON t.id=rt.tag_id WHERE"
          + " rt.revision_id=#{id} ORDER BY t.name")
  List<Map<String, Object>> tags(long id);

  @Select("SELECT category_id FROM content_revision WHERE id=#{id}")
  Long categoryId(long id);

  @Select("SELECT EXISTS(SELECT 1 FROM category WHERE id=#{id})")
  boolean categoryExists(long id);

  @Delete("DELETE FROM revision_media WHERE revision_id=#{id}")
  void clearMedia(long id);

  @Insert(
      "INSERT INTO revision_media(revision_id,media_asset_id,usage_type,sort_order)"
          + " VALUES(#{id},#{asset},#{usage},#{sort})")
  void media(
      @Param("id") long id,
      @Param("asset") long asset,
      @Param("usage") String usage,
      @Param("sort") int sort);

  @Select(
      "SELECT media_asset_id FROM revision_media WHERE revision_id=#{id} AND usage_type='gallery'"
          + " ORDER BY sort_order,id")
  List<Long> gallery(long id);
}
