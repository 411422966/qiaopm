# F125 — MktJourney 分页查询（POST/GET 双形式）— Runtime Evidence（❌ BROKEN + I058 / N13 实证）

> Feature ID: F125
> Module: 营销旅程 / 分页（双形式）
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:32 (UTC+8)
> SoT: `MktJourneyController.java:37-60` + `MarketingAutomationService.java:492 journeyPage`
> 验证方式: API-only

---

## 1. 端点（双形式）

```
POST /mkt/journey/page    # body: {pageNum,pageSize,status,journeyType,keyword}
GET  /mkt/journey/page    # query: pageNum,pageSize,status,journeyType,keyword
```

## 2. ❌ 双形式都 500 — `Parameter index out of range`

```bash
$ admin GET /mkt/journey/page?pageNum=1&pageSize=5
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"99680c5151844fd0b02ac537c674200a"}
```

## 3. 根因 — `journeyPage` COUNT SQL 复用 args

**Caused by**: `org.springframework.dao.TransientDataAccessResourceException: Parameter index out of range (2 > number of parameters, which is 1)`

**SQL**（`MarketingAutomationService.java:489-507`）:
```java
// COUNT SQL — 只用 1 个 tenant_id 占位符
StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mkt_journey WHERE deleted = 0 AND tenant_id = ? ");
Long total = jdbc.queryForObject(csql.toString(), Long.class, args.toArray());
// args.toArray() — 包含 status / journeyType / keyword / pageSize / offset 等占位符值
// 即使无过滤参数也包含 pageSize / offset → "Parameter index out of range"
```

## 4. N13 / I058 实证 — POST/GET 双形式

`MktJourneyController.java:37-60` 同时定义 `@PostMapping("/page")` + `@GetMapping("/page")` 都委派到 `journeyPage`：
- POST body + GET query 两种调用形式**都返回 500**
- 同一 bug 影响双形式（不是 form-specific）

## 5. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| HTTP 200 (POST) | ✅ | ❌ 500 |
| HTTP 200 (GET) | ✅ | ❌ 500 |
| 总数 + 记录 | ✅ | ❌ SQL 错 |
| 双形式共存 | ⚠ I058 风格不一致 | **N13/I058 实证**（双形式同时崩）|

## 6. 结论

**Final Result**: **BROKEN + I058 实证** — 分页端点彻底不可用（无论 POST/GET）。

Post-Audit 修复：
```java
// 修正：COUNT 用独立 args 数组（仅含 tenant_id + 已启用的过滤条件）
StringBuilder csql = new StringBuilder("SELECT COUNT(*) FROM mkt_journey WHERE deleted = 0 AND tenant_id = ? ");
List<Object> cArgs = new ArrayList<>(); cArgs.add(tenantId);
if (status != null) { csql.append("AND status = ? "); cArgs.add(status); }
// ... 但 pageSize / offset **不应**进入 COUNT
Long total = jdbc.queryForObject(csql.toString(), Long.class, cArgs.toArray());
```

证据落档：traceId 99680c5151844fd0b02ac537c674200a (GET)。

注: F124/F126/F127 不依赖 page 端点，所以**业务层仍可工作**（创建→发布→暂停→进入→推进）— page 端点是独立的 read-only 端点。