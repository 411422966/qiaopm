# F115 — MktChannel / 创建渠道配置 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:42 (UTC+8)
> **方式**：API + DB 验证 + 静态源码分析（N7 实证）

## 1. 接口实证

```
POST /mkt/channel
Body: {"channelName":"audit-5A-F115-N7","channelType":"PUSH","rateLimitPerMin":60,"provider":"audit"}
→ HTTP 200 / code 200 / data = {"id":12,"channelName":"audit-5A-F115-N7","channelType":"PUSH",...}
→ traceId b3f91db9675a4888bf4c9df9a8b79f86
```

## 2. DB 交叉验证

```
mysql> SELECT id, channel_name, channel_type, rate_limit_per_min, rate_limit_per_day, retry_max, retry_interval_sec, status, deleted, tenant_id FROM mkt_channel_config WHERE channel_name='audit-5A-F115-N7';

id | channel_name           | channel_type | rate_limit_per_min | rate_limit_per_day | retry_max | retry_interval_sec | status | deleted | tenant_id
12 | audit-5A-F115-N7      | PUSH         | 60                 | 5000               | 3         | 60                 | 1      | 0       | 1
```

## 3. N7 实证 — I062 setter 缺失（空语句）

**源码位置**：`MarketingAutomationService.java:80`

```java
if (c.getRateLimitPerDay() == null) c.getRateLimitPerDay();   // ← 空语句：getter 不存
```

- 字段未通过 setter 显式赋值（getter 调用结果丢弃）
- DB 显示 `rate_limit_per_day=5000`（**DDL DEFAULT 触发**，非代码赋值）
- 功能性影响：0（DDL 兜底）
- **Part I 决策**：登记，不修（Post-Audit 统一整改）

## 4. 默认值一致性

| 字段 | Service 默认值 | DDL DEFAULT | 实证 |
|---|---|---|---|
| status | 1 | 1 | ✅ |
| retryMax | 3 | 3 | ✅ |
| retryIntervalSec | 60 | 60 | ✅ |
| rateLimitPerMin | 60 | 60 | ✅ |
| rateLimitPerDay | **null（空语句）** | 5000 | ⚠️ N7 I062 |

## 5. 重复防护

- Service L72-75：`selectCount` 查 (tenant_id, brand_id, channel_type) 唯一
- 重复创建期望 `BizCode.CONFLICT` 200/409

## 6. Final Result

**VERIFIED**（功能性）+ **N7 I062 PARTIAL-NON-BLOCKER**（setter 空语句登记）
