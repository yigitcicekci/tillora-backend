#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"
TEST_DIR="$(mktemp -d)"
trap 'rm -rf "$TEST_DIR"' EXIT

for script in start-local.sh stop-local.sh scripts/*.sh; do
    bash -n "$script"
done

bash scripts/init-local.sh "$TEST_DIR/first.env" >/dev/null
bash scripts/init-local.sh "$TEST_DIR/second.env" >/dev/null
cp "$TEST_DIR/first.env" "$TEST_DIR/original.env"
if bash scripts/init-local.sh "$TEST_DIR/first.env" >/dev/null 2>&1; then
    printf '%s\n' 'Existing environment was overwritten.' >&2
    exit 1
fi
cmp "$TEST_DIR/first.env" "$TEST_DIR/original.env"
ln -s "$TEST_DIR/absent.env" "$TEST_DIR/link.env"
if bash scripts/init-local.sh "$TEST_DIR/link.env" >/dev/null 2>&1; then
    exit 1
fi
test -n "$(find "$TEST_DIR/first.env" -perm 600 -print)"
if grep -q '__GENERATE_SECRET__' "$TEST_DIR/first.env"; then
    exit 1
fi
if cmp -s "$TEST_DIR/first.env" "$TEST_DIR/second.env"; then
    exit 1
fi

(
    source "$TEST_DIR/first.env"
    test "$SPRING_DATASOURCE_PASSWORD" = "$POSTGRES_PASSWORD"
    test "$TILLORA_JWT_SECRET" != "$TILLORA_PLATFORM_ADMIN_JWT_SECRET"
    test "$(printf '%s' "$TILLORA_JWT_SECRET" | openssl base64 -d -A | wc -c)" -ge 32
    test "${#TILLORA_MANAGEMENT_PASSWORD}" -ge 16
    test "${#TILLORA_BOOTSTRAP_ADMIN_PASSWORD}" -ge 10
    test "$TILLORA_OBJECT_STORAGE_ENABLED" = false
)

unset COMPOSE_PROFILES
docker compose --env-file "$TEST_DIR/first.env" -f docker-compose.yml config --format json |
    jq -e '
        (.services | keys) == ["postgres", "redis", "tillora-api"] and
        .services["tillora-api"].environment.TILLORA_OBJECT_STORAGE_ENABLED == "false" and
        .services["tillora-api"].environment.SPRING_DATASOURCE_PASSWORD == .services.postgres.environment.POSTGRES_PASSWORD and
        (.services["tillora-api"].environment.TILLORA_CORS_ALLOWED_ORIGINS | contains("http://localhost:5173")) and
        ([.services[].ports[]?.host_ip] | all(. == "127.0.0.1"))
    ' >/dev/null

docker compose --env-file "$TEST_DIR/first.env" -f docker-compose.yml \
    --profile storage --profile monitoring config --format json |
    jq -e '(.services | keys) == ["grafana", "minio", "postgres", "prometheus", "redis", "tillora-api"]' >/dev/null

sed '/^TILLORA_OBJECT_STORAGE_/d; /^MINIO_/d; /^GRAFANA_/d' "$TEST_DIR/first.env" > "$TEST_DIR/minimal.env"
docker compose --env-file "$TEST_DIR/minimal.env" -f docker-compose.yml config --quiet

TILLORA_IMAGE=example/tillora:test TILLORA_ADMIN_WEB_IMAGE=example/admin:test \
REDIS_PASSWORD=test-redis-password TILLORA_RELEASE_VERSION=test \
CLOUDFLARE_API_TOKEN=test-token TILLORA_API_DOMAIN=api.example.com TILLORA_ADMIN_DOMAIN=admin.example.com \
docker compose --env-file "$TEST_DIR/minimal.env" -f compose.production.yml config --format json |
    jq -e '
        .services.api.environment.TILLORA_OBJECT_STORAGE_ENABLED == "false" and
        .services.api.environment.TILLORA_OBJECT_STORAGE_ACCESS_KEY == "" and
        .services.api.environment.TILLORA_OBJECT_STORAGE_SECRET_KEY == "" and
        .services.caddy.environment.TILLORA_API_DOMAIN == "api.example.com" and
        .services.caddy.environment.TILLORA_ADMIN_DOMAIN == "admin.example.com" and
        (.services.api | has("ports") | not)
    ' >/dev/null

printf '%s\n' 'Local setup and Compose configuration checks passed.'
