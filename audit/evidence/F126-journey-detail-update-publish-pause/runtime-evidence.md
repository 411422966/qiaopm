# F126 — MktJourney / 详情+更新+发布+暂停（Phase 5A 验证）

> **状态**：✅ VERIFIED  
> **验证时间**：2026-09-16 02:16 (UTC+8)  
> **验证方式**：API + DB + 静态  
> **Issue 关联**：无新 Issue

---

## 1. API 调用（4 端点 × id=4）

### 1.1 GET /mkt/journey/4（详情）

- **HTTP 状态**：200
- **响应 data.journey**：包含 id=4 全字段（journeyName / status / nodes / edges）
- **响应 data.nodes**：JSON 解析成功（n1=start, n2=sms）

证据：`raw-detail.txt`

### 1.2 PUT /mkt/journey/4（更新）

```
PUT http://localhost:8080/mkt/journey/4
Body: {"journeyName":"5A F124 旅程-已更新","description":"Phase 5A F126 update test","journeyType":"MARKETING"}
```

- **HTTP 状态**：200
- **响应 message**：`已更新`
- **响应 data.id**：4（成功）

证据：`raw-update.txt`

### 1.3 POST /mkt/journey/4/publish（发布）

- **HTTP 状态**：200
- **响应 message**：`已发布`
- **SQL**：`UPDATE mkt_journey SET status = 2, published_at = NOW() WHERE id = ?`

证据：`raw-publish.txt`

### 1.4 POST /mkt/journey/4/pause（暂停）

- **HTTP 状态**：200
- **响应 message**：`已暂停`
- **SQL**：`UPDATE mkt_journey SET status = 3 WHERE id = ?`

证据：`raw-pause.txt`

---

## 2. DB 断言

| id | journey_code | journey_name | status | published_at |
|---|---|---|---|---|
| 4 | 5A-F124-J1 | 5A F124 旅程-已更新 | **3**（最终暂停态）| **2026-09-16 02:16:57**（publish 时间戳）|

✅ publish → published_at 设置 ✅ pause → status=3 ✅

证据：`db-after.txt`

---

## 3. 静态分析（MarketingAutomationService.java:444-465）

```java
public Result<MktJourney> updateJourney(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    MktJourney old = journeyMapper.selectById(id);
    if (old == null) return Result.error(BizCode.NOT_FOUND, "旅程不存在");
    old.setJourneyName((String) body.getOrDefault("journeyName", old.getJourneyName()));
    ... 
    journeyMapper.updateById(old);
}

public Result<Void> publishJourney(@PathVariable Long id) {
    jdbc.update("UPDATE mkt_journey SET status = 2, published_at = NOW() WHERE id = ?", id);
}

public Result<Void> pauseJourney(@PathVariable Long id) {
    jdbc.update("UPDATE mkt_journey SET status = 3 WHERE id = ?", id);
}
```

⚠ **缺失**：update / publish / pause / detail 都**未校验 tenantId** — 与 I056 / I057 同模式风险（PARTIAL-NON-BLOCKER，登记不修）

---

## 4. Final Result

| 维度 | 状态 | 说明 |
|---|---|---|
| GET 详情 | ✅ | 200 + JSON 解析 nodes/edges |
| PUT 更新 | ✅ | 200 + name/desc 成功更新 |
| POST publish | ✅ | 200 + status=2 + published_at 设置 |
| POST pause | ✅ | 200 + status=3（最终态）|

**F126 — VERIFIED** ✅

---

证据清单：
- `raw-detail.txt` — GET 详情
- `raw-update.txt` — PUT 更新
- `raw-publish.txt` — POST publish
- `raw-pause.txt` — POST pause
- `db-after.txt` — DB 写后断言