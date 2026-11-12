# I152 — `crm_order` / `crm_order_item` 引用外溢：13 个文件 21 处 SQL 指向不存在的表

> **状态**：Open
> **严重度**：P1（部分）/ P2（大部分）—— 部分入口显式 500，部分被 catch 静默吞掉造成「假成功」
> **发现日期**：2026-11-11（I142 批 1 修复过程中顺带全仓扫描发现）
> **登记人**：Cursor（I142 批 1 — LTV 训练 ETL 8 处表名/列名修复）
> **决策**：用户 2026-11-11 拍板 —— **本批只做 A1-A8（`LtvTrainingService` 内 8 处）**，
> 其余引用按 **5 个批次**拆分推进（批 2 / 批 3 / 批 4 / 批 5），并在本 issue 中登记完整清单
> **关联**：I142（`LtvTrainingService` 引用 4 张不存在的表 — 本 issue 的同源兄弟）、I150、I151

---

## 0. 一句话描述

I142 修复时只处理了 `LtvTrainingService` 内的 8 处。
全仓扫描发现：**另有 13 个文件、21 处代码行**的原生 SQL 仍在引用
`crm_order` / `crm_order_item` 这两张**在 `meiyetec` 库中不存在**的表 ——
分布在 RFM、流失预测、Cohort、价目表、会员日、数据治理、数据中台、GDPR 合规、LTV 造数等模块。

**两类失败形态并存**：

| 形态 | 表现 | 危险度 |
|---|---|---|
| **显式 500** | 异常冒泡到 Controller → HTTP 500 | 相对良性（有监控可捕获） |
| **静默吞掉** | 被 `catch (Exception e) {}` 吞掉 → 返回**空数据 + 成功** | **高危** —— 用户看到「查到了，无数据」，实则是表不存在 |

---

## 1. 根因：4 张表是「废弃设计」，不是「漏建」

| 判定项 | 证据 |
|---|---|
| 无任何 DDL 历史 | `git log --all -S "crm_order"` 最早只命中 `a414f95 recover: 从工作区重建`，无建表脚本 |
| 全仓 SQL 无引用 | `*.sql` 全仓 grep 4 个表名 → **0 命中** |
| 代码注释自证 | `DcepService.java:759`：「之前：直接 `jdbc.update("UPDATE crm_order ...")`（表名错误：实际是 `pos_order`）」 |
| 同文件自相矛盾 | `LtvSeedController.java:142-160` 造订单代码**已经写 `pos_order`**，但 L52 删订单却写 `crm_order` |

**结论**：这 4 张表是早期设计稿里臆造的表名，从未落地。
真实订单域是 `pos_order` / `pos_order_item`（POS 收银）。

---

## 2. 完整引用清单（21 处代码行 / 13 个文件）

> ⚠️ 行号基于 commit `97361b1`（I142 批 1 修复**前**的 `main`）。
> 注释行已排除：`LtvSeedController.java:21`、`ChurnTrainingService.java:389`、
> `DcepService.java:759`、`LtvTrainingService.java:31`。

