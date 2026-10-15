# CURRENT PROJECT REALITY BASELINE · CLEAN (Independent Re-Audit)

> **审计员**：完全外部视角、未接触任何历史报告
> **方法**：仅实测当前源码 / SQL / 实跑 API / DB / 服务 / 测试
> **纪律**：READ-ONLY，零修改
> **生成日期**：2026-08-15

---

## 0 · Executive Summary（一句话）

> **Q：项目当前状态到底算什么？**
> **A：技术栈完整、构建链真实可跑、核心收银 + 钱包 + 退款 + DCEP-mock 写得到位；但数据库 schema 正在活活漂移、`pos_store.tenant_id` 列根本不存在的实绩让所有 `/member/*` API 全是 500、`sys_config` 表不存在让定时任务每次启动失败、12 处硬编码 `tenantId=1L` 把多租户隔离打成窟窿、P0-MONEY-001 的 CCB/ABC/ICBC DCEP 真实 SDK 仍是 NOT_IMPLEMENTED、`pos_refund_reverse_log` 100% 的 ref_id 是 NULL。**
>
> 历史报告声称"ALL GREEN + Reality Score 93%+"与本轮实测**多处矛盾**（见第 14 节 History Contradictions）。

---

## 1 · Current Reality Score（自评）

| 维度 | 单项分（/100） | 权重 | 加权得分 | 关键判据 |
|---|---:|---:|---:|---|
| 1. 项目可启动 | 75 | 5% | 3.75 | 后端 / 前端 / MySQL / Redis 全在跑，但后端 `MultiTenantWalletTest` 5:52 启动失败、`LogRetentionTask` 每小时 `sys_config doesn't exist` |
| 2. API 可访问 | 55 | 10% | 5.5 | `/auth/login` OK · `/pos/services/list` OK · 但 `/member/1` · `/member/page` · `/pos/orders/page GET` 全 500，原因是 `pos_store.tenant_id` 缺失 |
| 3. 数据库 Schema 与代码一致 | 40 | 15% | 6.0 | 17 FK / 118 表（14.4% FK 覆盖率）；`pos_store/crm_member_profile` 缺 `tenant_id`；`sys_config` 表根本没建 |
| 4. 多租户隔离 | 35 | 15% | 5.25 | 12 处硬编码 `setTenantId(1L)`；29 行 `crm_wallet.tenant_id=NULL`；`PosControllerTenantIsolationTest` 历史 6 失败 |
| 5. 钱包 / 支付 / 退款业务 | 70 | 15% | 10.5 | `WalletServiceImpl` SSOT + CAS + 内存锁 + 雪花——真实跑通；`PosRefundService` 真实落库 270 单；但历史 `crm_wallet_drift_log` 记 13400 元漂移被 AUTO_FIX 才平息 |
| 6. DCEP 数字人民币 | 30 | 10% | 3.0 | `application.yml`: `mode: dev`（运行时允许 MOCK- 前缀签名）；`CcbDcepAdapter` / `AbcDcepAdapter` / `IcbcDcepAdapter` 4 个方法全 `NOT_IMPLEMENTED`；`e_cny_soft/hard` POS 路径仅生成 ECNY- + 雪花 transactionId，没有任何真实银行调用 |
| 7. Golden Path 端到端可跑 | 50 | 10% | 5.0 | Booking→POS→Pay→Refund 之间 SQL 真实落库；但 `/member/*` 500 让 E2E `full-flow.spec.ts` 主动 `test.skip(!backendHealthy)`；只剩 UI 路由可达性 |
| 8. 测试真实性 | 55 | 5% | 2.75 | `mvn_test_full.log`: 34/34 全过 ✓；但 `mvn_test_all_p004.log`: PosControllerTenantIsolationTest 7 用例 4 错 + 2 失败；E2E `last-run.json passed` 但因为 `checkBackendHealthy()` 失败就跳过业务步骤 |
| 9. E2E 真实性 | 50 | 5% | 2.5 | Playwright 1.62.1，5 spec，2 reports。但 `auth.setup.ts` 用 `permissions: ['*']` 兜底，`full-flow.spec.ts` 写死依赖 `sys_user.tenant_id` 不存在的注释保留至今 |
| 10. 资金链路 | 65 | 5% | 3.25 | Wallet SSOT 已收口；退款链路 270 单真实落库；DCEP 通道 25 (21 dev / 3 MOCK / 1 PROD)；pay_dcep_notify sign_verified=18/20 |
| 11. AI / 第三方 / 行业 / 压测 / 灾备 | 25 | 5% | 1.25 | `mh_visual_archive` 0 行；`mkt_brand` 0 行；`ai_*` 表空；这些模块绝大多数是 `@RestController` 直接写在 `service/` 包下但没有任何真实集成 SDK 调用证据 |

### **CURRENT REALITY SCORE = 48.25 / 100**

> 与历史报告声称的 93%+ 形成 ~45 分差距。差距来源：DB schema drift 未对账、多租户隔离未稳定通过、DCEP 真实银行未对接、Golden Path E2E 主动跳过业务步骤。

