# P0-DB-001 — Phase 2 Repair Plan (READ-ONLY)

**Task**: P0-DB-001 — Database Schema Drift Repair
**Phase**: 2 — REPAIR PLAN + EVIDENCE VERIFICATION
**Date**: 2026-08-13
**Auditor**: Senior Architect (Reality-First)
**Status**: COMPLETE — READ-ONLY. No code, schema, or DB changes.

---

## 1. Executive Summary

Phase 1 confirmed 6 P0 tables have **DDL_MISSING** drift. Phase 2 re-verifies the evidence, performs the orphan check on all 17 FK candidates, deeply audits `crm_wallet` and the wallet/member balance dual-track, evaluates schema normalization options, and proposes a minimal, safe repair sequence.

**Key new findings (vs. Phase 1)**:

1. **`sys_login_log` IS NOT "Entity-less"** — Phase 1 had a wrong sub-finding. The Entity (`SysLoginLog.java`), Mapper, Service, Controller all exist. The Controller uses `QueryWrapper` (MyBatis-Plus), NOT `JdbcTemplate` (only the CSV export endpoint uses raw SQL).
2. **`biz_employee_schedule` IS NOT a DEAD TABLE** — Phase 1 was wrong. It has `ScheduleController.java` with full CRUD (`/schedule` endpoint) and is actively used by `BookingController.java` line 144-147 (technician availability check). Frontend route `/booking/schedule` exists.
3. **All 50 NULL-tenant `crm_wallet` rows CAN be safely derived from `crm_member.tenant_id`** — all 50 reference valid `crm_member` rows with `tenant_id=1`. **0 cross-tenant contamination**.
4. **Root cause of 50 NULL wallets**: `WalletMapper.xml:18-22` — `insertIfAbsent` SQL has NO `tenant_id` column. All wallets created via `WalletController.ensureWallet()` get NULL tenant.
5. **`sys_log.tenant_id` is 100% NULL** (1089 rows) — `SysLog` Entity has no `tenantId` field. `LogAspect` never sets it. The DB column exists (from `fix_missing_tables.sql`) but no code writes to it.
6. **29 wallet/member balance mismatches** (out of 108 wallets, 21 in NULL-tenant group, 8 in OK group). Total monetary drift: **-32,152.00** (member.total less than wallet.total).
7. **9 FK orphans found**: 4 in `crm_wallet_transaction.operator_id` + 5 in `biz_booking.operator_id`, all referencing `sys_user.id=7` (deleted/missing user).

---

## 2. Final Schema Truth (Re-verified)

Same 6 P0 tables from Phase 1; the DDL chain status remains:

| Table | DDL in init_*.sql? | DDL in fix_*.sql? | DB has it? | Entity has tenant_id? | Entity missing tenant_id? |
|---|---|---|---|---|---|
| `crm_wallet` | ❌ | ✅ `fix_missing_tables.sql:57-70` | ✅ | ❌ | ⚠️ Entity has NO tenantId field |
| `crm_wallet_transaction` | ❌ | ✅ `fix_missing_tables.sql:75-95` | ✅ | ✅ | OK |
| `biz_booking` | ❌ | ✅ `fix_booking_calendar_tables.sql:10-43` | ✅ | ✅ | OK |
| `biz_room` | ❌ | ✅ `fix_booking_calendar_tables.sql:72-90` | ✅ | ✅ | OK |
| `biz_employee_schedule` | ❌ | ✅ `fix_booking_calendar_tables.sql:48-67` | ✅ | ✅ | OK |
| `sys_login_log` | ❌ (only `init_rbac_v2_loginlog.sql:4-7` ALTER, requires table pre-exists) | ✅ `fix_missing_tables.sql:37-52` | ✅ | ✅ | OK |
| `sys_log` (bonus) | ❌ | ✅ `fix_missing_tables.sql:10-32` | ✅ | ❌ | ⚠️ Entity has NO tenantId field |

### 2.1 Phase 1 vs Phase 2 Corrections

| Phase 1 Claim | Reality | Status |
|---|---|---|
| `sys_login_log` has No Entity | Entity `SysLoginLog.java` exists | ❌ CONTRADICTED |
| `sys_login_log` Controller uses `JdbcTemplate` | Controller uses `QueryWrapper` (MyBatis-Plus) | ❌ CONTRADICTED |
| `biz_employee_schedule` is DEAD TABLE | Has `ScheduleController.java` + used by `BookingController` | ❌ CONTRADICTED |

These corrections do NOT change the **drift** (DDL_MISSING) — they only refine the impact assessment.

---

## 3. 17 FK Candidate — Final Decisions

### 3.1 Evidence Collection Method

For each candidate, verified:
- (1) Child table & column exist via `information_schema.COLUMNS`
- (2) Parent table & PK exist via `SHOW CREATE TABLE`
- (3) Java Entity field exists (grep)
- (4) Mapper/Service real use (grep for `memberId`, `getMemberId`, etc.)
- (5) Business semantics (Controller validation)
- (6) Orphan count (NOT EXISTS join)
- (7) Tenant isolation impact

### 3.2 Final Decision Matrix

