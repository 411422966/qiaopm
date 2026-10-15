package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.AbAssignment;
import com.meiyetec.entity.PricingExperiment;
import com.meiyetec.entity.PricingExperimentResult;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.AbAssignmentMapper;
import com.meiyetec.mapper.PricingExperimentMapper;
import com.meiyetec.mapper.PricingExperimentResultMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * M14 动态定价 A/B 测试引擎
 *
 * <p>核心职责：
 * <ol>
 *   <li>会员分组分配（幂等：同一会员同一实验永远同组）</li>
 *   <li>quote() 时按组计算折扣价格</li>
 *   <li>记录每笔结果（pricing_experiment_result）</li>
 *   <li>统计显著性计算（Z-test / 置信度）</li>
 *   <li>结论生成（WIN_CONTROL / WIN_VARIANT / NO_SIGNIFICANCE）</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingExperimentService {

    private final PricingExperimentMapper expMapper;
    private final PricingExperimentResultMapper resultMapper;
    private final AbAssignmentMapper assignmentMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 分组分配（核心幂等方法）
    // ====================================================================

    /**
     * 获取会员在指定实验中的分组
     *
     * @return A / B / C / D（永不变化，同一会话同组）
     */
    public String assignVariant(Long memberId, PricingExperiment exp) {
        if (memberId == null || exp == null) return "A";

        // 幂等：先查 ab_assignment
        AbAssignment existing = assignmentMapper.selectOne(new LambdaQueryWrapper<AbAssignment>()
                .eq(AbAssignment::getExpKey, exp.getExpKey())
                .eq(AbAssignment::getUserId, memberId)
                .last("LIMIT 1"));

        if (existing != null) return existing.getVariantKey();

        // 首次分配：确定性 hash
        String variant = computeVariant(memberId, exp);

        AbAssignment assign = new AbAssignment();
        assign.setTenantId(exp.getTenantId());
        assign.setExpKey(exp.getExpKey());
        assign.setUserId(memberId);
        assign.setVariantKey(variant);
        assign.setAssignedAt(LocalDateTime.now());
        assignmentMapper.insert(assign);

        return variant;
    }

    /**
     * 确定性分组计算（纯算法，无随机数）
     */
    String computeVariant(Long memberId, PricingExperiment exp) {
        long bucket = Math.abs((memberId * 31L + exp.getExpKey().hashCode()) % 1000L);
        int groupCount = exp.getGroupCount() == null ? 2 : exp.getGroupCount();
        BigDecimal ctrlRatio = exp.getControlTrafficRatio() == null
                ? BigDecimal.valueOf(0.5) : exp.getControlTrafficRatio();
        BigDecimal abRatio = exp.getControlABRatio() == null
                ? BigDecimal.valueOf(0.5) : exp.getControlABRatio();
        BigDecimal cdRatio = exp.getVariantCDRatio() == null
                ? BigDecimal.valueOf(0.5) : exp.getVariantCDRatio();

        int ctrlEnd = ctrlRatio.multiply(BigDecimal.valueOf(1000)).intValue();
        int aEnd = BigDecimal.valueOf(ctrlEnd).multiply(abRatio).intValue();

        if (bucket < aEnd) return "A";
        if (bucket < ctrlEnd) return "B";
        if (groupCount <= 2) return "C";

        int varEnd = 1000;
        int cEnd = ctrlEnd + (varEnd - ctrlEnd) / 2; // 简化：均分

        if (bucket < cEnd) return "C";
        return "D";
    }

    // ====================================================================
    // 2. 折扣计算（quote 核心）
    // ====================================================================

    /**
     * 根据分组计算折扣后价格
     */
    public BigDecimal computeDiscountedPrice(BigDecimal originalPrice, String variant, PricingExperiment exp) {
        if (originalPrice == null) return BigDecimal.ZERO;
        if (variant == null) variant = "A";

        BigDecimal value;
        String mode;
        switch (variant) {
            case "A": value = exp.getGroupAValue(); mode = exp.getGroupAMode(); break;
            case "B": value = exp.getGroupBValue(); mode = exp.getGroupBMode(); break;
            case "C": value = exp.getGroupCValue(); mode = exp.getGroupCMode(); break;
            case "D": value = exp.getGroupDValue(); mode = exp.getGroupDMode(); break;
            default: value = exp.getGroupAValue(); mode = exp.getGroupAMode();
        }
        if (value == null) value = BigDecimal.ZERO;
        if (mode == null || "ORIGINAL".equals(mode)) return originalPrice;

        BigDecimal discounted = switch (mode) {
            case "PERCENT" -> originalPrice.multiply(BigDecimal.valueOf(100).subtract(value))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case "FIXED" -> originalPrice.subtract(value).max(BigDecimal.ZERO);
            default -> originalPrice;
        };
        return discounted.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal computeDiscountAmount(BigDecimal original, BigDecimal discounted) {
        if (original == null || discounted == null) return BigDecimal.ZERO;
        return original.subtract(discounted).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // ====================================================================
    // 3. 记录实验结果
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public void recordResult(Long tenantId, Long expId, String expKey,
                              Long memberId, Long storeId, Long projectId,
                              String variant, boolean isControl,
                              BigDecimal originalPrice, BigDecimal finalPrice,
                              Long ruleId, String ruleCode) {
        PricingExperimentResult r = new PricingExperimentResult();
        r.setTenantId(tenantId);
        r.setExpId(expId);
        r.setExpKey(expKey);
        r.setMemberId(memberId);
        r.setStoreId(storeId);
        r.setProjectId(projectId);
        r.setVariant(variant);
        r.setIsControl(isControl);
        r.setOriginalPrice(originalPrice);
        r.setFinalPrice(finalPrice);
        r.setDiscountAmount(computeDiscountAmount(originalPrice, finalPrice));
        r.setRuleId(ruleId);
        r.setRuleCode(ruleCode);
        r.setQuoteAt(LocalDateTime.now());
        r.setConversionStatus("RECORDED");
        resultMapper.insert(r);

        // 增量更新实验样本数
        String col = isControl ? "control_sample_size" : "variant_sample_size";
        jdbc.update("UPDATE pricing_experiment SET " + col + " = " + col + " + 1 WHERE id = ?", expId);
    }

    // ====================================================================
    // 4. 转化回调（POS 支付成功后调用）
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public void markConverted(Long memberId, Long orderId, BigDecimal orderAmount) {
        List<PricingExperimentResult> pending = resultMapper.selectList(
                new LambdaQueryWrapper<PricingExperimentResult>()
                        .eq(PricingExperimentResult::getMemberId, memberId)
                        .eq(PricingExperimentResult::getConversionStatus, "RECORDED")
                        .orderByDesc(PricingExperimentResult::getCreateTime)
                        .last("LIMIT 10"));
        for (PricingExperimentResult r : pending) {
            r.setConversionStatus("CONVERTED");
            r.setConvertedAt(LocalDateTime.now());
            r.setOrderId(orderId);
            r.setConversionAmount(orderAmount);
            resultMapper.updateById(r);

            String col = Boolean.TRUE.equals(r.getIsControl())
                    ? "control_conversions = control_conversions + 1"
                    : "variant_conversions = variant_conversions + 1";
            jdbc.update("UPDATE pricing_experiment SET " + col + " WHERE id = ?", r.getExpId());
        }
    }

    // ====================================================================
    // 5. 统计显著性计算（Z-test）
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public PricingExperiment computeSignificance(Long expId) {
        PricingExperiment exp = expMapper.selectById(expId);
        if (exp == null) return null;

        int nC = exp.getControlSampleSize() == null ? 0 : exp.getControlSampleSize();
        int nV = exp.getVariantSampleSize() == null ? 0 : exp.getVariantSampleSize();
        int xC = exp.getControlConversions() == null ? 0 : exp.getControlConversions();
        int xV = exp.getVariantConversions() == null ? 0 : exp.getVariantConversions();

        BigDecimal threshold = exp.getSignificanceThreshold() == null
                ? BigDecimal.valueOf(0.95) : exp.getSignificanceThreshold();

        BigDecimal pC = nC == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(xC).divide(BigDecimal.valueOf(nC), 6, RoundingMode.HALF_UP);
        BigDecimal pV = nV == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(xV).divide(BigDecimal.valueOf(nV), 6, RoundingMode.HALF_UP);
        BigDecimal lift = pC.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : pV.subtract(pC).divide(pC, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal zScore = BigDecimal.ZERO;
        BigDecimal confidence = BigDecimal.ZERO;

        if (nC > 0 && nV > 0) {
            BigDecimal pPool = BigDecimal.valueOf(xC + xV)
                    .divide(BigDecimal.valueOf(nC + nV), 6, RoundingMode.HALF_UP);
            if (pPool.compareTo(BigDecimal.ZERO) > 0 && pPool.compareTo(BigDecimal.ONE) < 0) {
                BigDecimal var = pPool.multiply(BigDecimal.ONE.subtract(pPool))
                        .multiply(BigDecimal.valueOf(1.0 / nC + 1.0 / nV).setScale(6, RoundingMode.HALF_UP));
                if (var.compareTo(BigDecimal.ZERO) > 0) {
                    zScore = pV.subtract(pC)
                            .divide(sqrtBig(var), 6, RoundingMode.HALF_UP);
                    confidence = normalCdf(zScore.abs());
                }
            }
        }

        String conclusion;
        int minSize = exp.getMinSampleSize() == null ? 100 : exp.getMinSampleSize();
        if (nC >= minSize && nV >= minSize) {
            if (confidence.compareTo(threshold) >= 0) {
                conclusion = pV.compareTo(pC) > 0 ? "WIN_VARIANT" : "WIN_CONTROL";
            } else {
                conclusion = "NO_SIGNIFICANCE";
            }
        } else {
            conclusion = "PENDING";
        }

        // 写入 DB
        jdbc.update("""
            UPDATE pricing_experiment SET
                control_metric_value = ?,
                variant_metric_value = ?,
                confidence_level = ?,
                lift_percent = ?,
                conclusion = ?
            WHERE id = ?
            """, pC, pV, confidence, lift, conclusion, expId);

        exp.setControlMetricValue(pC);
        exp.setVariantMetricValue(pV);
        exp.setConfidenceLevel(confidence);
        exp.setLiftPercent(lift);
        exp.setConclusion(conclusion);

        log.info("[PricingExp] significance: exp={} nC={} nV={} pC={} pV={} lift={}% conf={} conclusion={}",
                exp.getExpKey(), nC, nV, pC, pV, lift, confidence, conclusion);

        return exp;
    }

    private static BigDecimal sqrtBig(BigDecimal x) {
        if (x.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal guess = x.divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        for (int i = 0; i < 20; i++) {
            BigDecimal next = guess.add(x.divide(guess, 6, RoundingMode.HALF_UP))
                    .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
            if (next.subtract(guess).abs().compareTo(BigDecimal.valueOf(0.000001)) < 0) break;
            guess = next;
        }
        return guess;
    }

    /** 标准正态分布 CDF（A&S 近似） */
    private static BigDecimal normalCdf(BigDecimal z) {
        if (z.compareTo(BigDecimal.ZERO) < 0) z = z.negate();
        double zd = z.doubleValue();
        double t = 1.0 / (1.0 + 0.2316419 * zd);
        double poly = t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))));
        double pdf = Math.exp(-0.5 * zd * zd) / Math.sqrt(2 * Math.PI);
        double cdf = 1.0 - pdf * poly;
        return BigDecimal.valueOf(cdf).setScale(4, RoundingMode.HALF_UP);
    }

    // ====================================================================
    // 6. 定时重新计算所有进行中实验
    // ====================================================================

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    public void recomputeAllRunning() {
        List<PricingExperiment> running = expMapper.selectList(
                new LambdaQueryWrapper<PricingExperiment>()
                        .eq(PricingExperiment::getStatus, "RUNNING"));
        for (PricingExperiment exp : running) {
            try { computeSignificance(exp.getId()); }
            catch (Exception e) { log.warn("[PricingExp] recompute failed: exp={}", exp.getExpKey(), e); }
        }
    }

    // ====================================================================
    // 7. CRUD API
    // ====================================================================

    public Result<Page<PricingExperiment>> page(int pageNum, int pageSize, String status, String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PricingExperiment> qw = new LambdaQueryWrapper<PricingExperiment>()
                .eq(PricingExperiment::getTenantId, tenantId)
                .orderByDesc(PricingExperiment::getId);
        if (status != null && !status.isEmpty()) qw.eq(PricingExperiment::getStatus, status);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(PricingExperiment::getExpKey, keyword)
                    .or().like(PricingExperiment::getExpName, keyword));
        }
        Page<PricingExperiment> page = expMapper.selectPage(new Page<>(pageNum, pageSize), qw);
        return Result.success(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<PricingExperiment> save(PricingExperiment exp) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        exp.setTenantId(tenantId);
        if (exp.getStatus() == null) exp.setStatus("DRAFT");
        if (exp.getControlTrafficRatio() == null) exp.setControlTrafficRatio(BigDecimal.valueOf(0.5));
        if (exp.getControlABRatio() == null) exp.setControlABRatio(BigDecimal.valueOf(0.5));
        if (exp.getVariantCDRatio() == null) exp.setVariantCDRatio(BigDecimal.valueOf(0.5));
        if (exp.getGroupCount() == null) exp.setGroupCount(2);
        if (exp.getSignificanceThreshold() == null) exp.setSignificanceThreshold(BigDecimal.valueOf(0.95));
        if (exp.getMinSampleSize() == null) exp.setMinSampleSize(100);

        if (exp.getId() == null) {
            if (exp.getExpKey() == null || exp.getExpName() == null)
                return Result.error(BizCode.BAD_REQUEST, "expKey / expName 必填");
            Long dup = expMapper.selectCount(new LambdaQueryWrapper<PricingExperiment>()
                    .eq(PricingExperiment::getTenantId, tenantId)
                    .eq(PricingExperiment::getExpKey, exp.getExpKey()));
            if (dup > 0) return Result.error(BizCode.CONFLICT, "expKey 已存在");
            expMapper.insert(exp);
            return Result.success("已创建", exp);
        } else {
            expMapper.updateById(exp);
            return Result.success("已更新", expMapper.selectById(exp.getId()));
        }
    }

    public Result<Void> start(Long id) {
        PricingExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        if (!"DRAFT".equals(exp.getStatus()) && !"PAUSED".equals(exp.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "只有草稿/暂停可启动");
        exp.setStatus("RUNNING");
        exp.setStartDate(LocalDate.now());
        expMapper.updateById(exp);
        return Result.success("已启动", null);
    }

    public Result<Void> pause(Long id) {
        PricingExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        exp.setStatus("PAUSED");
        expMapper.updateById(exp);
        return Result.success("已暂停", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> complete(Long id) {
        PricingExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        exp.setStatus("COMPLETED");
        exp.setEndDate(LocalDate.now());
        expMapper.updateById(exp);
        computeSignificance(id);
        return Result.success("已结束", null);
    }

    public Result<Map<String, Object>> stats(Long id) {
        PricingExperiment exp = expMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", exp.getId());
        data.put("expKey", exp.getExpKey());
        data.put("expName", exp.getExpName());
        data.put("status", exp.getStatus());
        data.put("groupCount", exp.getGroupCount());

        int nC = exp.getControlSampleSize() == null ? 0 : exp.getControlSampleSize();
        int nV = exp.getVariantSampleSize() == null ? 0 : exp.getVariantSampleSize();
        int xC = exp.getControlConversions() == null ? 0 : exp.getControlConversions();
        int xV = exp.getVariantConversions() == null ? 0 : exp.getVariantConversions();
        BigDecimal pC = exp.getControlMetricValue();
        BigDecimal pV = exp.getVariantMetricValue();

        data.put("controlSampleSize", nC);
        data.put("controlConversions", xC);
        data.put("controlRate", pC == null ? "0.00%" : pC.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
        data.put("variantSampleSize", nV);
        data.put("variantConversions", xV);
        data.put("variantRate", pV == null ? "0.00%" : pV.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
        data.put("confidenceLevel", exp.getConfidenceLevel() == null ? "0%" :
                exp.getConfidenceLevel().multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) + "%");
        data.put("liftPercent", exp.getLiftPercent() == null ? "0.00%" :
                exp.getLiftPercent().setScale(2, RoundingMode.HALF_UP) + "%");
        data.put("conclusion", exp.getConclusion() == null ? "PENDING" : exp.getConclusion());
        BigDecimal threshold = exp.getSignificanceThreshold() == null ? BigDecimal.valueOf(0.95) : exp.getSignificanceThreshold();
        boolean significant = exp.getConfidenceLevel() != null
                && exp.getConfidenceLevel().compareTo(threshold) >= 0;
        data.put("significant", significant);
        return Result.success(data);
    }

    /**
     * 按组统计结果分布
     */
    public Result<List<Map<String, Object>>> resultDistribution(Long expId) {
        String sql = """
            SELECT variant, is_control,
                   COUNT(*) AS sample_size,
                   SUM(CASE WHEN conversion_status = 'CONVERTED' THEN 1 ELSE 0 END) AS conversions,
                   AVG(original_price) AS avg_original_price,
                   AVG(final_price) AS avg_final_price,
                   AVG(discount_amount) AS avg_discount
            FROM pricing_experiment_result
            WHERE exp_id = ? AND deleted = 0
            GROUP BY variant, is_control
            ORDER BY variant
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, expId);
        return Result.success(rows);
    }

    /**
     * 找当前适用于指定条件的实验
     */
    public PricingExperiment findActiveExperiment(Long tenantId, Long projectId, Long storeId,
                                                  Long memberLevelId, BigDecimal orderAmount) {
        List<PricingExperiment> running = expMapper.selectList(
                new LambdaQueryWrapper<PricingExperiment>()
                        .eq(PricingExperiment::getTenantId, tenantId)
                        .eq(PricingExperiment::getStatus, "RUNNING")
                        .orderByAsc(PricingExperiment::getId)
                        .last("LIMIT 1"));
        if (running.isEmpty()) return null;
        PricingExperiment exp = running.get(0);

        // 适用条件检查
        if (!matchesScope(exp, projectId, storeId, memberLevelId, orderAmount)) return null;
        return exp;
    }

    private boolean matchesScope(PricingExperiment exp, Long projectId, Long storeId,
                                  Long memberLevelId, BigDecimal amount) {
        if (amount != null && exp.getMinOrderAmount() != null
                && amount.compareTo(exp.getMinOrderAmount()) < 0) return false;
        if (projectId != null && !"ALL".equals(exp.getApplicableProjects())
                && exp.getApplicableProjects() != null) {
            if (!Arrays.asList(exp.getApplicableProjects().split(","))
                    .contains(String.valueOf(projectId))) return false;
        }
        if (storeId != null && !"ALL".equals(exp.getApplicableStores())
                && exp.getApplicableStores() != null) {
            if (!Arrays.asList(exp.getApplicableStores().split(","))
                    .contains(String.valueOf(storeId))) return false;
        }
        return true;
    }
}

