# P0 Final Freeze Report

**Report Scope:** P0 Multi-Tenant + Wallet + Refund Reverse Trace + Tenant Isolation Hardening
**Period:** T01-ext → T08
**Status:** **P0 → COMPLETE 🔒**
**Generated:** 2026-08-15
**Source-of-Truth:** `docs/audit/BEAUTY_SAAS/00_CLEAN_CURRENT_REALITY_BASELINE.md` + `P0_REPAIR_EXECUTION_PLAN.md`

---

## 1. Task 状态矩阵

| Task | 标题 | 范围 | 状态 | 冻结 |
|---|---|---|---|---|
| T01-ext | `pos_store` ↔ Store Entity 完整 Schema 对齐 | Schema Drift | ✅ PASS | 🔒 |
| T02 | `sys_config` 表创建 + `LogRetentionTask` 修复 | Schema + 调度 | ✅ PASS | 🔒 |
| T03 | `WalletMapper.insertIfAbsent` 写 `tenant_id` | 多租户写入链 | ✅ PASS | 🔒 |
| T04 | `crm_wallet.tenant_id` 历史 NULL 回填 | 历史数据 | ✅ PASS | 🔒 |
| T05 | `pos_refund_reverse_log.ref_id` 历史回填（含孤儿识别） | 历史数据 | ✅ PASS | 🔒 |
| T06 | `PosRefundService` 运行时 `ref_id` 精确追踪 | 运行时改正 | ✅ PASS | 🔒 |
| T07 | `tenantId=1L` 硬编码治理（14 处） | 多租户旁路 | ✅ PASS | 🔒 |
| T08 | `PosController` 租户隔离验证 | 测试 Fixture | ✅ PASS | 🔒 |

---

## 2. 每个任务目标

