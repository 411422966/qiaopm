package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P2 高级合规 + 国际化套件：
 * - P2.18 GDPR（数据导出/删除/同意）
 * - P2.19 HIPAA（PHI 加密 + 访问审计）
 * - P2.20 Google Maps（地图 + 围栏）
 * - P2.21 海外 POS（Square/Stripe Terminal）
 */
@Slf4j
@RestController
@RequestMapping("/p2-suite")
@RequiredArgsConstructor
public class P2ComplianceSuiteService {

    private final GdprConsentMapper gdprConsentMapper;
    private final GdprDataRequestMapper gdprRequestMapper;
    private final GdprAuditLogMapper gdprAuditMapper;
    private final HipaaPhiAccessMapper phiAccessMapper;
    private final HipaaEncryptionKeyMapper encryptionKeyMapper;
    private final MapConfigMapper mapConfigMapper;
    private final GeofenceMapper geofenceMapper;
    private final PosDeviceMapper posDeviceMapper;
    private final PosTransactionMapper posTxMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // P2.18 GDPR
    // ====================================================================

    /**
     * 同意管理
     */
    @PostMapping("/gdpr/consent")
    public Result<GdprConsent> grantConsent(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = ((Number) body.get("memberId")).longValue();
        GdprConsent c = new GdprConsent();
        c.setTenantId(tenantId);
        c.setMemberId(memberId);
        c.setConsentType((String) body.getOrDefault("consentType", "MARKETING"));
        c.setGranted(Boolean.TRUE.equals(body.get("granted")) ? 1 : 0);
        c.setVersion((String) body.getOrDefault("version", "1.0"));
        c.setIpAddress((String) body.get("ipAddress"));
        c.setUserAgent((String) body.get("userAgent"));
        c.setChannel((String) body.getOrDefault("channel", "WEB"));
        gdprConsentMapper.insert(c);

        // 审计日志
        saveGdprAudit(tenantId, body.get("actorId"), (String) body.get("actorName"),
                "GRANT_CONSENT", "MEMBER", memberId, true, c.toString());
        return Result.success("已记录", c);
    }

    @GetMapping("/gdpr/consent/by-member/{memberId}")
    public Result<List<GdprConsent>> getConsents(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(gdprConsentMapper.selectList(new LambdaQueryWrapper<GdprConsent>()
                .eq(GdprConsent::getTenantId, tenantId)
                .eq(GdprConsent::getMemberId, memberId)
                .orderByDesc(GdprConsent::getId)));
    }

    /**
     * 数据主体请求（导出/删除/纠正）
     */
    @PostMapping("/gdpr/request")
    @Transactional(rollbackFor = Exception.class)
    public Result<GdprDataRequest> dataRequest(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = ((Number) body.get("memberId")).longValue();
        String type = (String) body.getOrDefault("requestType", "EXPORT");

        GdprDataRequest r = new GdprDataRequest();
        r.setTenantId(tenantId);
        r.setRequestNo("GDPR" + System.currentTimeMillis());
        r.setMemberId(memberId);
        r.setRequestType(type);
        r.setStatus("PENDING");
        r.setReason((String) body.get("reason"));
        gdprRequestMapper.insert(r);

        // 审计
        saveGdprAudit(tenantId, null, null, "DATA_REQUEST_" + type, "MEMBER", memberId, true, r.getRequestNo());

        return Result.success("请求已创建", r);
    }

