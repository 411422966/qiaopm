# F136 — ChannelDispatcher 限流检查实证

> **运行时间**：2026-09-16 23:14-23:16 (UTC+8)
> **环境**：后端 8080 + MySQL 3307 + admin token
> **traceId1**：bddd1429d2a84fb7bdbc1dbd4e47c692（task id=10）
> **traceId2**：cc2f84ed02d94f8e8aa781c12f5e156a（task id=11）

## 一、限流配置现状（mkt_channel_config）

| id | tenant_id | brand_id | channel_type | rate_limit_per_min | rate_limit_per_day | status |
|---|---|---|---|---|---|---|
| 2 | 1 | 1 | MINI_PROGRAM | 100 | 5000 | 1 |
| 3 | 1 | 1 | MP | 50 | 3000 | 1 |
| 4 | 1 | NULL | IN_APP | 1000 | 99999 | 1 |
| 10 | 1 | NULL | PUSH | 60 | 5000 | 1 |
| 13 | 1 | NULL | SMS | **3** | **5** | 1 |

## 二、Test 1 — brand_id=NULL + SMS（rate_limit_per_day=5）

**实证**：当天 tenant=1 SMS 触达已 1488 条（远超 5），但 task 触达 489 全部成功，**未触发限流**。

**根因**：MyBatis-Plus `LambdaQueryWrapper.eq(MktChannelConfig::getBrandId, brandId)` 当 `brandId=null` 时生成的 SQL 是 `brand_id = NULL`（永远 false），导致 `cfg = null`，rateLimitCheck 跳过判断直接返回 true。

### SQL 验证

```sql
-- brand_id = NULL（永远不匹配）
SELECT id FROM mkt_channel_config 
WHERE tenant_id = 1 AND brand_id = NULL AND channel_type = 'SMS' AND status = 1 LIMIT 1;
-- → 0 rows

-- brand_id IS NULL（正确匹配 id=13）
SELECT id FROM mkt_channel_config 
WHERE tenant_id = 1 AND brand_id IS NULL AND channel_type = 'SMS' AND status = 1 LIMIT 1;
-- → id=13
```

### Task 触达结果

```
taskId=10 brand_id=NULL channel=SMS target=489 success=489 fail=0
```

> ⚠️ **I075 NEW 实证**：ChannelDispatcher.rateLimitCheck `eq(brand_id, null)` 生成错误 SQL，**brand_id 为 null 的任务永远不限流**。
> 配置 rate_limit_per_min=3 / rate_limit_per_day=5 完全失效。

## 三、Test 2 — brand_id=1 + MINI_PROGRAM（rate_limit_per_min=100）

**实证**：task 触达 100 行后**立即停止**，符合 rate_limit_per_min=100 阈值。

### Task 触达结果

```
taskId=11 brand_id=1 channel=MINI_PROGRAM target=489 success=100 fail=0
```

✅ **限流生效**：100 = rate_limit_per_min → 后续 389 条触达被 rateLimitCheck 拦下（`UPDATE marketing_touch_log SET status = 1 WHERE id = ?` 跳过发送，count 不递增）。

> 当 brand_id 非 null 时，rateLimitCheck 工作正常。

## 四、F136 结论

| 场景 | rateLimitCheck 行为 | 实证 |
|---|---|---|
| cfg 找到且 cfg.rateLimitPerMin != null 且 perMin >= 阈值 | 返回 false（限流）| ✅ task id=11 MINI_PROGRAM 100 行后停止 |
| cfg 找到且 cfg.rateLimitPerDay != null 且 perDay >= 阈值 | 返回 false（限流）| ⚠ 未实测（无 brand_id=NULL 且当日触达超阈值的可靠配置）|
| **cfg 为 null（brand_id=NULL 时 eq 不匹配）**| **跳过判断返回 true（不限流）**| ❌ task id=10 SMS 1488 已超 5 但 489 全成功 |
| cfg.rateLimitPerMin 为 null | 跳过判断 | ⚠ 未实测 |

**Result**：PARTIAL-NON-BLOCKER
- 当 brand_id 完整时（F129 5A 触达 489 全成功是因为 rateLimitPerDay=5000 远大于 489），限流工作正常
- 当 brand_id=NULL 时（多数 task 创建时未传 brandId），限流**永远失效** — 这是个潜在运维风险（如果配置 rate_limit_per_min 有意义但 brand_id=NULL，运维预期会被限但实际不会）

**新增 Issue**：
- **I075 NEW**：ChannelDispatcher.rateLimitCheck `eq(brandId, null)` → SQL `brand_id = NULL` → cfg=null → 永远不限流。fix 方案：MyBatis-Plus `eq(field, null)` 改用 `isNull(BrandId::getBrandId)`，或在 Service 层先判断 brandId null 直接走"无 brand 兜底配置"路径。

## 五、Evidence 文件清单

- `audit/evidence/F136-rate-limit-check/raw-response-task-10.txt`（task 10 启动响应）
- `audit/evidence/F136-rate-limit-check/raw-response-task-11.txt`（task 11 启动响应）
- `audit/evidence/F136-rate-limit-check/db-channel-config.txt`（5 条 mkt_channel_config 配置）
- `audit/evidence/F136-rate-limit-check/db-task-10.txt` + `db-task-11.txt`（DB 验证）
