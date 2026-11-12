package cn.caiya.media;

import cn.caiya.common.*;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
  private final MediaMapper mapper;
  private final MediaService service;

  public MediaController(MediaMapper mapper, MediaService service) {
    this.mapper = mapper;
    this.service = service;
  }

  @GetMapping("/api/admin/media")
  public Map<String, Object> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "24") int size) {
    if (page < 1 || page > 100000 || size < 1 || size > 50) throw new ApiException(400, "分页参数无效");
    return Map.of(
        "items",
        mapper.list(size, (page - 1) * size).stream().map(Views::camel).toList(),
        "total",
        mapper.count(),
        "page",
        page,
        "size",
        size);
  }

  @PostMapping("/api/admin/media")
  public Map<String, Object> upload(@RequestParam MultipartFile file) {
    return service.upload(file);
  }

  @DeleteMapping("/api/admin/media/{id}")
  public void delete(@PathVariable long id) {
    service.delete(id);
  }

  @GetMapping("/api/public/media/{id}")
  public ResponseEntity<?> publicFile(
      @PathVariable long id, @RequestParam(defaultValue = "original") String variant) {
    if (!mapper.isPublic(id)) throw new ApiException(404, "图片不存在");
    // Storage names are UUIDs and file content never changes: cache forever.
    return file(
        id,
        CacheControl.maxAge(365, java.util.concurrent.TimeUnit.DAYS).cachePublic().immutable(),
        variant);
  }

  @GetMapping("/api/admin/media/{id}/file")
  public ResponseEntity<?> file(
      @PathVariable long id, @RequestParam(defaultValue = "original") String variant) {
    return file(id, CacheControl.noStore(), variant);
  }

  private ResponseEntity<?> file(long id, CacheControl cacheControl, String variant) {
    var row = mapper.find(id);
    if (row == null) throw new ApiException(404, "图片不存在");
    // variant=display serves the downscaled copy when one exists.
    java.nio.file.Path path =
        "display".equals(variant) && service.displayPath(row) != null
            ? service.displayPath(row)
            : service.path(row);
    var resource = new FileSystemResource(path);
    if (!resource.exists()) throw new ApiException(404, "图片文件不存在");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(row.get("mime_type").toString()))
        .cacheControl(cacheControl)
        .body(resource);
  }
}