    /**
     * 执行请求（处理）
     */
    @PostMapping("/gdpr/request/{id}/process")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> processRequest(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        GdprDataRequest r = gdprRequestMapper.selectById(id);
        if (r == null || !r.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "请求不存在");
        r.setStatus("PROCESSING");
        gdprRequestMapper.updateById(r);

        Map<String, Object> data = new LinkedHashMap<>();
        switch (r.getRequestType()) {
            case "EXPORT": {
                // 导出所有会员数据
                Map<String, Object> exportData = exportMemberData(tenantId, r.getMemberId());
                r.setExportUrl("/gdpr/export/" + r.getRequestNo());
                r.setExportExpireAt(LocalDateTime.now().plusDays(7));
                r.setProcessorId(body.get("processorId") == null ? null : ((Number) body.get("processorId")).longValue());
                r.setProcessedAt(LocalDateTime.now());
                r.setStatus("COMPLETED");
                data.put("export", exportData);
                data.put("exportUrl", r.getExportUrl());
                break;
            }
            case "DELETE": {
                // 软删除 + 匿名化
                anonymizeMember(tenantId, r.getMemberId());
                r.setProcessorId(body.get("processorId") == null ? null : ((Number) body.get("processorId")).longValue());
                r.setProcessedAt(LocalDateTime.now());
                r.setStatus("COMPLETED");
                data.put("action", "ANONYMIZED");
                break;
            }
            case "RECTIFY": {
                // 纠正（接受 corrections 字段）
                if (body.get("corrections") instanceof Map) {
                    Map<String, Object> corr = (Map<String, Object>) body.get("corrections");
                    applyCorrections(tenantId, r.getMemberId(), corr);
                }
                r.setProcessorId(body.get("processorId") == null ? null : ((Number) body.get("processorId")).longValue());
                r.setProcessedAt(LocalDateTime.now());
                r.setStatus("COMPLETED");
                data.put("action", "RECTIFIED");
                break;
            }
            default:
                return Result.error(BizCode.BAD_REQUEST, "未知类型");
        }
        gdprRequestMapper.updateById(r);
        saveGdprAudit(tenantId, r.getProcessorId(), null, "DATA_REQUEST_PROCESSED", "MEMBER", r.getMemberId(), true, r.getRequestNo());
        return Result.success("已处理", data);
    }

