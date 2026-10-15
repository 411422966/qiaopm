# ============================================================
# Recovery Script: First Batch (5 Controllers) — DRY-RUN SAFE
# ============================================================
#
# Scope: Restore 5 disabled controllers identified in
#        DISABLED_CONTROLLER_RECOVERY_AUDIT.md (A-class, P0)
#
# Affected files (5 renames):
#   - AttendanceController.java.disabled           -> AttendanceController.java
#   - AttendanceConfigController.java.disabled     -> AttendanceConfigController.java
#   - LeaveRequestController.java.disabled         -> LeaveRequestController.java
#   - CrmFollowUpTaskController.java.disabled      -> CrmFollowUpTaskController.java
#   - CrmReviewController.java.disabled            -> CrmReviewController.java
#   - CrmReviewTemplateController.java.disabled    -> CrmReviewTemplateController.java
#   (CrmReviewTemplate merged into same batch — same FE page)
#
# Pre-condition: compile baseline must be fixed FIRST
#   (CrmSyncLog.java Lombok setter issue — see Phase 0)
#
# Usage:
#   ./recovery-first-batch.ps1            # DRY-RUN (default)
#   ./recovery-first-batch.ps1 -Execute   # ACTUAL EXECUTION (irreversible!)
#
# Rollback: ./recovery-first-batch-rollback.ps1
# ============================================================

param(
    [switch]$Execute = $false
)

$ErrorActionPreference = 'Stop'
$RootPath = 'd:\QiaoPM'

