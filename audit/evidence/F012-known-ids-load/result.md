# F012 — known-ids 差集加载

> Phase 5A | DB-only | P0
> 时间：2026-09-12 23:21 (UTC+8)

---

## 1. 验证方式

**DB-only**：`CrmSyncScheduler.loadKnownExternalIds` (CrmSyncScheduler.java:174-194)
依据 plan.md §1.1 / §三：

> 预期 Phase 5 暴露 I002/F023：表 `crm_contact_map` 缺失 → `List` 抛 `BadSqlGrammarException` → catch 吞 → 返回 empty Set → 失去差集去重

## 2. DB 断言

### 2.1 表存在性

完整：`audit/evidence/F012-known-ids-load/db-table-check.txt`

| 查询 | 结果 |
|---|---|
| `SHOW TABLES LIKE 'crm_contact_map'` | 0 行 |
| `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='meiyetec' AND table_name='crm_contact_map'` | **0** |
| `DESCRIBE crm_contact_map` | **ERROR 1146 (42S02)**: Table 'meiyetec.crm_contact_map' doesn't exist |

### 2.2 影响传播

如果 `loadKnownExternalIds` 被调用到，会执行：
```java
LambdaQueryWrapper<CrmContactMap> w = new LambdaQueryWrapper<>();
w.eq(CrmContactMap::getTenantId, tenantId)
 .eq(CrmContactMap::getPlatform, platform)
 .last("LIMIT " + KNOWN_IDS_LIMIT);
List<CrmContactMap> list = crmContactMapMapper.selectList(w);  // ← 表不存在抛 BadSqlGrammarException
```

→ 实际抛错类型：`Table 'meiyetec.crm_contact_map' doesn't exist`（MySQL Error 1146）

## 3. 日志观察（triggered tick）

### 3.1 `loadKnownExternalIds` 调用痕迹

完整：`audit/evidence/F012-known-ids-load/log-known-method-search.txt`

| 关键词 | 命中 |
|---|---|
| `loadKnown` | 0 |
| `knownIds` | 0 |
| `known_ids` | 0 |
| `差集` | 0 |
| `known_external_ids` | 0 |

→ **日志中完全无 loadKnownExternalIds 相关调用记录**

### 3.2 根因：I002 前置阻塞

F010 显示 tick 在 `selectList(integration)` 就抛 `Unknown column 'sync_token'`，**根本走不到 `loadKnownExternalIds`**。

→ F012 在运行时**完全没机会执行**：
- 静态代码可达 L0
- 运行时不可达（被 I002 在更早阶段阻塞）

## 4. 静态代码可达性（L0 证据）

`backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java:174-194`：

```java
private Set<String> loadKnownExternalIds(CrmIntegration cfg) {
    Set<String> known = new HashSet<>();
    try {
        LambdaQueryWrapper<CrmContactMap> w = new LambdaQueryWrapper<>();
        w.eq(CrmContactMap::getTenantId, cfg.getTenantId())
         .eq(CrmContactMap::getPlatform, cfg.getPlatform())
         .last("LIMIT " + KNOWN_IDS_LIMIT);
        List<CrmContactMap> list = crmContactMapMapper.selectList(w);  // ← 抛 BadSqlGrammarException
        for (CrmContactMap m : list) {
            known.add(m.getExternalId());
        }
    } catch (Exception e) {                                            // ← I008 catch 吞
        log.warn("[CRM-Scheduler] 加载 known external ids 失败", e);
        return new HashSet<>();   // ← 返回 empty Set
    }
    return known;
}
```

## 5. ✅/⚠ 验证结论

### ⚠ **DB 层面负向证据确凿**

`crm_contact_map` 表**完全不存在**（3 个独立查询全部 0 行）：
- `SHOW TABLES LIKE` → 0 行
- `information_schema.tables` → 0 行
- `DESCRIBE` → Error 1146

### ❌ **运行时不可达**

F010 证据显示 tick 在 `selectList(integration)` 抛 I002，**F012 在运行时完全没机会执行**：
- 日志中 0 行 `loadKnownExternalIds` 相关调用
- 日志中 0 行 `knownIds / 差集 / known_external_ids` 关键词

### ⚠ I002 / F023 — DDL 缺表

| 维度 | 评估 |
|---|---|
| 表缺失 | ❌ 完全缺失（3/3 查询确认） |
| L0 代码静态可达 | ✅ `loadKnownExternalIds` 方法存在 + try-catch 完整 |
| Runtime 可达 | ❌ 永远到不了（被 I002 阻塞） |
| 差集去重功能 | ❌ **完全失效**（即使能跑到，也会因表缺失返回 empty Set） |

### ⚠ 静态 vs Runtime 差距

- **静态**：方法存在 → L0 PASS
- **运行时**：tick 早期抛错 + 表缺失 → Runtime FAIL
- **审计可见性**：FAIL（生产环境将永远无法做差集去重 → 重复联系人入库）

## 6. 状态结论

| 维度 | 判定 |
|---|---|
| DB 表存在性 | ❌ **MISSING**（F023 负向证据）|
| L0 静态可达 | ✅ |
| Runtime 实际可达 | ❌ 不可达（I002 前置阻塞）|
| 差集去重功能 | ❌ **完全失效** |
| 与 plan.md §三 预期 | ⚠ **一致**（plan §三 标"反向证明 F023 PARTIAL-BLOCKER"）|
| **最终状态** | **BROKEN**（表缺失 + 代码不可达 + 功能完全失效） |

## 7. 修复方向（不实施，仅登记）

🛠 跑 `migrate_ext_15_crm_real.sql` 补齐 `crm_contact_map` 表 DDL
🛠 跑迁移同时修复 I002（9 列缺失）→ tick selectList 恢复 → loadKnownExternalIds 可达
🛠 在 tick 中加 metric：`crm_load_known_ids_failed_total` Prometheus 告警

## 8. 副作用

- `crm_contact_map` 表仍**不存在**（I023 维持）
- `crm_sync_log` 仍为 0 行
- 重复联系人入库风险：未约束（无差集去重）
