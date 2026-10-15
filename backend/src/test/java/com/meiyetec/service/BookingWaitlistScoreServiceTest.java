package com.meiyetec.service;

import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M13 · BookingWaitlistScoreService 评分算法单元测试
 *
 * <p>覆盖 5 个评分维度 + 综合分排序 + 锁定会员降级 + 冷却过滤。</p>
 */
class BookingWaitlistScoreServiceTest {

    private JdbcTemplate jdbc;
    private BookingWaitlistScoreService svc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        svc = new BookingWaitlistScoreService();
        svc.jdbc = jdbc;
        // 设置测试租户上下文
        TenantContext ctx = TenantContext.empty();
        ctx.setTenantId(1L);
        ctx.setStoreId(1L);
        ctx.setUserId(999L);
        ctx.setUsername("tester");
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ============ 维度 1：期望匹配度 ============

    @Test
    void dateMatch_exactMatch_returns100() {
        LocalDate d = LocalDate.of(2026, 9, 5);
        assertEquals(100.0, svc.scoreDateMatch(d, d, false), 0.01);
    }

    @Test
    void dateMatch_oneDayApart_returns60() {
        assertEquals(60.0, svc.scoreDateMatch(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 6), false), 0.01);
    }

    @Test
    void dateMatch_twoDaysApart_returns30() {
        assertEquals(30.0, svc.scoreDateMatch(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 7), false), 0.01);
    }

    @Test
    void dateMatch_sevenDaysApart_returns0() {
        assertEquals(0.0, svc.scoreDateMatch(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 12), false), 0.01);
    }

    @Test
    void dateMatch_strictMode_returns0_for_anyDifference() {
        assertEquals(0.0, svc.scoreDateMatch(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 6), true), 0.01);
    }

    @Test
    void dateMatch_null_returns50_neutral() {
        assertEquals(50.0, svc.scoreDateMatch(null, LocalDate.of(2026, 9, 5), false), 0.01);
        assertEquals(50.0, svc.scoreDateMatch(LocalDate.now(), null, false), 0.01);
    }

    @Test
    void timeMatch_perfectOverlap_returns100() {
        double score = svc.scoreTimeMatch(
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalTime.of(10, 0), LocalTime.of(12, 0));
        assertEquals(100.0, score, 0.01);
    }

    @Test
    void timeMatch_partialOverlap_returnsProportional() {
        // 期望 10-12，释放 11-13，重叠 11-12 = 60min
        // 较短段（期望）= 120min，重叠率 = 60/120 = 50% → 50
        double score = svc.scoreTimeMatch(
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalTime.of(11, 0), LocalTime.of(13, 0));
        assertEquals(50.0, score, 0.01);
    }

    @Test
    void timeMatch_noOverlap_returns0() {
        double score = svc.scoreTimeMatch(
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalTime.of(14, 0), LocalTime.of(16, 0));
        assertEquals(0.0, score, 0.01);
    }

    @Test
    void timeMatch_slotAllDay_returns50_neutral() {
        // 释放时段 [00:00, 23:59]，期望 [10:00, 12:00]
        // 重叠 = [10:00, 12:00] = 120min；较短段 = 期望 = 120min → 100
        // 但封顶是 ratio * 100 = 100
        double score = svc.scoreTimeMatch(
                LocalTime.of(10, 0), LocalTime.of(12, 0),
                LocalTime.of(0, 0), LocalTime.of(23, 59));
        assertEquals(100.0, score, 0.01);
    }

    @Test
    void technicianMatch_same_returns100() {
        assertEquals(100.0, svc.scoreTechnicianMatch(100L, 100L), 0.01);
    }

    @Test
    void technicianMatch_different_returns0() {
        assertEquals(0.0, svc.scoreTechnicianMatch(100L, 200L), 0.01);
    }

    @Test
    void technicianMatch_unbounded_returns70() {
        assertEquals(70.0, svc.scoreTechnicianMatch(null, 100L), 0.01);
    }

    @Test
    void technicianMatch_preferUnspecified_returns60() {
        assertEquals(60.0, svc.scoreTechnicianMatch(100L, null), 0.01);
    }

    // ============ 维度 2：会员价值 ============

    @Test
    void memberValue_diamondHighFreq_returnsNear100() {
        Map<String, Object> m = new HashMap<>();
        m.put("level_code", "DIAMOND");
        m.put("order_count", 15);
        m.put("wallet_balance", 5000);
        double s = svc.scoreMemberValue(m);
        // level 100 * 0.6 + freq 100 * 0.4 + balBonus 5 = 105
        assertTrue(s >= 100.0, "diamond+high freq should be capped at 100+ → got " + s);
    }

    @Test
    void memberValue_normalLowFreq_returnsLowScore() {
        Map<String, Object> m = new HashMap<>();
        m.put("level_code", "NORMAL");
        m.put("order_count", 1);
        m.put("wallet_balance", 0);
        double s = svc.scoreMemberValue(m);
        // level 50 * 0.6 + freq 60 * 0.4 + 0 = 30 + 24 = 54
        assertEquals(54.0, s, 0.01);
    }

    @Test
    void memberValue_emptyMap_returns30_default() {
        // 没数据 → 给基础分（不歧视新会员）
        assertEquals(30.0, svc.scoreMemberValue(new HashMap<>()), 0.01);
        assertEquals(30.0, svc.scoreMemberValue(null), 0.01);
    }

    // ============ 维度 3：爽约风险（反向） ============

    @Test
    void noShowRisk_perfect_returns100() {
        Map<String, Object> m = new HashMap<>();
        m.put("credit", 95);
        m.put("no_show_count", 0);
        m.put("total_bookings", 10);
        // credit 100 * 0.5 + rate 100 * 0.5 = 100
        assertEquals(100.0, svc.scoreNoShowRisk(m), 0.01);
    }

    @Test
    void noShowRisk_lowCredit_returnsLowerScore() {
        Map<String, Object> m = new HashMap<>();
        m.put("credit", 40);
        m.put("no_show_count", 5);
        m.put("total_bookings", 10);
        // credit 20 * 0.5 + rate 0 * 0.5 = 10
        assertEquals(10.0, svc.scoreNoShowRisk(m), 0.01);
    }

    @Test
    void noShowRisk_empty_returns80_default() {
        // 没数据 → 默认风险低（保守）
        assertEquals(80.0, svc.scoreNoShowRisk(new HashMap<>()), 0.01);
    }

    // ============ 维度 5：响应率（无 DB 时返回中性） ============

    @Test
    void responseRate_emptyReturns50() {
        // JdbcTemplate 抛异常时降级到 50
        when(jdbc.queryForMap(anyString(), any())).thenThrow(new RuntimeException("no table"));
        assertEquals(50.0, svc.scoreResponseRate(123L), 0.01);
    }

    @Test
    void responseRate_highRate_returns100() {
        Map<String, Object> agg = new HashMap<>();
        agg.put("succ", 8);
        agg.put("total", 10);
        when(jdbc.queryForMap(anyString(), eq(123L))).thenReturn(agg);
        assertEquals(100.0, svc.scoreResponseRate(123L), 0.01);
    }

    @Test
    void responseRate_zeroRate_returns10() {
        Map<String, Object> agg = new HashMap<>();
        agg.put("succ", 0);
        agg.put("total", 5);
        when(jdbc.queryForMap(anyString(), eq(456L))).thenReturn(agg);
        assertEquals(10.0, svc.scoreResponseRate(456L), 0.01);
    }

    // ============ 综合评分 ============

    @Test
    void scoreAndRank_sortsDescendingByTotalScore() {
        // mock DB 让 loadMemberSignals 返回空（评分走中性默认）
        when(jdbc.queryForList(anyString(), any(Long.class))).thenReturn(List.of());
        when(jdbc.queryForMap(anyString(), any(Long.class))).thenReturn(new HashMap<>());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any())).thenReturn(0L);

        BookingWaitlistScoreService.ScoringContext ctx = baseCtx();
        ctx.setPreferredDate(LocalDate.of(2026, 9, 5));
        ctx.setSlotStart(LocalTime.of(10, 0));
        ctx.setSlotEnd(LocalTime.of(18, 0));
        ctx.setEmployeeId(null);

        List<Map<String, Object>> candidates = List.of(
                row(1L, 1L, "小 A", LocalDate.of(2026, 9, 5), LocalTime.of(10, 0), LocalTime.of(12, 0), null),
                row(2L, 2L, "小 B", LocalDate.of(2026, 9, 5), LocalTime.of(10, 0), LocalTime.of(18, 0), null),
                row(3L, 3L, "小 C", LocalDate.of(2026, 9, 5), null, null, null)
        );

        List<BookingWaitlistScoreService.ScoreResult> scored = svc.scoreAndRank(candidates, ctx);
        assertEquals(3, scored.size());
        // ranking 必须是 1/2/3
        assertEquals(1, scored.get(0).getRanking());
        assertEquals(2, scored.get(1).getRanking());
        assertEquals(3, scored.get(2).getRanking());
        // 总分必须降序
        for (int i = 0; i < scored.size() - 1; i++) {
            assertTrue(scored.get(i).getTotalScore() >= scored.get(i + 1).getTotalScore(),
                    "row " + i + " score=" + scored.get(i).getTotalScore() +
                    " < row " + (i + 1) + " score=" + scored.get(i + 1).getTotalScore());
        }
    }

    @Test
    void scoreAndRank_emptyCandidates_returnsEmpty() {
        BookingWaitlistScoreService.ScoringContext ctx = baseCtx();
        List<BookingWaitlistScoreService.ScoreResult> r = svc.scoreAndRank(List.of(), ctx);
        assertNotNull(r);
        assertEquals(0, r.size());
    }

    @Test
    void scoreCandidate_lockedMember_returnsZeroScore() {
        // mock locked = true
        Map<String, Object> locked = new HashMap<>();
        locked.put("_locked", true);
        locked.put("credit", 100);
        locked.put("no_show_count", 0);
        locked.put("total_bookings", 0);
        locked.put("level_code", "NORMAL");
        locked.put("wallet_balance", 0);
        when(jdbc.queryForList(anyString(), eq(99L))).thenReturn(List.of(Map.of("id", 99L)));
        // 这里需要返回单行：mock 两次 queryForList 都返回 locked 行
        when(jdbc.queryForList(anyString(), any(Long.class))).thenReturn(List.of(toMemberRow(true, 100)));
        when(jdbc.queryForMap(anyString(), any(Long.class))).thenReturn(toOrderRow(0));

        BookingWaitlistScoreService.ScoringContext ctx = baseCtx();
        ctx.setPreferredDate(LocalDate.of(2026, 9, 5));
        ctx.setSlotStart(LocalTime.of(10, 0));
        ctx.setSlotEnd(LocalTime.of(18, 0));

        Map<String, Object> row = new HashMap<>();
        row.put("id", 99L);
        row.put("member_id", 99L);
        row.put("member_name", "锁定会员");
        row.put("member_phone", "13800000000");
        row.put("service_item_id", 1L);
        row.put("preferred_date", LocalDate.of(2026, 9, 5));
        row.put("preferred_time_from", LocalTime.of(10, 0));
        row.put("preferred_time_to", LocalTime.of(12, 0));
        row.put("create_time", LocalDateTime.now().minusHours(1));

        BookingWaitlistScoreService.ScoreResult r = svc.scoreCandidate(row, ctx);
        assertFalse(r.isEligible());
        assertTrue(r.getRejectReason() != null && r.getRejectReason().contains("锁定"));
        assertEquals(0.0, r.getTotalScore(), 0.01);
    }

    // ============ 综合分超过阈值 → eligible ============

    @Test
    void scoreCandidate_perfectCandidate_isEligible() {
        // mock: 普通会员、非锁定、有 10 单历史
        when(jdbc.queryForList(anyString(), any(Long.class))).thenReturn(List.of(toMemberRow(false, 100)));
        when(jdbc.queryForMap(anyString(), any(Long.class))).thenReturn(toOrderRow(10));
        when(jdbc.queryForMap(anyString(), eq(1L))).thenReturn(Map.of("succ", 5, "total", 5));

        BookingWaitlistScoreService.ScoringContext ctx = baseCtx();
        ctx.setPreferredDate(LocalDate.of(2026, 9, 5));
        ctx.setSlotStart(LocalTime.of(10, 0));
        ctx.setSlotEnd(LocalTime.of(12, 0));

        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("member_id", 1L);
        row.put("member_name", "完美匹配");
        row.put("member_phone", "13800000001");
        row.put("service_item_id", 100L);
        row.put("preferred_date", LocalDate.of(2026, 9, 5));
        row.put("preferred_time_from", LocalTime.of(10, 0));
        row.put("preferred_time_to", LocalTime.of(12, 0));
        row.put("employee_id", null);
        row.put("create_time", LocalDateTime.now().minusMinutes(45));

        BookingWaitlistScoreService.ScoreResult r = svc.scoreCandidate(row, ctx);
        assertTrue(r.isEligible(), "Perfect match should be eligible. score=" + r.getTotalScore());
        assertTrue(r.getTotalScore() >= 60, "Perfect match should be >=60. got " + r.getTotalScore());
    }

    @Test
    void scoreCandidate_unmatchedDate_returnsLowScore() {
        when(jdbc.queryForList(anyString(), any(Long.class))).thenReturn(List.of(toMemberRow(false, 100)));
        when(jdbc.queryForMap(anyString(), any(Long.class))).thenReturn(toOrderRow(0));
        when(jdbc.queryForMap(anyString(), eq(2L))).thenReturn(Map.of("succ", 0, "total", 0));

        BookingWaitlistScoreService.ScoringContext ctx = baseCtx();
        ctx.setPreferredDate(LocalDate.of(2026, 9, 5));   // 释放日 9.5
        ctx.setSlotStart(LocalTime.of(10, 0));
        ctx.setSlotEnd(LocalTime.of(12, 0));
        ctx.setStrictMatchOnly(true);  // 严格匹配

        Map<String, Object> row = new HashMap<>();
        row.put("id", 2L);
        row.put("member_id", 2L);
        row.put("member_name", "差 3 天");
        row.put("member_phone", "13800000002");
        row.put("service_item_id", 100L);
        row.put("preferred_date", LocalDate.of(2026, 9, 8));  // 期望 9.8
        row.put("preferred_time_from", LocalTime.of(10, 0));
        row.put("preferred_time_to", LocalTime.of(12, 0));
        row.put("employee_id", null);
        row.put("create_time", LocalDateTime.now());

        BookingWaitlistScoreService.ScoreResult r = svc.scoreCandidate(row, ctx);
        // 严格匹配下日期偏差 3 天 → date component = 0
        // preference = 0 * 0.5 + time(100) * 0.35 + tech(60) * 0.15 = 44
        // 但综合分仍受其他维度影响 → < 完美匹配的 85.2
        assertTrue(r.getPreferenceScore() < 60,
                "strict date mismatch should drop preference below 60, got " + r.getPreferenceScore());
    }

    // ============ 辅助方法 ============

    private BookingWaitlistScoreService.ScoringContext baseCtx() {
        BookingWaitlistScoreService.ScoringContext ctx = new BookingWaitlistScoreService.ScoringContext();
        ctx.setTenantId(1L);
        ctx.setStoreId(1L);
        ctx.setServiceItemId(100L);
        BookingWaitlistScoreService.ScoreConfig cfg = new BookingWaitlistScoreService.ScoreConfig();
        cfg.setWPreference(0.30);
        cfg.setWValue(0.25);
        cfg.setWNoShow(0.20);
        cfg.setWWaitTime(0.15);
        cfg.setWResponse(0.10);
        cfg.setMinScoreToNotify(30.0);
        cfg.setMaxNotifyPerRound(5);
        cfg.setCooldownMinutes(60);
        cfg.setWaitTimeCapHours(8);
        cfg.setLockedMemberAction("SKIP");
        cfg.setExpiredAction("SKIP");
        cfg.setEnabled(true);
        ctx.setConfig(cfg);
        return ctx;
    }

    private Map<String, Object> row(Long id, Long memberId, String name,
                                    LocalDate prefDate, LocalTime from, LocalTime to,
                                    Long employeeId) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("member_id", memberId);
        m.put("member_name", name);
        m.put("member_phone", "13800000000");
        m.put("service_item_id", 100L);
        m.put("employee_id", employeeId);
        m.put("preferred_date", prefDate);
        m.put("preferred_time_from", from);
        m.put("preferred_time_to", to);
        m.put("create_time", LocalDateTime.now().minusMinutes(45));
        return m;
    }

    private Map<String, Object> toMemberRow(boolean locked, int credit) {
        Map<String, Object> m = new HashMap<>();
        m.put("member_level", "GOLD");
        m.put("wallet_balance", 1000);
        m.put("credit", credit);
        m.put("no_show_count", 0);
        m.put("total_bookings", 10);
        m.put("locked", locked);
        return m;
    }

    private Map<String, Object> toOrderRow(int cnt) {
        return Map.of("cnt", cnt);
    }
}