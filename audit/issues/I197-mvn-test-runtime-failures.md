# I197 — `mvn test` 运行期失败 60 项 + `jwt.secret` 弱密钥导致 `@SpringBootTest` 上下文全军覆没

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**（阻断 CI 正式守门序列接入 —— 修完编译后，`mvn test` 仍红，且 148 个 error 的根因是**配置**问题而非代码问题）
> **发现日期**：2026-11-17（I196 编译修复完成后执行 `mvn test` 时暴露）
> **登记人**：Cursor
> **绑定基线**：HEAD `c37a549` + I196 编译修复（7 个测试文件，+42/−20）
> **验证等级**：**运行 VERIFIED**（两次 `mvn test` 全量实测，日志落证据目录）
> **证据目录**：`audit/evidence/F172-i186-test-compile-fix/`（`02` 默认 profile 日志 zip、`03` test profile 日志 zip、`04` 摘要）
> **关联**：**I196**（编译层问题，本条是其运行期遗留）、**I052**（同源历史登记）、**I195**（A 类「规格有、后端无」的同类形态）、**I184 / I185**（C 类权限码漂移）、**I193**（B 类本地库 tenant_id 脏数据）

---

## 0. 一句话描述

I196 把「编译不过」修掉之后，**123 个测试文件 / 1552 个用例第一次真正跑起来**，随即暴露两层问题：
**① 一个配置缺陷让 148 个 `@SpringBootTest` 用例根本起不来；② 剩下 60 个用例失败，分属 5 类，其中 6 个是「后端类根本不存在」。**

---

## 1. 根因一（单一配置缺陷，影响 148 个 error）

### 1.1 事实链

| 环节 | 事实 | 出处 |
|---|---|---|
| ① 测试配置里的默认密钥 | `jwt.secret: ${JWT_SECRET:meiyetec-test-only-minimum-32-chars-secret-key}` | `backend/src/test/resources/application.yml:17` |
| ② 宿主的弱值黑名单 | `PROD_DENYLIST` 含 `(?i)meiyetec`、`(?i)\btest-only\b`、`(?i)password[\d]*`、`(?i)123456` | `backend/src/main/java/com/meiyetec/util/JwtUtil.java:65-72` |
| ③ profile 判定 | 只有 `dev` / `test` profile 才降级为 WARN；**默认 profile → fail-fast** | `JwtUtil.java:89-102` |
| ④ 测试没激活 profile | 测试日志首行 `No active profile set, falling back to 1 default profile: "default"` | `03-mvn-test-test-profile.log` 起始段 |
| ⑤ 结果 | `JwtUtil` 初始化抛 `IllegalStateException` → `jwtUtil` bean 创建失败 → `jwtInterceptor` → `webConfig` 连锁失败 → **Spring 上下文加载失败** | 日志 `Caused by: java.lang.IllegalStateException: [JwtUtil] jwt.secret 命中弱值检测（pattern: (?i)meiyetec）` |

### 1.2 实测对照（同一份代码，仅改运行时 profile）

| 命令 | 用例数 | Failures | Errors | 结论 |
|---|---|---|---|---|
| `mvn test`（默认 profile） | 1503 | 23 | **171** | 148 个 error = 上下文加载失败 |
| `mvn test -Dspring.profiles.active=test` | **1552** | 37 | **23** | 上下文类 error 全部消失，用例数 +49（原本起不来的类开始执行） |

> **推论（可复现）**：只要测试运行不激活 `test` profile（或不注入合规 `JWT_SECRET`），**所有 `@SpringBootTest` 类必然全红**。这与生产代码无关，是**测试运行配置缺陷**。

### 1.3 修复方向（三选一，未执行）

| 方案 | 做法 | 影响面 | 备注 |
|---|---|---|---|
| A（推荐） | `src/test/resources/application.yml` 增加 `spring.profiles.active: test` | 仅测试资源 | 与 `JwtUtil` 的 dev/test 降级语义对齐，零业务风险 |
| B | 测试默认密钥换成不含 `meiyetec` / `test-only` 的随机串 | 仅测试资源 | 密钥需 ≥32 字节且不得命中黑名单；仍建议同时做 A |
| C | CI 侧固定注入 `JWT_SECRET` 环境变量 | CI 配置 | 能跑，但**本地开发仍会踩**，不解决根因 |

---

## 2. 根因二：60 个运行期失败用例分类

数据源：`03-mvn-test-test-profile.log`（`-Dspring.profiles.active=test`，1552 用例 / 37 failures / 23 errors / 3 skipped），失败分布在 **24 个测试类**。

### 2.1 A 类 —— 后端类根本不存在（6 项，presence 测试 `ClassNotFound`）

已在 `backend/src/main/java` 全量静态检索确认 **6 个类均不存在**：

