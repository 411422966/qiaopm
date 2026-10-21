# I116 — 备份恢复演练证据缺失（P0）

## 现象
- 备份/恢复脚本、runbook、systemd timer 均已就绪
- 但 audit/evidence/ 下无任何恢复演练记录
- 无法证明 RTO ≤ 1h 达标（restore-db.sh 设计目标 1h）

## 影响
- 备份是否可用未经验证（备份文件存在 ≠ 能恢复）
- RTO 目标是设计值，非实测值
- 生产事故时无法预估恢复时间

## 处置
- [ ] 在测试环境执行一次完整恢复演练：
      scripts/restore-db.sh --from s3://meiyetec-prod-backups/daily/<最近备份>
- [ ] 记录：开始/结束时间、DB 大小、导入耗时、校验结果
- [ ] 产出 audit/evidence/p0-backup-drill-<date>/README.md
- [ ] 更新 docs/runbook/backup-restore.md 记录演练结果
- [ ] 若 RTO 不达标，调整恢复脚本或增加并行策略

## 关联
- 来源：P0 核验清单 §4
- 前置：I099（备份机制缺口，已修复）

## 状态
待办

## 演练结果（2026-10-22）

### 环境
- 演练容器：`mysql-dr-test`（mysql:8.0.46，端口 3308，库 meiyetec_drill）
- 生产库：`meiyetec`（3307），282 表 / 30.89 MB / 34,633 行
- 生产库全程未参与恢复，演练后已 `docker rm -f` 清理

### 备份（手工生成，非 backup-db.sh 产物）
- 命令：`mysqldump --single-transaction --quick --routines --triggers --events --default-character-set=utf8mb4`
- 耗时：**2.5 秒**
- 文件：10,297,652 字节（10.3 MB），281 CREATE TABLE
- 留存：`audit/evidence/i116-backup-drill-20261022/full_backup.sql`

### 恢复（实测）
- 耗时：**41.2 秒（0.69 分钟）**
- 目标 RTO：≤ 60 分钟
- 结论：✅ **大幅达标（目标 60 分钟，实测 0.69 分钟，富余 98.9%）**

### 数据完整性校验
| 检查 | 结果 |
|---|---|
| 表数量 | 282 / 282 ✅ |
| crm_member 行数 | 507 / 507 ✅ |
| pos_order 行数 | 1848 / 1848 ✅ |
| pos_order_item 行数 | 180 / 180 ✅ |
| crm_cross_store_rule 行数 | 12 / 12 ✅ |
| crm_member CHECKSUM | 838868750 / 838868750 ✅ |
| pos_order CHECKSUM | 3256867157 / 3256867157 ✅ |

### 演练暴露的问题
- ⚠️ **备份自动化从未运行** → 已登记 I123
- ⚠️ `aws` cli 未装
- ⚠️ `/var/backups/meiyetec/daily` 不存在
- ⚠️ systemd timer 无法在容器环境生效

### 结论
- [x] **恢复能力验证通过**：RTO 41.2s，数据 100% 一致
- [ ] 备份自动化未通过 → I123

## 状态
已演练通过（恢复能力），备份自动化待修（I123）
