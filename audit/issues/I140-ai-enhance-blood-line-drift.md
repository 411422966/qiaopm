# I140 — `AiEnhanceService` 数据血缘漂移：`pos_order_item.service_id` 指向 `biz_service`，代码却 JOIN `biz_service_item`

> **状态**：Closed（已修复并通过运行验证）
> **严重度**：P1 — 静默错误数据（假成功），无异常、无日志
> **发现日期**：2026-11-07（I128-b 收口调查过程中发现）
> **登记人**：Cursor（I128-b 字符集批次）
> **修复状态**：**VERIFIED**（2026-11-07 修复落地，附 curl + SQL 运行证据）
> **修复方案**：方案 A（仅改两处 SQL，不动表结构）

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

## 5. 修复建议（**已实施**，见 §7）

1. **最小改动**：`AiEnhanceService.java:129` 与 L142-145 的表名由 `biz_service_item` 改为 `biz_service`，
   列名同步调整（`biz_service` 的列是 `service_name` / `unit_price`，**没有** `price` / `sales_count` / `category_id` 字段）。
2. **必须先确认**：`biz_service` 表是否需要补 `category_id` / `price` / `sales_count` 三列，
   否则 L142-145 的热销 Top10 无法直接迁表。
3. 迁移脚本必须**同时清理** `pos_order_item` 中 `service_id` 指向 `biz_service_item` 的 45 行测试数据，
   或为其补一层映射表，避免修复后这批数据变成孤儿引用。

> ⚠️ 本 issue 修复时**未修改表结构**（§7 方案 A），故 §5.2 的"补三列"与 §5.3 的"清理测试数据"均未执行 ——
> `biz_service` 的 `category_id` 全 NULL 属另一问题，已拆分为 **I141**；45 行测试数据的清理一并留待 I141 批次。

---

## 6. 同类待核查点

| 位置 | 片段 | 状态 |
|---|---|---|
| `backend/src/main/java/com/meiyetec/service/LtvTrainingService.java:232` | `JOIN biz_service_item s ON s.id = oi.service_id`（`crm_order_item`） | ✅ **已核查并拆分** —— 该处引用的 `crm_order` / `crm_order_item` / `crm_point_account` / `crm_member_coupon` **四张表在库中均不存在**，已登记 **[I142](I142-ltv-training-missing-tables.md)** |
| `backend/src/main/java/com/meiyetec/service/BizTreatmentService.java:96 / 464 / 520 / 703` | `LEFT JOIN biz_service_item i ON p.service_id = i.id`（`biz_treatment_plan`） | 已确认 `biz_treatment_plan.service_id` → `biz_service_item`（见 `init_rbac_v2_1_a.sql:195` 的 `SELECT id FROM biz_service_item`）→ **此处正确** |

---

## 7. 修复实施记录（2026-11-07）

### 7.1 改动内容

**唯一改动文件**：`backend/src/main/java/com/meiyetec/service/AiEnhanceService.java`
（仅 `computeRecommendations` 方法内两处 SQL，不动任何 Java 逻辑、不动表结构）

**改动 1 —— 会员历史查询（L126-135）**

原 SQL（ERROR 3065：`DISTINCT` 与 `ORDER BY` 非选择列 `o.created_at` 冲突）：

```java
"SELECT DISTINCT s.category_id FROM pos_order_item i " +
"JOIN pos_order o ON i.order_id = o.id " +
"JOIN biz_service_item s ON i.service_id = s.id " +          // ← 血缘漂移
"WHERE o.member_id = ? AND o.deleted = 0 AND i.deleted = 0 " +
"ORDER BY o.created_at DESC LIMIT 50"
```

修复后：

```java
"SELECT s.category_id, MAX(o.created_at) AS last_at " +
"FROM pos_order_item i " +
"JOIN pos_order o ON i.order_id = o.id " +
"JOIN biz_service s ON i.service_id = s.id " +              // ← 血缘修正
"WHERE o.member_id = ? AND o.deleted = 0 AND i.deleted = 0 " +
"GROUP BY s.category_id ORDER BY last_at DESC LIMIT 50"
```

- 血缘：`biz_service_item` → **`biz_service`**（依据 §2 写入侧 / 数据侧双重取证）
- ERROR 3065：`DISTINCT` + `ORDER BY o.created_at` → **`GROUP BY s.category_id` + `MAX(o.created_at) AS last_at` + `ORDER BY last_at`**
  （等价语义，且消除非选择列排序冲突）

**改动 2 —— 热销 Top10（L142-152）**

原 SQL（ERROR 1054 `Unknown column 'price'`；且 `biz_service_item` 无 `price` / `sales_count` / `enabled` 列）：

```java
"SELECT id, service_name as name, price, sales_count " +
"FROM biz_service_item " +
"WHERE deleted = 0 AND tenant_id = ? AND enabled = 1 " +
"ORDER BY sales_count DESC LIMIT 10"
```

修复后：

