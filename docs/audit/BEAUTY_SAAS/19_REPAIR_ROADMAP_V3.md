# 19_REPAIR_ROADMAP_V3

> **基线建立日期**: 2026-08-13
> **目的**: 把所有发现转换为可执行工程任务，按 P0 → P1 → P2 排序

---

## 0. 任务 ID 命名规范

| 前缀 | 含义 |
|------|------|
| `P0-DB` | 数据库相关 |
| `P0-MONEY` | 资金/支付安全 |
| `P0-TEST` | 测试补齐 |
| `P0-E2E` | E2E 测试 |
| `P1-LOGIC` | 业务逻辑下沉（空壳 Service）|
| `P1-FEATURE` | 功能完整性 |
| `P2-QUALITY` | 代码质量/技术债 |

---

## 1. P0 任务（必须修复，影响资金/数据/部署）

### TASK-P0-DB-001

**Title**: 整合 wallet/booking/room/schedule DDL 到版本化迁移

**Problem**: 5 张核心表（crm_wallet, crm_wallet_transaction, biz_booking, biz_room, biz_employee_schedule）的 DDL 散落在项目根目录 `fix_*.sql`，未纳入 `db/sql/` 版本化迁移流程。新部署/新租户运行迁移时必报 `Table doesn't exist`。

**Evidence**:
- `init_rbac_v2_b.sql:34` `ALTER TABLE crm_wallet` 但主表未创建
- `/fix_missing_tables.sql` (root) 与 `/fix_booking_calendar_tables.sql` (root)
- `backend-current.log` 历史报错 `Table 'meiyetec.crm_wallet' doesn't exist`

**Root Cause**: 历史修复采用 ad-hoc 脚本，未回归版本化迁移

**Affected Modules**: 全部依赖会员/钱包/预约的业务

**Required Change**:
1. 在 `db/sql/` 创建 `init_migration_v0_baseline.sql`，按依赖顺序：
   - 1) 创建 `crm_wallet`, `crm_wallet_transaction`（取自 fix_missing_tables.sql）
   - 2) 创建 `biz_booking`, `biz_employee_schedule`, `biz_room`（取自 fix_booking_calendar_tables.sql）
2. 修改主入口（Spring Boot 启动任务或 SQL 加载器）按依赖顺序执行
3. 在 README/部署文档说明此脚本是 mandatory baseline

**Database Change**: 新增 1 个 SQL 文件，5 张表正式纳入迁移

**Backend Change**: 无（DDL 已在运行时存在）

**Frontend Change**: 无

**Test Required**: 新增集成测试 `MigrationSmokeTest`：模拟全新数据库，跑完整迁移后查 5 张表都存在

**Risk**: 低；脚本已通过运行验证，仅重新整合

**Dependencies**: 无

**Acceptance Criteria**:
- 删除运行时表后，重新执行迁移脚本能完整恢复
- README 部署说明引用此脚本
- 现有功能测试全部通过

**Definition of Done**:
- `db/sql/init_migration_v0_baseline.sql` 提交
- `MigrationSmokeTest` 通过
- 文档更新

**工时估计**: 0.5 人日

---

### TASK-P0-DB-002

**Title**: 增加 8 条 P0 物理外键约束

**Problem**: 0 条 FK 约束，数据完整性依赖应用层。订单/支付/退款/库存消耗父子关系缺失 DB 级保护。

**Evidence**: 见 `14_DATABASE_INTEGRITY_MATRIX_V3.md` 2.1.1 节

**Root Cause**: 历史采用"应用层 FK + 软删除"模式，但订单-明细/支付/退款的强聚合关系也未受保护

**Affected Modules**: POS 订单、退款、DCEP、库存消耗

**Required Change**: 添加 8 条 FK（见 V3 文档 4.1 节完整 DDL）：
- pos_order_item.order_id → pos_order.id
- pos_payment.order_id → pos_order.id
- pos_order_consumption.order_id → pos_order.id
- pos_order_consumption.stock_record_id → stock_record.id
- pos_refund.order_id → pos_order.id
- pos_refund_item.refund_id → pos_refund.id
- pay_dcep_order.tenant_id → sys_tenant.id
- pay_dcep_notify.dcep_order_id → pay_dcep_order.id

