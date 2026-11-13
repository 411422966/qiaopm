# I158 — `repurchase_prob_30d` 分档只看频率不看样本量，置信度与证据强度反向

> **状态**：Open
> **严重度**：P1（模型输出误导）—— 不产生脏数据，但直接把运营决策带偏
> **发现日期**：2026-11-13（I157 立项时复核复购概率分档逻辑发现）
> **登记人**：Cursor（I157 LTV 年化口径立项的连带发现）
> **关联**：I157（同源 —— 均为「样本量不足却按强样本口径输出」）、I152（§4A Q1 年化口径遗留）、I155（预测端不消费训练模型 —— 训练模型本可自行处理小样本）、I151 / I152 §4A Q6（退款未扣，口径叠加）
> **修复决策**：待业务拍板（方案 A / 方案 B，见 §5）

---

## 0. 一句话描述

`crm_ltv_snapshot.repurchase_prob_30d/90d/180d` 的分档规则**只按 `purchase_frequency`（年均单数）分档**，
**完全不看 tenure（观测时长）与样本量（订单数）**：

| `purchase_frequency` | `prob_30d` | `prob_90d` | `prob_180d` |
|---|---|---|---|
| `>= 12` | **0.85** | 0.95 | 0.99 |
| `>= 4` | **0.70** | 0.80 | 0.90 |
| `< 4` | **0.45** | 0.55 | 0.70 |

于是：

- **1 单新客**（tenure 7 天 → 年化 52.14）→ **0.85**（最高档）
- **年消费 12 次的老客**（观测 365 天）→ **0.85**（同一档）
- **年消费 4 次的稳态客户**（观测 365 天）→ **0.70**

**样本量最弱的新客与证据最强的老客拿到完全相同的置信度，
而证据强度中等的稳态客户反而更低** —— **置信度与证据强度反向**。

而这个字段**直接驱动前端运营决策**：LTV 看板的「30 天平均复购概率」KPI 取自它的平均值，
单会员风险分级据此派发「建议 P0 召回 / P1 触达」等动作。

---

## 1. 缺陷位置

### 1.1 主写入路径：`batchPredictLtv`

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` |
| 方法 | `batchPredictLtv(Long tenantId, int limit)`（L525） |
| 入口 | `POST /governance/model/batch-predict`（`modelType=LTV`，L485 `batchPredict` → L499） |
| 分档代码 | L566-577（三个 `CASE WHEN` 表达式） |
| **数据源** | `o.purchase_frequency`（**年化单数**，L585-586 `COUNT(*)*365/max(tenure,1)`） |
| **未使用的字段** | `o.tenure_days`（L674 已算出并使用于 `monthly_freq`，**但未参与概率分档**）、`o.total_orders`（仅用于 `= 0` 判零） |
| 落库列 | `crm_ltv_snapshot.repurchase_prob_30d/90d/180d`，类型 `decimal(5,4)` |

```sql
CASE WHEN COALESCE(o.total_orders, 0) = 0 THEN 0
     WHEN o.purchase_frequency >= 12 THEN 0.85
     WHEN o.purchase_frequency >= 4 THEN 0.70
     ELSE 0.45 END AS prob_30d
```

> 注意 `total_orders = 0` 的会员被正确置 0（无消费不该有复购概率），
> 但 `total_orders = 1` 的会员（**样本量最薄**）会直接进入 `>= 12` 档拿 0.85 ——
> **判零做了，判「样本是否够」没做**。

### 1.2 第二写入路径：`computeLtv`（分档阈值与取值都不同）

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` |
| 方法 | `computeLtv()`（L75） |
| 分档代码 | L128-130 |
| 数据源 | `freqPerYear`（L111，年化单数，与 batch 等价） |
| 落库列 | 同 `crm_ltv_snapshot.repurchase_prob_*` |

```java
s.setRepurchaseProb30d(BigDecimal.valueOf(freqPerYear > 12 ? 0.85 : 0.30).setScale(2, RoundingMode.HALF_UP));
s.setRepurchaseProb90d(BigDecimal.valueOf(freqPerYear > 4 ? 0.7 : 0.45).setScale(2, RoundingMode.HALF_UP));
s.setRepurchaseProb180d(BigDecimal.valueOf(0.55).setScale(2, RoundingMode.HALF_UP));
```