| # | 文件 | 行号 | SQL 片段 | 失败形态 |
|---|---|---|---|---|
| 1 | `service/RfmService.java` | 70 | `FROM crm_order WHERE member_id = ? AND status = 3 AND deleted = 0` | 🔴 **显式 500** |
| 2 | `service/ChurnPredictionService.java` | 188 | `FROM crm_order WHERE member_id = ? AND status = 3 AND deleted = 0` | 🔴 **显式 500** |
| 3 | `service/P1SuiteService.java` | 609 | `SELECT COUNT(DISTINCT member_id) FROM crm_order WHERE ...` | 🔴 **显式 500** |
| 4 | `service/P1SuiteService.java` | 683 | `LEFT JOIN crm_order o ON o.member_id = m.id AND o.status = 3` | 🟠 **静默吞掉** |
| 5 | `service/DataGovernanceService.java` | 536 | `(SELECT AVG(pay_amount) FROM crm_order WHERE member_id = ? ...)` | 🟠 **静默吞掉** |
| 6 | `service/DataGovernanceService.java` | 564 | `FROM crm_order o WHERE o.deleted = 0 AND o.status = 3` | 🔴 **显式 500** |
| 7 | `service/CohortService.java` | 84 | `FROM crm_order o ... GROUP BY cohort_month` | 🟠 **静默吞掉** |
| 8 | `service/LtvChurnService.java` | 137 | `SELECT COALESCE(SUM(pay_amount),0) ... FROM crm_order ...` | 🟠 **静默吞掉** |
| 9 | `service/LtvChurnService.java` | 287 | `SELECT MAX(pay_time) ... FROM crm_order ...` | 🟠 **静默吞掉** |
| 10 | `service/PriceListService.java` | 236 | `LEFT JOIN crm_order_item oi ON oi.service_id = s.id` | 🟠 **静默吞掉** |
| 11 | `service/PriceListService.java` | 237 | `LEFT JOIN crm_order o ON o.id = oi.order_id AND o.status = 3` | 🟠 **静默吞掉** |
| 12 | `service/ChurnTrainingService.java` | 410 | `JOIN crm_order o ON o.member_id = m.id AND o.status = 3` | 🟠 **静默吞掉** |
| 13 | `service/P3EcosystemSuiteService.java` | 450 | `SELECT id, order_no, pay_amount FROM crm_order WHERE ... LIMIT 100` | 🟠 **静默吞掉** |
| 14 | `service/AiSchedulerService.java` | 49 | `FROM crm_order o WHERE o.tenant_id = ? AND o.deleted = 0 ...` | 🟠 **静默吞掉** |
| 15 | `service/MemberDayService.java` | 337 | `SELECT COALESCE(SUM(o.pay_amount),0) FROM crm_order o JOIN member_day_execution e ...` | 🟠 **静默吞掉** |
| 16 | `service/P2ComplianceSuiteService.java` | 174 | `SELECT * FROM crm_order WHERE member_id = ? AND tenant_id = ?` | 🔴 **显式 500**（GDPR 导出） |
| 17 | `service/P2ComplianceSuiteService.java` | 189 | `UPDATE crm_order SET member_name = CONCAT('ANON_', member_id) ...` | 🔴 **显式 500**（GDPR 匿名化） |
| 18 | `service/DataHubService.java` | 388 | 内置指标 SQL：`SELECT COALESCE(SUM(pay_amount),0) FROM crm_order ...` | 🟠 **运行时才失败** |
| 19 | `service/DataHubService.java` | 391 | 内置指标 SQL：`SELECT COUNT(*) FROM crm_order ...` | 🟠 **运行时才失败** |
| 20 | `service/DataHubService.java` | 394 | 内置指标 SQL：`SELECT COUNT(DISTINCT member_id) FROM crm_order ...` | 🟠 **运行时才失败** |
| 21 | `controller/LtvSeedController.java` | 52 | `DELETE FROM crm_order WHERE tenant_id = ?` | 🔴 **显式 500**（造数前置清理） |

### 2.1 已确认无影响的项（排除项）

| 位置 | 结论 |
|---|---|
| `backend/src/main/resources/mapper/**` | **无任何** `crm_order` 引用（MyBatis XML 层干净） |
| `frontend/meiyetec-web/src/views/**` | `DataHub.vue:114` 的 `crm_order` 仅为 placeholder 文本，**非真实引用** |
| `DcepService.java:759` | 注释，非代码 |
| `LtvTrainingService.java:31` | 类注释（本批已随 A1-A8 一并更新语义） |

---

## 3. 修复时必须同步解决的「语义陷阱」

> ⚠️ **本节是各批次动手前的强制检查项。** 机械替换表名会引入**比原缺陷更严重**的数据错误。

### 3.1 `status` 枚举语义完全相反 🔴

