# Reality Score 自动评分脚本（PowerShell 版）
# 严禁人工修改分数，必须基于代码扫描结果自动计算
# 用法：powershell -ExecutionPolicy Bypass -File docs/governance/reality_score.ps1

$ErrorActionPreference = "Stop"

$Backend   = "D:\QiaoPM\backend\src\main\java\com\meiyetec"
$SqlDir    = "D:\QiaoPM\backend\src\main\resources\db\sql"
$Frontend  = "D:\QiaoPM\frontend\meiyetec-web\e2e"
$Doc       = "D:\QiaoPM\docs\audit\FEATURE_COMPARISON_MATRIX.md"
$Inventory = "D:\QiaoPM\docs\governance\feature_inventory.tsv"
$OutDir    = "D:\QiaoPM\docs\evidence\reality"

if (!(Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$OutPath   = Join-Path $OutDir "reality_score_${Timestamp}.json"

# ---------- 1. 代码实装度扫描（Controller + Service + Mapper + Entity 至少 4 类） ----------
function Score-Code($ctrlKeyword, $svcKeyword, $mapperKeyword, $entityKeyword) {
    # Controller 既可能在 controller/ 目录，也可能在 service/ 目录（合并类）
    $ctrlDir = @(Get-ChildItem "$Backend\controller" -Filter "*.java" -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match $ctrlKeyword })
    $ctrlSvc = @(Get-ChildItem "$Backend\service" -Filter "*.java" -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match $ctrlKeyword -and (Select-String -Path $_.FullName -Pattern '@RestController|@RequestMapping' -Quiet -ErrorAction SilentlyContinue) })
    $ctrl = @($ctrlDir + $ctrlSvc)

    $svc  = @(Get-ChildItem "$Backend\service"    -Filter "*.java" -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match $svcKeyword })
    $mapper = @(Get-ChildItem "$Backend\mapper"   -Filter "*.java" -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -match $mapperKeyword })
    $ent = @(Get-ChildItem "$Backend\entity"      -Filter "*.java" -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -match $entityKeyword })

    $hasSQL = @(Get-ChildItem $SqlDir -Filter "*.sql" -Recurse -ErrorAction SilentlyContinue).Count

    $layers = 0
    $layers += $ctrl.Count
    $layers += $svc.Count
    $layers += $mapper.Count
    $layers += $ent.Count

    if ($layers -ge 4 -and $svc.Count -ge 1 -and $ctrl.Count -ge 1) { return 1.0 }
    if ($svc.Count -ge 1 -and $ctrl.Count -ge 1) { return 0.7 }
    if ($svc.Count -ge 1 -or $ctrl.Count -ge 1) { return 0.4 }
    return 0.0
}

# ---------- 2. E2E 覆盖度扫描 ----------
function Score-E2E($spec) {
    if ([string]::IsNullOrWhiteSpace($spec)) { return 0.0 }
    $files = @(Get-ChildItem $Frontend -Filter "*.spec.ts" -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "${spec}*" })
    if ($files.Count -eq 0) { return 0.0 }
    $totalTests = 0
    foreach ($f in $files) {
        $totalTests += (Select-String -Path $f.FullName -Pattern '^\s*test\(' -ErrorAction SilentlyContinue).Count
    }
    if ($totalTests -ge 10) { return 1.0 }
    if ($totalTests -ge 5)  { return 0.8 }
    if ($totalTests -ge 1)  { return 0.5 }
    return 0.0
}

# ---------- 3. 运行时探测 ----------
$global:Token = $null
function Get-Token() {
    if ($global:Token) { return $global:Token }
    try {
        $body = @{ username = 'admin'; password = '123456' } | ConvertTo-Json
        $r = Invoke-RestMethod -Uri "http://localhost:8080/auth/login" -Method Post -Body $body -ContentType "application/json" -TimeoutSec 3
        $global:Token = $r.data.token
        return $global:Token
    } catch { return "" }
}

