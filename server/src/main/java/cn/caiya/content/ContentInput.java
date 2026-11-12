package cn.caiya.content;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;

public record ContentInput(
    @NotNull @Pattern(regexp="article|work|tutorial|deal") String type,
    @NotNull @Size(max=160) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String slug,
    @NotNull @Size(max=200) String title,
    @NotNull @Size(max=5000) String summary,
    @NotNull @Size(max=500000) String bodyMarkdown,
    @NotNull @PositiveOrZero Long lockVersion,
    @Size(max=500) String changeNote,
    @Size(max=30) List<@Positive Long> tagIds,
    @Positive Long categoryId,@Positive Long coverAssetId,@Size(max=30) List<@Positive Long> galleryAssetIds,
    @Valid ContentDetails details
) {
    public ContentInput {
        tagIds=tagIds==null?List.of():List.copyOf(tagIds);
        galleryAssetIds=galleryAssetIds==null?List.of():List.copyOf(galleryAssetIds);
        details=details==null?ContentDetails.defaults():details;
    }
    public ContentInput(String type,String slug,String title,String summary,String body,long lockVersion,String note) {
        this(type,slug,title,summary,body,lockVersion,note,List.of(),null,null,List.of(),null);
    }
    public ContentInput(String type,String slug,String title,String summary,String body,Long lockVersion,String note,List<Long> tagIds,Long coverAssetId,List<Long> galleryAssetIds,ContentDetails details) {
        this(type,slug,title,summary,body,lockVersion,note,tagIds,null,coverAssetId,galleryAssetIds,details);
    }
}
