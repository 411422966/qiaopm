# F010 — 自动 tick 扫描

> Phase 5A | API+DB（观察）| P0
> 时间：2026-09-12 23:19 (UTC+8)

---

## 1. 验证方式

**API+DB（观察）**：`@Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}", initialDelay = 60_000L)` 每 5 分钟扫一次 `status=ACTIVE + sync_enabled=1` 的集成
（来源 `CrmSyncScheduler.java:47-56`，依据 plan.md §1.1）

> ⚠ Phase 5 不可加速排程 — 通过 DB 断言 + 后端日志观察副作用

## 2. 关键日志证据

### 2.1 多次 tick 失败记录（间隔 5 分钟）

完整：`audit/evidence/F010-auto-tick-scan/log-crm-scheduler-all.txt`

| # | 时间戳 | 线程 | 级别 | 消息 |
|---|---|---|---|---|
| 1 | 22:49:26.468 | scheduling-1 | WARN | 拉取集成列表失败 |
| 2 | 22:54:26.483 | scheduling-1 | WARN | 拉取集成列表失败 |
| 3 | 22:59:26.507 | scheduling-1 | WARN | 拉取集成列表失败 |
| 4 | 23:04:26.524 | scheduling-1 | WARN | 拉取集成列表失败 |
| 5 | 23:09:26.533 | scheduling-1 | WARN | 拉取集成列表失败 |
| 6 | 23:14:26.551 | scheduling-1 | WARN | 拉取集成列表失败 |
| 7 | 23:19:26.568 | scheduling-1 | WARN | 拉取集成列表失败 |

**7 次连续 tick，全部失败，间隔严格 300s = 5 分钟**（与 `crm.sync.tick-interval-ms:300000` 配置一致）。

### 2.2 完整 stack（含 SQL）

完整：`audit/evidence/F010-auto-tick-scan/log-crm-scheduler-context.txt`

```
2026-09-12 23:19:26.568 [scheduling-1] WARN  com.meiyetec.crm.CrmSyncScheduler
    [traceId=] [tenant=] [order=] [pay=] [CRM-Scheduler] 拉取集成列表失败：
### Error querying database.  Cause: java.sql.SQLSyntaxErrorException:
    Unknown column 'sync_token' in 'field list'
### SQL: SELECT  id,tenant_id,platform,...,sync_token,sync_schedule,
    sync_enabled,last_incremental_sync_at,private_key,username,login_url,
    object_mappings,sync_window_minutes
    FROM crm_integration
    WHERE  (status = ? AND sync_enabled = ?)
### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'sync_token'
```

## 3. DB 断言（7 次 tick 后）

完整：`audit/evidence/F010-auto-tick-scan/db-after-tick.txt`

| id | tenant_id | status | last_sync_at | 备注 |
|---|---|---|---|---|
| 1 | 1 | ACTIVE | NULL | 历史数据 |
| 2 | 1 | ACTIVE | 2026-09-12 23:13:08 | F001 创建 + F008 我手动设的 |
| 3 | 2 | ACTIVE | NULL | F009 SQL 创建 |

| 维度 | 实测 | plan §三 预期 |
|---|---|---|
| `crm_sync_log` | **0 行** | 应有 INCREMENTAL 行 ❌ |
| 任何 integration.status=ERROR | **0 行** | 失败应切 ERROR ❌（但 plan 没说）|
| 任何 integration.error_message | **全部 NULL** | 应写错误信息 ❌ |

## 4. ✅/⚠ 验证结论

### ❌ **核心负向证据：tick 静默失败**

7 次连续 tick，全部 WARN 级别抛出后被吞掉 — **没有任何业务副作用**：

