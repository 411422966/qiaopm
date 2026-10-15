package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3 · LbsRouteOptimizer 路线优化算法单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>Haversine 距离计算</li>
 *   <li>圆形区域点包含判断</li>
 *   <li>技能匹配评分</li>
 *   <li>距离评分（归一化）</li>
 *   <li>负荷评分</li>
 *   <li>时间窗评分</li>
 *   <li>综合派单评分</li>
 *   <li>最近邻 TSP 路线优化</li>
 *   <li>上门费计算</li>
 * </ol>
 *
 * @author Cursor Agent
 * @since 2026-09-05 (Phase 3 上门服务 LBS 派单)
 */
class LbsRouteOptimizerTest {

    private final LbsRouteOptimizer optimizer = new LbsRouteOptimizer();

    // ====================================================================
    // 1. Haversine 距离
    // ====================================================================

    @Test
    void haversine_samePoint_returnsZero() {
        double d = optimizer.haversine(39.9042, 116.4074, 39.9042, 116.4074);
        assertEquals(0.0, d, 0.001);
    }

    @Test
    void haversine_knownDistance_beijingToShanghai() {
        // 北京 (39.9042, 116.4074) → 上海 (31.2304, 121.4737)
        // 直线约 1068 km
        double d = optimizer.haversine(39.9042, 116.4074, 31.2304, 121.4737);
        assertTrue(d > 1000 && d < 1200, "Beijing-Shanghai should be ~1068km, got " + d);
    }

    @Test
    void haversine_shortDistance_1km() {
        // 同一纬度，1 度经度约 111km * cos(lat)
        // 北京附近 0.01 度 ≈ 0.9km
        double d = optimizer.haversine(39.9042, 116.4074, 39.9042, 116.4164);
        assertTrue(d < 1.5, "0.009度经度差 should be <1.5km, got " + d);
    }

    @Test
    void haversine_withBigDecimal() {
        double d = optimizer.haversine(
                new BigDecimal("39.904200"),
                new BigDecimal("116.407400"),
                new BigDecimal("39.904200"),
                new BigDecimal("116.416400"));
        assertTrue(d < 1.5, "got " + d);
    }

    // ====================================================================
    // 2. 圆形区域点包含
    // ====================================================================

    @Test
    void isInsideCircle_center_returnsTrue() {
        assertTrue(optimizer.isInsideCircle(39.9042, 116.4074, 39.9042, 116.4074, 10.0));
    }

    @Test
    void isInsideCircle_withinRadius_returnsTrue() {
        // 北京 CBD 内一点（距离约 3km）
        assertTrue(optimizer.isInsideCircle(39.914, 116.417, 39.9042, 116.4074, 10.0));
    }

    @Test
    void isInsideCircle_outsideRadius_returnsFalse() {
        // 北京 → 上海（>1000km），肯定在 10km 圆外
        assertFalse(optimizer.isInsideCircle(31.2304, 121.4737, 39.9042, 116.4074, 10.0));
    }

    @Test
    void isInsideCircle_onBoundary_returnsTrue() {
        // 正好在圆上（约 10km 处）
        // 北京 (39.9042, 116.4074) 向东 0.09 度约 8.5km，仍在 10km 内
        assertTrue(optimizer.isInsideCircle(39.9042, 116.497, 39.9042, 116.4074, 10.0));
    }

    // ====================================================================
    // 3. 技能匹配评分
    // ====================================================================

    @Test
    void scoreSkillMatch_allMatch_returns100() {
        Set<String> tech = new java.util.HashSet<>(Arrays.asList("MANICURE", "PEDICURE", "EYELASH"));
        Set<String> req  = new java.util.HashSet<>(Arrays.asList("MANICURE", "PEDICURE", "EYELASH"));
        assertEquals(100.0, optimizer.scoreSkillMatch(tech, req), 0.01);
    }

    @Test
    void scoreSkillMatch_partialMatch_returnsProportional() {
        Set<String> tech = new java.util.HashSet<>(Arrays.asList("MANICURE", "PEDICURE"));
        Set<String> req  = new java.util.HashSet<>(Arrays.asList("MANICURE", "PEDICURE", "EYELASH", "FACIAL"));
        // 2/4 = 50%
        assertEquals(50.0, optimizer.scoreSkillMatch(tech, req), 0.01);
    }

