#!/bin/bash

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env.development"

echo "Tillora Backend local development durduruluyor"
echo ""

if [ ! -f "$ENV_FILE" ]; then
    echo -e "${YELLOW}.env.development bulunamadı, docker compose varsayılanlarıyla durdurulacak${NC}"
    docker compose --profile '*' down
else
    docker compose --env-file "$ENV_FILE" --profile '*' down
fi

echo ""
echo -e "${GREEN}Local servisler durduruldu${NC}"
