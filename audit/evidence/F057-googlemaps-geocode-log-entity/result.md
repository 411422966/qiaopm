# F057 — GoogleMapsGeocodeLog 实体

**状态**：MISSING（I029 维持）
**验证类型**：DB-only
**DB**：meiyetec @ 127.0.0.1:3307
**测试时间**：2026-09-14 00:39 (UTC+8)

## 1. 实体定义（L0 静态）

- 文件：`backend/src/main/java/com/meiyetec/entity/GoogleMapsGeocodeLog.java`
- 字段：11 个（含 `place_id VARCHAR(64)` @ L20 复用 Place Details）
- 写入路径：`GoogleMapsService.geocode` L160-176 / `geocodeBatch` L307-323（try/catch 吞异常）

## 2. DB 实证

```sql
SHOW TABLES LIKE 'google_maps_geocode_log';
DESCRIBE google_maps_geocode_log;
```

**结果**：

```
ERROR 1146 (42S02) at line 1: Table 'meiyetec.google_maps_geocode_log' doesn't exist
```

证据文件：
- `audit/evidence/F057-googlemaps-geocode-log-entity/db-describe.txt` — 完整输出

## 3. DDL 预期（未跑迁移）

`backend/src/main/resources/db/sql/migrate_ext_17_google_maps.sql:40-53` 应有 11 字段。

## 4. 副作用分析

`GoogleMapsService.geocode` 和 `geocodeBatch` 中 logMapper.insert 都包了 try/catch（L173-175 + L320-323）：

```java
try {
    logMapper.insert(log);
} catch (Exception ignored) {}
```

→ **表缺失时业务不会崩溃**，仅静默丢日志。I031 实证详见 F062。

## 5. 结论

| 维度 | 结果 |
|---|---|
| 实体存在 | ✅（Java 源码） |
| DB 表存在 | ❌（I029 MISSING） |
| 静默丢日志 | ✅（写日志 try/catch 吞） |
| 运行时 `/geocode/log` 调用 | ❌ 500 "数据库异常"（详见 F064） |

**最终状态**：**MISSING（I029 维持，D13 不跑迁移）**
**修复 Phase**：Post-Audit（DBA 决策）