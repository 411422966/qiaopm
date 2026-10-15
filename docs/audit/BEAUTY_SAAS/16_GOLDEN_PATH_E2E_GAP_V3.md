# 16_GOLDEN_PATH_E2E_GAP_V3

> **基线建立日期**: 2026-08-13
> **审计目的**: 重新定义 Beauty SaaS Golden Path，对每个节点逐一验证 UI/API/Backend/DB/Transaction/Test/E2E 状态

---

## 0. 重新定义的 Golden Path（10 节点）

```
Member Onboarding
↓
Booking 预约
↓
Check-in 签到
↓
Service Item Selection 项目选择
↓
Inventory Consumption 库存消耗
↓
POS Order 订单结算
↓
Payment 支付（含钱包 / 微信 / DCEP / 套餐核销）
↓
Commission 提成计算
↓
Follow-up 跟进
↓
Review/Report 评价/报表
```

---

## 1. 节点级评估矩阵

| Step | UI | API | Backend | DB | Transaction | Test | E2E | Status |
|------|----|----|---------|----|-----------|------|-----|--------|
| 1. Member | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 | 🟠 | ⚫ | **GREEN** |
| 2. Booking | 🟢 | 🟢 | 🟢（Controller 业务）| 🟢（runtime 修复后）| 🟢 | ⚫ | ⚫ | **YELLOW** |
| 3. Check-in | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 | ⚫ | ⚫ | **YELLOW** |
| 4. Service Item | 🟢 | 🟢 | 🟢 | 🟢 | — | ⚫ | ⚫ | **YELLOW** |
| 5. Inventory Consumption | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 | 🟢（ConcurrencyTest） | ⚫ | **GREEN** |
| 6. POS Order | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 | 🟢（PosRefundTest） | ⚫ | **GREEN** |
| 7. Payment | 🟢 | 🟢 | 🟡（wallet REAL，DCEP MOCK）| 🟢 | 🟢 | 🟢 | ⚫ | **YELLOW** |
| 8. Commission | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 | ⚫ | ⚫ | **YELLOW** |
| 9. Follow-up | 🟢 | 🟢 | 🟢 | 🟢 | — | ⚫ | ⚫ | **YELLOW** |
| 10. Review/Report | 🟢 | 🟢 | 🟢 | 🟢 | 🟢 | ⚫ | ⚫ | **YELLOW** |

图例：🟢=存在/已验证  🟡=部分  🟠=缺口  ⚫=不存在

---

## 2. 节点详细验证

### 2.1 Member Onboarding ✅ GREEN

| 维度 | 证据 |
|------|------|
| UI | `frontend/meiyetec-web/src/views/member/*.vue`（多个页面） |
| API | `MemberController` 30+ endpoints |
| Backend | `MemberServiceImpl` 空壳，但 Controller 直 |
| DB | `crm_member` 表，DDL 存在（`init_m03_member.sql`） |
| Transaction | — |
| Test | 无专用 member 测试 |
| E2E | 无 |

**评估**：业务可用，但缺少单元测试。

### 2.2 Booking 🟡 YELLOW

| 维度 | 证据 |
|------|------|
| UI | `frontend/.../booking/*.vue`（calendar/list/create）|
| API | `BookingController` 15+ endpoints |
| Backend | `BookingController.java` 346 LOC 真实业务（员工冲突检查、房间冲突检查、签到、取消）|
| DB | `biz_booking`, `biz_employee_schedule`, `biz_room` 由 ad-hoc 修复创建 |
| Transaction | `@Transactional` 在创建预约、签到处使用 |
| Test | 无 |
| E2E | 无 |

**评估**：业务真实运行，但 DDL 在 root 目录不在迁移内。

### 2.3 Check-in 🟡 YELLOW

| 维度 | 证据 |
|------|------|
| API | `BookingController.checkin()` |
| Backend | 状态变更 1→3，写 `checkin_time` |
| DB | `biz_booking.status` |
| Test | 无 |
| E2E | 无 |

### 2.4 Service Item 🟡 YELLOW

| 维度 | 证据 |
|------|------|
| API | `PosController.servicesList()`, `BizServiceCategory`, `BizServiceItem`, `BizServiceMaterial`（BOM） |
| Backend | `biz_service` 主表 + `biz_service_material`（BOM）映射服务-耗材关系 |
| DB | DDL 存在 |
| Test | 无 BOM 验证测试 |
| E2E | 无 |

### 2.5 Inventory Consumption ✅ GREEN

| 维度 | 证据 |
|------|------|
| UI | 库存模块有可视化页面 |
| API | `StockController.outbound()`, `PosController.pay()` 触发 |
| Backend | `InventoryConsumptionService.consumeByOrder()`（210 LOC）：3 层幂等 + FEFO 选批次 + 多批次 CAS + 乐观锁 + Transactional |
| DB | `stock_inventory`, `stock_record`, `pos_order_consumption` 均 DDL 存在 |
| Transaction | `@Transactional(rollbackFor = Exception.class)` |
| Test | `ConcurrencyIntegrationTest.inventoryNoOversell()` 10 并发 × 20 件 vs 库存 100 |
| E2E | 无 |