**Database Change**: 新增 1 个 SQL 文件 `db/sql/init_migration_v1_fk.sql`

**Backend Change**: 无（DB 层加 FK 不影响业务代码）

**Test Required**:
- 现有测试应继续通过
- 新增 `ForeignKeyConstraintTest`：尝试 INSERT pos_order_item.order_id=999999（不存在）应失败

**Risk**: 中。如果现有数据有孤儿记录，FK 创建会失败；建议先跑孤儿检查脚本

**Dependencies**: TASK-P0-DB-001

**Acceptance Criteria**:
- 8 条 FK 创建成功
- 现有测试通过
- 数据无孤儿（孤儿检查脚本通过）

**Definition of Done**:
- 迁移脚本提交
- 孤儿检查脚本通过
- FKConstraintTest 通过

**工时估计**: 0.5 人日（含孤儿检查）

---

### TASK-P0-MONEY-001

**Title**: 替换 DCEP Mock 为银行 SDK（或强制 MOCK 仅 dev）

**Problem**: `DcepService.mockPrepay()` 硬编码 1×1 像素 PNG QR + `verifySign` 接受 `MOCK-` 前缀绕过

**Evidence**:
- `DcepService.java:170-183` 硬编码 QR + PREPAY-
- `DcepService.java:348` `sign.startsWith("MOCK-")` 即通过

**Root Cause**: 历史采用 MOCK 兜底但未做环境隔离

**Affected Modules**: 数字人民币支付、notify 回调

**Required Change**:
1. 增加 `@Profile("dev")` 注解到 mockPrepay 和 verifySign 的 MOCK 分支
2. 生产环境强制走真实 SDK（建行/工行/农行任一）
3. 如果暂时无法接入真实 SDK，则：
   - 增加 `app.dcep.mode=PROD` 配置项
   - PROD 模式下 mockPrepay 返回 `status=FAIL, errorCode=NOT_IMPLEMENTED`
   - PROD 模式下 verifySign 不接受 `MOCK-` 前缀
4. `DcepService.notify` 改为通过 OrderService 业务方法更新订单（而不是直接 JDBC）

**Database Change**: 无

**Backend Change**:
- DcepService 重构
- 增加配置类 DcepConfigProperties

**Frontend Change**: 无（错误码透传）

**Test Required**:
- `DcepMockEnvTest`：dev 模式 mockPrepay 返回 PREPAY-，prod 模式返回 FAIL
- `DcepVerifySignTest`：prod 模式下 MOCK- 前缀必须被拒

**Risk**: 高。生产环境若已依赖 mockPrepay 返回成功，禁用将导致真实交易失败；需要与业务方确认

**Dependencies**: 无

**Acceptance Criteria**:
- dev 模式 mockPrepay 正常工作
- prod 模式 mockPrepay 返回 NOT_IMPLEMENTED
- prod 模式 verifySign 不接受 MOCK- 前缀

**Definition of Done**:
- 配置注解生效
- 双环境测试通过
- 部署文档更新

**工时估计**: 1.5 人日（不含真实 SDK 接入）

---

### TASK-P0-MONEY-002

**Title**: 消除 crm_member.balance 与 crm_wallet.balance 双写

**Problem**: 钱包余额同时维护在 `crm_wallet.balance` 和 `crm_member.balance`，存在双轨不一致风险

**Evidence**:
- `WalletController.java:152` `m.setBalance(before.add(add))` + `memberService.updateById(m)`
- `WalletController.java:197` 同上（消费路径）
- `WalletController.java:250` 同上（退款路径）
- `PosController.java:294-301` 仅更新 member.balance，不写 wallet

**Root Cause**: 早期字段冗余设计，后期未清理

**Affected Modules**: Wallet、Member、POS 订单

**Required Change**:
1. 选择 `crm_wallet.balance` 作为权威源（推荐，因为有乐观锁 + 流水表）
2. `crm_member.balance` 改为：
   - 选项 A：废弃字段（从 Entity 删除 + DB 列删除或保留只读视图）
   - 选项 B：保留字段但改为只读视图（trigger 或 service 层强制）
