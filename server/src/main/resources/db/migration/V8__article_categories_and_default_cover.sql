-- Restore independent article categories after V7 unified the old taxonomy.
CREATE TABLE category (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name varchar(60) NOT NULL CHECK(length(trim(name))>0),
  slug varchar(80) NOT NULL UNIQUE CHECK(slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  sort_order integer NOT NULL DEFAULT 0 CHECK(sort_order>=0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(name)
);
CREATE UNIQUE INDEX category_name_normalized ON category(lower(name));
INSERT INTO category(name,slug,sort_order) VALUES
  (U&'\4f5c\54c1','works',10),
  (U&'\6559\7a0b','tutorials',20),
  (U&'\7f8a\6bdb','deals',30);
ALTER TABLE content_revision ADD COLUMN category_id bigint REFERENCES category(id);
-- This is a metadata-only backfill of frozen revisions; restore immutability immediately after it.
DROP TRIGGER immutable_revision ON content_revision;
UPDATE content_revision r
SET category_id=c.id
FROM category c
JOIN tag t ON lower(t.name)=lower(c.name)
JOIN revision_tag rt ON rt.tag_id=t.id
WHERE r.id=rt.revision_id AND r.content_type='article';
CREATE TRIGGER immutable_revision BEFORE UPDATE OR DELETE ON content_revision
  FOR EACH ROW EXECUTE FUNCTION prevent_frozen_revision_change();
UPDATE site_setting
SET value_jsonb=value_jsonb || '{"defaultCoverAssetId":null}'::jsonb
WHERE setting_key='site.public';
