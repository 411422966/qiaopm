package com.meiyetec.controller;

import com.meiyetec.util.GrayReleaseHasher;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.AbAssignment;
import com.meiyetec.entity.AbExperiment;
import com.meiyetec.entity.FeatureFlag;
import com.meiyetec.entity.PricingExperimentResult;
import com.meiyetec.exception.BizCode;
import com.meiyetec.featureflag.FeatureFlagEvaluator;
import com.meiyetec.mapper.AbAssignmentMapper;
import com.meiyetec.mapper.AbExperimentMapper;
import com.meiyetec.mapper.FeatureFlagMapper;
import com.meiyetec.mapper.PricingExperimentMapper;
import com.meiyetec.mapper.PricingExperimentResultMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-H P2.6 + AI-006：灰度发布 / AB Test
 *
 * <p>提供：
 * <ul>
 *   <li>Feature Flag：开关 + 灰度百分比 + 白/黑名单（评估逻辑委托给 {@link FeatureFlagEvaluator}）</li>
 *   <li>AB Test：实验 + 变体 + 用户分桶 + 指标统计</li>
 * </ul>
 *
 * <p>AI-006 改造点：
 * <ul>
 *   <li>评估逻辑从静态 {@code FLAG_CACHE} 改成 Spring 注入的 {@link FeatureFlagEvaluator}（替换点）</li>
 *   <li>默认实现 In-Memory + 5s 轮询实现多实例热加载</li>
 *   <li>{@code feature.launchdarkly.enabled=true} 时切换到 LaunchDarkly SDK（流式推送）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/gray")
@RequiredArgsConstructor
public class GrayReleaseController {

    private final FeatureFlagMapper flagMapper;
    private final AbExperimentMapper expMapper;
    private final AbAssignmentMapper assignmentMapper;
    /** AI-007：M14 联动 — 定价实验结果写入 */
    private final PricingExperimentMapper pricingExpMapper;
    private final PricingExperimentResultMapper pricingResultMapper;

    @Autowired
    private JdbcTemplate jdbc;

    /** AI-006：评估器抽象（Spring 根据 {@code feature.launchdarkly.enabled} 自动选实现） */
    @Autowired
    private FeatureFlagEvaluator evaluator;

    // ====================================================================
    // 1. Feature Flag
    // ====================================================================

    @PostMapping("/flag")
    @Transactional(rollbackFor = Exception.class)
    public Result<FeatureFlag> createFlag(@RequestBody FeatureFlag flag) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        flag.setTenantId(tenantId);
        if (flag.getEnabled() == null) flag.setEnabled(0);
        if (flag.getRolloutPercent() == null) flag.setRolloutPercent(0);
        if (flag.getUserRule() == null) flag.setUserRule("HASH");

        FeatureFlag exist = flagMapper.selectOne(
                new LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)
                        .eq(FeatureFlag::getFlagKey, flag.getFlagKey()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "flagKey 已存在");

