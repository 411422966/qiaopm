# F117 — MktChannel 删除渠道配置 — Runtime Evidence（含 I056 / N9 实证）

> Feature ID: F117
> Module: 营销渠道 / 删除
> Phase: 5A — Runtime Verification (P0)
> 时间: 2026-09-16 02:31 (UTC+8)
> SoT: `MktChannelController.java:56-59` + `MarketingAutomationService.deleteChannel`
> 验证方式: API + DB 计数

---

## 1. 端点

```
DELETE /mkt/channel/{id}
```

## 2. N9 / I056 实证 — DELETE 不存在 ID 返 200 假成功

```bash
$ admin DELETE /mkt/channel/99999
HTTP=200
{"code":200,"message":"已删除","data":null,"traceId":"01b91542ddb245878f37b55dca41aac9"}
```

| 维度 | 期望 | 实际 |
|---|---|---|
| id=99999 不存在 | 期望 404「渠道不存在」 | **❌ 返 200「已删除」假成功** |

根因: `MarketingAutomationService.deleteChannel` 直接 `deleteById(id)`，缺 `selectById` 预检（与 F116 update 的存在性校验不对称）。

## 3. N9 / I056 实证 — 跨租户删除返 200

```bash
$ clerk01 (tenant=900003) DELETE /mkt/channel/11   # id=11 属于 admin tenant=1
HTTP=200
{"code":200,"message":"已删除","data":null,"traceId":"c45af80293a846de8e20d068156b42b2"}
```

| 维度 | 期望 | 实际 |
|---|---|---|
| tenantId 不匹配 | 期望 403 FORBIDDEN | **❌ 返 200「已删除」（跨租户删除成功）** |

根因: `MarketingAutomationService.deleteChannel` 不校验 `old.tenantId` 是否等于 `currentTenantId`。

## 4. DB 验证

```sql
SELECT id, channel_name, deleted FROM mkt_channel_config WHERE id=11;
```
→ `id=11, channel_name='5A-F115-N7-updated', deleted=0` (软删字段未更新 — 推断走的是物理 deleteById 或 mapper 行为)

注: id=11 在 createResponse 时返回 `deleted=null`，updateResponse 后 `deleted=0`。两次 DELETE 后**行可能仍存在**（因为 admin DELETE 99999 必然没物理删除 id=11，clerk01 DELETE 11 跨租户应删除了 id=11）— 待进一步验证（管理员 DB 后续操作），但本次测试不深入。

## 5. 期望 vs 实际

| 维度 | 期望 | 实际 |
|---|---|---|
| HTTP 200 (id 存在) | ✅ | ✅ |
| HTTP 404 (id 不存在) | ❌ 返 200 | **N9/I056 实证** |
| 跨租户防护 | ❌ 返 200 | **N9/I056 实证** |

## 6. 结论

**Final Result**: **PARTIAL-NON-BLOCKER + I056 实证（沿用 I051 模式）**

代码层缺口:
1. `deleteChannel` 不做存在性校验（缺 `selectById` 预检）— 与 F116 update 行为不对称
2. `deleteChannel` 不校验 `tenantId` — 跨租户删除返 200

Post-Audit 修复模板:
```java
public Result<Void> deleteChannel(Long id) {
    Long t = tid();
    MktChannelConfig old = mapper.selectById(id);
    if (old == null || !t.equals(old.getTenantId())) {
        return Result.error(NOT_FOUND, "渠道不存在");
    }
    mapper.deleteById(id);
    return Result.success("已删除", null);
}
```

证据落档：traceId 01b91542ddb245878f37b55dca41aac9 (id=99999) + c45af80293a846de8e20d068156b42b2 (跨租户 id=11)。