| 表 | 枚举 | 「有效订单」取值 |
|---|---|---|
| `crm_order`（**臆造表**） | `status = 3` = 有效 | 3 |
| `pos_order`（**真实表**） | `1进行中 2已支付 3已退款 4已撤销` | **2** |

**若把 `status = 3` 直接照搬到 `pos_order`，会把「已退款订单」当成有效消费计入 GMV** ——
从「查不到数据」变成「数据算错」，**更危险**。

**佐证**：`LtvSeedController.java:144` 注释「大部分已完成(status=2)，少量退款(status=3)」。

### 3.2 `pay_amount` → `final_amount`

`pos_order` **无** `pay_amount` 列。候选：

| 列 | 注释 | 语义 |
|---|---|---|
| `final_amount` | 实付金额 | **客户消费承诺口径** ← I142 批 1 已采用 |
| `paid_amount` | 已付金额 | 可能含未结清部分 |
| `total_amount` | 原价总额 | 折扣前 |

**决策（用户 2026-11-11）**：统一用 `final_amount`。

### 3.3 其它列名/枚举差异

| 原写法 | 真实写法 | 备注 |
|---|---|---|
| `crm_member.created_at` | `crm_member.create_time` | I142 批 1 已修正 |
| `crm_review.created_at` | `crm_review.create_time` | I142 批 1 已修正 |
| `crm_order_item` | `pos_order_item` | 需补 `deleted = 0` 过滤（`pos_order_item` 有软删列） |
| `biz_service_item` JOIN | `biz_service` JOIN | `pos_order_item.service_id` 实指 `biz_service.id`（由 `PosController.addItemInternal` 用 `BizServiceMapper` 写入确认） |
| `crm_member_coupon` | `market_coupon_grant` | 无 `deleted` 列；`status` 枚举 1未使用/2已使用/3已过期 与原语义吻合 |

---

## 4. 分批计划（用户 2026-11-11 拍板）

### 批 2 — 在线读路径（8 个 Service / 12 处）🟠 中高风险

| 文件 | 行号 |
|---|---|
| `service/RfmService.java` | 70 |
| `service/CohortService.java` | 84 |
| `service/LtvChurnService.java` | 137, 287 |
| `service/ChurnPredictionService.java` | 188 |
| `service/PriceListService.java` | 236, 237 |
| `service/ChurnTrainingService.java` | 410 |
| `service/P3EcosystemSuiteService.java` | 450 |
| `service/AiSchedulerService.java` | 49 |
| `service/MemberDayService.java` | 337 |
| `service/P1SuiteService.java` | 609, 683 |

**风险**：直接影响 RFM 分层、流失评分、Cohort 留存率、热度降级策略、会员日 ROI、排班预测的**业务产出**。
修复后这些模块的输出会**从「空/默认」变为「真实数据」**，历史数值不可比。

**依赖的业务决策**：
1. `status` 口径统一为 `pos_order.status = 2`（已支付）——是否**排除部分退款**订单？（参见 I151；**已由 §4A Q6 落档**）
2. `pay_amount` 统一为 `final_amount` —— 已定（§3.2）。
3. `LtvChurnService` / `ChurnTrainingService` 中的**兜底常量**（如 `repurchaseRate = 0.65` / `retentionYears = 2.0`）
   在修复后会**被真实数据取代** —— 这些硬编码是「假数据」，需业务确认可否被真实值覆盖。

### 批 3 — 数据治理 / 数据中台（5 处）🟡 中风险

| 文件 | 行号 | 说明 |
|---|---|---|
| `service/DataGovernanceService.java` | 536 | 客户价值评分 `AVG(pay_amount) * 12` |
| `service/DataGovernanceService.java` | 564 | 流失风险分批量更新（`LIMIT ?`） |
| `service/DataHubService.java` | 388, 391, 394 | 内置指标定义（**运行时才失败**，需同时更新前端指标口径） |

