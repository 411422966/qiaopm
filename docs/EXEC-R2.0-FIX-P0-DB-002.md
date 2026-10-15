# EXEC-R2.0-FIX-P0-DB-002 — 外键约束迁移执行报告

> **任务**：P0-DB-002 — Foreign Key Constraints Enforcement
> **批次**：2 / 8（修复路线第 1 周 Day 2）
> **执行日期**：2026-08-14
> **执行人**：Cursor Agent
> **范围**：13 个 P0 FK 落地 + 孤儿数据修复 + deleted 列新增
> **前置依赖**：批次 1（P0-DB-001 DDL Drift）✅
> **后续依赖**：批次 3（P0-MONEY-001 DCEP）
> **实际验收**：✅ **FK Smoke Test 8/8 PASS（17 FK 全部落地）**

---

## 1. 背景

Phase 2 审计（`docs/audit/BEAUTY_SAAS/22_P0_DB_001_PHASE2_REPAIR_PLAN.md`）识别了 17 个 FK 候选：
- 9 个 P0_FK_REQUIRED（核心业务不变式）
- 6 个 P1_FK_RECOMMENDED（建议加）
- 2 个 BLOCKED_BY_DATA（需先修数据）

本批次目标：**把 17 个 FK 全部落地**（FK-1 到 FK-17）。

---

## 2. 实际 FK 孤儿诊断（实测）

| FK | 表.列 | 父表 | Phase 2 报告 | 实测 | 差异 |
|---|---|---|---:|---:|---|
| FK-1 | crm_wallet.member_id | crm_member | 0 | **136** | ⚠️ 新发现 |
| FK-2 | crm_wallet.tenant_id | sys_tenant | 0 | 0 | ✅ |
| FK-3 | crm_wallet_txn.member_id | crm_member | 0 | **37** | ⚠️ 新发现 |
| FK-4 | crm_wallet_txn.order_id | pos_order | 0 | 0 | ✅ |
| FK-5 | crm_wallet_txn.operator_id | sys_user | 4 | 0 | ✅ 批次 1 修复 |
| FK-6 | crm_wallet_txn.tenant_id | sys_tenant | 0 | 0 | ✅ |
| FK-7 | biz_booking.member_id | crm_member | 0 | 0 | ✅ |
| FK-8 | biz_booking.employee_id | pos_employee | 0 | **1** | ⚠️ 新发现 |
| FK-9 | biz_booking.room_id | biz_room | 0 | 0 | ✅ |
| FK-10 | biz_booking.operator_id | sys_user | 5 | 0 | ✅ 批次 1 修复 |
| FK-11 | biz_booking.tenant_id | sys_tenant | 0 | 0 | ✅ |
| FK-12 | biz_room.store_id | pos_store | 0 | 0 | ✅ |
| FK-13 | biz_room.tenant_id | sys_tenant | 0 | 0 | ✅ |
| FK-14 | biz_employee_schedule.employee_id | pos_employee | 0 | 0 | ✅ |
| FK-15 | biz_employee_schedule.tenant_id | sys_tenant | 0 | 0 | ✅ |
| FK-16 | sys_login_log.user_id | sys_user | 0 | **34** | ⚠️ 新发现 |
| FK-17 | sys_login_log.tenant_id | sys_tenant | 0 | 0（204 NULL） | ✅ |

**3 个新孤儿 + Phase 2 漏报**（实际数据规模比 phase 2 报告大）：
- FK-1: 136 wallet.member_id 引用 9001-9999 不存在的 member
- FK-3: 37 wallet_txn.member_id 引用 9001-9999 不存在的 member
- FK-8: 1 booking.employee_id=7 引用不存在的 employee（seed 数据）
- FK-16: 34 login_log.user_id 引用已删除的 sys_user

---

## 3. 修复策略决策

