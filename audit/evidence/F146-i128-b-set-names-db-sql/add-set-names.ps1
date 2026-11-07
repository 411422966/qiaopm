<#
    ================================================================
    ⚠️ 非正式工具（证据副本）
    ---------------------------------------------------------------
    本文件是 scripts/add-set-names.ps1 的**只读证据副本**，
    随 audit/evidence/F146-i128-b-set-names-db-sql/ 一并归档，用于复现本批落盘过程。

    原始工具位置：scripts/add-set-names.ps1
    原始工具状态：**未入库** —— 被 .gitignore L143 的全局 *.ps1 规则忽略；
                  .gitignore 属治理保护文件，未经用户单独批准不得修改，
                  因此本批不在仓库根 scripts/ 下登记该脚本。

    本副本**不进入 CI / PR 必跑清单**，不得当作正式守门工具使用。
    ================================================================
.SYNOPSIS
    为 db/sql 目录下的全部 .sql 脚本补齐 SET NAMES utf8mb4，防止中文乱码。

.DESCRIPTION
    背景：docker-compose 把 backend/src/main/resources/db/sql 整目录挂载到
    /docker-entrypoint-initdb.d。容器内 mysql 客户端 character_set_client = latin1，
    脚本若不显式声明 SET NAMES，中文按 latin1 解释入库 → 乱码（I128-b 战役根因）。

    本脚本是**一次性迁移工具**，非 CI 守门脚本。
    - 幂等：已含 SET NAMES 的文件直接跳过
    - 保行尾：沿用文件自身主导行尾（CRLF / LF）
    - 无 BOM：UTF-8 无 BOM 写回
    - 严格解码：UTF8Encoding(false, true) 严格模式，解码失败即报错跳过
    - 排除 Flyway：backend/src/main/resources/db/migration/V1__init_baseline.sql
      （改动会破坏 Flyway checksum）

.PARAMETER Root
    要处理的 SQL 根目录。默认 backend/src/main/resources/db/sql

.PARAMETER DryRun
    仅报告不落盘（默认开启）。传 -Apply 才真正写文件。

.PARAMETER Apply
    实际写盘。与 -DryRun 互斥。

.PARAMETER IncludeSubDir
    是否递归处理子目录（默认 $true）。audit-subset/ 下的 3 个脚本也一并处理。

.PARAMETER ReportPath
    报告文件路径（可选）。给出则把逐文件报告写到此文件（UTF-8 无 BOM）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/add-set-names.ps1
    → DryRun 报告，不改任何文件

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/add-set-names.ps1 -Apply
    → 实际写入

.NOTES
    退出码：
      0 = 全部成功（DryRun 完成 / Apply 完成且无错误）
      1 = 有文件处理失败（解码错误、写入失败等）
      2 = 参数错误（如同时指定 -DryRun 与 -Apply，或 Root 不存在）
