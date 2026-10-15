package com.meiyetec.engine;

import com.meiyetec.entity.CommissionCondition;
import com.meiyetec.entity.CommissionRule;
import com.meiyetec.entity.CommissionTier;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 多维提成规则计算引擎
 */
@Slf4j
public class CommissionEngine {

    public static class SimulationContext {
        public Long employeeId;
        public String employeeName;
        public Long employeeLevelId;
        public Long serviceId;
        public Long serviceCategoryId;
        public BigDecimal serviceAmount;
        public String customerType;
        public String memberLevel;
        public LocalDateTime businessTime;
        public Long storeId;
    }

    public static class CalculationResult {
        public CommissionRule matchedRule;
        public List<CommissionCondition> matchedConditions;
        public String calculationMethod;
        public BigDecimal rate;
        public BigDecimal fixedAmount;
        public BigDecimal commissionAmount;
        public String detail;

        public CalculationResult() {}

        public boolean isMatched() {
            return matchedRule != null;
        }
    }

    public static CommissionRule selectRule(List<CommissionRule> candidates, SimulationContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .filter(r -> isRuleEffective(r, context.businessTime == null ? LocalDate.now() : context.businessTime.toLocalDate()))
                .max((a, b) -> {
                    int scoreA = score(a, context);
                    int scoreB = score(b, context);
                    return Integer.compare(scoreA, scoreB);
                })
                .orElse(null);
    }

    private static int score(CommissionRule rule, SimulationContext context) {
        int score = 0;
        if (rule.getTargetId() != null) {
            score += 1000;
            if (rule.getTargetId().equals(context.serviceId)) {
                score += 500;
            }
        }
        if (rule.getPriority() != null) {
            score += rule.getPriority() * 10;
        }
        return score;
    }

    public static boolean isRuleEffective(CommissionRule rule, LocalDate date) {
        if (rule.getStatus() == null || rule.getStatus() != 1) return false;
        if (rule.getIsActive() != null && rule.getIsActive() != 1) return false;
        if (rule.getEffectiveDate() != null && date.isBefore(rule.getEffectiveDate())) return false;
        if (rule.getExpiryDate() != null && date.isAfter(rule.getExpiryDate())) return false;
        return true;
    }

