package cn.caiya.content;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class TaxonomyController {
    private final TaxonomyMapper mapper;private final AuditService audit;
    public TaxonomyController(TaxonomyMapper mapper,AuditService audit) {this.mapper=mapper;this.audit=audit;}
    public record Input(@NotBlank @Size(max=60) String name,@NotNull @Size(max=80) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String slug,String type,@Min(0) int sortOrder) {}
    @GetMapping({"/api/public/taxonomy","/api/admin/taxonomy"}) public Map<String,Object> list() {
        return Map.of("categories",mapper.categories().stream().map(Views::camel).toList(),"tags",mapper.tags());
    }
    @PostMapping("/api/admin/taxonomy/{kind:categories|tags}") @Transactional
    public Map<String,Object> create(@PathVariable String kind,@Valid @RequestBody Input input) {
        var v=values(input);if(kind.equals("categories")) {ContentService.type(input.type()==null?"":input.type());mapper.createCategory(v);}else mapper.createTag(v);
        audit.record("taxonomy.create",kind,Views.number(v,"id"));return v;
    }
    @PutMapping("/api/admin/taxonomy/{kind:categories|tags}/{id}") @Transactional
    public Map<String,Object> update(@PathVariable String kind,@PathVariable long id,@Valid @RequestBody Input input) {
        var v=values(input);v.put("id",id);int changed=kind.equals("categories")?mapper.updateCategory(v):mapper.updateTag(v);
        if(changed!=1) throw new ApiException(404,"分类或标签不存在");audit.record("taxonomy.update",kind,id);return v;
    }
    @DeleteMapping("/api/admin/taxonomy/{kind:categories|tags}/{id}") @Transactional
    public void delete(@PathVariable String kind,@PathVariable long id) {
        int changed=kind.equals("categories")?mapper.deleteCategory(id):mapper.deleteTag(id);
        if(changed!=1) throw new ApiException(404,"分类或标签不存在");audit.record("taxonomy.delete",kind,id);
    }
    private Map<String,Object> values(Input i) {
        var v=new HashMap<String,Object>();v.put("name",i.name().trim());v.put("slug",i.slug());v.put("type",i.type());v.put("sortOrder",i.sortOrder());return v;
    }
}
