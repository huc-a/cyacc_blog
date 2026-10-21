package cn.caiya.system;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class AuditService {
    private final AuditMapper mapper;
    public AuditService(AuditMapper mapper) {this.mapper=mapper;}
    public void record(String action,String type,long id) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        mapper.append(auth==null?null:auth.getName(),action,type,id,UUID.randomUUID());
    }
}
