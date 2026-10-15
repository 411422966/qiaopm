package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.config.KioskAlertProperties;
import com.meiyetec.entity.Booking;
import com.meiyetec.entity.KioskCheckin;
import com.meiyetec.entity.KioskDevice;
import com.meiyetec.entity.Member;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BookingMapper;
import com.meiyetec.mapper.KioskCheckinMapper;
import com.meiyetec.mapper.KioskDeviceMapper;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 自助签到 Kiosk（FEATURE_COMPARISON_MATRIX.md 253-264 P1-11）
 *
 * <p>完整功能链路：
 * <pre>
 *  扫码 → KioskDevice.checkin()
 *         ├── ① 设备存在且 ACTIVE
 *         ├── ② 幂等检查（device+booking 10min 内不重复）
 *         ├── ③ phone 自动匹配会员
 *         ├── ④ 更新 biz_booking.status = 3（已签到）
 *         └── ⑤ 写 kiosk_checkin
 *
 *  设备心跳 → POST /kiosk/device/{code}/heartbeat
 *              └── 更新 kiosk_device.last_heartbeat
 *
 *  KPI 看板 → GET /kiosk/stats
 *              └── 今日签到数 / 等待中 / 服务中 / 已完成 / 活跃设备数 / 平均等待时长
 *
 *  开始服务 → POST /kiosk/checkin/{id}/start-service
 *             └── 写 service_started_at + status = IN_SERVICE
 *
 *  结束服务 → POST /kiosk/checkin/{id}/finish-service
 *             └── 写 service_finished_at + staff_id + status = FINISHED
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/kiosk")
@RequiredArgsConstructor
public class KioskService {

    /** 幂等窗口（分钟）：同一设备 + 同一 bookingId 在此窗口内不重复签到 */
    private static final int IDEMPOTENCY_WINDOW_MINUTES = 10;

    private final KioskDeviceMapper deviceMapper;
    private final KioskCheckinMapper checkinMapper;
    private final MemberMapper memberMapper;
    private final BookingMapper bookingMapper;
    private final KioskAlertProperties alertProps;

    // ====================================================================
    // P0-1 · 设备心跳
    // ====================================================================

    /**
     * POST /kiosk/device/{code}/heartbeat
     *
     * <p>心跳间隔建议 ≤ 60s。超过 5 分钟无心跳视为设备离线。
     */
    @PostMapping("/device/{code}/heartbeat")
    public Result<KioskDevice> heartbeat(@PathVariable String code,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        KioskDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<KioskDevice>()
                .eq(KioskDevice::getTenantId, tenantId)
                .eq(KioskDevice::getDeviceCode, code));
        if (device == null) return Result.error(BizCode.NOT_FOUND, "设备未注册: " + code);

