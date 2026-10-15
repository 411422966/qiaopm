package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.BirthdayMember;
import com.meiyetec.entity.BirthdaySendLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BirthdayMemberMapper;
import com.meiyetec.mapper.BirthdaySendLogMapper;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * M09-02：生日提醒 REST 接口层（接入 BirthdayNotificationService 真实通道）
 *
 * <p>职责：
 * <ul>
 *   <li>HTTP 入口：录入/查询/发送/仪表盘</li>
 *   <li>委托 BirthdayNotificationService 处理所有真实业务</li>
 * </ul>
 *
 * <p>定时任务委托给 BirthdayNotificationService.scheduledScan()
 */
@Slf4j
@RestController
@RequestMapping("/birthday")
@RequiredArgsConstructor
public class BirthdayReminderService {

    private final BirthdayMemberMapper memberMapper;
    private final BirthdaySendLogMapper sendLogMapper;
    private final BirthdayNotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbc;

    // ==============================================================
    // 生日录入
    // ==============================================================

    @PostMapping("/member/register")
    @Transactional(rollbackFor = Exception.class)
    public Result<BirthdayMember> register(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        String dateStr = (String) body.get("birthday");
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        if (dateStr == null) return Result.error(BizCode.BAD_REQUEST, "birthday 必填");

        // 检查会员存在
        Map<String, Object> member = getMemberInfo(memberId);
        if (member.isEmpty()) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        BirthdayMember exist = memberMapper.selectOne(new LambdaQueryWrapper<BirthdayMember>()
                .eq(BirthdayMember::getTenantId, tenantId)
                .eq(BirthdayMember::getMemberId, memberId));
        if (exist != null) {
            // 已录入 → 更新
            exist.setBirthday(LocalDate.parse(dateStr));
            exist.setBirthdayType((String) body.getOrDefault("birthdayType", "SOLAR"));
            exist.setAutoSend(Boolean.TRUE.equals(body.get("autoSend")) ? 1 : 0);
            exist.setAutoCoupon((String) body.get("autoCoupon"));
            if (body.get("autoCouponAmount") != null) {
                exist.setAutoCouponAmount(new BigDecimal(body.get("autoCouponAmount").toString()));
            }
            exist.setVipLevel(body.get("vipLevel") == null ? null : ((Number) body.get("vipLevel")).intValue());
            exist.setTags((String) body.get("tags"));
            memberMapper.updateById(exist);
            return Result.success("已更新", exist);
        }

        BirthdayMember b = new BirthdayMember();
        b.setTenantId(tenantId);
        b.setMemberId(memberId);
        b.setBirthday(LocalDate.parse(dateStr));
        b.setBirthdayType((String) body.getOrDefault("birthdayType", "SOLAR"));
        b.setAutoSend(Boolean.TRUE.equals(body.get("autoSend")) ? 1 : 0);
        b.setAutoCoupon((String) body.get("autoCoupon"));
        if (body.get("autoCouponAmount") != null) {
            b.setAutoCouponAmount(new BigDecimal(body.get("autoCouponAmount").toString()));
        }
        b.setVipLevel(body.get("vipLevel") == null ? null : ((Number) body.get("vipLevel")).intValue());
        b.setTags((String) body.get("tags"));
        memberMapper.insert(b);
        return Result.success("已录入", b);
    }

