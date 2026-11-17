# I196 — 后端测试源文件无法编译，`mvn test` 整模块不可用

> **状态**：**Fixed（编译层已修复，2026-11-17；运行期遗留转 I197）**
> **严重度**：**P2**（不阻断主代码编译与打包；阻断的是**测试验证能力**与 CI 的 backend test job）
> **发现日期**：2026-11-17（I186 第 1 批落地后执行 `mvn test-compile` 时截获）
> **修复日期**：2026-11-17（用户决策「全修 7 个」后执行）
> **登记人**：Cursor
> **绑定基线**：HEAD `c37a549`（`fix(datascope): I186 第 1 批——范围解析基建 + 缺省注入 + 专项修复（G0/G1/专项1/专项2/Q1）`）
> **验证等级**：**修复前 = 静态 VERIFIED**（编译器输出即事实，含行号与「需要/找到」参数签名）；**修复后 = 运行 VERIFIED**（`mvn test-compile` 实测 EXIT=0；`mvn test` 实测 1552 用例执行，37 failures / 23 errors）
> **证据目录**：`audit/evidence/F172-i186-test-compile-fix/`（01 修复前编译日志、02/03 两次 `mvn test` 全量日志、04 摘要）
> **关联**：**I186**（第 1 批「补 3」的验证绕过测试编译）、**I161**（Maven 打包问题）、**I052**（2026-09-14 已登记同一现象的旧版）、**I103**（`PluginRuntimeTest` 编译失败，已单独登记）、**I197**（修复后暴露的运行期失败与 `jwt.secret` 弱密钥拦路虎）

---

## 0. 一句话描述

`backend` 模块 **123 个**测试源文件中，**7 个**因引用**已删除 / 已变更**的 API 而编译失败，共 **29 个编译错误**；
`mvn test-compile` 直接 BUILD FAILURE → **`mvn test` 整模块不可用**，CI 的 backend 测试步骤必然红。

---

## 1. 事实（编译器输出）

执行命令（`backend/` 目录）：

```
mvn test-compile "-Dmaven.test.skip=false"
```

结果：`EXIT=1`，`[INFO] 29 errors`，`[INFO] BUILD FAILURE`，
失败 goal 为 `org.apache.maven.plugins:maven-compiler-plugin:3.11.0:testCompile (default-testCompile)`。

日志原始输出：`D:\QiaoPM\.audit-tmp\i186-testcompile.log`（临时目录，非正式证据目录）。

### 1.1 错误分布（按文件）

| # | 测试文件 | 错误行 | 错误类型 | 涉及的业务类 |
|---|---|---|---|---|
| 1 | `service/LbsRouteOptimizerTest.java` | 245–247, 283–285, 317–319 | `private` 访问控制（6 个字段 × 3 组） | `LbsRouteOptimizer.DispatchConfig` |
| 2 | `plugin/PluginRuntimeTest.java` | 89, 100, 112, 131 | 构造器参数数量不匹配 | `PluginRuntimeService` |
| 3 | `service/BookingWaitlistScoreServiceTest.java` | 38, 42 | `private` 访问控制 + 方法不存在 | `BookingWaitlistScoreService` / `TenantContext` |
| 4 | `service/RefundAuditWorkflowServiceE2eTest.java` | 71, 95 | 方法不存在（2 处） | `TenantContext` / `Result` |
| 5 | `service/InventoryConsumptionFlowTest.java` | 48 | 构造器参数数量不匹配 | `InventoryConsumptionService` |
| 6 | `service/InventoryConsumptionBomMissingTest.java` | 62 | 构造器参数数量不匹配 | `InventoryConsumptionService` |
| 7 | `service/AttendIsLateAlgorithmTest.java` | 22 | 构造器参数数量不匹配 | `AttendanceConfigServiceImpl` |

### 1.2 三类根因

#### 根因 A：Service 构造器新增依赖，测试仍用旧签名（3 个文件 / 7 处）

| 测试调用 | 编译器要求的签名 | 测试实际传入 |
|---|---|---|
| `InventoryConsumptionService` | 8 参，末尾多 `StockDualVerifyService` | 7 参 |
| `PluginRuntimeService` | 3 参：`PluginRuntimeProperties, PluginPersistenceService, PluginTenantService` | 1 参 |
| `AttendanceConfigServiceImpl` | 2 参：`AttendanceConfigMapper, DataScopeService` | 1 参 |

