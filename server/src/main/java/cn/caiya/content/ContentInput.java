package cn.caiya.content;

import jakarta.validation.constraints.*;
import java.util.List;

public record ContentInput(
    @NotNull @Pattern(regexp = "article") String type,
    // Optional on create: when null/blank the service assigns the next
    // numeric slug automatically. Immutable once the content exists.
    @Size(max = 160) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
    @NotNull @Size(max = 200) String title,
    @NotNull @Size(max = 5000) String summary,
    @NotNull @Size(max = 500000) String bodyMarkdown,
    @NotNull @PositiveOrZero Long lockVersion,
    @Size(max = 500) String changeNote,
    @Size(max = 30) List<@Positive Long> tagIds,
    @Positive Long categoryId,
    @Positive Long coverAssetId,
    @Size(max = 30) List<@Positive Long> galleryAssetIds) {
  public ContentInput {
    tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
    galleryAssetIds = galleryAssetIds == null ? List.of() : List.copyOf(galleryAssetIds);
  }

  public ContentInput(
      String type,
      String slug,
      String title,
      String summary,
      String body,
      long lockVersion,
      String note) {
    this(type, slug, title, summary, body, lockVersion, note, List.of(), null, null, List.of());
  }
}
