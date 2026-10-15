package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 3 · 上门服务 LBS 派单增强版
 *
 * <p>在原有 Haversine 单订单派单基础上新增：</p>
 * <ul>
 *   <li>5 维度派单评分（距离 / 技能 / 评分 / 负荷 / 时间窗）</li>
 *   <li>最近邻 TSP 路线优化</li>
 *   <li>派单决策日志（explainable AI）</li>
 *   <li>批量派单 + 派单预览（不写库）</li>
 *   <li>服务区域管理</li>
 *   <li>技师技能管理</li>
 *   <li>路线规划 + 历史路线</li>
 *   <li>派单策略配置（5 维度权重可调）</li>
 *   <li>调度看板</li>
 * </ul>
 *
 * <p>REST 前缀：{@code /lbs}</p>
 *
 * @author Cursor Agent
 * @since 2026-09-05 (Phase 3 上门服务 LBS 派单)
 */
@Slf4j
@RestController
@RequestMapping("/lbs")
@RequiredArgsConstructor
public class LbsDispatchService {

    private final LbsTechnicianMapper techMapper;
    private final LbsServiceOrderMapper orderMapper;
    private final LbsTechnicianSkillMapper skillMapper;
    private final LbsServiceZoneMapper zoneMapper;
    private final LbsDispatchLogMapper logMapper;
    private final LbsDispatchConfigMapper configMapper;
    private final LbsRoutePlanMapper routePlanMapper;
    private final LbsLocationHistoryMapper locationHistoryMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LbsRouteOptimizer optimizer;

    private final ObjectMapper om = new ObjectMapper();

    // ====================================================================
    // 技师管理
    // ====================================================================

