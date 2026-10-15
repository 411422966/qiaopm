package com.meiyetec.googlemaps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * EXT-17：Google Maps HTTP 客户端（Geocoding + Places）
 *
 * <p>Geocoding API：
 * <pre>
 *   GET https://maps.googleapis.com/maps/api/geocode/json
 *       ?key={key}&address={url-encoded}&language=zh-CN&region=cn
 *   resp: { "status":"OK", "results":[{"formatted_address":"...",
 *          "geometry":{"location":{"lat":39.9,"lng":116.4}}, "place_id":"ChIJ..."}] }
 * </pre>
 *
 * <p>Places API — Find Place from Text：
 * <pre>
 *   GET https://maps.googleapis.com/maps/api/place/findplacefromtext/json
 *       ?input={query}&inputtype=textquery&fields=formatted_address,geometry,place_id,name&key={key}
 * </pre>
 *
 * <p>本类只做 HTTP + JSON 解析；配置由调用方注入（解耦 config 存储）。
 */
@Slf4j
public class GoogleMapsGeocodingClient {

    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String PLACES_URL  = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json";
    private static final String PLACE_DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json";

    private final ObjectMapper jackson;

    public GoogleMapsGeocodingClient(ObjectMapper jackson) {
        this.jackson = jackson;
    }

    // ============================================================
    //  URL 构造（暴露为 package-private，便于单测验证）
    // ============================================================

    static String buildGeocodeUrl(String apiKey, String address, String language, String region) {
        return GEOCODE_URL
                + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                + "&language=" + URLEncoder.encode(language == null ? "zh-CN" : language, StandardCharsets.UTF_8)
                + "&region=" + URLEncoder.encode(region == null ? "cn" : region, StandardCharsets.UTF_8);
    }

    static String buildFindPlaceUrl(String apiKey, String query, String language) {
        return PLACES_URL
                + "?input=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&inputtype=textquery"
                + "&fields=formatted_address,geometry,place_id,name"
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + (language == null ? "" : "&language=" + URLEncoder.encode(language, StandardCharsets.UTF_8));
    }

    static String buildPlaceDetailsUrl(String apiKey, String placeId, String language) {
        return PLACE_DETAILS_URL
                + "?place_id=" + URLEncoder.encode(placeId, StandardCharsets.UTF_8)
                + "&fields=name,formatted_address,formatted_phone_number,international_phone_number,"
                + "website,opening_hours,rating,user_ratings_total,business_status,geometry,types"
                + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + (language == null ? "" : "&language=" + URLEncoder.encode(language, StandardCharsets.UTF_8));
    }

    // ============================================================
    //  Geocoding
    // ============================================================

    /**
     * 地址 → 经纬度
     *
     * @param apiKey   Google Maps API Key
     * @param address  待解析地址
     * @param language 语言（可空，默认 "zh-CN"）
     * @param region   区域 bias（可空，默认 "cn"）
     */
    public GeocodeResult geocode(String apiKey, String address, String language, String region) {
        long start = System.currentTimeMillis();
        GeocodeResult r = new GeocodeResult();
        r.requestAddress = address;
        if (apiKey == null || apiKey.isEmpty()) {
            r.status = "REQUEST_DENIED";
            r.errorMessage = "apiKey is empty";
            r.elapsedMs = System.currentTimeMillis() - start;
            return r;
        }
        if (address == null || address.isEmpty()) {
            r.status = "INVALID_REQUEST";
            r.errorMessage = "address is empty";
            r.elapsedMs = System.currentTimeMillis() - start;
            return r;
        }
        try {
            String url = GEOCODE_URL
                    + "?key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + "&address=" + URLEncoder.encode(address, "UTF-8")
                    + "&language=" + URLEncoder.encode(language == null ? "zh-CN" : language, "UTF-8")
                    + "&region=" + URLEncoder.encode(region == null ? "cn" : region, "UTF-8");
            String body = httpGet(url, 5000, 15000);
            JsonNode root = jackson.readTree(body);
            r.status = root.path("status").asText("UNKNOWN_ERROR");
            r.rawResponse = body;
            if ("OK".equals(r.status) && root.path("results").isArray() && root.path("results").size() > 0) {
                JsonNode first = root.path("results").get(0);
                r.formattedAddress = first.path("formatted_address").asText(null);
                r.placeId = first.path("place_id").asText(null);
                JsonNode loc = first.path("geometry").path("location");
                r.lat = new BigDecimal(loc.path("lat").asText());
                r.lng = new BigDecimal(loc.path("lng").asText());
            } else if (!"OK".equals(r.status) && !"ZERO_RESULTS".equals(r.status)) {
                r.errorMessage = root.path("error_message").asText(r.status);
            }
            r.elapsedMs = System.currentTimeMillis() - start;
            log.info("[GOOGLE-MAPS] geocode: addr='{}' status={} lat={} lng={} elapsed={}ms",
                    address, r.status, r.lat, r.lng, r.elapsedMs);
            return r;
        } catch (Exception e) {
            r.status = "NETWORK_ERROR";
            r.errorMessage = e.getMessage();
            r.elapsedMs = System.currentTimeMillis() - start;
            log.warn("[GOOGLE-MAPS] geocode failed: addr='{}' err={}", address, e.getMessage());
            return r;
        }
    }

