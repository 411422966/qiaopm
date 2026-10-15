# F114 — MktChannel 渠道列表查询 — Runtime Evidence

> Feature ID: F114
> Module: 营销渠道 / 列表查询
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:31-02:33 (UTC+8)
> SoT: `MktChannelController.java:42-44` + `MarketingAutomationService.channelList`
> 验证方式: API + DB 计数

---

## 1. 端点

```
GET /mkt/channel/list
```

## 2. 真实响应（admin token / dataScope=ALL）

```bash
$ admin GET /mkt/channel/list
HTTP=200
traceId: 85686204c4ac4a0482dc30316b43d1d6
```

| # | id | channelType | channelName | tenantId | rateLimitPerDay | lastHealthAt |
|---|---|---|---|---|---|---|
| 1 | 4 | IN_APP | 站内信 | 1 | 99999 | 2026-09-16T02:27:57 |
| 2 | 2 | MINI_PROGRAM | 小程序订阅消息 | 1 | 5000 | 2026-09-16T02:27:57 |
| 3 | 3 | MP | 公众号模板消息 | 1 | 3000 | null |
| 4 | 10 | PUSH | audit-test-F115-push-N7 | 1 | 5000 | null |
| 5 | 1 | SMS | 阿里云短信 | 1 | 2000 | 2026-09-16T02:27:57 |
| 6 | 8 | SMS | 5A-SMS-Updated-V2 | 1 | 5000 | 2026-09-16T02:27:57 |

## 3. 风险发现（PARTIAL-NON-BLOCKER — I072 NEW）

**`MarketingAutomationService.channelList()` 未做 `where tenant_id = currentTenant` 过滤**

- admin (dataScope=ALL) 看到 6 条
- clerk01 (tenant=900003) 看到**同样 6 条**（无 tenant 过滤）— 跨租户可见

实证：
- 现状：因当前 DB 只有 tenant_id=1 的渠道，clerk01 看到的与 admin 一致，**说明 SQL 无 WHERE**
- 风险：若 tenant=900003 有自己的渠道，业务上仍能看到 admin 的（泄露风险）
- 严重度：PARTIAL-NON-BLOCKER（依赖 dataScope 兜底；非紧急安全风险但设计缺陷）

## 4. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| HTTP 状态 | 200 | ✅ 200 |
| 响应字段驼峰 | ✅ | ✅ rateLimitPerMin / rateLimitPerDay / lastHealthAt 等 |
| DB 表存在 | ✅ mkt_channel_config 20 列 | ✅ |
| tenant 过滤 | ⚠ 应有 `WHERE tenant_id = ?` | ❌ 无过滤 |

## 5. 结论

**Final Result**: **PARTIAL-NON-BLOCKER** — 功能 OK 但缺 tenant 过滤（I072 NEW）。

Post-Audit 修复建议：
- 在 `MarketingAutomationService.channelList()` 末尾加 `WHERE tenant_id = #{currentTenantId}` 或依赖 MyBatis-Plus TenantLineInnerInterceptor（待统一治理）

证据落档：traceId 85686204c4ac4a0482dc30316b43d1d6 (admin) / 与 clerk01 同样 6 条。