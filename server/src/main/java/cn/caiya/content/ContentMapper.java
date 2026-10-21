package cn.caiya.content;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ContentMapper {
    @Select("SELECT * FROM content WHERE id=#{id}") Map<String,Object> identity(long id);
    @Select("SELECT * FROM content WHERE id=#{id} FOR UPDATE") Map<String,Object> lock(long id);
    @Select("SELECT * FROM content_revision WHERE id=#{id}") Map<String,Object> revision(long id);
    @Insert("INSERT INTO content(content_type,slug) VALUES(#{type},#{slug})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insert(Map<String,Object> values);
    @Insert("INSERT INTO content_revision(content_id,content_type,revision_no,title,summary,change_note) VALUES(#{contentId},#{type},#{revisionNo},#{title},#{summary},#{changeNote})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insertRevision(Map<String,Object> values);
    @Update("UPDATE content SET working_revision_id=#{revisionId} WHERE id=#{id}")
    void working(@Param("id") long id,@Param("revisionId") long revisionId);
    @Update("UPDATE content_revision SET title=#{title},summary=#{summary},change_note=#{changeNote},lock_version=lock_version+1,updated_at=now() WHERE id=#{id} AND revision_state='editing'")
    int updateRevision(Map<String,Object> values);
    @Select("SELECT body_markdown FROM article_detail WHERE revision_id=#{id}") String body(long id);
    @Insert("INSERT INTO article_detail(revision_id,body_markdown) VALUES(#{id},#{body}) ON CONFLICT(revision_id) DO UPDATE SET body_markdown=EXCLUDED.body_markdown")
    void bodySave(@Param("id") long id,@Param("body") String body);
    @Select("SELECT coalesce(max(revision_no),0)+1 FROM content_revision WHERE content_id=#{id}") int nextRevision(long id);
    @Update("UPDATE content SET lock_version=lock_version+1,updated_at=now() WHERE id=#{id} AND lock_version=#{version}")
    int touch(@Param("id") long id,@Param("version") long version);
    @Update("UPDATE content_revision SET revision_state='frozen',frozen_at=now(),updated_at=now() WHERE id=#{id} AND revision_state='editing'") int freeze(long id);
    @Update("UPDATE content SET publish_status='published',published_revision_id=#{revisionId},working_revision_id=NULL,first_published_at=coalesce(first_published_at,now()),last_published_at=now() WHERE id=#{id}")
    void publish(@Param("id") long id,@Param("revisionId") long revisionId);
    @Update("UPDATE content SET publish_status='offline' WHERE id=#{id}") void offline(long id);
    @Update("UPDATE content SET deleted_at=now(),publish_status=CASE WHEN published_revision_id IS NULL THEN 'draft' ELSE 'offline' END WHERE id=#{id}") void trash(long id);
    @Update("UPDATE content SET deleted_at=NULL,publish_status=CASE WHEN published_revision_id IS NULL THEN 'draft' ELSE 'offline' END WHERE id=#{id}") void restore(long id);
    List<Map<String,Object>> list(Map<String,Object> query);
    long count(Map<String,Object> query);
    @Select("SELECT id FROM content WHERE slug=#{slug} AND publish_status='published' AND deleted_at IS NULL") Long publicId(String slug);
    @Select("SELECT id,revision_no,title,summary,revision_state,created_at,frozen_at,change_note FROM content_revision WHERE content_id=#{id} ORDER BY revision_no DESC")
    List<Map<String,Object>> history(long id);
}
