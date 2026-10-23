#!/usr/bin/env bash
# Requires a built caiya-server:$RELEASE_TAG image. Uses only a new isolated volume.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
project="cyacc-media-check-$(date +%s)-$$"
volume="${project}_uploads"
export RELEASE_TAG=${RELEASE_TAG:-local}
export POSTGRES_PASSWORD=test-only MIGRATION_PASSWORD=test-only DB_PASSWORD=test-only
export ADMIN_PASSWORD=test-only SITE_URL=https://example.invalid
compose=(docker compose --env-file /dev/null -p "$project" -f "$root/deploy/compose.yaml")
image="caiya-server:$RELEASE_TAG"
docker image inspect "$image" > /dev/null
cleanup() {
  if [[ $(docker volume inspect --format '{{index .Labels "cyacc.test"}}' "$volume" 2>/dev/null) == media-permissions ]]; then
    docker volume rm "$volume" > /dev/null
  fi
}
trap cleanup EXIT
docker volume create --label cyacc.test=media-permissions "$volume" > /dev/null
docker run --rm --user 0 --network none --entrypoint /bin/sh \
  --mount "type=volume,source=$volume,target=/app/uploads,volume-nocopy" "$image" -ec \
  'printf preserved > /app/uploads/existing; chmod 600 /app/uploads/existing; chmod 500 /app/uploads; chown 0:0 /app/uploads'
if docker run --rm --network none --entrypoint /bin/sh -v "$volume:/app/uploads" "$image" -c 'test -w /app/uploads'; then
  echo 'Expected the root-owned fixture to be unwritable' >&2; exit 1
fi
echo 'PASS reproduced root-owned upload volume failure'
for attempt in 1 2; do
  "${compose[@]}" run -T --rm --no-deps media-init
  docker run --rm --network none --entrypoint /bin/sh -v "$volume:/app/uploads" "$image" -ec \
    'test -w /app/uploads; test -x /app/uploads; probe=$(mktemp /app/uploads/.check.XXXXXXXX); printf check > "$probe"; test "$(cat "$probe")" = check; rm "$probe"'
  docker run --rm --user 0 --network none --entrypoint /bin/sh -v "$volume:/app/uploads:ro" "$image" -ec \
    'test "$(cat /app/uploads/existing)" = preserved; test "$(stat -c %u:%g:%a /app/uploads/existing)" = 0:0:600'
  echo "PASS initialization $attempt: application can write; existing file bytes/owner/mode preserved"
done
