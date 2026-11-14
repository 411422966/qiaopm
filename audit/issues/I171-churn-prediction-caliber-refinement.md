# I171 — `crm_churn_prediction` 口径精化配套项：写入精度、阈值边界、`tenure_days` 语义、看板快照过滤、枚举漂移、唯一约束、第三写入者幂等

> **状态**：Open
> **严重度**：P2 —— 不产生重复数据（重复已由 I156 修复），但存在口径不一致与统计失真
> **发现日期**：2026-11-14（I156 A3 修复批次 —— §5.5 配套清单从 I156 拆出独立立项）
> **登记人**：Cursor（I156 A3 修复批次）
> **关联**：I156（双写互斥，**已 Fixed**，本 issue 是其 §5.5 未完成配套项的承接）、I170（LTV 侧同源双写，**待与本 issue 同批处理**）、I155（预测端不消费训练模型）、I157（同批修复的年化分母）
> **修复决策**：**待拍板** —— 建议与 I170 同批实施

---

## 0. 一句话描述

I156 已于 2026-11-14 按 **A3 方案**修复 `computeChurn` 与 `batchPredictChurn` 的双写互斥
（`computeChurn` 加先删后插 + `model_code` 改为 `COMPUTE-CHURN-V1`），
**重复数据与静默覆盖两个失败模式已消除**。

但 I156 §5.5 同时列出的 **7 项配套清单本轮均未做** ——
它们不影响「行数正确」，但影响「行内容正确」「看板统计正确」「数据层强约束」，
其中有 3 项（#1 精度、#2 阈值、#3 `tenure_days` 语义）会让**同一会员在不同写入者下产出不可比的记录**，
1 项（#4 看板过滤）会让**历史快照混入当前统计**，1 项（#5 枚举漂移）会让**风险分布恒缺三档**。

本 issue 承接这 7 项，作为独立可跟踪项。

---

## 1. 配套清单（逐项定位与影响）

### 1.1 `churn_score` 精度不一致（4 位 vs 2 位）

| 项 | 内容 |
|---|---|
| 写入者 A | `DataGovernanceService.batchPredictChurn`（L616）→ `churn_score` 由 SQL 计算，列类型 `decimal(5,4)` → **4 位小数** |
| 写入者 B | `LtvChurnController.computeChurn`（L205）`p.setChurnScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP))` → **2 位小数** |
| 写入者 C | `ChurnPredictionService.predictForMember`（L209）`BigDecimal.valueOf(raw).setScale(4, RoundingMode.HALF_UP)` → **4 位小数** |
| 表 | `crm_churn_prediction.churn_score decimal(5,4)`（`backend/src/main/resources/db/sql/migrate_r3_0_h_p1_6_churn_prediction.sql` L48） |
| 影响 | 同一会员在 B 路径下分数被**截断到 2 位**（例：`0.4444` → `0.4400`）。A3 下谁后跑谁覆盖 → **落库值随点击顺序变化**；`avgChurnScore` 均值也随覆盖方变化 |
| 建议 | B 路径改 `setScale(4, RoundingMode.HALF_UP)`，与 A/C 对齐 |

### 1.2 风险阈值边界不一致（`>` vs `>=`）

| 项 | 内容 |
|---|---|
| 写入者 A | `DataGovernanceService.batchPredictChurn`（L653-661）`WHEN ... >= 0.66 THEN 'HIGH'` / `>= 0.33 THEN 'MED'` |
| 写入者 B | `LtvChurnController.computeChurn`（L191-192）`if (score > 0.66) riskLevel = "HIGH"; else if (score > 0.33) riskLevel = "MED";` → **严格大于** |
| 写入者 C | `ChurnPredictionService.riskLevel`（L136）走 `ai_churn_model.threshold_high` / `threshold_med`（默认 0.70 / 0.40）→ **阈值来源与常量不同** |
| 影响 | 分数**恰好等于**阈值边界的会员（`0.66` / `0.33`）在 A 落 HIGH/MED、在 B 落 MED/LOW → **同一会员风险等级不可比**；且 C 的默认阈值（0.70/0.40）与 A/B（0.66/0.33）**本就不同** |
| 建议 | 统一为 `>=`；阈值**集中到一处**（`ai_churn_model.threshold_high/threshold_med` 或代码常量），避免三处各写一套 |

### 1.3 `tenure_days` 疑似复制粘贴错误