| FK ID | Child Table | Child Col | Parent Table | Parent Col | Code Evidence | Orphan Count | Decision | Reason |
|---|---|---|---|---|---|---:|---|---|
| **FK-1** | `crm_wallet` | `member_id` | `crm_member` | `id` | ✅ `WalletController.ensureWallet` validates member | 0 | **P0_FK_REQUIRED** | Core business invariant. Required. |
| **FK-2** | `crm_wallet` | `tenant_id` | `sys_tenant` | `id` | ✅ `WalletController` uses `TenantContextHolder` | 0 (50 NULL allowed) | **P1_FK_RECOMMENDED** | After NULL cleanup, FK nullable with `ON DELETE SET NULL`. |
| **FK-3** | `crm_wallet_transaction` | `member_id` | `crm_member` | `id` | ✅ `WalletController.doRecharge/doConsume/doRefund` validates | 0 | **P0_FK_REQUIRED** | Core invariant. |
| **FK-4** | `crm_wallet_transaction` | `order_id` | `pos_order` | `id` | ✅ Refund path uses `order_id` | 0 (8 NULL allowed) | **P0_FK_REQUIRED** (nullable) | Refund idempotency check. |
| **FK-5** | `crm_wallet_transaction` | `operator_id` | `sys_user` | `id` | ✅ `WalletController` uses `dto.getOperatorId()` | **4 orphans** | **BLOCKED_BY_DATA** | 4 rows reference `sys_user.id=7` (missing). Must remediate first. |
| **FK-6** | `crm_wallet_transaction` | `tenant_id` | `sys_tenant` | `id` | ✅ `WalletController` reads `TenantContextHolder` | 0 | **P1_FK_RECOMMENDED** (nullable) | Tenant scope. |
| **FK-7** | `biz_booking` | `member_id` | `crm_member` | `id` | ✅ `BookingController.create` validates | 0 | **P0_FK_REQUIRED** | Core invariant. |
| **FK-8** | `biz_booking` | `employee_id` | `pos_employee` | `id` | ✅ `BookingController` validates | 0 | **P0_FK_REQUIRED** | Core invariant. |
| **FK-9** | `biz_booking` | `room_id` | `biz_room` | `id` | ✅ Indirect (room_name stored separately) | 0 | **P1_FK_RECOMMENDED** (nullable) | Optional field. |
| **FK-10** | `biz_booking` | `operator_id` | `sys_user` | `id` | ✅ Stored but not validated | **5 orphans** | **BLOCKED_BY_DATA** | 5 rows reference `sys_user.id=7`. Must remediate first. |
| **FK-11** | `biz_booking` | `tenant_id` | `sys_tenant` | `id` | ✅ Tenant context | 0 | **P1_FK_RECOMMENDED** (nullable) | Tenant scope. |
| **FK-12** | `biz_room` | `store_id` | `pos_store` | `id` | ✅ `RoomController` accepts store_id | 0 | **P0_FK_REQUIRED** (nullable) | Core invariant. |
| **FK-13** | `biz_room` | `tenant_id` | `sys_tenant` | `id` | ✅ | 0 | **P1_FK_RECOMMENDED** (nullable) | Tenant scope. |
| **FK-14** | `biz_employee_schedule` | `employee_id` | `pos_employee` | `id` | ✅ `BookingController` line 144 validates | 0 | **P0_FK_REQUIRED** | Core invariant. |
| **FK-15** | `biz_employee_schedule` | `tenant_id` | `sys_tenant` | `id` | ✅ | 0 | **P1_FK_RECOMMENDED** (nullable) | Tenant scope. |
| **FK-16** | `sys_login_log` | `user_id` | `sys_user` | `id` | ✅ `AuthController` writes, `SysLoginLogController` reads | 0 (35 NULL allowed) | **P1_FK_RECOMMENDED** (nullable) | Nullable: failed-login rows have NULL user_id. |
| **FK-17** | `sys_login_log` | `tenant_id` | `sys_tenant` | `id` | ✅ `init_rbac_v2_loginlog.sql` adds column | 0 (173 NULL allowed) | **P1_FK_RECOMMENDED** (nullable) | After NULL cleanup. |

### 3.3 FK Counts

| Decision | Count |
|---|---:|
| **P0_FK_REQUIRED** (mandatory P0) | 9 |
| **P1_FK_RECOMMENDED** (after data remediation) | 6 |
| **BLOCKED_BY_DATA** (needs remediation first) | 2 |
| **NO_FK** | 0 |
| **Total** | **17** |

### 3.4 Orphan Data Details (BLOCKED_BY_DATA)

#### FK-5: `crm_wallet_transaction.operator_id` → `sys_user.id` (4 orphans)

| txn_id | tenant_id | member_id | type | operator_id | remark |
|---:|---:|---:|---:|---:|---|
| 4 | 1 | 5 | 1 | 7 | 充值 5000 送 500 活动 |
| 5 | 1 | 6 | 1 | 7 | 新会员充值优惠 |
| 6 | 1 | 4 | 1 | 7 | 7天前充值 |
| 7 | 1 | 7 | 1 | 7 | 5天前充值 |

**Root cause**: `sys_user.id=7` no longer exists. `seed_wallet.sql:8-11` inserted 4 wallet_txn rows referencing `operator_id=7` (a cashier named "操作员7" who was likely an admin in seed data that has since been deleted).

**Safe remediation** (proposed, NOT executed):
- Option A: `UPDATE crm_wallet_transaction SET operator_id = (SELECT id FROM sys_user WHERE username='admin' LIMIT 1) WHERE operator_id = 7;` — reassigns to admin user.
- Option B: `UPDATE crm_wallet_transaction SET operator_id = NULL WHERE operator_id = 7;` — clears (since operator_id is nullable).

#### FK-10: `biz_booking.operator_id` → `sys_user.id` (5 orphans)

| booking_id | booking_no | member_id | employee_id | room_id | operator_id | status |
|---:|---|---:|---:|---:|---:|---:|
| 9 | BK20260809170001 | 7 | 1 | 3 | 7 | 1 |
| 10 | BK20260809130001 | 8 | 2 | 4 | 7 | 5 |
| 11 | BK20260810100001 | 1 | 1 | 1 | 7 | 1 |
| 12 | BK20260810140001 | 3 | 3 | 2 | 7 | 1 |
| 13 | BK20260811090001 | 2 | 5 | 1 | 7 | 1 |

