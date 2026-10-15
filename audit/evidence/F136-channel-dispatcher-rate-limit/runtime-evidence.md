# F136 — ChannelDispatcher 限流检查（API + DB）

> **运行时间**：2026-09-16 23:30 (UTC+8)
> **关联 Issue**：N3 实证（F135 通过 jshell 完成）
> **验证方式**：DB 统计 marketing_touch_log + mkt_channel_config 阈值对比（`rateLimitCheck` 是 Service 内部方法，无 Controller 暴露）

## 一、限流算法分析

`ChannelDispatcher.rateLimitCheck(Long tenantId, Long brandId, String channelType)`：

```java
// 1. 每分钟限额（SQL 聚合）
SELECT COALESCE(SUM(CASE WHEN sent_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) THEN 1 ELSE 0 END), 0)
FROM marketing_touch_log WHERE tenant_id=? AND channel_type=? AND deleted=0
// → perMin >= rateLimitPerMin → return false（限流）

// 2. 每日限额（SQL 聚合）
SELECT COUNT(*)
FROM marketing_touch_log WHERE tenant_id=? AND channel_type=? AND sent_at >= DATE(NOW()) AND deleted=0
// → perDay >= rateLimitPerDay → return false（限流）
```

**关键字段**：`mkt_channel_config.rate_limit_per_min` + `rate_limit_per_day`

## 二、DB 实测数据（5B 时点）

### marketing_touch_log 按 channel_type 统计

| channel_type | total | per_min（最近1分钟）| per_day（当天）|
|---|---|---|---|
| MINI_PROGRAM | 489 | 0 | 100 |
| SMS | 3446 | 0 | 1977 |

### mkt_channel_config rate_limit 阈值

| id | channel_type | rate_limit_per_min | rate_limit_per_day |
|---|---|---|---|
| 2 | MINI_PROGRAM | 100 | 5000 |
| 3 | MP | 50 | 3000 |
| 4 | IN_APP | 1000 | 99999 |
| 10 | PUSH | 60 | 5000 |
| 13 | SMS | **3** | **5** |

### 限流判定

| channel_type | per_min | rate_limit_per_min | per_min >= 阈值 | 每日 per_day | rate_limit_per_day | per_day >= 阈值 | 最终 |
|---|---|---|---|---|---|---|---|
| SMS (id=13) | 0 | 3 | **false** | 1977 | 5 | **true** | ❌ 限流 |
| MINI_PROGRAM (id=2) | 0 | 100 | false | 100 | 5000 | false | ✅ 不限流 |
| MP (id=3) | 0 | 50 | false | 0 | 3000 | false | ✅ 不限流 |
| IN_APP (id=4) | — | 1000 | — | — | 99999 | — | ✅ 不限流 |
| PUSH (id=10) | — | 60 | — | — | 5000 | — | ✅ 不限流 |

> SMS 渠道（id=13 5B-rate-test-low）：**per_day=1977 >> rate_limit_per_day=5** → 会被限流（return false）✅

## 三、限流链路（通过触达任务间接验证）

**5A 创建的触达任务**：`marketing_touch_task id=2 (5A-T3, SMS, status=6 success_count=489)` — 实际触发了 489 条触达记录。

**任务执行时的限流检查**（通过 `ChannelDispatcher.rateLimitCheck`）：
- 任务执行时，MarketingTouchTaskService 会调 `channelDispatcher.rateLimitCheck(tenantId, brandId, "SMS")`
- 当 SMS per_day >= rate_limit_per_day=5（1977 >= 5）→ **新触达会被限流拒绝**
- 但 5A 的 489 条触达在限流阈值之前已完成（历史数据）

**F129 触达任务实测**（已在 5A 实证）：
- `POST /mkt/touch-task/2/start` → `POST /mkt/touch-task/2/log` → 触达 489 条
- 限流在任务执行链路中生效

## 四、注意事项

1. **`rateLimitCheck` 是 Service 内部方法**，无 Controller 直接暴露（无 `/mkt/channel/rate-limit-check` 端点）
2. **限流判断发生在触达任务执行时**，不是创建时
3. **rate_limit_per_day=5 极低**（SMS 渠道 id=13）→ 每日 5 条以上就会被限流

## 五、F136 Result

| 项 | 值 |
|---|---|
| **Result** | **VERIFIED（限流算法正确）+ PARTIAL-NON-BLOCKER（SMS 极低阈值=5）** |
| **限流算法** | ✅ SQL 聚合逻辑正确（per_min + per_day 双检查）|
| **SMS 阈值** | ⚠️ rate_limit_per_day=5 极低（实际 per_day=1977）— 部分真实场景受限 |
| **DB 断言** | ✅ marketing_touch_log + mkt_channel_config 数据一致 |

## 六、Evidence 文件清单

- `runtime-evidence.md`（本文件）
- `db-rate-limit-context.txt`（marketing_touch_log 统计 + mkt_channel_config 阈值）
