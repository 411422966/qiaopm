# I141 — `biz_service.category_id` 全表 NULL，AI 推荐"会员偏好类别"链路永远为空

> **状态**：Open
> **严重度**：P2 — 数据缺失导致功能半降级（不报错、不空集，属于"死代码"形态）
> **发现日期**：2026-11-07（I140 修复过程中顺带发现）
> **登记人**：Cursor（I140 修复批次）
> **修复状态**：**UNVERIFIED**（本文件仅登记缺陷，未修改业务代码、未补数据）

---

## 0. 一句话描述

`biz_service` 表 10 行的 `category_id` **全部为 NULL**，导致 `AiEnhanceService.computeRecommendations`
里计算出来的 `memberCategories` 集合**永远为空** —— 该集合在方法内**声明并写入，却从未被读取**，
属于事实上的死代码。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java` |
| 类 | `com.meiyetec.service.AiEnhanceService` |
| 方法 | `computeRecommendations(Long tenantId, Long memberId, String type)`（private） |
| 缺陷行 | **L124**（`Set<Long> memberCategories = new HashSet<>();`）→ **L136**（写入 `memberCategories.add(...)`） |
| 数据表 | `biz_service.category_id`（列存在，但 10/10 行均为 NULL） |

```124:137:backend/src/main/java/com/meiyetec/service/AiEnhanceService.java
            Set<Long> memberCategories = new HashSet<>();
            try {
                List<Map<String, Object>> history = jdbc.queryForList(
                    "SELECT s.category_id, MAX(o.created_at) AS last_at " +
                    ...
                for (Map<String, Object> h : history) {
                    if (h.get("category_id") != null)
                        memberCategories.add(((Number) h.get("category_id")).longValue());
                }
            } catch (Exception e) {
                log.debug("会员历史查询失败：{}", e.getMessage());
            }
```

> 方法内 `memberCategories` 声明于 L124、写入于 L136，**此后全文再无读取**（已 grep 确认）。

---

## 2. 实测证据

### 2.1 `biz_service.category_id` 全 NULL

```sql
SELECT COUNT(*) total, SUM(category_id IS NULL) cat_null, COUNT(DISTINCT service_code) codes
FROM biz_service;
-- total=10  cat_null=10  codes=5
```

即 **10/10 行 `category_id IS NULL`**，且表内只有 5 个 `service_code`（每个 code 重复 2 行，见 I140 §2.3）。

### 2.2 修复后的历史查询返回的 `category_id` 仍为 NULL

I140 修复后（血缘改到 `biz_service`）的实测 SQL：

```sql
SELECT s.category_id, MAX(o.created_at) AS last_at
FROM pos_order_item i
JOIN pos_order o ON i.order_id = o.id
JOIN biz_service s ON i.service_id = s.id
WHERE o.member_id = 1 AND o.deleted = 0 AND i.deleted = 0
GROUP BY s.category_id ORDER BY last_at DESC LIMIT 50;
```

结果：`category_id = NULL, last_at = 2026-10-30 00:47:23`（1 行）。

即：**SQL 不再报 ERROR 3065，但取回的类别仍然是 NULL** → `memberCategories` 恒为空集。

### 2.3 `biz_service_item` 侧有 category 数据，但那是另一套数据集

`biz_service_item`（32 行有效 26 / 存活，32 个 code）**有 `category_id`**，
但它是 I140 已证实的**另一套服务目录**（`SV001`~`SV022`），与 `biz_service`（`SV0001`~`SV0005`）不可互换。

---

## 3. 影响面

| 受影响功能 | 说明 |
|---|---|
| AI 推荐"会员最近消费类别" | `memberCategories` 恒空 → 若后续要按类别加权，当前**无任何类别信号**可用 |
| `memberCategories` 本身 | 方法内无读取点 → 当前是**死代码**，对最终推荐结果**无实际影响**（推荐结果仅由"热销榜"驱动） |

> ⚠️ 因此本 issue **不是**"推荐结果错误"的成因（那是 I140），而是"类别维度能力缺失 + 死代码"。
> 修 I140 后 `/ai/recommend` 已能返回真实推荐（见 I140 §7）。

---

## 4. 修复建议（未实施）

1. **补数据**：为 `biz_service` 5 个 `service_code` 补 `category_id`（需先确认服务分类权威表 —— 疑似 `biz_service_item.category_id` 或独立分类表，**本次未取证，不猜测**）。
2. **接上死代码**：确认 `memberCategories` 的预期用途（是否应对 `hot` 榜单做类别加成 —— L158 注释写的是"类别加成"，但代码未实现），再决定：
   - 要么实现类别加权（`score` 计算里用上 `memberCategories`）；
   - 要么删除 `memberCategories` 及其历史查询（消除死代码 + 省一次 DB 查询）。
3. **建议先做 2 的"删除"分支**：在业务确认类别加权是否需要之前，删除死代码比保留空集合更诚实。

> ⚠️ 本 issue **未修改任何业务代码**，状态保持 `UNVERIFIED`。
> 删除死代码会移除一次 DB 查询、改变 `AiEnhanceService` 内部结构，需单独批次 + 运行证据。

---

## 5. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 取证命令 | `mysql --default-character-set=utf8mb4`（只读 SELECT） |
| 取证时间 | 2026-11-07 |
| 证据文件 | `audit/evidence/F145-i140-ai-enhance-fix/after-02-sql-verification.txt` §[C] §[D] |
| commit hash | 待补（本 issue 未提交修复） |
