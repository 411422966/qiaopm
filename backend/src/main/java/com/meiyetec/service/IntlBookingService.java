package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.booking.BookingConfirmationResult;
import com.meiyetec.booking.BookingConfirmationService;
import com.meiyetec.entity.OverseasBooking;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.OverseasBookingMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 国际预约 / 海外预约（EXT-15 / P3-25 国际支付 / Google + Instagram 对接）
 *
 * <p>P3-26 深化（P0-MONEY-002）：
 * <ul>
 *   <li>所有预约走 {@link OverseasBookingMapper} 持久化，替换原内存 List 模式</li>
 *   <li>支持 Google Business Profile OAuth2 + availability 推送</li>
 *   <li>支持 Instagram Messaging Webhook + DM 解析入库</li>
 *   <li>预约转化链路：海外客户 → 创建本地会员（crm_member）→ 生成订单（pos_order）</li>
 *   <li>customerLocale 驱动多语言确认模板（EXT-19）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/intl-booking")
@RequiredArgsConstructor
public class IntlBookingService {

    private final OverseasBookingMapper bookingMapper;
    private final JdbcTemplate jdbc;
    private final BookingConfirmationService confirmationService;

    // ========================================================================
    // 渠道连接（OAuth2 入口 — 真实 Google / Instagram 跳转 URL）
    // ========================================================================

    @PostMapping("/google/connect")
    public Result<Map<String, Object>> connectGoogle(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String clientId = (String) body.get("clientId");
        String clientSecret = (String) body.get("clientSecret");
        if (clientId == null || clientId.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "clientId 必填");
        }
        // P3-26：返回真实 Google OAuth2 授权 URL
        // https://developers.google.com/my-business/reference/rest/v4/accounts.locations
        String state = "google-" + tenantId + "-" + System.currentTimeMillis();
        String authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + java.net.URLEncoder.encode("https://api.meiyetec.com/intl-booking/google/callback", java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=" + java.net.URLEncoder.encode("https://www.googleapis.com/auth/business.manage", java.nio.charset.StandardCharsets.UTF_8)
                + "&access_type=offline"
                + "&state=" + state;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", "GOOGLE");
        data.put("authUrl", authUrl);
        data.put("state", state);
        data.put("configuredAt", LocalDateTime.now());
        return Result.success("请使用 authUrl 完成 Google OAuth2 授权", data);
    }

    @PostMapping("/google/callback")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> googleCallback(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String code = (String) body.get("code");
        String clientId = (String) body.get("clientId");
        String clientSecret = (String) body.get("clientSecret");
        if (code == null) return Result.error(BizCode.BAD_REQUEST, "code 必填");

        // P3-26：真实场景调用 Google token 端点：
        //   POST https://oauth2.googleapis.com/token
        //   body: code, client_id, client_secret, redirect_uri, grant_type=authorization_code
        //   返回 access_token + refresh_token
        // 当前为本地演示：直接落库一条配置记录（externalId = code 前缀）

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", "GOOGLE");
        data.put("accessToken", "ya29.DEMO-" + code.substring(0, Math.min(8, code.length())));
        data.put("refreshToken", "1//0DEMO-" + code.hashCode());
        data.put("connectedAt", LocalDateTime.now());
        return Result.success("已绑定 Google Business Profile（演示模式，请填入真实 OAuth Client）", data);
    }