**依赖的业务决策**：
1. `DataGovernanceService:536` 的 `* 12`（年化系数）是否保留？（**已由 §4A Q1 落档**：实际已改为「按 tenure 推导年均单数」，未经业务确认）
2. `DataHubService` 3 个内置指标的**指标名称 / 单位**是否随之调整（前端 `DataHub.vue` 展示联动）？
3. 用户已明确：**本轮不动 `DataHubService`**（Q9）。

### 批 4 — GDPR 匿名化 / 导出（2 处）🔴 高风险

| 文件 | 行号 | 说明 |
|---|---|---|
| `service/P2ComplianceSuiteService.java` | 174 | GDPR 数据导出：`SELECT * FROM crm_order` |
| `service/P2ComplianceSuiteService.java` | 189 | GDPR 匿名化：`UPDATE crm_order SET member_name = CONCAT('ANON_', ...)` |

**为什么高风险**：
- **L174（导出）**：`SELECT *` 导出订单给数据主体。改错表 → 导出的个人数据**不完整**（漏掉 POS 订单），构成**合规缺口**。
- **L189（匿名化）**：现状是**静默无效**（表不存在 → catch 吞掉 → 会员已脱敏但**订单里的 `member_name`/`member_phone` 未脱敏**）。
  改成 `pos_order` 后会**真正执行 UPDATE**，即批量改写历史订单的 `member_name` ——
  这是**不可逆的写操作**，需确认：
  1. `pos_order` 中需脱敏的字段全集（`member_name` / `member_phone` / `remark`？`remark` 可能含 PII）
  2. 是否需保留脱敏前的可追溯副本（`pos_order` 无软删、无历史表 → **一旦改写不可回滚**）

**依赖的业务决策**：脱敏字段范围 + 是否要求可回滚 + 存量数据修复方案。

### 批 5 — LTV 造数前置清理（1 处）🔴 高风险

| 文件 | 行号 | 说明 |
|---|---|---|
| `controller/LtvSeedController.java` | 52 | `DELETE FROM crm_order WHERE tenant_id = ?` |

**用户决策（Q10）**：**保留原样，让它失败**。

理由：改成 `DELETE FROM pos_order WHERE tenant_id = ?` 会**真删掉 1848 行真实 POS 订单**（含真实会员的消费记录），
而该端点名为「造数/重置」，一旦被误调就是**不可逆的真实数据损失**。

**遗留处置建议（待用户后续决策）**：
- 短期：保持失败（安全）—— 已确认 L52 抛出的异常会冒泡为显式 500（**需复核是否被外层 catch**，见 §6）
- 中期：改为「仅删除本端点自己造的样例订单」（例如按 `order_no` 前缀标记），或直接**去掉该 DELETE 语句**
- 长期：造数端点应加**环境护栏**（仅允许 `profile != prod` 时执行）

---

## 4A. 批 2-3 遗留决策项登记（Q1 / Q2 / Q6 / Q8）

> 本节补登 4 项在 **commit `43bbcce`（I152 批 2-3 修复）** 中**已由实现事实上决定、但未经业务显式签字**的遗留项。
> 补登原因：§4 批 2 / 批 3 的「依赖的业务决策」问句在修复落地后已部分失效，需把**实际生效口径**写清楚，避免后续批次按旧问句理解现状。
> 涉及文件：`backend/src/main/java/com/meiyetec/service/DataGovernanceService.java`（`batchPredictLtv` L525 / `batchPredictChurn` L616 / `executeIdempotent` L707 / `batchPredict` L485）、`backend/src/main/java/com/meiyetec/controller/LtvChurnController.java`（`computeLtv` L75 / `computeChurn` L160）。
> 核实环境：本地 Docker `meiyetec-mysql` @ `127.0.0.1:3307`，commit `43bbcce`。

### Q1 — 年化系数 `* 12` 未保留，改为「按 tenure 推导的年均单数」 🟠 待业务确认

**旧口径（§4 批 3 决策项 #1 问句所指）**：commit `43bbcce^` 的 `DataGovernanceService.java:536` 为
`COALESCE((SELECT AVG(pay_amount) FROM crm_order WHERE ...) * 12, 0)` —— 用**固定 12 倍**假定客户年下单 12 次，直接作为 `predicted_ltv_365d`。