        flagMapper.insert(flag);
        // AI-006：写后立即刷新本地缓存（让当次 evaluate 立刻生效）
        evaluator.refresh(tenantId, null);
        return Result.success("已创建", flag);
    }

    @PostMapping("/flag/{key}/toggle")
    @Transactional(rollbackFor = Exception.class)
    public Result<FeatureFlag> toggleFlag(@PathVariable String key, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        FeatureFlag f = flagMapper.selectOne(
                new LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)
                        .eq(FeatureFlag::getFlagKey, key));
        if (f == null) return Result.error(BizCode.NOT_FOUND, "flag 不存在");
        if (body.get("enabled") != null) f.setEnabled(((Number) body.get("enabled")).intValue());
        if (body.get("rolloutPercent") != null) f.setRolloutPercent(((Number) body.get("rolloutPercent")).intValue());
        flagMapper.updateById(f);
        // AI-006：热加载 — 让所有 evaluate() 立刻看到变更（无需重启）
        evaluator.refresh(tenantId, key);
        return Result.success("已更新", f);
    }

    /**
     * 评估 feature flag 对某用户是否启用
     */
    @GetMapping("/flag/{key}/evaluate")
    public Result<Map<String, Object>> evaluateFlag(@PathVariable String key,
                                                       @RequestParam Long userId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        FeatureFlagEvaluator.EvalResult er = evaluator.evaluate(tenantId, key, userId, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("flagKey", key);
        data.put("userId", userId);
        data.put("enabled", er.enabled());
        // 兼容旧 API 的 reason：enabled ? "match" : "not_match"
        data.put("reason", er.enabled() ? "match" : "not_match");
        data.put("detail", er.reason());
        data.put("evaluatorMode", evaluator.mode());
        return Result.success(data);
    }

    @GetMapping("/flag/list")
    public Result<List<FeatureFlag>> listFlags() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(flagMapper.selectList(
                new LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)));
    }

    /**
     * AI-006：返回当前评估器来源（监控用）
     */
    @GetMapping("/flag/mode")
    public Result<Map<String, Object>> mode() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", evaluator.mode());
        data.put("message", "IN_MEMORY".equals(evaluator.mode())
                ? "本地评估：5s 轮询 + 写后即时刷新；开启 feature.launchdarkly.enabled=true 切换到 LD SDK"
                : "LaunchDarkly 流式推送：开关变更毫秒级生效");
        return Result.success(data);
    }

    // ====================================================================
    // 2. AB Test
    // ====================================================================

    @PostMapping("/experiment")
    @Transactional(rollbackFor = Exception.class)
    public Result<AbExperiment> createExperiment(@RequestBody AbExperiment exp) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        exp.setTenantId(tenantId);
        if (exp.getStatus() == null) exp.setStatus("DRAFT");
        if (exp.getTargetRule() == null) exp.setTargetRule("USER_ID_HASH");

        AbExperiment exist = expMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getTenantId, tenantId)
                        .eq(AbExperiment::getExpKey, exp.getExpKey()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "expKey 已存在");
        expMapper.insert(exp);
        return Result.success("已创建", exp);
    }

    @PostMapping("/experiment/{key}/start")
    @Transactional(rollbackFor = Exception.class)
    public Result<AbExperiment> startExperiment(@PathVariable String key) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        AbExperiment e = expMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getTenantId, tenantId)
                        .eq(AbExperiment::getExpKey, key));
        if (e == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        e.setStatus("RUNNING");
        if (e.getStartAt() == null) e.setStartAt(LocalDateTime.now());
        expMapper.updateById(e);
        return Result.success("已启动", e);
    }

    /**
     * 用户分桶（一致性 hash）
     */
    @GetMapping("/experiment/{key}/assign")
    public Result<Map<String, Object>> assignVariant(@PathVariable String key,
                                                       @RequestParam Long userId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        AbExperiment e = expMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getTenantId, tenantId)
                        .eq(AbExperiment::getExpKey, key));
        if (e == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        if (!"RUNNING".equals(e.getStatus()))
            return Result.error(BizCode.CONFLICT, "实验未运行");

        // 查已分桶
        AbAssignment exist = assignmentMapper.selectOne(
                new LambdaQueryWrapper<AbAssignment>()
                        .eq(AbAssignment::getTenantId, tenantId)
                        .eq(AbAssignment::getExpKey, key)
                        .eq(AbAssignment::getUserId, userId));
        if (exist != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", userId);
            data.put("variantKey", exist.getVariantKey());
            data.put("cached", true);
            return Result.success(data);
        }

        // 新分桶
        String variantKey = pickVariant(e.getVariants(), key + ":" + userId);

        AbAssignment a = new AbAssignment();
        a.setTenantId(tenantId);
        a.setExpKey(key);
        a.setUserId(userId);
        a.setVariantKey(variantKey);
        a.setAssignedAt(LocalDateTime.now());
        assignmentMapper.insert(a);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("variantKey", variantKey);
        data.put("cached", false);
        return Result.success(data);
    }

    /**
     * 简化：从 variants JSON 字符串中解析，按 weight 随机选择
     * 生产应解析 JSON
     */
    private String pickVariant(String variants, String seed) {
        if (variants == null || variants.isEmpty()) return "control";
        String[] parts = variants.split(",");
        long hash = GrayReleaseHasher.stableHash(seed);
        return parts[Math.floorMod(hash, parts.length)].split(":")[0];
    }

    @GetMapping("/experiment/stats")
    public Result<Map<String, Object>> expStats(@RequestParam String key) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> stats = jdbc.queryForList(
            "SELECT variant_key, COUNT(*) AS user_count " +
            "FROM ab_assignment WHERE tenant_id = ? AND exp_key = ? " +
            "GROUP BY variant_key ORDER BY variant_key",
            tenantId, key);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("expKey", key);
        data.put("variantStats", stats);
        data.put("totalUsers", stats.stream().mapToInt(s -> ((Number) s.get("user_count")).intValue()).sum());
        return Result.success(data);
    }

    // ====================================================================
    // AI-007：A/B 测试深化
    //   - GET  /ab-test/{id}/stats        实时卡方统计
    //   - POST /ab-test/{key}/convert     标记转化 → 同步 pricing_experiment_result
    //   - POST /ab-test/{key}/recompute   手动重算（调试用）
    // ====================================================================

    /**
     * 实时统计 + 卡方显著性（基于当前 ab_assignment 的转化数据）
     */
    @GetMapping("/ab-test/{id:\\d+}/stats")
    public Result<Map<String, Object>> abTestStats(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        AbExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        if (!tenantId.equals(exp.getTenantId())) {
            return Result.error(BizCode.TENANT_DENIED, "跨租户访问被拒绝");
        }

        // 1. 拉所有分桶记录
        List<AbAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<AbAssignment>()
                        .eq(AbAssignment::getExpKey, exp.getExpKey()));

        // 2. 聚合观察数据
        Map<String, AbExperiment.VariantStats> observed = AbExperiment.aggregateAssignments(assignments);

        // 3. 卡方检验
        AbExperiment.ChiSquareResult result = exp.computeSignificance(observed);

        // 4. 落库（实时统计字段）
        exp.setChiSquare(result.getChiSquare());
        exp.setChiDf(result.getDf());
        exp.setChiPValue(result.getPValue());
        exp.setChiUpdatedAt(LocalDateTime.now());
        exp.setConclusion(result.getConclusion());
        exp.setBestVariant(result.getBestVariant());
        expMapper.updateById(exp);

        // 5. 构造响应
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", exp.getId());
        data.put("expKey", exp.getExpKey());
        data.put("expName", exp.getExpName());
        data.put("status", exp.getStatus());
        data.put("variants", parseVariants(exp.getVariants()));
        data.put("pricingExpId", exp.getPricingExpId());
        data.put("totalSamples", assignments.size());
        data.put("conversionTotal", observed.values().stream()
                .mapToLong(AbExperiment.VariantStats::getConversions).sum());
        data.put("perVariant", observed.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        en -> {
                            AbExperiment.VariantStats vs = en.getValue();
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("sampleSize", vs.getSampleSize());
                            m.put("conversions", vs.getConversions());
                            m.put("rate", String.format("%.2f%%", vs.getConversionRate() * 100));
                            m.put("rateRaw", vs.getConversionRate());
                            return m;
                        },
                        (a, b) -> a, LinkedHashMap::new)));
        data.put("chiSquare", result.getChiSquare());
        data.put("df", result.getDf());
        data.put("pValue", result.getPValue());
        data.put("significant", result.isSignificant());
        data.put("conclusion", result.getConclusion());
        data.put("bestVariant", result.getBestVariant());
        data.put("updatedAt", exp.getChiUpdatedAt());
        log.info("[AI-007] ab-test stats: id={} exp={} chi2={} df={} p={} conclusion={}",
                id, exp.getExpKey(), result.getChiSquare(), result.getDf(),
                result.getPValue(), result.getConclusion());
        return Result.success(data);
    }

    /**
     * 标记某个用户已转化（ab-test 的一次成功事件）
     *
     * <p>会同时把结果写入 pricing_experiment_result（M14 定价实验联动），
     * 条件：ab_experiment.pricing_exp_id != null。
     */
    @PostMapping("/ab-test/{key}/convert")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> abTestConvert(@PathVariable String key,
                                                       @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (body == null || body.get("userId") == null) {
            return Result.error(BizCode.BAD_REQUEST, "userId 必填");
        }
        Long userId = body.get("userId") instanceof Number n ? n.longValue()
                : Long.parseLong(body.get("userId").toString());
        BigDecimal amount = body.get("amount") == null ? null
                : new BigDecimal(body.get("amount").toString());
        Long orderId = body.get("orderId") == null ? null
                : Long.parseLong(body.get("orderId").toString());
        Long storeId = body.get("storeId") == null ? null
                : Long.parseLong(body.get("storeId").toString());

        // 1. 找分桶记录
        AbAssignment assign = assignmentMapper.selectOne(
                new LambdaQueryWrapper<AbAssignment>()
                        .eq(AbAssignment::getExpKey, key)
                        .eq(AbAssignment::getUserId, userId));
        if (assign == null) return Result.error(BizCode.NOT_FOUND, "未找到分桶记录 userId=" + userId);

        // 2. 标记 CONVERTED
        assign.setConversionStatus("CONVERTED");
        assign.setConvertedAt(LocalDateTime.now());
        if (amount != null) assign.setConversionAmount(amount);
        if (amount != null) assign.setMetricValue(amount);
        assign.setMetricSource("CN_ORDER");
        assignmentMapper.updateById(assign);

        // 3. AI-007：同步到 pricing_experiment_result（M14 联动）
        AbExperiment exp = expMapper.selectOne(
                new LambdaQueryWrapper<AbExperiment>()
                        .eq(AbExperiment::getExpKey, key));
        Map<String, Object> syncResult = new LinkedHashMap<>();
        if (exp != null && exp.getPricingExpId() != null) {
            syncToPricingResult(tenantId, exp, assign, orderId, storeId);
            syncResult.put("pricingSynced", true);
            syncResult.put("pricingExpId", exp.getPricingExpId());
        } else {
            syncResult.put("pricingSynced", false);
            syncResult.put("reason", exp == null ? "exp_key 不存在"
                    : "exp.pricing_exp_id 为空，未启用 M14 联动");
        }

        // 4. 实时重算统计
        Long expId = exp == null ? null : exp.getId();
        if (expId != null) {
            try { recalcInternal(expId); } catch (Exception e) {
                log.warn("[AI-007] recompute after convert failed: {}", e.getMessage());
            }
        }
        syncResult.put("assignment", assign);
        return Result.success("已记录转化", syncResult);
    }

    /**
     * 手动重算显著性（测试/调试用）
     */
    @PostMapping("/ab-test/{id:\\d+}/recompute")
    public Result<Map<String, Object>> abTestRecompute(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        AbExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        if (!tenantId.equals(exp.getTenantId())) {
            return Result.error(BizCode.TENANT_DENIED, "跨租户访问被拒绝");
        }
        Map<String, Object> r = recalcInternal(id);
        return Result.success("已重算", r);
    }

    /**
     * 内部：拉数据 → 卡方 → 落库 → 返回结果
     */
    private Map<String, Object> recalcInternal(Long expId) {
        AbExperiment exp = expMapper.selectById(expId);
        if (exp == null) return null;
        List<AbAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<AbAssignment>()
                        .eq(AbAssignment::getExpKey, exp.getExpKey()));
        Map<String, AbExperiment.VariantStats> observed = AbExperiment.aggregateAssignments(assignments);
        AbExperiment.ChiSquareResult result = exp.computeSignificance(observed);
        exp.setChiSquare(result.getChiSquare());
        exp.setChiDf(result.getDf());
        exp.setChiPValue(result.getPValue());
        exp.setChiUpdatedAt(LocalDateTime.now());
        exp.setConclusion(result.getConclusion());
        exp.setBestVariant(result.getBestVariant());
        expMapper.updateById(exp);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", expId);
        r.put("chiSquare", result.getChiSquare());
        r.put("pValue", result.getPValue());
        r.put("df", result.getDf());
        r.put("conclusion", result.getConclusion());
        r.put("bestVariant", result.getBestVariant());
        r.put("significant", result.isSignificant());
        return r;
    }

    /**
     * AI-007：把 ab_assignment 的转化/分配同步到 pricing_experiment_result
     */
    private void syncToPricingResult(Long tenantId, AbExperiment exp,
                                       AbAssignment assign, Long orderId, Long storeId) {
        // 1. 写一条 RECORDED 行（如果不存在）
        PricingExperimentResult existing = pricingResultMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PricingExperimentResult>()
                        .eq(PricingExperimentResult::getExpId, exp.getPricingExpId())
                        .eq(PricingExperimentResult::getMemberId, assign.getUserId())
                        .eq(PricingExperimentResult::getVariant, assign.getVariantKey())
                        .last("LIMIT 1"));

        if (existing == null) {
            PricingExperimentResult r = new PricingExperimentResult();
            r.setTenantId(tenantId);
            r.setExpId(exp.getPricingExpId());
            r.setExpKey(exp.getExpKey());
            r.setMemberId(assign.getUserId());
            r.setStoreId(storeId);
            r.setVariant(assign.getVariantKey());
            // ab-test 第一个 variant 视为 control（约定：第一个权重最大）
            r.setIsControl(isFirstVariant(exp, assign.getVariantKey()));
            r.setQuoteAt(assign.getAssignedAt());
            r.setConversionStatus(assign.getConversionStatus());
            r.setConvertedAt(assign.getConvertedAt());
            r.setOrderId(orderId);
            r.setConversionAmount(assign.getConversionAmount());
            pricingResultMapper.insert(r);
            log.info("[AI-007] M14 同步 INSERT：expKey={} userId={} variant={} pricingExpId={}",
                    exp.getExpKey(), assign.getUserId(), assign.getVariantKey(), exp.getPricingExpId());
        } else {
            // 已存在：更新转化字段
            existing.setConversionStatus(assign.getConversionStatus());
            existing.setConvertedAt(assign.getConvertedAt());
            if (orderId != null) existing.setOrderId(orderId);
            if (assign.getConversionAmount() != null) {
                existing.setConversionAmount(assign.getConversionAmount());
            }
            pricingResultMapper.updateById(existing);
            log.info("[AI-007] M14 同步 UPDATE：expKey={} userId={} variant={}",
                    exp.getExpKey(), assign.getUserId(), assign.getVariantKey());
        }
    }

    private boolean isFirstVariant(AbExperiment exp, String variantKey) {
        if (variantKey == null || exp.getVariants() == null) return false;
        try {
            // variants 形如 "[{key, name, weight}]"，简化：取首个 '{' 到 '}，再 split(":")[0]
            String s = exp.getVariants();
            int i = s.indexOf("\"key\"");
            if (i < 0) i = s.indexOf("'key'");
            if (i < 0) return false;
            int colon = s.indexOf(':', i);
            int quote = s.indexOf('"', colon);
            int quote2 = s.indexOf('"', quote + 1);
            if (quote < 0 || quote2 < 0) return false;
            String firstKey = s.substring(quote + 1, quote2);
            return firstKey.equalsIgnoreCase(variantKey);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 variants JSON 字符串 → variant 列表（key/name/weight）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseVariants(String variants) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (variants == null || variants.isEmpty()) return out;
        // 极简解析：剔除 [ ] { } ，用 comma + ':' 分键值
        // 与 OLD GrayReleaseService.pickVariant 配合使用（字符串形如 "k1:n1:w1,k2:n2:w2"）
        for (String seg : variants.replace("[", "").replace("]", "").split(",")) {
            String[] parts = seg.replace("{", "").replace("}", "").trim().split(":");
            if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", parts[0].trim());
                if (parts.length > 1) m.put("name", parts[1].trim());
                if (parts.length > 2) {
                    try { m.put("weight", Double.parseDouble(parts[2].trim())); }
                    catch (Exception ignored) {}
                }
                out.add(m);
            }
        }
        return out;
    }

    // ====================================================================
    // 工具
    // ====================================================================

}
