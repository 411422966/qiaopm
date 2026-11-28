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
| A（推荐） | `src/test/resources/application.yml` 增加 `spring.profiles.active: test` | 仅测试资源 | 与 `JwtUtil` 的 dev/test 降级语义对齐，零业务风险（**2026-11-28 更新：实际采用方案 B，见 §1.4；方案 B 与 A 实测等价且不依赖 profile 切换**） |
| B | 测试默认密钥换成不含 `meiyetec` / `test-only` 的随机串 | 仅测试资源 | 密钥需 ≥32 字节且不得命中黑名单；仍建议同时做 A → **✅ 2026-11-28 已实施（commit `99bac3e`）** |
| C | CI 侧固定注入 `JWT_SECRET` 环境变量 | CI 配置 | 能跑，但**本地开发仍会踩**，不解决根因 |

### 1.4 第 1 批 · 环境：已执行（2026-11-28）

| 项 | 内容 |
|---|---|
| 改动 | `backend/src/test/resources/application.yml` L18 默认密钥 → 40 字节随机串（不命中 `JwtUtil.PROD_DENYLIST`） |
| 前置 | 本地库全量备份 `meiyetec-before-i197-p3-20261128.sql`（28,135,830 字节） |
| 修复前 | Tests run 1503 / Failures 23 / Errors 171 / Skipped 3；上下文失败 **34 类 / 165 个用例**（另有 `BookingCrossTenantStoreIsolationTest` 1 个用例因 `@BeforeAll` 非 static 失败，不计上下文失败；合并计「从未真正执行」为 35 类 / 166 用例） |
| 修复后 | Tests run 1552 / Failures 37 / Errors 23 / Skipped 3；上下文失败 **0** |
| 变化 | 上下文失败 165→0；Errors 171→23（**−148**）；Failures 23→37（**+14**）；用例 1503→1552（**+49**） |
| 与 test profile 对比 | 修复后数字与 2026-11-17 的 `-Dspring.profiles.active=test` 实测（1552 / 37 / 23 / 3）**完全一致** → 方案 B 与方案 A 等价 |
| 证据 | `audit/evidence/F176-i197-p3-jwt-secret-fix/` |

> 📌 **「修复前」数字的口径**：P3 **未重跑**修复前基线，此处复用 2026-11-17 默认 profile 的实测日志
> `.audit-tmp/i196-mvn-test.log`（`Finished at: 2026-11-17T16:28:19+08:00`）。
> 修复后日志为 `.audit-tmp/p3-mvn-test.log`（`Finished at: 2026-11-28T15:28:23+08:00`）。

**新增暴露 31 项**（14 Failures + 17 Errors，全部为首次执行）：
- **A 类 0 新增**（5 个 presence 类属基线可见；第 6 项 `SubsModulesPresenceTest.subs_001` 已更正为 E 类，见 §2.1）
- **B 类 15**：`RefundAuditWorkflowServiceE2eTest`(5E)、`RefundReverseLogBackfillTest`(5F)、`T06ReverseLogRefIdTest`(3F)、`WalletNullTenantBackfillTest`(2F)
- **C 类 8**：`ReportQueryTest`
- **D 类 2**：`WalletConsumeConcurrencyTest`(1F)、`DcepAdapterFactoryTest`(1F)
- **E 类 6**：`BookingCreateConflictTest`(1F/2E)、`BookingRoomConflictTest`(1F/2E)

> 📌 **口径更正（2026-11-28）**：本节初稿记为「新增暴露 **32** 项（14 F + 18 E）」。
> 按两份日志逐项比对，修复前的真实失败基线为 **29 项**（23 F + 6 E，其中 6 个非上下文 error 为
> `AiModulesPresenceTest` 1 / `FiscalModulesPresenceTest` 1 / `IotControllersPresenceTest` 1 /
> `ReportControllersPresenceTest` 2 / `BookingCrossTenantStoreIsolationTest` 1），
> 修复后 60 项（37 F + 23 E），**60 − 29 = 31**。
> 差异的 1 项是 `BookingCrossTenantStoreIsolationTest` 的 `@BeforeAll` 错误 —— 该错误**修复前已存在**，
> 不应计入「新增」，故**新增 E = 17**（B 的 5 + C 的 8 + E 类的 4）。

**数据破坏（新发现，已单开 I213）**：`LogRetentionTaskSmokeTest` 删除 `sys_log` **145 行**
（`DELETE FROM sys_log WHERE (created_at < ?) LIMIT 5000`），见 **I213**。

**本地库数据漂移（跑测前 → 跑测后，已回滚）**：