# ----- 颜色输出 -----
function Write-Step($msg)  { Write-Host "[STEP]  $msg" -ForegroundColor Cyan }
function Write-OK($msg)    { Write-Host "[OK]    $msg" -ForegroundColor Green }
function Write-Warn($msg)  { Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Write-Err($msg)   { Write-Host "[ERROR] $msg" -ForegroundColor Red }
function Write-Dry($msg)   { Write-Host "[DRY]   $msg (not executed)" -ForegroundColor DarkYellow }

# ----- 1. 5 个待恢复 Controller -----
$Targets = @(
    'AttendanceController',
    'AttendanceConfigController',
    'LeaveRequestController',
    'CrmFollowUpTaskController',
    'CrmReviewController',
    'CrmReviewTemplateController'
)

Write-Host ""
Write-Host "============================================================"
Write-Host " Recovery Script: First Batch (5 Controllers)" -ForegroundColor White
Write-Host " Mode: $(if ($Execute) {'EXECUTE (irreversible)'} else {'DRY-RUN'})"
Write-Host " Root: $RootPath"
Write-Host " Targets: $($Targets.Count) files"
Write-Host "============================================================"
Write-Host ""

# ============================================================
# Phase 0: 编译基线修复（CrmSyncLog Lombok）
# ============================================================
Write-Step "Phase 0: Verify compile baseline (CrmSyncLog Lombok setter)"
Write-Host ""
Write-Host "  Issue:  CrmSyncScheduler.java:120,121,124 cannot find"
Write-Host "          CrmSyncLog.setRecordsTotal/setRecordsFiltered/setSyncToken"
Write-Host "  File:   backend/src/main/java/com/meiyetec/entity/CrmSyncLog.java"
Write-Host "          (fields exist at lines 42-47, but Lombok @Data not generating setters)"
Write-Host ""
Write-Host "  Fix options (choose one):"
Write-Host "    a) mvn clean compile -U        # re-process annotations"
Write-Host "    b) IDE: invalidate caches + rebuild project"
Write-Host "    c) Add explicit @Setter/@Getter to CrmSyncLog (or replace @Data)"
Write-Host ""
Write-Host "  Verification: mvn compile -pl backend"
Write-Host "  Expected: BUILD SUCCESS"
Write-Host ""
Write-Host "  *** DO NOT proceed to Phase 1 if Phase 0 fails ***"
Write-Host ""

# ============================================================
# Phase 1: 重命名 5 个 .java.disabled -> .java
# ============================================================
Write-Step "Phase 1: Rename 5 disabled controllers to enabled"

$SourceDir = Join-Path $RootPath 'backend\src\main\java\com\meiyetec\controller'

foreach ($name in $Targets) {
    $disabled = Join-Path $SourceDir "$name.java.disabled"
    $enabled  = Join-Path $SourceDir "$name.java"

    if (-not (Test-Path $disabled)) {
        Write-Warn "Missing: $disabled"
        continue
    }
    if (Test-Path $enabled) {
        Write-Warn "Already enabled: $enabled"
        continue
    }

    if ($Execute) {
        Rename-Item -Path $disabled -NewName "$name.java"
        Write-OK "Renamed: $name"
    } else {
        Write-Dry "Would rename: $name.java.disabled -> $name.java"
    }
}

Write-Host ""

# ============================================================
# Phase 2: 编译验证
# ============================================================
Write-Step "Phase 2: Verify compilation"
Write-Host ""

$Cmd = "mvn -pl backend clean compile"
if ($Execute) {
    Write-Host "  Running: $Cmd"
    Push-Location $RootPath
    try {
        & mvn -pl backend clean compile
        if ($LASTEXITCODE -eq 0) {
            Write-OK "Compile SUCCESS"
        } else {
            Write-Err "Compile FAILED (exit code: $LASTEXITCODE)"
            Write-Err "ROLLBACK REQUIRED — run recovery-first-batch-rollback.ps1"
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Dry "Would run: $Cmd"
}

Write-Host ""

# ============================================================
# Phase 3: 测试验证（如果存在）
# ============================================================
Write-Step "Phase 3: Run targeted unit tests"

$TestClasses = @(
    'AttendEntityFieldsTest',
    'AttendIsLateAlgorithmTest',
    'CrmEntityFieldsTest'
)
$TestCmd = "mvn -pl backend test -Dtest=$($TestClasses -join ',')"
if ($Execute) {
    Push-Location $RootPath
    try {
        & mvn -pl backend test -Dtest="$($TestClasses -join ',')"
        if ($LASTEXITCODE -eq 0) {
            Write-OK "Targeted tests passed"
        } else {
            Write-Warn "Some tests failed — review before promoting"
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Dry "Would run: $TestCmd"
}

Write-Host ""

# ============================================================
# Phase 4: 启动后端（手工，不在本脚本）
# ============================================================
Write-Step "Phase 4: Backend restart + smoke test (manual)"
Write-Host ""
Write-Host "  Start:   cd backend; mvn spring-boot:run"
Write-Host "  Wait:    'Started MeiyetecApplication'"
Write-Host ""
Write-Host "  Smoke test (with valid token):"
Write-Host "    curl -H 'Authorization: Bearer <token>' http://localhost:8080/attendance/today?employeeId=1"
Write-Host "    curl -H 'Authorization: Bearer <token>' http://localhost:8080/attendance/config/active"
Write-Host "    curl -H 'Authorization: Bearer <token>' http://localhost:8080/attendance/leave/pending-count?storeId=1"
Write-Host "    curl -H 'Authorization: Bearer <token>' http://localhost:8080/crm/care/follow/task/pending-count"
Write-Host "    curl -H 'Authorization: Bearer <token>' http://localhost:8080/crm/care/review/list"
Write-Host "    curl -H 'Authorization: Bearer <token>' http://localhost:8080/crm/care/review/tpl/list"
Write-Host ""
Write-Host "  Expected: HTTP 200 with JSON body (not 404/500)"
Write-Host ""

# ============================================================
# Phase 5: 浏览器验证（手工）
# ============================================================
Write-Step "Phase 5: Browser verification (manual)"
Write-Host ""
Write-Host "  Routes to test (login as SUPER_ADMIN or ADMIN with perms):"
Write-Host "    http://localhost:5173/attendance"
Write-Host "    http://localhost:5173/attendance/records"
Write-Host "    http://localhost:5173/attendance/leave"
Write-Host "    http://localhost:5173/attendance/statistics"
Write-Host "    http://localhost:5173/attendance/config"
Write-Host "    http://localhost:5173/crm/care/follow-task"
Write-Host "    http://localhost:5173/crm/care/review"
Write-Host "    http://localhost:5173/crm/care/review-tpl"
Write-Host ""
Write-Host "  Pass criteria:"
Write-Host "    - Page renders (no blank screen)"
Write-Host "    - List tables show data (not '加载失败')"
Write-Host "    - Browser Network tab: 200 OK on all /attendance/* and /crm/care/*"
Write-Host "    - No 404/500 in DevTools console"
Write-Host ""

# ============================================================
# 总结
# ============================================================
Write-Host "============================================================"
Write-Host " Summary"
Write-Host "============================================================"
Write-Host "  Mode: $(if ($Execute) {'EXECUTE'} else {'DRY-RUN'})"
Write-Host "  Files renamed: $($Targets.Count) (in EXECUTE mode)"
Write-Host "  Rollback: recovery-first-batch-rollback.ps1"
Write-Host ""
if (-not $Execute) {
    Write-Host "  *** This was a DRY-RUN. Nothing was changed. ***" -ForegroundColor Yellow
    Write-Host "  *** To actually execute, re-run with -Execute flag. ***" -ForegroundColor Yellow
}
Write-Host ""
