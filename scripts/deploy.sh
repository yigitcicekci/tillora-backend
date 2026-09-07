#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
[ "$(pwd)" = /opt/tillora ] || exit 2
[ -f compose.production.yml ] || exit 2
[ -f Dockerfile.caddy ] || exit 2
[ -f Caddyfile ] || exit 2
[ -f .env.production ] || exit 2

env_value() {
  sed -n "s/^$1=//p" .env.production | tail -n 1
}

redis_password="$(env_value REDIS_PASSWORD)"
[ -n "$redis_password" ] || exit 2

new_image="${1:-}"
case "$new_image" in
  ghcr.io/*:*) ;;
  *) exit 2 ;;
esac

configured_admin_image="$(env_value TILLORA_ADMIN_WEB_IMAGE)"
new_admin_image="${2:-$configured_admin_image}"
case "$new_admin_image" in
  ghcr.io/*:*) ;;
  *) exit 2 ;;
esac

candidate_compose() {
  TILLORA_IMAGE="$new_image" TILLORA_ADMIN_WEB_IMAGE="$new_admin_image" \
    docker compose --env-file .env.production -f compose.production.yml "$@"
}

previous_image=""
previous_admin_image=""
if [ -f .deploy.env ]; then
  previous_image="$(sed -n 's/^TILLORA_IMAGE=//p' .deploy.env | tail -n 1)"
  previous_admin_image="$(sed -n 's/^TILLORA_ADMIN_WEB_IMAGE=//p' .deploy.env | tail -n 1)"
fi
if [ -z "$previous_image" ]; then
  container_id="$(candidate_compose ps -q api 2>/dev/null || true)"
  if [ -n "$container_id" ]; then
    previous_image="$(docker inspect --format '{{.Config.Image}}' "$container_id")"
  fi
fi
if [ -z "$previous_admin_image" ]; then
  admin_container_id="$(candidate_compose ps -q admin-web 2>/dev/null || true)"
  if [ -n "$admin_container_id" ]; then
    previous_admin_image="$(docker inspect --format '{{.Config.Image}}' "$admin_container_id")"
  fi
fi

candidate_compose config --quiet
candidate_compose pull api admin-web postgres redis
candidate_compose build caddy
candidate_compose run --rm --no-deps caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
postgres_id="$(candidate_compose ps -q postgres 2>/dev/null || true)"
if [ -n "$postgres_id" ]; then
  ./scripts/backup-postgres.sh
fi

deploy_file="$(mktemp .deploy.env.XXXXXX)"
trap 'rm -f "$deploy_file"' EXIT HUP INT TERM
printf 'TILLORA_IMAGE=%s\nTILLORA_ADMIN_WEB_IMAGE=%s\n' "$new_image" "$new_admin_image" > "$deploy_file"
mv "$deploy_file" .deploy.env
trap - EXIT HUP INT TERM

report_deploy_failure() {
  printf '%s\n' 'Candidate deployment diagnostics:'
  docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml ps api admin-web caddy || true
  api_id="$(docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml ps -q api 2>/dev/null || true)"
  if [ -n "$api_id" ]; then
    docker inspect "$api_id" --format 'api_state={{json .State}}' || true
  fi
  admin_web_id="$(docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml ps -q admin-web 2>/dev/null || true)"
  if [ -n "$admin_web_id" ]; then
    docker inspect "$admin_web_id" --format 'admin_web_state={{json .State}}' || true
  fi
  docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml logs --no-color --tail=300 api || true
  docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml logs --no-color --tail=200 admin-web || true
  docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml logs --no-color --tail=100 caddy || true
}

if ! docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml up -d --no-build; then
  report_deploy_failure
elif ! ./scripts/health-check.sh 240; then
  report_deploy_failure
else
  docker image prune -f --filter until=168h >/dev/null
  exit 0
fi

rollback_required=false
if [ -n "$previous_image" ] && [ "$previous_image" != "$new_image" ]; then
  rollback_required=true
fi
if [ -n "$previous_admin_image" ] && [ "$previous_admin_image" != "$new_admin_image" ]; then
  rollback_required=true
fi

if [ "$rollback_required" = true ]; then
  rollback_image="${previous_image:-$new_image}"
  rollback_admin_image="${previous_admin_image:-$new_admin_image}"
  printf 'TILLORA_IMAGE=%s\nTILLORA_ADMIN_WEB_IMAGE=%s\n' "$rollback_image" "$rollback_admin_image" > .deploy.env
  if ! docker compose --env-file .env.production --env-file .deploy.env -f compose.production.yml up -d --no-build; then
    printf '%s\n' 'Rollback start failed.'
    report_deploy_failure
    exit 1
  fi
  if ! ./scripts/health-check.sh 180; then
    printf '%s\n' 'Rollback healthcheck failed.'
    report_deploy_failure
    exit 1
  fi
fi

exit 1
