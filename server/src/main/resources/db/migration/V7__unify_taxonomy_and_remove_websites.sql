-- Merge the former per-content categories into the global tag vocabulary.
DROP TRIGGER immutable_tags ON revision_tag;
DO $$
DECLARE c record; candidate text;
BEGIN
  FOR c IN SELECT id, name, slug FROM category ORDER BY id LOOP
    IF NOT EXISTS (SELECT 1 FROM tag WHERE lower(name)=lower(c.name)) THEN
      candidate := c.slug;
      IF EXISTS (SELECT 1 FROM tag WHERE slug=candidate) THEN
        candidate := 'category-' || c.id;
      END IF;
      INSERT INTO tag(name, slug) VALUES (c.name, candidate);
    END IF;
    INSERT INTO revision_tag(revision_id, tag_id)
      SELECT r.id, t.id
      FROM content_revision r JOIN tag t ON lower(t.name)=lower(c.name)
      WHERE r.category_id=c.id
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- Temporarily disable revision immutability while carrying out this schema migration.
DROP TRIGGER immutable_revision ON content_revision;
DROP TRIGGER immutable_article ON article_detail;

-- Save website-specific values until the content type has been changed to article.
CREATE TEMP TABLE legacy_website_detail ON COMMIT DROP AS
SELECT revision_id, website_url, description_markdown, icon_asset_id FROM website_detail;

DROP TABLE website_detail;

ALTER TABLE content_revision DROP CONSTRAINT content_revision_category_id_content_type_fkey;
ALTER TABLE content_revision DROP COLUMN category_id;
DROP TABLE category;

ALTER TABLE content DROP CONSTRAINT content_content_type_check;
ALTER TABLE content_revision DROP CONSTRAINT content_revision_content_id_content_type_fkey;
UPDATE content_revision SET content_type='article' WHERE content_type='website';
UPDATE content SET content_type='article' WHERE content_type='website';
INSERT INTO article_detail(revision_id, body_markdown)
SELECT w.revision_id,
       concat_ws(E'\n\n', nullif(w.description_markdown, ''),
         CASE WHEN nullif(w.website_url, '') IS NOT NULL
           THEN '[打开原网站](' || w.website_url || ')' END)
FROM legacy_website_detail w;
UPDATE content_revision r
SET cover_asset_id=COALESCE(r.cover_asset_id, w.icon_asset_id)
FROM legacy_website_detail w
WHERE w.revision_id=r.id AND w.icon_asset_id IS NOT NULL;
ALTER TABLE content ADD CONSTRAINT content_content_type_check
  CHECK (content_type IN ('article','work','tutorial','deal'));
ALTER TABLE content_revision ADD CONSTRAINT content_revision_content_id_content_type_fkey
  FOREIGN KEY(content_id,content_type) REFERENCES content(id,content_type);

CREATE TRIGGER immutable_revision BEFORE UPDATE OR DELETE ON content_revision
  FOR EACH ROW EXECUTE FUNCTION prevent_frozen_revision_change();
CREATE TRIGGER immutable_tags BEFORE INSERT OR UPDATE OR DELETE ON revision_tag
  FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE TRIGGER immutable_article BEFORE INSERT OR UPDATE OR DELETE ON article_detail
  FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();

UPDATE site_setting
SET value_jsonb=jsonb_set(value_jsonb, '{homeModules}',
  COALESCE((SELECT jsonb_agg(value) FROM jsonb_array_elements(value_jsonb->'homeModules') value WHERE value #>> '{}' <> 'website'),
    '["article","work","tutorial","deal"]'::jsonb), true)
WHERE setting_key='site.public';