| 项 | 内容 |
|---|---|
| 位置 | `LtvChurnController.computeChurn`（L203-204） |
| 代码 | `p.setLastPurchaseDays(lastPurchaseDays);` 紧接 `p.setTenureDays(s.getLastPurchaseDays() == null ? 0 : s.getLastPurchaseDays());` |
| 局部变量来源 | L178 `int lastPurchaseDays = s.getLastPurchaseDays() == null ? 0 : s.getLastPurchaseDays();` |
| 问题 | `tenure_days` 与 `last_purchase_days` **写入同源同值** → `tenure_days` 列**恒等于** `last_purchase_days`，失去独立语义（tenure = 会员生命周期，last_purchase_days = 距上次消费天数） |
| 对照 | 写入者 A `batchPredictChurn` 的 `tenure_days` 来自 `DATEDIFF(CURDATE(), MIN(pay_time))`（**真实生命周期**），与 `last_purchase_days`（`MAX(pay_time)`）**是两个不同的值**；写入者 C `predictForMember` L228 `p.setTenureDays(tenureDays)` 取的是**真实 tenure** |
| 影响 | B 路径的 `tenure_days` **无业务含义**；任何消费该列的下游（看板、训练特征、告警）若用 A 路径口径的值会得到**完全不同**的分布 |
| 修复前提 | `CrmLtvSnapshot` 实体**无 `tenureDays` 字段**（只有 `lastPurchaseDays` / `totalOrders` / `purchaseFrequency` 等）→ **需先确认 LTV 快照侧能否提供真实 tenure**，否则只能在 B 路径按 `crm_member.join_date` 重算 |
| 状态 | **具体修复位置待确认** —— 需先决定 tenure 的权威来源（`crm_member.join_date` vs `crm_ltv_snapshot` 新增列），本轮**仅登记** |

### 1.4 `GET /ltv-churn/dashboard` 未按 `snapshot_date` 过滤

| 项 | 内容 |
|---|---|
| 位置 | `LtvChurnController.dashboard()`（L229-233）`ltvMapper.selectList(eq(tenantId, t))` / `churnMapper.selectList(eq(tenantId, t))` |
| 问题 | **全表统计**，不加 `snapshot_date` 条件 |
| 影响 | ① LTV 侧 `totalLtv` / `avgLtv` / `membersWithLtv` 把**全部 21 个历史日期**的快照一起累加/求平均（本地 4595 行 / 21 日期）→ 与 I162 已对齐的「最近一批」语义**不一致**；② Churn 侧 `highRiskCount` / `medRiskCount` / `lowRiskCount` / `highMedRatio` 同样累加历史 → **I156 修复前的历史重复行会永久污染看板**（I156 §2.4 实测 `highRiskCount` 由 393 变 786）；③ `highMedRatio` 的分母是 `churnAll.size()`（全表行数），被历史行数主导 |
| 建议 | 两处 `selectList` 均加 `.eq(snapshot_date, LocalDate.now())`（或 `MAX(snapshot_date)`），与 `ChurnPredictionService.overview()`（L462-463，**已按 `MAX(snapshot_date)` 过滤**）口径对齐 |
| 备注 | **前端已验证通过的 `/dashboard` 首页不受影响** —— 本项针对的是 `/ltv-churn/dashboard`（P2Suite 消费），与首页 `/dashboard` 是不同端点 |

### 1.5 `/intel/churn/overview` 枚举漂移

| 项 | 内容 |
|---|---|
| 位置 | `ChurnPredictionService.overview()`（L456-467） |
| 代码 | `for (String level : new String[]{"HIGH", "MEDIUM", "LOW", "SAFE"})` → `data.put(level.toLowerCase() + "Count", ...)` → 输出 `highCount` / `mediumCount` / `lowCount` / `safeCount` |
| 问题 | 三个写入者写的 `risk_level` 分别是：A = `HIGH/MED/LOW`、B = `HIGH/MED/LOW`、C = `HIGH/MEDIUM/LOW/SAFE`。**统计端点枚举与写入端枚举不一致** |
| 影响 | `mediumCount` / `lowCount` / `safeCount` **恒为 0**（实测 mediumCount=0, lowCount=0, safeCount=0）→ **HIGH 之外的风险分布在任何写入路径下都看不到**；前端若展示风险分布图，只会出现 HIGH 一档 |
| 注意 | 该端点**已按 `MAX(snapshot_date)` 过滤**（L462-463），统计范围本身是对的，**问题只在枚举值** |
| 建议 | 二选一：① 把 A/B 的 `MED` 统一改为 `MEDIUM`、`LOW` 保持（影响存量数据需迁移）；② 把统计端点的枚举改为 `HIGH/MED/LOW`（改动面小，但与 C 的 `SAFE` 档仍不兼容）→ 需业务拍板对外契约 |

### 1.6 `crm_churn_prediction` 缺唯一约束（DDL）

| 项 | 内容 |
|---|---|
| 现状 DDL | `backend/src/main/resources/db/sql/migrate_r3_0_h_p1_6_churn_prediction.sql` L32-59 —— 仅有 `INDEX idx_tenant_score` / `idx_tenant_risk` / `INDEX idx_member`，**无 `UNIQUE (tenant_id, member_id, snapshot_date)`** |
| 收益 | 让「同一会员同一天的预测重复」在**数据库层直接失败**，不依赖应用层先删后插的正确性 |
| 前置 | ① 必须先清理存量重复行；② **需生产库执行 DDL**（本轮无生产库访问权限）→ **生产存量是否已有重复行 UNVERIFIED** |
| 关联 | 写入者 C `ChurnPredictionService.predictForMember`（L238 `predMapper.insert(p)`）**仍无先删后插** → 加约束后 C 路径会直接抛 `DuplicateKeyException`，**必须先给 C 补幂等**（见 #7），否则启用约束即打断现有功能 |
| 建议 | **必须与 #7 同批实施**，顺序：先给 C 补幂等 → 再清存量 → 最后加约束 |

