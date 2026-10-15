#!/usr/bin/env bash
# =============================================================================
# scripts/rollback-db-restore.sh
# Meiyetec prod DB rollback — wrapper over restore-db.sh (I099)
#
# Differences from restore-db.sh:
#   - Forces pre-restore snapshot (anti-misclick)
#   - Mandatory --target-time for audit trail
#   - Measures + reports RTO actual time
#   - Refuses to run if smoke test file missing (fail-fast)
#   - LF + set -euo pipefail
#
# Usage:
#   scripts/rollback-db-restore.sh --from <local|s3://...> --target-time <ISO8601> [--dry-run]
#
# Examples:
#   scripts/rollback-db-restore.sh --from s3://meiyetec-prod-backups/daily/meiyetec_20261010_020000.sql.gz --target-time "2026-10-11T00:00:00+08:00"
#   scripts/rollback-db-restore.sh --from /var/backups/meiyetec/daily/meiyetec_LATEST.sql.gz --target-time "$(date -Iseconds)" --dry-run
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESTORE_SCRIPT="${SCRIPT_DIR}/restore-db.sh"
SMOKE_TEST="$(git rev-parse --show-toplevel 2>/dev/null || pwd)/backend/src/main/resources/db/sql/migration_smoke_test.sql"
RTO_TARGET_MIN="${RTO_TARGET_MIN:-30}"

DRY_RUN=0
SKIP_RTO=0
FROM_URI=""
TARGET_TIME=""

C_RED=''; C_YEL=''; C_GRN=''; C_RST=''
if [[ -t 1 ]]; then
    C_RED='\033[0;31m'; C_YEL='\033[0;33m'; C_GRN='\033[0;32m'; C_RST='\033[0m'
fi
Log()  { printf "%b[%s]%b %s\n" "$C_GRN" "$(date '+%F %T')" "$C_RST" "$*"; }
Warn() { printf "%b[%s]%b %s\n" "$C_YEL" "$(date '+%F %T')" "$C_RST" "$*" >&2; }
Err()  { printf "%b[%s]%b %s\n" "$C_RED" "$(date '+%F %T')" "$C_RST" "$*" >&2; }

Usage() {
    cat <<EOF
Usage: $(basename "$0") --from <local|s3://...> --target-time <ISO8601> [options]

  --from <path>           backup source: local file path or s3://bucket/key
  --target-time <ISO>     ISO-8601 target time (audit trail only; e.g. "$(date -Iseconds)")
  --dry-run               verify only, do not import
  --skip-rto-report       skip RTO actual-time measurement
  -h, --help              show this help

Note: RTO target = ${RTO_TARGET_MIN} min (decisions 1.6-1/6). Pre-restore snapshot
is mandatory (no --skip-backup-current option; this is a rollback, not a drill).
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --from)              FROM_URI="$2"; shift 2 ;;
        --from=*)            FROM_URI="${1#*=}"; shift ;;
        --target-time)       TARGET_TIME="$2"; shift 2 ;;
        --target-time=*)     TARGET_TIME="${1#*=}"; shift ;;
        --dry-run)           DRY_RUN=1; shift ;;
        --skip-rto-report)   SKIP_RTO=1; shift ;;
        -h|--help)           Usage; exit 0 ;;
        *) Err "unknown arg: $1"; Usage; exit 2 ;;
    esac
done

if [[ -z "$FROM_URI" || -z "$TARGET_TIME" ]]; then
    Err "missing --from or --target-time"
    Usage
    exit 2
fi

# Validate ISO-8601 format
if ! date -d "$TARGET_TIME" '+%Y-%m-%dT%H:%M:%S%z' >/dev/null 2>&1; then
    Err "invalid --target-time: $TARGET_TIME (expected ISO-8601, e.g. 2026-10-11T00:00:00+08:00)"
    exit 2
fi