| FK | 决策 | 理由 |
|---|---|---|
| FK-1 | **物理删除 136 行 + 备份到 crm_wallet_orphan_log** | member_id 是 NOT NULL，软删除加 FK 仍校验 deleted=1 行 |
| FK-3 | **物理删除 37 行 + 备份到 crm_wallet_txn_orphan_log** | 同 FK-1 |
| FK-8 | **重派 booking.employee_id=7 → 1**（EMP001 王雪梅） | 单条脏数据，booking 状态 status=5 已取消 |
| FK-16 | **软删除 + 已删行 user_id 置 NULL** | user_id 是 nullable，软删 + 清空已软删行的 user_id |
| FK-17 | **nullable FK** | 204 行 NULL tenant（系统层日志）保留 |

---

## 4. 迁移脚本与验收

### 4.1 文件清单

| 文件 | 用途 |
|---|---|
| `migrate_v0_p0_db_002_fk_constraints.sql` | 主迁移脚本（PRECHECK + 数据修复 + 加 FK + POSTCHECK） |
| `migration_smoke_test_fk.sql` | 8 步验收 |

### 4.2 验收清单（实测 PASS）

| 测试 | 期望 | 实测 | 状态 |
|---|---:|---:|:---:|
| T1: 17 FK 全部存在 | 17 | 17 | ✅ |
| T2: 11 个 FK 孤儿=0 | 11×0 | 11×0 | ✅ |
| T3: 3 张表加 deleted 列 | 3 | 3 | ✅ |
| T4: 软删除行数 | 0/0/34 | 0/0/34 | ✅ |
| T5: 活跃行数 | 8/8/6/4/42/170/8 | 全部吻合 | ✅ |
| T6: FK 拦截测试 | CAUGHT | CAUGHT | ✅ |
| T7: NULL tenant 允许 | YES YES | YES YES | ✅ |
| T8: 跨租户污染 | 0 | 0 | ✅ |
| 总评 | PASS | PASS | ✅ |

### 4.3 数据快照（迁移后）

```
crm_wallet:               8 行（活跃） + 136 行备份到 crm_wallet_orphan_log
crm_wallet_transaction:   8 行（活跃） + 37 行备份到 crm_wallet_txn_orphan_log
biz_booking:              6 行（1 条 employee_id 已重派）
biz_room:                 4 行
biz_employee_schedule:   42 行
sys_login_log:          170 行活跃 + 34 行 deleted=1（孤儿已 user_id=NULL）
crm_member:               8 行
pos_employee:             6 行
pos_order:              303 行
sys_tenant:               2 行（id=1 default, id=2 tenant_b）
```

---

## 5. 已落地的 17 个 FK

```
fk_wallet_member        crm_wallet.member_id        → crm_member.id
fk_wallet_tenant        crm_wallet.tenant_id        → sys_tenant.id            (ON DELETE SET NULL)
fk_wallet_txn_member    crm_wallet_txn.member_id    → crm_member.id
fk_wallet_txn_order     crm_wallet_txn.order_id     → pos_order.id             (ON DELETE SET NULL)
fk_wallet_txn_operator  crm_wallet_txn.operator_id  → sys_user.id              (ON DELETE SET NULL)
fk_wallet_txn_tenant    crm_wallet_txn.tenant_id    → sys_tenant.id            (ON DELETE SET NULL)
fk_booking_member       biz_booking.member_id       → crm_member.id
fk_booking_employee     biz_booking.employee_id     → pos_employee.id
fk_booking_room         biz_booking.room_id         → biz_room.id              (ON DELETE SET NULL)
fk_booking_operator     biz_booking.operator_id     → sys_user.id              (ON DELETE SET NULL)
fk_booking_tenant       biz_booking.tenant_id       → sys_tenant.id            (ON DELETE SET NULL)
fk_room_store           biz_room.store_id           → pos_store.id             (ON DELETE SET NULL)
fk_room_tenant          biz_room.tenant_id          → sys_tenant.id            (ON DELETE SET NULL)
fk_schedule_employee    biz_employee_schedule.employee_id → pos_employee.id
fk_schedule_tenant      biz_employee_schedule.tenant_id   → sys_tenant.id     (ON DELETE SET NULL)
fk_login_log_user       sys_login_log.user_id       → sys_user.id              (ON DELETE SET NULL)
fk_login_log_tenant     sys_login_log.tenant_id     → sys_tenant.id            (ON DELETE SET NULL)
```

---

## 6. 执行踩坑记录（重要）

### 6.1 MySQL FK 校验所有行（不只是 deleted=0）