### T01-ext — `pos_store` Schema 对齐
**目标**：使 `pos_store` 表与 `Store.java` Entity 完整对齐，消除 /member/* 500 错误。
**完成**：补齐 9 列（`tenant_id`/`brand_id`/`store_type`/`region_code`/`is_hq`/`manager_employee_id`/`manager_name`/`open_date`/`brand_priority`） + 2 索引。

### T02 — `sys_config` 表创建
**目标**：使 `LogRetentionTask` 调度任务可以读取 `sys_config` 而不抛 `Table doesn't exist`。
**完成**：DDL 创建 `sys_config` + 初始 seed（`log.retention-days=90` / `idempotency.retention-hours=72`）。

### T03 — `WalletMapper.insertIfAbsent` 写 `tenant_id`
**目标**：钱包自动建账时写入正确的 `tenant_id`，杜绝 crm_wallet 出现 NULL。
**完成**：Entity 加 `tenantId` + Mapper XML 改写 + `resolveTenantIdForInsert()` 强制 ctx 解析 + 测试覆盖。

### T04 — `crm_wallet.tenant_id` 历史 NULL 回填
**目标**：将 29 行历史 NULL `crm_wallet.tenant_id` 回填为对应 member 的 tenant_id。
**完成**：基于 `crm_wallet.member_id → crm_member.tenant_id` 映射，0 业务数据变更，pre/post 快照，审计日志。

### T05 — `pos_refund_reverse_log.ref_id` 历史回填
**目标**：对 9 条具有完整证据链的 PACKAGE reverse_log 记录回填 `ref_id`；其余 682 条 NULL 保留。
**完成**：强证据链识别（pos_refund → pos_order → pos_order_item.package_order_id → crm_package_order.id）；孤儿/不含非回填；POINTS/STOCK/COMMISSION 不伪造。

### T06 — `PosRefundService` 运行时 `ref_id` 精确追踪
**目标**：未来所有 `logReverse()` 调用按 reverse_type 携带正确业务来源 `ref_id`。
**完成**：
- PACKAGE：每个 `refundAddTimes` 成功的 `crm_package_order.id` 产生一条日志
- STOCK：每个 insert 成功的 `stock_record.id` 产生一条日志
- POINTS：使用 `market_point_record.id`（含 `addPointsReturningId` 链路）
- COMMISSION：`invalidateOrder()` 返回 `List<Long>` 作废记录列表
- 历史 682 条 NULL 保持不动

### T07 — `tenantId=1L` 硬编码治理
**目标**：消除 14 处 `1L` 硬编码（含 DIAG 发现的扩展点），统一从 `TenantContextHolder` 获取。
**完成（D1-D6 决策）**：
- D1 缺 ctx → 抛 `UNAUTHORIZED`
- D2 Booking 兜底 → Controller 强制填，Service 移除兜底
- D3 CommissionSettlement.generate → 内部 ctx 注入，签名不变
- D4 ChannelDispatcher.healthCheckAll → 保留 `1L`（默认租户系统级操作） + WARN 日志
- D5 storeId=1L → 必填，禁用 fallback
- D6 14 处全部处理，不限于原 12 处

### T08 — `PosController` 租户隔离验证 Fixture
**目标**：让 `PosControllerTenantIsolationTest` 真正能跑过 7 条租户隔离验证用例。
**完成**：在 `@BeforeAll`/`@AfterAll` 注入 `sys_tenant`（900001/900002）+ `sys_user`（900001/900002）Fixture，全部对应 tenant，INSERT IGNORE 幂等，cleanup 按前缀精确删除。

---

## 3. 修改文件范围

### Production Code Changes

| Task | 文件 | 修改类型 |
|---|---|---|
| T01-ext | `entity/Store.java` | 加字段 |
| T02 | `task/LogRetentionTask.java` 运行时已 OK（依赖 DDL） | 仅验证 |
| T03 | `entity/Wallet.java` + `mapper/WalletMapper.java` + `mapper/xml/WalletMapper.xml` + `service/impl/WalletServiceImpl.java` + `controller/WalletController.java` + `service/PosRefundService.java` | Entity/Mapper/Service/Controller |
| T04 | 无生产代码改动（仅 SQL 回填） | 仅 SQL |
| T05 | 无生产代码改动（仅 SQL 回填） | 仅 SQL |
| T06 | `service/CommissionCalculationService.java` + `service/PointService.java` + `service/impl/PointServiceImpl.java` + `service/PosRefundService.java` | Service 接口与实现 |
| T07 | `controller/StockController.java` + `controller/PackageController.java` + `controller/BookingController.java` + `controller/CommissionController.java` + `service/OpenPlatformEnhanceService.java` + `service/CommissionSettlementService.java` + `service/channel/ChannelDispatcher.java` + `service/OrgHierarchyService.java` + `service/impl/BookingServiceImpl.java` | 9 个 Controller/Service |

### Test Only Changes

| Task | 文件 | 用途 |
|---|---|---|
| T02 | `test/.../task/LogRetentionTaskSmokeTest.java` | 验证 sys_config 读取 + 任务执行 |
| T03 | `test/.../service/WalletTenantInsertTest.java` | 验证 tenant_id 写入 |
| T04 | `test/.../service/WalletNullTenantBackfillTest.java` | 验证 NULL 回填 + T03 仍有效 |
| T05 | `test/.../service/RefundReverseLogBackfillTest.java` | 验证 9 条历史回填 + 682 NULL 保留 |
| T06 | `test/.../service/T06ReverseLogRefIdTest.java` | 验证运行时 ref_id 语义 |
| T07 | `test/.../service/BookingCreateConflictTest.java` + `BookingRoomConflictTest.java` | 验证 Booking 兜底 tenant 注入 |
| T08 | `test/.../controller/PosControllerTenantIsolationTest.java` | 验证 7 个租户隔离用例 |

### Database Migration

| Task | 文件 | 类型 |
|---|---|---|
| T01-ext | `migrate_v1_0_pos_store_add_p3_columns.sql` | DDL（8 列 + 索引） |
| T01 | `migrate_v1_0_pos_store_add_tenant_id.sql` | DDL（1 列） |
| T02 | `migrate_t02_sys_config.sql` | DDL + DML（seed） |
| T04 | `migrate_v0_p0_db_001_t04_backfill_wallet_tenant_id.sql` | DML（历史回填） |
| T05 | `migrate_v0_p0_db_001_t05_refund_reverse_log_backfill.sql` | DML（9 条 PACKAGE 历史回填） |
| T06 | 无 | （运行时修复，不再 DDL） |
| T07 | 无 | （代码层修复） |
| T08 | 无 | （测试运行时临时 fixture，自动清理） |

---

## 4. 测试证据

| Task | 测试类 | 通过 |
|---|---|---|
| T02 | `LogRetentionTaskSmokeTest` | ✅ |
| T03 | `WalletTenantInsertTest`（5 用例） | ✅ 5/5 |
| T04 | `WalletNullTenantBackfillTest`（10 用例） | ✅ 10/10 |
| T05 | `RefundReverseLogBackfillTest`（12 用例） | ✅ 12/12 |
| T06 | `T06ReverseLogRefIdTest`（10 用例） | ✅ 10/10 |
| T07 | `BookingCreateConflictTest`（3 用例）+ `BookingRoomConflictTest`（3 用例） | ✅ 6/6 |
| T08 | `PosControllerTenantIsolationTest`（7 用例） | ✅ 7/7 |
| 核心 | `MultiTenantWalletTest`（6 用例） | ✅ 6/6 |

---

## 5. 数据库变化

### Schema 变更（T01-ext + T02）
- `pos_store` 增加 9 列 + 2 索引
- `sys_config` 表创建（首次）

### Data 变更（T04 + T05）
- `crm_wallet.tenant_id`：29 行 NULL → 0 行 NULL（回填至对应 member.tenant_id）
- `pos_refund_reverse_log.ref_id`：9 条 PACKAGE → 非 NULL（强证据链）；其余 682 条 NULL 保留

### 临时运行时数据（T08）
- `sys_tenant` 临时 +2 行（900001/900002），测试结束后清理
- `sys_user` 临时 +2 行（900001/900002），测试结束后清理
- `crm_member`/`crm_wallet`/`pos_order`/`pos_order_item`/`pos_payment`/`crm_wallet_transaction` 临时若干行，测试结束后清理

---

## 6. 未处理问题列表（OUT OF SCOPE）

| # | 问题 | 来源 |
|---|---|---|
| 1 | `crm_package_order.storeId` schema drift | T01-ext 之外 |
| 2 | `stock_inventory.version` schema drift | T01-ext 之外 |
| 3 | `crm_wallet.tenant_id IS NULL = 4`（MultiTenantWalletTest 残留） | 预存状态，DIAG §F6 |
| 4 | T04/T05/T06 自测试 baseline drift（互相干扰） | DIAG §F6 已记录 |
| 5 | `IndustryService.applyTemplate` 引用不存在的 `tenant_industry` 表 | 00_CLEAN_BASELINE §N |
| 6 | `tenant_id` 900001/900002 不存在于 `sys_tenant`（生产） | T08 用 fixture 解决 |
| 7 | DCEP / `app.dcep.mode: dev` 走 mock | T07 之后任务 |
| 8 | E2E Playwright `fallbackRbacSession('*')` 仍可能掩盖 RBAC 问题 | T08 之后任务 |
| 9 | 前端 Vitest 单元测试 / Playwright E2E 真实回归 | T08 之外 |
| 10 | 历史 682 条 NULL `pos_refund_reverse_log.ref_id`（POINTS/STOCK/COMMISSION） | T05 保留 |

---

## 7. Out Of Scope 列表（已确认）

- ❌ 不进入 T09 / T10
- ❌ 不处理 `crm_package_order.storeId`
- ❌ 不处理 `stock_inventory.version`
- ❌ 不处理 TenantContext 注入链
- ❌ 不处理 DCEP / 银行 SDK 真实化
- ❌ 不处理 POS refund / reverse_log
- ❌ 不修改 T05 / T06 / T07 / T08 测试代码
- ❌ 不优化 T08 Fixture
- ❌ 不扩大 Tenant Isolation 范围
- ❌ 不清理 `crm_wallet` NULL drift（预存状态）

---

## 8. 当前系统基线

### Production Code Status
- `WalletService` SSOT 完整（crm_wallet 是 crm_member.balance 唯一真源）
- `TenantContextHolder` 为标准租户来源
- `PosRefundService` `ref_id` 链路运行时准确
- 14 处 `1L` 硬编码治理完毕（D4 1 处保留为系统级默认 + WARN）
- `sys_tenant` 主表为租户唯一真源

### Database Status
- Schema 与 Entity 全部对齐（pos_store / crm_wallet / sys_config）
- `crm_wallet.tenant_id` 0 NULL（生产路径）
- `pos_refund_reverse_log.ref_id` 9 条 PACKAGE 已回填；682 条 NULL 保留
- 所有 FK 约束未 DROP / DISABLE

### Test Status
- T01-T08 专项测试全部 PASS
- 核心多租户隔离测试（MultiTenantWalletTest）6/6 PASS
- T08 真实执行 7 条验证用例（不再因 FK 阻断阻塞）

### Mock / Fake / Dev Mode
- `app.dcep.mode: dev` 仍启用（DCEP 真实化非 P0 范围）
- `fallbackRbacSession('*')` 仍存在（E2E RBAC 验证非 P0 范围）

---

## 9. 决策记录（D3 Decisions）

| Task | 决策 | 决定 |
|---|---|---|
| T01-ext | 9 列全补 vs 最小化 | 9 列全补（历史 init SQL 依据） |
| T05 | 全量回填 vs 强证据链 | 强证据链 + 孤儿保留（9 + 682） |
| T06 | ref_id 语义 | PACKAGE/STOCK 各实体一记录；POINTS 用 market_point_record.id；COMMISSION 用 invalidation list |
| T07 | D1 | 缺 ctx → UNAUTHORIZED |
| T07 | D2 | Controller 强制填，Service 移除兜底 |
| T07 | D3 | `CommissionSettlement.generate` 内部 ctx 注入 |
| T07 | D4 | ChannelDispatcher.healthCheckAll 保留 1L + WARN |
| T07 | D5 | storeId=1L 必填，禁用 fallback |
| T07 | D6 | 14 处全部处理（超原 12 处规划） |
| T08 | 范围 | 仅 PosControllerTenantIsolationTest 测试环境 |
| T08 | 方法 | 方案 A：插入 sys_tenant fixture，不 DROP FK |

---

## 10. 最终状态

```
T01-ext  ✅ PASS
T02      ✅ PASS
T03      ✅ PASS
T04      ✅ PASS 🔒 FROZEN
T05      ✅ PASS 🔒 FROZEN
T06      ✅ PASS 🔒 FROZEN
T07      ✅ PASS 🔒 FROZEN
T08      ✅ PASS 🔒 FROZEN

P0 → COMPLETE 🔒
```

**P0 阶段正式关闭。等待下一个人工指令。**
