package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.ecommerce.EcommerceAdapter;
import com.meiyetec.ecommerce.EcommerceAdapterRouter;
import com.meiyetec.entity.EcommerceOrder;
import com.meiyetec.entity.EcommercePlatform;
import com.meiyetec.entity.EcommerceProduct;
import com.meiyetec.entity.EcommerceSyncLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 电商集成 微店/有赞（FEATURE_COMPARISON_MATRIX.md 280-287 P3-25，EXT-16）
 *
 * <p>实际平台对接由 {@link EcommerceAdapter} 实现：
 * <ul>
 *   <li>YOUZAN：{@code YouzanEcommerceAdapter}（kdtapi v1.0，OAuth2 Client-Credentials）</li>
 *   <li>MOCK：本地样本数据，dev fallback</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/ecommerce")
@RequiredArgsConstructor
public class EcommerceService {

    private final EcommercePlatformMapper platformMapper;
    private final EcommerceProductMapper productMapper;
    private final EcommerceOrderMapper orderMapper;
    private final EcommerceSyncLogMapper syncLogMapper;
    private final EcommerceAdapterRouter adapterRouter;

    @Autowired(required = false)
    private ObjectMapper jackson;

    // I028：platform 枚举校验（与 MockEcommerceAdapter @ConditionalOnMissingBean fallback 对齐）
    // 前端 el-select 包含 MOCK（仅 dev/内部），后端必须接受 — 防止绕过前端直接 POST 写入脏数据
    private static final Set<String> ALLOWED_PLATFORMS = Set.of("WEIDIAN", "YOUZAN", "TAOBAO", "DOUYIN", "MOCK");

    // ============================================================
    //  平台配置 CRUD
    // ============================================================

