package com.meiyetec.service;

import com.meiyetec.entity.BookingWaitlist;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.BookingWaitlistMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * M13 · 候补智能补位服务
 *
 * <p>基于 {@link BookingWaitlistScoreService} 的评分结果执行补位：
 *  <ol>
 *    <li>初筛：同项目 + 同日期 + status=1 + 时段重叠</li>
 *    <li>AI 评分：5 维度打分 + 总分排序</li>
 *    <li>阈值过滤：总分 &lt; 配置阈值 / 锁定 / 过期 → 跳过</li>
 *    <li>冷却过滤：同候补 cooldown 内已通知过 → 跳过</li>
 *    <li>限额：单轮最多通知 maxNotifyPerRound 位</li>
 *    <li>通知：写 booking_waitlist_notify + 更新 booking_waitlist.status=2</li>
 *  </ol>
 *
 * <p>与原 {@code BookingResourceService.autoFill} 共存：
 *  - 旧端点 {@code POST /biz/booking/waitlist/auto-fill} 保留兼容（旧排序 = priority + FIFO）
 *  - 新端点 {@code POST /biz/booking/waitlist/smart-fill} 走评分算法（默认权重 + 可配）
 *  - 新端点 {@code POST /biz/booking/waitlist/score-preview} 返回评分明细（不写日志 / 不通知）
 * </p>
 *
 * @author Cursor Agent
 * @since 2026-09-05 (M13 补完)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingWaitlistFillService {

    private final BookingWaitlistMapper waitlistMapper;
    private final BookingWaitlistScoreService scoreService;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 智能补位主入口（执行评分 + 写通知 + 改候补状态 + 写评分日志）
     *
     * @param body 请求体：serviceItemId / employeeId? / preferredDate / startTime / endTime
     * @return 通知结果（含评分明细）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> smartFill(Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        // 1) 解析参数
        Long serviceItemId = body.get("serviceItemId") == null ? null
                : ((Number) body.get("serviceItemId")).longValue();
        if (serviceItemId == null) return Result.error(BizCode.BAD_REQUEST, "serviceItemId 必填");
        Long employeeId = body.get("employeeId") == null ? null
                : ((Number) body.get("employeeId")).longValue();
        LocalDate preferredDate = body.get("preferredDate") == null ? LocalDate.now()
                : LocalDate.parse(body.get("preferredDate").toString());
        LocalTime startTime = parseTime(body.get("startTime"), LocalTime.of(0, 0));
        LocalTime endTime   = parseTime(body.get("endTime"),   LocalTime.of(23, 59));

        // 2) 加载评分配置
        BookingWaitlistScoreService.ScoreConfig cfg = scoreService.loadConfig(tenantId, storeId);
        if (!cfg.isEnabled()) {
            log.info("[WAITLIST-SMART-FILL] 评分功能 disabled → 跳过");
            return Result.success("评分功能未启用，跳过", Collections.emptyMap());
        }

        // 3) 初筛候补（与原 autoFill 同样的 SQL 条件）
        List<Map<String, Object>> candidates = loadCandidates(tenantId, serviceItemId, preferredDate,
                startTime, endTime, employeeId);
        if (candidates.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("notifiedCount", 0);
            data.put("notifiedIds", Collections.emptyList());
            data.put("scoredCount", 0);
            data.put("rejectedCount", 0);
            data.put("message", "无候选候补");
            return Result.success("无可通知候补", data);
        }

        // 4) 构建评分上下文 + 评分排序
        BookingWaitlistScoreService.ScoringContext ctx = new BookingWaitlistScoreService.ScoringContext();
        ctx.setTenantId(tenantId);
        ctx.setStoreId(storeId);
        ctx.setServiceItemId(serviceItemId);
        ctx.setEmployeeId(employeeId);
        ctx.setPreferredDate(preferredDate);
        ctx.setSlotStart(startTime);
        ctx.setSlotEnd(endTime);
        ctx.setConfig(cfg);

        List<BookingWaitlistScoreService.ScoreResult> scored = scoreService.scoreAndRank(candidates, ctx);

        // 5) 过滤：冷却 + 限额
        List<BookingWaitlistScoreService.ScoreResult> toNotify = filterAndLimit(scored, cfg);
        List<BookingWaitlistScoreService.ScoreResult> skipped = new ArrayList<>();
        for (BookingWaitlistScoreService.ScoreResult r : scored) {
            if (!toNotify.contains(r)) skipped.add(r);
        }

        // 6) 写通知 + 改状态
        List<Long> notifiedIds = new ArrayList<>();
        for (BookingWaitlistScoreService.ScoreResult r : toNotify) {
            String content = buildNotifyContent(r, serviceItemId, preferredDate, startTime, endTime);
            try {
                jdbc.update(
                    "INSERT INTO booking_waitlist_notify " +
                    "(waitlist_id, notify_type, target, content, succeeded) " +
                    "VALUES (?, 'SMS', ?, ?, 1)",
                    r.getWaitlistId(), r.getMemberPhone(), content);
                jdbc.update(
                    "UPDATE booking_waitlist SET status = 2, notify_at = NOW(), " +
                    "notify_count = notify_count + 1 WHERE id = ?",
                    r.getWaitlistId());
                notifiedIds.add(r.getWaitlistId());
            } catch (Exception e) {
                log.warn("[WAITLIST-SMART-FILL] notify failed for waitlist {}: {}",
                        r.getWaitlistId(), e.getMessage());
            }
        }

        // 7) 持久化评分日志
        scoreService.persistScoreResults(tenantId, storeId, serviceItemId, preferredDate, "AUTO_FILL", scored);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notifiedCount", notifiedIds.size());
        data.put("notifiedIds", notifiedIds);
        data.put("scoredCount", scored.size());
        data.put("rejectedCount", skipped.size());
        data.put("config", cfgToMap(cfg));
        data.put("ranked", toScoredListMap(toNotify));
        data.put("skipped", toScoredListMap(skipped));
        return Result.success("已按 AI 评分通知候补成员", data);
    }

    /**
     * 评分预览（不写日志、不通知、不改状态）
     */
    public Result<Map<String, Object>> preview(Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        Long serviceItemId = body.get("serviceItemId") == null ? null
                : ((Number) body.get("serviceItemId")).longValue();
        if (serviceItemId == null) return Result.error(BizCode.BAD_REQUEST, "serviceItemId 必填");
        Long employeeId = body.get("employeeId") == null ? null
                : ((Number) body.get("employeeId")).longValue();
        LocalDate preferredDate = body.get("preferredDate") == null ? LocalDate.now()
                : LocalDate.parse(body.get("preferredDate").toString());
        LocalTime startTime = parseTime(body.get("startTime"), LocalTime.of(0, 0));
        LocalTime endTime   = parseTime(body.get("endTime"),   LocalTime.of(23, 59));
        boolean strictMatch = Boolean.TRUE.equals(body.get("strictMatch"));

        BookingWaitlistScoreService.ScoreConfig cfg = scoreService.loadConfig(tenantId, storeId);
        List<Map<String, Object>> candidates = loadCandidates(tenantId, serviceItemId, preferredDate,
                startTime, endTime, employeeId);

        BookingWaitlistScoreService.ScoringContext ctx = new BookingWaitlistScoreService.ScoringContext();
        ctx.setTenantId(tenantId);
        ctx.setStoreId(storeId);
        ctx.setServiceItemId(serviceItemId);
        ctx.setEmployeeId(employeeId);
        ctx.setPreferredDate(preferredDate);
        ctx.setSlotStart(startTime);
        ctx.setSlotEnd(endTime);
        ctx.setStrictMatchOnly(strictMatch);
        ctx.setConfig(cfg);

        List<BookingWaitlistScoreService.ScoreResult> scored =
                scoreService.scoreAndRank(candidates, ctx);
        List<BookingWaitlistScoreService.ScoreResult> notifiable = filterAndLimit(scored, cfg);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config", cfgToMap(cfg));
        data.put("candidatesCount", candidates.size());
        data.put("scoredCount", scored.size());
        data.put("notifiableCount", notifiable.size());
        data.put("scored", toScoredListMap(scored));
        return Result.success("评分预览", data);
    }

    // ============ 私有方法 ============

    /**
     * 初筛 SQL：与 BookingResourceService.autoFill 条件对齐
     */
    private List<Map<String, Object>> loadCandidates(Long tenantId, Long serviceItemId,
                                                     LocalDate date, LocalTime startTime, LocalTime endTime,
                                                     Long employeeId) {
        String sql =
            "SELECT id, waitlist_no, member_id, member_name, member_phone, " +
            "       service_item_id, employee_id, preferred_date, preferred_time_from, preferred_time_to, " +
            "       priority, status, create_time " +
            "FROM booking_waitlist " +
            "WHERE deleted = 0 AND status = 1 AND tenant_id = ? " +
            "  AND service_item_id = ? " +
            "  AND preferred_date = ? " +
            "  AND (preferred_time_from IS NULL OR preferred_time_from < ?) " +
            "  AND (preferred_time_to   IS NULL OR preferred_time_to   > ?) " +
            "  AND (employee_id IS NULL OR employee_id = ?) " +
            "ORDER BY create_time ASC LIMIT 50";
        return jdbc.queryForList(sql, tenantId, serviceItemId, date,
                endTime.toString() + ":00", startTime.toString() + ":00",
                employeeId == null ? -1 : employeeId);
    }

    /**
     * 冷却过滤（notify_at + cooldownMinutes > now → 跳过）+ 限额
     */
    private List<BookingWaitlistScoreService.ScoreResult> filterAndLimit(
            List<BookingWaitlistScoreService.ScoreResult> scored,
            BookingWaitlistScoreService.ScoreConfig cfg) {
        List<BookingWaitlistScoreService.ScoreResult> result = new ArrayList<>();
        for (BookingWaitlistScoreService.ScoreResult r : scored) {
            if (!r.isEligible()) continue;
            if (isInCooldown(r.getWaitlistId(), cfg.getCooldownMinutes())) {
                r.setEligible(false);
                r.setRejectReason("冷却期内（" + cfg.getCooldownMinutes() + " 分钟内已通知）");
                continue;
            }
            result.add(r);
            if (result.size() >= cfg.getMaxNotifyPerRound()) break;
        }
        return result;
    }

    private boolean isInCooldown(Long waitlistId, int cooldownMinutes) {
        if (cooldownMinutes <= 0) return false;
        try {
            List<Map<String, Object>> rs = jdbc.queryForList(
                "SELECT notify_at FROM booking_waitlist WHERE id = ? AND notify_at IS NOT NULL " +
                "AND notify_at > DATE_SUB(NOW(), INTERVAL ? MINUTE)",
                waitlistId, cooldownMinutes);
            return !rs.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String buildNotifyContent(BookingWaitlistScoreService.ScoreResult r,
                                      Long serviceItemId, LocalDate date,
                                      LocalTime startTime, LocalTime endTime) {
        return String.format(
            "【AI 智能候补通知】尊敬的 %s，您候补的项目（ID %d）有空位：%s %s-%s，" +
            "综合评分 %.1f，请尽快回复确认（30 分钟内）。",
            Optional.ofNullable(r.getMemberName()).orElse("会员"),
            serviceItemId, date, startTime, endTime,
            Optional.ofNullable(r.getTotalScore()).orElse(0.0));
    }

    private LocalTime parseTime(Object o, LocalTime dflt) {
        if (o == null) return dflt;
        try {
            String s = o.toString();
            return LocalTime.parse(s.length() > 5 ? s.substring(0, 5) : s);
        } catch (Exception e) { return dflt; }
    }

    private Map<String, Object> cfgToMap(BookingWaitlistScoreService.ScoreConfig cfg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("wPreference", cfg.getWPreference());
        m.put("wValue", cfg.getWValue());
        m.put("wNoShow", cfg.getWNoShow());
        m.put("wWaitTime", cfg.getWWaitTime());
        m.put("wResponse", cfg.getWResponse());
        m.put("minScoreToNotify", cfg.getMinScoreToNotify());
        m.put("maxNotifyPerRound", cfg.getMaxNotifyPerRound());
        m.put("cooldownMinutes", cfg.getCooldownMinutes());
        m.put("waitTimeCapHours", cfg.getWaitTimeCapHours());
        m.put("lockedMemberAction", cfg.getLockedMemberAction());
        m.put("expiredAction", cfg.getExpiredAction());
        m.put("enabled", cfg.isEnabled());
        return m;
    }

    private List<Map<String, Object>> toScoredListMap(List<BookingWaitlistScoreService.ScoreResult> results) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BookingWaitlistScoreService.ScoreResult r : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("waitlistId", r.getWaitlistId());
            m.put("memberId", r.getMemberId());
            m.put("memberName", r.getMemberName());
            m.put("memberPhone", r.getMemberPhone());
            m.put("preferredDate", r.getPreferredDate());
            m.put("preferredTimeFrom", r.getPreferredTimeFrom());
            m.put("preferredTimeTo", r.getPreferredTimeTo());
            m.put("totalScore", r.getTotalScore());
            m.put("ranking", r.getRanking());
            m.put("eligible", r.isEligible());
            m.put("rejectReason", r.getRejectReason());
            m.put("preferenceScore", r.getPreferenceScore());
            m.put("valueScore", r.getValueScore());
            m.put("noShowScore", r.getNoShowScore());
            m.put("waitTimeScore", r.getWaitTimeScore());
            m.put("responseScore", r.getResponseScore());
            if (r.getMeta() != null) m.put("meta", r.getMeta());
            out.add(m);
        }
        return out;
    }
}