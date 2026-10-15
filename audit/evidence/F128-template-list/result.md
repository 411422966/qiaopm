# F128 — MktTemplate / 列表+创建+审核 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:50 (UTC+8)
> **方式**：API + DB（3 端点）

## 1. 接口实证

### GET /mkt/template/list

```
→ HTTP 200 / code 200 / message: "操作成功"
```

### POST /mkt/template（修正字段名后）

```
Body: {"templateCode":"audit-5A-F128","templateName":"audit-5A-F128","channelType":"SMS","scene":"WELCOME","content":"hi ${name}","enabled":1}
→ HTTP 200 / code 200 / message: "已创建"
→ traceId b3f91db9675a4888bf4c9df9a8b79f86
```

### PUT /mkt/template/{id}/audit（修正字段名后）

```
PUT /mkt/template/12/audit
Body: {"auditStatus":2,"auditRemark":"audit-5A-F128-OK"}
→ HTTP 200 / code 200 / message: "已审核"
→ traceId 6e1e45ea31fa4de2983ca978047d2995
```

## 2. DB 交叉验证

```
mysql> SELECT id, template_code, channel_type, scene, audit_status, audit_remark FROM mkt_channel_template WHERE id=12;
id | template_code     | channel_type | scene   | audit_status | audit_remark
12 | audit-5A-F128     | SMS          | WELCOME | 2            | audit-5A-F128-OK
```

- audit_status: 1（默认）→ **2（已审核）** ✅
- audit_remark: 写入 ✅

## 3. 字段名修正记录

| 字段名误（首次尝试） | 正确字段名 |
|---|---|
| tplCode | templateCode |
| status | auditStatus |

## 4. Final Result

**VERIFIED** — 3 端点全部 200，状态机转换 OK