function Probe-Url($url) {
    if ([string]::IsNullOrWhiteSpace($url)) { return "N/A" }
    try {
        $token = Get-Token
        $headers = @{}
        if ($token) { $headers["Authorization"] = "Bearer $token" }
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 4 -Headers $headers -ErrorAction Stop
        if ($resp.StatusCode -eq 200) { return "1.0" }
        if ($resp.StatusCode -in @(401, 403, 404)) { return "0.5" }
        return "0.5"
    } catch {
        $msg = $_.Exception.Message
        if ($msg -match "Unable to connect|connection|timeout|refused|目标计算机|actively refused") { return "N/A" }
        return "0.0"
    }
}

# ---------- 4. 文档同步 ----------
function Check-Doc($keyword) {
    if ([string]::IsNullOrWhiteSpace($keyword)) { return 0.5 }
    try {
        $matches = Select-String -Path $Doc -Pattern ([regex]::Escape($keyword)) -ErrorAction Stop
        return if ($matches) { 1.0 } else { 0.5 }
    } catch { return 0.5 }
}

# ---------- 5. 计算总分 ----------
function Calc-Score($code, $e2e, $runtime, $doc) {
    if ($runtime -eq "N/A") {
        return [math]::Round((0.55 * $code) + (0.27 * $e2e) + (0.18 * $doc), 4)
    }
    return [math]::Round((0.50 * $code) + (0.25 * $e2e) + (0.15 * [double]$runtime) + (0.10 * $doc), 4)
}

# ---------- 6. 读取 TSV ----------
$lines = Get-Content $Inventory -Encoding UTF8 | Where-Object { $_ -notmatch '^\s*#' -and $_ -notmatch '^\s*$' }
$features = @()
foreach ($line in $lines) {
    $cols = $line -split "`t"
    if ($cols.Count -lt 8) { continue }
    $fid          = $cols[0]
    $module       = $cols[1]
    $ctrlKeyword  = $cols[2]
    $svcKeyword   = $cols[3]
    $mapperKey    = $cols[4]
    $entityKey    = $cols[5]
    $spec         = $cols[6]
    $url          = $cols[7]
    $keyword      = $cols[8]

    $codeScore    = Score-Code $ctrlKeyword $svcKeyword $mapperKey $entityKey
    $e2eScore     = Score-E2E $spec
    $runtimeScore = Probe-Url $url
    $docScore     = Check-Doc $keyword
    $total        = Calc-Score $codeScore $e2eScore $runtimeScore $docScore

    $features += [PSCustomObject]@{
        id      = $fid
        module  = $module
        code    = $codeScore
        e2e     = $e2eScore
        runtime = $runtimeScore
        doc     = $docScore
        score   = $total
    }
}

# ---------- 7. 计算整体 Reality Score ----------
$avgScore = if ($features.Count -gt 0) {
    [math]::Round((($features | Measure-Object -Property score -Average).Average), 4)
} else { 0.0 }

# ---------- 8. 输出 JSON ----------
$json = @{
    scored_at     = (Get-Date -Format "s")
    model         = "0.50*code + 0.25*e2e + 0.15*runtime + 0.10*doc (runtime=N/A 时调整为 0.55/0.27/0.18)"
    inventory     = $Inventory
    feature_count = $features.Count
    overall_score = $avgScore
    features      = @($features)
}
$json | ConvertTo-Json -Depth 5 | Set-Content $OutPath -Encoding UTF8

# ---------- 9. 终端概要 ----------
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Reality Score 计算完成" -ForegroundColor Cyan
Write-Host "输出文件: $OutPath" -ForegroundColor Cyan
Write-Host "------------------------------------------" -ForegroundColor Cyan
Write-Host ("功能总数: {0}" -f $features.Count)
Write-Host ("Reality Score: {0:P1}" -f $avgScore) -ForegroundColor Green
Write-Host "------------------------------------------" -ForegroundColor Cyan
$features | ForEach-Object {
    $color = if ($_.score -ge 0.8) { "Green" }
              elseif ($_.score -ge 0.5) { "Yellow" }
              else { "Red" }
    $line = "  {0,-6} {1,-22} code={2} e2e={3} runtime={4} doc={5} -> {6}" -f $_.id, $_.module, $_.code, $_.e2e, $_.runtime, $_.doc, $_.score
    Write-Host $line -ForegroundColor $color
}
Write-Host "==========================================" -ForegroundColor Cyan