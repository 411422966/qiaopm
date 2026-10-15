# F002 — 集成列表

> Phase 5A | API-only | P0
> 时间：2026-09-12 23:10 (UTC+8)

---

## 1. 验证方式

**API-only**：GET `/crm-integration/list`（来源 `CrmIntegrationService.java:55`，依据 plan.md §1.1）

## 2. 命令

```bash
curl -i http://localhost:8080/crm-integration/list \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

完整 raw：`audit/evidence/F002-list-integration/raw-response.txt`

## 3. HTTP 响应

| 字段 | 值 |
|---|---|
| HTTP 状态 | **200** |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `f589a40454e8479c8b678effb821827a` |
| data | **null** |

> ⚠ **HTTP 200 但 code=500**：这是 EXT-24 §0.1 的标准响应包装格式 — 后端用 `Result.error(...)` 把异常装到 200 body。前端需要检查 `code` 而非 HTTP 状态码。

## 4. 后端日志负向证据（traceId 全文）

完整 stack：`audit/evidence/F002-list-integration/log-trace-context.txt`

**核心异常**：
```
org.springframework.jdbc.BadSqlGrammarException:
### Error querying database.  Cause: java.sql.SQLSyntaxErrorException:
    Unknown column 'sync_token' in 'field list'
### SQL: SELECT  id,tenant_id,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes
    FROM crm_integration
    WHERE  (tenant_id = ?)
    ORDER BY id DESC
### The error may exist in com/meiyetec/mapper/CrmIntegrationMapper.java
    at CrmIntegrationService.list(CrmIntegrationService.java:55)
```

## 5. ✅/⚠/🛠 验证结论

### ❌ **关键发现**（与 plan.md §三 预期对比）

| 维度 | plan.md §1.1 预期 | Phase 5A 实测 | 偏差 |
|---|---|---|---|
| HTTP 行为 | ✅ 可读 + id 校验 | ❌ **HTTP 200 + code=500 + data=null** | **升级为 BLOCKER** |
| 触发列缺失 | I002 silent（缺 9 列） | ❌ **MyBatis-Plus selectList 抛错**（`Unknown column 'sync_token'`） | **更严重** |
| 是否能列出 id=2 | 应该能 | ❌ **不能** — list 完全失败 | list 整个端点不可用 |

### ⚠ 升级的负向证据链

**Plan 阶段评估**：
- F002 plan §1.1 标 "I002（缺 9 列 silent）" — 预期 list 能返回，缺列 silent
- §二 F002 行标注 "—" （Phase 5 预期暴露 Issue 列空）

**Phase 5A 实测**：
1. **MyBatis-Plus 自动生成 SELECT** 时包含 `sync_token, sync_schedule, sync_enabled, last_incremental_sync_at, private_key, username, login_url, object_mappings, sync_window_minutes` 共 9 列
2. 数据库缺 `sync_token` → 抛 `BadSqlGrammarException`
3. `GlobalExceptionHandler` 捕获 → 返回 `code=500, message="数据库异常"`（**I008 catch 吞异常**）
4. 端点 **完全不可用**

### 5.1 I002 — DDL 缺列（直接抛错，未 silent）

| 缺失列 | 影响端点 |
|---|---|
| `sync_token` | **直接抛 `BadSqlGrammarException`** — selectList 失败 |
| `sync_schedule` / `sync_enabled` / `last_incremental_sync_at` / `private_key` / `username` / `login_url` / `object_mappings` / `sync_window_minutes` | 同上（只要 SELECT 触发必中）|

### 5.2 I008 — catch 吞异常（Fake Success 链）

`GlobalExceptionHandler` 把 `BadSqlGrammarException` 包成 `code=500, message="数据库异常"`：
- 异常被吞掉，不传 traceId 对应的 SQL/列名到前端
- 前端调试只能看到 "数据库异常" — 需要查后端日志才知道根因
- 实际**不是 I008 的 Fake Success（success=true）**，而是 **I008 的 catch 吞异常（success=false + 模糊 message）**

### 5.3 调用链（栈）

```
DispatcherServlet
  → CrmIntegrationService.list(CrmIntegrationService.java:55)
  → $Proxy314.selectList (MyBatis-Plus)
  → SqlSessionTemplate.selectList
  → MyBatisMapperMethod.executeForMany
  → java.sql.SQLSyntaxErrorException: Unknown column 'sync_token'
  → BadSqlGrammarException
  → GlobalExceptionHandler [DBError] → code=500, message="数据库异常"
```

## 6. 状态结论

| 维度 | 判定 |
|---|---|
| HTTP 行为 | ❌ **BROKEN**（code=500） |
| 端点可用性 | ❌ **端点完全不可用** |
| 与 plan.md §三 预期 | ⚠ **超出预期**（plan 标 silent，实测抛 500） |
| **最终状态** | **BROKEN**（端点抛异常，无 I002 silent — 比 PARTIAL-BLOCKER 更严重） |

## 7. 副作用

- 无 DB 副作用（异常在 SELECT 阶段抛出，未触达 INSERT/UPDATE）
- `crm_integration` 行数仍为 **2**（F001 创建的 id=2 + 历史 id=1）
- 但**前端 UI 无法加载列表** — 这是 F024/F025 也无法工作的根因

## 8. 修复方向（不实施，仅登记）

🛠 跑 `migrate_ext_15_crm_real.sql` 补齐 9 列 → F002 自动恢复（D5 决策暂不跑）
🛠 或者在 `CrmIntegrationMapper` 用 `@Select` 显式指定列（绕过 MyBatis-Plus 自动生成）
🛠 或者在 GlobalExceptionHandler 把 `BadSqlGrammarException` 单独归类返回（不要统一"数据库异常"）
