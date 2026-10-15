# F005 — 手动触发同步 ⭐

> Phase 5A | API+DB | P0
> 时间：2026-09-12 23:11 (UTC+8)

---

## 1. 验证方式

**API+DB**：POST `/crm-integration/{id}/sync?force=0|1`（来源 `CrmIntegrationService.java:88`，依据 plan.md §1.1）

## 2. 命令

```bash
# 无 force
curl -X POST http://localhost:8080/crm-integration/2/sync \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# force=1
curl -X POST "http://localhost:8080/crm-integration/2/sync?force=1" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

完整 raw：
- `audit/evidence/F005-manual-sync/raw-response-force-null.txt`
- `audit/evidence/F005-manual-sync/raw-response-force1.txt`

## 3. HTTP 响应

| 调用 | HTTP 状态 | code | message | traceId |
|---|---|---|---|---|
| `POST /sync` (force=null) | 200 | **500** | **"数据库异常"** | `6d88f7fb85f34ab8a078568f2ccdaa62` |
| `POST /sync?force=1` | 200 | **500** | **"数据库异常"** | `04ce23413980482594e81bd2f9d73801` |

> HTTP 200 但 code=500 — I008 catch 吞异常的标准响应包装。

## 4. 后端日志负向证据

### 4.1 force=null（traceId=`6d88f7fb...`）

完整：`audit/evidence/F005-manual-sync/log-context-force-null.txt`

```
at com.meiyetec.service.CrmIntegrationService.syncNow(CrmIntegrationService.java:88)
at $Proxy314.selectById
SQL: SELECT id,tenant_id,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes 
    FROM crm_integration WHERE id=?
### Cause: java.sql.SQLSyntaxErrorException:
    Unknown column 'sync_token' in 'field list'
```

### 4.2 调用链（force=null）

```
DispatcherServlet
  → CrmIntegrationService.syncNow:88            ← 入口
  → $Proxy314.selectById                        ← MyBatis-Plus
  → SqlSessionTemplate.selectOne
  → MyBatisMapperMethod.execute
  → java.sql.SQLSyntaxErrorException:
      Unknown column 'sync_token' in 'field list'
  → BadSqlGrammarException
  → GlobalExceptionHandler [DBError]
  → Result.error(500, "数据库异常")
```

### 4.3 force=1（traceId=`04ce2341...`）

日志结构与 force=null **完全一致** — force 参数在 selectById 阶段根本没机会生效。

## 5. DB 断言（sync 触发后）

完整：`audit/evidence/F005-manual-sync/db-after-sync.txt`

| 表 | 查询 | 实测值 | 预期 |
|---|---|---|---|
| `crm_integration.id=2` | status | `ACTIVE`（未切 ERROR）| plan §三：失败应切 ERROR |
| `crm_integration.id=2` | last_sync_at | `NULL` | plan §三：保持不变 ✅ |
| `crm_integration.id=2` | updated_at | `NULL` | 未更新 ✅ |
| `crm_sync_log` | COUNT | **0** | plan §三：应有 FAILED 行 ❌ |

### 5.1 ⚠ 与 plan §三 预期偏差

| 维度 | plan §三 预期 | 实测 | 偏差 |
|---|---|---|---|
| `last_incremental_sync_at` 保持不变 | ✅ | ✅（实际是 `last_sync_at`）| plan 列名错（应是 `last_sync_at`），但语义一致 |
| `crm_sync_log` 有 FAILED 行 | ✅ | ❌ **0 行** | **未写入** — syncNow 抛在 selectById，事务回滚 |
| `crm_integration.status` 切 ERROR | plan §三没预期 | ❌ **保持 ACTIVE** | 未切 — 异常太早抛出 |

## 6. ✅/⚠/🛠 验证结论

### ❌ **核心负向证据**

`Unknown column 'sync_token'` 在 MyBatis-Plus 自动生成的 SELECT 子句中触发 → `syncNow` 整体不可用。

| 影响范围 | 详细 |
|---|---|
| `syncNow` 入口 | ❌ 第 88 行 `selectById` 抛错 |
| `shouldRunForManualTrigger` 窗口判断 | ❌ 永远到不了（抛在它前面） |
| 真实同步链路 | ❌ 永远到不了 |
| `crm_sync_log` 写入 | ❌ 事务回滚，0 行 |

### ⚠ I002 — DDL 缺列（直接抛错）

MyBatis-Plus 自动生成的 SELECT 包含 9 个 EXT-15 缺失列，第一个缺列 `sync_token` 触发 `BadSqlGrammarException`。

### ⚠ I008 — catch 吞异常（Fake Success 链证据）

`GlobalExceptionHandler` 把 `BadSqlGrammarException` 包成 `code=500, message="数据库异常"`：
- 前端只看到模糊 "数据库异常"，无 traceId 时根因难定位
- 实测 traceId 在 body 中返回（✅ 至少可查日志）

### ⚠ I009 — 事务回滚生效（**正面**证据）

syncNow 的 `@Transactional` 生效：异常抛出 → 事务回滚 → DB 副作用全清零。
但这是因为抛错太早，根本没触达"故意不写 last_incremental_sync_at 保留上次成功时间"的逻辑。

## 7. 状态结论

| 维度 | 判定 |
|---|---|
| HTTP 行为 | ❌ BROKEN（code=500）|
| syncNow 可用性 | ❌ 完全不可用 |
| 窗口判断 (shouldRun) | ❌ 抛错前置，到不了 |
| 真实同步 | ❌ 到不了 |
| 与 plan.md §三 预期 | ⚠ 部分超出（plan 预期 I009 事务保留，实际异常太早事务回滚没东西可保留）|
| **最终状态** | **BROKEN**（端点抛异常 + 无副作用） |

## 8. 修复方向（不实施，仅登记）

🛠 跑 `migrate_ext_15_crm_real.sql` 补齐 9 列 → selectById 自动恢复（D5 决策暂不跑）
🛠 或者在 `CrmIntegrationMapper` 用 `@Select` 显式指定列
🛠 或者在 GlobalExceptionHandler 把 `BadSqlGrammarException` 单独归类返回

## 9. 副作用

- `crm_integration.id=2`: status=ACTIVE, last_sync_at=NULL, updated_at=NULL（未变）
- `crm_sync_log`: 0 行（事务回滚生效）
- 测试用 integrationId：**2** 仍可用
