package cn.caiya.media;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MediaMapper {
    @Insert("INSERT INTO media_asset(storage_key,original_name,mime_type,size_bytes,width,height,sha256) VALUES(#{storageKey},#{originalName},#{mimeType},#{sizeBytes},#{width},#{height},#{sha256})")
    @Options(useGeneratedKeys=true,keyProperty="id") void insert(Map<String,Object> values);
    @Select("SELECT * FROM media_asset WHERE id=#{id}") Map<String,Object> find(long id);
    @Select("SELECT * FROM media_asset WHERE id=#{id} FOR UPDATE") Map<String,Object> lock(long id);
    @Select("SELECT id,original_name,mime_type,size_bytes,width,height,created_at FROM media_asset ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> list(@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT count(*) FROM media_asset") long count();
    @Select("SELECT (SELECT count(*) FROM admin_user WHERE avatar_asset_id=#{id})+(SELECT count(*) FROM content_revision WHERE cover_asset_id=#{id})+(SELECT count(*) FROM revision_media WHERE media_asset_id=#{id})+(SELECT count(*) FROM site_setting WHERE (value_jsonb->>'backgroundAssetId'=CAST(#{id} AS text) OR value_jsonb->>'avatarAssetId'=CAST(#{id} AS text) OR value_jsonb->>'loginBackgroundAssetId'=CAST(#{id} AS text) OR value_jsonb->>'defaultCoverAssetId'=CAST(#{id} AS text)))") long references(long id);
    @Select("SELECT EXISTS(SELECT 1 FROM content c JOIN content_revision r ON r.id=c.published_revision_id WHERE c.publish_status='published' AND c.deleted_at IS NULL AND (r.cover_asset_id=#{id} OR EXISTS(SELECT 1 FROM revision_media m WHERE m.revision_id=r.id AND m.media_asset_id=#{id}))) OR EXISTS(SELECT 1 FROM site_setting WHERE (value_jsonb->>'backgroundAssetId'=CAST(#{id} AS text) OR value_jsonb->>'avatarAssetId'=CAST(#{id} AS text) OR value_jsonb->>'loginBackgroundAssetId'=CAST(#{id} AS text) OR value_jsonb->>'defaultCoverAssetId'=CAST(#{id} AS text)))") boolean isPublic(long id);
    @Delete("DELETE FROM media_asset WHERE id=#{id}") void delete(long id);
}
