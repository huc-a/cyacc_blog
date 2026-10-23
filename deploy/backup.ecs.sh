#!/usr/bin/env bash
# ECS variant: keep the host Nginx/certificates in place, back up an idle app.
# The blog may return 502 briefly while its writers are stopped.
set -euo pipefail
cd "$(dirname "$0")"
umask 077
compose=(docker compose -f compose.yaml -f compose.ecs.yaml --env-file .env)
bundle="backup-$(date -u +%Y%m%dT%H%M%SZ)"
"${compose[@]}" build ops
trap '"${compose[@]}" up -d --wait --wait-timeout 300 server web' EXIT
"${compose[@]}" stop web server
"${compose[@]}" run --rm --no-deps ops backup --database caiya --media /media \
  --out "/backups/$bundle" --quiesced
echo "Verified backup bundle: $bundle"
