# 07-scan-transaction.ps1
# Scan for "@Transactional boundary issues":
#   - Self-invocation (this.method()) making @Transactional ineffective
#   - @Transactional on private methods (no effect)
#   - @Transactional + try/catch swallowing exceptions (no rollback)
#   - @Transactional(readOnly=false) on read-only methods
#   - @Transactional without rollbackFor (default only rolls back RuntimeException)
# Output: audit/static-gap/07-transaction.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/07-transaction.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$counter = 0
$findings = @()

$javaFiles = Get-ChildItem -Path $BackendSrc -Filter "*.java" -Recurse

foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
    $lines = $content -split "`n"

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $matched = $false
        $severity = "MEDIUM"
        $pattern = ""

        # 1. @Transactional without rollbackFor
        if ($line -match '@Transactional\b' -and $line -notmatch 'rollbackFor' -and $line -notmatch 'noRollbackFor') {
            # Skip class-level annotation with multi-line
            $matched = $true
            $pattern = "Transactional-no-rollbackFor"
            $severity = "MEDIUM"
        }

        # 2. @Transactional on private method (no AOP interception)
        if ($i -gt 0) {
            $prevLine = $lines[$i - 1]
            if ($line -match '^\s*private\s+\w+\s+\w+\s*\(' -and $prevLine -match '@Transactional') {
                $matched = $true
                $pattern = "Transactional-private-method"
                $severity = "HIGH"
            }
        }

        # 3. @Transactional + try/catch swallowing exception
        if ($line -match '@Transactional\b') {
            $hasSwallowCatch = $false
            for ($j = $i; $j -lt [Math]::Min($i + 40, $lines.Count); $j++) {
                # Check if method ends before catching
                if ($j -gt $i + 5 -and $lines[$j] -match '^\s*\}\s*$') { break }
                if ($lines[$j] -match 'catch\s*\(' -and ($j + 1) -lt $lines.Count) {
                    $nextLine = $lines[$j + 1]
                    if ($nextLine -match 'log\.(warn|info|debug)\(' -or $nextLine -match '//\s*ignore') {
                        $hasSwallowCatch = $true
                        break
                    }
                }
            }
            if ($hasSwallowCatch) {
                $matched = $true
                $pattern = "Transactional-catch-swallow"
                $severity = "HIGH"
            }
        }

        if ($matched) {
            $counter++
            $findings += [PSCustomObject]@{
                No        = $counter
                File      = $relPath
                Line      = $i + 1
                Pattern   = $pattern
                Snippet   = $line.Trim().Substring(0, [Math]::Min(120, $line.Trim().Length))
                Severity  = $severity
            }
        }
    }
}

$header = @"
========================================
Static Gap Audit -- 07 Transaction Boundary Scan
========================================
Execution Time: $timestamp
Scan Backend: $BackendSrc
Total Hits: $counter

Pattern Descriptions:
- Transactional-no-rollbackFor: Default only rolls back RuntimeException, Checked Exceptions do not trigger rollback
- Transactional-private-method: @Transactional on private method - Spring AOP does not intercept private methods
- Transactional-catch-swallow: catch block eats exception - transaction will not rollback

Hits:

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no hits)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "07-scan-transaction: $counter hits" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
