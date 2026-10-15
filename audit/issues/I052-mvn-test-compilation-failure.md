# I052 — mvn test 编译失败（CI 阻塞）

> **登记时间**：2026-09-14 22:20 (UTC+8)
> **登记人**：Batch 1.4 Phase 5B
> **严重度**：🟠 PARTIAL-BLOCKER
> **影响 Feature**：F108 / F109 / F110（EXT-19 相关测试无法执行）

## 1. 现象

执行 `mvn test` 时，编译阶段（`testCompile`）失败，28 个编译错误，共 6 个测试文件：

| # | 文件 | 错误类型 | 根因 |
|---|---|---|---|
| 1 | `InventoryConsumptionFlowTest.java:48` | 构造器参数数量不匹配 | `InventoryConsumptionService` 新增了第 8 个参数 `StockDualVerifyService`，测试仍用 7 参数构造 |
| 2 | `InventoryConsumptionBomMissingTest.java:62` | 构造器参数数量不匹配 | 同上 |
| 3 | `BookingWaitlistScoreServiceTest.java:38,42` | private 访问控制 + 找不到方法 | `TenantContext.jdbc` 改为 private；`ctx.setStoreId(long)` 方法不存在 |
| 4 | `LbsRouteOptimizerTest.java:245-319`（3 处）| private 访问控制 | `DispatchConfig.wDistance/wSkill/wRating/wLoad/wTime/minDispatchScore` 改为 private |
| 5 | `RefundAuditWorkflowServiceE2eTest.java:71,95` | 找不到方法 | `ctx.setStoreId(Long)` 不存在；`Result.isSuccess()` 方法不存在 |
| 6 | `PluginRuntimeTest.java:89,100,112,131`（4 处）| 构造器参数数量不匹配 | `PluginRuntimeService` 新增了 2 个依赖（`PluginPersistenceService`/`PluginTenantService`），测试仍用 1 参数构造 |

**全部不是 EXT-19 相关文件**（EXT-19 相关测试文件为 `BookingConfirmation*Test` 等，不在报错列表）。

## 2. 影响范围

- `mvn test` 整体编译失败，**所有测试无法运行**
- EXT-19 的 4 个测试文件（`BookingConfirmationServiceTest` 等）**根本未被编译**，状态 UNVERIFIED
- CI pipeline 中 `mvn test` 步骤会失败，**阻塞部署**

## 3. 根因分析

| 根因 | 说明 |
|---|---|
| **Service 层重构后测试未同步** | `InventoryConsumptionService` / `PluginRuntimeService` 新增依赖后，测试文件未更新构造器参数 |
| **内部类字段可见性收紧** | `LbsRouteOptimizer.DispatchConfig` / `TenantContext` 的 private 字段收紧后，测试直接字段访问失效 |
| **Result 类 API 变更** | `Result.isSuccess()` 方法被移除或改名 |
| **非 EXT-19 范围** | 所有报错文件均不属于 EXT-19（多语言预约确认）范围 |

## 4. Fix Phase

**Post-Audit**（跨模块重构后果，不在 EXT-19 审计范围内）。

5B 应对策略：范围限定运行 `mvn -Dtest='BookingConfirmation*Test,*SenderTest' test`（不触发编译阶段全量编译）。

## 5. 与 EXT-19 关系

| EXT-19 测试文件 | 预期状态（静态分析）| 运行时状态 |
|---|---|---|
| `BookingConfirmationServiceTest.java` | 静态分析通过（未见明显硬编码）| **UNVERIFIED**（被 mvn test 编译失败阻塞）|
| `BookingConfirmationTemplateResolverTest.java` | 静态分析通过 | **UNVERIFIED** |
| `EmailSenderTest.java` | 静态分析通过 | **UNVERIFIED** |
| `InAppSenderTest.java` | 未找到文件 | N/A |

## 6. 决策

- **Fix Phase**: Post-Audit
- **5B 应对**: 使用 `mvn -Dtest='BookingConfirmation*Test,*SenderTest' test` 范围限定（plan §7.2）
- **不修**: 审计阶段不修改业务代码（规则 6）
