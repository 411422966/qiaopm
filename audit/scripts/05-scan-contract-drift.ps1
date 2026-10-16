# 05-scan-contract-drift.ps1
# Scan for "contract drift" patterns:
#   - @RequestParam(required=false) without defaultValue (potential NPE)
#   - @RequestParam with no required (defaults to true, potential 400)
#   - @RequestBody without @Valid (no validation)
#   - Frontend field naming inconsistencies
# Output: audit/static-gap/05-contract-drift.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/05-contract-drift.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"
$FrontendApi = Join-Path $ProjectRoot "frontend/meiyetec-web/src/api"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$counter = 0
$findings = @()

# === Backend: @RequestParam issues ===
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

        # 1. @RequestParam(required=false) without defaultValue
        if ($line -match '@RequestParam\s*\(\s*required\s*=\s*false') {
            # Check next 3 lines for defaultValue
            $hasDefault = $false
            for ($j = $i; $j -lt [Math]::Min($i + 3, $lines.Count); $j++) {
                if ($lines[$j] -match 'defaultValue') { $hasDefault = $true; break }
            }
            if (-not $hasDefault) {
                $matched = $true
                $pattern = "RequestParam-required-false-no-defaultValue"
                $severity = "MEDIUM"
            }
        }

        # 2. @RequestParam(...) without required attribute (defaults to true)
        elseif ($line -match '@RequestParam\s*\(\s*"[^"]+"\s*\)' -and $line -notmatch 'required') {
            $matched = $true
            $pattern = "RequestParam-default-required-true"
            $severity = "MEDIUM"
        }

        # 3. @RequestBody without @Valid
        elseif ($line -match '@RequestBody\b' -and $line -notmatch '@Valid') {
            # Same line @RequestBody + @Valid is OK
            $matched = $true
            $pattern = "RequestBody-without-Valid"
            $severity = "MEDIUM"
        }

        # 4. @PathVariable without required (default true)
        elseif ($line -match '@PathVariable\s*\(\s*"[^"]+"\s*\)' -and $line -notmatch 'required') {
            # Actually @PathVariable is always required - skip false positive
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

# === Frontend: type declarations ===
$apiFiles = Get-ChildItem -Path $FrontendApi -Filter "*.ts" -Recurse -ErrorAction SilentlyContinue
foreach ($file in $apiFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
    $lines = $content -split "`n"

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]

        # Field with underscore (camelCase expected)
        if ($line -match '^\s*(\w+)\s*[?:]\s*(string|number|boolean|any|object)\b') {
            $fieldName = $matches[1]
            if ($fieldName -match '_' -and $fieldName -notmatch '__') {
                $counter++
                $findings += [PSCustomObject]@{
                    No        = $counter
                    File      = $relPath
                    Line      = $i + 1
                    Pattern   = "frontend-field-naming-convention"
                    Snippet   = $line.Trim().Substring(0, [Math]::Min(120, $line.Trim().Length))
                    Severity  = "LOW"
                }
            }
        }
    }
}

$header = @"
========================================
Static Gap Audit -- 05 Contract Drift Scan
========================================
Execution Time: $timestamp
Scan Backend: $BackendSrc
Scan Frontend: $FrontendApi
Total Hits: $counter

Pattern Descriptions:
- RequestParam-required-false-no-defaultValue: Optional param without default value (NPE risk)
- RequestParam-default-required-true: Required attribute missing (default true, potential 400)
- RequestBody-without-Valid: No input validation
- frontend-field-naming-convention: Field uses underscore (snake_case) when camelCase expected

Hits:

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no hits)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "05-scan-contract-drift: $counter hits" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
