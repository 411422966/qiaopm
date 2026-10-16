# 03-scan-disabled.ps1
# Scan for "dead code / disabled / TODO" patterns:
#   - @Disabled / @Deprecated / @SuppressWarnings("deprecation")
#   - Comments: TODO / FIXME / XXX / HACK / "not implemented"
#   - throw new UnsupportedOperationException
#   - return null; placeholder
#   - method body only contains TODO comment
# Output: audit/static-gap/03-disabled.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/03-disabled.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"
$FrontendSrc = Join-Path $ProjectRoot "frontend/meiyetec-web/src"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$counter = 0
$findings = @()

# === Backend ===
$backendDirs = Get-ChildItem -Path $BackendSrc -Directory
foreach ($dir in $backendDirs) {
    $javaFiles = Get-ChildItem -Path $dir.FullName -Filter "*.java" -Recurse
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

            # 1. @Disabled / @Deprecated
            if ($line -match '@(Disabled|Deprecated)\b') {
                $matched = $true
                $pattern = "annotation-disabled-deprecated"
                $severity = "MEDIUM"
            }

            # 2. throw new UnsupportedOperationException
            elseif ($line -match 'throw\s+new\s+UnsupportedOperationException') {
                $matched = $true
                $pattern = "UnsupportedOperationException"
                $severity = "HIGH"
            }

            # 3. // TODO / FIXME / XXX / HACK
            elseif ($line -match '//\s*(TODO|FIXME|XXX|HACK)\b') {
                $matched = $true
                $pattern = "comment-todo-fixme"
                $severity = "MEDIUM"
            }

            # 4. throw new RuntimeException("not implemented")
            elseif ($line -match 'throw\s+new\s+RuntimeException\s*\(\s*"(not implemented|to be implemented|not yet)' ) {
                $matched = $true
                $pattern = "runtime-not-implemented"
                $severity = "HIGH"
            }

            # 5. single-line return null; placeholder
            elseif ($line -match '^\s*return\s+null\s*;\s*$') {
                $matched = $true
                $pattern = "return-null-placeholder"
                $severity = "MEDIUM"
            }

            # 6. method body only contains TODO (next non-empty line is closing brace)
            elseif ($line -match '//\s*(TODO|FIXME|XXX|HACK|to be implemented|not implemented)' -and ($i + 1) -lt $lines.Count) {
                # Look for next non-empty line
                $nextIdx = $i + 1
                while ($nextIdx -lt $lines.Count -and $lines[$nextIdx] -match '^\s*$') { $nextIdx++ }
                if ($nextIdx -lt $lines.Count -and $lines[$nextIdx] -match '^\s*\}\s*$') {
                    $matched = $true
                    $pattern = "method-body-only-todo"
                    $severity = "HIGH"
                }
            }

            if ($matched) {
                $counter++
                $findings += [PSCustomObject]@{
                    No        = $counter
                    Scope     = "Backend"
                    File      = $relPath
                    Line      = $i + 1
                    Pattern   = $pattern
                    Snippet   = $line.Trim().Substring(0, [Math]::Min(120, $line.Trim().Length))
                    Severity  = $severity
                }
            }
        }
    }
}

# === Frontend ===
$frontendFiles = @()
$frontendFiles += Get-ChildItem -Path $FrontendSrc -Filter "*.ts" -Recurse -ErrorAction SilentlyContinue
$frontendFiles += Get-ChildItem -Path $FrontendSrc -Filter "*.vue" -Recurse -ErrorAction SilentlyContinue
$frontendFiles = $frontendFiles | Where-Object { $_.FullName -notmatch "node_modules" } | Where-Object { $_ -is [System.IO.FileInfo] }

foreach ($file in $frontendFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
    $lines = $content -split "`n"

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $matched = $false
        $severity = "MEDIUM"
        $pattern = ""

        # 1. // TODO / FIXME / XXX
        if ($line -match '//\s*(TODO|FIXME|XXX|HACK)\b') {
            $matched = $true
            $pattern = "comment-todo-fixme"
            $severity = "MEDIUM"
        }

        # 2. throw new Error("not implemented")
        elseif ($line -match 'throw\s+new\s+Error\s*\(\s*["''](not implemented|TODO|to be implemented)' ) {
            $matched = $true
            $pattern = "throw-not-implemented"
            $severity = "HIGH"
        }

        # 3. console.log debug
        elseif ($line -match '^\s*console\.(log|debug)\s*\(') {
            $matched = $true
            $pattern = "console-log-debug"
            $severity = "LOW"
        }

        # 4. alert() leftover
        elseif ($line -match '^\s*alert\s*\(') {
            $matched = $true
            $pattern = "alert-usage"
            $severity = "MEDIUM"
        }

        if ($matched) {
            $counter++
            $findings += [PSCustomObject]@{
                No        = $counter
                Scope     = "Frontend"
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
Static Gap Audit -- 03 Disabled / Dead Code / TODO Scan
========================================
Execution Time: $timestamp
Scan Backend: $BackendSrc
Scan Frontend: $FrontendSrc
Total Hits: $counter

Pattern Descriptions:
- annotation-disabled-deprecated: @Disabled or @Deprecated annotation
- UnsupportedOperationException: Throws 'not implemented' exception (explicit placeholder)
- comment-todo-fixme: TODO / FIXME / XXX / HACK comments
- runtime-not-implemented: RuntimeException with 'not implemented' message
- return-null-placeholder: Single-line return null (likely placeholder)
- method-body-only-todo: Method body contains only TODO comment (typical SHELL)
- console-log-debug: Debug leftover
- alert-usage: alert() leftover

Hits:

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no hits)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "03-scan-disabled: $counter hits" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
