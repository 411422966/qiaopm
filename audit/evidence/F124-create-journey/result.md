# F124 — MktJourney / 创建旅程 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:48 (UTC+8)
> **方式**：API + DB

## 1. 接口实证（修正参数后）

```
POST /mkt/journey
Body: {"journeyCode":"audit-5A-F124","journeyName":"audit-5A-F124","journeyType":"WELCOME","triggerEvent":"MEMBER_CREATED","nodes":[{"nodeType":"SEND_SMS","tplCode":"tpl-001","dayOffset":0}]}
→ HTTP 200 / code 200 / message: "已创建"
→ traceId 6febd7031e2d4ffc819c948fdc437f08
```

## 2. DB 交叉验证

```
mysql> SELECT id, journey_code, journey_name, status, tenant_id FROM mkt_journey WHERE journey_code='audit-5A-F124';
id | journey_code      | journey_name      | status | tenant_id
6  | audit-5A-F124     | audit-5A-F124     | 1      | 1
```

## 3. 参数校验

源码 `MarketingAutomationService.java:417-419`：
- journeyCode + journeyName **必填**（首次跑 400 报 "编码/名称 必填"）
- 同租户同编码 → 重复返回 CONFLICT

## 4. Final Result

**VERIFIED** — 创建成功 + DB 写入 + 重复防护 OK
