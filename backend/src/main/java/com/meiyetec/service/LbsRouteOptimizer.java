package com.meiyetec.service;

import com.meiyetec.entity.LbsServiceOrder;
import com.meiyetec.entity.LbsTechnician;
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
import java.util.stream.Collectors;

/**
 * Phase 3 · LBS 路线优化引擎
 *
 * <p>提供上门服务派单的核心算法：</p>
 * <ol>
 *   <li>Haversine 距离计算</li>
 *   <li>最近邻（Nearest Neighbor）TSP 路线排序</li>
 *   <li>5 维度派单评分：距离 / 技能 / 评分 / 负荷 / 时间窗</li>
 *   <li>圆形区域点包含判断</li>
 *   <li>技能标签匹配</li>
 *   <li>服务区域上门费计算</li>
 * </ol>
 *
 * <p>所有计算纯内存执行，不写库；写库由 {@link LbsDispatchService} 负责。</p>
 *
 * @author Cursor Agent
 * @since 2026-09-05 (Phase 3 上门服务 LBS 派单)
 */
@Slf4j
@Service
public class LbsRouteOptimizer {

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 核心数据类型
    // ====================================================================

    /** 派单评分配置（来自 lbs_dispatch_config） */
    @Data
    public static class DispatchConfig {
        private double wDistance = 0.35;
        private double wSkill   = 0.25;
        private double wRating   = 0.15;
        private double wLoad     = 0.15;
        private double wTime     = 0.10;
        private int    maxOrdersPerDay = 8;
        private int    defaultServiceMinutes = 90;
        private int    bufferMinutes = 15;
        private double minDispatchScore = 40.0;
        private boolean strictZoneMatch = false;
        private boolean fallbackToNearest = true;
    }

    /** 技师可派单信号（内存缓存，避免每次 DB 查询） */
    @Data
    public static class TechnicianSignal {
        private Long id;
        private String name;
        private String phone;
        private BigDecimal lat;
        private BigDecimal lng;
        private String onlineStatus;
        private Set<String> skillTags;
        private Map<String, Integer> skillLevels;
        private BigDecimal rating;
        private BigDecimal maxRadiusKm;
        private Integer available;
        private LocalDateTime lastLocTime;
        private Integer maxOrdersPerDay;
        private Integer avgServiceMinutes;
        private BigDecimal travelSpeedKmh;
        private LocalTime workStart;
        private LocalTime workEnd;
        private int todayOrderCount;
    }

    /** 单个订单派单评分结果 */
    @Data
    public static class DispatchScoreResult {
        private Long orderId;
        private String orderNo;
        private Long technicianId;
        private String technicianName;
        private Double distanceKm;
        private Double skillScore;      // 0..100
        private Double distanceScore;  // 0..100
        private Double ratingScore;    // 0..100
        private Double loadScore;      // 0..100
        private Double timeScore;      // 0..100
        private Double totalScore;     // 0..100
        private boolean eligible;
        private String rejectReason;
        private BigDecimal travelFee;
        private Integer etaMinutes;
    }

    /** 路线节点（TSP 路线中的一个订单） */
    @Data
    public static class RouteNode {
        private Long orderId;
        private String orderNo;
        private BigDecimal lat;
        private BigDecimal lng;
        private LocalDateTime scheduledTime;
        private int visitOrder;
        private double distanceFromPrev;  // km
        private int travelMinutesFromPrev;
        private LocalDateTime etaArrival;
        private LocalDateTime etaComplete;
    }

    /** 路线规划结果 */
    @Data
    public static class RouteResult {
        private Long technicianId;
        private String technicianName;
        private List<RouteNode> nodes;
        private double totalDistanceKm;
        private int totalTravelMinutes;
        private int totalServiceMinutes;
        private int totalOrders;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private double totalTravelFee;
        private double[] gainKmVsRandom;  // 与随机排序相比节省的里程
    }

    // ====================================================================
    // 1. Haversine 距离计算
    // ====================================================================

    /**
     * 计算两点之间的 Haversine 距离（单位：km）。
     * Earth radius = 6371 km.
     */
    public double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public double haversine(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        return haversine(
                lat1.doubleValue(), lng1.doubleValue(),
                lat2.doubleValue(), lng2.doubleValue());
    }

