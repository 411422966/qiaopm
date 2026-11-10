# I154 — LTV/Churn 训练链路 6 个代码缺陷：治理看板不可见 / 下线端点缺失 / Churn 标签反向 / 重要性错位 / 均值计算错 / 运行日志不关联

> **状态**：PARTIAL —— ①-⑤ 已修复（本轮），⑥ 仍 Open
> **严重度**：① P1（静默假成功）、② P1（前端必 404）、③ P1（模型语义反）、④ P2、⑤ P2、⑥ P3
> **发现日期**：2026-11-11（I142 批 1 ETL 修复完成后的训练阶段只读调查）
> **登记人**：Cursor（训练链路只读调查 → 用户拍板修复 ①-⑤、登记 ⑥）
> **决策**：用户 2026-11-11 拍板 —— **本轮修 ①-⑤，⑥ 登记不修，⑦（预测端不消费模型）单列 I155**
> **关联**：I142（`LtvTrainingService` 表名/列名修复，批 1）、I152（`crm_order` 引用外溢）、I153（`biz_service.category_id`）、I155（预测端设计问题）

---

## 0. 一句话描述

LTV/Churn **训练 → 部署**这一段链路本身是通的（表都存在、接口都返回 200、模型能落库），
但训练链路上有 6 处代码缺陷，使得：

- 已部署模型在**治理看板 / 批量预测 / A/B 验证**三处**全部不可见**（静默假成功）
- 模型**无法下线**（前端 4 处调用必然 404）
- Churn 模型**标签方向完全反了**（训练的是"活跃=流失"）
- LTV **特征重要性错位一位**（首个特征拿到的是 bias 绝对值）
- LTV **训练集均值算错**（多算最后一行一次）
- 训练**运行日志不与模型关联**（一次训练产生 2 条孤立日志）

---

## 1. 缺陷总表

| # | 缺陷 | 位置 | 严重度 | 本轮状态 |
|---|---|---|---|---|
| ① | 模型注册表 SQL 查不存在的 `deleted` 列 | `DataGovernanceService.java` 8 处 | **P1** | ✅ Fixed |
| ② | `/model/{id}/offline` 端点缺失 | `LtvTrainingService` / `ChurnTrainingService` | **P1** | ✅ Fixed |
| ③ | Churn 标签方向反 | `ChurnTrainingService.java:373` | **P1** | ✅ Fixed |
| ④ | 特征重要性权重错位一位 | `LtvTrainingService.java:356` | P2 | ✅ Fixed |
| ⑤ | `computeStats` 均值计算 bug | `LtvTrainingService.java:455-465` | P2 | ✅ Fixed |
| ⑥ | 运行日志不与模型关联（runId 未使用） | `LtvTrainingService` / `ChurnTrainingService` | P3 | 🔴 **Open** |

---

## 2. ① 模型注册表 SQL 查不存在的 `deleted` 列（P1，静默假成功）

### 2.1 现象

`ltv_model_registry` / `churn_model_registry` **没有 `deleted` 列**，但治理侧手写 SQL 带 `deleted = 0`：

```
ERROR 1054 (42S22): Unknown column 'deleted' in 'where clause'
```

### 2.2 涉及位置（修复前行号，共 8 处）

| 文件 | 行号 | 语句 | 失败形态 |
|---|---|---|---|
| `service/DataGovernanceService.java` | 330 | `ltv_model_registry ... is_active = 1 AND deleted = 0 LIMIT 1` | 🟠 catch 吞掉 |
| 同上 | 335 | `ltv_model_registry ... is_active = 0 AND deleted = 0` | 🟠 catch 吞掉 |
| 同上 | 370 | `churn_model_registry ... is_active = 1 AND deleted = 0 LIMIT 1` | 🟠 catch 吞掉 |
| 同上 | 375 | `churn_model_registry ... is_active = 0 AND deleted = 0` | 🟠 catch 吞掉 |
| 同上 | 420 | `ltv_model_registry WHERE tenant_id = ? AND deleted = 0 ORDER BY ...` | 🟠 catch 吞掉 |
| 同上 | 432 | `churn_model_registry WHERE tenant_id = ? AND deleted = 0 ORDER BY ...` | 🟠 catch 吞掉 |
| 同上 | 513 | `batchPredictLtv` 取已部署模型 `... AND deleted = 0 LIMIT 1` | 🔴 显式报错 |
| 同上 | 549(551) | `batchPredictChurn` 取已部署模型 `... AND deleted = 0 LIMIT 1` | 🔴 显式报错 |

