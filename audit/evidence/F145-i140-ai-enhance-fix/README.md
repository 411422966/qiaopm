# F145 — I140 `AiEnhanceService` 血缘漂移修复 · 运行证据

> **Issue**：I140（`audit/issues/I140-ai-enhance-blood-line-drift.md`）
> **关联 issue**：I141（`biz_service.category_id` 全 NULL）、I142（`LtvTrainingService` 缺 4 张表）
> **取证日期**：2026-11-07
> **取证人**：Cursor

---

## 1. 环境

| 项 | 值 |
|---|---|
| 数据库 | `meiyetec` @ `127.0.0.1:3307`（Docker 容器 `meiyetec-mysql`） |
| 认证 | `POST /auth/login` → `admin` / `123456` → Bearer token |
| 后端 | Docker 镜像 `meiyetec/api:dev`，容器 `meiyetec-api`（宿主端口 8080） |
| 构建 | `mvn "-Dmaven.test.skip=true" package`（JDK 17.0.13）→ `docker build -t meiyetec/api:dev .` → `docker compose up -d api` |
| 健康检查 | `GET /actuator/health` → `{"status":"UP"}` |
| 字符集 | 所有 SQL 均带 `--default-character-set=utf8mb4`（容器 mysql 客户端默认 latin1，会把中文显示成 `?`） |

---

## 2. 证据文件

| 文件 | 内容 |
|---|---|
| `after-01-curl-runtime.txt` | 修复后 `GET /ai/recommend` 的 curl 实测输出（memberId=1 冷路径 / memberId=2 冷路径 / memberId=1 二次调用命中缓存） |
| `after-02-sql-verification.txt` | SQL 层只读验证：`biz_service` 表结构、修复后两条 SQL 的实际返回、`biz_service.category_id` NULL 统计、I142 缺表清单 |

---

## 3. 修复前后对照

| 阶段 | `GET /ai/recommend?memberId=1` 返回 |
|---|---|
| 修复前 | `{"items":[{"reason":"数据不足","name":"暂无推荐","id":0,"score":0}],"cacheHit":false}` |
| 修复后（冷路径） | `{"items":[{"id":1,"name":"基础面部护理","price":280.00,...},{"id":5,"name":"美睫嫁接",...},{"id":3,"name":"肩颈舒缓按摩",...},{"id":4,"name":"全身精油SPA",...},{"id":2,"name":"深层补水护理",...}],"cacheHit":false}` |

---

## 4. 关键取证说明

1. **陈旧缓存**：修复前 `memberId=1` 的降级结果（"暂无推荐"）已写入 `ai_recommend_cache`（有效期 6 小时）。
   验证时执行 `DELETE FROM ai_recommend_cache WHERE id=1` 清除该条后复测，才得到冷路径输出。
   **这是缓存的正常行为，不是缺陷。**
2. **缓存路径缺陷（未修复，仅报告）**：`AiEnhanceService.parseJsonList` 是 `return List.of();` 的桩实现，
   导致 `cacheHit=true` 时返回 `items: []`。该缺陷独立于本次血缘修复，超出本批授权范围。
3. **SQL 层客户端字符集**：容器内 mysql 客户端 `character_set_client = latin1`，
   首次取证时中文显示为 `????`（**是显示问题，不是数据问题**）。
   重跑时加 `--default-character-set=utf8mb4` 后中文正常显示，此处保存的是重跑后的版本。

---

## 5. 验证等级

| 项 | 等级 |
|---|---|
| SQL 层（`biz_service` 结构、两条修复后 SQL 返回） | **VERIFIED**（只读实测） |
| HTTP 层（冷路径返回真实推荐） | **VERIFIED**（curl 实测输出见 `after-01-curl-runtime.txt`） |
| HTTP 层（缓存路径 `cacheHit=true`） | **PARTIAL** —— 因 `parseJsonList` 桩实现返回空数组（既存缺陷，未在本批修复） |
| 表结构变更 | **无**（本修复未动任何 DDL） |
