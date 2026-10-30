# I135 — 部署漂移 D1：运行中的 app.jar 落后源码 10 天，重建镜像会静默砍掉 `/gray/**`

> **编号说明**：用户 2026-10-30 19:23 指定为 `I133-deployed-jar-stale-vs-source.md`；本目录已占用 I133（见 `I133-schema-drift-missing-columns.md`），故顺延为 **I135**。
> **严重度**：P0（部署漂移，影响所有运行时验证的可信度 + 一次重建即造成功能回归）
> **状态**：BROKEN（未修复）

---

## 1. 现象

运行中的容器 `meiyetec-api` 所用 `app.jar` 构建于 **2026-10-20 15:59**，而仓库当前源码（commit `cc95a00`）与之差异**恰好 3 个类**：

| 方向 | 类 | 说明 |
|---|---|---|
| 源码有、运行包无 | `com.meiyetec.controller.GrayReleaseController` | 灰度发布 / AB Test 的新控制器 |
| 源码有、运行包无 | `com.meiyetec.util.GrayReleaseHasher` | 稳定哈希工具 |
| 运行包有、源码已删 | `com.meiyetec.service.GrayReleaseService` | 旧实现，已从源码删除 |

证据：`audit/evidence/F140-i118-ddl-landing/jar-diff.json`、`jar-diff.txt`

```
jar top-level classes 1735 | src files 1544
=== SOURCE_ONLY === 2
com.meiyetec.controller.GrayReleaseController
com.meiyetec.util.GrayReleaseHasher
=== JAR_ONLY === 1
com.meiyetec.service.GrayReleaseService
```

## 2. 后果

1. **今天所有运行时验证都跑在 10 天前的构件上。** I118 的 DDL 在 DB 层已生效不受影响，但凡涉及 Java 行为的结论（包括 `audit/evidence/F140-i118-ddl-landing/result.md` 里的 GET 扫描）**只对旧构件成立**；新构件可能表现不同。
2. **一旦重建镜像，`/gray/**` 整族功能会静默消失。**
   - 旧 `GrayReleaseService` 已从源码删除 → 重建后不复存在；
   - 新 `GrayReleaseController` 带着 I118 的止血守卫：
     ```49:49:backend/src/main/java/com/meiyetec/controller/GrayReleaseController.java
     @ConditionalOnProperty(name = "feature.legacy-missing-ddl.enabled", havingValue = "true", matchIfMissing = false)
     ```
   - `backend/src/main/resources/application.yml:151-152` = `feature.legacy-missing-ddl.enabled: false`
   - → Bean 不注册 → 所有 `/gray/**` 路径 **HTTP 404（无 handler）**，且日志里没有一行报错 → 典型的**静默回归**。
   - 当前 `/gray/flag/list` 还能 200，正是因为旧构件里的 `GrayReleaseService` 在服务 —— 这是**假象**，不是健康。

## 3. 为什么可以安全解封 GrayReleaseController

`GrayReleaseController` 依赖的 4 张表在当前库中**均已存在**（2026-10-30 16:54 快照实测）：

| 表 | 快照位置 |
|---|---|
| `feature_flag` | `backup/meiyetec-pre-i118-batch2-20261030.sql:7496` |
| `ab_experiment` | 同上 `:54` |
| `ab_assignment` | 同上 `:25` |
| `pricing_experiment_result` | 同上 `:13397` |

即 I118 的 DDL 已落地，**不再需要该止血守卫**。

## 4. 修复方向（用户 2026-10-30 19:23 已给方向）

- **只对 `GrayReleaseController` 摘掉** `@ConditionalOnProperty` 守卫（不要把 `feature.legacy-missing-ddl.enabled` 整体改成 `true` —— 那会同时解封另外 16 个守卫 Controller：
  `BizTreatmentReportController`、`MemberInviteController`、`InviteController`、`MultiAccountSplitController`、`ResourceUtilizationController`、`ResourceConflictLogController`、`ServiceMaterialGroupController`、`MaterialSubstituteController`、`StockShortageLogController`、`ServiceMaterialRuleController`、`ServiceMaterialDeductLogController`、`MaterialConsumptionLogController`、`ExpiryHandleLogController`、`CommissionTierController`、`CommissionSimulationController`、`CommissionConditionController`，
  其中若干张表在当前库中**仍然缺失**，解封即重新引入 500）。
- 同步删除随之失效的 `import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;`。
- 之后 **重建镜像 + 重建容器**，再复测 `/gray/**` 族端点。

## 5. 连带风险（需在重建后一并复核）

1. 旧构件上「200 正常」的端点，在新构件上可能变化 —— 需要在新构件上**重跑一次 GET 全量扫描**。
2. 旧 `GrayReleaseService` 与新 `GrayReleaseController` 的路由前缀是否完全一致（旧的是 `/gray` 族？）需在重建后核对，避免出现 404 或重复注册。
3. 重建镜像本身受 I126「运行镜像漂移」与 I123「备份自动化未部署」影响，重建前应确认备份可用（当前快照 `backup/meiyetec-pre-i118-batch2-20261030.sql` 已在）。

## 6. 证据

- `audit/evidence/F140-i118-ddl-landing/jar-diff.json`、`jar-diff.txt` —— 类级差异
- `audit/evidence/F140-i118-ddl-landing/result.md` —— §4 D1 段
- 源码：`backend/src/main/java/com/meiyetec/controller/GrayReleaseController.java:49`、`backend/src/main/resources/application.yml:150-152`
- 环境：commit `cc95a00`，DB `127.0.0.1:3307/meiyetec`，env `audit/baseline/env.md`

## 7. 状态

- **BROKEN**（部署漂移未消除；`/gray/**` 在下次重建时会被静默下线）
