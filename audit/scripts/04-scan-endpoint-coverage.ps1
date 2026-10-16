# 04-scan-endpoint-coverage.ps1
# Scan for "frontend-backend endpoint coverage delta":
#   - Backend @RequestMapping/@PostMapping/@GetMapping -> all endpoints
#   - Frontend api/*.ts files request.{get,post,put,delete} -> all API calls
#   - Output 3 sets: only-backend / only-frontend / both
# Output: audit/static-gap/04-endpoint-coverage.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/04-endpoint-coverage.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"
$FrontendApi = Join-Path $ProjectRoot "frontend/meiyetec-web/src/api"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

# Collect backend endpoints
$backendEndpoints = @{}

$javaFiles = Get-ChildItem -Path $BackendSrc -Filter "*.java" -Recurse
foreach ($file in $javaFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }

    # Class-level @RequestMapping("/xxx")
    $classMatch = [regex]::Match($content, '@RequestMapping\s*\(\s*["'']([^"'']+)["'']\s*\)')
    $classPath = if ($classMatch.Success) { $classMatch.Groups[1].Value } else { "" }

    # Method-level @PostMapping/@GetMapping/@PutMapping/@DeleteMapping
    $methodPattern = '@(PostMapping|GetMapping|PutMapping|DeleteMapping|RequestMapping)\s*\(\s*(?:value\s*=\s*)?["'']([^"'']*)["'']'
    $matches = [regex]::Matches($content, $methodPattern)

    foreach ($m in $matches) {
        $verb = $m.Groups[1].Value.Replace("Mapping", "").ToUpper()
        if ($verb -eq "REQUEST") { $verb = "ANY" }
        $subPath = $m.Groups[2].Value
        $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count

        $fullPath = if ($subPath.StartsWith("/")) { $classPath + $subPath } else { $classPath + "/" + $subPath }
        $fullPath = $fullPath -replace '/+', '/'
        $key = "$verb`:$fullPath"

        if (-not $backendEndpoints.ContainsKey($key)) {
            $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')
            $backendEndpoints[$key] = "$relPath`:$lineNum"
        }
    }
}

# Collect frontend API calls
$frontendApis = @{}
$apiFiles = Get-ChildItem -Path $FrontendApi -Filter "*.ts" -Recurse -ErrorAction SilentlyContinue
foreach ($file in $apiFiles) {
    $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $content) { continue }
    $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')

    $apiPattern = 'request\.(get|post|put|delete)\s*\(\s*[`"'']([^`"'']+)[`"'']'
    $matches = [regex]::Matches($content, $apiPattern)

    foreach ($m in $matches) {
        $verb = $m.Groups[1].Value.ToUpper()
        $path = $m.Groups[2].Value
        $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count
        $key = "$verb`:$path"
        if (-not $frontendApis.ContainsKey($key)) {
            $frontendApis[$key] = "$relPath`:$lineNum"
        }
    }
}

# Compute delta sets
$both = @()
$onlyBackend = @()
$onlyFrontend = @()

foreach ($key in $backendEndpoints.Keys) {
    if ($frontendApis.ContainsKey($key)) {
        $both += [PSCustomObject]@{ Endpoint = $key; Backend = $backendEndpoints[$key]; Frontend = $frontendApis[$key] }
    } else {
        $onlyBackend += [PSCustomObject]@{ Endpoint = $key; Backend = $backendEndpoints[$key] }
    }
}

foreach ($key in $frontendApis.Keys) {
    if (-not $backendEndpoints.ContainsKey($key)) {
        $onlyFrontend += [PSCustomObject]@{ Endpoint = $key; Frontend = $frontendApis[$key] }
    }
}

$header = @"
========================================
Static Gap Audit -- 04 Endpoint Coverage Scan
========================================
Execution Time: $timestamp
Scan Backend: $BackendSrc
Scan Frontend: $FrontendApi

Backend Endpoints Total: $($backendEndpoints.Count)
Frontend API Calls Total: $($frontendApis.Count)

Matched (both):                 $($both.Count)
Only-Backend (Shadow Feature):  $($onlyBackend.Count)
Only-Frontend (Ghost Feature):  $($onlyFrontend.Count)

Matched List (consistent):

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8

if ($both.Count -gt 0) {
    $both | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(none)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

"" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
"Only-Backend (Shadow Feature):" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
"========================================" | Out-File -FilePath $OutputFile -Append -Encoding UTF8

if ($onlyBackend.Count -gt 0) {
    $onlyBackend | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(none)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

"" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
"Only-Frontend (Ghost Feature):" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
"========================================" | Out-File -FilePath $OutputFile -Append -Encoding UTF8

if ($onlyFrontend.Count -gt 0) {
    $onlyFrontend | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(none)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "04-scan-endpoint-coverage: backend=$($backendEndpoints.Count) frontend=$($frontendApis.Count) both=$($both.Count) shadow=$($onlyBackend.Count) ghost=$($onlyFrontend.Count)" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