**修复后实际口径**（`DataGovernanceService.batchPredictLtv` L585-586、L562-565）：

```sql
ROUND(COUNT(*) * 365 / GREATEST(GREATEST(DATEDIFF(CURDATE(), MIN(p.pay_time)), 0), 1), 2) AS purchase_frequency
-- pred_ltv_30d  = aov * purchase_frequency / 12
-- pred_ltv_90d  = aov * purchase_frequency / 4
-- pred_ltv_365d = aov * purchase_frequency
-- pred_ltv_lifetime = aov * purchase_frequency * 3
```

| 项 | 旧 | 新 |
|---|---|---|
| 年化依据 | **固定 12 次**（拍脑袋常数） | **实际年均单数** = `COUNT(*) * 365 / max(tenure_days, 1)` |
| `predicted_ltv_365d` | `AVG(pay_amount) * 12` | `aov * 年均单数` |
| 客户仅 1 单 / 注册 10 天 | `AVG * 12` → **虚高 12 倍** | `1 * 365/10 = 36.5` → 仍虚高但由 tenure 约束 |

**结论**：§4 批 3 决策项 #1「`* 12` 是否保留」**已被实现层面回答为「不保留」**，
但**未经业务确认**，且 I152 §4 批 3 的问句文本未更新 —— 属**口径漂移未落档**。

**遗留风险**：新口径的 `purchase_frequency` 在**新客（tenure 极短）**下会被 365/tenure 放大到数十倍年化，
`predicted_ltv_365d` 可远超其历史消费额；且 `pred_ltv_lifetime = 365d * 3` 再乘一层。
**待业务确认**：是否需要对新客（`tenure_days < N`）封顶年均单数。

**关联**：`LtvChurnController.computeLtv` L111 用 `orders.size() * (tenureDays <= 0 ? 365.0 : 365.0 / tenureDays)`，
与 batch 版公式**代数等价**，但 `tenureDays` 起算点不同（`LocalDate` 差 vs `DATEDIFF(CURDATE(), MIN(pay_time))` 含时分），
在首单当日消费的会员上可能相差 1 天 → 两入口 `predicted_ltv_365d` 可能不一致。

### Q2 — 模型本体不参与计算，`model_version` 仅为标签 🟠 待业务确认（关联 I155）

**实际口径**（`DataGovernanceService.batchPredictLtv` L526 注释原文 / `batchPredictChurn` L617 注释原文）：

> `// 取已部署模型的版本号（模型本体不参与计算，本轮为规则版）`

两个批量预测端点**只从注册表取 `version` 字符串**写入 `model_version` 字段，
**从不读取 `model_formula` / `lr_coefficients_json` / `val_auc` 等模型参数参与计算**：

| 读取的字段 | 位置 | 用途 |
|---|---|---|
| `ltv_model_registry.version` | L531-539 | 仅作 `model_version` 标签 |
| `churn_model_registry.version`（`is_active=1`） | L620-627 | 仅作 `model_version` 标签（**且写入值硬编码 `'v1'`**，L640，与取到的 version 无关） |

**佐证 — 已激活的模型本身不可用**（本地库实测）：

```
SELECT id, tenant_id, version, is_active, LEFT(model_formula, 60) FROM ltv_model_registry;
id  tenant_id  version  is_active  model_formula
1   1          v1       0          LTV = 0.0000 + 0.0000 * ((recency_days - 5931.0818) / 4862.6...
2   1          v1       0          LTV = 0.0000 + 0.0000 * ((recency_days - 5930.6803) / 4862.6...
3   1          v1       1          LTV = 0.0000 + 0.0000 * ((recency_days - 5930.6803) / 4862.6...
```

`is_active = 1` 的记录（id=3）**全部系数为 `0.0000`** → 即便接入模型公式，预测值也会恒为 0。

