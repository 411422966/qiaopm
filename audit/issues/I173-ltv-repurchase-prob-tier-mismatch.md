# I173 — 复购概率档位口径不一致：`compute` 两档（0.85/0.30）vs `batch` 三档（0.85/0.70/0.45），同会员实测差 0.15

> **状态**：Open（**待业务拍板** —— 属于业务风控口径，技术侧不得单方面统一）
> **严重度**：P2 —— 影响会员复购概率的展示与下游营销触达，不影响金额类指标
> **发现日期**：2026-11-14（I170 部分修复批次的调查中确立）
> **登记人**：Cursor（I170 部分修复批次）
> **关联**：I170（父 issue：多写入者无互斥）、I172（LTV 快照口径权威性 —— 本 issue 依附于该拍板）、I158（`repurchase_prob_30d` 分档只看频率不看样本量，与本 issue 叠加）

---

## 0. 一句话描述

`crm_ltv_snapshot.repurchase_prob_30d / 90d / 180d` 有**两套互不相同的分档规则**，
由 A、B 两个写入者各写各的 —— 同一个会员**同一天的两行**给出**不同的复购概率**（本地实测差 0.15）。

---

## 1. 缺陷 / 待决点

### 1.1 两套分档规则原文

**A `batch`（`DataGovernanceService.batchPredictLtv`，SQL 内联 CASE）**
位置：`backend/src/main/java/com/meiyetec/service/DataGovernanceService.java:566-577`

```sql
CASE WHEN COALESCE(o.total_orders, 0) = 0 THEN 0
     WHEN o.purchase_frequency >= 12 THEN 0.85      -- 90d=0.95 / 180d=0.99
     WHEN o.purchase_frequency >= 4  THEN 0.70      -- 90d=0.80 / 180d=0.90
     ELSE 0.45 END AS prob_30d                        -- 90d=0.55 / 180d=0.70
```

| 档 | 触发条件（`purchase_frequency`） | `prob_30d` | `prob_90d` | `prob_180d` |
|---|---|---|---|---|
| A-0 | `total_orders = 0` | 0 | 0 | 0 |
| A-高 | `>= 12` | **0.85** | 0.95 | 0.99 |
| A-中 | `>= 4` | **0.70** | 0.80 | 0.90 |
| A-低 | 其余 | **0.45** | 0.55 | 0.70 |

**B `compute-job`（`LtvChurnController.computeLtv`，Java 三元表达式）**
位置：`backend/src/main/java/com/meiyetec/controller/LtvChurnController.java:130-132`

```java
s.setRepurchaseProb30d(BigDecimal.valueOf(freqPerYear > 12 ? 0.85 : 0.30).setScale(2, RoundingMode.HALF_UP));
s.setRepurchaseProb90d(BigDecimal.valueOf(freqPerYear > 4 ? 0.7 : 0.45).setScale(2, RoundingMode.HALF_UP));
s.setRepurchaseProb180d(BigDecimal.valueOf(0.55).setScale(2, RoundingMode.HALF_UP));
```

| 档 | 触发条件（`freqPerYear`） | `prob_30d` | `prob_90d` | `prob_180d` |
|---|---|---|---|---|
| B-高 | `> 12` | **0.85** | 0.70 | 0.55 |
| B-低 | 其余 | **0.30** | 0.45 | 0.55 |

### 1.2 三处结构性差异

| # | 差异 | A | B |
|---|---|---|---|
| 1 | **档位数** | **三档**（高/中/低） | **两档**（高/低）—— **缺中间档** |
| 2 | **边界算子** | `>= 12` / `>= 4` | `> 12` / `> 4` —— `freq` **恰为 12 或 4** 时两侧落档不同 |
| 3 | **0 消费会员** | 有专门分支 → 三个概率全为 **0** | 无此分支（B 的输入本就只有有消费会员，见 I172） |
| 4 | **90d / 180d 语义** | 随 `purchase_frequency` **分三档递增**（0.55→0.80→0.95） | `prob_90d` 递进但 `prob_180d` **恒为 0.55** —— **长周期概率不随档位变化** |

> ⚠️ 差异 4 尤其可疑：`prob_180d` 在 B 中是常量，意味着 B 侧「180 天复购概率」**不含任何会员分层信息**，
> 与 A 侧（0.70 / 0.90 / 0.99）语义完全相反。

### 1.3 实测差异（本地，租户 1，2026-11-14 当日快照）