    @PostMapping("/platform")
    public Result<EcommercePlatform> createPlatform(@RequestBody EcommercePlatform p) {
        // I090：租户隔离 — tenantId 强制从 TenantContextHolder 注入，禁止从入参穿透
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (p.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
        // I028：platform 枚举校验
        if (!ALLOWED_PLATFORMS.contains(p.getPlatform())) {
            return Result.error(BizCode.BAD_REQUEST, "platform 仅支持 " + ALLOWED_PLATFORMS + "，当前：" + p.getPlatform());
        }
        if (p.getStatus() == null) p.setStatus("ACTIVE");
        p.setTenantId(tenantId);  // I090：替换 0L 写死
        p.setId(null);
        platformMapper.insert(p);
        return Result.success("已添加", p);
    }

    @GetMapping("/platform/list")
    public Result<List<EcommercePlatform>> listPlatforms() {
        // I090：租户隔离 — 仅返回当前租户的平台（admin dataScope=ALL 可跨租户查看）
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<EcommercePlatform> qw = new LambdaQueryWrapper<EcommercePlatform>()
                .orderByDesc(EcommercePlatform::getId);
        if (!"ALL".equals(TenantContextHolder.get().getDataScope())) {
            qw.eq(EcommercePlatform::getTenantId, tenantId);
        }
        return Result.success(platformMapper.selectList(qw));
    }

    @PostMapping("/platform/{id}")
    public Result<EcommercePlatform> updatePlatform(@PathVariable Long id, @RequestBody EcommercePlatform p) {
        // I090：租户隔离 — 强制覆盖 tenantId 防止跨租户篡改
        Long tenantId = TenantContextHolder.get().getTenantId();
        EcommercePlatform existing = selectPlatformByTenant(id, tenantId);
        if (existing == null) return Result.error(BizCode.NOT_FOUND, "平台不存在");
        p.setId(id);
        p.setTenantId(tenantId);  // I090：覆盖入参中可能存在的脏 tenantId
        platformMapper.updateById(p);
        return Result.success("已更新", platformMapper.selectById(id));
    }

    /** 健康检查（走 adapter 实际链路） */
    @GetMapping("/platform/{id}/health")
    public Result<EcommerceAdapter.HealthStatus> platformHealth(@PathVariable Long id) {
        // I090：租户隔离 — 跨租户拒绝 health check（adapter.healthCheck 可能泄露凭据）
        Long tenantId = TenantContextHolder.get().getTenantId();
        EcommercePlatform pf = selectPlatformByTenant(id, tenantId);
        if (pf == null) return Result.error(BizCode.NOT_FOUND, "平台不存在");
        EcommerceAdapter adapter = adapterRouter.getAdapter(pf.getPlatform());
        return Result.success(adapter.healthCheck(pf));
    }

    // ============================================================
    //  商品列表
    // ============================================================

    @GetMapping("/product/list")
    public Result<List<EcommerceProduct>> listProducts(@RequestParam(required = false) Long platformId) {
        // I090：租户隔离 — 仅返回当前租户平台下的商品
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<EcommerceProduct> qw = new LambdaQueryWrapper<EcommerceProduct>()
                .orderByDesc(EcommerceProduct::getId);
        if (!"ALL".equals(TenantContextHolder.get().getDataScope())) {
            qw.eq(EcommerceProduct::getTenantId, tenantId);
        }
        if (platformId != null) {
            EcommercePlatform pf = selectPlatformByTenant(platformId, tenantId);
            if (pf != null) qw.eq(EcommerceProduct::getPlatform, pf.getPlatform());
        }
        return Result.success(productMapper.selectList(qw));
    }

    /**
     * 同步商品：调 adapter 拉取 → 写 ecommerce_product → 写 ecommerce_sync_log
     */
    @PostMapping("/product/sync/{platformId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<EcommerceSyncLog> syncProducts(@PathVariable Long platformId,
                                                 @RequestParam(required = false) Integer pageSize) {
        long start = System.currentTimeMillis();
        // I090：租户隔离 — 跨租户拒绝同步
        Long tenantId = TenantContextHolder.get().getTenantId();
        EcommercePlatform pf = selectPlatformByTenant(platformId, tenantId);
        if (pf == null) return Result.error(BizCode.NOT_FOUND, "平台不存在");
        int size = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 100);

        EcommerceSyncLog syncLog = new EcommerceSyncLog();
        syncLog.setTenantId(tenantId);  // I090：替换 0L 写死
        syncLog.setPlatform(pf.getPlatform());
        syncLog.setSyncType("PRODUCT");
        syncLog.setStatus("RUNNING");
        syncLogMapper.insert(syncLog);

        try {
            EcommerceAdapter adapter = adapterRouter.getAdapter(pf.getPlatform());
            EcommerceAdapter.ProductPage page = adapter.fetchProducts(pf, 1, size);
            int ok = 0, fail = 0;
            for (Map<String, Object> raw : page.items) {
                try {
                    upsertProduct(pf.getPlatform(), raw);
                    ok++;
                } catch (Exception ex) {
                    log.warn("[ECOMMERCE] upsert product failed: {}", ex.getMessage());
                    fail++;
                }
            }
            // token 缓存回写
            persistTokenCache(pf);

            pf.setLastSyncAt(LocalDateTime.now());
            pf.setErrorMessage(null);
            platformMapper.updateById(pf);

            syncLog.setSuccessCount(ok);
            syncLog.setFailCount(fail);
            syncLog.setDurationMs((int) (System.currentTimeMillis() - start));
            syncLog.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            syncLogMapper.updateById(syncLog);
            log.info("[ECOMMERCE] product sync done: platform={} ok={} fail={} total={}",
                    pf.getPlatform(), ok, fail, page.totalCount);
            return Result.success("已同步", syncLog);
        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setDurationMs((int) (System.currentTimeMillis() - start));
            syncLogMapper.updateById(syncLog);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败：" + e.getMessage());
        }
    }

    private void upsertProduct(String platform, Map<String, Object> raw) {
        if (jackson == null) return;
        EcommerceProduct p = jackson.convertValue(raw, EcommerceProduct.class);
        p.setPlatform(platform);
        // 从 raw 中提取关键字段（convertValue 可能因下划线字段映射失败）
        Object externalId = raw.get("item_id") != null ? raw.get("item_id") : raw.get("id");
        if (externalId != null) p.setExternalProductId(String.valueOf(externalId));
        Object price = raw.get("price");
        if (price != null) {
            try { p.setPrice(new BigDecimal(price.toString())); } catch (Exception ignored) {}
        }
        Object stock = raw.get("quantity") != null ? raw.get("quantity") : raw.get("stock");
        if (stock != null) {
            try { p.setStock(((Number) stock).intValue()); } catch (Exception ignored) {}
        }
        Object title = raw.get("title") != null ? raw.get("title") : raw.get("name");
        if (title != null) p.setProductName(title.toString());

        // upsert by (platform, external_product_id)
        EcommerceProduct existing = productMapper.selectOne(new LambdaQueryWrapper<EcommerceProduct>()
                .eq(EcommerceProduct::getPlatform, platform)
                .eq(EcommerceProduct::getExternalProductId, p.getExternalProductId()));
        p.setLastSyncAt(LocalDateTime.now());
        if (existing == null) {
            p.setId(null);
            productMapper.insert(p);
        } else {
            p.setId(existing.getId());
            productMapper.updateById(p);
        }
    }

