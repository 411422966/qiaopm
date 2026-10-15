package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CareRecipe;
import com.meiyetec.entity.CareRecipeItem;
import com.meiyetec.entity.CareSkinAssessment;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.CareRecipeItemMapper;
import com.meiyetec.mapper.CareRecipeMapper;
import com.meiyetec.mapper.CareSkinAssessmentMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P0.2 配方/护理建议（按 FEATURE_COMPARISON_MATRIX.md 244-251 P0-2 项独立实现）
 *
 * 模块：
 * 1. 配方管理（care_recipe + care_recipe_item）
 * 2. 皮肤检测（care_skin_assessment）
 * 3. 智能推荐（基于肤质 + 问题 + 年龄 + 季节）
 * 4. 跟进追踪
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CareSuggestionService {

    private final CareRecipeMapper recipeMapper;
    private final CareRecipeItemMapper itemMapper;
    private final CareSkinAssessmentMapper assessmentMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // T1. 配方管理
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<CareRecipe> createRecipe(@RequestBody CareRecipe r) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (r.getRecipeCode() == null || r.getRecipeName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (r.getStatus() == null) r.setStatus("ACTIVE");
        if (r.getSessionCount() == null) r.setSessionCount(4);
        if (r.getIntervalDays() == null) r.setIntervalDays(14);
        if (r.getScore() == null) r.setScore(80);
        r.setTenantId(tenantId);
        r.setId(null);
        r.setUseCount(0);
        recipeMapper.insert(r);
        return Result.success("已创建", r);
    }

    public Result<CareRecipe> updateRecipe(@PathVariable Long id, @RequestBody CareRecipe r) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CareRecipe exist = recipeMapper.selectById(id);
        if (exist == null || !exist.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "配方不存在");
        r.setId(id);
        r.setTenantId(tenantId);
        recipeMapper.updateById(r);
        return Result.success("已更新", recipeMapper.selectById(id));
    }

    public Result<List<CareRecipe>> listRecipes(@RequestParam(required = false) String skinType,
                                                @RequestParam(required = false) String concern) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<CareRecipe> qw = new LambdaQueryWrapper<CareRecipe>()
                .eq(CareRecipe::getTenantId, tenantId)
                .eq(CareRecipe::getStatus, "ACTIVE");
        if (skinType != null && !skinType.isEmpty()) qw.eq(CareRecipe::getSkinType, skinType);
        if (concern != null && !concern.isEmpty()) qw.like(CareRecipe::getConcerns, concern);
        qw.orderByDesc(CareRecipe::getScore).orderByDesc(CareRecipe::getUseCount);
        return Result.success(recipeMapper.selectList(qw));
    }

    public Result<Map<String, Object>> getRecipe(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        CareRecipe r = recipeMapper.selectById(id);
        if (r == null || !r.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "配方不存在");
        List<CareRecipeItem> items = itemMapper.selectList(new LambdaQueryWrapper<CareRecipeItem>()
                .eq(CareRecipeItem::getRecipeId, id).orderByAsc(CareRecipeItem::getSequence));
        // 自增 use_count
        jdbc.update("UPDATE care_recipe SET use_count = use_count + 1 WHERE id = ?", id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipe", r);
        data.put("items", items);
        return Result.success(data);
    }

    public Result<CareRecipeItem> addItem(@PathVariable Long id, @RequestBody CareRecipeItem item) {
        if (item.getItemName() == null)
            return Result.error(BizCode.BAD_REQUEST, "项目名称必填");
        if (item.getItemType() == null) item.setItemType("STEP");
        item.setRecipeId(id);
        item.setId(null);
        itemMapper.insert(item);
        return Result.success("已添加", item);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> initDefaultRecipes() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                Map.ofEntries(
                        Map.entry("code", "WHITENING_BASIC"),
                        Map.entry("name", "美白基础护理"),
                        Map.entry("skinType", "ALL"),
                        Map.entry("concerns", "WHITENING,DULL"),
                        Map.entry("description", "VC 精华 + 美白面膜 + 防晒"),
                        Map.entry("sessionCount", 6),
                        Map.entry("intervalDays", 7),
                        Map.entry("cost", 1880),
                        Map.entry("improvement", "提亮肤色，淡化色斑"),
                        Map.entry("tag", "HOT"),
                        Map.entry("season", "SUMMER")
                ),
                Map.ofEntries(
                        Map.entry("code", "ANTI_AGE_PREMIUM"),
                        Map.entry("name", "抗衰至尊方案"),
                        Map.entry("skinType", "MATURE"),
                        Map.entry("concerns", "ANTI_AGE,WRINKLE"),
                        Map.entry("description", "射频 + 玻尿酸 + 胶原蛋白疗程"),
                        Map.entry("sessionCount", 12),
                        Map.entry("intervalDays", 14),
                        Map.entry("cost", 8880),
                        Map.entry("improvement", "紧致提拉，淡化皱纹"),
                        Map.entry("tag", "RECOMMEND")
                ),
                Map.ofEntries(
                        Map.entry("code", "ACNE_CLEAR"),
                        Map.entry("name", "痘痘清护方案"),
                        Map.entry("skinType", "OILY"),
                        Map.entry("concerns", "ACNE,PORE"),
                        Map.entry("description", "果酸焕肤 + 红蓝光 + 清痘护理"),
                        Map.entry("sessionCount", 8),
                        Map.entry("intervalDays", 10),
                        Map.entry("cost", 3280),
                        Map.entry("improvement", "消炎祛痘，细致毛孔"),
                        Map.entry("tag", "NEW")
                ),
                Map.ofEntries(
                        Map.entry("code", "DRY_REPAIR"),
                        Map.entry("name", "干燥修复方案"),
                        Map.entry("skinType", "DRY,SENSITIVE"),
                        Map.entry("concerns", "DRY,REDNESS"),
                        Map.entry("description", "玻尿酸导入 + 舒缓面膜"),
                        Map.entry("sessionCount", 5),
                        Map.entry("intervalDays", 7),
                        Map.entry("cost", 1280),
                        Map.entry("improvement", "补水保湿，舒缓泛红"),
                        Map.entry("tag", "SEASONAL"),
                        Map.entry("season", "WINTER")
                ),
                Map.ofEntries(
                        Map.entry("code", "SENSITIVE_CALM"),
                        Map.entry("name", "敏感肌舒缓"),
                        Map.entry("skinType", "SENSITIVE"),
                        Map.entry("concerns", "REDNESS,SENSITIVE"),
                        Map.entry("description", "洋甘菊舒缓 + 修复精华"),
                        Map.entry("sessionCount", 6),
                        Map.entry("intervalDays", 10),
                        Map.entry("cost", 1680),
                        Map.entry("improvement", "修复屏障，降低敏感"),
                        Map.entry("tag", "RECOMMEND")
                )
        );

        int created = 0;
        for (Map<String, Object> d : defaults) {
            CareRecipe exist = recipeMapper.selectOne(new LambdaQueryWrapper<CareRecipe>()
                    .eq(CareRecipe::getTenantId, tenantId)
                    .eq(CareRecipe::getRecipeCode, d.get("code")));
            if (exist != null) continue;
            CareRecipe r = new CareRecipe();
            r.setTenantId(tenantId);
            r.setRecipeCode((String) d.get("code"));
            r.setRecipeName((String) d.get("name"));
            r.setSkinType((String) d.get("skinType"));
            r.setConcerns((String) d.get("concerns"));
            r.setDescription((String) d.get("description"));
            r.setSessionCount((Integer) d.get("sessionCount"));
            r.setIntervalDays((Integer) d.get("intervalDays"));
            r.setEstimatedCost(new java.math.BigDecimal(d.get("cost").toString()));
            r.setExpectedImprovement((String) d.get("improvement"));
            r.setTag((String) d.get("tag"));
            r.setApplicableSeason(d.get("season") == null ? "ALL" : (String) d.get("season"));
            r.setScore(85);
            r.setUseCount(0);
            r.setStatus("ACTIVE");
            recipeMapper.insert(r);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("total", defaults.size());
        return Result.success("已初始化", data);
    }

    // ====================================================================
    // T2. 皮肤检测
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> submitAssessment(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        if (memberId == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");

        CareSkinAssessment a = new CareSkinAssessment();
        a.setTenantId(tenantId);
        a.setMemberId(memberId);
        a.setAssessmentNo("AS" + System.currentTimeMillis());
        a.setSkinType((String) body.get("skinType"));
        a.setOilLevel(toInt(body.get("oilLevel")));
        a.setMoistureLevel(toInt(body.get("moistureLevel")));
        a.setElasticityLevel(toInt(body.get("elasticityLevel")));
        a.setPigmentLevel(toInt(body.get("pigmentLevel")));
        a.setPoreLevel(toInt(body.get("poreLevel")));
        a.setWrinkleLevel(toInt(body.get("wrinkleLevel")));
        a.setConcernsJson((String) body.get("concernsJson"));
        a.setSource((String) body.getOrDefault("source", "MANUAL"));
        a.setOperatorId(body.get("operatorId") == null ? null : ((Number) body.get("operatorId")).longValue());

        // 计算综合得分
        int score = calcScore(a);
        a.setSkinScore(score);

        // 自动推荐 Top 3
        List<Long> recommendations = recommendByAssessment(a);
        if (!recommendations.isEmpty()) {
            a.setRecommendedRecipeId(recommendations.get(0));
            a.setRecommendedRecipeIds(recommendations.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }

        // 生成分析
        a.setAnalysisJson(generateAnalysis(a, recommendations));
        assessmentMapper.insert(a);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("assessment", a);
        data.put("recommendedRecipes", recommendations);
        return Result.success("已记录", data);
    }

    /**
     * 综合评分（0-100，分越高皮肤状态越好）
     */
    private int calcScore(CareSkinAssessment a) {
        int score = 100;
        if (a.getOilLevel() != null && a.getOilLevel() > 7) score -= 5;
        if (a.getOilLevel() != null && a.getOilLevel() < 3) score -= 5;
        if (a.getMoistureLevel() != null && a.getMoistureLevel() < 5) score -= 15;
        if (a.getElasticityLevel() != null && a.getElasticityLevel() < 5) score -= 10;
        if (a.getPigmentLevel() != null && a.getPigmentLevel() > 5) score -= 10;
        if (a.getPoreLevel() != null && a.getPoreLevel() > 6) score -= 8;
        if (a.getWrinkleLevel() != null && a.getWrinkleLevel() > 4) score -= 15;
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 推荐 Top 3
     */
    private List<Long> recommendByAssessment(CareSkinAssessment a) {
        LambdaQueryWrapper<CareRecipe> qw = new LambdaQueryWrapper<CareRecipe>()
                .eq(CareRecipe::getTenantId, a.getTenantId())
                .eq(CareRecipe::getStatus, "ACTIVE");

        // 肤质匹配（优先严格匹配，其次 ALL）
        if (a.getSkinType() != null) {
            qw.and(w -> w.eq(CareRecipe::getSkinType, a.getSkinType())
                    .or().eq(CareRecipe::getSkinType, "ALL"));
        }

        List<CareRecipe> candidates = recipeMapper.selectList(qw);

        // 计算匹配分
        return candidates.stream()
                .map(r -> {
                    int match = 0;
                    if (a.getSkinType() != null && a.getSkinType().equals(r.getSkinType())) match += 30;
                    if (a.getSkinType() != null && "ALL".equals(r.getSkinType())) match += 10;
                    if (a.getConcernsJson() != null && r.getConcerns() != null) {
                        for (String c : a.getConcernsJson().replace("[", "").replace("]", "")
                                .replace("\"", "").split(",")) {
                            if (r.getConcerns().contains(c.trim())) match += 20;
                        }
                    }
                    match += r.getScore() != null ? r.getScore() / 5 : 0;
                    return new AbstractMap.SimpleEntry<>(r, match);
                })
                .filter(e -> e.getValue() >= 20)
                .sorted((a2, b) -> Integer.compare(b.getValue(), a2.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .map(CareRecipe::getId)
                .collect(Collectors.toList());
    }

    private String generateAnalysis(CareSkinAssessment a, List<Long> rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", a.getSkinScore());
        m.put("summary", a.getSkinScore() >= 80 ? "良好" : a.getSkinScore() >= 60 ? "一般" : "需改善");
        m.put("issues", new ArrayList<>());
        if (a.getMoistureLevel() != null && a.getMoistureLevel() < 5)
            ((List<String>) m.get("issues")).add("缺水");
        if (a.getOilLevel() != null && a.getOilLevel() > 7)
            ((List<String>) m.get("issues")).add("油腻");
        if (a.getPoreLevel() != null && a.getPoreLevel() > 6)
            ((List<String>) m.get("issues")).add("毛孔粗大");
        if (a.getWrinkleLevel() != null && a.getWrinkleLevel() > 4)
            ((List<String>) m.get("issues")).add("细纹");
        m.put("recommendations", rec);
        return toJson(m);
    }

    private String toJson(Object o) {
        // 极简 JSON（避免引入 jackson 复杂依赖）
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 会员检测历史
     */
    public Result<List<CareSkinAssessment>> historyByMember(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(assessmentMapper.selectList(new LambdaQueryWrapper<CareSkinAssessment>()
                .eq(CareSkinAssessment::getTenantId, tenantId)
                .eq(CareSkinAssessment::getMemberId, memberId)
                .orderByDesc(CareSkinAssessment::getAssessmentDate)));
    }

    /**
     * 推荐方案（API，供前端/小程序调用）
     */
    public Result<List<CareRecipe>> recommend(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 模拟 assessment 推荐
        CareSkinAssessment fake = new CareSkinAssessment();
        fake.setTenantId(tenantId);
        fake.setSkinType((String) body.get("skinType"));
        String concerns = (String) body.get("concerns");
        if (concerns != null) fake.setConcernsJson(concerns);
        List<Long> ids = recommendByAssessment(fake);
        if (ids.isEmpty()) return Result.success(new ArrayList<>());
        return Result.success(recipeMapper.selectBatchIds(ids));
    }

    // ====================================================================
    // 工具
    // ====================================================================
    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}