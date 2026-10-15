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

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-G P3：生态/国际化/AI 平台（最小骨架）
 *  - 微信小程序预约  /wx/booking/...
 *  - 美团/抖音对接   /platform/...
 *  - Open API        /open/...
 *  - IoT 设备集成    /iot/...
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class P3EcosystemService {

    private final WxMemberBindMapper wxMemberBindMapper;
    private final WxBookingRecordMapper wxBookingRecordMapper;
    private final WxAppConfigMapper wxAppConfigMapper;
    private final PlatformOrderMapper platformOrderMapper;
    private final PlatformDealMapper platformDealMapper;
    private final PlatformWebhookLogMapper platformWebhookLogMapper;
    private final OpenAppMapper openAppMapper;
    private final OpenAccessTokenMapper openAccessTokenMapper;
    private final OpenApiLogMapper openApiLogMapper;
    private final IotDeviceMapper iotDeviceMapper;
    private final IotDeviceEventMapper iotDeviceEventMapper;
    private final IotDeviceCommandMapper iotDeviceCommandMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final SecureRandom RNG = new SecureRandom();

    // ====================================================================
    // 1. 微信小程序预约
    // ====================================================================

    /**
     * 模拟 jscode2session：生产环境替换为 https://api.weixin.qq.com/sns/jscode2session
     * 这里直接用 code 生成稳定的 openid
     */
    private static String mockOpenId(String appid, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest((appid + ":" + code).getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", b[i]));
            return "wx_" + sb;
        } catch (Exception e) {
            return "wx_" + System.currentTimeMillis();
        }
    }

    /** 1.1 jscode2session：换 openid */
    @PostMapping("/wx/auth/session")
    public Result<Map<String, Object>> wxSession(@RequestBody Map<String, Object> body) {
        String appid = (String) body.get("appid");
        String code = (String) body.get("code");
        if (appid == null || code == null)
            return Result.error(BizCode.BAD_REQUEST, "appid / code 必填");
        String openid = mockOpenId(appid, code);
        Map<String, Object> data = new HashMap<>();
        data.put("openid", openid);
        data.put("sessionKey", "mock_sk_" + UUID.randomUUID().toString().substring(0, 16));
        data.put("unionid", null);
        return Result.success("OK", data);
    }

    /** 1.2 绑定小程序 openid 到会员 */
    @PostMapping("/wx/bind")
    @Transactional(rollbackFor = Exception.class)
    public Result<WxMemberBind> bindMember(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String appid = (String) body.get("appid");
        String openid = (String) body.get("openid");
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        if (appid == null || openid == null || memberId == null)
            return Result.error(BizCode.BAD_REQUEST, "appid / openid / memberId 必填");

        WxMemberBind exist = wxMemberBindMapper.selectOne(
                new LambdaQueryWrapper<WxMemberBind>()
                        .eq(WxMemberBind::getAppid, appid)
                        .eq(WxMemberBind::getOpenid, openid));
        if (exist != null) {
            jdbc.update(
                "UPDATE wx_member_bind SET member_id=?, nickname=?, avatar_url=?, last_login_at=NOW(), " +
                "       session_key=? WHERE id=?",
                memberId, body.get("nickname"), body.get("avatarUrl"),
                body.get("sessionKey"), exist.getId());
            return Result.success("已更新绑定", exist);
        }
        WxMemberBind b = new WxMemberBind();
        b.setTenantId(tenantId);
        b.setMemberId(memberId);
        b.setAppid(appid);
        b.setOpenid(openid);
        b.setNickname((String) body.get("nickname"));
        b.setAvatarUrl((String) body.get("avatarUrl"));
        b.setPhone((String) body.get("phone"));
        b.setSessionKey((String) body.get("sessionKey"));
        b.setBindAt(LocalDateTime.now());
        b.setLastLoginAt(LocalDateTime.now());
        b.setEnabled(1);
        wxMemberBindMapper.insert(b);
        return Result.success("已绑定", b);
    }

    /** 1.3 小程序端发起预约 */
    @PostMapping("/wx/booking/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<WxBookingRecord> createWxBooking(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String appid = (String) body.get("appid");
        String openid = (String) body.get("openid");
        Long storeId = body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue();
        Long serviceId = body.get("serviceId") == null ? null
                : ((Number) body.get("serviceId")).longValue();
        if (appid == null || openid == null || storeId == null)
            return Result.error(BizCode.BAD_REQUEST, "appid / openid / storeId 必填");

        // 通过绑定关系取 memberId
        Long memberId = jdbc.queryForObject(
            "SELECT member_id FROM wx_member_bind WHERE appid=? AND openid=? AND deleted=0 AND enabled=1",
            Long.class, appid, openid);

        WxBookingRecord r = new WxBookingRecord();
        r.setTenantId(tenantId);
        r.setAppid(appid);
        r.setOpenid(openid);
        r.setMemberId(memberId);
        r.setStoreId(storeId);
        r.setServiceId(serviceId);
        r.setServiceName((String) body.get("serviceName"));
        r.setEmployeeId(body.get("employeeId") == null ? null
                : ((Number) body.get("employeeId")).longValue());
        r.setEmployeeName((String) body.get("employeeName"));
        r.setBookingDate(LocalDate.parse((String) body.get("bookingDate")));
        r.setBookingTime(LocalDateTime.parse((String) body.get("bookingTime")));
        r.setDurationMinutes((Integer) body.getOrDefault("durationMinutes", 60));
        r.setContactPhone((String) body.get("contactPhone"));
        r.setStatus("PENDING");
        r.setSource("WX_MINI");
        wxBookingRecordMapper.insert(r);
        return Result.success("预约创建成功（待门店确认）", r);
    }

    /** 1.4 门店端确认预约 */
    @PostMapping("/wx/booking/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> confirmWxBooking(@PathVariable Long id) {
        WxBookingRecord r = wxBookingRecordMapper.selectById(id);
        if (r == null) return Result.error(BizCode.NOT_FOUND, "预约不存在");
        if (!"PENDING".equals(r.getStatus()))
            return Result.error(BizCode.CONFLICT, "当前状态不可确认");
        jdbc.update(
            "UPDATE wx_booking_record SET status='CONFIRMED', confirmed_at=NOW() WHERE id=?",
            id);
        return Result.success("已确认", null);
    }

    /** 1.5 会员查自己的预约 */
    @GetMapping("/wx/booking/list")
    public Result<Map<String, Object>> listWxBookings(
            @RequestParam String appid,
            @RequestParam String openid) {
        long total = wxBookingRecordMapper.selectCount(
                new LambdaQueryWrapper<WxBookingRecord>()
                        .eq(WxBookingRecord::getAppid, appid)
                        .eq(WxBookingRecord::getOpenid, openid)
                        .orderByDesc(WxBookingRecord::getId));
        List<WxBookingRecord> records = wxBookingRecordMapper.selectList(
                new LambdaQueryWrapper<WxBookingRecord>()
                        .eq(WxBookingRecord::getAppid, appid)
                        .eq(WxBookingRecord::getOpenid, openid)
                        .orderByDesc(WxBookingRecord::getId));
        Map<String, Object> p = new HashMap<>();
        p.put("total", total);
        p.put("records", records);
        return Result.success(p);
    }

    // ====================================================================
    // 2. 美团/抖音 第三方平台对接
    // ====================================================================

    /** 2.1 接收 webhook（订单创建/核销/退款） */
    @PostMapping("/platform/webhook/{platform}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> webhook(@PathVariable String platform,
                                                @RequestBody(required = false) String rawBody) {
        String body = rawBody == null ? "" : rawBody;
        String hash = sha256(body);

        // 幂等：相同 hash 不重复处理
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM platform_webhook_log WHERE payload_hash=?",
            Long.class, hash);
        if (dup != null && dup > 0) {
            return Result.success("已处理（幂等）", Map.of("duplicate", true));
        }
        jdbc.update(
            "INSERT INTO platform_webhook_log (platform, event_type, payload_hash, raw_body, processed) " +
            "VALUES (?, 'ORDER_CREATED', ?, ?, 1)",
            platform.toUpperCase(), hash, body);
        return Result.success("webhook 已记录", Map.of("processed", true, "hash", hash));
    }

    /** 2.2 核销第三方平台券（美团/抖音核销码） */
    @PostMapping("/platform/verify")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> verifyPlatform(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String code = (String) body.get("verificationCode");
        String platform = (String) body.getOrDefault("platform", "MEITUAN");
        Long storeId = body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue();
        if (code == null || storeId == null)
            return Result.error(BizCode.BAD_REQUEST, "verificationCode / storeId 必填");

        PlatformOrder order = platformOrderMapper.selectOne(
                new LambdaQueryWrapper<PlatformOrder>()
                        .eq(PlatformOrder::getPlatform, platform)
                        .eq(PlatformOrder::getVerificationCode, code)
                        .eq(PlatformOrder::getDeleted, 0));
        if (order == null) return Result.error(BizCode.NOT_FOUND, "核销码无效");
        if (!"PENDING".equals(order.getStatus()))
            return Result.error(BizCode.CONFLICT, "订单已被处理（" + order.getStatus() + "）");
        if (!order.getStoreId().equals(storeId))
            return Result.error(BizCode.CONFLICT, "核销码不归属本门店");

        jdbc.update(
            "UPDATE platform_order SET status='VERIFIED', verified_at=NOW() WHERE id=?",
            order.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("platformOrderId", order.getPlatformOrderId());
        data.put("dealName", order.getDealName());
        data.put("amount", order.getAmount());
        return Result.success("核销成功", data);
    }

    @GetMapping("/platform/order/page")
    public Result<Map<String, Object>> pagePlatformOrders(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PlatformOrder> qw = new LambdaQueryWrapper<>();
        qw.eq(PlatformOrder::getTenantId, tenantId);
        if (platform != null && !platform.isEmpty()) qw.eq(PlatformOrder::getPlatform, platform);
        if (status != null && !status.isEmpty()) qw.eq(PlatformOrder::getStatus, status);
        qw.orderByDesc(PlatformOrder::getId);
        long total = platformOrderMapper.selectCount(qw);
        List<PlatformOrder> records = platformOrderMapper.selectList(qw);
        Map<String, Object> p = new HashMap<>();
        p.put("total", total);
        p.put("records", records);
        p.put("current", pageNum);
        p.put("size", pageSize);
        return Result.success(p);
    }

    @GetMapping("/platform/stats")
    public Result<Map<String, Object>> platformStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT platform, " +
            "       COUNT(*) total, " +
            "       SUM(CASE WHEN status='VERIFIED' THEN 1 ELSE 0 END) verified, " +
            "       IFNULL(SUM(CASE WHEN status='VERIFIED' THEN amount ELSE 0 END),0) revenue, " +
            "       IFNULL(SUM(commission),0) total_commission " +
            "FROM platform_order WHERE deleted=0 AND tenant_id=? GROUP BY platform",
            tenantId);
        data.put("byPlatform", rows);
        return Result.success(data);
    }

    // ====================================================================
    // 3. Open API 开发者平台
    // ====================================================================

    /** 3.1 申请开放平台应用 */
    @PostMapping("/open/app")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> createApp(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String appCode = (String) body.get("appCode");
        String appName = (String) body.get("appName");
        if (appCode == null || appName == null)
            return Result.error(BizCode.BAD_REQUEST, "appCode / appName 必填");

        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM open_app WHERE deleted=0 AND tenant_id=? AND app_code=?",
            Long.class, tenantId, appCode);
        if (dup != null && dup > 0) return Result.error(BizCode.CONFLICT, "appCode 已存在");

        OpenApp app = new OpenApp();
        app.setTenantId(tenantId);
        app.setAppCode(appCode);
        app.setAppName(appName);
        app.setAppSecret(genSecret());  // 仅返回一次
        app.setAppType((String) body.getOrDefault("appType", "PARTNER"));
        app.setStatus("ACTIVE");
        app.setContactEmail((String) body.get("contactEmail"));
        app.setContactName((String) body.get("contactName"));
        app.setRateLimit((Integer) body.getOrDefault("rateLimit", 1000));
        app.setAllowedIps((String) body.get("allowedIps"));
        app.setScopes((String) body.getOrDefault("scopes", "member:read,booking:read"));
        app.setApprovedAt(LocalDateTime.now());
        app.setExpiresAt(LocalDateTime.now().plusYears(1));
        app.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        openAppMapper.insert(app);

        Map<String, Object> hint = new HashMap<>();
        hint.put("app", app);
        hint.put("warning", "appSecret 仅返回一次，请妥善保存");
        return Result.success(hint);
    }

    /** 3.2 OAuth2 client_credentials 颁发 token */
    @PostMapping("/open/oauth/token")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> issueToken(@RequestBody Map<String, Object> body) {
        String appCode = (String) body.get("appCode");
        String appSecret = (String) body.get("appSecret");
        if (appCode == null || appSecret == null)
            return Result.error(BizCode.BAD_REQUEST, "appCode / appSecret 必填");

        OpenApp app = openAppMapper.selectOne(
                new LambdaQueryWrapper<OpenApp>()
                        .eq(OpenApp::getAppCode, appCode));
        if (app == null) return Result.error(BizCode.UNAUTHORIZED, "appCode 不存在");
        if (!"ACTIVE".equals(app.getStatus()))
            return Result.error(BizCode.UNAUTHORIZED, "应用状态：" + app.getStatus());
        if (!appSecret.equals(app.getAppSecret()))
            return Result.error(BizCode.UNAUTHORIZED, "appSecret 错误");

        String token = "tk_" + UUID.randomUUID().toString().replace("-", "");
        String refresh = "rt_" + UUID.randomUUID().toString().replace("-", "");
        OpenAccessToken t = new OpenAccessToken();
        t.setAppId(app.getId());
        t.setAccessToken(token);
        t.setRefreshToken(refresh);
        t.setExpiresAt(LocalDateTime.now().plusHours(2));
        t.setRefreshExpiresAt(LocalDateTime.now().plusDays(30));
        t.setScopes(app.getScopes());
        t.setClientIp((String) body.getOrDefault("clientIp", "0.0.0.0"));
        openAccessTokenMapper.insert(t);

        Map<String, Object> data = new HashMap<>();
        data.put("access_token", token);
        data.put("refresh_token", refresh);
        data.put("token_type", "Bearer");
        data.put("expires_in", 7200);
        data.put("scopes", app.getScopes());
        return Result.success(data);
    }

    /** 3.3 校验 token（供 open api 模块内部调用） */
    public OpenApp verifyToken(String bearer) {
        if (bearer == null || !bearer.startsWith("Bearer "))
            throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "无效 token");
        String token = bearer.substring(7);
        OpenAccessToken t = openAccessTokenMapper.selectOne(
                new LambdaQueryWrapper<OpenAccessToken>()
                        .eq(OpenAccessToken::getAccessToken, token));
        if (t == null) throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "token 不存在");
        if (t.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new com.meiyetec.exception.BusinessException(BizCode.UNAUTHORIZED, "token 已过期");
        return openAppMapper.selectById(t.getAppId());
    }

    @GetMapping("/open/app/list")
    public Result<List<OpenApp>> listApps() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(openAppMapper.selectList(
                new LambdaQueryWrapper<OpenApp>()
                        .eq(OpenApp::getTenantId, tenantId)
                        .orderByDesc(OpenApp::getId)));
    }

    @PostMapping("/open/app/{id}/suspend")
    public Result<Void> suspendApp(@PathVariable Long id) {
        jdbc.update("UPDATE open_app SET status='SUSPENDED' WHERE id=?", id);
        return Result.success("已暂停", null);
    }

    /** 3.4 Open API 调用日志查询 */
    @GetMapping("/open/log/page")
    public Result<Map<String, Object>> pageOpenLogs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer httpStatus) {
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM open_api_log WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (path != null && !path.isEmpty()) { sql.append("AND path LIKE ? "); args.add("%" + path + "%"); }
        if (httpStatus != null) { sql.append("AND http_status=? "); args.add(httpStatus); }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        Map<String, Object> p = new HashMap<>();
        p.put("records", records);
        p.put("current", pageNum);
        p.put("size", pageSize);
        return Result.success(p);
    }

    // ====================================================================
    // 4. IoT 设备集成
    // ====================================================================

    /** 4.1 设备注册 */
    @PostMapping("/iot/device")
    @Transactional(rollbackFor = Exception.class)
    public Result<IotDevice> registerDevice(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String deviceSn = (String) body.get("deviceSn");
        String deviceType = (String) body.get("deviceType");
        if (deviceSn == null || deviceType == null)
            return Result.error(BizCode.BAD_REQUEST, "deviceSn / deviceType 必填");

        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM iot_device WHERE deleted=0 AND tenant_id=? AND device_sn=?",
            Long.class, tenantId, deviceSn);
        if (dup != null && dup > 0) return Result.error(BizCode.CONFLICT, "设备 SN 已注册");

        IotDevice d = new IotDevice();
        d.setTenantId(tenantId);
        d.setStoreId(body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue());
        d.setDeviceSn(deviceSn);
        d.setDeviceType(deviceType);
        d.setDeviceName((String) body.get("deviceName"));
        d.setVendor((String) body.get("vendor"));
        d.setModel((String) body.get("model"));
        d.setFirmware((String) body.get("firmware"));
        d.setMacAddress((String) body.get("macAddress"));
        d.setIpAddress((String) body.get("ipAddress"));
        d.setOnlineStatus("OFFLINE");
        d.setEnabled(1);
        iotDeviceMapper.insert(d);
        return Result.success("设备已注册", d);
    }

    /** 4.2 设备心跳上报 */
    @PostMapping("/iot/heartbeat")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> heartbeat(@RequestBody Map<String, Object> body) {
        String deviceSn = (String) body.get("deviceSn");
        if (deviceSn == null) return Result.error(BizCode.BAD_REQUEST, "deviceSn 必填");

        IotDevice d = iotDeviceMapper.selectOne(
                new LambdaQueryWrapper<IotDevice>()
                        .eq(IotDevice::getDeviceSn, deviceSn));
        if (d == null) return Result.error(BizCode.NOT_FOUND, "设备未注册");

        jdbc.update(
            "UPDATE iot_device SET online_status='ONLINE', last_heartbeat=NOW(), " +
            "       firmware=COALESCE(?, firmware) WHERE id=?",
            body.get("firmware"), d.getId());
        // 写心跳事件
        IotDeviceEvent ev = new IotDeviceEvent();
        ev.setTenantId(d.getTenantId());
        ev.setDeviceId(d.getId());
        ev.setDeviceSn(d.getDeviceSn());
        ev.setEventType("HEARTBEAT");
        ev.setSeverity("INFO");
        ev.setHandled(1);
        ev.setHandledAt(LocalDateTime.now());
        ev.setOccurredAt(LocalDateTime.now());
        iotDeviceEventMapper.insert(ev);
        return Result.success("心跳已记录", null);
    }

    /** 4.3 设备上行事件 */
    @PostMapping("/iot/event")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reportEvent(@RequestBody Map<String, Object> body) {
        String deviceSn = (String) body.get("deviceSn");
        String eventType = (String) body.get("eventType");
        if (deviceSn == null || eventType == null)
            return Result.error(BizCode.BAD_REQUEST, "deviceSn / eventType 必填");

        IotDevice d = iotDeviceMapper.selectOne(
                new LambdaQueryWrapper<IotDevice>()
                        .eq(IotDevice::getDeviceSn, deviceSn));
        if (d == null) return Result.error(BizCode.NOT_FOUND, "设备未注册");

        IotDeviceEvent ev = new IotDeviceEvent();
        ev.setTenantId(d.getTenantId());
        ev.setDeviceId(d.getId());
        ev.setDeviceSn(d.getDeviceSn());
        ev.setEventType(eventType);
        ev.setEventData(body.get("eventData") == null ? null : body.get("eventData").toString());
        ev.setSeverity((String) body.getOrDefault("severity", "INFO"));
        ev.setHandled(0);
        ev.setOccurredAt(LocalDateTime.now());
        iotDeviceEventMapper.insert(ev);
        return Result.success("事件已记录", null);
    }

    /** 4.4 下行指令（UNLOCK 等） */
    @PostMapping("/iot/command")
    @Transactional(rollbackFor = Exception.class)
    public Result<IotDeviceCommand> sendCommand(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long deviceId = body.get("deviceId") == null ? null
                : ((Number) body.get("deviceId")).longValue();
        String code = (String) body.get("commandCode");
        if (deviceId == null || code == null)
            return Result.error(BizCode.BAD_REQUEST, "deviceId / commandCode 必填");

        IotDevice d = iotDeviceMapper.selectById(deviceId);
        if (d == null) return Result.error(BizCode.NOT_FOUND, "设备不存在");
        if (!"ONLINE".equals(d.getOnlineStatus()))
            return Result.error(BizCode.CONFLICT, "设备离线");

        IotDeviceCommand cmd = new IotDeviceCommand();
        cmd.setTenantId(tenantId);
        cmd.setDeviceId(deviceId);
        cmd.setCommandCode(code);
        cmd.setPayload(body.get("payload") == null ? null : body.get("payload").toString());
        cmd.setStatus("PENDING");
        cmd.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        iotDeviceCommandMapper.insert(cmd);
        // 模拟发送（生产替换为 mqtt / tcp / websocket 下发）
        jdbc.update(
            "UPDATE iot_device_command SET status='SENT', sent_at=NOW() WHERE id=?",
            cmd.getId());
        return Result.success("指令已下发", cmd);
    }

    @GetMapping("/iot/event/recent")
    public Result<Map<String, Object>> recentEvents(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(defaultValue = "100") Integer limit) {
        LambdaQueryWrapper<IotDeviceEvent> qw = new LambdaQueryWrapper<>();
        if (deviceId != null) qw.eq(IotDeviceEvent::getDeviceId, deviceId);
        qw.orderByDesc(IotDeviceEvent::getOccurredAt).last("LIMIT " + limit);
        List<IotDeviceEvent> records = iotDeviceEventMapper.selectList(qw);
        Map<String, Object> p = new HashMap<>();
        p.put("records", records);
        return Result.success(p);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private static String genSecret() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", b[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}