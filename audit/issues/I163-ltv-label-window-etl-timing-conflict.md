# I163 — LTV 标签窗口与 ETL 时序冲突：T-1 快照的「未来 90 天」标签恒为 0，训练出退化常量模型

> **状态**：**Open**（2026-11-13 登记；**本轮不修**，需业务决策标签窗口定义）
> **严重度**：**P1**（训练链路可跑通但产出**无学习价值**的模型，且指标判定误导）
> **发现日期**：2026-11-13（I161 Layer 1 修复后，训练首次成功时暴露）
> **登记人**：Cursor
> **关联**：**I161**（Layer 1 已修，Layer 2 即本 Issue）、**I155**（预测端不消费训练模型）、I154、I150、I157
> **证据**：`audit/evidence/F160-i161-train-featuredate-default/`（§5 退化现象的运行证据）
> **受影响功能**：F152（F152 LTV 训练 ETL）、F153（F153 LTV 训练链路修复）、F160

---

## 0. 一句话描述

LTV 训练标签定义为「**特征快照日之后 90 天**的实际 GMV」，
而 ETL 端点 `runEtl` 缺省 `featureDate = now().minusDays(1)`（昨天），
**昨天的快照不可能有「未来 90 天」的数据** ⇒ `next_90d_revenue` 恒为 0、`next_90d_active` 恒为 0
⇒ 标签退化为常量 ⇒ 模型学不到任何信号（指标全 0），且 `conclusion` 仍被判定为 **GOOD**。

**这不是代码 bug，是「标签窗口定义」与「ETL 时序」的业务设计冲突。**

---

## 1. 缺陷位置

| 位置 | 代码 | 作用 |
|---|---|---|
| `backend/.../service/LtvTrainingService.java` `runEtl()` | `LocalDate featureDate = LocalDate.now().minusDays(1); // 特征快照 T-1` | ETL 缺省快照日 = 昨天 |
| `backend/.../service/LtvTrainingService.java` `computeLabels()` | `labelStart = featureDate.plusDays(1)`；`labelEnd = featureDate.plusDays(labelWindow)`（labelWindow 默认 90） | 标签取 **未来 90 天** |
| `backend/.../service/LtvTrainingService.java` `computeLabels()` 第二段 | `SET next_90d_revenue = 0, next_90d_active = 0 WHERE ... IS NULL` | 无未来消费 ⇒ **补 0**（不是留 NULL） |
| `backend/.../service/LtvTrainingService.java` `trainModel()` | `SELECT ... next_90d_revenue AS label ... WHERE next_90d_revenue IS NOT NULL` | 过滤条件把「补 0」的样本**全部留下** |

**致命组合**：`computeLabels` 把「没有未来数据」补成 **0** 而不是留 `NULL`，
而 `trainModel` 的样本过滤只排除 `IS NULL` ⇒ **全 0 标签的样本被当作有效训练样本**。

---

## 2. 运行证据（L5b，2026-11-13 21:12）

I161 Layer 1 修复后，训练端点首次**成功**返回，但指标全 0：

```
POST /intel/ltv/training/train（不带 featureDate）
  featureDate     = 2026-11-10
  trainingSamples = 489
  trainMAE=0.0000  trainRMSE=0.0000  trainMAPE=0.0000  trainR2=0.0000
  valMAE=0.0000    valRMSE=0.0000    valMAPE=0.0000    valR2=0.0000
  conclusion=GOOD          ← 退化常量模型被判定为 GOOD
  topFeatures: recency_days=0.0, frequency_total=0.0, monetary_total=0.0, ...（权重全 0）

POST /intel/churn/training/train（不带 featureDate）
  featureDate     = 2026-11-10
  trainingSamples = 489
  positiveRate    = 100.0%    ← 全部样本同标签（label=1）
  valAUC=0.5  valF1=1.0  valPrecision=1.0  valRecall=1.0
  conclusion=POOR
```

DB 快照：

```
feature_date   cnt   labeled_rev   labeled_act
2026-11-10     489   489           489
```

原始响应：`audit/evidence/F160-i161-train-featuredate-default/raw/02-ltv-train-no-featuredate.json`、`raw/03-churn-train-no-featuredate.json`

**推论链**：
`feature_date = 2026-11-10` ⇒ 标签窗口 = `2026-11-11 ~ 2027-02-08` ⇒ 该区间无消费数据
⇒ `next_90d_revenue` 全 0（`computeLabels` 补 0）⇒ 回归目标常量 ⇒ 权重全 0 ⇒ 指标全 0。

---

## 3. 影响

| 维度 | 影响 |
|---|---|
| 功能 | LTV / Churn 训练「跑通了」，但产出**无区分度**的常量模型 |
| 指标误导 | LTV `conclusion=GOOD`（因 MAPE=0 被判优）、Churn `conclusion=POOR`（因 AUC=0.5）—— **两个结论都不是真实质量** |
| 下游 | 与 **I155**（预测端完全不消费训练模型）叠加 ⇒ 训练产物目前**无任何生产影响**；但一旦 I155 修好，错误模型会被部署使用 |
| 数据 | `ltv_training_feature` 每次 ETL 追加一份快照行，**旧快照不删** ⇒ 历史退化快照持续累积 |
| 掩盖 | `etlStats.trainingReady >= 100` 判定「样本就绪」，但**不校验标签是否有区分度** |

