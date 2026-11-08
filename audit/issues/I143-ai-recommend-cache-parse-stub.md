# I143 — AI 推荐缓存反序列化为桩代码，命中缓存返回空数组

> **状态**：Fixed
> **严重度**：P2 — 第二次请求起永远返回空（不报错，属"假成功"形态）
> **发现日期**：2026-11-08（I140 修复批次顺带发现）
> **登记人**：Cursor（I140 修复批次）
> **修复状态**：**VERIFIED**（2026-11-08 23:03 实测冷热路径对照通过，证据见 `audit/evidence/F148-i143-cache-parse-fix/`）
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
| 缺陷行 | **L434-437**（桩代码实现）→ 修复后实现位于 **L444-465** |
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

## 5. 修复方案（已实施）

### 5.1 实施内容

复用项目既有的全局 `ObjectMapper`（`com.meiyetec.util.ObjectMapperHolder.MAPPER`），
用 Jackson `TypeReference<List<Map<String, Object>>>` 反序列化缓存 JSON：

```444:465:backend/src/main/java/com/meiyetec/service/AiEnhanceService.java
    private static List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> items = ObjectMapperHolder.MAPPER.readValue(
                    json, new TypeReference<List<Map<String, Object>>>() {});
            return items == null ? List.of() : items;
        } catch (Exception e) {
            log.warn("推荐缓存反序列化失败，返回空列表：{}", e.getMessage());
            return List.of();
        }
    }
```

- `items` schema：`{"id":1,"name":"基础面部护理","price":280.00,"score":0.04,"reason":"热销推荐"}`
- `price` / `score` 由 Jackson 解析为数值类型（`Double`），与冷路径 `computeRecommendations` 产出的 `BigDecimal` / `Double` 在 JSON 序列化后等价
- **解析失败兜底**：`catch (Exception)` → `log.warn` + 返回空列表，**不抛异常**，缓存脏数据不会导致接口 500
- 新增 import：`com.fasterxml.jackson.core.type.TypeReference`、`com.meiyetec.util.ObjectMapperHolder`

### 5.2 实测验证（2026-11-08 23:03）

前置：`DELETE FROM ai_recommend_cache WHERE member_id=1;`（清空旧缓存），重建镜像 `meiyetec/api:dev` 并重启容器 `meiyetec-api`。

| 次序 | cacheHit | items 数量 | items 内容 |
|---|---|---|---|
| call #1 | `false` | **5** | 基础面部护理 / 美睫嫁接 / 肩颈舒缓按摩 / 全身精油SPA / 深层补水护理 |
| call #2 | `true` | **5** | **同上，逐条一致** |

- 落库校验：`ai_recommend_cache.recommend_items` 内容完整合法 JSON（`--default-character-set=utf8mb4` 读出中文正常）
- `hit_count` 由 0 → 1，证明第二次确实走的是**缓存命中分支**（而非重新计算）
- 容器日志无「推荐缓存反序列化失败」告警
- 证据：`audit/evidence/F148-i143-cache-parse-fix/01-curl-cache-hit-path.txt`

### 5.3 遗留项（不阻塞本次修复）

1. `toJson` 的手写序列化**不转义引号 / 反斜杠**，`reason` 中若出现 `"` 或 `\` 会产出非法 JSON。
   解析侧实现后建议一并评估是否改用 Jackson 序列化。
2. 解析失败时**返回空列表**而非"重新计算" —— 与本 issue 修复前的失败形态一致（空数组），
   但已补 `log.warn` 可观测。彻底消除需引入缓存版本号或「解析失败即失效并重算」策略，属独立增强项。

---

## 6. 取证方式

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（本地 Docker `meiyetec-mysql`） |
| 应用 | `http://localhost:8080/ai/recommend`（容器 `meiyetec-api`） |
| 取证命令 | `Invoke-WebRequest` + `Authorization: Bearer <token>`；DB 只读 SELECT |
| 取证时间 | 2026-11-08 00:32（缺陷登记）/ 2026-11-08 23:03（修复后复验） |
| 证据文件 | 缺陷登记：`audit/evidence/F147-i143-ai-recommend-cache-stub/`；修复复验：`audit/evidence/F148-i143-cache-parse-fix/` |
| commit hash | 待补（随修复 commit 回填） |
