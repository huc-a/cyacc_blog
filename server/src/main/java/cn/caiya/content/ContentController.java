package cn.caiya.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContentController {
    private final ContentService service;
    public ContentController(ContentService service) {this.service=service;}
    public record Version(@NotNull @PositiveOrZero Long lockVersion) {}
    @GetMapping("/api/public/contents")
    public Map<String,Object> publicList(@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,
        @RequestParam(required=false) Long categoryId,@RequestParam(required=false) Long tagId,
        @RequestParam(required=false) java.time.LocalDate date,@RequestParam(defaultValue="false") boolean home) {return service.list(true,type,"all",q,page,size,categoryId,tagId,date,home);}
    @GetMapping("/api/public/contents/{slug}") public Map<String,Object> publicDetail(@PathVariable String slug) {return service.publicDetail(slug);}
    @GetMapping("/api/admin/contents")
    public Map<String,Object> adminList(@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="all") String status,
        @RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int size,
        @RequestParam(required=false) Long categoryId,@RequestParam(required=false) Long tagId) {return service.list(false,type,status,q,page,size,categoryId,tagId,null);}
    @GetMapping("/api/admin/contents/{id}") public Map<String,Object> detail(@PathVariable long id) {return service.detail(id,false);}
    @GetMapping("/api/admin/contents/{id}/revisions") public List<Map<String,Object>> history(@PathVariable long id) {return service.history(id);}
    @PostMapping("/api/admin/contents") public Map<String,Object> create(@Valid @RequestBody ContentInput input) {return service.create(input);}
    @PutMapping("/api/admin/contents/{id}") public Map<String,Object> save(@PathVariable long id,@Valid @RequestBody ContentInput input) {return service.save(id,input);}
    @PostMapping("/api/admin/contents/{id}/publish") public Map<String,Object> publish(@PathVariable long id,@Valid @RequestBody Version version) {return service.publish(id,version.lockVersion());}
    @PostMapping("/api/admin/contents/{id}/{action:offline|trash|restore}")
    public Map<String,Object> transition(@PathVariable long id,@PathVariable String action,@Valid @RequestBody Version version) {return service.transition(id,version.lockVersion(),action);}
}
