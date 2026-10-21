package cn.caiya.auth;

import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AuthMapper mapper;private final PasswordEncoder encoder;private final AuditService audit;
    public AccountService(AuthMapper mapper,PasswordEncoder encoder,AuditService audit) {this.mapper=mapper;this.encoder=encoder;this.audit=audit;}
    @Transactional public void password(String username,String current,String next,long version) {
        var user=mapper.find(username);
        if(user==null||!encoder.matches(current,user.get("password_hash").toString())) throw new ApiException(400,"当前密码不正确");
        if(mapper.password(username,encoder.encode(next),version)!=1) throw ApiException.conflict();
        audit.record("account.password","account",Views.number(user,"id"));
    }
    @Transactional public void profile(String username,String name,long version) {
        var user=mapper.find(username);
        if(mapper.profile(username,name.trim(),version)!=1) throw ApiException.conflict();
        audit.record("account.profile","account",Views.number(user,"id"));
    }
}
