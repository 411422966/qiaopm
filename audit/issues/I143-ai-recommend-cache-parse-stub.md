# I143 — AI 推荐缓存反序列化为桩代码，命中缓存返回空数组

> **状态**：Open
> **严重度**：P2 — 第二次请求起永远返回空（不报错，属"假成功"形态）
> **发现日期**：2026-11-08（I140 修复批次顺带发现）
> **登记人**：Cursor（I140 修复批次）
> **修复状态**：**UNVERIFIED**（本文件仅登记缺陷，未修改业务代码）
> **关联**：I140（`AiEnhanceService` 数据血缘漂移，已修复并 VERIFIED）

---

## 0. 一句话描述

`AiEnhanceService.parseJsonList(String json)` 是**桩代码**（`return List.of();`），
导致缓存命中分支（`cacheHit = true`）把已持久化的推荐结果**丢弃成空数组** ——
即**同一个接口，第一次请求有数据，第二次起恒为空**。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 文件 | `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java` |
| 类 | `com.meiyetec.service.AiEnhanceService` |
| 方法 | `parseJsonList(String json)`（private static） |
| 缺陷行 | **L434-437**（桩代码实现） |
| 调用点 | **L81**（缓存命中分支 `data.put("items", parseJsonList(cached.getRecommendItems()))`） |
| 数据表 | `ai_recommend_cache.recommend_items`（JSON 文本列，**数据本身是完整的**） |
| 关联接口 | `GET /ai/recommend`（`AiEnhanceController`） |

```434:437:backend/src/main/java/com/meiyetec/service/AiEnhanceService.java
    private static List<Map<String, Object>> parseJsonList(String json) {
        // 简化：直接当 JSON 列表返回（生产应用 Jackson）
        return List.of();
    }
```

---

## 2. 缺陷链路

```
GET /ai/recommend?memberId=1
  └─ AiEnhanceService.recommend(...)
       ├─ [命中缓存] L76-86
       │    ├─ UPDATE ai_recommend_cache SET hit_count=hit_count+1 ...   ← 命中计数照常累加
       │    ├─ data.put("items", parseJsonList(cached.getRecommendItems()))  ← L81 丢数据
       │    └─ return Result.success(data)   ← HTTP 200 + cacheHit=true + items=[]
       └─ [未命中] L89-117
            ├─ items = computeRecommendations(...)   ← 真实计算，5 条
            ├─ itemsJson = toJson(items)             ← 手写 JSON 序列化（能写）
            └─ return ... cacheHit=false + items=5 条
```

**写得出、读不回**：同一文件里 `toJson(List)`（L439-459）实现了完整的手写序列化，
而对称的 `parseJsonList` 却是空实现 —— 写入路径正常，读取路径丢弃。

---

## 3. 实测证据

### 3.1 库中缓存内容是完整的

```sql
SELECT id, member_id, recommend_type, hit_count, expires_at, LEFT(recommend_items, 80)
FROM ai_recommend_cache;
```

```
2	2	SERVICE	1	2026-11-08 05:14:25	[{"id":1,"name":"基础面部护理","price":280.00,"score":0.04,"reason":"热销推荐"},{"id":5,"n
3	1	SERVICE	3	2026-11-08 05:14:47	[{"id":1,"name":"基础面部护理","price":280.00,"score":0.04,"reason":"热销推荐"},{"id":5,"n
```

→ `recommend_items` 存的是**合法且完整的 JSON 数组**，不是脏数据。

### 3.2 连续两次调用，第二次起恒为空

```
GET /ai/recommend?memberId=1   (Authorization: Bearer <token>)
```

| 次序 | 响应 |
|---|---|
| call #1 | `{"code":200,"message":"操作成功","data":{"items":[],"algorithm":"COLLAB-V1","score":0.8500,"cacheHit":true},...}` |
| call #2 | `{"code":200,"message":"操作成功","data":{"items":[],"algorithm":"COLLAB-V1","score":0.8500,"cacheHit":true},...}` |

两次调用后 `hit_count` 由 3 → 5（**确实走了缓存分支**），
但 `items` 恒为 `[]`。

### 3.3 与冷路径对照

| 路径 | 触发条件 | 返回 items |
|---|---|---|
| 冷路径（真实计算） | 缓存不存在 / 已过期 | **5 条**（I140 修复后实测：`基础面部护理` / `美睫嫁接` / `肩颈舒缓按摩` / `全身精油SPA` / `深层补水护理`） |
| 热路径（缓存命中） | 缓存未过期（6h TTL） | **0 条**（本 issue） |

---

## 4. 影响面

| 受影响功能 | 说明 |
|---|---|
| `GET /ai/recommend` | **第二次起恒返回空推荐**，用户表现为"推荐功能用着用着就空了" |
| 缓存机制本身 | 缓存**只写不读**，6 小时 TTL 内命中率 100% 但有效利用率 0% |
| 副作用 | `hit_count` / `last_hit_at` 照常累加，**统计上显示"缓存很热"，实际返回空** —— 属于假成功形态（HTTP 200 + 业务无效） |

> ⚠️ 本 issue **独立于 I140**：I140 修的是冷路径的血缘漂移（SQL 层面），
> I143 是热路径的反序列化缺失（Java 层面）。两者互不覆盖。

---

## 5. 修复建议（未实施）

1. **实现真正的 JSON 解析**，`items` schema 为：

   ```json
   [{"id":1,"name":"基础面部护理","price":280.00,"score":0.04,"reason":"热销推荐"}]
   ```

   - 字段类型：`id` → Number / `Long`；`name` → String；`price` → Number（`BigDecimal` 或 `Double`）；`score` → Number；`reason` → String
   - `price` / `score` 在 `toJson` 中是**无引号数字**，解析时需转成数值类型，不能当字符串
   - 建议用项目既有的 Jackson `ObjectMapper`：`mapper.readValue(json, new TypeReference<List<Map<String,Object>>>() {})`
     （该类 L439 已引入 Jackson 依赖的同类手写风格，可直接复用同一 `ObjectMapper` 实例）
   - **解析失败兜底**：JSON 非法时应回落到"重新计算"而非返回空数组，否则等于把"空数组"换成"500"
2. **补一次冷热路径对照验证**：修完后需实测 `cacheHit=false` 与 `cacheHit=true` 两次响应的 `items` **内容一致**（不只是数量一致），才算 VERIFIED。
3. **顺带评估**：`toJson` 的手写序列化不转义引号/反斜杠，`reason` 中若出现 `"` 或 `\` 会产出非法 JSON。
   本 issue 不要求改动它，但解析侧实现后建议一并评估是否改用 Jackson 序列化。

> ⚠️ 本 issue **未修改任何业务代码**，状态保持 `UNVERIFIED`。
> 修改 `AiEnhanceService` 需单独批次 + 运行证据（curl 冷热对照）。

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 应用 | `http://localhost:8080/ai/recommend`（容器 `meiyetec-api`） |
| 取证命令 | `Invoke-WebRequest` + `Authorization: Bearer <token>`；DB 只读 SELECT |
| 取证时间 | 2026-11-08 00:32 |
| 证据文件 | `audit/evidence/F147-i143-ai-recommend-cache-stub/` |
| commit hash | 待补（本 issue 未提交修复） |