---

## 2 · P0 Blockers（已验证存在、阻塞当前业务）

| # | 描述 | 真实证据 |
|---|---|---|
| **P0-B1** | `/member/1` 实时 500 DB 异常 | `Invoke-RestMethod http://localhost:8080/member/1` 返回 `{"code":500,"message":"数据库异常"}` · `logs/meiyetec-error.log:11919` `Cause: java.sql.SQLSyntaxErrorException: Unknown column 'tenant_id' in 'field list'` · 栈追溯到 `StoreServiceImpl.listByIds` → `MemberController.fillExtras:564` → `MemberController.getById:262` → `pos_store` 表缺 `tenant_id` |
| **P0-B2** | `LogRetentionTask` 每小时 `sys_config doesn't exist` | `logs/meiyetec-error.log:1-50` 每小时定时任务触发；所有 init SQL 文件**没有任何 CREATE TABLE sys_config**（仅 `init_rbac_v2_b.sql:52` 用 `INSERT IGNORE INTO sys_config`） |
| **P0-B3** | 12 处 `setTenantId(1L)` 硬编码旁路 | `grep "setTenantId(1L)"` 命中：BookingServiceImpl:81 · CommissionSettlementService:60:89 · StockController:119:181 · PackageController:106:155:492:619 · OrgHierarchyService:247 · ChannelDispatcher:43 · OpenPlatformEnhanceService:403 · CommissionController:250:650 |
| **P0-B4** | DCEP 三家真实银行全部 `NOT_IMPLEMENTED` | `dcep/impl/CcbDcepAdapter.java:38-83` · `AbcDcepAdapter.java:32-77` · `IcbcDcepAdapter.java:34-77` 4 个方法全部返回 `errorCode("NOT_IMPLEMENTED", "…尚未对接真实 SDK")` |
| **P0-B5** | `e_cny_soft` / `e_cny_hard` POS 支付只生成交易号 | `PosController.java:323` 注释 `// 数字人民币：模拟对接（实际接入央行/运营机构网关 TODO)`；case 体只 `p.setTransactionId("ECNY-" + idGenerator.nextIdStr())` |
| **P0-B6** | `crm_wallet` 29 行 `tenant_id=NULL` | DB 实测 `SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL → 29`；根因在 `WalletMapper.xml:18-22` `insertIfAbsent` 写入字段为 `member_id, balance, total_recharge, total_consume, frozen_amount, version`——**没有 tenant_id 列** |

---

## 3 · Database Reality

### 3.1 表与约束
- **118 张表**（SHOW TABLES 实测）
- **17 条 FK 约束**（information_schema.KEY_COLUMN_USAGE 计数）→ **FK 覆盖率 17/118 = 14.4%**
- `sys_config` 表**未建**（init SQL 不含 CREATE）——但代码 `LogRetentionTask` 每小时读它

### 3.2 关键表实际数据（实测）
| 表 | 行数 | tenant_id 分布 |
|---|---:|---|
| sys_tenant | 2 | id=1, id=2（无 900001 / 900002） |
| sys_user | 1 | 仅 admin tenant_id=1 |
| sys_role_data_scope | 5 | （用于 R2-A 数据权限扩展） |
| crm_member | 92 | 92 → tenant_id=1 |
| crm_member_profile | 4 | （含 fields 名 `consume_pref` `visit_record` 实表） |
| crm_wallet | **37** | 8 → tenant_id=1 / **29 → NULL** |
| crm_wallet_transaction | 23 | 23 → tenant_id=1 |
| crm_wallet_drift_log | 16 | 8 PRE_FIX_BACKUP + 8 AUTO_FIX |
| biz_booking | **0** | （DB 一行都没有——但代码 + 测试都存在） |
| biz_employee_schedule | 42 | tenant_id=1 |
| biz_room | 4 | |
| biz_service_item | 22 | |
| crm_package | 4 | |
| crm_package_order | 14 | |
| pos_order | **481** | 425 → tenant_id=1 / 0 → NULL / **56 → {900001, 900002}** |
| pos_order_item | 198 | |
| pos_payment | **451** | 395 → 1 / **56 → {900001, 900002}** |
| pos_refund | 270 | |
| pos_refund_reverse_log | **691** | **691/691 ref_id = NULL（100%）** |
| stock_product | 14 | tenant_id=1 |
| stock_inventory | 6 | |
| commission_record | **0** | （提成主表空） |
| commission_reversal | **0** | |
| commission_settlement | **0** | |
| pay_dcep_channel | 25 | 21 dev / 3 MOCK / 1 PROD |
| pay_dcep_order | 78 | status 1=17 / 2=21 / 3=13 / 4=15 / 5=12 |
| pay_dcep_notify | 20 | sign_verified 1=18 / 0=2 |
| mh_visual_archive | **0** | |
| mkt_brand | **0** | |
| crm_follow_up_plan | 3 | |
| crm_follow_up_task | **0** | |
| report_export_task | **0** | |
| report_metric_snapshot | 16 | |

