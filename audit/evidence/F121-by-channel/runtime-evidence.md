# F121 — MktConversion 按渠道统计 — Runtime Evidence

> Feature ID: F121
> Module: 营销转化 / 按 channelType 聚合
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:32 (UTC+8)
> SoT: `MktConversionController.java:46-48` + `MarketingAutomationService.byChannel`
> 验证方式: API-only

---

## 1. 端点

```
GET /mkt/conversion/by-channel
```

## 2. 真实响应

```bash
$ admin GET /mkt/conversion/by-channel
HTTP=200
traceId: cb6ac215236f4f7b84ad6c61055aea32
```

```json
{
  "code": 200, "message": "操作成功",
  "data": [
    {"channelType": "SMS", "sent": 487, "converted": 3, "reach": 1469}
  ]
}
```

## 3. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| HTTP 200 | ✅ | ✅ |
| 按 channelType 分组聚合 | ✅ | ✅ SMS 一组（其它渠道 0 行被过滤）|
| 4 维字段 reach/sent/converted | ✅ | ✅ 全部返回 |

## 4. 结论

**Final Result**: **VERIFIED**