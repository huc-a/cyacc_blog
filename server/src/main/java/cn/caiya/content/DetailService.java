package cn.caiya.content;

import cn.caiya.common.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class DetailService {
  private final DetailMapper mapper;
  private static final Pattern MEDIA = Pattern.compile("/api/public/media/([0-9]+)(?![0-9])");

  public DetailService(DetailMapper mapper) {
    this.mapper = mapper;
  }

  public void validate(ContentInput input) {
    if (input.categoryId() != null && !mapper.categoryExists(input.categoryId()))
      throw new ApiException(400, "内容分类不存在");
    if (input.bodyMarkdown().length() > 2_000_000) throw new ApiException(400, "正文总长度超过限制");
  }

  public void save(long rid, ContentInput input) {
    validate(input);
    mapper.metadata(rid, input);
    mapper.save(rid, input);
    mapper.clearTags(rid);
    for (long tag : new LinkedHashSet<>(input.tagIds())) mapper.tag(rid, tag);
    mapper.clearMedia(rid);
    int order = 0;
    for (long asset : new LinkedHashSet<>(input.galleryAssetIds()))
      mapper.media(rid, asset, "gallery", order++);
    var assets = new LinkedHashSet<Long>();
    var matcher = MEDIA.matcher(input.bodyMarkdown());
    while (matcher.find()) {
      try {
        assets.add(Long.parseLong(matcher.group(1)));
      } catch (NumberFormatException e) {
        throw new ApiException(400, "媒体编号无效");
      }
    }
    for (long asset : assets) mapper.media(rid, asset, "body", 0);
  }

  public Map<String, Object> read(long rid) {
    var raw = mapper.read(rid);
    if (raw == null) throw new ApiException(409, "内容详情缺失");
    var d = Views.camel(raw);
    d.remove("revisionId");
    d.remove("contentType");
    Object body = d.remove("bodyMarkdown");
    var tags = mapper.tags(rid);
    var result = new HashMap<String, Object>();
    result.put("bodyMarkdown", body == null ? "" : body);
    result.put("details", d);
    result.put("tags", tags);
    result.put("tagIds", tags.stream().map(t -> t.get("id")).toList());
    result.put("galleryAssetIds", mapper.gallery(rid));
    result.put("categoryId", mapper.categoryId(rid));
    return result;
  }

  public void validatePublish(long rid, String title) {
    var result = read(rid);
    if (title.isBlank() || result.get("bodyMarkdown").toString().isBlank())
      throw new ApiException(400, "发布前请填写标题和正文");
  }
}
