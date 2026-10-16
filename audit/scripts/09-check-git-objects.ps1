# 09-check-git-objects.ps1
# 守门：检查 .git/objects 内是否有非 git 标准格式文件（I113/I114 防护）
# 用途：并入每周巡检（I110）

param([string]$RepoRoot = "D:\QiaoPM")

$ErrorActionPreference = "Stop"
$gitObjects = Join-Path $RepoRoot ".git\objects"
if (-not (Test-Path $gitObjects)) {
    Write-Host "SKIP: .git/objects not found"
    exit 0
}

$issues = @()

# 1. loose objects 必须 zlib 头（78 01 / 78 9C / 78 DA / 78 5E）
Get-ChildItem $gitObjects -Recurse -File |
  Where-Object { $_.FullName -match '\\objects\\[0-9a-f]{2}\\[0-9a-f]{38}$' } |
  ForEach-Object {
    $h = [System.IO.File]::ReadAllBytes($_.FullName)[0..3]
    $hex = ($h | ForEach-Object { $_.ToString('X2') }) -join ' '
    if ($hex -notmatch '^78 (01|9C|DA|5E)') {
      $issues += "LOOSE_BAD_HEAD: $($_.FullName)  ->  $hex"
    }
  }

# 2. pack / idx / rev 必须标准魔数
Get-ChildItem "$gitObjects\pack" -File -ErrorAction SilentlyContinue | ForEach-Object {
  $h = [System.IO.File]::ReadAllBytes($_.FullName)[0..3]
  $hex = ($h | ForEach-Object { $_.ToString('X2') }) -join ' '
  $ext = $_.Extension
  $expected = @{ '.pack' = '50 41 43 4B'; '.idx' = 'FF 74 4F 63'; '.rev' = '52 49 44 58' }
  if ($expected.ContainsKey($ext) -and $hex -ne $expected[$ext]) {
    $issues += "PACK_BAD_HEAD: $($_.FullName)  ->  $hex"
  }
}

if ($issues.Count -eq 0) {
  Write-Host "OK: .git/objects clean"
  exit 0
} else {
  Write-Host "FAIL: $($issues.Count) issue(s) found"
  $issues | ForEach-Object { Write-Host "  $_" }
  exit 1
}
