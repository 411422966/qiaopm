# F020 — CrmContactMap Entity（DB-only BROKEN inherited from I002 via 5A F012）

> **Phase**：5B Runtime Verification
> **Feature ID**：F020
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **继承策略**：I002 强依赖 → 引用 5A F012 证据

---

## 1. 继承结论

**F020 = BROKEN (inherited from I002 via 5A F012)**

| 维度 | 状态 | 证据来源 |
|---|---|
| 表存在性 | ❌ 表不存在 | 5A F023 + 5A F012 |
| 写库可达性 | ❌ sync 永远到不了 upsertContactMap | 5A F012 |
| 唯一键 (platform + externalId) | ✅ Entity 定义 | 5B F019 间接 |
| 5B 重新跑 | ⏸ 跳过 | 继承策略 |

## 2. 5A 引用证据

| 文件 | 关键内容 |
|---|---|
| `audit/evidence/F012-known-ids-load/result.md` | loadKnownExternalIds 抛 `Table 'meiyetec.crm_contact_map' doesn't exist` |
| `audit/evidence/F023-crm-contact-map-existence/result.md`（待建）| `SHOW TABLES LIKE 'crm_contact_map'` 0 行 |
| `audit/issues/I002-crm-ddl-missing.md`（待建）| DB 缺 crm_contact_map 表 |

## 3. 修复路径

跑 `migrate_ext_15_crm_real.sql` 创建 crm_contact_map 表（DDL 已就绪）

## 4. ✅ 最终状态

**❌ BROKEN (inherited from I002 via 5A F012)**
