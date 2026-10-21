# I116 备份恢复演练证据（2026-10-22）

## 环境
- 演练容器：`mysql-dr-test`（mysql:8.0.46，端口 3308）
- 演练库：`meiyetec_drill`
- 生产库：`meiyetec`（3307），282 表 / 30.89 MB / 34,633 行
- 生产库全程未参与恢复，演练后已 `docker rm -f` 清理

## 备份
- 命令：`mysqldump --single-transaction --quick --routines --triggers --events --default-character-set=utf8mb4`
- 耗时：2.5 秒
- 文件：`full_backup.sql`，10,297,652 字节（10.3 MB），281 CREATE TABLE
- **注意**：此备份为手工 mysqldump 生成，非 `backup-db.sh` 产物（见 I123）

## 恢复
- 耗时：**41.2 秒（0.69 分钟）**
- 目标 RTO：≤ 60 分钟
- 结论：✅ 大幅达标

## 数据完整性
| 检查 | 生产 | 演练 | 结果 |
|---|---|---|---|
| 表数量 | 282 | 282 | ✅ |
| crm_member 行数 | 507 | 507 | ✅ |
| pos_order 行数 | 1848 | 1848 | ✅ |
| pos_order_item 行数 | 180 | 180 | ✅ |
| crm_cross_store_rule 行数 | 12 | 12 | ✅ |
| crm_member CHECKSUM | 838868750 | 838868750 | ✅ |
| pos_order CHECKSUM | 3256867157 | 3256867157 | ✅ |

## 本目录文件
- `README.md`：本文件（入库）
- `full_backup.sql`：演练用备份（10.3 MB，**不入库**，本地留存，已 .gitignore）

## 关联
- I116 演练记录：`audit/issues/I116-backup-drill-evidence-gap.md`
- 暴露的缺口：`audit/issues/I123-backup-automation-not-deployed.md`