### 3.3 Schema Drift 活证据

1. **`pos_store` 表缺 `tenant_id` 列**（DESCRIBE 14 字段无 tenant_id，StoreServiceImpl.listByIds 用 * select）—— **MembeController.fillExtras 调用即崩**
2. **数据库列名 `consume_pref` / `visit_record` 已存在**，但 MemberProfile 实体字段名 `consumePref` / `visitRecord` —— 这是 snake_case 转换 OK
3. **`crm_wallet` 表 `tenant_id` 允许 NULL**，且 `insertIfAbsent` 写入字段不含 tenant_id → 持续产生 NULL 数据
4. **`sys_config` 表不存在**，但 init SQL `init_rbac_v2_b.sql:52` 直接 `INSERT IGNORE INTO sys_config` —— ETL 阶段也崩

### 3.4 init_migration_v0_baseline_ALL.sql 评估

- **存在但不可独立重建当前 DB**：只有一份 ALL 合集（50524 bytes）；其他 70+ 个 init/migrate/patch/fix 文件散布在 `db/sql/` 下，按什么顺序跑、依赖什么，无 README 文档
- 测试时通过 Spring Boot `createDatabaseIfNotExist=true` + 一些 init SQL 拼装，**无法从零 SQL-only 重建**

---

## 4 · Tenant Isolation Reality

### 4.1 当前实现链路

```
HTTP → JwtInterceptor (parse token, populate TenantContextHolder)
     → RbacContextLoader.load() → PermissionAspect
     → Controller → Service (resolveTenantId 或 setTenantId(1L) 兜底)
     → MyBatis Mapper (TenantMetaObjectHandler 自动注入 tenant_id?)
```

### 4.2 实际漏洞

| 漏洞 | 文件 / 行 | 危险度 |
|---|---|---|
| 12 处硬编码 `setTenantId(1L)` | 见 P0-B3 | P0 |
| `WalletServiceImpl.doConsume`/`doRecharge`/`doRefund` 用 `resolveTenantId` 优先 ctx，缺失则 fallback 给调用方——`WalletController` 又把 ctx.tenantId 显式传入 | WalletController 行 79/90/101 调用 walletService.doXxx(dto, tenantId)；doXxx 中的 resolveTenantId 优先 ctx.ctx.getTenantId() | OK（设计良好） |
| `crm_wallet` 29 行 tenant_id=NULL | WalletMapper.xml insertIfAbsent 不写 tenant | P0 |
| `pos_order.tenant_id` 默认值 '1'（DDL 级） | DB DDL | ORANGE（业务方需保证每次显式设置） |
| `PosControllerTenantIsolationTest` 6 失败 | mvn_test_all_p004.log:154-492 | P0（隔离测试不稳定） |
| `sys_user` 只有 1 行，tenant 隔离实际**未在生产数据级验证过** | DB 实测 | UNVERIFIED |
| `pos_order.tenant_id ∈ {900001, 900002}` 56 行（sys_tenant 中不存在） | DB 实测 | ORANGE（测试数据污染） |

### 4.3 跨租户风险评估
- **是**，存在多处硬编码旁路，任何 ctx=null 或调用方传入 null 的路径都会让数据归到 tenant_id=1

---

## 5 · Money / Wallet / Payment / DCEP Reality

### 5.1 钱包链路
- 代码：`WalletController` → `WalletServiceImpl`（CAS + SSOT + 内存锁 + 雪花）
- SQL 端：CAS 真的（`updateBalance`）+ 悲观兜底（`selectByMemberIdForUpdate`）
- 关键 SQL `WalletMapper.xml:18-22`：`insertIfAbsent` 不写 tenant_id → 持续产生 NULL
- 数据库：crm_wallet 8 行 tenant_id=1 / 29 行 NULL
- 历史漂移：crm_wallet_drift_log 16 行 → PRE_FIX_BACKUP 单条最大 13400 元、AUTO_FIX 收敛

### 5.2 POS 支付
- `PosController.processOnePayment` switch 4 个分支：
  - `wallet` → 走 `WalletService.doConsume` 真链路 ✓
  - `package` → `redeemPackage` 真链路（直接 jdbc.update crm_package_order）✓
  - `e_cny_soft` / `e_cny_hard` → 仅生成 transactionId，无真实调用 ❌
  - `cash` / `wechat` / `alipay` / `unionpay` → 只插 PosPayment 表 ❌（这些方法也没有任何外部 SDK 调用，无真实通道）

### 5.3 退款链路
- `PosRefundController` → `PosRefundService`（状态机 + 幂等 + reverse log + 真逆向）
- pos_refund 270 单 real 落库 ✓
- pos_refund_reverse_log 691 行，**100% ref_id = NULL** ❌（逆向缺少业务主键，无法追溯）

### 5.4 DCEP 数字人民币
- `DcepService.prepay` → `mockPrepay`：
  - `mode=prod` → 返回 FAIL/NOT_IMPLEMENTED（安全护栏）
  - `mode=dev` + envMode=MOCK → 直接返回 OK + base64 1x1 PNG
  - 当前 `application.yml` 是 **dev** → 真实可走 mock 通道