### 1.7 第三个写入者 `ChurnPredictionService.predictForMember` 无幂等

| 项 | 内容 |
|---|---|
| 位置 | `ChurnPredictionService.predictForMember`（L162，`GET /intel/churn/predict/{memberId}`）；批量入口 L313 `POST /intel/churn/batch/predict`（L333 循环调用 `predictForMember`） |
| 写入 | L238 `predMapper.insert(p)` —— **无 DELETE、无 upsert** |
| 当前影响 | **暂不产生数据** —— `ai_churn_model` 表无 `is_active=1 AND enabled=1` 的记录，调用直接短路返回 `NOT_FOUND`（I156 §5 实测：`GET /intel/churn/predict/1` → `{"code":404,"message":"资源不存在: 未配置激活的流失模型"}`） |
| 一旦激活后 | 会用**第四套枚举**（`HIGH/MEDIUM/LOW/SAFE`，见 `riskLevel` L136 + `ai_churn_model` 默认阈值 0.70/0.40）与 4 位小数的 `churn_score` 写入同一张表 → 与 A/B **互不可比**；且**直接破坏 #6 的唯一约束**（同一会员同日可写多行） |
| 附带副作用 | L241-242 `upsertAlert(...)` 在 `HIGH` / `MEDIUM` 时自动开单 → 激活模型后会**批量产生告警工单** |
| 建议 | 给 C 也加先删后插（`tenant_id + member_id + snapshot_date` 精确删除该会员当日行），并把其枚举与阈值来源与 A/B 统一 |

---

## 2. 优先级与处理批次建议

| 项 | 类型 | 是否需 DDL / 生产库 | 建议批次 |
|---|---|---|---|
| #1 精度 | 应用代码 | 否 | 与 I170 同批 |
| #2 阈值 | 应用代码 | 否 | 与 I170 同批 |
| #3 `tenure_days` | 应用代码（**修复来源待定**） | 否 | 与 I170 同批（需先定 tenure 来源） |
| #4 看板过滤 | 应用代码 | 否 | 与 I170 同批 |
| #5 枚举统一 | 应用代码（**对外契约需业务拍板**） | 否 | 与 I170 同批 |
| #6 唯一约束 | **DDL** | **是** | **独立批次**（需生产库 + 先完成 #7） |
| #7 第三写入者幂等 | 应用代码 | 否 | **必须早于 #6** |

**推荐顺序**：先做 #7（解除 #6 的前置阻塞）→ 再做 #1/#2/#4（纯应用层，低风险）→
#3（需先定 tenure 来源）/ #5（需业务拍板枚举契约）→
最后做 #6（DDL，需生产库）。

---

## 3. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 生产环境 `crm_churn_prediction` 是否已有存量重复行 | **未确认** —— 本次仅在本地 Docker 验证；生产库不可访问 |
| `tenure_days` 的权威来源应取 `crm_member.join_date` 还是 LTV 快照 | **未确认** —— 需业务/工程拍板；`CrmLtvSnapshot` 实体当前无该字段 |
| 对外风险等级枚举应统一为 `HIGH/MED/LOW` 还是 `HIGH/MEDIUM/LOW/SAFE` | **未确认** —— 涉及前端契约（`P2Suite.vue` 有 `modelCode` 列、`intel/ltv.vue` 等展示风险等级），需业务拍板 |
| `ai_churn_model.threshold_high` / `threshold_med`（默认 0.70 / 0.40）是否应成为唯一阈值来源 | **未确认** —— 需评估训练链路的模型激活流程（I155：预测端不消费训练模型） |
| `ChurnPredictionService.overview()` 的前端消费方对 `mediumCount` / `lowCount` / `safeCount` 的依赖程度 | **未确认** —— 未做前端全仓扫描 |

---

## 4. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ 本地 Docker `meiyetec-mysql`，租户 1 |
| 应用 | 本地 `meiyetec-api` @ `localhost:8080`（镜像 `meiyetec/api:dev`，已重建并 healthy） |
| commit hash | `8d59bea` `fix(ltv): computeChurn 先删后插（I156 A3）+ 年化分母下限 30 天（I157）` |
| DB 快照 | `crm_churn_prediction` 总 1527 行 / 今日 489 行 / dup 组 0；`crm_ltv_snapshot` 总 4595 行 / 21 个日期 / 今日 781 行 |
| Evidence | `audit/evidence/F165-compute-churn-mutex/raw/02-after-i156-i157.txt`、`raw/03-cleanup.txt` |
| 源码依据 | `LtvChurnController.java` L178/L191-192/L203-205/L229-246；`DataGovernanceService.java` L616/L653-661；`ChurnPredictionService.java` L136/L162/L209/L228/L238/L313/L333/L456-467；`migrate_r3_0_h_p1_6_churn_prediction.sql` L32-59 |
| 关联 issue | I156、I170、I155、I157 |
| 取证时间 | 2026-11-14 18:00 – 18:10 |
