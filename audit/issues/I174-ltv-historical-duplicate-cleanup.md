# I174 — `crm_ltv_snapshot` 历史 5 组重复行待清理（2026-09-02 / 10-30 / 10-31 / 11-02），并放大 `GET /intel/ltv/trend` 的历史趋势

> **状态**：Open（**清理动作待授权 / 待业务确认** —— 本批明确不清理）
> **严重度**：P2 —— 影响历史趋势曲线的真实性，不影响当日看板（看板已由 I170 修复 1 加 `snapshot_date` 过滤）
> **发现日期**：2026-11-14（I170 部分修复批次的调查中确立）
> **登记人**：Cursor（I170 部分修复批次）
> **关联**：I170（父 issue：多写入者无互斥 —— 写入者 C `online` 是重复行的制造者）、I172（口径权威性，决定「留哪一行」的标准）、I175（唯一约束 DDL 的前置条件 —— 必须先清存量才能加约束）

---

## 0. 一句话描述

`crm_ltv_snapshot` 的**历史日期**里已积累 **5 组**同会员重复行（分布在 4 个快照日），
全部来自写入者 C（`operator_name='online'`，即修复前的 `LtvService.computeForMember`）——
而 `GET /intel/ltv/trend` 是**按月 `SUM`** 口径，重复行会**直接放大历史趋势曲线**。

---

## 1. 存量重复组明细（本地实测，租户 1，2026-11-14）

```sql
SELECT s.snapshot_date, s.member_id, COUNT(*) rows_all,
       GROUP_CONCAT(s.operator_name ORDER BY s.id) ops,
       GROUP_CONCAT(s.id ORDER BY s.id) ids
FROM crm_ltv_snapshot s
JOIN (SELECT member_id, snapshot_date FROM crm_ltv_snapshot
      WHERE tenant_id = 1 GROUP BY member_id, snapshot_date HAVING COUNT(*) > 1) d
  ON d.member_id = s.member_id AND d.snapshot_date = s.snapshot_date
WHERE s.tenant_id = 1 AND s.snapshot_date < CURDATE()
GROUP BY s.snapshot_date, s.member_id
ORDER BY s.snapshot_date, s.member_id;
```

| `snapshot_date` | `member_id` | 行数 | `operator_name` 序列 | `id` 序列 |
|---|---|---|---|---|
| 2026-09-02 | 1224 | **5** | `seed` × 1 + `online` × 4 | 32, 4351, 4352, 4353, 4354 |
| 2026-10-30 | 1 | **3** | `online` × 3 | 4358, 4359, 4360 |
| 2026-10-30 | 1548 | **3** | `online` × 3 | 4356, 4357, 4361 |
| 2026-10-31 | 1548 | **3** | `online` × 3 | 4362, 4363, 4364 |
| 2026-11-02 | 1548 | **2** | `online` × 2 | 4366, 4367 |
| **合计** | | **16 行 → 保留 5 行，多出 11 行** | | |

> ✅ **来源结论**：5 组中 **4 组为纯 `online`**，剩余 1 组（2026-09-02 / member 1224）是 `seed` + `online` 混合 ——
> **没有任何一组来自 A/B（`batch` / `compute-job`）组合**。
> 换言之：**写入者 C 是历史重复行的唯一来源**，与 I170 修复 2（单会员幂等）的判断一致。

### 1.1 当日重复组（对照）

| 口径 | 组数 |
|---|---|
| 当日（`snapshot_date = CURDATE()`）重复组 | **231**（修复 2 落地后；修复前为 232） |
| 历史（`snapshot_date < CURDATE()`）重复组 | **5** |
| **全表重复组合计** | **236** |

---

## 2. 对历史趋势的放大（核心影响）

`GET /intel/ltv/trend`（`backend/src/main/java/com/meiyetec/service/LtvService.java:297-310`）口径：

```sql
SELECT DATE_FORMAT(snapshot_date, '%Y-%m') AS ym,
       ROUND(SUM(historical_ltv), 2)        AS hist,
       ROUND(SUM(predicted_ltv_365d), 2)    AS pred
FROM crm_ltv_snapshot
WHERE tenant_id = ? AND snapshot_date >= DATE_SUB(CURDATE(), INTERVAL 12 MONTH)
GROUP BY ym ORDER BY ym
```

> ⚠️ **只 `GROUP BY ym`，没有按 `member_id` 去重** → 同月内的重复行被**逐条累加**。

**多计金额量化**（保留每组最小 `id`，其余视为多余行）：

```sql
SELECT DATE_FORMAT(s.snapshot_date,'%Y-%m') ym, COUNT(*) extra_rows,
       ROUND(SUM(s.predicted_ltv_365d),2) extra_ltv365
FROM crm_ltv_snapshot s
JOIN (SELECT member_id, snapshot_date, MIN(id) keep FROM crm_ltv_snapshot
      WHERE tenant_id=1 GROUP BY member_id, snapshot_date HAVING COUNT(*)>1) d
  ON d.member_id=s.member_id AND d.snapshot_date=s.snapshot_date AND s.id <> d.keep
WHERE s.snapshot_date < CURDATE() GROUP BY ym;
```

| 月份 (`ym`) | 多余行数 | 多计 `predicted_ltv_365d` | 当月实际 `SUM` | 多计占比 |
|---|---|---|---|---|
| 2026-09 | 4 | **¥3,903,377.00** | ¥58,876,767.03 | **≈ 6.6%** |
| 2026-10 | 6 | **¥9,268.40** | ¥13,902.60 | **≈ 66.7%** |
| 2026-11 | 1 | **¥0.00** | ¥172,731,833.28 | 0%（member 1548 的 LTV 为 0） |
| **合计** | **11** | **¥3,912,645.40** | | |