#>
[CmdletBinding()]
param(
    [string]$Root = "backend/src/main/resources/db/sql",
    [switch]$DryRun,
    [switch]$Apply,
    [bool]$IncludeSubDir = $true,
    [string]$ReportPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------- 参数校验
if ($DryRun -and $Apply) {
    Write-Error "参数互斥：-DryRun 与 -Apply 不能同时指定"
    exit 2
}
if (-not $Apply) {
    $DryRun = $true
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$rootPath = if ([System.IO.Path]::IsPathRooted($Root)) { $Root } else { Join-Path $repoRoot $Root }

if (-not (Test-Path -LiteralPath $rootPath -PathType Container)) {
    Write-Error "根目录不存在：$rootPath"
    exit 2
}

Write-Host "=================================================="
Write-Host " add-set-names.ps1  —  为 SQL 脚本补 SET NAMES utf8mb4"
Write-Host "=================================================="
Write-Host "根目录 : $rootPath"
Write-Host "模式   : $(if ($Apply) { 'APPLY（实际写盘）' } else { 'DRY-RUN（仅报告）' })"
Write-Host "子目录 : $(if ($IncludeSubDir) { '包含' } else { '排除' })"
Write-Host ""

# ---------------------------------------------------------------- 排除规则
# Flyway 管理的迁移文件：改动会破坏 checksum，禁止触碰
$excludedFiles = @(
    "db/migration/V1__init_baseline.sql"
)

function Test-Excluded {
    param([string]$FullPath)
    $rel = $FullPath.Substring($repoRoot.Length).TrimStart('\', '/') -replace '/', '\'
    foreach ($e in $excludedFiles) {
        if ($rel -ieq $e) { return $true }
        # 目标目录整体排除（编译产物 / Flyway 目录）
        if ($rel -ilike "backend\target\*") { return $true }
    }
    return $false
}

# ---------------------------------------------------------------- 文件枚举
$allFiles = Get-ChildItem -LiteralPath $rootPath -Filter "*.sql" -File -Recurse
if (-not $IncludeSubDir) {
    $allFiles = $allFiles | Where-Object { $_.DirectoryName -eq $rootPath }
}
$allFiles = @($allFiles | Sort-Object FullName)

Write-Host "候选 .sql 文件数 : $($allFiles.Count)"
Write-Host ""

# ---------------------------------------------------------------- 正则
# 已含 SET NAMES（含注释行中的说明性文字不算）
$reHasSetNames = [regex]'(?im)^[ \t]*SET[ \t]+NAMES\b'
# 文件头部：连续的空行 / -- 注释行
$reHeaderLine = '^[ \t]*(--.*)?$'

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8Strict = New-Object System.Text.UTF8Encoding($false, $true)

$inserted = 0
$skipped = 0
$excluded = 0
$failed = 0
$report = New-Object System.Collections.Generic.List[string]

function Add-Report {
    param([string]$Line)
    $report.Add($Line)
}

Add-Report "=== add-set-names.ps1 报告 ==="
Add-Report "时间    : $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Add-Report "根目录  : $rootPath"
Add-Report "模式    : $(if ($Apply) { 'APPLY' } else { 'DRY-RUN' })"
Add-Report "候选数  : $($allFiles.Count)"
Add-Report ""

foreach ($f in $allFiles) {
    if (Test-Excluded $f.FullName) {
        $excluded++
        Write-Host ("EXCLUDED  {0}" -f $f.FullName.Substring($repoRoot.Length + 1))
        Add-Report ("EXCLUDED  {0}" -f $f.FullName.Substring($repoRoot.Length + 1))
        continue
    }

    $relPath = $f.FullName.Substring($repoRoot.Length + 1)
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)

    # 严格 UTF-8 解码
    try {
        $text = $utf8Strict.GetString($bytes)
    } catch {
        $failed++
        Write-Host ("ERROR     {0}  (非合法 UTF-8: {1})" -f $relPath, $_.Exception.Message) -ForegroundColor Red
        Add-Report ("ERROR     {0}  (非合法 UTF-8: {1})" -f $relPath, $_.Exception.Message)
        continue
    }

    # 幂等：已含 SET NAMES
    if ($reHasSetNames.IsMatch($text)) {
        $skipped++
        Write-Host ("SKIP      {0}  (已含 SET NAMES)" -f $relPath)
        Add-Report ("SKIP      {0}  (已含 SET NAMES)" -f $relPath)
        continue
    }

    # 主导行尾
    $crlfCount = ([regex]::Matches($text, "`r`n")).Count
    $lfOnlyCount = ([regex]::Matches($text, "(?<!`r)`n")).Count
    $nl = if ($crlfCount -ge $lfOnlyCount) { "`r`n" } else { "`n" }

    # 拆分行（保留行尾信息：用 Split 去掉行尾再判断）
    $lines = $text -split "`r?`n", 0

    # 定位插入点：跳过文件开头的连续空行 / -- 注释行
    $insertAt = 0
    while ($insertAt -lt $lines.Count -and ($lines[$insertAt] -match $reHeaderLine)) {
        $insertAt++
    }

    $newLines = New-Object System.Collections.Generic.List[string]
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $newLines.Add($lines[$i])
        if ($i -eq $insertAt - 1) {
            # 在头部块之后插入两行
            $newLines.Add("SET NAMES utf8mb4;")
            $newLines.Add("")
        }
    }
    # 文件头部就是 SQL（insertAt == 0）：把 SET NAMES 放在最前
    if ($insertAt -eq 0) {
        $newLines = New-Object System.Collections.Generic.List[string]
        $newLines.Add("SET NAMES utf8mb4;")
        $newLines.Add("")
        foreach ($l in $lines) { $newLines.Add($l) }
    }

    $newText = [string]::Join($nl, $newLines)

    # 若原文件以换行结尾，Join 后需保留结尾换行
    if ($text.EndsWith("`n")) {
        # [string]::Join 已包含尾部空元素（Split 保留了末尾空串），此处确认
        # Split("",0) 对 "a\n" 得到 @("a","") → Join 后仍是 "a\n"，无需额外处理
    }

    $inserted++
    Write-Host ("INSERT    {0}  (行尾={1}, 插入位置=第 {2} 行)" -f $relPath, $(if ($nl -eq "`r`n") { "CRLF" } else { "LF" }), ($insertAt + 1))
    Add-Report ("INSERT    {0}  (行尾={1}, 插入位置=第 {2} 行)" -f $relPath, $(if ($nl -eq "`r`n") { "CRLF" } else { "LF" }), ($insertAt + 1))

    if ($Apply) {
        try {
            [System.IO.File]::WriteAllText($f.FullName, $newText, $utf8NoBom)
        } catch {
            $failed++
            Write-Host ("ERROR     {0}  (写入失败: {1})" -f $relPath, $_.Exception.Message) -ForegroundColor Red
            Add-Report ("ERROR     {0}  (写入失败: {1})" -f $relPath, $_.Exception.Message)
        }
    }
}

Write-Host ""
Write-Host "--------------------------------------------------"
Write-Host ("SKIP    : {0}" -f $skipped)
Write-Host ("INSERT  : {0}" -f $inserted)
Write-Host ("EXCLUDE : {0}" -f $excluded)
Write-Host ("ERROR   : {0}" -f $failed)
Write-Host ("--------------------------------------------------"
)
if (-not $Apply) {
    Write-Host "DRY-RUN 结束，未修改任何文件。审核后加 -Apply 执行。"
}

if ($ReportPath -ne "") {
    $rp = if ([System.IO.Path]::IsPathRooted($ReportPath)) { $ReportPath } else { Join-Path $repoRoot $ReportPath }
    [System.IO.File]::WriteAllText($rp, ($report -join "`r`n"), $utf8NoBom)
    Write-Host "报告已写入 : $rp"
}

if ($failed -gt 0) { exit 1 }
exit 0
