package cn.caiya.site;

import java.util.List;
import jakarta.validation.constraints.*;

public record SiteSettings(
    @NotBlank @Size(max=80) String name,@NotNull @Size(max=300) String description,
    @NotBlank @Size(max=80) String author,@NotNull @Size(max=500) String bio,
    @NotNull @Size(max=2000) String github,@NotNull @Email @Size(max=254) String email,
    @NotNull @Size(max=300) String footer,@NotBlank @Size(max=120) String headline,@NotNull @Size(max=300) String subtitle,
    @NotNull @Pattern(regexp="mist|blue|dusk") String background,@Positive Long backgroundAssetId,
    @DecimalMin("0.60") @DecimalMax("0.98") double glassOpacity,
    @DecimalMin("0.80") @DecimalMax("0.99") double articleOpacity,
    @Min(0) @Max(28) int blur,@Min(16) @Max(23) int fontSize,
    @DecimalMin("1.6") @DecimalMax("2.2") double lineHeight,
    @NotEmpty @Size(max=5) List<@Pattern(regexp="article|work|tutorial|website|deal") String> homeModules
) {}
