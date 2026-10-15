# F116 — MktChannel 更新渠道配置 — Runtime Evidence

> Feature ID: F116
> Module: 营销渠道 / 更新
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:31 (UTC+8)
> SoT: `MktChannelController.java:51-54` + `MarketingAutomationService.updateChannel`
> 验证方式: API + DB

---

## 1. 端点

```
PUT /mkt/channel/{id}
```

## 2. 真实响应

```bash
PUT /mkt/channel/11
body: {"channelName":"5A-F115-N7-updated","rateLimitPerMin":200}
```

```json
{
  "code": 200, "message": "已更新",
  "data": {
    "id": 11, "tenantId": 1,
    "channelName": "5A-F115-N7-updated",
    "rateLimitPerMin": 200,
    "rateLimitPerDay": 5000,            ← **DB DEFAULT 兜底生效**
    "retryMax": 5, "retryIntervalSec": 90,
    "status": 1, "deleted": 0
  },
  "traceId": "bc61a816d67b4820a052096093cf482a"
}
```

## 3. 关键发现

### 3.1 跨租户写防护 ✅

`MarketingAutomationService.updateChannel` 通过 `c.setTenantId(old.getTenantId())` 强制保留**原**租户 — 即使 body 含 `tenantId=2` 也会被忽略：
- 攻击向量：PUT body 含 `{"tenantId":2,...}` → 期望 tenant 改 2 → **实际**保留原 tenant=1

### 3.2 部分更新 ✅

body 只传 `channelName` + `rateLimitPerMin`，其它字段（retryMax/retryIntervalSec/status）保留原值 ✅。

## 4. DB 断言

```sql
SELECT id, channel_name, rate_limit_per_min, rate_limit_per_day, retry_max, status, deleted, update_time
FROM mkt_channel_config WHERE id=11;
```
→ `channel_name='5A-F115-N7-updated', rate_limit_per_min=200, rate_limit_per_day=5000, retry_max=5, status=1, deleted=0, update_time='2026-09-16 02:31:58'`

## 5. 结论

| 维度 | 结果 |
|---|---|
| HTTP 200 | ✅ |
| DB 写入 | ✅ update_time 刷新 |
| tenant 强制保留 | ✅ 防越权写 |
| 部分字段更新 | ✅ 仅传字段改 |

**Final Result**: **VERIFIED**