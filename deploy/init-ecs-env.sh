#!/usr/bin/env bash
# Run on ECS only. Refuse to overwrite existing production credentials.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -e .env || -L .env ]]; then
  echo 'deploy/.env already exists; nothing was changed.' >&2
  exit 1
fi
command -v openssl >/dev/null
umask 077
postgres_password="$(openssl rand -hex 24)"
migration_password="$(openssl rand -hex 24)"
runtime_password="$(openssl rand -hex 24)"
admin_password="$(openssl rand -hex 24)"
set -o noclobber
cat > .env <<EOF
COMPOSE_PROJECT_NAME=cyacc-blog
POSTGRES_PASSWORD=$postgres_password
MIGRATION_PASSWORD=$migration_password
DB_PASSWORD=$runtime_password
ADMIN_USERNAME=admin
ADMIN_PASSWORD=$admin_password
SITE_URL=https://cyacc.cn
RELEASE_TAG=ecs-2026-10-23
COOKIE_SECURE=true
BIND_ADDRESS=127.0.0.1
HTTP_PORT=8088
BACKUP_DIR=/var/backups/cyacc
UMAMI_URL=
UMAMI_WEBSITE_ID=
UMAMI_TOKEN=
UMAMI_SCRIPT_URL=
EOF
chmod 600 .env
unset postgres_password migration_password runtime_password admin_password
echo 'Created deploy/.env for cyacc.cn with four random secrets (mode 600).'
echo 'Keep the file private. The admin password is available there; do not paste it into chat.'