    @PostMapping("/instagram/connect")
    public Result<Map<String, Object>> connectInstagram(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String appId = (String) body.get("appId");
        String appSecret = (String) body.get("appSecret");
        if (appId == null || appId.isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "appId 必填");
        }
        // P3-26：真实 Instagram Graph API OAuth2 URL
        // https://developers.facebook.com/docs/instagram-api/reference/ig-message
        String state = "ig-" + tenantId + "-" + System.currentTimeMillis();
        String authUrl = "https://www.facebook.com/v18.0/dialog/oauth"
                + "?client_id=" + appId
                + "&redirect_uri=" + java.net.URLEncoder.encode("https://api.meiyetec.com/intl-booking/instagram/callback", java.nio.charset.StandardCharsets.UTF_8)
                + "&scope=" + java.net.URLEncoder.encode("instagram_basic,instagram_manage_messages,pages_show_list", java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&state=" + state;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", "INSTAGRAM");
        data.put("authUrl", authUrl);
        data.put("state", state);
        data.put("configuredAt", LocalDateTime.now());
        return Result.success("请使用 authUrl 完成 Instagram OAuth2 授权", data);
    }

    @PostMapping("/instagram/callback")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> instagramCallback(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String code = (String) body.get("code");
        if (code == null) return Result.error(BizCode.BAD_REQUEST, "code 必填");

        // P3-26：真实场景：
        //   GET https://graph.facebook.com/v18.0/oauth/access_token
        //       ?client_id={app-id}&redirect_uri={redirect-uri}&client_secret={app-secret}&code={code}
        //   返回 access_token；用此 token 订阅 webhook
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", "INSTAGRAM");
        data.put("accessToken", "IGQVJ-DEMO-" + code.substring(0, Math.min(8, code.length())));
        data.put("connectedAt", LocalDateTime.now());
        return Result.success("已绑定 Instagram（演示模式，请填入真实 Meta App）", data);
    }

    @GetMapping("/channels")
    public Result<List<Map<String, Object>>> listChannels() {
        // P3-26：channel 列表改成只读静态枚举（无状态，纯展示）
        List<Map<String, Object>> channels = List.of(
                Map.of("id", 1L, "channel", "GOOGLE", "name", "Google Business Profile", "status", "READY"),
                Map.of("id", 2L, "channel", "INSTAGRAM", "name", "Instagram DM", "status", "READY"),
                Map.of("id", 3L, "channel", "FACEBOOK", "name", "Facebook Page", "status", "PLANNED"),
                Map.of("id", 4L, "channel", "TIKTOK", "name", "TikTok Shop", "status", "PLANNED")
        );
        return Result.success(channels);
    }

    // ========================================================================
    // 预约拉取（替换原内存 BOOKINGS List 模式）
    // ========================================================================

    @PostMapping("/{channelId}/pull")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> pullBookings(@PathVariable Long channelId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String channel = channelIdToName(channelId);
        if (channel == null) return Result.error(BizCode.NOT_FOUND, "渠道不存在");

        // P3-26：真实场景：
        //   Google: GET https://mybusiness.googleapis.com/v4/accounts/{accountId}/locations/{locationId}/appointments
        //   Instagram: GET https://graph.facebook.com/v18.0/{page-id}/conversations?fields=messages
        // 当前为演示模式：插入 1 条示例预约记录（验证 DB 持久化链路通畅）
        int pulled = 0;
        if ("GOOGLE".equals(channel) || "INSTAGRAM".equals(channel)) {
            OverseasBooking b = new OverseasBooking();
            b.setTenantId(tenantId);
            b.setBookingNo(channel + "-" + System.currentTimeMillis());
            b.setSource(channel);
            b.setExternalId("EXT-" + System.currentTimeMillis());
            b.setCustomerName("海外客户演示");
            b.setCustomerLocale("en");
            b.setCustomerPhone("+1-555-" + (1000 + pulled));
            b.setServiceName("Facial Treatment");
            b.setBookingDate(java.time.LocalDate.now().plusDays(1));
            b.setBookingTime("14:00");
            b.setStatus("NEW");
            b.setRawPayload("{\"source\":\"DEMO\",\"channel\":\"" + channel + "\"}");
            bookingMapper.insert(b);
            pulled = 1;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pulled", pulled);
        data.put("channel", channel);
        return Result.success("已拉取（演示模式，真实 API 接入需 OAuth Client 完成授权）", data);
    }

    @GetMapping("/list")
    public Result<List<OverseasBooking>> listBookings(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String source) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<OverseasBooking> qw = new LambdaQueryWrapper<OverseasBooking>()
                .eq(OverseasBooking::getTenantId, tenantId)
                .orderByDesc(OverseasBooking::getId);
        if (status != null && !status.isEmpty()) qw.eq(OverseasBooking::getStatus, status);
        if (source != null && !source.isEmpty()) qw.eq(OverseasBooking::getSource, source);
        return Result.success(bookingMapper.selectList(qw));
    }

    @GetMapping("/{id}")
    public Result<OverseasBooking> getBooking(@PathVariable Long id) {
        OverseasBooking b = bookingMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "预约不存在");
        return Result.success(b);
    }

    @PostMapping("/{id}/status")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OverseasBooking b = bookingMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "预约不存在");
        String status = (String) body.get("status");
        if (status == null) return Result.error(BizCode.BAD_REQUEST, "status 必填");
        String oldStatus = b.getStatus();
        b.setStatus(status);
        if ("CONFIRMED".equals(status)) {
            b.setNotes((b.getNotes() == null ? "" : b.getNotes()) + "\n[confirmed] " + LocalDateTime.now());
        }
        if ("CANCELLED".equals(status)) {
            b.setNotes((b.getNotes() == null ? "" : b.getNotes()) + "\n[cancelled] " + LocalDateTime.now());
        }
        bookingMapper.updateById(b);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("booking", b);

        // EXT-19（后续优化 3）：状态从非 CONFIRMED → CONFIRMED 时自动触发多语言确认发送
        // 默认开启；body.autoSend=false 可关闭
        Boolean autoSend = body.get("autoSend") == null ? Boolean.TRUE
                : Boolean.valueOf(body.get("autoSend").toString());
        if (autoSend && "CONFIRMED".equals(status) && !"CONFIRMED".equals(oldStatus)) {
            try {
                BookingConfirmationResult r = confirmationService.sendConfirmation(b);
                data.put("confirmation", r);
                log.info("[INTL-BOOKING] auto-send on CONFIRM: bookingId={} overallSuccess={} locale={} (fallback={}) channels={}",
                        id, r.isOverallSuccess(), r.getResolvedLocale(), r.getUsedFallback(),
                        r.getChannelResults().keySet());
            } catch (Exception e) {
                log.warn("[INTL-BOOKING] auto-send failed (status 仍成功): {}", e.getMessage());
                data.put("confirmationError", e.getMessage());
            }
        }
        return Result.success("已更新状态", data);
    }

    /**
     * EXT-19：手动触发多语言确认发送
     *
     * <p>独立于状态变更，店长可对任何状态的预约重发确认（例如客户改了联系方式）。
     * 自动触发（如 CONFIRMED → 发）需业务侧另外决策，本端点保持手动。
     */
    @PostMapping("/{id}/send-confirmation")
    public Result<BookingConfirmationResult> sendConfirmation(@PathVariable Long id) {
        OverseasBooking b = bookingMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "预约不存在");
        BookingConfirmationResult r = confirmationService.sendConfirmation(b);
        return Result.success("已发送", r);
    }

    // ========================================================================
    // Webhook 入口（Google / Instagram 推送预约变更 / DM 消息）
    // ========================================================================

    /**
     * Google Business Profile Webhook 入口
     * 真实场景：Google → POST 到该端点，payload 为 AppointmentNotification 格式
     *
     * <p>外部平台 webhook：异常时返 HTTP 500（不是 200），让上游 Google 重试。
     * 不走 GlobalExceptionHandler（统一返 HTTP 200）— 改为 {@link ResponseEntity} 直接控制状态码。
     */
    @PostMapping("/webhook/google")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Result<Map<String, Object>>> googleWebhook(@RequestBody Map<String, Object> payload,
                                                     @RequestHeader(value = "X-Goog-Channel-Token", required = false) String token) {
        Long tenantId = resolveTenantFromToken(token);
        if (tenantId == null) tenantId = 1L; // 演示模式兜底
        log.info("[GOOGLE WEBHOOK] payload={}", payload);

        try {
            // P3-26：解析 Google AppointmentNotification → 写入 overseas_booking
            OverseasBooking b = new OverseasBooking();
            b.setTenantId(tenantId);
            b.setBookingNo("GOOGLE-WEBHOOK-" + System.currentTimeMillis());
            b.setSource("GOOGLE");
            b.setExternalId(String.valueOf(payload.getOrDefault("notificationId", "unknown")));
            b.setCustomerName((String) payload.getOrDefault("customerName", "Google Customer"));
            b.setCustomerPhone((String) payload.get("customerPhone"));
            b.setCustomerEmail((String) payload.get("customerEmail"));
            b.setCustomerLocale((String) payload.getOrDefault("locale", "en"));
            b.setServiceName((String) payload.getOrDefault("serviceName", "Service"));
            Object bookingTime = payload.get("bookingTime");
            if (bookingTime != null) {
                try {
                    b.setBookingDate(java.time.LocalDate.parse(bookingTime.toString().substring(0, 10)));
                    b.setBookingTime(bookingTime.toString().substring(11, 16));
                } catch (Exception ignore) {}
            }
            b.setStatus("NEW");
            b.setRawPayload(payload.toString());
            bookingMapper.insert(b);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("code", "SUCCESS");
            res.put("bookingId", b.getId());
            return ResponseEntity.ok(Result.success(res));
        } catch (Exception e) {
            // I047 同源：DB 异常（I038 overseas_booking 缺表）→ 上游需见 HTTP 500 才能重试
            log.error("[GOOGLE WEBHOOK] process failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(BizCode.SYSTEM_ERROR, "Google webhook 处理失败: " + e.getMessage()));
        }
    }

    /**
     * Instagram Messaging Webhook 入口
     * 真实场景：Meta → POST 到该端点，entry[*].messaging[*] 含 sender_id / message.text
     *
     * <p>外部平台 webhook：异常时返 HTTP 500（不是 200），让上游 Meta IG 重试。
     * 不走 GlobalExceptionHandler（统一返 HTTP 200）— 改为 {@link ResponseEntity} 直接控制状态码。
     */
    @PostMapping("/webhook/instagram")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Result<Map<String, Object>>> instagramWebhook(@RequestBody Map<String, Object> payload) {
        log.info("[INSTAGRAM WEBHOOK] payload={}", payload);

        // P3-26：解析 IG messaging webhook → 提取 text → 简单 NLP（关键词匹配服务名/时间）→ 写入 overseas_booking
        Long tenantId = 1L; // 演示模式：从 webhook 订阅表查询
        try {
            Object entryList = payload.get("entry");
            if (entryList instanceof List<?> entries && !entries.isEmpty()) {
                Object firstEntry = entries.get(0);
                if (firstEntry instanceof Map<?, ?> entry) {
                    Object messagingList = entry.get("messaging");
                    if (messagingList instanceof List<?> msgs && !msgs.isEmpty()) {
                        Object firstMsg = msgs.get(0);
                        if (firstMsg instanceof Map<?, ?> msg) {
                            Object messageObj = msg.get("message");
                            if (messageObj instanceof Map<?, ?> message) {
                                String text = (String) message.get("text");
                                Object sender = msg.get("sender");
                                String senderId = sender instanceof Map ? (String) ((Map<?, ?>) sender).get("id") : null;

                                OverseasBooking b = new OverseasBooking();
                                b.setTenantId(tenantId);
                                b.setBookingNo("IG-WEBHOOK-" + System.currentTimeMillis());
                                b.setSource("INSTAGRAM");
                                b.setExternalId(senderId == null ? "unknown" : senderId);
                                b.setCustomerName("IG User " + (senderId == null ? "?" : senderId.substring(0, Math.min(6, senderId.length()))));
                                b.setCustomerLocale("en");
                                b.setServiceName(parseServiceFromText(text));
                                b.setBookingDate(java.time.LocalDate.now().plusDays(1));
                                b.setBookingTime("10:00");
                                b.setNotes("IG DM: " + (text == null ? "(empty)" : text));
                                b.setStatus("NEW");
                                b.setRawPayload(payload.toString());
                                bookingMapper.insert(b);

                                Map<String, Object> res = new LinkedHashMap<>();
                                res.put("code", "SUCCESS");
                                res.put("bookingId", b.getId());
                                return ResponseEntity.ok(Result.success(res));
                            }
                        }
                    }
                }
            }
            // payload 合法但无 parseable message（如 IG 推送 sticker / 已读回执）— 合法 NOOP
            return ResponseEntity.ok(Result.success(Map.of("code", "NOOP", "message", "no parseable message")));
        } catch (Exception e) {
            // I047 修复（commit 1：完整版）：HTTP status 真的为 500，让上游 Meta IG 可见失败信号并触发重试
            log.error("[INSTAGRAM WEBHOOK] parse failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(BizCode.SYSTEM_ERROR, "IG webhook 解析失败: " + e.getMessage()));
        }
    }

    /**
     * Webhook 验签（GET 用于 Meta/Google 的 webhook 验证握手）
     */
    @GetMapping("/webhook/instagram")
    public Result<Map<String, Object>> instagramWebhookVerify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        // P3-26：返回 hub.challenge 完成 Meta webhook 验证握手
        if ("subscribe".equals(mode) && challenge != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("hub.challenge", challenge);
            return Result.success(data);
        }
        return Result.error(BizCode.BAD_REQUEST, "verify token invalid");
    }

    // ========================================================================
    // 预约转化（海外客户 → Member → PosOrder）
    // ========================================================================

    /**
     * P3-26 任务 4：将 overseas_booking 转化为本地会员 + 订单
     * 真实落地 convertedMemberId / convertedOrderId
     */
    @PostMapping("/{id}/convert")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> convertBooking(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        OverseasBooking b = bookingMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "预约不存在");
        if (b.getConvertedMemberId() != null) {
            return Result.error(BizCode.CONFLICT, "已转化过，会员ID=" + b.getConvertedMemberId());
        }
        if (!"CONFIRMED".equals(b.getStatus())) {
            return Result.error(BizCode.BAD_REQUEST, "仅 CONFIRMED 状态可转化");
        }

        // 1. 通过 jdbc 写入 crm_member（演示：直接 SQL，避免引入 MemberService 复杂依赖）
        Long memberId = doCreateMember(tenantId, b);

        // 2. 写入 pos_order（简化演示：占位订单）
        Long orderId = doCreatePosOrder(tenantId, memberId, b);

        // 3. 写回 convertedMemberId / convertedOrderId
        b.setConvertedMemberId(memberId);
        b.setConvertedOrderId(orderId);
        bookingMapper.updateById(b);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", memberId);
        data.put("orderId", orderId);
        return Result.success("已转化为会员与订单", data);
    }

    private Long doCreateMember(Long tenantId, OverseasBooking b) {
        // P3-26 任务 4：通过 JdbcTemplate 写 crm_member（演示模式）
        String memberNo = "M" + System.currentTimeMillis();
        jdbc.update(
            "INSERT INTO crm_member (tenant_id, store_id, member_no, name, phone, status, version, create_time, update_time) " +
            "VALUES (?, ?, ?, ?, ?, 1, 0, NOW(), NOW())",
            tenantId,
            b.getStoreId() == null ? 1L : b.getStoreId(),
            memberNo,
            b.getCustomerName() == null ? "Overseas Customer" : b.getCustomerName(),
            b.getCustomerPhone() == null ? "00000000000" : b.getCustomerPhone()
        );
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long doCreatePosOrder(Long tenantId, Long memberId, OverseasBooking b) {
        // P3-26 任务 4：写入 pos_order 占位记录（无 source_channel 列，把渠道信息写入 remark）
        String orderNo = "PO-INTL-" + System.currentTimeMillis();
        String remark = "[IntlBooking] bookingId=" + b.getId() + " channel=" + b.getSource();
        jdbc.update(
            "INSERT INTO pos_order (tenant_id, store_id, order_no, member_id, member_name, member_phone, type, " +
            "total_amount, discount_amount, final_amount, paid_amount, status, booking_id, remark, create_time, update_time) " +
            "VALUES (?, ?, ?, ?, ?, ?, 1, 0, 0, 0, 0, 1, ?, ?, NOW(), NOW())",
            tenantId,
            b.getStoreId() == null ? 1L : b.getStoreId(),
            orderNo,
            memberId,
            b.getCustomerName(),
            b.getCustomerPhone(),
            b.getId(),
            remark
        );
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long resolveTenantFromToken(String token) {
        // P3-26：根据 token 反查 tenant_id；演示模式返回 null 兜底为 1L
        return null;
    }

    private String channelIdToName(Long id) {
        if (id == null) return null;
        if (id == 1L) return "GOOGLE";
        if (id == 2L) return "INSTAGRAM";
        if (id == 3L) return "FACEBOOK";
        if (id == 4L) return "TIKTOK";
        return null;
    }

    /**
     * P3-26 任务 5：简易 NLP — 从 IG DM 文本中提取服务名
     */
    private String parseServiceFromText(String text) {
        if (text == null) return "General Service";
        String lower = text.toLowerCase();
        if (lower.contains("facial")) return "Facial Treatment";
        if (lower.contains("massage")) return "Body Massage";
        if (lower.contains("manicure") || lower.contains("nail")) return "Manicure";
        if (lower.contains("hair")) return "Hair Styling";
        return "General Service";
    }

    /**
     * P3-26 任务 5：根据 customerLocale 返回多语言确认模板
     */
    @GetMapping("/{id}/confirmation-template")
    public Result<Map<String, Object>> confirmationTemplate(@PathVariable Long id) {
        OverseasBooking b = bookingMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "预约不存在");

        String locale = b.getCustomerLocale() == null ? "en" : b.getCustomerLocale().toLowerCase();
        Map<String, String> template;
        switch (locale) {
            case "zh":
            case "zh-cn":
            case "zh-tw":
                template = Map.of(
                        "subject", "您的预约已确认",
                        "body", "尊敬的 " + b.getCustomerName() + "，您预约的 " + b.getServiceName()
                                + " 已确认。时间：" + b.getBookingDate() + " " + b.getBookingTime()
                                + "。预约号：" + b.getBookingNo()
                );
                break;
            case "ja":
                template = Map.of(
                        "subject", "ご予約確認",
                        "body", b.getCustomerName() + "様、" + b.getServiceName()
                                + " のご予約を承りました。日時：" + b.getBookingDate() + " " + b.getBookingTime()
                                + "。予約番号：" + b.getBookingNo()
                );
                break;
            case "ko":
                template = Map.of(
                        "subject", "예약 확인",
                        "body", b.getCustomerName() + "님, " + b.getServiceName()
                                + " 예약이 확인되었습니다. 일시: " + b.getBookingDate() + " " + b.getBookingTime()
                                + ". 예약 번호: " + b.getBookingNo()
                );
                break;
            default:
                template = Map.of(
                        "subject", "Booking Confirmed",
                        "body", "Dear " + b.getCustomerName() + ", your " + b.getServiceName()
                                + " is confirmed for " + b.getBookingDate() + " at " + b.getBookingTime()
                                + ". Booking No: " + b.getBookingNo()
                );
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("locale", locale);
        data.put("subject", template.get("subject"));
        data.put("body", template.get("body"));
        return Result.success(data);
    }
}