Same root cause: `sys_user.id=7` no longer exists.

**Safe remediation** (proposed, NOT executed):
- Same options A/B as FK-5.

### 3.5 SysLog FK (out of 17 list, but still relevant)

Phase 1 did not list `sys_log` FKs in the 17. For completeness:
- `sys_log.user_id → sys_user.id`: 0 orphans (1089 rows, none orphan). Optional, P1.
- `sys_log.tenant_id → sys_tenant.id`: 1089 NULL rows. **Blocked by data** until `SysLog` Entity is updated to populate `tenant_id`. **OUT OF SCOPE for P0-DB-001.**

---

## 4. Orphan Data Audit (Per FK)

### 4.1 Orphan Counts (Verified via NOT EXISTS)

| FK | Child Col | Parent | Orphan Count | Affected Rows |
|---|---|---|---:|---|
| FK-1 | crm_wallet.member_id | crm_member.id | **0** | None |
| FK-2 | crm_wallet.tenant_id | sys_tenant.id | **0** | None (50 NULL allowed) |
| FK-3 | crm_wallet_txn.member_id | crm_member.id | **0** | None |
| FK-4 | crm_wallet_txn.order_id | pos_order.id | **0** | None (8 NULL allowed) |
| **FK-5** | **crm_wallet_txn.operator_id** | **sys_user.id** | **4** | **BLOCKED — operator_id=7** |
| FK-6 | crm_wallet_txn.tenant_id | sys_tenant.id | **0** | None |
| FK-7 | biz_booking.member_id | crm_member.id | **0** | None |
| FK-8 | biz_booking.employee_id | pos_employee.id | **0** | None |
| FK-9 | biz_booking.room_id | biz_room.id | **0** | None (1 NULL allowed) |
| **FK-10** | **biz_booking.operator_id** | **sys_user.id** | **5** | **BLOCKED — operator_id=7** |
| FK-11 | biz_booking.tenant_id | sys_tenant.id | **0** | None |
| FK-12 | biz_room.store_id | pos_store.id | **0** | None |
| FK-13 | biz_room.tenant_id | sys_tenant.id | **0** | None |
| FK-14 | biz_employee_schedule.employee_id | pos_employee.id | **0** | None |
| FK-15 | biz_employee_schedule.tenant_id | sys_tenant.id | **0** | None |
| FK-16 | sys_login_log.user_id | sys_user.id | **0** | None (35 NULL allowed) |
| FK-17 | sys_login_log.tenant_id | sys_tenant.id | **0** | None (173 NULL allowed) |

### 4.2 ORPHAN DATA FOUND

- **FK-5**: 4 orphan rows in `crm_wallet_transaction` referencing deleted `sys_user.id=7`.
- **FK-10**: 5 orphan rows in `biz_booking` referencing deleted `sys_user.id=7`.
- **All 9 orphans share the same root cause** (deleted seed user). **Both FKs must be marked `BLOCKED_BY_DATA`** until remediated.

---

## 5. crm_wallet Tenant Data Audit

### 5.1 Tenant Derivation Evidence Matrix (sample)

50 NULL-tenant wallet rows, all reference valid `crm_member` rows. Sample (full list in audit run):

| wallet_id | member_id | wallet.tenant_id | member.tenant_id | Can Derive? | Reason |
|---:|---:|---:|---:|---|---|
| 9 | 9004 | NULL | 1 | ✅ YES | Member exists, has tenant_id=1 |
| 10 | 9001 | NULL | 1 | ✅ YES | Member exists, has tenant_id=1 |
| 11 | 9009 | NULL | 1 | ✅ YES | Member exists (退款测试会员) |
| 19 | 9029 | NULL | 1 | ✅ YES | Member exists, has tenant_id=1 |
| ... | ... | ... | ... | ✅ YES | (all 50 follow the same pattern) |

**Summary**: **50/50 rows are 100% derivable**. **0 cross-tenant mismatches**. **0 orphan member references**.

### 5.2 Additional Wallet Checks

- **Duplicate wallets (same member_id)**: 0 (UNIQUE KEY `uk_member` enforced).
- **member_id=0 or NULL**: 0.
- **Cross-tenant (wallet.tenant_id != member.tenant_id)**: 0 rows.
- **sys_user.id=7 issue**: This user is referenced by 4 wallet_txn and 5 bookings, NOT by any wallet. **Not a wallet issue.**

### 5.3 Root Cause of 50 NULL Wallets

**File**: `backend/src/main/resources/mapper/WalletMapper.xml:18-22`
```xml
<insert id="insertIfAbsent">
    INSERT INTO crm_wallet(member_id, balance, total_recharge, total_consume, frozen_amount, version)
    SELECT #{memberId}, 0, 0, 0, 0, 0
    WHERE NOT EXISTS (SELECT 1 FROM crm_wallet WHERE member_id = #{memberId})
</insert>
```

**Defect**: The INSERT does NOT include `tenant_id`. Since `crm_wallet.tenant_id` is `DEFAULT NULL`, all wallets created via `WalletController.ensureWallet()` (called from doRecharge/doConsume/doRefund/PosRefundService) get `tenant_id=NULL`.

**This is a real Java/MyBatis defect** that affects production behavior. **Out of scope for P0-DB-001 Phase 2** (which is READ-ONLY) — recorded in `REPAIR_FOLLOWUPS.md` candidates.

### 5.4 Safe Remediation Plan for NULL tenants

