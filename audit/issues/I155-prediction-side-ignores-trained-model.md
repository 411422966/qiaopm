# I155 — 预测端完全不消费训练出来的模型：部署成功的模型是"空挂"，两套模型注册表并存

> **状态**：Open
> **严重度**：P1（架构级）—— 不报错、不崩，训练与部署全链路 200，但**训练产出的模型对业务预测毫无影响**
> **发现日期**：2026-11-11（I154 训练链路调查的附带发现）
> **登记人**：Cursor（训练链路只读调查）
> **决策**：用户 2026-11-11 拍板 —— **单独立 issue，不在本轮修改**（需业务决策）
> **关联**：I154（训练链路 6 个代码缺陷）、I152（`crm_order` 引用外溢）

---

## 0. 一句话描述

`ltv_model_registry.model_formula` 与 `lr_coefficients_json` 这两个字段
**在全仓没有任何消费点 —— 只有写入，没有读取**。

训练 → 部署链路完整跑通（接口全 200、模型确实落库、`is_active=1`），
但**预测端用的还是训练之前就存在的老公式**：
- `LtvService.computeForMember` → 手写 BG/NBD 公式 + `pos_order` 实算
- `DataGovernanceService.batchPredictLtv` → `AVG(pay_amount) * 12`

**训练 → 部署 → 预测这条链的第三段是断的。**

---

## 1. 事实链

### 1.1 训练侧确实产出了东西

`POST /intel/ltv/training/train` 返回 `modelId`，并写入：

| 字段 | 内容 | 是否有消费点 |
|---|---|---|
| `model_formula` | MLR 公式字符串 `LTV = 741.9384 + 253.1469 * ((recency_days - 249.5000) / 86.6021) + ...` | ❌ **无** |
| `feature_importance_json` | 特征重要性 JSON | ❌ **无**（仅前端展示） |
| `lr_coefficients_json`（Churn） | LR 系数 JSON | ❌ **无** |
| `val_mae` / `val_auc` 等 | 评估指标 | ✅ 治理看板读 |

> `model_formula` 与 `lr_coefficients_json` 的写入点在
> `LtvTrainingService`（`model.setModelFormula(formula)`）与 `ChurnTrainingService`
> （`model.setLrCoefficientsJson(...)`）；**全仓 grep 后确认没有第二处读取**。

### 1.2 预测侧用的是另外两套逻辑

| 预测入口 | 实际算法 | 与训练模型的关系 |
|---|---|---|
| `service/LtvService.java:52-146` `computeForMember` | 手写 BG/NBD 公式 + `pos_order` 实算 | ❌ **完全无关** |
| `service/DataGovernanceService.java:512-538` `batchPredictLtv` | `COALESCE((SELECT AVG(pay_amount) FROM crm_order ...) * 12, 0)` | ❌ **完全无关** |

注意：`batchPredictLtv` **读了** `model_formula`（L512 处的 SELECT 把它查出来了），
但**从未使用它** —— 查出来只是为了满足"必须有一个已部署模型"的守卫，
实际写入 `crm_ltv_snapshot` 的值与公式无关。

### 1.3 两套模型注册表并存

| 注册表 | 来源 | 消费点 |
|---|---|---|
| `ltv_model_registry` / `churn_model_registry` | **本轮训练链路的产物**（`LtvTrainingService` / `ChurnTrainingService`） | 仅治理看板读指标，**公式无消费** |
| `ai_ltv_model` | AI 模块的独立注册表 | 未确认 |

两套注册表**互不感知**，各自有独立 DDL 与写入路径。

---

## 2. 影响面

| 受影响项 | 说明 | 状态 |
|---|---|---|
| **LTV 单会员预测** | `LtvService.computeForMember` 走 BG/NBD，训练成果完全不参与 | ✅ 已确认 |
| **LTV 批量预测** | `batchPredictLtv` 写 `crm_ltv_snapshot` 用 `AVG*12`，与模型无关 | ✅ 已确认 |
| **Churn 批量预测** | `batchPredictChurn` 用 `recency/1.8` 硬编码阈值，非模型输出 | ✅ 已确认 |
| **模型可信度** | 治理看板展示的 `val_mae` / `val_auc` 与业务实际用的算法**不是同一个东西** | ✅ 已确认 |
| **训练投入的价值** | 每次训练+部署都成功，但**对业务结果零影响** | ✅ 已确认 |
| **`ai_ltv_model` 与新注册表的关系** | 是否有意并存、是否需要收敛 | **未确认** |
| **`crm_ltv_snapshot.model_version` 字段值 `'v1'`** | 写入的是硬编码 `'v1'`，与实际模型版本无关 | ✅ 已确认（代码 `L538` 附近） |

---

## 3. 待业务决策事项

### Q1：预测端统一到训练出的模型，还是保留两套？

| 方案 | 描述 | 代价 |
|---|---|---|
| **A. 统一到训练模型** | `computeForMember` / `batchPredictLtv` 改为解析 `model_formula` 并执行 | 需引入公式求值器；训练标签窗口未满（当前恒 0）时模型无意义，上线即错 |
| **B. 保留两套** | 明确 `ltv_model_registry` 为"实验/评估用途"，`ai_ltv_model` + BG/NBD 为"生产用途" | 训练链路继续"空跑"，但语义清晰；需在 UI 上标注 |
| **C. 废弃训练链路** | 删除训练/部署端点与注册表，只保留 BG/NBD | 需先解决标签窗口问题（见 Q2） |

### Q2：标签窗口何时能产出有效样本？

`next_90d_revenue` 需要**未来 90 天**的真实消费数据，当前 489 行全 0。
即使统一到训练模型（方案 A），**在标签窗口填满之前，模型输出恒为 0**，比 BG/NBD 更差。

**是否已有历史回填机制（用历史订单反推过去 90 天标签）？** —— **未确认**。

### Q3：`lr_coefficients_json` 同理

Churn 模型的系数 JSON 同样无消费点。Churn 标签取反后（I154 ③）单类别率 100%，
训练质量同样受限于标签数据。

---

## 4. 附带发现（不在本 issue 主线）

| 项 | 说明 |
|---|---|
| `crm_ltv_snapshot.model_version` 硬编码 `'v1'` | 与实际模型版本无关，无法追溯快照由哪个模型产出 |
| `batchPredictLtv` 查了 `model_formula` 却不使用 | 疑似"半成品"实现残留，可作为方案 A 的改造起点 |
| `model_formula` 是字符串而非结构化 JSON | 若要走方案 A，建议同步新增结构化字段（如 `coefficients_json`），避免写公式解析器 |

---

## 5. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证方式 | 只读代码分析 + `SELECT` 查询 + 实际调用训练/部署/预测端点 |
| 取证时间 | 2026-11-11 |
| 关联 issue | I152（`crm_order` 引用外溢，导致批量预测实际写入 0 行）、I154 |
| 关联证据 | `audit/evidence/F153-ltv-training-chain-fix/` |