### 2.3 DDL 依据（证明 `deleted` 确实不存在）

`docs/audit/p4_1_ltv_training.sql:67` 与 `backend/ai_ddl.sql:133` 的建表语句中，
`ltv_model_registry` 只有 `is_active` / `status` / `trained_at` / `deployed_at` / `version`，
**无 `deleted` 字段**。`churn_model_registry` 同构。

全局 MyBatis-Plus 配了 `logic-delete-field: deleted`（`application.yml:56`），
但那只对**带 `deleted` 字段的实体**生效 —— `LtvModelRegistry` / `ChurnModelRegistry` 实体均无该字段，
所以**纯手写 SQL 单独踩坑**。

### 2.4 影响面（静默假成功 —— 最危险的一类）

| 端点 | 修复前表现 | 真实状态 |
|---|---|---|
| `GET /governance/model/report` | `ltvModels: []`、`churnModels: []`、`overallStatus: "PENDING"` | 明明已有 2 个已部署模型 |
| `POST /governance/model/batch-predict` | `error: "无已部署 LTV 模型"` | 已部署却报没有 |
| `POST /governance/model/ab/validate` | `error: "A/B 验证失败: ... bad SQL grammar ..."` | SQL 语法错 |

### 2.5 修复方案

**去掉 8 处 SQL 中的 `deleted = 0`**（未给表加列 —— 加列会牵动实体与全局软删语义，
属于更大的设计变更，本轮不做）。

---

## 3. ② `/model/{id}/offline` 端点缺失（P1，前端必 404）

### 3.1 现象

后端只有 `POST /model/{id}/deploy`，**没有任何下线入口**。
前端 4 处调用必然 404：

| 前端位置 | 调用 |
|---|---|
| `frontend/meiyetec-web/src/views/dev/LtvTraining.vue:374` | `POST /intel/ltv/training/model/${m.id}/offline`（批量下线） |
| 同上 `:375` | `POST /intel/churn/training/model/${m.id}/offline`（批量下线） |
| 同上 `:624` | `POST /intel/ltv/training/model/${row.id}/offline`（单个下线） |
| 同上 `:644` | `POST /intel/churn/training/model/${row.id}/offline`（单个下线） |

### 3.2 修复方案

新增 2 个端点，与 `deploy` 对称：

```java
@PostMapping("/model/{id}/offline")
@Transactional(rollbackFor = Exception.class)
public Result<Void> offline(@PathVariable Long id) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    // ... 校验模型存在且同租户 ...
    jdbc.update("UPDATE ltv_model_registry SET is_active = 0, status = 4, deployed_at = NULL WHERE id = ?", id);
    return Result.success("已下线", null);
}
```

`status = 4` 的语义来自 DDL 注释：`status TINYINT DEFAULT 1 COMMENT '1=草稿 2=已训练 3=已部署 4=已下线'`。

---

## 4. ③ Churn 标签方向反（P1，模型语义完全错误）

### 4.1 缺陷

`ChurnTrainingService.java:373`（修复前）：

```java
", next_90d_active AS label " +
```

Churn（流失）的定义是「**不**活跃」= 正样本 1。
`next_90d_active = 1` 表示**活跃**，即**未流失** —— 语义完全反了。

### 4.2 被数据现状掩盖

实测 `ltv_training_feature` 489 行：`next_90d_active = 1` 有 **0 行**，全为 0。
因此修复前 `positiveRate = 0.0%`、正样本 0 → `computeAuc` 在 `posCount == 0` 时
直接 `return 0.5`（`ChurnTrainingService.java:355`）→ AUC 恒 0.5、F1/Precision/Recall 全 0。