3. 删除 `WalletController` 中的 `m.setBalance(...)` 和 `memberService.updateById(m)` 同步逻辑
4. 删除 `PosController` 第 286-302 行的 wallet 路径重复处理（统一走 WalletController.doConsume）
5. 前端查询 `/wallet/{memberId}` 已经返回 wallet 数据

**Database Change**: 可选 `ALTER TABLE crm_member DROP COLUMN balance`（备份后）

**Backend Change**:
- Member Entity 删除 balance 字段
- WalletController 删除冗余同步
- PosController 走统一钱包服务

**Frontend Change**: 检查所有调用 member.balance 的前端代码，改为调用 /wallet/{memberId}

**Test Required**:
- `WalletSingleSourceTest`：钱包操作后 crm_member.balance（如果保留）必须等于 crm_wallet.balance
- 现有 PosRefundTest Case 3 钱包路径必须继续通过

**Risk**: 高。Member.balance 在前端和很多地方使用，需要全面排查

**Dependencies**: 无

**Acceptance Criteria**:
- 钱包操作后单一权威源
- 前端所有余额显示走 /wallet API
- 现有测试通过

**Definition of Done**:
- 字段清理完成
- 前端调用替换
- 数据迁移验证

**工时估计**: 3 人日（前端+后端+测试）

---

### TASK-P0-MONEY-003

**Title**: 强化资金 ID 生成（雪花算法替换 Math.random）

**Problem**: 订单号、退款单号、会员号使用 `Math.random()` 生成，存在碰撞风险

**Evidence**:
- `PosController.java:595` 1/999999 碰撞
- `PosController.java:600` 1/9999 碰撞
- `PosRefundService.java:604` 1/1000 碰撞
- `MemberController.java:283,511` 1/9999 碰撞

**Root Cause**: 历史采用时间戳 + 随机数方案，碰撞率随并发量增加

**Affected Modules**: POS Order, Refund, Member

**Required Change**:
1. 引入雪花算法（Snowflake）工具类 `IdGenerator`，基于 (workerId, datacenterId, sequence)
2. `IdGenerator` 启动时初始化 workerId（基于机器 MAC 或配置）
3. 替换：
   - `PosController.generateOrderNo` → `IdGenerator.nextId()` 字符串形式 + 前缀 "O"
   - `PosRefundService.generateRefundNo` → 同上 + "RF"
   - `MemberController` memberNo → 同上 + "M"

**Database Change**: 无

**Backend Change**: 新增 util.IdGenerator，替换 4 处生成

**Frontend Change**: 无

**Test Required**:
- `IdGeneratorTest`：10000 并发生成 ID 无碰撞
- 现有测试继续通过

**Risk**: 低。雪花算法是标准方案

**Dependencies**: 无

**Acceptance Criteria**:
- 10000 并发生成无碰撞
- 雪花 ID 全局唯一
- 现有功能测试通过

**Definition of Done**:
- IdGenerator 上线
- 4 处替换完成
- 碰撞测试通过

**工时估计**: 1 人日

---

### TASK-P0-MONEY-004

**Title**: 修复 PosController wallet path 硬编码 tenant_id=1L

**Problem**: `PosController.java:298-301` 和 `:391-396` 直接 JDBC INSERT 时硬编码 `1L`，破坏多租户隔离

**Evidence**:
- `PosController.java:300` `1L, m.getId(), 2, amount, before, after, ...`
- `PosController.java:395` 同上

**Root Cause**: Controller 直接 JDBC，绕过 TenantContextHolder

**Affected Modules**: POS Order Wallet 路径、Wallet Refund 路径

**Required Change**:
- 把硬编码 `1L` 替换为 `TenantContextHolder.get().getTenantId()`
- 长期方案：抽取 `WalletTransactionService.recordConsume(memberId, amount, orderId, operatorId)` 让 Controller 调用，而不是直接 JDBC

**Database Change**: 无

**Backend Change**: PosController 修改 2 处

**Frontend Change**: 无

**Test Required**:
- `MultiTenantWalletTest`：不同租户的钱包交易记录 tenant_id 必须正确

**Risk**: 中。如果有依赖 tenant_id=1L 的报表，需要同步修复