    // ====================================================================
    // 2. 圆形区域点包含判断
    // ====================================================================

    /**
     * 判断坐标 (lat, lng) 是否在圆形服务区域内。
     * @param lat   目标纬度
     * @param lng   目标经度
     * @param cLat  区域中心纬度
     * @param cLng  区域中心经度
     * @param rKm   半径 km
     */
    public boolean isInsideCircle(double lat, double lng, double cLat, double cLng, double rKm) {
        return haversine(lat, lng, cLat, cLng) <= rKm;
    }

    // ====================================================================
    // 3. 技能匹配评分
    // ====================================================================

    /**
     * 技能标签匹配评分。
     * - 有全部标签：100
     * - 部分匹配：按比例
     * - 无标签要求：默认 80
     *
     * @param techSkills 技师技能标签集合
     * @param requiredTags 订单所需技能标签集合
     */
    public double scoreSkillMatch(Set<String> techSkills, Set<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) return 80;
        if (techSkills == null || techSkills.isEmpty()) return 10;
        long matched = requiredTags.stream().filter(techSkills::contains).count();
        return (matched * 100.0) / requiredTags.size();
    }

    /**
     * 技能等级评分（1-5 → 20-100）。
     * 取技师在 requiredTags 中的最高等级。
     */
    public double scoreSkillLevel(Map<String, Integer> techLevels, Set<String> requiredTags) {
        if (techLevels == null || techLevels.isEmpty() || requiredTags == null || requiredTags.isEmpty()) {
            return 50; // 无数据给中性
        }
        int maxLevel = 0;
        for (String tag : requiredTags) {
            Integer lvl = techLevels.get(tag);
            if (lvl != null && lvl > maxLevel) maxLevel = lvl;
        }
        return Math.min(100, maxLevel * 20);
    }

    // ====================================================================
    // 4. 距离评分（归一化：0-10km → 100-0）
    // ====================================================================

    /**
     * 距离评分：距离越近分越高。10km=0，0km=100。
     */
    public double scoreDistance(double distanceKm, double maxRadiusKm) {
        if (maxRadiusKm <= 0) return 50;
        double ratio = 1 - Math.min(distanceKm, maxRadiusKm) / maxRadiusKm;
        return ratio * 100;
    }

    // ====================================================================
    // 5. 负荷评分（当日已派单数越少分越高）
    // ====================================================================

    /**
     * 负荷评分：maxOrdersPerDay=8 为基准。
     * 0 单=100，8 单=50，满额=0。
     */
    public double scoreLoad(int todayCount, int maxOrders) {
        if (maxOrders <= 0) return 50;
        double ratio = 1.0 - Math.min(todayCount, maxOrders) / (double) maxOrders;
        return ratio * 100;
    }

    // ====================================================================
    // 6. 时间窗评分
    // ====================================================================

    /**
     * 时间窗匹配评分：
     * - 技师在 [workStart, workEnd] 内：100
     * - 接近边界：80
     * - 超出：0
     * - 无限制（null）：80
     */
    public double scoreTimeWindow(LocalDateTime orderTime,
                                  LocalTime workStart, LocalTime workEnd) {
        if (workStart == null && workEnd == null) return 80;
        LocalTime t = orderTime.toLocalTime();
        if (workStart != null && t.isBefore(workStart)) return 0;
        if (workEnd != null && t.isAfter(workEnd)) return 0;
        // 边界容差 30 分钟
        if (workStart != null && t.isBefore(workStart.plusMinutes(30))) return 60;
        if (workEnd != null && t.isAfter(workEnd.minusMinutes(30))) return 60;
        return 100;
    }

    // ====================================================================
    // 7. 综合派单评分
    // ====================================================================

    /**
     * 对一个（订单，技师）对做 5 维度评分。
     *
     * @param order        订单（含 lat/lng/scheduledTime）
     * @param tech         技师信号（含所有维度数据）
     * @param cfg         派单配置
     * @param requiredTags 订单所需技能标签
     * @return 评分结果
     */
    public DispatchScoreResult scoreDispatch(LbsServiceOrder order, TechnicianSignal tech,
                                            DispatchConfig cfg, Set<String> requiredTags) {
        DispatchScoreResult r = new DispatchScoreResult();
        r.setOrderId(order.getId());
        r.setOrderNo(order.getOrderNo());
        r.setTechnicianId(tech.getId());
        r.setTechnicianName(tech.getName());

        // 距离
        double dist = haversine(order.getLat().doubleValue(), order.getLng().doubleValue(),
                tech.getLat().doubleValue(), tech.getLng().doubleValue());
        r.setDistanceKm(round(dist));
        double distScore = scoreDistance(dist, nz(tech.getMaxRadiusKm()).doubleValue());
        r.setDistanceScore(round(distScore));

        // 技能匹配
        double skillMatch = scoreSkillMatch(tech.getSkillTags(), requiredTags);
        double skillLevel = scoreSkillLevel(tech.getSkillLevels(), requiredTags);
        double skillScore = skillMatch * 0.6 + skillLevel * 0.4;
        r.setSkillScore(round(skillScore));

        // 评分（1-5 星 → 20-100）
        double rating = nz(tech.getRating()).doubleValue();
        double ratingScore = rating * 20;
        r.setRatingScore(round(ratingScore));

        // 负荷
        double loadScore = scoreLoad(tech.getTodayOrderCount(), tech.getMaxOrdersPerDay() == null ? 0 : tech.getMaxOrdersPerDay());
        r.setLoadScore(round(loadScore));

        // 时间窗
        LocalDateTime schedTime = order.getScheduledTime() != null
                ? order.getScheduledTime() : LocalDateTime.now();
        double timeScore = scoreTimeWindow(schedTime,
                tech.getWorkStart(), tech.getWorkEnd());
        r.setTimeScore(round(timeScore));

        // 综合分
        double total = distScore * cfg.wDistance
                + skillScore * cfg.wSkill
                + ratingScore * cfg.wRating
                + loadScore * cfg.wLoad
                + timeScore * cfg.wTime;
        r.setTotalScore(round(total));

        // 资格判断
        boolean eligible = total >= cfg.minDispatchScore;
        String rejectReason = null;
        if (!eligible) {
            rejectReason = "综合分 " + r.getTotalScore() + " < 阈值 " + cfg.minDispatchScore;
        }
        r.setEligible(eligible);
        r.setRejectReason(rejectReason);

        // 预计到达时间（分钟）
        double speed = nz(tech.getTravelSpeedKmh()).doubleValue();
        if (speed <= 0) speed = 25.0;
        int eta = (int) Math.round(dist / speed * 60);
        r.setEtaMinutes(eta);

        // 上门费（由服务区域决定，这里用默认公式）
        BigDecimal travelFee = BigDecimal.valueOf(dist * 2).setScale(2, RoundingMode.HALF_UP);
        r.setTravelFee(travelFee);

        return r;
    }

    // ====================================================================
    // 8. 候选技师筛选
    // ====================================================================

    /**
     * 加载可用技师信号（含当日订单数、位置、技能）。
     * @param tenantId  租户 ID
     * @param serviceDate 服务日期（用于统计当日已派单数）
     */
    public List<TechnicianSignal> loadTechnicianSignals(Long tenantId, LocalDate serviceDate) {
        String sql =
            "SELECT t.id, t.name, t.phone, t.current_lat, t.current_lng, t.online_status, " +
            "       t.max_radius_km, t.rating, t.available, t.last_loc_time, " +
            "       t.skill_tags, t.skill_levels, t.max_orders_per_day, t.avg_service_minutes, " +
            "       t.travel_speed_kmh, t.work_start, t.work_end, " +
            "       COUNT(o.id) AS today_orders " +
            "FROM lbs_technician t " +
            "LEFT JOIN lbs_service_order o ON o.technician_id = t.id " +
            "  AND DATE(o.scheduled_time) = ? " +
            "  AND o.dispatch_status NOT IN ('CANCELLED','COMPLETED') " +
            "WHERE t.deleted = 0 AND t.status = 'ACTIVE' AND t.tenant_id = ? " +
            "  AND t.online_status = 'ONLINE' " +
            "  AND t.available = 1 " +
            "  AND t.current_lat IS NOT NULL AND t.current_lng IS NOT NULL " +
            "GROUP BY t.id " +
            "HAVING today_orders < IFNULL(t.max_orders_per_day, 8) " +
            "ORDER BY t.id";
        try {
            return jdbc.queryForList(sql, serviceDate.toString(), tenantId).stream()
                    .map(this::toTechnicianSignal)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[LBS-ROUTE] loadTechnicianSignals failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private TechnicianSignal toTechnicianSignal(Map<String, Object> row) {
        TechnicianSignal s = new TechnicianSignal();
        s.setId(((Number) row.get("id")).longValue());
        s.setName(str(row.get("name"), ""));
        s.setPhone(str(row.get("phone"), ""));
        s.setLat(numB(row.get("current_lat")));
        s.setLng(numB(row.get("current_lng")));
        s.setOnlineStatus(str(row.get("online_status"), "OFFLINE"));
        s.setMaxRadiusKm(numB(row.get("max_radius_km")));
        s.setRating(numB(row.get("rating")));
        s.setAvailable(numI(row.get("available"), 1));
        s.setLastLocTime(timestamp(row.get("last_loc_time")));
        s.setMaxOrdersPerDay(numI(row.get("max_orders_per_day"), 8));
        s.setAvgServiceMinutes(numI(row.get("avg_service_minutes"), 90));
        s.setTravelSpeedKmh(numB(row.get("travel_speed_kmh")));
        s.setWorkStart(time(row.get("work_start")));
        s.setWorkEnd(time(row.get("work_end")));
        s.setTodayOrderCount(numI(row.get("today_orders"), 0));

        // skill_tags: JSON 数组字符串
        String tags = str(row.get("skill_tags"), "");
        if (tags != null && !tags.isEmpty()) {
            try {
                s.setSkillTags(new java.util.HashSet<>(
                        Arrays.asList(tags.replaceAll("[\\[\\]\"]", "").split(","))));
            } catch (Exception e) {
                s.setSkillTags(new java.util.HashSet<>());
            }
        } else {
            s.setSkillTags(new java.util.HashSet<>());
        }

        // skill_levels: JSON 对象
        s.setSkillLevels(new java.util.HashMap<>());
        return s;
    }

    // ====================================================================
    // 9. 最近邻 TSP 路线优化
    // ====================================================================

    /**
     * 对一组订单做最近邻（Nearest Neighbor）TSP 路线规划。
     *
     * <p>算法：</p>
     * <ol>
     *   <li>以技师的当前位置为起点</li>
     *   <li>每次选距离最近的未访问订单</li>
     *   <li>重复直到所有订单都被访问</li>
     *   <li>与随机排序的总里程对比，计算节省量</li>
     * </ol>
     *
     * @param tech    技师信号
     * @param orders  待排序订单（须含 lat/lng/scheduledTime）
     * @param serviceMinutes  平均服务时长（分钟）
     * @param bufferMinutes   相邻订单缓冲时间
     */
    public RouteResult optimizeRoute(TechnicianSignal tech,
                                     List<LbsServiceOrder> orders,
                                     int serviceMinutes, int bufferMinutes) {
        RouteResult result = new RouteResult();
        result.setTechnicianId(tech.getId());
        result.setTechnicianName(tech.getName());

        if (orders == null || orders.isEmpty()) {
            result.setNodes(Collections.emptyList());
            return result;
        }

        // 随机排序基线（用于计算节省量）
        List<LbsServiceOrder> shuffled = new ArrayList<>(orders);
        Collections.shuffle(shuffled);
        double randomTotalDist = calcTotalDistance(tech, shuffled);

        // 最近邻算法
        List<RouteNode> plan = new ArrayList<>();
        Set<Long> visited = new java.util.HashSet<>();
        double curLat = tech.getLat().doubleValue();
        double curLng = tech.getLng().doubleValue();
        LocalDateTime curTime = LocalDateTime.now();

        List<LbsServiceOrder> remaining = new ArrayList<>(orders);
        for (int i = 0; i < orders.size(); i++) {
            LbsServiceOrder nearest = null;
            double minDist = Double.MAX_VALUE;
            for (LbsServiceOrder o : remaining) {
                if (visited.contains(o.getId())) continue;
                double d = haversine(curLat, curLng,
                        o.getLat().doubleValue(), o.getLng().doubleValue());
                if (d < minDist) {
                    minDist = d;
                    nearest = o;
                }
            }
            if (nearest == null) break;
            visited.add(nearest.getId());

            RouteNode node = new RouteNode();
            node.setOrderId(nearest.getId());
            node.setOrderNo(nearest.getOrderNo());
            node.setLat(nearest.getLat());
            node.setLng(nearest.getLng());
            node.setScheduledTime(nearest.getScheduledTime());
            node.setVisitOrder(i + 1);
            node.setDistanceFromPrev(round(minDist));

            double speed = nz(tech.getTravelSpeedKmh()).doubleValue();
            if (speed <= 0) speed = 25.0;
            int travelMin = (int) Math.round(minDist / speed * 60);
            node.setTravelMinutesFromPrev(travelMin);

            curTime = curTime.plusMinutes(travelMin + bufferMinutes);
            node.setEtaArrival(curTime);

            if (nearest.getScheduledTime() != null) {
                // 以预约时间为准
                node.setEtaArrival(nearest.getScheduledTime());
            }
            node.setEtaComplete(node.getEtaArrival().plusMinutes(serviceMinutes));
            curTime = node.getEtaComplete();
            curLat = nearest.getLat().doubleValue();
            curLng = nearest.getLng().doubleValue();
            plan.add(node);
        }

        // 汇总统计
        double totalDist = plan.stream().mapToDouble(n -> n.getDistanceFromPrev()).sum();
        int totalTravel = plan.stream().mapToInt(n -> n.getTravelMinutesFromPrev()).sum();
        int totalService = orders.size() * serviceMinutes;
        double gainKm = randomTotalDist - totalDist;
        if (gainKm < 0) gainKm = 0; // 只算节省，不算浪费

        result.setNodes(plan);
        result.setTotalDistanceKm(round(totalDist));
        result.setTotalTravelMinutes(totalTravel);
        result.setTotalServiceMinutes(totalService);
        result.setTotalOrders(plan.size());
        result.setStartTime(plan.isEmpty() ? null : plan.get(0).getEtaArrival());
        result.setEndTime(plan.isEmpty() ? null : plan.get(plan.size() - 1).getEtaComplete());
        result.setTotalTravelFee(BigDecimal.valueOf(totalDist * 2)
                .setScale(2, RoundingMode.HALF_UP).doubleValue());
        result.setGainKmVsRandom(new double[]{round(gainKm), round(randomTotalDist)});
        return result;
    }

    private double calcTotalDistance(TechnicianSignal tech, List<LbsServiceOrder> orders) {
        double total = 0;
        double curLat = tech.getLat().doubleValue();
        double curLng = tech.getLng().doubleValue();
        for (LbsServiceOrder o : orders) {
            total += haversine(curLat, curLng,
                    o.getLat().doubleValue(), o.getLng().doubleValue());
            curLat = o.getLat().doubleValue();
            curLng = o.getLng().doubleValue();
        }
        return total;
    }

    // ====================================================================
    // 10. 服务区域上门费计算
    // ====================================================================

    /**
     * 计算上门费：baseFee + distance * feePerKm，夹在 [minFee, maxFee] 之间。
     */
    public BigDecimal calcTravelFee(BigDecimal baseFee, BigDecimal feePerKm,
                                   BigDecimal minFee, BigDecimal maxFee,
                                   double distanceKm) {
        double base = nz(baseFee).doubleValue();
        double perKm = nz(feePerKm).doubleValue();
        double min = nz(minFee).doubleValue();
        double max = nz(maxFee).doubleValue();
        double fee = base + distanceKm * perKm;
        return BigDecimal.valueOf(Math.max(min, Math.min(max, fee)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ====================================================================
    // 工具方法
    // ====================================================================

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static BigDecimal numB(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); } catch (Exception e) { return null; }
    }

    private static Integer numI(Object o, Integer dflt) {
        if (o == null) return dflt;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return dflt; }
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String str(Object o, String dflt) {
        if (o == null) return dflt;
        String s = o.toString();
        return s.isEmpty() ? dflt : s;
    }

    private static LocalTime time(Object o) {
        if (o == null) return null;
        try {
            String s = o.toString();
            if (s.length() >= 5) s = s.substring(0, 5);
            return LocalTime.parse(s);
        } catch (Exception e) { return null; }
    }

    private static LocalDateTime timestamp(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp) return ((java.sql.Timestamp) o).toLocalDateTime();
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        try { return LocalDateTime.parse(o.toString().substring(0, 19)); }
        catch (Exception e) { return null; }
    }
}