    @Test
    void scoreSkillMatch_noMatch_returnsLow() {
        Set<String> tech = new java.util.HashSet<>(Arrays.asList("MASSAGE", "SPA"));
        Set<String> req  = new java.util.HashSet<>(Arrays.asList("MANICURE"));
        assertTrue(optimizer.scoreSkillMatch(tech, req) < 20);
    }

    @Test
    void scoreSkillMatch_noRequiredTags_returns80() {
        // 订单无技能要求，技师中性得 80
        Set<String> tech = new java.util.HashSet<>(Arrays.asList("MANICURE"));
        assertEquals(80.0, optimizer.scoreSkillMatch(tech, null), 0.01);
        assertEquals(80.0, optimizer.scoreSkillMatch(tech, new java.util.HashSet<>()), 0.01);
    }

    @Test
    void scoreSkillMatch_noTechSkills_returnsLow() {
        Set<String> req = new java.util.HashSet<>(Arrays.asList("MANICURE"));
        assertTrue(optimizer.scoreSkillMatch(null, req) < 20);
        assertTrue(optimizer.scoreSkillMatch(new java.util.HashSet<>(), req) < 20);
    }

    // ====================================================================
    // 4. 距离评分（归一化）
    // ====================================================================

    @Test
    void scoreDistance_zero_returns100() {
        assertEquals(100.0, optimizer.scoreDistance(0, 10.0), 0.01);
    }

    @Test
    void scoreDistance_atMaxRadius_returns0() {
        assertEquals(0.0, optimizer.scoreDistance(10.0, 10.0), 0.01);
    }

    @Test
    void scoreDistance_halfRadius_returns50() {
        assertEquals(50.0, optimizer.scoreDistance(5.0, 10.0), 0.01);
    }

    @Test
    void scoreDistance_beyondMax_returnsNegative() {
        // 超过最大范围，分数为负（会被 eligible 过滤）
        assertTrue(optimizer.scoreDistance(15.0, 10.0) < 0);
    }

    @Test
    void scoreDistance_zeroMaxRadius_returns50_neutral() {
        assertEquals(50.0, optimizer.scoreDistance(5.0, 0), 0.01);
    }

    // ====================================================================
    // 5. 负荷评分
    // ====================================================================

    @Test
    void scoreLoad_empty_returns100() {
        assertEquals(100.0, optimizer.scoreLoad(0, 8), 0.01);
    }

    @Test
    void scoreLoad_halfFull_returns50() {
        assertEquals(50.0, optimizer.scoreLoad(4, 8), 0.01);
    }

    @Test
    void scoreLoad_full_returns0() {
        assertEquals(0.0, optimizer.scoreLoad(8, 8), 0.01);
    }

    @Test
    void scoreLoad_overCapacity_returnsNegative() {
        assertTrue(optimizer.scoreLoad(10, 8) < 0);
    }

    @Test
    void scoreLoad_zeroMax_returns50_neutral() {
        assertEquals(50.0, optimizer.scoreLoad(5, 0), 0.01);
    }

    // ====================================================================
    // 6. 时间窗评分
    // ====================================================================