    // ============================================================
    //  Places — Find Place from Text
    // ============================================================

    /**
     * 文本 → Place（含 lat/lng/place_id/formatted_address）
     */
    public GeocodeResult findPlace(String apiKey, String query, String language) {
        long start = System.currentTimeMillis();
        GeocodeResult r = new GeocodeResult();
        r.requestAddress = query;
        if (apiKey == null || apiKey.isEmpty()) {
            r.status = "REQUEST_DENIED";
            r.errorMessage = "apiKey is empty";
            r.elapsedMs = System.currentTimeMillis() - start;
            return r;
        }
        try {
            String url = PLACES_URL
                    + "?input=" + URLEncoder.encode(query, "UTF-8")
                    + "&inputtype=textquery"
                    + "&fields=formatted_address,geometry,place_id,name"
                    + "&key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + (language == null ? "" : "&language=" + URLEncoder.encode(language, "UTF-8"));
            String body = httpGet(url, 5000, 15000);
            JsonNode root = jackson.readTree(body);
            r.status = root.path("status").asText("UNKNOWN_ERROR");
            r.rawResponse = body;
            JsonNode candidates = root.path("candidates");
            if ("OK".equals(r.status) && candidates.isArray() && candidates.size() > 0) {
                JsonNode first = candidates.get(0);
                r.formattedAddress = first.path("formatted_address").asText(null);
                r.placeId = first.path("place_id").asText(null);
                JsonNode loc = first.path("geometry").path("location");
                r.lat = new BigDecimal(loc.path("lat").asText());
                r.lng = new BigDecimal(loc.path("lng").asText());
            } else if (!"OK".equals(r.status) && !"ZERO_RESULTS".equals(r.status)) {
                r.errorMessage = root.path("error_message").asText(r.status);
            }
            r.elapsedMs = System.currentTimeMillis() - start;
            log.info("[GOOGLE-MAPS] findPlace: query='{}' status={} elapsed={}ms",
                    query, r.status, r.elapsedMs);
            return r;
        } catch (Exception e) {
            r.status = "NETWORK_ERROR";
            r.errorMessage = e.getMessage();
            r.elapsedMs = System.currentTimeMillis() - start;
            log.warn("[GOOGLE-MAPS] findPlace failed: query='{}' err={}", query, e.getMessage());
            return r;
        }
    }

    // ============================================================
    //  Places — Place Details（计费接口，调用需 enablePlaceDetails）
    // ============================================================

