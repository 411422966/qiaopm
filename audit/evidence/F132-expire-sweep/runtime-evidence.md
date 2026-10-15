# F132 — MktCoupon 过期扫描 + 券实例分页 + 导出 — Runtime Evidence（expire-sweep ✅, page ✅, export ❌）

> Feature ID: F132
> Module: 营销卡券实例 / expire-sweep + page + export
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:34 (UTC+8)
> SoT: `MktCouponController.java:225-260 expireSweep + 282-298 couponPage + 304-329 export`
> 验证方式: API + DB

---

## 1. 端点（3 个）

```
POST /mkt/coupon/expire-sweep
GET  /mkt/coupon/page
GET  /mkt/coupon/export          ← ❌ 500
```

## 2. expire-sweep ✅ 200 + 0 副作用

```bash
POST /mkt/coupon/expire-sweep
HTTP=200
{"message":"扫描完成","data":{"expiredCount":0,"nearExpireCount":0,"batchesClosed":0}}
traceId: b0f20fc1e9d54bc1b55a48b8c8d35c1c
```

`expireSweep` 实测：
- 0 张券过期（当前数据 expire_date 都在未来）
- 0 个批次关闭
- ⚠ **N2 / I060 实证**：实现 `MktCouponController.java:230-246` 确实**无 try/catch**，但本次 sweep 路径不抛错（数据干净）— 若 SQL 异常（如手工注入脏数据），会直接抛 Spring 500，无 audit log（I060 沿用实证，本次 sweep 路径未触发但风险存在）。

## 3. coupon page ✅ 200（LambdaQueryWrapper，路径与 F131 grant 不同）

```bash
GET /mkt/coupon/page?pageNum=1&pageSize=5
HTTP=200
{"total":0,"records":[]}
traceId: f60973089cf8473f9b81ce7bef4d14e3
```

**0 条 records** — grant 失败没插券，符合预期。

注: `couponPage` 用 MyBatis-Plus `LambdaQueryWrapper<MktCoupon>.selectPage()` 而非 JdbcTemplate raw SQL，且 `couponPage` 没 SELECT coupon_name 列（看 export 失败根因）。

## 4. ❌ export 失败 — `coupon_name` 列不存在

```bash
GET /mkt/coupon/export
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"7d82e7b69b7e41108da69a8d934228b1"}
```

**Caused by**: `java.sql.SQLSyntaxErrorException: Unknown column 'coupon_name' in 'field list'`

**SQL**:
```sql
SELECT id, tenant_id, store_id, batch_id, coupon_code, member_id, member_name,
       coupon_name, coupon_type, discount_amount, discount_rate, min_consume,
       discount_scope, status, used_order_id, used_time, used_amount,
       effective_date, expire_date, granted_at, granted_by, idempotency_key,
       create_time, update_time, deleted
FROM mkt_coupon
WHERE deleted=0 AND (tenant_id = ?) ORDER BY id DESC
```

**`mkt_coupon` 表实际无 `coupon_name` 列** — 表 schema 仅含 coupon_code + batch_id (→ mkt_coupon_batch.coupon_name) 等。

**新触达 Issue: I073 NEW**（与 F122/F120/F131 复合 — 实体字段 vs 表 schema 漂移）。

## 5. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| expire-sweep 200 | ✅ | ✅ (N2/I060 风险存在但本次未触发) |
| page 200 | ✅ | ✅ 0 条 |
| export 200 | ✅ | ❌ 500 (coupon_name 列缺失) |

## 6. 结论

**Final Result**: **PARTIAL-NON-BLOCKER + I073 NEW 实证 + I060 风险存在**

- sweep + page 端点 OK
- export 端点 BROKEN（实体字段 vs 表 schema 漂移）

Post-Audit 修复路径：
1. `mkt_coupon` 表加 `coupon_name VARCHAR(255) DEFAULT NULL` 列（migration）— 或 —
2. `MktCoupon` 实体 `@TableField(exist = false)` 标记 `couponName` 字段 — 或 —
3. export 端点显式 select 列表从 mkt_coupon_batch JOIN

证据落档：traceId b0f20fc1e9d54bc1b55a48b8c8d35c1c (sweep) + f60973089cf8473f9b81ce7bef4d14e3 (page) + 7d82e7b69b7e41108da69a8d934228b1 (export)。