# F147 — I143 证据：AI 推荐缓存命中路径返回空数组

> **状态**：UNVERIFIED（**缺陷已取证，尚未修复**）
> **批次**：I140 修复批次顺带发现（2026-11-08）
> **关联 issue**：`audit/issues/I143-ai-recommend-cache-parse-stub.md`

## 1. 缺陷

`backend/src/main/java/com/meiyetec/service/AiEnhanceService.java:434-437`

```java
private static List<Map<String, Object>> parseJsonList(String json) {
    // 简化：直接当 JSON 列表返回（生产应用 Jackson）
    return List.of();
}
```

调用点 L81 位于缓存命中分支 → **缓存命中时把已持久化的推荐结果丢弃成空数组**。
同一文件 L439 的 `toJson` 却实现了完整序列化 → **写得出、读不回**。

## 2. 证据

| 文件 | 内容 |
|---|---|
| `01-curl-cache-hit-path.txt` | 连续两次 `GET /ai/recommend?memberId=1` 的完整响应 + 前后 `ai_recommend_cache` 行状态 |

关键事实：

- 两次调用均返回 `cacheHit=true` 且 `items: []`
- `ai_recommend_cache.hit_count` 由 3 → 5 → **确实走了缓存分支**
- 同一行 `recommend_items` 存的是**完整合法 JSON 数组**（含中文 `基础面部护理` / `美睫嫁接` …）
- 对照冷路径（缓存不存在/已过期）返回 **5 条**真实推荐 —— 见 `F145-i140-ai-enhance-fix/`

## 3. 结论

**HTTP 200 + 业务无效**（假成功形态）。缓存 6 小时 TTL 内命中率 100%，
但有效利用率 0%：第二次起恒返回空推荐。

修复方案见 I143 §5（实现真正的 JSON 解析 + 冷热路径内容一致性复验）。
本批**未修改任何业务代码**，状态保持 UNVERIFIED。
