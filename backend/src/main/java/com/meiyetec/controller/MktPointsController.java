package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MktPointsRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MktPointsRuleMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.PointService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分规则 + 手动发分 controller（/mkt/points/*）
 *
 * 与 PointController（/market/point/*）的关系：
 *  - PointController 走「旧积分」（market_point_record）
 *  - MktPointsController 走「新积分」（mkt_points_rule + mkt_points_log）
 *  - 前端 views/mkt/points.vue 实际用 /mkt/points/* 这一组
 */
@RestController
@RequestMapping("/mkt/points")
@RequiredArgsConstructor
@SysOpLog(module = "积分规则")
public class MktPointsController {

    private final MktPointsRuleMapper ruleMapper;
    private final PointService pointService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/rule/list")
    @RequirePermission("mkt:points")
    public Result<List<MktPointsRule>> list() {
        Long t = tid();
        LambdaQueryWrapper<MktPointsRule> w = new LambdaQueryWrapper<>();
        w.eq(MktPointsRule::getTenantId, t).orderByAsc(MktPointsRule::getPriority);
        return Result.success(ruleMapper.selectList(w));
    }

    @PostMapping("/rule")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:points")
    public Result<MktPointsRule> create(@RequestBody MktPointsRule rule) {
        Long t = tid();
        if (rule.getRuleName() == null || rule.getRuleName().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "ruleName 必填");
        }
        rule.setId(null);
        rule.setTenantId(t);
        if (rule.getEnabled() == null) rule.setEnabled(1);
        if (rule.getConsumeRate() == null) rule.setConsumeRate(new BigDecimal("1.00"));
        if (rule.getRechargeMultiplier() == null) rule.setRechargeMultiplier(new BigDecimal("1.00"));
        if (rule.getPromotionMultiplier() == null) rule.setPromotionMultiplier(new BigDecimal("1.00"));
        if (rule.getPointsToCash() == null) rule.setPointsToCash(new BigDecimal("0.01"));
        if (rule.getMaxUsePercent() == null) rule.setMaxUsePercent(new BigDecimal("30.00"));
        ruleMapper.insert(rule);
        return Result.success("创建成功", rule);
    }

    @PutMapping("/rule/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:points")
    public Result<MktPointsRule> update(@PathVariable Long id, @RequestBody MktPointsRule rule) {
        Long t = tid();
        MktPointsRule old = ruleMapper.selectById(id);
        if (old == null || !t.equals(old.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "规则不存在");
        }
        if (rule.getRuleName() != null) old.setRuleName(rule.getRuleName());
        if (rule.getConsumeRate() != null) old.setConsumeRate(rule.getConsumeRate());
        if (rule.getRechargeMultiplier() != null) old.setRechargeMultiplier(rule.getRechargeMultiplier());
        if (rule.getPromotionMultiplier() != null) old.setPromotionMultiplier(rule.getPromotionMultiplier());
        if (rule.getPointsToCash() != null) old.setPointsToCash(rule.getPointsToCash());
        if (rule.getMaxUsePercent() != null) old.setMaxUsePercent(rule.getMaxUsePercent());
        if (rule.getEnabled() != null) old.setEnabled(rule.getEnabled());
        if (rule.getPriority() != null) old.setPriority(rule.getPriority());
        if (rule.getEffectiveDate() != null) old.setEffectiveDate(rule.getEffectiveDate());
        if (rule.getExpiryDate() != null) old.setExpiryDate(rule.getExpiryDate());
        ruleMapper.updateById(old);
        return Result.success("更新成功", old);
    }

    /**
     * 手动发分：根据 sourceType 计算应得积分
     * - CONSUME: amount × consumeRate
     * - RECHARGE: amount × rechargeMultiplier
     * - MANUAL: 直接发 amount 分
     */
    @PostMapping("/award")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("mkt:points")
    public Result<Map<String, Object>> award(@RequestBody Map<String, Object> body) {
        Long t = tid();
        if (body.get("memberId") == null) return Result.error(BizCode.BAD_REQUEST, "memberId 必填");
        if (body.get("amount") == null) return Result.error(BizCode.BAD_REQUEST, "amount 必填");
        Long memberId = ((Number) body.get("memberId")).longValue();
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String sourceType = body.get("sourceType") == null ? "MANUAL" : body.get("sourceType").toString();
        String remark = body.get("remark") == null ? "" : body.get("remark").toString();

        // 找生效规则
        LambdaQueryWrapper<MktPointsRule> w = new LambdaQueryWrapper<>();
        w.eq(MktPointsRule::getTenantId, t).eq(MktPointsRule::getEnabled, 1).last("LIMIT 1");
        MktPointsRule rule = ruleMapper.selectOne(w);

        int points;
        if ("MANUAL".equalsIgnoreCase(sourceType)) {
            points = amount.intValue();
            remark = remark.isEmpty() ? "手动调整" : remark;
        } else if ("CONSUME".equalsIgnoreCase(sourceType)) {
            BigDecimal rate = rule == null || rule.getConsumeRate() == null ? new BigDecimal("1.00") : rule.getConsumeRate();
            points = amount.multiply(rate).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            remark = remark.isEmpty() ? "消费得积分" : remark;
        } else if ("RECHARGE".equalsIgnoreCase(sourceType)) {
            BigDecimal rate = rule == null || rule.getRechargeMultiplier() == null ? new BigDecimal("1.00") : rule.getRechargeMultiplier();
            points = amount.multiply(rate).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            remark = remark.isEmpty() ? "储值得积分" : remark;
        } else {
            return Result.error(BizCode.BAD_REQUEST, "sourceType 必须为 MANUAL/CONSUME/RECHARGE");
        }

        if (points <= 0) {
            return Result.error(BizCode.BAD_REQUEST, "计算后积分为 0，请检查金额与规则");
        }

        String sourceKey = sourceType.toLowerCase();
        Long id = pointService.addPointsReturningId(memberId, sourceKey, points, null, remark);

        Map<String, Object> out = new HashMap<>();
        out.put("recordId", id);
        out.put("points", points);
        return Result.success("已发放", out);
    }
}