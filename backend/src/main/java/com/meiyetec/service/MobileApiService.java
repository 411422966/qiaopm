package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MobileDevice;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MobileDeviceMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.4：移动端精简 API
 *
 * <p>为小程序/H5 提供精简响应（低带宽、聚合字段），与 PC 端 API 解耦。
 *
 * <p>设计原则：
 * <ul>
 *   <li>响应字段少（聚合 + 必要）</li>
 *   <li>支持设备绑定 + 推送</li>
 *   <li>支持离线缓存（lastModified）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/mapi")
@RequiredArgsConstructor
public class MobileApiService {

    private final MobileDeviceMapper deviceMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 设备注册
    // ====================================================================

    @PostMapping("/device/register")
    @Transactional(rollbackFor = Exception.class)
    public Result<MobileDevice> registerDevice(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String deviceId = (String) body.get("deviceId");
        String platform = (String) body.get("platform");
        if (deviceId == null || platform == null)
            return Result.error(BizCode.BAD_REQUEST, "deviceId / platform 必填");

        MobileDevice exist = deviceMapper.selectOne(
                new LambdaQueryWrapper<MobileDevice>()
                        .eq(MobileDevice::getTenantId, tenantId)
                        .eq(MobileDevice::getDeviceId, deviceId));
        if (exist != null) {
            exist.setLastLoginAt(LocalDateTime.now());
            exist.setLoginCount(exist.getLoginCount() + 1);
            exist.setPlatform(platform);
            if (body.get("pushToken") != null) exist.setPushToken((String) body.get("pushToken"));
            if (body.get("appVersion") != null) exist.setAppVersion((String) body.get("appVersion"));
            if (body.get("osVersion") != null) exist.setOsVersion((String) body.get("osVersion"));
            if (body.get("deviceModel") != null) exist.setDeviceModel((String) body.get("deviceModel"));
            exist.setUserId(body.get("userId") == null ? exist.getUserId()
                    : ((Number) body.get("userId")).longValue());
            deviceMapper.updateById(exist);
            return Result.success("已更新", exist);
        }
        MobileDevice d = new MobileDevice();
        d.setTenantId(tenantId);
        d.setDeviceId(deviceId);
        d.setPlatform(platform);
        d.setPushToken((String) body.get("pushToken"));
        d.setAppVersion((String) body.get("appVersion"));
        d.setOsVersion((String) body.get("osVersion"));
        d.setDeviceModel((String) body.get("deviceModel"));
        d.setUserId(body.get("userId") == null ? null : ((Number) body.get("userId")).longValue());
        d.setLastLoginAt(LocalDateTime.now());
        d.setLoginCount(1);
        d.setEnabled(1);
        deviceMapper.insert(d);
        return Result.success("已注册", d);
    }

    // ====================================================================
    // 2. 移动端首页（聚合 + 精简）
    // ====================================================================

    @GetMapping("/home")
    public Result<Map<String, Object>> home(@RequestParam(required = false) Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 1) 用户概要（如果登录）
        if (memberId != null) {
            try {
                Map<String, Object> member = jdbc.queryForMap(
                    "SELECT id, name, level_code, points, balance FROM crm_member " +
                    "WHERE id = ? AND deleted = 0 AND tenant_id = ?",
                    memberId, tenantId);
                data.put("member", Map.of(
                        "id", member.get("id"),
                        "name", member.get("name"),
                        "level", member.get("level_code"),
                        "points", member.get("points"),
                        "balance", member.get("balance")));
            } catch (Exception e) {
                data.put("member", null);
            }
        } else {
            data.put("member", null);
        }

        // 2) 最近订单（最多 5 单）
        List<Map<String, Object>> orders = jdbc.queryForList(
            "SELECT id, order_no, total_amount, status, created_at " +
            "FROM pos_order WHERE deleted = 0 AND tenant_id = ? " +
            (memberId != null ? "AND member_id = ? " : "") +
            "ORDER BY created_at DESC LIMIT 5",
            memberId != null ? new Object[]{tenantId, memberId} : new Object[]{tenantId});
        data.put("recentOrders", orders);

        // 3) 优惠卡（最多 3）
        List<Map<String, Object>> coupons = jdbc.queryForList(
            "SELECT id, coupon_name, discount_amount, status " +
            "FROM mkt_coupon WHERE deleted = 0 AND tenant_id = ? " +
            "  AND status = 1 LIMIT 3",
            tenantId);
        data.put("coupons", coupons);

