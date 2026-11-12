package cn.caiya.site;

import jakarta.validation.constraints.*;
import java.util.List;

public record SiteSettings(
    @NotBlank @Size(max = 80) String name,
    @NotNull @Size(max = 300) String description,
    @NotBlank @Size(max = 80) String author,
    @NotNull @Size(max = 500) String bio,
    @NotNull @Size(max = 2000) String github,
    @NotNull @Email @Size(max = 254) String email,
    @NotNull @Size(max = 300) String footer,
    @NotBlank @Size(max = 120) String headline,
    @NotNull @Size(max = 300) String subtitle,
    @NotNull @Pattern(regexp = "mist|blue|dusk") String background,
    @Positive Long backgroundAssetId,
    @DecimalMin("0.60") @DecimalMax("0.98") double glassOpacity,
    @DecimalMin("0.80") @DecimalMax("0.99") double articleOpacity,
    @Min(0) @Max(28) int blur,
    @Min(16) @Max(23) int fontSize,
    @DecimalMin("1.6") @DecimalMax("2.2") double lineHeight,
    @NotEmpty @Size(max = 4)
        List<@Pattern(regexp = "article|work|tutorial|deal") String> homeModules,
    @Positive Long avatarAssetId,
    @NotNull @Size(max = 80) String icpNumber,
    @DecimalMin("0") @DecimalMax("1") Double navTopOpacity,
    @DecimalMin("0.5") @DecimalMax("1") Double navScrolledOpacity,
    @NotNull @Size(max = 80) @Pattern(regexp = "|[\\p{IsHan}]{1,12}公网安备\\s*[0-9]{14}号")
        String policeNumber,
    @Positive Long loginBackgroundAssetId,
    @Positive Long defaultCoverAssetId) {
  public SiteSettings {
    icpNumber = icpNumber == null ? "" : icpNumber;
    policeNumber = policeNumber == null ? "" : policeNumber.strip();
    navTopOpacity = navTopOpacity == null ? 0.0 : navTopOpacity;
    navScrolledOpacity = navScrolledOpacity == null ? 0.92 : navScrolledOpacity;
  }
}