**这个 bug 被"标签全 0"掩盖了** —— 即使将来 `next_90d_active` 出现 1 的值，
旧代码也会把"活跃"样本当成"流失"正样本，完全反着训练。

### 4.3 修复方案

```java
", (1 - next_90d_active) AS label " +
```

> ⚠️ **修复后的正确表现**：因 `next_90d_active` 489 行全 0，取反后**全为 1**，
> `positiveRate` 从 0.0% → **100.0%**，AUC 仍 **0.5**（单类别，`computeAuc` 的
> `posCount == n` 分支同样 `return 0.5`）。
> **这是数据现状的正确反映，不是改错了** —— 需要积累到「未来 90 天」的真实消费数据才能有双类别样本。

### 4.4 语义一致性核对

降级路径 `loadSamplesFromRaw`（`ChurnTrainingService.java:410` 附近）的标签是
`int label = recency <= 90 ? 0 : 1;`（90 天内有消费 = 未流失 = 0）——
**与修复后的 `(1 - next_90d_active)` 语义一致**（都是"不活跃=流失=1"）。修复前两条路径语义互相矛盾。

---

## 5. ④ 特征重要性权重错位一位（P2）

### 5.1 缺陷

`LtvTrainingService.java:356`（修复前）：

```java
importance.put(FEATURE_COLUMNS.get(i), Math.abs(weights[i]));
```

但 `gradientDescent` 返回的 `fullWeights` 长度是 `d + 1`：
`fullWeights[0] = bias`，`fullWeights[1..d]` 才是各特征权重。

后果：
- `recency_days` 拿到的是 **bias 的绝对值**
- `point_balance` 拿到的是 `coupon_used_count` 的权重
- 最后一个特征 `point_balance` 的真实权重**被直接丢弃**（循环只到 `i = 18`，而真实权重在 `weights[19]`）

对比 `buildFormula`（`LtvTrainingService.java:593`）用的是 `weights[i + 1]` ——
**公式是对的，重要性图是错的**，两者互相不一致。

### 5.2 修复方案

```java
importance.put(FEATURE_COLUMNS.get(i), Math.abs(weights[i + 1]));
```

---

## 6. ⑤ `computeStats` 均值计算 bug（P2，影响训练质量）

### 6.1 缺陷

`LtvTrainingService.java:455-465`（修复前）：

```java
double sum = 0;
for (Map<String, Object> r : rows) {
    sum = toDouble(r.get(FEATURE_COLUMNS.get(i)));   // ← 赋值，不是累加
}
// 简化计算
for (Map<String, Object> r : rows) {
    sum += toDouble(r.get(FEATURE_COLUMNS.get(i)));   // ← 累加
}
means[i] = sum / n;
```

第一个循环是**覆盖**不是累加 → 循环结束后 `sum` = 最后一行该特征的值；
第二个循环再累加全部 → `means[i] = (Σ全部 + 最后一行值) / n`，**多算最后一行一次**。
`stds` 也基于这个错误均值计算。

### 6.2 实测影响

修复前公式里 `recency_days` 的均值 = **5931.0818**；
修复后 = **5930.6803**（差异 = 最后一行 recency / n）。

### 6.3 修复方案

删掉第一个 `for` 循环，只保留累加循环。

---

## 7. ⑥ 运行日志不与模型关联（P3，**本轮未修，仍 Open**）

### 7.1 现象

`LtvTrainingService.logRun` 每次调用都 `logMapper.insert` 一条**新行**（修复前 L622-641），
**不更新已有行**。因此一次训练产生 **2 条独立日志**：

| 行 | 内容 | `model_id` |
|---|---|---|
| 第 1 条 | run_type=TRAIN, step=TRAIN, status=RUNNING | `NULL` |
| 第 2 条 | run_type=TRAIN, step=COMPLETED | 指向模型 |

`trainModel` 里赋值的 `runId` 变量**从未被使用**。
ETL 阶段同样问题（2 次 ETL → 4 行日志）。实测 `ltv_training_run_log` 6 行印证。

### 7.2 建议修复路径（**本轮未实施**）