    public static boolean matchConditions(List<CommissionCondition> conditions, SimulationContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (CommissionCondition condition : conditions) {
            if (!matchCondition(condition, context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchCondition(CommissionCondition condition, SimulationContext context) {
        try {
            switch (condition.getConditionType()) {
                case "service":
                    return matchEq(condition, context.serviceId);
                case "service_category":
                    return matchEq(condition, context.serviceCategoryId);
                case "employee_level":
                    return matchEq(condition, context.employeeLevelId);
                case "customer_type":
                    return matchEq(condition, context.customerType);
                case "member_level":
                    return matchEq(condition, context.memberLevel);
                case "min_amount":
                    return matchGte(condition, context.serviceAmount);
                case "max_amount":
                    return matchLte(condition, context.serviceAmount);
                case "time_slot":
                    return matchTimeSlot(condition, context.businessTime);
                case "weekday":
                    return matchWeekday(condition, context.businessTime);
                default:
                    return true;
            }
        } catch (Exception e) {
            log.error("条件匹配失败: {}", e.getMessage());
            return false;
        }
    }

    private static boolean matchEq(CommissionCondition c, Object actual) {
        if (actual == null) return false;
        String value = c.getConditionValue();
        if (value == null) return false;
        return String.valueOf(actual).equals(value);
    }

    private static boolean matchGte(CommissionCondition c, BigDecimal amount) {
        if (amount == null) return false;
        try {
            BigDecimal threshold = new BigDecimal(c.getConditionValue());
            return amount.compareTo(threshold) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matchLte(CommissionCondition c, BigDecimal amount) {
        if (amount == null) return false;
        try {
            BigDecimal ceiling = new BigDecimal(c.getConditionValue());
            return amount.compareTo(ceiling) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matchTimeSlot(CommissionCondition c, LocalDateTime dateTime) {
        if (dateTime == null) return false;
        try {
            String[] parts = c.getConditionValue().split("-");
            if (parts.length != 2) return false;
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            int hour = dateTime.getHour();
            return hour >= start && hour < end;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean matchWeekday(CommissionCondition c, LocalDateTime dateTime) {
        if (dateTime == null) return false;
        try {
            int weekday = dateTime.getDayOfWeek().getValue();
            return String.valueOf(weekday).equals(c.getConditionValue());
        } catch (Exception e) {
            return false;
        }
    }

    public static CalculationResult calculate(CommissionRule rule, List<CommissionTier> tiers, BigDecimal baseAmount) {
        CalculationResult result = new CalculationResult();
        result.matchedRule = rule;
        if (rule == null || baseAmount == null) {
            return result;
        }
        String method = rule.getCalculationMethod();
        if ("TIER".equals(method) && tiers != null && !tiers.isEmpty()) {
            return calculateByTiers(rule, tiers, baseAmount, result);
        } else if ("RATE".equals(method)) {
            BigDecimal rate = rule.getRate() == null ? BigDecimal.ZERO : rule.getRate();
            BigDecimal amount = baseAmount.multiply(rate);
            result.calculationMethod = "RATE";
            result.rate = rate;
            result.commissionAmount = amount.setScale(2, RoundingMode.HALF_UP);
            result.detail = String.format("服务金额 %.2f × 提成比例 %.2f%% = %.2f",
                    baseAmount, rate.multiply(new BigDecimal("100")), result.commissionAmount);
            return result;
        } else if ("FIXED".equals(method)) {
            BigDecimal fixed = rule.getFixedAmount() == null ? BigDecimal.ZERO : rule.getFixedAmount();
            result.calculationMethod = "FIXED";
            result.fixedAmount = fixed;
            result.commissionAmount = fixed.setScale(2, RoundingMode.HALF_UP);
            result.detail = String.format("固定提成 %.2f", result.commissionAmount);
            return result;
        }
        return result;
    }

    private static CalculationResult calculateByTiers(CommissionRule rule, List<CommissionTier> tiers,
                                                     BigDecimal baseAmount, CalculationResult result) {
        result.calculationMethod = "TIER";
        BigDecimal totalAmount = BigDecimal.ZERO;
        StringBuilder detailBuilder = new StringBuilder("阶梯式计算：");
        for (CommissionTier tier : tiers) {
            BigDecimal tierStart = tier.getTierStart() == null ? BigDecimal.ZERO : tier.getTierStart();
            BigDecimal tierEnd = tier.getTierEnd();
            BigDecimal tierMax = tierEnd == null ? baseAmount : tierEnd.min(baseAmount);
            if (baseAmount.compareTo(tierStart) <= 0) {
                continue;
            }
            BigDecimal tierAmount = tierMax.subtract(tierStart);
            if (tierAmount.signum() <= 0) continue;
            BigDecimal tierCommission;
            if ("FIXED".equals(tier.getCalculationMethod())) {
                tierCommission = tier.getRate() == null ? BigDecimal.ZERO : tier.getRate();
                detailBuilder.append(String.format("[%s-%s]固定 %.2f, ",
                        tierStart, tierEnd == null ? "∞" : tierEnd, tierCommission));
            } else {
                BigDecimal rate = tier.getRate() == null ? BigDecimal.ZERO : tier.getRate();
                tierCommission = tierAmount.multiply(rate);
                detailBuilder.append(String.format("[%s-%s]%s×%.2f%%=%.2f, ",
                        tierStart, tierEnd == null ? "∞" : tierEnd, tierAmount, rate.multiply(new BigDecimal("100")), tierCommission));
            }
            totalAmount = totalAmount.add(tierCommission);
            if (tierEnd == null || baseAmount.compareTo(tierEnd) <= 0) {
                break;
            }
        }
        result.commissionAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        result.detail = detailBuilder.toString() + String.format("合计 %.2f", result.commissionAmount);
        return result;
    }
}