| 失败测试 | 期望类 | 静态检索结果 |
|---|---|---|
| `AiModulesPresenceTest.ai_alias_controller_exposes_inventory_paths:27` | `com.meiyetec.controller.AiApiAliasController` | **NOT FOUND** |
| `FiscalModulesPresenceTest.fiscal_004_recharge_audit_controller_and_service_present:46` | `com.meiyetec.controller.RechargeAuditController` | **NOT FOUND** |
| `IotControllersPresenceTest.iot_device_controller_exposes_link_endpoints:17` | `com.meiyetec.controller.IotDeviceController` | **NOT FOUND** |
| `ReportControllersPresenceTest.report_caliber_controller_exists_and_exposes_endpoints:16` | `com.meiyetec.controller.ReportCaliberController` | **NOT FOUND** |
| `ReportControllersPresenceTest.report_task_controller_exists_and_exposes_endpoints:26` | `com.meiyetec.controller.ReportTaskController` | **NOT FOUND** |
| `SubsModulesPresenceTest.subs_001_subscription_service_and_endpoints:17` | `com.meiyetec.service.SubscriptionService` | **NOT FOUND** |

> **形态判定**：与 **I195**（前端调用、后端 3 条 `/commission/records*` 路由缺失）同族 —— **规格/前端有，后端没有**。
> ⚠️ 这类缺失**不能靠改测试掩盖**：测试断言的正是「模块应存在」的契约。
> **未确认**：这些模块是「未实现」还是「已改名/已迁移」，需逐个对上游规格核对（**本轮未做**）。

### 2.2 B 类 —— 本地库 / DDL 漂移

| 失败测试 | 现象 | 判定依据 |
|---|---|---|
| `RefundAuditWorkflowServiceE2eTest`（5 errors） | `bad SQL grammar [INSERT INTO pos_refund ...]` → 根因 `SQLSyntaxErrorException: Unknown column 'deleted' in 'field list'` | 实体 `com.meiyetec.entity.PosRefund` **无 `deleted` 字段**；仓库 DDL（`migrate_pos_008_refund_audit_chain_v2.sql`）亦未定义该列 → **测试 SQL 引用了不存在的列** |
| `WalletNullTenantBackfillTest.noNullTenantAnywhere` / `ssotIntact` | 断言「无 tenant_id IS NULL」不成立 | 与 **I193**（`pos_employee` 144 行 `tenant_id IS NULL`）同族脏数据问题 |
| `RefundReverseLogBackfillTest`（5）、`T06ReverseLogRefIdTest`（3） | 反向流水 `pos_refund_reverse_log` 的 `ref_id` 断言不成立 | 需逐条核对回填脚本口径（**本轮未做**） |

### 2.3 C 类 —— 权限码断言过期（8 errors，全在 `ReportQueryTest`）

| 失败用例 | 报错 |
|---|---|
| `dashboardSalesAggregation:93` / `dashboardDateFilter:128` / `dashboardStatusFilter:150` / `dashboardTenantIsolation:173` / `dashboardInvalidStore:277` | `需要权限码：report:dashboard` |
| `employeeCommissionAggregation:240` / `salesDailyAggregation:199` | `需要权限码：report:biz:list` |
| `dashboardNoTenant:264` | `未登录` |

> 与 **I184**（commission 权限码未绑定）/ **I185**（前后端权限码漂移）同族：**测试用的权限码已被真实权限体系取代**。

### 2.4 D 类 —— 期望值 vs 现行实现漂移（约 28 个用例）

| 测试类 | 数量 | 代表性断言差异（实测值 vs 期望值） |
|---|---|---|
| `LbsRouteOptimizerTest` | 7 | `scoreDistance_beyondMax_returnsNegative:163` 期望 true 实得 false（**距离超限当前返回 0.0 而非负数**）；`calcTravelFee_normalRange:457` 期望 `40.00` 实得 `0.00`；`scoreTimeWindow_nearBoundary:228` 期望 `60.0` 实得 `0.0`；`scoreDispatch_farDistance_lowScore:311` 期望负分实得 `0.0` |
| `BookingConfirmationTemplateResolverTest` | 4 | i18n 模板回退行为与预期不符 |
| `BookingConfirmationServiceTest` | 2 | 渠道聚合结果与预期不符 |
| `HubspotZohoAdapterTest` | 3 | 适配器行为与预期不符 |
| `BookingWaitlistScoreServiceTest` | 2 | `technicianMatch_unbounded_returns70` 期望 `60.0` 实得 `70.0`；`technicianMatch_preferUnspecified_returns60` 期望 `60.0` 实得 `70.0` |
| `CrmAdapterTest` | 1 | `mockFetch:50` 期望 3 实得 **4**（Mock 适配器样本数已变，呼应 EXT-24 §0.3） |
| `DcepAdapterFactoryTest` | 1 | `factory_has_three_adapters:29` 期望 3 实得 **5**（适配器注册数已变） |
| `GoogleMapsGeocodingTest` | 1 | `findPlaceUrl:59` 字段包含断言不成立 |
| `SysLoginLogServiceImplTest` | 1 | `clearBeforeDays_zeroOrNegative_returnsZero_withoutCalling_delete` |
| `TreatmentReportServiceTest` | 1 | `generate_abandoned_plan_records_abandoned_count` |
| `WalletConsumeConcurrencyTest` | 1 | `concurrentConsume`（耗时 46.7s，疑似并发/超时问题） |

