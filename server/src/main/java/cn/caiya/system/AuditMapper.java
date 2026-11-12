package cn.caiya.system;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuditMapper {
    @Insert("INSERT INTO operation_log(operator_id,action,target_type,target_id,request_id) VALUES((SELECT id FROM admin_user WHERE username=#{actor}),#{action},#{type},#{id},#{requestId})")
    void append(@Param("actor") String actor,@Param("action") String action,@Param("type") String type,@Param("id") long id,@Param("requestId") UUID requestId);
    @Select("SELECT l.id,l.action,l.target_type,l.target_id,l.result,l.created_at,u.display_name FROM operation_log l LEFT JOIN admin_user u ON u.id=l.operator_id ORDER BY l.id DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String,Object>> list(@Param("size") int size,@Param("offset") int offset);
    @Delete("DELETE FROM operation_log WHERE created_at < #{cutoff}")
    int purge(@Param("cutoff") java.time.OffsetDateTime cutoff);
}
