package com.meiyetec.service;

import com.meiyetec.security.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * M13 · 候补智能补位评分算法
 *
 * <p>5 维度评分（权重可按租户配置，默认权重 = 100%）：</p>
 * <pre>
 *   ① 期望匹配度 (Preference Match)   30%  - 日期 / 时段 / 技师 三层匹配
 *   ② 会员价值      (Member Value)     25%  - 会员等级 + 历史消费次数
 *   ③ 爽约风险      (No-Show Risk)     20%  - 反向：爽约率高/信用低 → 分低
 *   ④ 等待时长      (Wait Time)        15%  - 候补创建越久分越高（公平性）
 *   ⑤ 历史响应率    (Response Rate)    10%  - 历史通知转化率（候补 → 转预约）
 * </pre>
 *
 * <p>所有评分输出 0..100 的连续值，越高越优先。</p>
 *
 * <p>数据来源：</p>
 * <ul>
 *   <li>crm_member / crm_member_tag_value / crm_member_level（会员价值）</li>
 *   <li>member_credit / member_credit_log（爽约风险）</li>
 *   <li>pos_order（历史消费次数）</li>
 *   <li>booking_waitlist_notify（候补通知转化率）</li>
 *   <li>booking_waitlist（候补基本字段 + 等待时长）</li>
 * </ul>
 *
 * @author Cursor Agent
 * @since 2026-09-05 (M13 补完)
 */
@Slf4j
@Service
public class BookingWaitlistScoreService {

    @Autowired
    private JdbcTemplate jdbc;

    // ============ 默认权重（DB 无配置时使用）============
    public static final double DEFAULT_W_PREFERENCE = 0.30;
    public static final double DEFAULT_W_VALUE      = 0.25;
    public static final double DEFAULT_W_NO_SHOW    = 0.20;
    public static final double DEFAULT_W_WAIT_TIME   = 0.15;
    public static final double DEFAULT_W_RESPONSE   = 0.10;

    public static final double DEFAULT_MIN_SCORE_TO_NOTIFY = 30.0;
    public static final int    DEFAULT_MAX_NOTIFY_PER_ROUND = 5;
    public static final int    DEFAULT_COOLDOWN_MINUTES = 60;
    public static final int    DEFAULT_WAIT_TIME_CAP_HOURS = 8;

    // ============ 评分上下文 ============

    /** 调用方传入的评分上下文（释放时段、项目、技师等） */
    @Data
    public static class ScoringContext {
        private Long tenantId;
        private Long storeId;
        private Long serviceItemId;
        private String serviceItemName;
        private Long employeeId;
        private String employeeName;
        private LocalDate preferredDate;
        private LocalTime slotStart;     // 可释放时段开始
        private LocalTime slotEnd;       // 可释放时段结束
        private boolean strictMatchOnly; // 严格匹配：候补日期必须完全等于释放日期
        private ScoreConfig config;      // 权重配置（DB 查出来）
    }

    /** 评分配置（来自 booking_waitlist_score_config） */
    @Data
    public static class ScoreConfig {
        private double wPreference;
        private double wValue;
        private double wNoShow;
        private double wWaitTime;
        private double wResponse;
        private double minScoreToNotify;
        private int    maxNotifyPerRound;
        private int    cooldownMinutes;
        private int    waitTimeCapHours;
        private String lockedMemberAction; // SKIP / NOTIFY
        private String expiredAction;      // SKIP / NOTIFY
        private boolean enabled;
    }

    /** 单个候补评分结果（评分明细） */
    @Data
    public static class ScoreResult {
        private Long waitlistId;
        private Long memberId;
        private String memberName;
        private String memberPhone;
        private Long serviceItemId;
        private LocalDate preferredDate;
        private LocalTime preferredTimeFrom;
        private LocalTime preferredTimeTo;
        private Double preferenceScore;
        private Double valueScore;
        private Double noShowScore;
        private Double waitTimeScore;
        private Double responseScore;
        private Double totalScore;       // 0..100
        private boolean eligible;        // 是否通过资格检查（未锁定 / 未过期 / 分数达标）
        private String rejectReason;     // 不合格原因
        private Integer ranking;
        private Map<String, Object> meta; // 透出额外字段供前端展示
    }

