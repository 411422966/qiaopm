# F023 — EXT-15 迁移检查（Phase 5C）

> **Phase**：5C — Migration Status Check
> **Feature ID**：F023
> **DB-only 验证**（不修业务代码、不跑迁移）
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 0. 验证范围

| 项 | 值 |
|---|---|
| 迁移文件 | `backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql` |
| 预期变更 | 1. `crm_integration` ADD 9 列 + 2 索引<br>2. `crm_contact_map` CREATE TABLE<br>3. `crm_sync_log` ADD 3 列 |
| 当前状态 | ❌ **未跑**（用户决策 D5 保留 PARTIAL-BLOCKER）|
| 验证方法 | SHOW CREATE TABLE + SHOW TABLES 对比 |

---

## 1. 迁移 SQL 期望（来自 `migrate_ext_15_crm_real.sql`）

### 1.1 crm_integration ALTER

```sql
ALTER TABLE crm_integration
    ADD COLUMN sync_token              VARCHAR(255) DEFAULT NULL COMMENT '平台 sync token（增量同步游标）',
    ADD COLUMN sync_schedule           VARCHAR(64)  DEFAULT '0 */30 * * * *' COMMENT 'cron 表达式（默认 30 分钟）',
    ADD COLUMN sync_enabled            TINYINT      DEFAULT 1 COMMENT '0=禁用 1=启用排程',
    ADD COLUMN last_incremental_sync_at DATETIME    DEFAULT NULL COMMENT '上次增量同步时间戳',
    ADD COLUMN private_key             MEDIUMTEXT   DEFAULT NULL COMMENT 'OAuth2 JWT 签名私钥（PEM / 建议加密）',
    ADD COLUMN username                VARCHAR(128) DEFAULT NULL COMMENT '平台账号（Salesforce 用）',
    ADD COLUMN login_url               VARCHAR(255) DEFAULT NULL COMMENT '登录端点（Salesforce: login.salesforce.com / test.salesforce.com）',
    ADD COLUMN object_mappings         TEXT         DEFAULT NULL COMMENT '字段映射 JSON（local_field -> external_field）',
    ADD COLUMN sync_window_minutes     INT          DEFAULT 30 COMMENT '增量同步窗口（分钟），默认 30',
    ADD INDEX idx_sync_enabled (sync_enabled),
    ADD INDEX idx_platform_status (platform, status);
```

### 1.2 crm_contact_map CREATE

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

### 1.3 crm_sync_log ALTER

```sql
ALTER TABLE crm_sync_log
    ADD COLUMN sync_token     VARCHAR(255) DEFAULT NULL,
    ADD COLUMN records_total  INT          DEFAULT 0,
    ADD COLUMN records_filtered INT        DEFAULT 0;
```

---

## 2. 实际状态 vs 期望状态

### 2.1 crm_integration

| 期望变更 | 实际状态 | 命中？ |
|---|---|---|
| ADD `sync_token` VARCHAR(255) | ❌ 不存在 | ❌ |
| ADD `sync_schedule` VARCHAR(64) DEFAULT '0 */30 * * * *' | ❌ 不存在 | ❌ |
| ADD `sync_enabled` TINYINT DEFAULT 1 | ❌ 不存在 | ❌ |
| ADD `last_incremental_sync_at` DATETIME | ❌ 不存在 | ❌ |
| ADD `private_key` MEDIUMTEXT | ❌ 不存在 | ❌ |
| ADD `username` VARCHAR(128) | ❌ 不存在 | ❌ |
| ADD `login_url` VARCHAR(255) | ❌ 不存在 | ❌ |
| ADD `object_mappings` TEXT | ❌ 不存在 | ❌ |
| ADD `sync_window_minutes` INT DEFAULT 30 | ❌ 不存在 | ❌ |
| ADD INDEX `idx_sync_enabled` | ❌ 不存在（依赖 sync_enabled 列）| ❌ |
| ADD INDEX `idx_platform_status` | ❌ 不存在（仅 `idx_tenant`）| ❌ |

**完成度：0 / 11（0%）**

### 2.2 crm_contact_map

