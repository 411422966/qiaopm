# Phase 2.3 — DB 迁移执行 Checklist（按依赖排序）

> **配套文档**：`audit/post-audit/phase-2.3-migration-plan.md`
> **状态**：📋 **CHECKLIST 阶段**（**未 apply**，等用户确认）
> **目标**：6 个 SQL 脚本族 → 9 张表 + 16 列

---

## 0. 总览

| 顺序 | 批次 | 脚本数 | 表/列数 | 风险 | 预计耗时 |
|---|---|---|---|---|---|
| **第 1 批（无依赖、低风险）** | 幂等 IF NOT EXISTS | 3 | 3 张表 + 1 列 | 🟢 低 | 5 分钟 |
| **第 2 批（破坏性 DROP + CREATE）** | 需备份 | 2 | 4 张 mkt_* 表 | 🟠 中 | 10 分钟 |
| **第 3 批（破坏性 DROP + CREATE）** | 需备份 | 1 | 2 张 google_maps 表 | 🟠 中 | 5 分钟 |
| **第 4 批（ALTER 安全）** | 列添加 | 2 | 12 列（crm_integration 9 + crm_sync_log 3）| 🟡 低 | 2 分钟 |
| **第 5 批（ALTER 安全）** | 列添加 | 1 | 3 列（ecommerce_platform）| 🟡 低 | 1 分钟 |
| **回滚预案** | — | 6 个 rollback | — | — | — |

**总计**：6 个脚本族 → 9 张表 + 16 列 → 预计 23 分钟（不含备份/验证）

---

## 1. 依赖分析

### 1.1 外键依赖图（粗判）

| 表/列 | 上游依赖 | 下游被引用 | 独立可跑？ |
|---|---|---|---|
| `plugin_runtime_state` | 无 | 无 | ✅ 完全独立 |
| `member_message` | 无 | 无 | ✅ 完全独立 |
| `overseas_booking` (+ 列) | 无 | 无 | ✅ 完全独立 |
| `mkt_invitation` (+ rule) | 无 | 无（无 FK）| ⚠️ 需先确认无引用 |
| `mkt_dispatch` (+ target) | 无 | mkt_dispatch_target 引用 dispatch_id（**同脚本**）| ⚠️ 需先确认无引用 |
| `google_maps_config` | 无 | 无 | ⚠️ DROP 前需备份 |
| `google_maps_geocode_log` | 无 | 无 | ⚠️ DROP 前需备份 |
| `crm_integration` (+ 9 列) | 无 | crm_sync_log 引用（**crm_sync_log 表已存在**）| ✅ ALTER 安全 |
| `crm_sync_log` (+ 3 列) | crm_integration 已存在 | 无 | ✅ ALTER 安全 |
| `ecommerce_platform` (+ 3 列) | 无 | 无 | ✅ ALTER 安全 |

### 1.2 顺序原则

1. **完全独立的 IF NOT EXISTS 脚本** → 先跑（无需备份，安全幂等）
2. **DROP + CREATE 脚本** → 中间跑（按业务相关性分组，组内可并行）
3. **ALTER ADD COLUMN** → 最后跑（最安全，仅追加列）
4. **跨脚本依赖** → 同组内连续跑

---

## 2. 第 1 批：IF NOT EXISTS（3 脚本 — 完全安全）

### 2.1 脚本清单

| 序 | 脚本 | 表/列 | 风险 | 备份需求 |
|---|---|---|---|---|
| 1.1 | `migrate_ext_23_plugin_runtime_state.sql` | `plugin_runtime_state`（建表）| 🟢 低 | ❌ 不需 |
| 1.2 | `init_m09_member_message.sql` | `member_message`（建表）| 🟢 低 | ❌ 不需 |
| 1.3 | `migrate_ext_19_mini_program_openid.sql` | `overseas_booking`（建表 + `mini_program_open_id` 列）| 🟢 低 | ❌ 不需 |

### 2.2 执行步骤