    /**
     * Place 详情（按 place_id 拿电话 / 营业时间 / 评分等）
     *
     * @return {@link PlaceDetailResult}；失败 status != "OK"
     */
    public PlaceDetailResult placeDetails(String apiKey, String placeId, String language) {
        long start = System.currentTimeMillis();
        PlaceDetailResult r = new PlaceDetailResult();
        r.placeId = placeId;
        if (apiKey == null || apiKey.isEmpty()) {
            r.status = "REQUEST_DENIED";
            r.errorMessage = "apiKey is empty";
            r.elapsedMs = System.currentTimeMillis() - start;
            return r;
        }
        if (placeId == null || placeId.isEmpty()) {
            r.status = "INVALID_REQUEST";
            r.errorMessage = "placeId is empty";
            r.elapsedMs = System.currentTimeMillis() - start;
            return r;
        }
        try {
            String url = PLACE_DETAILS_URL
                    + "?place_id=" + URLEncoder.encode(placeId, "UTF-8")
                    + "&fields=name,formatted_address,formatted_phone_number,international_phone_number,"
                    + "website,opening_hours,rating,user_ratings_total,business_status,geometry,types"
                    + "&key=" + URLEncoder.encode(apiKey, "UTF-8")
                    + (language == null ? "" : "&language=" + URLEncoder.encode(language, "UTF-8"));
            String body = httpGet(url, 5000, 15000);
            JsonNode root = jackson.readTree(body);
            r.status = root.path("status").asText("UNKNOWN_ERROR");
            r.rawResponse = body;
            if ("OK".equals(r.status)) {
                JsonNode result = root.path("result");
                r.name = result.path("name").asText(null);
                r.formattedAddress = result.path("formatted_address").asText(null);
                r.formattedPhoneNumber = result.path("formatted_phone_number").asText(null);
                r.internationalPhoneNumber = result.path("international_phone_number").asText(null);
                r.website = result.path("website").asText(null);
                r.rating = result.path("rating").isMissingNode() || result.path("rating").isNull()
                        ? null : new BigDecimal(result.path("rating").asText());
                r.userRatingsTotal = result.path("user_ratings_total").isMissingNode() || result.path("user_ratings_total").isNull()
                        ? null : result.path("user_ratings_total").asInt();
                r.businessStatus = result.path("business_status").asText(null);
                JsonNode hours = result.path("opening_hours");
                if (hours.isObject()) {
                    r.openNow = hours.path("open_now").asBoolean(false);
                    JsonNode wd = hours.path("weekday_text");
                    if (wd.isArray()) {
                        List<String> list = new ArrayList<>();
                        for (JsonNode n : wd) list.add(n.asText());
                        r.weekdayText = list;
                    }
                }
                JsonNode types = result.path("types");
                if (types.isArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonNode n : types) list.add(n.asText());
                    r.types = list;
                }
                JsonNode loc = result.path("geometry").path("location");
                if (!loc.isMissingNode()) {
                    r.lat = new BigDecimal(loc.path("lat").asText());
                    r.lng = new BigDecimal(loc.path("lng").asText());
                }
            } else if (!"OK".equals(r.status) && !"ZERO_RESULTS".equals(r.status)) {
                r.errorMessage = root.path("error_message").asText(r.status);
            }
            r.elapsedMs = System.currentTimeMillis() - start;
            log.info("[GOOGLE-MAPS] placeDetails: placeId={} status={} elapsed={}ms",
                    placeId, r.status, r.elapsedMs);
            return r;
        } catch (Exception e) {
            r.status = "NETWORK_ERROR";
            r.errorMessage = e.getMessage();
            r.elapsedMs = System.currentTimeMillis() - start;
            log.warn("[GOOGLE-MAPS] placeDetails failed: placeId={} err={}", placeId, e.getMessage());
            return r;
        }
    }

    // ============================================================
    //  HTTP
    // ============================================================

    private String httpGet(String url, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String body = sb.toString();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("HTTP " + code + ": " + body);
            }
            return body;
        }
    }

    // ============================================================
    //  DTO
    // ============================================================

    @Data
    public static class GeocodeResult {
        /** 请求地址（入参） */
        private String requestAddress;
        /** 规范化地址（响应） */
        private String formattedAddress;
        /** 纬度 */
        private BigDecimal lat;
        /** 经度 */
        private BigDecimal lng;
        /** Google Place ID */
        private String placeId;
        /** 状态：OK / ZERO_RESULTS / OVER_QUERY_LIMIT / REQUEST_DENIED / INVALID_REQUEST / NETWORK_ERROR / UNKNOWN_ERROR */
        private String status;
        private String errorMessage;
        /** 原始 JSON（用于审计 / 调试） */
        private String rawResponse;
        /** 耗时毫秒 */
        private Long elapsedMs;

        public boolean ok() {
            return "OK".equals(status) && lat != null && lng != null;
        }
    }

    /**
     * Place Details 详情（电话 / 营业时间 / 评分）。
     * 计费接口：调用前必须确认 {@code google_maps_config.enablePlaceDetails = 1}。
     */
    @Data
    public static class PlaceDetailResult {
        private String placeId;
        private String name;
        private String formattedAddress;
        private String formattedPhoneNumber;
        private String internationalPhoneNumber;
        private String website;
        /** 当前是否营业 */
        private Boolean openNow;
        /** 营业时间（按周） */
        private List<String> weekdayText;
        private BigDecimal rating;
        private Integer userRatingsTotal;
        /** OPERATIONAL / CLOSED_TEMPORARILY / CLOSED_PERMANENTLY */
        private String businessStatus;
        /** 类别（如 beauty_salon, store） */
        private List<String> types;
        private BigDecimal lat;
        private BigDecimal lng;
        /** 状态：OK / ZERO_RESULTS / NOT_FOUND / OVER_QUERY_LIMIT / REQUEST_DENIED / INVALID_REQUEST / NETWORK_ERROR */
        private String status;
        private String errorMessage;
        /** 原始 JSON */
        private String rawResponse;
        private Long elapsedMs;

        public boolean ok() {
            return "OK".equals(status) && placeId != null;
        }
    }
}
