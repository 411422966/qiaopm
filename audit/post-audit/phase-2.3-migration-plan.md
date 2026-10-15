# Phase 2.3 — DB 迁移 Plan（Post-Audit 跑批子集）

> **准备时间**：2026-09-20 00:20 (UTC+8)
> **2.3 范围**：用户决策覆盖 §5.1 的 **#3 部分 + #4 部分 + #5 + #6 + #7 + #8**
> **未在本批次范围**：§5.1 #1 market_activity RENAME+DROP / #2 mkt_channel_config 重建 / #9 业务代码后索引 / webhook_msg_log
> **状态**：📋 **PLAN 阶段**（**未 apply**，等用户确认）

---

## 0. 范围核证（vs §5.1）

| 维度 | §5.1 完整清单 | 2.3 用户范围 | 备注 |
|---|---|---|---|
| **表数** | 9 项 | **9 张表** | 严格子集（不含 §5.1 #2 mkt_channel_config 重建 / webhook_msg_log / #9 索引）|
| **列数** | 16 列 | **16 列** | crm_integration 9 + crm_sync_log 3 + ecommerce_platform 3 + overseas_booking 1 = 16 |
| **脚本族** | 9 个 | **6 个** | I063 涉及 2 个（r3_0_e + r3_0_f）；I002 / I017 / I029 / I038 / I045 各 1 个 |

---

## 1. 9 张表清单（含已有 DDL 与风险）

| # | 表名 | 来源 Issue | 现有 DDL 脚本 | 脚本模式 | 风险等级 | 备注 |
|---|---|---|---|---|---|---|
| 1 | **mkt_invitation** | I063 | `migrate_r3_0_e_p2_features.sql` L43-71 | ⚠️ DROP + CREATE | 🟠 中 | 含 INSERT seed（FISSION-001 规则）|
| 2 | **mkt_invitation_rule** | I063 | 同上 L73-101 + seed L100 | ⚠️ DROP + CREATE | 🟠 中 | 含 INSERT seed（"老带新通用规则"）|
| 3 | **mkt_dispatch** | I063 | `migrate_r3_0_f_p1_remaining.sql` L13-37 | ⚠️ DROP + CREATE | 🟠 中 | 无 seed |
| 4 | **mkt_dispatch_target** | I063 | 同上 L39-58 | ⚠️ DROP + CREATE | 🟠 中 | 无 seed |
| 5 | **overseas_booking** | I038 | `migrate_ext_19_mini_program_openid.sql` L9-39 | ✅ CREATE TABLE IF NOT EXISTS + ALTER TABLE ADD COLUMN IF NOT EXISTS | 🟢 低 | 同时含 mini_program_open_id 列（16 列之一）|
| 6 | **member_message** | I045 | `init_m09_member_message.sql` | ✅ CREATE TABLE IF NOT EXISTS | 🟢 低 | 完整 21 列定义 |
| 7 | **plugin_runtime_state** | I017 | `migrate_ext_23_plugin_runtime_state.sql` L8-26 | ✅ CREATE TABLE IF NOT EXISTS | 🟢 低 | 含 uk_tenant_plugin 唯一键 |
| 8 | **google_maps_config** | I029 | `migrate_ext_17_google_maps.sql` L10-37 | ⚠️ DROP + CREATE | 🟠 中 | 17 列定义完整；enable_place_details 在 ext_22 单独加 |
| 9 | **google_maps_geocode_log** | I029 | `migrate_ext_17_google_maps.sql` L40-58 | ⚠️ DROP + CREATE | 🟡 中-低 | 11 列 |

> ⚠️ **DROP + CREATE 破坏性警告**：mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target / google_maps_config / google_maps_geocode_log 共 6 张表（4+2）使用 `DROP TABLE IF EXISTS`。**生产前必须先备份**且确认无重要数据。

---

## 2. 16 列清单（含 ALTER 脚本）