```bash
# Step 1.1
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/migrate_ext_23_plugin_runtime_state.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW TABLES LIKE 'plugin_runtime_state'; DESCRIBE plugin_runtime_state;"

# Step 1.2
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/init_m09_member_message.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW TABLES LIKE 'member_message'; DESCRIBE member_message;"

# Step 1.3
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/migrate_ext_19_mini_program_openid.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW TABLES LIKE 'overseas_booking'; DESCRIBE overseas_booking;"
```

### 2.3 验证标准

- [ ] 3 张表全部 `SHOW TABLES` 返回
- [ ] `DESCRIBE` 列定义完整（plugin_runtime_state 11 列 / member_message 21 列 / overseas_booking 23 列含 mini_program_open_id）
- [ ] `migrate_ext_19` 的最后 SELECT（如有）返回非 0 行数

---

## 3. 第 2 批：DROP + CREATE（2 脚本 — 4 张 mkt_* 表）

### 3.1 脚本清单

| 序 | 脚本 | 表 | 风险 | 备份需求 |
|---|---|---|---|---|
| 2.1 | `migrate_r3_0_e_p2_features.sql` | `mkt_invitation` + `mkt_invitation_rule` | 🟠 中（**DROP + seed**）| ✅ mysqldump |
| 2.2 | `migrate_r3_0_f_p1_remaining.sql` | `mkt_dispatch` + `mkt_dispatch_target` | 🟠 中（**DROP**）| ✅ mysqldump |

### 3.2 apply 前必做

```bash
# Step 0: 备份（apply 前必须跑）
mysqldump -h 127.0.0.1 -P 3307 -uroot -proot123456 --single-transaction \
  --databases meiyetec --tables mkt_invitation mkt_invitation_rule mkt_dispatch mkt_dispatch_target \
  > /backup/phase-2.3-mkt-$(date +%Y%m%d-%H%M%S).sql

# 验证备份
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -e \
  "SELECT COUNT(*) FROM meiyetec.mkt_invitation;"
```

### 3.3 执行步骤

```bash
# Step 2.1 — 含 seed INSERT（rule_code='FISSION-001'）
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/migrate_r3_0_e_p2_features.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW TABLES LIKE 'mkt_invitation%'; SELECT * FROM mkt_invitation_rule WHERE rule_code='FISSION-001';"

# Step 2.2 — 无 seed
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/migrate_r3_0_f_p1_remaining.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW TABLES LIKE 'mkt_dispatch%'; DESCRIBE mkt_dispatch_target;"
```

### 3.4 验证标准

- [ ] 4 张表全部存在（mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target）
- [ ] `mkt_invitation_rule` 含 1 行 seed（FISSION-001）
- [ ] 备份文件存在（apply 前）
- [ ] 应用启动后无"表不存在"错误（重启 Spring Boot 看 log）

---

## 4. 第 3 批：DROP + CREATE（1 脚本 — 2 张 google_maps 表）

### 4.1 脚本清单

| 序 | 脚本 | 表 | 风险 | 备份需求 |
|---|---|---|---|---|
| 3.1 | `migrate_ext_17_google_maps.sql` | `google_maps_config` + `google_maps_geocode_log` | 🟠 中（**DROP**）| ✅ mysqldump |

### 4.2 apply 前必做

```bash
mysqldump -h 127.0.0.1 -P 3307 -uroot -proot123456 --single-transaction \
  --databases meiyetec --tables google_maps_config google_maps_geocode_log \
  > /backup/phase-2.3-google-maps-$(date +%Y%m%d-%H%M%S).sql
```

### 4.3 执行步骤

```bash
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW TABLES LIKE 'google_maps_%'; SELECT COUNT(*) AS cfg FROM google_maps_config; SELECT COUNT(*) AS log FROM google_maps_geocode_log;"
```

### 4.4 验证标准

- [ ] 2 张表存在
- [ ] 应用启动后 `/google-maps/config` 端点不 500
- [ ] `/google-maps/geocode/batch` 可调（admin-only）

