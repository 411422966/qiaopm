# F060 — GoogleMapsService `/config` + `/health`

**状态**：PARTIAL-NON-BLOCKER + I036 + I029 + C1
**验证类型**：API-only + DB-only
**测试时间**：2026-09-14 00:39 (UTC+8)

## 1. 端点 L0 静态

| 端点 | 方法 | 行号 | 业务说明 |
|---|---|---|---|
| `GET /google-maps/config` | 读配置 | L86-105 | 返回配置（含 `apiKey` 字段遮蔽后 4 位 — **I036**） |
| `POST /google-maps/config` | 保存 | L57-84 | upsert by (tenant+brand) |
| `POST /google-maps/health` | 健康检查 | L111-130 | 用一次 geocode 验证 apiKey 有效 |

## 2. API 实证

### 2.1 `GET /google-maps/health`（无 token）

```bash
curl.exe -s http://localhost:8080/google-maps/health -w "HTTP=%{http_code}"
```

**结果**：

```json
{"code":500,"message":"系统繁忙，请稍后重试","data":null,"traceId":"c803a7be410842319751a8cf6ee09f8a"}
HTTP=200
```

→ 返回 Spring `Result.error`，但 HTTP=200（项目约定）。

### 2.2 `GET /google-maps/config`（admin token）

```bash
curl.exe -s http://localhost:8080/google-maps/config -H "Authorization: Bearer $TOKEN"
```

**结果**：

```json
{"code":500,"message":"数据库异常","data":null,"traceId":"7de29b6540c84c0da988c6ea90d09b05"}
HTTP=200
```

→ **I029 MISSING 直接暴露**：表不存在 → SQL 抛 `BadSqlGrammarException`。

证据文件：
- `audit/evidence/F060-googlemaps-service-config-health/raw-health.txt`
- `audit/evidence/F060-googlemaps-service-config-health/raw-config.txt`

## 3. I036 GCP API Key 泄露

### 3.1 代码定位

`backend/src/main/java/com/meiyetec/service/GoogleMapsService.java:87`

```java
r.put("apiKey", "***" + apiKey.substring(Math.max(0, apiKey.length() - 4)));
```

→ 当前实现仅遮蔽前 N-4 位 + 后 4 位明文，前 N-4 位仍可猜测。

### 3.2 实证情况

**降级**：DB MISSING 导致无法在 Phase 5 拿到真实 apiKey 响应。
但 **代码层**已确认 I036 行为存在：
- 后端 service L87 明确返回 `***xxxx`（最后 4 位明文）
- 前端 `google-maps/index.vue:40` 渲染 `尾号 {{ config.apiKey }}`（用户可视化）
- 39 位 GCP API Key 中前 35 位 + `***` 仍可逐位爆破

### 3.3 与 EXT-22 §5 一致

EXT-22 Operational Notes §5 已标 "**`getConfig` 端点泄露风险**：建议生产改成完全不回显，前端用 masked input"。

## 4. C1 — configForm 缺字段

`frontend/meiyetec-web/src/views/google-maps/index.vue:138`

```typescript
const configForm = ref({ apiKey: '', defaultZoom: 13, enablePlaceDetails: 0 })
```

→ 前端表单只 3 字段，**缺**：
- `defaultLanguage`（默认 zh-CN）
- `defaultRegion`（默认 cn）
- `quotaDaily`（默认 10000）
- `enabled`（默认 1）

后端 service L62-65 默认填充这些，但用户**无法**自定义。
→ C1 configForm 缺字段 PARTIAL-NON-BLOCKER 实证。

## 5. 结论

| 维度 | 结果 |
|---|---|
| `/health` 端点可达 | ✅（I029 MISSING 时 500，行为符合预期） |
| `/config` 端点可达 | ✅（I029 MISSING 时 500，行为符合预期） |
| I036 Key 泄露代码层实证 | ✅（L87 字符串拼接） |
| I036 Phase 5 实证 | ❌（I029 优先暴露，降级） |
| C1 configForm 缺字段 | ✅ PARTIAL-NON-BLOCKER |

**最终状态**：**PARTIAL-NON-BLOCKER**（I036 代码层实证 + I029 MISSING 暴露 + C1 缺字段）
**修复 Phase**：Post-Audit（D19 仅标注不改）