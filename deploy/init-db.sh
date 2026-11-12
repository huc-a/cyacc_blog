#!/usr/bin/env bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v migrator_password="$MIGRATION_PASSWORD" -v runtime_password="$DB_PASSWORD" <<'SQL'
-- pg_trgm powers the trigram search indexes (V10). Extension creation needs
-- the database owner, so it happens here (superuser), not in a migration.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE ROLE blog_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'migrator_password';
CREATE ROLE blog_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'runtime_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO blog_migrator;
GRANT USAGE ON SCHEMA public TO blog_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE blog_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO blog_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE blog_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO blog_runtime;
SQL
