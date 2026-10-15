# F047 — Plugin Marketplace 评价（×2 端点 + I019 跨租户读风险 实证）

> **验证时间**：2026-09-13 20:46 (UTC+8)
> **Phase**：5B — API + DB 实证 + I019 风险实证
> **验证方式**：clerk01 写评价 → SQL tamper tenant_id=2 → clerk01 仍读到
> **最终状态**：**PARTIAL-NON-BLOCKER**（CRUD + **I019 跨租户读风险实证** + tenantId null BUG）

---

## 一、2 端点实证

### 1.1 实证结果（来自 `probe-5b-f047.ps1`）

| # | 端点 | body / 操作 | 响应 | DB |
|---|---|---|---|---|
| 1 | `POST /plugin/review` (clerk01) | `{pluginId:21, rating:5, title:"probe review", comment:"..."}` | **200** `{id:10, tenantId:NULL, pluginId:21, memberId:0, rating:5, ...}` | INSERT plugin_review (10, **NULL**, 21, 0, 5, ...) |
| 2 | `GET /plugin/review/list/21` (clerk01) | — | **200** `[{id:10, tenantId:NULL, pluginId:21, ...}]` | SELECT WHERE plugin_id=21 |
| 3 | SQL tamper | `UPDATE plugin_review SET tenant_id=2 WHERE plugin_id=21` | — | UPDATE → tenant_id=2 |
| 4 | `GET /plugin/review/list/21` (clerk01 tenant=1) | — | **200** `[{id:10, **tenantId:2**, pluginId:21, ...}]` ⚠️ | **仍返回** tenant=2 行 |

### 1.2 关键发现

#### BUG 1：`POST /plugin/review` 没写 tenantId

`PluginService.review`（行 113-121）：
```java
public Result<PluginReview> review(@RequestBody Map<String, Object> body) {
    PluginReview r = new PluginReview();
    r.setPluginId(...);
    r.setRating(...);
    r.setContent(...);
    r.setMemberId(0L);
    r.setTitle(...);
    // ❌ 没调 setTenantId(TenantContextHolder.get().getTenantId())
    reviewMapper.insert(r);
    return Result.success("已评价", r);
}
```

**问题**：tenantId=null → DB 行的 tenant_id 字段为 NULL（实测确认）。

#### BUG 2：I019 跨租户读风险 实证

`PluginService.listReviews`（行 123-130）：
```java
public Result<List<PluginReview>> listReviews(@PathVariable Long pluginId) {
    return Result.success(reviewMapper.selectList(new LambdaQueryWrapper<PluginReview>()
            .eq(PluginReview::getPluginId, pluginId)  // ❌ 没过滤 tenantId
            .orderByDesc(PluginReview::getId)));
}
```

**实测实证**：
- clerk01 (tenant=1) 写评价 → DB tenant_id=NULL（BUG 1）
- SQL tamper 改 tenant_id=2（模拟跨租户数据）
- clerk01 (tenant=1) GET → **仍返回** tenant_id=2 行 → **I019 跨租户读风险实证确认**

## 二、I019 跨租户读风险分析

### 2.1 风险路径

```
tenantA 写评价 → DB plugin_review.tenant_id=A
tenantB 调 GET /plugin/review/list/{pluginId}
  → SELECT WHERE plugin_id=X （不过滤 tenantId）
  → 返回所有 tenant 的评价
```

### 2.2 影响

- 评价内容（comment/rating）通常不敏感，但**仍属跨租户数据访问**
- 不符合"租户数据隔离"基本要求
- 建议加 `.eq(PluginReview::getTenantId, currentTenantId)` 过滤

### 2.3 修复建议（Post-Audit）

```java
public Result<List<PluginReview>> listReviews(@PathVariable Long pluginId) {
    Long currentTenantId = TenantContextHolder.get().getTenantId();
    return Result.success(reviewMapper.selectList(new LambdaQueryWrapper<PluginReview>()
            .eq(PluginReview::getPluginId, pluginId)
            .eq(PluginReview::getTenantId, currentTenantId)  // 加 tenant 过滤
            .orderByDesc(PluginReview::getId)));
}
```

## 三、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| review 写入 | ⚠️ PARTIAL | 写入成功但 tenantId=null（BUG）|
| review 列表 | ⚠️ PARTIAL | 不分页、不限 tenant（I019 风险）|
| I019 跨租户读风险 | ✅ 实证确认 | tamper tenant_id=2 后仍能读到 |
| I019 命名异味 | ✅ 静态识别 | PluginService 直接是 @RestController |
| BUG: review 没写 tenantId | ✅ 实证确认 | review.tenantId=null |

### 最终状态

**F047 = PARTIAL-NON-BLOCKER**（CRUD 正确 + I019 跨租户读风险实证 + review tenantId null BUG）

---

## 四、Evidence 文件

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-f047.ps1` |
| Service | `backend/src/main/java/com/meiyetec/service/PluginService.java:112-130` |

---

**F047 CRUD 行为正确。I019 跨租户读风险实证确认。**
