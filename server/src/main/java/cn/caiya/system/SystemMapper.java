package cn.caiya.system;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SystemMapper {
  @Select(
      "SELECT content_type, count(*) FILTER(WHERE deleted_at IS NULL) AS total,count(*)"
          + " FILTER(WHERE working_revision_id IS NOT NULL AND deleted_at IS NULL) AS"
          + " drafts,count(*) FILTER(WHERE publish_status='published' AND deleted_at IS NULL) AS"
          + " published,count(*) FILTER(WHERE deleted_at IS NOT NULL) AS trash FROM content GROUP"
          + " BY content_type ORDER BY content_type")
  List<Map<String, Object>> counts();

  @Select("SELECT id FROM content ORDER BY id")
  List<Long> allContentIds();

  @Select(
      "SELECT DISTINCT (first_published_at AT TIME ZONE 'Asia/Shanghai')::date AS date FROM content"
          + " WHERE publish_status='published' AND deleted_at IS NULL ORDER BY date DESC LIMIT 366")
  List<Map<String, Object>> calendar();

  @Select(
      "SELECT (SELECT count(*) FROM content WHERE publish_status='published' AND deleted_at IS"
          + " NULL) AS contents,(SELECT count(*) FROM tag) AS tags")
  Map<String, Object> publicCounts();

  @Select("SELECT count(*) FROM operation_log")
  long logCount();
}
