# F117 — MktChannel / 删除渠道配置 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:43 (UTC+8)
> **方式**：API + DB + 静态源码分析（N9 I056 实证）

## 1. 接口实证

```
DELETE /mkt/channel/12
→ HTTP 200 / code 200 / message: "已删除"
→ traceId abc94c54a6d84c7f9f415c1325d519ab
```

## 2. DB 交叉验证

```
mysql> SELECT id, channel_name, deleted FROM mkt_channel_config WHERE id=12;
12 | audit-5A-F115-N7 | 1   ← 软删成功
```

## 3. N9 I056 实证 — 跨租户删除风险

**源码位置**：`MarketingAutomationService.java:94-98`

```java
public Result<Void> deleteChannel(@PathVariable Long id) {
    channelConfigMapper.deleteById(id);                  // ← 无 tenantId 校验
    return Result.success("已删除", null);
}
```

**对照 F116 updateChannel**（L88-92）显式 `c.setTenantId(old.getTenantId())` 防越权写
**对照 F117 deleteChannel**：无 tenantId 校验 → 任何租户用户可删任何 id 记录

### 实证限制

- 当前 sys_user 全 tenant_id=1，无法运行时触发跨租户删除
- 静态实证：源码层缺保护（与 F116 形成反差）
- **I056 PARTIAL-NON-BLOCKER**：登记，不修（Post-Audit 整改）

## 4. Final Result

**VERIFIED**（功能性 — 软删成功）+ **N9 I056 PARTIAL-NON-BLOCKER**（跨租户删除风险登记）
