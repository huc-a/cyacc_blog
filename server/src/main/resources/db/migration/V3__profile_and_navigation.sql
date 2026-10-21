-- Add settings without replacing existing content, appearance or contact values.
UPDATE site_setting
SET value_jsonb = '{"avatarAssetId":null,"icpNumber":"","navTopOpacity":0,"navScrolledOpacity":0.92}'::jsonb || value_jsonb,
    schema_version = 2, lock_version = lock_version + 1
WHERE setting_key = 'site.public';
