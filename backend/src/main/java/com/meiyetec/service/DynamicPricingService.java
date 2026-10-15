package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.MktDynamicPricingRule;
import com.meiyetec.entity.MktPricingLog;
import com.meiyetec.entity.PricingExperiment;
import com.meiyetec.entity.TechnicianLevel;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.MktDynamicPricingRuleMapper;
import com.meiyetec.mapper.MktPricingLogMapper;
import com.meiyetec.mapper.TechnicianLevelMapper;
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
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P3 · T-P04：动态定价（按技师/时段/项目/会员等级）
 *  - 收银台可见优惠计算
 *  - 5 类规则（TECHNICIAN/TIME_SLOT/PROJECT/MEMBER_LEVEL/STORE）
 *  - 3 种计算模式（PERCENT/FIXED/MULTIPLIER）
 *  - 规则优先级 + 叠加应用
 */
@Slf4j
@RestController
@RequestMapping("/mkt/pricing")
@RequiredArgsConstructor
public class DynamicPricingService {

    private final MktDynamicPricingRuleMapper ruleMapper;
    private final MktPricingLogMapper logMapper;
    private final com.meiyetec.service.PricingExperimentService experimentService;
    private final com.meiyetec.mapper.TechnicianLevelMapper technicianLevelMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // 定价规则 CRUD
    // ====================================================================

    @PostMapping("/rule")
    public Result<MktDynamicPricingRule> createRule(@RequestBody MktDynamicPricingRule r) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (r.getRuleCode() == null || r.getRuleName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称 必填");
        if (r.getRuleType() == null || r.getTargetType() == null)
            return Result.error(BizCode.BAD_REQUEST, "ruleType / targetType 必填");
        if (r.getEnabled() == null) r.setEnabled(1);
        if (r.getPriority() == null) r.setPriority(0);
        r.setTenantId(tenantId);
        ruleMapper.insert(r);
        return Result.success("已创建", r);
    }

