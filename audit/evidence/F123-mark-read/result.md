# F123 — MktConversion / 标记已读 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:45 (UTC+8)
> **方式**：API + DB + 静态（N10 I057 实证）

## 1. 接口实证

```
POST /mkt/conversion/mark-read
Body: {"id":1}
→ HTTP 200 / code 200 / message: "已标记"
→ traceId b6a287a9f44243c1b8ab8a2aa3f377b9
```

## 2. N10 I057 实证 — 跨租户写无防护

**源码位置**：`MarketingAutomationService.java:762-768`

```java
public Result<Void> markRead(@PathVariable Long id) {
    jdbc.update("UPDATE marketing_touch_log SET status = 4, read_at = NOW() WHERE id = ? AND status = 2", id);
    jdbc.update("UPDATE marketing_touch_task SET read_count = read_count + 1 WHERE id = " +
            "(SELECT task_id FROM marketing_touch_log WHERE id = ?)", id);
    return Result.success("已标记", null);
}
```

**关键证据**：
- `marketing_touch_log.id=1` 当前 `tenant_id=2`（不同租户数据）
- admin 用户（tenant_id=1）调用 `markRead(id=1)` → **返回 200 已标记**（应 403 越权）
- UPDATE 无 `AND tenant_id = ?` 过滤 → 跨租户可写

## 3. DB 实证

```
mysql> SELECT id, status, read_at, tenant_id FROM marketing_touch_log WHERE id=1;
id | status | read_at | tenant_id
 1 | 1      | NULL    | 2
```

- 当前 status=1（非 2），UPDATE WHERE status=2 不命中 → 实际未更新
- 但 HTTP 200 已返回 → **安全漏洞**（与 F122 trackConversion 形成反差）

## 4. Final Result

**PARTIAL-NON-BLOCKER** — **N10 I057 实证**：跨租户 mark-read 写无 tenantId 过滤
- 当前 status=2 不匹配，实际未更新；但**调用方收到 200 误报成功**
- 应加 `WHERE id = ? AND tenant_id = ?` + 校验 UPDATE rows > 0
