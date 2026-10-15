# F116 — MktChannel / 更新渠道配置 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:43 (UTC+8)
> **方式**：API + DB 前后 diff

## 1. 接口实证

```
PUT /mkt/channel/12
Body: {"channelName":"audit-5A-F115-N7","channelType":"PUSH","rateLimitPerMin":120,"provider":"audit"}
→ HTTP 200 / code 200 / data = {...rateLimitPerMin:120...}
→ traceId 08d51879b3534e7182e01fd7cec58f1c
```

## 2. DB 交叉验证（更新后）

```
mysql> SELECT id, channel_name, rate_limit_per_min FROM mkt_channel_config WHERE id=12;
12 | audit-5A-F115-N7 | 120
```

`rate_limit_per_min` 60 → 120 ✅ 更新生效

## 3. 源码保护 — 防越权写

```java
public Result<MktChannelConfig> updateChannel(@PathVariable Long id, @RequestBody MktChannelConfig c) {
    MktChannelConfig old = channelConfigMapper.selectById(id);
    if (old == null) return Result.error(BizCode.NOT_FOUND, "渠道不存在");
    c.setId(id);
    c.setTenantId(old.getTenantId());                  // ← 强制保留原租户
    channelConfigMapper.updateById(c);
    ...
}
```

- L88-92 显式 `c.setTenantId(old.getTenantId())` — 防止 body 携带的 tenantId 越权写
- 模式：与 F117 `deleteChannel` 形成对照（delete 缺同样保护 → N9 I056）

## 4. Final Result

**VERIFIED** — 更新生效 + 租户保留逻辑正确