```sql
-- 软删除后再加 FK → 仍会失败
UPDATE crm_wallet SET deleted=1 WHERE ...;
ALTER TABLE crm_wallet ADD CONSTRAINT fk FOREIGN KEY (member_id) ...;
-- ERROR 1452: Cannot add or update a child row: a foreign key constraint fails

-- 原因：MySQL ADD FK 会校验整张表的所有行（含 deleted=1）
```

**解决**：要么真删除孤儿行，要么把孤儿列设为 NULL（仅对 nullable 列有效）。`member_id` 是 NOT NULL，所以必须物理删除（先备份到 orphan_log 表）。

### 6.2 MySQL DELETE 不支持外层别名

```sql
-- ❌ Unknown column 'w.member_id'
DELETE FROM crm_wallet w
WHERE w.member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id);

-- ✅ 必须用全表名
DELETE FROM crm_wallet
WHERE member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = crm_wallet.member_id);
```

### 6.3 幂等性：FK 加不上第二次（缺 IF NOT EXISTS）

MySQL 8 不支持 `ALTER TABLE ADD CONSTRAINT IF NOT EXISTS`。解决方案：存储过程 + information_schema.TABLE_CONSTRAINTS 查重：

```sql
CREATE PROCEDURE add_fk_if_missing(IN p_table, IN p_constraint, IN p_sql)
BEGIN
  DECLARE v_exists INT;
  SELECT COUNT(*) INTO v_exists FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = 'meiyetec' AND TABLE_NAME = p_table AND CONSTRAINT_NAME = p_constraint;
  IF v_exists = 0 THEN PREPARE stmt FROM p_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END;
```

### 6.4 Phase 2 报告漏报 136 行孤儿

Phase 2 报告说 crm_wallet 0 孤儿，但实测 136 行（member_id ≥ 9001）。phase 2 审计基于 phase 1 时点的 seed 数据，没追到生产新增的脏数据。

**修复策略**：每次批次启动前先做 PRECHECK（脚本自带），不依赖 Phase 2 报告的旧数字。

---

## 7. 后续路径

```
批次 2 ✅（PASS — 17 FK 落地）
  ↓
批次 3 (P0-MONEY-001 DCEP) — 1.5d
  ↓
批次 5 (P0-MONEY-003/004 ID/多租户) — 1.5d
  ↓
批次 6 (P0-TEST-001 Wallet/Booking 并发) — 2.0d
  ↓
Reality Score 预期从 96% → 97%+
```

---

## 8. 交付物

| 类型 | 文件 |
|---|---|
| 新建 | `backend/src/main/resources/db/sql/migrate_v0_p0_db_002_fk_constraints.sql` |
| 新建 | `backend/src/main/resources/db/sql/migration_smoke_test_fk.sql` |
| 自动生成 | `crm_wallet_orphan_log`（136 行孤儿备份） |
| 自动生成 | `crm_wallet_txn_orphan_log`（37 行孤儿备份） |
| 修改 | `docs/EXEC-R2.0-FIX-P0-DB-002.md`（本报告） |

---

## 9. 与 Phase 2 审计的差异

| 维度 | Phase 2 报告 | 实测 | 原因 |
|---|---:|---:|---|
| FK-1 wallet orphans | 0 | 136 | phase 2 未考虑 member_id ≥ 9001 的生产脏数据 |
| FK-3 wallet_txn orphans | 0 | 37 | 同上 |
| FK-8 booking orphans | 0 | 1 | phase 2 时 booking.employee_id=7 已存在但 pos_employee 0 行，phase 2 没识别 |
| FK-16 login_log orphans | 0 | 34 | phase 2 未审计 login_log.user_id 的 orphan 情况 |
| sys_tenant | (无审计) | 2 行 | phase 2 未提 sys_tenant 表，但实际数据存在 |
| crm_wallet NULL tenant | 50 | 86（后 86→0） | phase 2 漏掉 36 个（seed 与生产脏数据混算） |

**结论**：Phase 2 审计数字与生产实测有显著差异。建议**所有批次 1-6 完成后做一次 Phase 3 全量回测**，作为 Reality Score 修正基线。
