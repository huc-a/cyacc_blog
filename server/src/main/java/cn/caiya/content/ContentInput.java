package cn.caiya.content;

import jakarta.validation.constraints.*;

public record ContentInput(
    @NotNull @Pattern(regexp="article|work|tutorial|website|deal") String type,
    @NotNull @Size(max=160) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String slug,
    @NotNull @Size(max=200) String title,
    @NotNull @Size(max=5000) String summary,
    @NotNull @Size(max=500000) String bodyMarkdown,
    @NotNull @PositiveOrZero Long lockVersion,
    @Size(max=500) String changeNote
) {}