---

## 5. 第 4 批：ALTER TABLE ADD COLUMN（2 脚本 — 12 列）

### 5.1 脚本清单

| 序 | 脚本 | 表 | 列数 | 风险 |
|---|---|---|---|---|
| 4.1 | `migrate_ext_15_crm_real.sql`（仅前 19 行，**不跑** DROP crm_contact_map + CREATE 部分）| `crm_integration` | 9 列 + 2 索引 | 🟡 低 |
| 4.1b | 同上 L46-48 | `crm_sync_log` | 3 列 | 🟢 低 |

### 5.2 关键决策

> ⚠️ **`migrate_ext_15_crm_real.sql` 包含 DROP + CREATE `crm_contact_map`（L27-54）**。**2.3 范围不含 crm_contact_map**（I002 9 列在 crm_integration，crm_contact_map 不在用户 2.3 列表）。

**执行策略**：手动拆分脚本，只跑 **L9-19（crm_integration 9 列 + 2 索引）+ L46-48（crm_sync_log 3 列）**。

### 5.3 执行步骤

```bash
# 提取安全子集（仅 ALTER 部分）
cat > /tmp/phase-2.3-crm-alter.sql << 'EOF'
-- 仅 ALTER（不含 DROP crm_contact_map）
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

ALTER TABLE crm_sync_log
    ADD COLUMN sync_token     VARCHAR(255) DEFAULT NULL,
    ADD COLUMN records_total  INT          DEFAULT 0,
    ADD COLUMN records_filtered INT        DEFAULT 0;
EOF

mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec < /tmp/phase-2.3-crm-alter.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "DESCRIBE crm_integration; SHOW INDEX FROM crm_integration; DESCRIBE crm_sync_log;"
```

### 5.4 验证标准

- [ ] `crm_integration` 有 9 新列 + `idx_sync_enabled` + `idx_platform_status`
- [ ] `crm_sync_log` 有 3 新列
- [ ] 旧列保留（向后兼容）

---

## 6. 第 5 批：ALTER TABLE ADD COLUMN（1 脚本 — 4 列）

### 6.1 脚本清单

| 序 | 脚本 | 表 | 列数 | 风险 |
|---|---|---|---|---|
| 5.1 | `migrate_ext_22_google_maps_place_details.sql` | `google_maps_config` | 1 列（`enable_place_details`）| 🟢 低 |
| 5.1b | 同上 | `ecommerce_platform` | 3 列（`app_id` / `kdt_id` / `sign_secret`）| 🟢 低 |

### 6.2 执行步骤

```bash
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  < backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql

# 验证
mysql -h 127.0.0.1 -P 3307 -uroot -proot123456 -D meiyetec \
  -e "SHOW COLUMNS FROM google_maps_config LIKE 'enable_place_details'; SHOW COLUMNS FROM ecommerce_platform LIKE 'app_id'; SHOW COLUMNS FROM ecommerce_platform LIKE 'kdt_id'; SHOW COLUMNS FROM ecommerce_platform LIKE 'sign_secret';"
```

### 6.3 验证标准

- [ ] `google_maps_config.enable_place_details` TINYINT 默认 0
- [ ] `ecommerce_platform.app_id` / `kdt_id` VARCHAR(64)
- [ ] `ecommerce_platform.sign_secret` VARCHAR(255)
- [ ] 应用启动后 `/google-maps/config` 返回 `enablePlaceDetails=0`
- [ ] 应用启动后 `/ecommerce/platform/list` 返回新字段

---

## 7. apply 后验证（curl）

### 7.1 Feature 验证清单