```java
"SELECT MIN(s.id) AS id, MIN(s.service_name) AS name, " +
"MIN(s.unit_price) AS price, COALESCE(SUM(x.cnt), 0) AS sales_count " +
"FROM biz_service s " +
"LEFT JOIN (SELECT service_id, COUNT(*) AS cnt FROM pos_order_item " +
"           WHERE deleted = 0 GROUP BY service_id) x ON x.service_id = s.id " +
"WHERE s.deleted = 0 AND s.tenant_id = ? AND s.status = 1 " +
"GROUP BY s.service_code ORDER BY sales_count DESC LIMIT 10"
```

- 表：`biz_service_item` → **`biz_service`**
- 列名：`price` → `unit_price`；`enabled` → `status`；`sales_count` → 由 `pos_order_item` 计数**派生**
- **去重**：`biz_service` 每个 `service_code` 有 2 行重复（§2.3），故按 `service_code` 聚合，
  SELECT 用 `MIN(s.id)` / `MIN(s.service_name)` / `MIN(s.unit_price)` 固定取值
  —— **兼容 `ONLY_FULL_GROUP_BY`**（实测 `@@sql_mode` 含该选项）

### 7.2 运行证据

**构建与部署**：`mvn -Dmaven.test.skip=true package` → `docker build -t meiyetec/api:dev .` → `docker compose up -d api`
→ `/actuator/health` = `{"status":"UP"}`

**curl 实测（`GET /ai/recommend`，admin 登录取 Bearer token）**：

| 阶段 | 输出 |
|---|---|
| **修复前** | `{"items":[{"reason":"数据不足","name":"暂无推荐","id":0,"score":0}],"cacheHit":false}` —— 整条推荐降级为单条"暂无推荐" |
| **修复后（memberId=1，冷路径）** | `{"items":[{"id":1,"name":"基础面部护理","price":280.00,"score":0.04,"reason":"热销推荐"},{"id":5,"name":"美睫嫁接",...},{"id":3,"name":"肩颈舒缓按摩",...},{"id":4,"name":"全身精油SPA",...},{"id":2,"name":"深层补水护理",...}],"cacheHit":false}` —— **返回 5 条真实推荐** |
| **修复后（memberId=2，冷路径）** | 同上 5 条真实推荐 |

> ⚠️ 修复前 `memberId=1` 的降级结果已写入 `ai_recommend_cache`（缓存 6 小时）。
> 验证时**已删除该条陈旧缓存**（`DELETE FROM ai_recommend_cache WHERE id=1`）后复测，方得到上述冷路径输出。
> 这是缓存的正常行为，非缺陷。

**SQL 层验证**（`--default-character-set=utf8mb4`，只读）：

- 修复后热销 SQL → 返回 **5 行**（id 1/2/3/4/5，每个 `service_code` 一行），`sales_count` = 41 / 10 / 8 / 6 / 5
- 修复后历史 SQL → **无 ERROR 3065**，返回 1 行（`category_id` 为 NULL → 见 I141）
- `biz_service` 真实列名确认：`unit_price` / `status` / `category_id`（**无** `price` / `sales_count` / `enabled`）

**证据文件**：`audit/evidence/F145-i140-ai-enhance-fix/`
- `after-01-curl-runtime.txt` —— 修复后 curl 实测输出
- `after-02-sql-verification.txt` —— SQL 层只读验证（含 `biz_service` 表结构、缺表清单）

### 7.3 修复过程中新发现的遗留（已拆独立 issue）

| Issue | 摘要 |
|---|---|
| **[I141](I141-biz-service-category-null.md)** | `biz_service.category_id` 10/10 全 NULL → `memberCategories` 恒空；且该集合方法内**从未被读取**（死代码） |
| **[I142](I142-ltv-training-missing-tables.md)** | `LtvTrainingService` 引用的 `crm_order` / `crm_order_item` / `crm_point_account` / `crm_member_coupon` **四张表均不存在** → LTV 训练 ETL 全链路不可用 |

### 7.4 遗留（不在本 issue 范围，仅报告）

| 位置 | 现象 |
|---|---|
| `AiEnhanceService.java:434-437` `parseJsonList(String)` | 实现为 `return List.of();` —— **桩实现**。导致缓存命中路径（`cacheHit=true`）返回 `items: []` 空数组，尽管缓存表里存的是正确 JSON。**未修复**（超出本批授权范围） |

> ⚠️ 该桩实现是**缓存路径独立于本次血缘修复的既存缺陷**：首次调用（`cacheHit=false`）返回真实推荐，
> 第二次调用（`cacheHit=true`）返回空数组。不影响 I140 修复结论（冷路径已 VERIFIED），但缓存路径需单独处理。

---

## 8. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证脚本 | 只读 SELECT / `SHOW TABLES` / `SHOW COLUMNS`（`--default-character-set=utf8mb4`） |
| 取证时间 | 2026-11-07 |
| 运行时证据 | `audit/evidence/F145-i140-ai-enhance-fix/after-01-curl-runtime.txt`、`after-02-sql-verification.txt` |
| commit hash | 待补（随 commit 1 回填） |
| DB snapshot | 本地开发库，无生产快照；表结构变更 **0**（本修复未动 DDL） |
