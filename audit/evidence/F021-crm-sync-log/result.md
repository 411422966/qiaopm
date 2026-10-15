# F021 — CrmSyncLog Entity（Static BROKEN inherited from I002 via 5A F005）

> **Phase**：5B Runtime Verification
> **Feature ID**：F021
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **继承策略**：I002 强依赖 → 引用 5A F005 证据

---

## 1. 继承结论

**F021 = BROKEN (inherited from I002 via 5A F005)**

| 维度 | 状态 | 证据来源 |
|---|---|---|
| 表存在性 | ✅ 表存在 | DB SHOW TABLES |
| 缺列数 | ❌ 缺 3 列（sync_token / records_total / records_filtered）| `migrate_ext_15_crm_real.sql:46-48` |
| 写库可达性 | ❌ sync 整链路被 I002 阻塞（crm_sync_log 永远 0 行）| 5A F005 + F010 |
| 5B 重新跑 | ⏸ 跳过 | 继承策略 |

## 2. crm_sync_log 表缺列确认

`backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql:46-48`：

```sql
ALTER TABLE crm_sync_log
    ADD COLUMN sync_token     VARCHAR(255) DEFAULT NULL,
    ADD COLUMN records_total  INT          DEFAULT 0,
    ADD COLUMN records_filtered INT        DEFAULT 0;
```

→ **3 列 EXT-15 缺失**

## 3. 5A 引用证据

| 文件 | 关键内容 |
|---|---|
| `audit/evidence/F005-manual-sync/result.md` | 触发 sync 后 `SELECT COUNT(*) FROM crm_sync_log` = 0（事务回滚生效）|
| `audit/evidence/F010-auto-tick-scan/result.md` | tick 7 次后 crm_sync_log 仍 0 行 |
| `audit/evidence/F019-crm-integration-entity/result.md` | F019 同根因 |

## 4. ✅ 最终状态

**❌ BROKEN (inherited from I002 via 5A F005)**

修复路径：跑 `migrate_ext_15_crm_real.sql` 补 3 列 → 5C 重测