> 🔴 **2026-10 尤其严重**：该月全表 `SUM` 只有 ¥13,902.60，其中 **¥9,268.40（66.7%）是重复行贡献** ——
> 趋势曲线上 10 月的「塌陷」有三分之二是数据重复造成的，**不是真实的业务下滑**。

### 2.1 对其它读取方的影响

| 读取方 | 是否受历史重复影响 | 说明 |
|---|---|---|
| `GET /intel/ltv/trend` | ✅ **受影响** | 按月 `SUM`，见上表 |
| `GET /ltv-churn/dashboard` | ❌ 不受影响 | 已由 I170 修复 1 加 `snapshot_date = MAX(...)` 过滤，只看最新日 |
| `GET /ltv-churn/ltv/member/{id}` | ⚠️ 间接 | `orderByDesc(id) LIMIT 1` → 取到最新那条，历史重复不影响取值，但会掩盖「该会员当日有多行」的事实 |
| `GET /ltv-churn/churn/compute` | ❌ 不受影响 | 输入按 `orderByDesc(id)` + `putIfAbsent` 取每会员最后一条，重复行被去重 |
| `GET /intel/ltv/trend` 的前端消费方 | ✅ **受影响** | 前端按 `pred` 画折线，重复直接抬高折线 |

---

## 3. 待业务 / 待授权确认的问题（**必答项**）

1. **「留哪一行」的判定标准是什么？** 备选：① 留 `id` 最大（最新写入）—— 与「取最新一条」的既有语义一致；② 留 `id` 最小；③ 留 `operator_name='batch'` 的（若存在）—— 与 I172 的权威口径一致。
   **注**：5 组历史重复中**没有 `batch` / `compute-job` 行**，全为 `online` / `seed`，因此「留 batch」在这 5 组上**不可用**，实际只能在 ① / ② / 「留 seed」之间选。
2. **member 1224 的 2026-09-02 组如何处理？** 该组含 1 条 `seed` + 4 条 `online` —— `seed` 行是历史示例数据，保留它是否合理？
3. **清理动作是否授权执行？**（本批明确**不清理**）
4. **是否需要保留审计痕迹？** 建议清理前先 `INSERT ... SELECT` 备份到 `crm_ltv_snapshot_cleanup_log`（若新建表），避免不可逆。

---

## 4. 建议的清理 SQL（**待授权后才执行**）

> ⚠️ 以下为**建议稿**，本批**未执行**。执行前须确认 §3 的判定标准，且**先备份**。

```sql
-- 步骤 1：备份将被删除的行（建议先建归档表）
-- CREATE TABLE crm_ltv_snapshot_archive LIKE crm_ltv_snapshot;
INSERT INTO crm_ltv_snapshot_archive
SELECT s.* FROM crm_ltv_snapshot s
JOIN (SELECT member_id, snapshot_date, MIN(id) keep FROM crm_ltv_snapshot
      WHERE tenant_id = 1 GROUP BY member_id, snapshot_date HAVING COUNT(*) > 1) d
  ON d.member_id = s.member_id AND d.snapshot_date = s.snapshot_date AND s.id <> d.keep
WHERE s.tenant_id = 1 AND s.snapshot_date < CURDATE();

-- 步骤 2：删除多余行（保留 MIN(id)）
DELETE s FROM crm_ltv_snapshot s
JOIN (SELECT member_id, snapshot_date, MIN(id) keep FROM crm_ltv_snapshot
      WHERE tenant_id = 1 GROUP BY member_id, snapshot_date HAVING COUNT(*) > 1) d
  ON d.member_id = s.member_id AND d.snapshot_date = s.snapshot_date AND s.id <> d.keep
WHERE s.tenant_id = 1 AND s.snapshot_date < CURDATE();
```

**预期效果**：历史重复组 5 → 0；全表重复组 236 → 231；`trend` 的 2026-09 减少 ¥3,903,377.00、2026-10 减少 ¥9,268.40。

> ⚠️ **必须与 I175 衔接**：唯一约束 DDL（`UNIQUE (tenant_id, member_id, snapshot_date)`）**要求先清完全部重复**，
> 否则 `ALTER TABLE ... ADD UNIQUE` 会直接失败。**清理顺序在 I175 中固化。**

---

## 5. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境历史重复组数量与金额 | **未确认** —— 本次仅在本地 Docker 验证，生产库不可访问 |
| 生产环境的 `operator_name` 分布是否与本地一致（本地 `online` 仅 17 行） | **未确认** |
| 业务方是否已经基于历史趋势曲线做过对外汇报 | **未确认** —— 需业务方确认（若已汇报，清理后曲线会下移，需提前告知） |
| `crm_ltv_snapshot` 是否有归档表机制 | **未确认** —— 本 issue 未审阅备份/归档设施 |

---

## 6. 关联证据

| 项 | 值 |
|---|---|
| Evidence | `audit/evidence/F166-ltv-snapshot-idempotency/raw/05-db-snapshot-after-fix.txt`、`raw/06-verify-i170-partial.txt` |
| 取证时间 | 2026-11-14 23:24 – 23:30 |
| 父 issue | I170（§2.4 历史重复来源拆解、§7 修复 2） |
