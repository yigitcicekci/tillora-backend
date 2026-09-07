#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${1:-$PROJECT_DIR/.env.development}"

if [ -e "$ENV_FILE" ] || [ -L "$ENV_FILE" ]; then
    printf '%s\n' "Existing environment preserved: $ENV_FILE" >&2
    exit 1
fi

command -v openssl >/dev/null
umask 077
TEMP_FILE="$(mktemp "${ENV_FILE}.XXXXXX")"
trap 'rm -f "$TEMP_FILE"' EXIT

while IFS= read -r line || [ -n "$line" ]; do
    if [[ "$line" == *=__GENERATE_SECRET__ ]]; then
        line="${line%%=*}=$(openssl rand -hex 32)"
    fi
    printf '%s\n' "$line"
done < "$PROJECT_DIR/.env.development.example" > "$TEMP_FILE"

ln "$TEMP_FILE" "$ENV_FILE"
printf '%s\n' "Created $ENV_FILE with unique local secrets (mode 600)." \
    'The demo admin password is TILLORA_BOOTSTRAP_ADMIN_PASSWORD in that file.' \
    'Use these credentials only for local development.'
