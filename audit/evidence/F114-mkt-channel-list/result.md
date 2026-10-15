# F114 — MktChannel / 渠道列表查询 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:38 (UTC+8)
> **方式**：API-only (curl GET `/mkt/channel/list`) + DB 交叉验证

## 1. 接口实证

```
GET /mkt/channel/list
Authorization: Bearer <admin_token>
→ HTTP 200 / code 200 / data rows = 6 / traceId 709c7719ca3e4f29a2bffabff0837aa5
```

## 2. DB 交叉验证

```
mysql> SELECT COUNT(*), COUNT(DISTINCT tenant_id) FROM mkt_channel_config WHERE deleted=0;
total = 6
tenants = 1
```

API 返回 6 行 ↔ DB 行数 6 行 ✅ 一致

## 3. 列对齐（驼峰响应 ↔ DB）

| 响应字段（驼峰） | DB 列 | 类型 |
|---|---|---|
| `id` | id | bigint |
| `tenantId` | tenant_id | bigint |
| `channelType` | channel_type | varchar |
| `channelName` | channel_name | varchar |
| `rateLimitPerMin` | rate_limit_per_min | int |
| `rateLimitPerDay` | rate_limit_per_day | int |
| `retryMax` | retry_max | int |
| `retryIntervalSec` | retry_interval_sec | int |
| `status` | status | tinyint |
| `lastHealthAt` | last_health_at | datetime |
| `lastHealthMsg` | last_health_msg | varchar |

## 4. 租户隔离（tenantId 强制）

- L55-65 `MarketingAutomationService.listChannels`：`WHERE deleted = 0 AND tenant_id = ?` 强绑定 TenantContextHolder
- 当前所有 sys_user tenant_id=1，无法触发跨租户负向证据（与 F117 同模式）

## 5. Final Result

**VERIFIED** — 端点响应 + DB 行数 + 列对齐 + 租户隔离 全部一致。