    // ============ 配置加载 ============

    /**
     * 加载租户 + 门店的评分配置；找不到则返回默认配置。
     */
    public ScoreConfig loadConfig(Long tenantId, Long storeId) {
        ScoreConfig c = new ScoreConfig();
        c.setWPreference(DEFAULT_W_PREFERENCE);
        c.setWValue(DEFAULT_W_VALUE);
        c.setWNoShow(DEFAULT_W_NO_SHOW);
        c.setWWaitTime(DEFAULT_W_WAIT_TIME);
        c.setWResponse(DEFAULT_W_RESPONSE);
        c.setMinScoreToNotify(DEFAULT_MIN_SCORE_TO_NOTIFY);
        c.setMaxNotifyPerRound(DEFAULT_MAX_NOTIFY_PER_ROUND);
        c.setCooldownMinutes(DEFAULT_COOLDOWN_MINUTES);
        c.setWaitTimeCapHours(DEFAULT_WAIT_TIME_CAP_HOURS);
        c.setLockedMemberAction("SKIP");
        c.setExpiredAction("SKIP");
        c.setEnabled(true);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT weight_preference, weight_value, weight_no_show, weight_wait_time, weight_response, " +
                "       min_score_to_notify, max_notify_per_round, cooldown_minutes, wait_time_cap_hours, " +
                "       locked_member_action, expired_action, enabled " +
                "FROM booking_waitlist_score_config " +
                "WHERE deleted = 0 AND tenant_id = ? AND (store_id = ? OR store_id = 0) " +
                "ORDER BY store_id DESC LIMIT 1", tenantId, storeId == null ? 0 : storeId);
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                c.setWPreference(num(r.get("weight_preference"), (Double) DEFAULT_W_PREFERENCE));
                c.setWValue(num(r.get("weight_value"), (Double) DEFAULT_W_VALUE));
                c.setWNoShow(num(r.get("weight_no_show"), (Double) DEFAULT_W_NO_SHOW));
                c.setWWaitTime(num(r.get("weight_wait_time"), (Double) DEFAULT_W_WAIT_TIME));
                c.setWResponse(num(r.get("weight_response"), (Double) DEFAULT_W_RESPONSE));
                c.setMinScoreToNotify(num(r.get("min_score_to_notify"), (Double) DEFAULT_MIN_SCORE_TO_NOTIFY));
                c.setMaxNotifyPerRound(num(r.get("max_notify_per_round"), (double) DEFAULT_MAX_NOTIFY_PER_ROUND).intValue());
                c.setCooldownMinutes(num(r.get("cooldown_minutes"), (double) DEFAULT_COOLDOWN_MINUTES).intValue());
                c.setWaitTimeCapHours(num(r.get("wait_time_cap_hours"), (double) DEFAULT_WAIT_TIME_CAP_HOURS).intValue());
                c.setLockedMemberAction(str(r.get("locked_member_action"), "SKIP"));
                c.setExpiredAction(str(r.get("expired_action"), "SKIP"));
                c.setEnabled(((Number) r.getOrDefault("enabled", 1)).intValue() == 1);
            }
        } catch (Exception e) {
            log.warn("[WAITLIST-SCORE] 加载评分配置失败，使用默认权重: {}", e.getMessage());
        }
        return c;
    }

    // ============ 主入口：批量评分排序 ============

    /**
     * 对一组候补候选项（已经 SQL 初筛过：同项目 + 同日期 + status=1）做 AI 评分排序。
     *
     * <p>不修改候补表；只返回评分结果供调用方决定通知谁。</p>
     *
     * @param candidates 候补候选列表（通常 1-20 条）
     * @param ctx        评分上下文（释放的时段 + 配置）
     * @return 评分排序后的候补列表（高分在前），含维度明细
     */
    public List<ScoreResult> scoreAndRank(List<Map<String, Object>> candidates, ScoringContext ctx) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        ScoreConfig cfg = ctx.getConfig() == null ? loadConfig(ctx.getTenantId(), ctx.getStoreId()) : ctx.getConfig();
        ctx.setConfig(cfg);

        List<ScoreResult> results = new ArrayList<>(candidates.size());
        for (Map<String, Object> row : candidates) {
            ScoreResult r = scoreCandidate(row, ctx);
            results.add(r);
        }

        // 按总分倒序（null 排到最后）
        results.sort((a, b) -> {
            Double ta = a.getTotalScore(), tb = b.getTotalScore();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return Double.compare(tb, ta);
        });

        // 写排名
        for (int i = 0; i < results.size(); i++) results.get(i).setRanking(i + 1);
        return results;
    }

    // ============ 单个候补评分 ============

    /**
     * 计算单个候补的 5 维度分 + 总分。
     */
    public ScoreResult scoreCandidate(Map<String, Object> row, ScoringContext ctx) {
        ScoreConfig cfg = ctx.getConfig();
        ScoreResult r = new ScoreResult();
        r.setWaitlistId(((Number) row.get("id")).longValue());
        r.setMemberId(row.get("member_id") == null ? null : ((Number) row.get("member_id")).longValue());
        r.setMemberName(str(row.get("member_name"), ""));
        r.setMemberPhone(str(row.get("member_phone"), ""));
        r.setServiceItemId(row.get("service_item_id") == null ? null
                : ((Number) row.get("service_item_id")).longValue());

        // 期望日期 / 时段
        Object pd = row.get("preferred_date");
        if (pd instanceof java.sql.Date || pd instanceof LocalDate) {
            r.setPreferredDate(pd instanceof LocalDate ? (LocalDate) pd : ((java.sql.Date) pd).toLocalDate());
        } else if (pd != null) {
            try { r.setPreferredDate(LocalDate.parse(pd.toString())); } catch (Exception ignored) {}
        }
        Object pf = row.get("preferred_time_from");
        Object pt = row.get("preferred_time_to");
        if (pf instanceof java.sql.Time) r.setPreferredTimeFrom(((java.sql.Time) pf).toLocalTime());
        else if (pf != null) { try { r.setPreferredTimeFrom(LocalTime.parse(pf.toString().substring(0, 5))); } catch (Exception ignored) {} }
        if (pt instanceof java.sql.Time) r.setPreferredTimeTo(((java.sql.Time) pt).toLocalTime());
        else if (pt != null) { try { r.setPreferredTimeTo(LocalTime.parse(pt.toString().substring(0, 5))); } catch (Exception ignored) {} }

        Long expectedEmployeeId = row.get("employee_id") == null ? null
                : ((Number) row.get("employee_id")).longValue();

        Map<String, Object> memberInfo = r.getMemberId() == null ? Collections.emptyMap() : loadMemberSignals(r.getMemberId());
        boolean locked = Boolean.TRUE.equals(memberInfo.get("_locked"));
        if (locked && "SKIP".equalsIgnoreCase(cfg.getLockedMemberAction())) {
            r.setEligible(false);
            r.setRejectReason("会员已被锁定（locked_until 未到期）");
            // 锁定会员仍然给一个分数（前端可见，但不会通知）
            r.setPreferenceScore(0.0); r.setValueScore(0.0); r.setNoShowScore(0.0);
            r.setWaitTimeScore(0.0); r.setResponseScore(0.0); r.setTotalScore(0.0);
            r.setMeta(memberInfo);
            return r;
        }

        // 1) 期望匹配度 (30%)
        double pref = scorePreference(
                r.getPreferredDate(), r.getPreferredTimeFrom(), r.getPreferredTimeTo(),
                expectedEmployeeId, ctx);
        r.setPreferenceScore(round(pref));

        // 2) 会员价值 (25%)
        double val = scoreMemberValue(memberInfo);
        r.setValueScore(round(val));

        // 3) 爽约风险 (20%, 反向)
        double noShow = scoreNoShowRisk(memberInfo);
        r.setNoShowScore(round(noShow));

        // 4) 等待时长 (15%)
        double wait = scoreWaitTime(row, cfg);
        r.setWaitTimeScore(round(wait));

        // 5) 响应率 (10%)
        double resp = scoreResponseRate(r.getWaitlistId());
        r.setResponseScore(round(resp));

        // 综合 = Σ(score * weight)
        double total = pref * cfg.getWPreference()
                + val * cfg.getWValue()
                + noShow * cfg.getWNoShow()
                + wait * cfg.getWWaitTime()
                + resp * cfg.getWResponse();
        r.setTotalScore(round(total));

        boolean eligible = total >= cfg.getMinScoreToNotify() && !locked;
        r.setEligible(eligible);
        if (!eligible && locked) {
            r.setRejectReason("会员已被锁定");
        } else if (!eligible) {
            r.setRejectReason("综合评分 " + r.getTotalScore() + " < 阈值 " + cfg.getMinScoreToNotify());
        }
        r.setMeta(memberInfo);
        return r;
    }

    // ============ 维度 1：期望匹配度 (30%) ============

    /**
     * 日期/时段/技师三层加权：日期 50% + 时段 35% + 技师 15%
     */
    double scorePreference(LocalDate prefDate, LocalTime prefFrom, LocalTime prefTo,
                           Long prefEmployeeId, ScoringContext ctx) {
        double dateScore = scoreDateMatch(prefDate, ctx.getPreferredDate(), ctx.isStrictMatchOnly());
        double timeScore = scoreTimeMatch(prefFrom, prefTo, ctx.getSlotStart(), ctx.getSlotEnd());
        double techScore = scoreTechnicianMatch(prefEmployeeId, ctx.getEmployeeId());
        return dateScore * 0.50 + timeScore * 0.35 + techScore * 0.15;
    }

    double scoreDateMatch(LocalDate prefDate, LocalDate slotDate, boolean strict) {
        if (prefDate == null || slotDate == null) return 50; // 都没指定 → 中性
        long diff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(prefDate, slotDate));
        if (diff == 0) return 100;
        if (strict) return 0;
        if (diff == 1) return 60;
        if (diff == 2) return 30;
        return 0;
    }

    double scoreTimeMatch(LocalTime prefFrom, LocalTime prefTo, LocalTime slotFrom, LocalTime slotTo) {
        if (slotFrom == null || slotTo == null) return 50;
        if (prefFrom == null && prefTo == null) return 50;
        // 候补期望时段与释放时段的重叠度
        LocalTime pFrom = prefFrom != null ? prefFrom : LocalTime.MIN;
        LocalTime pTo = prefTo != null ? prefTo : LocalTime.MAX;
        // 求重叠
        LocalTime oStart = pFrom.isAfter(slotFrom) ? pFrom : slotFrom;
        LocalTime oEnd = pTo.isBefore(slotTo) ? pTo : slotTo;
        if (!oEnd.isAfter(oStart)) return 0; // 无重叠
        long overlapMin = Duration.between(oStart, oEnd).toMinutes();
        long prefMin = Duration.between(pFrom, pTo).toMinutes();
        long slotMin = Duration.between(slotFrom, slotTo).toMinutes();
        long denom = Math.max(1, Math.min(prefMin, slotMin));
        double ratio = Math.min(1.0, overlapMin / (double) denom);
        return round(ratio * 100);
    }

    double scoreTechnicianMatch(Long prefEmpId, Long slotEmpId) {
        if (prefEmpId == null) return 60; // 未指定技师 → 中性偏上
        if (slotEmpId == null) return 70; // 释放没指定技师 → 默认都行
        return prefEmpId.equals(slotEmpId) ? 100 : 0;
    }

    // ============ 维度 2：会员价值 (25%) ============

    /**
     * 等级 60% + 历史消费次数 40%
     * - 等级映射（crm_member_level.level_code）：DIAMOND=100 / GOLD=85 / SILVER=70 / NORMAL=50
     * - 历史消费次数阈值：>=10=100 / 5-9=80 / 1-4=60 / 0=30
     */
    double scoreMemberValue(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return 30; // 没数据 → 给基础分（不歧视）
        String level = str(m.get("level_code"), "NORMAL").toUpperCase();
        double lvl;
        switch (level) {
            case "DIAMOND": case "VIP": case "PLATINUM": lvl = 100; break;
            case "GOLD": lvl = 85; break;
            case "SILVER": lvl = 70; break;
            case "NORMAL": default: lvl = 50; break;
        }
        int orders = num(m.get("order_count"), (Double) 0d).intValue();
        double freq;
        if (orders >= 10) freq = 100;
        else if (orders >= 5) freq = 80;
        else if (orders >= 1) freq = 60;
        else freq = 30;
        // wallet_balance 加成（少量）
        double bal = num(m.get("wallet_balance"), (Double) 0d).doubleValue();
        double balBonus = Math.min(10, bal / 1000.0); // 每 1000 元 +1，封顶 10
        return lvl * 0.60 + freq * 0.40 + balBonus;
    }

    // ============ 维度 3：爽约风险 (20%, 反向) ============

    /**
     * 信用分 50% + 爽约率 50%
     * - 信用分：>=90=100 / 70-89=80 / 50-69=50 / <50=20
     * - 爽约率：0%=100 / 1-10%=70 / 11-30%=40 / >30%=0
     */
    double scoreNoShowRisk(Map<String, Object> m) {
        if (m == null || m.isEmpty()) return 80; // 没数据 → 默认风险低
        double credit = num(m.get("credit"), (Double) 100d).doubleValue();
        double cs;
        if (credit >= 90) cs = 100;
        else if (credit >= 70) cs = 80;
        else if (credit >= 50) cs = 50;
        else cs = 20;

        int noShow = num(m.get("no_show_count"), (Double) 0d).intValue();
        int total = num(m.get("total_bookings"), (Double) 0d).intValue();
        double rate = total == 0 ? 0 : (noShow * 100.0 / total);
        double rs;
        if (rate == 0) rs = 100;
        else if (rate <= 10) rs = 70;
        else if (rate <= 30) rs = 40;
        else rs = 0;
        return cs * 0.50 + rs * 0.50;
    }

    // ============ 维度 4：等待时长 (15%) ============

    /**
     * 候补创建时间 → 现在，分钟数。
     * <30min=40 / 30-60=60 / 1-2h=80 / 2-4h=90 / 4h+=100（按 cap 封顶）
     */
    double scoreWaitTime(Map<String, Object> row, ScoreConfig cfg) {
        Object ct = row.get("create_time");
        if (ct == null) return 30;
        LocalDateTime createTime;
        if (ct instanceof java.sql.Timestamp) createTime = ((java.sql.Timestamp) ct).toLocalDateTime();
        else if (ct instanceof LocalDateTime) createTime = (LocalDateTime) ct;
        else {
            try { createTime = LocalDateTime.parse(ct.toString().substring(0, 19)); }
            catch (Exception e) { return 30; }
        }
        long minutes = Duration.between(createTime, LocalDateTime.now()).toMinutes();
        long capMin = cfg.getWaitTimeCapHours() * 60L;
        if (minutes >= capMin) return 100;
        if (minutes < 30) return 40;
        if (minutes < 60) return 60;
        if (minutes < 120) return 80;
        if (minutes < 240) return 90;
        return 100;
    }

    // ============ 维度 5：历史响应率 (10%) ============

    /**
     * 历史通知转化率：booking_waitlist_notify 中 succeeded=1 且后续 booking_waitlist.status=3 的比例。
     * 无历史 → 中性 50。
     */
    double scoreResponseRate(Long waitlistId) {
        try {
            // 取该候补的所有历史通知（每个候补 id 在 booking_waitlist_notify 里都记录过历史）
            Map<String, Object> agg = jdbc.queryForMap(
                "SELECT " +
                "  SUM(CASE WHEN succeeded = 1 THEN 1 ELSE 0 END) AS succ, " +
                "  COUNT(*) AS total " +
                "FROM booking_waitlist_notify WHERE waitlist_id = ?",
                waitlistId);
            int total = num(agg.get("total"), (Double) 0d).intValue();
            if (total == 0) return 50;
            int succ = num(agg.get("succ"), (Double) 0d).intValue();
            double rate = succ * 100.0 / total;
            if (rate >= 70) return 100;
            if (rate >= 40) return 70;
            if (rate >= 10) return 40;
            return 10;
        } catch (Exception e) {
            return 50;
        }
    }

    // ============ 拉取会员信号（一次性查询） ============

    /**
     * 一次性把会员等级、信用、消费次数、储值余额、锁定状态 装进 Map。
     * 字段：level_code / credit / no_show_count / total_bookings / order_count / wallet_balance / _locked
     */
    Map<String, Object> loadMemberSignals(Long memberId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1) 会员等级（来自 crm_member_tag_value 或 crm_member.member_level 字段）
            List<Map<String, Object>> memberRows = jdbc.queryForList(
                "SELECT m.member_level, m.wallet_balance, " +
                "       COALESCE(c.credit, 100) AS credit, " +
                "       COALESCE(c.no_show_count, 0) AS no_show_count, " +
                "       COALESCE(c.total_bookings, 0) AS total_bookings, " +
                "       (c.locked_until IS NOT NULL AND c.locked_until > NOW()) AS locked " +
                "FROM crm_member m LEFT JOIN member_credit c ON c.member_id = m.id " +
                "WHERE m.id = ? AND m.deleted = 0 LIMIT 1", memberId);
            if (memberRows.isEmpty()) return result;
            Map<String, Object> m = memberRows.get(0);
            String level = str(m.get("member_level"), "NORMAL");
            result.put("level_code", level);
            result.put("credit", m.get("credit"));
            result.put("no_show_count", m.get("no_show_count"));
            result.put("total_bookings", m.get("total_bookings"));
            result.put("wallet_balance", m.get("wallet_balance"));
            result.put("_locked", Boolean.TRUE.equals(m.get("locked")));

            // 2) 历史完成订单数（pos_order.status = 3）
            Map<String, Object> orderStats = jdbc.queryForMap(
                "SELECT COUNT(*) AS cnt FROM pos_order " +
                "WHERE member_id = ? AND status IN (2,3) AND deleted = 0",
                memberId);
            result.put("order_count", orderStats.get("cnt"));
        } catch (Exception e) {
            log.warn("[WAITLIST-SCORE] loadMemberSignals({}) failed: {}", memberId, e.getMessage());
        }
        return result;
    }

    // ============ 持久化评分结果 ============

    /**
     * 把一组评分结果写日志 + 回写候补 last_score* 字段。
     * 不抛异常（写日志失败不影响主流程）。
     */
    public void persistScoreResults(Long tenantId, Long storeId, Long serviceItemId, LocalDate preferredDate,
                                     String triggerSource, List<ScoreResult> results) {
        if (results == null || results.isEmpty()) return;
        try {
            for (ScoreResult r : results) {
                // 1) 写日志
                String breakdown = String.format(
                    "{\"preference\":%.2f,\"value\":%.2f,\"noShow\":%.2f,\"waitTime\":%.2f,\"response\":%.2f}",
                    nz(r.getPreferenceScore()), nz(r.getValueScore()),
                    nz(r.getNoShowScore()), nz(r.getWaitTimeScore()), nz(r.getResponseScore()));
                jdbc.update(
                    "INSERT INTO booking_waitlist_score_log " +
                    "(tenant_id, store_id, waitlist_id, member_id, service_item_id, preferred_date, " +
                    " total_score, breakdown_json, ranking, trigger_source, decision, reason) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId, storeId, r.getWaitlistId(), r.getMemberId(), serviceItemId, preferredDate,
                    r.getTotalScore(), breakdown, r.getRanking(),
                    triggerSource,
                    r.isEligible() ? "NOTIFIED" : "SKIPPED",
                    r.getRejectReason());
                // 2) 回写候补 last_score
                jdbc.update(
                    "UPDATE booking_waitlist SET last_score = ?, last_score_at = NOW(), last_score_json = ? " +
                    "WHERE id = ?",
                    r.getTotalScore(), breakdown, r.getWaitlistId());
            }
        } catch (Exception e) {
            log.warn("[WAITLIST-SCORE] persistScoreResults failed: {}", e.getMessage());
        }
    }

    // ============ 工具方法 ============

    private static double nz(Double d) { return d == null ? 0.0 : d; }

    private static Double num(Object o, Double dflt) {
        if (o == null) return dflt;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return dflt; }
    }

    private static String str(Object o, String dflt) {
        if (o == null) return dflt;
        String s = o.toString();
        return s.isEmpty() ? dflt : s;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}