        device.setLastHeartbeat(LocalDateTime.now());
        // 可选：接收设备上报的 IP / 位置变化
        if (body != null) {
            if (body.containsKey("ipAddress")) device.setIpAddress(String.valueOf(body.get("ipAddress")));
            if (body.containsKey("location")) device.setLocation(String.valueOf(body.get("location")));
        }
        deviceMapper.updateById(device);
        log.debug("[Kiosk] heartbeat device={} tenant={}", code, tenantId);
        return Result.success("心跳正常", device);
    }

    // ====================================================================
    // P0-2 · KPI 统计
    // ====================================================================

    /**
     * GET /kiosk/stats
     *
     * <p>返回今日 Kiosk KPI：
     * <ul>
     *   <li>todayCheckins：今日总签到数（含 walk-in）</li>
     *   <li>waitingCount：等待中（CHECKED_IN，未开始服务）</li>
     *   <li>inServiceCount：服务中（IN_SERVICE）</li>
     *   <li>finishedCount：已完成（FINISHED）</li>
     *   <li>avgWaitMinutes：平均等待时长（分钟），仅包含已完成的记录</li>
     *   <li>activeDevices：活跃设备数（5min 内有心跳）</li>
     *   <li>totalDevices：设备总数</li>
     *   <li>statusBreakdown：各状态分布</li>
     * </ul>
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        // 查询今日所有签到
        List<KioskCheckin> today = checkinMapper.selectList(new LambdaQueryWrapper<KioskCheckin>()
                .eq(KioskCheckin::getTenantId, tenantId)
                .ge(KioskCheckin::getCheckinTime, todayStart));

        long todayCheckins = today.size();
        long waiting = today.stream().filter(c -> "CHECKED_IN".equals(c.getStatus())).count();
        long inService = today.stream().filter(c -> "IN_SERVICE".equals(c.getStatus())).count();
        long finished = today.stream().filter(c -> "FINISHED".equals(c.getStatus())).count();

        // 平均等待时长：service_started_at - arrived_at（仅 FINISHED）
        double avgWaitMinutes = today.stream()
                .filter(c -> "FINISHED".equals(c.getStatus())
                        && c.getServiceStartedAt() != null && c.getArrivedAt() != null)
                .mapToLong(c -> ChronoUnit.MINUTES.between(c.getArrivedAt(), c.getServiceStartedAt()))
                .average().orElse(0.0);

        // 活跃设备（5min 内有心跳）
        LocalDateTime fiveMinAgo = now.minusMinutes(5);
        List<KioskDevice> allDevices = deviceMapper.selectList(new LambdaQueryWrapper<KioskDevice>()
                .eq(KioskDevice::getTenantId, tenantId));
        long activeDevices = allDevices.stream()
                .filter(d -> d.getLastHeartbeat() != null
                        && d.getLastHeartbeat().isAfter(fiveMinAgo))
                .count();

        // 设备状态分布
        Map<String, Long> deviceStatusBreakdown = new LinkedHashMap<>();
        deviceStatusBreakdown.put("ACTIVE", allDevices.stream().filter(d -> "ACTIVE".equals(d.getStatus())).count());
        deviceStatusBreakdown.put("INACTIVE", allDevices.stream().filter(d -> "INACTIVE".equals(d.getStatus())).count());
        deviceStatusBreakdown.put("OFFLINE", allDevices.stream().filter(d -> {
            if (d.getStatus() != null) return false;
            return d.getLastHeartbeat() == null || d.getLastHeartbeat().isBefore(fiveMinAgo);
        }).count());

        // 签到状态分布
        Map<String, Long> checkinStatusBreakdown = new LinkedHashMap<>();
        checkinStatusBreakdown.put("CHECKED_IN", waiting);
        checkinStatusBreakdown.put("IN_SERVICE", inService);
        checkinStatusBreakdown.put("FINISHED", finished);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("todayCheckins", todayCheckins);
        stats.put("waitingCount", waiting);
        stats.put("inServiceCount", inService);
        stats.put("finishedCount", finished);
        stats.put("avgWaitMinutes", Math.round(avgWaitMinutes * 10.0) / 10.0);  // 保留 1 位小数
        stats.put("activeDevices", activeDevices);
        stats.put("totalDevices", allDevices.size());
        stats.put("deviceStatusBreakdown", deviceStatusBreakdown);
        stats.put("checkinStatusBreakdown", checkinStatusBreakdown);
        stats.put("timestamp", now);

        log.info("[Kiosk] stats tenant={} today={} waiting={} inService={} finished={}",
                tenantId, todayCheckins, waiting, inService, finished);

        return Result.success(stats);
    }

    // ====================================================================
    // 设备 CRUD（保留原有接口）
    // ====================================================================

    @PostMapping("/device")
    @Transactional(rollbackFor = Exception.class)
    public Result<KioskDevice> registerDevice(@RequestBody KioskDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getDeviceCode() == null) return Result.error(BizCode.BAD_REQUEST, "deviceCode 必填");
        if (d.getStatus() == null) d.setStatus("ACTIVE");
        if (d.getLocation() == null) d.setLocation("门店门口");
        d.setTenantId(tenantId);
        d.setId(null);
        deviceMapper.insert(d);
        return Result.success("已注册", d);
    }

    @GetMapping("/device/list")
    public Result<List<KioskDevice>> listDevices() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(deviceMapper.selectList(new LambdaQueryWrapper<KioskDevice>()
                .eq(KioskDevice::getTenantId, tenantId)
                .orderByDesc(KioskDevice::getId)));
    }

    @PostMapping("/device/{id}")
    public Result<KioskDevice> updateDevice(@PathVariable Long id, @RequestBody KioskDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        KioskDevice exist = deviceMapper.selectById(id);
        if (exist == null || !exist.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "设备不存在");
        d.setId(id);
        d.setTenantId(tenantId);
        deviceMapper.updateById(d);
        return Result.success("已更新", deviceMapper.selectById(id));
    }

    // ====================================================================
    // P1-1 · 签到增强（幂等 + phone 匹配会员 + 更新预约状态）
    // ====================================================================

    /**
     * POST /kiosk/checkin
     *
     * <p>增强版签到：
     * <ol>
     *   <li>设备 ACTIVE 校验</li>
     *   <li>幂等：同 deviceCode + bookingId 在 IDEMPOTENCY_WINDOW_MINUTES 内不重复</li>
     *   <li>phone 自动匹配会员（memberId 为空时尝试）</li>
     *   <li>更新关联 booking.status → 3（已签到）</li>
     *   <li>记录 arrived_at</li>
     * </ol>
     *
     * <p>请求体：
     * <pre>
     * { deviceCode, bookingId?, memberId?, phone?, checkinMethod?: "QRCODE"|"MANUAL"|"WALK_IN" }
     * </pre>
     */
    @PostMapping("/checkin")
    @Transactional(rollbackFor = Exception.class)
    public Result<KioskCheckin> checkin(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String deviceCode = (String) body.get("deviceCode");
        if (deviceCode == null) return Result.error(BizCode.BAD_REQUEST, "deviceCode 必填");

        // ① 设备校验
        KioskDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<KioskDevice>()
                .eq(KioskDevice::getTenantId, tenantId)
                .eq(KioskDevice::getDeviceCode, deviceCode));
        if (device == null) return Result.error(BizCode.NOT_FOUND, "设备未注册: " + deviceCode);
        if (!"ACTIVE".equals(device.getStatus())) return Result.error(BizCode.BAD_REQUEST, "设备已停用");

        Long bookingId = body.get("bookingId") == null ? null : ((Number) body.get("bookingId")).longValue();

        // ② 幂等检查
        if (bookingId != null) {
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(IDEMPOTENCY_WINDOW_MINUTES);
            Long existing = checkinMapper.selectCount(new LambdaQueryWrapper<KioskCheckin>()
                    .eq(KioskCheckin::getTenantId, tenantId)
                    .eq(KioskCheckin::getDeviceId, device.getId())
                    .eq(KioskCheckin::getBookingId, bookingId)
                    .ge(KioskCheckin::getCheckinTime, windowStart));
            if (existing > 0) {
                log.info("[Kiosk] idempotent checkin skipped device={} bookingId={}", deviceCode, bookingId);
                return Result.error(BizCode.BAD_REQUEST,
                        "该预约已在 " + IDEMPOTENCY_WINDOW_MINUTES + " 分钟内签到过，无需重复签到");
            }
        }

        // ③ phone 自动匹配会员
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        if (memberId == null && body.containsKey("phone")) {
            String phone = String.valueOf(body.get("phone"));
            Member member = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
                    .eq(Member::getTenantId, tenantId)
                    .eq(Member::getPhone, phone));
            if (member != null) {
                memberId = member.getId();
                log.info("[Kiosk] phone={} matched memberId={}", phone, memberId);
            } else {
                log.debug("[Kiosk] phone={} no member found, proceed as walk-in", phone);
            }
        }

        // ④ 更新预约状态
        if (bookingId != null) {
            Booking booking = bookingMapper.selectById(bookingId);
            if (booking != null && booking.getTenantId().equals(tenantId)) {
                booking.setStatus(3);  // 3=已签到
                booking.setCheckinTime(LocalDateTime.now());
                bookingMapper.updateById(booking);
                log.info("[Kiosk] booking {} status → 3 CHECKED_IN", bookingId);
            } else {
                log.warn("[Kiosk] bookingId={} not found or wrong tenant", bookingId);
            }
        }

        // ⑤ 写入签到记录
        KioskCheckin c = new KioskCheckin();
        c.setTenantId(tenantId);
        c.setDeviceId(device.getId());
        c.setMemberId(memberId);
        c.setBookingId(bookingId);
        c.setStoreId(device.getStoreId());
        c.setCheckinMethod((String) body.getOrDefault("checkinMethod", "QRCODE"));
        c.setCheckinTime(LocalDateTime.now());
        c.setArrivedAt(LocalDateTime.now());
        c.setStatus("CHECKED_IN");
        if (body.containsKey("notes")) c.setNotes(String.valueOf(body.get("notes")));
        checkinMapper.insert(c);

        log.info("[Kiosk] checkin OK device={} checkinId={} memberId={} bookingId={}",
                deviceCode, c.getId(), memberId, bookingId);

        // 携带会员信息返回
        if (memberId != null) {
            Member m = memberMapper.selectById(memberId);
            if (m != null) c.setNotes(m.getName() + " " + m.getPhone());
        }

        return Result.success("签到成功", c);
    }

    // ====================================================================
    // P1-2 · 开始服务
    // ====================================================================

    /**
     * POST /kiosk/checkin/{id}/start-service
     *
     * <p>接待员点「开始服务」时调用。
     * 仅允许状态为 CHECKED_IN 的记录。
     */
    @PostMapping("/checkin/{id}/start-service")
    @Transactional(rollbackFor = Exception.class)
    public Result<KioskCheckin> startService(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        KioskCheckin c = checkinMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "签到记录不存在");
        if (!"CHECKED_IN".equals(c.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "当前状态为 " + c.getStatus() + "，无法开始服务");

        c.setStatus("IN_SERVICE");
        c.setServiceStartedAt(LocalDateTime.now());
        if (body != null && body.containsKey("staffId")) {
            c.setStaffId(((Number) body.get("staffId")).longValue());
        }
        checkinMapper.updateById(c);

        log.info("[Kiosk] start-service checkinId={} staffId={}", id, c.getStaffId());
        return Result.success("服务已开始", c);
    }

    // ====================================================================
    // P1-3 · 结束服务
    // ====================================================================

    /**
     * POST /kiosk/checkin/{id}/finish-service
     *
     * <p>接待员点「结束服务」时调用。
     * 仅允许状态为 IN_SERVICE 的记录。
     */
    @PostMapping("/checkin/{id}/finish-service")
    @Transactional(rollbackFor = Exception.class)
    public Result<KioskCheckin> finishService(@PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        KioskCheckin c = checkinMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "签到记录不存在");
        if (!"IN_SERVICE".equals(c.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "当前状态为 " + c.getStatus() + "，无法结束服务");

        c.setStatus("FINISHED");
        c.setServiceFinishedAt(LocalDateTime.now());
        if (body != null) {
            if (body.containsKey("staffId")) {
                c.setStaffId(((Number) body.get("staffId")).longValue());
            }
            if (body.containsKey("notes")) {
                c.setNotes(String.valueOf(body.get("notes")));
            }
        }
        checkinMapper.updateById(c);

        // 同时更新预约状态 → 4（已完成）
        if (c.getBookingId() != null) {
            Booking booking = bookingMapper.selectById(c.getBookingId());
            if (booking != null && booking.getTenantId().equals(tenantId)) {
                booking.setStatus(4);  // 4=已完成
                booking.setCompleteTime(LocalDateTime.now());
                bookingMapper.updateById(booking);
                log.info("[Kiosk] booking {} status → 4 FINISHED", c.getBookingId());
            }
        }

        log.info("[Kiosk] finish-service checkinId={} waitMinutes={}",
                id, ChronoUnit.MINUTES.between(c.getArrivedAt(), c.getServiceFinishedAt()));

        return Result.success("服务已完成", c);
    }

    // ====================================================================
    // 签到列表（保留原有接口）
    // ====================================================================

    @GetMapping("/checkin/list")
    public Result<List<KioskCheckin>> listCheckins(@RequestParam(required = false) String deviceCode,
                                                  @RequestParam(required = false) String date,
                                                  @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<KioskCheckin> qw = new LambdaQueryWrapper<KioskCheckin>()
                .eq(KioskCheckin::getTenantId, tenantId)
                .orderByDesc(KioskCheckin::getId);
        if (deviceCode != null && !deviceCode.isEmpty()) {
            KioskDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<KioskDevice>()
                    .eq(KioskDevice::getTenantId, tenantId)
                    .eq(KioskDevice::getDeviceCode, deviceCode));
            if (device != null) qw.eq(KioskCheckin::getDeviceId, device.getId());
        }
        if (date != null && !date.isEmpty())
            qw.ge(KioskCheckin::getCheckinTime, LocalDate.parse(date).atStartOfDay());
        if (status != null && !status.isEmpty())
            qw.eq(KioskCheckin::getStatus, status);
        return Result.success(checkinMapper.selectList(qw));
    }

    // ====================================================================
    // 签到详情
    // ====================================================================

    @GetMapping("/checkin/{id}")
    public Result<KioskCheckin> getCheckin(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        KioskCheckin c = checkinMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "签到记录不存在");
        return Result.success(c);
    }

    // ====================================================================
    // P2 · Walk-in 散客签到
    // ====================================================================

    /** Walk-in 散客签到幂等窗口（秒）：同设备 + 同手机号短时间内不重复建单 */
    private static final long WALK_IN_IDEMPOTENCY_SECONDS = 60;

    /**
     * POST /kiosk/checkin/walk-in
     *
     * <p>无预约到店（散客）链路：
     * <ol>
     *   <li>设备 ACTIVE 校验</li>
     *   <li>解析会员：memberId 优先 → 否则按 phone 自动匹配</li>
     *   <li>幂等：同设备 + 同 memberId 在 60 秒内不重复建 booking</li>
     *   <li>创建 booking（status=3 已签到 / source=4 Kiosk 散客）</li>
     *   <li>自动写 kiosk_checkin 记录</li>
     * </ol>
     *
     * <p>请求体：
     * <pre>
     * {
     *   "deviceCode": "KIOSK-01",
     *   "memberId":   123,                 // 二选一
     *   "phone":      "13800138000",       // 二选一（mobile 即可匹配）
     *   "serviceItem":"基础护理",          // 服务项目名（必填）
     *   "employeeId": 5,                   // 技师（可选）
     *   "notes":      "首次到店"
     * }
     * </pre>
     *
     * <p>响应：
     * <pre>
     * {
     *   "checkinId":   88,
     *   "bookingId":   1001,
     *   "bookingNo":   "KW20260906...",
     *   "memberId":    123,
     *   "memberName":  "张三",
     *   "memberPhone": "138****8000",
     *   "serviceItem": "基础护理",
     *   "walkIn":      true
     * }
     * </pre>
     */
    @PostMapping("/checkin/walk-in")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> walkInCheckin(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();

        // ----- ① 设备校验
        String deviceCode = (String) body.get("deviceCode");
        if (deviceCode == null || deviceCode.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "deviceCode 必填");
        KioskDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<KioskDevice>()
                .eq(KioskDevice::getTenantId, tenantId)
                .eq(KioskDevice::getDeviceCode, deviceCode));
        if (device == null) return Result.error(BizCode.NOT_FOUND, "设备未注册: " + deviceCode);
        if (!"ACTIVE".equals(device.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "设备已停用，无法签到");

        // ----- ② 解析会员
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String phone  = body.get("phone") == null ? null : String.valueOf(body.get("phone")).trim();
        Member member = null;
        if (memberId != null) {
            member = memberMapper.selectById(memberId);
            if (member != null && !member.getTenantId().equals(tenantId)) member = null;
        }
        if (member == null && phone != null && !phone.isEmpty()) {
            member = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
                    .eq(Member::getTenantId, tenantId)
                    .eq(Member::getPhone, phone));
        }
        if (member == null)
            return Result.error(BizCode.NOT_FOUND,
                    "未匹配到会员（请先录入会员信息或扫会员码）");
        memberId = member.getId();

        // ----- ③ 解析 serviceItem
        String serviceItem = body.get("serviceItem") == null ? null : String.valueOf(body.get("serviceItem")).trim();
        if (serviceItem == null || serviceItem.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "serviceItem 必填");

        // ----- ④ 60 秒幂等：同设备 + 同 memberId 不重复建 booking
        if (checkinMapper.selectCount(new LambdaQueryWrapper<KioskCheckin>()
                .eq(KioskCheckin::getTenantId, tenantId)
                .eq(KioskCheckin::getDeviceId, device.getId())
                .eq(KioskCheckin::getMemberId, memberId)
                .ge(KioskCheckin::getCheckinTime, LocalDateTime.now().minusSeconds(WALK_IN_IDEMPOTENCY_SECONDS))) > 0) {
            log.info("[Kiosk] walk-in idempotent skipped device={} memberId={}", deviceCode, memberId);
            return Result.error(BizCode.BAD_REQUEST,
                    "该会员在 " + WALK_IN_IDEMPOTENCY_SECONDS + " 秒内已到店签到，请勿重复操作");
        }

        // ----- ⑤ 创建 booking（散客 / source=4 / status=3 已签到）
        Long employeeId = body.get("employeeId") == null ? null : ((Number) body.get("employeeId")).longValue();
        LocalDateTime now = LocalDateTime.now();

        Booking booking = new Booking();
        booking.setTenantId(tenantId);
        booking.setBookingNo(generateWalkInBookingNo());
        booking.setMemberId(memberId);
        booking.setMemberName(member.getName());
        booking.setMemberPhone(member.getPhone());
        booking.setEmployeeId(employeeId);
        booking.setServiceItem(serviceItem);
        booking.setBookingDate(LocalDate.now());
        booking.setStartTime(now);
        booking.setStatus(3);                              // 3 = 已签到
        booking.setSource(4);                              // 4 = Kiosk walk-in 散客
        booking.setCheckinTime(now);
        if (body.containsKey("notes")) booking.setRemark(String.valueOf(body.get("notes")));
        booking.setCreateTime(now);
        booking.setUpdateTime(now);
        bookingMapper.insert(booking);

        log.info("[Kiosk] walk-in booking created bookingId={} bookingNo={} memberId={}",
                booking.getId(), booking.getBookingNo(), memberId);

        // ----- ⑥ 写 kiosk_checkin
        KioskCheckin c = new KioskCheckin();
        c.setTenantId(tenantId);
        c.setDeviceId(device.getId());
        c.setMemberId(memberId);
        c.setBookingId(booking.getId());
        c.setStoreId(device.getStoreId());
        c.setCheckinMethod("WALK_IN");
        c.setCheckinTime(now);
        c.setArrivedAt(now);
        c.setStatus("CHECKED_IN");
        if (body.containsKey("notes")) c.setNotes(String.valueOf(body.get("notes")));
        checkinMapper.insert(c);

        log.info("[Kiosk] walk-in checkin OK device={} checkinId={} bookingId={} memberId={}",
                deviceCode, c.getId(), booking.getId(), memberId);

        // ----- ⑦ 响应聚合
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkinId",   c.getId());
        result.put("bookingId",   booking.getId());
        result.put("bookingNo",   booking.getBookingNo());
        result.put("memberId",    memberId);
        result.put("memberName",  member.getName());
        result.put("memberPhone", maskPhone(member.getPhone()));
        result.put("serviceItem", serviceItem);
        result.put("walkIn",      true);

        return Result.success("Walk-in 签到成功", result);
    }

    /**
     * 生成长格式 booking 编号：`KW + yyyyMMddHHmmss + 3 位随机`
     * KW = Kiosk Walk-in
     */
    private String generateWalkInBookingNo() {
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String ts = LocalDateTime.now().format(fmt);
        int rand = new java.util.Random().nextInt(900) + 100;     // 100-999
        return "KW" + ts + rand;
    }

    /** 手机号脱敏：保留前 3 + 后 4，中间用 * 替代 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    // ====================================================================
    // P2-分析 · 24 小时签到分布
    // ====================================================================

    /**
     * GET /kiosk/stats/hourly?date=yyyy-MM-dd
     *
     * <p>返回 24 个时间桶（00..23）的签到数。
     * <p>date 缺省 → 当天
     */
    @GetMapping("/stats/hourly")
    public Result<List<Map<String, Object>>> hourlyStats(
            @RequestParam(required = false) String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate targetDate = (date == null || date.isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(date);
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        List<KioskCheckin> day = checkinMapper.selectList(new LambdaQueryWrapper<KioskCheckin>()
                .eq(KioskCheckin::getTenantId, tenantId)
                .ge(KioskCheckin::getCheckinTime, start)
                .lt(KioskCheckin::getCheckinTime, end));

        // 24 桶统计
        long[] byHour = new long[24];
        long[] finishedByHour = new long[24];
        for (KioskCheckin c : day) {
            int h = c.getCheckinTime().getHour();
            byHour[h]++;
            if ("FINISHED".equals(c.getStatus())) finishedByHour[h]++;
        }

        List<Map<String, Object>> buckets = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("hour",      h);
            b.put("label",     String.format("%02d:00", h));
            b.put("count",     byHour[h]);
            b.put("finished",  finishedByHour[h]);
            buckets.add(b);
        }
        return Result.success(buckets);
    }

    // ====================================================================
    // P2-分析 · 技师绩效对比
    // ====================================================================

    /**
     * GET /kiosk/stats/staff?date=yyyy-MM-dd
     *
     * <p>按技师聚合今日服务数据：服务总数 / 已完成 / 完成率 / 平均等待时长。
     * <p>排序：服务总数 desc。
     */
    @GetMapping("/stats/staff")
    public Result<List<Map<String, Object>>> staffStats(
            @RequestParam(required = false) String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate targetDate = (date == null || date.isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(date);
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        // 取出当日所有有 staffId 的签到
        List<KioskCheckin> day = checkinMapper.selectList(new LambdaQueryWrapper<KioskCheckin>()
                .eq(KioskCheckin::getTenantId, tenantId)
                .ge(KioskCheckin::getCheckinTime, start)
                .lt(KioskCheckin::getCheckinTime, end)
                .isNotNull(KioskCheckin::getStaffId));

        Map<Long, long[]> agg = new LinkedHashMap<>();   // staffId -> [total, finished, waitSumMin, waitCount]
        for (KioskCheckin c : day) {
            long[] a = agg.computeIfAbsent(c.getStaffId(), k -> new long[4]);
            a[0]++;
            if ("FINISHED".equals(c.getStatus())) {
                a[1]++;
                if (c.getServiceStartedAt() != null && c.getArrivedAt() != null) {
                    a[2] += ChronoUnit.MINUTES.between(c.getArrivedAt(), c.getServiceStartedAt());
                    a[3]++;
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, long[]> e : agg.entrySet()) {
            long[] a = e.getValue();
            long total = a[0], finished = a[1], waitSum = a[2], waitCnt = a[3];
            double avgWait = waitCnt > 0 ? (double) waitSum / waitCnt : 0.0;
            double finishRate = total > 0 ? Math.round((double) finished * 1000.0 / total) / 10.0 : 0.0;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("staffId",         e.getKey());
            row.put("total",           total);
            row.put("finished",        finished);
            row.put("finishRate",      finishRate);
            row.put("avgWaitMinutes",  Math.round(avgWait * 10.0) / 10.0);
            rows.add(row);
        }
        rows.sort((a, b) -> Long.compare((long) b.get("total"), (long) a.get("total")));
        return Result.success(rows);
    }

    // ====================================================================
    // P2-分析 · 离线告警
    // ====================================================================

    /**
     * GET /kiosk/alerts/offline
     *
     * <p>返回当前所有 OFFLINE 状态的设备（含：最后心跳时间、心跳年龄、离线分钟数、严重度）。
     * <p>严重度阈值从 {@code kiosk.alert.*} 配置读取（application.yml），无需改代码。
     */
    @GetMapping("/alerts/offline")
    public Result<List<Map<String, Object>>> offlineAlerts() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int infoMin     = alertProps.getInfoMinutes();
        int criticalMin = alertProps.getCriticalMinutes();
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(infoMin);

        List<KioskDevice> all = deviceMapper.selectList(new LambdaQueryWrapper<KioskDevice>()
                .eq(KioskDevice::getTenantId, tenantId));

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (KioskDevice d : all) {
            // 显式 INACTIVE 不告警；ACTIVE 但无心跳/超时 → 告警
            if (!"ACTIVE".equals(d.getStatus())) continue;
            if (d.getLastHeartbeat() != null && d.getLastHeartbeat().isAfter(threshold)) continue;

            long offlineMin;
            if (d.getLastHeartbeat() == null) {
                offlineMin = -1;   // 从未心跳
            } else {
                offlineMin = ChronoUnit.MINUTES.between(d.getLastHeartbeat(), LocalDateTime.now());
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id",                d.getId());
            row.put("deviceCode",        d.getDeviceCode());
            row.put("deviceName",        d.getDeviceName());
            row.put("location",          d.getLocation());
            row.put("lastHeartbeat",     d.getLastHeartbeat());
            row.put("offlineMinutes",    offlineMin);
            row.put("severity",          alertProps.severityOf(offlineMin));
            row.put("thresholdInfoMin",  infoMin);
            row.put("thresholdWarnMin",  alertProps.getWarnMinutes());
            row.put("thresholdCriticalMin", criticalMin);
            alerts.add(row);
        }
        // 严重度降序：CRITICAL → WARN → INFO → NEVER
        alerts.sort((a, b) -> Integer.compare(
                severityRank((String) b.get("severity")),
                severityRank((String) a.get("severity"))));
        return Result.success(alerts);
    }

    private static int severityRank(String s) {
        switch (s) {
            case "CRITICAL": return 3;
            case "WARN":     return 2;
            case "INFO":     return 1;
            case "NEVER":    return 0;
            default:         return -1;
        }
    }

    // ====================================================================
    // P2-分析 · 客流目标线 + 客流对比配置
    // ====================================================================

    /**
     * GET /kiosk/stats/daily-target?date=YYYY-MM-DD
     *
     * <p>返回指定日期的客流日均目标（per-day 总数）。
     * <p>当前实现：取 {@code kiosk.alert.daily-target-default} 作为兜底；
     * 可扩展为按租户配置 / 周一至周日差异化。
     */
    @GetMapping("/stats/daily-target")
    public Result<Map<String, Object>> dailyTarget(
            @RequestParam(required = false) String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate targetDate = (date == null || date.isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(date);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", targetDate);
        data.put("tenantId", tenantId);
        data.put("target", alertProps.getDailyTargetDefault());
        data.put("source", "kiosk.alert.daily-target-default");
        return Result.success(data);
    }

    /**
     * GET /kiosk/stats/daily-target POST（更新）
     * body: { "target": 120 }
     *
     * <p>当前简化实现：仅在内存 + 日志中保存。
     * <p>生产建议：扩展为 {@code kiosk_daily_target(tenant_id, date, target)} 表。
     */
    @PostMapping("/stats/daily-target")
    public Result<Map<String, Object>> updateDailyTarget(@RequestBody Map<String, Object> body) {
        Object t = body == null ? null : body.get("target");
        if (t == null) return Result.error(BizCode.BAD_REQUEST, "target 必填");
        int target = t instanceof Number ? ((Number) t).intValue() : Integer.parseInt(String.valueOf(t));
        if (target <= 0 || target > 10_000) return Result.error(BizCode.BAD_REQUEST, "target 超出范围 (1..10000)");
        alertProps.setDailyTargetDefault(target);
        log.warn("[Kiosk] daily-target-default updated to {}（仅在 JVM 内存生效，重启恢复）", target);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", target);
        data.put("scope", "jvm-memory-only");
        return Result.success("目标已更新（仅本实例）", data);
    }

    // ====================================================================
    // P2-分析 · 技师周/月趋势
    // ====================================================================

    /**
     * GET /kiosk/stats/staff-trend
     *
     * <p>查询参数：
     * <ul>
     *   <li>{@code staffIds}：逗号分隔的技师 ID（必填；上限 8 人）</li>
     *   <li>{@code period}：{@code week|month}（默认 week）</li>
     *   <li>{@code endDate}：截止日期（含），默认今天</li>
     * </ul>
     *
     * <p>响应（每天一行 × N 个技师 × period 天）：
     * <pre>
     * {
     *   period: "week",
     *   fromDate: "2026-09-01",
     *   toDate:   "2026-09-07",
     *   staffIds: [1, 2, 3],
     *   rows: [
     *     { date: "2026-09-01", total: 8, finished: 7, finishRate: 87.5,
     *       perStaff: [
     *         { staffId: 1, total: 3, finished: 3, avgWaitMinutes: 4.5 },
     *         { staffId: 2, total: 5, finished: 4, avgWaitMinutes: 6.0 }
     *       ]},
     *     ...
     *   ]
     * }
     * </pre>
     */
    @GetMapping("/stats/staff-trend")
    public Result<Map<String, Object>> staffTrend(
            @RequestParam String staffIds,
            @RequestParam(required = false, defaultValue = "week") String period,
            @RequestParam(required = false) String endDate) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate end = (endDate == null || endDate.isEmpty())
                ? LocalDate.now()
                : LocalDate.parse(endDate);
        int days = "month".equalsIgnoreCase(period) ? 30 : 7;
        LocalDate start = end.minusDays(days - 1);

        List<Long> ids = new ArrayList<>();
        for (String s : staffIds.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            try { ids.add(Long.parseLong(t)); } catch (NumberFormatException ignored) {}
        }
        if (ids.isEmpty()) return Result.error(BizCode.BAD_REQUEST, "staffIds 不能为空");
        if (ids.size() > 8) return Result.error(BizCode.BAD_REQUEST, "最多 8 位技师");

        // 取整段时间内所有有 staffId 的签到
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt   = end.plusDays(1).atStartOfDay();
        List<KioskCheckin> all = checkinMapper.selectList(new LambdaQueryWrapper<KioskCheckin>()
                .eq(KioskCheckin::getTenantId, tenantId)
                .ge(KioskCheckin::getCheckinTime, startDt)
                .lt(KioskCheckin::getCheckinTime, endDt)
                .in(KioskCheckin::getStaffId, ids));

        // 按 (date, staffId) 聚合
        Map<String, Map<Long, long[]>> agg = new TreeMap<>();   // date → staffId → [total, finished, waitSum, waitCnt]
        for (KioskCheckin c : all) {
            LocalDate d = c.getCheckinTime().toLocalDate();
            String key = d.toString();
            Map<Long, long[]> inner = agg.computeIfAbsent(key, k -> new HashMap<>());
            long[] a = inner.computeIfAbsent(c.getStaffId(), k -> new long[4]);
            a[0]++;
            if ("FINISHED".equals(c.getStatus())) {
                a[1]++;
                if (c.getServiceStartedAt() != null && c.getArrivedAt() != null) {
                    a[2] += ChronoUnit.MINUTES.between(c.getArrivedAt(), c.getServiceStartedAt());
                    a[3]++;
                }
            }
        }

        // 装桶：days × staffIds
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<Long, long[]> inner = agg.getOrDefault(d.toString(), Collections.emptyMap());
            long total = 0, finished = 0;
            List<Map<String, Object>> perStaff = new ArrayList<>();
            for (Long sid : ids) {
                long[] a = inner.get(sid);
                long t = a == null ? 0 : a[0];
                long f = a == null ? 0 : a[1];
                long ws = a == null ? 0 : a[2];
                long wc = a == null ? 0 : a[3];
                double avg = wc > 0 ? (double) ws / wc : 0.0;
                total += t; finished += f;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("staffId",         sid);
                row.put("total",           t);
                row.put("finished",        f);
                row.put("avgWaitMinutes",  Math.round(avg * 10.0) / 10.0);
                perStaff.add(row);
            }
            double rate = total > 0 ? Math.round((double) finished * 1000.0 / total) / 10.0 : 0.0;
            Map<String, Object> dayRow = new LinkedHashMap<>();
            dayRow.put("date",         d);
            dayRow.put("total",        total);
            dayRow.put("finished",     finished);
            dayRow.put("finishRate",   rate);
            dayRow.put("perStaff",     perStaff);
            rows.add(dayRow);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period",   period);
        result.put("fromDate", start);
        result.put("toDate",   end);
        result.put("staffIds", ids);
        result.put("rows",     rows);
        return Result.success(result);
    }
}

