package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CarePlan;
import com.meiyetec.entity.SkinProfile;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CarePlanMapper;
import com.meiyetec.mapper.SkinProfileMapper;
import com.meiyetec.security.TenantContextHolder;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * P4.2.2 护肤方案推荐服务
 *
 * 根据肤质档案 + 关注问题 + 年龄段 + 消费历史，智能推荐：
 * 1. 服务项目（按肤质+问题匹配）
 * 2. 疗程方案（按目标组合）
 * 3. 周频次/周期
 * 4. 预估费用
 * 5. 推荐理由
 */
@Slf4j
@RestController
@RequestMapping("/care/plan")
@RequiredArgsConstructor
public class CarePlanService {

    private final SkinProfileMapper skinProfileMapper;
    private final CarePlanMapper carePlanMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // 肤质-服务项目 映射权重
    private static final Map<String, List<String>> CONCERN_TO_SERVICE = Map.of(
            "acne", List.of("清洁", "祛痘", "控油", "毛孔"),
            "pigmentation", List.of("美白", "淡斑", "亮肤", "祛黄"),
            "wrinkle", List.of("抗皱", "紧致", "抗衰", "提拉"),
            "dryness", List.of("补水", "保湿", "水光"),
            "sensitivity", List.of("舒缓", "修复", "敏感护理"),
            "dullness", List.of("焕肤", "亮肤", "清洁")
    );

    // ====================================================================
    // T1.1 肤质档案录入
    // ====================================================================

    @PostMapping("/skin/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<SkinProfile> saveSkinProfile(@RequestBody SkinProfile profile) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (profile.getMemberId() == null)
            return Result.error(BizCode.BAD_REQUEST, "会员ID必填");

        // 推断 age_group 和 life_stage
        if (profile.getAgeGroup() == null && profile.getMemberId() != null) {
            try {
                Map<String, Object> member = jdbc.queryForMap(
                        "SELECT TIMESTAMPDIFF(YEAR, birthday, CURDATE()) AS age, gender FROM crm_member " +
                                "WHERE id = ? AND deleted = 0",
                        profile.getMemberId());
                if (member.get("age") != null) {
                    int age = ((Number) member.get("age")).intValue();
                    profile.setAgeGroup(age < 25 ? "YOUNG" : age < 40 ? "MIDDLE" : "MATURE");
                }
            } catch (Exception ignored) {}
        }

        // 自动推断 skin_type
        if (profile.getSkinType() == null) {
            profile.setSkinType(inferSkinType(profile));
        }

        profile.setTenantId(tenantId);
        if (profile.getAssessDate() == null) profile.setAssessDate(LocalDate.now());
        profile.setId(null);
        skinProfileMapper.insert(profile);
        return Result.success("已保存", profile);
    }

    private String inferSkinType(SkinProfile p) {
        int oil = p.getOilLevel() == null ? 5 : p.getOilLevel();
        int moisture = p.getMoistureLevel() == null ? 5 : p.getMoistureLevel();
        int sensitivity = p.getSensitivityLevel() == null ? 5 : p.getSensitivityLevel();

        if (sensitivity >= 7) return "SENSITIVE";
        if (oil >= 7) return moisture < 5 ? "OILY" : "COMBINATION";
        if (oil <= 3) return moisture < 5 ? "DRY" : "NORMAL";
        return "COMBINATION";
    }

    @GetMapping("/skin/{memberId}")
    public Result<List<SkinProfile>> getSkinProfiles(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<SkinProfile> list = skinProfileMapper.selectList(new LambdaQueryWrapper<SkinProfile>()
                .eq(SkinProfile::getTenantId, tenantId)
                .eq(SkinProfile::getMemberId, memberId)
                .orderByDesc(SkinProfile::getAssessDate));
        return Result.success(list);
    }

    // ====================================================================
    // T1.2 护肤方案推荐（核心）
    // ====================================================================

