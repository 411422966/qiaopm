# Batch 6 Apply Log — `crm_contact_map` 新建

> **执行时间**：2026-09-21 16:00 (UTC+8)
> **执行人**：Cursor（用户确认 apply）
> **批次**：Phase 2.3 Batch 6
> **脚本**：`backend/src/main/resources/db/sql/audit-subset/migrate_crm_contact_map_subset.sql`
> **MySQL 版本**：8.0.46
> **连接**：`127.0.0.1:3307` / `meiyetec`

---

## 1. apply 命令

```powershell
mysql -h 127.0.0.1 -P 3307 -u root -proot123456 meiyetec -e "SOURCE d:/QiaoPM/backend/src/main/resources/db/sql/audit-subset/migrate_crm_contact_map_subset.sql"
```

**结果**：Exit 0，无报错。

---

## 2. 验证结果

### 2.1 表存在性

```sql
SHOW TABLES LIKE 'crm_contact_map';
```

| 结果 | 状态 |
|---|---|
| `crm_contact_map` | ✅ 1 行 |

### 2.2 列清单（13 列，全部对齐）

| 字段 | 类型 | Null | Key | Default | Extra |
|---|---|---|---|---|---|
| `id` | bigint | NO | PRI | NULL | auto_increment ✅ |
| `tenant_id` | bigint | NO | MUL | `1` | ✅ |
| `platform` | varchar(64) | NO | MUL | NULL | ✅ |
| `local_contact_id` | bigint | YES | MUL | NULL | ✅ |
| `external_id` | varchar(64) | NO | | NULL | ✅ |
| `external_etag` | varchar(128) | YES | | NULL | ✅ |
| `sync_direction` | varchar(16) | YES | | `BIDIRECTIONAL` | ✅ |
| `last_inbound_at` | datetime | YES | | NULL | ✅ |
| `last_outbound_at` | datetime | YES | | NULL | ✅ |
| `last_full_sync_at` | datetime | YES | | NULL | ✅ |
| `conflict_flag` | tinyint | YES | MUL | `0` | ✅ |
| `create_time` | datetime | YES | | CURRENT_TIMESTAMP | DEFAULT_GENERATED ✅ |
| `update_time` | datetime | YES | | CURRENT_TIMESTAMP | DEFAULT_GENERATED on update ✅ |

### 2.3 索引清单（5 named indexes / 7 index entries）

| Key_name | Non_unique | 列字段 | 索引类型 |
|---|---|---|---|
| `PRIMARY` | 0 | `id` | BTREE ✅ |
| `uk_platform_external` | 0 | `platform` + `external_id` | BTREE ✅ |
| `idx_local` | 1 | `local_contact_id` | BTREE ✅ |
| `idx_tenant_platform` | 1 | `tenant_id` + `platform` | BTREE ✅ |
| `idx_conflict` | 1 | `conflict_flag` | BTREE ✅ |

### 2.4 引擎与字符集

```sql
SHOW CREATE TABLE crm_contact_map\G
```

| 属性 | 值 | 状态 |
|---|---|---|
| ENGINE | InnoDB | ✅ |
| CHARSET | utf8mb4 | ✅ |
| COLLATE | utf8mb4_bin | ✅ |
| 表 COMMENT | `CRM Contact bidirectional ID mapping` | ✅ |

### 2.5 行数

```sql
SELECT COUNT(*) FROM crm_contact_map;
```

| 结果 | 状态 |
|---|---|
| 0 | ✅（空表，符合新建预期） |

---

## 3. 与 EXT-24 契约对齐（EXT-24 §0.5）

| 契约项 | 期望 | 实际 | 状态 |
|---|---|---|---|
| 唯一键 | `platform + external_id` | `uk_platform_external (platform, external_id)` | ✅ |
| tenant_id 列 | `tenant_id`（NOT NULL） | ✅ | ✅ |
| 冲突标记 | `conflict_flag` TINYINT | ✅ DEFAULT 0 | ✅ |
| INBOUND 字段 | `last_inbound_at` | ✅ | ✅ |
| 创建时间 | `create_time` | ✅ | ✅ |

---

## 4. 影响 Feature 解锁（预期）

| Feature | 阻塞状态 | 解锁路径 |
|---|---|---|
| **F012** known-ids 差集加载 | PARTIAL-BLOCKER → 待验 | `CrmSyncScheduler.loadKnownExternalIds` 现在读真实表而非抛异常 |
| **F020** upsert contact map | PARTIAL-BLOCKER → 待验 | `upsertContactMap` 现在可写 `crm_contact_map` |
| **F023** 表存在性 | PARTIAL-BLOCKER → 待验 | 表已存在 |

> **注意**：Feature 实际解锁验证需重启后端应用 + 触发 CRM 同步（Phase 2.4 回归测试范围）。

---

## 5. 本批与历史批次的关联

| 批次 | 内容 | 状态 |
|---|---|---|
| Batch 1 | `overseas_booking` +1 / `member_message` +1 / `plugin_runtime_state` | ✅ 已 apply |
| Batch 2 | `mkt_invitation_subset` / `mkt_dispatch_subset` | ✅ 已 apply（事故 2，用户接受现状）|
| Batch 3 | `google_maps_geocode_log` + `google_maps_config` (EXT-17) | ✅ 已 apply |
| Batch 4 | `crm_integration` +9 列 / `crm_sync_log` +3 列 (EXT-24) | ✅ 已 apply |
| Batch 5 | EXT-22 子集（`enable_place_details` / `ecommerce_platform` 3 列）| ✅ 已 apply |
| **Batch 6** | **`crm_contact_map` 新建** | ✅ **本批已 apply** |

---

## 6. 下一步

| 阶段 | 内容 | 状态 |
|---|---|---|
| Phase 2.4 | EXT-24 / EXT-22 / EXT-23 / EXT-19 回归测试 | ⏸ 等用户确认进入 |
| Batch 7+ | 其余 schema（`pos_store` 字段 / `webhook_msg_log` / `market_activity` 等）| ⏸ 待规划 |

---

**本文件为只读记录，不执行任何操作。**
