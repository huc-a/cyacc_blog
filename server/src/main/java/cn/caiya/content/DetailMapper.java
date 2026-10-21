package cn.caiya.content;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DetailMapper {
    Map<String,Object> read(@Param("id") long id,@Param("type") String type);
    void save(@Param("id") long id,@Param("input") ContentInput input);
    @Update("UPDATE content_revision SET category_id=#{input.categoryId},cover_asset_id=#{input.coverAssetId} WHERE id=#{id} AND revision_state='editing'")
    void metadata(@Param("id") long id,@Param("input") ContentInput input);
    @Delete("DELETE FROM revision_tag WHERE revision_id=#{id}") void clearTags(long id);
    @Insert("INSERT INTO revision_tag(revision_id,tag_id) VALUES(#{id},#{tagId})") void tag(@Param("id") long id,@Param("tagId") long tagId);
    @Select("SELECT t.id,t.name,t.slug FROM tag t JOIN revision_tag rt ON t.id=rt.tag_id WHERE rt.revision_id=#{id} ORDER BY t.name") List<Map<String,Object>> tags(long id);
    @Select("SELECT id,name,slug FROM category WHERE id=#{id}") Map<String,Object> category(long id);
    @Select("SELECT chapter_key,slug,title,body_markdown FROM tutorial_chapter WHERE tutorial_revision_id=#{id} ORDER BY sort_order,id") List<Map<String,Object>> chapters(long id);
    @Delete("DELETE FROM tutorial_chapter WHERE tutorial_revision_id=#{id}") void clearChapters(long id);
    @Insert("INSERT INTO tutorial_chapter(tutorial_revision_id,chapter_key,slug,title,body_markdown,sort_order) VALUES(#{id},#{chapter.chapterKey},#{chapter.slug},#{chapter.title},#{chapter.bodyMarkdown},#{sort})")
    void chapter(@Param("id") long id,@Param("chapter") ContentDetails.Chapter chapter,@Param("sort") int sort);
    @Delete("DELETE FROM revision_media WHERE revision_id=#{id}") void clearMedia(long id);
    @Insert("INSERT INTO revision_media(revision_id,media_asset_id,usage_type,sort_order) VALUES(#{id},#{asset},#{usage},#{sort})")
    void media(@Param("id") long id,@Param("asset") long asset,@Param("usage") String usage,@Param("sort") int sort);
    @Select("SELECT media_asset_id FROM revision_media WHERE revision_id=#{id} AND usage_type='gallery' ORDER BY sort_order,id") List<Long> gallery(long id);
    @Select("SELECT result,verified_at,evidence_url,note FROM deal_verification_log WHERE revision_id=#{id} ORDER BY verified_at DESC,id DESC LIMIT 1") Map<String,Object> verification(long id);
    @Insert("INSERT INTO deal_verification_log(content_id,revision_id,result,evidence_url,note,operator_id) VALUES(#{id},#{revisionId},#{result},#{url},#{note},(SELECT id FROM admin_user WHERE username=#{actor}))")
    void verify(@Param("id") long id,@Param("revisionId") long revisionId,@Param("result") String result,@Param("url") String url,@Param("note") String note,@Param("actor") String actor);
}
