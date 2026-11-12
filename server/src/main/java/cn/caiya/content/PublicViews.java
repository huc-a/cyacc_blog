package cn.caiya.content;

import java.util.*;

/**
 * Whitelisted output shapes for the anonymous public API. Public responses
 * are assembled field-by-field here instead of deleting private fields from
 * a shared map, so a new internal column can never leak by accident.
 * The per-type {@code details} payload stays a map because its shape is
 * defined by the content type; everything around it is typed.
 */
public final class PublicViews {
    private PublicViews() {}

    public record Page<T>(List<T> items, long total, int page, int size) {}

    public record ContentItem(
        long id, String contentType, String slug, String publishStatus,
        String firstPublishedAt, String lastPublishedAt,
        long revisionId, long revisionNo,
        String title, String summary, Long coverAssetId,
        List<String> tagNames, Long categoryId, String categoryName
    ) {}

    public record Neighbor(long id, String contentType, String slug, String title) {}

    public record Navigation(Neighbor previous, Neighbor next) {}

    public record ContentDetail(
        long id, String contentType, String slug, String publishStatus,
        String createdAt, String firstPublishedAt, String lastPublishedAt,
        long revisionId, long revisionNo,
        String title, String summary, String bodyMarkdown,
        Long coverAssetId, Long categoryId,
        Map<String, Object> details,
        List<Map<String, Object>> tags, List<Long> tagIds, List<Long> galleryAssetIds,
        Navigation navigation
    ) {}
}
