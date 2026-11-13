# I157 — LTV 年化口径对短 tenure 新客的放大效应：无观测窗口下限，`predicted_ltv_365d` 可虚高数十倍

> **状态**：Open
> **严重度**：P2 —— 数值口径失真，不产生重复数据；影响面随「新客占比」线性放大
> **发现日期**：2026-11-13（I152 §4A Q1 补登时复核年化公式时发现）
> **登记人**：Cursor（I152 §4A Q1 遗留登记项复核）
> **关联**：I152（Q1 年化系数遗留项 —— 本 issue 是 Q1「换掉 `*12` 之后引入的新边界缺陷」）、I155（预测端不消费训练模型）、I151 / I152 Q6（退款未扣，口径叠加）、I156（同表 `crm_churn_prediction` 双写）
> **修复决策**：待业务拍板（方案 A / 方案 B，见 §5）

---

## 0. 一句话描述

I152 批 3 把 `DataGovernanceService.batchPredictLtv` 的年化口径从
「固定 `AVG(pay_amount) * 12`」改为「按实际消费周期推导年均单数」：

```sql
ROUND(COUNT(*) * 365 / GREATEST(GREATEST(DATEDIFF(CURDATE(), MIN(p.pay_time)), 0), 1), 2) AS purchase_frequency
```

这个公式**方向正确**（用真实消费周期替代拍脑袋常数 12），
但**观测窗口没有下限** —— 分母最小只取 **1 天**。
对注册不久、消费次数少的新客，`365 / tenure` 会把极少样本放大成极高的年化频率，
再乘以客单价后写入 `predicted_ltv_365d` / `predicted_ltv_lifetime`，
并连带把 `repurchase_prob_30d/90d/180d` 顶到最高档。

**这不是「保留 `*12`」的问题，是「换成另一种有边界缺陷的算法」的问题** —— 单独立项。

---

## 1. 缺陷位置

### 1.1 主写入路径：`batchPredictLtv`

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` |
| 方法 | `batchPredictLtv(Long tenantId, int limit)`（L525） |
| 入口 | `POST /governance/model/batch-predict`（`modelType=LTV`，L485 `batchPredict` → L499） |
| 年化公式 | L585-586 `purchase_frequency = ROUND(COUNT(*) * 365 / GREATEST(GREATEST(DATEDIFF(CURDATE(), MIN(p.pay_time)), 0), 1), 2)` |
| **分母下限** | **1 天**（`GREATEST(..., 1)`） |
| 放大链 | L562-565 `pred_ltv_30d/90d/365d/lifetime = aov * purchase_frequency * {1/12, 1/4, 1, 3}` |
| 连带影响 | L566-577 `repurchase_prob_30d/90d/180d` 按 `purchase_frequency` 分档：`>=12 → 0.85/0.95/0.99`，`>=4 → 0.70/0.80/0.90`，否则 `0.45/0.55/0.70` |
| 落库表 | `crm_ltv_snapshot`（列 `purchase_frequency decimal(8,2)`、`predicted_ltv_365d decimal(12,2)`、`predicted_ltv_lifetime decimal(12,2)`、`repurchase_prob_* decimal(5,4)`） |

### 1.2 第二写入路径：`computeLtv`（同型缺陷，未被 I152 批 3 覆盖）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` |
| 方法 | `computeLtv()`（L75），`@PostMapping("/ltv/compute")`（L73） |
| 年化公式 | L111 `double freqPerYear = orders.size() * (tenureDays <= 0 ? 365.0 : 365.0 / tenureDays);` |
| **分母下限** | **等效 1 天**（`tenureDays <= 0` 分支直接取 `365.0`，等价于 `365 / 1`） |
| 放大链 | L112-117 `predictedLtv30d/90d/365d = aov * freqPerYear * {1/12, 1/4, 1}`；L127 `predictedLtvLifetime = predictedLtv365d * 3` |
| 连带影响 | L128-130 `repurchaseProb30d = freqPerYear > 12 ? 0.85 : 0.30`、L129 `90d = freqPerYear > 4 ? 0.7 : 0.45`、L130 `180d` 恒 `0.55` |
| 落库表 | 同 `crm_ltv_snapshot` |

> ⚠️ **两条路径公式代数等价**（`COUNT*365/tenure` ≡ `orders.size() * 365/tenureDays`，
> tenure 起算点均为最早 `pay_time` 的日期部分），因此**缺陷在两条路径上同时存在**，
> 修复必须**成对处理**，只改一处会引入两条路径的口径分叉。

### 1.3 反例：Churn 侧已经用了 30 天下限，LTV 侧没用

同文件 `batchPredictChurn` 的月频公式（L646-647）：