    /**
     * 更新生日信息（支持只改部分字段）
     */
    @PutMapping("/member/{memberId}")
    @Transactional(rollbackFor = Exception.class)
    public Result<BirthdayMember> update(@PathVariable Long memberId, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        BirthdayMember exist = memberMapper.selectOne(new LambdaQueryWrapper<BirthdayMember>()
                .eq(BirthdayMember::getTenantId, tenantId)
                .eq(BirthdayMember::getMemberId, memberId));
        if (exist == null) return Result.error(BizCode.NOT_FOUND, "未录入该会员生日");

        if (body.get("birthday") != null) {
            exist.setBirthday(LocalDate.parse((String) body.get("birthday")));
        }
        if (body.get("birthdayType") != null) {
            exist.setBirthdayType((String) body.get("birthdayType"));
        }
        if (body.get("autoSend") != null) {
            exist.setAutoSend(Boolean.TRUE.equals(body.get("autoSend")) ? 1 : 0);
        }
        if (body.get("autoCoupon") != null) {
            exist.setAutoCoupon((String) body.get("autoCoupon"));
        }
        if (body.get("autoCouponAmount") != null) {
            exist.setAutoCouponAmount(new BigDecimal(body.get("autoCouponAmount").toString()));
        }
        if (body.get("tags") != null) {
            exist.setTags((String) body.get("tags"));
        }
        memberMapper.updateById(exist);
        return Result.success("已更新", exist);
    }

    // ==============================================================
    // 查询
    // ==============================================================

