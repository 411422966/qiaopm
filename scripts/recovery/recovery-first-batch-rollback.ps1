# ============================================================
# Rollback Script: First Batch (5 Controllers)
# ============================================================
#
# Reverses the recovery-first-batch.ps1 EXECUTE operation
#
# Pre-condition: First Batch EXECUTE was run successfully
# Effect: renames .java -> .java.disabled for the same 5 files
#
# Usage:
#   ./recovery-first-batch-rollback.ps1            # DRY-RUN
#   ./recovery-first-batch-rollback.ps1 -Execute   # ACTUAL ROLLBACK
# ============================================================

param(
    [switch]$Execute = $false
)

$ErrorActionPreference = 'Stop'
$RootPath = 'd:\QiaoPM'

function Write-Step($msg) { Write-Host "[STEP]  $msg" -ForegroundColor Cyan }
function Write-OK($msg)   { Write-Host "[OK]    $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Write-Dry($msg)  { Write-Host "[DRY]   $msg (not executed)" -ForegroundColor DarkYellow }

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
Write-Host " Rollback: First Batch Recovery"
Write-Host " Mode: $(if ($Execute) {'EXECUTE'} else {'DRY-RUN'})"
Write-Host "============================================================"
Write-Host ""

$SourceDir = Join-Path $RootPath 'backend\src\main\java\com\meiyetec\controller'

foreach ($name in $Targets) {
    $enabled  = Join-Path $SourceDir "$name.java"
    $disabled = Join-Path $SourceDir "$name.java.disabled"

    if (Test-Path $enabled) {
        if ($Execute) {
            Rename-Item -Path $enabled -NewName "$name.java.disabled"
            Write-OK "Rolled back: $name"
        } else {
            Write-Dry "Would rollback: $name.java -> $name.java.disabled"
        }
    } elseif (Test-Path $disabled) {
        Write-Warn "Already disabled: $name"
    } else {
        Write-Warn "Missing both: $name"
    }
}

Write-Host ""
if (-not $Execute) {
    Write-Host "*** DRY-RUN. Nothing was changed. ***" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "After rollback, run: mvn -pl backend clean compile"
Write-Host "Expected: same compile status as before First Batch."
Write-Host ""
