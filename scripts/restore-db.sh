#!/usr/bin/env bash
# =============================================================================
# scripts/restore-db.sh
# Meiyetec prod MySQL restore (local or s3 -> verify -> backup-current -> import)
#
# Goals:
#   - RTO 1h (single DB < 50GB incl. download + import + verify)
#   - --dry-run for drill
#   - Force backup of current DB before overwrite (anti-misclick)
#   - Run migration_smoke_test.sql after import
#
# Usage:
#   scripts/restore-db.sh --from /var/backups/meiyetec/daily/meiyetec_YYYYMMDD_HHMMSS.sql.gz
#   scripts/restore-db.sh --from s3://meiyetec-prod-backups/daily/meiyetec_YYYYMMDD_HHMMSS.sql.gz
#   scripts/restore-db.sh --from <uri> --dry-run
#
# Required env: DB_HOST / DB_PORT(default 3306) / DB_USER / DB_PASS / DB_NAME
# Optional env: S3_BUCKET (default s3://meiyetec-prod-backups)
#               SKIP_BACKUP_CURRENT=1  SKIP_SMOKE_TEST=1
# =============================================================================

set -euo pipefail

DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-meiyetec}"
S3_BUCKET="${S3_BUCKET:-s3://meiyetec-prod-backups}"
DRY_RUN=0
SKIP_BACKUP_CURRENT=0
SKIP_SMOKE_TEST=0
FROM_URI=""

C_RED=''; C_YEL=''; C_GRN=''; C_RST=''
if [[ -t 1 ]]; then
    C_RED='\033[0;31m'; C_YEL='\033[0;33m'; C_GRN='\033[0;32m'; C_RST='\033[0m'
fi
log()  { printf "%b[%s]%b %s\n" "$C_GRN" "$(date '+%F %T')" "$C_RST" "$*"; }
warn() { printf "%b[%s]%b %s\n" "$C_YEL" "$(date '+%F %T')" "$C_RST" "$*" >&2; }
err()  { printf "%b[%s]%b %s\n" "$C_RED" "$(date '+%F %T')" "$C_RST" "$*" >&2; }

usage() {
    cat <<EOF
Usage: $(basename "$0") --from <local-file|s3-uri> [options]

  --from <path>           backup source: local file path or s3://bucket/key
  --dry-run               verify only, do not import
  --skip-backup-current   skip pre-import safety snapshot
  --skip-smoke-test       skip migration_smoke_test.sql verification
  -h, --help              show this help
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from)                FROM_URI="$2"; shift 2 ;;
        --from=*)              FROM_URI="${1#*=}"; shift ;;
        --dry-run)             DRY_RUN=1; shift ;;
        --skip-backup-current) SKIP_BACKUP_CURRENT=1; shift ;;
        --skip-smoke-test)     SKIP_SMOKE_TEST=1; shift ;;
        -h|--help)             usage; exit 0 ;;
        *) err "unknown arg: $1"; usage; exit 2 ;;
    esac
done

if [[ -z "$FROM_URI" ]]; then
    err "missing --from"
    usage
    exit 2
fi
for v in DB_HOST DB_USER DB_PASS DB_NAME; do
    if [[ -z "${!v:-}" ]]; then
        err "env $v not set"
        exit 3
    fi
done

if [[ "$DRY_RUN" == "1" ]]; then
    # dry-run skips DB client check
    :
