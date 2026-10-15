# F133 — MktPromotion / 分页+列表+创建+更新+删除+导出 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:53 (UTC+8)
> **方式**：API + DB（7 端点 + 双路径 / BOM 验证）

## 1. 接口实证

### GET /mkt/promotion/page

```
→ HTTP 200 / code 200 / message: "操作成功"
```

### GET /mkt/promotion/ + /mkt/promotion（双路径）

```
GET /mkt/promotion/ → HTTP 200 / code 200
GET /mkt/promotion  → HTTP 200 / code 200
```

### POST /mkt/promotion（修正 LocalDateTime 格式后）

```
Body: {"promotionName":"audit-5A-F133","promotionType":"DISCOUNT","discountValue":0.85,"startTime":"2026-09-15T00:00:00","endTime":"2026-12-31T23:59:59"}
→ HTTP 200 / code 200 / message: "创建成功"
→ new id = 4
```

### PUT /mkt/promotion/{id}

```
PUT /mkt/promotion/4
Body: {"promotionName":"audit-5A-F133-UPDATED"}
→ HTTP 200 / code 200 / message: "更新成功"
```

### DELETE /mkt/promotion/{id}

```
→ HTTP 200 / code 200 / message: "已删除"
```

### GET /mkt/promotion/export

```
→ saved 121 bytes CSV (UTF-8 BOM: YES)
```

## 2. DB 交叉验证

```
mysql> SELECT id, promotion_name, deleted FROM mkt_promotion WHERE id=4;
id | promotion_name           | deleted
4  | audit-5A-F133-UPDATED   | 1   ← 软删成功
```

## 3. UTF-8 BOM 验证

- 导出文件前 3 字节：`EF BB BF` ✅ UTF-8 BOM

## 4. Final Result

**VERIFIED** — 7 端点全部 200，双路径 + 软删 + BOM 验证通过