| # | 表 | 新增列 | 现有 ALTER 脚本 | 风险 | 备注 |
|---|---|---|---|---|---|
| 1 | `crm_integration` | `sync_token` | `migrate_ext_15_crm_real.sql` L9-19 | 🟡 低 | 与其余 8 列同一 ALTER |
| 2 | `crm_integration` | `sync_schedule` | 同上 | 🟡 低 | 默认 `'0 */30 * * * *'` |
| 3 | `crm_integration` | `sync_enabled` | 同上 | 🟡 低 | 默认 1 |
| 4 | `crm_integration` | `last_incremental_sync_at` | 同上 | 🟡 低 | |
| 5 | `crm_integration` | `private_key` | 同上 | 🟡 低 | MEDIUMTEXT（建议加密）|
| 6 | `crm_integration` | `username` | 同上 | 🟡 低 | VARCHAR(128) |
| 7 | `crm_integration` | `login_url` | 同上 | 🟡 低 | VARCHAR(255) |
| 8 | `crm_integration` | `object_mappings` | 同上 | 🟡 低 | TEXT（JSON 字段映射）|
| 9 | `crm_integration` | `sync_window_minutes` | 同上 | 🟡 低 | 默认 30 |
| 10 | `crm_sync_log` | `sync_token` | 同上 L46-48 | 🟢 低 | VARCHAR(255) |
| 11 | `crm_sync_log` | `records_total` | 同上 | 🟢 低 | INT 默认 0 |
| 12 | `crm_sync_log` | `records_filtered` | 同上 | 🟢 低 | INT 默认 0 |
| 13 | `ecommerce_platform` | `app_id` | `migrate_ext_22_google_maps_place_details.sql` L10 | 🟡 低 | VARCHAR(64)（区别于 appKey）|
| 14 | `ecommerce_platform` | `kdt_id` | 同上 L11 | 🟡 低 | VARCHAR(64) |
| 15 | `ecommerce_platform` | `sign_secret` | 同上 L12 | 🟡 低 | VARCHAR(255)（webhook 验签密钥）|
| 16 | `overseas_booking` | `mini_program_open_id` | `migrate_ext_19_mini_program_openid.sql` L42 | 🟢 低 | VARCHAR(128) |

> ⚠️ 16 列 = crm_integration 9 + crm_sync_log 3 + ecommerce_platform 3 + overseas_booking 1 — 与 §5.1 一致。

---

## 3. 脚本文件状态汇总

| 脚本路径 | 大小 | 用途 | 待写？ |
|---|---|---|---|
| `db/sql/migrate_r3_0_e_p2_features.sql` | 10976 B | mkt_invitation + mkt_invitation_rule | ✅ 已存在（**DROP + CREATE**，需重写为 IF NOT EXISTS）|
| `db/sql/migrate_r3_0_f_p1_remaining.sql` | 8477 B | mkt_dispatch + mkt_dispatch_target | ✅ 已存在（**DROP + CREATE**，需重写为 IF NOT EXISTS）|
| `db/sql/migrate_ext_19_mini_program_openid.sql` | 1190 B | overseas_booking（IF NOT EXISTS）+ mini_program_open_id | ✅ 已存在 |
| `db/sql/init_m09_member_message.sql` | 1948 B | member_message（IF NOT EXISTS）| ✅ 已存在 |
| `db/sql/migrate_ext_23_plugin_runtime_state.sql` | 1210 B | plugin_runtime_state（IF NOT EXISTS）| ✅ 已存在 |
| `db/sql/migrate_ext_17_google_maps.sql` | 1360 B | google_maps_config + google_maps_geocode_log（**DROP + CREATE**）| ✅ 已存在（**DROP + CREATE**，需重写）|
| `db/sql/migrate_ext_22_google_maps_place_details.sql` | 1500 B | google_maps_config.enable_place_details + ecommerce_platform 3 列 | ✅ 已存在 |
| `db/sql/migrate_ext_15_crm_real.sql` | 4500 B | crm_integration 9 列 + crm_sync_log 3 列 + crm_contact_map | ✅ 已存在 |
| `db/sql/migrate_mkt_002_drop_market_activity.sql` | 3513 B | market_activity RENAME+DROP（**§5.1 #1，2.3 不含**）| ⏸ 不动 |
| `db/sql/migrate_mkt_002_rollback.sql` | 1633 B | market_activity rollback（**2.3 不含**）| ⏸ 不动 |

**结论**：所有 2.3 范围内的脚本**已存在**，**无需新写**。

---

## 4. 风险矩阵

| 风险类型 | 数量 | 描述 | 缓解措施 |
|---|---|---|---|
| 🟠 DROP + CREATE 破坏性 | 6 张表 | mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target / google_maps_config / google_maps_geocode_log | apply 前 `mysqldump` 备份 + DBA 审核 + staging 先跑 |
| 🟡 MEDIUMTEXT 加密 | 1 列 | crm_integration.private_key | 备份加密后再写库（应用层） |
| 🟡 默认 seed 数据 | 1 张表 | mkt_invitation_rule 含 INSERT IGNORE（FISSION-001 规则）| apply 后检查 rule_code 是否插入 |
| 🟢 IF NOT EXISTS 安全 | 3 张表 | overseas_booking / member_message / plugin_runtime_state | 可直接 apply，无需备份（已 idempotent）|
| 🟢 ADD COLUMN IF NOT EXISTS 安全 | 1 列 | overseas_booking.mini_program_open_id | 可直接 apply |

