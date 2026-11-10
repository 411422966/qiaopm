# F153 — LTV/Churn 训练链路 6 个代码缺陷修复（I154 ①-⑤）

> **状态**：VERIFIED（①-⑤ 修复生效，8 个验证点全部有运行证据）
> **关联 issue**：I154（主）、I152（`crm_order` 引用外溢，导致批量预测实际写入 0 行）、I155（预测端不消费训练模型）
> **取证时间**：2026-11-11
> **取证环境**：本地 Docker（`meiyetec-mysql` / `meiyetec-api`），非生产库

---

## 1. 证据清单

| 文件 | 内容 |
|---|---|
| `raw/01-ltv-train.json` | `POST /intel/ltv/training/train` 完整响应 |
| `raw/02-churn-train.json` | `POST /intel/churn/training/train` 完整响应 |
| `raw/03-offline-endpoints.json` | deploy / offline 4 个端点的完整响应 |
| `raw/04-offline-db-state.txt` | offline 后 `ltv_model_registry` / `churn_model_registry` 的 DB 状态 |
| `raw/05-governance-model-report.json` | `GET /governance/model/report` 响应 |
| `raw/06-ab-validate-ltv.json` | `POST /governance/model/ab/validate`（LTV）响应 |
| `raw/07-batch-predict-ltv.json` | `POST /governance/model/batch-predict`（LTV）响应 |
| `raw/08-batch-predict-churn.json` | `POST /governance/model/batch-predict`（CHURN）响应 |
| `raw/09-label-data-snapshot.txt` | `ltv_training_feature` 标签列的分布快照 |
| `raw/10-reflection-stats-and-index.txt` | 反射验证 ⑤ 均值 + ④ 权重索引 |
| `raw/11-reflection-eval-control.txt` | 反射验证 evaluate 链路（含全 0 标签对照组） |
| `raw/VerifyLtvFix.java` | 验证脚本（**非正式工具**，见文件头说明） |
| `raw/VerifyLtvEval.java` | 验证脚本（**非正式工具**，见文件头说明） |

---

## 2. 构建与部署

| 项 | 值 |
|---|---|
| 后端构建 | `mvn clean package "-Dmaven.test.skip=true"` → **BUILD SUCCESS**（1547 源文件） |
| 镜像重建 | `docker compose build api` → `meiyetec/api:dev` |
| 容器重建 | `docker compose up -d api` → `Up (healthy)` |
| 鉴权 | `POST /auth/login`（admin / 123456，SUPER_ADMIN） |
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |

---

## 3. 改动摘要（5 处，共 3 个文件）

### ① `service/DataGovernanceService.java` — 8 处 SQL 去掉 `deleted = 0`

模型注册表 `ltv_model_registry` / `churn_model_registry` **没有 `deleted` 列**（DDL 见
`docs/audit/p4_1_ltv_training.sql:67`），治理侧手写 SQL 却带该条件 → `ERROR 1054` → 被 catch 吞掉 →
**静默假成功**。

| 修复前行号 | 语句片段 |
|---|---|
| 330 | `ltv_model_registry ... is_active = 1 AND deleted = 0 LIMIT 1` |
| 335 | `ltv_model_registry ... is_active = 0 AND deleted = 0` |
| 370 | `churn_model_registry ... is_active = 1 AND deleted = 0 LIMIT 1` |
| 375 | `churn_model_registry ... is_active = 0 AND deleted = 0` |
| 420 | `ltv_model_registry WHERE tenant_id = ? AND deleted = 0 ORDER BY ...` |
| 432 | `churn_model_registry WHERE tenant_id = ? AND deleted = 0 ORDER BY ...` |
| 513 | `batchPredictLtv` 取已部署模型 `... AND deleted = 0 LIMIT 1` |
| 549 | `batchPredictChurn` 取已部署模型 `... AND deleted = 0 LIMIT 1` |

### ② 新增 2 个 `/model/{id}/offline` 端点

`LtvTrainingService` / `ChurnTrainingService` 各补 1 个，与 `deploy` 对称：

```java
jdbc.update("UPDATE ltv_model_registry SET is_active = 0, status = 4, deployed_at = NULL WHERE id = ?", id);
```

`status = 4` 语义来自 DDL 注释：`1=草稿 2=已训练 3=已部署 4=已下线`。
前端 `dev/LtvTraining.vue` 的 4 处调用（`:374` `:375` `:624` `:644`）不再 404。

### ③ `service/ChurnTrainingService.java` — Churn 标签取反

```java
// 修复前：", next_90d_active AS label "     ← 语义反了（活跃 ≠ 流失）
// 修复后：
", (1 - next_90d_active) AS label " +
```

### ④ `service/LtvTrainingService.java` — 特征重要性权重索引

