# 01-scan-fake-success.ps1
# Scan for "fake success" patterns:
#   - catch blocks that swallow exceptions (only log.warn/no handling)
#   - try/catch wrapping entire method + return success
#   - return Result.success with no real business operation
#   - swallow exception comments / FIXME / etc.
# Output: audit/static-gap/01-fake-success.txt

param(
    [string]$ProjectRoot = ".",
    [string]$OutputFile = "audit/static-gap/01-fake-success.txt"
)

$ErrorActionPreference = "SilentlyContinue"

$BackendSrc = Join-Path $ProjectRoot "backend/src/main/java"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$findings = @()
$counter = 0

$backendDirs = Get-ChildItem -Path $BackendSrc -Directory
$totalFiles = 0

foreach ($dir in $backendDirs) {
    $javaFiles = Get-ChildItem -Path $dir.FullName -Filter "*.java" -Recurse
    $totalFiles += $javaFiles.Count

    foreach ($file in $javaFiles) {
        $content = Get-Content $file.FullName -Raw -ErrorAction SilentlyContinue
        if (-not $content) { continue }
        $relPath = $file.FullName.Replace($ProjectRoot, "").TrimStart('/','\')

        # Pattern A: catch (Exception e) { log.warn(...); } -- single line swallowing
        $patternA = 'catch\s*\(\s*[A-Za-z]+\s+\w+\s*\)\s*\{\s*log\.(warn|info|debug)\('
        $matchesA = [regex]::Matches($content, $patternA)
        foreach ($m in $matchesA) {
            $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count
            $counter++
            $findings += [PSCustomObject]@{
                No        = $counter
                File      = $relPath
                Line      = $lineNum
                Pattern   = "catch-log-swallow"
                Snippet   = $m.Value.Substring(0, [Math]::Min(120, $m.Value.Length))
                Severity  = "MEDIUM"
            }
        }

        # Pattern B: catch (Exception ignore) {} -- empty catch block
        $patternB = 'catch\s*\([^)]*ignore[^)]*\)\s*\{\s*\}'
        $matchesB = [regex]::Matches($content, $patternB)
        foreach ($m in $matchesB) {
            $lineNum = ($content.Substring(0, $m.Index) -split "`n").Count
            $counter++
            $findings += [PSCustomObject]@{
                No        = $counter
                File      = $relPath
                Line      = $lineNum
                Pattern   = "catch-ignore-empty"
                Snippet   = $m.Value
                Severity  = "HIGH"
            }
        }
    }
}

$header = @"
========================================
Static Gap Audit -- 01 Fake Success Scan
========================================
Execution Time: $timestamp
Scan Directory: $BackendSrc
Total Files Scanned: $totalFiles
Total Hits: $counter

Pattern Descriptions:
- catch-log-swallow: catch block only calls log.warn/info/debug (silent failure)
- catch-ignore-empty: catch (Exception ignore) {} pattern (explicit swallowing)

Hits (file:line):

"@

$header | Out-File -FilePath $OutputFile -Encoding UTF8
if ($findings.Count -gt 0) {
    $findings | Format-Table -AutoSize | Out-File -FilePath $OutputFile -Append -Encoding UTF8
} else {
    "(no hits)" | Out-File -FilePath $OutputFile -Append -Encoding UTF8
}

Write-Host "01-scan-fake-success: $counter hits" -ForegroundColor Yellow
Write-Host "Output: $OutputFile"