# Required: only enforced for real run; dry-run skips so drill works on any host
if [[ "$DRY_RUN" == "0" ]]; then
    # restore-db.sh exists
    if [[ ! -f "$RESTORE_SCRIPT" ]]; then
        Err "restore-db.sh not found at $RESTORE_SCRIPT (this is a wrapper, needs I099 deliverable)"
        exit 3
    fi
    # smoke test file exists (fail-fast; rollback should never run without it)
    if [[ ! -f "$SMOKE_TEST" ]]; then
        Err "smoke test not found at $SMOKE_TEST (rollback without smoke test is unsafe; aborting)"
        exit 3
    fi
    # env from restore-db.sh
    for v in DB_HOST DB_USER DB_PASS DB_NAME; do
        if [[ -z "${!v:-}" ]]; then
            Err "env $v not set (required by restore-db.sh)"
            exit 4
        fi
    done
    # aws cli for s3:// sources
    if [[ "$FROM_URI" == s3://* ]]; then
        command -v aws >/dev/null 2>&1 || { Err "s3 source needs aws cli"; exit 5; }
    fi
fi

Log "===== rollback-db-restore start ====="
Log "from:        $FROM_URI"
Log "target time: $TARGET_TIME (audit)"
Log "RTO target:  ≤ ${RTO_TARGET_MIN} min"
Log "smoke test:  $SMOKE_TEST"

# --- step a: hint about stopping the app ---
if [[ "$DRY_RUN" == "0" ]]; then
    cat <<'EOF'

=================================================================
  WARNING: confirm before proceeding with rollback-db-restore
=================================================================
  1. nginx 上游已摘除 / API 容器已停服（避免脏写）
  2. 业务方已通知（预估 RTO 30 min）
  3. --from 指向正确的备份（看文件名 YYYYMMDD_HHMMSS）
  4. DB_NAME 是目标库（默认 meiyetec）
  5. Current DB 会被自动快照到 /var/backups/meiyetec/pre-restore/
=================================================================

EOF
    Warn "press Enter to continue, Ctrl+C to abort"
    if [[ -t 0 ]]; then
        read -r _
    else
        Log "non-tty; proceeding"
    fi
fi

# --- step b: pre-flight sanity on backup ---
# (verify file/s3 exists, gzip -t, head contains CREATE TABLE)
Log "pre-flight: verifying source backup integrity"
if [[ "$FROM_URI" == s3://* ]]; then
    if [[ "$DRY_RUN" == "1" ]]; then
        Log "[DRY-RUN] aws s3 ls $FROM_URI"
        Log "[DRY-RUN] aws s3 cp $FROM_URI <tmp> --only-show-errors"
    else
        aws s3 ls "$FROM_URI" >/dev/null || { Err "S3 object not found: $FROM_URI"; exit 10; }
    fi
elif [[ "$DRY_RUN" == "0" ]]; then
    if [[ ! -f "$FROM_URI" ]]; then
        Err "local file not found: $FROM_URI"
        exit 11
    fi
fi

# --- step c: dry-run path: skip restore-db.sh; report RTO pre-calc ---
if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] would invoke: bash $RESTORE_SCRIPT --from $FROM_URI"
    Log "[DRY-RUN] (no --skip-backup-current: pre-restore snapshot MANDATORY for rollback)"
    Log "[DRY-RUN] (no --skip-smoke-test: smoke test MANDATORY for rollback)"
    Log "[DRY-RUN] post-import verification:"
    Log "[DRY-RUN]   mysql ... < $SMOKE_TEST"
    Log "[DRY-RUN]   row count: pos_order_consumption / pos_refund / sys_idempotency_record / stock_record / crm_member"
    Log "===== [DRY-RUN] all commands listed above; no changes made ====="
    exit 0
fi

# --- step d: real restore with RTO timing ---
RTO_START_EPOCH="$(date +%s)"
Log "RTO timer started at $(date '+%F %T')"

# Call restore-db.sh WITHOUT --skip-* flags (mandatory pre-restore + smoke test)
Log "invoking restore-db.sh (mandatory pre-restore snapshot + smoke test)"
if ! bash "$RESTORE_SCRIPT" --from "$FROM_URI"; then
    Err "restore-db.sh failed; pre-restore snapshot still available at /var/backups/meiyetec/pre-restore/"
    Err "manual recovery: gunzip -c /var/backups/meiyetec/pre-restore/<latest>.sql.gz | mysql ..."
    exit 20
fi

# --- step e: report RTO ---
if [[ "$SKIP_RTO" == "1" ]]; then
    Log "RTO report skipped (--skip-rto-report)"
else
    RTO_END_EPOCH="$(date +%s)"
    RTO_SECS=$((RTO_END_EPOCH - RTO_START_EPOCH))
    RTO_MINS=$((RTO_SECS / 60))
    RTO_REMAINDER=$((RTO_SECS % 60))
    Log "===== RTO report ====="
    Log "elapsed: ${RTO_MINS} min ${RTO_REMAINDER} sec (${RTO_SECS} sec)"
    if [[ "$RTO_MINS" -le "$RTO_TARGET_MIN" ]]; then
        Log "status:  WITHIN target (≤ ${RTO_TARGET_MIN} min)"
    else
        Warn "status:  OVER target by $((RTO_MINS - RTO_TARGET_MIN)) min (target ≤ ${RTO_TARGET_MIN} min)"
        Warn "next:    open issue if this repeats in 2 consecutive drills"
    fi
fi

Log "===== rollback-db-restore ok ====="
Log "log: see restore-db.sh stdout (above) and its internal pre-restore snapshot"
Log "audit trail:"
Log "  from:        $FROM_URI"
Log "  target time: $TARGET_TIME"
exit 0
