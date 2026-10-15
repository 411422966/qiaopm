# F129 — MktTouchTask / 任务分页+创建+启停+日志 — Runtime Evidence

> **Phase**：5A — Runtime Verification
> **时间**：2026-09-16 02:50 (UTC+8)
> **方式**：API + DB（8 端点 + N13 I058 双形式实证）

## 1. 接口实证

### POST /mkt/task/page（N13 I058 POST 形式）

```
Body: {"pageNum":1,"pageSize":10}
→ HTTP 200 / code 200 / message: "操作成功"
→ traceId 0da4d934ba1549eea2b09ba5d1b67a50
```

### GET /mkt/task/page（N13 I058 GET 形式）

```
GET /mkt/task/page?pageNum=1&pageSize=10
→ HTTP 200 / code 200 / message: "操作成功"
→ traceId 77c46b88cc334eb48b5e980d0deed99d
```

### POST /mkt/task

```
Body: {"campaignName":"audit-5A-F129","channelType":"SMS","status":1}
→ HTTP 200 / code 200 / message: "已创建"
→ traceId ...  new id = 5
```

### POST /mkt/task/{id}/start + /pause + /resume + /cancel

| 操作 | 响应 |
|---|---|
| start  | 200 / "已完成" |
| pause  | 200 / "已暂停" |
| resume | 200 / "已恢复" |
| cancel | 200 / "已取消" |

### GET /mkt/task/{id}/log

```
→ HTTP 200 / code 200 / message: "操作成功"
```

## 2. DB 交叉验证

```
mysql> SELECT id, campaign_name, status FROM marketing_touch_task WHERE id=5;
id | campaign_name      | status
5  | audit-5A-F129     | 6   ← 最终 cancelled
```

## 3. N13 I058 实证 — POST/GET 双路由形式

- `MktTouchTaskController.java:37 @PostMapping("/page")` + `:47 @GetMapping("/page")`
- 两种调用都成功 → **N13 I058 实证：双形式共存**
- 与 F125 journeyPage 同模式（POST/GET 双形式），但 journeyPage 因 queryForMap 500 崩，本端点正常

## 4. Final Result

**VERIFIED** — 8 端点全部 200，状态机 OK + N13 I058 双形式实证