**结论**：模型训练链路（`ai_ltv_model` / `ltv_model_registry`）与预测链路**完全脱钩**，
`batchPredictLtv` / `batchPredictChurn` 是**规则版**（`model_type = 'RULE'`）。
**待业务确认**：预测端何时切到消费训练模型 —— 与 **I155** 是同一根因，**一并决策**避免两处重复拍板。

### Q6 — 退款未扣：LTV / Churn 均按 `SUM(final_amount)` 计算，未减 `refunded_amount` 🟠 待业务确认（关联 I151）

**实际口径**（`DataGovernanceService.batchPredictLtv` L582 / `batchPredictChurn` L671）：

```sql
COALESCE(SUM(p.final_amount), 0) AS historical_ltv,
COALESCE(AVG(p.final_amount), 0) AS avg_order_value
-- FROM pos_order p WHERE p.tenant_id = ? AND p.status = 2 AND p.deleted = 0
```

**缺什么**：`pos_order` 表**有** `refunded_amount` 列（由 `PosRefundService` / `PosOrderMapper.incrementRefundedAmount` 维护，
带 CAS 守卫），但两处聚合**完全未引用该列** → **已退款金额仍被计入 LTV / 客单价**。

**本地库实测影响面**：

```
SELECT COUNT(*) partial_refund_rows, SUM(refunded_amount), SUM(final_amount)
  FROM pos_order WHERE deleted = 0 AND status = 2 AND COALESCE(refunded_amount, 0) > 0;
partial_refund_rows  refund_amt  fin
4                    2000.00     4000.00
```

| 项 | 值 |
|---|---|
| 受影响订单（`status=2` 已支付 + 已部分退款） | **4 行** |
| 计入 LTV 但已退款的金额 | **2000.00** |
| 这些订单的 `final_amount` 合计 | 4000.00 → **退款额占 50%** |
| `status=2` 全量 `final_amount` | 28,008,394.00 → 退款占比约 **0.007%** |

**结论**：
1. **口径缺陷成立** —— 已退款金额未从 LTV 基数扣除，`historical_ltv` / `avg_order_value` / `purchase_frequency` 全部偏高。
2. **当前本地影响量级小**（4 单 / 2000 元）→ **非 P1**，但**生产库未确认**（生产 `pos_order` 数据量与部分退款比例未知）。
3. `status = 3`（已退款整单）已被过滤 ✅，**缺口仅在「已支付 + 部分退款」这一类**。

**待业务确认**：LTV 口径是否应按 `final_amount - refunded_amount` 计算（净额口径），
以及是否需要**历史快照重算**（现存 `crm_ltv_snapshot` 3522 行 / `crm_churn_prediction` 489 行均按旧口径生成）。
**关联 I151**（退款语义），**一并决策**。

### Q8 — 失败语义：`catch` 后仍返回 `Result.success`（HTTP 200 假成功） 🟠 待业务确认

**实际口径**（`DataGovernanceService.batchPredict` L497-510）：

```java
try {
    ...
    result.put("message", "批量预测完成");
} catch (Exception e) {
    result.put("error", e.getMessage());   // ← 只把异常塞进 map
}
return Result.success(result);             // ← 无论如何都是 200
```

| 失败点 | 异常信息 | 外部可见 |
|---|---|---|
| 无已部署 LTV 模型 | `RuntimeException("无已部署 LTV 模型")`（L535/L537） | `{"code":200, "data":{"error":"无已部署 LTV 模型"}}` |
| 无已部署 Churn 模型 | `RuntimeException("无已部署 Churn 模型")`（L625/L627） | 同上 |
| SQL 失败 / 表不存在 | 原始异常 message | 同上 |
| `executeIdempotent` 的 INSERT 失败 | 原始异常 message | 同上 |

**前端只能通过「`data.error` 字段非空」判断失败** —— 属于**假成功（Fake Success）**契约漂移，
与本项目已登记的 I047（Instagram webhook catch 吞异常）**同型**。

**第二重失败语义 —— 非事务降级分支**（`executeIdempotent` L707-719）：

