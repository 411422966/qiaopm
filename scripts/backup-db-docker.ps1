# backup-db-docker.ps1 v4
# 修复：用 cmd /c 直接重定向到宿主机文件，绕开容器内文件 + docker cp
# 修复：控制台 UTF-8 避免乱码

param(
    [string]$BackupDir = "D:\QiaoPM\backup",
    [string]$LogDir = "D:\QiaoPM\backup\logs",
    [int]$RetentionDays = 7,
    [string]$Container = "meiyetec-mysql",
    [string]$DbUser = "root",
    [string]$DbPass = "root123456",
    [string]$DbName = "meiyetec"
)

# 控制台 UTF-8，避免中文乱码
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ErrorActionPreference = "Continue"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dateOnly = Get-Date -Format "yyyyMMdd"
$backupFile = Join-Path $BackupDir "meiyetec_${timestamp}.sql"
$errFile = Join-Path $BackupDir "meiyetec_${timestamp}.err.log"
$logFile = Join-Path $LogDir "backup-${dateOnly}.log"

function Write-Log($msg) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $msg"
    $line | Out-File -FilePath $logFile -Append -Encoding utf8
    Write-Host $line
}

try {
    Write-Log "=== 备份开始 ==="
    Write-Log "目标文件: $backupFile"

    $containerStatus = (docker inspect -f '{{.State.Status}}' $Container 2>$null)
    if ($containerStatus -ne "running") { throw "容器 $Container 状态异常: $containerStatus" }
    Write-Log "容器状态: running"

    # 用 cmd /c 把 docker exec 的 stdout 直接重定向到宿主机文件
    # 不经过 PowerShell 管道，不做编码转换
    $cmdLine = "docker exec $Container mysqldump -u${DbUser} -p${DbPass} --single-transaction --quick --routines --triggers --events --default-character-set=utf8mb4 ${DbName} 1>`"$backupFile`" 2>`"$errFile`""
    Write-Log "执行命令"
    cmd /c $cmdLine

    # 验证宿主机文件
    if (-not (Test-Path $backupFile)) { throw "备份文件未生成" }
    $fileInfo = Get-Item $backupFile
    if ($fileInfo.Length -lt 1000000) {
        $errContent = if (Test-Path $errFile) { Get-Content $errFile -Raw } else { "(无错误文件)" }
        throw "备份文件过小: $($fileInfo.Length) 字节，错误: $errContent"
    }

    # 验证内容含 CREATE TABLE
    $createCount = (Select-String -Path $backupFile -Pattern '^CREATE TABLE' -Encoding UTF8 -ErrorAction SilentlyContinue).Count
    if ($createCount -lt 100) { throw "CREATE TABLE 数量异常: $createCount" }

    Write-Log "备份成功: $($fileInfo.Length) 字节，$createCount 张表"

    # 清理错误文件（成功时不需要）
    if (Test-Path $errFile) { Remove-Item $errFile -Force }

    # 清理超过保留期的本地备份
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -Path $BackupDir -Filter "meiyetec_*.sql" -File |
        Where-Object { $_.LastWriteTime -lt $cutoff } |
        ForEach-Object {
            Remove-Item $_.FullName -Force
            Write-Log "清理旧备份: $($_.Name)"
        }

    Write-Log "=== 备份成功完成 ==="
    exit 0
}
catch {
    Write-Log "=== 备份失败: $_ ==="
    exit 1
}