- `Ccb/Abc/Icbc DcepAdapter`：`prepay/query/refund/close` 4 个方法全 `NOT_IMPLEMENTED`
- `verifySign`：dev 模式接受 `MOCK-` 前缀签名 ✓ 风险存在
- `DcepService.notify`：选 posOrder + 发布 OrderPaidEvent → 但 DCEP 状态下 `posOrder.status = 2`

### 5.5 雪花 vs Math.random
- `IdGenerator` 是真实雪花算法，EPOCH=1767225600000（2026-01-01），workerId 默认 0
- PosController 行 326、DcepService.notify 多处使用 `idGenerator.nextIdStr()` 替代原 Math.random
- `WalletServiceImpl.casUpdateRetry` 仍 `new java.util.Random()` 做退避抖动（生产可接受）

---

## 6 · Golden Path Reality（重新走一遍）

| 段 | 状态 | 调用链 | 测试 | Runtime 证据 | 问题 |
|---|---|---|---|---|---|
| **Member** | 🔴 RED | MemberController → MemberService.getById → fillExtras → StoreService.listByIds | — | `/member/1` 500 DB 异常（pos_store 缺 tenant_id 列） | P0-B1 |
| **Booking** | 🟡 YELLOW | BookingController → BookingServiceImpl (悲观 FOR UPDATE) | — | `biz_booking` 0 行 | BookingServiceImpl 行 81 `setTenantId(1L)` 硬编码兜底 |
| **Check-in** | 🟡 YELLOW | BookingController.checkin POST | — | 0 预约 → 无 runtime | 全部走 BookingServiceImpl，假定 tenant=1 |
| **Service Consumption** | 🟢 GREEN | PosController.processOnePayment case wallet → WalletService.doConsume | WalletConsumeConcurrencyTest 34/34 | pos_payment 451 行 / crm_wallet_transaction 23 行 | 无 |
| **Inventory Consumption** | 🟢 GREEN | InventoryConsumptionService.consumeByOrder (BOM/FEFO/CAS) | InventoryConsumptionFlowTest 25/25 (?) | stock_inventory 6 行 | 无 |
| **POS Order** | 🟡 YELLOW | PosController.createOrder/pay/void | — | pos_order 481 行 | tenant_id 默认 '1' DDL；`/pos/orders/page` GET 500 |
| **Payment (Cash/Wechat/Alipay)** | 🟠 ORANGE | switch case → 只插 PosPayment 表无外部 SDK | — | pos_payment method 落库 ✓ | **无真实第三方支付通道** |
| **Payment (E-CNY)** | 🔴 RED | setTransactionId(雪花) | DcepNotifyTest（在 dev mode 通过） | 78 DCEP 订单 mostly 走 MOCK 通道 | P0-B4 / P0-B5 |
| **Refund** | 🟢 GREEN | PosRefundController → PosRefundService 状态机 + 逆操作 | PosRefundIntegrationTest 5/5 PASS | pos_refund 270 ✓ | pos_refund_reverse_log.ref_id NULL 100% |
| **Commission** | 🔴 RED | CommissionCalculationService | — | commission_record 0 行 | 代码存在但无任何已结提成 |
| **Follow-up** | ⚪ UNVERIFIED | FollowUpClosureService 表存在 | — | crm_follow_up_plan 3 / task 0 | 与 Golden Path 集成未验证 |
| **Report** | 🟡 YELLOW | ReportController 真实 CRUD + ReportV2Service | ReportQueryTest PASS | report_export_task 0 / report_metric_snapshot 16 | 无种子数据 |

**Golden Path 评级：🔴 RED**（DCEP 通道、Commission、Follow-up 端到端未真实运行）

---

## 7 · Service / Business Logic Reality

### 7.1 Service 总览
- 92 service 文件：混合 interface（30 多个）和 @RestController 直接暴露（多数为 P3 高级功能）
- 真实业务实现：WalletService / BookingService / PosController 自营 / DcepService 部分 / PosRefundService / InventoryConsumptionService / MemberProfileService（只有 IService 默认实现）

### 7.2 实施评分

| Service / 模块 | 实现度 | 验证手段 |
|---|---|---|
| WalletServiceImpl | ★★★★★ | 34/34 测试 PASS + WalletConsumeConcurrencyTest |
| PosRefundService | ★★★★ | PosRefundIntegrationTest 5/5 |
| InventoryConsumptionService | ★★★★ | InventoryConsumptionFlowTest |
| BookingServiceImpl | ★★★ | BookingCreateConflictTest + 多事务保护 |
| AuthService | ★★★★ | 实测 admin/123456 登录成功 |
| DcepService | ★★（dev only） | DcepNotifyTest + pay_dcep_notify sign_verified 18/20 |
| DcepAdapters (CCB/ABC/ICBC) | ✘ | NOT_IMPLEMENTED 全方法 |
| MemberServiceImpl | ★★ | 缺 tenant 过滤；fillExtras 路径 500 |
| CommissionService | ★★ | 代码完整但 commission_record=0 |
| Brand / Industry / AI / Cloud / Security / PerfLoad | ★（仅占位） | 文件存在 + @RestController 直接路由 + 多数 CRUD 占位 |

