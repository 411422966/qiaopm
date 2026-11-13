# I160 — Churn 训练运行日志链路缺失：`churn_training_run_log` 表 0 行且无 `/run/log` 端点

> **状态**：Fixed（本地已修复并验证，2026-11-13）
> **严重度**：**P2**（诊断链路缺失 —— 训练过程无留痕，不影响训练结果本身，但排障与审计无据）
> **发现日期**：2026-11-13（I159 排查 `/dev/LtvTraining` 时与 LTV 侧对账发现）
> **登记人**：Cursor
> **关联**：**I159**（前端 `sysApi.request` 缺失 → 该页面的 Churn 日志 tab 全程静默失败）、I154（「运行日志不关联」条目同源）、I155（预测端不消费训练模型）
> **修复方案**：方案 C —— 补 `GET /run/log` 端点 + `train()` 内补 `logMapper.insert`
> **证据**：`audit/evidence/F157-dev-tools-sysapi-fix/`

---

## 0. 一句话描述

`ChurnTrainingService` 注入了 `ChurnTrainingRunLogMapper`（`churn_training_run_log` 表）却**从未调用 `insert`**，
也**没有暴露 `/run/log` 查询端点** —— 导致：

1. `churn_training_run_log` 表**恒为 0 行**，训练过程无任何留痕；
2. 前端 `views/dev/LtvTraining.vue` 的「Churn 训练日志」tab 调用 `GET /intel/churn/training/run/log` → **HTTP 404**（该端点在修复前不存在，`run/log` 映射匹配数 = 0，静态确认；404 为代码级推断，本次未复现实测）；
3. 与 LTV 侧**完全不对称**（LTV 侧 `LtvTrainingService` 既有 `logRun()` 写入、又有 `/run/log` 端点，表内 10 行）。

---

## 1. 缺陷位置

### 1.1 写入缺失

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/ChurnTrainingService.java` |
| 类 | `ChurnTrainingService`（`@RestController @RequestMapping("/intel/churn/training")`） |
| 注入 | `private final ChurnTrainingRunLogMapper logMapper;`（**注入了但从未使用**） |
| 修复前 | 全文 `logMapper.insert` 匹配行数 = **0**（证据 `raw/06-before-after-static-diff.txt`） |

对比 LTV 侧（对称实现，存在）：`backend/src/main/java/com/meiyetec/service/LtvTrainingService.java`
`private Long logRun(...)` L631-L649，其中 `logMapper.insert(log)` 在 **L647**。

### 1.2 端点缺失

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/ChurnTrainingService.java` |
| 修复前 | 全文 `run/log` 映射匹配行数 = **0** |
| 修复后 | `ChurnTrainingService.java:590` `@GetMapping("/run/log")` |
| 对照（LTV 侧） | `LtvTrainingService.java:474` `@GetMapping("/run/log")` |

### 1.3 数据表

| 项 | 内容 |
|---|---|
| 实体 | `backend/src/main/java/com/meiyetec/entity/ChurnTrainingRunLog.java`（`@TableName("churn_training_run_log")`） |
| 字段 | `id / tenantId / modelId / runType / step / startAt / endAt / durationMs / samples / metric / metricValue / message / error / createdAt` |
| 修复前行数 | **0** |
| LTV 同构表 | `ltv_training_run_log`（修复前即有 **10** 行） |

---

## 2. 影响

| 维度 | 影响 |
|---|---|
| 可观测性 | Churn 训练（模型注册、样本数、耗时、AUC、失败原因）**零留痕** |
| 前端 | `/dev/LtvTraining.vue` 的「Churn 训练日志」tab 请求 **404**（即使 I159 修好 `sysApi.request` 也仍为空） |
| 审计 | 无法回答「这个 Churn 模型是哪次训练产出的、样本多少、失败过没有」 |
| 数据风险 | 无（不写脏数据，只是缺记录） |

> 与 **I159** 的耦合关系：I159 的 `sysApi.request` 缺失使**所有** `/dev` 请求静默失败，
> 用户看到的是「No Data」而**没有任何 404 线索** —— I159 修好后，Churn 日志的真实 404 才暴露出来。
> 即 **I159 遮蔽了 I160**。

---

## 3. 修复

**方案 C（已采用）**，改动集中在 `backend/src/main/java/com/meiyetec/service/ChurnTrainingService.java`：

### 3.1 补写日志方法（`ChurnTrainingService.java:603-L621`）

```java
private Long logRun(Long tenantId, Long modelId, String runType, String step,
                    Integer durationMs, Integer samples, String metric,
                    BigDecimal metricValue, String message, String error) {
    ChurnTrainingRunLog runLog = new ChurnTrainingRunLog();
    runLog.setTenantId(tenantId);
    runLog.setModelId(modelId);
    runLog.setRunType(runType);
    runLog.setStep(step);
    runLog.setDurationMs(durationMs);
    runLog.setSamples(samples);
    runLog.setMetric(metric);
    runLog.setMetricValue(metricValue);
    runLog.setMessage(message);
    runLog.setError(error);
    runLog.setStartAt(LocalDateTime.now());
    if (durationMs != null) runLog.setEndAt(LocalDateTime.now());
    logMapper.insert(runLog);
    return runLog.getId();
}
```