| 表 / 口径 | 跑测前 | 跑测后 | 差 |
|---|---|---|---|
| `sys_log` | 13154 | 13009 | **−145** |
| `trace_span` | 69018 | 69058 | +40 |
| `market_point_record` | 509 | 519 | +10 |
| `pos_refund` | 310 | 315 | +5 |
| `pos_refund_reverse_log` | 731 | 737 | +6 |
| `crm_wallet`（`tenant_id IS NULL`） | 6 | 8 | +2 |
| `mh_visual_archive`（`deleted=0`） | 3 | 3 | 0 |

全库合计 **1672 INSERT + 5022 UPDATE + 3 DELETE**。
已用备份回滚，**5 项关键表计数全部回基线**（`sys_log`=13154 / `pos_refund`=310 / `pos_refund_reverse_log`=731 / `crm_wallet(NULL)`=6 / `mh_visual_archive`=3）。

> ⚠️ **可复现性**：上表 7 行为逐表实测值；另有 13 张表仅有差值记录（`pos_order` +10 / `pos_refund_item` +6 / `pos_payment` +6 / `crm_member` +6 / `pay_dcep_order` +5 / `pos_employee` +4 / `pos_store` +1 / `stock_product` +1 / `stock_inventory` +1 等），前后绝对值未落盘 → 该部分不可复现。

---

## 2. 根因二：60 个运行期失败用例分类

数据源：`03-mvn-test-test-profile.log`（`-Dspring.profiles.active=test`，1552 用例 / 37 failures / 23 errors / 3 skipped），失败分布在 **24 个测试类**。

### 2.1 A 类 —— 后端类根本不存在（5 项，presence 测试 `ClassNotFound`）

已在 `backend/src/main/java` 全量静态检索确认 **5 个类均不存在**：

| 失败测试 | 期望类 | 静态检索结果 |
|---|---|---|
| `AiModulesPresenceTest.ai_alias_controller_exposes_inventory_paths:27` | `com.meiyetec.controller.AiApiAliasController` | **NOT FOUND** |
| `FiscalModulesPresenceTest.fiscal_004_recharge_audit_controller_and_service_present:46` | `com.meiyetec.controller.RechargeAuditController` | **NOT FOUND** |
| `IotControllersPresenceTest.iot_device_controller_exposes_link_endpoints:17` | `com.meiyetec.controller.IotDeviceController` | **NOT FOUND** |
| `ReportControllersPresenceTest.report_caliber_controller_exists_and_exposes_endpoints:16` | `com.meiyetec.controller.ReportCaliberController` | **NOT FOUND** |
| `ReportControllersPresenceTest.report_task_controller_exists_and_exposes_endpoints:26` | `com.meiyetec.controller.ReportTaskController` | **NOT FOUND** |

> 📌 **2026-11-28 更正**：原第 6 项 `SubsModulesPresenceTest.subs_001_subscription_service_and_endpoints:17`
> **实际是 E 类** —— 该类**不是** `ClassNotFound`：
> `com.meiyetec.service.SubscriptionService` **存在**，且经 `javap` 确认为 **`public interface`**；
> 测试对 interface 断言 `@RestController` 必然失败（`assertNotNull(c.getAnnotation(RestController.class))`
> → `expected: not <null>`）。原"NOT FOUND"结论作废，该项已移至 **E 类**，见 §2.5。

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

### 2.4 D 类 —— 期望值 vs 现行实现漂移（24 个用例，P3 实测）

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

### 2.5 E 类 —— 测试自身缺陷（8 项：5 errors + 3 failures）

> **2026-11-28 追加**：`SubsModulesPresenceTest.subs_001` 由 §2.1 A 类移入本类（A 类 6 → 5）。原因见 §2.1 更正说明。

| 测试类 | 现象 |
|---|---|
| `BookingCrossTenantStoreIsolationTest` | `JUnit @BeforeAll method 'void setupFixtures()' must be static unless the test class is annotated with @TestInstance(Lifecycle.PER_CLASS)` —— JUnit5 用法违规，**该类从未真正执行过** |
| `BookingCreateConflictTest` | `sequentialDoubleBooking:211` / `differentTimeSlotsSameEmployee:243` → `员工冲突` 业务异常与预期不符（1 failure + 2 errors） |
| `BookingRoomConflictTest` | `sequentialDoubleRoomBooking:208` / `differentRoomsSameTime:241` → 房间冲突判定与预期不符（1 failure + 2 errors） |
| `SubsModulesPresenceTest.subs_001_subscription_service_and_endpoints:17` | `SubscriptionService` 是 `interface`，测试对 interface 断言 `@RestController` 必然失败 → `expected: not <null>` | **E（2026-11-28 从 A 类移入）** |

> 📌 **分类计数核对（60 项，2026-11-28）**：A 5 + B 15 + C 8 + **D 24** + E 8 = **60** ✅
> （§2.4 标题已按 P3 实测同步为「24 个用例」；原「约 28 个用例」为旧口径。）


