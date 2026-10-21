package cn.caiya.auth;

import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AuthMapper {
    @Select("SELECT * FROM admin_user WHERE username=#{username}") Map<String,Object> find(String username);
    @Select("SELECT count(*) FROM admin_user") int count();
    @Insert("INSERT INTO admin_user(username,password_hash) VALUES(#{username},#{hash})") void create(@Param("username") String username,@Param("hash") String hash);
    @Update("UPDATE admin_user SET last_login_at=now() WHERE username=#{username}") void login(String username);
    @Update("UPDATE admin_user SET password_hash=#{hash},lock_version=lock_version+1,updated_at=now() WHERE username=#{username} AND lock_version=#{version}")
    int password(@Param("username") String username,@Param("hash") String hash,@Param("version") long version);
    @Update("UPDATE admin_user SET display_name=#{name},lock_version=lock_version+1,updated_at=now() WHERE username=#{username} AND lock_version=#{version}")
    int profile(@Param("username") String username,@Param("name") String name,@Param("version") long version);
}
