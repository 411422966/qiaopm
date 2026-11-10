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
1. `status` 口径统一为 `pos_order.status = 2`（已支付）——是否**排除部分退款**订单？（参见 I151）
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
1. `DataGovernanceService:536` 的 `* 12`（年化系数）是否保留？
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

## 5. 与 I142 批 1 的关系

| 批次 | 范围 | 状态 |
|---|---|---|
| I142 批 1 | `LtvTrainingService` 内 8 处（A1-A8） | ✅ **已完成**（2026-11-11，commit 1） |
| 批 2 | 在线读路径 12 处 | ⏳ 待启动 |
| 批 3 | 数据治理 / 数据中台 5 处 | ⏳ 待启动（用户已明确本轮不动 DataHub） |
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
| 关联 issue | I142、I150、I151 |
| commit hash | 见 commit 1 `fix(ltv): 修复 LtvTrainingService 8 处表名与列名错误（I142 批 1）` |
