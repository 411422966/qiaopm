# I172 — LTV 快照口径权威性待业务拍板：`compute` 覆盖 292 会员 vs `batch` 覆盖 489 会员，谁是权威？

> **状态**：Open（**待业务决策** —— 技术侧不得单方面拍板）
> **严重度**：P1 —— 决定 `crm_ltv_snapshot` 的权威口径，决定 A3 修法（是否照搬 I156）、决定看板数字含义
> **发现日期**：2026-11-14（I170 部分修复批次的调查中确立）
> **登记人**：Cursor（I170 部分修复批次）
> **关联**：I170（父 issue：多写入者无互斥）、I156（CHURN 侧已按 A3 修复）、I171（CHURN 侧配套项）、I173（复购概率档位）、I175（唯一约束）

---

## 0. 一句话描述

`crm_ltv_snapshot` 有两个规则口径写入者（A `batch` / B `compute-job`），**覆盖的会员集合不同**：

| 写入者 | 输入 | 覆盖会员集合 | 本地实测（2026-11-14） |
|---|---|---|---|
| A `batch`（`DataGovernanceService.batchPredictLtv`） | `crm_member` LEFT JOIN `pos_order` | **全部会员**（含 0 消费会员） | **489** |
| B `compute-job`（`LtvChurnController.computeLtv`） | `pos_order`（`status=2`）GROUP BY member | **仅有消费会员** | **292** |

> 📌 **方向与 CHURN 侧相反**：CHURN 侧 `computeChurn` 的输入是 `crm_ltv_snapshot` 全量历史行去重 → 覆盖 **551** 个会员（**超集**），
> 所以 I156 的 A3「先删当日全表再重算」在 CHURN 侧**不丢数据**；
> LTV 侧 A3 会让 **489 − 292 = 197 个无消费会员**的当日快照**直接消失**。

---

## 1. 缺陷 / 待决点

### 1.1 两个口径的算法差异

| 维度 | A `batch`（SQL 版） | B `compute-job`（Java 版） |
|---|---|---|
| LTV 365d | `ROUND(avg_order_value × purchase_frequency, 2)`，其中 `purchase_frequency = ROUND(COUNT(*) × 365 / GREATEST(tenure, 30), 2)` | `aov(2位) × freqPerYear(未取整 double)`，其中 `freqPerYear = orders × 365.0 / max(tenureDays, 30)` |
| 复购概率 | **三档**：`prob_30d` = 0.85 / 0.70 / 0.45（阈值 `freq >= 12` / `>= 4`），`prob_90d` = 0.95 / 0.80 / 0.55，`prob_180d` = 0.99 / 0.90 / 0.70 | **两档**：`prob_30d` = 0.85 / 0.30（阈值 `freq > 12`），`prob_90d` = 0.70 / 0.45，`prob_180d` = 0.55（固定） |
| 0 消费会员 | **会写入**一行（各预测值 = 0，`prob_*` = 0） | **不写入**（无消费会员不出现在输入里） |
| `model_version` | 取 `ltv_model_registry` 的**已部署模型版本** | 硬编码 `'v1'` |
| 精度差异 | 频率保留 2 位小数后再乘 | 频率为未取整 double 再乘 → 同会员两侧 365d 相差约 0.1%（实测 member 4：14612.16 vs 14629.80） |

### 1.2 为什么这必须由业务拍板

| 若判定 A 权威 | 若判定 B 权威 |
|---|---|
| B 的写入应被限制在「覆盖 A 的全集」→ A3 需改造 B 让它也覆盖 0 消费会员 | A 的写入应被限制在「仅有消费会员」→ A3 需改造 A 的 LEFT JOIN |
| 看板 LTV 池包含 0 消费会员的 0 值行（对求和无影响，但影响 `membersWithLtv` 计数） | 看板 LTV 池只含 197 个有消费会员的行 |
| `batchPredictLtv` 的「先删后插」语义可作为全局标准，B 对齐 A | `computeLtv` 的「只算有消费的人」语义更贴近业务（无消费会员谈 LTV 无意义），A 需对齐 B |

> 🔒 **技术侧不代替业务做这个决定** —— 它决定「LTV 池里到底算不算没有消费的会员」这一业务定义。

---

## 2. 证据

| 项 | 值 |
|---|---|
| 覆盖会员数 | A = 489（= `crm_member` 租户内 `deleted=0` 总数）；B = 292 |
| 查询 | `SELECT operator_name, model_type, COUNT(*) FROM crm_ltv_snapshot WHERE tenant_id=1 AND snapshot_date=CURDATE() GROUP BY operator_name, model_type;` |
| 差异样本 | member 4 / member 6：`repurchase_prob_30d` A=0.45 vs B=0.30（差 0.15）；`predicted_ltv_365d` A=14612.16 vs B=14629.80 |
| Evidence | `audit/evidence/F166-ltv-snapshot-idempotency/raw/05-db-snapshot-after-fix.txt`、`audit/issues/I170-ltv-snapshot-duplicate-write.md` §2.1 / §8 |
| 取证时间 | 2026-11-14 23:05 – 23:15 |

---

## 3. 影响面

| 项 | 影响 |
|---|---|
| I170 A3 修法 | **阻塞** —— A3（`computeLtv` 加 `DELETE ... snapshot_date=CURDATE()`）在本 issue 拍板前**不可实施** |
| `GET /ltv-churn/dashboard` | 已加 `snapshot_date` 过滤后仍有 **231 组当日重复**（同一会员 2 行）→ `totalLtv365d` 仍虚高约 0.5%（见 §4 待办） |
| `GET /ltv-churn/ltv/top` | 同一会员可同时占据两条 Top 记录 |
| `GET /intel/ltv/trend` | 按月求和，重复行放大历史趋势（见 I174 量化） |
| `crm_ltv_snapshot` 唯一约束 | 必须先确定「同一 `(member_id, snapshot_date)` 允许几行」才能加（见 I175） |
| 训练链路 `ltv_training_feature` | 训练特征从 `crm_ltv_snapshot` 取数 → 口径未定时训练样本本身就不确定 |

---

## 4. 待业务回答的问题（**必答项**）

1. **LTV 池是否包含「0 消费会员」？**
   - 是 → 以 A（`batch`）为准，B 需补齐覆盖；
   - 否 → 以 B（`compute`）为准，A 需改为 INNER JOIN 语义。
2. **看板 `membersWithLtv` 的分母应该是 489 还是 292？**
3. **`model_version` 是否必须取已部署模型版本？**（B 当前硬编码 `'v1'`，即使拍板 B 权威也需要修正版本号来源）
4. **同会员同日多行在口径统一前如何展示？**（建议：拍板前先按「取最新一条」展示，拍板后再清理存量）

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境 A / B 各自的覆盖会员数 | **未确认** —— 本地实测，生产库不可访问 |
| 训练链路 `LtvTrainingService` / `ltv_training_feature` 对 `model_type='RULE'` vs `'BG'` 的消费差异 | **未确认** —— 本 issue 未审阅训练链路 |
| 业务方历史上是否已按某个口径出过报表 / 承诺过数字 | **未确认** —— 需业务方确认 |

---

## 6. 建议处理顺序

1. 业务回答 §4 的 4 个问题 → 产出「权威口径」结论；
2. 按结论改造写入者（一方收敛到另一方的覆盖范围 + 对齐概率档位，见 I173）；
3. 再实施 A3（`computeLtv` 先删后插）；
4. 清理存量重复（I174）→ 加唯一约束（I175）。
