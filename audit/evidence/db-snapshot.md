# DB Snapshot — Phase 5A 起点

> 抓取时间：2026-09-12 23:06 (UTC+8)
> 连接：`mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 --default-character-set=utf8mb4 meiyetec`

---

## 1. `crm_integration` schema（22 列）

详见 `db-snapshot-crm_integration.txt`

| 列 | 类型 | Null | 默认 | 备注 |
|---|---|---|---|---|
| id | bigint | NO | NULL auto_increment | PK |
| tenant_id | bigint | NO | 1 | 索引 |
| platform | varchar(64) | NO | NULL | |
| app_id | varchar(128) | YES | NULL | |
| client_id | varchar(128) | YES | NULL | |
| client_secret | varchar(255) | YES | NULL | |
| access_token | varchar(500) | YES | NULL | |
| refresh_token | varchar(500) | YES | NULL | |
| token_expire_at | datetime | YES | NULL | |
| api_base_url | varchar(255) | YES | NULL | |
| portal_id | varchar(64) | YES | NULL | |
| sync_direction | varchar(32) | YES | NULL | |
| sync_members / sync_orders / sync_contacts / sync_deals | tinyint | YES | 0 | |
| auto_sync | tinyint | YES | 0 | |
| last_sync_at | datetime | YES | NULL | |
| status | varchar(16) | YES | ACTIVE | |
| error_message | varchar(500) | YES | NULL | |
| created_at / updated_at | datetime | YES | CURRENT_TIMESTAMP | |

### 1.1 ❌ 缺失列（EXT-24 SoT §0 / I002）

| 缺失列 | 引用位置（计划中的代码）| 影响 Feature |
|---|---|---|
| `sync_window_minutes` | `CrmSyncScheduler.shouldRun` / `CrmIntegrationService.syncNow` | **F005 / F008 / F011** |
| `object_mappings` | `SalesforceCrmAdapter.parseObjectMappings` / `CrmIntegrationService` | F005 / F014 / F019 |
| `private_key` | `SalesforceCrmAdapter.authenticate` / `CrmIntegration` entity | F005 / F014 / F019 |
| `sync_enabled` | `CrmSyncScheduler.runIncremental` 过滤 | **F010** / F019 |
| `last_incremental_sync_at` | `shouldRun` / `syncNow` 副作用 | **F005** / F008 / F029 |
| `sync_token` | `CrmSyncScheduler.runIncremental` / `CrmIntegration` | F019 / F021 / F030 |
| `sync_schedule` | 同上 | F019 / F030 |
| `username` | `SalesforceCrmAdapter.authenticate` | F014 / F019 |
| `login_url` | `SalesforceCrmAdapter.authenticate` | F014 / F019 |

## 2. `crm_sync_log` schema（12 列）

详见 `db-snapshot-crm_sync_log.txt`

### 2.1 ❌ 缺失列（I002）

| 缺失列 | 影响 |
|---|---|
| `sync_token` | F005 / F021 写库 silent 丢失 |
| `records_total` | F005 写库 silent 丢失 |
| `records_filtered` | F005 写库 silent 丢失 |

## 3. `crm_contact_map` 表（**完全缺失**）

详见 `db-snapshot-crm_contact_map-and-counts.txt` → `SHOW TABLES LIKE 'crm_contact_map'` → 0 行。

### 3.1 影响 Feature

| Feature | 影响路径 |
|---|---|
| **F012** known-ids 差集加载 | `loadKnownExternalIds` 抛 `BadSqlGrammarException` → catch 吞 → empty Set |
| **F020** upsert contact map | `upsertContactMap` 抛 → fail_count++ |
| F023 表存在性 | 直接确认 |

## 4. 起点行数（Phase 5A 前）

| 表 | 行数 | 备注 |
|---|---|---|
| `crm_integration` | **1** | 已存在 1 条历史记录（用户标签为"未跑 EXT-15" → 实际 init 阶段插入了基础 schema 与至少 1 条样例） |
| `crm_sync_log` | **0** | 干净起点 |
| `crm_contact_map` | — | 表不存在 |
