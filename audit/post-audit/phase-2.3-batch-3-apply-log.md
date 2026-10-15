# Phase 2.3 Batch 3 Apply Log — EXT-17 Google Maps 基础表

> 批次：Phase 2.3 Batch 3  
> 执行时间：2026-09-21 14:52 (UTC+8)  
> 执行人：Cursor Agent  
> 规则：加固流程（Cursor 执行 → Cursor 报告 → 用户确认下一批）

---

## 1. Apply 前状态

| 检查项 | 结果 |
|---|---|
| `google_maps_config` 存在性 | ❌ 不存在（ERROR 1146） |
| `google_maps_geocode_log` 存在性 | ❌ 不存在（ERROR 1146） |
| dump 文件 | `audit/baseline/pre-2.3-batch-3-dump.sql`（598 bytes，session-only，无 INSERT） |

---

## 2. Apply 操作

| 项 | 值 |
|---|---|
| 脚本 | `backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql` |
| 脚本版本 | EXT-17（2026-09-05） |
| 执行命令 | `mysql meiyetec < migrate_ext_17_google_maps.sql` |
| 实际执行方式 | `mysql -h localhost -P 3307 -u root -proot123456 --default-character-set=utf8mb4 meiyetec < "D:\QiaoPM\backend\src\main\resources\db\sql\migrate_ext_17_google_maps.sql"` |
| Exit Code | **0（成功）** |
| dump-before 文件 | `audit/baseline/pre-2.3-batch-3-dump.sql`（SHA256: `45E2AA7339F1410309CFBF92D0FD5CAF971E37790617A3871EF73A777D4DFC40`） |

### 脚本操作内容

```sql
DROP TABLE IF EXISTS google_maps_config;   -- 表不存在，no-op
DROP TABLE IF EXISTS google_maps_geocode_log;  -- 表不存在，no-op
CREATE TABLE google_maps_config (...)      -- 新建 17 列
CREATE TABLE google_maps_geocode_log (...) -- 新建 11 列
```

---

## 3. Apply 后验证

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `google_maps_config` 列数 | 17 | 17 | ✅ |
| `google_maps_geocode_log` 列数 | 11 | 11 | ✅ |
| `google_maps_config` 行数 | 0 | 0 | ✅ |
| `google_maps_geocode_log` 行数 | 0 | 0 | ✅ |

### `google_maps_config` 17 列

```
id / tenant_id / brand_id / api_key / default_language /
default_region / quota_daily / rate_limit_per_min / enabled /
last_health_at / last_health_msg / config_json /
operator_id / operator_name / create_time / update_time / deleted
```

### `google_maps_geocode_log` 11 列

```
id / tenant_id / address / formatted_address /
lat / lng / place_id / status / error_message /
duration_ms / request_at
```

---

## 4. 范围外副作用评估

| 影响 | 评估 |
|---|---|
| 其他表被修改 | 无（仅操作上述两表） |
| 已有数据被删除 | 无（apply 前两表不存在，DROP IF EXISTS = no-op） |
| Flyway schema_history 记录 | ❌ 未记录（手动 mysql 执行，不走 Flyway） |
| EXT-22 字段（enable_place_details） | ❌ 未创建（属于 Batch 3.5 范围） |
| ecommerce_platform 字段（app_id/kdt_id/sign_secret） | ❌ 未创建（属于 Batch 3.5 范围） |

---

## 5. 下一步

- **Batch 3.5**：EXT-22 补跑 → `migrate_ext_22_google_maps_place_details.sql`（ALTER 两表）
- 当前两表为空（无数据），apply EXT-22 ALTER 无数据损坏风险