**Phase 3 minimum** (proposed, NOT executed):
```sql
-- Precheck
SELECT COUNT(*) FROM crm_wallet w
  INNER JOIN crm_member m ON m.id = w.member_id
  WHERE w.tenant_id IS NULL AND m.tenant_id IS NULL;
-- Expected: 0

-- Apply derivation
UPDATE crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL;

-- Postcheck
SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL;
-- Expected: 0
```

After this, FK-2 (`crm_wallet.tenant_id → sys_tenant.id`) becomes safe to add as `P1_FK_RECOMMENDED`.

---

## 6. Wallet Balance Dual-Track Evidence

### 6.1 Reality Matrix

| Concern | Evidence | Status |
|---|---|---|
| **Q1: Which column is the recharge WRITE source?** | `WalletController.doRecharge` (line 124-162) updates BOTH `crm_wallet.balance` (via CAS) and `crm_member.balance` (line 151: `m.setBalance(before.add(add)); memberService.updateById(m);`). | **DUAL WRITE** to both tables. |
| **Q2: Which column is the POS wallet consume READ source?** | `PosController.java` line 290: `m.getBalance()` — reads from `crm_member`, NOT `crm_wallet`. | **`crm_member.balance` is the runtime READ source.** |
| **Q3: Which column is the refund WRITE source?** | `WalletController.doRefund` (line 211-264) and `PosController` line 390-398 — both update `crm_member.balance`. | **DUAL WRITE** (mirror). |
| **Q4: Which balance does the transaction table use?** | `crm_wallet_transaction.balance_before` and `balance_after` are written from Java variables that mirror whichever balance was just changed. Both controllers use `member.getBalance()` as the source. | **Mirrors `crm_member.balance`** for the POS path; mirrors `crm_wallet.balance` (CAS result) for the direct `WalletController` path. |
| **Q5: Are there balance differences?** | **YES** — see Q6. | Confirmed. |
| **Q6: Difference count?** | **29 wallets** (21 in NULL-tenant group + 8 in OK group). Out of 108 total wallets. | 26.8% drift rate. |
| **Q7: Difference amount?** | `SUM(wallet.balance - member.balance)` = **-32,152.00** (member.total is 32,152 less than wallet.total). | Mostly due to negative seed balances in `seed_member.sql` line 30. |

### 6.2 Detailed Mismatches (sample)

| wallet_id | member_id | wallet_bal | member_bal | diff | Likely cause |
|---:|---:|---:|---:|---:|---|
| 1 | 1 | -298.00 | 7700.00 | -7998.00 | Seed anomaly (negative wallet) |
| 2 | 2 | -198.00 | 2200.00 | -2398.00 | Seed anomaly |
| 3 | 3 | -388.00 | 500.00 | -888.00 | Seed anomaly |
| 4 | 4 | 3300.00 | 12800.00 | -9500.00 | Seed anomaly (member.total huge) |
| 5 | 5 | 5500.00 | 3500.00 | 2000.00 | Wallet includes gift, member doesn't |
| 6 | 6 | 2200.00 | 15600.00 | -13400.00 | Seed anomaly |
| 7 | 7 | 1000.00 | 800.00 | 200.00 | Wallet created via ensureWallet, member balance lagged |
| 8 | 8 | -168.00 | 4500.00 | -4668.00 | Seed anomaly |
| 9 | 9004 | 0.00 | 4000.00 | -4000.00 | Wallet created via ensureWallet, never written |
| 19-49 | (NULL tenant group) | 0.00 or 500.00 | various | various | Wallets created via `ensureWallet()` (`insertIfAbsent`) without balance sync |

### 6.3 Conclusion: Dual-Track Reality

- **Runtime read source** = `crm_member.balance` (used by PosController).
- **Audit/source of truth for analytics** = unclear; code keeps both updated, but drift exists.
- **`crm_wallet` is a redundant mirror**, not the canonical truth.
- **Root cause of drift**: `WalletMapper.xml:insertIfAbsent` does not populate balance; `ensureWallet` then returns the freshly-inserted wallet (balance=0) but `crm_member.balance` retains its old value.

**Out of scope for P0-DB-001** (data model consolidation). Recorded for `P1-DB-*` follow-up.

---

## 7. sys_login_log Decision

### 7.1 Architecture (verified)

| Layer | File | Exists? |
|---|---|---|
| Entity | `entity/SysLoginLog.java` | ✅ |
| Mapper | `mapper/SysLoginLogMapper.java` | ✅ (BaseMapper only) |
| Service | `service/SysLoginLogService.java` | ✅ |
| ServiceImpl | `service/impl/SysLoginLogServiceImpl.java` | ✅ (has `asyncRecord`, `page`, `clearBeforeDays`, `search`...) |
| Controller | `controller/SysLoginLogController.java` | ✅ (uses `QueryWrapper`, NOT JdbcTemplate) |
| Insert caller | `controller/AuthController.java` (line 32, 47, 65, 81) | ✅ |
| DDL source | `fix_missing_tables.sql:37-52` | ✅ |
| ALTER script | `init_rbac_v2_loginlog.sql:4-7` (depends on table pre-existing) | ✅ |

### 7.2 Architecture Reality (Phase 1 was wrong)

Phase 1 said "Controller uses JdbcTemplate". **Reality**: Controller uses `QueryWrapper<SysLoginLog>` for `page`, `search`. The raw SQL appears only in the **CSV export** path (`SysLoginLogController.java:97` `URLEncoder.encode("sys_login_log_" + ...)` followed by raw `service.list(...)` then CSV writer).

### 7.3 Init Module Placement

`sys_login_log` is conceptually **RBAC/auth-related** (tracks login attempts). The natural init module is **`init_m01_auth.sql`** (currently has `sys_user`, `sys_role`, `sys_permission`, `sys_user_role`, `sys_role_permission`).