### 1.3 两条路径的分档差异（叠加缺陷）

| 维度 | `batchPredictLtv`（L566-577） | `computeLtv`（L128-130） |
|---|---|---|
| 阈值比较符 | `>=`（含边界） | `>`（不含边界） |
| `prob_30d` 低档 | **0.45** | **0.30** |
| `prob_30d` 中档 | 0.70 | **0.30**（只有两档） |
| `prob_90d` 低档 | 0.55 | 0.45 |
| `prob_180d` | 三档（0.99/0.90/0.70） | **恒定 0.55**（不随频率变化） |
| `total_orders = 0` | 显式置 0 | 无此分支 |
| 精度 | SQL `CASE` → `decimal(5,4)` | `setScale(2)` → 2 位小数 |

**同一个会员、同一天**，走 A 路径得 `0.85`，走 B 路径在 `freq` 恰为 12 时得 `0.30` ——
**差 2.8 倍**。该差异本身是独立缺陷（口径漂移），本 issue 以 `batchPredictLtv` 为主。

### 1.4 第三套算法存在但只用于单会员在线计算（未写快照）

`LtvService.computeRepurchaseProb`（`backend/src/main/java/com/meiyetec/service/LtvService.java:154-165`）是**连续函数**，
**已经正确处理了衰减与样本量**：

```java
double months = Math.max(tenureDays / 30.0, 1.0);          // ← tenure 有 30 天下限
double monthlyFreq = totalOrders / months;
double decay = Math.exp(-lastPurchaseDays / 60.0);          // ← 考虑最近购买距今
double freqFactor = Math.log1p(monthlyFreq) / Math.log(10); // ← log 压缩，1 单不会爆
double baseProb = Math.min(1.0, 0.1 + 0.3 * freqFactor + 0.5 * decay);
double horizonProb = 1 - Math.pow(1 - baseProb, horizonDays / 30.0);
return Math.max(0.01, Math.min(0.99, horizonProb));
```

**这段代码就是方案 A 的现成参考实现**（§5）。它服务于 `GET /intel/ltv/compute/{memberId}`（`LtvService` L47），
**写快照但不写 `repurchase_prob` 分档值**（L116-118 写入的是它自己算的连续值）——
而批量路径的分档值会覆盖/混入同表。

> ⚠️ **LtvService 存在租户过滤缺失**：L66 `FROM pos_order WHERE member_id = ? AND status = 2 AND deleted = 0`
> **无 `tenant_id` 条件**（对比 `DataGovernanceService` L676、L588 均带 `p.tenant_id = ?`）。
> 属跨租户数据口径问题，**不在本 issue 范围**，仅备注供后续处理。

---

## 2. 影响：直接驱动前端运营决策 🔴

### 2.1 前端消费点（`frontend/meiyetec-web/src/views/intel/ltv.vue`）

| 消费点 | 行号 | 数据来源 | 用途 |
|---|---|---|---|
| **KPI「30 天平均复购概率」** | L28 | `GET /intel/ltv/overview` → `AVG(crm_ltv_snapshot.repurchase_prob_30d)`（`LtvService` L262-265） | 看板主视觉指标 |
| 单会员详情「复购概率」进度条 ×3 | L147 / L254 / L258 / L262 | `GET /intel/ltv/compute/{id}` → `repurchaseProb30d/90d/180d` | 会员详情页展示 |
| **风险分级 → 运营动作派发** | L611-643 | `result.repurchaseProb30d` + `lastPurchaseDays` | **直接生成运营指令** |

### 2.2 运营动作派发逻辑（`ltv.vue` L611-643）

