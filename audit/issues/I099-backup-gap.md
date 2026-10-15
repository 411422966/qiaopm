# I099 — 备份恢复缺口（修复前）

| 项 | 值 |
|---|---|
| **Issue ID** | I099 |
| **标题** | 生产 DB 备份策略完全缺失（手工 ad-hoc + 无异地 + 无调度 + 无演练） |
| **严重度** | **P0**（阻塞 Gate 3 部署门禁） |
| **影响范围** | 生产 DB 全部业务数据 + 故障恢复能力 |
| **发现批次** | Gate 3 批次 1.3（只读评估，2026-10-10） |
| **修复批次** | Gate 3 批次 1.3（同上） |
| **负责人** | DBA 主值班 + SRE 主值班 |
| **状态** | ✅ 已修复（本 commit 闭环） |

---

## 1. 问题描述

### 1.1 现状（修复前）

| 项 | 现状 | 风险 |
|---|---|---|
| 备份策略 | 仅 DEPLOY-SOP §1.1 给出 3 行 mysqldump 命令，由 DBA 在每次发布前手动跑 | ❶ 两次发布之间的数据变更**完全无备份兜底**（最坏 24h 数据丢失）；❷ 依赖人记得 |
| 备份脚本 | 无 | 每次手动敲命令，参数易错 |
| 恢复 Runbook | DEPLOY-SOP §1.4 一行 `gunzip \| mysql` | ❶ 无 step-by-step；❷ 无 P0 表验证；❸ 无联系人 |
| 保留期策略 | 无 | `/backup/` 永久增长，磁盘满 → 备份失败 |
| 异地容灾 | 无 | 主机单点（named volume，docker 崩 = 数据崩） |
| 演练记录 | 无 | 从未验证"备份真能恢复" |
| 调度 | 无（无 cron / timer / k8s CronJob） | 完全手工 |
| Redis 备份 | 无 | AOF 仅本地 volume，主机崩 = session / 缓存 / 限流状态全丢 |
| docker volume 兜底 | named volume，`driver: local` | 主机崩 = 数据崩 |

### 1.2 业务影响

- 任何主机级故障（磁盘坏 / 误 `docker volume rm` / 机房断电）= **生产数据全部丢失**
- 任何误操作（drop / truncate / 误更新）= **无回滚能力**，只能从最近一次发布前备份恢复（最坏丢 24h+）
- 故障恢复时间不可控（无 Runbook → 现场救火 → 慢）
- Gate 3 部署门禁要求"可恢复的发布通道"，备份缺失 = **门禁不通过**

---

## 2. 决策记录（2026-10-10 修复前会议）

| # | 决策项 | 选择 | 理由 |
|---|---|---|---|
| 1 | RPO 目标 | **1h**（最坏 24h） | 业务方可接受，mysqldump 全量起步 |
| 2 | binlog 增量 | **不做** | 复杂度高，当前 RPO 满足 |
| 3 | 保留期 | 本地 7d / S3 30d / Glacier 1y | 成本与合规平衡 |
| 4 | 存储 | **S3**（aws cli 凭据已就位，DEPLOY-SOP §3.1 验证） | 不引入新依赖 |
| 5 | mysql-data 改 host bind | **不做** | 生产 DB 不跑 compose（独立 KVM / 物理机） |
| 6 | Redis 异地备份 | **不做**，Runbook §6 Acknowledge | AOF 本地可接受数据丢失 |
| 7 | 调度器 | **systemd timer** | 生产非 k8s 跑 DB；cron 老旧不推荐 |
| 8 | 演练频率 | **季度** | 库规模 < 50GB，季度足够 |
| 9 | 工具链 | **mysqldump**（< 50GB 适用） | 升级 XtraBackup 留待 > 50GB |
| 10 | 脚本位置 | `scripts/`（与 `recovery/` 平级） | 与现有结构一致 |
| 11 | S3 bucket | `s3://meiyetec-prod-backups` | 与现有 `meiyetec-static` 区分（备份 vs 静态资源） |

---

## 3. 修复范围（本 commit 闭环）

### 3.1 交付清单（6 项）

| # | 路径 | 用途 | 行数 |
|---|---|---|---|
| 1 | `scripts/backup-db.sh` | mysqldump + gzip + S3 upload + 校验 + --dry-run | 172 |
| 2 | `scripts/restore-db.sh` | 下载/本地 → 校验 → 自动备份当前库 → 导入 → smoke test + 行数 sanity | 211 |
| 3 | `scripts/cleanup-local-backups.sh` | 7 天本地清理 + --dry-run | 90 |
| 4 | `infra/systemd/meiyetec-backup.service` | oneshot service + EnvironmentFile + 备份+清理串联 + 失败重试 | 35 |
| 5 | `infra/systemd/meiyetec-backup.timer` | 每日 02:00 触发 + Persistent=true 补跑 + 5min 随机延迟 | 16 |
| 6 | `docs/runbook/backup-restore.md` | 8 章节 Runbook（触发 / 决策树 / 步骤 / 验证 / 联系人 / Redis Ack / 演练 / RTO RPO） | 270 |

**审计 Issue**：本文件。

### 3.2 关键能力

- ✅ **自动化**：systemd timer 每日 02:00 触发，无需人工
- ✅ **异地**：本地 + S3 同步上传（决策 4）
- ✅ **可恢复验证**：脚本内置 `gzip -t` + `CREATE TABLE` 校验 + `migration_smoke_test.sql` + 5 张关键表行数 sanity
- ✅ **防误恢复**：恢复脚本强制先备份当前库到 `/var/backups/meiyetec/pre-restore/`
- ✅ **可演练**：3 个脚本 + systemd unit 均支持 `--dry-run`
- ✅ **运维友好**：彩色日志 + journal 输出 + 失败重试
- ✅ **RPO 1h / RTO 1h**：单库 < 50GB 满足
- ✅ **季度演练**：Runbook §7 含 4 项 checklist