| Feature | 端点 | 期望 | 来源 Issue |
|---|---|---|---|
| F032/F036/F037 | `GET /plugin/runtime/dashboard` | 200 + 含 `persistedRecords` | I017 |
| F049/F051 | `GET /plugin/runtime/persisted` | 200 + 不抛 SQL 异常 | I017 |
| F056/F057 | `GET /google-maps/config` | 200 + `enablePlaceDetails: 0` | I029 |
| F058-F065 | `POST /google-maps/geocode/batch` | 200（即使无 key 也走 mock）| I029 |
| F066 | `GET /ecommerce/platform/list` | 200 + JSON 含 `appId/kdtId/signSecret=null` | I029 |
| F075/F076 | `POST /ecommerce/platform` | 200 + 写新字段成功 | I029 |
| F081-F104 | `POST /intl-booking/{id}/send-confirmation` | 200 + multi-channel | I038 + I045 |
| F138 | `POST /mkt/dispatch/*`（MarketingAutomationService）| 200 或 404（业务未实装，但**表已建**）| I063 |

### 7.2 回归测试命令

```bash
# 重新打包 + 启动
cd backend
mvn -B -Dmaven.test.skip=true package
java -jar target/meiyetec-api-1.0.0.jar &

# 等启动
sleep 30

# 登录
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | jq -r .data.token)

# 跑全部验证（参考 scripts/verify-B1-B4.ps1 模板）
# ... 略，见 §7.1
```

---

## 8. 回滚预案（apply 失败时）

### 8.1 按批次回滚

| 批次 | rollback 命令 | 风险 |
|---|---|---|
| 第 1 批 | 无需回滚（IF NOT EXISTS 幂等）| 无 |
| 第 2 批 | `mysqldump` 备份文件恢复 | ⚠️ DROP 数据丢失 |
| 第 3 批 | 同上 | ⚠️ DROP 数据丢失 |
| 第 4 批 | `ALTER TABLE crm_integration DROP COLUMN ...`（9 列）+ DROP INDEX 2 个 + `ALTER TABLE crm_sync_log DROP COLUMN ...`（3 列）| 🟢 安全 |
| 第 5 批 | `ALTER TABLE google_maps_config DROP COLUMN enable_place_details` + `ALTER TABLE ecommerce_platform DROP COLUMN app_id, kdt_id, sign_secret` | 🟢 安全 |

### 8.2 紧急停止信号

任一情况立即停止：
- ⚠️ ALTER 报 `Duplicate column name`（脚本非幂等问题）
- ⚠️ DROP 报错表不存在（备份不匹配）
- ⚠️ 应用启动报 `BeanCreationException` 与新增表相关
- ⚠️ curl 验证出现 5xx

---

## 9. 时间线估算

| 阶段 | 预计耗时 | 备注 |
|---|---|---|
| 0. 备份 | 10 分钟 | 含 crm_contact_map + 6 张 DROP 表 + crm_integration 等 |
| 1. 第 1 批 | 5 分钟 | 3 脚本 |
| 2. 第 2 批 | 10 分钟 | 2 脚本 + 4 张表 + seed 验证 |
| 3. 第 3 批 | 5 分钟 | 1 脚本 + 2 张表 |
| 4. 第 4 批 | 2 分钟 | 1 脚本拆 + 12 列 |
| 5. 第 5 批 | 1 分钟 | 1 脚本 + 4 列 |
| 6. 验证（curl + jshell）| 30 分钟 | 11+ Feature |
| **总计** | **~63 分钟** | 不含 staging 跑批 + DBA 审核 |

---

## 10. 不在本 checklist 范围

- ❌ market_activity RENAME + DROP（§5.1 #1，2.3 不含）
- ❌ mkt_channel_config 重建（§5.1 #2，待写脚本）
- ❌ mkt_promotion / mkt_segment / mkt_audit_log / mkt_automation 4 表（§5.1 #3 部分，2.3 不含）
- ❌ webhook_msg_log（§5.1 #6 部分，2.3 不含）
- ❌ crm_contact_map DROP+CREATE（I002 不在 2.3 范围）
- ❌ 业务代码后索引（§5.1 #9，需业务代码先修）
- ❌ P0-12 / P0-13（软删除 / Profile，2.4+）

---

**Phase 2.3 checklist 完成。等待用户确认 + DBA 审核后 apply。**
