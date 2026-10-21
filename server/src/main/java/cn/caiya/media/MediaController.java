package cn.caiya.media;

import java.util.*;
import cn.caiya.common.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
    private final MediaMapper mapper;private final MediaService service;
    public MediaController(MediaMapper mapper,MediaService service) {this.mapper=mapper;this.service=service;}
    @GetMapping("/api/admin/media") public Map<String,Object> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="24") int size) {
        if(page<1||page>100000||size<1||size>50) throw new ApiException(400,"分页参数无效");
        return Map.of("items",mapper.list(size,(page-1)*size).stream().map(Views::camel).toList(),"total",mapper.count(),"page",page,"size",size);
    }
    @PostMapping("/api/admin/media") public Map<String,Object> upload(@RequestParam MultipartFile file) {return service.upload(file);}
    @DeleteMapping("/api/admin/media/{id}") public void delete(@PathVariable long id) {service.delete(id);}
    @GetMapping("/api/public/media/{id}") public ResponseEntity<?> publicFile(@PathVariable long id) {
        if(!mapper.isPublic(id)) throw new ApiException(404,"图片不存在");return file(id);
    }
    @GetMapping("/api/admin/media/{id}/file") public ResponseEntity<?> file(@PathVariable long id) {
        var row=mapper.find(id);if(row==null) throw new ApiException(404,"图片不存在");var resource=new FileSystemResource(service.path(row));
        if(!resource.exists()) throw new ApiException(404,"图片文件不存在");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(row.get("mime_type").toString())).cacheControl(CacheControl.noStore()).body(resource);
    }
}