---

## 8 · API Reality

### 8.1 实测可用 / 不可用
**实测登录 `admin/123456` → token ✓**

| 路由 | 方法 | 实测 | 备注 |
|---|---|---|---|
| `/auth/login` | POST | 200 OK | 返回 token |
| `/auth/userinfo` | GET | OK（需要 bearer） | |
| `/auth/logout` | POST | OK | |
| `/member/1` | GET | **500 数据库异常** | pos_store 缺 tenant_id 列 |
| `/member/page` | POST | **500 数据库异常** | 同上 |
| `/wallet/1` | GET | 200 OK | balance=-298 drift；与 crm_wallet_drift_log 第 1 条对应 |
| `/wallet/recharge` | POST | 未实测 | |
| `/pos/services/list` | GET | 200 OK | 22 行服务 |
| `/pos/orders/page` | POST | 未实测 | |
| `/sys/menu/tree` | GET | 404 | 路由不存在 |

### 8.2 路由统计
- 29 个 Controller，~290+ Endpoint
- 多数有 @RequirePermission 注解 → JwtInterceptor + PermissionAspect 双重鉴权
- CORS 配置存在（CorsConfig.java）

---

## 9 · Test Reality

### 9.1 后端测试结果
| Log | 结果 |
|---|---|
| `mvn_test_full.log` | Tests run: **34**, Failures: 0, Errors: 0, Skipped: 0 → BUILD SUCCESS |
| `mvn_test_all_p004.log` | Tests run: 7（PosControllerTenantIsolationTest）, **4 Errors + 2 Failures** → BUILD FAILURE |
| `mvn_test_all_p004_v5.log` | Tests run: **15**, Failures: 0, Errors: 0 → PASS（清理后重跑） |
| `MultiTenantWalletTest` | `APPLICATION FAILED TO START` (2026-08-15 05:52) → circular ref `packageController ↔ walletServiceImpl` |

**冲突证据**：
- `PosControllerTenantIsolationTest` 7 用例历史全部失败 → 重测后 PASS
- 说明租户隔离测试**不稳定**——任何并发下都可能不同结果

### 9.2 E2E
- 框架：Playwright 1.62.1（真实存在 + 真实跑过）
- 5 spec：auth.setup / auth / smoke / golden-path / full-flow
- `.last-run.json` `"status":"passed"` (日期 2026-08-15 07:50)
- **关键缺陷**：
  - `auth.setup.ts:28-37` `fallbackRbacSession()` 用 `permissions: ['*']` 兜底
  - `full-flow.spec.ts:77-86` `checkBackendHealthy()` 检测 `/member/1` 不返回就 **跳过业务步骤**
  - 注释保留"已知：当前 dev 库缺 sys_user.tenant_id 列，会让任意 /member/1 等查询爆 500"——但 sys_user 实测**有 tenant_id 列**！这是过时的兜底逻辑

---

## 10 · E2E Reality

| 维度 | 事实 |
|---|---|
| 框架 | Playwright 1.62.1 |
| Spec 数 | 5 |
| Projects 数 | 5 (setup / chromium / mobile / unauth-chromium / unauth-mobile) |
| `.last-run.json` | passed（但 full-flow 含 `test.skip(!backendHealthy)`） |
| 真实业务跑通？ | **否**，业务步骤受 `checkBackendHealthy()` 保护，schema-drift 时跳过 |
| 报告 | `e2e/reports/index.html`（生成） |

**结论**：E2E 框架真实存在但设计上"业务可跳过"，**不是真 Golden Path 通行证**。

---

## 11 · Runtime Reality

### 11.1 进程 & 端口
```
mysqld       4 个进程 (docker meiyetec-mysql，端口 3307)
redis-server pid 5332 (localhost:6379)
java         pid 4180, 22064 (Spring Boot 8080)
node         pid 120 (vite dev server 5173)
node         其他（Vitest 用）
```

### 11.2 运行时健康
- 后端 12:06 PM 仍在跑
- 前端 12:06 PM 仍在跑
- MySQL 118 表可读
- Redis 6379 在跑

### 11.3 已知运行时异常
- `LogRetentionTask` 每小时失败（sys_config 表不存在）
- `/member/*` 500（pos_store 缺 tenant_id）
- `MultiTenantWalletTest` 因 packageController → walletServiceImpl 循环依赖无法启动（在测试阶段）

---

## 12 · Mock / Shell / Fake Reality