#### 根因 B：内部字段/类被收紧为 `private`，测试直接字段访问（2 个文件 / 22 处）

- `LbsRouteOptimizer.DispatchConfig` 的 `wDistance / wSkill / wRating / wLoad / wTime / minDispatchScore` 全部 `private` → 测试在 3 处构造点直赋 → 编译失败。
- `BookingWaitlistScoreService.jdbc` 为 `private` → 测试直访 → 编译失败。

#### 根因 C：方法被移除 / 改名，测试仍在调用（2 个文件 / 3 处）

| 调用点 | 缺失方法 | 所在类 |
|---|---|---|
| `BookingWaitlistScoreServiceTest.java:42` | `setStoreId(long)` | `com.meiyetec.security.TenantContext` |
| `RefundAuditWorkflowServiceE2eTest.java:71` | `setStoreId(java.lang.Long)` | `com.meiyetec.security.TenantContext` |
| `RefundAuditWorkflowServiceE2eTest.java:95` | `isSuccess()` | `com.meiyetec.util.Result` |

---

## 2. 与 I052 / I103 的关系（重要：不是重复登记）

本条**不是**凭空新增的问题，而是对既有事实的**重新定级 + 归属澄清**：

| 对比项 | I052（2026-09-14 登记） | I196（本条） |
|---|---|---|
| 覆盖文件 | **6** 个 | **7** 个（多出 `AttendIsLateAlgorithmTest`） |
| 错误数 | 28 | 29 |
| 归属 | 登记于 EXT-19 审计期，定级 PARTIAL-BLOCKER，Fix Phase 标为 Post-Audit | 归属 **I186 第 1 批之后**，定级 P2，明确「先调查再逐个修」 |
| 处置 | 未修 | 未修 |

**关键新增事实**：`AttendIsLateAlgorithmTest.java:22` 的编译错误是 **I186 第 1 批 commit `c37a549` 新引入的**，
此前不存在。证据：

```
git show c37a549 -- backend/src/main/java/com/meiyetec/service/impl/AttendanceConfigServiceImpl.java
+import com.meiyetec.security.DataScopeService;
+    private final DataScopeService dataScopeService;
```

该类由 `@RequiredArgsConstructor` 生成构造器 → 从 1 参变 2 参 → 测试 `new AttendanceConfigServiceImpl(mapper)` 失配。
→ **其余 6 个文件的历史遗留（与 I052 / I103 同源），第 7 个是本批的责任，必须由本批闭环。**

---

## 3. 影响

| 影响维度 | 描述 |
|---|---|
| **测试验证能力** | 任何改动都**无法用 `mvn test` 验证** —— 全量编译阶段就失败，`-Dtest=X` 也救不了（编译失败发生在选择用例之前） |
| **CI** | backend job 的 `mvn test` 步骤必红；本仓当前靠 `mvn -D"maven.test.skip=true" package` 绕过（I103 §1.3 记录的做法），属于**未入正式守门序列的黑魔法** |
| **I186 第 1 批的验证** | 第 1 批的 22 条断言无法走 `mvn test`，只能**另写反射运行器**绕过测试编译（用户决策中「补 3 的 Q-E 纵深防御验证」即以反射方式完成） |
| **主代码与打包** | **不受影响** —— `mvn compile` / `mvn -DskipTests package` 正常；仅 `testCompile` 阶段失败 |
| **回归防线** | 已存在的 123 个测试文件**全部未被执行**，其中 116 个本身不报错也拿不到运行结论 → 长期处于「写了但没跑过」状态 |

---

## 4. 修复方向（需先调查，本条不修）

| 步骤 | 内容 |
|---|---|
| ① 逐类确认「是测试过时，还是主代码 API 应当保留」 | 尤其 `TenantContext.setStoreId` 与 `Result.isSuccess()`：**是方法被误删，还是有意收紧 API？** 若属误删 → 修主代码；若有意的 API 收敛 → 改测试 |
| ② 构造器类（3 个文件） | 补齐缺失依赖参数：`InventoryConsumptionService` 补 `StockDualVerifyService`（mock）；`PluginRuntimeService` 补 `PluginPersistenceService` + `PluginTenantService`（mock，I103 §2.1 已给出参考写法）；`AttendanceConfigServiceImpl` 补 `DataScopeService`（mock） |
| ③ `private` 字段类（2 个文件） | 优先**改测试**（改用构造器 / builder / setter）；若这些字段本就该被测试直接配置，则需为测试放宽可见性或提供工厂方法 —— **不得为迁就测试而放宽生产代码可见性** |
| ④ 方法缺失类（2 个文件） | 按 ① 的结论二选一 |
| ⑤ 验证门槛 | 修复后必须 `mvn test-compile` 退出码 0 且 `mvn test` 全绿，才能声称「测试可用」；并把 `mvn test`（而非 `-Dmaven.test.skip=true`）接入 CI 正式守门序列 |

