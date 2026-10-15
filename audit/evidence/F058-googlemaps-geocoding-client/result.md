# F058 — GoogleMapsGeocodingClient

**状态**：UNVERIFIED（GCP 凭据缺失，D10 铁律）
**验证类型**：Static-Only + 启动日志
**测试时间**：2026-09-14 00:40 (UTC+8)

## 1. 类定义（L0 静态）

- 文件：`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsGeocodingClient.java`
- 类签名：`public class GoogleMapsGeocodingClient { ... }`
- 方法：`geocode(...)` L122-181 + `placeDetails(...)` L202-282 + `buildPlaceDetailsUrl(...)` L71-95 + `buildGeocodeUrl(...)` L97-115
- placeDetails fields 列表（L74）：`name,formatted_address,formatted_phone_number,international_phone_number,website,opening_hours,rating,user_ratings_total,business_status,geometry,types`
  - **全部 11 字段一次性**（EXT-22 §0.3 命中 Basic+Contact+Atmosphere 三 SKU）

## 2. Bean 注册验证

- 注册位置：`GoogleMapsClientConfig.googleMapsGeocodingClient(ObjectMapper)` L13-16
- `@Configuration` + `@Bean`（无条件 — **无 `@ConditionalOnMissingBean` fallback**，与 EXT-22 §0.5 一致）

```java
@Configuration
public class GoogleMapsClientConfig {
    @Bean
    public GoogleMapsGeocodingClient googleMapsGeocodingClient(ObjectMapper jackson) {
        return new GoogleMapsGeocodingClient(jackson);
    }
}
```

## 3. 启动日志

后端启动日志未在 INFO 级别打印 Bean 注册（Spring 默认行为）。
但 `backend/logs/meiyetec.log` 中可见 5 处异常栈 trace（L2538/L2664/L2840/L2974/L3101/L3227），
均显示 `GoogleMapsService` 已成功注入 `GoogleMapsGeocodingClient`（错误在调用时抛 SQL mapper 异常，不是构造时 NPE）。
→ **反推 Bean 已成功注册**。

## 4. 真实链路可验证性

| 维度 | 状态 |
|---|---|
| 真实 GCP Geocoding API 调用 | ❌ UNVERIFIED（GCP API Key 缺失） |
| 真实 GCP Place Details API 调用 | ❌ UNVERIFIED（GCP API Key 缺失） |
| Mock 降级路径（geocodeStore）| ✅ L335-348 mockGeocode(address) 路径存在 |
| 连接超时配置 | ✅ L107 connect=5s, read=15s |

## 5. 结论

| 维度 | 结果 |
|---|---|
| 类编译 | ✅ |
| Bean 注册 | ✅（启动期反推） |
| 真实 GCP 调用 | ❌ UNVERIFIED |
| Mock 降级 | ✅（代码可达） |

**最终状态**：**UNVERIFIED**（GCP 凭据缺失，D10 铁律，不造证据）
**降级原因**：user decision #1 — Mock 降级
**修复 Phase**：Post-Audit（GCP 凭据接入 + 测试凭据沙箱）