package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.engine.CommissionEngine;
import com.meiyetec.entity.CommissionCondition;
import com.meiyetec.entity.CommissionRule;
import com.meiyetec.entity.CommissionSimulation;
import com.meiyetec.entity.CommissionTier;
import com.meiyetec.mapper.CommissionConditionMapper;
import com.meiyetec.mapper.CommissionRuleMapper;
import com.meiyetec.mapper.CommissionSimulationMapper;
import com.meiyetec.mapper.CommissionTierMapper;
import com.meiyetec.service.CommissionSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommissionSimulationServiceImpl extends ServiceImpl<CommissionSimulationMapper, CommissionSimulation>
        implements CommissionSimulationService {

    private final CommissionRuleMapper commissionRuleMapper;
    private final CommissionTierMapper commissionTierMapper;
    private final CommissionConditionMapper commissionConditionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> simulate(Map<String, Object> params) {
        CommissionEngine.SimulationContext context = new CommissionEngine.SimulationContext();
        context.employeeId = getLong(params, "employeeId");
        context.employeeLevelId = getLong(params, "employeeLevelId");
        context.serviceId = getLong(params, "serviceId");
        context.serviceCategoryId = getLong(params, "serviceCategoryId");
        Object amountObj = params.get("serviceAmount");
        context.serviceAmount = amountObj == null ? BigDecimal.ZERO : new BigDecimal(amountObj.toString());
        context.customerType = (String) params.get("customerType");
        context.memberLevel = (String) params.get("memberLevel");
        context.storeId = getLong(params, "storeId");
        context.businessTime = params.get("simulationDate") != null
                ? LocalDateTime.parse(params.get("simulationDate").toString() + "T12:00:00")
                : LocalDateTime.now();

        LambdaQueryWrapper<CommissionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommissionRule::getStoreId, context.storeId);
        wrapper.eq(CommissionRule::getStatus, 1);
        wrapper.le(CommissionRule::getEffectiveDate, context.businessTime.toLocalDate());
        wrapper.and(w -> w.isNull(CommissionRule::getExpiryDate)
                .or().ge(CommissionRule::getExpiryDate, context.businessTime.toLocalDate()));
        wrapper.orderByDesc(CommissionRule::getPriority);
        List<CommissionRule> candidates = commissionRuleMapper.selectList(wrapper);

        List<Map<String, Object>> allMatches = new ArrayList<>();
        CommissionRule selectedRule = null;
        List<CommissionCondition> selectedConditions = null;
        List<CommissionTier> selectedTiers = null;
        for (CommissionRule rule : candidates) {
            if (!CommissionEngine.isRuleEffective(rule, context.businessTime.toLocalDate())) continue;
            List<CommissionCondition> conditions = commissionConditionMapper.findByRuleId(rule.getId());
            if (!CommissionEngine.matchConditions(conditions, context)) continue;
            Map<String, Object> matchInfo = new HashMap<>();
            matchInfo.put("rule", rule);
            matchInfo.put("conditions", conditions);
            allMatches.add(matchInfo);
            if (selectedRule == null) {
                selectedRule = rule;
                selectedConditions = conditions;
                selectedTiers = commissionTierMapper.findByRuleId(rule.getId());
            }
        }
        if (selectedRule == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("matched", false);
            empty.put("message", "未匹配到任何提成规则");
            empty.put("candidatesCount", candidates.size());
            return empty;
        }

        CommissionEngine.CalculationResult result = CommissionEngine.calculate(
                selectedRule, selectedTiers, context.serviceAmount);

        CommissionSimulation sim = new CommissionSimulation();
        sim.setEmployeeId(context.employeeId);
        sim.setEmployeeLevelId(context.employeeLevelId);
        sim.setServiceId(context.serviceId);
        sim.setServiceAmount(context.serviceAmount);
        sim.setCustomerType(context.customerType);
        sim.setSimulationDate(context.businessTime.toLocalDate().toString());
        sim.setMatchedRuleId(selectedRule.getId());
        sim.setMatchedRuleName(selectedRule.getRuleName());
        sim.setCalculationMethod(result.calculationMethod);
        sim.setRate(result.rate);
        sim.setFixedAmount(result.fixedAmount);
        sim.setCommissionAmount(result.commissionAmount);
        sim.setCalculationDetail(result.detail);
        save(sim);

        Map<String, Object> response = new HashMap<>();
        response.put("matched", true);
        response.put("rule", selectedRule);
        response.put("conditions", selectedConditions);
        response.put("tiers", selectedTiers);
        response.put("calculationMethod", result.calculationMethod);
        response.put("rate", result.rate);
        response.put("fixedAmount", result.fixedAmount);
        response.put("commissionAmount", result.commissionAmount);
        response.put("detail", result.detail);
        response.put("totalMatches", allMatches.size());
        return response;
    }

    @Override
    public List<CommissionSimulation> getRecentByEmployee(Long employeeId) {
        return baseMapper.findRecentByEmployee(employeeId);
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof String) {
            String str = (String) value;
            return str.isEmpty() ? null : Long.parseLong(str);
        }
        return null;
    }
}