    // ============================================================
    //  订单列表
    // ============================================================

    @GetMapping("/order/list")
    public Result<List<EcommerceOrder>> listOrders(@RequestParam(required = false) Long platformId,
                                                  @RequestParam(required = false) String status) {
        // I090：租户隔离 — 仅返回当前租户订单
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<EcommerceOrder> qw = new LambdaQueryWrapper<EcommerceOrder>()
                .orderByDesc(EcommerceOrder::getId);
        if (!"ALL".equals(TenantContextHolder.get().getDataScope())) {
            qw.eq(EcommerceOrder::getTenantId, tenantId);
        }
        if (platformId != null) {
            EcommercePlatform pf = selectPlatformByTenant(platformId, tenantId);
            if (pf != null) qw.eq(EcommerceOrder::getPlatform, pf.getPlatform());
        }
        if (status != null && !status.isEmpty()) qw.eq(EcommerceOrder::getStatus, status);
        return Result.success(orderMapper.selectList(qw));
    }

    /**
     * 同步订单：调 adapter 拉取（默认最近 24 小时）→ 写 ecommerce_order → 写 sync log
     */
    @PostMapping("/order/sync/{platformId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<EcommerceSyncLog> syncOrders(@PathVariable Long platformId,
                                               @RequestParam(required = false) String startTime,
                                               @RequestParam(required = false) String endTime,
                                               @RequestParam(required = false) Integer pageSize) {
        long t0 = System.currentTimeMillis();
        // I090：租户隔离 — 跨租户拒绝同步
        Long tenantId = TenantContextHolder.get().getTenantId();
        EcommercePlatform pf = selectPlatformByTenant(platformId, tenantId);
        if (pf == null) return Result.error(BizCode.NOT_FOUND, "平台不存在");
        int size = pageSize == null || pageSize <= 0 ? 50 : Math.min(pageSize, 100);
        String startStr = startTime == null || startTime.isEmpty()
                ? LocalDateTime.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : startTime;
        String endStr = endTime == null || endTime.isEmpty()
                ? LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : endTime;

        EcommerceSyncLog syncLog = new EcommerceSyncLog();
        syncLog.setTenantId(tenantId);  // I090：替换 0L 写死
        syncLog.setPlatform(pf.getPlatform());
        syncLog.setSyncType("ORDER");
        syncLog.setStatus("RUNNING");
        syncLogMapper.insert(syncLog);

        try {
            EcommerceAdapter adapter = adapterRouter.getAdapter(pf.getPlatform());
            EcommerceAdapter.OrderPage page = adapter.fetchOrders(pf, startStr, endStr, 1, size);
            int ok = 0, fail = 0;
            for (Map<String, Object> raw : page.orders) {
                try {
                    upsertOrder(pf.getPlatform(), raw);
                    ok++;
                } catch (Exception ex) {
                    log.warn("[ECOMMERCE] upsert order failed: {}", ex.getMessage());
                    fail++;
                }
            }
            persistTokenCache(pf);

            pf.setLastSyncAt(LocalDateTime.now());
            pf.setErrorMessage(null);
            platformMapper.updateById(pf);

            syncLog.setSuccessCount(ok);
            syncLog.setFailCount(fail);
            syncLog.setDurationMs((int) (System.currentTimeMillis() - t0));
            syncLog.setStatus(fail == 0 ? "SUCCESS" : "PARTIAL");
            syncLogMapper.updateById(syncLog);
            log.info("[ECOMMERCE] order sync done: platform={} ok={} fail={} total={} window=[{}..{}]",
                    pf.getPlatform(), ok, fail, page.totalCount, startStr, endStr);
            return Result.success("已同步", syncLog);
        } catch (Exception e) {
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setDurationMs((int) (System.currentTimeMillis() - t0));
            syncLogMapper.updateById(syncLog);
            return Result.error(BizCode.SYSTEM_ERROR, "同步失败：" + e.getMessage());
        }
    }