> ⚠️ **纪律**：修复**只允许改测试代码**（或按 ① 的结论修主代码 API），**不得**为了让编译通过而删除断言、注释掉用例或放宽生产可见性。

---

## 5. 边界声明（登记阶段）

- 登记阶段**仅登记**：**未修改任何业务代码、未修改任何测试代码、未写库、未执行 DDL、未新增入库脚本**。
- 全部事实来自 `mvn test-compile` 的编译器输出（可复现），行号与签名均取自原始报错文本。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验证页面及其数据接口。
- 未打开浏览器、未连生产库。

---

## 6. 修复结果（2026-11-17，用户决策「全修 7 个」）

### 6.1 逐类判定（先调查再改，结论：**7 个文件全部是测试过时，无一需要改主代码**）

| 测试文件 | 编译器报错 | 主代码现状 | 判定 | 采用的修复 |
|---|---|---|---|---|
| `AttendIsLateAlgorithmTest.java:22` | 构造器参数不匹配 | `AttendanceConfigServiceImpl`（`@RequiredArgsConstructor`）自 I186 第 1 批 `c37a549` 起新增 `private final DataScopeService dataScopeService`，构造器 1 参 → 2 参 | 测试过时 | 第 2 参传 `mock(DataScopeService.class)`；被测的 `isLate / isEarlyLeave / getAllowLateMinutes / getActiveConfig` **均不经过** `dataScopeService`（见 `AttendanceConfigServiceImpl.java:31-63`），语义不变 |
| `InventoryConsumptionFlowTest.java:48`<br>`InventoryConsumptionBomMissingTest.java:62` | 构造器参数不匹配 | `InventoryConsumptionService`（`@RequiredArgsConstructor`）8 参，末尾 `private final StockDualVerifyService dualVerifyService`（`InventoryConsumptionService.java:55`） | 测试过时 | 补 `mock(StockDualVerifyService.class)`；被 mock 的方法 `createPendingRecordIfNeeded` 为 void → mock 默认 no-op，不改变断言 |
| `PluginRuntimeTest.java:89/100/112/131` | 构造器参数不匹配 | `PluginRuntimeService` 3 参构造器（`PluginRuntimeService.java:56-62`） | 测试过时 | 补 `mock(PluginPersistenceService.class)` + `mock(PluginTenantService.class)`（同 I103 §2.1 参考写法） |
| `LbsRouteOptimizerTest.java:245-247/283-285/317-319` | private 字段直访（18 处） | `LbsRouteOptimizer.DispatchConfig` 带 `@Data`（`LbsRouteOptimizer.java:50-62`）→ **setter 一直存在** | 测试过时 | 改用 `setWDistance / setWSkill / setWRating / setWLoad / setWTime / setMinDispatchScore`；写入值与字段默认值完全相同 → 行为不变 |
| `BookingWaitlistScoreServiceTest.java:38` | private 字段直访 | `BookingWaitlistScoreService.jdbc` 是 `@Autowired` **字段注入**（`BookingWaitlistScoreService.java:48-49`），无构造器可传 | 测试过时 | 改用 `ReflectionTestUtils.setField(svc, "jdbc", jdbc)`（`spring-boot-starter-test` 已含 spring-test，pom.xml:195-199）→ **未为迁就测试放宽生产可见性** |
| `BookingWaitlistScoreServiceTest.java:42`<br>`RefundAuditWorkflowServiceE2eTest.java:71` | 方法不存在 `setStoreId` | `TenantContext` 只有 `setStoreIds(Set<Long>)`（`TenantContext.java:53`），`getStoreId()` 是**派生 getter**（`:56-59`） | **主代码有意 API 收敛**（非误删） | 改用 `ctx.setStoreIds(Set.of(1L))`；`getStoreId()` 仍返回 1 |
| `RefundAuditWorkflowServiceE2eTest.java:95` | 方法不存在 `isSuccess()` | `Result` 用 `code` 字段（`@Data` → `getCode()`），成功码 200（`Result.java:17-23`） | **主代码有意 API 收敛** | 改用 `r1.getCode() == 200` |