> ⚠️ `LbsRouteOptimizerTest` 的「距离超限不扣分」「行程费恒为 0」两处**不像纯测试漂移，更像派单算法被改弱了**（打分下限被夹到 0、费用项返回 0）—— **需人工判定「是算法有意改版还是退化」**，本轮只登记不下结论。

### 2.5 E 类 —— 测试自身缺陷（5 errors）

| 测试类 | 现象 |
|---|---|
| `BookingCrossTenantStoreIsolationTest` | `JUnit @BeforeAll method 'void setupFixtures()' must be static unless the test class is annotated with @TestInstance(Lifecycle.PER_CLASS)` —— JUnit5 用法违规，**该类从未真正执行过** |
| `BookingCreateConflictTest` | `sequentialDoubleBooking:211` / `differentTimeSlotsSameEmployee:243` → `员工冲突` 业务异常与预期不符（1 failure + 2 errors） |
| `BookingRoomConflictTest` | `sequentialDoubleRoomBooking:208` / `differentRoomsSameTime:241` → 房间冲突判定与预期不符（1 failure + 2 errors） |

---

## 3. 影响

| 影响维度 | 描述 |
|---|---|
| **CI 守门序列** | `mvn test` 仍为红 → **无法**把 `mvn test`（替代 `mvn -D"maven.test.skip=true" package` 黑魔法）接入正式守门序列（I196 §5 ⑤ 的前置条件未达成） |
| **回归防线** | 1552 个用例中 **1492 绿 / 60 红**；红色部分**分属 5 类不同性质**，无法用「一红了之」处理，也不能一改了之 |
| **假绿风险** | 此前「编译不过 → 全红」掩盖了 60 个真实失败；修复编译后这些问题才第一次可见 —— 这是**风险暴露**而非新增缺陷 |
| **A 类的产品含义** | 6 个模块（AI 库存别名 / 财务充值审核 / IoT 设备 / 报表口径 / 报表任务 / SUBS 订阅）**后端无实现**，与 I195 同族，属产品缺口而非测试问题 |
| **B 类的环境含义** | 本地开发库与仓库 DDL 已漂移（`pos_refund.deleted`），**本地环境不可信** → 任何以本地库为基准的验证结论都需降级 |
| **生产影响** | **无**。本条全部落在测试代码与测试环境，不影响 `mvn compile` / `-DskipTests package` / 生产运行 |

---

## 4. 修复方向（建议分 3 批，本条不执行）

| 批次 | 内容 | 优先级 | 说明 |
|---|---|---|---|
| **第 1 批 · 环境** | 修 §1.3 的 A/B/C（测试 profile 或合规 `JWT_SECRET`） | **P1** | 成本最低、收益最大：一改就能消掉 148 个 error，让真实信号浮出水面 |
| **第 2 批 · 环境对齐** | 本地库补齐 DDL（`pos_refund.deleted` 等）、清理 I193 类脏数据 | P2 | 让 B 类失败可被正确判定 |
| **第 3 批 · 逐类定性** | A 类 6 个模块：**先定性是「未实现」还是「改名」**，再决定补实现还是改测试；C 类 8 个：统一权限码口径；D 类 28 个：**逐条判定「测试过时」还是「实现退化」**（尤其 Lbs 派单算法两项）；E 类 5 个：修测试自身缺陷 | P1（随模块排期） | **不得**用「删断言 / 注释用例」的方式转绿 |

> **纪律**：本条**只登记**。修复时**禁止**为转绿而删除断言、注释用例、放宽生产可见性。

---

## 5. 边界声明

- 本条**仅登记**：本轮**未修改任何业务代码、未修改任何测试代码、未写库、未执行 DDL、未新增入库脚本、未新增可执行脚本**。
- 全部事实来自两次 `mvn test` 的真实输出（1552 用例全量执行），日志落 `audit/evidence/F172-i186-test-compile-fix/`。
- 测试连接的是**本机开发库** `jdbc:mysql://localhost:3307/meiyetec`（`src/test/resources/application.yml`），**未连接生产库、未打开浏览器**。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验证页面及其数据接口。
- §2.4 中「派单算法是否退化」「A 类 6 个模块的定性」**均标记为未确认**，本轮未下结论。
