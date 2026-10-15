package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.GoogleMapsConfig;
import com.meiyetec.entity.GoogleMapsGeocodeLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.googlemaps.GoogleMapsGeocodingClient;
import com.meiyetec.mapper.GoogleMapsConfigMapper;
import com.meiyetec.mapper.GoogleMapsGeocodeLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * EXT-17：Google Maps 集成（真实 API）
 *
 * <p>配置存 {@code google_maps_config} 表（按 tenant + brand 隔离，参考 mkt_channel_config 结构）；
 * {@code /geocode} 走真实 Geocoding API；未配置时降级到内存 mock（保持原有 demo 行为）。
 *
 * <p>API Key 不在响应中明文返回（仅回显后 4 位）。
 */
@Slf4j
@RestController
@RequestMapping("/google-maps")
@RequiredArgsConstructor
public class GoogleMapsService {

    private final GoogleMapsGeocodingClient geocodingClient;
    private final GoogleMapsConfigMapper configMapper;
    private final GoogleMapsGeocodeLogMapper logMapper;

    /** 演示用门店（保留旧 mock 数据，无配置时仍可展示） */
    private static final List<Map<String, Object>> STORES = Collections.synchronizedList(new ArrayList<>(List.of(
            Map.of("id", 1, "name", "北京三里屯店", "lat", 39.9376, "lng", 116.4474,
                    "address", "北京市朝阳区三里屯 SOHO", "monthlyBookings", 320),
            Map.of("id", 2, "name", "上海徐汇店", "lat", 31.1958, "lng", 121.4365,
                    "address", "上海市徐汇区港汇恒隆广场", "monthlyBookings", 285),
            Map.of("id", 3, "name", "深圳南山店", "lat", 22.5333, "lng", 113.9361,
                    "address", "深圳市南山区万象天地", "monthlyBookings", 240),
            Map.of("id", 4, "name", "纽约店 (USA)", "lat", 40.7128, "lng", -74.0060,
                    "address", "Manhattan, NY", "monthlyBookings", 95),
            Map.of("id", 5, "name", "东京店 (JP)", "lat", 35.6762, "lng", 139.6503,
                    "address", "Shibuya, Tokyo", "monthlyBookings", 130)
    )));

    // ============================================================
    //  配置 CRUD（存 google_maps_config）
    // ============================================================

