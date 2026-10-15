#!/usr/bin/env bash
# =============================================================================
# scripts/cleanup-local-backups.sh
# Trim local /var/backups/meiyetec/daily .sql.gz older than N days
#
# Policy: local 7d (decision 1.3-2)
# Offsite (S3) 30d + archive 1y managed by S3 lifecycle rule (out of scope here)
#
# Triggers: chained in meiyetec-backup.service after backup-db.sh
# Or independent cleanup-local-backups.timer (03:00 daily)
#
# Args:
#   --dry-run    only print, do not delete
#   --days N     retention days (default 7)
#   --dir PATH   backup dir (default /var/backups/meiyetec/daily)
# =============================================================================

set -euo pipefail

DAYS=7
DIR="/var/backups/meiyetec/daily"
DRY_RUN=0

C_GRN=''; C_YEL=''; C_RST=''
if [[ -t 1 ]]; then
    C_GRN='\033[0;32m'; C_YEL='\033[0;33m'; C_RST='\033[0m'
fi
log()  { printf "%b[%s]%b %s\n" "$C_GRN" "$(date '+%F %T')" "$C_RST" "$*"; }
warn() { printf "%b[%s]%b %s\n" "$C_YEL" "$(date '+%F %T')" "$C_RST" "$*" >&2; }

usage() {
    sed -n '2,16p' "$0" | sed 's/^# \?//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)  DRY_RUN=1; shift ;;
        --days)     DAYS="$2"; shift 2 ;;
        --dir)      DIR="$2"; shift 2 ;;
        -h|--help)  usage; exit 0 ;;
        *)          echo "unknown arg: $1" >&2; usage; exit 2 ;;
    esac
done

if ! [[ "$DAYS" =~ ^[0-9]+$ ]] || [[ "$DAYS" -lt 1 ]]; then
    echo "ERROR: --days must be a positive integer" >&2
    exit 3
fi

if [[ ! -d "$DIR" ]]; then
    warn "dir not found, skipping: $DIR"
    exit 0
fi

log "scan: $DIR  retention: ${DAYS}d  dry-run: ${DRY_RUN}"

mapfile -t TARGETS < <(find "$DIR" -maxdepth 1 -type f -name '*.sql.gz' -mtime +"$DAYS" 2>/dev/null | sort)

if [[ ${#TARGETS[@]} -eq 0 ]]; then
    log "no expired files, nothing to do"
    exit 0
fi

log "candidates: ${#TARGETS[@]}"
DELETED=0
FAILED=0
for f in "${TARGETS[@]}"; do
    size=$(stat -c '%s' "$f" 2>/dev/null || stat -f '%z' "$f")
    age_days=$(( ( $(date +%s) - $(stat -c '%Y' "$f" 2>/dev/null || stat -f '%m' "$f") ) / 86400 ))
    if [[ "$DRY_RUN" == "1" ]]; then
        log "  [DRY] $f  (${size} bytes, ${age_days}d old)  -> would delete"
    else
        if rm -f -- "$f"; then
            log "  [DEL] $f  (${size} bytes, ${age_days}d old)"
            DELETED=$((DELETED + 1))
        else
            warn "  [FAIL] $f"
            FAILED=$((FAILED + 1))
        fi
    fi
done

if [[ "$DRY_RUN" == "1" ]]; then
    log "===== [DRY-RUN] done (no actual delete) ====="
    exit 0
fi

log "===== cleanup done: ok $DELETED / fail $FAILED / candidates ${#TARGETS[@]} ====="
[[ "$FAILED" -eq 0 ]] || exit 4
exit 0