改为「插入后回填 `model_id`」或「按 `run_id` update」：

```java
// 方案 A：logRun 返回自增主键，训练完成后回填 model_id
Long runId = logRun(...);
...
modelMapper.insert(model);
logMapper.update(null, Wrappers.<LtvTrainingRunLog>lambdaUpdate()
        .eq(LtvTrainingRunLog::getId, runId)
        .set(LtvTrainingRunLog::getModelId, model.getId()));
```

### 7.3 附带发现

`ChurnTrainingService` 注入了 `logMapper`（L42）却**从未调用** ——
`churn_training_run_log` 表存在但 **0 行**，属孤儿依赖。

---

## 8. 本轮验证结果（8 个验证点）

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | LTV `trainMAE` / `trainRMSE` 不再是精确 0 | ⚠️ **仍为 0**，但**属数据现状非代码缺陷** | 见 §8.1 |
| 2 | LTV `feature_importance_json` 中 `recency_days` 不再是 bias 绝对值 | ✅ 反射实测 | 见 §8.2 |
| 3 | Churn `positiveRate` 0.0% → 100% | ✅ 100.0% | 见 §8.3 |
| 4 | Churn AUC 仍 0.5（单类别） | ✅ 0.5 | 见 §8.3 |
| 5 | `GET /governance/model/report` 能看到 2 个已部署模型 | ✅ ltvModels 2 条 + churnModels 2 条，`overallStatus: PARTIAL` | 见 §8.4 |
| 6 | `POST /governance/model/batch-predict` 不再报「无已部署 LTV 模型」 | ✅ 不再报该错 | 见 §8.5 |
| 7 | `POST /intel/ltv/training/model/{id}/offline` → 200 | ✅ 200，`is_active=0 / status=4 / deployed_at=NULL` | 见 §8.6 |
| 8 | `POST /intel/churn/training/model/{id}/offline` → 200 | ✅ 200，`is_active=0 / status=4 / deployed_at=NULL` | 见 §8.6 |

### 8.1 验证点 1 说明：LTV 指标恒为 0 的根因是**标签全 0**

实测 `ltv_training_feature`（489 行）：

```
total  null_rev  zero_rev  pos_rev  act1  act0
489    0         489       0       0     489
```

`next_90d_revenue` **489 行全为 0**（标签窗口在未来 90 天，当前无数据），
`next_90d_active` 也全为 0。

**反射实测（合成数据 + 全 0 对照组）**：

| 场景 | MAE | RMSE | MAPE | R² |
|---|---|---|---|---|
| 合成标签 `label = 3*recency + 7` | 14.1026 | 16.4040 | 1.8310 | 0.9960 |
| 对照组：标签全 0 | 0.0 | 0.0 | 0.0 | 0.0 |

→ **评估链路本身正确**，产出非零指标；
生产恒 0 是因为**常量模型恰好等于全 0 标签**（退化模型），与本次修复无关。

同理 ⑤ 的修复效果由 `model_formula` 中的均值从 `5931.0818` → `5930.6803` 直接印证。

### 8.2 验证点 2 说明：反射实测 ④

用 `sun.misc.Unsafe` 分配 `LtvTrainingService` 实例，反射调用私有
`computeStats` / `gradientDescent` / `buildFormula`（脚本置于系统临时目录，**不入仓**）：

```
computed means[0] = 249.5
expected  means[0] = 249.5
delta             = 0.0
PASS(均值精确)     = true          ← ⑤ 修复生效

weights[0] (bias)      = 741.9383570007031
weights[1] (feature0)   = 253.14687464773007
旧映射 recency_days 重要性 = 741.9383570007031   (=bias 的绝对值, 错误)
新映射 recency_days 重要性 = 253.14687464773007   (=feature0 权重, 正确)
两者是否不同(可证伪)       = true
新映射是否等于 |w[1]|     = true
```

同时 `buildFormula` 输出 `LTV = 741.9384 + 253.1469 * ((recency_days - 249.5000) / 86.6021) + ...`
→ **重要性图与公式现在指向同一个权重**（253.1469），错位消除。

### 8.3 验证点 3/4：Churn 训练结果

