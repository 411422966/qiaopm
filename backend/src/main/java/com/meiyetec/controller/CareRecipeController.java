package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * P0.2 配方 / 皮肤检测 / 智能推荐（/care/*）
 *
 *  路由（原写在 service 包下的 CareSuggestionService 类里，现迁到 controller 包）：
 *  - GET    /care/recipe/list?skinType&concern       → listRecipes
 *  - GET    /care/recipe/{id}                          → getRecipe
 *  - POST   /care/recipe                               → createRecipe
 *  - POST   /care/recipe/{id}                          → updateRecipe
 *  - POST   /care/recipe/{id}/item                     → addItem
 *  - POST   /care/recipe/init-default                  → initDefaultRecipes
 *  - POST   /care/assessment                           → submitAssessment
 *  - GET    /care/assessment/by-member/{memberId}      → historyByMember
 *  - POST   /care/recommend                            → recommend
 *
 *  数据源：
 *  - care_recipe          （新建，含 tenant_id）
 *  - care_recipe_item     （新建，FK recipe_id）
 *  - care_skin_assessment （新建，含 tenant_id）
 */
@RestController
@RequestMapping("/care")
@RequiredArgsConstructor
public class CareRecipeController {

    private final CareRecipeMapper recipeMapper;
    private final CareRecipeItemMapper itemMapper;
    private final CareSkinAssessmentMapper assessmentMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    // ====================================================================
    // 1. 配方管理
    // ====================================================================

    @PostMapping("/recipe")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:recipe:edit")
    public Result<CareRecipe> createRecipe(@RequestBody CareRecipe r) {
        Long tenantId = tid();
        if (r.getRecipeCode() == null || r.getRecipeName() == null)
            return Result.error(BizCode.BAD_REQUEST, "recipeCode / recipeName 必填");
        if (r.getStatus() == null) r.setStatus("ACTIVE");
        if (r.getSessionCount() == null) r.setSessionCount(4);
        if (r.getIntervalDays() == null) r.setIntervalDays(14);
        if (r.getScore() == null) r.setScore(80);
        r.setTenantId(tenantId);
        r.setId(null);
        if (r.getUseCount() == null) r.setUseCount(0);
        // 唯一性：同租户 recipeCode 不可重复
        Long dup = recipeMapper.selectCount(new LambdaQueryWrapper<CareRecipe>()
                .eq(CareRecipe::getTenantId, tenantId)
                .eq(CareRecipe::getRecipeCode, r.getRecipeCode()));
        if (dup != null && dup > 0) return Result.error(BizCode.CONFLICT, "配方编码已存在");
        recipeMapper.insert(r);
        return Result.success("已创建", r);
    }

    @PostMapping("/recipe/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:recipe:edit")
    public Result<CareRecipe> updateRecipe(@PathVariable Long id, @RequestBody CareRecipe r) {
        Long tenantId = tid();
        CareRecipe exist = recipeMapper.selectById(id);
        if (exist == null || !tenantId.equals(exist.getTenantId()))
            return Result.error(BizCode.NOT_FOUND, "配方不存在");
        r.setId(id);
        r.setTenantId(tenantId);
        recipeMapper.updateById(r);
        return Result.success("已更新", recipeMapper.selectById(id));
    }

    @GetMapping("/recipe/list")
    @RequirePermission("crm:care:recipe:list")
    public Result<List<CareRecipe>> listRecipes(@RequestParam(required = false) String skinType,
                                                @RequestParam(required = false) String concern) {
        Long tenantId = tid();
        LambdaQueryWrapper<CareRecipe> qw = new LambdaQueryWrapper<CareRecipe>()
                .eq(CareRecipe::getTenantId, tenantId)
                .ne(CareRecipe::getStatus, "INACTIVE");
        if (skinType != null && !skinType.isEmpty()) qw.eq(CareRecipe::getSkinType, skinType);
        if (concern != null && !concern.isEmpty()) qw.like(CareRecipe::getConcerns, concern);
        qw.orderByDesc(CareRecipe::getScore).orderByDesc(CareRecipe::getUseCount);
        return Result.success(recipeMapper.selectList(qw));
    }

