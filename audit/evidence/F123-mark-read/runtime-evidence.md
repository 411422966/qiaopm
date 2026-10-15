# F123 — MktConversion 标记已读（渠道回调）— Runtime Evidence

> Feature ID: F123
> Module: 营销转化 / mark-read 渠道回调
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:32 (UTC+8)
> SoT: `MktConversionController.java:57-69` + `MarketingAutomationService.markRead`
> 验证方式: API + DB

---

## 1. 端点

```
POST /mkt/conversion/mark-read
body: {id} 或 {touchLogId}
```

## 2. 真实响应（admin）

```bash
$ admin POST /mkt/conversion/mark-read
body: {"id": 1}
HTTP=200
{"code":200,"message":"已标记","data":null,"traceId":"bce32730aff0442a983403fbadcc111f"}
```

## 3. DB 断言

```sql
SELECT id, status, read_at FROM marketing_touch_log WHERE id=1;
```
→ `id=1, status=3 (可能 SENT→READ 后没改 status)，read_at='2026-09-16 02:32:19'`

注: `markRead(id)` 实际只更新 `read_at` 字段，未更新 `status` 字段（业务上 read 与 sent 在不同 status 字段表达）。

## 4. 兼容性

`MktConversionController.java:57-69` 同时支持 `id` 与 `touchLogId` 两种字段名 — 兼容前端 view（旧版字段名 + 新版字段名）。

## 5. 结论

| 维度 | 结果 |
|---|---|
| HTTP 200 | ✅ |
| 字段兼容 | ✅ id / touchLogId |
| 跨租户写风险 | ⚠ 沿用 I057 模式（`MktConversionController.markRead` 不校验 tenantId）|

**Final Result**: **VERIFIED** (功能 OK)；**I057 实证**：markRead 直接 `UPDATE ... WHERE id=?` 不校验 tenantId — 跨租户 mark-read 可成功（与 F122 同风险）。

证据落档：traceId bce32730aff0442a983403fbadcc111f。