```
positiveRate = 100.0%     ← ③ 修复后（数据全 0 → 取反全 1）
trainAUC     = 0.5
valAUC       = 0.5        ← 单类别，computeAuc 的 posCount == n 分支
valF1        = 1.0
valPrecision = 1.0
valRecall    = 1.0
conclusion   = "POOR"
```

### 8.4 验证点 5：治理模型报告

```json
{
  "ltvModels":   [ { "model_code": "LTV_MLR_1794330728", "is_active": 1, ... },
                   { "model_code": "LTV_MLR_1794329746", "is_active": 0, ... } ],
  "churnModels": [ { "model_code": "CHURN_LR_1794330738", "is_active": 1, "val_auc": 0.5000, ... },
                   { "model_code": "CHURN_LR_1794329758", "is_active": 0, ... } ],
  "summary": { "ltvReady": true, "churnReady": false, "overallStatus": "PARTIAL" }
}
```

（`churnReady: false` 是因为 `val_auc = 0.5 < 0.7` 阈值 —— **正确反映单类别模型的真实质量**。）

### 8.5 验证点 6：批量预测

```json
{ "modelType": "LTV", "limit": 50, "predicted": 0, "message": "批量预测完成" }
```

不再报「无已部署 LTV 模型」✅。
但 `predicted = 0` —— 原因是 `batchPredictLtv` 的 INSERT 里
`SELECT AVG(pay_amount) FROM crm_order` 引用了**不存在的 `crm_order` 表**（**I152 已登记，Open**），
异常被 `catch (Exception ignored)` 静默吞掉，**每条会员的写入都失败**。
**此为 I152 范畴，本轮未处理。**

`modelType=CHURN` 的批量预测则**显式报错**（同样因 `crm_order`）：

```
error: PreparedStatementCallback; bad SQL grammar [UPDATE crm_member m JOIN (... FROM crm_order o ...) ...]
```

同样属 **I152**。

### 8.6 验证点 7/8：offline 端点

```json
POST /intel/ltv/training/model/2/offline    → { "code": 200, "message": "已下线" }
POST /intel/churn/training/model/2/offline  → { "code": 200, "message": "已下线" }
```

数据库核对：

```
ltv_model_registry   id=2 → is_active=0  status=4  deployed_at=NULL
churn_model_registry id=2 → is_active=0  status=4  deployed_at=NULL
```

### 8.7 验证环境

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`，MySQL 8.0） |
| 后端构建 | `mvn clean package "-Dmaven.test.skip=true"` → **BUILD SUCCESS**（1547 源文件） |
| 镜像重建 | `docker compose build api` → `meiyetec/api:dev` |
| 容器重建 | `docker compose up -d api` → `Up (healthy)` |
| 鉴权 | `POST /auth/login`（admin / 123456，SUPER_ADMIN） |
| 取证时间 | 2026-11-11 |
| 关联证据 | `audit/evidence/F153-ltv-training-chain-fix/` |
| 关联 issue | I142、I152、I153、I155 |

---

## 9. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| `crm_ltv_snapshot` 的 `predicted_ltv_365d` 应写入**训练模型预测值**还是 `AVG(pay_amount)*12` | **未确认**（属 I155 设计决策） |
| `ltv_model_registry` 与 `ai_ltv_model` 两套注册表并存是否为有意设计 | **未确认**（属 I155） |
| `status = 4` 是否为前端期望的下线状态值 | **未确认**（前端只判 `isActive`，未读 `status`） |
| 未来 90 天真实消费数据何时能产生非零 `next_90d_revenue` | **未确认**（依赖业务数据积累） |

---

## 10. 与 I155 的边界

本 issue 只覆盖**训练链路内部**的代码缺陷。
**「预测端完全不消费训练出来的模型」是独立的设计问题**，已单列 **I155**。

本 issue **不含**以下内容（用户明确要求不动）：
- `LtvService.computeForMember` 的 BG/NBD 公式
- `DataGovernanceService.batchPredictLtv` 的 `AVG(pay_amount) * 12`
- ⑥ 运行日志重构（本轮只登记）
