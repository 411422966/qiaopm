# F115 — MktChannel 创建渠道配置 — Runtime Evidence（含 I062 / N7 实证）

> Feature ID: F115
> Module: 营销渠道 / 创建
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:31 (UTC+8)
> SoT: `MktChannelController.java:46-49` + `MarketingAutomationService.java:67-83` (line 79 空语句)
> 验证方式: API + DB

---

## 1. 端点

```
POST /mkt/channel
```

## 2. 正确 body 字段

- 必填: `channelType` + `channelName`
- 可选: `rateLimitPerMin` / `rateLimitPerDay` / `status` / `retryMax` / `retryIntervalSec` / `brandId` / `configJson` / `provider` / `signature` / `templatePrefix`

注意：plan §3.1 F115 描述用的 `name` 字段**不存在**（实体只有 `channelName`），文档漂移但**不影响**实际调用（Jackson 忽略未知字段）。

## 3. N7 / I062 实证 — rateLimitPerDay setter 缺失

**调用**:
```bash
POST /mkt/channel
body: {"channelType":"PUSH","channelName":"5A-F115-N7-test","rateLimitPerMin":120,"retryMax":5,"retryIntervalSec":90}
```

**响应**:
```json
{
  "code": 200, "message": "已创建",
  "data": {
    "id": 11, "tenantId": 1,
    "channelType": "PUSH",
    "channelName": "5A-F115-N7-test",
    "rateLimitPerMin": 120,
    "rateLimitPerDay": null,        ← **I062 实证：响应 rateLimitPerDay 为 null**
    "retryMax": 5, "retryIntervalSec": 90,
    "status": 1, "createTime": "2026-09-16T02:31:49"
  },
  "traceId": "09769c1003a74e78bde68bd97fca9678"
}
```

**代码层根因**（`MarketingAutomationService.java:78-79`）:
```java
if (c.getRateLimitPerMin() == null) c.setRateLimitPerMin(60);
if (c.getRateLimitPerDay() == null) c.getRateLimitPerDay();   // ← **空语句**（line 79）
```

期望: `c.setRateLimitPerDay(5000);`
实际: `c.getRateLimitPerDay();` — 无副作用的 getter 调用

## 4. DB 断言

F116 update id=11 后：
```sql
SELECT id, channel_name, rate_limit_per_min, rate_limit_per_day, deleted
FROM mkt_channel_config WHERE id=11;
```
→ `rate_limit_per_min=200, rate_limit_per_day=5000, deleted=0`

**`rate_limit_per_day=5000` 来自 MySQL 列 DEFAULT 兜底**（响应当时是 null，DB 后来 F116 update 走 updateById 也没显式设 rate_limit_per_day，DB DEFAULT 兜底）。

| 维度 | 期望 | 实际 |
|---|---|---|
| 响应 rateLimitPerDay | 默认 5000 | **null**（I062 setter 缺失） |
| DB rate_limit_per_day | 5000（默认） | 5000（DB DEFAULT 兜底） |
| 业务影响 | 设置生效 | **接口与 DB 字段不一致**（响应 null vs DB 5000） |

## 5. 期望 vs 实际

| 维度 | 结果 |
|---|---|
| HTTP 200 | ✅ |
| 写库成功 | ✅ id=11 |
| 跨租户防护 | ✅ `c.setTenantId(tenantId)` 强制 |
| 默认值生效 | ⚠ rateLimitPerMin=60 ✅ / rateLimitPerDay=null ⚠ |

## 6. 结论

**Final Result**: **PARTIAL-NON-BLOCKER** — I062 setter 缺失 + DB DEFAULT 兜底救场；代码层 setter 真缺失。

Post-Audit 修复建议：把 `c.getRateLimitPerDay();` 改为 `c.setRateLimitPerDay(5000);`

证据落档：traceId 09769c1003a74e78bde68bd97fca9678 (create) + bc61a816d67b4820a052096093cf482a (update)。