---

## 5. Rollback 准备

| 脚本 | 是否需要 rollback | rollback 方式 |
|---|---|---|
| `migrate_r3_0_e_p2_features.sql` (mkt_invitation + mkt_invitation_rule) | ⚠️ DROP 后只能备份恢复 | **需先 mysqldump**；rollback = DROP TABLE + 从备份恢复 |
| `migrate_r3_0_f_p1_remaining.sql` (mkt_dispatch + mkt_dispatch_target) | ⚠️ DROP 后只能备份恢复 | 同上 |
| `migrate_ext_19_mini_program_openid.sql` (overseas_booking + 列) | ✅ 可逆 | `DROP COLUMN mini_program_open_id` |
| `init_m09_member_message.sql` (member_message) | ✅ 可逆（drop table）| `DROP TABLE member_message` |
| `migrate_ext_23_plugin_runtime_state.sql` | ✅ 可逆（drop table）| `DROP TABLE plugin_runtime_state` |
| `migrate_ext_17_google_maps.sql` (google_maps_config + geocode_log) | ⚠️ DROP 后只能备份恢复 | **需先 mysqldump**；rollback = DROP TABLE + 从备份恢复 |
| `migrate_ext_22_google_maps_place_details.sql` (3+1 列) | ✅ 可逆 | `DROP COLUMN app_id / kdt_id / sign_secret / enable_place_details` |
| `migrate_ext_15_crm_real.sql` (crm_integration 9 + crm_sync_log 3) | ⚠️ DROP crm_contact_map 不可逆 | **crm_contact_map 需先备份**；9+3 列 ADD 可 DROP COLUMN |

---

## 6. DBA 审核 Checklist（apply 前必过）

### 6.1 apply 前

- [ ] **mysqldump** 6 张 DROP 表（mkt_invitation / mkt_invitation_rule / mkt_dispatch / mkt_dispatch_target / google_maps_config / google_maps_geocode_log）+ crm_contact_map
- [ ] **mysqldump** crm_integration / crm_sync_log / ecommerce_platform / overseas_booking（ADD COLUMN 备份）
- [ ] **MySQL 版本** ≥ 8.0.29（`ADD COLUMN IF NOT EXISTS` 需要）
- [ ] **staging 环境** 先跑完整 6 个脚本 + curl 验证 11+ Feature
- [ ] **生产窗口** 选低峰（建议 02:00-04:00）
- [ ] **运维在岗** + **DBA 现场审批**

### 6.2 apply 中

- [ ] 按 `phase-2.3-migration-checklist.md` 顺序跑（先无依赖 → 后有外键）
- [ ] 每个脚本跑完立即 `SHOW TABLES` / `DESCRIBE` 验证
- [ ] 任一脚本失败 → **立即停止**，启动 rollback

### 6.3 apply 后

- [ ] curl 验证 **11+ Feature**（按 §5.1 表格 — F032/F036/F037/F049/F051 / F056/F057/F058-F065 / F066/F075/F076/F077 / F081-F104 / F138 部分）
- [ ] `git commit` migration apply tag（建议 `phase-2.3-apply-20260920`）
- [ ] 监控 1 小时看 MySQL 慢查询日志 / 应用错误日志
- [ ] 把 25 个 PARTIAL-BLOCKER Feature 更新为 VERIFIED（**等人工确认**，不在 2.3 范围）

---

## 7. 2.3 阶段产物清单（已生成）

- ✅ `audit/post-audit/phase-2.3-migration-plan.md`（本文件）
- ⏳ `audit/post-audit/phase-2.3-migration-checklist.md`（下一个 todo）
- ⏳ 不实际跑迁移（**仅准备**）

---

## 8. 不做的事（用户红线）

- ❌ **不实际跑迁移**（apply 是下一阶段，需 DBA 审核 + staging 验证）
- ❌ **不改业务代码**（只生成 DDL 准备文档）
- ❌ **不进 P0-12/P0-13**（软删除/Profile 是 2.4 之后）
- ❌ **不动 market_activity 重构 / mkt_channel_config 重建 / webhook_msg_log / 业务代码后索引**（用户 2.3 范围不含）
- ❌ **不生成新脚本**（所有 DDL 已存在）

---

**Phase 2.3 准备完成。下一步：生成 `phase-2.3-migration-checklist.md`（按依赖排序的执行清单）。**
