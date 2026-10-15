# F022 — CRM DDL 检查（Phase 5C）

> **Phase**：5C — DDL Verification
> **Feature ID**：F022
> **DB-only 验证**（不修业务代码、不跑迁移）
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 0. 验证方法

| 步骤 | 命令 | 抓取时间 (UTC+8) |
|---|---|---|
| 1 | `mysql -h 127.0.0.1 -P 3307 ... -e "SHOW CREATE TABLE crm_integration\G" meiyetec` | 2026-09-13 18:01 |
| 2 | `mysql ... -e "SHOW CREATE TABLE crm_sync_log\G" meiyetec` | 2026-09-13 18:01 |
| 3 | `mysql ... -e "SHOW TABLES LIKE 'crm_contact_map';" meiyetec` | 2026-09-13 18:01 |
| 4 | `mysql ... -e "SHOW TABLES LIKE 'crm%';" meiyetec` | 2026-09-13 18:01 |

**连接**：`127.0.0.1:3307`（MySQL 8.0 Docker，本地 dev，**非生产**）
**账号**：`root/root123456`
**DB**：`meiyetec`
**编码**：`utf8mb4 / utf8mb4_bin`

---

## 1. 实际 DDL vs 期望 DDL 对比

### 1.1 `crm_integration`

#### 1.1.1 实际 DDL（22 列）

```sql
CREATE TABLE `crm_integration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `platform` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `app_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `client_id` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `client_secret` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `access_token` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `refresh_token` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `token_expire_at` datetime DEFAULT NULL,
  `api_base_url` varchar(255) COLLATE utf8mb4_bin DEFAULT NULL,
  `portal_id` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_direction` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_members` tinyint DEFAULT '0',
  `sync_orders` tinyint DEFAULT '0',
  `sync_contacts` tinyint DEFAULT '0',
  `sync_deals` tinyint DEFAULT '0',
  `auto_sync` tinyint DEFAULT '0',
  `last_sync_at` datetime DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_bin DEFAULT 'ACTIVE',
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
```

**Evidence 路径**：`audit/evidence/F022-crm-ddl-check/ddl-crm_integration.txt`

#### 1.1.2 ❌ 缺失列（与 `migrate_ext_15_crm_real.sql` 对比）

| 序号 | 缺失列 | 类型 | 默认 | EXT-15 期望 | 影响 Feature |
|---|---|---|---|---|---|
| 1 | `sync_token` | VARCHAR(255) | NULL | ✅ | F005 / F019 / F021 / F030 |
| 2 | `sync_schedule` | VARCHAR(64) | `'0 */30 * * * *'` | ✅ | F019 / F030 |
| 3 | `sync_enabled` | TINYINT | 1 | ✅ | F010 / F019 |
| 4 | `last_incremental_sync_at` | DATETIME | NULL | ✅ | F005 / F008 / F029 |
| 5 | `private_key` | MEDIUMTEXT | NULL | ✅ | F005 / F014 / F019 |
| 6 | `username` | VARCHAR(128) | NULL | ✅ | F014 / F019 |
| 7 | `login_url` | VARCHAR(255) | NULL | ✅ | F014 / F019 |
| 8 | `object_mappings` | TEXT | NULL | ✅ | F005 / F014 / F019 |
| 9 | `sync_window_minutes` | INT | 30 | ✅ | F005 / F008 / F011 |

**缺失列数：9 / 9 = 100%**

#### 1.1.3 ❌ 缺失索引

| 索引 | EXT-15 期望 | 实际 |
|---|---|---|
| `idx_sync_enabled (sync_enabled)` | ✅ | ❌ 缺失（sync_enabled 列本身缺失） |
| `idx_platform_status (platform, status)` | ✅ | ❌ 缺失（仅 `idx_tenant`） |

**缺失索引数：2 / 2 = 100%**

#### 1.1.4 ✅ 字符集 / 引擎