    @PostMapping("/technician")
    @Transactional(rollbackFor = Exception.class)
    public Result<LbsTechnician> registerTech(@RequestBody LbsTechnician t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getTechnicianId() == null) return Result.error(BizCode.BAD_REQUEST, "technicianId 必填");
        if (t.getStatus() == null) t.setStatus("ACTIVE");
        if (t.getOnlineStatus() == null) t.setOnlineStatus("OFFLINE");
        if (t.getAvailable() == null) t.setAvailable(1);
        if (t.getMaxRadiusKm() == null) t.setMaxRadiusKm(new BigDecimal("10"));
        if (t.getRating() == null) t.setRating(new BigDecimal("5.0"));
        if (t.getTravelSpeedKmh() == null) t.setTravelSpeedKmh(new BigDecimal("25.0"));
        if (t.getAvgServiceMinutes() == null) t.setAvgServiceMinutes(90);
        if (t.getMaxOrdersPerDay() == null) t.setMaxOrdersPerDay(8);
        t.setTenantId(tenantId);
        t.setId(null);
        techMapper.insert(t);
        return Result.success("已注册", t);
    }

    @GetMapping("/technician/list")
    public Result<List<LbsTechnician>> listTechs() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(techMapper.selectList(new LambdaQueryWrapper<LbsTechnician>()
                .eq(LbsTechnician::getTenantId, tenantId)
                .eq(LbsTechnician::getStatus, "ACTIVE")
                .orderByDesc(LbsTechnician::getId)));
    }

    @PostMapping("/technician/{id}/location")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateLocation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal lat = new BigDecimal(body.get("lat").toString());
        BigDecimal lng = new BigDecimal(body.get("lng").toString());
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE lbs_technician SET current_lat=?, current_lng=?, last_loc_time=?, online_status='ONLINE' WHERE id=?",
                lat, lng, now, id);
        // 同时写位置历史（非事务辅助方法，失败不影响主事务）
        insertLocationHistory(id, lat, lng, now, body.getOrDefault("source", "APP").toString());
        return Result.success("位置已更新", null);
    }

    /** 非事务辅助方法：写位置历史，失败仅日志不抛异常 */
    private void insertLocationHistory(Long technicianId, BigDecimal lat, BigDecimal lng,
                                      LocalDateTime reportedAt, String source) {
        try {
            LbsLocationHistory h = new LbsLocationHistory();
            h.setTenantId(TenantContextHolder.get().getTenantId());
            h.setTechnicianId(technicianId);
            h.setLat(lat);
            h.setLng(lng);
            h.setReportedAt(reportedAt);
            h.setSource(source);
            locationHistoryMapper.insert(h);
        } catch (Exception e) {
            log.warn("[LBS] write location history failed: {}", e.getMessage());
        }
    }

    @PostMapping("/technician/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("onlineStatus");
        if (status == null) return Result.error(BizCode.BAD_REQUEST, "onlineStatus 必填");
        jdbc.update("UPDATE lbs_technician SET online_status=? WHERE id=?", status, id);
        return Result.success("状态已更新", null);
    }

    @GetMapping("/technician/{id}")
    public Result<Map<String, Object>> getTech(@PathVariable Long id) {
        LbsTechnician t = techMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "技师不存在");
        // 拉技能
        List<LbsTechnicianSkill> skills = skillMapper.selectList(new LambdaQueryWrapper<LbsTechnicianSkill>()
                .eq(LbsTechnicianSkill::getTechnicianId, id)
                .eq(LbsTechnicianSkill::getDeleted, 0));
        // 今日订单数
        int todayOrders = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lbs_service_order WHERE technician_id=? AND DATE(scheduled_time)=CURDATE() AND dispatch_status NOT IN ('CANCELLED','COMPLETED')",
                Integer.class, id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tech", t);
        data.put("skills", skills);
        data.put("todayOrderCount", todayOrders);
        return Result.success(data);
    }

    // ====================================================================
    // 技师技能管理
    // ====================================================================

    @PostMapping("/technician/{techId}/skill")
    @Transactional(rollbackFor = Exception.class)
    public Result<LbsTechnicianSkill> addSkill(@PathVariable Long techId, @RequestBody Map<String, Object> body) {
        String code = (String) body.get("skillCode");
        String name = (String) body.get("skillName");
        Integer level = body.get("skillLevel") == null ? 3 : ((Number) body.get("skillLevel")).intValue();
        if (code == null) return Result.error(BizCode.BAD_REQUEST, "skillCode 必填");
        Long exist = skillMapper.selectCount(new LambdaQueryWrapper<LbsTechnicianSkill>()
                .eq(LbsTechnicianSkill::getTechnicianId, techId)
                .eq(LbsTechnicianSkill::getSkillCode, code)
                .eq(LbsTechnicianSkill::getDeleted, 0));
        if (exist > 0) return Result.error(BizCode.CONFLICT, "技能已存在");
        LbsTechnicianSkill s = new LbsTechnicianSkill();
        s.setTenantId(TenantContextHolder.get().getTenantId());
        s.setTechnicianId(techId);
        s.setSkillCode(code);
        s.setSkillName(name != null ? name : code);
        s.setSkillLevel(level);
        s.setCertified(body.get("certified") != null ? ((Number) body.get("certified")).intValue() : 0);
        skillMapper.insert(s);
        return Result.success("技能已添加", s);
    }

    @DeleteMapping("/technician/{techId}/skill/{skillId}")
    public Result<Void> removeSkill(@PathVariable Long skillId) {
        skillMapper.deleteById(skillId);
        return Result.success("技能已移除", null);
    }

    // ====================================================================
    // 服务区域管理
    // ====================================================================

    @GetMapping("/zone/list")
    public Result<List<LbsServiceZone>> listZones() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(zoneMapper.selectList(new LambdaQueryWrapper<LbsServiceZone>()
                .eq(LbsServiceZone::getTenantId, tenantId)
                .eq(LbsServiceZone::getEnabled, 1)
                .eq(LbsServiceZone::getDeleted, 0)
                .orderByAsc(LbsServiceZone::getSortOrder)));
    }

    @PostMapping("/zone")
    @Transactional(rollbackFor = Exception.class)
    public Result<LbsServiceZone> createZone(@RequestBody LbsServiceZone z) {
        if (z.getZoneCode() == null) return Result.error(BizCode.BAD_REQUEST, "zoneCode 必填");
        if (z.getZoneName() == null) return Result.error(BizCode.BAD_REQUEST, "zoneName 必填");
        if (z.getZoneType() == null) z.setZoneType("CIRCLE");
        z.setTenantId(TenantContextHolder.get().getTenantId());
        z.setEnabled(1);
        if (z.getServiceFeePerKm() == null) z.setServiceFeePerKm(new BigDecimal("2.00"));
        if (z.getBaseFee() == null) z.setBaseFee(BigDecimal.ZERO);
        if (z.getMinFee() == null) z.setMinFee(BigDecimal.ZERO);
        if (z.getMaxFee() == null) z.setMaxFee(new BigDecimal("999.00"));
        zoneMapper.insert(z);
        return Result.success("区域已创建", z);
    }

    @PutMapping("/zone/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<LbsServiceZone> updateZone(@PathVariable Long id, @RequestBody LbsServiceZone z) {
        LbsServiceZone old = zoneMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "区域不存在");
        z.setId(id);
        z.setTenantId(old.getTenantId());
        zoneMapper.updateById(z);
        return Result.success("区域已更新", zoneMapper.selectById(id));
    }

    @DeleteMapping("/zone/{id}")
    public Result<Void> deleteZone(@PathVariable Long id) {
        zoneMapper.deleteById(id);
        return Result.success("区域已删除", null);
    }

    // ====================================================================
    // 派单核心（增强版）
    // ====================================================================

    /**
     * 创建上门订单并执行增强派单。
     * POST /lbs/order/dispatch
     *
     * <p>行为：
     * <ol>
     *   <li>写 lbs_service_order（status=PENDING）</li>
     *   <li>执行 5 维度评分派单</li>
     *   <li>写派单日志</li>
     *   <li>路线优化（如有多订单则生成路线计划）</li>
     *   <li>更新订单派单状态</li>
     * </ol>
     */
    @PostMapping("/order/dispatch")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> dispatch(@RequestBody Map<String, Object> body) {
        long startMs = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        BigDecimal lat = new BigDecimal(body.get("lat").toString());
        BigDecimal lng = new BigDecimal(body.get("lng").toString());

        // 解析技能标签
        Set<String> requiredTags = new java.util.HashSet<>();
        Object tagsObj = body.get("skillTags");
        if (tagsObj instanceof List) {
            ((List<?>) tagsObj).forEach(t -> requiredTags.add(t.toString()));
        }

        // 写订单
        LbsServiceOrder o = new LbsServiceOrder();
        o.setTenantId(tenantId);
        o.setOrderNo("LBS" + System.currentTimeMillis());
        if (body.get("memberId") != null) o.setMemberId(((Number) body.get("memberId")).longValue());
        o.setMemberName((String) body.get("memberName"));
        o.setMemberPhone((String) body.get("memberPhone"));
        o.setServiceId(((Number) body.get("serviceId")).longValue());
        o.setServiceName((String) body.get("serviceName"));
        o.setAddress((String) body.get("address"));
        o.setLat(lat);
        o.setLng(lng);
        if (body.get("scheduledTime") != null) {
        o.setScheduledTime(LocalDateTime.parse(body.get("scheduledTime").toString().replace(" ", "T")));
        }
        o.setAmount(new BigDecimal(body.getOrDefault("amount", "0").toString()));
        o.setDispatchStatus("PENDING");
        orderMapper.insert(o);

        // 执行派单
        LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, storeId);
        LocalDate serviceDate = o.getScheduledTime() != null
                ? o.getScheduledTime().toLocalDate() : LocalDate.now();
        List<LbsRouteOptimizer.TechnicianSignal> techs = optimizer.loadTechnicianSignals(tenantId, serviceDate);

        // 5 维度评分
        List<LbsRouteOptimizer.DispatchScoreResult> candidates = new ArrayList<>();
        for (LbsRouteOptimizer.TechnicianSignal tech : techs) {
            LbsRouteOptimizer.DispatchScoreResult r = optimizer.scoreDispatch(o, tech, cfg, requiredTags);
            candidates.add(r);
        }
        // 排序
        candidates.sort((a, b) -> Double.compare(
                b.getTotalScore() == null ? 0 : b.getTotalScore(),
                a.getTotalScore() == null ? 0 : a.getTotalScore()));

        // 选最优
        LbsRouteOptimizer.DispatchScoreResult best = null;
        String rejectReason = null;
        for (LbsRouteOptimizer.DispatchScoreResult r : candidates) {
            if (r.isEligible()) { best = r; break; }
        }
        if (best == null && !candidates.isEmpty()) {
            LbsRouteOptimizer.DispatchScoreResult top = candidates.get(0);
            rejectReason = top.getRejectReason() != null ? top.getRejectReason() : "无可用技师";
        }

        // 写派单日志
        writeDispatchLog(tenantId, storeId, o, "AUTO", candidates, best, rejectReason,
                (int) (System.currentTimeMillis() - startMs));

        if (best != null) {
            o.setTechnicianId(best.getTechnicianId());
            o.setDispatchStatus("DISPATCHED");
            o.setDispatchTime(LocalDateTime.now());
            o.setTravelFee(best.getTravelFee());
            o.setDispatchScore(BigDecimal.valueOf(best.getTotalScore()));
            o.setEtaMinutes(best.getEtaMinutes());
            try {
                String bd = om.writeValueAsString(Map.of(
                        "skill", best.getSkillScore(),
                        "distance", best.getDistanceScore(),
                        "rating", best.getRatingScore(),
                        "load", best.getLoadScore(),
                        "time", best.getTimeScore()));
                o.setDispatchBreakdown(bd);
            } catch (JsonProcessingException ignored) {}
            orderMapper.updateById(o);
        } else {
            o.setRejectReason(rejectReason);
            o.setDispatchStatus("REJECTED");
            orderMapper.updateById(o);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", o.getId());
        data.put("orderNo", o.getOrderNo());
        if (best != null) {
            data.put("success", true);
            data.put("technicianId", best.getTechnicianId());
            data.put("technicianName", best.getTechnicianName());
            data.put("phone", findTechPhone(best.getTechnicianId()));
            data.put("distanceKm", best.getDistanceKm());
            data.put("totalScore", best.getTotalScore());
            data.put("skillScore", best.getSkillScore());
            data.put("distanceScore", best.getDistanceScore());
            data.put("ratingScore", best.getRatingScore());
            data.put("loadScore", best.getLoadScore());
            data.put("timeScore", best.getTimeScore());
            data.put("travelFee", best.getTravelFee());
            data.put("etaMinutes", best.getEtaMinutes());
        } else {
            data.put("success", false);
            data.put("reason", rejectReason != null ? rejectReason : "无可用技师");
        }
        data.put("candidatesCount", candidates.size());
        data.put("eligibleCount", (int) candidates.stream().filter(LbsRouteOptimizer.DispatchScoreResult::isEligible).count());
        data.put("algorithmMs", System.currentTimeMillis() - startMs);
        return Result.success(best != null ? "已派单" : "派单失败", data);
    }

    /**
     * 派单预览（不写库，返回候选列表）
     * POST /lbs/order/preview
     */
    @PostMapping("/order/preview")
    public Result<Map<String, Object>> previewDispatch(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        BigDecimal lat = new BigDecimal(body.get("lat").toString());
        BigDecimal lng = new BigDecimal(body.get("lng").toString());
        LocalDate serviceDate = body.get("scheduledDate") != null
                ? LocalDate.parse(body.get("scheduledDate").toString())
                : LocalDate.now();

        Set<String> requiredTags = new java.util.HashSet<>();
        Object tagsObj = body.get("skillTags");
        if (tagsObj instanceof List) {
            ((List<?>) tagsObj).forEach(t -> requiredTags.add(t.toString()));
        }

        LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, storeId);
        List<LbsRouteOptimizer.TechnicianSignal> techs = optimizer.loadTechnicianSignals(tenantId, serviceDate);

        // 模拟一个临时订单
        LbsServiceOrder tmp = new LbsServiceOrder();
        tmp.setLat(lat);
        tmp.setLng(lng);
        tmp.setScheduledTime(body.get("scheduledTime") != null
                ? LocalDateTime.parse(body.get("scheduledTime").toString().replace(" ", "T"))
                : LocalDateTime.now());

        List<LbsRouteOptimizer.DispatchScoreResult> scored = new ArrayList<>();
        for (LbsRouteOptimizer.TechnicianSignal tech : techs) {
            LbsRouteOptimizer.DispatchScoreResult r = optimizer.scoreDispatch(tmp, tech, cfg, requiredTags);
            r.setOrderId(null);
            r.setOrderNo(null);
            scored.add(r);
        }
        scored.sort((a, b) -> Double.compare(
                b.getTotalScore() == null ? 0 : b.getTotalScore(),
                a.getTotalScore() == null ? 0 : a.getTotalScore()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("candidatesCount", scored.size());
        data.put("eligibleCount", (int) scored.stream().filter(LbsRouteOptimizer.DispatchScoreResult::isEligible).count());
        data.put("config", cfg);
        data.put("scored", toListMap(scored));
        return Result.success("派单预览", data);
    }

    /**
     * 批量派单
     * POST /lbs/order/batch-dispatch
     *
     * <p>body: { orderIds: [1,2,3] } — 对已有订单批量执行派单评分（不自动派，可选）</p>
     */
    @PostMapping("/order/batch-dispatch")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchDispatch(@RequestBody Map<String, Object> body) {
        long startMs = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        List<?> idsRaw = (List<?>) body.get("orderIds");
        if (idsRaw == null || idsRaw.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "orderIds 必填");
        List<Long> orderIds = idsRaw.stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList());
        boolean autoAssign = Boolean.TRUE.equals(body.get("autoAssign"));

        List<LbsServiceOrder> orders = orderMapper.selectBatchIds(orderIds);
        LocalDate serviceDate = orders.isEmpty() ? LocalDate.now()
                : (orders.get(0).getScheduledTime() != null
                        ? orders.get(0).getScheduledTime().toLocalDate() : LocalDate.now());

        LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, storeId);
        List<LbsRouteOptimizer.TechnicianSignal> techs = optimizer.loadTechnicianSignals(tenantId, serviceDate);

        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> requiredTags = new java.util.HashSet<>(); // 缺技能标签按空集处理

        for (LbsServiceOrder o : orders) {
            if (o.getLat() == null || o.getLng() == null) {
                results.add(Map.of("orderId", o.getId(), "success", false, "reason", "缺少坐标"));
                continue;
            }
            List<LbsRouteOptimizer.DispatchScoreResult> candidates = new ArrayList<>();
            for (LbsRouteOptimizer.TechnicianSignal tech : techs) {
                candidates.add(optimizer.scoreDispatch(o, tech, cfg, requiredTags));
            }
            candidates.sort((a, b) -> Double.compare(
                    b.getTotalScore() == null ? 0 : b.getTotalScore(),
                    a.getTotalScore() == null ? 0 : a.getTotalScore()));

            writeDispatchLog(tenantId, storeId, o, "AUTO", candidates,
                    candidates.stream().filter(LbsRouteOptimizer.DispatchScoreResult::isEligible).findFirst().orElse(null),
                    null, (int) (System.currentTimeMillis() - startMs));

            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("orderId", o.getId());
            item.put("orderNo", o.getOrderNo());
            if (autoAssign) {
                var best = candidates.stream().filter(LbsRouteOptimizer.DispatchScoreResult::isEligible).findFirst();
                if (best.isPresent()) {
                    LbsRouteOptimizer.DispatchScoreResult r = best.get();
                    o.setTechnicianId(r.getTechnicianId());
                    o.setDispatchStatus("DISPATCHED");
                    o.setDispatchTime(LocalDateTime.now());
                    o.setTravelFee(r.getTravelFee());
                    o.setDispatchScore(BigDecimal.valueOf(r.getTotalScore()));
                    orderMapper.updateById(o);
                    item.put("success", true);
                    item.put("technicianName", r.getTechnicianName());
                    item.put("totalScore", r.getTotalScore());
                } else {
                    item.put("success", false);
                    item.put("reason", "无合格技师");
                }
            } else {
                item.put("success", false);
                item.put("reason", "preview模式（autoAssign=false）");
            }
            item.put("candidatesCount", candidates.size());
            results.add(item);
        }

        // 批量路线规划（为每个技师生成路线）
        if (autoAssign) {
            Map<Long, List<LbsServiceOrder>> byTech = new java.util.HashMap<>();
            for (LbsServiceOrder o : orders) {
                if (o.getTechnicianId() != null) {
                    byTech.computeIfAbsent(o.getTechnicianId(), k -> new ArrayList<>()).add(o);
                }
            }
            for (Map.Entry<Long, List<LbsServiceOrder>> e : byTech.entrySet()) {
                var tech = techs.stream().filter(t -> t.getId().equals(e.getKey())).findFirst();
                if (tech.isPresent()) {
                    LbsRouteOptimizer.RouteResult route = optimizer.optimizeRoute(
                            tech.get(), e.getValue(), cfg.getDefaultServiceMinutes(), cfg.getBufferMinutes());
                    saveRoutePlan(tenantId, storeId, e.getKey(), serviceDate, route);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("results", results);
        data.put("totalOrders", orders.size());
        data.put("totalCandidates", techs.size());
        data.put("algorithmMs", System.currentTimeMillis() - startMs);
        return Result.success("批量派单完成", data);
    }

    /**
     * 重新派单
     * POST /lbs/order/{id}/reassign
     */
    @PostMapping("/order/{id}/reassign")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> reassign(@PathVariable Long id) {
        LbsServiceOrder o = orderMapper.selectById(id);
        if (o == null) return Result.error(BizCode.NOT_FOUND, "订单不存在");
        Long tenantId = o.getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();

        Set<String> requiredTags = new java.util.HashSet<>();
        LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, storeId);
        LocalDate serviceDate = o.getScheduledTime() != null
                ? o.getScheduledTime().toLocalDate() : LocalDate.now();
        List<LbsRouteOptimizer.TechnicianSignal> techs = optimizer.loadTechnicianSignals(tenantId, serviceDate);

        List<LbsRouteOptimizer.DispatchScoreResult> candidates = new ArrayList<>();
        for (LbsRouteOptimizer.TechnicianSignal tech : techs) {
            if (tech.getId().equals(o.getTechnicianId())) continue; // 排除原技师
            candidates.add(optimizer.scoreDispatch(o, tech, cfg, requiredTags));
        }
        candidates.sort((a, b) -> Double.compare(
                b.getTotalScore() == null ? 0 : b.getTotalScore(),
                a.getTotalScore() == null ? 0 : a.getTotalScore()));

        LbsRouteOptimizer.DispatchScoreResult best = candidates.stream()
                .filter(LbsRouteOptimizer.DispatchScoreResult::isEligible).findFirst().orElse(null);
        writeDispatchLog(tenantId, storeId, o, "REASSIGN", candidates, best, null, 0);

        Map<String, Object> data = new LinkedHashMap<>();
        if (best != null) {
            o.setTechnicianId(best.getTechnicianId());
            o.setDispatchStatus("DISPATCHED");
            o.setDispatchTime(LocalDateTime.now());
            o.setTravelFee(best.getTravelFee());
            o.setDispatchScore(BigDecimal.valueOf(best.getTotalScore()));
            orderMapper.updateById(o);
            data.put("success", true);
            data.put("technicianName", best.getTechnicianName());
            data.put("totalScore", best.getTotalScore());
            return Result.success("已重新派单", data);
        } else {
            data.put("success", false);
            data.put("reason", "无合格技师");
            return Result.success("重新派单失败", data);
        }
    }

    // ====================================================================
    // 路线规划
    // ====================================================================

    /**
     * 获取技师当日路线
     * GET /lbs/technician/{id}/route?date=
     */
    @GetMapping("/technician/{id}/route")
    public Result<Map<String, Object>> getRoute(@PathVariable Long id,
                                                 @RequestParam(required = false) String date) {
        LocalDate planDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        Long tenantId = TenantContextHolder.get().getTenantId();

        // 已有路线计划
        LbsRoutePlan plan = jdbc.queryForObject(
                "SELECT * FROM lbs_route_plan WHERE tenant_id=? AND technician_id=? AND plan_date=? AND status IN ('DRAFT','ACTIVE') LIMIT 1",
                (rs, rn) -> {
                    LbsRoutePlan p = new LbsRoutePlan();
                    p.setId(rs.getLong("id"));
                    p.setPlanNo(rs.getString("plan_no"));
                    p.setTotalOrders(rs.getInt("total_orders"));
                    p.setTotalDistanceKm(rs.getBigDecimal("total_distance_km"));
                    p.setTotalTravelMinutes(rs.getInt("total_travel_minutes"));
                    p.setTotalServiceMinutes(rs.getInt("total_service_minutes"));
                    p.setEtaComplete(rs.getTimestamp("eta_complete") != null
                            ? rs.getTimestamp("eta_complete").toLocalDateTime() : null);
                    p.setAlgorithm(rs.getString("algorithm"));
                    p.setOptimizeGainKm(rs.getBigDecimal("optimize_gain_km"));
                    p.setStatus(rs.getString("status"));
                    return p;
                }, tenantId, id, planDate.toString());

        // 当日已派单
        List<LbsServiceOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<LbsServiceOrder>()
                .eq(LbsServiceOrder::getTechnicianId, id)
                .eq(LbsServiceOrder::getTenantId, tenantId)
                .apply("DATE(scheduled_time) = {0}", planDate.toString())
                .notIn(LbsServiceOrder::getDispatchStatus, "CANCELLED", "COMPLETED")
                .orderByAsc(LbsServiceOrder::getScheduledTime));

        // 路线优化
        LbsRouteOptimizer.TechnicianSignal tech = optimizer.loadTechnicianSignals(tenantId, planDate)
                .stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        LbsRouteOptimizer.RouteResult route = null;
        if (tech != null && !orders.isEmpty()) {
            LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, TenantContextHolder.get().getStoreId());
            route = optimizer.optimizeRoute(tech, orders, cfg.getDefaultServiceMinutes(), cfg.getBufferMinutes());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", plan);
        data.put("orders", orders.stream().map(this::orderToMap).collect(Collectors.toList()));
        data.put("route", route);
        return Result.success(data);
    }

    /**
     * 生成/重新生成路线计划
     * POST /lbs/technician/{id}/route/generate
     */
    @PostMapping("/technician/{id}/route/generate")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> generateRoute(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        LocalDate planDate = body.get("date") != null
                ? LocalDate.parse(body.get("date").toString()) : LocalDate.now();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();

        List<LbsServiceOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<LbsServiceOrder>()
                .eq(LbsServiceOrder::getTechnicianId, id)
                .eq(LbsServiceOrder::getTenantId, tenantId)
                .apply("DATE(scheduled_time) = {0}", planDate.toString())
                .notIn(LbsServiceOrder::getDispatchStatus, "CANCELLED", "COMPLETED"));

        LbsRouteOptimizer.TechnicianSignal tech = optimizer.loadTechnicianSignals(tenantId, planDate)
                .stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        if (tech == null) return Result.error(BizCode.NOT_FOUND, "技师未找到或未在线");

        LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, storeId);
        LbsRouteOptimizer.RouteResult route = optimizer.optimizeRoute(tech, orders,
                cfg.getDefaultServiceMinutes(), cfg.getBufferMinutes());

        LbsRoutePlan plan = saveRoutePlan(tenantId, storeId, id, planDate, route);
        // 更新订单路线顺序
        for (LbsRouteOptimizer.RouteNode node : route.getNodes()) {
            jdbc.update("UPDATE lbs_service_order SET route_seq=? WHERE id=?",
                    node.getVisitOrder(), node.getOrderId());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", plan);
        data.put("route", route);
        return Result.success("路线已生成", data);
    }

    // ====================================================================
    // 派单日志
    // ====================================================================

    @GetMapping("/dispatch-log")
    public Result<List<Map<String, Object>>> dispatchLog(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(defaultValue = "50") int limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
                "SELECT l.id, l.order_id, l.order_no, l.trigger_source, l.chosen_technician_id, " +
                "l.chosen_score, l.breakdown_json, l.decision, l.reject_reason, " +
                "l.duration_ms, l.operator_name, l.create_time, " +
                "t.name AS tech_name " +
                "FROM lbs_dispatch_log l " +
                "LEFT JOIN lbs_technician t ON t.id = l.chosen_technician_id " +
                "WHERE l.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (orderId != null) { sql.append("AND l.order_id = ? "); args.add(orderId); }
        if (technicianId != null) { sql.append("AND l.chosen_technician_id = ? "); args.add(technicianId); }
        sql.append("ORDER BY l.id DESC LIMIT ?");
        args.add(Math.min(limit, 200));
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return Result.success(Keys.camel(rows));
    }

    // ====================================================================
    // 派单策略配置
    // ====================================================================

    @GetMapping("/dispatch-config")
    public Result<Map<String, Object>> getConfig() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        LbsRouteOptimizer.DispatchConfig cfg = loadConfig(tenantId, storeId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenantId", tenantId);
        m.put("storeId", storeId);
        m.put("wDistance", cfg.getWDistance());
        m.put("wSkill", cfg.getWSkill());
        m.put("wRating", cfg.getWRating());
        m.put("wLoad", cfg.getWLoad());
        m.put("wTime", cfg.getWTime());
        m.put("routeStrategy", "NEAREST_NEIGHBOR");
        m.put("maxOrdersPerDay", cfg.getMaxOrdersPerDay());
        m.put("strictZoneMatch", cfg.isStrictZoneMatch());
        m.put("fallbackToNearest", cfg.isFallbackToNearest());
        m.put("defaultServiceMinutes", cfg.getDefaultServiceMinutes());
        m.put("bufferMinutes", cfg.getBufferMinutes());
        m.put("minDispatchScore", cfg.getMinDispatchScore());
        m.put("enabled", true);
        return Result.success(m);
    }

    @PutMapping("/dispatch-config")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> updateConfig(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        // 校验权重和
        double wD = numD(body.get("wDistance"), 0.35);
        double wS = numD(body.get("wSkill"), 0.25);
        double wR = numD(body.get("wRating"), 0.15);
        double wL = numD(body.get("wLoad"), 0.15);
        double wT = numD(body.get("wTime"), 0.10);
        double sum = wD + wS + wR + wL + wT;
        if (Math.abs(sum - 1.0) > 0.01)
            return Result.error(BizCode.BAD_REQUEST, "5 个权重之和必须为 1.0（当前=" + sum + "）");

        Long sid = storeId == null ? 0L : storeId;
        Long exist = jdbc.queryForObject(
                "SELECT id FROM lbs_dispatch_config WHERE deleted=0 AND tenant_id=? AND store_id=? LIMIT 1",
                Long.class, tenantId, sid);
        if (exist == null) {
            jdbc.update(
                    "INSERT INTO lbs_dispatch_config (tenant_id,store_id,weight_distance,weight_skill," +
                    "weight_rating,weight_load,weight_time,max_orders_per_tech,strict_zone_match," +
                    "fallback_to_nearest,default_service_minutes,buffer_minutes,min_dispatch_score,enabled,operator_name) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,?)",
                    tenantId, sid, wD, wS, wR, wL, wT,
                    numI(body.get("maxOrdersPerDay"), 8),
                    numI(body.get("strictZoneMatch"), 0),
                    numI(body.get("fallbackToNearest"), 1),
                    numI(body.get("defaultServiceMinutes"), 90),
                    numI(body.get("bufferMinutes"), 15),
                    numD(body.get("minDispatchScore"), 40.0),
                    body.getOrDefault("operatorName", "admin"));
        } else {
            jdbc.update(
                    "UPDATE lbs_dispatch_config SET weight_distance=?,weight_skill=?," +
                    "weight_rating=?,weight_load=?,weight_time=?,max_orders_per_tech=?," +
                    "strict_zone_match=?,fallback_to_nearest=?,default_service_minutes=?," +
                    "buffer_minutes=?,min_dispatch_score=?,update_time=NOW() WHERE id=?",
                    wD, wS, wR, wL, wT,
                    numI(body.get("maxOrdersPerDay"), 8),
                    numI(body.get("strictZoneMatch"), 0),
                    numI(body.get("fallbackToNearest"), 1),
                    numI(body.get("defaultServiceMinutes"), 90),
                    numI(body.get("bufferMinutes"), 15),
                    numD(body.get("minDispatchScore"), 40.0),
                    exist);
        }
        return Result.success("配置已更新", null);
    }

    // ====================================================================
    // 调度看板
    // ====================================================================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalTechs", safeLong("SELECT COUNT(*) FROM lbs_technician WHERE deleted=0 AND status='ACTIVE' AND tenant_id=" + tenantId));
        data.put("onlineTechs", safeLong("SELECT COUNT(*) FROM lbs_technician WHERE deleted=0 AND status='ACTIVE' AND online_status='ONLINE' AND tenant_id=" + tenantId));
        data.put("todayOrders", safeLong("SELECT COUNT(*) FROM lbs_service_order WHERE tenant_id=" + tenantId + " AND DATE(scheduled_time)=CURDATE()"));
        data.put("pendingOrders", safeLong("SELECT COUNT(*) FROM lbs_service_order WHERE tenant_id=" + tenantId + " AND dispatch_status='PENDING'"));
        data.put("dispatchedToday", safeLong("SELECT COUNT(*) FROM lbs_service_order WHERE tenant_id=" + tenantId + " AND dispatch_status='DISPATCHED' AND DATE(scheduled_time)=CURDATE()"));
        data.put("completedToday", safeLong("SELECT COUNT(*) FROM lbs_service_order WHERE tenant_id=" + tenantId + " AND dispatch_status='COMPLETED' AND DATE(scheduled_time)=CURDATE()"));
        data.put("rejectedToday", safeLong("SELECT COUNT(*) FROM lbs_service_order WHERE tenant_id=" + tenantId + " AND dispatch_status='REJECTED' AND DATE(scheduled_time)=CURDATE()"));
        data.put("avgDispatchScore", jdbc.queryForObject(
                "SELECT AVG(dispatch_score) FROM lbs_service_order WHERE tenant_id=" + tenantId + " AND dispatch_score IS NOT NULL AND DATE(scheduled_time)=CURDATE()",
                Double.class));
        data.put("totalDistanceKm", jdbc.queryForObject(
                "SELECT SUM(total_distance_km) FROM lbs_route_plan WHERE tenant_id=" + tenantId + " AND DATE(plan_date)=CURDATE()",
                Double.class));
        data.put("totalDispatchLogs", safeLong("SELECT COUNT(*) FROM lbs_dispatch_log WHERE tenant_id=" + tenantId));
        return Result.success(data);
    }

    // ====================================================================
    // 订单列表（增强）
    // ====================================================================

    @GetMapping("/order/list")
    public Result<List<Map<String, Object>>> listOrders(@RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String sql = "SELECT o.*, t.name AS tech_name, t.phone AS tech_phone " +
                "FROM lbs_service_order o " +
                "LEFT JOIN lbs_technician t ON t.id = o.technician_id " +
                "WHERE o.tenant_id = ?";
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (status != null && !status.isEmpty()) { sql += " AND o.dispatch_status = ?"; args.add(status); }
        sql += " ORDER BY o.id DESC LIMIT 200";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        return Result.success(Keys.camel(rows));
    }

    @PostMapping("/order/{id}/status")
    public Result<Void> updateOrderStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        if (status == null) return Result.error(BizCode.BAD_REQUEST, "status 必填");
        LbsServiceOrder o = orderMapper.selectById(id);
        if (o == null) return Result.error(BizCode.NOT_FOUND, "订单不存在");
        o.setDispatchStatus(status);
        if ("ARRIVED".equals(status) || "ACCEPTED".equals(status)) {
            if (o.getAcceptTime() == null) o.setAcceptTime(LocalDateTime.now());
        }
        if ("COMPLETED".equals(status)) {
            // 写路线计划完成
            jdbc.update("UPDATE lbs_route_plan SET status='COMPLETED' WHERE technician_id=? AND DATE(plan_date)=? AND status='ACTIVE'",
                    o.getTechnicianId(), o.getScheduledTime().toLocalDate().toString());
        }
        orderMapper.updateById(o);
        return Result.success("状态已更新", null);
    }

    @PostMapping("/order/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> accept(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        LbsServiceOrder o = orderMapper.selectById(id);
        if (o == null) return Result.error(BizCode.NOT_FOUND, "订单不存在");
        if (!"DISPATCHED".equals(o.getDispatchStatus()))
            return Result.error(BizCode.BAD_REQUEST, "订单状态不允许接单");
        o.setDispatchStatus("ACCEPTED");
        o.setAcceptTime(LocalDateTime.now());
        orderMapper.updateById(o);
        return Result.success("已接单", null);
    }

    // ====================================================================
    // 私有方法
    // ====================================================================

    private LbsRouteOptimizer.DispatchConfig loadConfig(Long tenantId, Long storeId) {
        LbsRouteOptimizer.DispatchConfig c = new LbsRouteOptimizer.DispatchConfig();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT weight_distance,weight_skill,weight_rating,weight_load,weight_time," +
                    "max_orders_per_tech,default_service_minutes,buffer_minutes,min_dispatch_score," +
                    "strict_zone_match,fallback_to_nearest " +
                    "FROM lbs_dispatch_config " +
                    "WHERE deleted=0 AND tenant_id=? AND (store_id=? OR store_id=0) " +
                    "ORDER BY store_id DESC LIMIT 1",
                    tenantId, storeId == null ? 0 : storeId);
            if (!rows.isEmpty()) {
                Map<String, Object> r = rows.get(0);
                c.setWDistance(numD(r.get("weight_distance"), 0.35));
                c.setWSkill(numD(r.get("weight_skill"), 0.25));
                c.setWRating(numD(r.get("weight_rating"), 0.15));
                c.setWLoad(numD(r.get("weight_load"), 0.15));
                c.setWTime(numD(r.get("weight_time"), 0.10));
                c.setMaxOrdersPerDay(numI(r.get("max_orders_per_tech"), 8));
                c.setDefaultServiceMinutes(numI(r.get("default_service_minutes"), 90));
                c.setBufferMinutes(numI(r.get("buffer_minutes"), 15));
                c.setMinDispatchScore(numD(r.get("min_dispatch_score"), 40.0));
                c.setStrictZoneMatch(numI(r.get("strict_zone_match"), 0) == 1);
                c.setFallbackToNearest(numI(r.get("fallback_to_nearest"), 1) == 1);
            }
        } catch (Exception e) {
            log.warn("[LBS-CONFIG] loadConfig failed, using defaults: {}", e.getMessage());
        }
        return c;
    }

    private void writeDispatchLog(Long tenantId, Long storeId, LbsServiceOrder o,
                                   String trigger, List<LbsRouteOptimizer.DispatchScoreResult> candidates,
                                   LbsRouteOptimizer.DispatchScoreResult chosen, String rejectReason,
                                   int durationMs) {
        try {
            String candidatesJson = null;
            String breakdownJson = null;
            if (chosen != null) {
                try {
                    breakdownJson = om.writeValueAsString(Map.of(
                            "skill", chosen.getSkillScore(),
                            "distance", chosen.getDistanceScore(),
                            "rating", chosen.getRatingScore(),
                            "load", chosen.getLoadScore(),
                            "time", chosen.getTimeScore()));
                } catch (JsonProcessingException ignored) {}
            }
            if (!candidates.isEmpty()) {
                List<Map<String, Object>> tops = candidates.stream().limit(10)
                        .map(r -> {
                            Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("techId", r.getTechnicianId());
                            m.put("techName", r.getTechnicianName());
                            m.put("score", r.getTotalScore());
                            m.put("distance", r.getDistanceKm());
                            m.put("eligible", r.isEligible());
                            return m;
                        }).collect(Collectors.toList());
                candidatesJson = om.writeValueAsString(tops);
            }
            jdbc.update(
                    "INSERT INTO lbs_dispatch_log (tenant_id,store_id,order_id,order_no,trigger_source," +
                    "candidates_json,chosen_technician_id,chosen_score,breakdown_json,decision,reject_reason,duration_ms) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenantId, storeId, o.getId(), o.getOrderNo(), trigger,
                    candidatesJson, chosen != null ? chosen.getTechnicianId() : null,
                    chosen != null ? chosen.getTotalScore() : null,
                    breakdownJson, chosen != null ? "DISPATCHED" : "REJECTED",
                    rejectReason, durationMs);
        } catch (Exception e) {
            log.warn("[LBS] writeDispatchLog failed: {}", e.getMessage());
        }
    }

    private LbsRoutePlan saveRoutePlan(Long tenantId, Long storeId, Long techId,
                                        LocalDate planDate, LbsRouteOptimizer.RouteResult route) {
        String planNo = "RP" + techId + planDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + System.currentTimeMillis() % 1000;
        LbsRoutePlan plan = new LbsRoutePlan();
        plan.setTenantId(tenantId);
        plan.setStoreId(storeId);
        plan.setPlanNo(planNo);
        plan.setTechnicianId(techId);
        plan.setPlanDate(planDate);
        plan.setTotalOrders(route.getTotalOrders());
        plan.setTotalDistanceKm(BigDecimal.valueOf(route.getTotalDistanceKm()));
        plan.setTotalTravelMinutes(route.getTotalTravelMinutes());
        plan.setTotalServiceMinutes(route.getTotalServiceMinutes());
        plan.setEtaComplete(route.getEndTime());
        plan.setAlgorithm("NEAREST_NEIGHBOR");
        plan.setOptimizeGainKm(BigDecimal.valueOf(route.getGainKmVsRandom()[0]));
        plan.setStatus("ACTIVE");
        routePlanMapper.insert(plan);
        return plan;
    }

    private Map<String, Object> orderToMap(LbsServiceOrder o) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("memberName", o.getMemberName());
        m.put("serviceName", o.getServiceName());
        m.put("address", o.getAddress());
        m.put("lat", o.getLat());
        m.put("lng", o.getLng());
        m.put("scheduledTime", o.getScheduledTime());
        m.put("dispatchStatus", o.getDispatchStatus());
        m.put("routeSeq", o.getRouteSeq());
        m.put("etaMinutes", o.getEtaMinutes());
        return m;
    }

    private List<Map<String, Object>> toListMap(List<LbsRouteOptimizer.DispatchScoreResult> list) {
        return list.stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("technicianId", r.getTechnicianId());
            m.put("technicianName", r.getTechnicianName());
            m.put("distanceKm", r.getDistanceKm());
            m.put("totalScore", r.getTotalScore());
            m.put("skillScore", r.getSkillScore());
            m.put("distanceScore", r.getDistanceScore());
            m.put("ratingScore", r.getRatingScore());
            m.put("loadScore", r.getLoadScore());
            m.put("timeScore", r.getTimeScore());
            m.put("eligible", r.isEligible());
            m.put("rejectReason", r.getRejectReason());
            m.put("travelFee", r.getTravelFee());
            m.put("etaMinutes", r.getEtaMinutes());
            return m;
        }).collect(Collectors.toList());
    }

    private String findTechPhone(Long techId) {
        try {
            return jdbc.queryForObject("SELECT phone FROM lbs_technician WHERE id=?", String.class, techId);
        } catch (Exception e) { return ""; }
    }

    private long safeLong(String sql) {
        try { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0 : v; }
        catch (Exception e) { return 0; }
    }

    private double numD(Object o, double dflt) {
        if (o == null) return dflt;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return dflt; }
    }

    private int numI(Object o, int dflt) {
        if (o == null) return dflt;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return dflt; }
    }
}
