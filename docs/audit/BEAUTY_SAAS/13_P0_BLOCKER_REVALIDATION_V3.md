# 13_P0_BLOCKER_REVALIDATION_V3

> **基线建立日期**: 2026-08-13
> **审计阶段**: Reality Repair Planning V3
> **目的**: 对 V2 阶段报告的 6 个 P0 Blocker 进行逐项重新核验，每个结论必须有代码 + DDL + 运行时证据支撑

---

## 0. 审计方法论

每张"声称缺失"的表都经过以下五层验证：

| 层级 | 验证项 | 通过标志 |
|------|--------|----------|
| L1 | Entity 存在 | `@TableName("xxx")` 在 `entity/*.java` |
| L2 | Mapper 存在 | `mapper/XxxMapper.java` |
| L3 | Service 接口 + (Impl 或 Controller 调用) | `service/XxxService.java` |
| L4 | DDL 存在 | `db/sql/*.sql` 中出现 `CREATE TABLE xxx` |
| L5 | 运行时数据库存在 | backend 日志、表查询、seed 文件验证 |

**状态分类**：
- `REAL` — 五层全部通过
- `SCHEMA_DRIFT` — L1-L3 存在但 L4 缺失（DDL 未纳入版本化迁移）
- `DEPLOYMENT_DRIFT` — L1-L4 存在但 L5 缺失（DDL 已声明但未部署到运行时）
- `MISSING` — L1-L4 任意一层缺失
- `MANUAL_FIX` — DDL 缺失但有 ad-hoc root 文件 (`fix_*.sql`) 修复运行时

---

## 1. P0-001-A: `crm_wallet`（会员钱包主表）

### 1.1 五层核验

| Layer | Item | Path | Evidence | Pass |
|-------|------|------|----------|------|
| L1 | Entity | `entity/Wallet.java` | `@TableName("crm_wallet")` + `memberId, balance, version` 等字段 | ✅ |
| L2 | Mapper | `mapper/WalletMapper.java` | `updateBalance()` 方法已定义（被 WalletController:272 调用） | ✅ |
| L3 | Service | `service/WalletService.java` + `controller/WalletController.java:115-264`（doRecharge/doConsume/doRefund 完整事务方法） | 真实业务下沉到 Controller | ✅ |
| L4 | DDL | `db/sql/*.sql` 全量扫描 | ❌ **0 条匹配** `CREATE TABLE crm_wallet` | ❌ |
| L5 | Runtime | `backend-current.log` 报错 `Table 'meiyetec.crm_wallet' doesn't exist`（曾发生） | 历史缺失 | ⚠️ |

### 1.2 漂移判定

| 漂移类型 | 是否触发 | 证据 |
|----------|----------|------|
| SCHEMA_DRIFT | **是** | `init_rbac_v2_b.sql:34` 假设存在 `ALTER TABLE crm_wallet`，但无人创建主表 |
| DEPLOYMENT_DRIFT | 否 | 后期通过 `fix_missing_tables.sql`（项目根目录）已创建到运行时 |
| MANUAL_FIX | **是** | `fix_missing_tables.out` 验证 `Tables_in_meiyetec (crm_wallet) → crm_wallet` 已成功执行 |

### 1.3 风险

| 维度 | 评估 |
|------|------|
| **风险等级** | **P0** |
| **影响面** | 钱包、订单消费、退款、充值全链路 |
| **漂移后果** | 新租户/新部署实例重新运行迁移脚本时必报 `Table 'meiyetec.crm_wallet' doesn't exist` |
| **测试覆盖** | 已有 `PosRefundIntegrationTest.refundWalletReturn()` 验证钱包余额回升（隐含 L5 存在） |

### 1.4 最终状态

> ⚠️ **SCHEMA_DRIFT + MANUAL_FIX**：当前代码 + 运行时正确，但 DDL 未纳入版本化迁移流程，新部署将失败。

---

## 2. P0-001-B: `crm_wallet_transaction`（钱包流水表）

