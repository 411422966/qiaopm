# F006 — 同步日志（BROKEN inherited from I002 via 5A F002/F005）

> **Phase**：5B Runtime Verification
> **Feature ID**：F006
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **继承策略**：I002 强依赖 → 引用 5A F002 证据

---

## 1. 继承结论

**F006 = BROKEN (inherited from I002 via 5A F002/F005)**

| 维度 | 状态 | 证据来源 |
|---|---|---|
| endpoint 可用性 | ❌ 完全不可用 | 5A F002 测过 selectList 抛 I002 |
| 数据契约 | ⚠ I003 漂移（5A 已确认）| 5A F002 §3.2 |
| 同步日志写入 | ❌ crm_sync_log 永远 0 行 | 5A F005 §5.2 |
| 5B 重新跑 | ⏸ 跳过（与 5A 一致根因）| 继承策略 |

## 2. 5A 引用证据

| 文件 | 关键内容 |
|---|---|
| `audit/evidence/F002-list-integration/result.md` | selectList 抛 `Unknown column 'sync_token'` → 500 |
| `audit/evidence/F005-manual-sync/result.md` | syncNow:88 selectById 抛 I002 → 500，crm_sync_log 0 行 |
| `audit/issues/I002-crm-ddl-missing.md`（待建）| DB 缺 9 列 EXT-15 字段 |

## 3. I003 契约漂移（5A 已确认）

CrmSyncLog 实体 + integrationId 列 → 实际 sync_log 表缺 `integration_id` 列（I002）

`CrmSyncLog` 表存在但缺 3 列：`sync_token / records_total / records_filtered`（I002 联动）

→ `/crm-integration/sync-log/list?integrationId=X` 返回值实际按 `platform` 过滤而非 `integrationId`

## 4. ✅ 最终状态

**❌ BROKEN (inherited from I002 via 5A F002/F005)**

修复路径：跑 `migrate_ext_15_crm_real.sql` 补 9 列 EXT-15 + 3 列 sync_log → 5C 重测