    /**
     * 推荐护肤方案：基于肤质+关注问题+历史偏好
     */
    @PostMapping("/recommend")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> recommend(@RequestBody(required = false) Map<String, Object> body) {
        long t0 = System.currentTimeMillis();
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = body == null || body.get("memberId") == null
                ? null : ((Number) body.get("memberId")).longValue();
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "会员ID必填");

        // 1) 取最新肤质档案
        SkinProfile profile = skinProfileMapper.selectOne(new LambdaQueryWrapper<SkinProfile>()
                .eq(SkinProfile::getTenantId, tenantId)
                .eq(SkinProfile::getMemberId, memberId)
                .orderByDesc(SkinProfile::getAssessDate)
                .last("LIMIT 1"));

        if (profile == null) {
            return Result.error(BizCode.NOT_FOUND, "请先录入肤质档案");
        }

        // 2) 解析关注问题
        List<String> concerns = parseJsonArray(profile.getConcerns());
        if (concerns.isEmpty()) {
            concerns = defaultConcerns(profile);
        }

        // 3) 匹配服务项目
        List<Map<String, Object>> matchedServices = matchServices(tenantId, profile, concerns);
        if (matchedServices.isEmpty()) {
            return Result.error(BizCode.NOT_FOUND, "未找到匹配的服务项目，请检查服务项目表");
        }

        // 4) 匹配疗程方案
        List<Map<String, Object>> matchedPlans = matchTreatmentPlans(tenantId, profile, concerns);

        // 5) 推算周频次 + 总次数 + 周期
        String ageGroup = profile.getAgeGroup() == null ? "MIDDLE" : profile.getAgeGroup();
        int weeklyFreq = computeWeeklyFreq(concerns, ageGroup);
        int totalSessions = computeTotalSessions(concerns, ageGroup);

