#!/bin/bash
set -euo pipefail
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  --set=umami_password="$UMAMI_DB_PASSWORD" <<'SQL'
CREATE ROLE umami WITH LOGIN PASSWORD :'umami_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE umami OWNER umami;
SQL