```javascript
const prob30 = Number(result.value.repurchaseProb30d || 0)
const lastDays = Number(result.value.lastPurchaseDays ?? 9999)
if (prob30 < 0.3 || lastDays > 90) {          // → '高风险 · 流失预警'，'建议 P0 召回：发大额券 + 客服外呼'
} else if (prob30 < 0.6 || lastDays > 45) {   // → '中风险 · 需关怀'，'建议 P1 触达：发关怀券 + 短信问候'
} else {                                       // → '健康 · 保持观察'
}
```

**倒挂在运营层的具体后果**：

| 会员类型 | `prob_30d` | 前端分级 | 派发动作 | 正确判断 |
|---|---|---|---|---|
| **1 单新客**（tenure 7 天） | **0.85** | 健康 · 保持观察 | 无动作 | ⚠️ 样本仅 1 单，不该断言「健康」 |
| 年消费 12 次老客 | 0.85 | 健康 · 保持观察 | 无动作 | ✅ 合理 |
| 年消费 4 次稳态客户 | 0.70 | 健康 · 保持观察 | 无动作 | ✅ 合理 |
| **1 单新客**（走 `computeLtv` 路径，freq=12.17 → `>12` 为真） | 0.85 | 健康 · 保持观察 | 无动作 | ⚠️ 同上 |
| **无消费会员**（`total_orders=0`） | 0（batch 路径显式置 0） | 高风险 · 流失预警 | P0 召回 | ✅ 合理（但该会员根本不该进 LTV 榜单） |

**注意最后一行与前面的矛盾**：无消费会员（**零样本**）会被判「高风险」并派发 P0 召回，
而 1 单新客（**同样证据不足**）会被判「健康」不做动作 ——
**同一个「证据不足」在两个方向上被误判**，说明该字段缺少「置信度」这一维度，
而只有「乐观/悲观」的二元倾向。

### 2.3 看板 KPI 被系统性抬高

`GET /intel/ltv/overview` 的 `avgProb30`（`LtvService` L262-265）= 最新快照日
`AVG(repurchase_prob_30d)`，**分母不含无消费会员之外的任何过滤**。

由于新客被批量抬到 0.85（最高档），
**新客占比越高，KPI「30 天平均复购概率」越虚高** —— 看板会系统性告诉运营「我们的会员复购意愿很高」。

### 2.4 本地数据实测（2026-11-13）

```
-- 2026-11-12 快照的概率分布（batch 路径写入）
SELECT repurchase_prob_30d, COUNT(*) FROM crm_ltv_snapshot
 WHERE tenant_id=1 AND snapshot_date='2026-11-12' GROUP BY repurchase_prob_30d;
```

本地 489 行快照的 `purchase_frequency` min 0.00 / max 41.01 / avg 4.31，
`> 24` 的仅 1 个会员（`member_id=1`，真实高频客）→ **本地无「1 单新客」样本**
（§4 有说明），**§2 的倒挂场景为公式推演**。

> 但**倒挂的判定逻辑本身与数据分布无关** —— 只要租户里存在 tenure 短、消费次数少的会员，
> 该会员就必然落入最高档。**这不是数据稀有的边缘情况，是新客业务的常态**。

---

## 3. 根因

```
分档输入 = purchase_frequency（年化单数）
分档输出 = 固定档位 0.45 / 0.70 / 0.85（及 90d / 180d）
缺失维度 = ① 观测时长 tenure   ② 样本量 total_orders
```

**年化单数本身是 I157 缺陷的产物** —— 它把短 tenure 的极少样本放大成极高的频率，
再被本 issue 的分档规则当作「高频客户」给最高置信度。
**I157 修掉年化放大后，一部分新客会从 0.85 落到 0.45/0.70，但倒挂的根因（不看样本量）仍在**。

**两个 issue 是「同源不同层」**：

| | I157 | I158（本 issue） |
|---|---|---|
| 缺陷层 | **数量**（LTV 金额） | **置信度**（复购概率） |
| 症状 | 新客 LTV 虚高数十倍 | 新客复购概率虚高至最高档 |
| 共同根因 | 用**年化频率**代表**观测强度**，忽略 tenure 与样本量 | 同上 |

---

## 4. 与既有登记项的关系