**Decision**: Back-port the `sys_login_log` CREATE TABLE into `init_m01_auth.sql` (or as a new section after `sys_role_permission`).

### 7.4 FK Decisions (re-stated from §3)

- **FK-16** `sys_login_log.user_id → sys_user.id`: **P1_FK_RECOMMENDED** (nullable).
- **FK-17** `sys_login_log.tenant_id → sys_tenant.id`: **P1_FK_RECOMMENDED** (nullable).

---

## 8. sys_log Decision

### 8.1 Architecture (verified)

| Layer | File | Exists? |
|---|---|---|
| Entity | `entity/SysLog.java` | ✅ (NO tenantId field!) |
| Mapper | `mapper/SysLogMapper.java` | ✅ |
| Service | `service/SysLogService.java` | ✅ |
| ServiceImpl | `service/impl/SysLogServiceImpl.java` | ✅ |
| Controller | `controller/SysLogController.java` | ✅ |
| Insert caller | `log/LogAspect.java:54-65` (`saveAsync`) | ✅ |
| DDL source | `fix_missing_tables.sql:10-32` | ✅ |

### 8.2 Entity vs DB Drift

The `SysLog` Entity does NOT declare `tenantId`. So:
- **Entity cannot read `sys_log.tenant_id`** (column exists in DB but not in Java fields).
- **`LogAspect.buildLog()` never sets `tenant_id`**.
- **All 1089 rows have `tenant_id=NULL`**.

**Fix requires Java change** (add `tenantId` field + populate in `LogAspect`). **OUT OF SCOPE for P0-DB-001** (which is DB-only). Recorded for `P0-DB-002` follow-up.

### 8.3 Init Module Placement

`sys_log` is **system infrastructure** (cross-cutting logging). It is **NOT** part of `init_m01_auth` (RBAC) — it would better fit either:
- A new `init_m00_infra.sql` (not yet existing)
- Or appended to `init_m02_basic.sql` (which already has `pos_store`, `pos_employee`, `mem_level`, `sys_category`)

**Decision**: Append to `init_m02_basic.sql` (or create `init_m00_infra.sql`). Since `init_m00_*` doesn't exist, appending to `init_m02_basic.sql` is the lowest-friction path.

### 8.4 FK for sys_log

- **`sys_log.user_id → sys_user.id`**: 0 orphans. **P1_FK_RECOMMENDED** (nullable).
- **`sys_log.tenant_id → sys_tenant.id`**: **BLOCKED by Entity drift** — Entity doesn't populate the column. Must fix Java first.

**Out of scope for P0-DB-001**. Recorded.

---

## 9. Fresh DB Initialization Gap

### 9.1 Standard init sequence

`init_m01_auth.sql` → `init_m02_basic.sql` → `init_m03_member.sql` → `init_m04_*.sql` (does not exist) → `init_m05_package.sql` → `init_m06_booking.sql` → `init_m07_pos.sql` → …

### 9.2 Tables that init_m0X series will create vs miss

| Module script | Creates | MISSING (per Phase 1+2 audit) |
|---|---|---|
| `init_m01_auth.sql` | sys_user, sys_role, sys_permission, sys_user_role, sys_role_permission | **sys_login_log** |
| `init_m02_basic.sql` | pos_store, pos_employee, mem_level, sys_category | **sys_log** |
| `init_m03_member.sql` | crm_member, crm_member_tag, crm_member_profile, … | **crm_wallet, crm_wallet_transaction** |
| `init_m04_*` | (does not exist) | — |
| `init_m05_package.sql` | crm_package, crm_package_order, crm_package_redemption | (not audited in P0-DB-001) |
| `init_m06_booking.sql` | (only DELETE/INSERT for biz_*) | **biz_booking, biz_room, biz_employee_schedule** |

**Net result**: A developer running `init_m01-06` on a fresh DB will end up with **7 missing tables**: `sys_login_log`, `sys_log`, `crm_wallet`, `crm_wallet_transaction`, `biz_booking`, `biz_room`, `biz_employee_schedule`.

### 9.3 Can fresh DB initialization succeed?

**NO.** A fresh DB following `init_m01` → `init_m06` will crash on:
- `/auth/login` (or anywhere auth is exercised) → `Table 'meiyetec.sys_login_log' doesn't exist` (when `init_rbac_v2_loginlog.sql` runs).
- `/booking/calendar` → `Table 'meiyetec.biz_booking' doesn't exist`.
- `/wallet/{id}` → `Table 'meiyetec.crm_wallet' doesn't exist`.

**The current init chain is broken.**

---

## 10. Existing DB Migration Gap

### 10.1 Current State (verified)

The current DB has all 7 missing tables, applied via `fix_missing_tables.sql` + `fix_booking_calendar_tables.sql`. Plus, there are patches:
- `patch_r2_0_a_tenant_id.sql` (added `tenant_id` to `sys_user`, `crm_member`, `mem_level`).
- `init_rbac_v2_loginlog.sql` (added `tenant_id` to `sys_login_log`).

### 10.2 Existing DB Cannot Simply Re-run init_*

A fresh `init_m01_auth.sql` does `DROP TABLE IF EXISTS sys_user; CREATE TABLE sys_user (...);`. If we re-run on the existing DB, we **lose** all existing `sys_user` data.

**Therefore**: For existing DB, must use `CREATE TABLE IF NOT EXISTS` or move CREATE TABLE blocks to separate migrations.

### 10.3 The Dual-Track Problem

- **Fresh DB**: needs full DDL chain.
- **Existing DB**: tables already exist; `CREATE TABLE IF NOT EXISTS` is needed; FK additions are idempotent.

A unified approach must handle both.

---

## 11. DDL Normalization Options

