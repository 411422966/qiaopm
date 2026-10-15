# F128 — MktTemplate 列表/创建/审核 — Runtime Evidence

> Feature ID: F128
> Module: 营销模板 / list + create + audit
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:33 (UTC+8)
> SoT: `MktTemplateController.java:36-55`
> 验证方式: API + DB

---

## 1. 端点

```
GET  /mkt/template/list
POST /mkt/template
PUT  /mkt/template/{id}/audit
```

## 2. list ✅

```bash
GET /mkt/template/list
HTTP=200
traceId: 8c66231eb7e54954b9a0ba8a19d6e687
```

8 条数据（id=1/2/3/4/5/6/9/10），覆盖 SMS/MINI_PROGRAM/EMAIL 各渠道 + BIRTHDAY/BOOKING/EXPIRE/MARKETING/EVALUATION 场景。

## 3. create ✅

```bash
POST /mkt/template
body: {
  "channelType":"EMAIL","templateCode":"T-5A-001","templateName":"5A-email-tpl",
  "scene":"BOOKING","subject":"预约确认","content":"您好 ${name}",
  "variables":"name","enabled":1
}
HTTP=200
traceId: c5f95b87a27847debb66f44f4a312ad9
```

返回 id=11, auditStatus=1（待审核）, enabled=1。

## 4. audit ✅

```bash
PUT /mkt/template/11/audit
body: {"auditStatus":2,"auditRemark":"5A audit pass"}
HTTP=200
traceId: 621b33aeb74a4e51ae5d2a945ac3c965
```

DB: `audit_status=2 (APPROVED), audit_remark='5A audit pass'`。

## 5. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| list 200 | ✅ | ✅ 8 条 |
| create 200 | ✅ | ✅ id=11 |
| audit 200 | ✅ | ✅ audit_status=2 |
| tenant 过滤（list）| ✅ | ✅ |
| auditStatus 必填校验 | ✅ | ✅ BAD_REQUEST 缺字段时 |

## 6. 结论

**Final Result**: **VERIFIED**