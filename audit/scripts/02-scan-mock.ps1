# 02-scan-mock.ps1
# Scan for "mock/fake/hardcode/localStorage" patterns:
#   - Java: @ConditionalOnMissingBean(name = "mock*") / hardcoded sample data / TODO comments
#   - JS/TS: localStorage.* / sessionStorage.* / mock variables / setTimeout fake async
# Output: audit/static-gap/02-mock.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/02-mock.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"
$FrontendSrc = Join-Path $ProjectRoot "frontend/meiyetec-web/src"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$counter = 0
$findings = @()

# === Backend: Java Mock / Hardcode patterns ===
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

            # 1. @ConditionalOnMissingBean(name = "mock*")
            if ($line -match '@ConditionalOnMissingBean\s*\(\s*name\s*=\s*"(mock|Mock|MOCK)[^"]*"') {
                $matched = $true
                $pattern = "ConditionalOnMissingBean-mock-fallback"
                $severity = "LOW-DESIGN"
            }

            # 2. // TODO: replace with real XXX
            elseif ($line -match '//\s*TODO[^\n]*(replace|real|production|mock)') {
                $matched = $true
                $pattern = "TODO-replace-needed"
                $severity = "MEDIUM"
            }

            # 3. hardcoded sample/demo/test string
            elseif ($line -match '"[^"]*(demo[_-]?(store|data|tenant)|sample[_-]?data|test[_-]?tenant)[^"]*"') {
                $matched = $true
                $pattern = "hardcoded-sample-string"
                $severity = "MEDIUM"
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

# === Frontend: JS/TS Mock / localStorage patterns ===
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

        # 1. localStorage.*
        if ($line -match 'localStorage\.(setItem|getItem|removeItem)') {
            $matched = $true
            $pattern = "localStorage-usage"
            $severity = "MEDIUM"
        }

        # 2. sessionStorage
        elseif ($line -match 'sessionStorage\.(setItem|getItem)') {
            $matched = $true
            $pattern = "sessionStorage-usage"
            $severity = "MEDIUM"
        }

        # 3. mock data variable
        elseif ($line -match '\b(const|let|var)\s+mock\w*\s*[=:]') {
            $matched = $true
            $pattern = "mock-variable"
            $severity = "HIGH"
        }

        # 4. setTimeout fake async
        elseif ($line -match 'setTimeout\s*\(\s*\(\s*\)\s*=>\s*\{' -and ($i + 1) -lt $lines.Count) {
            # check if next 3 lines have resolve()/success callback
            $nextFew = ($lines[$i..[Math]::Min($i + 3, $lines.Count - 1)] -join "`n")
            if ($nextFew -match 'resolve\(|success|callback|fake') {
                $matched = $true
                $pattern = "setTimeout-fake-async"
                $severity = "HIGH"
            }
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
Static Gap Audit -- 02 Mock/Fake/Hardcode/localStorage Scan
========================================
Execution Time: $timestamp
Scan Backend: $BackendSrc
Scan Frontend: $FrontendSrc
Total Hits: $counter

Pattern Descriptions:
- ConditionalOnMissingBean-mock-fallback: Mock design fallback (mark as MOCK-INTENTIONAL)
- TODO-replace-needed: Comments indicate replacement needed
- hardcoded-sample-string: Literal contains demo/sample/test/fake keywords
- localStorage-usage / sessionStorage-usage: Client-side persistence (potential fake success risk)
- mock-variable: Hardcoded mock data source (HIGH risk)
- setTimeout-fake-async: Using setTimeout to simulate API response (typical MOCK-FAKE)

Hits:

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no hits)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "02-scan-mock: $counter hits" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
