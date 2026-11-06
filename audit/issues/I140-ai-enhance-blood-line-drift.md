# I140 — `AiEnhanceService` 数据血缘漂移：`pos_order_item.service_id` 指向 `biz_service`，代码却 JOIN `biz_service_item`

> **状态**：Open（已定位，待修复）
> **严重度**：P1 — 静默错误数据（假成功），无异常、无日志
> **发现日期**：2026-11-07（I128-b 收口调查过程中发现）
> **登记人**：Cursor（I128-b 字符集批次）
> **修复状态**：**UNVERIFIED**（本文件仅登记缺陷，未修改业务代码）

---

## 0. 一句话描述

POS 收银写入的 `pos_order_item.service_id` 存的是 **`biz_service.id`**；
而 `AiEnhanceService` 用它去 JOIN **`biz_service_item`**，因两表 id 空间重叠，
**SQL 不报错、不空集，而是静默返回另一套服务的价格/分类/名称** —— AI 推荐结果基于错数据。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java` |
| 类 | `com.meiyetec.service.AiEnhanceService` |
| 方法 | `computeRecommendations(Long tenantId, Long memberId, String type)`（private，L120） |
| 缺陷行 | **L129** — `"JOIN biz_service_item s ON i.service_id = s.id "` |
| 同方法次发点 | **L142-145** — `"SELECT id, service_name as name, price, sales_count FROM biz_service_item "`（热销 Top10 热销榜同样取错表） |
| 关联表 | `pos_order_item` / `pos_order` / `biz_service` / `biz_service_item` |

```127:131:backend/src/main/java/com/meiyetec/service/AiEnhanceService.java
                "SELECT DISTINCT s.category_id FROM pos_order_item i " +
                "JOIN pos_order o ON i.order_id = o.id " +
                "JOIN biz_service_item s ON i.service_id = s.id " +
                "WHERE o.member_id = ? AND o.deleted = 0 AND i.deleted = 0 " +
                "ORDER BY o.created_at DESC LIMIT 50",
