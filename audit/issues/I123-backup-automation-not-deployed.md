# I123 — 备份自动化从未运行（脚本存在但未部署）

## 现象（I116 演练暴露）
- `scripts/backup-db.sh` 存在但从未执行
- `meiyetec-api` 容器内无 `aws` 命令，S3 上传不可能成功
- `/var/backups/meiyetec/daily` 目录不存在
- `infra/systemd/meiyetec-backup.timer` 已写，但容器环境无 systemd
- 生产库 282 表 / 30.89 MB / 34,633 行，无任何历史备份快照

## 影响
- 生产数据完全无备份保护
- 一旦库损坏/误删，无任何恢复途径
- RPO 目标 1h 完全不满足（实际 RPO = ∞）
- I116 演练虽然验证了「有备份文件时能恢复」，但备份文件本身不存在

## 处置
- [ ] 在宿主机（Windows 或 WSL）部署定时任务触发 mysqldump
  - 方案 A：Windows 任务计划 → docker exec mysqldump → 落盘到 D:\QiaoPM\backup\
  - 方案 B：WSL cron → docker exec → rsync 到备份盘
  - 方案 C：在 mysql 容器内装 cron（不推荐，容器重建丢失）
- [ ] 安装 aws cli 或用 PowerShell 的 AWS.Tools.S3 模块上传 S3
- [ ] 备份文件落盘到宿主机（不能只在容器内 /tmp）
- [ ] 首次备份成功后重跑 I116 演练（使用真实 backup-db.sh 产物）
- [ ] 监控：备份失败告警（邮件 / 企业微信 webhook）

## 关联
- 来源：I116 备份恢复演练暴露
- 前置：I116（恢复能力已演练通过：RTO 41.2s，数据 100% 一致）

## 状态
待办
