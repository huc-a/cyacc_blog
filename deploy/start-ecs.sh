#!/usr/bin/env bash
# Run from the existing release: reuse .env and volumes, stop on the first error.
set -euo pipefail
cd "$(dirname "$0")"
trap 'status=$?; printf "ECS startup stopped (exit %s). Resolve the error above before retrying; do not switch Nginx yet.\n" "$status" >&2; exit "$status"' ERR
[[ -f .env ]] || { echo 'deploy/.env is missing. Initialize it only for a new deployment.' >&2; exit 1; }
compose=(docker compose -f compose.yaml -f compose.ecs.yaml --env-file .env)
"${compose[@]}" config --quiet
"${compose[@]}" pull db
"${compose[@]}" build migrate web
"${compose[@]}" up -d --pull never --wait --wait-timeout 300
"${compose[@]}" ps -a
curl -fsS --connect-timeout 5 --max-time 20 -o /dev/null http://127.0.0.1:8089/api/public/site
curl -fsS --connect-timeout 5 --max-time 20 -o /dev/null http://127.0.0.1:8088/
echo 'Application health checks passed. Continue with database verification before switching Nginx.'
