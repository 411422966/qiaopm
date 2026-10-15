# Phase 2.3 — Batch 4 Apply Log

> **执行时间**：2026-09-21 15:25 (UTC+8)
> **目标**：crm_integration +9 列 + 2 索引；crm_sync_log +3 列
> **脚本**：`backend/src/main/resources/db/sql/audit-subset/migrate_ext_15_crm_alter_subset.sql`
> **子集 SHA256**：`33a43ae27a7d3e2422f9267cf9a726765c15d73c5c55915dd47c3119605ce32b`
> **来源 SHA256**：`5f40b5e5cd4660ecd4ff073026c750827079680f6b49d811029e44567cb39f20`
> **环境**：`127.0.0.1:3307 / root / MySQL 8.0.46 / meiyetec`
> **结果**：✅ **全部成功**

---

## 1. 前置检查（已完成）

| 检查项 | 期望 | 实际 |
|---|---|---|
| crm_integration 列数 | 22 | 22 |
| crm_sync_log 列数 | 12 | 12 |
| 12 列冲突 | 全部不存在 | 全部不存在 |
| platform 列存在 | 存在 | 存在 |
| status 列存在 | 存在 | 存在 |

---

## 2. apply 执行

```bash
mysql -h 127.0.0.1 -P 3307 -u root -proot123456 --default-character-set=utf8mb4 meiyetec \
  -e "SOURCE d:/QiaoPM/backend/src/main/resources/db/sql/audit-subset/migrate_ext_15_crm_alter_subset.sql"
```

**EXIT_CODE**：`0`（无 stderr 输出）

---

## 3. 验证结果

### 3.1 列验证 — crm_integration（9 列全部存在）

| 列 | 类型 | Null | Default |
|---|---|---|---|
| sync_token | varchar(255) | YES | NULL |
| sync_schedule | varchar(64) | YES | `0 */30 * * * *` |
| sync_enabled | tinyint | YES | 1 |
| last_incremental_sync_at | datetime | YES | NULL |
| private_key | mediumtext | YES | NULL |
| username | varchar(128) | YES | NULL |
| login_url | varchar(255) | YES | NULL |
| object_mappings | text | YES | NULL |
| sync_window_minutes | int | YES | 30 |

### 3.2 列验证 — crm_sync_log（3 列全部存在）

| 列 | 类型 | Null | Default |
|---|---|---|---|
| sync_token | varchar(255) | YES | NULL |
| records_total | int | YES | 0 |
| records_filtered | int | YES | 0 |

### 3.3 索引验证 — crm_integration

| Key_name | Seq | Column | Non_unique |
|---|---|---|---|
| idx_sync_enabled | 1 | sync_enabled | 1 |
| idx_platform_status | 1 | platform | 1 |
| idx_platform_status | 2 | status | 1 |

### 3.4 列数验证

| 表 | 旧列数 | 加数列 | 实际列数 | 期望列数 |
|---|---|---|---|---|
| crm_integration | 22 | +9 | **31** | 31 ✅ |
| crm_sync_log | 12 | +3 | **15** | 15 ✅ |

---

## 4. 范围外内容（已剔除，未执行）

| 剔除内容 | 说明 |
|---|---|
| crm_contact_map CREATE TABLE | 12 列 + 4 索引，全文剔除（Batch 5 范围） |
| 末尾验证 SELECT | 引用 crm_contact_map，已剔除 |

---

## 5. Batch 5 待确认

**范围**：`crm_contact_map` 新建（12 列 + 4 索引）

---

**本文件为只读记录，不执行任何操作。**