与 LTV 侧 `LtvTrainingService.logRun`（L631）**同构**，字段语义一致。

### 3.2 `train()` 内三处埋点

| 位置 | 语义 | 写入内容 |
|---|---|---|
| `ChurnTrainingService.java:87` | **训练开始** | `runType=TRAIN, step=TRAIN, samples=N, metric="samples", message="算法=LR,churnThreshold=90,horizon=90"`，`modelId=null`（模型尚未生成） |
| `ChurnTrainingService.java:169` | **训练成功** | `modelId=新模型 id, durationMs=耗时, samples=N, metric="val_auc", metricValue=AUC, message="训练完成"` |
| `ChurnTrainingService.java:175` | **训练失败** | `modelId=null, durationMs=耗时, samples=0, error=e.toString(), message="训练失败"` |

> ⚠️ 事务边界：`train()` 标注 `@Transactional(rollbackFor = Exception.class)`，
> **训练失败分支的日志随事务回滚而丢弃**（`logMapper.insert` 在同一事务内）。
> 本次未改事务语义（改动会扩大影响面），**失败留痕这一项目前仅为「成功路径 + 开始路径」可见**，
> 列为 §5 未确认/待办项。

### 3.3 补查询端点（`ChurnTrainingService.java:590-L599`）

```java
@GetMapping("/run/log")
public Result<List<ChurnTrainingRunLog>> runLog(
        @RequestParam(defaultValue = "20") Integer limit) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    return Result.success(logMapper.selectList(new LambdaQueryWrapper<ChurnTrainingRunLog>()
            .eq(ChurnTrainingRunLog::getTenantId, tenantId)
            .orderByDesc(ChurnTrainingRunLog::getId)
            .last("LIMIT " + limit)));
}
```

与 LTV 侧 `LtvTrainingService.runLog`（L474）**完全对称**，同样带 `tenantId` 隔离。

---

## 4. 验证

| 项 | 证据 | 结果 |
|---|---|---|
| 端点可用 | `GET /intel/churn/training/run/log` | **HTTP 200**（修复前端点不存在 → 404，静态确认 + 代码级推断） |
| 表写入 | `SELECT COUNT(*) FROM churn_training_run_log` | **2 行**（修复前 0 行） |
| 行内容 | `raw/03-db-churn-run-log.txt` | id=1：TRAIN/samples=489/「算法=LR,churnThreshold=90,horizon=90」；id=2：TRAIN/val_auc=0.5000/durationMs=125/「训练完成」 |
| 租户隔离 | 端点实现带 `eq(tenantId, ...)` | 代码级确认（未做跨租户运行实测） |

原始输出：
- `audit/evidence/F157-dev-tools-sysapi-fix/raw/01-core-5-endpoints.txt`
- `audit/evidence/F157-dev-tools-sysapi-fix/raw/03-db-churn-run-log.txt`

### 验证等级

| 项 | 等级 |
|---|---|
| 端点 200 + 返回 2 行 | **L5b**（真实 HTTP 响应体 + DB 快照） |
| DB 写入 | **L5b**（直查 `churn_training_run_log`） |
| 训练失败路径留痕 | **UNVERIFIED** —— 未触发过失败训练，且受事务回滚影响（§3.2 ⚠️） |
| 跨租户隔离 | **UNVERIFIED** —— 仅代码级确认，未运行实测 |

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 训练失败时日志是否落库 | **未确认** —— `train()` 的 `@Transactional` 会回滚同事务内的 `logMapper.insert`，失败日志**大概率不落库**（需实测确认） |
| 生产环境 `churn_training_run_log` 历史数据 | **未确认** —— 本地表此前 0 行，生产是否有历史数据未查 |
| 端点 `limit` 参数是否需要上限保护 | **未确认** —— 与 LTV 侧一致（无上限），沿用现状未改 |
| 前端 Churn 日志 tab 渲染 | **未确认** —— 需浏览器人工验收（无 Playwright MCP） |

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 核实 commit | **`c27d370`** `docs(audit): 补登 I157、选定 I156/I157 方案、登记 I158`（修复前 HEAD） |
| 源码依据 | `backend/src/main/java/com/meiyetec/service/ChurnTrainingService.java` L87 / L169 / L175（埋点）、L590（端点）、L603（`logRun`）；对照 `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java` L474 / L631-L649；`backend/src/main/java/com/meiyetec/entity/ChurnTrainingRunLog.java` |
| 运行时 | Docker `meiyetec-api`（:8080，healthy），2026-11-13 16:10 重建（含本修复） |
| DB 快照 | `churn_training_run_log` 2 行 / `ltv_training_run_log` 10 行（租户 1） |
| 证据目录 | `audit/evidence/F157-dev-tools-sysapi-fix/` |
| 取证时间 | 2026-11-13 16:45 – 16:58 |