    @GetMapping("/recipe/{id}")
    @RequirePermission("crm:care:recipe:list")
    public Result<Map<String, Object>> getRecipe(@PathVariable Long id) {
        Long tenantId = tid();
        CareRecipe r = recipeMapper.selectById(id);
        if (r == null || !tenantId.equals(r.getTenantId()))
            return Result.error(BizCode.NOT_FOUND, "配方不存在");
        List<CareRecipeItem> items = itemMapper.selectList(new LambdaQueryWrapper<CareRecipeItem>()
                .eq(CareRecipeItem::getRecipeId, id)
                .orderByAsc(CareRecipeItem::getSequence));
        // 自增 use_count
        jdbc.update("UPDATE care_recipe SET use_count = use_count + 1 WHERE id = ?", id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipe", r);
        data.put("items", items);
        return Result.success(data);
    }

    @PostMapping("/recipe/{id}/item")
    @RequirePermission("crm:care:recipe:edit")
    public Result<CareRecipeItem> addItem(@PathVariable Long id, @RequestBody CareRecipeItem item) {
        Long tenantId = tid();
        CareRecipe exist = recipeMapper.selectById(id);
        if (exist == null || !tenantId.equals(exist.getTenantId()))
            return Result.error(BizCode.NOT_FOUND, "配方不存在");
        if (item.getItemName() == null) return Result.error(BizCode.BAD_REQUEST, "itemName 必填");
        if (item.getItemType() == null) item.setItemType("STEP");
        item.setRecipeId(id);
        item.setId(null);
        itemMapper.insert(item);
        return Result.success("已添加", item);
    }

    @PostMapping("/recipe/init-default")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:recipe:edit")
    public Result<Map<String, Object>> initDefaultRecipes() {
        Long tenantId = tid();
        List<Map<String, Object>> defaults = List.of(
                Map.ofEntries(
                        Map.entry("code", "WHITENING_BASIC"),
                        Map.entry("name", "Whitening Basic"),
                        Map.entry("skinType", "ALL"),
                        Map.entry("concerns", "WHITENING,DULL"),
                        Map.entry("description", "VC + whitening mask + sunscreen"),
                        Map.entry("sessionCount", 6),
                        Map.entry("intervalDays", 7),
                        Map.entry("cost", 1880),
                        Map.entry("improvement", "Brighten skin, fade spots"),
                        Map.entry("tag", "HOT"),
                        Map.entry("season", "SUMMER")
                ),
                Map.ofEntries(
                        Map.entry("code", "ANTI_AGE_PREMIUM"),
                        Map.entry("name", "Anti-Age Premium"),
                        Map.entry("skinType", "MATURE"),
                        Map.entry("concerns", "ANTI_AGE,WRINKLE"),
                        Map.entry("description", "RF + hyaluronic acid + collagen"),
                        Map.entry("sessionCount", 12),
                        Map.entry("intervalDays", 14),
                        Map.entry("cost", 8880),
                        Map.entry("improvement", "Firming, reduce wrinkles"),
                        Map.entry("tag", "RECOMMEND")
                ),
                Map.ofEntries(
                        Map.entry("code", "ACNE_CLEAR"),
                        Map.entry("name", "Acne Clear"),
                        Map.entry("skinType", "OILY"),
                        Map.entry("concerns", "ACNE,PORE"),
                        Map.entry("description", "AHA peel + red/blue light + clearing"),
                        Map.entry("sessionCount", 8),
                        Map.entry("intervalDays", 10),
                        Map.entry("cost", 3280),
                        Map.entry("improvement", "Anti-inflammation, refine pores"),
                        Map.entry("tag", "NEW")
                ),
                Map.ofEntries(
                        Map.entry("code", "DRY_REPAIR"),
                        Map.entry("name", "Dry Repair"),
                        Map.entry("skinType", "DRY"),
                        Map.entry("concerns", "DRY,REDNESS"),
                        Map.entry("description", "Hyaluronic infusion + soothing mask"),
                        Map.entry("sessionCount", 5),
                        Map.entry("intervalDays", 7),
                        Map.entry("cost", 1280),
                        Map.entry("improvement", "Hydrate and soothe redness"),
                        Map.entry("tag", "SEASONAL"),
                        Map.entry("season", "WINTER")
                ),
                Map.ofEntries(
                        Map.entry("code", "SENSITIVE_CALM"),
                        Map.entry("name", "Sensitive Calm"),
                        Map.entry("skinType", "SENSITIVE"),
                        Map.entry("concerns", "REDNESS,SENSITIVE"),
                        Map.entry("description", "Chamomile soothing + repair essence"),
                        Map.entry("sessionCount", 6),
                        Map.entry("intervalDays", 10),
                        Map.entry("cost", 1680),
                        Map.entry("improvement", "Repair barrier, reduce sensitivity"),
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
            r.setEstimatedCost(new BigDecimal(d.get("cost").toString()));
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
    // 2. 皮肤检测
    // ====================================================================

    @PostMapping("/assessment")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("crm:care:recipe:edit")
    public Result<Map<String, Object>> submitAssessment(@RequestBody Map<String, Object> body) {
        Long tenantId = tid();
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
        List<Long> recommendations = recommendByAssessment(a, tenantId);
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

    private List<Long> recommendByAssessment(CareSkinAssessment a, Long tenantId) {
        LambdaQueryWrapper<CareRecipe> qw = new LambdaQueryWrapper<CareRecipe>()
                .eq(CareRecipe::getTenantId, tenantId)
                .ne(CareRecipe::getStatus, "INACTIVE");
        if (a.getSkinType() != null) {
            qw.and(w -> w.eq(CareRecipe::getSkinType, a.getSkinType())
                    .or().eq(CareRecipe::getSkinType, "ALL"));
        }
        List<CareRecipe> candidates = recipeMapper.selectList(qw);
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
        m.put("summary", a.getSkinScore() >= 80 ? "good" : a.getSkinScore() >= 60 ? "average" : "needs improvement");
        List<String> issues = new ArrayList<>();
        if (a.getMoistureLevel() != null && a.getMoistureLevel() < 5) issues.add("dehydration");
        if (a.getOilLevel() != null && a.getOilLevel() > 7) issues.add("oily");
        if (a.getPoreLevel() != null && a.getPoreLevel() > 6) issues.add("large pores");
        if (a.getWrinkleLevel() != null && a.getWrinkleLevel() > 4) issues.add("fine lines");
        m.put("issues", issues);
        Map<String, Object> m2 = new LinkedHashMap<>();
        m2.put("score", a.getSkinScore());
        m2.put("summary", m.get("summary"));
        m2.put("issues", issues);
        m2.put("recommendations", rec);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m2);
        } catch (Exception e) {
            return "{}";
        }
    }

    @GetMapping("/assessment/by-member/{memberId}")
    @RequirePermission("crm:care:recipe:list")
    public Result<List<CareSkinAssessment>> historyByMember(@PathVariable Long memberId) {
        Long tenantId = tid();
        return Result.success(assessmentMapper.selectList(new LambdaQueryWrapper<CareSkinAssessment>()
                .eq(CareSkinAssessment::getTenantId, tenantId)
                .eq(CareSkinAssessment::getMemberId, memberId)
                .orderByDesc(CareSkinAssessment::getAssessmentDate)));
    }

    // ====================================================================
    // 3. 智能推荐
    // ====================================================================

    @PostMapping("/recommend")
    @RequirePermission("crm:care:recipe:list")
    public Result<List<CareRecipe>> recommend(@RequestBody Map<String, Object> body) {
        Long tenantId = tid();
        CareSkinAssessment fake = new CareSkinAssessment();
        fake.setTenantId(tenantId);
        fake.setSkinType((String) body.get("skinType"));
        String concerns = (String) body.get("concerns");
        if (concerns != null) fake.setConcernsJson(concerns);
        List<Long> ids = recommendByAssessment(fake, tenantId);
        if (ids.isEmpty()) return Result.success(new ArrayList<>());
        return Result.success(recipeMapper.selectBatchIds(ids));
    }

    // ====================================================================
    // 4. 工具
    // ====================================================================

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}