    @PostMapping("/config")
    public Result<Map<String, Object>> saveConfig(@RequestBody GoogleMapsConfig body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (body.getApiKey() == null || body.getApiKey().isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "apiKey 必填");
        if (body.getDefaultLanguage() == null) body.setDefaultLanguage("zh-CN");
        if (body.getDefaultRegion() == null) body.setDefaultRegion("cn");
        if (body.getQuotaDaily() == null) body.setQuotaDaily(10000);
        if (body.getEnabled() == null) body.setEnabled(1);
        if (body.getEnablePlaceDetails() == null) body.setEnablePlaceDetails(0);

        // upsert by (tenant + brand)
        GoogleMapsConfig existing = configMapper.selectOne(new LambdaQueryWrapper<GoogleMapsConfig>()
                .eq(GoogleMapsConfig::getTenantId, tenantId)
                .eq(GoogleMapsConfig::getBrandId, body.getBrandId() == null ? 0L : body.getBrandId()));
        body.setTenantId(tenantId);
        if (existing == null) {
            body.setId(null);
            configMapper.insert(body);
        } else {
            body.setId(existing.getId());
            configMapper.updateById(body);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("zoom", 13);
        data.put("configId", body.getId());
        return Result.success("已保存", data);
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        GoogleMapsConfig c = configMapper.selectOne(new LambdaQueryWrapper<GoogleMapsConfig>()
                .eq(GoogleMapsConfig::getTenantId, tenantId)
                .orderByDesc(GoogleMapsConfig::getId)
                .last("LIMIT 1"));
        if (c == null) {
            return Result.success(Map.of("configured", false));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("configured", true);
        r.put("defaultLanguage", c.getDefaultLanguage());
        r.put("defaultRegion", c.getDefaultRegion());
        r.put("quotaDaily", c.getQuotaDaily());
        r.put("enabled", c.getEnabled());
        r.put("enablePlaceDetails", c.getEnablePlaceDetails());
        r.put("lastHealthAt", c.getLastHealthAt());
        r.put("lastHealthMsg", c.getLastHealthMsg());
        String apiKey = c.getApiKey() == null ? "" : c.getApiKey();
        // I036 修复：API Key 完全遮蔽，不回显后 4 位（生产安全：避免 39 字符 key 前 35 位可猜测）
        r.put("apiKey", "***CONFIGURED***");
        r.put("apiKeyLength", apiKey.length());
        return Result.success(r);
    }

    /** 健康检查：用一次最简请求验证 API Key 有效 */
    @PostMapping("/health")
    public Result<Map<String, Object>> health() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        GoogleMapsConfig c = configMapper.selectOne(new LambdaQueryWrapper<GoogleMapsConfig>()
                .eq(GoogleMapsConfig::getTenantId, tenantId)
                .eq(GoogleMapsConfig::getEnabled, 1)
                .orderByDesc(GoogleMapsConfig::getId)
                .last("LIMIT 1"));
        if (c == null) return Result.error(BizCode.NOT_FOUND, "未配置或未启用");
        // 用一个肯定存在的地址做 geocode
        GoogleMapsGeocodingClient.GeocodeResult r =
                geocodingClient.geocode(c.getApiKey(), "Beijing", "en", "cn");
        // 写回健康状态
        c.setLastHealthAt(LocalDateTime.now());
        c.setLastHealthMsg(r.ok() ? "OK" : (r.getErrorMessage() == null ? r.getStatus() : r.getErrorMessage()));
        configMapper.updateById(c);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("healthy", r.ok());
        data.put("status", r.getStatus());
        data.put("message", c.getLastHealthMsg());
        data.put("elapsedMs", r.getElapsedMs());
        return Result.success(data);
    }

    // ============================================================
    //  地理编码（真实 / Mock 降级）
    // ============================================================

    @PostMapping("/geocode")
    public Result<Map<String, Object>> geocode(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String address = (String) body.get("address");
        if (address == null || address.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "address 必填");

        GoogleMapsConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<GoogleMapsConfig>()
                .eq(GoogleMapsConfig::getTenantId, tenantId)
                .eq(GoogleMapsConfig::getEnabled, 1)
                .orderByDesc(GoogleMapsConfig::getId)
                .last("LIMIT 1"));

        GoogleMapsGeocodingClient.GeocodeResult r;
        if (cfg != null && cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
            // 真实链路
            r = geocodingClient.geocode(cfg.getApiKey(), address,
                    cfg.getDefaultLanguage(), cfg.getDefaultRegion());
        } else {
            // 降级到 mock（保留 demo 体验）
            r = mockGeocode(address);
        }

        // 写日志（无论真实/降级）
        try {
            GoogleMapsGeocodeLog log = new GoogleMapsGeocodeLog();
            log.setTenantId(tenantId);
            log.setAddress(address);
            log.setFormattedAddress(r.getFormattedAddress());
            log.setLat(r.getLat());
            log.setLng(r.getLng());
            log.setPlaceId(r.getPlaceId());
            log.setStatus(r.getStatus());
            log.setErrorMessage(r.getErrorMessage());
            log.setDurationMs(r.getElapsedMs() == null ? null : r.getElapsedMs().intValue());
            logMapper.insert(log);
        } catch (Exception e) {
            log.warn("[GOOGLE-MAPS] geocode log write failed: address={} err={}", address, e.getMessage());
        }

        if (!r.ok()) {
            return Result.error(BizCode.SYSTEM_ERROR,
                    "geocode " + r.getStatus() + ": " + r.getErrorMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("address", address);
        data.put("formattedAddress", r.getFormattedAddress());
        data.put("lat", r.getLat());
        data.put("lng", r.getLng());
        data.put("placeId", r.getPlaceId());
        data.put("source", cfg != null ? "GOOGLE_MAPS" : "MOCK");
        return Result.success("已解析", data);
    }

    // ============================================================
    //  Places API — 详情（计费接口，需 enablePlaceDetails=1）
    // ============================================================

    /**
     * Place Details：按 place_id 拿电话 / 营业时间 / 评分。
     * <p>Google 计费 SKUs：Basic Data ($17/1000)，Contact Data ($3/1000)，Atmosphere Data ($5/1000)。
     * <p>门禁：需 {@code google_maps_config.enablePlaceDetails = 1}；关闭时返回 PERMISSION_DENIED。
     * <p>降级：未配置 apiKey 时降级到 mock（与 /geocode 行为对齐），保留 demo 体验；⛔ enablePlaceDetails 计费闸门不降级，避免掩盖未开通计费的事实。
     */
    @PostMapping("/place/details")
    public Result<Map<String, Object>> placeDetails(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String placeId = body.get("placeId") == null ? null : body.get("placeId").toString().trim();
        if (placeId == null || placeId.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "placeId 必填");
        }
        GoogleMapsConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<GoogleMapsConfig>()
                .eq(GoogleMapsConfig::getTenantId, tenantId)
                .eq(GoogleMapsConfig::getEnabled, 1)
                .orderByDesc(GoogleMapsConfig::getId)
                .last("LIMIT 1"));

        // ⭐ I031 fix：cfg 缺失或 apiKey 为空 → 降级到 mock；否则走真实链路（计费闸门仍生效）
        GoogleMapsGeocodingClient.PlaceDetailResult r;
        boolean isMock;
        if (cfg == null || cfg.getApiKey() == null || cfg.getApiKey().isEmpty()) {
            r = mockPlaceDetails(placeId);
            isMock = true;
        } else {
            // 计费开关：默认关闭
            if (cfg.getEnablePlaceDetails() == null || cfg.getEnablePlaceDetails() != 1) {
                return Result.error(BizCode.PERMISSION_DENIED,
                        "Place Details 接口已关闭，请在 google_maps_config 启用 enablePlaceDetails=1");
            }
            String lang = cfg.getDefaultLanguage() == null ? "zh-CN" : cfg.getDefaultLanguage();
            r = geocodingClient.placeDetails(cfg.getApiKey(), placeId, lang);
            isMock = false;
        }

        // ⭐ I031 fix：复用 google_maps_geocode_log 写入（与 /geocode L165-177 逻辑一致）
        try {
            GoogleMapsGeocodeLog log = new GoogleMapsGeocodeLog();
            log.setTenantId(tenantId);
            log.setAddress("[PlaceDetails] " + placeId);   // 占位：与 geocode 请求区分
            log.setFormattedAddress(r.getFormattedAddress());
            log.setLat(r.getLat());
            log.setLng(r.getLng());
            log.setPlaceId(r.getPlaceId());
            log.setStatus(r.getStatus());
            log.setErrorMessage(r.getErrorMessage());
            log.setDurationMs(r.getElapsedMs() == null ? null : r.getElapsedMs().intValue());
            logMapper.insert(log);
        } catch (Exception e) {
            log.warn("[GOOGLE-MAPS] log write failed: placeId={} err={}", placeId, e.getMessage());
        }

        if (!r.ok()) {
            return Result.error(BizCode.SYSTEM_ERROR,
                    "place details " + r.getStatus() + ": " + r.getErrorMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("placeId", placeId);
        data.put("name", r.getName());
        data.put("formattedAddress", r.getFormattedAddress());
        data.put("formattedPhoneNumber", r.getFormattedPhoneNumber());
        data.put("internationalPhoneNumber", r.getInternationalPhoneNumber());
        data.put("website", r.getWebsite());
        data.put("openNow", r.getOpenNow());
        data.put("weekdayText", r.getWeekdayText());
        data.put("rating", r.getRating());
        data.put("userRatingsTotal", r.getUserRatingsTotal());
        data.put("businessStatus", r.getBusinessStatus());
        data.put("types", r.getTypes());
        data.put("lat", r.getLat());
        data.put("lng", r.getLng());
        data.put("elapsedMs", r.getElapsedMs());
        data.put("status", r.getStatus());
        data.put("source", isMock ? "MOCK" : "GOOGLE_MAPS");   // ⭐ I031 fix：与 /geocode 字段对齐
        return Result.success("已查询", data);
    }

    // ============================================================
    //  批量地理编码（仅 ADMIN / HQ 角色可用；用于一次性导入门店）
    // ============================================================

    /**
     * 批量地理编码：admin-only，避免公网滥用耗 Google 配额。
     * 请求：{"addresses":["addr1","addr2",...]}  （最多 50 条 / 单次）
     * 响应：{"total":N, "ok":X, "fail":Y, "results":[{address,lat,lng,placeId,status}]}
     */
    @PostMapping("/geocode/batch")
    public Result<Map<String, Object>> geocodeBatch(@RequestBody Map<String, Object> body) {
        // 1. admin 守卫：仅 SUPER_ADMIN / PLATFORM_ADMIN / HQ_ADMIN 可调
        String role = TenantContextHolder.getRoleCode();
        if (!"SUPER_ADMIN".equals(role) && !"PLATFORM_ADMIN".equals(role) && !"HQ_ADMIN".equals(role)) {
            return Result.error(BizCode.PERMISSION_DENIED, "批量 geocode 仅管理员可用");
        }
        // 2. 入参校验
        Object addrsObj = body.get("addresses");
        if (!(addrsObj instanceof List<?> addrList) || addrList.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "addresses 必填且非空");
        }
        if (addrList.size() > 50) {
            return Result.error(BizCode.BAD_REQUEST, "单次最多 50 条");
        }
        // 3. 取配置（与 /geocode 共享）
        Long tenantId = TenantContextHolder.get().getTenantId();
        GoogleMapsConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<GoogleMapsConfig>()
                .eq(GoogleMapsConfig::getTenantId, tenantId)
                .eq(GoogleMapsConfig::getEnabled, 1)
                .orderByDesc(GoogleMapsConfig::getId)
                .last("LIMIT 1"));

        long t0 = System.currentTimeMillis();
        int ok = 0, fail = 0;
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object a : addrList) {
            String address = a == null ? null : a.toString().trim();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("address", address);
            if (address == null || address.isEmpty()) {
                item.put("status", "INVALID_REQUEST");
                item.put("errorMessage", "address is empty");
                results.add(item);
                fail++;
                continue;
            }
            GoogleMapsGeocodingClient.GeocodeResult r;
            if (cfg != null && cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
                r = geocodingClient.geocode(cfg.getApiKey(), address,
                        cfg.getDefaultLanguage(), cfg.getDefaultRegion());
            } else {
                r = mockGeocode(address);
            }
            // 复用单条日志写入逻辑
            try {
                GoogleMapsGeocodeLog log = new GoogleMapsGeocodeLog();
                log.setTenantId(tenantId);
                log.setAddress(address);
                log.setFormattedAddress(r.getFormattedAddress());
                log.setLat(r.getLat());
                log.setLng(r.getLng());
                log.setPlaceId(r.getPlaceId());
                log.setStatus(r.getStatus());
                log.setErrorMessage(r.getErrorMessage());
                log.setDurationMs(r.getElapsedMs() == null ? null : r.getElapsedMs().intValue());
                logMapper.insert(log);
            } catch (Exception e) {
                log.warn("[GOOGLE-MAPS] batch log write failed: address={} err={}", address, e.getMessage());
            }
            item.put("formattedAddress", r.getFormattedAddress());
            item.put("lat", r.getLat());
            item.put("lng", r.getLng());
            item.put("placeId", r.getPlaceId());
            item.put("status", r.getStatus());
            if (!r.ok()) {
                item.put("errorMessage", r.getErrorMessage());
                fail++;
            } else {
                ok++;
            }
            results.add(item);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", addrList.size());
        resp.put("ok", ok);
        resp.put("fail", fail);
        resp.put("durationMs", System.currentTimeMillis() - t0);
        resp.put("source", cfg != null ? "GOOGLE_MAPS" : "MOCK");
        resp.put("results", results);
        log.info("[GOOGLE-MAPS] batch geocode: total={} ok={} fail={} elapsed={}ms",
                addrList.size(), ok, fail, System.currentTimeMillis() - t0);
        return Result.success("已批量解析", resp);
    }

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

