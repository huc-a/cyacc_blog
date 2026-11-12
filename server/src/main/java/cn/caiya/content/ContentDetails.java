package cn.caiya.content;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.*;

public record ContentDetails(
    @Size(max = 2000) String demoUrl,
    @Size(max = 2000) String sourceUrl,
    @Pattern(regexp = "planning|developing|released|archived") String projectStatus,
    @Size(max = 30) List<@NotBlank @Size(max = 60) String> techStack,
    @Pattern(regexp = "beginner|intermediate|advanced") String difficulty,
    @Size(max = 20000) String prerequisitesMarkdown,
    @Size(max = 2000) String claimUrl,
    @Size(max = 1000) String benefitText,
    @Size(max = 20000) String requirementsMarkdown,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    @Size(max = 200) List<@Valid Chapter> chapters) {
  public record Chapter(
      @NotNull UUID chapterKey,
      @NotNull @Size(max = 120) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
      @NotNull @Size(max = 200) String title,
      @NotNull @Size(max = 200000) String bodyMarkdown) {}

  public ContentDetails {
    demoUrl = empty(demoUrl);
    sourceUrl = empty(sourceUrl);
    projectStatus = projectStatus == null ? "developing" : projectStatus;
    techStack = techStack == null ? List.of() : List.copyOf(techStack);
    difficulty = difficulty == null ? "beginner" : difficulty;
    prerequisitesMarkdown = empty(prerequisitesMarkdown);
    claimUrl = empty(claimUrl);
    benefitText = empty(benefitText);
    requirementsMarkdown = empty(requirementsMarkdown);
    chapters = chapters == null ? List.of() : List.copyOf(chapters);
  }

  private static String empty(String v) {
    return v == null ? "" : v;
  }

  public static ContentDetails defaults() {
    return new ContentDetails(
        null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
