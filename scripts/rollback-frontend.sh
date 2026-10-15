#!/usr/bin/env bash
# =============================================================================
# scripts/rollback-frontend.sh
# Meiyetec prod frontend rollback — S3 sync prev-build + CloudFront invalidate
#
# Goals (batch 1.6 decisions):
#   - RTO 10min (s3 sync + cloudfront invalidate + curl verify)
#   - --dry-run for drill
#   - LF + set -euo pipefail
#
# Usage:
#   scripts/rollback-frontend.sh --prev-build <s3-path> [--dry-run]
#
# Examples:
#   scripts/rollback-frontend.sh --prev-build s3://meiyetec-static/v2.0.42
#   scripts/rollback-frontend.sh --prev-build s3://meiyetec-static/v2.0.42 --dry-run
#
# Required env:
#   FRONTEND_BASE_URL (default https://meiyetec.com)
#   S3_CURRENT_BUCKET  (default s3://meiyetec-static/current)
#   CF_DISTRIBUTION_ID (default E123ABC)
# =============================================================================

set -euo pipefail

FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-https://meiyetec.com}"
S3_CURRENT_BUCKET="${S3_CURRENT_BUCKET:-s3://meiyetec-static/current}"
CF_DISTRIBUTION_ID="${CF_DISTRIBUTION_ID:-E123ABC}"
DRY_RUN=0
PREV_BUILD=""

C_RED=''; C_YEL=''; C_GRN=''; C_RST=''
if [[ -t 1 ]]; then
    C_RED='\033[0;31m'; C_YEL='\033[0;33m'; C_GRN='\033[0;32m'; C_RST='\033[0m'
fi
Log()  { printf "%b[%s]%b %s\n" "$C_GRN" "$(date '+%F %T')" "$C_RST" "$*"; }
Warn() { printf "%b[%s]%b %s\n" "$C_YEL" "$(date '+%F %T')" "$C_RST" "$*" >&2; }
Err()  { printf "%b[%s]%b %s\n" "$C_RED" "$(date '+%F %T')" "$C_RST" "$*" >&2; }

Usage() {
    cat <<EOF
Usage: $(basename "$0") --prev-build <s3-path> [options]

  --prev-build <s3-path>  S3 path of previous build (e.g. s3://meiyetec-static/v2.0.42)
  --dry-run               verify only, do not sync or invalidate
  -h, --help              show this help
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --prev-build)   PREV_BUILD="$2"; shift 2 ;;
        --prev-build=*) PREV_BUILD="${1#*=}"; shift ;;
        --dry-run)      DRY_RUN=1; shift ;;
        -h|--help)      Usage; exit 0 ;;
        *) Err "unknown arg: $1"; Usage; exit 2 ;;
    esac
done

if [[ -z "$PREV_BUILD" ]]; then
    Err "missing --prev-build"
    Usage
    exit 2
fi

if [[ "$DRY_RUN" == "0" ]]; then
    for c in aws curl; do
        command -v "$c" >/dev/null 2>&1 || { Err "missing cmd: $c"; exit 3; }
    done
fi

Log "===== rollback-frontend start ====="
Log "prev-build:    $PREV_BUILD"
Log "current sync:  $S3_CURRENT_BUCKET/"
Log "cf dist id:    $CF_DISTRIBUTION_ID"
Log "frontend url:  $FRONTEND_BASE_URL"

# --- step a: aws s3 ls verify prev-build exists ---
Log "verify prev-build exists in S3"
if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] aws s3 ls ${PREV_BUILD}/"
else
    if ! aws s3 ls "${PREV_BUILD}/" >/dev/null 2>&1; then
        Err "prev-build not found: ${PREV_BUILD}/"
        Err "fallback: list all builds with: aws s3 ls s3://meiyetec-static/ | sort -k1,2"
        exit 10
    fi
    Log "  -> ok"
fi

# --- step b: aws s3 sync prev -> current ---
if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] aws s3 sync ${PREV_BUILD}/ ${S3_CURRENT_BUCKET}/ --delete"
else
    Log "aws s3 sync ${PREV_BUILD}/ -> ${S3_CURRENT_BUCKET}/ --delete"
    if ! aws s3 sync "${PREV_BUILD}/" "${S3_CURRENT_BUCKET}/" --delete --only-show-errors 2>&1; then
        Err "s3 sync failed"
        exit 11
    fi
    Log "  -> sync ok"
fi

# --- step c: cloudfront create-invalidation ---
if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] aws cloudfront create-invalidation --distribution-id $CF_DISTRIBUTION_ID --paths '/*'"
else
    Log "aws cloudfront create-invalidation --paths '/*'"
    if ! aws cloudfront create-invalidation \
            --distribution-id "$CF_DISTRIBUTION_ID" \
            --paths '/*' >/dev/null; then
        Err "cloudfront invalidation failed"
        Err "fallback: login AWS Console -> CloudFront -> $CF_DISTRIBUTION_ID -> Invalidations -> Create -> /*"
        exit 12
    fi
    Log "  -> invalidation triggered (ETA 30s-5min)"
fi

# --- step d: curl verify index.html md5 matches prev-build ---
# (skip in dry-run; would still hit network)
if [[ "$DRY_RUN" == "1" ]]; then
    Log "[DRY-RUN] curl -fsS $FRONTEND_BASE_URL/ | md5sum"
    Log "[DRY-RUN] aws s3 cp ${PREV_BUILD}/index.html - | md5sum"
    Log "[DRY-RUN] (compare two md5; must match)"
    Log "===== [DRY-RUN] all commands listed above; no changes made ====="
    exit 0
fi

# Brief settle period for CloudFront invalidation
Log "wait 10s for CloudFront edge propagation"
sleep 10

Log "verify index.html md5"
LOCAL_MD5="$(curl -fsS "${FRONTEND_BASE_URL}/" 2>/dev/null | md5sum | awk '{print $1}')" || {
    Err "curl frontend url failed: $FRONTEND_BASE_URL/"
    exit 13
}
PREV_MD5="$(aws s3 cp "${PREV_BUILD}/index.html" - 2>/dev/null | md5sum | awk '{print $1}')" || {
    Err "aws s3 cp prev-build index.html failed: ${PREV_BUILD}/index.html"
    exit 13
}
Log "  current/ : $LOCAL_MD5"
Log "  prev-bld : $PREV_MD5"

if [[ "$LOCAL_MD5" != "$PREV_MD5" ]]; then
    Err "md5 mismatch: current/ does not match prev-build"
    Err "CDN may still be propagating; wait 60s and re-run verify, or check CloudFront invalidation status"
    exit 14
fi

Log "===== rollback-frontend ok ====="
Log "prev-build: $PREV_BUILD"
Log "next: smoke test (DEPLOY-SOP §5); monitor SPA console errors for 5min"
exit 0
