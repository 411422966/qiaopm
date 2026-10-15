# I073 — `MarketingAutomationService.journeyPage` COUNT 查询 args 共享导致 SQLException（POST+GET 双形式同中招）

> **发现时间**：2026-09-16 02:48 (UTC+8)
> **Phase**：5A — Runtime Verification
> **关联 Feature**：F125（MktJourney / 旅程分页 POST/GET 双形式）
> **严重度**：🟠 **HIGH**（双形式分页 100% 500 — 阻塞旅程列表）
> **类别**：SQL 参数异常 + I058 双形式同 bug
> **修复阶段**：**Post-Audit**（Phase 7）

---

## 1. 摘要

`MarketingAutomationService.journeyPage()` 在 `queryForList`（SELECT records）+ `queryForObject`（COUNT total）之间共享 `args` 列表（含 `pageSize + offset`），但 COUNT SQL 只需 `tenantId` 占位符 — 多余参数触发 `IncorrectArgumentCountException` / `SQLException` → 500。

| 维度 | 值 |
|---|---|
| **触发条件** | `POST /mkt/journey/page` body `{"pageNum":1,"pageSize":10}` 或 `GET /mkt/journey/page?pageNum=1&pageSize=10` |
| **失败现象** | HTTP 200 / code 500 / message="数据库异常"（POST + GET 双形式同 bug）|
| **根本原因** | `args.toArray()` 在 COUNT 查询时绑定多余参数（pageSize + offset）→ SQL 异常 |
| **影响范围** | F125 全部分页调用 + I058 同模式 F129（但 F129 实证 OK — 服务实现差异）|
| **批次元数据** | Batch 1.5（Phase 5A 实证发现） |

---

## 2. 实证证据（Phase 5A）

### 2.1 运行时实证 — POST 形式

```
POST http://localhost:8080/mkt/journey/page
Body: {"pageNum":1,"pageSize":10}
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 24fa90f303ab4b168e41cea3502c9edf
```

### 2.2 运行时实证 — GET 形式

```
GET http://localhost:8080/mkt/journey/page?pageNum=1&pageSize=10
→ HTTP 200 / code 500 / message: "数据库异常"
→ traceId 8ef64519eb52420d8df952e066b43b3d
```

证据文件：
- `audit/evidence/F125-journey-page/raw-response-post.json`（POST 500）
- `audit/evidence/F125-journey-page/raw-response-get.json`（GET 500）

### 2.3 代码位置

`backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java:467-496`：

```java
List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
// args 含 [tenantId, ?, ?]（pageSize + offset 占位）→ SELECT 正常
Long total = jdbc.queryForObject(csql.toString(), Long.class, args.toArray());
// COUNT SQL 形如 "SELECT COUNT(*) FROM mkt_journey WHERE tenant_id = ? AND deleted = 0"
// 但 args 含 3 个参数 → COUNT SQL 只需 1 个 → SQLException
```

---

## 3. N13 I058 关联

F129（MktTouchTask）的 page 端点同样有 POST/GET 双形式 + 同样 args 共享模式，但 Phase 5A 实证 **F129 双形式都 200 + total=1 一致**（同 Service 内的 touchTaskPage 实现略有差异 — args 不复用）。

| Feature | page 端点 | Phase 5A 实证 |
|---|---|---|
| F125 journeyPage | POST + GET 都 500 | ❌ 阻塞 |
| F129 touchTaskPage | POST + GET 都 200 + total=1 | ✅ OK |

→ **服务实现差异**：journeyPage 用 `args.toArray()` 全复用 → bug；touchTaskPage 可能拆出独立的 COUNT args → OK

---

## 4. 影响 Feature

| Feature ID | Module / 功能点 | 当前 Result | 修复后 Result |
|---|---|---|---|
| **F125** | MktJourney / 旅程分页（双形式）| **UNVERIFIED**（POST+GET 双 500 — 跑不通）| VERIFIED（修复 args → 只传 [tenantId]）|

---

## 5. 修复建议（Post-Audit）

```java
// 方案 1：COUNT 单独拼 args
List<Object> countArgs = new ArrayList<>();
countArgs.add(tenantId);  // 只传 COUNT 需要的参数
Long total = jdbc.queryForObject(csql.toString(), Long.class, countArgs.toArray());

// 方案 2：COUNT SQL 用静态 + 单独条件拼接（不依赖 args）
String csql = "SELECT COUNT(*) FROM mkt_journey WHERE tenant_id = " + tenantId + " AND deleted = 0";
Long total = jdbc.queryForObject(csql, Long.class);
```

---

## 6. 关联文档

- `audit/evidence/F125-journey-page/raw-response-post.json` — POST 500
- `audit/evidence/F125-journey-page/raw-response-get.json` — GET 500
- `audit/evidence/F125-journey-page/result.md` — F125 Phase 5A 验证报告
- `audit/runtime/report-5A-batch-1.5.md` §一 F125 + §三 I073 NEW + §二 N13
- `audit/runtime/plan-batch-1.5.md` §三.3.1 F125 + §五.5 N13