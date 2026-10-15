# F029 — Status 状态机（API+DB + I002 阻塞）

> **Phase**：5B Runtime Verification
> **Feature ID**：F029
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 1. 状态机设计 L0

文件：`backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java:97-160`

### 1.1 状态机路径

```java
public CrmSyncLog runIncremental(CrmIntegration cfg, String syncType) {
    // 1. RUNNING
    logRow.setStatus("RUNNING");
    syncLogMapper.insert(logRow);
    
    try {
        // 2. 拉取 + 写库 + 统计
        ...
        
        // 3a. 成功路径：ACTIVE
        cfg.setLastIncrementalSyncAt(LocalDateTime.now());
        cfg.setLastSyncAt(LocalDateTime.now());
        cfg.setStatus("ACTIVE");           // ← 强制 ACTIVE
        cfg.setErrorMessage(null);
        integrationMapper.updateById(cfg);
        
        logRow.setStatus("SUCCESS");
        ...
    } catch (Exception e) {
        // 3b. 失败路径：ERROR
        logRow.setStatus("FAILED");
        logRow.setErrorMessage(e.getMessage());
        syncLogMapper.updateById(logRow);
        cfg.setStatus("ERROR");            // ← 强制 ERROR
        cfg.setErrorMessage(e.getMessage());
        integrationMapper.updateById(cfg);
    }
}
```

### 1.2 状态机定义

| 状态 | 触发 | 副作用 |
|---|---|---|
| `RUNNING` | sync 开始 | logRow.insert |
| `ACTIVE` | sync 成功 | cfg.lastIncrementalSyncAt = now / status = ACTIVE / errorMessage = null |
| `ERROR` | sync 失败 | cfg.status = ERROR / cfg.errorMessage = exception |
| `SUCCESS` | sync 成功 | logRow.status = SUCCESS |
| `FAILED` | sync 失败 | logRow.status = FAILED / errorMessage = exception |

✅ **完整的状态机实现**

### 1.3 静态可达性 L0

| 路径 | 静态可达 | 运行时可达 |
|---|---|---|
| toggle 手动切 ACTIVE/DISABLED | ✅ CrmIntegrationService.java:67-77 | ❌ 被 I002 阻塞 |
| sync 成功 → ACTIVE | ✅ CrmSyncScheduler.java:138-141 | ❌ 被 I002 阻塞 |
| sync 失败 → ERROR | ✅ CrmSyncScheduler.java:155-158 | ❌ 被 I002 阻塞 |

→ **F029 静态完整 / 运行时全部不可达**

## 2. 实测：状态机失效

### 2.1 测试场景：ERROR → sync → ?

完整 evidence：`audit/evidence/F029-status-state-machine/`

| 步骤 | 操作 | traceId | 结果 |
|---|---|---|---|
| 1 | SQL `UPDATE ... status='ERROR', error_message='manual test (audit)' WHERE id=2` | — | ✅ 状态变为 ERROR |
| 2 | POST `/crm-integration/2/sync?force=1` | `6af58ca580ce45818d93c89c5761e4f6` | ❌ code=500（I002）|
| 3 | SQL `SELECT status, error_message FROM crm_integration WHERE id=2` | — | ⚠ **status 仍为 ERROR，error_message 仍为 'manual test (audit)'** |

完整 DB 状态：
- `db-setup-ERROR.txt` — 步骤 1 后状态
- `db-after-sync-trigger.txt` — 步骤 3 后状态
- `raw-sync-trigger.txt` — sync HTTP 响应

### 2.2 后端日志

完整：`audit/evidence/F029-status-state-machine/log-sync-context.txt`

```
org.springframework.jdbc.BadSqlGrammarException:
### SQL: SELECT id,tenant_id,platform,...,sync_token,...,sync_window_minutes
    FROM crm_integration WHERE id=?
### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'sync_token' in 'field list'
at SqlSessionTemplate.selectOne
at MybatisMapperMethod.execute
at $Proxy314.selectById
at CrmIntegrationService.syncNow(CrmIntegrationService.java:88)   ← I002 在 selectById 抛错
```

→ **sync 链路在 selectById 抛 I002 → runIncremental 永远不被调用 → 状态机失效**

### 2.3 DB 重置

完整：`audit/evidence/F029-status-state-machine/db-reset.txt`

```sql
UPDATE crm_integration SET status='ACTIVE', error_message=NULL WHERE id=2;
-- id status error_message
--  2 ACTIVE  NULL
```

## 3. ⚠️ 状态机失效的连锁影响

### 3.1 运维侧盲区

- **F007 dashboard catch 吞异常（I008）**：即使 status=ERROR，dashboard 也只会展示空 data
- **I008 catch 静默**：tick 7 次失败无任何告警，运维无法感知 status 应切 ERROR
- **前端无 status 切换 UI**：即便 status=ERROR，前端只能看到 ACTIVE 状态

### 3.2 业务侧盲区

| 场景 | 当前 | 影响 |
|---|---|---|
| Salesforce 真实凭据错误 | status 应切 ERROR | 不会切（被 I002 阻塞）|
| Salesforce OAuth2 失败 | status 应切 ERROR | 不会切（被 I002 阻塞）|
| 网络超时 | status 应切 ERROR | 不会切（被 I002 阻塞）|
| 限流 / OVER_QUERY_LIMIT | status 应切 ERROR | 不会切（被 I002 阻塞）|

→ **所有失败场景均无法被 status 状态机捕获**

### 3.3 跨 Feature 影响

- F004 toggle API 不可用（I002）
- F005/F008/F010 同步失败 status 不变
- F007 dashboard 显示空 data
- F012 known-ids 不加载
- F020 crm_contact_map 永远无数据
- F021 crm_sync_log 永远无 SUCCESS/FAILED 行

→ **I002 是 F029 失效的根因**

## 4. ✅/⚠ 验证结论

| 维度 | 状态 |
|---|---|
| 状态机静态实现 | ✅ 完整（ACTIVE / ERROR 路径）|
| 状态机定义一致性 | ✅ 与 EXT-24 §4 一致 |
| toggle API 可达 | ❌ I002 阻塞（5B F004 已测）|
| sync 成功 → ACTIVE | ❌ I002 阻塞 |
| sync 失败 → ERROR | ❌ I002 阻塞 |
| DB 持久化 | ❌ 任何状态变更都到不了 DB |
| crm_sync_log 写入 | ❌ 0 行（事务回滚生效）|

### **最终状态**：❌ **BROKEN**

（状态机静态完整 / 运行时全部失效 / 根因 = I002）

## 5. 5B 继承式策略影响

- F029 = BROKEN（I002 强依赖）
- 不被继承（独立验证）
- 与 F002 / F005 / F008 / F010 / F012 同根因

## 6. 关联

- `audit/issues/issue-register.md` §十 I002
- `audit/issues/I002-crm-ddl-missing.md`（待建）
- `audit/runtime/plan.md` §一.2 F029
- `audit/runtime/report-5A.md` §0.3 关键发现 2（I008 catch 静默 + 状态机盲区）
- `audit/evidence/F004-toggle-enum-drift/result.md`（F004 toggle API 不可用）
