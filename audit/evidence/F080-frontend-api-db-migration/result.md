# F080 Frontend API + DB 迁移 — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：I029（MISSING DB 迁移未跑）+ D13（不跑 migration）

---

## 1. 实证结论

**状态**：**MISSING**（D13 决策维持 — 迁移脚本存在但未跑）

| 验证目标 | 期望 | 实际 | 状态 |
|---|---|---|---|
| **DB snapshot grep 7 项** | 0 命中 | 6 项 0 命中，1 项误命中 | ✅ I029 实证 PASS |
| **migration 脚本存在** | 是 | `migrate_ext_22_google_maps_place_details.sql` 存在 | ✅ |
| **migration 已跑** | 否 | 未跑（D13 决策）| ⚠️ D13 维持 |

## 2. DB snapshot grep 结果

文件：`db-snapshot-crm_integration.txt`、`db-snapshot-crm_contact_map-and-counts.txt`、`db-snapshot-crm_sync_log.txt`

| 关键词 | 期望 | db-snapshot-crm_integration.txt | db-snapshot-crm_contact_map-and-counts.txt | db-snapshot-crm_sync_log.txt |
|---|---|---|---|---|
| `google_maps_config` | 0 | 0 | 0 | 0 |
| `google_maps_geocode_log` | 0 | 0 | 0 | 0 |
| `ecommerce_platform` | 0 | 0 | 0 | 0 |
| `enable_place_details` | 0 | 0 | 0 | 0 |
| `kdt_id` | 0 | 0 | 0 | 0 |
| `sign_secret` | 0 | 0 | 0 | 0 |
| `app_id` | 0 | **1（CRM 表误命中）** | 0 | 0 |

→ **7 项里 6 项完全 0 命中**（PASS），**app_id 1 命中是 CRM 表 crm_integration.app_id 误命中**（非 EXT-22 字段）

## 3. 迁移脚本

`backend/src/main/resources/db/sql/migrate_ext_22_google_maps_place_details.sql`（13 行）：

```sql
-- EXT-22：Google Maps + Place Details 计费开关 + 电商平台签名字段
ALTER TABLE google_maps_config
  ADD COLUMN IF NOT EXISTS enable_place_details TINYINT DEFAULT 0 COMMENT '...';

ALTER TABLE ecommerce_platform
  ADD COLUMN IF NOT EXISTS app_id       VARCHAR(64)  DEFAULT NULL COMMENT '...' AFTER app_secret,
  ADD COLUMN IF NOT EXISTS kdt_id       VARCHAR(64)  DEFAULT NULL COMMENT '...' AFTER app_id,
  ADD COLUMN IF NOT EXISTS sign_secret  VARCHAR(255) DEFAULT NULL COMMENT '...' AFTER kdt_id;
```

兼容性：
- MySQL 8.0.29+ 支持 `ADD COLUMN IF NOT EXISTS` ✓（当前 8.0.46）
- 但**前置条件**：`google_maps_config` / `ecommerce_platform` 表必须已存在
- ecommerce_platform 表已存在（5B F066 实证）→ 可以跑 ALTER
- google_maps_config 表**不存在**（5A F056 实证）→ ALTER 会失败

→ **批量迁移前需先建 google_maps_config / google_maps_geocode_log 表**（执行 `migrate_ext_17_google_maps.sql`）

## 4. Frontend API 静态证据

| 文件 | 行数 | 内容 |
|---|---|---|
| `frontend/meiyetec-web/src/api/google-maps.ts` | 18 | geocode / geocodeBatch / config / health / placeDetails 等 |
| `frontend/meiyetec-web/src/api/ecommerce.ts` | 19 | platform CRUD / sync / dashboard |

→ API 封装完整，与后端 Controller 一一对应。

## 5. Issue 状态

| Issue | 关联 | 状态 |
|---|---|---|
| **I029** | EXT-22 迁移未跑 | 维持 MISSING（D13 决策）|
| **I029 PARTIAL-MISSING** | 5B 修正：列缺失而非表缺失 | 维持 |

## 6. Phase 7 决策建议

Post-Audit 阶段：
1. 跑 `migrate_ext_17_google_maps.sql` 建 google_maps_config / google_maps_geocode_log 表
2. 跑 `migrate_ext_22_google_maps_place_details.sql` 加 4 个新列
3. 重跑 F056 / F057 / F066 / F075 / F077 / F078 全部链路验证
4. 期望从 MISSING/PARTIAL-MISSING 转为 VERIFIED

## 7. 证据文件

- `db-snapshot-grep.txt` — 7 项 grep 结果