```java
// 修复前：importance.put(FEATURE_COLUMNS.get(i), Math.abs(weights[i]));      ← weights[0] 是 bias
importance.put(FEATURE_COLUMNS.get(i), Math.abs(weights[i + 1]));
```

与 `buildFormula`（本就使用 `weights[i + 1]`）对齐。

### ⑤ `service/LtvTrainingService.java` — `computeStats` 均值

```java
// 修复前：第一个循环是覆盖（sum = ...）而非累加，导致 means[i] = (Σ全部 + 最后一行值) / n
double sum = 0;
for (Map<String, Object> r : rows) {
    sum += toDouble(r.get(FEATURE_COLUMNS.get(i)));
}
means[i] = sum / n;
```

---

## 4. 验证结果（8 个验证点）

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | LTV `trainMAE` / `trainRMSE` 不再是精确 0 | ⚠️ **仍为 0**，属数据现状 | `raw/09` + `raw/11` |
| 2 | `feature_importance_json` 中 `recency_days` 不再是 bias 绝对值 | ✅ | `raw/10` |
| 3 | Churn `positiveRate` 0.0% → 100% | ✅ 100.0% | `raw/02` |
| 4 | Churn AUC 仍 0.5（单类别） | ✅ 0.5 | `raw/02` |
| 5 | `GET /governance/model/report` 看到已部署模型 | ✅ ltvModels 2 + churnModels 2 | `raw/05` |
| 6 | `batch-predict` 不再报「无已部署 LTV 模型」 | ✅ | `raw/07` |
| 7 | `POST /intel/ltv/training/model/{id}/offline` → 200 | ✅ `is_active=0 / status=4 / deployed_at=NULL` | `raw/03` `raw/04` |
| 8 | `POST /intel/churn/training/model/{id}/offline` → 200 | ✅ `is_active=0 / status=4 / deployed_at=NULL` | `raw/03` `raw/04` |

### 4.1 验证点 1 的说明（重要 —— 不是修复失败）

生产 LTV 指标恒为 0 的根因是**标签全 0**：

```
$ raw/09-label-data-snapshot.txt
total  null_rev  zero_rev  pos_rev  act1  act0
489    0         489       0       0     489
```

`next_90d_revenue` 489 行全为 0（标签窗口在未来 90 天，当前无数据）。

反射实测（`raw/11`）证明**评估链路本身正确**：

| 场景 | MAE | RMSE | MAPE | R² |
|---|---|---|---|---|
| 合成标签 `label = 3*recency + 7` | 14.1026 | 16.4040 | 1.8310 | 0.9960 |
| 对照组：标签全 0 | 0.0 | 0.0 | 0.0 | 0.0 |

→ 常量模型恰好等于全 0 标签时误差必然为 0。**待标签窗口有真实数据后指标才会有意义。**

同理 ⑤ 的修复效果由 `model_formula` 中的均值从 **5931.0818 → 5930.6803** 直接印证
（差异 = 最后一行 recency / n，即被多算的那一次）。

### 4.2 验证点 2 的说明（`raw/10`）

```
computed means[0] = 249.5     expected = 249.5     delta = 0.0     PASS = true   ← ⑤ 生效
weights[0] (bias)    = 741.9383570007031
weights[1] (feature0) = 253.14687464773007
旧映射 recency_days 重要性 = 741.9383570007031   (=bias 的绝对值, 错误)
新映射 recency_days 重要性 = 253.14687464773007   (=feature0 权重, 正确)
两者是否不同(可证伪)       = true
新映射是否等于 |w[1]|     = true
```

`buildFormula` 输出 `LTV = 741.9384 + 253.1469 * ((recency_days - 249.5000) / 86.6021) + ...`
→ **重要性图与公式现在指向同一个权重**，错位消除。

### 4.3 验证点 6 的补充说明

LTV 批量预测返回 `"predicted": 0` —— 原因是 `batchPredictLtv` 内部
`SELECT AVG(pay_amount) FROM crm_order` 引用了**不存在的 `crm_order` 表**，
异常被 `catch (Exception ignored)` 静默吞掉，**每条会员写入都失败**。
**属 I152（Open）范畴，本轮未处理。**

CHURN 批量预测则**显式报错**（同样因 `crm_order`），见 `raw/08`。同样属 I152。

---

## 5. 未确认项

| 项 | 状态 |
|---|---|
| `crm_ltv_snapshot` 的 `predicted_ltv_365d` 应写训练模型预测值还是 `AVG*12` | 未确认（I155 设计决策） |
| `ltv_model_registry` 与 `ai_ltv_model` 并存是否为有意设计 | 未确认（I155） |
| `status = 4` 是否为前端期望的下线状态值 | 未确认（前端只判 `isActive`） |
| 未来 90 天真实消费数据何时能产生非零 `next_90d_revenue` | 未确认（依赖业务数据积累） |