### 2.1 五层核验

| Layer | Item | Path | Evidence | Pass |
|-------|------|------|----------|------|
| L1 | Entity | `entity/WalletTransaction.java` | `@TableName("crm_wallet_transaction")` | ✅ |
| L2 | Mapper | `mapper/WalletTransactionMapper.java` | 已存在 | ✅ |
| L3 | Service | `service/WalletTransactionService.java` + `service/impl/WalletTransactionServiceImpl.java`（13 LOC 空壳，但 Controller 直接调用） | 真实业务在 Controller | ✅ |
| L4 | DDL | 全量 SQL 扫描 | ❌ **0 条匹配** | ❌ |
| L5 | Runtime | `WalletController.java:147`（Service.save） + `PosController.java:298-301`（直接 JDBC INSERT） | 运行期表必须存在 | ⚠️ |

### 2.2 双重调用路径（DOUBLE_WRITE 风险）

```java
// WalletController.java:136-147（走 WalletTransaction Entity 持久化）
WalletTransaction txn = new WalletTransaction();
txn.setType(TYPE_RECHARGE);
...
txnService.save(txn);  // MyBatis-Plus save → 实体插入

// PosController.java:298-301（绕过 Service，直接 JDBC INSERT）
"INSERT INTO crm_wallet_transaction(...) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
1L, m.getId(), 2, amount, before, after, "wallet", order.getId(), order.getCashierId(),
"订单消费:" + order.getOrderNo(), LocalDateTime.now();
```

**字段对比**：
- Entity `WalletTransaction`：`tenantId, memberId, type, amount, balanceBefore, balanceAfter, giftAmount, payMethod, orderId, operatorId, remark` + 时间字段
- PosController INSERT 字段：`tenant_id, member_id, type, amount, balance_before, balance_after, pay_method, order_id, operator_id, remark, created_at`

差异：PosController 路径**缺 `gift_amount` 字段**，字段顺序不同；`gift_amount` 默认值将由 DDL DEFAULT 0 兜底。

### 2.3 漂移判定

| 漂移类型 | 是否触发 | 证据 |
|----------|----------|------|
| SCHEMA_DRIFT | **是** | 同 P0-001-A，DDL 未纳入版本化 |
| DEPLOYMENT_DRIFT | 否 | 已由 `fix_missing_tables.sql` 补齐 |
| DOUBLE_WRITE_RISK | **是** | Controller JDBC 与 Service Entity 双轨 |

### 2.4 最终状态

> ⚠️ **SCHEMA_DRIFT + DOUBLE_WRITE**：表实际可用，但 DDL 不在迁移脚本内，且业务层有两套插入路径，存在数据漂移风险。

---

## 3. P0-001-C: `biz_booking`（预约表）

### 3.1 五层核验

| Layer | Item | Path | Evidence | Pass |
|-------|------|------|----------|------|
| L1 | Entity | `entity/Booking.java:13` | `@TableName("biz_booking")` | ✅ |
| L2 | Mapper | `mapper/BookingMapper.java` | 已存在 | ✅ |
| L3 | Service | `service/BookingService.java` + `impl/BookingServiceImpl.java`（**11 LOC 空壳** `extends ServiceImpl<BookingMapper, Booking>`） | 业务下沉到 `BookingController.java` | 🟠 SHELL |
| L4 | DDL | 全量 SQL 扫描 `CREATE TABLE.*biz_booking\|CREATE TABLE.*booking\b` | ❌ **0 条匹配** | ❌ |
| L5 | Runtime | `backend-current.log` 历史 500 错误：`/booking/calendar` 因表不存在 | 已由 ad-hoc 修复 | ⚠️ |

### 3.2 现有预约相关 DDL

`init_rbac_v2_1_b.sql` 已创建 `booking_resource`, `booking_resource_link`, `booking_booking_resource`, `booking_waitlist`, `booking_waitlist_notify`, `booking_no_show_rule`（6 张），但**主表 `biz_booking` 缺失**。