| 维度 | plan §三 预期 | 实测 |
|---|---|---|
| 抛 `Unknown column 'sync_enabled'` | ✅ | ✅（实测抛 `Unknown column 'sync_token'`，但 WHERE 子句确认含 `sync_enabled`）|
| catch 吞异常 | ✅ | ✅（WARN 级别）|
| tick 静默失败 | ✅ | ✅（无任何 DB 副作用）|
| `crm_sync_log` 有 INCREMENTAL 行 | 应无 ✅ | ✅（实测 0 行）|
| `crm_integration.status` 切 ERROR | — | ❌（实测 3/3 都是 ACTIVE）|

### ⚠ I002 — DDL 缺列

tick 触发的 SQL：
```
SELECT ... FROM crm_integration WHERE (status = ? AND sync_enabled = ?)
```
- `sync_enabled` 列缺失 → MyBatis-Plus 自动生成 SELECT 含 `sync_token` → 先抛 `sync_token`
- `sync_enabled` 的缺列影响被前一个缺列屏蔽（chain reaction）

### ⚠ I008 — catch 吞异常

`CrmSyncScheduler.runIncremental` 内部 catch `Exception`，输出 WARN 日志但**没有 traceId、没有 metric、没有 ERROR 告警**。
**生产环境将持续 5 分钟一次失败告警，无人感知**。

### ⚠ **与 plan §三 预期细节偏差**

| 维度 | plan 预期 | 实测 | 偏差 |
|---|---|---|---|
| 抛错列 | `Unknown column 'sync_enabled'` | `Unknown column 'sync_token'` | plan 列名有偏差，但链 reaction 一致 |
| `crm_sync_log` 应有 INCREMENTAL 失败行 | plan §三说"应有" | 实测 0 行 | **更严重** — tick 连 RUNNING 都没写就抛了 |
| `crm_integration.status` 切 ERROR | plan §三没明确说 | 实测 3/3 都 ACTIVE | 不影响 — 反向证明 tick 静默 |

## 5. 静态代码可达性（L0 证据）

`backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java`：

```java
@Scheduled(fixedDelayString = "${crm.sync.tick-interval-ms:300000}",
            initialDelay = 60_000L)
public void tick() {
    try {
        List<CrmIntegration> list = crmIntegrationMapper.selectList(
            new LambdaQueryWrapper<CrmIntegration>()
                .eq(CrmIntegration::getStatus, "ACTIVE")
                .eq(CrmIntegration::getSyncEnabled, 1)   // ← I002 缺列触发
        );
        for (CrmIntegration cfg : list) {
            runIncremental(cfg, "INCREMENTAL");
        }
    } catch (Exception e) {                            // ← I008 catch 吞
        log.warn("[CRM-Scheduler] 拉取集成列表失败：", e);
    }
}
```

## 6. 状态结论

| 维度 | 判定 |
|---|---|
| 排程触发 | ✅ VERIFIED（5 分钟间隔，7 次连续）|
| 排程执行成功 | ❌ BROKEN（全部失败）|
| 异常可观测 | ⚠ PARTIAL（WARN 日志，但无 traceId/无 metric）|
| 静默失败风险 | ❌ **生产风险高**（无监控告警）|
| 与 plan.md §三 预期 | ⚠ plan §三 标"反向证明 I002"，实测**反向证明 I002 + I008 catch 吞** |
| **最终状态** | **BROKEN**（tick 完全失效 + 静默失败） |

## 7. 修复方向（不实施，仅登记）

🛠 跑 `migrate_ext_15_crm_real.sql` 补齐 9 列 → tick selectList 恢复
🛠 把 catch 改为 throw — 让 @Scheduled 抛错到 TaskScheduler → 至少产生 ERROR 日志
🛠 加 `crm_scheduler_tick_failed_total` metric + Prometheus 告警
🛠 把 "拉取集成列表失败" WARN 改为 ERROR 级别（业务上是失败）

## 8. 副作用

- `crm_integration`: 3 行全部 status=ACTIVE（tick 没切 ERROR）
- `crm_sync_log`: 0 行（tick 没写 RUNNING 行）
- `crm_contact_map`: 仍不存在（F012 验证）
- 现有测试用 integrationId：1 / 2 / 3 共存
