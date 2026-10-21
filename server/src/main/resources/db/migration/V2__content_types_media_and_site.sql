CREATE TABLE media_asset (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 storage_provider varchar(30) NOT NULL DEFAULT 'local', storage_key text NOT NULL,
 original_name varchar(255) NOT NULL, mime_type varchar(120) NOT NULL,
 size_bytes bigint NOT NULL CHECK(size_bytes>=0), width integer, height integer,
 sha256 varchar(64), created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(storage_provider,storage_key)
);
CREATE TABLE category (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 content_type varchar(20) NOT NULL CHECK(content_type IN ('article','work','tutorial','website','deal')),
 name varchar(60) NOT NULL CHECK(length(trim(name))>0), slug varchar(80) NOT NULL CHECK(slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
 sort_order integer NOT NULL DEFAULT 0 CHECK(sort_order>=0),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(id,content_type),UNIQUE(content_type,name),UNIQUE(content_type,slug)
);
CREATE TABLE tag (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,name varchar(60) NOT NULL CHECK(length(trim(name))>0),
 slug varchar(80) NOT NULL UNIQUE CHECK(slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),created_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX tag_name_normalized ON tag(lower(name));
ALTER TABLE content_revision ADD category_id bigint;
ALTER TABLE content_revision ADD cover_asset_id bigint REFERENCES media_asset(id);
ALTER TABLE content_revision ADD FOREIGN KEY(category_id,content_type) REFERENCES category(id,content_type);
CREATE TABLE revision_tag (
 revision_id bigint NOT NULL REFERENCES content_revision(id),tag_id bigint NOT NULL REFERENCES tag(id),PRIMARY KEY(revision_id,tag_id)
);
CREATE TABLE work_detail (
 revision_id bigint PRIMARY KEY,content_type varchar(20) NOT NULL DEFAULT 'work' CHECK(content_type='work'),
 body_markdown text NOT NULL DEFAULT '',demo_url text NOT NULL DEFAULT '',source_url text NOT NULL DEFAULT '',
 project_status varchar(20) NOT NULL DEFAULT 'developing' CHECK(project_status IN ('planning','developing','released','archived')),
 tech_stack text[] NOT NULL DEFAULT '{}',
 FOREIGN KEY(revision_id,content_type) REFERENCES content_revision(id,content_type)
);
CREATE TABLE tutorial_detail (
 revision_id bigint PRIMARY KEY,content_type varchar(20) NOT NULL DEFAULT 'tutorial' CHECK(content_type='tutorial'),
 intro_markdown text NOT NULL DEFAULT '',difficulty varchar(20) NOT NULL DEFAULT 'beginner' CHECK(difficulty IN ('beginner','intermediate','advanced')),
 prerequisites_markdown text NOT NULL DEFAULT '',FOREIGN KEY(revision_id,content_type) REFERENCES content_revision(id,content_type)
);
CREATE TABLE tutorial_chapter (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,tutorial_revision_id bigint NOT NULL REFERENCES tutorial_detail(revision_id),
 chapter_key uuid NOT NULL,slug varchar(120) NOT NULL CHECK(slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),title varchar(200) NOT NULL,
 body_markdown text NOT NULL DEFAULT '',sort_order integer NOT NULL CHECK(sort_order>=0),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(tutorial_revision_id,chapter_key),UNIQUE(tutorial_revision_id,slug),UNIQUE(id,tutorial_revision_id)
);
CREATE TABLE website_detail (
 revision_id bigint PRIMARY KEY,content_type varchar(20) NOT NULL DEFAULT 'website' CHECK(content_type='website'),
 website_url text NOT NULL DEFAULT '',description_markdown text NOT NULL DEFAULT '',icon_asset_id bigint REFERENCES media_asset(id),
 FOREIGN KEY(revision_id,content_type) REFERENCES content_revision(id,content_type)
);
CREATE TABLE deal_detail (
 revision_id bigint PRIMARY KEY,content_type varchar(20) NOT NULL DEFAULT 'deal' CHECK(content_type='deal'),
 body_markdown text NOT NULL DEFAULT '',source_url text NOT NULL DEFAULT '',claim_url text NOT NULL DEFAULT '',
 benefit_text text NOT NULL DEFAULT '',requirements_markdown text NOT NULL DEFAULT '',starts_at timestamptz,ends_at timestamptz,
 CHECK(ends_at IS NULL OR starts_at IS NULL OR ends_at>starts_at),
 FOREIGN KEY(revision_id,content_type) REFERENCES content_revision(id,content_type)
);
CREATE TABLE revision_media (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,revision_id bigint NOT NULL REFERENCES content_revision(id),
 media_asset_id bigint NOT NULL REFERENCES media_asset(id),usage_type varchar(20) NOT NULL CHECK(usage_type IN ('body','gallery','chapter')),
 chapter_id bigint,alt_text text NOT NULL DEFAULT '',caption text NOT NULL DEFAULT '',sort_order integer NOT NULL DEFAULT 0 CHECK(sort_order>=0),
 CHECK((usage_type='chapter')=(chapter_id IS NOT NULL)),
 FOREIGN KEY(chapter_id,revision_id) REFERENCES tutorial_chapter(id,tutorial_revision_id)
);
CREATE TABLE site_setting (
 setting_key varchar(100) PRIMARY KEY,value_jsonb jsonb NOT NULL,schema_version integer NOT NULL DEFAULT 1,
 lock_version bigint NOT NULL DEFAULT 0,updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE website_check_log (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,content_id bigint NOT NULL,revision_id bigint NOT NULL,
 checked_at timestamptz NOT NULL DEFAULT now(),result varchar(20) NOT NULL CHECK(result IN ('reachable','unreachable','unknown')),
 http_status integer,latency_ms integer,error_summary text,
 FOREIGN KEY(revision_id,content_id) REFERENCES content_revision(id,content_id)
);
CREATE TABLE deal_verification_log (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,content_id bigint NOT NULL,revision_id bigint NOT NULL,
 verified_at timestamptz NOT NULL DEFAULT now(),result varchar(20) NOT NULL CHECK(result IN ('valid','invalid','unknown')),
 verification_method varchar(20) NOT NULL DEFAULT 'manual' CHECK(verification_method IN ('manual','automated')),
 evidence_url text NOT NULL DEFAULT '',note varchar(1000) NOT NULL DEFAULT '',operator_id bigint REFERENCES admin_user(id),
 FOREIGN KEY(revision_id,content_id) REFERENCES content_revision(id,content_id)
);
CREATE TRIGGER immutable_work BEFORE INSERT OR UPDATE OR DELETE ON work_detail FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE TRIGGER immutable_tutorial BEFORE INSERT OR UPDATE OR DELETE ON tutorial_detail FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE TRIGGER immutable_website BEFORE INSERT OR UPDATE OR DELETE ON website_detail FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE TRIGGER immutable_deal BEFORE INSERT OR UPDATE OR DELETE ON deal_detail FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE TRIGGER immutable_tags BEFORE INSERT OR UPDATE OR DELETE ON revision_tag FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE TRIGGER immutable_media BEFORE INSERT OR UPDATE OR DELETE ON revision_media FOR EACH ROW EXECUTE FUNCTION prevent_frozen_detail_change();
CREATE FUNCTION prevent_frozen_chapter_change() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rid bigint;
BEGIN
 rid:=CASE WHEN TG_OP='DELETE' THEN OLD.tutorial_revision_id ELSE NEW.tutorial_revision_id END;
 IF EXISTS(SELECT 1 FROM content_revision WHERE id=rid AND revision_state='frozen') THEN RAISE EXCEPTION 'Frozen chapter is immutable' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND OLD.tutorial_revision_id<>NEW.tutorial_revision_id THEN RAISE EXCEPTION 'Cannot move chapter' USING ERRCODE='23514'; END IF;
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
CREATE TRIGGER immutable_chapters BEFORE INSERT OR UPDATE OR DELETE ON tutorial_chapter FOR EACH ROW EXECUTE FUNCTION prevent_frozen_chapter_change();
INSERT INTO site_setting(setting_key,value_jsonb) VALUES('site.public','{
 "name":"菜牙爱吃菜","description":"记录技术里的发现，也收藏生活中值得停留的片刻。",
 "author":"菜牙","bio":"热爱技术，也认真生活。","github":"","email":"",
 "footer":"风景作底，文字为先。","headline":"山色入笺，日常成诗。",
 "subtitle":"记录技术里的发现，也收藏生活中值得停留的片刻。",
 "background":"mist","backgroundAssetId":null,"glassOpacity":0.66,"articleOpacity":0.88,
 "blur":18,"fontSize":19,"lineHeight":1.95,"homeModules":["article","work","tutorial","website","deal"]
}');
