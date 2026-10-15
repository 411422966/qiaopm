# F120 — MktConversion ROI 统计 — Runtime Evidence（❌ BROKEN）

> Feature ID: F120
> Module: 营销转化 / ROI 统计
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:32 (UTC+8)
> SoT: `MktConversionController.java:40-43` + `MarketingAutomationService.java:686 conversionRoi`
> 验证方式: API-only

---

## 1. 端点

```
GET /mkt/conversion/roi
```

## 2. ❌ 失败响应（HTTP 500）

```bash
$ admin GET /mkt/conversion/roi
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"b661f48c3cef40a8b9c23cc301d2ee2e"}
```

## 3. 根因 — `marketing_touch_conversion` 表无 `deleted` 列

**Caused by**: `java.sql.SQLSyntaxErrorException: Unknown column 'c.deleted' in 'where clause'`

**SQL**（`MarketingAutomationService.java:686`）:
```sql
SELECT COALESCE(SUM(amount), 0) AS total_revenue,
       COUNT(*) AS convert_count
FROM marketing_touch_conversion c
WHERE c.deleted = 0 AND c.tenant_id = ?
```

**`marketing_touch_conversion` 表实际列**（已通过其它端点 INSERT 实证）:
- tenant_id / touch_log_id / task_id / conversion_type / amount / converted_at / create_time / **related_id** / status / external_id
- ⚠ **无 deleted 列**（与软删除约定漂移 — I055 类似 SoT 反向）

**新触达 Issue: I070 NEW**

| 维度 | 结果 |
|---|---|
| HTTP 200 | ❌ 500 数据库异常 |
| ROI 聚合 | ❌ 不可用 |
| 任何参数化 | ❌ 即使 taskId=null 仍失败（SQL 固定使用 c.deleted） |

## 4. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| HTTP 200 | ✅ | ❌ 500 |
| ROI = 转化收益/触达成本 | ✅ | ❌ SQL 错 |
| tenant_id 过滤 | ✅ | ❌ 永远先撞 deleted 列错 |

## 5. 结论

**Final Result**: **BROKEN** — 端点不可用；新触达 **I070 NEW**（marketing_touch_conversion 实体字段缺失，SQL `c.deleted` 列不存在）。

Post-Audit 修复选项：
1. 表加 `deleted TINYINT(1) DEFAULT 0` 列（migration）
2. 或代码改 `WHERE c.tenant_id = ?` 移除 deleted 谓词
3. 或删除 `MarketingTouchConversion` 实体 @TableLogic 注解

证据落档：traceId b661f48c3cef40a8b9c23cc301d2ee2e (HTTP 500)。