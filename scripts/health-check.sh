#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
[ -f compose.production.yml ] || exit 2
[ -f .env.production ] || exit 2

timeout_seconds="${1:-180}"
case "$timeout_seconds" in
  ''|*[!0-9]*) exit 2 ;;
esac

set -- docker compose --env-file .env.production
[ ! -f .deploy.env ] || set -- "$@" --env-file .deploy.env
deadline=$(( $(date +%s) + timeout_seconds ))

while [ "$(date +%s)" -lt "$deadline" ]; do
  if "$@" -f compose.production.yml exec -T api curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1 \
    && "$@" -f compose.production.yml exec -T admin-web wget -q -O /dev/null http://127.0.0.1:8080/healthz >/dev/null 2>&1; then
    exit 0
  fi
  sleep 5
done

printf '%s\n' 'Deployment healthcheck timed out.'
"$@" -f compose.production.yml ps api admin-web caddy
"$@" -f compose.production.yml logs --tail 100 api admin-web caddy
exit 1