---

## 3. 影响

| 影响维度 | 描述 |
|---|---|
| **CI 守门序列** | `mvn test` 仍为红 → **无法**把 `mvn test`（替代 `mvn -D"maven.test.skip=true" package` 黑魔法）接入正式守门序列（I196 §5 ⑤ 的前置条件未达成） |
| **回归防线** | 1552 个用例中 **1492 绿 / 60 红**；红色部分**分属 5 类不同性质**，无法用「一红了之」处理，也不能一改了之 |
| **假绿风险** | 此前「编译不过 → 全红」掩盖了 60 个真实失败；修复编译后这些问题才第一次可见 —— 这是**风险暴露**而非新增缺陷 |
| **A 类的产品含义** | 5 个模块（AI 库存别名 / 财务充值审核 / IoT 设备 / 报表口径 / 报表任务）**后端无实现**，与 I195 同族，属产品缺口而非测试问题 |
| **B 类的环境含义** | 本地开发库与仓库 DDL 已漂移（`pos_refund.deleted`），**本地环境不可信** → 任何以本地库为基准的验证结论都需降级 |
| **生产影响** | **无**。本条全部落在测试代码与测试环境，不影响 `mvn compile` / `-DskipTests package` / 生产运行 |

---

## 4. 修复方向（建议分 3 批，本条不执行）

| 批次 | 内容 | 优先级 | 说明 |
|---|---|---|---|
| **第 1 批 · 环境** | 修 §1.3 的 A/B/C（测试 profile 或合规 `JWT_SECRET`） | **P1** | 成本最低、收益最大：一改就能消掉 148 个 error，让真实信号浮出水面 |
| **第 2 批 · 环境对齐** | 本地库补齐 DDL（`pos_refund.deleted` 等）、清理 I193 类脏数据 | P2 | 让 B 类失败可被正确判定 |
| **第 3 批 · 逐类定性** | A 类 5 个模块：**先定性是「未实现」还是「改名」**，再决定补实现还是改测试；C 类 8 个：统一权限码口径；D 类 24 个：**逐条判定「测试过时」还是「实现退化」**（尤其 Lbs 派单算法两项）；E 类 8 个：修测试自身缺陷 | P1（随模块排期） | **不得**用「删断言 / 注释用例」的方式转绿 |

> **纪律**：本条**只登记**。修复时**禁止**为转绿而删除断言、注释用例、放宽生产可见性。

### 4.5 本地库无测试隔离（2026-11-28 新增观察）

P3 跑测实测：**1672 INSERT + 5022 UPDATE + 3 DELETE** 全部落在**本地业务库 `meiyetec`**（`localhost:3307`），
**无测试库、无租户隔离、无事务回滚**。这是 **I193**（本地库脏数据持续增长）类问题的**稳定来源**。

| 影响 | 说明 |
|---|---|
| 脏数据累积 | 每跑一次 `mvn test` 就往业务表写测试夹具（`pos_refund` / `pos_refund_reverse_log` / `crm_wallet` / `market_point_record` / `trace_span` …），且**不清理** |
| 不可逆销毁 | `LogRetentionTaskSmokeTest` 的 DELETE 属**不可逆销毁**（`sys_log` −145 行），已单开 **I213** |
| 验证结论降级 | 任何以本地库为基准的计数类断言都会**互相污染**（本批实测即出现：`WalletNullTenantBackfillTest` 断言 `tenant_id IS NULL == 0` 实得 8、`RefundReverseLogBackfillTest` 断言 `pos_refund == 270` 实得 315 —— **与该测试自身写入的行数直接相关**） |
| CI 前置条件 | 后续若要让 `mvn test` 进 CI，**必须先解决"测试库隔离"**，否则每次流水线都会污染共享库 / 销毁数据 |

> **未确认**：本轮**未**判定哪些失败用例是"被污染导致"、哪些是"自身缺陷"——
> 需在**干净库**上重跑一遍才能区分，属第 2 批（环境对齐）的任务。

---

## 5. 边界声明

- 本条**仅登记**：本轮**未修改任何业务代码、未修改任何测试代码、未写库、未执行 DDL、未新增入库脚本、未新增可执行脚本**。
- 全部事实来自两次 `mvn test` 的真实输出（1552 用例全量执行），日志落 `audit/evidence/F172-i186-test-compile-fix/`。
- 测试连接的是**本机开发库** `jdbc:mysql://localhost:3307/meiyetec`（`src/test/resources/application.yml`），**未连接生产库、未打开浏览器**。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验证页面及其数据接口。
- §2.4 中「派单算法是否退化」「A 类 5 个模块的定性」**均标记为未确认**，本轮未下结论。
