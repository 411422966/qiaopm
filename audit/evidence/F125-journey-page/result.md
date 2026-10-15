# F125 — MktJourney / 旅程分页 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:48 (UTC+8)
> **方式**：API + 源码（N13 I058 实证）

## 1. 接口实证

### POST /mkt/journey/page

```
POST /mkt/journey/page
Body: {"pageNum":1,"pageSize":10}
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 24fa90f303ab4b168e41cea3502c9edf
```

### GET /mkt/journey/page

```
GET /mkt/journey/page?pageNum=1&pageSize=10
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 8ef64519eb52420d8df952e066b43b3d
```

## 2. 失败根因（源码层）

`MarketingAutomationService.java:467-496`

```java
List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
...
Long total = jdbc.queryForObject(csql.toString(), Long.class, args.toArray());  // ← args 共享
```

- L487-489: `args` 在 queryForList + queryForObject 之间共享，但 COUNT SQL 只需要第一个占位符 (tenantId)
- queryForObject 会按 `?` 数量绑定 args（可能 pageSize + offset 多余参数引发 SQLException）
- 同样模式影响 MktTouchTaskController（MktTouchTask 8 端点）— 但 F129 page 端点正常（已 200）→ 服务实现差异

## 3. N13 I058 实证 — POST/GET 双路由形式

**Controller**：
- L43 `@PostMapping("/page")` — body 接收参数
- L53 `@GetMapping("/page")` — query 接收参数

**两种调用都进入 handler 但都 500**（同样的 queryForMap/Object bug）

## 4. Final Result

**PARTIAL-NON-BLOCKER** — 端点存在 + 双形式路由 (N13 I058) 但**分页查询 500 失败**
- 与 F120 ROI queryForMap 空数据崩同模式
- 服务代码层 bug（queryForObject args 共享）