### 6.2 改动清单（**仅测试代码，`backend/src/main/**` 零改动**）

```
backend/src/test/java/com/meiyetec/service/AttendIsLateAlgorithmTest.java          +7 / -1
backend/src/test/java/com/meiyetec/service/InventoryConsumptionFlowTest.java       +4 / -1
backend/src/test/java/com/meiyetec/service/InventoryConsumptionBomMissingTest.java  +4 / -1
backend/src/test/java/com/meiyetec/plugin/PluginRuntimeTest.java                   +9 / -4
backend/src/test/java/com/meiyetec/service/LbsRouteOptimizerTest.java              +18 / -9
backend/src/test/java/com/meiyetec/service/BookingWaitlistScoreServiceTest.java    +6 / -2
backend/src/test/java/com/meiyetec/service/RefundAuditWorkflowServiceE2eTest.java   +5 / -2
合计 +42 / -20（git diff --stat）
```

> 纪律遵守：**未删除任何断言、未注释任何用例、未放宽任何生产代码可见性、未删除任何生产方法**。

### 6.3 运行验证（实测，非推断）

| 命令 | 修复前 | 修复后 |
|---|---|---|
| `mvn test-compile`（backend/） | EXIT=1，`29 errors`，BUILD FAILURE | **EXIT=0** |
| `mvn test`（默认 profile） | 编译期即失败，**0 个用例被执行** | 1503 用例执行 → 23 failures / **171 errors** / 3 skipped，BUILD FAILURE |
| `mvn test -Dspring.profiles.active=test` | 同上，仍是编译期失败 | **1552 用例执行 → 37 failures / 23 errors / 3 skipped**，BUILD FAILURE |

证据：`audit/evidence/F172-i186-test-compile-fix/`（`01` 修复前编译日志、`02` 默认 profile 全量日志 zip、`03` test profile 全量日志 zip、`04` 摘要）。

### 6.4 结论与遗留

- **本条的编译层问题已闭环**：`mvn test-compile` EXIT=0，`mvn test` 从「跑不了」变为「能跑并给出真实结论」。
- **`mvn test` 仍为红**，但**剩余失败与本条无关**，已按类别拆到 **I197**：
  - `jwt.secret` 弱密钥导致 `@SpringBootTest` 上下文加载失败（默认 profile 下 148 个 error）→ I197 §1
  - 后端类缺失 6 项（`AiApiAliasController` / `RechargeAuditController` / `IotDeviceController` / `ReportCaliberController` / `ReportTaskController` / `SubsModulesPresenceTest.subs_001`）→ I197 §2 A 类
  - 本地 DB 漂移（`pos_refund` 缺 `deleted` 列等）→ I197 §2 B 类
  - 权限码断言过期（`report:dashboard` / `report:biz:list`）→ I197 §2 C 类
  - 期望值 vs 现行算法漂移、`@BeforeAll` 非 static 等 → I197 §2 D/E 类
- **本次修复涉及的 3 个文件自身也在失败清单里**（`LbsRouteOptimizerTest` 7、`BookingWaitlistScoreServiceTest` 2、`RefundAuditWorkflowServiceE2eTest` 5 errors），已逐条核对**与本次改动无关**：前两者失败在第 163/192/228/311/345/457/481 行（改动只落在 245/283/317 的配置写入且写入值等于默认值）、`BookingWaitlistScoreServiceTest` 为算法期望漂移、`RefundAuditWorkflowServiceE2eTest` 为本地库缺列。

### 6.5 边界声明（修复阶段）

- **未修改任何主代码**；改动全部落在 `backend/src/test/java/**` 的 7 个文件。
- **未写生产库、未执行 DDL、未新增入库脚本**；`mvn test` 运行的 `@SpringBootTest` 连接的是本机开发库 `jdbc:mysql://localhost:3307/meiyetec`（`src/test/resources/application.yml`），**未连接生产库**。
- **未打开浏览器**。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验证页面及其数据接口。
- 未新增可执行脚本；证据中的 mvn 命令均为人工执行的一次性命令，未入库为守门脚本。
