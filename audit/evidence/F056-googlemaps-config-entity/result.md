# F056 — GoogleMapsConfig 实体

**状态**：MISSING（I029 维持）
**验证类型**：DB-only
**DB**：meiyetec @ 127.0.0.1:3307
**测试时间**：2026-09-14 00:39 (UTC+8)

## 1. 实体定义（L0 静态）

- 文件：`backend/src/main/java/com/meiyetec/entity/GoogleMapsConfig.java`
- 字段：21 个（含 EXT-22 新增 `enablePlaceDetails` @ L43，Integer 类型）

## 2. DB 实证

```sql
SHOW TABLES LIKE 'google_maps_config';
DESCRIBE google_maps_config;
```

**结果**：

```
ERROR 1146 (42S02) at line 1: Table 'meiyetec.google_maps_config' doesn't exist
```

证据文件：
- `audit/evidence/F056-googlemaps-config-entity/db-describe.txt` — 完整输出

## 3. DDL 预期（未跑迁移）

`backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql:9-39` 应有 21 字段。
EXT-22 ALTER：`migrate_ext_22_google_maps_place_details.sql:6` 应加 `enable_place_details` 列。

## 4. 结论

| 维度 | 结果 |
|---|---|
| 实体存在 | ✅（Java 源码） |
| DB 表存在 | ❌（I029 MISSING） |
| 后端启动能否影响 | ✅ 不影响（Mapper lazy load） |
| 运行时 `/config` 调用 | ❌ 500 "数据库异常"（详见 F060） |

**最终状态**：**MISSING（I029 维持，D13 不跑迁移）**
**修复 Phase**：Post-Audit（DBA 决策）