    @Test
    void scoreTimeWindow_withinWindow_returns100() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 5, 14, 0);
        assertEquals(100.0, optimizer.scoreTimeWindow(t,
                LocalTime.of(9, 0), LocalTime.of(21, 0)), 0.01);
    }

    @Test
    void scoreTimeWindow_beforeStart_returns0() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 5, 7, 0);
        assertEquals(0.0, optimizer.scoreTimeWindow(t,
                LocalTime.of(9, 0), LocalTime.of(21, 0)), 0.01);
    }

    @Test
    void scoreTimeWindow_afterEnd_returns0() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 5, 22, 0);
        assertEquals(0.0, optimizer.scoreTimeWindow(t,
                LocalTime.of(9, 0), LocalTime.of(21, 0)), 0.01);
    }

    @Test
    void scoreTimeWindow_nearBoundary_returns60() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 5, 8, 45); // 距 9:00 还有 15 分钟
        assertEquals(60.0, optimizer.scoreTimeWindow(t,
                LocalTime.of(9, 0), LocalTime.of(21, 0)), 0.01);
    }

    @Test
    void scoreTimeWindow_noLimits_returns80() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 5, 23, 0);
        assertEquals(80.0, optimizer.scoreTimeWindow(t, null, null), 0.01);
    }

    // ====================================================================
    // 7. 综合派单评分（集成测试）
    // ====================================================================

    @Test
    void scoreDispatch_perfectMatch_isEligible() {
        LbsRouteOptimizer.DispatchConfig cfg = new LbsRouteOptimizer.DispatchConfig();
        cfg.wDistance = 0.35; cfg.wSkill = 0.25; cfg.wRating = 0.15;
        cfg.wLoad = 0.15; cfg.wTime = 0.10;
        cfg.minDispatchScore = 40.0;

        com.meiyetec.entity.LbsServiceOrder order = new com.meiyetec.entity.LbsServiceOrder();
        order.setId(1L);
        order.setOrderNo("TEST001");
        order.setLat(new BigDecimal("39.9042"));
        order.setLng(new BigDecimal("116.4074"));
        order.setScheduledTime(LocalDateTime.of(2026, 9, 5, 14, 0));

        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(1L);
        tech.setName("张技师");
        tech.setLat(new BigDecimal("39.9050"));
        tech.setLng(new BigDecimal("116.4080"));
        tech.setMaxRadiusKm(new BigDecimal("10"));
        tech.setRating(new BigDecimal("5.0"));
        tech.setTravelSpeedKmh(new BigDecimal("25.0"));
        tech.setWorkStart(LocalTime.of(9, 0));
        tech.setWorkEnd(LocalTime.of(21, 0));
        tech.setMaxOrdersPerDay(8);
        tech.setTodayOrderCount(0);
        tech.setSkillTags(new java.util.HashSet<>(Arrays.asList("MANICURE")));
        tech.setSkillLevels(new java.util.HashMap<>());

        Set<String> required = new java.util.HashSet<>(Arrays.asList("MANICURE"));
        LbsRouteOptimizer.DispatchScoreResult r = optimizer.scoreDispatch(order, tech, cfg, required);

        assertTrue(r.isEligible(), "Perfect match should be eligible, got score=" + r.getTotalScore());
        assertTrue(r.getTotalScore() >= 60, "Total score should be >=60, got " + r.getTotalScore());
        assertNotNull(r.getTravelFee());
        assertTrue(r.getEtaMinutes() >= 0);
    }

    @Test
    void scoreDispatch_farDistance_lowScore() {
        LbsRouteOptimizer.DispatchConfig cfg = new LbsRouteOptimizer.DispatchConfig();
        cfg.wDistance = 0.35; cfg.wSkill = 0.25; cfg.wRating = 0.15;
        cfg.wLoad = 0.15; cfg.wTime = 0.10;
        cfg.minDispatchScore = 40.0;

        com.meiyetec.entity.LbsServiceOrder order = new com.meiyetec.entity.LbsServiceOrder();
        order.setId(2L);
        order.setOrderNo("TEST002");
        order.setLat(new BigDecimal("39.9042")); // 北京
        order.setLng(new BigDecimal("116.4074"));
        order.setScheduledTime(LocalDateTime.of(2026, 9, 5, 14, 0));

        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(2L);
        tech.setName("远距离技师");
        tech.setLat(new BigDecimal("31.2304")); // 上海 — 距离 ~1068km
        tech.setLng(new BigDecimal("121.4737"));
        tech.setMaxRadiusKm(new BigDecimal("10"));
        tech.setRating(new BigDecimal("5.0"));
        tech.setTravelSpeedKmh(new BigDecimal("25.0"));
        tech.setWorkStart(LocalTime.of(9, 0));
        tech.setWorkEnd(LocalTime.of(21, 0));
        tech.setMaxOrdersPerDay(8);
        tech.setTodayOrderCount(0);
        tech.setSkillTags(new java.util.HashSet<>());
        tech.setSkillLevels(new java.util.HashMap<>());

        LbsRouteOptimizer.DispatchScoreResult r = optimizer.scoreDispatch(order, tech, cfg, new java.util.HashSet<>());
        assertTrue(r.getDistanceKm() > 1000, "Beijing-Shanghai should be >1000km, got " + r.getDistanceKm());
        assertTrue(r.getDistanceScore() < 0, "Far distance should have negative score, got " + r.getDistanceScore());
    }

    @Test
    void scoreDispatch_skillMismatch_reducesScore() {
        LbsRouteOptimizer.DispatchConfig cfg = new LbsRouteOptimizer.DispatchConfig();
        cfg.wDistance = 0.35; cfg.wSkill = 0.25; cfg.wRating = 0.15;
        cfg.wLoad = 0.15; cfg.wTime = 0.10;
        cfg.minDispatchScore = 40.0;

        com.meiyetec.entity.LbsServiceOrder order = new com.meiyetec.entity.LbsServiceOrder();
        order.setId(3L);
        order.setLat(new BigDecimal("39.9042"));
        order.setLng(new BigDecimal("116.4074"));
        order.setScheduledTime(LocalDateTime.of(2026, 9, 5, 14, 0));

        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(3L);
        tech.setName("不匹配技师");
        tech.setLat(new BigDecimal("39.9043")); // 很近
        tech.setLng(new BigDecimal("116.4075"));
        tech.setMaxRadiusKm(new BigDecimal("10"));
        tech.setRating(new BigDecimal("5.0"));
        tech.setTravelSpeedKmh(new BigDecimal("25.0"));
        tech.setWorkStart(LocalTime.of(9, 0));
        tech.setWorkEnd(LocalTime.of(21, 0));
        tech.setMaxOrdersPerDay(8);
        tech.setTodayOrderCount(0);
        tech.setSkillTags(new java.util.HashSet<>(Arrays.asList("MASSAGE")));
        tech.setSkillLevels(new java.util.HashMap<>());

        Set<String> required = new java.util.HashSet<>(Arrays.asList("MANICURE"));
        LbsRouteOptimizer.DispatchScoreResult r = optimizer.scoreDispatch(order, tech, cfg, required);
        // 技能 0% 匹配 → skillScore ≈ 0
        assertEquals(0.0, r.getSkillScore(), 0.01);
        // 但距离极近，距离分接近 100
        assertTrue(r.getDistanceScore() > 90, "Near tech should have high distance score, got " + r.getDistanceScore());
    }

    // ====================================================================
    // 8. 最近邻 TSP 路线优化
    // ====================================================================

    @Test
    void optimizeRoute_singleOrder_createsOneNode() {
        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(1L);
        tech.setName("测试技师");
        tech.setLat(new BigDecimal("39.9042"));
        tech.setLng(new BigDecimal("116.4074"));
        tech.setTravelSpeedKmh(new BigDecimal("25.0"));

        com.meiyetec.entity.LbsServiceOrder o = new com.meiyetec.entity.LbsServiceOrder();
        o.setId(1L);
        o.setLat(new BigDecimal("39.9142"));
        o.setLng(new BigDecimal("116.4174"));

        LbsRouteOptimizer.RouteResult route = optimizer.optimizeRoute(tech, List.of(o), 90, 15);

        assertEquals(1, route.getTotalOrders());
        assertEquals(1, route.getNodes().size());
        assertEquals(1, route.getNodes().get(0).getVisitOrder());
        assertTrue(route.getTotalDistanceKm() > 0);
        assertTrue(route.getTotalTravelMinutes() > 0);
    }

    @Test
    void optimizeRoute_emptyOrders_returnsEmpty() {
        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(1L);
        tech.setLat(new BigDecimal("39.9042"));
        tech.setLng(new BigDecimal("116.4074"));
        tech.setTravelSpeedKmh(new BigDecimal("25.0"));

        LbsRouteOptimizer.RouteResult route = optimizer.optimizeRoute(tech, List.of(), 90, 15);

        assertEquals(0, route.getTotalOrders());
        assertTrue(route.getNodes().isEmpty());
    }

    @Test
    void optimizeRoute_nearestNeighbor_prefersCloser() {
        // 技师在 A；B 离 A 1km，C 离 A 5km
        // 路线应为 A→B→C
        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(1L);
        tech.setLat(new BigDecimal("39.9042"));   // A = (39.9042, 116.4074)
        tech.setLng(new BigDecimal("116.4074"));
        tech.setTravelSpeedKmh(new BigDecimal("60.0")); // 60km/h → 1km = 1min

        com.meiyetec.entity.LbsServiceOrder b = new com.meiyetec.entity.LbsServiceOrder();
        b.setId(1L);
        b.setLat(new BigDecimal("39.9130"));  // ~1km away
        b.setLng(new BigDecimal("116.4074"));

        com.meiyetec.entity.LbsServiceOrder c = new com.meiyetec.entity.LbsServiceOrder();
        c.setId(2L);
        c.setLat(new BigDecimal("39.9500"));  // ~5km away
        c.setLng(new BigDecimal("116.4074"));

        // 打乱顺序，验证 NN 算法会重排
        LbsRouteOptimizer.RouteResult route = optimizer.optimizeRoute(tech, List.of(c, b), 60, 10);

        assertEquals(2, route.getNodes().size());
        // 第一站应该是 B（更近）
        assertEquals(1L, route.getNodes().get(0).getOrderId());
        assertEquals(2L, route.getNodes().get(1).getOrderId());
    }

    @Test
    void optimizeRoute_gainVsRandom_isPositiveOrZero() {
        LbsRouteOptimizer.TechnicianSignal tech = new LbsRouteOptimizer.TechnicianSignal();
        tech.setId(1L);
        tech.setLat(new BigDecimal("39.9042"));
        tech.setLng(new BigDecimal("116.4074"));
        tech.setTravelSpeedKmh(new BigDecimal("30.0"));

        List<com.meiyetec.entity.LbsServiceOrder> orders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            com.meiyetec.entity.LbsServiceOrder o = new com.meiyetec.entity.LbsServiceOrder();
            o.setId((long) (i + 1));
            o.setLat(new BigDecimal(String.format("39.90%02d", i + 1)));
            o.setLng(new BigDecimal("116.40" + String.format("%02d", i + 1)));
            orders.add(o);
        }

        LbsRouteOptimizer.RouteResult route = optimizer.optimizeRoute(tech, orders, 60, 10);
        // 多次运行，因为随机基线有随机性，gain 可正可零
        assertTrue(route.getTotalOrders() == 5);
        assertTrue(route.getNodes().size() == 5);
        assertTrue(route.getTotalDistanceKm() > 0);
        // gain_km[0] 是节省的里程，应 >= 0
        assertTrue(route.getGainKmVsRandom()[0] >= 0, "Gain should be >=0, got " + route.getGainKmVsRandom()[0]);
        // gain_km[1] 是随机排序里程
        assertTrue(route.getGainKmVsRandom()[1] > 0);
    }

    // ====================================================================
    // 9. 上门费计算
    // ====================================================================

    @Test
    void calcTravelFee_normalRange() {
        BigDecimal fee = optimizer.calcTravelFee(
                new BigDecimal("30"), new BigDecimal("2"), null, null, 5.0);
        // 30 + 5*2 = 40
        assertEquals(new BigDecimal("40.00"), fee);
    }

    @Test
    void calcTravelFee_belowMin_usesMin() {
        // 30 + 1*2 = 32，但 min=50
        BigDecimal fee = optimizer.calcTravelFee(
                new BigDecimal("30"), new BigDecimal("2"), new BigDecimal("50"), null, 1.0);
        assertEquals(new BigDecimal("50.00"), fee);
    }

    @Test
    void calcTravelFee_aboveMax_usesMax() {
        // 30 + 200*2 = 430，但 max=200
        BigDecimal fee = optimizer.calcTravelFee(
                new BigDecimal("30"), new BigDecimal("2"), null, new BigDecimal("200"), 200.0);
        assertEquals(new BigDecimal("200.00"), fee);
    }

    @Test
    void calcTravelFee_nullBase_usesZero() {
        BigDecimal fee = optimizer.calcTravelFee(
                null, new BigDecimal("2"), null, null, 10.0);
        // 0 + 10*2 = 20
        assertEquals(new BigDecimal("20.00"), fee);
    }
}
