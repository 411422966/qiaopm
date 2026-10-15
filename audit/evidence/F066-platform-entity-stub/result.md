# F066 EcommercePlatform 实体 — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification（有赞侧）
> **关联**：I029（MISSING 修正为 PARTIAL-MISSING）+ I028 字段漂移确认

---

## 1. 实证结论

**状态**：**PARTIAL-MISSING**（5A Phase 3 漏判修正 — 由 MISSING 细化为表存在但缺 3 列）

| 维度 | 状态 | 证据 |
|---|---|---|
| **表存在性** | ✅ 存在 | `SHOW TABLES LIKE 'ecommerce_platform'` → 1 行 |
| **字段数** | ⚠️ 缺 3 列 | 实体声明 22 字段，DB 实际 20 字段 |
| **缺哪些列** | ❌ app_id / kdt_id / sign_secret | INFORMATION_SCHEMA 0 命中（DESCRIBE 0 命中） |
| **EXT-22 迁移** | ❌ 未跑 | `migrate_ext_22_google_maps_place_details.sql` 存在但 D13 不跑 |
| **INSERT 路径** | ✅ 可写（POST 200） | F075 实证写入了 2 行 |
| **SELECT 路径** | ❌ 全 500 | F075 GET /list 触发 `Unknown column 'app_id'` |

## 2. 关键 SQL 证据

```sql
DESCRIBE ecommerce_platform;
-- → 20 字段，无 app_id / kdt_id / sign_secret

SELECT COLUMN_NAME FROM information_schema.columns
WHERE table_schema='meiyetec' AND table_name='ecommerce_platform';
-- → id, tenant_id, platform, shop_name, shop_id, app_key, app_secret,
--   access_token, token_expire_at, webhook_url, webhook_secret, api_base_url,
--   sync_products, sync_orders, sync_inventory, last_sync_at, status,
--   error_message, created_at, updated_at
-- 缺: app_id, kdt_id, sign_secret  ← EXT-22 应加的 3 列
```

## 3. 实体 vs DB 对比

| 实体字段（EcommercePlatform.java）| DB 列 | 状态 |
|---|---|---|
| appId (L19) | app_id | ❌ MISSING |
| kdtId (L21) | kdt_id | ❌ MISSING |
| signSecret (L23) | sign_secret | ❌ MISSING |
| 其他 19 个 | 同名 | ✅ 存在 |

## 4. 副作用扩大（5B 新发现，5A 未暴露）

I029 PARTIAL-MISSING 让**所有 SELECT * 路径**都触发 `Unknown column 'app_id'`：

- F075 GET `/ecommerce/platform/list` → 500
- F077 webhook 反查 `EcommercePlatform` → 500
- F068/F069/F070 调用链 → 全部 500

→ **5A 仅看到 google_maps_config 整表 MISSING（TableNotFound），5B 看到 PARTIAL-MISSING 触发 Unknown column — 这是 5A 未识别的更深影响**

## 5. Issue 状态

| Issue | 维持/修正 |
|---|---|
| **I029 MISSING** | **修正为 PARTIAL-MISSING**：表存在，缺 3 列 |
| **I028 字段漂移** | 维持：前端缺字段 UI（appId/kdtId/signSecret） |

## 6. 5B 后续验证

D13 不跑 migration → F066 维持 PARTIAL-MISSING。
Post-Audit Phase 7：跑 `migrate_ext_22_google_maps_place_details.sql` 后重跑 F066 应转为 VERIFIED。

## 7. 证据文件

- `db-describe.txt` — DESCRIBE + INFORMATION_SCHEMA 精准查询
