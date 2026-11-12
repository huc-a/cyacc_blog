-- V12: the blog is article-only. Drop the work/tutorial/deal detail storage,
-- tighten the content type check, and prune stored home-module settings.
DROP TABLE IF EXISTS deal_verification_log;
ALTER TABLE revision_media DROP CONSTRAINT IF EXISTS revision_media_chapter_id_revision_id_fkey;
ALTER TABLE revision_media DROP COLUMN IF EXISTS chapter_id;
DROP TABLE IF EXISTS tutorial_chapter;
DROP TABLE IF EXISTS work_detail;
DROP TABLE IF EXISTS tutorial_detail;
DROP TABLE IF EXISTS deal_detail;
DROP FUNCTION IF EXISTS prevent_frozen_chapter_change();

ALTER TABLE content DROP CONSTRAINT content_content_type_check;
ALTER TABLE content ADD CONSTRAINT content_content_type_check CHECK (content_type = 'article');

UPDATE site_setting
SET value_jsonb = jsonb_set(value_jsonb, '{homeModules}', '["article"]'::jsonb)
WHERE value_jsonb ? 'homeModules';
