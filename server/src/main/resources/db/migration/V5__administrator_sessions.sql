ALTER TABLE admin_user ADD COLUMN security_version bigint NOT NULL DEFAULT 0 CHECK (security_version >= 0);
