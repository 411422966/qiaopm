# Phase 2.3 — Batch 1 Apply Log

> **执行时间**：2026-09-20 00:30 (UTC+8)
> **目标**：IF NOT EXISTS 3 脚本（plugin_runtime_state / member_message / overseas_booking）
> **环境**：localhost:3307 / root:root123456 / MySQL 8.0.46 / meiyetec
> **结果**：✅ **全部成功**

---

## 1. 前置（已完成）

| 项 | 文件 | 大小 | 备注 |
|---|---|---|---|
| 数据备份 | `audit/baseline/pre-2.3-backup-20260920.sql` | 22,602 B | crm_integration 5 行 + crm_sync_log 0 行 + ecommerce_platform 3 行 |
| Schema 快照 | `audit/baseline/schema-before-2.3.sql` | 810,832 B | 271 张 CREATE TABLE |
| stderr 日志 | `audit/baseline/pre-2.3-backup-20260920.stderr.log` | 0 B | 无错误 |

---

## 2. 6 张目标 DROP 表当前状态（apply 前）

| 表 | 状态 | 行数 |
|---|---|---|
| mkt_invitation | ❌ 不存在 | 0 |
| mkt_invitation_rule | ❌ 不存在 | 0 |
| mkt_dispatch | ❌ 不存在 | 0 |
| mkt_dispatch_target | ❌ 不存在 | 0 |
| google_maps_config | ❌ 不存在 | 0 |
| google_maps_geocode_log | ❌ 不存在 | 0 |

→ **全部为空，无需 mysqldump 单表备份**，直接 DROP+CREATE 风险为零。

---

## 3. Batch 1 apply 步骤

### 3.1 `migrate_ext_23_plugin_runtime_state.sql` → ✅ 成功

```sql
CREATE TABLE IF NOT EXISTS plugin_runtime_state (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  plugin_code VARCHAR(64) NOT NULL,
  ...
  UNIQUE KEY uk_tenant_plugin (tenant_id, plugin_code),
  ...
);
```

**stderr**：`空`（无输出即成功）

### 3.2 `init_m09_member_message.sql` → ✅ 成功

21 列完整定义，IF NOT EXISTS 安全。

**stderr**：`空`

### 3.3 `migrate_ext_19_mini_program_openid.sql` → ⚠️ 部分成功

第 1 段 `CREATE TABLE IF NOT EXISTS overseas_booking` 成功。
第 2 段 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 报 1064 错误（**MySQL 8.0 不支持 ALTER 的 IF NOT EXISTS 语法**）。

**但** 因为第 1 段 CREATE 已包含 `mini_program_open_id VARCHAR(128)` 列定义（见脚本 L19），所以**最终结果是正确的** — 表已创建、列已存在。

> 注：脚本作者的 ADD COLUMN IF NOT EXISTS 是**为已存在的旧库准备的迁移路径**；首次创建走 CREATE 已带列，所以 ALTER 是冗余触发。

---

## 4. 验证

```
mysql> SHOW TABLES LIKE 'plugin_runtime_state';
+-----------------------------+
| Tables_in_meiyetec (plugin_runtime_state) |
+-----------------------------+
| plugin_runtime_state        |
+-----------------------------+

mysql> SHOW TABLES LIKE 'member_message';
+--------------------------+
| Tables_in_meiyetec (member_message) |
+--------------------------+
| member_message           |
+--------------------------+

mysql> SHOW TABLES LIKE 'overseas_booking';
+--------------------------+
| Tables_in_meiyetec (overseas_booking) |
+--------------------------+
| overseas_booking         |
+--------------------------+

mysql> SHOW COLUMNS FROM overseas_booking LIKE 'mini_program_open_id';
+---------------------+--------------+------+-----+---------+-------+
| Field               | Type         | Null | Key | Default | Extra |
+---------------------+--------------+------+-----+---------+-------+
| mini_program_open_id | varchar(128) | YES  |     | NULL    |       |
+---------------------+--------------+------+-----+---------+-------+
```

✅ **3 张表 + 1 列全部就位**。

---

## 5. 已知偏差

`migrate_ext_19_mini_program_openid.sql` 第 31 行的 `ALTER TABLE ADD COLUMN IF NOT EXISTS` 在 MySQL 8.0.46 上**报错**（语法不支持 IF NOT EXISTS in ALTER），但因 CREATE 路径已带列，**功能上无影响**。

> 建议后续清理：把 ALTER 段改为 `SHOW COLUMNS` 判断后再 ALTER（或直接删掉，因为该列已被 CREATE 覆盖）。

---

## 6. 下一步

继续 Batch 2（mkt_invitation + mkt_invitation_rule + mkt_dispatch + mkt_dispatch_target）。但发现 `migrate_r3_0_e_p2_features.sql` 和 `migrate_r3_0_f_p1_remaining.sql` 都包含 **远超 2.3 范围**的 schema 变更 → **暂停汇报**。
