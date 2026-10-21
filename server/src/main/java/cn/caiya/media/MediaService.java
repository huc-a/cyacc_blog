package cn.caiya.media;

import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import javax.imageio.ImageIO;
import cn.caiya.common.*;
import cn.caiya.system.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private final MediaMapper mapper;private final AuditService audit;private final Path root;
    public MediaService(MediaMapper mapper,AuditService audit,@Value("${app.media-dir}") String directory) {
        this.mapper=mapper;this.audit=audit;this.root=Path.of(directory).toAbsolutePath().normalize();
    }
    @Transactional public Map<String,Object> upload(MultipartFile file) {
        if(file.isEmpty()||file.getSize()>5*1024*1024) throw new ApiException(400,"图片必须为 5 MB 以内的 PNG 或 JPEG");
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("image").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        if(name.length()>255) throw new ApiException(400,"文件名过长");
        String ext=name.toLowerCase(Locale.ROOT).endsWith(".png")?"png":name.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")?"jpeg":"";
        if(ext.isEmpty()||!("image/"+ext).equals(file.getContentType())) throw new ApiException(400,"仅支持 PNG/JPEG，扩展名和媒体类型须一致");
        Path path=null;
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext()) throw new ApiException(400,"无法解码图片");
            var reader=readers.next();java.awt.image.BufferedImage decoded;
            try {
                reader.setInput(stream,true,true);
                if(!reader.getFormatName().equalsIgnoreCase(ext)) throw new ApiException(400,"图片实际格式与扩展名不符");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>8000||height>8000||(long)width*height>16_000_000) throw new ApiException(400,"图片尺寸过大，最多 1600 万像素");
                decoded=reader.read(0);if(decoded==null) throw new ApiException(400,"无法解码图片");
            } finally {reader.dispose();}
            var output=new ByteArrayOutputStream();if(!ImageIO.write(decoded,ext,output)) throw new ApiException(400,"图片重新编码失败");
            byte[] bytes=output.toByteArray();if(bytes.length>10*1024*1024) throw new ApiException(400,"处理后的图片超过 10 MB");
            Files.createDirectories(root);String key=UUID.randomUUID()+"."+ext;path=root.resolve(key);
            Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
            final Path created=path;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED) try{Files.deleteIfExists(created);}catch(IOException ignored){}}
            });
            var row=new HashMap<String,Object>();row.put("storageKey",key);row.put("originalName",name);row.put("mimeType","image/"+ext);
            row.put("sizeBytes",bytes.length);row.put("width",decoded.getWidth());row.put("height",decoded.getHeight());
            row.put("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));mapper.insert(row);
            audit.record("media.upload","media",Views.number(row,"id"));return Views.camel(mapper.find(Views.number(row,"id")));
        } catch(IOException|NoSuchAlgorithmException e) {throw new ApiException(400,"图片读取或存储失败");}
    }
    @Transactional public void delete(long id) {
        var row=mapper.lock(id);if(row==null) throw new ApiException(404,"图片不存在");
        if(mapper.references(id)>0) throw new ApiException(409,"图片已被内容版本或站点背景引用，不能删除");
        Path file=path(row);mapper.delete(id);audit.record("media.delete","media",id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {try{Files.deleteIfExists(file);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(MediaService.class).warn("Orphan media file requires cleanup: {}",file.getFileName());}}
        });
    }
    public Path path(Map<String,Object> row) {
        Path path=root.resolve(row.get("storage_key").toString()).normalize();
        if(!path.startsWith(root)) throw new ApiException(404,"图片不存在");return path;
    }
}
