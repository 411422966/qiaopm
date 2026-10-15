# F118 — MktChannel / 健康检查 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:43 (UTC+8)
> **方式**：API + DB + 静态（N11 I050 沿用 实证）

## 1. 接口实证

```
POST /mkt/channel/health-check
→ HTTP 200 / code 200 / message: "操作成功"
```

## 2. DB 交叉验证（5 sender 轮询后）

```
mysql> SELECT id, channel_type, last_health_at, last_health_msg, tenant_id FROM mkt_channel_config WHERE deleted=0;
id | channel_type  | last_health_at          | last_health_msg | tenant_id
 1 | SMS           | 2026-09-16 02:43:55    | FAIL            | 1
 2 | MINI_PROGRAM  | 2026-09-16 02:43:55    | FAIL            | 1
 3 | MP            | NULL                   | NULL            | 1
 4 | IN_APP        | 2026-09-16 02:43:55    | FAIL            | 1
 8 | SMS           | 2026-09-16 02:43:55    | FAIL            | 1
10 | PUSH          | NULL                   | NULL            | 1
```

- 4/6 行 last_health_at + last_health_msg = FAIL（凭据缺失 → 真实链路失败）
- 2/6 行 NULL（id=3 MP / id=10 PUSH）— ChannelDispatcher 可能跳过（异常或非注册 sender）

## 3. N11 I050 沿用 实证 — tenant_id=1L 硬编码

**源码位置**：`ChannelDispatcher.java:39-46`（沿用 Batch 1.4 EXT-19 实证）

```java
Long tenantId = 1L;                                     // ← 硬编码 1L
log.warn("[T07-D4] healthCheckAll hardcoded tenant=1 (only updates tenant_id=1)");
jdbc.update("UPDATE mkt_channel_config SET last_health_at = NOW(), last_health_msg = ? WHERE tenant_id = ? AND channel_type = ? AND deleted = 0",
        ok ? "OK" : "FAIL", tenantId, s.channelType());
```

- DB 实证：所有 last_health_at 更新行的 tenant_id = 1
- 当前 sys_user 全 tenant_id=1，无法触发跨租户场景
- 静态实证：硬编码 1L 与 T07-D4 设计意图冲突

## 4. Final Result

**PARTIAL-NON-BLOCKER**（N11 I050 沿用 — ChannelDispatcher 仅更新 tenant=1）
