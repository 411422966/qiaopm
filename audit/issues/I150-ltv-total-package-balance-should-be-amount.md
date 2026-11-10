# I150 — `ltv_training_feature.total_package_balance` 口径错误：存的是「剩余次数」，字段语义是「余额（金额）」

> **状态**：Open
> **严重度**：P2 — 特征值语义错配，不报错、不崩，但污染 LTV 模型的输入特征
> **发现日期**：2026-11-11（I142 批 1 修复过程中发现）
> **登记人**：Cursor（I142 批 1 — LTV 训练 ETL 8 处表名/列名修复）
> **决策**：用户 2026-11-11 拍板 —— **本轮用 `SUM(remaining_times)`（剩余次数）**，并单独立 issue 记录「应改为金额口径」
> **关联**：I142（`LtvTrainingService` 引用 4 张不存在的表）、I151（LTV GMV 未扣减部分退款）、I152（`crm_order` 引用外溢 17 处）

---

## 0. 一句话描述

`LtvTrainingService.extractFeatures` 抽取的 `total_package_balance` 特征，
底层取自 `crm_package_order.remaining_times`（**套餐剩余次数**，整数），
但字段名 `total_package_balance` 与下游消费方（模型训练 + `LtvTrainingFeature.totalPackageBalance`）
都按「**余额（金额）**」理解 —— **量纲错配**：一个「次数」被当成「元」喂给回归模型。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java` |
| 方法 | `extractFeatures`（private）—— 第 1 段 INSERT…SELECT 的套餐子查询 |
| SQL | `SELECT member_id, COUNT(*) AS active_packages, SUM(remaining_times) AS total_remaining_times FROM crm_package_order WHERE deleted = 0 AND status = 1 AND expire_time > ? GROUP BY member_id` |
| 落库列 | `ltv_training_feature.total_package_balance`（`decimal(12,2)`） |
| 实体 | `backend/src/main/java/com/meiyetec/entity/LtvTrainingFeature.java` — `totalPackageBalance` |
| 特征清单 | `LtvTrainingService.FEATURE_COLUMNS`（L56-60）—— `total_package_balance` 是模型第 12 个输入特征 |

### 1.1 字段语义错配链路

```
crm_package_order.remaining_times   （int，套餐剩余【次数】）
        ↓ SUM()
   total_remaining_times             （int）
        ↓ COALESCE(..., 0)
   ltv_training_feature.total_package_balance   （decimal(12,2)）
        ↓ FEATURE_COLUMNS
   MLR 训练输入特征（与 monetary_total / wallet_balance 等【金额量纲】特征并列）
```

`monetary_total` / `aov` / `wallet_balance` 都是金额量纲，`total_package_balance` 是次数量纲，
**未经归一化直接并列送入梯度下降** —— 数值分布差异会导致该特征权重失真。

### 1.2 原始设计的意图（推断）

原 SQL 写的是 `SUM(remaining_value)` + 别名 `total_balance`：
`remaining_value` 暗示**金额**，但 `crm_package_order` 表**根本没有这一列**（实际列是 `remaining_times`）。
即：原始设计者本意是「剩余**金额**」，只是写表名/列名时臆造了一个不存在的列。
I142 修复时因该列不存在，只能退而使用语义最接近的真实列 `remaining_times` → 量纲错配由此产生。

---

## 2. 实测证据

### 2.1 `crm_package_order` 无 `remaining_value` 列，只有 `remaining_times`

```
SHOW CREATE TABLE crm_package_order;
  `total_times`     int NOT NULL,
  `remaining_times` int NOT NULL,
  `price`           decimal(12,2) NOT NULL,
```

### 2.2 修复后 ETL 落库值（2026-11-11 实测）

```sql
SELECT SUM(active_packages), SUM(total_package_balance) FROM ltv_training_feature;
-- 结果： 1  |  124.00
```

489 条特征中仅 **1 条** `active_packages > 0`，`total_package_balance` 合计 **124.00** ——
**124 显然不是金额（元），而是次数**。该值直接作为模型特征参与训练。

### 2.3 数据量极稀疏

```sql
SELECT COUNT(*) FROM crm_package_order WHERE deleted = 0 AND status = 1 AND expire_time > NOW();
-- 结果： 25
```

25 条有效套餐订单中，仅 1 条能匹配到 `crm_member`（489 个会员）——
**该特征在 488/489 条样本上恒为 0**，当前对模型无实际贡献，但口径错误仍会在数据积累后显现。

---

## 3. 影响面

| 受影响项 | 说明 |
|---|---|
| LTV 回归模型训练 | `total_package_balance` 作为第 12 个输入特征，量纲与其它金额特征不一致 |
| `ltv_training_feature` 表数据 | 已落库的值语义为「次数」，字段名/注释暗示「金额」 |
| 前端 `/dev/ltv-training` | 若有展示该字段的图表，会把次数当金额展示（**未确认**，前端未逐页核对） |

---

## 4. 建议修复（**本轮未实施**）

1. **首选**：新增金额口径 —— `SUM(price * remaining_times)`（套餐售价 × 剩余次数 = 剩余可消费金额），
   或从套餐定义表按 `package_id` 取单价再乘。
2. **次选**：保持次数口径但**改名** —— 列改为 `total_package_remaining_times`，
   同步改 `LtvTrainingFeature` 实体字段、`FEATURE_COLUMNS`、模型特征重要性 JSON 的 key
   （注意：**模型特征名变更会使历史模型不可比**，需同步处理 `ltv_model_registry.feature_importance_json`）。
3. **过渡期**：保留现列名，在字段注释与文档中明确标注「次数口径」，避免误读（**本轮已加实体注释**）。

> ⚠️ 涉及 `ltv_training_feature` 表结构变更时，须走 `db/sql/` 迁移脚本，
> 并遵循 EXT-19 / EXT-22 的 `ADD COLUMN IF NOT EXISTS`（MySQL ≥ 8.0.29）兼容约束。

---

## 5. 本轮已做的最小动作

| 项 | 内容 |
|---|---|
| 修复 | `SUM(remaining_value)`（不存在列）→ `SUM(remaining_times)`（真实列），别名同步改为 `total_remaining_times` |
| 注释 | `LtvTrainingFeature.totalPackageBalance` 增加注释：`套餐剩余总次数（口径：剩余次数，非金额；金额口径待 I150）` |
| 未做 | 未改表结构、未改字段名、未改模型特征名（用户拍板「本轮用次数口径」） |

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 前端是否有页面展示 `total_package_balance` | **未确认** |
| `crm_package_order.price` 是否为「整包售价」（可乘 remaining_times 得金额） | **未确认**（未逐条核对 price 与 total_times 的关系） |
| 历史模型是否已用该特征训练过 | **未确认** — 实测 `ltv_training_feature` 修复前 0 行，无历史模型可比 |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证命令 | `mysql --default-character-set=utf8mb4`（只读 `SHOW CREATE TABLE` / `SELECT`） |
| 取证时间 | 2026-11-11 |
| 关联 issue | I142（批 1 修复）、I151、I152 |
| commit hash | 见 commit 1 `fix(ltv): 修复 LtvTrainingService 8 处表名与列名错误（I142 批 1）` |
