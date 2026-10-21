package cn.caiya.content;

import java.util.*;
import java.security.Principal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class VerificationController {
    private final ContentMapper contents;private final DetailMapper details;private final AuditService audit;
    public VerificationController(ContentMapper contents,DetailMapper details,AuditService audit) {this.contents=contents;this.details=details;this.audit=audit;}
    public record Input(@NotNull Long revisionId,@NotNull @Pattern(regexp="valid|invalid|unknown") String result,@NotNull @Size(max=2000) String evidenceUrl,@NotNull @Size(max=1000) String note) {}
    @PostMapping("/api/admin/contents/{id}/verify") @Transactional
    public Map<String,Object> verify(@PathVariable long id,@Valid @RequestBody Input input,Principal principal) {
        var c=contents.lock(id);
        if(c==null||!"deal".equals(c.get("content_type"))) throw new ApiException(404,"羊毛条目不存在");
        if(c.get("deleted_at")!=null || !Objects.equals(c.get("published_revision_id"),input.revisionId())) throw new ApiException(409,"公开版本已改变，请刷新后核验");
        SafeUrls.check(input.evidenceUrl());details.verify(id,input.revisionId(),input.result(),input.evidenceUrl(),input.note(),principal.getName());
        audit.record("deal.verify","deal",id);return Views.camel(details.verification(input.revisionId()));
    }
}