**Dependencies**: 无

**Acceptance Criteria**:
- 钱包交易记录 tenant_id 与上下文一致
- 跨租户测试通过

**Definition of Done**:
- PosController 修改完成
- MultiTenantWalletTest 通过

**工时估计**: 0.5 人日

---

### TASK-P0-TEST-001

**Title**: 补齐 Wallet / Booking 并发与冲突测试

**Problem**: Wallet 并发扣款、Booking 冲突检测无测试覆盖，无法保证并发安全

**Evidence**: 见 `17_BUSINESS_TEST_MATRIX_V3.md` 3.1 节

**Root Cause**: 历史测试只覆盖了库存和退款

**Affected Modules**: Wallet, Booking

**Required Change**:
1. 新增 `WalletRechargeConcurrencyTest`：50 并发充值同一会员，验证余额准确 + version 提升 + 仅首次成功
2. 新增 `WalletConsumeConcurrencyTest`：50 并发扣款，验证余额非负 + 不超额
3. 新增 `WalletRefundIdempotentTest`：同一订单号两次 refund，第二次拒绝
4. 新增 `BookingCreateConflictTest`：同一员工同一时段两次预约，第二次拒绝
5. 新增 `BookingRoomConflictTest`：同一房间同时段两次预约，第二次拒绝

**Database Change**: 无

**Backend Change**: 无

**Frontend Change**: 无

**Test Required**: 上述 5 个测试类

**Risk**: 低

**Dependencies**: TASK-P0-DB-001（确保 Booking 表可写入）

**Acceptance Criteria**:
- 5 个测试类全部通过
- 并发场景下业务正确性验证

**Definition of Done**:
- 测试类提交
- CI 集成（如果有 GitHub Actions）

**工时估计**: 2 人日

---

### TASK-P0-TEST-002

**Title**: 补齐 Commission / Report / Member 单元测试

**Problem**: 5 个业务模块（Member/Booking/Commission/Report/DCEP）完全无测试覆盖

**Evidence**: 见 `17_BUSINESS_TEST_MATRIX_V3.md` 1 节

**Root Cause**: 历史测试仅覆盖退款和库存

**Affected Modules**: Commission, Report, Member

**Required Change**:
1. `MemberServiceTest`：CRUD + 软删除 + 余额查询（已删除则返回 404）
2. `CommissionCalculationTest`：固定数据输入验证提成金额计算正确
3. `CommissionSettlementTest`：批量结算准确性
4. `ReportQueryTest`：报表查询 SQL 正确性（按维度聚合）
5. `DcepNotifyTest`：notify 回调处理（mock 验签、查订单、更新状态）

**Database Change**: 无

**Backend Change**: 无

**Test Required**: 上述 5 个测试类

**Risk**: 低

**Dependencies**: 无

**Acceptance Criteria**:
- 5 个测试类全部通过
- 业务逻辑覆盖率 ≥60%

**Definition of Done**:
- 测试类提交
- jacoco 报告展示覆盖

**工时估计**: 3 人日

---

### TASK-P0-E2E-001

**Title**: 建立 E2E 测试框架 + 1 条核心链路（预约→签到→消费→退款）

**Problem**: 0 E2E 测试，无法验证前端 → 后端 → DB 全链路

**Evidence**: 无 Cypress/Playwright 配置

**Root Cause**: 历史只做后端集成测试

**Affected Modules**: 全链路

**Required Change**:
1. 在 `frontend/meiyetec-web/` 集成 Playwright
2. 编写 `e2e/full-flow.spec.ts`：会员预约 → 签到 → 服务消费 → 支付 → 退款
3. CI 集成：GitHub Actions 跑 E2E（mock 后端或真后端 + 真 DB）

**Database Change**: 无

**Backend Change**: 无

**Frontend Change**: 增加 Playwright 配置 + 1 个测试用例

**Test Required**: 上述 E2E

**Risk**: 中。E2E 维护成本高

**Dependencies**: TASK-P0-TEST-001 + TASK-P0-TEST-002

**Acceptance Criteria**:
- Playwright 框架跑通
- 1 条核心链路 E2E 通过
- CI 集成

