#!/bin/bash

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env.development"

echo "Tillora Backend local development başlatılıyor"
echo ""

if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}.env.development bulunamadı; önce bash scripts/init-local.sh çalıştırın${NC}"
    exit 1
fi

set -a
source "./$ENV_FILE"
set +a

MANAGEMENT_PORT="${MANAGEMENT_PORT:-8081}"
READINESS_URL="http://127.0.0.1:${MANAGEMENT_PORT}/actuator/health/readiness"

for required in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD SPRING_REDIS_HOST SPRING_REDIS_PORT TILLORA_JWT_SECRET TILLORA_PLATFORM_ADMIN_JWT_SECRET TILLORA_COMPANY_PROVISIONING_TOKEN TILLORA_MANAGEMENT_PASSWORD; do
    if [ -z "${!required:-}" ]; then
        echo -e "${RED}${required} .env.development içinde tanımlı değil${NC}"
        exit 1
    fi
done

if [ "${TILLORA_OBJECT_STORAGE_ENABLED:-false}" = "true" ]; then
    for required in TILLORA_OBJECT_STORAGE_ENDPOINT TILLORA_OBJECT_STORAGE_ACCESS_KEY TILLORA_OBJECT_STORAGE_SECRET_KEY TILLORA_OBJECT_STORAGE_BUCKET; do
        if [ -z "${!required:-}" ]; then
            echo -e "${RED}${required} object storage açıkken tanımlı olmalı${NC}"
            exit 1
        fi
    done
    if [ "$TILLORA_OBJECT_STORAGE_ENDPOINT" = "http://minio:9000" ]; then
        export COMPOSE_PROFILES="${COMPOSE_PROFILES:+$COMPOSE_PROFILES,}storage"
    fi
fi

if [[ ",${COMPOSE_PROFILES:-}," == *,storage,* ]]; then
    : "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required for storage}"
    : "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required for storage}"
fi
if [[ ",${COMPOSE_PROFILES:-}," == *,monitoring,* ]]; then
    : "${GRAFANA_ADMIN_PASSWORD:?GRAFANA_ADMIN_PASSWORD is required for monitoring}"
fi

if [ "${TILLORA_BOOTSTRAP_ENABLED:-false}" = "true" ]; then
    for required in TILLORA_BOOTSTRAP_COMPANY_NAME TILLORA_BOOTSTRAP_COMPANY_LEGAL_NAME TILLORA_BOOTSTRAP_COMPANY_TAX_NUMBER TILLORA_BOOTSTRAP_ADMIN_USERNAME TILLORA_BOOTSTRAP_ADMIN_EMAIL TILLORA_BOOTSTRAP_ADMIN_PASSWORD TILLORA_BOOTSTRAP_ADMIN_FIRST_NAME TILLORA_BOOTSTRAP_ADMIN_LAST_NAME; do
        if [ -z "${!required:-}" ]; then
            echo -e "${RED}${required} bootstrap açıkken tanımlı olmalı${NC}"
            exit 1
        fi
    done
    if ! [[ "$TILLORA_BOOTSTRAP_COMPANY_TAX_NUMBER" =~ ^[0-9]{10,11}$ ]]; then
        echo -e "${RED}TILLORA_BOOTSTRAP_COMPANY_TAX_NUMBER 10 veya 11 haneli numerik olmalı${NC}"
        exit 1
    fi
    if [ "${#TILLORA_BOOTSTRAP_ADMIN_PASSWORD}" -lt 10 ]; then
        echo -e "${RED}TILLORA_BOOTSTRAP_ADMIN_PASSWORD en az 10 karakter olmalı${NC}"
        exit 1
    fi
fi

echo "Docker daemon kontrol ediliyor"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Docker daemon çalışmıyor veya erişilemiyor${NC}"
    exit 1
fi
echo -e "${GREEN}Docker hazır${NC}"
echo ""

echo "Docker servisleri başlatılıyor"
docker compose --env-file "$ENV_FILE" up -d --build
echo -e "${GREEN}Docker servisleri başlatıldı${NC}"
echo ""

echo "API readiness bekleniyor"
READY=0
for _ in $(seq 1 45); do
    if curl -fsS "$READINESS_URL" > /dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 2
done

if [ "$READY" -ne 1 ]; then
    echo -e "${RED}API readiness başarısız${NC}"
    echo "Logları kontrol edin: docker compose --env-file .env.development logs --tail=200 tillora-api"
    exit 1
fi

echo -e "${GREEN}API hazır: ${READINESS_URL}${NC}"
echo ""
echo -e "${YELLOW}Servisler:${NC}"
docker compose --env-file "$ENV_FILE" ps
echo ""
echo -e "${YELLOW}API logları görüntüleniyor. Çıkmak için CTRL+C kullanın; servisler çalışmaya devam eder.${NC}"
echo ""
docker compose --env-file "$ENV_FILE" logs -f tillora-api
