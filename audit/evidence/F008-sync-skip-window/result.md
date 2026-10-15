# F008 — 手动 sync 窗口跳过

> Phase 5A | API+DB | P0
> 时间：2026-09-12 23:13 (UTC+8)

---

## 1. 验证方式

**API+DB**：POST `/crm-integration/{id}/sync`（无 force）+ 预置 `last_sync_at = NOW()` 模拟窗口内条件
（来源 `CrmIntegrationService.java:96-103`，依据 plan.md §1.1）

## 2. 测试条件

| 项 | 值 | 备注 |
|---|---|---|
| integrationId | 2 | F001 创建 |
| `last_sync_at` | `2026-09-12 23:13:08`（= NOW()）| SQL 手动设置 |
| `sync_window_minutes` 列 | **缺失** | I002 EXT-15 未跑 |
| `force` 参数 | 未传（默认 null）| — |

> plan §1.1 预期："shouldRunForManualTrigger 抛 → 返回异常（非 `skipped=true`）" — **反向证明 I002 / F008 PARTIAL-NON-BLOCKER**

## 3. 命令

### 3.1 预置窗口内条件

```sql
UPDATE crm_integration SET last_sync_at=NOW() WHERE id=2;
SELECT id, status, last_sync_at, NOW() AS server_now FROM crm_integration WHERE id=2;
```

完整：`audit/evidence/F008-sync-skip-window/db-setup-last-sync.txt`

| id | status | last_sync_at | server_now |
|---|---|---|---|
| 2 | ACTIVE | 2026-09-12 23:13:08 | 2026-09-12 23:13:08 |

→ 窗口内条件成立（last_sync_at + 0 ≤ now）。

### 3.2 调用 sync（无 force）

```bash
curl -X POST http://localhost:8080/crm-integration/2/sync \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

完整 raw：`audit/evidence/F008-sync-skip-window/raw-response.txt`

## 4. HTTP 响应

| 字段 | 值 |
|---|---|
| HTTP 状态 | **200** |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `517fbb98457643358d9fd7fd311f5c7d` |
| data | **null** |

## 5. 后端日志负向证据

完整：`audit/evidence/F008-sync-skip-window/log-context.txt`

```
2026-09-12 23:13:16.687 [http-nio-8080-exec-4] ERROR c.m.exception.GlobalExceptionHandler
    [traceId=517fbb98457643358d9fd7fd311f5c7d] [tenant=] [order=] [pay=] [DBError]
org.springframework.jdbc.BadSqlGrammarException:
### Error querying database.  Cause: java.sql.SQLSyntaxErrorException:
    Unknown column 'sync_token' in 'field list'
### SQL: SELECT id,tenant_id,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes FROM crm_integration WHERE id=?
```

## 6. DB 断言（sync 后）

完整：`audit/evidence/F008-sync-skip-window/db-after-sync.txt`

| 维度 | 实测 | 预期 |
|---|---|---|
| `crm_integration.id=2.status` | ACTIVE（未变）| 跳过时不变 ✅ |
| `crm_integration.id=2.last_sync_at` | 2026-09-12 23:13:08（= 预置值，未被 sync 覆盖）| plan §1.1："保持不变" ✅ |
| `crm_integration.id=2.updated_at` | 2026-09-12 23:13:08（未变）| 不变 ✅ |
| `crm_sync_log` | 0 行 | 跳过时**不应**写 sync_log ✅ |

## 7. ✅/⚠ 验证结论

### ❌ **核心负向证据：窗口判断到不了**

`shouldRunForManualTrigger` 应该判断 `last_sync_at + sync_window_minutes ≤ now` 并返回 `skipped=true`，
但 `selectById`（syncNow 第 88 行）在它之前就抛 `BadSqlGrammarException`，
**窗口判断永远没有机会执行**。

| 维度 | plan §三 预期 | 实测 |
|---|---|---|
| 返回 `skipped=true` | 应到不了（I002 抛错）| ❌ 直接抛 500 |
| 返回 `success + skipped=true + reason="not in cron window..."` | 应到不了 | ❌ 未返回 |
| `last_sync_at` 保持不变 | ✅ | ✅（实测保持 = 预置值）|
| `crm_sync_log` 不写 | ✅ | ✅（实测 0 行）|

### ⚠ I002 — DDL 缺列（直接抛错）

与 F005 完全相同的根因：MyBatis-Plus 自动生成的 SELECT 包含 9 个缺列。

### ⚠ I008 — catch 吞异常

返回 `code=500, message="数据库异常"` — 模糊消息，但 traceId 可查日志。

### ⚠ F008 行为语义丧失

由于 syncNow 第 88 行 selectById 抛错，`shouldRunForManualTrigger`（line 96-103）整段不可达。
即：
- 窗口判断失效（不知道什么时候该跳）
- 跨租户拒绝失效（F009 同理 — 在 selectById 之后才判断 tenant）
- mock adapter / Salesforce adapter 调用失效

整个 `syncNow` 入口在 DDL 修复前**完全不可用**。

## 8. 状态结论

| 维度 | 判定 |
|---|---|
| HTTP 行为 | ❌ BROKEN（code=500）|
| 窗口判断 (shouldRunForManualTrigger) | ❌ 不可达（selectById 前置抛错）|
| 窗口外跳过返回 skipped=true | ❌ 不可达 |
| 窗口内同步正常执行 | ❌ 不可达 |
| DB 副作用 | ✅ 不变（事务回滚 + 异常太早）|
| 与 plan.md §三 预期 | ⚠ **一致**（plan 也预期 I002 抛错，但 plan 标 PARTIAL-NON-BLOCKER，实测 BROKEN）|
| **最终状态** | **BROKEN**（端点完全不可用 + plan 严重度低估） |

## 9. 修复方向（不实施，仅登记）

🛠 同 F005 — 跑 `migrate_ext_15_crm_real.sql` 补齐 9 列 → selectById 恢复 → 窗口判断可执行

## 10. 副作用

- `crm_integration.id=2`: status=ACTIVE, last_sync_at=2026-09-12 23:13:08（保持我 SQL 预置的值）
- `crm_sync_log`: 0 行
- 测试用 integrationId：**2** 仍可用