    @GetMapping("/member/list")
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) Boolean autoSendOnly,
            @RequestParam(required = false) Integer daysUntilBirthday) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT bm.*, m.member_name AS name, m.phone FROM birthday_member bm " +
                "LEFT JOIN crm_member m ON m.id = bm.member_id " +
                "WHERE bm.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (Boolean.TRUE.equals(autoSendOnly)) {
            sql.append("AND bm.auto_send = 1 ");
        }
        sql.append("ORDER BY bm.id DESC LIMIT 200");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
            // 过滤 N 天内生日
            if (daysUntilBirthday != null && daysUntilBirthday > 0) {
                LocalDate today = LocalDate.now();
                rows.removeIf(r -> {
                    Object bd = r.get("birthday");
                    if (bd == null) return false;
                    LocalDate bday;
                    try {
                        bday = bd instanceof java.sql.Date
                                ? ((java.sql.Date) bd).toLocalDate()
                                : LocalDate.parse(bd.toString().substring(0, 10));
                    } catch (Exception e) { return false; }
                    LocalDate next = bday.withYear(today.getYear());
                    if (next.isBefore(today)) next = next.plusYears(1);
                    long diff = ChronoUnit.DAYS.between(today, next);
                    r.put("daysUntilBirthday", diff);
                    return diff > daysUntilBirthday;
                });
            }
            return Result.success(rows);
        } catch (Exception e) {
            log.warn("birthday member list error: {}", e.getMessage());
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/member/{memberId}")
    public Result<Map<String, Object>> memberBirthday(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT bm.*, m.member_name AS name, m.phone FROM birthday_member bm " +
                "LEFT JOIN crm_member m ON m.id = bm.member_id " +
                "WHERE bm.tenant_id = ? AND bm.member_id = ? LIMIT 1",
                tenantId, memberId);
        if (rows.isEmpty()) return Result.error(BizCode.NOT_FOUND, "未录入生日");
        return Result.success(rows.get(0));
    }

    // ==============================================================
    // 即将到来的生日（按配置天数）
    // ==============================================================

    @GetMapping("/upcoming")
    public Result<List<Map<String, Object>>> upcoming(@RequestParam(defaultValue = "7") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();
        String sql = """
            SELECT bm.*, m.member_name AS name, m.phone,
                   TIMESTAMPDIFF(YEAR, bm.birthday, CURDATE()) AS age
            FROM birthday_member bm
            LEFT JOIN crm_member m ON m.id = bm.member_id
            WHERE bm.tenant_id = ? AND bm.auto_send = 1
            ORDER BY DATE_FORMAT(bm.birthday, '%m-%d') ASC
            """;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId);
            List<Map<String, Object>> upcoming = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Object bd = r.get("birthday");
                if (bd == null) continue;
                LocalDate bday;
                try {
                    bday = bd instanceof java.sql.Date
                            ? ((java.sql.Date) bd).toLocalDate()
                            : LocalDate.parse(bd.toString().substring(0, 10));
                } catch (Exception e) { continue; }
                LocalDate next = bday.withYear(today.getYear());
                if (next.isBefore(today)) next = next.plusYears(1);
                long diff = ChronoUnit.DAYS.between(today, next);
                if (diff >= 0 && diff <= days) {
                    r.put("daysUntilBirthday", diff);
                    r.put("nextBirthday", next.toString());
                    upcoming.add(r);
                }
            }
            return Result.success(upcoming);
        } catch (Exception e) {
            log.warn("upcoming birthdays error: {}", e.getMessage());
            return Result.success(new ArrayList<>());
        }
    }

    // ==============================================================
    // 发送生日礼（真实通道 — 委托 BirthdayNotificationService）
    // ==============================================================

    /**
     * 手动触发生日礼物发放（真实通道）
     *
     * @param body 包含：
     *   memberId     会员ID（必填）
     *   giftType     礼物类型：COUPON / POINTS / GIFT / SMS_ONLY（默认 SMS_ONLY）
     *   couponId     当 giftType=COUPON 时，优惠券ID
     *   points       当 giftType=POINTS 时，积分数量（默认500）
     *   channelType  通知通道：SMS / MINI_PROGRAM / IN_APP（默认 IN_APP，多个逗号分隔）
     *   templateId   消息模板ID（可选）
     */
    @PostMapping("/send")
    public Result<BirthdaySendLog> sendGift(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");

        String giftType = (String) body.getOrDefault("giftType", "SMS_ONLY");
        Long couponId = body.get("couponId") == null ? null
                : ((Number) body.get("couponId")).longValue();
        Integer points = body.get("points") == null ? null
                : ((Number) body.get("points")).intValue();
        String channelType = (String) body.getOrDefault("channelType", "IN_APP");
        Long templateId = body.get("templateId") == null ? null
                : ((Number) body.get("templateId")).longValue();

        return notificationService.sendGift(memberId, giftType, couponId, points, channelType, templateId);
    }

    /**
     * 批量触发生日礼物
     */
    @PostMapping("/send/batch")
    public Result<Map<String, Object>> sendBatch(@RequestBody Map<String, Object> body) {
        List<?> ids = body.get("memberIds") == null ? null : (List<?>) body.get("memberIds");
        Integer daysBefore = body.get("daysBefore") == null ? null
                : ((Number) body.get("daysBefore")).intValue();
        String giftType = (String) body.getOrDefault("giftType", "SMS_ONLY");
        Long couponId = body.get("couponId") == null ? null
                : ((Number) body.get("couponId")).longValue();
        String channelType = (String) body.getOrDefault("channelType", "IN_APP");

        // 解析会员范围
        List<Map<String, Object>> targets;
        if (ids != null && !ids.isEmpty()) {
            targets = new ArrayList<>();
            for (Object id : ids) {
                Long mid = ((Number) id).longValue();
                Map<String, Object> m = getMemberInfo(mid);
                if (!m.isEmpty()) targets.add(m);
            }
        } else if (daysBefore != null) {
            Result<List<Map<String, Object>>> r = upcoming(daysBefore);
            targets = r.getData();
        } else {
            return Result.error(BizCode.BAD_REQUEST, "memberIds 或 daysBefore 至少填一个");
        }

        int success = 0, fail = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (Map<String, Object> t : targets) {
            Long mid = ((Number) t.get("member_id")).longValue();
            try {
                notificationService.sendGift(mid, giftType, couponId, null, channelType, null);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add(Map.of("memberId", mid, "error", e.getMessage()));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", targets.size());
        data.put("success", success);
        data.put("fail", fail);
        data.put("errors", errors);
        return Result.success("批量发送完成：" + success + " 成功，" + fail + " 失败", data);
    }

    // ==============================================================
    // 发送记录
    // ==============================================================

    @GetMapping("/log/list")
    public Result<List<Map<String, Object>>> sendLogs(
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String giftType) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT sl.*, m.member_name AS memberName, m.phone " +
                "FROM birthday_send_log sl " +
                "LEFT JOIN crm_member m ON m.id = sl.member_id " +
                "WHERE sl.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (year != null) { sql.append("AND sl.send_year = ? "); args.add(year); }
        if (status != null && !status.isEmpty()) { sql.append("AND sl.status = ? "); args.add(status); }
        if (giftType != null && !giftType.isEmpty()) { sql.append("AND sl.gift_type = ? "); args.add(giftType); }
        sql.append("ORDER BY sl.id DESC LIMIT ?");
        args.add(limit);
        try {
            return Result.success(jdbc.queryForList(sql.toString(), args.toArray()));
        } catch (Exception e) {
            log.warn("birthday log list error: {}", e.getMessage());
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/log/stat")
    public Result<Map<String, Object>> logStat(@RequestParam(required = false) Integer year) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int y = year == null ? LocalDate.now().getYear() : year;
        String sql = """
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS success,
                SUM(CASE WHEN status = 'FAILED' OR status = 'PARTIAL_FAILED' THEN 1 ELSE 0 END) AS failed,
                SUM(CASE WHEN gift_type = 'COUPON' THEN 1 ELSE 0 END) AS couponCount,
                SUM(CASE WHEN gift_type = 'POINTS' THEN 1 ELSE 0 END) AS pointsCount,
                SUM(CASE WHEN gift_type = 'SMS_ONLY' THEN 1 ELSE 0 END) AS smsOnlyCount
            FROM birthday_send_log
            WHERE tenant_id = ? AND send_year = ?
            """;
        try {
            Map<String, Object> row = jdbc.queryForMap(sql, tenantId, y);
            return Result.success(row);
        } catch (Exception e) {
            return Result.success(Map.of("total", 0, "success", 0, "failed", 0));
        }
    }

    // ==============================================================
    // 仪表盘
    // ==============================================================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int year = LocalDate.now().getYear();

        long totalMembers = memberMapper.selectCount(
                new LambdaQueryWrapper<BirthdayMember>().eq(BirthdayMember::getTenantId, tenantId));
        long autoSendMembers = memberMapper.selectCount(
                new LambdaQueryWrapper<BirthdayMember>()
                        .eq(BirthdayMember::getTenantId, tenantId)
                        .eq(BirthdayMember::getAutoSend, 1));
        long sentThisYear = sendLogMapper.selectCount(
                new LambdaQueryWrapper<BirthdaySendLog>()
                        .eq(BirthdaySendLog::getTenantId, tenantId)
                        .eq(BirthdaySendLog::getSendYear, year));

        Result<List<Map<String, Object>>> upcomingR = upcoming(7);
        List<Map<String, Object>> upcomingList = upcomingR.getData();

        // 按月统计
        List<Map<String, Object>> monthly = new ArrayList<>();
        try {
            monthly = jdbc.queryForList(
                    "SELECT send_year, send_month, COUNT(*) AS cnt " +
                    "FROM birthday_send_log " +
                    "WHERE tenant_id = ? AND send_year >= ? " +
                    "GROUP BY send_year, send_month ORDER BY send_year DESC, send_month DESC LIMIT 12",
                    tenantId, year - 1);
        } catch (Exception ignored) {}

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalMembers", totalMembers);
        data.put("autoSendMembers", autoSendMembers);
        data.put("sentThisYear", sentThisYear);
        data.put("upcoming7d", upcomingList);
        data.put("upcomingCount", upcomingList.size());
        data.put("monthly", monthly);
        return Result.success(data);
    }

    // ==============================================================
    // 预览（模拟发送，看会发给谁）
    // ==============================================================

    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam(defaultValue = "7") Integer days) {
        Result<List<Map<String, Object>>> r = upcoming(days);
        List<Map<String, Object>> targets = r.getData();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", targets.size());
        data.put("members", targets);
        data.put("message", "系统将在生日当天/提前7天自动发送站内信通知");
        return Result.success(data);
    }

    // ==============================================================
    // 辅助
    // ==============================================================

    private Map<String, Object> getMemberInfo(Long memberId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id AS member_id, member_name AS name, phone, openid " +
                    "FROM crm_member WHERE id = ? AND deleted = 0 LIMIT 1",
                    memberId);
            return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
