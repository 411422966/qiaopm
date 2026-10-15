# F135 — ChannelDispatcher.getSender 单元测试（jshell）

> **运行时间**：2026-09-16 23:29 (UTC+8)
> **关联 Issue**：I061（CRITICAL NPE 风险）+ I039 沿用（别名漂移）+ I050 沿用（硬编码 tenant_id=1）
> **验证方式**：jshell 反射实例化 ChannelDispatcher + 5 sender

## 一、jshell 实证

**F135 jshell 探针**：`probe-5B.6-channelDispatcher-getSender.jsh`
**classpath**：`backend/cp.txt + target/classes`（12295 字符）

### Test 3.1 — 5 个有效 channelType

| channelType | getSender() 返回 | 预期 | 结果 |
|---|---|---|---|
| SMS | `SmsSender` | 非 null | ✅ MATCH |
| EMAIL | `EmailSender` | 非 null | ✅ MATCH |
| IN_APP | `InAppSender` | 非 null | ✅ MATCH |
| MINI_PROGRAM | `MiniProgramSender` | 非 null | ✅ MATCH |
| WECHAT_OFFICIAL_ACCOUNT | `WechatOfficialAccountSender` | 非 null | ✅ MATCH |

**有效 channelType = 5/5 ✅**

### Test 3.2 — 2 个 DB 存在但无对应 sender（I039 别名漂移）

| DB 中 channelType | getSender() 返回 | 预期 | 结果 | 说明 |
|---|---|---|---|---|
| `MP` | null | null | ✅ EXPECTED_NULL | DB 有 id=3，sender 只注册 `WECHAT_OFFICIAL_ACCOUNT` |
| `PUSH` | null | null | ✅ EXPECTED_NULL | DB 有 id=10，无对应 sender |

**别名漂移实证**：`WechatOfficialAccountSender.channelType() = "WECHAT_OFFICIAL_ACCOUNT"`，而 DB 中存储的是 "MP"（旧别名），**路由永远不命中**。

### Test 3.3 — 完全未知 channelType（I061 NPE 风险）

| channelType | getSender() 返回 | 预期 | 结果 |
|---|---|---|---|
| `UNKNOWN_CHANNEL_TYPE` | null | null | ✅ EXPECTED_NULL |

**I061 NPE 风险实证**：`ChannelDispatcher.getSender()` miss 时返回 `null`（line 34-39 `findFirst().orElse(null)`），调用方直接调用 `.send()` 时 NPE。

### Test 3.4 — senders List size = 5

```
ChannelDispatcher.senders.size() = 5 ✅
```

### Test 3.5 — null 入参

```
getSender(null) = null ✅
```

## 二、ChannelDispatcher.rateLimitCheck 静态分析

`ChannelDispatcher.rateLimitCheck(Long tenantId, Long brandId, String channelType)`：

```java
// 每分钟限额
String minSql = "SELECT COALESCE(SUM(CASE WHEN t.sent_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) THEN 1 ELSE 0 END), 0) AS per_min " +
    "FROM marketing_touch_log t WHERE t.tenant_id = ? AND t.channel_type = ? AND t.deleted = 0";
Long perMin = jdbc.queryForObject(minSql, Long.class, tenantId, channelType);
MktChannelConfig cfg = channelConfigMapper.selectOne(...);
// 每分钟：perMin >= cfg.rateLimitPerMin → 限流（return false）

// 每日限额
String daySql = "SELECT COUNT(*) FROM marketing_touch_log t " +
    "WHERE t.tenant_id = ? AND t.channel_type = ? AND t.sent_at >= DATE(NOW()) AND t.deleted = 0";
Long perDay = jdbc.queryForObject(daySql, Long.class, tenantId, channelType);
// 每日：perDay >= cfg.rateLimitPerDay → 限流（return false）
```

**分析**：无 Controller 暴露此方法（Service 内部），无法直接 curl 验证。DB 实测：
- `marketing_touch_log` SMS per_day=1977, rate_limit_per_day=5 → per_day >= 5 → **会被限流**（return false）
- `marketing_touch_log` MINI_PROGRAM per_day=100, rate_limit_per_day=5000 → per_day >= 5000 false → **不限流**

**限流算法逻辑正确**（SQL 聚合 + cfg 阈值比较），但属于 Service 内部方法（无 Controller 层暴露）。

## 三、I061 I063 CONFLICT

| 项 | 描述 |
|---|---|
| **I061 CRITICAL-P2** | `getSender(channelType)` miss → 返回 null（NPE 风险）— jshell 已实证 |
| **I039 沿用** | `WECHAT_OFFICIAL_ACCOUNT` vs DB 存储 `MP`（别名漂移，路由永远 miss）— jshell 已实证 |
| **I050 沿用** | `healthCheckAll()` 硬编码 `tenant_id=1L`（已在 F118 实证）|

## 四、F135 Result

| Feature | 验证方式 | Result | 关键 Issue |
|---|---|---|---|
| **F135 ChannelDispatcher.getSender** | jshell 反射单元测试 | **PARTIAL-BLOCKER** | I061 NPE 风险 + I039 别名漂移 |

**PARTIAL-BLOCKER 原因**：
1. `getSender` 返回 null 时调用方 NPE（CRITICAL）
2. MP/PUSH 别名漂移导致 DB 记录无法路由（N12）

## 五、Evidence 文件清单

- `runtime-evidence.md`（本文件）
- `probe-5B.6-channelDispatcher-getSender.jsh`（jshell 探针 109 行）
- `jshell-output.txt`（jshell 运行输出）