| 关联项 | 关系 |
|---|---|
| **I157** | 同源。I157 §5 配套动作 #1 已把「`repurchase_prob_*` 分档阈值是否需同步调整」列为待决策项 —— **本 issue 是该项的展开与独立立项**。修复 I157 方案 A（30 天下限）会让新客 `freq` 从 52.14 落到 12.17 → **仍 ≥12 → 仍是 0.85 最高档**，**倒挂不被解决** |
| **I155** | 预测端不消费训练模型 —— 训练出的模型本可对样本不足做正确处理；规则版无此能力，是本缺陷得以长期存在的前提 |
| **I152 §4A Q1 / Q6** | Q1 年化口径是本 issue 的输入；Q6 退款未扣使 `aov` 偏高 → 叠加放大 |
| **I151** | 同源口径问题（GMV 未扣退款） |
| **I156** | 同表 `crm_churn_prediction` 双写 —— `computeChurn` 消费 `crm_ltv_snapshot.last_purchase_days`，**不消费 `repurchase_prob_*`** → 与本 issue 无直接数据耦合 |

---

## 5. 修复方向（二选一，待业务决策）

### 方案 A：引入置信度系数（连续衰减，不做硬分档）

- 思路：把「年化频率」拆成**「观测到的频率」**与**「样本量置信度」**两个因子，
  概率 = 基础概率 × 置信度系数，样本越少系数越低。
- **现成参考实现**：`LtvService.computeRepurchaseProb`（`LtvService.java:154-165`，§1.4）
  —— 已包含 `months = max(tenureDays/30, 1)` 的窗口下限、`log1p` 频率压缩、`exp` 时间衰减，
  **可直接复用或抽出为公共方法**，让批量路径与在线路径共用同一套算法。
- 备选形式（用户建议）：`min(0.85, base + log(sample_size) * factor)` —— 需确定 `base` 与 `factor`。
- **优点**：
  1. **连续、无档位跳变** —— 不再出现「12 与 12.01 分属不同档」；
  2. 与已有的 `LtvService` 实现**同源**，消除三套算法（§1.1 / §1.2 / §1.4）并存；
  3. 顺带消除 §1.3 的两路径分档差异。
- **代价 / 需决策**：
  1. **存量快照全部重算** —— `repurchase_prob_*` 数值会大面积变化（当前 489 行），
     且 `crm_ltv_snapshot` 存在多历史日期（本地 3522 行）；
  2. **`log` / `exp` 参数需业务调参** —— `factor`、`decay` 系数无业务先例，需运营/数据团队标定；
  3. **与 I157 的修复顺序耦合** —— 若 I157 先改年化分母，概率公式的输入口径会再变一次 →
     **建议 I157 + I158 合并到同一批次**，避免两次全量重算；
  4. **看板 KPI 数值下移** —— `avgProb30` 大概率从当前值下降（虚高被消除），
     需提前告知运营方，避免被误读为「业务下滑」。

### 方案 B：tenure < 90 天的会员强制标 0.5（「证据不足」）

- 思路：不改公式，只在分档前加一道门槛：
  `tenure_days < 90` 的会员，其 `repurchase_prob_*` 一律置为固定值 `0.5`（中性），并建议前端标注「证据不足」。
- **优点**：
  1. **改动面极小**（一处 `CASE` 前置条件 + 前端一处文案）；
  2. **语义诚实** —— 0.5 明确表示「无法判断」，且**不会被前端误判为健康**：
     前端 `ltv.vue` L615 `prob30 < 0.6` → 落入「中风险 · 需关怀」分支 → **派发 P1 触达而非 P0 召回**，
     比当前「1 单新客判健康不动作」更安全；
  3. **不改变老客的既有数值**（`tenure >= 90` 时公式等价）→ 无历史数值漂移（仅短 tenure 会员变动）。
- **代价 / 需决策**：
  1. **90 天阈值需业务拍板**（无业务先例）；
  2. **短 tenure 会员的复购概率信息完全丢失** —— 新客全部退化为 0.5，
     **看板 KPI 的分母被稀释**，运营侧需要「新客单独分组看」的配套视图；
  3. **「0.5」是人工约定值**，需业务确认其含义（中位/中性/不可用）与展示方式；
  4. **只治 tenure 维度，不治样本量维度** —— 例如 `tenure = 200 天但只消费 1 单` 的会员
     仍会落入 `freq = 1.83 < 4` → 0.45 档，样本量不足的问题依然存在。