    @PutMapping("/rule/{id}")
    public Result<MktDynamicPricingRule> updateRule(@PathVariable Long id, @RequestBody MktDynamicPricingRule r) {
        MktDynamicPricingRule old = ruleMapper.selectById(id);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "规则不存在");
        r.setId(id);
        r.setTenantId(old.getTenantId());
        ruleMapper.updateById(r);
        return Result.success("已更新", ruleMapper.selectById(id));
    }

    @DeleteMapping("/rule/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        ruleMapper.deleteById(id);
        return Result.success("已删除", null);
    }

    @GetMapping("/rule/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> rulePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer enabled) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<MktDynamicPricingRule> q = new LambdaQueryWrapper<>();
        q.eq(MktDynamicPricingRule::getTenantId, tenantId);
        if (ruleType != null && !ruleType.isEmpty()) q.eq(MktDynamicPricingRule::getRuleType, ruleType);
        if (targetType != null && !targetType.isEmpty()) q.eq(MktDynamicPricingRule::getTargetType, targetType);
        if (enabled != null) q.eq(MktDynamicPricingRule::getEnabled, enabled);
        q.orderByDesc(MktDynamicPricingRule::getPriority).orderByDesc(MktDynamicPricingRule::getId);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<MktDynamicPricingRule> page =
                ruleMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize), q);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> result =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MktDynamicPricingRule r : page.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("ruleCode", r.getRuleCode());
            m.put("ruleName", r.getRuleName());
            m.put("ruleType", r.getRuleType());
            m.put("targetType", r.getTargetType());
            m.put("targetCode", r.getTargetCode());
            m.put("targetName", r.getTargetName());
            m.put("pricingMode", r.getPricingMode());
            m.put("pricingValue", r.getPricingValue());
            m.put("minAmount", r.getMinAmount());
            m.put("maxAmount", r.getMaxAmount());
            m.put("timeStart", r.getTimeStart());
            m.put("timeEnd", r.getTimeEnd());
            m.put("dayOfWeek", r.getDayOfWeek());
            m.put("priority", r.getPriority());
            m.put("enabled", r.getEnabled());
            m.put("validFrom", r.getValidFrom());
            m.put("validTo", r.getValidTo());
            m.put("description", r.getDescription());
            m.put("createTime", r.getCreateTime());
            rows.add(m);
        }
        result.setRecords(rows);
        result.setTotal(page.getTotal());
        return Result.success(result);
    }

    // ====================================================================
    // M14 技师等级溢价规则 CRUD
    // ====================================================================

    /**
     * M14 · 技师等级溢价规则（规则类型 = TECHNICIAN_LEVEL）
     *
     * <p>与普通规则的区别：
     * <ul>
     *   <li>targetType = TECHNICIAN_LEVEL</li>
     *   <li>targetCode = 技师等级编码（L1/L2/L3/CHIEF 等）</li>
     *   <li>pricingMode = PREMIUM（溢价百分比）或 PREMIUM_FIXED（固定加价）</li>
     *   <li>targetId = TechnicianLevel.id（关联等级主键）</li>
     *   <li>pricingValue = 溢价百分比（如 15 = 在基础价上 +15%）</li>
     * </ul>
     *
     * <p>叠加顺序（从 quote 链路）：
     * <ol>
     *   <li>项目基价（basePrice）</li>
     *   <li>+ 技师等级溢价（TECHNICIAN_LEVEL PREMIUM = basePrice × pricingValue%）</li>
     *   <li>+ 时段折扣（TIME_SLOT 等，普通规则）</li>
     *   <li>+ A/B 实验折扣</li>
     * </ol>
     */
    @PostMapping("/rule/technician-level")
    public Result<MktDynamicPricingRule> createTechnicianLevelRule(@RequestBody MktDynamicPricingRule r) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (r.getRuleCode() == null || r.getRuleName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (r.getTargetId() == null)
            return Result.error(BizCode.BAD_REQUEST, "targetId（技师等级ID）必填");
        if (r.getPricingMode() == null) r.setPricingMode("PREMIUM");
        if (r.getPricingValue() == null)
            return Result.error(BizCode.BAD_REQUEST, "pricingValue（溢价比例）必填");
        if (r.getEnabled() == null) r.setEnabled(1);
        if (r.getPriority() == null) r.setPriority(0);
        r.setTenantId(tenantId);
        r.setRuleType("TECHNICIAN_LEVEL");
        r.setTargetType("TECHNICIAN_LEVEL");
        // 查找等级名称
        TechnicianLevel tl = technicianLevelMapper.selectById(r.getTargetId());
        if (tl != null) {
            r.setTargetCode(tl.getLevelCode());
            r.setTargetName(tl.getLevelName());
        }
        ruleMapper.insert(r);
        return Result.success("技师等级溢价规则已创建", r);
    }

    /**
     * 查询技师等级列表（含当前溢价规则命中状态）
     */
    @GetMapping("/rule/technician-level/levels")
    public Result<List<Map<String, Object>>> listTechnicianLevels() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<TechnicianLevel> levels = technicianLevelMapper.findActiveLevels();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (TechnicianLevel tl : levels) {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", tl.getId());
            m.put("levelCode", tl.getLevelCode());
            m.put("levelName", tl.getLevelName());
            m.put("levelColor", tl.getLevelColor());
            m.put("discountRate", tl.getDiscountRate());
            m.put("commissionBonus", tl.getCommissionBonus());
            // 查该等级的溢价规则
            MktDynamicPricingRule rule = ruleMapper.selectOne(
                    new LambdaQueryWrapper<MktDynamicPricingRule>()
                            .eq(MktDynamicPricingRule::getTenantId, tenantId)
                            .eq(MktDynamicPricingRule::getRuleType, "TECHNICIAN_LEVEL")
                            .eq(MktDynamicPricingRule::getTargetId, tl.getId())
                            .eq(MktDynamicPricingRule::getEnabled, 1)
                            .last("LIMIT 1"));
            m.put("premiumRuleId", rule == null ? null : rule.getId());
            m.put("premiumMode", rule == null ? null : rule.getPricingMode());
            m.put("premiumValue", rule == null ? null : rule.getPricingValue());
            m.put("premiumRuleName", rule == null ? null : rule.getRuleName());
            out.add(m);
        }
        return Result.success(out);
    }

    /**
     * 批量设置技师等级溢价规则（方便前端一键配置）
     */
    @PostMapping("/rule/technician-level/batch")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchSetTechnicianPremium(@RequestBody List<Map<String, Object>> batch) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        int created = 0, updated = 0;
        for (Map<String, Object> item : batch) {
            Long levelId = item.get("levelId") == null ? null : ((Number) item.get("levelId")).longValue();
            String premiumMode = (String) item.get("premiumMode");
            Number premiumValue = item.get("premiumValue") == null ? null : (Number) item.get("premiumValue");
            if (levelId == null) continue;
            TechnicianLevel tl = technicianLevelMapper.selectById(levelId);
            if (tl == null) continue;

            MktDynamicPricingRule rule = ruleMapper.selectOne(
                    new LambdaQueryWrapper<MktDynamicPricingRule>()
                            .eq(MktDynamicPricingRule::getTenantId, tenantId)
                            .eq(MktDynamicPricingRule::getRuleType, "TECHNICIAN_LEVEL")
                            .eq(MktDynamicPricingRule::getTargetId, levelId)
                            .last("LIMIT 1"));
            if (rule == null) {
                rule = new MktDynamicPricingRule();
                rule.setTenantId(tenantId);
                rule.setRuleCode("TECH-LVL-" + tl.getLevelCode());
                rule.setRuleName(tl.getLevelName() + "服务溢价");
                rule.setRuleType("TECHNICIAN_LEVEL");
                rule.setTargetType("TECHNICIAN_LEVEL");
                rule.setTargetId(levelId);
                rule.setTargetCode(tl.getLevelCode());
                rule.setTargetName(tl.getLevelName());
                rule.setPricingMode(premiumMode == null ? "PREMIUM" : premiumMode);
                rule.setPricingValue(premiumValue == null ? BigDecimal.ZERO : new BigDecimal(premiumValue.toString()));
                rule.setEnabled(1);
                rule.setPriority(10);
                ruleMapper.insert(rule);
                created++;
            } else {
                if (premiumMode != null) rule.setPricingMode(premiumMode);
                if (premiumValue != null) rule.setPricingValue(new BigDecimal(premiumValue.toString()));
                rule.setEnabled(1);
                ruleMapper.updateById(rule);
                updated++;
            }
        }
        return Result.success("设置完成", Map.of("created", created, "updated", updated));
    }

    // ====================================================================
    // 收银台：实时定价计算
    // ====================================================================

    /**
     * 收银台报价（POST 简化版）
     * 输入：项目ID + 技师ID + 会员ID + 时间 + 门店
     * 输出：原价、命中规则、最终价格、节省金额
     */
    @PostMapping("/quote")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> quote(@RequestBody Map<String, Object> body) {
        Long projectId = body.get("projectId") == null ? null : ((Number) body.get("projectId")).longValue();
        Long technicianId = body.get("technicianId") == null ? null : ((Number) body.get("technicianId")).longValue();
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
        LocalDateTime pricingTime = body.get("pricingTime") == null ? LocalDateTime.now()
                : LocalDateTime.parse((String) body.get("pricingTime"));
        if (projectId == null) return Result.error(BizCode.BAD_REQUEST, "projectId 必填");

        // 拉项目基价
        Map<String, Object> project = jdbc.queryForList(
            "SELECT id, project_name, base_price, level_id FROM pos_project WHERE id = ? AND deleted = 0", projectId)
            .stream().findFirst().orElse(null);
        if (project == null) return Result.error(BizCode.NOT_FOUND, "项目不存在");
        BigDecimal baseAmount = new BigDecimal(project.get("base_price").toString());

        // 拉技师 + 会员
        Map<String, Object> tech = technicianId == null ? null : jdbc.queryForList(
            "SELECT id, name, skill_level FROM sys_user WHERE id = ?", technicianId).stream().findFirst().orElse(null);
        Map<String, Object> member = memberId == null ? null : jdbc.queryForList(
            "SELECT id, name, level_id FROM crm_member WHERE id = ? AND deleted = 0", memberId).stream().findFirst().orElse(null);

        // 拉所有启用规则
        List<MktDynamicPricingRule> allRules = ruleMapper.selectList(new LambdaQueryWrapper<MktDynamicPricingRule>()
                .eq(MktDynamicPricingRule::getTenantId, TenantContextHolder.get().getTenantId())
                .eq(MktDynamicPricingRule::getEnabled, 1)
                .orderByDesc(MktDynamicPricingRule::getPriority));

        BigDecimal amount = baseAmount;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<Map<String, Object>> hitRules = new ArrayList<>();
        String hitRuleCode = null;
        Long hitRuleId = null;

        for (MktDynamicPricingRule rule : allRules) {
            if (!validateRule(rule, pricingTime, baseAmount, tech, member, project)) continue;
            BigDecimal delta = BigDecimal.ZERO;
            switch (rule.getPricingMode()) {
                case "PERCENT":
                    delta = amount.multiply(rule.getPricingValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    break;
                case "FIXED":
                    delta = rule.getPricingValue();
                    break;
                case "MULTIPLIER":
                    BigDecimal mult = amount.multiply(rule.getPricingValue());
                    delta = mult.subtract(amount);
                    break;
                case "PREMIUM":
                    // M14 技师等级溢价：在 baseAmount（基价）上叠加溢价百分比
                    // 技师服务 = 基价 + (基价 × premium%)
                    delta = baseAmount.multiply(rule.getPricingValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    break;
                case "PREMIUM_FIXED":
                    // 技师等级固定加价
                    delta = rule.getPricingValue();
                    break;
            }
            amount = amount.add(delta);
            totalDiscount = totalDiscount.add(delta);
            Map<String, Object> hit = new HashMap<>();
            hit.put("ruleCode", rule.getRuleCode());
            hit.put("ruleName", rule.getRuleName());
            hit.put("pricingMode", rule.getPricingMode());
            hit.put("pricingValue", rule.getPricingValue());
            hit.put("delta", delta);
            hit.put("description", rule.getDescription());
            hitRules.add(hit);
            if (hitRuleId == null || rule.getPriority() > (hitRuleId == null ? 0 : 1)) {
                hitRuleId = rule.getId();
                hitRuleCode = rule.getRuleCode();
            }
        }
        // 保底不低于 0
        if (amount.compareTo(BigDecimal.ZERO) < 0) amount = BigDecimal.ZERO;

        // ====================================================================
        // M14 定价 A/B 实验 — 在规则折扣后再叠加
        // ====================================================================
        PricingExperiment activeExp = null;
        String abVariant = null;
        BigDecimal abOriginal = amount;
        BigDecimal abDiscount = BigDecimal.ZERO;

        try {
            activeExp = experimentService.findActiveExperiment(
                    TenantContextHolder.get().getTenantId(),
                    projectId, storeId,
                    member == null ? null : ((Number) member.get("level_id")).longValue(),
                    amount);
            if (activeExp != null) {
                abVariant = experimentService.assignVariant(memberId, activeExp);
                BigDecimal expPrice = experimentService.computeDiscountedPrice(amount, abVariant, activeExp);
                abDiscount = experimentService.computeDiscountAmount(amount, expPrice);
                amount = expPrice;
                // 记录实验结果
                experimentService.recordResult(
                        TenantContextHolder.get().getTenantId(),
                        activeExp.getId(), activeExp.getExpKey(),
                        memberId, storeId, projectId,
                        abVariant, "A".equals(abVariant) || "B".equals(abVariant),
                        abOriginal, amount,
                        hitRuleId, hitRuleCode);
            }
        } catch (Exception e) {
            log.warn("[DynamicPricing] A/B 实验处理异常（不影响主线）: {}", e.getMessage());
        }

        // 落库
        MktPricingLog log = new MktPricingLog();
        log.setTenantId(TenantContextHolder.get().getTenantId());
        log.setProjectId(projectId);
        log.setProjectName((String) project.get("project_name"));
        log.setTechnicianId(technicianId);
        log.setTechnicianName(tech == null ? null : (String) tech.get("name"));
        log.setMemberId(memberId);
        log.setMemberLevelId(member == null ? null : ((Number) member.get("level_id")).longValue());
        log.setStoreId(storeId);
        log.setPricingTime(pricingTime);
        log.setBaseAmount(baseAmount);
        log.setHitRuleId(hitRuleId);
        log.setHitRuleCode(hitRuleCode);
        log.setFinalAmount(amount);
        log.setDiscountAmount(totalDiscount.add(abDiscount));
        log.setRulesJson(hitRules.toString());
        log.setCheckoutVisible(1);
        logMapper.insert(log);

        Map<String, Object> data = new HashMap<>();
        data.put("baseAmount", baseAmount);
        data.put("finalAmount", amount);
        data.put("discountAmount", totalDiscount);
        data.put("abDiscountAmount", abDiscount);
        data.put("abVariant", abVariant);
        data.put("abExpKey", activeExp == null ? null : activeExp.getExpKey());
        data.put("hitRules", hitRules);
        data.put("hitRuleCode", hitRuleCode);
        data.put("pricingLogId", log.getId());
        return Result.success(data);
    }

    private boolean validateRule(MktDynamicPricingRule rule, LocalDateTime time,
                                  BigDecimal baseAmount,
                                  Map<String, Object> tech, Map<String, Object> member,
                                  Map<String, Object> project) {
        // 时段
        if (rule.getTimeStart() != null && rule.getTimeEnd() != null) {
            Time now = Time.valueOf(time.toLocalTime());
            if (now.before(rule.getTimeStart()) || now.after(rule.getTimeEnd())) return false;
        }
        // 周几
        if (rule.getDayOfWeek() != null && !rule.getDayOfWeek().isEmpty()) {
            int dow = time.getDayOfWeek().getValue(); // ISO 周一=1
            if (!rule.getDayOfWeek().contains(String.valueOf(dow))) return false;
        }
        // 有效期
        LocalDate today = time.toLocalDate();
        if (rule.getValidFrom() != null && today.isBefore(rule.getValidFrom())) return false;
        if (rule.getValidTo() != null && today.isAfter(rule.getValidTo())) return false;
        // 金额区间
        if (rule.getMinAmount() != null && baseAmount.compareTo(rule.getMinAmount()) < 0) return false;
        if (rule.getMaxAmount() != null && baseAmount.compareTo(rule.getMaxAmount()) > 0) return false;
        // target
        switch (rule.getTargetType()) {
            case "PROJECT":
                if (!"ALL".equals(rule.getTargetCode()) && project != null) {
                    if (!String.valueOf(project.get("id")).equals(rule.getTargetCode())) return false;
                }
                break;
            case "TECHNICIAN":
                if (tech == null) return false;
                if (!"ALL".equals(rule.getTargetCode()) && !"JUNIOR".equals(rule.getTargetCode()) && !"CHIEF".equals(rule.getTargetCode())) {
                    if (!String.valueOf(tech.get("id")).equals(rule.getTargetCode())) return false;
                }
                break;
            case "TECHNICIAN_LEVEL":
                // TECHNICIAN_LEVEL 规则：按技师关联的等级 ID 匹配
                // 目标等级 ID 存在 targetId 中，targetCode 存等级编码（L1/L2/CHIEF）
                if (tech == null) return false;
                String techLevel = (String) tech.get("employee_level");
                if (techLevel == null) return false;
                // 支持精确匹配 levelId 或等级编码
                if (rule.getTargetCode() != null && !rule.getTargetCode().isEmpty()) {
                    if (!rule.getTargetCode().equals(techLevel)
                            && !String.valueOf(rule.getTargetId()).equals(techLevel)) return false;
                }
                break;
            case "MEMBER_LEVEL":
                if (member == null) return false;
                // 简化：用 level_id 比较
                break;
        }
        return true;
    }
}