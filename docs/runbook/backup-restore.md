# Runbook — Meiyetec 数据库备份恢复（R1.0-E  T-E04）

> **定位**：DBA / SRE 故障恢复的 step-by-step 操作手册。
> 配套：DEPLOY-SOP.md §1（数据库变更）、audit/issues/I099-backup-gap.md（本 Runbook 修复的缺口登记）
>
> **RTO / RPO 目标**（决策 1.3-1/2/6）：
> - RPO = 1h（每天 02:00 全量；误删窗口最长 24h）
> - RTO = 1h（单库 < 50GB）
> - 演练频率：季度
> - 保留：本地 7d / S3 30d / Glacier 1y

---

## §1 触发场景

| 场景 | 紧急度 | 操作入口 |
|---|---|---|
| **A. 误删数据**（drop / truncate / 误更新） | 高 | §3 步骤 + 选误删前的最近全量 |
| **B. 灾难恢复**（主机崩 / 磁盘坏） | 极高 | §3 步骤 + 选 S3 最新全量 |
| **C. 数据迁移**（机房搬迁 / DB 升级前后） | 中 | §3 步骤 + 选迁移前最近全量 |
| **D. 发布严重回滚** | 极高 | DEPLOY-SOP §1.4 单行回滚 + §3 验证清单 |

> ⚠️ 本 Runbook **不**处理「Binlog 增量恢复」（决策 1.3-1：不做 15min binlog）。
> 若需要 PIT（point-in-time）恢复，**当前能力不支持**，请先回退到「最近全量」并 ack 24h 数据丢失。

---

## §2 决策树 — 选哪个备份点

```
[故障]
  │
  ├─── 已知误删时间点 YYYY-MM-DD HH:MM？
  │      │
  │      └── 是 → 选 YYYY-MM-DD 之前最近一次 02:00 全量
  │             （误删可能发生在备份后 → 至多丢 24h 数据）
  │
  ├─── 仅主机崩、磁盘坏、机房断电？
  │      └── 选 S3 最新全量（s3://meiyetec-prod-backups/daily/ 列表排序）
  │
  └─── 不确定？
         └── 默认 S3 最新全量；不冒险 PIT
```

**找备份点命令**：

```bash
# 列 S3 全部备份（按时间倒序）
aws s3 ls s3://meiyetec-prod-backups/daily/ | sort -r | head -10

# 列本地保留的备份
ls -lh /var/backups/meiyetec/daily/
```

---

## §3 恢复步骤（严格按顺序执行）

### 3.1 决策 & 准备（5 分钟）

- [ ] 确认触发场景（§1）
- [ ] 跑 §2 决策树选定 `--from` 目标
- [ ] 通知 PM / 业务方（预计 RTO 1h）
- [ ] 准备 `backup.env`（含 DB_HOST/USER/PASS/NAME）：

```bash
# /etc/meiyetec/backup.env 模板（root only, chmod 600）
DB_HOST=prod-db.internal
DB_PORT=3306
DB_USER=backup_user
DB_PASS=********
DB_NAME=meiyetec
S3_BUCKET=s3://meiyetec-prod-backups
```

### 3.2 停服（避免脏写）

```bash
# 1. nginx 上游摘除（停服标志）
sudo sed -i 's/server 127\.0\.0\.1:8080;/# server 127.0.0.1:8080;/' /etc/nginx/conf.d/meiyetec.conf
sudo nginx -s reload

# 2. 确认无活跃写入
mysql -h $DB_HOST -u root -p -e "
SELECT id, user, db, command, time, state, info
FROM information_schema.processlist
WHERE db = 'meiyetec' AND command != 'Sleep' AND time > 5;
"
# 期望：0 行
```

### 3.3 预演（强烈推荐，先 dry-run）

```bash
DRY_RUN=1 bash scripts/restore-db.sh \
    --from s3://meiyetec-prod-backups/daily/meiyetec_YYYYMMDD_HHMMSS.sql.gz
# 期望：EXIT=0
```

### 3.4 正式恢复

```bash
# 真跑（脚本会自动备份当前库到 /var/backups/meiyetec/pre-restore/）
bash scripts/restore-db.sh \
    --from s3://meiyetec-prod-backups/daily/meiyetec_YYYYMMDD_HHMMSS.sql.gz
```

**脚本会做**：
1. 下载备份到临时目录
2. `gzip -t` 校验
3. head 200 行确认含 `CREATE TABLE`
4. **`mysqldump 当前库` 备份到 `/var/backups/meiyetec/pre-restore/`**（防误恢复覆盖）
5. `gunzip | mysql` 导入
6. 跑 `migration_smoke_test.sql`
7. 打印 5 张关键表行数

### 3.5 验证（§4 清单）

### 3.6 起服

```bash
# 1. 恢复 nginx 上游
sudo sed -i 's/# server 127\.0\.0\.1:8080;/server 127.0.0.1:8080;/' /etc/nginx/conf.d/meiyetec.conf
sudo nginx -s reload

# 2. 健康检查
curl -f http://localhost:8080/actuator/health

# 3. 冒烟用例（DEPLOY-SOP §5）：登录 → 下单 → 支付 → 退款
```

---

## §4 验证清单（5 张关键表 + 行数 sanity）

恢复后**必须**人工确认：