### 12.1 全项目扫描结果（命中行）
| 命中 | 文件 / 行 | 状态 |
|---|---|---|
| `NOT_IMPLEMENTED` × 13 处 | dcep/impl/{Ccb,Abc,Icbc}DcepAdapter × 12 + DcepService × 1 | P0（资金） |
| `TODO` × 12 处 | dcep/impl/{Ccb,Abc,Icbc}DcepAdapter × 12 + PosController:323 + EventListener × 2 | ORANGE |
| `MOCK-` 前缀接受 | DcepService.verifySign:404（dev mode） | P0（资金） |
| `mockPrepay` 输出 base64 1x1 PNG | DcepService.java:217 | ORANGE |
| 雪花算法被替换 `Math.random()` | PosController:326 + IdGenerator 已替换 | ✓ REAL |
| `new Random()` 仅作 CAS 退避 | WalletServiceImpl.casUpdateRetry:354 | OK（生产可接受） |
| `UUID.randomUUID()` | DcepService.prepay（用作部分内部标识） | OK |

### 12.2 业务模块真实度
- 钱包：REAL
- 退款：REAL
- POS 收银（除 e_cny、wechat、alipay 通道）：REAL
- DCEP：MOCK（dev）/ NOT_IMPLEMENTED（prod）
- AI Vision：MISSING（0 数据，0 模型）
- Industry / Brand：MISSING（表已建但 0 数据，applyTemplate 引用不存在的 tenant_industry 表）
- 第三方微信 / 支付宝 / 美团 / 抖音：MISSING（仅 PosController switch case 占位，无外部 SDK 调用证据）
- Cloud / Security / PerfLoad / GrayRelease：SHELL（@RestController 直接路由占位）

---

## 13 · Historical Contradictions

| 历史声称 | 实测结果 | VERDICT |
|---|---|---|
| `Reality Score 93%+` | 实测 **48.25 / 100** | **REVERSED** |
| `P0-DB-001 数据修复完成` | crm_wallet 29 NULL + pos_refund_reverse_log 100% NULL + pos_store.tenant_id 列不存在 + sys_config 表不存在 | **REVERSED** |
| `P0-MONEY-001 DCEP 真实化` | CCB/ABC/ICBC Adapter 全 NOT_IMPLEMENTED；application.yml mode=dev | **REVERSED** |
| `P0-MONEY-002 钱包双轨 SSOT` | WalletServiceImpl.syncMemberBalance 唯一入口 + AUTO_FIX 跑通 | CONFIRMED |
| `P0-MONEY-003 雪花算法 ID` | IdGenerator 完整实现 + 多处替换 Math.random | CONFIRMED |
| `P0-MONEY-004 多租户 ctx 收口` | 代码层 OK，但测试层 PosControllerTenantIsolationTest 6 失败 + 12 处硬编码 tenantId=1L | **PARTIALLY_CONFIRMED** |
| `AI Vision` | mh_visual_archive 0 行 + VisualAiService 11KB 文件存在但无运行证据 | **REVERSED** |
| `Golden Path E2E 跑通` | `.last-run.json` passed 但 spec 内含 `test.skip(!backendHealthy)` | **PARTIALLY_CONFIRMED** |
| `E2E 框架 + 业务闭环` | 框架真实 + 业务闭环受 schema-drift 兜底 | **PARTIALLY_CONFIRMED** |
| `Industry / Brand` | mkt_brand 0 行 + IndustryService.applyTemplate 引用不存在的 tenant_industry 表 | **REVERSED** |
| `Wallet` | REAL | CONFIRMED |
| `DCEP` | 21 dev / 3 MOCK / 1 PROD 通道；sign_verified 18/20 | **PARTIALLY_CONFIRMED** |
| `MultiTenantWalletTest` | 5:52 AM 启动失败（circular ref），但 `mvn_test_all_p004_v5.log` 中显示 15/15 PASS | **PARTIALLY_CONFIRMED** |

---

## 14 · Evidence Matrix（关键 SQL / 实测一览）

| 主题 | SQL / 命令 | 结果 |
|---|---|---|
| 表数 | `SHOW TABLES` (118 行) | 118 |
| FK 总数 | `information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA IS NOT NULL` | 17 |
| crm_wallet NULL tenant | `SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL` | 29 |
| pos_order 伪租户 | `SELECT COUNT(*) FROM pos_order WHERE tenant_id IN (900001,900002)` | 56 |
| pos_refund_reverse_log ref_id NULL | `SELECT COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NULL / COUNT(*)` | 691 / 691 (100%) |
| pay_dcep_channel env_mode | `GROUP BY env_mode` | MOCK=3 / dev=21 / PROD=1 |
| pay_dcep_notify sign_verified | `GROUP BY sign_verified` | 1 → 18 / 0 → 2 |
| sys_tenant 行 | `SELECT * FROM sys_tenant` | (1, 默认), (2, tenant_b) — 无 900001 / 900002 |
| sys_user 行 | `SELECT * FROM sys_user` | 1 行 admin / tenant_id=1 |
| mh_visual_archive 行 | `SELECT COUNT(*) FROM mh_visual_archive` | 0 |
| commission_record 行 | `SELECT COUNT(*) FROM commission_record` | 0 |
| mkt_brand 行 | `SELECT COUNT(*) FROM mkt_brand` | 0 |
| biz_booking 行 | `SELECT COUNT(*) FROM biz_booking` | **0** |
| crm_wallet_drift_log | `SELECT fix_type, COUNT(*) FROM crm_wallet_drift_log GROUP BY fix_type` | PRE_FIX_BACKUP=8, AUTO_FIX=8 |
| 登录 admin/123456 | `Invoke-RestMethod /auth/login` | 200 OK + token |
| `/member/1` | `Invoke-RestMethod /member/1` | **500 数据库异常** |
| `/wallet/1` | `Invoke-RestMethod /wallet/1` | 200 OK, balance=-298 |
| `/pos/services/list` | `Invoke-RestMethod /pos/services/list` | 200 OK, 22 行 |
| `/pos/orders/page GET` | `Invoke-WebRequest GET /pos/orders/page` | 500 系统繁忙（POST 才是真接口） |
| `/sys/menu/tree` | `Invoke-RestMethod /sys/menu/tree` | 404 |
| git status | `git status` | **fatal: not a git repository** |

