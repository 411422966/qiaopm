# 06-scan-soft-delete.ps1
# Scan for "soft delete + unique index conflict" patterns:
#   - Tables with deleted/is_deleted column + UNIQUE index not including deleted
#   - This causes: second soft-delete with same key fields -> UNIQUE conflict
# Output: audit/static-gap/06-soft-delete.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/06-soft-delete.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$DbSnapshot = Join-Path $ProjectRoot "audit/baseline/db-snapshot.sql"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$counter = 0
$findings = @()

if (-not (Test-Path $DbSnapshot)) {
    "DB snapshot not found: $DbSnapshot" | Out-File -FilePath $OutputFile -Encoding UTF8
    Write-Host "06-scan-soft-delete: DB snapshot missing" -ForegroundColor Red
    exit 1
}

$content = Get-Content $DbSnapshot -Raw

# Match CREATE TABLE blocks - use greedy match until last ) ENGINE
$tablePattern = '(?ms)CREATE TABLE\s+`?(\w+)`?\s*\((.+?)\)\s*ENGINE\s*=\s*\w+'
$tableMatches = [regex]::Matches($content, $tablePattern)
Write-Host "Tables matched: $($tableMatches.Count)"

foreach ($tm in $tableMatches) {
    $tableName = $tm.Groups[1].Value
    $tableBody = $tm.Groups[2].Value

    # Check if has deleted/is_deleted column (allow backticks / whitespace between name and type)
    $hasDeleted = $tableBody -match '(?i)(?:`?|`?\s*`?)\b(deleted|is_deleted)\b[`\s\)]+(TINYINT|BOOLEAN|INT|BIT|INTEGER)'

    if ($hasDeleted) {
        # Check UNIQUE KEYs
        $uniqueKeys = [regex]::Matches($tableBody, '(?i)UNIQUE\s+KEY\s+`?\w+`?\s*\(([^)]+)\)')

        foreach ($uk in $uniqueKeys) {
            $columnsRaw = $uk.Groups[1].Value
            $columns = $columnsRaw -split ',' | ForEach-Object { $_.Trim().TrimStart('`') }

            $hasDeletedInUnique = $false
            foreach ($col in $columns) {
                if ($col -match '(?i)deleted') {
                    $hasDeletedInUnique = $true
                    break
                }
            }

            if (-not $hasDeletedInUnique) {
                $counter++
                $findings += [PSCustomObject]@{
                    No         = $counter
                    Table      = $tableName
                    UniqueKey  = $uk.Groups[0].Value.Replace("`n", " ").Replace("  ", " ")
                    Columns    = $columnsRaw
                    HasDeleted = $hasDeleted
                    Risk       = "HIGH"
                    Note       = "Table has deleted column but UNIQUE KEY excludes it - soft delete + re-insert will conflict"
                }
            } else {
                $counter++
                $findings += [PSCustomObject]@{
                    No         = $counter
                    Table      = $tableName
                    UniqueKey  = $uk.Groups[0].Value.Replace("`n", " ").Replace("  ", " ")
                    Columns    = $columnsRaw
                    HasDeleted = $hasDeleted
                    Risk       = "OK"
                    Note       = "UNIQUE KEY already includes deleted column - soft delete safe"
                }
            }
        }
    }
}

$header = @"
========================================
Static Gap Audit -- 06 Soft Delete + Unique Index Conflict Scan
========================================
Execution Time: $timestamp
DB Snapshot: $DbSnapshot
Total Hits: $counter

Risk Classification:
- HIGH: Table has deleted column but UNIQUE KEY excludes it
  -> Soft delete + re-insert with same key fields will trigger UNIQUE constraint
- OK: UNIQUE KEY already includes deleted column
  -> MySQL 8.0+ uses deleted=0 vs deleted=ID to differentiate

Hits:

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize -Wrap | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no soft-delete tables found)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "06-scan-soft-delete: $counter hits (including OK markers)" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