| 维度 | 实际 | EXT-15 期望 | 一致？ |
|---|---|---|---|
| Engine | InnoDB | InnoDB | ✅ |
| Charset | utf8mb4 | utf8mb4 | ✅ |
| Collate | utf8mb4_bin | utf8mb4_bin | ✅ |

#### 1.1.5 AUTO_INCREMENT 旁注

`AUTO_INCREMENT=6` 表示已分配 ID 5 条（用于 Phase 5A/5B reset 测试）。**行数不作为 DDL 验证指标**。

---

### 1.2 `crm_sync_log`

#### 1.2.1 实际 DDL（12 列）

```sql
CREATE TABLE `crm_sync_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint DEFAULT NULL,
  `platform` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL,
  `sync_type` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `direction` varchar(16) COLLATE utf8mb4_bin DEFAULT NULL,
  `success_count` int DEFAULT '0',
  `fail_count` int DEFAULT '0',
  `skipped_count` int DEFAULT '0',
  `error_message` varchar(500) COLLATE utf8mb4_bin DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
```

**Evidence 路径**：`audit/evidence/F022-crm-ddl-check/ddl-crm_sync_log.txt`

#### 1.2.2 ❌ 缺失列

| 序号 | 缺失列 | 类型 | 默认 | EXT-15 期望 | 影响 Feature |
|---|---|---|---|---|---|
| 1 | `sync_token` | VARCHAR(255) | NULL | ✅ | F005 / F021 |
| 2 | `records_total` | INT | 0 | ✅ | F005 |
| 3 | `records_filtered` | INT | 0 | ✅ | F005 |

**缺失列数：3 / 3 = 100%**

---

### 1.3 `crm_contact_map`（**完全缺失**）

#### 1.3.1 实际 DDL

```
mysql> SHOW TABLES LIKE 'crm_contact_map';
Empty set (0.00 sec)
```

→ **表不存在**

**Evidence 路径**：`audit/evidence/F022-crm-ddl-check/ddl-crm_contact_map.txt`

#### 1.3.2 EXT-15 期望 schema

```sql
CREATE TABLE `crm_contact_map` (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    platform        VARCHAR(64)  NOT NULL COMMENT 'SALESFORCE / HUBSPOT / ...',
    local_contact_id BIGINT      DEFAULT NULL COMMENT '本系统 Contact/Member ID',
    external_id     VARCHAR(64)  NOT NULL COMMENT '平台侧 ID（Salesforce Contact Id）',
    external_etag   VARCHAR(128) DEFAULT NULL COMMENT '乐观锁 / 平台 revision marker',
    sync_direction  VARCHAR(16)  DEFAULT 'BIDIRECTIONAL' COMMENT 'INBOUND / OUTBOUND / BIDIRECTIONAL',
    last_inbound_at  DATETIME    DEFAULT NULL,
    last_outbound_at DATETIME    DEFAULT NULL,
    last_full_sync_at DATETIME   DEFAULT NULL,
    conflict_flag   TINYINT      DEFAULT 0 COMMENT '0=无冲突 1=有冲突待人工',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_external (platform, external_id),
    INDEX idx_local (local_contact_id),
    INDEX idx_tenant_platform (tenant_id, platform),
    INDEX idx_conflict (conflict_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='CRM Contact 双向 ID 映射';
```

#### 1.3.3 影响 Feature

| Feature | 影响路径 | 状态 |
|---|---|---|
| **F012** known-ids 差集加载 | `loadKnownExternalIds` 抛 `BadSqlGrammarException` → catch 吞 → empty Set | ❌ 已确认（5B F012 实测）|
| **F020** upsert contact map | `upsertContactMap` 抛 → fail_count++ | ❌ 已确认（5B F020 实测）|
| **F023** 表存在性 | 直接确认 | ❌ 表不存在 |

---

## 2. crm_* 表全景（32 个）

`SHOW TABLES LIKE 'crm%'` 结果：32 个表

```
crm_complaint_log                crm_follow_up_plan
crm_complaint_ticket             crm_follow_up_task
crm_cross_store_redemption       crm_follow_up_trigger_log
crm_cross_store_rule             crm_integration         ← F019 / F001-F005 涉及
crm_follow_up_note               crm_ltv_snapshot
crm_member                       crm_member_profile
crm_member_store                 crm_member_tag
crm_member_tag_rel               crm_member_tag_value
crm_package                      crm_package_order
crm_package_redemption           crm_recharge_audit
crm_recharge_audit_log           crm_review
crm_review_template              crm_rfm_snapshot
crm_sync_log                     ← F006 / F021 涉及
crm_tag_dictionary               crm_tenant
crm_wallet                       crm_wallet_drift_log
crm_wallet_orphan_log            crm_wallet_transaction
crm_wallet_txn_orphan_log
```

**Evidence 路径**：`audit/evidence/F022-crm-ddl-check/ddl-crm-tables-list.txt`

**关键观察**：
- ✅ `crm_integration`（22 列）存在
- ✅ `crm_sync_log`（12 列）存在
- ❌ `crm_contact_map` **缺失**（EXT-15 新增表未创建）

---

## 3. ✅ 最终状态

### 3.1 DDL 完整度

| 表 | 期望列数 | 实际列数 | 缺失列数 | 缺失索引 | 表存在？ |
|---|---|---|---|---|---|
| `crm_integration` | 22 + 9 = 31 | 22 | **9** | **2** | ✅ |
| `crm_sync_log` | 12 + 3 = 15 | 12 | **3** | 0 | ✅ |
| `crm_contact_map` | 13 | 0 | **13（全表缺失）** | **4** | ❌ |

### 3.2 状态结论

**F022 = ❌ PARTIAL-BLOCKER**

- ✅ 表结构基础部分存在（22 列 crm_integration + 12 列 crm_sync_log）
- ❌ EXT-15 新增字段全部缺失（9 + 3 = 12 列）
- ❌ EXT-15 新增索引全部缺失（2 + 4 = 6 个）
- ❌ EXT-15 新增表 `crm_contact_map` 完全缺失
- ❌ 字符集 / 引擎 / 排序规则一致（utf8mb4_bin / InnoDB）

### 3.3 与 Phase 5A / 5B 一致性

| 维度 | 5A | 5B | 5C |
|---|---|---|---|
| `crm_integration` 列数 | 22 | 22 | 22 ✅ 一致 |
| `crm_sync_log` 列数 | 12 | 12 | 12 ✅ 一致 |
| `crm_contact_map` | 不存在 | 不存在 | 不存在 ✅ 一致 |
| EXT-15 缺失列 | 9 | 9 | 9 ✅ 一致 |

→ DDL 在 5A→5B→5C 期间**无变化**（D5 决策保留 PARTIAL-BLOCKER）

---

## 4. 修复路径（不动业务代码前提下）

**P0**：执行 `migrate_ext_15_crm_real.sql` 解锁 EXT-15 / EXT-24 全部功能

**预演（不实际跑）**：
```sql
-- ALTER crm_integration ADD 9 列 + 2 索引
-- CREATE TABLE crm_contact_map
-- ALTER crm_sync_log ADD 3 列
```

**风险评估**：
- ALTER TABLE ADD COLUMN 默认 NULL → 不锁表
- CREATE TABLE → 无依赖
- 9 + 3 = 12 列 ADD + 1 表 CREATE → 预计 < 5 秒

**当前不做**（用户决策 D5：保留 PARTIAL-BLOCKER）

---

## 5. 交叉引用

- **Phase 5A**：F019 entity DDL 验证 → I002 PARTIAL-BLOCKER
- **Phase 5B**：F019 result.md + F020 result.md 确认 EXT-15 缺失
- **Phase 5C（本 Phase）**：F022 SHOW CREATE TABLE 实证
- **关联 Issue**：I002（DB 缺列）/ F012（known-ids）/ F020（upsert contact map）
