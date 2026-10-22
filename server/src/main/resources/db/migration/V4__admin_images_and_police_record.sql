ALTER TABLE admin_user ADD COLUMN avatar_asset_id bigint REFERENCES media_asset(id);
UPDATE admin_user SET lock_version=lock_version+1;

UPDATE site_setting
SET value_jsonb = '{"policeNumber":"","loginBackgroundAssetId":null}'::jsonb || value_jsonb,
    schema_version = 3, lock_version = lock_version + 1
WHERE setting_key = 'site.public';