        // 6) 预估费用
        BigDecimal estimatedCost = matchedServices.stream()
                .map(s -> toBigDecimal(s.get("memberPrice")))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(totalSessions / (double) matchedServices.size()));

        // 7) 生成步骤
        List<Map<String, Object>> steps = generateSteps(matchedServices, weeklyFreq, concerns);

        // 8) 推荐理由
        List<String> reasons = generateReasons(profile, concerns, matchedServices);

        // 9) 持久化
        CarePlan plan = new CarePlan();
        plan.setTenantId(tenantId);
        plan.setMemberId(memberId);
        plan.setPlanCode("CARE_" + System.currentTimeMillis());
        plan.setPlanName(generatePlanName(concerns, ageGroup));
        plan.setGoal(String.join("、", concerns));
        plan.setStartDate(LocalDate.now());
        plan.setEndDate(LocalDate.now().plusDays(28));
        plan.setCycleDays(28);
        plan.setSessionsTotal(totalSessions);
        plan.setSessionsPerWeek(BigDecimal.valueOf(weeklyFreq));
        plan.setEstimatedCost(estimatedCost.setScale(2, RoundingMode.HALF_UP));
        plan.setStepsJson(toJson(steps));
        plan.setReasonJson(toJson(reasons.stream().map(r -> Map.of("reason", r)).collect(Collectors.toList())));
        plan.setServiceIds(matchedServices.stream().map(s -> s.get("id").toString()).collect(Collectors.joining(",")));
        plan.setStatus("ACTIVE");
        plan.setAdherenceScore(BigDecimal.ZERO);
        carePlanMapper.insert(plan);

        // 10) 输出
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plan", plan);
        data.put("services", matchedServices);
        data.put("treatmentPlans", matchedPlans);
        data.put("steps", steps);
        data.put("reasons", reasons);
        data.put("concerns", concerns);
        data.put("skinType", profile.getSkinType());
        data.put("ageGroup", ageGroup);
        data.put("durationMs", System.currentTimeMillis() - t0);
        return Result.success("已推荐", data);
    }

    // ====================================================================
    // 匹配算法
    // ====================================================================

    private List<Map<String, Object>> matchServices(Long tenantId, SkinProfile profile, List<String> concerns) {
        // 关键词列表
        Set<String> keywords = new HashSet<>();
        for (String c : concerns) {
            List<String> kws = CONCERN_TO_SERVICE.getOrDefault(c, List.of());
            keywords.addAll(kws);
        }
        if (keywords.isEmpty()) keywords.add("基础护理");

        // 拼接 IN 条件
        String keywordList = keywords.stream()
                .map(k -> "'%" + k + "%'")
                .collect(Collectors.joining(","));

        // 敏感肤质过滤（避免推荐强刺激项目）
        String sensitiveFilter = "SENSITIVE".equals(profile.getSkinType())
                ? "AND (s.service_name NOT LIKE '%酸%' AND s.service_name NOT LIKE '%A醇%' AND s.service_name NOT LIKE '%激光%')"
                : "";

        String sql = """
            SELECT s.id, s.service_code, s.service_name AS name, s.member_price AS memberPrice,
                   s.duration, s.sop, c.category_name AS categoryName
            FROM biz_service_item s
            LEFT JOIN biz_service_category c ON c.id = s.category_id
            WHERE s.deleted = 0 AND s.tenant_id = ? AND s.status = 1
              AND (s.service_name LIKE ANY_VALUE(?)
                   OR s.sop LIKE ANY_VALUE(?))
              AND s.service_name NOT LIKE '%测试%'
            """ + sensitiveFilter + """
            GROUP BY s.id
            ORDER BY s.sales_count DESC
            LIMIT 8
            """;

        List<Map<String, Object>> all = new ArrayList<>();
        try {
            for (String kw : keywords) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT s.id, s.service_code, s.service_name AS name, s.member_price AS memberPrice, " +
                                "s.duration, s.sop, c.category_name AS categoryName, s.sales_count " +
                                "FROM biz_service_item s LEFT JOIN biz_service_category c ON c.id = s.category_id " +
                                "WHERE s.deleted = 0 AND s.tenant_id = ? AND s.status = 1 " +
                                "AND (s.service_name LIKE ? OR s.sop LIKE ?)" +
                                (sensitiveFilter.isEmpty() ? "" : sensitiveFilter) +
                                " ORDER BY s.sales_count DESC LIMIT 5",
                        tenantId, "%" + kw + "%", "%" + kw + "%");
                for (Map<String, Object> r : rows) {
                    if (all.stream().noneMatch(a -> a.get("id").equals(r.get("id")))) {
                        all.add(r);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("服务项目匹配降级: {}", e.getMessage());
        }

        // 兜底：热销 Top 5
        if (all.isEmpty()) {
            try {
                all = jdbc.queryForList(
                        "SELECT id, service_code, service_name AS name, member_price AS memberPrice, " +
                                "duration, sop, sales_count FROM biz_service_item " +
                                "WHERE deleted = 0 AND tenant_id = ? AND status = 1 " +
                                "ORDER BY sales_count DESC LIMIT 5",
                        tenantId);
            } catch (Exception e) {
                log.warn("热销服务降级: {}", e.getMessage());
            }
        }
        return all.size() > 6 ? all.subList(0, 6) : all;
    }

    private List<Map<String, Object>> matchTreatmentPlans(Long tenantId, SkinProfile profile, List<String> concerns) {
        String sql = """
            SELECT id, plan_code, plan_name AS name, total_sessions, cycle_days,
                   member_price AS memberPrice
            FROM biz_treatment_plan
            WHERE deleted = 0 AND tenant_id = ? AND status = 1
            ORDER BY plan_price ASC LIMIT 5
            """;
        try {
            return jdbc.queryForList(sql, tenantId);
        } catch (Exception e) {
            log.warn("疗程方案匹配降级: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        // 简化解析：["acne","wrinkle"]
        String content = json.replaceAll("[\\[\\]\"]", "");
        if (content.isEmpty()) return new ArrayList<>();
        return Arrays.stream(content.split(",")).map(String::trim).collect(Collectors.toList());
    }

    private List<String> defaultConcerns(SkinProfile p) {
        List<String> c = new ArrayList<>();
        if (p.getOilLevel() != null && p.getOilLevel() >= 7) c.add("acne");
        if (p.getPigmentationLevel() != null && p.getPigmentationLevel() >= 6) c.add("pigmentation");
        if (p.getWrinkleLevel() != null && p.getWrinkleLevel() >= 6) c.add("wrinkle");
        if (p.getMoistureLevel() != null && p.getMoistureLevel() <= 4) c.add("dryness");
        if (p.getSensitivityLevel() != null && p.getSensitivityLevel() >= 7) c.add("sensitivity");
        if (c.isEmpty()) c.add("dullness");
        return c;
    }

    private int computeWeeklyFreq(List<String> concerns, String ageGroup) {
        int base = "MATURE".equals(ageGroup) ? 2 : "YOUNG".equals(ageGroup) ? 1 : 1;
        if (concerns.contains("acne") || concerns.contains("wrinkle")) base++;
        return Math.min(3, base);
    }

    private int computeTotalSessions(List<String> concerns, String ageGroup) {
        int base = 8;
        if (concerns.contains("wrinkle") || concerns.contains("pigmentation")) base += 4;
        if ("MATURE".equals(ageGroup)) base += 2;
        return base;
    }

    private List<Map<String, Object>> generateSteps(List<Map<String, Object>> services, int weeklyFreq, List<String> concerns) {
        List<Map<String, Object>> steps = new ArrayList<>();
        int idx = 1;
        for (Map<String, Object> svc : services) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", idx++);
            step.put("serviceId", svc.get("id"));
            step.put("serviceName", svc.get("name"));
            step.put("frequency", "每周 " + weeklyFreq + " 次");
            step.put("purpose", svc.getOrDefault("sop", "改善" + String.join("、", concerns)));
            steps.add(step);
        }
        return steps;
    }

    private List<String> generateReasons(SkinProfile profile, List<String> concerns, List<Map<String, Object>> services) {
        List<String> reasons = new ArrayList<>();
        reasons.add("基于肤质类型：「" + profile.getSkinType() + "」量身定制");
        if (!concerns.isEmpty()) {
            reasons.add("针对您的核心问题：" + String.join("、", concerns) + " 设计");
        }
        if ("MATURE".equals(profile.getAgeGroup())) {
            reasons.add("结合年龄段（" + profile.getAgeGroup() + "）加强抗衰方案");
        }
        if (services.size() > 0) {
            reasons.add("推荐 " + services.size() + " 个匹配项目，按" + computeWeeklyFreq(concerns, profile.getAgeGroup()) + "次/周 节奏");
        }
        return reasons;
    }

    private String generatePlanName(List<String> concerns, String ageGroup) {
        String prefix = concerns.isEmpty() ? "全方位" : concerns.get(0);
        return prefix + "护理方案";
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String toJson(Object obj) {
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) obj;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            Map<?, ?> m = (Map<?, ?>) obj;
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Number) sb.append(v);
                else if (v == null) sb.append("null");
                else sb.append("\"").append(v.toString().replace("\"", "\\\"")).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + obj + "\"";
    }

    // ====================================================================
    // 方案查询
    // ====================================================================

    @GetMapping("/list/{memberId}")
    public Result<List<CarePlan>> listPlans(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(carePlanMapper.selectList(new LambdaQueryWrapper<CarePlan>()
                .eq(CarePlan::getTenantId, tenantId)
                .eq(CarePlan::getMemberId, memberId)
                .orderByDesc(CarePlan::getCreatedAt)));
    }

    @PostMapping("/pause/{id}")
    public Result<Void> pause(@PathVariable Long id) {
        CarePlan p = carePlanMapper.selectById(id);
        if (p == null) return Result.error(BizCode.NOT_FOUND, "方案不存在");
        p.setStatus("PAUSED");
        carePlanMapper.updateById(p);
        return Result.success("已暂停", null);
    }
}