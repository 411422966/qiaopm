# 08-scan-shadow-ghost.ps1
# Scan for "Shadow / Ghost / Zombie / Fake / Shell Feature" patterns:
#   - Shadow Feature: backend @RequestMapping without frontend call
#   - Ghost Feature: frontend API call without backend @RequestMapping
#   - Zombie Feature: @Deprecated still referenced
#   - Fake Feature: setTimeout / direct return stub
#   - Shell Feature: empty / minimal Vue component
# Output: audit/static-gap/08-shadow-ghost.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/08-shadow-ghost.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"
$FrontendSrc = Join-Path $ProjectRoot "frontend/meiyetec-web/src"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$counter = 0
$findings = @()

# Collect backend endpoints
$backendEndpoints = @{}
$javaFiles = Get-ChildItem -Path $BackendSrc -Filter "*.java" -Recurse
foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $classMatch = [regex]::Match($content, '@RequestMapping\s*\(\s*["'']([^"'']+)["'']\s*\)')
    $classPath = if ($classMatch.Success) { $classMatch.Groups[1].Value } else { "" }
    $methodPattern = '@(PostMapping|GetMapping|PutMapping|DeleteMapping|RequestMapping)\s*\(\s*(?:value\s*=\s*)?["'']([^"'']*)["'']'
    foreach ($m in [regex]::Matches($content, $methodPattern)) {
        $verb = $m.Groups[1].Value.Replace("Mapping", "").ToUpper()
        if ($verb -eq "REQUEST") { $verb = "ANY" }
        $subPath = $m.Groups[2].Value
        $fullPath = if ($subPath.StartsWith("/")) { $classPath + $subPath } else { $classPath + "/" + $subPath }
        $fullPath = $fullPath -replace '/+', '/'
        $key = "$verb`:$fullPath"
        if (-not $backendEndpoints.ContainsKey($key)) {
            $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
            $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count
            $backendEndpoints[$key] = "$relPath`:$lineNum"
        }
    }
}

# Collect frontend API calls
$frontendApis = @{}
$apiFiles = Get-ChildItem -Path "$FrontendSrc/api" -Filter "*.ts" -Recurse -ErrorAction SilentlyContinue
foreach ($file in $apiFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    foreach ($m in [regex]::Matches($content, 'request\.(get|post|put|delete)\s*\(\s*[`"'']([^`"'']+)[`"'']')) {
        $verb = $m.Groups[1].Value.ToUpper()
        $path = $m.Groups[2].Value
        $key = "$verb`:$path"
        if (-not $frontendApis.ContainsKey($key)) {
            $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
            $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count
            $frontendApis[$key] = "$relPath`:$lineNum"
        }
    }
}

$shadowCount = 0
foreach ($key in $backendEndpoints.Keys) {
    if (-not $frontendApis.ContainsKey($key)) {
        $counter++
        $shadowCount++
        $findings += [PSCustomObject]@{
            No        = $counter
            Type      = "Shadow Feature"
            Endpoint  = $key
            Location  = $backendEndpoints[$key]
            Risk      = "MEDIUM"
            Note      = "Backend implemented, no frontend entry"
        }
    }
}

$ghostCount = 0
foreach ($key in $frontendApis.Keys) {
    if (-not $backendEndpoints.ContainsKey($key)) {
        $counter++
        $ghostCount++
        $findings += [PSCustomObject]@{
            No        = $counter
            Type      = "Ghost Feature"
            Endpoint  = $key
            Location  = $frontendApis[$key]
            Risk      = "HIGH"
            Note      = "Frontend called, no backend @RequestMapping"
        }
    }
}

# Zombie Feature: @Deprecated
$zombieCount = 0
foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
    $lines = $content -split "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '@Deprecated\b') {
            $counter++
            $zombieCount++
            $findings += [PSCustomObject]@{
                No        = $counter
                Type      = "Zombie Feature (suspicious)"
                Endpoint  = "N/A"
                Location  = "$relPath`:$($i + 1)"
                Risk      = "MEDIUM"
                Note      = "@Deprecated annotation, recommend cleanup"
            }
        }
    }
}

# Fake Feature: setTimeout stub
$fakeCount = 0
$vueFiles = Get-ChildItem -Path $FrontendSrc -Filter "*.vue" -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "node_modules" }
foreach ($file in $vueFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
    $lines = $content -split "`n"
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match 'setTimeout\s*\(\s*\(\s*\)\s*=>\s*\{') {
            $counter++
            $fakeCount++
            $findings += [PSCustomObject]@{
                No        = $counter
                Type      = "Fake Feature (setTimeout stub)"
                Endpoint  = "N/A"
                Location  = "$relPath`:$($i + 1)"
                Risk      = "HIGH"
                Note      = "setTimeout simulating async - typical MOCK-FAKE"
            }
        }
    }
}

# Shell Feature: empty Vue file (< 500 bytes)
$shellCount = 0
foreach ($file in $vueFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')

    if ($content.Length -lt 500) {
        $counter++
        $shellCount++
        $findings += [PSCustomObject]@{
            No        = $counter
            Type      = "Shell Feature (empty page)"
            Endpoint  = "N/A"
            Location  = "$relPath ($($content.Length) bytes)"
            Risk      = "MEDIUM"
            Note      = "Vue file < 500 bytes, possibly placeholder"
        }
    }
}

$header = @"
========================================
Static Gap Audit -- 08 Shadow / Ghost / Zombie / Fake / Shell Feature Scan
========================================
Execution Time: $timestamp
Scan Backend: $BackendSrc
Scan Frontend: $FrontendSrc

Hit Statistics:
- Shadow Feature (only-backend): $shadowCount
- Ghost Feature (only-frontend): $ghostCount
- Zombie Feature (suspicious @Deprecated): $zombieCount
- Fake Feature (setTimeout stub): $fakeCount
- Shell Feature (empty page): $shellCount
- Total: $counter

Hits:

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no hits)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "08-scan-shadow-ghost: Shadow=$shadowCount Ghost=$ghostCount Zombie=$zombieCount Fake=$fakeCount Shell=$shellCount" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