### 11.1 Option A: New Consolidated `init_db_p0_drift_v1.sql`

Create a single new SQL script:
```
backend/src/main/resources/db/sql/init_db_p0_drift_v1.sql
```

Contents:
1. `CREATE TABLE IF NOT EXISTS sys_login_log ...` (mirrors fix_missing_tables.sql:37-52)
2. `CREATE TABLE IF NOT EXISTS sys_log ...` (mirrors fix_missing_tables.sql:10-32)
3. `CREATE TABLE IF NOT EXISTS crm_wallet ...` (mirrors fix_missing_tables.sql:57-70)
4. `CREATE TABLE IF NOT EXISTS crm_wallet_transaction ...` (mirrors fix_missing_tables.sql:75-95)
5. `CREATE TABLE IF NOT EXISTS biz_booking ...` (mirrors fix_booking_calendar_tables.sql:10-43)
6. `CREATE TABLE IF NOT EXISTS biz_room ...` (mirrors fix_booking_calendar_tables.sql:72-90)
7. `CREATE TABLE IF NOT EXISTS biz_employee_schedule ...` (mirrors fix_booking_calendar_tables.sql:48-67)
8. `ALTER TABLE crm_wallet ADD CONSTRAINT fk_crm_wallet_member ... FOREIGN KEY (member_id) REFERENCES crm_member(id);` etc. for the 17 FKs (after precheck).
9. Precheck queries that abort if orphans exist.
10. Data remediation (NULL tenant_id cleanup, FK-5/FK-10 orphan reassignment).

| Dimension | Rating |
|---|---|
| Fresh DB support | ✅ Yes (idempotent CREATE TABLE IF NOT EXISTS) |
| Existing DB support | ✅ Yes (idempotent) |
| Number of new files | 1 |
| Migration complexity | Low |
| Risk | Low (idempotent, precheck-protected) |
| Adoption friction | Medium (operators must remember to run this in addition to init_m0X) |

### 11.2 Option B: Back-port into Existing init_*.sql

Modify:
- `init_m01_auth.sql`: add `CREATE TABLE IF NOT EXISTS sys_login_log`.
- `init_m02_basic.sql`: add `CREATE TABLE IF NOT EXISTS sys_log`.
- `init_m03_member.sql`: add `CREATE TABLE IF NOT EXISTS crm_wallet, crm_wallet_transaction`.
- `init_m06_booking.sql`: add `CREATE TABLE IF NOT EXISTS biz_booking, biz_room, biz_employee_schedule`.
- Add FKs at the end of each.

| Dimension | Rating |
|---|---|
| Fresh DB support | ✅ Yes (natural, in-place) |
| Existing DB support | ⚠️ Risk of DDL re-run (DROP IF EXISTS would destroy data; ALTER FK would fail if constraint exists) |
| Number of files touched | 4-5 |
| Migration complexity | High (must ensure `CREATE TABLE IF NOT EXISTS` and `IF NOT EXISTS` for FKs) |
| Risk | Higher (each script gets bigger, harder to reason about) |
| Adoption friction | Low (no new file to remember) |

### 11.3 Option C: Only Add FK Migration (no DDL normalization)

Keep `fix_*.sql` as the source of truth. Add only a new migration script:
```
migrations/V001__p0_db_001_fk_enforcement.sql
```

Contents:
1. Precheck: abort if orphans > 0.
2. NULL tenant_id cleanup for `crm_wallet`.
3. Reassign FK-5 / FK-10 orphans to admin user.
4. Add all 17 FKs (idempotent via `IF NOT EXISTS`).

| Dimension | Rating |
|---|---|
| Fresh DB support | ❌ NO — tables still missing |
| Existing DB support | ✅ Yes |
| Number of new files | 1 |
| Migration complexity | Low |
| Risk | Low (FK additions are guarded) |
| Adoption friction | Low (FK-only) |

### 11.4 Option Comparison

| Option | Fresh DB | Existing DB | Files | Complexity | Risk | Adopt |
|---|---|---|---|---|---|---|
| **A** (consolidated v1) | ✅ | ✅ | 1 | Low | Low | ✅ |
| **B** (back-port) | ✅ | ⚠️ | 4-5 | High | Medium | ⚠️ |
| **C** (FK-only) | ❌ | ✅ | 1 | Low | Low | ❌ (fresh DB still broken) |

---

## 12. Recommended Architecture

**Recommendation**: Adopt **Canonical Init + Migration** (closest to Option A):

### 12.1 Architecture (Canonical Init + Migration)

| File | Purpose | Status |
|---|---|---|
| `init_m01_auth.sql` | Add `CREATE TABLE IF NOT EXISTS sys_login_log` after `sys_role_permission`. | NEW |
| `init_m02_basic.sql` | Add `CREATE TABLE IF NOT EXISTS sys_log` after `sys_category`. | NEW |
| `init_m03_member.sql` | Add `CREATE TABLE IF NOT EXISTS crm_wallet, crm_wallet_transaction` after `crm_member`. | NEW |
| `init_m06_booking.sql` | Replace `DELETE FROM biz_*` lines with `CREATE TABLE IF NOT EXISTS biz_booking, biz_room, biz_employee_schedule` before DELETEs. | MODIFIED |
| `fix_missing_tables.sql` | KEEP AS-IS (historical artifact). | UNCHANGED |
| `fix_booking_calendar_tables.sql` | KEEP AS-IS (historical artifact). | UNCHANGED |
| `migrations/V001__p0_db_001_repair.sql` (new) | All FK additions, NULL cleanup, orphan remediation. | NEW |

### 12.2 Rationale

