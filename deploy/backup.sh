#!/usr/bin/env bash
# Run from any directory. The bundle contains password hashes; restrict access.
set -euo pipefail
cd "$(dirname "$0")"
bundle="backup-$(date -u +%Y%m%dT%H%M%SZ)"
docker compose build ops
docker compose stop nginx web server
trap 'docker compose up -d server web nginx' EXIT
docker compose run --rm --no-deps ops backup --database caiya --media /media \
  --out "/backups/$bundle" --quiesced
echo "Verified backup: $bundle"