**Definition of Done**:
- Playwright 在 frontend 项目中配置
- CI 配置 E2E 步骤

**工时估计**: 3 人日

---

## 2. P1 任务（业务架构改进，非紧急但重要）

### TASK-P1-LOGIC-001

**Title**: 16 个空壳 ServiceImpl 业务下沉

**Problem**: 16 个 ServiceImpl（Booking/Room/EmployeeSchedule/Member/MemberTag/MemberLevel/MemberProfile/Category/PackageItem/PackageOrder/PackageRedemption/Store/Employee/DcepConfig/Coupon/WalletTransaction）仅 11 LOC `extends ServiceImpl`，业务逻辑全在 Controller

**Evidence**: `service/impl/*.java` 16 个 11 LOC 文件 + V3 文档

**Root Cause**: 历史设计倾向 Controller 直接处理业务

**Affected Modules**: 全部业务

**Required Change**:
1. 选 5 个高频使用 + 业务复杂的优先：BookingServiceImpl, MemberServiceImpl, PackageRedemptionServiceImpl, RoomServiceImpl, EmployeeScheduleServiceImpl
2. 把对应 Controller 的业务逻辑（事务、计算、跨表）下沉到 Service
3. Controller 只做参数校验、权限校验、调用 Service、返回结果

**Database Change**: 无

**Backend Change**: 5 个 ServiceImpl 业务下沉

**Frontend Change**: 无

**Test Required**: 5 个对应的 Service 单元测试 + 现有集成测试

**Risk**: 中。Controller 重构可能影响现有调用

**Dependencies**: TASK-P0-TEST-001

**Acceptance Criteria**:
- 5 个 ServiceImpl 平均行数 ≥100 LOC
- 现有集成测试通过

**Definition of Done**:
- 5 个 Service 实现完整业务
- Controller 简化（瘦身）
- 测试通过

**工时估计**: 5 人日

---

### TASK-P1-LOGIC-002

**Title**: 修复 5 个 Controller 直接 Mapper 滥用

**Problem**: PosController(4 Mapper), StockController(6), ReportController(8), CommissionController(5), DcepMerchantQualification(1) 共 24 处直接 Mapper 注入

**Evidence**: Grep `@Autowired.*Mapper\|final.*Mapper` 结果

**Root Cause**: 业务下沉前的临时方案

**Affected Modules**: POS, Stock, Report, Commission, DcepMerchantQualification

**Required Change**:
1. 抽取报表查询到 ReportQueryService（SQL 集中）
2. StockController 直连 6 个 Mapper 改为 StockQueryService + StockCommandService
3. Commission 同理
4. 紧急优先级：先 Stock 和 Report（业务查询为主，风险较低）

**Database Change**: 无

**Backend Change**: 新增 Service，Controller 改注入 Service

**Frontend Change**: 无

**Test Required**: 新 Service 单元测试

**Risk**: 中

**Dependencies**: TASK-P1-LOGIC-001

**Acceptance Criteria**:
- Controller 直接 Mapper 注入数 ≤5
- 新 Service 有完整业务逻辑

**Definition of Done**:
- 5 个新 Service 上线
- Controller 简化

**工时估计**: 3 人日

---

### TASK-P1-FEATURE-001

**Title**: 补齐 VisualArchiveService AI 调用（非 Mock）

**Problem**: `VisualArchiveService.mockAiAnalyze()` Random 生成假特征，写入 DB 标 `AI-VISION-V1-DEMO`

**Evidence**: `VisualArchiveService.java:423-450`

**Root Cause**: AI 通道未对接

**Affected Modules**: Mh 医美/AI 分析

**Required Change**:
1. 抽出 `AiVisionService` 接口，定义 `analyzeFeatures(image)` 
2. 实现类：
   - `MockAiVisionService`（默认）
   - `RealAiVisionService`（待接入真实 API）
3. 通过配置切换 `@ConditionalOnProperty`

**Database Change**: 无

**Backend Change**: 1 个 Service 接口 + 2 个实现 + 配置

**Frontend Change**: 无

**Test Required**: `AiVisionServiceTest`

**Risk**: 低。AI 通道本就是占位

**Dependencies**: 无

