package cn.caiya.site;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SiteMapper {
  @Select(
      "SELECT value_jsonb::text AS settings,lock_version FROM site_setting WHERE"
          + " setting_key='site.public'")
  Map<String, Object> read();

  @Update(
      "UPDATE site_setting SET value_jsonb=CAST(#{json} AS"
          + " jsonb),lock_version=lock_version+1,updated_at=now() WHERE setting_key='site.public'"
          + " AND lock_version=#{version}")
  int save(@Param("json") String json, @Param("version") long version);
}
