# F061 — GoogleMapsService `/geocode` Mock 降级

**状态**：MOCK-INTENTIONAL（user decision #1 Mock 降级一致）
**验证类型**：Static-Only + API-only（受限）
**测试时间**：2026-09-14 00:39 (UTC+8)

## 1. 端点 L0 静态

| 项 | 值 |
|---|---|
| 路径 | `POST /google-maps/geocode` |
| 行号 | `GoogleMapsService.geocode` L139-180 |
| 入参 | `Map<String,Object>` body，必填 `address` |
| 降级路径 | `mockGeocode(address)` L335-348 |

## 2. mockGeocode 实现（L335-348）

```java
private GoogleMapsGeocodingClient.GeocodeResult mockGeocode(String address) {
    Random r = new Random();
    GoogleMapsGeocodingClient.GeocodeResult res = new GoogleMapsGeocodingClient.GeocodeResult();
    res.setRequestAddress(address);
    res.setFormattedAddress(address);
    res.setLat(BigDecimal.valueOf(30 + r.nextDouble() * 10).setScale(7, java.math.RoundingMode.HALF_UP));
    res.setLng(BigDecimal.valueOf(110 + r.nextDouble() * 20).setScale(7, java.math.RoundingMode.HALF_UP));
    res.setPlaceId("ChIJ" + System.currentTimeMillis());
    res.setStatus("OK");
    res.setElapsedMs(10L);
    return res;
}
```

→ Mock 返回 `lat ∈ [30, 40]` + `lng ∈ [110, 130]`（中国范围）+ `placeId = ChIJ{ms时间戳}` + `status = OK`。

## 3. 降级触发条件（L144-150）

```java
if (cfg != null && cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
    // 真实链路
    r = geocodingClient.geocode(cfg.getApiKey(), address, ...);
} else {
    // 降级到 mock
    r = mockGeocode(address);
}
```

→ 触发 Mock 降级需 `cfg == null`（无配置）或 `cfg.apiKey.isEmpty()`（缺凭据）。
→ **DB 缺失导致 cfg 查不到** → 预期走 Mock 路径。

## 4. Phase 5 API 实证

### 4.1 admin 调用

```bash
curl.exe -X POST http://localhost:8080/google-maps/geocode \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json;charset=UTF-8" \
  --data-binary '{"address":"北京"}'
```

**结果**：

```json
{"code":500,"message":"数据库异常","data":null,"traceId":"a237dc4cca9f41d4be8f5983f6015c6c"}
HTTP=200
```

→ **I029 MISSING 优先暴露**：service L145 的 `configMapper.selectOne(...)` 直接抛 `BadSqlGrammarException`，**未到 mock 降级分支**。

### 4.2 实证降级

**预期**：Mock 降级路径应在 I029 MISSING 条件下生效（cfg == null 时走 mock）。
**实际**：MyBatis 调用 SQL 时抛异常，**不会**返回空 cfg — 异常直接抛到 controller。
→ Mock 路径在 Phase 5 无法触发（需建表 + 不插配置才走 mock）。

证据文件：
- `audit/evidence/F061-googlemaps-service-geocode-mock/raw-geocode-post.txt`

## 5. I031 写日志分析

L160-176 try/catch 写日志（geocodeLog）：

```java
try {
    GoogleMapsGeocodeLog log = new GoogleMapsGeocodeLog();
    ...
    logMapper.insert(log);
} catch (Exception ignored) {}
```

→ 表缺失时**静默丢日志**，不抛业务异常（与 F057 一致）。
→ I031 维持（geocode 已写日志，placeDetails 未写 — 详见 F062）。

## 6. 结论

| 维度 | 结果 |
|---|---|
| Mock 降级路径存在 | ✅（L335-348） |
| Mock 范围合规（lat∈[30,40] lng∈[110,130]）| ✅ |
| Phase 5 实证 Mock 触发 | ❌（I029 优先暴露 DB 异常） |
| I031 写日志代码层 | ✅（geocode 路径） |

**最终状态**：**MOCK-INTENTIONAL**（user decision #1 Mock 降级一致）
**降级原因**：Phase 5 无法在 I029 MISSING 条件下触发 mock 分支
**修复 Phase**：Post-Audit（建表后单测覆盖 mockGeocode 范围合规性）