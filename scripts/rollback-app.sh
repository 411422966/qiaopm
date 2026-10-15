#!/usr/bin/env bash
# =============================================================================
# scripts/rollback-app.sh
# Meiyetec prod app (api container) rollback — sed image tag + compose up + health
#
# Goals (batch 1.6 decisions):
#   - RTO 5min (sed + compose up + 30x health check)
#   - --dry-run for drill
#   - Auto-revert on health-check failure
#   - LF + set -euo pipefail
#
# Usage:
#   scripts/rollback-app.sh --to <git-tag|git-sha> [--dry-run]
#
# Required env (prod):
#   COMPOSE_FILE (default ./docker-compose.yml)
#
# Optional env:
#   HEALTH_URL    (default http://localhost:8080/actuator/health)
#   HEALTH_RETRIES (default 30, each 2s = 60s total)
#   LOG_DIR       (default /var/log/meiyetec)
# =============================================================================

set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-./docker-compose.yml}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
LOG_DIR="${LOG_DIR:-/var/log/meiyetec}"
DRY_RUN=0
TARGET_SHA=""
PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

C_RED=''; C_YEL=''; C_GRN=''; C_RST=''
if [[ -t 1 ]]; then
    C_RED='\033[0;31m'; C_YEL='\033[0;33m'; C_GRN='\033[0;32m'; C_RST='\033[0m'
fi
Log()  { printf "%b[%s]%b %s\n" "$C_GRN" "$(date '+%F %T')" "$C_RST" "$*"; }
Warn() { printf "%b[%s]%b %s\n" "$C_YEL" "$(date '+%F %T')" "$C_RST" "$*" >&2; }
Err()  { printf "%b[%s]%b %s\n" "$C_RED" "$(date '+%F %T')" "$C_RST" "$*" >&2; }

Usage() {
    cat <<EOF
Usage: $(basename "$0") --to <git-tag|git-sha> [options]

  --to <ref>            target git tag or short SHA to rollback to
  --dry-run             verify only, do not modify compose file or restart
  -h, --help            show this help

Examples:
  $(basename "$0") --to v2.0.42
  $(basename "$0") --to ea8b14c
  $(basename "$0") --to v2.0.42 --dry-run
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --to)        TARGET_SHA="$2"; shift 2 ;;
        --to=*)      TARGET_SHA="${1#*=}"; shift ;;
        --dry-run)   DRY_RUN=1; shift ;;
        -h|--help)   Usage; exit 0 ;;
        *) Err "unknown arg: $1"; Usage; exit 2 ;;
    esac
done

if [[ -z "$TARGET_SHA" ]]; then
    Err "missing --to"
    Usage
    exit 2
fi

for c in git sed docker; do
    if ! command -v "$c" >/dev/null 2>&1; then
        Err "missing cmd: $c"
        exit 3
    fi
done

# --- step a: git log --grep=RELEASE find history ---
if ! git rev-parse --git-dir >/dev/null 2>&1; then
    Err "not a git repo; run inside ${PROJECT_ROOT}"
    exit 4
fi
Log "current HEAD: $(git rev-parse --short HEAD)"
Log "target:       ${TARGET_SHA}"
Log "candidate history (last 5 RELEASE commits):"
git log --grep='^chore(release)' --oneline -5 || Warn "no RELEASE-tagged commits found; using --to ref directly"

# --- step b: log to /var/log/meiyetec/rollback-app-*.log ---
TS="$(date '+%Y%m%d_%H%M%S')"
LOG_FILE="${LOG_DIR}/rollback-app-${TS}.log"
if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] would write to: $LOG_FILE"
else
    install -d -m 0750 "$LOG_DIR" 2>/dev/null || { Err "cannot create $LOG_DIR (need sudo)"; exit 5; }
    {
        echo "===== rollback-app ${TS} ====="
        echo "operator:   $(whoami)"
        echo "from HEAD:  $(git rev-parse HEAD)"
        echo "to:         ${TARGET_SHA}"
        echo "compose:    ${COMPOSE_FILE}"
        echo "health url: ${HEALTH_URL}"
    } > "$LOG_FILE"
    Log "log file: $LOG_FILE"