```

---

## 2. 血缘证据链

### 2.1 写入侧：`service_id` 的来源是 `biz_service`

| 证据 | 位置 | 内容 |
|---|---|---|
| POS 服务列表端点 | `backend/src/main/java/com/meiyetec/controller/PosController.java:95-107` | `GET /pos/services/list` 返回 `Result<List<BizService>>`，`LambdaQueryWrapper<BizService>` → **数据源是 `biz_service` 表** |
| 写入点 | `backend/src/main/java/com/meiyetec/controller/PosController.java:214` | `item.setServiceId(itemDto.getServiceId())` ← 前端从 `/pos/services/list` 取的 id |
| 端点权限 | `PosController.java:96` | `@RequirePermission("pos:service:list")` |

### 2.2 数据侧：`pos_order_item.service_id` 与 `biz_service` 对得上，与 `biz_service_item` 对不上

实测 SQL（`meiyetec` 库，`pos_order_item` 中 `service_id IS NOT NULL` 共 **115** 行）：

```
total_items=115   hits_biz_service=70   hits_biz_service_item=115   hits_both=70
```

逐行对照（快照名 vs 两张表的名字）：

| item_id | service_id | `pos_order_item.service_name`（快照） | `biz_service.service_name` | `biz_service_item.service_name` |
|---|---|---|---|---|
| 2 | 3 | 肩颈舒缓按摩 | **肩颈舒缓按摩** ✅ | 抗皱紧致提升 ❌ |
| 3 | 4 | 全身精油SPA | **全身精油SPA** ✅ | 玻尿酸导入 ❌ |
| 4 | 5 | 美睫嫁接 | **美睫嫁接** ✅ | 美白淡斑 ❌ |
| 5 | 1 | 基础面部护理 | **基础面部护理** ✅ | 基础面部清洁 ❌ |
| 7 | 2 | 深层补水护理 | **深层补水护理** ✅ | 深层补水护理（碰巧同值） |
| 8/9 | 1 | 基础面部护理 | **基础面部护理** ✅ | 基础面部清洁 ❌ |

> 结论：70 行**真实 POS 订单**的快照名与 `biz_service` 逐行一致。
> `biz_service_item` 的 id 1-22 是**另一套数据集**（`SV001`~`SV022`，服务名完全不同）。

### 2.3 为什么"两表 id 空间重叠"让问题更危险

`biz_service` 与 `biz_service_item` 的 `id` 都是 1..N 自增，存在**同号不同义**：

| id | `biz_service` | `biz_service_item` |
|---|---|---|
| 1 | 基础面部护理 | 基础面部清洁 |
| 3 | 肩颈舒缓按摩 | 抗皱紧致提升 |
| 5 | 美睫嫁接 | 美白淡斑 |

因此 `JOIN biz_service_item s ON i.service_id = s.id`：

- **不会抛异常**（不像外键缺失会报错）；
- **不会返回空集**（70 行全部命中同号行）；
- 而是**静默取到另一套服务的 `category_id` / `price` / `sales_count` / `service_name`**。

这是典型的**假成功 + 数据血缘漂移**（验收规则 §10 要求单独标记）。

### 2.4 另有 45 行"测试数据"指向 `biz_service_item`

`item_id` 28~39 等行的 `service_name` 快照是 `Service-001`…`Service-012`，
`service_id` 落在 13~22（**`biz_service` 中不存在这些 id**），`biz_service` 侧 `service_name` 为 `NULL`。
这批是测试种子写的模拟订单。对它们而言 JOIN `biz_service_item` 反而"对得上"，
因此**不能**用"JOIN 有结果"来反推血缘——必须用快照名比对（§2.2）。

---

## 3. 影响面

| 受影响功能 | 说明 |
|---|---|
| AI 推荐（`computeRecommendations` L126-136） | 会员历史消费类别集合 `memberCategories` 取自错误服务的 `category_id` → 下游推荐类别整体偏移 |
| AI 热销 Top10（L142-145） | `service_name` / `price` / `sales_count` 全部来自 `biz_service_item` 而非 `biz_service` |

---

## 4. 同类待核查点（**未确认**，不在本 issue 修复范围）

| 位置 | 片段 | 状态 |
|---|---|---|
| `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java:232` | `JOIN biz_service_item s ON s.id = oi.service_id`（`crm_order_item`） | **未确认** —— `crm_order_item.service_id` 的血缘未取证，疑似同类问题 |
| `backend/src/main/java/com/meiyetec/service/BizTreatmentService.java:96 / 464 / 520 / 703` | `LEFT JOIN biz_service_item i ON p.service_id = i.id`（`biz_treatment_plan`） | 已确认 `biz_treatment_plan.service_id` → `biz_service_item`（见 `init_rbac_v2_1_a.sql:195` 的 `SELECT id FROM biz_service_item`）→ **此处正确** |

---

## 5. 修复建议（未实施）

1. **最小改动**：`AiEnhanceService.java:129` 与 L142-145 的表名由 `biz_service_item` 改为 `biz_service`，
   列名同步调整（`biz_service` 的列是 `service_name` / `unit_price`，**没有** `price` / `sales_count` / `category_id` 字段）。
2. **必须先确认**：`biz_service` 表是否需要补 `category_id` / `price` / `sales_count` 三列，
   否则 L142-145 的热销 Top10 无法直接迁表。
3. 迁移脚本必须**同时清理** `pos_order_item` 中 `service_id` 指向 `biz_service_item` 的 45 行测试数据，
   或为其补一层映射表，避免修复后这批数据变成孤儿引用。

> ⚠️ 本 issue **未修改任何业务代码**，状态保持 `UNVERIFIED`，修复需另行开批次并附运行证据。

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `localhost:3307`（本地 Docker `meiyetec-mysql`） |
| 取证脚本 | `.audit-tmp/i140-bloodline.sql`（只读 SELECT） |
| 取证时间 | 2026-11-07 |
| commit hash | 待补（本批次尚未提交） |
| DB snapshot | `audit/evidence/` 下待补 |