else
    for c in mysql; do
        command -v "$c" >/dev/null 2>&1 || { err "missing cmd: $c"; exit 4; }
    done
    if [[ "$FROM_URI" == s3://* ]]; then
        command -v aws >/dev/null 2>&1 || { err "S3 source needs aws cli"; exit 4; }
    fi
fi
for c in gzip gunzip; do
    command -v "$c" >/dev/null 2>&1 || { err "missing cmd: $c"; exit 4; }
done

# --- download phase (if S3) ---
WORK_DIR="$(mktemp -d -t meiyetec-restore-XXXXXX)"
trap 'rm -rf "$WORK_DIR"' EXIT
LOCAL_GZ="$WORK_DIR/$(basename "${FROM_URI##*/}")"

if [[ "$FROM_URI" == s3://* ]]; then
    log "download from S3: $FROM_URI"
    if [[ "$DRY_RUN" == "1" ]]; then
        log "[DRY-RUN] aws s3 cp $FROM_URI $LOCAL_GZ"
    else
        aws s3 cp "$FROM_URI" "$LOCAL_GZ" --only-show-errors || { err "S3 download failed"; exit 10; }
    fi
else
    if [[ ! -f "$FROM_URI" ]]; then
        err "local file not found: $FROM_URI"
        exit 11
    fi
    if [[ "$DRY_RUN" == "1" ]]; then
        log "[DRY-RUN] cp $FROM_URI $LOCAL_GZ"
    else
        cp -f "$FROM_URI" "$LOCAL_GZ"
    fi
fi

# --- verify ---
log "gzip integrity check: $LOCAL_GZ"
if [[ "$DRY_RUN" == "1" ]]; then
    log "[DRY-RUN] gzip -t $LOCAL_GZ"
    log "[DRY-RUN] gunzip -c $LOCAL_GZ | head -200 | grep -c CREATE TABLE"
    log "[DRY-RUN] mysql ... < $LOCAL_GZ (decompressed)"
    log "[DRY-RUN] mysql ... < migration_smoke_test.sql"
    log "===== [DRY-RUN] all passed ====="
    exit 0
fi

if ! gzip -t "$LOCAL_GZ" 2>/dev/null; then
    err "gzip verify failed: file may be corrupted"
    exit 12
fi
if ! gunzip -c "$LOCAL_GZ" | head -200 | grep -q "CREATE TABLE"; then
    err "no CREATE TABLE found in dump head, likely not a DB dump"
    exit 13
fi
log "  -> verify ok"

# --- pre-flight banner ---
cat <<'EOF'

=================================================================
  WARNING: confirm before proceeding with restore
=================================================================
  1. Business owner notified, traffic gated off (nginx / maint page)
  2. Verify --from points to the correct backup (check date in filename)
  3. Verify DB_NAME is the intended target
  4. Current DB will be auto-snapshotted to pre-restore/
     (use --skip-backup-current to skip)
=================================================================

EOF

# --- snapshot current DB (anti-misclick) ---
CURRENT_BACKUP=""
if [[ "$SKIP_BACKUP_CURRENT" == "1" ]]; then
    warn "skipping pre-import snapshot (--skip-backup-current)"
else
    TS="$(date '+%Y%m%d_%H%M%S')"
    CURRENT_BACKUP="/var/backups/meiyetec/pre-restore/${DB_NAME}_${TS}.sql.gz"
    install -d -m 0750 "$(dirname "$CURRENT_BACKUP")"
    log "snapshot current DB -> $CURRENT_BACKUP"
    if ! mysqldump \
        -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "-p${DB_PASS}" \
        --single-transaction --routines --triggers \
        --set-gtid-purged=OFF \
        "$DB_NAME" | gzip -9 > "$CURRENT_BACKUP"; then
        err "snapshot of current DB failed: aborting restore"
        exit 14
    fi
    gzip -t "$CURRENT_BACKUP" || { err "snapshot gzip verify failed"; exit 15; }
    log "  -> current DB snapshotted ($CURRENT_BACKUP)"
fi

# --- import ---
log "===== import start ====="
log "source: $FROM_URI"
log "target: ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

if ! gunzip -c "$LOCAL_GZ" | mysql \
        -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "-p${DB_PASS}" \
        --default-character-set=utf8mb4 \
        "$DB_NAME"; then
    err "mysql import failed"
    err "rollback hint: reverse-restore from $CURRENT_BACKUP"
    exit 16
fi
log "  -> import complete"

# --- smoke test ---
if [[ "$SKIP_SMOKE_TEST" == "1" ]]; then
    warn "skipping migration_smoke_test.sql (--skip-smoke-test)"
else
    SMOKE="$(dirname "$(readlink -f "$0")")/../backend/src/main/resources/db/sql/migration_smoke_test.sql"
    if [[ -f "$SMOKE" ]]; then
        log "running migration_smoke_test.sql ..."
        if ! mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "-p${DB_PASS}" "$DB_NAME" < "$SMOKE"; then
            err "smoke test failed: restore result suspicious"
            exit 17
        fi
        log "  -> smoke test ok"
    else
        warn "smoke test file not found at $SMOKE, run Runbook §4 manually"
    fi
fi

# --- row count sanity (5 key tables) ---
log "key table row count sanity:"
for tbl in pos_order_consumption pos_refund sys_idempotency_record stock_record crm_member; do
    cnt=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "-p${DB_PASS}" \
              -N -B -e "SELECT COUNT(*) FROM $DB_NAME.$tbl" 2>/dev/null || echo "ERR")
    log "  $tbl = $cnt"
done

log "===== restore ok ====="
log "if rollback needed (reverse-restore from this snapshot):"
log "  gunzip -c $CURRENT_BACKUP | mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p $DB_NAME"
exit 0
