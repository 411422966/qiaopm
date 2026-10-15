#!/usr/bin/env bash
# =============================================================================
# scripts/backup-db.sh
# Meiyetec prod MySQL full backup (mysqldump + gzip + S3 sync)
#
# Goals (batch 1.3 decisions):
#   - RPO 1h (daily full; max 24h data loss window)
#   - Retention: local 7d / S3 30d / archive 1y
#   - Tool: mysqldump (< 50GB) + aws cli (S3 creds ready)
#
# Triggers:
#   - systemd timer (infra/systemd/meiyetec-backup.timer @ 02:00 daily)
#   - Manual by DBA 2h before release (DEPLOY-SOP §1.1)
#
# Required env:
#   DB_HOST / DB_USER / DB_PASS / DB_NAME
# Optional env:
#   DB_PORT (default 3306) / S3_BUCKET (default s3://meiyetec-prod-backups)
#   LOCAL_DIR (default /var/backups/meiyetec/daily)
#   DRY_RUN=1 / RETENTION_DAYS=7
# =============================================================================

set -euo pipefail

DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-meiyetec}"
S3_BUCKET="${S3_BUCKET:-s3://meiyetec-prod-backups}"
LOCAL_DIR="${LOCAL_DIR:-/var/backups/meiyetec/daily}"
DRY_RUN="${DRY_RUN:-0}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

C_RED=''; C_YEL=''; C_GRN=''; C_RST=''
if [[ -t 1 ]]; then
    C_RED='\033[0;31m'; C_YEL='\033[0;33m'; C_GRN='\033[0;32m'; C_RST='\033[0m'
fi
log()  { printf "%b[%s]%b %s\n" "$C_GRN" "$(date '+%F %T')" "$C_RST" "$*"; }
warn() { printf "%b[%s]%b %s\n" "$C_YEL" "$(date '+%F %T')" "$C_RST" "$*" >&2; }
err()  { printf "%b[%s]%b %s\n" "$C_RED" "$(date '+%F %T')" "$C_RST" "$*" >&2; }

for arg in "$@"; do
    case "$arg" in
        --dry-run)    DRY_RUN=1 ;;
        --help|-h)
            sed -n '2,28p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) err "unknown arg: $arg (--help)"; exit 2 ;;
    esac
done

for v in DB_HOST DB_USER DB_PASS DB_NAME; do
    if [[ -z "${!v:-}" ]]; then
        err "env $v not set"
        exit 3
    fi
done

need_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        err "missing cmd: $1"
        exit 4
    fi
}
if [[ "$DRY_RUN" == "1" ]]; then
    # dry-run skips DB client check (allows verify on any host)
    :
else
    need_cmd mysqldump
    need_cmd aws
fi
need_cmd gzip

TS="$(date '+%Y%m%d_%H%M%S')"
BASE="meiyetec_${TS}.sql"
LOCAL_FILE="${LOCAL_DIR}/${BASE}"
LOCAL_GZ="${LOCAL_FILE}.gz"
S3_KEY="daily/${BASE}.gz"
S3_URI="${S3_BUCKET}/${S3_KEY}"

if [[ "$DRY_RUN" == "1" ]]; then
    log "[DRY-RUN] mkdir -p $LOCAL_DIR"
else
    install -d -m 0750 "$LOCAL_DIR"
fi

log "===== backup start ====="
log "DB:     ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
log "local:  ${LOCAL_GZ}"
log "S3:     ${S3_URI}"

DUMP_CMD=(
    mysqldump
    -h "$DB_HOST"
    -P "$DB_PORT"
    -u "$DB_USER"
    "-p${DB_PASS}"
    --single-transaction
    --routines
    --triggers
    --set-gtid-purged=OFF
    --default-character-set=utf8mb4
    --hex-blob
    "$DB_NAME"
)

if [[ "$DRY_RUN" == "1" ]]; then
    log "[DRY-RUN] ${DUMP_CMD[*]} | gzip > ${LOCAL_GZ}"
    log "[DRY-RUN] aws s3 cp ${LOCAL_GZ} ${S3_URI}"
    log "[DRY-RUN] gzip -t ${LOCAL_GZ}"
    log "[DRY-RUN] find ${LOCAL_DIR} -mtime +${RETENTION_DAYS} -name '*.sql.gz' -print"
    log "===== [DRY-RUN] done ====="
    exit 0
fi

# 1) mysqldump -> gzip (pipe, no temp file on disk)
log "[1/4] mysqldump -> gzip ..."
if ! "${DUMP_CMD[@]}" | gzip -9 > "$LOCAL_GZ"; then
    err "mysqldump or gzip failed"
    rm -f "$LOCAL_GZ"
    exit 5
fi

# 2) verify: gzip integrity + size > 0
log "[2/4] gzip -t verify ..."
if ! gzip -t "$LOCAL_GZ" 2>/dev/null; then
    err "gzip verify failed: $LOCAL_GZ"
    exit 6
fi
SIZE=$(stat -c '%s' "$LOCAL_GZ" 2>/dev/null || stat -f '%z' "$LOCAL_GZ")
if [[ "$SIZE" -le 0 ]]; then
    err "backup size is 0: $LOCAL_GZ"
    exit 7
fi
log "  -> verify ok (size=${SIZE} bytes)"

# 3) sanity: head contains CREATE TABLE
log "[3/4] schema sanity ..."
if ! gunzip -c "$LOCAL_GZ" | head -200 | grep -q "CREATE TABLE"; then
    err "no CREATE TABLE found in dump head, likely empty: $LOCAL_GZ"
    exit 8
fi

# 4) upload to S3
log "[4/4] aws s3 cp ${S3_URI} ..."
if ! aws s3 cp "$LOCAL_GZ" "$S3_URI" --only-show-errors; then
    err "S3 upload failed: $S3_URI"
    err "local copy kept: $LOCAL_GZ (manual handling required)"
    exit 9
fi
log "  -> S3 upload ok"

log "===== backup ok ====="
log "local: ${LOCAL_GZ}"
log "S3:   ${S3_URI}"
log "next: cleanup-local-backups will trim local files older than ${RETENTION_DAYS}d"
exit 0