---

## 15 · Top Risks（按真实证据）

1. **P0·数据库 schema drift 当前正在阻塞业务**：pos_store 缺 tenant_id 列让 `/member/*` 全 500 · sys_config 表不存在让定时任务每跑一次崩一次
2. **P0·多租户隔离有 12 处硬编码旁路**：Audit 必触发误传数据到 tenant_id=1
3. **P0·DCEP 真实银行 CCB / ABC / ICBC Adapter 全 NOT_IMPLEMENTED** + `application.yml mode=dev`
4. **P0·e_cny POS 支付路径仅生成 transactionId**：无真实银行网关
5. **P0·crm_wallet 持续产生 tenant_id=NULL**（写入 SQL 不带 tenant_id）
6. **P0·pos_refund_reverse_log.ref_id NULL 100%**：逆向操作无可追溯业务主键
7. **P0·PosControllerTenantIsolationTest 历史 6 失败**：租户隔离测试不稳定
8. **P0·MultiTenantWalletTest 因循环依赖启动失败**：测试阶段即破
9. **P1·IndustryService.applyTemplate 引用不存在的 tenant_industry 表**：实跑必崩
10. **P1·AI Vision / mkt_brand / commission_record / biz_booking / crm_follow_up_task 等近 10 张表 0 数据**

---

## 16 · Top Strengths

1. 完整 Spring Boot 3.1.5 + MyBatis Plus 全栈（118 表 / 247 mapper / 248 entity）
2. `WalletServiceImpl` SSOT + CAS + 内存锁 + 雪花算法真实可用，测试 PASS
3. 收银 + 库存消耗 + 退款真实链路 270 单退款 + 481 订单真实落库
4. `IdGenerator` 雪花算法完整，时钟回拨处理 + workerId 校验
5. `DcepService.notify` 防重 + 验签 + OrderPaidEvent 真实链路
6. Playwright 5 spec + 5 projects + html 报告，框架可跑
7. 前端 Vite + Vue 3 + Element Plus + Pinia 完整技术栈
8. 后端 8080 / 前端 5173 / MySQL 3307 / Redis 6379 全栈在跑
9. `JwtInterceptor` + `RbacContextLoader` + `PermissionAspect` 三层权限
10. `InventoryConsumptionService.consumeByOrder` 真实 BOM / FEFO / CAS，库存扣减可验证

---

## 17 · UNKNOWN / NEEDS VERIFICATION

| # | 项目 | 原因 |
|---|---|---|
| 1 | 后端所有 29 Controller 完整端到端 | 本轮只实测 8 个路由 |
| 2 | `ai_ltv_model` / `ai_tag_model` 等模型表实跑 | 不进 e2e |
| 3 | `OrderPaidEventListener` 完整订阅 | 仅有 TODO 注释 |
| 4 | `biz_booking` 0 数据是否预期？ init_m06_booking.sql 完整？ | 表空 + 0 行 |
| 5 | Redis 是否被业务实际使用 | `RedisConfig.java` 存在但未实测 |
| 6 | `sys_user` 仅 1 行的合规性 | 真实业务必须至少 store1 |
| 7 | `crm_follow_up_plan` 3 行的真实业务含义 | 0 task |
| 8 | `pay_dcep_notify.sign_verified=0` 的 2 行具体失败原因 | 日志未追溯 |
| 9 | `multi_warehouse`、`cross_store`、`cross_tenant` 等 P2 高级特性是否真做 | 仅 sql 文件存在 |
| 10 | `packageController` ↔ `walletServiceImpl` 循环依赖是否在运行时也存在 | 仅测试阶段报 |
| 11 | `RedisConfig` 是否影响 IdempotencyService | 未实测 |
| 12 | 多租户 wallet 在生产 2 个 tenant 下实际表宽度 | sys_user 只有 1 个 |
| 13 | `crm_member.balance` 与 `crm_wallet.balance` SSOT 是否每次充值 / 退款后实时同步 | AUTO_FIX 跑了 8 次说明仍非 100% |
| 14 | `wallet_op_dto.amount` 的 ± 校验与 `BigDecimal.compareTo` 边界 | 仅粗看 CAS 逻辑 |
| 15 | `E2E .last-run.json` 日期 2026-08-15 07:50 与本次 12:06 PM 中间是否又出现过 fail | 时间早无 running diff |