`SELECT member_id, operator_name, repurchase_prob_30d, repurchase_prob_90d, repurchase_prob_180d, purchase_frequency FROM crm_ltv_snapshot WHERE tenant_id=1 AND snapshot_date=CURDATE() AND member_id IN (4,6) ORDER BY member_id, operator_name;`

| `member_id` | `operator_name` | `purchase_frequency` | `prob_30d` | `prob_90d` | `prob_180d` | `predicted_ltv_365d` |
|---|---|---|---|---|---|---|
| 4 | `batch` | 3.72 | **0.45** | 0.55 | 0.70 | 14,612.16 |
| 4 | `compute-job` | 3.72 | **0.30** | 0.45 | 0.55 | 14,629.80 |
| 6 | `batch` | 3.76 | **0.45** | 0.55 | 0.70 | 4,188.64 |
| 6 | `compute-job` | 3.76 | **0.30** | 0.45 | 0.55 | 4,191.86 |

- `prob_30d` 差 **0.15**（0.45 vs 0.30）✅ 与「实测差 0.15」一致
- `prob_90d` 差 **0.10**（0.55 vs 0.45）
- `prob_180d` 差 **0.15**（0.70 vs 0.55）
- `predicted_ltv_365d` 差约 **0.12%**（14,612.16 vs 14,629.80）—— 源于 A 先 `ROUND(freq,2)` 再乘、B 用未取整 double 再乘（**这是精度问题，非口径问题**，与本 issue 主线分开）

---

## 2. 影响面

| 项 | 影响 |
|---|---|
| `GET /ltv-churn/ltv/member/{memberId}` | `orderByDesc(id) LIMIT 1` → 返回哪条取决于写入顺序，**同一会员的复购概率在 0.30 / 0.45 / 0.55 / 0.70 / 0.80 / 0.85 / 0.90 / 0.95 / 0.99 之间跳变** |
| `GET /ltv-churn/ltv/top` | 同一会员两条记录并存，Top 列表重复占位 |
| 前端会员详情 / 营销触达 | 若按 `repurchase_prob_30d >= 0.7` 之类阈值圈选人群，**A 侧命中、B 侧不命中**（`freq` 在 4~12 区间时）→ 触达名单随「谁最后跑」漂移 |
| `crm_ltv_snapshot` 存量数据 | 同一 `(member_id, snapshot_date)` 的两行概率列不可直接聚合（无 `SUM`/`AVG` 语义） |
| I158（置信度倒挂） | 叠加：分档只看 `purchase_frequency`，**样本量 `total_orders` 极小时也会落到 0.45/0.30 档** —— 两 issue 需一并考虑 |

---

## 3. 待业务回答的问题（**必答项**）

1. **复购概率用几档？** 两档（B 现制）/ 三档（A 现制）/ 改为连续概率模型？
2. **中间档（`freq` 落在 4~12）应该给多少？** A 给 0.70，B 直接掉到 0.30 —— **同一会员在两套口径下相差 0.4**，是本 issue 中最大的单点分歧。
3. **`prob_180d` 是否应该恒定？** B 的常量 0.55 与 A 的 0.70/0.90/0.99 三档孰对？
4. **边界用 `>=` 还是 `>`？**（`freq` 恰为 4 / 12 时落档不同）
5. **0 消费会员的概率是否应为 0？**（A 是；B 因输入口径不覆盖该人群而无此定义）
6. **拍板后由谁落地？** 建议收敛到**一个**写入者实现，另一方改为调用同一段逻辑（避免再分叉）。

---

## 4. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境两套规则的分布 | **未确认** —— 本次仅在本地 Docker 验证，生产库不可访问 |
| 业务方历史上对外承诺的复购概率数值 | **未确认** —— 需业务方确认 |
| `prob_90d` / `prob_180d` 是否被营销规则引擎消费 | **未确认** —— 本 issue 未审阅营销侧规则配置 |
| B 的 `freqPerYear` 与 A 的 `purchase_frequency` 是否恒等 | **未确认** —— A 先 `ROUND(...,2)`，B 为未取整 double，边界会员可能落档不同 |

---

## 5. 建议处理顺序

1. 业务回答 §3 的 6 个问题 → 产出「复购概率权威分档表」；
2. 与 I172 的口径拍板**合并落地**（两者都决定「哪个写入者是权威」，一次改到位）；
3. 统一后清理存量概率列（与 I174 的历史重复行清理一并做）；
4. 补回归测试：同一会员在两条路径下产出的概率必须相等。