    private void upsertOrder(String platform, Map<String, Object> raw) {
        EcommerceOrder o = new EcommerceOrder();
        o.setPlatform(platform);

        // ---- 主键 / 外部订单号 ----
        Object tid = raw.get("tid") != null ? raw.get("tid") : raw.get("order_id");
        String externalOrderId = tid == null ? null : String.valueOf(tid);
        o.setExternalOrderId(externalOrderId);
        // orderNo 优先取 tid，fallback 到 external_order_id
        o.setOrderNo(externalOrderId);

        // ---- 状态 ----
        o.setStatus(raw.get("status") == null ? null : raw.get("status").toString());

        // ---- 金额 ----
        // Youzan: total_amount 含运费；post_fee 是运费；amount = total - post
        BigDecimal total = parseDecimal(raw.get("total_amount"));
        BigDecimal shippingFee = parseDecimal(raw.get("post_fee"));
        o.setTotalAmount(total);
        o.setShippingFee(shippingFee);
        if (total != null) {
            o.setAmount(shippingFee == null ? total : total.subtract(shippingFee));
        }

        // ---- 买家信息（Youzan receiver_* 系列，兼容 weidian buyer_* 系列） ----
        o.setBuyerName(firstNonNull(raw,
                "receiver_name", "buyer_name", "user_name"));
        o.setBuyerPhone(firstNonNull(raw,
                "receiver_phone", "receiver_mobile", "buyer_phone", "mobile", "phone"));

        // ---- 地址（receiver_state + receiver_city + receiver_district + receiver_address） ----
        String address = firstNonNull(raw, "receiver_address", "buyer_address", "address");
        if (address == null) {
            String state  = strOrNull(raw.get("receiver_state"));
            String city   = strOrNull(raw.get("receiver_city"));
            String district = strOrNull(raw.get("receiver_district"));
            String detail = strOrNull(raw.get("receiver_address"));
            StringBuilder sb = new StringBuilder();
            if (state != null)   sb.append(state);
            if (city != null)    { if (sb.length() > 0) sb.append(' '); sb.append(city); }
            if (district != null){ if (sb.length() > 0) sb.append(' '); sb.append(district); }
            if (detail != null)  { if (sb.length() > 0) sb.append(' '); sb.append(detail); }
            address = sb.length() == 0 ? null : sb.toString();
        }
        o.setAddress(address);

        // ---- 物流 ----
        o.setTrackingNo(firstNonNull(raw, "express_no", "tracking_no"));
        o.setShippingStatus(firstNonNull(raw, "shipping_status", "express_company_name"));

        // ---- 商品明细（Youzan: orders[] / weidian: items[]） ----
        Object itemsObj = raw.get("orders") != null ? raw.get("orders") : raw.get("items");
        if (itemsObj instanceof List<?> itemsList && !itemsList.isEmpty() && jackson != null) {
            try {
                // 精简字段：title / num / price / sku_properties_name
                List<Map<String, Object>> slim = new ArrayList<>();
                for (Object it : itemsList) {
                    if (it instanceof Map<?, ?> m) {
                        Map<String, Object> one = new LinkedHashMap<>();
                        putIfPresent(one, m, "title", "title");
                        putIfPresent(one, m, "num", "num", "quantity");
                        putIfPresent(one, m, "price", "price");
                        putIfPresent(one, m, "sku_properties_name", "sku_properties_name", "sku");
                        putIfPresent(one, m, "outer_item_id", "outer_item_id");
                        putIfPresent(one, m, "item_id", "item_id");
                        slim.add(one);
                    }
                }
                o.setItemsJson(jackson.writeValueAsString(slim));
            } catch (Exception ex) {
                log.warn("[ECOMMERCE] items serialize failed: {}", ex.getMessage());
            }
        }

        // ---- 订单时间（Youzan: created；fallback update_time） ----
        Object created = raw.get("created") != null ? raw.get("created") : raw.get("update_time");
        if (created != null) {
            String ts = created.toString();
            // Youzan timestamp 是秒级（10 位） 或 毫秒级（13 位）
            try {
                long epoch;
                if (ts.matches("\\d{10}")) {
                    epoch = Long.parseLong(ts) * 1000L;
                } else if (ts.matches("\\d{13}")) {
                    epoch = Long.parseLong(ts);
                } else {
                    epoch = 0;
                }
                if (epoch > 0) {
                    o.setOrderTime(java.time.Instant.ofEpochMilli(epoch)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                } else {
                    o.setOrderTime(parseDateTime(ts));
                }
            } catch (Exception ignored) {}
        }

        // ---- 原始 payload ----
        if (jackson != null) {
            try { o.setRawPayload(jackson.writeValueAsString(raw)); } catch (Exception ignored) {}
        }

        // ---- upsert by (platform, external_order_id) ----
        EcommerceOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<EcommerceOrder>()
                .eq(EcommerceOrder::getPlatform, platform)
                .eq(EcommerceOrder::getExternalOrderId, o.getExternalOrderId()));
        if (existing == null) {
            o.setId(null);
            orderMapper.insert(o);
        } else {
            o.setId(existing.getId());
            orderMapper.updateById(o);
        }
    }

