package cn.caiya.auth;

import java.util.*;
import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class AdminAccountService {
    private final AuthMapper mapper;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SessionRegistry sessions;
    public AdminAccountService(AuthMapper mapper,PasswordEncoder encoder,AuditService audit,SessionRegistry sessions) {
        this.mapper=mapper;this.encoder=encoder;this.audit=audit;this.sessions=sessions;
    }
    public Map<String,Object> list(int page,int size) {
        return Map.of("items",mapper.list(size,(page-1)*size).stream().map(this::view).toList(),
            "total",mapper.count(),"page",page,"size",size);
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var out=new LinkedHashMap<String,Object>();
        out.put("id",row.get("id"));out.put("username",row.get("username"));out.put("displayName",row.get("display_name"));
        out.put("enabled",row.get("enabled"));out.put("lastLoginAt",row.get("last_login_at"));
        out.put("createdAt",row.get("created_at"));out.put("lockVersion",row.get("lock_version"));return out;
    }
    @Transactional public void create(String operator,String username,String displayName,String password) {
        AccountService.validatePassword(password);
        requireOperator(mapper.lockAccounts(),operator);
        if(mapper.find(username)!=null) throw new ApiException(409,"该账号已存在，请使用其他账号名");
        mapper.add(username,encoder.encode(password),displayName.trim());
        audit.record("account.create","account",Views.number(mapper.find(username),"id"));
    }
    @Transactional public void enabled(String operator,long id,boolean enabled,long version) {
        var accounts=mapper.lockAccounts();requireOperator(accounts,operator);
        var target=target(accounts,id);
        if(target.get("username").equals(operator)) throw new ApiException(400,"不能停用当前登录账号");
        if(!enabled && accounts.stream().filter(a->Boolean.TRUE.equals(a.get("enabled"))).count()<=1)
            throw new ApiException(400,"至少保留一个启用的管理员账号");
        if(mapper.enabled(id,enabled,version)!=1) throw ApiException.conflict();
        audit.record(enabled?"account.enable":"account.disable","account",id);
        expireAfterCommit(target.get("username").toString());
    }
    @Transactional public void resetPassword(String operator,long id,String password,long version) {
        AccountService.validatePassword(password);
        var accounts=mapper.lockAccounts();requireOperator(accounts,operator);var target=target(accounts,id);
        String username=target.get("username").toString();
        if(username.equals(operator)) throw new ApiException(400,"请在系统设置中验证当前密码后修改自己的密码");
        if(mapper.password(username,encoder.encode(password),version)!=1) throw ApiException.conflict();
        audit.record("account.reset-password","account",id);expireAfterCommit(username);
    }
    private void requireOperator(List<Map<String,Object>> accounts,String operator) {
        if(accounts.stream().noneMatch(a->a.get("username").equals(operator)&&Boolean.TRUE.equals(a.get("enabled"))))
            throw new ApiException(401,"会话已失效，请重新登录");
    }
    private Map<String,Object> target(List<Map<String,Object>> accounts,long id) {
        return accounts.stream().filter(a->Views.number(a,"id")==id).findFirst()
            .orElseThrow(()->new ApiException(404,"账号不存在"));
    }
    private void expireAfterCommit(String username) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for(Object principal:sessions.getAllPrincipals())
                    if(principal instanceof UserDetails user && user.getUsername().equals(username))
                        sessions.getAllSessions(principal,false).forEach(s->s.expireNow());
            }
        });
    }
}
