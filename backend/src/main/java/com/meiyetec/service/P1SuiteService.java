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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P1.9 境外支付 + P1.10 国际预约 + P1.11 自助签到 + P1.12 并发症 SOP + P1.13 风控 + P1.14 Cohort
 */
@Slf4j
@RestController
@RequestMapping("/p1-suite")
@RequiredArgsConstructor
public class P1SuiteService {

    private final PaymentGatewayMapper gatewayMapper;
    private final PaymentIntentMapper intentMapper;
    private final OverseasBookingMapper overseasBookingMapper;
    private final KioskDeviceMapper kioskDeviceMapper;
    private final KioskCheckinMapper kioskCheckinMapper;
    private final ComplicationSopMapper sopMapper;
    private final ComplicationEventMapper eventMapper;
    private final RiskRuleMapper riskRuleMapper;
    private final RiskEventMapper riskEventMapper;
    private final CohortDefinitionMapper cohortDefMapper;
    private final CohortResultMapper cohortResultMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // P1.9 境外支付（Stripe/Square/BNPL）
    // ====================================================================

    @PostMapping("/payment/gateway")
    public Result<PaymentGateway> saveGateway(@RequestBody PaymentGateway g) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (g.getGatewayCode() == null) return Result.error(BizCode.BAD_REQUEST, "gatewayCode 必填");
        if (g.getStatus() == null) g.setStatus("ACTIVE");
        g.setTenantId(tenantId);
        g.setId(null);
        gatewayMapper.insert(g);
        return Result.success("已保存", g);
    }

    @GetMapping("/payment/gateway/list")
    public Result<List<PaymentGateway>> listGateways() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(gatewayMapper.selectList(new LambdaQueryWrapper<PaymentGateway>()
                .eq(PaymentGateway::getTenantId, tenantId)
                .eq(PaymentGateway::getStatus, "ACTIVE")));
    }

    /**
     * 创建支付意图
     */
    @PostMapping("/payment/intent")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> createIntent(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String gateway = (String) body.get("gatewayCode");
        String method = (String) body.getOrDefault("method", "CARD");
        Integer installments = body.get("installments") == null ? null : ((Number) body.get("installments")).intValue();

        PaymentIntent intent = new PaymentIntent();
        intent.setTenantId(tenantId);
        intent.setIntentNo("PI" + System.currentTimeMillis());
        intent.setGatewayCode(gateway);
        intent.setOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        intent.setOrderNo((String) body.get("orderNo"));
        intent.setAmount(amount);
        intent.setCurrency((String) body.getOrDefault("currency", "USD"));
        intent.setMethod(method);
        intent.setIntentStatus("CREATED");
        intent.setInstallments(installments);
        // 模拟 client secret
        intent.setClientSecret(intent.getIntentNo() + "_secret_" + UUID.randomUUID().toString().substring(0, 8));
        intentMapper.insert(intent);

        // 风控评估
        RiskEvent risk = assessRisk(tenantId, "PAYMENT", intent.getId(), intent.getIntentNo(), amount);
        if (risk != null && "BLOCK".equals(risk.getAction())) {
            intent.setIntentStatus("FAILED");
            intent.setErrorMessage("风控拦截");
            intentMapper.updateById(intent);
            return Result.error(BizCode.SYSTEM_ERROR, "支付已被风控拦截");
        }

        // 模拟支付（生产环境替换为真实 Stripe/Square SDK）
        boolean sandbox = body.get("sandbox") == null || Boolean.TRUE.equals(body.get("sandbox"));
        if (sandbox) {
            intent.setIntentStatus("SUCCEEDED");
            intent.setTransactionId("txn_" + UUID.randomUUID().toString().substring(0, 16));
            intent.setPaidAt(LocalDateTime.now());
            intentMapper.updateById(intent);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intentNo", intent.getIntentNo());
        data.put("clientSecret", intent.getClientSecret());
        data.put("amount", amount);
        data.put("status", intent.getIntentStatus());
        data.put("transactionId", intent.getTransactionId());
        return Result.success("已创建", data);
    }

    @PostMapping("/payment/intent/{id}/confirm")
    public Result<Void> confirmIntent(@PathVariable Long id) {
        PaymentIntent intent = intentMapper.selectById(id);
        if (intent == null) return Result.error(BizCode.NOT_FOUND, "Intent 不存在");
        intent.setIntentStatus("SUCCEEDED");
        intent.setPaidAt(LocalDateTime.now());
        if (intent.getTransactionId() == null) intent.setTransactionId("txn_" + UUID.randomUUID().toString().substring(0, 16));
        intentMapper.updateById(intent);
        return Result.success("已确认", null);
    }

    @PostMapping("/payment/webhook")
    public Result<Void> webhook(@RequestBody Map<String, Object> body) {
        // 模拟 Stripe webhook
        String intentNo = (String) body.get("intentNo");
        String status = (String) body.get("status");
        if (intentNo != null) {
            PaymentIntent intent = intentMapper.selectOne(new LambdaQueryWrapper<PaymentIntent>()
                    .eq(PaymentIntent::getIntentNo, intentNo));
            if (intent != null) {
                intent.setIntentStatus(status);
                intent.setPaidAt(LocalDateTime.now());
                intentMapper.updateById(intent);
            }
        }
        return Result.success("已处理", null);
    }

    @GetMapping("/payment/intent/list")
    public Result<List<PaymentIntent>> listIntents(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PaymentIntent> qw = new LambdaQueryWrapper<PaymentIntent>()
                .eq(PaymentIntent::getTenantId, tenantId)
                .orderByDesc(PaymentIntent::getId);
        if (status != null) qw.eq(PaymentIntent::getIntentStatus, status);
        return Result.success(intentMapper.selectList(qw));
    }

    // ====================================================================
    // P1.10 国际预约
    // ====================================================================

    /**
     * Google Business / Instagram 回调入口（webhook）
     */
    @PostMapping("/booking/webhook")
    @Transactional(rollbackFor = Exception.class)
    public Result<OverseasBooking> webhookBooking(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String source = (String) body.getOrDefault("source", "GOOGLE");

        OverseasBooking b = new OverseasBooking();
        b.setTenantId(tenantId);
        b.setBookingNo("OB" + System.currentTimeMillis());
        b.setSource(source);
        b.setExternalId((String) body.get("externalId"));
        b.setCustomerName((String) body.get("customerName"));
        b.setCustomerPhone((String) body.get("customerPhone"));
        b.setCustomerEmail((String) body.get("customerEmail"));
        b.setCustomerLocale((String) body.getOrDefault("locale", "en"));
        b.setServiceId(body.get("serviceId") == null ? null : ((Number) body.get("serviceId")).longValue());
        b.setServiceName((String) body.get("serviceName"));
        b.setBookingDate(body.get("bookingDate") == null ? LocalDate.now() : LocalDate.parse(body.get("bookingDate").toString()));
        b.setBookingTime((String) body.get("bookingTime"));
        b.setNotes((String) body.get("notes"));
        b.setIpAddress((String) body.get("ipAddress"));
        b.setUserAgent((String) body.get("userAgent"));
        b.setRawPayload(toJson(body));
        b.setStatus("NEW");
        overseasBookingMapper.insert(b);

        return Result.success("已接收", b);
    }

    @PostMapping("/booking/{id}/convert")
    @Transactional(rollbackFor = Exception.class)
    public Result<OverseasBooking> convertToLocal(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        OverseasBooking b = overseasBookingMapper.selectById(id);
        if (b == null || !b.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "预约不存在");
        b.setConvertedMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        b.setConvertedOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        b.setStatus("CONFIRMED");
        overseasBookingMapper.updateById(b);
        return Result.success("已转化", b);
    }

    @GetMapping("/booking/list")
    public Result<List<OverseasBooking>> listBookings(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<OverseasBooking> qw = new LambdaQueryWrapper<OverseasBooking>()
                .eq(OverseasBooking::getTenantId, tenantId)
                .orderByDesc(OverseasBooking::getId);
        if (status != null) qw.eq(OverseasBooking::getStatus, status);
        return Result.success(overseasBookingMapper.selectList(qw));
    }

    // ====================================================================
    // P1.11 自助签到
    // ====================================================================

    @PostMapping("/kiosk/device")
    public Result<KioskDevice> registerDevice(@RequestBody KioskDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getDeviceCode() == null) return Result.error(BizCode.BAD_REQUEST, "deviceCode 必填");
        d.setTenantId(tenantId);
        d.setId(null);
        d.setStatus("ONLINE");
        d.setLastHeartbeat(LocalDateTime.now());
        kioskDeviceMapper.insert(d);
        return Result.success("已注册", d);
    }

    @PostMapping("/kiosk/device/{id}/heartbeat")
    public Result<Void> heartbeat(@PathVariable Long id) {
        jdbc.update("UPDATE kiosk_device SET last_heartbeat = ? WHERE id = ?", LocalDateTime.now(), id);
        return Result.success("心跳已更新", null);
    }

    /**
     * 签到
     */
    @PostMapping("/kiosk/checkin")
    @Transactional(rollbackFor = Exception.class)
    public Result<KioskCheckin> checkin(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        KioskCheckin c = new KioskCheckin();
        c.setTenantId(tenantId);
        c.setDeviceId(body.get("deviceId") == null ? null : ((Number) body.get("deviceId")).longValue());
        c.setStoreId(body.get("storeId") == null ? null : ((Number) body.get("storeId")).longValue());
        c.setBookingId(body.get("bookingId") == null ? null : ((Number) body.get("bookingId")).longValue());
        c.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        c.setCheckinMethod((String) body.getOrDefault("method", "QRCODE"));
        c.setCheckinTime(LocalDateTime.now());
        c.setStatus("CHECKED_IN");
        kioskCheckinMapper.insert(c);
        return Result.success("已签到", c);
    }

    @PostMapping("/kiosk/checkin/{id}/update")
    public Result<Void> updateCheckinStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        KioskCheckin c = kioskCheckinMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "签到记录不存在");
        String status = (String) body.get("status");
        if (status == null) return Result.error(BizCode.BAD_REQUEST, "status 必填");
        c.setStatus(status);
        if ("ARRIVED".equals(status)) c.setArrivedAt(LocalDateTime.now());
        if ("IN_SERVICE".equals(status)) c.setServiceStartedAt(LocalDateTime.now());
        if ("COMPLETED".equals(status)) c.setServiceFinishedAt(LocalDateTime.now());
        if (body.get("staffId") != null) c.setStaffId(((Number) body.get("staffId")).longValue());
        kioskCheckinMapper.updateById(c);
        return Result.success("已更新", null);
    }

    @GetMapping("/kiosk/checkin/list")
    public Result<List<Map<String, Object>>> listCheckins(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String sql = "SELECT k.*, m.member_name FROM kiosk_checkin k LEFT JOIN crm_member m ON m.id = k.member_id WHERE k.tenant_id = ?";
        if (status != null) sql += " AND k.status = ?";
        sql += " ORDER BY k.id DESC LIMIT 200";
        List<Map<String, Object>> rows = status == null
                ? jdbc.queryForList(sql, tenantId)
                : jdbc.queryForList(sql, tenantId, status);
        return Result.success(rows);
    }

    // ====================================================================
    // P1.12 并发症 SOP（医美）
    // ====================================================================

    @PostMapping("/sop")
    public Result<ComplicationSop> createSop(@RequestBody ComplicationSop s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (s.getSopCode() == null || s.getSopName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (s.getCategory() == null) s.setCategory("INJECTION");
        if (s.getSeverity() == null) s.setSeverity("MEDIUM");
        if (s.getStatus() == null) s.setStatus("ACTIVE");
        if (s.getVersion() == null) s.setVersion("1.0");
        s.setTenantId(tenantId);
        s.setId(null);
        sopMapper.insert(s);
        return Result.success("已创建", s);
    }

    @GetMapping("/sop/list")
    public Result<List<ComplicationSop>> listSops() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(sopMapper.selectList(new LambdaQueryWrapper<ComplicationSop>()
                .eq(ComplicationSop::getTenantId, tenantId)
                .eq(ComplicationSop::getStatus, "ACTIVE")));
    }

    /**
     * 上报并发症事件
     */
    @PostMapping("/sop/event")
    @Transactional(rollbackFor = Exception.class)
    public Result<ComplicationEvent> reportEvent(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        ComplicationEvent e = new ComplicationEvent();
        e.setTenantId(tenantId);
        e.setEventNo("EV" + System.currentTimeMillis());
        e.setSopId(body.get("sopId") == null ? null : ((Number) body.get("sopId")).longValue());
        e.setSopCode((String) body.get("sopCode"));
        e.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
        e.setOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        e.setServiceId(body.get("serviceId") == null ? null : ((Number) body.get("serviceId")).longValue());
        e.setSeverity((String) body.getOrDefault("severity", "MEDIUM"));
        e.setSymptoms((String) body.get("symptoms"));
        e.setTriggerTime(LocalDateTime.now());
        e.setHandlerId(body.get("handlerId") == null ? null : ((Number) body.get("handlerId")).longValue());
        e.setHandlerName((String) body.get("handlerName"));
        e.setStatus("OPEN");
        eventMapper.insert(e);
        return Result.success("已上报", e);
    }

    @PostMapping("/sop/event/{id}/respond")
    public Result<Void> respond(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ComplicationEvent e = eventMapper.selectById(id);
        if (e == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        e.setResponseTime(LocalDateTime.now());
        e.setStatus("RESPONDING");
        String log = e.getExecutionLog() == null ? "" : e.getExecutionLog();
        log += "[" + LocalDateTime.now() + "] " + body.getOrDefault("note", "已响应") + "\n";
        e.setExecutionLog(log);
        eventMapper.updateById(e);
        return Result.success("已响应", null);
    }

    @PostMapping("/sop/event/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ComplicationEvent e = eventMapper.selectById(id);
        if (e == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        e.setResolvedTime(LocalDateTime.now());
        e.setStatus("RESOLVED");
        e.setOutcome((String) body.get("outcome"));
        eventMapper.updateById(e);
        return Result.success("已解决", null);
    }

    @GetMapping("/sop/event/list")
    public Result<List<ComplicationEvent>> listEvents(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<ComplicationEvent> qw = new LambdaQueryWrapper<ComplicationEvent>()
                .eq(ComplicationEvent::getTenantId, tenantId)
                .orderByDesc(ComplicationEvent::getId);
        if (status != null) qw.eq(ComplicationEvent::getStatus, status);
        return Result.success(eventMapper.selectList(qw));
    }

    @PostMapping("/sop/init-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initDefaultSop() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                Map.of("code", "BRUISING_HOT", "name", "玻尿酸淤青", "category", "INJECTION", "severity", "LOW",
                        "symptom", "注射部位淤青肿胀", "timing", "IMMEDIATE", "deadline", 30,
                        "med", "冰敷 + 喜辽妥", "rule", "轻度无需处理；严重需激光修复"),
                Map.of("code", "ALLERGIC_SHOCK", "name", "过敏性休克", "category", "INJECTION", "severity", "CRITICAL",
                        "symptom", "呼吸困难/血压下降", "timing", "IMMEDIATE", "deadline", 5,
                        "med", "肾上腺素 0.3-0.5mg 肌注", "rule", "立即拨打 120，转急诊"),
                Map.of("code", "LASER_BURN", "name", "激光灼伤", "category", "LASER", "severity", "HIGH",
                        "symptom", "皮肤红肿/水泡", "timing", "IMMEDIATE", "deadline", 60,
                        "med", "生理盐水冲洗 + 烫伤膏", "rule", "严重需整形修复"),
                Map.of("code", "INFECTION_RISK", "name", "感染风险", "category", "OTHER", "severity", "MEDIUM",
                        "symptom", "局部红肿热痛", "timing", "24H", "deadline", 120,
                        "med", "口服抗生素 + 局部消毒", "rule", "72小时未改善需住院")
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            ComplicationSop exist = sopMapper.selectOne(new LambdaQueryWrapper<ComplicationSop>()
                    .eq(ComplicationSop::getTenantId, tenantId)
                    .eq(ComplicationSop::getSopCode, d.get("code")));
            if (exist != null) continue;
            ComplicationSop s = new ComplicationSop();
            s.setTenantId(tenantId);
            s.setSopCode((String) d.get("code"));
            s.setSopName((String) d.get("name"));
            s.setCategory((String) d.get("category"));
            s.setSeverity((String) d.get("severity"));
            s.setTriggerSymptom((String) d.get("symptom"));
            s.setTriggerTiming((String) d.get("timing"));
            s.setResponseDeadlineMin((Integer) d.get("deadline"));
            s.setMedications((String) d.get("med"));
            s.setEscalationRule((String) d.get("rule"));
            s.setStepsJson(toJson(List.of(
                    Map.of("step", 1, "action", "现场评估", "duration", "5min"),
                    Map.of("step", 2, "action", "初步处理", "duration", "10min"),
                    Map.of("step", 3, "action", "上报医生", "duration", "5min"),
                    Map.of("step", 4, "action", "持续观察", "duration", "30min")
            )));
            s.setStatus("ACTIVE");
            s.setVersion("1.0");
            s.setResponsibleRole("DOCTOR");
            sopMapper.insert(s);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        return Result.success("已初始化", data);
    }

    // ====================================================================
    // P1.13 风控
    // ====================================================================

    @PostMapping("/risk/rule")
    public Result<RiskRule> createRule(@RequestBody RiskRule r) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (r.getRuleCode() == null || r.getRuleName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (r.getEnabled() == null) r.setEnabled(1);
        r.setTenantId(tenantId);
        r.setId(null);
        r.setHitCount(0);
        riskRuleMapper.insert(r);
        return Result.success("已创建", r);
    }

    @GetMapping("/risk/rule/list")
    public Result<List<RiskRule>> listRules() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(riskRuleMapper.selectList(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getTenantId, tenantId)
                .eq(RiskRule::getEnabled, 1)));
    }

    @PostMapping("/risk/init-default")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initRisk() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                Map.of("code", "HIGH_AMOUNT", "name", "大额交易", "type", "TRANSACTION", "level", "RISK", "threshold", 5000, "action", "FLAG",
                        "desc", "单笔超过 5000 标记"),
                Map.of("code", "FREQUENT_REFUND", "name", "频繁退款", "type", "REFUND", "level", "HIGH", "threshold", 3, "action", "REVIEW",
                        "desc", "24小时内退款超过 3 次"),
                Map.of("code", "ABNORMAL_LOGIN", "name", "异常登录", "type", "LOGIN", "level", "WARN", "threshold", 5, "action", "NOTIFY",
                        "desc", "5 分钟内登录 5 次以上"),
                Map.of("code", "COUPON_ABUSE", "name", "券滥用", "type", "COUPON", "level", "RISK", "threshold", 10, "action", "BLOCK",
                        "desc", "单用户单日领取超过 10 张券"),
                Map.of("code", "BLACKLIST_CARD", "name", "黑名单卡", "type", "TRANSACTION", "level", "CRITICAL", "threshold", 0, "action", "BLOCK",
                        "desc", "黑名单卡组织支付")
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            RiskRule exist = riskRuleMapper.selectOne(new LambdaQueryWrapper<RiskRule>()
                    .eq(RiskRule::getTenantId, tenantId)
                    .eq(RiskRule::getRuleCode, d.get("code")));
            if (exist != null) continue;
            RiskRule r = new RiskRule();
            r.setTenantId(tenantId);
            r.setRuleCode((String) d.get("code"));
            r.setRuleName((String) d.get("name"));
            r.setRuleType((String) d.get("type"));
            r.setRuleLevel((String) d.get("level"));
            r.setThreshold(d.get("threshold") == null ? null : new BigDecimal(d.get("threshold").toString()));
            r.setAction((String) d.get("action"));
            r.setDescription((String) d.get("desc"));
            r.setEnabled(1);
            r.setHitCount(0);
            riskRuleMapper.insert(r);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        return Result.success("已初始化", data);
    }

    /**
     * 评估（自动）
     */
    private RiskEvent assessRisk(Long tenantId, String bizType, Long bizId, String bizNo, BigDecimal amount) {
        List<RiskRule> rules = riskRuleMapper.selectList(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getTenantId, tenantId)
                .eq(RiskRule::getEnabled, 1));
        for (RiskRule rule : rules) {
            if ("TRANSACTION".equals(rule.getRuleType()) && amount != null
                    && rule.getThreshold() != null
                    && amount.compareTo(rule.getThreshold()) >= 0
                    && "HIGH_AMOUNT".equals(rule.getRuleCode())) {
                return saveRiskEvent(tenantId, rule, bizType, bizId, bizNo, amount);
            }
        }
        return null;
    }

    private RiskEvent saveRiskEvent(Long tenantId, RiskRule rule, String bizType, Long bizId, String bizNo, BigDecimal amount) {
        RiskEvent e = new RiskEvent();
        e.setTenantId(tenantId);
        e.setEventNo("RE" + System.currentTimeMillis());
        e.setRuleId(rule.getId());
        e.setRuleCode(rule.getRuleCode());
        e.setRuleLevel(rule.getRuleLevel());
        e.setBizType(bizType);
        e.setBizId(bizId);
        e.setBizNo(bizNo);
        e.setAmount(amount);
        e.setRiskScore(rule.getRuleLevel().equals("CRITICAL") ? 90 :
                rule.getRuleLevel().equals("HIGH") ? 70 : 50);
        e.setAction(rule.getAction());
        e.setDescription(rule.getRuleName());
        e.setHandled(0);
        e.setHandleStatus("PENDING");
        riskEventMapper.insert(e);
        jdbc.update("UPDATE risk_rule SET hit_count = hit_count + 1, last_hit_at = NOW() WHERE id = ?", rule.getId());
        return e;
    }

    @PostMapping("/risk/event/{id}/handle")
    public Result<Void> handleEvent(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        RiskEvent e = riskEventMapper.selectById(id);
        if (e == null) return Result.error(BizCode.NOT_FOUND, "事件不存在");
        e.setHandled(1);
        e.setHandleStatus((String) body.getOrDefault("status", "PASSED"));
        e.setHandlerId(body.get("handlerId") == null ? null : ((Number) body.get("handlerId")).longValue());
        e.setHandleNotes((String) body.get("notes"));
        e.setHandledAt(LocalDateTime.now());
        riskEventMapper.updateById(e);
        return Result.success("已处理", null);
    }

    @GetMapping("/risk/event/list")
    public Result<List<RiskEvent>> listRiskEvents(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<RiskEvent> qw = new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getTenantId, tenantId)
                .orderByDesc(RiskEvent::getId);
        if (status != null) qw.eq(RiskEvent::getHandleStatus, status);
        return Result.success(riskEventMapper.selectList(qw));
    }

    // ====================================================================
    // P1.14 Cohort 分析
    // ====================================================================

    @PostMapping("/cohort/definition")
    public Result<CohortDefinition> createCohort(@RequestBody CohortDefinition c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getCohortCode() == null) return Result.error(BizCode.BAD_REQUEST, "code 必填");
        if (c.getCohortType() == null) c.setCohortType("MONTHLY");
        if (c.getMetric() == null) c.setMetric("RETAIN");
        c.setTenantId(tenantId);
        c.setId(null);
        cohortDefMapper.insert(c);
        return Result.success("已创建", c);
    }

    @GetMapping("/cohort/definition/list")
    public Result<List<CohortDefinition>> listCohorts() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(cohortDefMapper.selectList(new LambdaQueryWrapper<CohortDefinition>()
                .eq(CohortDefinition::getTenantId, tenantId)));
    }

    /**
     * 计算 Cohort（按月留存）
     */
    @PostMapping("/cohort/compute")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> computeCohort(@RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int months = body == null || body.get("months") == null ? 12 : ((Number) body.get("months")).intValue();
        LocalDate endMonth = LocalDate.now().withDayOfMonth(1).minusMonths(1);

        // 删旧结果
        jdbc.update("DELETE FROM cohort_result WHERE tenant_id = ?", tenantId);

        int totalRows = 0;
        for (int i = months - 1; i >= 0; i--) {
            LocalDate cohort = endMonth.minusMonths(i);
            String cohortPeriod = cohort.toString().substring(0, 7); // 2025-01

            // 计算 cohort size：当月新增会员数
            Integer size = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT id) FROM crm_member WHERE tenant_id = ? AND deleted = 0 AND DATE_FORMAT(create_time, '%Y-%m') = ?",
                    Integer.class, tenantId, cohortPeriod);
            if (size == null || size == 0) continue;

            // 计算 0-12 月留存
            for (int periodIdx = 0; periodIdx <= months - i - 1 && periodIdx <= 12; periodIdx++) {
                LocalDate targetMonth = cohort.plusMonths(periodIdx);
                String targetPeriod = targetMonth.toString().substring(0, 7);
                Integer active = jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT member_id) FROM crm_order WHERE tenant_id = ? AND status = 3 AND deleted = 0 AND DATE_FORMAT(pay_time, '%Y-%m') = ? AND member_id IN (SELECT id FROM crm_member WHERE tenant_id = ? AND deleted = 0 AND DATE_FORMAT(create_time, '%Y-%m') = ?)",
                        Integer.class, tenantId, targetPeriod, tenantId, cohortPeriod);
                if (active == null) active = 0;
                double rate = periodIdx == 0 ? 1.0 : (size == 0 ? 0 : active * 1.0 / size);

                CohortResult cr = new CohortResult();
                cr.setTenantId(tenantId);
                cr.setCohortCode("MEMBER_RETAIN");
                cr.setCohortPeriod(cohortPeriod);
                cr.setSize(size);
                cr.setPeriodIndex(periodIdx);
                cr.setValue(BigDecimal.valueOf(rate).setScale(4, java.math.RoundingMode.HALF_UP));
                cr.setValueType("RATE");
                cohortResultMapper.insert(cr);
                totalRows++;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("months", months);
        data.put("rows", totalRows);
        return Result.success("已计算", data);
    }

    /**
     * Cohort 透视表
     */
    @GetMapping("/cohort/pivot")
    public Result<Map<String, Object>> pivot() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            List<CohortResult> results = cohortResultMapper.selectList(new LambdaQueryWrapper<CohortResult>()
                    .eq(CohortResult::getTenantId, tenantId)
                    .orderByAsc(CohortResult::getCohortPeriod)
                    .orderByAsc(CohortResult::getPeriodIndex));

            List<String> cohortPeriods = results.stream().map(CohortResult::getCohortPeriod).distinct().collect(Collectors.toList());
            int maxPeriod = results.stream().mapToInt(CohortResult::getPeriodIndex).max().orElse(0);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (String period : cohortPeriods) {
                final String fp = period;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("cohort", period);
                for (int i = 0; i <= maxPeriod; i++) {
                    final int fi = i;
                    CohortResult r = results.stream()
                            .filter(x -> x.getCohortPeriod().equals(fp) && x.getPeriodIndex() == fi)
                            .findFirst().orElse(null);
                    row.put("s" + i, r == null ? null : r.getValue());
                }
                row.put("size", results.stream().filter(x -> x.getCohortPeriod().equals(fp)).findFirst().map(CohortResult::getSize).orElse(0));
                rows.add(row);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", rows);
            data.put("maxPeriod", maxPeriod);
            return Result.success(data);
        } catch (Exception e) {
            return Result.success(new LinkedHashMap<>());
        }
    }

    @GetMapping("/cohort/revenue")
    public Result<List<Map<String, Object>>> revenueCohort(@RequestParam(defaultValue = "12") Integer months) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DATE_FORMAT(m.create_time, '%Y-%m') AS cohort,
                       COUNT(DISTINCT m.id) AS size,
                       COALESCE(SUM(o.pay_amount), 0) AS total_revenue,
                       COALESCE(AVG(o.pay_amount), 0) AS avg_revenue
                FROM crm_member m
                LEFT JOIN crm_order o ON o.member_id = m.id AND o.status = 3 AND o.deleted = 0
                WHERE m.tenant_id = ? AND m.deleted = 0
                  AND m.create_time >= DATE_SUB(CURDATE(), INTERVAL ? MONTH)
                GROUP BY DATE_FORMAT(m.create_time, '%Y-%m')
                ORDER BY cohort DESC
                """, tenantId, months);
            return Result.success(rows);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    // ====================================================================
    // 工具
    // ====================================================================
    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}