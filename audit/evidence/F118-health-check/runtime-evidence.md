# F118 — MktChannel 健康检查 — Runtime Evidence（含 N11 / I050 沿用实证）

> Feature ID: F118
> Module: 营销渠道 / 健康检查
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:32 (UTC+8)
> SoT: `MktChannelController.java:61-64` + `ChannelDispatcher.healthCheckAll`
> 验证方式: API + DB

---

## 1. 端点

```
POST /mkt/channel/health-check
```

## 2. 真实响应（5 sender 全部 healthy=false）

```bash
$ admin POST /mkt/channel/health-check
HTTP=200
traceId: 38b68210070b43a69cb5eb855d1dd35c
```

```json
{
  "code": 200, "message": "操作成功",
  "data": {
    "results": [
      {"healthy": false, "channelType": "EMAIL"},
      {"healthy": false, "channelType": "IN_APP"},
      {"healthy": false, "channelType": "MINI_PROGRAM"},
      {"healthy": false, "channelType": "SMS"},
      {"healthy": false, "channelType": "WECHAT_OFFICIAL_ACCOUNT"}
    ]
  }
}
```

**5 sender 全部 healthy=false** — 凭据缺失降级 mock 模式（符合 EXT-19 D10 铁律 + Batch 1.5 §九.9）。

## 3. N11 / I050 沿用实证 — `tenant_id=1L` 硬编码

`ChannelDispatcher.healthCheckAll()` 设计上硬编码 `Long tenantId = 1L`，并通过 `log.warn` 标记为「设计意图 — 仅监控 admin 全局渠道」。

DB 副作用验证：
```sql
SELECT id, tenant_id, channel_type, channel_name, last_health_at, LEFT(last_health_msg, 30) msg
FROM mkt_channel_config WHERE deleted=0 AND last_health_at IS NOT NULL;
```

| id | tenant_id | channel_type | last_health_at | msg |
|---|---|---|---|---|
| 1 | 1 | SMS | 2026-09-16 02:32:05 | FAIL |
| 2 | 1 | MINI_PROGRAM | 2026-09-16 02:32:05 | FAIL |
| 4 | 1 | IN_APP | 2026-09-16 02:32:05 | FAIL |
| 8 | 1 | SMS | 2026-09-16 02:32:05 | FAIL |

**所有 last_health_at 都是 tenant_id=1**（I050 沿用 T07-D4 例外）。

## 4. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| 返 5 sender 健康状态 | ✅ | ✅ 5 行 |
| 仅更新 tenant=1 全局渠道 | ✅ | ✅ (符合设计意图) |
| WECHAT_OA / EMAIL 凭据缺失 | ⚠ mock 降级 | ⚠ 全部 mock 降级（expected） |

## 5. 结论

**Final Result**: **MOCK-INTENTIONAL** — 5 sender 健康检查降级 mock + tenant=1L 硬编码是设计意图（log.warn 已声明）。

Post-Audit 改进项：
- 生产前注入 EMAIL/SMS/MP/MINI_PROGRAM 凭据
- 如需真支持多租户独立渠道健康，healthCheckAll 需接受 tenantId 参数

证据落档：traceId 38b68210070b43a69cb5eb855d1dd35c + 4 行 last_health_at 实证。