**评估**：最完整的 REAL 链路。

### 2.6 POS Order ✅ GREEN

| 维度 | 证据 |
|------|------|
| API | `PosController.orders/create/pay/refund` 30+ endpoints |
| Backend | `PosController.java` 586 LOC + `PosRefundService` 606 LOC |
| DB | `pos_order`, `pos_order_item`, `pos_payment`, `pos_refund`, `pos_refund_item`, `pos_refund_reverse_log` 均存在 |
| Transaction | 多处 `@Transactional` |
| Test | `PosRefundIntegrationTest` 5 个 Case（cash/wallet/3rd-party/mixed/package reverse）|
| E2E | 无 |

### 2.7 Payment 🟡 YELLOW

| 子路径 | 状态 | 证据 |
|--------|------|------|
| 现金 cash | 🟢 REAL | `PosController.java:324` 简单记录 |
| 微信/支付宝 wechat/alipay | 🟠 **占位** | `PosController.java:324` "记录即可" 注释 |
| 钱包 wallet | 🟢 REAL | `WalletController.doRecharge/doConsume/doRefund` 完整 + 测试覆盖 |
| 套餐核销 package | 🟢 REAL | `PosController.redeemPackage()` + `PosRefundTest Case 5` |
| DCEP e_cny_soft/hard | 🔴 **MOCK** | `PosController.java:318-323` + `DcepService.mockPrepay()` |

**风险**：DCEP 路径完全是 Mock，4 个 P0 见 `15_MONEY_SAFETY_AUDIT_V3.md`。

### 2.8 Commission 🟡 YELLOW

| 维度 | 证据 |
|------|------|
| API | `CommissionController` 8 mapper 注入 |
| Backend | `CommissionCalculationService`（155 LOC）+ `CommissionSettlementService`（179 LOC）|
| DB | `commission_record`, `commission_settlement`, `commission_rule` 等 |
| Transaction | `@Transactional` |
| Test | 无 |
| E2E | 无 |

### 2.9 Follow-up 🟡 YELLOW

| 维度 | 证据 |
|------|------|
| API | `CustomerCareService`/`MarketingAutomationService` |
| Backend | 跟进计划/任务实体 + 服务 |
| DB | `crm_follow_up_plan`, `crm_follow_up_task` |
| Test | 无 |
| E2E | 无 |

### 2.10 Review/Report 🟡 YELLOW

| 维度 | 证据 |
|------|------|
| API | `ReportController` 8 mapper 注入 + `ReportV2Service` |
| Backend | 67 mapper 引用 + 多统计脚本 |
| DB | `report_*` 系列表 |
| Test | 无 |
| E2E | 无 |

---

## 3. 断裂点定位

### 3.1 RED 断点

| 位置 | 断裂原因 |
|------|----------|
| 7-Payment.DCEP | `DcepService.mockPrepay()` 硬编码 QR + `verifySign` MOCK- 前缀绕过 |
| 7-Payment.微信/支付宝 | Controller 注释"记录即可"，无 SDK 接入 |

### 3.2 YELLOW 断点

| 位置 | 断裂原因 |
|------|----------|
| 整个 Golden Path | 缺 E2E 测试（无 Cypress/Playwright） |
| 整个 Golden Path | 大多数节点缺单元测试 |
| 2-Booking | DDL 在 ad-hoc 脚本，不在迁移内（新部署将失败） |
| 8-Commission | 实际跑通无测试证据 |

### 3.3 GREEN 节点

| 位置 | 完整性证据 |
|------|------------|
| 5-Inventory Consumption | 3 层幂等 + CAS + FEFO + Transactional + 并发测试 |
| 6-POS Order | 30+ endpoints + 606 LOC Service + 5 Case 集成测试 |
| 1-Member | 业务可用，缺测试 |

---

## 4. E2E 缺口

| 缺口 | 影响 | 优先级 |
|------|------|--------|
| 无 E2E 测试框架 | 无法验证多模块联调 | P1 |
| 无 API 集成测试覆盖 Booking | 无法保证新部署 Booking 模块可用 | P0 |
| 无支付链路集成测试（DCEP/微信/支付宝） | 无法保证 Mock 不进入生产 | P0 |
| 无退款链路 E2E | 已通过集成测试部分覆盖 | P1 |
| 无报表数据一致性测试 | 多 mapper 直连 Controller 风险 | P1 |

---

## 5. 结论

- Golden Path 10 个节点中，**3 个 GREEN（Member 简化、Inventory、POS Order）** + **6 个 YELLOW** + **0 个纯 RED**
- 主要断裂在 **Payment.DCEP**（完全 Mock）和 **第三方支付（占位）**
- **最大缺口是 E2E**：完全空白
- 节点 2-Booking 受 SCHEMA_DRIFT 影响，新部署将失败

---

**审计完成时间**: 2026-08-13 23:15
**文档版本**: 3.0