fi

# --- step c: sed docker-compose.yml image tag ---
BACKUP_COMPOSE="${COMPOSE_FILE}.bak.${TS}"
CURRENT_TAG="$(grep -E '^\s*image:\s*meiyetec/api:' "$COMPOSE_FILE" | head -1 | sed -E 's/.*meiyetec\/api:([^ ]+).*/\1/' || true)"
if [[ -z "$CURRENT_TAG" ]]; then
    Err "cannot parse current image tag from $COMPOSE_FILE (expected 'image: meiyetec/api:...')"
    exit 6
fi
Log "current image tag: meiyetec/api:${CURRENT_TAG}"
Log "target  image tag: meiyetec/api:${TARGET_SHA}"

if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] cp ${COMPOSE_FILE} ${BACKUP_COMPOSE}"
    Log "[DRY-RUN] sed -i.bak 's|meiyetec/api:${CURRENT_TAG}|meiyetec/api:${TARGET_SHA}|g' ${COMPOSE_FILE}"
    Log "[DRY-RUN] docker compose -f ${COMPOSE_FILE} up -d --no-deps api"
    Log "[DRY-RUN] curl $HEALTH_URL x ${HEALTH_RETRIES} retries"
    Log "===== [DRY-RUN] all commands listed above; no changes made ====="
    exit 0
fi

Log "backing up compose file: $BACKUP_COMPOSE"
cp -p "$COMPOSE_FILE" "$BACKUP_COMPOSE"

Log "sed image tag in compose file"
if ! sed -i.bak "s|meiyetec/api:${CURRENT_TAG}|meiyetec/api:${TARGET_SHA}|g" "$COMPOSE_FILE"; then
    Err "sed failed; reverting"
    cp -p "$BACKUP_COMPOSE" "$COMPOSE_FILE"
    exit 7
fi
Log "  -> done"

# --- step d: docker compose up -d --no-deps api ---
Log "docker compose up -d --no-deps api"
if ! docker compose -f "$COMPOSE_FILE" up -d --no-deps api 2>&1 | tee -a "$LOG_FILE"; then
    Err "docker compose up failed; reverting compose file + restarting original"
    cp -p "$BACKUP_COMPOSE" "$COMPOSE_FILE"
    docker compose -f "$COMPOSE_FILE" up -d --no-deps api >> "$LOG_FILE" 2>&1 || true
    exit 8
fi

# --- step e: loop curl /actuator/health 30 times (2s each) ---
Log "waiting for health check: $HEALTH_URL (${HEALTH_RETRIES} retries, 2s interval)"
HEALTH_OK=0
for i in $(seq 1 "$HEALTH_RETRIES"); do
    if curl -fsS -o /dev/null -w "%{http_code}" "$HEALTH_URL" 2>/dev/null | grep -q '^200$'; then
        Log "  [${i}/${HEALTH_RETRIES}] HTTP 200 — health OK"
        HEALTH_OK=1
        break
    fi
    Log "  [${i}/${HEALTH_RETRIES}] not yet; sleep 2s"
    sleep 2
done

# --- step f: failure path — revert to original tag + exit 1 ---
if [[ "$HEALTH_OK" -ne 1 ]]; then
    Err "health check failed after ${HEALTH_RETRIES} retries; reverting"
    cp -p "$BACKUP_COMPOSE" "$COMPOSE_FILE"
    docker compose -f "$COMPOSE_FILE" up -d --no-deps api >> "$LOG_FILE" 2>&1 || true
    Err "rollback-of-rollback completed; original tag restored"
    Err "log: $LOG_FILE"
    exit 1
fi

Log "===== rollback ok ====="
Log "from: meiyetec/api:${CURRENT_TAG}"
Log "to:   meiyetec/api:${TARGET_SHA}"
Log "log:  $LOG_FILE"
Log "next: smoke test (DEPLOY-SOP §5); monitor Grafana 5xx for 5min"
exit 0