```sql
ROUND(COALESCE(o.total_orders, 0) / GREATEST(GREATEST(COALESCE(o.tenure_days, 0), 0), 30) / 30, 2) AS monthly_freq
```

**分母下限是 30 天**，而不是 LTV 侧的 1 天。
即：**同一份代码里，Churn 侧作者已经意识到「短 tenure 会放大」并设了 30 天窗口，
LTV 侧却用了 1 天** —— 这是「LTV 侧下限应为 30 天」的**内部一致性佐证**，
也是方案 A 的直接依据。

---

## 2. 放大效应推演

公式：`purchase_frequency = COUNT(*) * 365 / max(tenure_days, 1)`，
`predicted_ltv_365d = avg_order_value * purchase_frequency`，
`predicted_ltv_lifetime = predicted_ltv_365d * 3`。

以客单价 `aov = 500 元` 为例（仅用于展示放大倍数，非真实数据）：

| 场景 | COUNT | tenure | `purchase_frequency` | `predicted_ltv_365d` | `predicted_ltv_lifetime` | `repurchase_prob_30d` | 相对历史消费额倍数 |
|---|---|---|---|---|---|---|---|
| **新客 A** | 1 | **7 天** | **52.14** | **26,070** | **78,210** | **0.85**（最高档） | **×52.1** |
| **新客 B** | 1 | **1 天** | **365.00** | **182,500** | **547,500** | **0.85** | **×365.0** |
| 新客 C | 3 | 7 天 | 156.43 | 78,215 | 234,645 | 0.85 | ×156.4 |
| 边界 D | 1 | 30 天 | 12.17 | 6,085 | 18,255 | 0.85 | ×12.2 |
| 稳态 E | 12 | 365 天 | 12.00 | 6,000 | 18,000 | 0.85 | ×12.0 |
| 稳态 F | 4 | 365 天 | 4.00 | 2,000 | 6,000 | 0.70 | ×4.0 |

**两个独立的问题叠加在场景 A/B 上**：

1. **量级放大** —— 观测窗口越短，年化频率越高（场景 B 达 ×365）。
   单笔 500 元的消费被外推成 18 万元年 LTV。
2. **置信度倒挂** —— `repurchase_prob_30d` 按 `purchase_frequency` 分档，
   **样本最少的新客（1 单）反而拿到与年消费 12 次的老客（场景 E）相同的最高档 0.85**，
   而一个年消费 4 次的稳态客户只有 0.70 —— **预测置信度与证据强度反向**。

> 场景 D 揭示了一个额外的口径断裂：**分母恰好 30 天** 时 `freq = 12.17`，
> 跨过 30 天后频率开始下降，但 `repurchase_prob_30d` 的分档阈值是 `>= 12`，
> 于是 **30 天档与 365 天档的复购概率相同（都是 0.85）**，而两者实际证据量差 12 倍。

---

## 3. 影响面（本地库实测，2026-11-13）

### 3.1 无法在本地复现 —— 缺新客样本

```
-- 租户 1 有消费会员的 tenure 分布
MIN(tenure)=75   MAX(tenure)=333   AVG(tenure)=168.14   members=292
-- 分桶
d_90-180d  156
c_30-90d    13
e_180d+    123
-- tenure < 30 天的有消费会员
new_members_30d = 0        ← 本地无新客样本
```

**本地 `meiyetec` 库的 292 个有消费会员，最短消费周期 75 天**，
`crm_member` 489 行的 `create_time` 全部落在 72–91 天前（`created_30d = 0`、`created_90d = 389`）
→ **本地环境无法构造出 tenure < 30 天的会员，§2 的放大效应属于公式推演，非本地实测**。

**生产库不可访问**，新客占比与实际放大倍数**未确认**。

### 3.2 本地可观测的存量后果（同型但非新客成因）

```
-- 2026-11-12 快照的 purchase_frequency 分布
min=0.00   max=41.01   avg=4.31   purchase_frequency > 24 的会员数 = 1

member_id  total_orders  purchase_frequency  aov      historical_ltv  pred_ltv_365d  pred_ltv_lifetime  prob_30d
1          10            41.01               100.00   1000.00         4101.00         12303.00          0.8500
1414       16            20.21               39753.25 636052.00      803413.18      2410239.55         0.8500
1219       16            19.47               39878.81 638061.00      776440.48      2329321.44         0.8500
```

- `member_id = 1` 是**真实高频客**（90 天内 10 单，`pay_time` 从 2026-08-15 到 08-24），
  `freq = 10*365/90 = 41.01` → 其 `predicted_ltv_365d = 4101` 对 `historical_ltv = 1000`，
  倍率 **×4.1** —— **这个场景是合理的**，不是缺陷。
- 说明：**公式在「观测窗口足够长」时表现正常，问题只在窗口不足时暴露** →
  修复应当只针对**短 tenure 会员**做截断，**不应改动长周期会员的既有数值**（避免大面积数值漂移）。