### 3.3 漂移判定

| 漂移类型 | 是否触发 | 证据 |
|----------|----------|------|
| SCHEMA_DRIFT | **是** | 整个预约主表未纳入迁移 |
| MANUAL_FIX | **是** | `fix_booking_calendar_tables.sql:11` 创建 `biz_booking`（项目根目录，非 `db/sql/`） |
| SHELL_SERVICE | **是** | BookingServiceImpl 完全空壳 |

### 3.4 业务证据

`BookingController.java` 实现完整业务流：
- 创建预约（含员工冲突检查 line 67-77、房间冲突检查 line 79-90）
- 列表（line 100）
- 确认/签到/完成/取消（隐含后续行）
- 排班查询 / 房间查询
- Excel 导出

### 3.5 最终状态

> ⚠️ **SCHEMA_DRIFT + SHELL_SERVICE**：Controller 含 346 LOC 真实业务逻辑，Service 空壳。运行时已通过 ad-hoc 脚本修复，但 DDL 不在版本化迁移内。

---

## 4. P0-001-D: `biz_room`（房间表）

### 4.1 五层核验

| Layer | Item | Path | Evidence | Pass |
|-------|------|------|----------|------|
| L1 | Entity | `entity/Room.java` | `@TableName("biz_room")` | ✅ |
| L2 | Mapper | `mapper/RoomMapper.java` | 已存在 | ✅ |
| L3 | Service | `service/RoomService.java` + `impl/RoomServiceImpl.java`（11 LOC 空壳）| 业务下沉到 `BookingController` | 🟠 SHELL |
| L4 | DDL | 全量 SQL 扫描 `CREATE TABLE.*biz_room\|CREATE TABLE.*room\b` | ❌ **0 条匹配** | ❌ |
| L5 | Runtime | `fix_booking_calendar_tables.out` 显示 `SHOW TABLES LIKE 'biz_room' → biz_room` | 已创建 | ✅ |

### 4.2 漂移判定

| 漂移类型 | 是否触发 |
|----------|----------|
| SCHEMA_DRIFT | **是** |
| MANUAL_FIX | **是** |

### 4.3 最终状态

> ⚠️ **SCHEMA_DRIFT**：运行时可用，迁移缺失。

---

## 5. P0-001-E: `biz_employee_schedule`（技师排班表）

### 5.1 五层核验

| Layer | Item | Path | Evidence | Pass |
|-------|------|------|----------|------|
| L1 | Entity | `entity/EmployeeSchedule.java` | `@TableName("biz_employee_schedule")` | ✅ |
| L2 | Mapper | `mapper/EmployeeScheduleMapper.java` | 已存在 | ✅ |
| L3 | Service | `service/EmployeeScheduleService.java` + `impl/EmployeeScheduleServiceImpl.java`（11 LOC 空壳）| 业务下沉到 `BookingController` / `ScheduleController` | 🟠 SHELL |
| L4 | DDL | 全量 SQL 扫描 `CREATE TABLE.*biz_employee_schedule\|CREATE TABLE.*employee_schedule\b` | ❌ **0 条匹配** | ❌ |
| L5 | Runtime | `fix_booking_calendar_tables.out` 显示 `SHOW TABLES LIKE 'biz_employee_schedule' → biz_employee_schedule` | 已创建 | ✅ |

### 5.2 漂移判定

| 漂移类型 | 是否触发 |
|----------|----------|
| SCHEMA_DRIFT | **是** |
| MANUAL_FIX | **是** |

### 5.3 最终状态

> ⚠️ **SCHEMA_DRIFT**：运行时可用，迁移缺失。

---

## 6. P0-001-F: `crm_package_item`（套餐项目表）

### 6.1 关键发现：表名歧义

V2 报告称 `PackageItem Entity 存在但无 DDL`。**重新核验发现 Entity `@TableName("crm_package")`**，并非 `crm_package_item`：

