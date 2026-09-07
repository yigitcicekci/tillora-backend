#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
[ -f compose.production.yml ] || exit 2
[ -f .env.production ] || exit 2

env_value() {
  sed -n "s/^$1=//p" .env.production | tail -n 1
}

database="$(env_value POSTGRES_DB)"
username="$(env_value POSTGRES_USER)"
backup_dir="$(env_value TILLORA_BACKUP_DIR)"
retention_days="$(env_value TILLORA_BACKUP_RETENTION_DAYS)"
rclone_remote="$(env_value TILLORA_BACKUP_RCLONE_REMOTE)"
backup_dir="${backup_dir:-/opt/tillora/backups}"
retention_days="${retention_days:-14}"

[ -n "$database" ] || exit 2
[ -n "$username" ] || exit 2
case "$backup_dir" in
  /*) ;;
  *) exit 2 ;;
esac
[ "$backup_dir" != / ] || exit 2
case "$retention_days" in
  ''|*[!0-9]*) exit 2 ;;
esac

mkdir -p "$backup_dir"
backup_file="$backup_dir/tillora-$(date -u +%Y%m%dT%H%M%SZ).dump"
set -- docker compose --env-file .env.production
[ ! -f .deploy.env ] || set -- "$@" --env-file .deploy.env
"$@" -f compose.production.yml exec -T postgres pg_dump --username "$username" --dbname "$database" --format custom --compress 9 > "$backup_file"
[ -s "$backup_file" ] || exit 1

if [ -n "$rclone_remote" ]; then
  command -v rclone >/dev/null 2>&1 || exit 3
  rclone copyto "$backup_file" "${rclone_remote%/}/$(basename "$backup_file")"
fi

find "$backup_dir" -maxdepth 1 -type f -name 'tillora-*.dump' -mtime "+$retention_days" -delete
printf '%s\n' "$backup_file"