### 3.3 整体量级参考

```
-- 2026-11-12 快照全量（489 行）
SUM(predicted_ltv_365d) = 43,312,487     SUM(historical_ltv) = 27,875,629
整体倍率 = 1.55×
```

本地数据整体倍率 1.55×（属正常外推范围）；**新客占比高的租户该倍率会显著更高** —— 量级未确认。

### 3.4 无消费会员不受影响

`crm_member` 中 257 个会员无任何 `pos_order` 记录 →
LEFT JOIN 后 `purchase_frequency` 为 `NULL` → `COALESCE(..., 0) = 0` → 不触发放大。
**放大只影响「有消费但消费次数少 / 消费期短」的会员。**

---

## 4. 与既有登记项的关系

| 关联项 | 关系 |
|---|---|
| **I152 §4A Q1** | Q1 是「`* 12` 换成 tenure 推导年均单数」这一**变更本身**的遗留登记；**本 issue 是该变更引入的新边界缺陷**。Q1 章节已写明「新客 tenure 短时会被放大」，本 issue 把它从一句话升级为独立可跟踪项 |
| **I155** | 预测端不消费训练模型 —— 训练出的模型理论上能自行处理小样本，**规则版无此能力**；两者叠加时规则版的缺陷更突出 |
| **I151 / I152 §4A Q6** | 退款未扣使 `historical_ltv` 与 `aov` 偏高 → 与本 issue 的外推叠加，**同一会员的最终 LTV 被双重放大** |
| **I156** | `crm_churn_prediction` 双写 → `computeChurn` 消费 `crm_ltv_snapshot` 的 `last_purchase_days`，
**不直接消费 `purchase_frequency`**，故 I156 的口径分叉**不直接放大 churn 分数**；但 `computeChurn` 把**年化** `purchase_frequency` 写进 `crm_churn_prediction.monthly_freq` 字段（见 §6 相邻观察） |

---

## 5. 修复方向（二选一，待业务决策）

### 方案 A：年化分母设 30 天下限（不足 30 天按 30 天算）

- 改动点 1：`DataGovernanceService.batchPredictLtv` L585-586
  `GREATEST(GREATEST(DATEDIFF(...), 0), 1)` → `GREATEST(GREATEST(DATEDIFF(...), 0), 30)`
- 改动点 2：`LtvChurnController.computeLtv` L111
  `tenureDays <= 0 ? 365.0 : 365.0 / tenureDays` → `365.0 / Math.max(tenureDays, 30)`
- **依据**：与同文件 `batchPredictChurn` 的 `monthly_freq`（L646-647，下限 30 天）**口径对齐**（§1.3）
- **优点**：改动面最小（2 处常量 + 2 处表达式）；长周期会员数值**完全不变**（`tenure >= 30` 时公式等价）→ **无历史数值漂移**
- **代价 / 需决策**：
  1. **新客仍被外推**（30 天窗口下 1 单 → `freq = 12.17`，仍虚高 ×12），只是把放大倍数从 ×52/×365 压到 ×12；
  2. **30 天是拍脑袋的新常数** —— 换了个任意值，业务需确认；是否应随场景变化未确认；
  3. **存量 `crm_ltv_snapshot` 的新客行需重算**（当前本地无新客样本，生产未确认）；
  4. `repurchase_prob_*` 的分档阈值（`>=12` / `>=4`）**是否随之调整**需一并确认 —— 截断后新客 `freq` 落到 12.17 档，**仍是最高档 0.85**，§2 的「置信度倒挂」问题**未被方案 A 解决**。

### 方案 B：tenure < 90 天的新客单独标记，LTV 用实际累计金额不外推

- 改动点：两条路径均需先识别「短 tenure 会员」（`tenure_days < 90`），
  对其**不做年化外推**：`predicted_ltv_365d = historical_ltv`（实际累计消费），`purchase_frequency` 置空或标记为「样本不足」。
- **优点**：**从根本上消除新客的虚假外推** —— §2 的量级放大与置信度倒挂**同时解决**；
  语义诚实（「新客 LTV 尚未可知，先记已发生额」）。
- **代价 / 需决策**：
  1. **需要一个新的标记位** —— `crm_ltv_snapshot` 当前**没有「样本不足 / 外推禁用」标识列**，
     要么加列（需 DDL 迁移），要么用 `model_type` 区分（当前只有 `'RULE'` 一个值，L550 硬编码）；
  2. **看板与下游口径变化** —— 新客的 `predicted_ltv_365d` 从「外推值」变为「实际累计额」，
     `avgLtv365d` / `totalLtv365d`（`GET /ltv-churn/dashboard` L226-233）的**业务含义变了**（混入了非年化值）→ 需业务确认是否接受，或在看板侧分开统计；
  3. **训练侧影响** —— `ltv_training_feature` 的标签若依赖 `predicted_ltv_365d`，口径变化会污染训练样本 → 需先确认训练链路的标签来源（与 I154 / I155 相关）；
  4. **需明确 90 天这个阈值**（业务需拍板）。