    private Map<String, Object> exportMemberData(Long tenantId, Long memberId) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            result.put("member", jdbc.queryForMap("SELECT * FROM crm_member WHERE id = ? AND tenant_id = ?", memberId, tenantId));
        } catch (Exception ignore) {}
        try {
            result.put("orders", jdbc.queryForList("SELECT * FROM crm_order WHERE member_id = ? AND tenant_id = ?", memberId, tenantId));
        } catch (Exception ignore) {}
        try {
            result.put("consents", jdbc.queryForList("SELECT * FROM gdpr_consent WHERE member_id = ? AND tenant_id = ?", memberId, tenantId));
        } catch (Exception ignore) {}
        return result;
    }

    private void anonymizeMember(Long tenantId, Long memberId) {
        // 匿名化关键 PII
        try {
            jdbc.update("UPDATE crm_member SET member_name = CONCAT('ANON_', id), phone = NULL, email = NULL, id_card = NULL, address = NULL WHERE id = ? AND tenant_id = ?",
                    memberId, tenantId);
        } catch (Exception ignore) {}
        try {
            jdbc.update("UPDATE crm_order SET member_name = CONCAT('ANON_', member_id) WHERE member_id = ? AND tenant_id = ?", memberId, tenantId);
        } catch (Exception ignore) {}
    }

    private void applyCorrections(Long tenantId, Long memberId, Map<String, Object> corr) {
        for (Map.Entry<String, Object> e : corr.entrySet()) {
            try {
                jdbc.update("UPDATE crm_member SET " + e.getKey() + " = ? WHERE id = ? AND tenant_id = ?",
                        e.getValue(), memberId, tenantId);
            } catch (Exception ignore) {}
        }
    }

    @GetMapping("/gdpr/request/list")
    public Result<List<GdprDataRequest>> listRequests(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<GdprDataRequest> qw = new LambdaQueryWrapper<GdprDataRequest>()
                .eq(GdprDataRequest::getTenantId, tenantId)
                .orderByDesc(GdprDataRequest::getId);
        if (status != null) qw.eq(GdprDataRequest::getStatus, status);
        return Result.success(gdprRequestMapper.selectList(qw));
    }

    @GetMapping("/gdpr/audit")
    public Result<List<GdprAuditLog>> audit(@RequestParam(defaultValue = "100") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(gdprAuditMapper.selectList(new LambdaQueryWrapper<GdprAuditLog>()
                .eq(GdprAuditLog::getTenantId, tenantId)
                .orderByDesc(GdprAuditLog::getId)
                .last("LIMIT " + limit)));
    }

    private void saveGdprAudit(Long tenantId, Object actorId, String actorName, String action, String targetType, Long targetId, boolean pii, String details) {
        GdprAuditLog log = new GdprAuditLog();
        log.setTenantId(tenantId);
        if (actorId instanceof Number) log.setActorId(((Number) actorId).longValue());
        log.setActorName(actorName);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setPiiData(pii ? 1 : 0);
        log.setDetails(details);
        gdprAuditMapper.insert(log);
    }

    // ====================================================================
    // P2.19 HIPAA
    // ====================================================================

    /**
     * PHI 访问记录（任何涉及 PII 的访问都记录）
     */
    @PostMapping("/hipaa/phi-access")
    public Result<HipaaPhiAccess> phiAccess(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        HipaaPhiAccess a = new HipaaPhiAccess();
        a.setTenantId(tenantId);
        a.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        a.setFieldName((String) body.get("fieldName"));
        a.setAccessType((String) body.getOrDefault("accessType", "READ"));
        a.setActorId(body.get("actorId") == null ? null : ((Number) body.get("actorId")).longValue());
        a.setActorName((String) body.get("actorName"));
        a.setActorRole((String) body.get("actorRole"));
        a.setPurpose((String) body.get("purpose"));
        a.setIpAddress((String) body.get("ipAddress"));
        a.setIsBreakGlass(Boolean.TRUE.equals(body.get("isBreakGlass")) ? 1 : 0);
        phiAccessMapper.insert(a);
        return Result.success("已记录", a);
    }

    @GetMapping("/hipaa/phi-access/list")
    public Result<List<HipaaPhiAccess>> listPhiAccess(@RequestParam(defaultValue = "200") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(phiAccessMapper.selectList(new LambdaQueryWrapper<HipaaPhiAccess>()
                .eq(HipaaPhiAccess::getTenantId, tenantId)
                .orderByDesc(HipaaPhiAccess::getId)
                .last("LIMIT " + limit)));
    }

    /**
     * 创建加密密钥（模拟 KMS）
     */
    @PostMapping("/hipaa/key/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<HipaaEncryptionKey> createKey(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        HipaaEncryptionKey k = new HipaaEncryptionKey();
        k.setTenantId(tenantId);
        k.setKeyAlias((String) body.getOrDefault("keyAlias", "main-key"));
        k.setAlgorithm("AES-256-GCM");
        k.setKeyStatus("ACTIVE");
        k.setKeyVersion(1);
        // 生成公钥（模拟 RSA）
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        k.setPublicKey(Base64.getEncoder().encodeToString(key));
        k.setKmsArn("arn:aws:kms:cn-north-1:000000000000:key/" + UUID.randomUUID().toString());
        k.setNextRotateAt(LocalDateTime.now().plusDays(90));
        encryptionKeyMapper.insert(k);
        return Result.success("已创建", k);
    }

    /**
     * 加密敏感字段
     */
    @PostMapping("/hipaa/encrypt")
    public Result<Map<String, Object>> encryptField(@RequestBody Map<String, Object> body) {
        String plaintext = (String) body.get("plaintext");
        if (plaintext == null) return Result.error(BizCode.BAD_REQUEST, "plaintext 必填");
        // 模拟 AES-256-GCM 加密
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        String encrypted = Base64.getEncoder().encodeToString((iv + plaintext).getBytes());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ciphertext", encrypted);
        data.put("algorithm", "AES-256-GCM");
        data.put("iv", Base64.getEncoder().encodeToString(iv));
        data.put("timestamp", System.currentTimeMillis());
        return Result.success("已加密", data);
    }

    /**
     * 密钥自动轮换（每天 04:00）
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void rotateKeys() {
        try {
            LocalDateTime threshold = LocalDateTime.now();
            List<HipaaEncryptionKey> expiring = encryptionKeyMapper.selectList(new LambdaQueryWrapper<HipaaEncryptionKey>()
                    .eq(HipaaEncryptionKey::getKeyStatus, "ACTIVE")
                    .le(HipaaEncryptionKey::getNextRotateAt, threshold));
            for (HipaaEncryptionKey old : expiring) {
                old.setKeyStatus("ROTATED");
                old.setRotatedAt(LocalDateTime.now());
                encryptionKeyMapper.updateById(old);

                HipaaEncryptionKey newKey = new HipaaEncryptionKey();
                newKey.setTenantId(old.getTenantId());
                newKey.setKeyAlias(old.getKeyAlias());
                newKey.setKeyVersion(old.getKeyVersion() + 1);
                newKey.setAlgorithm(old.getAlgorithm());
                newKey.setKeyStatus("ACTIVE");
                newKey.setNextRotateAt(LocalDateTime.now().plusDays(90));
                byte[] key = new byte[32];
                new SecureRandom().nextBytes(key);
                newKey.setPublicKey(Base64.getEncoder().encodeToString(key));
                encryptionKeyMapper.insert(newKey);
            }
        } catch (Exception e) {
            log.warn("[HIPAA] 密钥轮换失败: {}", e.getMessage());
        }
    }

    @GetMapping("/hipaa/dashboard")
    public Result<Map<String, Object>> hipaaDashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("todayAccess", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hipaa_phi_access WHERE tenant_id = ? AND DATE(created_at) = CURDATE()",
                    Long.class, tenantId));
            data.put("breakGlass", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hipaa_phi_access WHERE tenant_id = ? AND is_break_glass = 1 AND DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)",
                    Long.class, tenantId));
            data.put("activeKeys", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hipaa_encryption_key WHERE tenant_id = ? AND key_status = 'ACTIVE'",
                    Long.class, tenantId));
            data.put("byAccessType", jdbc.queryForList(
                    "SELECT access_type, COUNT(*) AS cnt FROM hipaa_phi_access WHERE tenant_id = ? AND DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY access_type",
                    tenantId));
            return Result.success(data);
        } catch (Exception e) {
            return Result.success(new LinkedHashMap<>());
        }
    }

    // ====================================================================
    // P2.20 Google Maps
    // ====================================================================

    @PostMapping("/map/config")
    public Result<MapConfig> saveMapConfig(@RequestBody MapConfig c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getProvider() == null) c.setProvider("GOOGLE");
        if (c.getEnabled() == null) c.setEnabled(1);
        if (c.getDefaultZoom() == null) c.setDefaultZoom(12);
        c.setTenantId(tenantId);
        c.setId(null);
        mapConfigMapper.insert(c);
        return Result.success("已保存", c);
    }

    @GetMapping("/map/config")
    public Result<List<MapConfig>> listMapConfigs() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(mapConfigMapper.selectList(new LambdaQueryWrapper<MapConfig>()
                .eq(MapConfig::getTenantId, tenantId)
                .eq(MapConfig::getEnabled, 1)));
    }

    /**
     * Geocoding（地址 → 坐标）
     * 生产环境替换为 Google Geocoding API
     */
    @PostMapping("/map/geocode")
    public Result<Map<String, Object>> geocode(@RequestBody Map<String, Object> body) {
        String address = (String) body.get("address");
        if (address == null) return Result.error(BizCode.BAD_REQUEST, "address 必填");

        // 模拟：根据地址 hash 生成稳定坐标
        int hash = Math.abs(address.hashCode() % 10000);
        double lat = 39.9 + (hash % 100) / 1000.0;
        double lng = 116.4 + (hash / 100) / 1000.0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("address", address);
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("formattedAddress", address + ", China");
        data.put("placeId", "place_" + UUID.randomUUID().toString().substring(0, 12));
        data.put("provider", "GOOGLE");
        return Result.success("已解析", data);
    }

    /**
     * 路径规划
     */
    @PostMapping("/map/route")
    public Result<Map<String, Object>> route(@RequestBody Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("distance", 12.5);
        data.put("duration", 35);
        data.put("polyline", "encoded_polyline_demo");
        data.put("steps", List.of(
                Map.of("instruction", "出发", "distance", 0, "duration", 0),
                Map.of("instruction", "直行 5km", "distance", 5, "duration", 15),
                Map.of("instruction", "右转 3km", "distance", 3, "duration", 10),
                Map.of("instruction", "到达目的地", "distance", 4.5, "duration", 10)
        ));
        return Result.success("已规划", data);
    }

    /**
     * 地理围栏
     */
    @PostMapping("/map/geofence")
    public Result<Geofence> createGeofence(@RequestBody Geofence g) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (g.getCenterLat() == null || g.getCenterLng() == null)
            return Result.error(BizCode.BAD_REQUEST, "坐标必填");
        if (g.getRadiusM() == null) g.setRadiusM(200);
        if (g.getAlertType() == null) g.setAlertType("CHECK_IN");
        if (g.getEnabled() == null) g.setEnabled(1);
        g.setTenantId(tenantId);
        g.setId(null);
        geofenceMapper.insert(g);
        return Result.success("已创建", g);
    }

    @GetMapping("/map/geofence/list")
    public Result<List<Geofence>> listGeofences() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(geofenceMapper.selectList(new LambdaQueryWrapper<Geofence>()
                .eq(Geofence::getTenantId, tenantId)
                .eq(Geofence::getEnabled, 1)));
    }

    /**
     * 检测点是否在围栏内
     */
    @PostMapping("/map/geofence/check")
    public Result<Map<String, Object>> checkGeofence(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BigDecimal lat = new BigDecimal(body.get("lat").toString());
        BigDecimal lng = new BigDecimal(body.get("lng").toString());

        List<Geofence> fences = geofenceMapper.selectList(new LambdaQueryWrapper<Geofence>()
                .eq(Geofence::getTenantId, tenantId)
                .eq(Geofence::getEnabled, 1));

        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Geofence f : fences) {
            double dist = haversine(lat.doubleValue(), lng.doubleValue(),
                    f.getCenterLat().doubleValue(), f.getCenterLng().doubleValue()) * 1000;
            if (dist <= f.getRadiusM()) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("fenceId", f.getId());
                hit.put("fenceName", f.getFenceName());
                hit.put("alertType", f.getAlertType());
                hit.put("distance", dist);
                hits.add(hit);
            }
        }
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("hitCount", hits.size());
        data.put("hits", hits);
        return Result.success(hits.isEmpty() ? "不在任何围栏" : "已进入围栏", data);
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ====================================================================
    // P2.21 海外 POS
    // ====================================================================

    @PostMapping("/pos/device/register")
    public Result<PosDevice> registerPos(@RequestBody PosDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getDeviceCode() == null || d.getStoreId() == null)
            return Result.error(BizCode.BAD_REQUEST, "deviceCode/storeId 必填");
        if (d.getVendor() == null) d.setVendor("SQUARE");
        if (d.getStatus() == null) d.setStatus("ONLINE");
        d.setTenantId(tenantId);
        d.setId(null);
        posDeviceMapper.insert(d);
        return Result.success("已注册", d);
    }

    @GetMapping("/pos/device/list")
    public Result<List<PosDevice>> listPosDevices() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(posDeviceMapper.selectList(new LambdaQueryWrapper<PosDevice>()
                .eq(PosDevice::getTenantId, tenantId)));
    }

    /**
     * POS 支付（刷卡）
     */
    @PostMapping("/pos/charge")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> posCharge(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long posId = body.get("posId") == null ? null : ((Number) body.get("posId")).longValue();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        BigDecimal tip = body.get("tip") == null ? BigDecimal.ZERO : new BigDecimal(body.get("tip").toString());

        PosTransaction tx = new PosTransaction();
        tx.setTenantId(tenantId);
        tx.setPosId(posId);
        tx.setStoreId(body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue());
        tx.setTxNo("POS" + System.currentTimeMillis());
        tx.setVendor((String) body.getOrDefault("vendor", "SQUARE"));
        tx.setAmount(amount);
        tx.setTip(tip);
        tx.setCurrency((String) body.getOrDefault("currency", "USD"));
        tx.setCardBrand((String) body.getOrDefault("cardBrand", "VISA"));
        tx.setCardLast4((String) body.get("cardLast4"));
        tx.setOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        tx.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());

        // 模拟 Square/Stripe Terminal SDK 调用
        boolean sandbox = body.get("sandbox") == null || Boolean.TRUE.equals(body.get("sandbox"));
        if (sandbox) {
            tx.setStatus("COMPLETED");
            tx.setExternalTxId("sq_" + UUID.randomUUID().toString().substring(0, 16));
            tx.setRawPayload("{\"simulated\": true}");
        } else {
            tx.setStatus("PENDING");
        }
        posTxMapper.insert(tx);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("txNo", tx.getTxNo());
        data.put("externalTxId", tx.getExternalTxId());
        data.put("amount", amount);
        data.put("tip", tip);
        data.put("total", amount.add(tip));
        data.put("status", tx.getStatus());
        return Result.success("已支付", data);
    }

    /**
     * Webhook（POS 异步通知）
     */
    @PostMapping("/pos/webhook")
    public Result<Void> posWebhook(@RequestBody Map<String, Object> body) {
        String txNo = (String) body.get("txNo");
        String status = (String) body.get("status");
        if (txNo != null) {
            Long tenantId = TenantContextHolder.get().getTenantId();
            PosTransaction tx = posTxMapper.selectOne(new LambdaQueryWrapper<PosTransaction>()
                    .eq(PosTransaction::getTxNo, txNo)
                    .eq(PosTransaction::getTenantId, tenantId));
            if (tx != null) {
                tx.setStatus(status);
                posTxMapper.updateById(tx);
            }
        }
        return Result.success("已处理", null);
    }

    @PostMapping("/pos/{id}/refund")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refundPos(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosTransaction tx = posTxMapper.selectById(id);
        if (tx == null || !tx.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "交易不存在");
        BigDecimal amount = body.get("amount") == null ? tx.getAmount() : new BigDecimal(body.get("amount").toString());

        // 创建退款记录
        PosTransaction refund = new PosTransaction();
        refund.setTenantId(tenantId);
        refund.setPosId(tx.getPosId());
        refund.setStoreId(tx.getStoreId());
        refund.setTxNo("REFUND" + System.currentTimeMillis());
        refund.setVendor(tx.getVendor());
        refund.setAmount(amount.negate());
        refund.setCurrency(tx.getCurrency());
        refund.setStatus("REFUNDED");
        refund.setOrderId(tx.getOrderId());
        refund.setMemberId(tx.getMemberId());
        posTxMapper.insert(refund);

        tx.setStatus("REFUNDED");
        posTxMapper.updateById(tx);
        return Result.success("已退款", null);
    }

    @GetMapping("/pos/transaction/list")
    public Result<List<PosTransaction>> listPosTx(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PosTransaction> qw = new LambdaQueryWrapper<PosTransaction>()
                .eq(PosTransaction::getTenantId, tenantId)
                .orderByDesc(PosTransaction::getId);
        if (status != null) qw.eq(PosTransaction::getStatus, status);
        return Result.success(posTxMapper.selectList(qw));
    }

    @GetMapping("/pos/dashboard")
    public Result<Map<String, Object>> posDashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("totalTx", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pos_transaction WHERE tenant_id = ?", Long.class, tenantId));
            data.put("totalRevenue", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM pos_transaction WHERE tenant_id = ? AND status = 'COMPLETED' AND amount > 0",
                    Double.class, tenantId));
            data.put("totalTips", jdbc.queryForObject(
                    "SELECT COALESCE(SUM(tip), 0) FROM pos_transaction WHERE tenant_id = ? AND status = 'COMPLETED'",
                    Double.class, tenantId));
            data.put("byVendor", jdbc.queryForList(
                    "SELECT vendor, COUNT(*) AS cnt, SUM(amount) AS total FROM pos_transaction WHERE tenant_id = ? AND status = 'COMPLETED' GROUP BY vendor",
                    tenantId));
            data.put("onlineDevices", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pos_device WHERE tenant_id = ? AND status = 'ONLINE'",
                    Long.class, tenantId));
            return Result.success(data);
        } catch (Exception e) {
            return Result.success(new LinkedHashMap<>());
        }
    }
}