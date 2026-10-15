package com.meiyetec.googlemaps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXT-17：Google Maps 地理编码客户端单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>URL 构造（含中文地址 URL 编码）</li>
 *   <li>输入校验：空 apiKey / 空 address → 失败状态码</li>
 *   <li>GeocodeResult.ok() 判定</li>
 *   <li>DTO 字段正确</li>
 * </ul>
 *
 * <p>真实 HTTP 调用不在单测覆盖范围（需 mock server / 联调环境）。
 */
@DisplayName("EXT-17 Google Maps Client")
class GoogleMapsGeocodingTest {

    private final ObjectMapper jackson = new ObjectMapper();
    private final GoogleMapsGeocodingClient client = new GoogleMapsGeocodingClient(jackson);

    // ==================== URL 构造 ====================

    @Test
    @DisplayName("geocode URL 合法：含 key / address / language / region")
    void geocodeUrl() {
        String url = GoogleMapsGeocodingClient.buildGeocodeUrl("AIza-test", "北京市朝阳区三里屯", "zh-CN", "cn");
        assertTrue(url.startsWith("https://maps.googleapis.com/maps/api/geocode/json?"));
        assertTrue(url.contains("key=AIza-test"));
        assertTrue(url.contains("address="));
        assertTrue(url.contains("language=zh-CN"));
        assertTrue(url.contains("region=cn"));
        // 中文应该被 URL 编码（UTF-8 百分号编码）
        assertTrue(url.contains("%E5%8C%97%E4%BA%AC"), "中文应被百分号编码");
    }

    @Test
    @DisplayName("geocode URL：language/region 为空时使用默认值")
    void geocodeUrlDefaults() {
        String url = GoogleMapsGeocodingClient.buildGeocodeUrl("k", "Beijing", null, null);
        assertTrue(url.contains("language=zh-CN"));
        assertTrue(url.contains("region=cn"));
    }

    @Test
    @DisplayName("findplace URL 合法：input + inputtype + fields + key")
    void findPlaceUrl() {
        String url = GoogleMapsGeocodingClient.buildFindPlaceUrl("AIza-test", "三里屯 SOHO", "zh-CN");
        assertTrue(url.startsWith("https://maps.googleapis.com/maps/api/place/findplacefromtext/json?"));
        assertTrue(url.contains("inputtype=textquery"));
        assertTrue(url.contains("fields=formatted_address%2Cgeometry%2Cplace_id%2Cname"),
                "fields 多值逗号应被百分号编码");
        assertTrue(url.contains("key=AIza-test"));
        assertTrue(url.contains("language=zh-CN"));
    }

    // ==================== 输入校验（不发 HTTP） ====================

    @Test
    @DisplayName("geocode：空 apiKey → REQUEST_DENIED")
    void geocodeEmptyKey() {
        GoogleMapsGeocodingClient.GeocodeResult r = client.geocode("", "Beijing", "en", "cn");
        assertEquals("REQUEST_DENIED", r.getStatus());
        assertNotNull(r.getErrorMessage());
    }

    @Test
    @DisplayName("geocode：空 address → INVALID_REQUEST")
    void geocodeEmptyAddress() {
        GoogleMapsGeocodingClient.GeocodeResult r = client.geocode("AIza-test", "", "en", "cn");
        assertEquals("INVALID_REQUEST", r.getStatus());
    }

    @Test
    @DisplayName("geocode：null 参数 → 安全返回失败状态")
    void geocodeNullParams() {
        GoogleMapsGeocodingClient.GeocodeResult r1 = client.geocode(null, "Beijing", "en", "cn");
        assertEquals("REQUEST_DENIED", r1.getStatus());
        GoogleMapsGeocodingClient.GeocodeResult r2 = client.geocode("k", null, "en", "cn");
        assertEquals("INVALID_REQUEST", r2.getStatus());
    }

    @Test
    @DisplayName("findPlace：空 apiKey → REQUEST_DENIED")
    void findPlaceEmptyKey() {
        GoogleMapsGeocodingClient.GeocodeResult r = client.findPlace("", "Beijing", "en");
        assertEquals("REQUEST_DENIED", r.getStatus());
    }

    // ==================== DTO 判定 ====================

    @Test
    @DisplayName("GeocodeResult.ok()：OK + lat/lng 非空")
    void geocodeResultOk() {
        GoogleMapsGeocodingClient.GeocodeResult r = new GoogleMapsGeocodingClient.GeocodeResult();
        assertFalse(r.ok(), "默认状态非 OK");

        r.setStatus("OK");
        assertFalse(r.ok(), "OK 但 lat/lng 仍为空时不算 ok");

        r.setLat(new BigDecimal("39.9"));
        r.setLng(new BigDecimal("116.4"));
        assertTrue(r.ok());

        r.setStatus("ZERO_RESULTS");
        assertFalse(r.ok());
    }

    @Test
    @DisplayName("GeocodeResult 字段默认值")
    void geocodeResultDefaults() {
        GoogleMapsGeocodingClient.GeocodeResult r = new GoogleMapsGeocodingClient.GeocodeResult();
        assertNull(r.getLat());
        assertNull(r.getLng());
        assertNull(r.getStatus());
        assertNull(r.getRequestAddress());
    }
}