### A / B 之外的补充动作

| # | 动作 | 说明 |
|---|---|---|
| 1 | **`repurchase_prob_*` 分档阈值单独评估** | §2 证明「样本量 ↔ 置信度」倒挂；两个方案都未解决该问题，建议**独立决策**：是否改为按「观测到的订单数 + tenure 是否足够」双条件分档 |
| 2 | **看板区分「外推 LTV」与「实际累计额」** | 配合方案 B；`GET /ltv-churn/dashboard` L226-233 当前把两者混在 `totalLtv365d` 里累加 |
| 3 | **存量数据重算** | 若采纳任一方案，`crm_ltv_snapshot` 存量行需重算；本地 489 行（2026-11-12 快照）无新客样本、**数值不受影响**，生产未确认 |
| 4 | **两条路径必须成对修改** | §1.2 —— `batchPredictLtv` 与 `computeLtv` 公式等价，漏改任一处即产生口径分叉 |

---

## 6. 相邻观察：同族量纲缺陷（未单独登记，仅备注）

`crm_churn_prediction.monthly_freq` 字段在两条写入路径下**存的不是同一种量**：

| 写入者 | 写入值 | 实际含义 |
|---|---|---|
| `batchPredictChurn`（L646-647） | `total_orders / max(tenure,30) / 30` | **真实月频**（≈ 0.00–0.5） |
| `computeChurn`（`LtvChurnController` L194 `p.setMonthlyFreq(s.getPurchaseFrequency())`） | `crm_ltv_snapshot.purchase_frequency` | **年化单数**（0–365） |

本地实测该列实际值：

```
id     member_id  total_orders  monthly_freq  tenure_days
2534   1          10            0.00          89
2888   1414       16            0.00          289
```

—— 均为 batch 写入的 `0.00`（`10/89/30 ≈ 0.0037` 被 `ROUND(...,2)` 抹平），
**该列当前对 Churn 消费几乎无信息量**。

这与 I156 §1.3 记录的「两个写入者口径不一致」同源，但**落在不同字段**（`churn_score` 精度 / `risk_level` 阈值 vs `monthly_freq` 量纲），
且**未影响 churn 分数计算**（`computeChurn` 不读 `monthly_freq`，`batchPredictChurn` 也不用它算分）→ **不单独立 issue**。

---

## 7. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境新客占比与实际放大倍数 | **未确认** —— 生产库不可访问；本地无 tenure<30 样本（§3.1），§2 为公式推演 |
| `crm_ltv_snapshot` 是否需要新增「样本不足」标识列 | **未确认** —— 需 DDL 迁移决策（方案 B 前提） |
| 训练链路 `ltv_training_feature` 的标签是否依赖 `predicted_ltv_365d` | **未确认** —— 未审阅 `LtvTrainingService` 标签生成逻辑；影响方案 B 的可行性评估 |
| 30 天 / 90 天两个阈值的业务依据 | **未确认** —— 均为待拍板的建议值 |
| `repurchase_prob_*` 分档阈值是否需同步调整 | **未确认** —— 依赖业务对「预测置信度」的定义 |
| `GET /ltv-churn/dashboard` 的 `totalLtv365d` 是否被外部报表 / 第三方消费 | **未确认** —— 未做调用方全仓扫描；影响方案 B 的口径变更风险评估 |

---

## 8. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`），租户 1 |
| 核实 commit | **`43bbcce`** `fix(ltv): 修复 batchPredictLtv/Churn 表名+量纲+幂等（I152 批 2-3）` |
| 源码依据 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` L525-604（`batchPredictLtv`）、L646-647（Churn 侧 30 天下限）、L616-702（`batchPredictChurn`）；`backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` L73-148（`computeLtv`） |
| DB 快照 | `crm_ltv_snapshot` 租户 1：`snapshot_date=2026-11-12` 489 行，`purchase_frequency` min 0.00 / max 41.01 / avg 4.31；`crm_churn_prediction` 489 行；`crm_member` 489 行（`create_time` 全部 72–91 天前）；`pos_order` `status=2` 1290 行 |
| 核心查询 | tenure 分布分桶、`crm_ltv_snapshot` 各快照日 frequency 分布、`SUM(predicted_ltv_365d)/SUM(historical_ltv)`、无消费会员计数 |
| 关联 issue | I152（§4A Q1）、I155、I151、I156 |
| 取证时间 | 2026-11-13 15:20 – 15:45 |
