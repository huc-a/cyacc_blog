CREATE TABLE admin_user (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 username varchar(60) NOT NULL UNIQUE, password_hash varchar(100) NOT NULL,
 display_name varchar(80) NOT NULL DEFAULT '菜牙', enabled boolean NOT NULL DEFAULT true,
 last_login_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), lock_version bigint NOT NULL DEFAULT 0
);
CREATE TABLE content (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 content_type varchar(20) NOT NULL CHECK(content_type IN ('article','work','tutorial','website','deal')),
 slug varchar(160) NOT NULL UNIQUE CHECK(slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
 publish_status varchar(20) NOT NULL DEFAULT 'draft' CHECK(publish_status IN ('draft','published','offline')),
 working_revision_id bigint, published_revision_id bigint,
 first_published_at timestamptz, last_published_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 deleted_at timestamptz, lock_version bigint NOT NULL DEFAULT 0,
 UNIQUE(id,content_type),
 CHECK(publish_status <> 'published' OR published_revision_id IS NOT NULL)
);
CREATE TABLE content_revision (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 content_id bigint NOT NULL, content_type varchar(20) NOT NULL,
 revision_no integer NOT NULL CHECK(revision_no > 0),
 revision_state varchar(20) NOT NULL DEFAULT 'editing' CHECK(revision_state IN ('editing','frozen')),
 title varchar(200) NOT NULL DEFAULT '', summary text NOT NULL DEFAULT '',
 change_note varchar(500), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 frozen_at timestamptz, lock_version bigint NOT NULL DEFAULT 0,
 UNIQUE(content_id,revision_no), UNIQUE(id,content_type), UNIQUE(id,content_id),
 FOREIGN KEY(content_id,content_type) REFERENCES content(id,content_type),
 CHECK(revision_state <> 'frozen' OR (length(trim(title))>0 AND frozen_at IS NOT NULL))
);
CREATE UNIQUE INDEX one_editing_revision ON content_revision(content_id) WHERE revision_state='editing';
ALTER TABLE content ADD CONSTRAINT working_revision_owner FOREIGN KEY(working_revision_id,id) REFERENCES content_revision(id,content_id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE content ADD CONSTRAINT published_revision_owner FOREIGN KEY(published_revision_id,id) REFERENCES content_revision(id,content_id) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX public_content_order ON content(last_published_at DESC,id DESC) WHERE publish_status='published' AND deleted_at IS NULL;
CREATE TABLE article_detail (
 revision_id bigint PRIMARY KEY, content_type varchar(20) NOT NULL DEFAULT 'article' CHECK(content_type='article'),
 body_markdown text NOT NULL DEFAULT '',
 FOREIGN KEY(revision_id,content_type) REFERENCES content_revision(id,content_type)
);
CREATE TABLE operation_log (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, operator_id bigint REFERENCES admin_user(id),
 action varchar(60) NOT NULL, target_type varchar(40) NOT NULL, target_id bigint,
 result varchar(20) NOT NULL DEFAULT 'success', request_id uuid NOT NULL,
 detail_jsonb jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION prevent_frozen_revision_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.revision_state='frozen' THEN RAISE EXCEPTION 'Frozen revision is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_revision BEFORE UPDATE OR DELETE ON content_revision FOR EACH ROW EXECUTE FUNCTION prevent_frozen_revision_change();
CREATE FUNCTION prevent_frozen_detail_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid bigint;
BEGIN
 rid := CASE WHEN TG_OP='DELETE' THEN OLD.revision_id ELSE NEW.revision_id END;
 IF EXISTS(SELECT 1 FROM content_revision WHERE id=rid AND revision_state='frozen') THEN
  RAISE EXCEPTION 'Frozen detail is immutable' USING ERRCODE='23514';
 END IF;
 IF TG_OP='UPDATE' AND OLD.revision_id<>NEW.revision_id THEN RAISE EXCEPTION 'Cannot move revision detail' USING ERRCODE='23514'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER immutable_article BEFORE INSERT OR UPDATE OR DELETE ON article_detail FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
