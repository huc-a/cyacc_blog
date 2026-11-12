-- Batch-1 housekeeping: drop the leftover website check log (website content
-- type was removed in V7 but its check-log table was never dropped), and add
-- the foreign-key indexes the original migrations omitted. PostgreSQL does
-- not index foreign keys automatically; these cover the hot join/filter
-- paths (revision lookups, tag filtering, chapters, media usage, audit).
DROP TABLE IF EXISTS website_check_log;

CREATE INDEX IF NOT EXISTS content_revision_content_idx ON content_revision(content_id);
CREATE INDEX IF NOT EXISTS revision_tag_tag_idx ON revision_tag(tag_id);
CREATE INDEX IF NOT EXISTS tutorial_chapter_revision_idx ON tutorial_chapter(tutorial_revision_id);
CREATE INDEX IF NOT EXISTS revision_media_asset_idx ON revision_media(media_asset_id);
CREATE INDEX IF NOT EXISTS revision_media_revision_idx ON revision_media(revision_id);
CREATE INDEX IF NOT EXISTS deal_verification_revision_idx ON deal_verification_log(revision_id);
CREATE INDEX IF NOT EXISTS operation_log_operator_idx ON operation_log(operator_id);
CREATE INDEX IF NOT EXISTS operation_log_created_idx ON operation_log(created_at DESC);
CREATE INDEX IF NOT EXISTS media_asset_sha256_idx ON media_asset(sha256);