**Acceptance Criteria**:
- 接口和实现分离
- 配置切换生效
- modelVersion 不再写 -DEMO 后缀（除非 dev 模式）

**Definition of Done**:
- AiVisionService 上线
- mockAiAnalyze 重构为 MockAiVisionService

**工时估计**: 1 人日

---

## 3. P2 任务（代码质量）

### TASK-P2-QUALITY-001

**Title**: 替换 3 个通知通道 Mock（SMS/MiniProgram/InApp）

**Problem**: `SmsSender`/`MiniProgramSender`/`InAppSender` 仅 `log.info` + 返回 `OK`

**Evidence**: `service/channel/SmsSender.java:42-43`, `MiniProgramSender.java:36-37`

**Root Cause**: 通知通道未对接

**Affected Modules**: 营销触达、跟进任务

**Required Change**: 与 TASK-P1-FEATURE-001 相同模式，ConditionalOnProperty 切换

**工时估计**: 1 人日

### TASK-P2-QUALITY-002

**Title**: 把 WalletController 营销规则硬编码改为可配置

**Problem**: `WalletController.java:349-358` 充值赠送规则硬编码

**Evidence**: promotionRules 返回 List.of 硬编码

**Root Cause**: 营销规则未持久化

**Affected Modules**: 钱包充值营销

**Required Change**: 抽取 sys_config 表 + 管理接口 + WalletController 改为读取配置

**工时估计**: 1 人日

---

## 4. 任务依赖图

```
P0-DB-001 (迁移整合)
  ↓
P0-DB-002 (FK 约束)
  ↓
P0-TEST-001 (Wallet/Booking 测试)
  ↓                              ↓
P1-LOGIC-001 (Service 下沉)  P0-TEST-002 (Comm/Report/Member)
  ↓
P1-LOGIC-002 (Mapper 滥用)
  ↓
P0-E2E-001 (E2E 框架)

独立：
P0-MONEY-001 (DCEP)
P0-MONEY-002 (双轨)
P0-MONEY-003 (ID 生成)
P0-MONEY-004 (多租户)
P1-FEATURE-001 (AI)
P2-QUALITY-001 (通知)
P2-QUALITY-002 (营销)
```

---

## 5. 修复顺序建议

### 第一周（紧急 P0）
1. **P0-DB-001** — 迁移整合（0.5d）
2. **P0-MONEY-004** — 多租户修复（0.5d）
3. **P0-MONEY-003** — ID 生成（1d）
4. **P0-MONEY-001** — DCEP 环境隔离（1.5d）
5. **P0-TEST-001** — Wallet/Booking 测试（2d）

合计：5.5 人日 → Reality 提升到 ~58%

### 第二周（业务下沉）
1. **P1-LOGIC-001** — 5 个空壳 Service 业务下沉（5d）
2. **P0-TEST-002** — Comm/Report/Member 测试（3d）

合计：8 人日 → Reality 提升到 ~64%

### 第三周（架构补齐）
1. **P0-DB-002** — FK 约束（0.5d）
2. **P0-MONEY-002** — 钱包双轨收敛（3d）
3. **P1-LOGIC-002** — Mapper 滥用修复（3d）
4. **P0-E2E-001** — E2E 框架（3d）

合计：9.5 人日 → Reality 提升到 ~70%

### 第四周及以后
- P1-FEATURE-001（AI）
- P2-QUALITY-001/002
- P0-E2E-001 后续 E2E 覆盖
- P1-LOGIC-001 剩余 11 个空壳 Service
- TASK-P1-LOGIC-002 剩余 Mapper 滥用

---

## 6. 总工时估算

| 类别 | 任务数 | 人日 |
|------|--------|------|
| P0-DB | 2 | 1.0 |
| P0-MONEY | 4 | 6.0 |
| P0-TEST | 2 | 5.0 |
| P0-E2E | 1 | 3.0 |
| P1-LOGIC | 2 | 8.0 |
| P1-FEATURE | 1 | 1.0 |
| P2-QUALITY | 2 | 2.0 |
| **合计** | **14** | **26 人日** |

---

**审计完成时间**: 2026-08-13 23:30
**文档版本**: 3.0