    /** 从 raw 中按多个候选 key 取第一个非空字符串 */
    private static String firstNonNull(Map<String, Object> raw, String... keys) {
        for (String k : keys) {
            Object v = raw.get(k);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return null;
    }

    /** Decimal 解析（容错：null / 非数字 → null） */
    private static BigDecimal parseDecimal(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    /** 日期时间解析（兼容 "yyyy-MM-dd HH:mm:ss" / ISO-8601） */
    private static java.time.LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return java.time.LocalDateTime.parse(s,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {}
        try {
            return java.time.OffsetDateTime.parse(s)
                    .atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {}
        return null;
    }

    /** 从 m 拷贝指定 key 到 target（key 不存在则跳过） */
    @SuppressWarnings("unchecked")
    private static void putIfPresent(Map<String, Object> target, Map<?, ?> m, String targetKey, String... sourceKeys) {
        for (String sk : sourceKeys) {
            Object v = m.get(sk);
            if (v != null) {
                target.put(targetKey, v);
                return;
            }
        }
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private void persistTokenCache(EcommercePlatform pf) {
        if (pf.getId() == null) return;
        // 仅写回 token 相关字段，避免覆盖其他用户修改
        EcommercePlatform update = new EcommercePlatform();
        update.setId(pf.getId());
        update.setAccessToken(pf.getAccessToken());
        update.setTokenExpireAt(pf.getTokenExpireAt());
        platformMapper.updateById(update);
    }

    // ============================================================
    //  数据看板
    // ============================================================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            // I090：租户隔离 — 仅统计当前租户数据；admin dataScope=ALL 可看全量
            Long tenantId = TenantContextHolder.get().getTenantId();
            LambdaQueryWrapper<EcommercePlatform> pfQw = new LambdaQueryWrapper<EcommercePlatform>();
            LambdaQueryWrapper<EcommerceProduct> prodQw = new LambdaQueryWrapper<EcommerceProduct>();
            LambdaQueryWrapper<EcommerceOrder> ordQw = new LambdaQueryWrapper<EcommerceOrder>();
            if (!"ALL".equals(TenantContextHolder.get().getDataScope())) {
                pfQw.eq(EcommercePlatform::getTenantId, tenantId);
                prodQw.eq(EcommerceProduct::getTenantId, tenantId);
                ordQw.eq(EcommerceOrder::getTenantId, tenantId);
            }
            data.put("totalPlatforms", platformMapper.selectCount(pfQw));
            data.put("totalProducts", productMapper.selectCount(prodQw));
            data.put("totalOrders", orderMapper.selectCount(ordQw));
            data.put("supportedPlatforms", adapterRouter.listPlatforms());
        } catch (Exception ignore) {}
        return Result.success(data);
    }

    // ============================================================
    //  I090：租户隔离 helper
    // ============================================================

    /**
     * I090：跨租户 selectById。tenantId 不匹配时抛 {@link BizCode} (403)；
     * admin {@code dataScope=ALL} 仍可跨租户读（与 CrmIntegrationService.syncNow 模式一致）。
     *
     * @return 平台对象；id 不存在返回 null；跨租户抛 BusinessException
     */
    private EcommercePlatform selectPlatformByTenant(Long id, Long tenantId) {
        EcommercePlatform pf = platformMapper.selectById(id);
        if (pf == null) return null;
        if (!"ALL".equals(TenantContextHolder.get().getDataScope())
                && pf.getTenantId() != null && !pf.getTenantId().equals(tenantId)) {
            throw new BusinessException(BizCode.TENANT_DENIED, "跨租户访问被拒绝（platform id=" + id + "）");
        }
        return pf;
    }
}