    /**
     * ⭐ I031 fix：placeDetails 的 mock 降级（与 mockGeocode 风格一致）。
     * <p>未配置 apiKey 时调用：返回 N/A 占位字段，不消耗 Google 配额。
     * <p>{@code ok() = (status==OK && placeId!=null)} → 设置 status=OK + placeId 即视为成功。
     */
    private GoogleMapsGeocodingClient.PlaceDetailResult mockPlaceDetails(String placeId) {
        GoogleMapsGeocodingClient.PlaceDetailResult res = new GoogleMapsGeocodingClient.PlaceDetailResult();
        res.setPlaceId(placeId);
        res.setName("N/A (mock)");
        res.setFormattedAddress("N/A (mock)");
        res.setBusinessStatus("N/A");
        res.setStatus("OK");
        res.setElapsedMs(5L);
        return res;
    }

    // ============================================================
    //  门店 / 热力图（保留原 mock）
    // ============================================================

    @GetMapping("/stores")
    public Result<List<Map<String, Object>>> listStores() {
        return Result.success(new ArrayList<>(STORES));
    }

    @GetMapping("/heatmap")
    public Result<Map<String, Object>> heatmap() {
        Random r = new Random();
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Map<String, Object> base = STORES.get(r.nextInt(STORES.size()));
            points.add(Map.of(
                    "lat", ((Number) base.get("lat")).doubleValue() + (r.nextDouble() - 0.5) * 0.5,
                    "lng", ((Number) base.get("lng")).doubleValue() + (r.nextDouble() - 0.5) * 0.5,
                    "weight", r.nextDouble()
            ));
        }
        return Result.success(Map.of("points", points, "total", points.size()));
    }

    /** 编码日志查询（最近 100 条） */
    @GetMapping("/geocode/log")
    public Result<List<GoogleMapsGeocodeLog>> recentGeocodeLog() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(logMapper.selectList(new LambdaQueryWrapper<GoogleMapsGeocodeLog>()
                .eq(GoogleMapsGeocodeLog::getTenantId, tenantId)
                .orderByDesc(GoogleMapsGeocodeLog::getId)
                .last("LIMIT 100")));
    }
}