- **`init_m0X` series**: Back-ports the DDL into the **canonical migration chain** for fresh DB. Uses `IF NOT EXISTS` for idempotency.
- **`migrations/V001`**: Handles **existing-DB-only** concerns (data remediation + FK enforcement). Idempotent.
- **`fix_*.sql`**: **Kept as historical artifact** — do NOT delete (some environments may need them; we don't want to break anyone).

### 12.3 Trade-offs

- **Fresh DB**: Now fully initialized by `init_m01-06`.
- **Existing DB**: Run `migrations/V001` (adds FKs, cleans NULL).
- **Both DBs**: Have all 7 missing tables, all 17 FKs (where data permits), and no NULL tenant_id in `crm_wallet`.

---

## 13. Migration Sequence (Phase 3 Plan, NOT executed yet)

### 13.1 Order of Operations

```
PRECHECK
  ↓
1. Verify all parent tables exist (sys_tenant, crm_member, etc.)
  ↓
2. Verify 0 orphans for FK-1, FK-3, FK-4, FK-7, FK-8, FK-9, FK-12, FK-14, FK-16, FK-17
  ↓
3. Remediate orphans:
   3a. Reassign FK-5 orphans (4 rows, operator_id=7 → sys_user.id=1)
   3b. Reassign FK-10 orphans (5 rows, operator_id=7 → sys_user.id=1)
  ↓
4. Derive NULL tenant_id in crm_wallet from crm_member (50 rows)
  ↓
5. ALTER crm_wallet ADD CONSTRAINT FK-1 (member_id → crm_member.id)
  ↓
6. ALTER crm_wallet_transaction ADD CONSTRAINT FK-3 (member_id → crm_member.id)
  ↓
7. ALTER crm_wallet_transaction ADD CONSTRAINT FK-4 (order_id → pos_order.id, nullable)
  ↓
8. ALTER crm_wallet_transaction ADD CONSTRAINT FK-5 (operator_id → sys_user.id, nullable)
  ↓
9. ALTER biz_booking ADD CONSTRAINT FK-7 (member_id → crm_member.id)
  ↓
10. ALTER biz_booking ADD CONSTRAINT FK-8 (employee_id → pos_employee.id)
  ↓
11. ALTER biz_booking ADD CONSTRAINT FK-9 (room_id → biz_room.id, nullable)
  ↓
12. ALTER biz_booking ADD CONSTRAINT FK-10 (operator_id → sys_user.id, nullable)
  ↓
13. ALTER biz_room ADD CONSTRAINT FK-12 (store_id → pos_store.id, nullable)
  ↓
14. ALTER biz_employee_schedule ADD CONSTRAINT FK-14 (employee_id → pos_employee.id)
  ↓
15. ALTER sys_login_log ADD CONSTRAINT FK-16 (user_id → sys_user.id, nullable)
  ↓
16. ALTER crm_wallet ADD CONSTRAINT FK-2 (tenant_id → sys_tenant.id, nullable)
  ↓
17. ... (FK-6, FK-11, FK-13, FK-15, FK-17 for tenant_id)
  ↓
POSTCHECK
  ↓
18. Re-verify orphan count = 0 for all FKs
  ↓
19. Re-verify FK count via information_schema (expected: 17)
  ↓
20. Smoke test: SELECT from each child table joined to parent (should return rows)
```

### 13.2 Idempotency

Each step uses `IF NOT EXISTS` patterns:
- `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS ...` — MySQL 8.0+ supports this.
- For MySQL 5.7: Use `@constraint_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE ...)` pattern.

---

## 14. Data Remediation Plan

### 14.1 Precheck Queries

```sql
-- Precheck: any orphan in FK-1?
SELECT COUNT(*) FROM crm_wallet w WHERE w.member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id);
-- Expected: 0

-- Precheck: any orphan in FK-5?
SELECT COUNT(*) FROM crm_wallet_transaction w WHERE w.operator_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.operator_id);
-- Expected: 4 (BLOCKED)

-- Precheck: any NULL tenant in crm_wallet?
SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL;
-- Expected: 50 (BLOCKED for FK-2)
```

### 14.2 Remediation Scripts (Proposed, NOT executed)

```sql
-- Reassign FK-5 orphans
UPDATE crm_wallet_transaction w
SET w.operator_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1)
WHERE w.operator_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.operator_id);

-- Reassign FK-10 orphans
UPDATE biz_booking b
SET b.operator_id = (SELECT id FROM sys_user WHERE username = 'admin' LIMIT 1)
WHERE b.operator_id IS NOT NULL AND b.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id);

-- Derive crm_wallet.tenant_id from crm_member
UPDATE crm_wallet w
INNER JOIN crm_member m ON m.id = w.member_id
SET w.tenant_id = m.tenant_id
WHERE w.tenant_id IS NULL AND m.tenant_id IS NOT NULL;
```

### 14.3 Postcheck Queries

```sql
-- Verify orphan counts now 0
SELECT 'FK-1', COUNT(*) FROM crm_wallet w WHERE w.member_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM crm_member m WHERE m.id = w.member_id)
UNION ALL
SELECT 'FK-5', COUNT(*) FROM crm_wallet_transaction w WHERE w.operator_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = w.operator_id)
UNION ALL
SELECT 'FK-10', COUNT(*) FROM biz_booking b WHERE b.operator_id IS NOT NULL AND b.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.id = b.operator_id);

-- Verify NULL tenants now 0
SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL;
-- Expected: 0
```

---

## 15. Rollback Plan

Each Phase 3 step must be reversible:

| Step | Rollback |
|---|---|
| Orphan reassignment | `UPDATE ... SET operator_id = 7 WHERE id IN (...);` (revert to old values, requires pre-snapshot) |
| NULL tenant derivation | `UPDATE crm_wallet SET tenant_id = NULL WHERE id IN (...);` (requires pre-snapshot) |
| FK addition | `ALTER TABLE ... DROP FOREIGN KEY ...` |
| DDL normalization | N/A (CREATE TABLE IF NOT EXISTS is a no-op on existing DB) |

**Mandatory**: Before Phase 3, snapshot the DB:
```bash
mysqldump -h... -P... -u... -p... meiyetec > backup_p0_db_001_phase3.sql
```

---

## 16. P0 / P1 Task Breakdown

### 16.1 P0 Tasks (Block Production)

| ID | Task | Severity | Phase 3 Status |
|---|---|---|---|
| P0-DB-001-A | Reassign FK-5 orphans (4 wallet_txn) | P0 | PROPOSED (not yet executed) |
| P0-DB-001-B | Reassign FK-10 orphans (5 biz_booking) | P0 | PROPOSED |
| P0-DB-001-C | Derive NULL tenant_id in crm_wallet (50 rows) | P0 | PROPOSED |
| P0-DB-001-D | Back-port DDL into `init_m01_auth.sql` (sys_login_log) | P0 | PROPOSED |
| P0-DB-001-E | Back-port DDL into `init_m02_basic.sql` (sys_log) | P0 | PROPOSED |
| P0-DB-001-F | Back-port DDL into `init_m03_member.sql` (crm_wallet, crm_wallet_transaction) | P0 | PROPOSED |
| P0-DB-001-G | Back-port DDL into `init_m06_booking.sql` (biz_booking, biz_room, biz_employee_schedule) | P0 | PROPOSED |
| P0-DB-001-H | Add 9 P0_FK_REQUIRED constraints (FK-1, 3, 4, 7, 8, 12, 14) | P0 | PROPOSED |

### 16.2 P1 Tasks (Defer to P0-DB-002 or later)

| ID | Task | Severity |
|---|---|---|
| P1-DB-001-I | Add 6 P1_FK_RECOMMENDED constraints (FK-2, 6, 9, 11, 13, 15, 16, 17) | P1 |
| P1-DB-001-J | Consolidate redundant `crm_member.balance` + `crm_wallet.balance` | P1 |
| P1-DB-001-K | Fix `WalletMapper.xml:insertIfAbsent` missing `tenant_id` | P1 |
| P1-DB-001-L | Add UNIQUE constraint on `biz_booking.booking_no` | P1 |
| P1-DB-001-M | Add UNIQUE on `(biz_room.store_id, biz_room.room_name)` | P1 |
| P1-DB-001-N | Add UNIQUE on `(biz_employee_schedule.employee_id, work_date)` | P1 |
| P1-DB-001-O | Add `@Version` annotation to `Wallet.version` field | P1 |
| P1-DB-001-P | Add `tenantId` field to `SysLog` Entity + populate in `LogAspect` | P1 |

---

## 17. Approval Gate

### 17.1 Phase 2 Final Answers

| # | Question | Answer |
|---|---|---|
| 1 | How many P0 FKs? | **9** (FK-1, 3, 4, 7, 8, 12, 14 — strictly required) |
| 2 | How many P1 FKs? | **6** (FK-2, 6, 9, 11, 13, 15-17 — recommended after data fix) |
| 3 | How many NO FKs? | **0** |
| 4 | How many BLOCKED_BY_DATA? | **2** (FK-5, FK-10 — both pending orphan remediation) |
| 5 | Can the 50 NULL tenant_id rows in crm_wallet be safely derived? | **YES** — 50/50 reference valid `crm_member` rows with `tenant_id=1`. 0 cross-tenant contamination. |
| 6 | Does wallet/member balance have differences? | **YES** — 29/108 wallets drift; total drift = -32,152.00. **Out of scope for P0-DB-001.** |
| 7 | Which init module for `sys_login_log`? | **`init_m01_auth.sql`** (RBAC/auth-related). |
| 8 | Which init module for `sys_log`? | **`init_m02_basic.sql`** (system infrastructure). |
| 9 | Can fresh DB fully initialize? | **NO** — current `init_m01-06` chain is broken; needs back-port. |
| 10 | Recommend A, B, C, or Canonical Init + Migration? | **Canonical Init + Migration** (Option A's intent, Option B's ergonomics). |
| 11 | Phase 3 minimum safe repair tasks? | A → C → D-G → H. (See §16.1.) |

### 17.2 Phase 2 Boundary Respected

✅ Zero modifications to:
- Java code
- Existing SQL files (no `init_*.sql` or `fix_*.sql` touched)
- Database (only `SELECT` / `SHOW CREATE TABLE` / `SHOW INDEX` / `information_schema` queries)
- Configuration

✅ Used only:
- `SHOW CREATE TABLE` / `SHOW INDEX`
- `SELECT` queries against `meiyetec` and `information_schema`
- `grep` / static code analysis
- Markdown report generation

---

## 18. Final Status

```
AUDIT COMPLETE
REPAIR PLAN READY
AWAITING APPROVAL
```

The Phase 2 plan is ready for user review. Pending decisions:

1. **Approve Canonical Init + Migration architecture** (back-port DDL into `init_m0X` + new `migrations/V001` for data remediation + FK additions)?
2. **Approve orphan reassignment strategy** (reassign operator_id=7 → admin via `sys_user.username='admin'`)?
3. **Approve NULL tenant_id derivation strategy** (set `crm_wallet.tenant_id = crm_member.tenant_id` for the 50 NULL rows)?
4. **Approve deferring P1 tasks** (wallet/member balance consolidation, FK-2 etc, UNIQUE constraints, Java fixes) to `P0-DB-002`?

Awaiting explicit user go-ahead before Phase 3 (DDL apply).