        // 4) 待办提醒
        Integer todo = jdbc.queryForObject(
            "SELECT COUNT(*) FROM crm_follow_up_task " +
            "WHERE tenant_id = ? AND status = 'PENDING'" +
            (memberId != null ? " AND member_id = ?" : ""),
            Integer.class,
            memberId != null ? new Object[]{tenantId, memberId} : new Object[]{tenantId});
        data.put("pendingTasks", todo == null ? 0 : todo);

        data.put("lastModified", LocalDateTime.now());
        return Result.success(data);
    }

    // ====================================================================
    // 3. 移动端会员卡（精简）
    // ====================================================================

    @GetMapping("/member/card")
    public Result<Map<String, Object>> memberCard(@RequestParam Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            Map<String, Object> m = jdbc.queryForMap(
                "SELECT id, name, level_code, points, balance, total_consume, " +
                "       birthday, register_date, last_visit_at " +
                "FROM crm_member WHERE id = ? AND deleted = 0 AND tenant_id = ?",
                memberId, tenantId);
            data.put("card", Map.of(
                    "id", m.get("id"),
                    "name", m.get("name"),
                    "level", m.get("level_code"),
                    "points", m.get("points"),
                    "balance", m.get("balance"),
                    "totalConsume", m.get("total_consume"),
                    "birthday", m.get("birthday")));
            // 累计次数
            Integer visitCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pos_order " +
                "WHERE member_id = ? AND deleted = 0 AND status IN ('PAID', 'COMPLETED')",
                Integer.class, memberId);
            data.put("visitCount", visitCount == null ? 0 : visitCount);
        } catch (Exception e) {
            return Result.error(BizCode.NOT_FOUND, "会员不存在");
        }
        return Result.success(data);
    }

    // ====================================================================
    // 4. 移动端预约日历（精简）
    // ====================================================================

    @GetMapping("/booking/slots")
    public Result<List<Map<String, Object>>> bookingSlots(
            @RequestParam Long storeId,
            @RequestParam Long serviceId,
            @RequestParam String date) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 简化版：返回当天已占用时段
        List<Map<String, Object>> occupied = jdbc.queryForList(
            "SELECT booking_time, employee_id FROM wx_booking_record " +
            "WHERE deleted = 0 AND tenant_id = ? AND store_id = ? " +
            "  AND service_id = ? AND DATE(booking_time) = ? AND status IN ('PENDING', 'CONFIRMED')",
            tenantId, storeId, serviceId, date);

        // 生成可预约时段（9:00-21:00，每 30 分钟）
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int h = 9; h < 21; h++) {
            for (int m = 0; m < 60; m += 30) {
                String slot = String.format("%02d:%02d", h, m);
                boolean isOccupied = occupied.stream().anyMatch(o ->
                        o.get("booking_time").toString().contains(slot));
                slots.add(Map.of(
                        "time", slot,
                        "available", !isOccupied));
            }
        }
        return Result.success(slots);
    }

    // ====================================================================
    // 5. 移动端活动流（首页 banner）
    // ====================================================================

    @GetMapping("/feed")
    public Result<Map<String, Object>> feed() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // Banner（取启用的最新 3 条）
        try {
            List<Map<String, Object>> banners = jdbc.queryForList(
                "SELECT id, title, image_url, link_url, sort_order " +
                "FROM mkt_banner WHERE deleted = 0 AND tenant_id = ? " +
                "  AND enabled = 1 AND start_time <= NOW() AND end_time >= NOW() " +
                "ORDER BY sort_order LIMIT 3",
                tenantId);
            data.put("banners", banners);
        } catch (Exception e) {
            data.put("banners", List.of());
        }

        // 限时活动
        try {
            List<Map<String, Object>> activities = jdbc.queryForList(
                "SELECT id, title, description, start_time, end_time, image_url " +
                "FROM mkt_campaign WHERE deleted = 0 AND tenant_id = ? " +
                "  AND status = 'ACTIVE' AND end_time >= NOW() " +
                "ORDER BY id DESC LIMIT 5",
                tenantId);
            data.put("activities", activities);
        } catch (Exception e) {
            data.put("activities", List.of());
        }
        return Result.success(data);
    }

    // ====================================================================
    // 6. 移动端版本检查
    // ====================================================================

    @GetMapping("/version/check")
    public Result<Map<String, Object>> versionCheck(@RequestParam(required = false) String currentVersion,
                                                     @RequestParam(required = false) String platform) {
        Map<String, Object> data = new LinkedHashMap<>();
        // 实际生产应从数据库读
        data.put("latestVersion", "1.2.0");
        data.put("minVersion", "1.0.0");
        data.put("forceUpdate", false);
        if (currentVersion != null) {
            data.put("needUpdate", !currentVersion.equals("1.2.0"));
        }
        data.put("updateUrl", "https://example.com/app/download");
        return Result.success(data);
    }
}