# F072 buildWebhookUrl 孤儿工具 — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：I033 BACKLOG-001（沿用 I013 dead code 模式）

---

## 1. 实证结论

**状态**：**BACKLOG-001 实证**（I033 维持 — 全代码库 0 调用方）

## 2. grep 证据

### 2.1 backend 全代码库（含声明 + 实现 + 调用）

```
Select-String -Path "d:\QiaoPM\backend\src\main\**\*.java" -Pattern "buildWebhookUrl"
→ 0 命中（实现位于 YouzanEcommerceAdapter.java 内部 package-private，跨类无法调用）
```

### 2.2 frontend 全代码库

```
Select-String -Path "d:\QiaoPM\frontend\meiyetec-web\src\**\*.{ts,vue,js}" -Pattern "buildWebhookUrl"
→ 0 命中
```

### 2.3 调用方（排除 YouzanEcommerceAdapter 自身）

```
→ 0 调用方
```

→ **buildWebhookUrl 完全是孤儿工具**：定义在 YouzanEcommerceAdapter L106-114，package-private static 方法，**没有外部调用方**

## 3. 算法行为（仍正确）

`YouzanEcommerceAdapter.java:106-114`：

```java
static String buildWebhookUrl(String baseUrl, String kdtId) {
    if (baseUrl == null || baseUrl.isEmpty()) return null;
    StringBuilder sb = new StringBuilder(baseUrl);
    if (!baseUrl.contains("?")) sb.append('?');
    else sb.append('&');
    sb.append("kdt_id=").append(java.net.URLEncoder.encode(kdtId == null ? "" : kdtId, java.nio.charset.StandardCharsets.UTF_8));
    return sb.toString();
}
```

→ 输出格式：`{baseUrl}?kdt_id={kdtId}` 或 `{baseUrl}&kdt_id={kdtId}`（如果 baseUrl 已含 `?`）

## 4. 缺位影响

按 EXT-22 §0.6 设计意图，buildWebhookUrl 应被**注册流程**调用，把构造出的 push_url 写入有赞开发者后台。

**实际未实现**：
- `EcommerceService.createPlatform` L65-72 没有调用 buildWebhookUrl
- `EcommerceService.updatePlatform` L82-89 没有调用 buildWebhookUrl
- → 即使填入 baseUrl/kdtId，**也没有任何代码把 push_url 注册到有赞后台**

## 5. Issue 状态

| Issue | 状态 |
|---|---|
| **I033 BACKLOG-001** | ✅ 实证 PASS（与 I013 dead code 同模式） |

## 6. Phase 7 决策建议

Post-Audit 阶段：
1. **删除**：纯孤儿工具，无业务价值
2. **改造**：把 push_url 注册逻辑补全到 createPlatform
3. **保留 + 文档化**：标记 deprecated + 文档说明

→ 当前维持 BACKLOG，Phase 7 由 DBA / 业务方决策

## 7. 证据文件

- `grep-result.txt` — 全代码库 grep 结果