```sql
-- 1. 6 张 P0 表存在性（迁移冒烟已自动跑过；这里人工二次确认）
SHOW TABLES LIKE 'pos_order_consumption';
SHOW TABLES LIKE 'pos_refund';
SHOW TABLES LIKE 'sys_idempotency_record';
SHOW TABLES LIKE 'stock_record';
SHOW TABLES LIKE 'crm_member';
SHOW TABLES LIKE 'crm_wallet';

-- 2. 行数合理性（与昨日对比，差异 < 30% 为正常）
SELECT COUNT(*) AS pos_orders_today
FROM pos_order_consumption
WHERE update_time >= CURDATE();

SELECT COUNT(*) AS refunds_today
FROM pos_refund
WHERE update_time >= CURDATE();

-- 3. tenant_id 完整性（不应有 NULL）
SELECT COUNT(*) AS null_tenant
FROM crm_member
WHERE tenant_id IS NULL;
-- 期望：0

-- 4. 业务对账：昨日订单金额总和
SELECT IFNULL(SUM(amount), 0) AS yesterday_revenue
FROM pos_order_consumption
WHERE DATE(update_time) = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
  AND status = 2;  -- 已支付
-- 与昨日业务报表交叉比对
```

**验证不通过 → 立即回滚**：

```bash
# 用本次恢复时自动备份的「pre-restore」快照反向恢复
gunzip -c /var/backups/meiyetec/pre-restore/meiyetec_YYYYMMDD_HHMMSS.sql.gz \
  | mysql -h $DB_HOST -u $DB_USER -p $DB_NAME
```

---

## §5 联系人

| 周次 | 主联系人 | 备份 | 电话 |
|---|---|---|---|
| 2026-W42 | DBA 主值班 | SRE 主值班 | 见 on-call 群 |

**SRE 主值班响应 SLA**：
- 工作日 9-18：15 min
- 非工作日：30 min
- 灾难恢复：5 min 启动

---

## §6 Redis 数据声明（Acknowledge）

> **决策 1.3-5**：Redis 不做异地备份。

**声明**：

1. Redis `redis-data` volume（`--appendonly yes` AOF 持久化）**只在本地**。
2. **可丢失数据**（按业务影响评估）：
   - ✅ Session token：用户重新登录即可
   - ✅ 业务缓存：DB 回源刷新
   - ✅ 幂等记录（`sys_idempotency_record` 在 MySQL，Redis 仅缓存）：重新生成
   - ⚠️ 限流计数器：可能瞬时穿透，但有 DB 兜底
3. **不可丢失数据**（如有新增）：**必须**登记并升级备份方案（X）。
4. 主机灾难恢复时 Redis 为空，**可接受 5-15 分钟业务降级**（缓存冷启动 + DB 直读）。

> 若业务方**不接受**上述任一「可丢失」项，请开新 issue 升级到 XtraBackup + Redis RDB 异地。

---

## §7 季度演练 Checklist（简化版）

> **决策 1.3-6**：季度（1/4/7/10 月最后一周）。
> 目标：验证「备份真能恢复」，**不在生产跑**，用 staging 库 + 同结构 dump。

```markdown
## Meiyetec 备份恢复季度演练 — YYYY-QX

- [ ] **1. 备份完整性验证**（耗时 ≤ 5 min）
  - 拉 S3 最新一份 .sql.gz
  - `gzip -t` 通过
  - head 200 行含 `CREATE TABLE`
  - 文件大小 > 100KB（不空）

- [ ] **2. 恢复时间达标**（目标 ≤ 1h）
  - 在 staging 环境跑 `restore-db.sh --from <s3-file>`
  - 记录：download X min / import Y min / verify Z min
  - 评估：X+Y+Z ≤ 60 min？

- [ ] **3. 数据对账**（耗时 ≤ 15 min）
  - 5 张关键表行数 vs 上周报告（差异 < 30% 算通过）
  - tenant_id NULL 数 = 0
  - 6 张 P0 表存在性 OK

- [ ] **4. 报警链路**（耗时 ≤ 5 min）
  - 故意 kill backup timer 一次
  - 验证：`journalctl -u meiyetec-backup.service` 有错误记录
  - 验证：on-call 群收到报警（或邮件）
  - 恢复 timer

- [ ] **结论**
  - 通过 / 不通过
  - 改进项：____
  - 下次演练时间：YYYY-QX+1

**DBA 签字**：
**SRE 签字**：
**日期**：
```

演练结果登记到 `audit/drills/YYYY-QX-backup-drill.md`。

---

## §8 RTO / RPO 说明

| 指标 | 目标 | 实际可达 | 备注 |
|---|---|---|---|
| **RPO** (数据可丢失时长) | 1h | **≤ 24h** | 当前仅全量，无 binlog；误删最坏丢 24h |
| **RTO** (故障到恢复时长) | 1h | **≤ 1h** | 单库 < 50GB：下载 ≤ 10min + 导入 ≤ 30min + 验证 ≤ 10min |
| **恢复验证耗时** | < 15min | — | 含 §4 全部项 |
| **演练频率** | 季度 | — | §7 |

**升级触发**（任一发生即开新 issue）：
- 库规模 > 50GB → 改 XtraBackup
- RPO 要求 < 24h → 加 binlog 增量
- RTO 要求 < 30min → 改热备 + VIP 切换
- 业务方不能接受 Redis 数据丢失 → 加 Redis RDB 异地
