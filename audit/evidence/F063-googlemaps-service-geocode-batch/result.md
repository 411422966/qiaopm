# F063 — GoogleMapsService `/geocode/batch`

**状态**：PARTIAL-NON-BLOCKER（I032 无独立 audit log 表 + I028 admin 守卫 + I029 优先暴露）
**验证类型**：API-only + DB-only + Static-Only
**测试时间**：2026-09-14 00:40 (UTC+8)

## 1. 端点 L0 静态

- 路径：`POST /google-maps/geocode/batch`
- 行号：`GoogleMapsService.geocodeBatch` L256-331
- 入参：`Map<String,Object>` body，必填 `addresses: List<String>`，最多 50 条
- admin 守卫：仅 `SUPER_ADMIN` / `PLATFORM_ADMIN` / `HQ_ADMIN` 可调（L259-263）

## 2. 实证

### 2.1 admin 调用（I029 优先暴露）

```bash
curl.exe -X POST http://localhost:8080/google-maps/geocode/batch \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json;charset=UTF-8" \
  --data-binary '{"addresses":["北京","上海","深圳","杭州"]}'
```

**结果**：

```json
{"code":500,"message":"数据库异常","data":null,"traceId":"5cb08cfabb1b4f98a1d18a25d8631a26"}
HTTP=200
```

→ admin 守卫过了（admin 有 SUPER_ADMIN 角色），到 L272 取 cfg 时抛 SQL 异常。

证据文件：
- `audit/evidence/F063-googlemaps-service-geocode-batch/raw-batch-admin.txt`

### 2.2 clerk 调用（I028 admin 守卫实证）

```bash
curl.exe -X POST http://localhost:8080/google-maps/geocode/batch \
  -H "Authorization: Bearer $CLERK_TOKEN" \
  -H "Content-Type: application/json;charset=UTF-8" \
  --data-binary '{"addresses":["北京","上海"]}'
```

**结果**：

```json
{"code":403,"message":"权限不足: 批量 geocode 仅管理员可用","data":null,"traceId":"22fb5096dc9d4364a93ac2e3a3e2dfc6"}
HTTP=200
```

→ **I028 admin 守卫实证成功**：clerk01 没 SUPER_ADMIN 角色 → 403 PERMISSION_DENIED，符合 service L259-263 设计。
→ **注意**：clerk 守卫**先于** DB 缺失触发，符合「权限校验优先」原则。

证据文件：
- `audit/evidence/F063-googlemaps-service-geocode-batch/raw-batch-clerk.txt`

## 3. N8 — `geocode_batch_log` 表不存在

DB 探针：

```sql
SHOW TABLES LIKE 'geocode_batch_log';
```

**结果**：

```
Empty set (0 hits)
```

→ **I032 实证成功**：`geocode_batch_log` 表不存在。
→ 代码 `GoogleMapsService.geocodeBatch` L256-331 **完全不引用** `geocode_batch_log`，
只在响应里返回 `{total, ok, fail, durationMs, source, results}`，**无独立 audit log 表**。

证据文件：
- `audit/evidence/F063-googlemaps-service-geocode-batch/db-batch-log-check.txt`

## 4. 与 EXT-22 §0.5 一致

EXT-22 Operational Notes §0.5：
> **`batch geocode` 无 audit**：admin-only 但**无 audit log 记录**是谁调了多少条，建议后续加 `geocode_batch_log` 表

→ 与 Phase 5 实证一致，I032 维持。

## 5. 副作用分析

- 写日志：L307-323 复用 geocode 日志写入路径，逐条 `logMapper.insert(log)` 包 try/catch
- 表缺失时静默丢日志（与 F057 / F061 一致）

## 6. 结论

| 维度 | 结果 |
|---|---|
| 端点可达 | ✅（admin 200, clerk 403） |
| admin 守卫 I028 | ✅ PARTIAL-NON-BLOCKER 实证 |
| N8 `geocode_batch_log` 表缺失 | ✅ I032 实证 |
| I029 MISSING 暴露 | ✅（500 "数据库异常"） |
| 写日志 I031 复用路径 | ✅（try/catch 静默丢） |

**最终状态**：**PARTIAL-NON-BLOCKER**
**负向证据**：
- I028：admin 守卫工作正常
- I032：`geocode_batch_log` 表不存在（无独立 audit log）
- I029：DB MISSING 暴露
- I031：复用 geocode 日志路径（与 F062 对比）

**修复 Phase**：Post-Audit（建表 + 加 audit log + 建表后补 Phase 5 实证）