---

## 4. 待业务决策（**本 Issue 的核心不是代码，是口径**）

用户决策原文：「根因：不是代码 bug，是『标签窗口设计』问题——要么用历史数据（如 T-90 的快照，标签窗口已填满），要么改标签定义为『过去 90 天』。」

| 方案 | 做法 | 影响面 | 代价 |
|---|---|---|---|
| **A. 历史快照法（推荐讨论）** | ETL 缺省 `featureDate` 取「**最近一个能凑满标签窗口的日期**」（即 `now() - labelWindow`），标签窗口仍是未来 90 天 | 改 `runEtl` 缺省逻辑 + 快照回溯 | 标签天然「已经填满」，指标有意义；但**每日 ETL 产出的是 T-90 的画像**，与「今日会员状态」差 90 天，用于**预测**（I155）时特征时效性存疑 |
| **B. 过去窗口法** | 标签改为「**特征快照日之前 90 天**」的实际 GMV（`monetary_90d` 已是这个口径，可直接复用） | 改 `computeLabels` 的时间区间 + 字段语义 | 特征与标签时间区间重叠 ⇒ **信息泄漏**（标签已被特征包含），模型指标会虚高 ⇒ 不可用于评估泛化能力 |
| **C. 双标签并行** | 同时产出 `next_90d_revenue`（未来 90 天，允许为空）与 `prev_90d_revenue`（过去 90 天，可立即算），训练用 A/B 各自评估 | 新增字段 + 双训练任务 | 工作量最大，但唯一能同时回答「历史外推能力」与「线上可预测性」 |

**另需决策的配套项**：

| # | 事项 | 说明 |
|---|---|---|
| 4.1 | 标签无数据时补 0 还是留 NULL | 当前补 0 ⇒ 全 0 样本混入训练。应改为留 `NULL` 并在训练时排除，或显式打「标签未成熟」标记 |
| 4.2 | 样本成熟度校验 | `etlStats.trainingReady` 应校验「标签有区分度」（如标签方差 > 0），而非只看行数 ≥ 100 |
| 4.3 | 退化模型的质量门禁 | `conclusion` 判定不应把 MAPE=0 的常量模型判为 GOOD（`LtvTrainingService.evalConclusion`） |
| 4.4 | 快照行累积 | `ltv_training_feature` 每日追加行、旧行不删；是否需要保留策略 / 归档 |
| 4.5 | 快照遮蔽效应 | I161 Layer 1 引入的 `MAX(feature_date)` 规则会**优先选中最新的退化快照**（例如刚跑过 ETL 的 T-1 快照）而非真正可用的 T-90 快照 —— 若采纳方案 A，两者可自洽；若采纳 B/C，需重新定义「最新可用」的判定 |

---

## 5. 明确不做（本轮）

| 项 | 说明 |
|---|---|
| 改 `runEtl` / `computeLabels` 的标签窗口 | 用户决策「不动 I163（标签窗口设计，需业务决策）」 |
| 改 `conclusion` 判定 / 加退化模型门禁 | 同上，属 4.3 待决项 |
| 改预测端消费模型 | 属 **I155**，独立 Issue |

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 业务上 LTV 模型的**目标**是什么（预测未来 LTV 用于分层运营？还是评估历史外推能力？） | **未确认** —— 决定 A / B / C 的选择 |
| 生产环境 `ltv_training_feature` 的历史快照分布与标签非零比例 | **未确认** —— 本地仅 1 天快照（2026-11-10），生产未查 |
| `labelWindow` 是否可配（当前 `runEtl` 支持 body 传 `labelWindow`） | **已确认存在入参**，但前端 ETL 调用（`LtvTraining.vue:483`）传的是 `{}` ⇒ 走默认 90 |
| Churn 侧标签（`next_90d_active`）是否应改用「过去 90 天是否活跃」 | **未确认** —— 与 4.x 同源决策 |
| `monetary_90d` 是否与「标签」在方案 B 下完全重复 | **未确认** —— `extractFeatures` 已算 `monetary_90d`（`featureDate-180d` 起的滚动 90 天），与方案 B 的「过去 90 天」窗口起点需逐项核对 |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 核实 commit | **`1be1e7c`** `docs(audit): 补登 I161 至 issue-register`（本 Issue 由 I161 Layer 1 修复后的运行结果反推登记） |
| 源码依据 | `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java`（`runEtl` L73、`computeLabels` L~300-330、`trainModel` L~318-345、`evalConclusion`） |
| 运行时 | Docker `meiyetec-api`（:8080，healthy），2026-11-13 21:12 |
| DB 快照 | `ltv_training_feature` 仅 `2026-11-10` 一天，489 行，489 行有标签 |
| 证据目录 | `audit/evidence/F160-i161-train-featuredate-default/`（§5 退化现象） |
| 取证时间 | 2026-11-13 21:12 – 21:20 |
