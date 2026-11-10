# I151 — LTV GMV 未扣减部分退款（`pos_order.refunded_amount` 被忽略）

> **状态**：Open
> **严重度**：P2 — 特征值偏高，模型系统性高估客户 LTV
> **发现日期**：2026-11-11（I142 批 1 修复过程中发现）
> **登记人**：Cursor（I142 批 1 — LTV 训练 ETL 8 处表名/列名修复）
> **决策**：用户 2026-11-11 拍板 —— **本轮不扣减部分退款**，单独立 issue 记录
> **关联**：I142（`LtvTrainingService` 引用 4 张不存在的表）、I150（`total_package_balance` 口径）、I152（`crm_order` 引用外溢 17 处）

---

## 0. 一句话描述

LTV 训练的所有金额特征（`monetary_total` / `monetary_90d` / `aov` / `next_90d_revenue`）
统一取 `SUM(pos_order.final_amount)`，**未扣减 `pos_order.refunded_amount`（已退款总额）** ——
部分退款的订单被**按全额计入消费**，导致会员 LTV 被系统性高估。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java` |
| 方法 | `extractFeatures`（第 1 段 INSERT…SELECT）、`computeLabels`（标签计算） |
| 金额列 | `pos_order.final_amount`（`decimal(12,2)`，注释「实付金额」） |
| 未使用的列 | `pos_order.refunded_amount`（`decimal(12,2)`，注释「已退款总额」）、`pos_order.refund_count`（int，退款次数） |
| 落库列 | `ltv_training_feature.monetary_total` / `monetary_90d` / `aov` / `next_90d_revenue` |

### 1.1 缺陷机制

```sql
-- extractFeatures（第 1 段）
COALESCE(SUM(o.final_amount), 0)                                    AS monetary_total
COALESCE(SUM(CASE WHEN o.pay_time >= ? THEN o.final_amount ELSE 0 END), 0) AS monetary_90d
COALESCE(AVG(o.final_amount), 0)                                    AS aov
```

```sql
-- computeLabels
COALESCE(SUM(o.final_amount), 0) AS future_revenue
```

`pos_order` 表**同时存在** `final_amount`（实付）与 `refunded_amount`（已退款总额）两列。
当前 SQL 只取前者，**退款金额被 100% 丢弃**。

净消费口径应为 `final_amount - refunded_amount`。

### 1.2 与「已退款订单」的区分（重要）

`pos_order.status` 枚举：`1进行中 2已支付 3已退款 4已撤销`。
本轮修复已把「有效订单」判定为 `status = 2`（**已支付**），因此：

| 订单状态 | 是否计入 GMV | 说明 |
|---|---|---|
| `status = 2` 且 `refunded_amount = 0` | ✅ 全额计入 | 正常消费 |
| `status = 2` 且 `refunded_amount > 0` | ⚠️ **全额计入（错误）** | **部分退款**场景 —— 本 issue 缺陷 |
| `status = 3`（已退款） | ❌ 不计入 | 完全退款订单已被 `status = 2` 过滤排除，**无问题** |

即：**完全退款已由 `status = 2` 过滤正确排除，但部分退款（`status` 仍为 2）被按全额计入**。

---

## 2. 实测证据

### 2.1 `pos_order` 表确有退款金额列

```
SHOW CREATE TABLE pos_order;
  `final_amount`     decimal(12,2) NOT NULL COMMENT '实付金额',
  `paid_amount`      decimal(12,2) DEFAULT '0.00' COMMENT '已付金额',
  `refunded_amount`  decimal(12,2) DEFAULT '0.00' COMMENT '已退款总额',
  `refund_count`     int DEFAULT '0' COMMENT '退款次数',
  `status`           tinyint DEFAULT '1' COMMENT '状态:1进行中 2已支付 3已退款 4已撤销',
```

### 2.2 当前库中 `status = 2` 订单的退款情况（2026-11-11 实测）

```sql
SELECT COUNT(*) AS total,
       SUM(refunded_amount > 0) AS partially_refunded,
       SUM(refunded_amount) AS refunded_sum
FROM pos_order WHERE deleted = 0 AND status = 2;
```

（结果见 §7 取证明细；当前库中 `status = 2` 且 `deleted = 0` 共 **1290** 笔）
部分退款笔数与退款总额见实测输出 —— 即本 issue 的**实际影响量级**。

### 2.3 修复后 ETL 的金额特征取值（2026-11-11 实测）

```sql
SELECT member_id, monetary_total, aov FROM ltv_training_feature ORDER BY monetary_total DESC LIMIT 8;
```

典型值：`monetary_total = 347083.00` / `aov = 43385.38` ——
为**未扣减退款**的原始实付累加值。

---

## 3. 影响面

| 受影响项 | 说明 |
|---|---|
| `monetary_total` / `monetary_90d` / `aov` | 偏高（等于「实付」而非「净消费」） |
| `next_90d_revenue`（回归标签） | 偏高 → **模型学到的映射整体上移**，预测的 LTV 系统性偏高 |
| `ltv_model_registry` 训练出的模型 | 一旦部署（`POST /intel/ltv/training/model/{id}/deploy`），预测结果继承该偏差 |
| 已退款大额客户 | 偏差最大（单笔部分退款金额占比越高，偏差越大） |

---

## 4. 建议修复（**本轮未实施**）

1. **金额列改净消费口径**（推荐）：
   ```sql
   COALESCE(SUM(o.final_amount - COALESCE(o.refunded_amount, 0)), 0)
   ```
   需同步修改 4 处：`monetary_total` / `monetary_90d` / `aov`（AVG 也应基于净额）/ `next_90d_revenue`。
2. **`aov` 口径澄清**：`AVG(final_amount)` 是否应改为 `AVG(final_amount - refunded_amount)` ——
   需业务确认「客单价」定义是「下单实付」还是「净消费」。
3. **部分退款订单是否仍算「活跃」**：`next_90d_active` 目前按 `COUNT(*) > 0` 判断，
   即部分退款后仍有 `status = 2` 的订单 → 判为活跃。这可能是**期望行为**（客户仍回来过），
   但需业务确认。

> ⚠️ 修改金额口径会使**历史模型不可比**（当前库中 `ltv_training_feature` 修复前为 0 行，
> 无历史模型，故当前修改**无历史包袱**）。

---

## 5. 本轮已做的最小动作

| 项 | 内容 |
|---|---|
| 未改 | GMV 扣退款逻辑保持原样（用户拍板「本轮不扣减部分退款」） |
| 已记录 | 本 issue 完整登记缺陷位置、量级、修复方案 |

---

## 6. 未确认项（禁止猜测）

| 项 | 值 |
|---|---|
| `refunded_amount` 是否总是 ≤ `final_amount` | **未确认**（未逐条核对） |
| `status = 2` 但 `refunded_amount > 0` 的业务含义是否确为「部分退款」 | **未确认**（表注释只说「已退款总额」，未核对退款流程代码的状态回写逻辑） |
| `aov` 的业务口径定义 | **未确认**（无 PRD） |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证命令 | `mysql --default-character-set=utf8mb4`（只读 `SHOW CREATE TABLE` / `SELECT`） |
| 取证时间 | 2026-11-11 |
| 关联 issue | I142（批 1 修复）、I150、I152 |
| commit hash | 见 commit 1 `fix(ltv): 修复 LtvTrainingService 8 处表名与列名错误（I142 批 1）` |
