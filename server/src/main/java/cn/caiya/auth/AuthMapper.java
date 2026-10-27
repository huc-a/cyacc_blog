package cn.caiya.auth;

import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthMapper {
    @Select("SELECT enabled,security_version FROM admin_user WHERE username=#{username}") Map<String,Object> state(String username);
    @Select("SELECT id,username,display_name,enabled,last_login_at,created_at,lock_version FROM admin_user ORDER BY id LIMIT #{size} OFFSET #{offset}")
    java.util.List<Map<String,Object>> list(@Param("size") int size,@Param("offset") int offset);
    @Select("SELECT id,username,enabled,lock_version FROM admin_user ORDER BY id FOR UPDATE")
    java.util.List<Map<String,Object>> lockAccounts();
    @Insert("INSERT INTO admin_user(username,password_hash,display_name) VALUES(#{username},#{hash},#{name})")
    void add(@Param("username") String username,@Param("hash") String hash,@Param("name") String name);
    @Update("UPDATE admin_user SET enabled=#{enabled},security_version=security_version+1,lock_version=lock_version+1,updated_at=now() WHERE id=#{id} AND lock_version=#{version}")
    int enabled(@Param("id") long id,@Param("enabled") boolean enabled,@Param("version") long version);
    @Select("SELECT * FROM admin_user WHERE username=#{username}") Map<String,Object> find(String username);
    @Select("SELECT count(*) FROM admin_user") int count();
    @Insert("INSERT INTO admin_user(username,password_hash) VALUES(#{username},#{hash})") void create(@Param("username") String username,@Param("hash") String hash);
    @Update("UPDATE admin_user SET last_login_at=now() WHERE username=#{username}") void login(String username);
    @Update("UPDATE admin_user SET password_hash=#{hash},security_version=security_version+1,lock_version=lock_version+1,updated_at=now() WHERE username=#{username} AND lock_version=#{version}")
    int password(@Param("username") String username,@Param("hash") String hash,@Param("version") long version);
    @Update("UPDATE admin_user SET display_name=#{name},avatar_asset_id=#{avatarAssetId},lock_version=lock_version+1,updated_at=now() WHERE username=#{username} AND lock_version=#{version}")
    int profile(@Param("username") String username,@Param("name") String name,@Param("avatarAssetId") Long avatarAssetId,@Param("version") long version);
}