```java
// entity/PackageItem.java:9
@TableName("crm_package")
```

### 6.2 五层核验（实际目标表 `crm_package`）

| Layer | Item | Path | Evidence | Pass |
|-------|------|------|----------|------|
| L1 | Entity | `entity/PackageItem.java` | `@TableName("crm_package")` | ✅ |
| L2 | Mapper | `mapper/PackageItemMapper.java` | 已存在 | ✅ |
| L3 | Service | `service/PackageItemService.java` + `impl/PackageItemServiceImpl.java`（11 LOC 空壳）| 业务下沉到 `PackageController` | 🟠 SHELL |
| L4 | DDL | `init_m05_package.sql:12` `CREATE TABLE crm_package` | ✅ | ✅ |
| L5 | Runtime | 已随 `init_m05_package.sql` 部署 | ✅ | ✅ |

### 6.3 状态判定

> ✅ **REAL**：V2 报告误判。`PackageItem` 实际映射到 `crm_package`，DDL 已存在。**无 P0 风险**。

---

## 7. P0-001 总结表

| # | Table | Entity | Mapper | Service | DDL | Runtime DB | Status | Evidence |
|---|-------|--------|--------|---------|-----|------------|--------|----------|
| A | `crm_wallet` | ✅ Wallet | ✅ WalletMapper | ✅ WalletService | ❌ | ✅ (manual) | **SCHEMA_DRIFT** | L4 缺失；L5 靠 ad-hoc 脚本 |
| B | `crm_wallet_transaction` | ✅ WalletTransaction | ✅ | ✅ | ❌ | ✅ (manual) | **SCHEMA_DRIFT** | 同上 + 双写 |
| C | `biz_booking` | ✅ Booking | ✅ | 🟠 SHELL | ❌ | ✅ (manual) | **SCHEMA_DRIFT** | BookingServiceImpl 11 LOC |
| D | `biz_room` | ✅ Room | ✅ | 🟠 SHELL | ❌ | ✅ (manual) | **SCHEMA_DRIFT** | RoomServiceImpl 11 LOC |
| E | `biz_employee_schedule` | ✅ EmployeeSchedule | ✅ | 🟠 SHELL | ❌ | ✅ (manual) | **SCHEMA_DRIFT** | EmployeeScheduleServiceImpl 11 LOC |
| F | `crm_package_item` | ⚠️ 误判 | — | — | — | — | **REAL（表名不同）** | 实际是 `crm_package`，DDL 存在 |

### 7.1 结论

- **V2 报告 6/6 中 5 个确实存在 SCHEMA_DRIFT**（wallet, wallet_transaction, biz_booking, biz_room, biz_employee_schedule）
- **V2 报告 1 个误判**（crm_package_item 实际是 crm_package，DDL 正常）
- **运行时 DB 已通过 ad-hoc 脚本 (`fix_missing_tables.sql` + `fix_booking_calendar_tables.sql`) 修复**，但这些脚本在项目根目录，**未被纳入 `db/sql/` 版本化迁移**，新部署将失败
- **5 个 ServiceImpl 完全空壳**（Booking/Room/EmployeeSchedule/Member/PackageItem/WalletTransaction），全部 11 LOC `extends ServiceImpl`

---

## 8. P0-001 整改优先级

| 任务 | 风险 | 工时估计 | 影响 |
|------|------|----------|------|
| 把 `fix_missing_tables.sql` 和 `fix_booking_calendar_tables.sql` 整合到 `db/sql/init_migration_v0.sql` 并加 LOAD 顺序到主入口 | P0 | 0.5d | 新部署不再崩溃 |
| BookingServiceImpl / RoomServiceImpl / EmployeeScheduleServiceImpl / MemberServiceImpl / PackageItemServiceImpl / WalletTransactionServiceImpl 业务下沉 | P1 | 3-5d | 架构统一，便于测试 |

---

**审计完成时间**: 2026-08-13 23:00
**审计员**: Reality Repair Planning V3
**文档版本**: 3.0