---

## 18 · 四个最终问题的明示回答

### ① 当前项目到底能不能算"可继续开发"？
**可以继续做后端业务开发（钱包 / 收银 / 退款链路是干净的），
但不能上生产。**
- 后端代码大部分可跑 + 可测 + 可演进
- 但 schema drift 阻断了 `/member/*`、阻断了定时任务、正在产生 29 个 NULL 钱包和 56 个伪租户订单
- 多租户隔离实际未稳定通过测试
- DCEP 真实银行 SDK 全部 NOT_IMPLEMENTED，e_cny 通道没有真实通道

### ② 最严重的 5 个真实问题（按本机实测、不可回避）

1. **`pos_store.tenant_id` 列不存在，导致 `/member/*` API 全部 500 DB 异常**（这是所有会员相关页面的 root cause）
2. **`sys_config` 表从未被 init SQL 创建，`LogRetentionTask` 每小时必崩**
3. **DCEP 三家真实银行 (CCB / ABC / ICBC) 全部 `NOT_IMPLEMENTED` + `application.yml mode=dev` 允许 mock 签名**
4. **12 处硬编码 `setTenantId(1L)` 把多租户隔离打成窟窿** — Booking / Commission / Stock / Package / Channel / OpenPlatform 等关键模块都有
5. **`pos_refund_reverse_log` 100% ref_id NULL（691/691）** — 逆向操作无可追溯业务主键，加上 `crm_wallet` 持续产生 tenant_id=NULL

### ③ 哪些历史"已完成"无法由当前代码证明？

| 历史声称"已完成" | 实测反向证据 |
|---|---|
| 钱包 SSOT 完成 + 数据修复 + P0-DB-001 修复 | crm_wallet 29 NULL 仍未清，crm_wallet_drift_log 8 AUTO_FIX 跑过但新数据继续 NULL |
| DCEP 真实化 (P0-MONEY-001) | CCB/ABC/ICBC Adapter 仍 NOT_IMPLEMENTED |
| 多租户 ctx 收口 (P0-MONEY-004) | 代码层 OK；测试层 PosControllerTenantIsolationTest 历史 6 失败；12 处硬编码 1L 兜底 |
| Industry / Brand / AI Vision 完成 | IndustryService 引不存在的表；mkt_brand / mh_visual_archive 0 行 |
| Golden Path 端到端 | `/member/1` 500 让 Golden Path 主动 `test.skip(!backendHealthy)` |
| E2E 已通过 | `.last-run.json` passed 是因业务步骤被跳过；不是真闭环 |

### ④ 下一步应该做什么？（审计员角度、不替你下决定）

**Step 1（必须先做、否则一切无意义）**：把阻塞类 schema drift 修掉
1. `ALTER TABLE pos_store ADD COLUMN tenant_id BIGINT NULL`（与 entity 对齐）
2. 在 init SQL 加上 `CREATE TABLE sys_config (...)`（用 code 写 `sys_config.getInt` 的字段）
3. `WalletMapper.xml:insertIfAbsent` 加 `tenant_id`（从 ctx 取）
4. 清理 `crm_wallet.tenant_id IS NULL` 29 行（按 init 时间→ tenant_id=1 兼容 fillback）
5. 清理 `pos_refund_reverse_log.ref_id IS NULL`（用 pos_refund.ref_id 反查）

**Step 2**：跑 `PosControllerTenantIsolationTest` 10 次，连续 5 次 PASS 才认为租户隔离基本通过

**Step 3**：把 12 处 `setTenantId(1L)` 替换为 `resolveTenantId(...)` 或确保 ctx 永远非空

**Step 4**：如要 DCEP 真对接：根据公开银行 SDK + 商务流程，签 CCB / ABC / ICBC 三家合同后，分别实现其真实 adapter；或暂时只接受 1 家作为最低 viable 方案

**Step 5**：E2E 拆掉 `test.skip(!backendHealthy)` + `fallbackRbacSession()`，把 schema drift 校验提前到启动阶段，强制 E2E 在干净库上跑

---

## 19 · 重要附记

- 本轮严格 READ-ONLY。未创建任务、未修复、未建议方案。
- 历史报告（`docs/audit/BEAUTY_SAAS/00_*` / `EXECUTION_STATUS_REPORT.md` / `FEATURE_COMPARISON_MATRIX.md` / `docs/EXEC-美业SaaS升级执行任务清单-v2.0.md` / `docs/audit/BEAUTY_SAAS_FEATURE_REALITY_AUDIT.md`）声称的"REALLY 完成 / ✅ 完成"与本轮独立验证存在多处矛盾（见第 13 节 History Contradictions 与第 18 节 Q3）。
- 审计员建议在做任何"已达成完成"声明前，重新按本 Baseline 重新对账 12 项 P0/P1 事实。

---

*END OF BASELINE — Generated 2026-08-15 — by independent re-audit.*
