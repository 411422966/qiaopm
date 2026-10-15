# F059 — GoogleMapsClientConfig

**状态**：UNVERIFIED（GCP 凭据缺失，D10 铁律）
**验证类型**：Static-Only + 启动日志
**测试时间**：2026-09-14 00:40 (UTC+8)

## 1. 类定义（L0 静态）

- 文件：`backend/src/main/java/com/meiyetec/googlemaps/GoogleMapsClientConfig.java`
- 全文件 16 行

```java
@Configuration
public class GoogleMapsClientConfig {

    @Bean
    public GoogleMapsGeocodingClient googleMapsGeocodingClient(ObjectMapper jackson) {
        return new GoogleMapsGeocodingClient(jackson);
    }
}
```

## 2. 注解分析

| 注解 | 有无 | 备注 |
|---|---|---|
| `@Configuration` | ✅ | 标准 Spring 配置类 |
| `@Bean` | ✅ | 注册 `googleMapsGeocodingClient` |
| `@ConditionalOnMissingBean(name = "googleMapsGeocodingClient")` | ❌ | 无 fallback — Bean 始终被覆盖 |
| `@ConditionalOnProperty(name = "google.maps.enabled", havingValue = "true")` | ❌ | 无总开关 |

**影响**：
- 没有 Mock fallback — 如果 `googleMapsGeocodingClient` Bean 被其他 Config 覆盖，行为不可预期
- 没有 enabled 开关 — Bean 永远被加载

## 3. 启动期反推

错误栈 L2538 等显示 `GoogleMapsConfigMapper` / `GoogleMapsGeocodeLogMapper` 在调用时被注入并抛 SQL 异常，
说明 Service 链路上所有 Bean 都已成功构造（含 `googleMapsGeocodingClient`）。

## 4. 结论

| 维度 | 结果 |
|---|---|
| 配置类编译 | ✅ |
| Bean 注册 | ✅（启动期反推） |
| Fallback 机制 | ❌ 无（与 EXT-22 §0.5 一致） |
| 总开关 | ❌ 无（与 `plugin.runtime.enabled` 不同） |

**最终状态**：**UNVERIFIED**（无真实 GCP 调用可验证，D10 铁律）
**降级原因**：GCP 凭据缺失，无 fallback 可走
**修复 Phase**：Post-Audit（GCP 凭据接入 + Mock fallback 设计）