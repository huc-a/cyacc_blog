#!/usr/bin/env bash
# Consistent PostgreSQL dump plus recovery credentials, readable only by root.
set -euo pipefail
umask 077
cd /opt/cyacc/umami
exec 9>/run/lock/cyacc-umami-backup.lock
flock -n 9 || { echo 'Another Umami backup is running'; exit 1; }
bundle="/var/backups/cyacc-umami/backup-$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 0700 "$bundle"
docker compose --env-file .env exec -T db pg_dump -U postgres -d umami --format=custom > "$bundle/umami.dump"
docker compose --env-file .env exec -T db pg_restore --list < "$bundle/umami.dump" > "$bundle/contents.txt"
cp -p .env compose.yaml init-db.sh admin-credentials.json reader-credentials.json integration.json "$bundle/"
sha256sum "$bundle/umami.dump" > "$bundle/SHA256SUMS"
touch "$bundle/COMPLETE"
echo "Umami backup completed: $bundle"
