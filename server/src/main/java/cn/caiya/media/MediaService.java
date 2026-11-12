package cn.caiya.media;

import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
  private final MediaMapper mapper;
  private final AuditService audit;
  private final Path root;

  public MediaService(
      MediaMapper mapper, AuditService audit, @Value("${app.media-dir}") String directory) {
    this.mapper = mapper;
    this.audit = audit;
    this.root = Path.of(directory).toAbsolutePath().normalize();
  }

  @Transactional
  public Map<String, Object> upload(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw new ApiException(400, "图片必须为 5 MB 以内的 PNG、JPEG 或 GIF");
    String name =
        Optional.ofNullable(file.getOriginalFilename()).orElse("image").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    if (name.length() > 255) throw new ApiException(400, "文件名过长");
    String ext =
        name.toLowerCase(Locale.ROOT).endsWith(".png")
            ? "png"
            : name.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")
                ? "jpeg"
                : name.toLowerCase(Locale.ROOT).endsWith(".gif") ? "gif" : "";
    if (ext.isEmpty() || !("image/" + ext).equals(file.getContentType()))
      throw new ApiException(400, "仅支持 PNG/JPEG/GIF，扩展名和媒体类型须一致");
    try {
      var encoded = SafeImageEncoder.encode(file.getBytes(), ext);
      byte[] bytes = encoded.bytes();
      String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      // Identical bytes were uploaded before: reuse the existing asset
      // instead of storing a second copy of the same image.
      var existing = mapper.findBySha(sha256);
      if (existing != null) return Views.camel(existing);
      Files.createDirectories(root);
      String key = UUID.randomUUID() + "." + ext;
      Path path = root.resolve(key);
      Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
      var written = new ArrayList<Path>();
      written.add(path);
      String displayKey = null;
      Integer displayWidth = null, displayHeight = null;
      var variant = SafeImageEncoder.displayVariant(bytes, ext);
      if (variant != null) {
        displayKey = UUID.randomUUID() + "." + ext;
        Path displayPath = root.resolve(displayKey);
        Files.write(displayPath, variant.bytes(), StandardOpenOption.CREATE_NEW);
        written.add(displayPath);
        displayWidth = variant.width();
        displayHeight = variant.height();
      }
      final var writtenFiles = List.copyOf(written);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED)
                for (Path writtenFile : writtenFiles)
                  try {
                    Files.deleteIfExists(writtenFile);
                  } catch (IOException ignored) {
                  }
            }
          });
      var row = new HashMap<String, Object>();
      row.put("storageKey", key);
      row.put("originalName", name);
      row.put("mimeType", "image/" + ext);
      row.put("sizeBytes", bytes.length);
      row.put("width", encoded.width());
      row.put("height", encoded.height());
      row.put("sha256", sha256);
      row.put("displayKey", displayKey);
      row.put("displayWidth", displayWidth);
      row.put("displayHeight", displayHeight);
      mapper.insert(row);
      audit.record("media.upload", "media", Views.number(row, "id"));
      return Views.camel(mapper.find(Views.number(row, "id")));
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new ApiException(400, "图片读取或存储失败");
    }
  }

  @Transactional
  public void delete(long id) {
    var row = mapper.lock(id);
    if (row == null) throw new ApiException(404, "图片不存在");
    if (mapper.references(id) > 0) throw new ApiException(409, "图片已被已发布或编辑中的内容版本、站点背景或头像引用，不能删除");
    Path file = path(row);
    Path displayFile =
        row.get("display_key") == null ? null : resolve(row.get("display_key").toString());
    mapper.delete(id);
    audit.record("media.delete", "media", id);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              Files.deleteIfExists(file);
              if (displayFile != null) Files.deleteIfExists(displayFile);
            } catch (IOException e) {
              org.slf4j.LoggerFactory.getLogger(MediaService.class)
                  .warn("Orphan media file requires cleanup: {}", file.getFileName());
            }
          }
        });
  }

  public Path path(Map<String, Object> row) {
    return resolve(row.get("storage_key").toString());
  }

  public Path displayPath(Map<String, Object> row) {
    Object key = row.get("display_key");
    return key == null ? null : resolve(key.toString());
  }

  private Path resolve(String key) {
    Path path = root.resolve(key).normalize();
    if (!path.startsWith(root)) throw new ApiException(404, "图片不存在");
    return path;
  }
}