| 期望变更 | 实际状态 | 命中？ |
|---|---|---|
| CREATE TABLE | ❌ 表不存在 | ❌ |

**完成度：0 / 1（0%）**

### 2.3 crm_sync_log

| 期望变更 | 实际状态 | 命中？ |
|---|---|---|
| ADD `sync_token` VARCHAR(255) | ❌ 不存在 | ❌ |
| ADD `records_total` INT DEFAULT 0 | ❌ 不存在 | ❌ |
| ADD `records_filtered` INT DEFAULT 0 | ❌ 不存在 | ❌ |

**完成度：0 / 3（0%）**

---

## 3. ✅ 最终状态

### 3.1 EXT-15 迁移完成度

| 表 | 期望变更数 | 已完成 | 完成度 |
|---|---|---|---|
| crm_integration | 11（9 列 + 2 索引）| 0 | **0%** |
| crm_contact_map | 1（CREATE TABLE）| 0 | **0%** |
| crm_sync_log | 3（3 列）| 0 | **0%** |
| **合计** | **15** | **0** | **0%** |

### 3.2 状态结论

**F023 = ❌ BROKEN（EXT-15 迁移完全未执行）**

- ❌ 全部 9 个 crm_integration 新增列缺失
- ❌ crm_contact_map 表完全缺失
- ❌ 全部 3 个 crm_sync_log 新增列缺失
- ❌ 全部 2 个 crm_integration 新增索引缺失（依赖缺失列）
- ✅ 迁移文件存在且语法正确（来自 git tree）

### 3.3 与用户决策一致性

| 决策 | 描述 | 当前状态 |
|---|---|---|
| D5 | 保留 PARTIAL-BLOCKER / **不跑 EXT-15** | ✅ 严格遵守 |

→ F023 = BROKEN 是 **设计意图** 而非事故，与 Phase 5A/5B 一致

---

## 4. 一旦执行迁移的影响（不动手前提下预演）

### 4.1 预演 SQL

```bash
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 \
  --default-character-set=utf8mb4 \
  meiyetec < backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql
```

### 4.2 预期效果（基于 Phase 5B 已确认的 I002 链路）

| 现状 | 执行后预期 |
|---|---|
| F002 list API → I002 BadSqlGrammar | F002 → 200 OK（缺列补全）|
| F003 update API → I011 Fake Failure | F003 → 200 OK（selectById 不抛错）|
| F005 sync API → I002 BadSqlGrammar | F005 → 200 OK（selectById 不抛错）|
| F007 dashboard → I008 Fake Success 链 | F007 → 200 OK（selectCount 不抛错）|
| F010 auto tick → I008 静默失败 | F010 → 200 OK（CrmSyncScheduler 不抛错）|
| F012 known-ids → catch 吞 → empty Set | F012 → 真实 Set（`crm_contact_map` 存在）|
| F020 upsert contact map → fail_count++ | F020 → upsert 成功 |
| F024 E2E 列表渲染 → 行数 = 0 | F024 E2E → 行数 ≥ 1 |
| F025 E2E 填表保存 → dialog 不关 | F025 E2E → dialog 关闭 |
| F026 E2E API 响应 → 500 | F026 E2E → 200 |
| F027 E2E API → 500 | F027 E2E → 200（force=1 真触发同步）|
| F028 E2E 创建 → 500 | F028 E2E → 200（objectMappings NULL 持久化）|

**预期解锁**：12 / 12 E2E 测试 + 全部 PARTIAL-BLOCKER Feature 升 PARTIAL 或 VERIFIED

### 4.3 不建议做的事（与决策冲突）

- ❌ 不实际跑迁移（D5 决策）
- ❌ 不验证迁移脚本的实际执行（用户决策保留 PARTIAL-BLOCKER）

---

## 5. 交叉引用

- **Phase 5A**：F019 entity DDL 验证（22 列 + 12 列 + 表缺失）
- **Phase 5B**：F020 upsert contact map + F012 known-ids load（5B 实测确认）
- **Phase 5C（本 Phase）**：F023 静态 SQL 对比
- **关联 Issue**：I002 / D5 决策
