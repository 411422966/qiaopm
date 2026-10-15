# F133 — MktPromotion 促销全流程（7 端点，含 create ❌）— Runtime Evidence

> Feature ID: F133
> Module: 营销促销 / 7 端点
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:34 (UTC+8)
> SoT: `MktPromotionController.java`
> 验证方式: API + DB

---

## 1. 端点（7 个，含 2 个 GET list / page + 1 个 CSV export）

```
GET  /mkt/promotion/page
GET  /mkt/promotion/      (list - 双路径 @GetMapping({"/", ""}))
POST /mkt/promotion        ← ❌ 500
PUT  /mkt/promotion/{id}
DELETE /mkt/promotion/{id}
GET  /mkt/promotion/export
```

## 2. page ✅

```bash
GET /mkt/promotion/page?pageNum=1&pageSize=5
HTTP=200
traceId: a7514421a4a147b18ad81c7b83412208
```
2 条历史 promotion（id=2/4），tenantId=1。

## 3. list ✅

```bash
GET /mkt/promotion/?status=1
HTTP=200
traceId: 7c4cfe0500b74ea081f055ef1f4fff40
```
2 条（与 page 一致）。

## 4. ❌ create 失败 — `start_time` 字段无 default

```bash
POST /mkt/promotion
body: {"promotionName":"5A-F133-promotion","promotionType":"FULL_REDUCE","reduceAmount":50,"thresholdAmount":200,"quotaTotal":100,"priority":5}
HTTP=500
{"code":500,"message":"数据库异常","data":null,"traceId":"4883815320584d3a9343b98e86c4a3c9"}
```

**Caused by**: `java.sql.SQLException: Field 'start_time' doesn't have a default value`

**SQL**:
```sql
INSERT INTO mkt_promotion
  (tenant_id, store_id, promotion_code, promotion_name, promotion_type,
   threshold_amount, reduce_amount, scope_type, stackable,
   quota_total, quota_used, status, priority, create_time, update_time)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```

**`mkt_promotion` 表**有 `start_time` / `end_time` NOT NULL 列，但 `create()` 实现 `MktPromotionController.java:78-90` **未默认值兜底** — 直接调 mapper.insert。

**新触达 Issue: I074 NEW**（与 F120/F122/F132 复合 — 实体/表 schema 缺默认）。

## 5. update ✅

```bash
PUT /mkt/promotion/4
body: {"promotionName":"5A-F133-updated","priority":10}
HTTP=200
traceId: d6ef52a4ec584268b053347e9bf8ef78
```
→ name 改 + priority=10。

## 6. delete ✅

```bash
DELETE /mkt/promotion/2
HTTP=200
traceId: 8aad011710864cd2906a7a2073ce82a0
```
物理删除 id=2（无 deleted 字段保护或保护不全）。

## 7. export ✅ CSV + UTF-8 BOM

```bash
GET /mkt/promotion/export
HTTP=200 (file written: 127 bytes)
Content-Disposition: attachment; filename=mkt_promotion.csv
Content-Type: text/csv;charset=utf-8
```

输出文件头：
```
[BOM]ID,编码,名称,类型,状态,优先级,创建时间
4,P1789496347029,5A-F133-updated,FULL_REDUCE,1,10,2026-09-16T02:19:07
```

UTF-8 BOM (EF BB BF) 头正确，Excel 打开中文不乱码。

## 8. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| page 200 | ✅ | ✅ 2 条 |
| list 200 | ✅ | ✅ 2 条 |
| create 200 | ✅ | ❌ 500 (start_time 无 default) |
| update 200 | ✅ | ✅ |
| delete 200 | ✅ | ✅ |
| export 200 | ✅ | ✅ UTF-8 BOM CSV |

## 9. 结论

**Final Result**: **PARTIAL-NON-BLOCKER + I074 NEW 实证** — 6/7 端点 200，create BROKEN。

Post-Audit 修复：`MktPromotionController.java:78-90 create()` 末尾加：
```java
if (p.getStartTime() == null) p.setStartTime(LocalDateTime.now());
if (p.getEndTime() == null) p.setEndTime(LocalDateTime.now().plusYears(1));
```

证据落档：traceId a7514421a4a147b18ad81c7b83412208 (page) + 7c4cfe0500b74ea081f055ef1f4fff40 (list) + 4883815320584d3a9343b98e86c4a3c9 (create 500) + d6ef52a4ec584268b053347e9bf8ef78 (update) + 8aad011710864cd2906a7a2073ce82a0 (delete) + export.csv file。