### A / B 之外的补充动作（两个方案都需要）

| # | 动作 | 说明 |
|---|---|---|
| 1 | **统一 `computeLtv` 与 `batchPredictLtv` 的分档** | §1.3 —— 两条路径当前阈值比较符、低档取值、180d 档位全不同，同会员差 2.8 倍 |
| 2 | **`computeLtv` 补 `total_orders = 0` 分支** | §1.2 —— batch 有，computeLtv 没有 |
| 3 | **`computeLtv` 的 `repurchaseProb180d` 恒 0.55** | §1.3 —— 该值**不携带任何信息**，若前端展示 180d 进度条（L262）则是**纯装饰** |
| 4 | **前端标注「证据不足」态** | `ltv.vue` L615-642 的三分支增加第四态，对应方案 B 的 0.5；否则 0.5 会被误读为「中风险」而非「未知」 |
| 5 | **KPI 分母口径确认** | `GET /intel/ltv/overview` 的 `avgProb30`（`LtvService` L262-265）是否应排除短 tenure 会员 —— 需业务确认看板含义 |
| 6 | **补 `LtvService` 的 `tenant_id` 过滤** | §1.4 备注 —— `LtvService.java:66` 缺租户条件，属独立缺陷，建议单独登记 |

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境新客占比与实际影响面 | **未确认** —— 生产库不可访问；本地 292 个有消费会员最短 tenure 75 天，无「1 单新客」样本（§2.4） |
| `log` / `exp` 系数（方案 A） | **未确认** —— 无业务先例，需运营 / 数据团队标定 |
| 90 天阈值（方案 B） | **未确认** —— 建议值，无业务依据 |
| 「证据不足」的展示约定 | **未确认** —— 0.5 的语义与前端文案需业务确认 |
| `crm_ltv_snapshot` 存量重算范围 | **未确认** —— 本地 3522 行跨多个历史日期；生产规模未确认 |
| 看板 KPI 是否被外部报表 / 第三方消费 | **未确认** —— 未做调用方全仓扫描 |
| `LtvService` 缺 `tenant_id` 过滤的实际影响 | **未确认** —— 未单独立项，影响面需另查 |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`），租户 1 |
| 核实 commit | **`43bbcce`** `fix(ltv): 修复 batchPredictLtv/Churn 表名+量纲+幂等（I152 批 2-3）`；分支 HEAD `b64af85` |
| 源码依据 | `backend/src/main/java/com/meiyetec/service/DataGovernanceService.java` L525-604（`batchPredictLtv`，分档 L566-577、年化 L585-586）；`backend/src/main/java/com/meiyetec/controller/LtvChurnController.java` L73-148（`computeLtv`，分档 L128-130）；`backend/src/main/java/com/meiyetec/service/LtvService.java` L47-165（在线连续算法 L154-165、overview KPI L262-265、缺租户过滤 L66） |
| 前端依据 | `frontend/meiyetec-web/src/views/intel/ltv.vue` L28（KPI）、L147 / L254 / L258 / L262（进度条）、L611-643（风险分级与运营动作派发）、L693（数据来源端点） |
| DB 快照 | `crm_ltv_snapshot` 租户 1：`snapshot_date=2026-11-12` 489 行，`purchase_frequency` min 0.00 / max 41.01 / avg 4.31（`>24` 仅 1 个会员）；`crm_member` 489 行（`create_time` 全部 72–91 天前）；`pos_order` `status=2` 1290 行 |
| 核心查询 | `crm_ltv_snapshot` 各快照日 `purchase_frequency` 分布、tenure 分桶、概率分布、会员数与消费周期统计 |
| 关联 issue | I157、I152（§4A Q1/Q6）、I155、I151、I156 |
| 取证时间 | 2026-11-13 15:30 – 16:05 |