```java
if (transactionManager == null) {
    jdbc.update(deleteSql, deleteArgs);      // 先删
    return jdbc.update(insertSql, insertArgs); // 后插（无事务包裹）
}
```

`transactionManager` 声明为 `@Autowired(required = false)`（L41-42）→ **若容器内无事务管理器**，
「先删后插」退化为**非原子**两步：DELETE 成功、INSERT 失败 → **当日快照被清空且无法回滚**（数据丢失）。

**未确认项**：
- 生产环境 `transactionManager` 是否为 `null` —— **未确认**（本地为 Spring Boot + `spring-boot-starter-jdbc`，
  通常自动装配 `DataSourceTransactionManager`，本地未观测到该分支被触发）。
- 前端 `LtvTraining.vue` 是否读取 `data.error` 字段 —— **未确认**。

**待业务确认**：批量预测失败是否应返回 `Result.error(...)`（非 200），
以及是否需要把 `executeIdempotent` 的非事务降级分支改为**直接抛错拒绝执行**（宁可失败也不清空数据）。

---

## 5. 与 I142 批 1 的关系

| 批次 | 范围 | 状态 |
|---|---|---|
| I142 批 1 | `LtvTrainingService` 内 8 处（A1-A8） | ✅ **已完成**（2026-11-11，commit 1） |
| 批 2 | 在线读路径 12 处 | ⏳ 待启动（**批 2-3 的实际修复范围仅 `DataGovernanceService`** —— commit `43bbcce` 只改了 1 个文件，本批 12 处读路径**未动**） |
| 批 3 | 数据治理 / 数据中台 5 处 | 🟡 **部分完成**（`DataGovernanceService` 2 处已由 commit `43bbcce` 修复；`DataHubService` 3 处**未动** —— 用户已明确本轮不动（Q9）） |
| 批 4 | GDPR 2 处 | ⏳ 待启动（需业务决策） |
| 批 5 | Seed DELETE 1 处 | ⏳ 待启动（当前故意保留失败） |

**批 1 已验证**：ETL 成功，`ltv_training_feature` 从 0 行 → **489 行**（覆盖 tenant 1 的 489 个会员）。

---

## 6. 未确认项（禁止猜测）

| 项 | 状态 |
|---|---|
| 各 Service 捕获异常的具体边界（部分行在 try 块内层还是外层） | **部分未确认** —— 已确认 `#1/#2/#3/#6/#16/#17/#21` 冒泡为显式 500，其余为静默吞掉 |
| `LtvSeedController` L52 的异常是否被外层 catch | **未确认** |
| 批 2 修复后各模块输出的历史可比性策略 | **未确认**（需业务决策） |
| `pos_order.remark` 是否含 PII（影响批 4 脱敏范围） | **未确认** |

---

## 7. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 全仓扫描 | `rg "crm_order" backend/src/main/java` → 21 处代码行 + 4 处注释 |
| SQL 脚本扫描 | `*.sql` 全仓 grep 4 表名 → 0 命中 |
| Git 历史 | `git log --all -S "crm_order"` → 最早 `a414f95 recover: 从工作区重建` |
| 取证时间 | 2026-11-11 |
| 关联 issue | I142、I150、I151、**I155**（模型脱钩，见 §4A Q2）、**I156**（双写重复，见 §4A Q8 关联）、**I151**（退款语义，见 §4A Q6） |
| commit hash | 见 commit 1 `fix(ltv): 修复 LtvTrainingService 8 处表名与列名错误（I142 批 1）` |
| §4A Q1/Q2/Q6/Q8 核实 commit | **`43bbcce`** `fix(ltv): 修复 batchPredictLtv/Churn 表名+量纲+幂等（I152 批 2-3）`（仅改 `DataGovernanceService.java`，+189 −44） |
| §4A 取证时间 | 2026-11-12 23:50 – 2026-11-13 00:05（本地 Docker `meiyetec-mysql` @ `127.0.0.1:3307`，租户 1） |
