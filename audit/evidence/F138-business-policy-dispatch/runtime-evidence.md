# F138 — BusinessPolicyDispatchController 4 表 MISSING 实证

> **运行时间**：2026-09-16 23:25 (UTC+8)
> **关联 Issue**：I063 CRITICAL-P2（4 张 MKT 表 Entity + Mapper 已建，表从未 CREATE）
> **关联文件**：`BusinessPolicyDispatchController.java` / `BusinessPolicyDispatchServiceImpl.java` / `MktDispatch.java` / `MktDispatchTarget.java` / `MktInvitation.java` / `MktInvitationRule.java`

## 一、4 张 MISSING 表清单（I063）

| # | 表名 | Entity | Mapper | 状态（information_schema 实测） |
|---|---|---|---|---|
| 1 | `mkt_dispatch` | `MktDispatch.java` | `MktDispatchMapper` | **不存在** |
| 2 | `mkt_dispatch_target` | `MktDispatchTarget.java` | `MktDispatchTargetMapper` | **不存在** |
| 3 | `mkt_invitation` | `MktInvitation.java` | `MktInvitationMapper` | **不存在** |
| 4 | `mkt_invitation_rule` | `MktInvitationRule.java` | `MktInvitationRuleMapper` | **不存在** |

> 注：`BusinessPolicyDispatchServiceImpl` + `P1RemainingService` / `P2FeaturesService` 通过 `JdbcTemplate.update("UPDATE mkt_xxx ...")` **直写 SQL**（不经过 MyBatis Mapper），调任何 `/m12/dispatch/*` 端点都期望 `InvalidDataAccessResourceUsageException: Table 'meiyetec.mkt_xxx' doesn't exist`。

## 二、Controller 端点（9 个）

| 端点 | HTTP | Phase 5B 实测 | DB 验证 |
|---|---|---|---|
| `/m12/dispatch` | POST | — | — |
| `/m12/dispatch/{id}/publish` | POST | — | — |
| `/m12/dispatch/{id}/publish-async` | POST | — | — |
| `/m12/dispatch/{id}/cancel` | POST | — | — |
| `/m12/dispatch/page` | GET | **500 数据库异常**（traceId b43a5aaa1fa14da6930da0e5bd56f484）| mkt_dispatch 不存在 |
| `/m12/dispatch/{id}` | GET | — | — |
| `/m12/dispatch/{id}/targets` | GET | — | — |
| `/m12/dispatch/{id}/logs` | GET | — | — |
| `/m12/dispatch/stats` | GET | **500 数据库异常**（traceId 53b79bd7544344acb9c58174f9a40b55）| mkt_dispatch 不存在 |

> 仅测 2 个无 id 端点（page / stats）— 任意带 id 的端点（如 `/m12/dispatch/1`）会先过 `ensureTenantOwnership(id)` → `dispatchService.getById(id)` → 同样 SELECT mkt_dispatch 抛 TableNotFoundException。**任一端点 500 是预期行为**。

## 三、实证

### 3.1 GET /m12/dispatch/page

```bash
curl -X GET 'http://localhost:8080/m12/dispatch/page' -H "Authorization: Bearer $adminToken"
```

**响应**：

```json
{
  "code": 500,
  "message": "数据库异常",
  "data": null,
  "traceId": "b43a5aaa1fa14da6930da0e5bd56f484",
  "timestamp": "2026-09-16 23:25:18"
}
```

### 3.2 GET /m12/dispatch/stats

```bash
curl -X GET 'http://localhost:8080/m12/dispatch/stats' -H "Authorization: Bearer $adminToken"
```

**响应**：

```json
{
  "code": 500,
  "message": "数据库异常",
  "data": null,
  "traceId": "53b79bd7544344acb9c58174f9a40b55",
  "timestamp": "2026-09-16 23:25:19"
}
```

### 3.3 DB 验证（4 表 0 匹配）

```sql
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'meiyetec' AND table_name IN (
  'mkt_invitation', 'mkt_invitation_rule', 'mkt_dispatch', 'mkt_dispatch_target'
);
-- 0 rows
```

> 见 `db-table-missing.txt`

## 四、链路分析

```
BusinessPolicyDispatchController.page()
  └─ dispatchService.page(pg, q)
       └─ MyBatis-Plus BaseMapper.selectPage(pg, q)
            └─ SELECT * FROM mkt_dispatch WHERE tenant_id=? AND deleted=0
                 → Table 'meiyetec.mkt_dispatch' doesn't exist
                      → InvalidDataAccessResourceUsageException
                           → GlobalExceptionHandler → Result.error(500, "数据库异常")
```

## 五、5A 已实证 + 5B 补强

| 项 | 5A 实证 | 5B 补强 |
|---|---|---|
| 4 表 0 匹配 | ✅ `tmp-final-verify.sql` | ✅ `db-table-missing.txt` 重新落档 |
| Controller 端点 500 | ✅ `tmp-final-verify.sql` 含 dispatch/page | ✅ **5B 新增**：`/m12/dispatch/page` + `/m12/dispatch/stats` 两个无 id 端点实测 500 |
| traceId 可追溯 | — | ✅ 2 个 traceId（b43a5aaa1fa14da6930da0e5bd56f484 + 53b79bd7544344acb9c58174f9a40b55）|

## 六、F138 Result

| 项 | 值 |
|---|---|
| **Result** | **PARTIAL-BLOCKER（4 表 MISSING 阻塞 M12 业务入口）** |
| **CRITICAL** | ✅ I063 沿用 |
| **5B 实证** | ✅ 2 端点 500 + 4 表 0 匹配 |
| **migrate_mkt_002_drop_market_activity.sql** | ⚠ 未跑（market_activity 仍存在 — F139 实证）|

## 七、Evidence 文件清单

- `runtime-evidence.md`（本文件）
- `raw-dispatch-page.json`（500 + traceId b43a5aaa1fa14da6930da0e5bd56f484）
- `raw-dispatch-stats.json`（500 + traceId 53b79bd7544344acb9c58174f9a40b55）
- `db-table-missing.txt`（4 表 information_schema 0 匹配）
