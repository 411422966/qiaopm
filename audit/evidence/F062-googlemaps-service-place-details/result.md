# F062 — GoogleMapsService `/place/details`

**状态**：PARTIAL-NON-BLOCKER（I031 未写日志 + I029 优先暴露 + C1）
**验证类型**：API-only + Static-Only
**测试时间**：2026-09-14 00:40 (UTC+8)

## 1. 端点 L0 静态

- 路径：`POST /google-maps/place/details`
- 行号：`GoogleMapsService.placeDetails` L200-240
- 入参：`Map<String,Object>` body，必填 `placeId`，可选 `language`

## 2. 三段门禁（L206-220）

```java
// 步骤 1：租户 + enabled + apiKey 非空
GoogleMapsConfig cfg = configMapper.selectOne(...);
if (cfg == null || cfg.getApiKey() == null || cfg.getApiKey().isEmpty()) {
    return Result.error(BizCode.NOT_FOUND, "未配置 Google Maps API Key");
}
// 步骤 2：计费开关
if (cfg.getEnablePlaceDetails() == null || cfg.getEnablePlaceDetails() != 1) {
    return Result.error(BizCode.PERMISSION_DENIED,
            "Place Details 接口已关闭，请在 google_maps_config 启用 enablePlaceDetails=1");
}
// 步骤 3：真实链路
GoogleMapsGeocodingClient.PlaceDetailResult r =
        geocodingClient.placeDetails(cfg.getApiKey(), placeId, lang);
```

## 3. Phase 5 API 实证（N6 + N7 + C1）

### 3.1 admin POST /place/details（I029 优先暴露）

```bash
curl.exe -X POST http://localhost:8080/google-maps/place/details \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json;charset=UTF-8" \
  --data-binary '{"placeId":"ChIJ-test","language":"zh-CN"}'
```

**结果**：

```json
{"code":500,"message":"数据库异常","data":null,"traceId":"09c9c9d42b224f11af48f91b28eb6b26"}
HTTP=200
```

→ **N6（PERMISSION_DENIED）无法实证**：I029 MISSING 导致 step 1 的 SQL 抛 `BadSqlGrammarException`，
**预期**的 NOT_FOUND/未配置消息被 DB 异常覆盖。
→ 计费闸门**逻辑层**正确，但 Phase 5 实证降级为 PARTIAL-NON-BLOCKER。

证据文件：
- `audit/evidence/F062-googlemaps-service-place-details/raw-place-details.txt`

### 3.2 N7 — Place Details 未写 google_maps_geocode_log

DB 探针：

```sql
SELECT COUNT(*) FROM google_maps_geocode_log;
```

**结果**：

```
ERROR 1146 (42S02) at line 1: Table 'meiyetec.google_maps_geocode_log' doesn't exist
```

→ **I029 MISSING 优先于 I031 实证**：表不存在，无法验证「placeDetails 不写 log 表」的 I031。
→ 但 L233-251 的 `placeDetails` 代码**确认未写任何 log**（无 try/catch logMapper.insert 调用）。

证据文件：
- `audit/evidence/F062-googlemaps-service-place-details/db-log-count.txt`

## 4. I031 代码层实证

`GoogleMapsService.placeDetails` L200-240 **完全没有写 logMapper.insert** 调用。
对比 `geocode` L160-176 / `geocodeBatch` L307-323 都有 `try { logMapper.insert(log); } catch (Exception ignored) {}`。
→ **I031 代码层 100% 实证**：Place Details 未写日志（与 EXT-22 §0.3 一致）。

## 5. C1 — configForm 缺 enablePlaceDetails 默认值

前端 `google-maps/index.vue:138`：

```typescript
const configForm = ref({ apiKey: '', defaultZoom: 13, enablePlaceDetails: 0 })
```

→ `enablePlaceDetails: 0` 默认值是**前端硬编码**，用户切换 el-switch 后**实际**会保存到后端
（`saveConfig` 方法 → 后端 L82 `setEnablePlaceDetails(0)` 默认值）。
→ 但**用户**看到的是 "关闭" 状态，要打开得在开关上点 → 服务端会用 `body.enablePlaceDetails = 1` 写入。

## 6. 结论

| 维度 | 结果 |
|---|---|
| 三段门禁顺序 | ✅ L206-220 逻辑正确 |
| N6 PERMISSION_DENIED Phase 5 实证 | ❌（I029 优先暴露，降级） |
| N7 Place Details 未写 log 代码层实证 | ✅（无 logMapper.insert 调用） |
| N7 Phase 5 DB 实证 | ❌（I029 优先） |
| C1 el-switch 默认值 | ✅（enablePlaceDetails=0） |

**最终状态**：**PARTIAL-NON-BLOCKER**
**负向证据**：
- N6：代码层 PERMISSION_DENIED 逻辑可达，Phase 5 实证降级（I029 优先）
- N7：代码层 100% 确认 Place Details 未写日志（I031 维持）
- C1：前端 el-switch 实际可用（D12 不加 UI，沿用 I006/I007 模式）

**修复 Phase**：Post-Audit（建表后补 Phase 5 实证 + 加日志 try/catch）