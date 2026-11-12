-- Optional downscaled display variant for raster images (list cards and
-- other small renderings). Originals stay untouched; the variant is a
-- derived cache and may be absent (older rows, GIFs, small images).
ALTER TABLE media_asset ADD COLUMN display_key text;
ALTER TABLE media_asset ADD COLUMN display_width integer;
ALTER TABLE media_asset ADD COLUMN display_height integer;
