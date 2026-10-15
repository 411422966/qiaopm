# F001 — 创建 CRM 集成

> Phase 5A | API-only | P0
> 时间：2026-09-12 23:09 (UTC+8)

---

## 1. 验证方式

**API-only**：POST `/crm-integration`（来源 `CrmIntegrationService.java:35`，依据 plan.md §1.1）

## 2. 命令

```bash
curl -X POST http://localhost:8080/crm-integration \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @body.json
```

body.json：
```json
{"platform":"MOCK","shopName":"5A-F001-test","syncDirection":"PULL","autoSync":1}
```

完整 raw：`audit/evidence/F001-create-integration/raw-response.txt`
完整 body：`audit/evidence/F001-create-integration/body.json`

## 3. HTTP 响应

| 字段 | 值 |
|---|---|
| HTTP 状态 | **200** |
| code | 200 |
| message | 已创建 |
| traceId | `ac198eff93aa4f7d86ffde1d61d4f831` |
| data.id | **2**（第一条历史是 id=1） |
| data.tenantId | 1 |
| data.platform | MOCK |
| data.syncDirection | PULL |
| data.autoSync | 1 |
| data.status | ACTIVE |

## 4. DB 断言

`SELECT * FROM crm_integration WHERE id=2\G` → 详见 `db-actual-row.txt`

| 列 | 实际值 |
|---|---|
| id | 2 |
| tenant_id | 1 ✅ |
| platform | MOCK ✅ |
| sync_direction | PULL ✅ |
| auto_sync | 1 ✅ |
| status | ACTIVE ✅ |
| created_at | 2026-09-12 23:09:46 ✅ |
| 其余 16 列 | NULL（默认）|

## 5. ✅ 验证通过 + ⚠ 负向证据

### ✅ 通过部分

- HTTP 200 + `code=200`
- `crm_integration` 表新增 1 行（id=2）
- 实体字段映射正确（已存在的列 → 实际值）
- `autoSync=1` 写入 `auto_sync` 列

### ⚠ I002 — silent 缺列写入（负向证据）

API 响应 JSON 中包含但**表中无列**的字段（全部 NULL）：

| 响应字段 | 表中列 | 写入行为 |
|---|---|---|
| `shopName` | `shop_name` ❌ | silent ignore |
| `syncWindowMinutes` | `sync_window_minutes` ❌ | silent ignore |
| `objectMappings` | `object_mappings` ❌ | silent ignore |
| `privateKey` | `private_key` ❌ | silent ignore |
| `syncEnabled` | `sync_enabled` ❌ | silent ignore |
| `lastIncrementalSyncAt` | `last_incremental_sync_at` ❌ | silent ignore |
| `syncToken` | `sync_token` ❌ | silent ignore |
| `syncSchedule` | `sync_schedule` ❌ | silent ignore |
| `username` | `username` ❌ | silent ignore |
| `loginUrl` | `login_url` ❌ | silent ignore |

**审计意义**：MyBatis-Plus insert 对缺失列默认 `silent-ignored`（不抛错、不写入）。这意味着前端传了字段也"看似成功"，但实际**没落库**。生产侧需要**列存在性 guard**（L0 静态校验 entity 与 schema 对齐）才能拦截。

## 6. 状态结论

| 维度 | 判定 |
|---|---|
| HTTP 行为 | ✅ VERIFIED（HTTP 200 + 行写入成功） |
| 数据一致性 | ⚠ **PARTIAL**（10 个 EXT-24 关键列 silent-ignored） |
| 与 plan.md §三 预期 | **一致** — plan 标注 `I002（缺 9 列 silent）`，实测 = 10 列 silent |
| **最终状态** | **PARTIAL-NON-BLOCKER**（CRUD 可用 + 关键字段丢失） |

## 7. 副作用

- `crm_integration` 行数 1 → **2**
- 测试用 integrationId：**2**（后续 F005/F008/F009 复用）