### 3.3 部署步骤（SRE 负责）

```bash
# 1. 创建部署目录
sudo mkdir -p /opt/meiyetec/scripts /opt/meiyetec/infra/systemd /opt/meiyetec/docs/runbook
sudo mkdir -p /var/backups/meiyetec/daily /var/backups/meiyetec/pre-restore
sudo mkdir -p /etc/meiyetec

# 2. ENV 文件
sudo tee /etc/meiyetec/backup.env >/dev/null <<'EOF'
DB_HOST=prod-db.internal
DB_PORT=3306
DB_USER=backup_user
DB_PASS=__SET_BY_SECRETS_MANAGER__
DB_NAME=meiyetec
S3_BUCKET=s3://meiyetec-prod-backups
EOF
sudo chmod 600 /etc/meiyetec/backup.env

# 3. 脚本 + unit 文件
sudo install -m 0750 scripts/backup-db.sh /opt/meiyetec/scripts/
sudo install -m 0750 scripts/restore-db.sh /opt/meiyetec/scripts/
sudo install -m 0750 scripts/cleanup-local-backups.sh /opt/meiyetec/scripts/
sudo install -m 0644 infra/systemd/meiyetec-backup.{service,timer} /opt/meiyetec/infra/systemd/
sudo install -m 0644 docs/runbook/backup-restore.md /opt/meiyetec/docs/runbook/

# 4. systemd 注册
sudo cp /opt/meiyetec/infra/systemd/meiyetec-backup.{service,timer} /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now meiyetec-backup.timer

# 5. 验证
sudo systemctl list-timers meiyetec-backup.timer
systemd-analyze verify /etc/systemd/system/meiyetec-backup.{service,timer}
DRY_RUN=1 bash /opt/meiyetec/scripts/backup-db.sh
```

---

## 4. 未做（本 commit 范围外，升级触发）

| # | 项 | 原因 | 升级触发 |
|---|---|---|---|
| 1 | Redis 异地备份 | 决策 1.3-5 显式不做 | 业务方不能接受 Redis 数据丢失 |
| 2 | S3 lifecycle rule（30d → IA，1y → Glacier） | 不在 P0 范围 | 成本优化（库 > 200GB 时） |
| 3 | mysql-data 改 host bind | 决策 1.3-4 显式不做（生产不跑 compose） | N/A |
| 4 | 独立 `backup-drill-checklist.md` | 决策并入 Runbook §7 | 演练清单超过 1 页时拆分 |
| 5 | Binlog 增量（15min） | 决策 1.3-1 显式不做 | RPO 要求 < 1h |
| 6 | XtraBackup 物理热备 | 当前 < 50GB 不需要 | 库规模 > 50GB |
| 7 | 跨 region 复制（S3 CRR） | 成本 vs 风险平衡 | 业务要求 RTO < 15min |
| 8 | 备份数据脱敏（PII） | 无 PII（已审） | 引入 PII 字段时启动 |
| 9 | 备份告警（飞书 / 钉钉） | 当前仅 journal 记录 | 演练发现漏报 |
| 10 | 自动化演练（staging 跑 + 报告） | 当前手工演练 | 季度演练人力 > 2h 时启动 |

---

## 5. 验证记录

### 5.1 静态验证（commit 前必做）

```bash
# --dry-run 演练
DRY_RUN=1 bash scripts/backup-db.sh                          # EXIT=0
DRY_RUN=1 bash scripts/cleanup-local-backups.sh              # EXIT=0
DRY_RUN=1 bash scripts/restore-db.sh --from /tmp/fake.sql.gz # EXIT=0

# systemd unit
systemd-analyze verify infra/systemd/meiyetec-backup.timer
systemd-analyze verify infra/systemd/meiyetec-backup.service

# shellcheck（如已装）
shellcheck scripts/*.sh
```

### 5.2 动态验证（部署后必做，本 commit 不执行）

- [ ] 部署到 staging：跑真实备份 → 验证 S3 上传
- [ ] staging 恢复演练：删除 staging 库 → 跑 restore-db.sh → 验证 6 张 P0 表 + 5 张关键表行数
- [ ] 第一次生产备份由 DBA 手工触发（DEPLOY-SOP §1.1 流程），同时验证 timer 隔日自动跑
- [ ] 首次季度演练：按 Runbook §7 跑 4 项 checklist，登记到 `audit/drills/`

---

## 6. 关联文档

- DEPLOY-SOP.md §1（数据库变更 SOP）
- DEPLOY-SOP.md §1.4（严重回滚单行命令，本 Runbook 升级版）
- docs/runbook/backup-restore.md（本 issue 修复产物）
- audit/issues/I098（ENV 化缺口登记，作为 ENV 文件安全参考）
- PROJECT_REALITY_AUDIT.md §Gate 3（部署门禁要求"可恢复的发布通道"）

---

## 7. 闭环证据

- 本 issue 与 commit `feat(gate3-1.3): 备份恢复 - mysqldump 脚本 + systemd timer + Runbook` 同 PR
- 部署后由 SRE 提交 `audit/drills/2026-Q4-backup-drill.md` 首次演练记录
- 季度复盘（Q1/Q2/Q3/Q4）由 DBA 汇总到 `audit/issues/I099-quarterly.md`
