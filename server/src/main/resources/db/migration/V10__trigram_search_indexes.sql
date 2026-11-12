-- Trigram indexes for the ILIKE '%q%' title/summary search. Creating the
-- pg_trgm extension requires the database owner, which the production
-- migration role is not; deploy/init-db.sh (run by the superuser) and the
-- dev database script install it. This migration therefore only builds the
-- indexes when the gin_trgm_ops operator class is actually visible on the
-- connection's search_path (the test suite migrates random scratch schemas
-- where it is not), and silently skips otherwise (search keeps working
-- with a sequential scan).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_opclass o
    JOIN pg_namespace n ON n.oid = o.opcnamespace
    JOIN pg_am a ON a.oid = o.opcmethod
    WHERE o.opcname = 'gin_trgm_ops' AND a.amname = 'gin'
      AND n.nspname = ANY (current_schemas(false))
  ) THEN
    CREATE INDEX IF NOT EXISTS content_revision_title_trgm_idx
      ON content_revision USING gin (title gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS content_revision_summary_trgm_idx
      ON content_revision USING gin (summary gin_trgm_ops);
  END IF;
END $$;
