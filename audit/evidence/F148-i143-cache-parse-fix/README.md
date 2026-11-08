# F148 — I143 修复证据：AI 推荐缓存反序列化桩代码

> 缺陷编号：I143
> 缺陷文件：`backend/src/main/java/com/meiyetec/service/AiEnhanceService.java`
> 缺陷方法：`parseJsonList(String json)`（原 L434-437 桩代码 `return List.of();`）
> 修复时间：2026-11-08 23:03 (+08:00)
> 验证等级：L5b（运行期 curl 实测冷热路径对照 + DB 落库校验 + 容器日志检查）

---

## 1. 环境

| 项 | 值 |
|---|---|
| 应用 | 容器 `meiyetec-api`（本地 Docker），镜像 `meiyetec/api:dev`（重建后重启） |
| 后端端口 | `http://localhost:8080` |
| 数据库 | `meiyetec` @ 容器 `meiyetec-mysql`（`--default-character-set=utf8mb4` 读取） |
| 鉴权 | `POST /auth/login`（admin / 123456）→ `Authorization: Bearer <JWT>` |
| 构建 | `mvn clean package "-Dmaven.test.skip=true"` → EXIT=0；`docker build -t meiyetec/api:dev .` → DONE |

---

## 2. 修复内容摘要

| 项 | 修复前 | 修复后 |
|---|---|---|
| 实现 | `return List.of();`（桩） | `ObjectMapperHolder.MAPPER.readValue(json, new TypeReference<List<Map<String,Object>>>() {})` |
| JSON 工具 | 无 | 复用项目全局 `com.meiyetec.util.ObjectMapperHolder.MAPPER` |
| 空/null 入参 | N/A | 直接返回 `List.of()` |
| 解析异常 | N/A | `catch (Exception)` → `log.warn("推荐缓存反序列化失败，返回空列表：{}")` → 返回 `List.of()`，**不抛异常**（不会 500） |
| 新增 import | — | `com.fasterxml.jackson.core.type.TypeReference`、`com.meiyetec.util.ObjectMapperHolder` |

---

## 3. 证据文件

| 文件 | 内容 |
|---|---|
| `01-curl-cache-hit-path.txt` | 清缓存 → 连续两次 `GET /ai/recommend?memberId=1` 的完整响应 + 落库缓存行 |

---

## 4. 验证结论

| 验证项 | 期望 | 实测 | 结论 |
|---|---|---|---|
| call #1 `cacheHit` | `false`（冷路径） | `false` | ✅ |
| call #1 `items` | 5 条 | 5 条（基础面部护理 / 美睫嫁接 / 肩颈舒缓按摩 / 全身精油SPA / 深层补水护理） | ✅ |
| call #2 `cacheHit` | `true`（命中缓存分支） | `true` | ✅ |
| call #2 `items` | **5 条且逐条与 call #1 一致** | 5 条，逐条一致 | ✅ |
| `ai_recommend_cache.hit_count` | 第二次调用后递增（证明走缓存分支而非重算） | 0 → 1 | ✅ |
| 落库 `recommend_items` | 完整合法 JSON、中文正常 | `--default-character-set=utf8mb4` 读出中文正常 | ✅ |
| 容器日志 | 无「推荐缓存反序列化失败」告警 | 无 | ✅ |
| 编译 | EXIT=0 | EXIT=0 | ✅ |

**最终状态：VERIFIED**
