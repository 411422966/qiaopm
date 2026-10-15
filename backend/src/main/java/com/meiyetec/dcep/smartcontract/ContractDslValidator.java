package com.meiyetec.dcep.smartcontract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DCEP-03：智能合约触发条件 DSL 静态校验器（链下）
 *
 * <p>在合约部署之前对 {@code triggerConditions} JSON 做静态校验，避免错误配置
 * 浪费链上资源或导致合约永远无法触发。
 *
 * <h3>支持的触发类型（type 字段）</h3>
 * <ul>
 *   <li>{@code expire}：到期触发（需 deadline）</li>
 *   <li>{@code purchase}：消费触发（需 conditions 数组）</li>
 *   <li>{@code milestone}：里程碑触发（需 conditions 数组 + 阈值）</li>
 *   <li>{@code hybrid}：混合触发（conditions 至少 2 个）</li>
 * </ul>
 *
 * <h3>condition 结构</h3>
 * <pre>
 * {
 *   "trigger":  "purchase" | "expire" | "refund" | "review",
 *   "amount":   "&gt;=100" | "&lt;50" | "==1000" | "100~500",
 *   "category": "beauty" | "hair" | "spa" | ...,
 *   "count":    3,                  // 触发次数（用于 milestone）
 *   "action":   "release" | "refund" | "coupon_grant" | "lock"
 * }
 * </pre>
 *
 * <h3>动作枚举</h3>
 * <ul>
 *   <li>{@code release}：释放托管金额到子钱包</li>
 *   <li>{@code refund}：原路退回托管金额</li>
 *   <li>{@code coupon_grant}：发放优惠券</li>
 *   <li>{@code lock}：续期锁定（延长 expiry）</li>
 * </ul>
 */
@Slf4j
public class ContractDslValidator {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 支持的触发类型 */
    public static final Set<String> SUPPORTED_TRIGGER_TYPES = new HashSet<>(Arrays.asList(
            "expire", "purchase", "milestone", "hybrid"));

    /** 顶层 trigger 字段值（condition 内部） */
    public static final Set<String> SUPPORTED_CONDITION_TRIGGERS = new HashSet<>(Arrays.asList(
            "purchase", "expire", "refund", "review", "attendance", "birthday"));

    /** 支持的动作 */
    public static final Set<String> SUPPORTED_ACTIONS = new HashSet<>(Arrays.asList(
            "release", "refund", "coupon_grant", "lock"));

    /** 支持的商户类目 */
    public static final Set<String> SUPPORTED_MERCHANT_CATEGORIES = new HashSet<>(Arrays.asList(
            "beauty", "hair", "spa", "nail", "eyelash", "massage", "skincare", "wellness"));

    /** amount 表达式：支持 >=N, <=N, >N, <N, ==N, N~M, 纯数字 */
    private static final Pattern AMOUNT_EXPR_PATTERN = Pattern.compile(
            "^(>=|<=|>|<|==)?\\s*\\d+(\\.\\d+)?\\s*$|^\\d+(\\.\\d+)?\\s*~\\s*\\d+(\\.\\d+)?\\s*$");

    /**
     * 校验结果
     */
    public static class ValidationResult {
        public boolean valid = true;
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public ValidationResult() {
        }

        void addError(String error) {
            this.errors.add(error);
            this.valid = false;
        }

        void addWarning(String warning) {
            this.warnings.add(warning);
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            if (valid) {
                sb.append("OK");
            } else {
                sb.append("INVALID (").append(errors.size()).append(" 错误)");
            }
            if (!warnings.isEmpty()) {
                sb.append(", ").append(warnings.size()).append(" 警告");
            }
            return sb.toString();
        }
    }

    /**
     * 校验 triggerConditions JSON
     *
     * @param triggerConditions 触发条件 JSON 字符串
     * @param now               当前时间（注入便于测试）
     */
    public ValidationResult validate(String triggerConditions, LocalDateTime now) {
        ValidationResult result = new ValidationResult();

        if (triggerConditions == null || triggerConditions.trim().isEmpty()) {
            result.addError("triggerConditions 不能为空");
            return result;
        }

        // 1. JSON 解析
        JsonNode root;
        try {
            root = JSON.readTree(triggerConditions);
        } catch (Exception e) {
            result.addError("JSON 解析失败：" + e.getMessage());
            return result;
        }

        if (root.isNull() || !root.isObject()) {
            result.addError("triggerConditions 必须是 JSON 对象");
            return result;
        }

        // 2. type 字段必填 + 合法性
        JsonNode typeNode = root.get("type");
        if (typeNode == null || typeNode.isNull()) {
            result.addError("缺少必填字段 'type'");
        } else {
            String type = typeNode.asText();
            if (!SUPPORTED_TRIGGER_TYPES.contains(type)) {
                result.addError("不支持的 type='" + type + "'，支持值：" + SUPPORTED_TRIGGER_TYPES);
            }
        }

        // 3. deadline 校验（expire 类型必填）
        JsonNode deadlineNode = root.get("deadline");
        if ("expire".equals(typeNode == null ? null : typeNode.asText())) {
            if (deadlineNode == null || deadlineNode.isNull()) {
                result.addError("type='expire' 必须提供 deadline 字段");
            } else {
                String deadline = deadlineNode.asText();
                LocalDate parsedDate;
                try {
                    parsedDate = LocalDate.parse(deadline, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (DateTimeParseException e) {
                    result.addError("deadline 格式错误，应为 yyyy-MM-dd，实际='" + deadline + "'");
                    parsedDate = null;
                }
                if (parsedDate != null && parsedDate.isBefore(now.toLocalDate())) {
                    result.addError("deadline 必须在未来，当前=" + now.toLocalDate() + "，deadline=" + parsedDate);
                }
            }
        }

        // 4. minSpend 校验
        JsonNode minSpendNode = root.get("minSpend");
        if (minSpendNode != null && !minSpendNode.isNull()) {
            if (!minSpendNode.isNumber()) {
                result.addError("minSpend 必须是数字，实际类型=" + minSpendNode.getNodeType());
            } else {
                BigDecimal minSpend = minSpendNode.decimalValue();
                if (minSpend.compareTo(BigDecimal.ZERO) < 0) {
                    result.addError("minSpend 不能为负数，实际=" + minSpend);
                }
            }
        }

        // 5. merchantCategories 校验
        JsonNode catsNode = root.get("merchantCategories");
        if (catsNode != null && !catsNode.isNull()) {
            if (!catsNode.isArray()) {
                result.addError("merchantCategories 必须是数组");
            } else if (catsNode.size() == 0) {
                result.addWarning("merchantCategories 为空，将对所有类目生效");
            } else {
                for (JsonNode cat : catsNode) {
                    String catStr = cat.asText();
                    if (!SUPPORTED_MERCHANT_CATEGORIES.contains(catStr)) {
                        result.addWarning("未知商户类目 '" + catStr + "'，建议从以下选择：" + SUPPORTED_MERCHANT_CATEGORIES);
                    }
                }
            }
        }

        // 6. conditions 数组
        JsonNode conditionsNode = root.get("conditions");
        if (conditionsNode != null && !conditionsNode.isNull()) {
            if (!conditionsNode.isArray()) {
                result.addError("conditions 必须是数组");
            } else {
                int size = conditionsNode.size();
                String typeStr = typeNode == null ? "" : typeNode.asText();
                if ("hybrid".equals(typeStr) && size < 2) {
                    result.addError("type='hybrid' 必须有 ≥2 个 condition，实际=" + size);
                }
                if (size > 10) {
                    result.addWarning("condition 数量过多（" + size + "），建议拆分为多个合约");
                }
                for (int i = 0; i < size; i++) {
                    validateCondition(conditionsNode.get(i), i, result);
                }
            }
        } else if ("purchase".equals(typeNode == null ? null : typeNode.asText())
                || "milestone".equals(typeNode == null ? null : typeNode.asText())) {
            result.addError("type='purchase'/'milestone' 必须提供 conditions 数组");
        }

        // 7. 整体一致性检查
        JsonNode escrowNode = root.get("escrowAmount");
        if (escrowNode != null && escrowNode.isNumber()) {
            BigDecimal escrow = escrowNode.decimalValue();
            if (escrow.compareTo(BigDecimal.ZERO) <= 0) {
                result.addError("escrowAmount 必须 > 0，实际=" + escrow);
            } else if (escrow.compareTo(new BigDecimal("10000000")) > 0) {
                result.addWarning("escrowAmount 超过 1000 万，建议拆分为多个合约");
            }
        }

        return result;
    }

    /**
     * 便捷方法：用当前时间校验
     */
    public ValidationResult validate(String triggerConditions) {
        return validate(triggerConditions, LocalDateTime.now());
    }

    private void validateCondition(JsonNode cond, int index, ValidationResult result) {
        String prefix = "conditions[" + index + "]";

        if (cond == null || !cond.isObject()) {
            result.addError(prefix + " 必须是对象");
            return;
        }

        // trigger
        JsonNode trig = cond.get("trigger");
        if (trig == null || trig.isNull()) {
            result.addError(prefix + ".trigger 必填");
        } else if (!SUPPORTED_CONDITION_TRIGGERS.contains(trig.asText())) {
            result.addError(prefix + ".trigger='" + trig.asText()
                    + "' 不支持，支持值：" + SUPPORTED_CONDITION_TRIGGERS);
        }

        // action
        JsonNode act = cond.get("action");
        if (act == null || act.isNull()) {
            result.addError(prefix + ".action 必填");
        } else if (!SUPPORTED_ACTIONS.contains(act.asText())) {
            result.addError(prefix + ".action='" + act.asText()
                    + "' 不支持，支持值：" + SUPPORTED_ACTIONS);
        }

        // amount 表达式
        JsonNode amountNode = cond.get("amount");
        if (amountNode != null && !amountNode.isNull()) {
            String expr = amountNode.asText();
            if (!AMOUNT_EXPR_PATTERN.matcher(expr).matches()) {
                result.addError(prefix + ".amount 表达式非法='" + expr
                        + "'，支持格式：>=100 / <50 / ==1000 / 100~500 / 纯数字");
            } else {
                // 解析阈值范围并检查合理性
                BigDecimal[] range = parseAmountRange(expr);
                if (range != null) {
                    if (range[0].compareTo(BigDecimal.ZERO) < 0) {
                        result.addError(prefix + ".amount 下界不能 < 0");
                    }
                    if (range[1] != null && range[1].compareTo(range[0]) < 0) {
                        result.addError(prefix + ".amount 区间上界 < 下界");
                    }
                }
            }
        }

        // count（milestone 必填）
        JsonNode countNode = cond.get("count");
        if (countNode != null && !countNode.isNull()) {
            if (!countNode.isInt()) {
                result.addError(prefix + ".count 必须是整数");
            } else if (countNode.asInt() <= 0) {
                result.addError(prefix + ".count 必须 > 0");
            }
        }

        // category
        JsonNode cat = cond.get("category");
        if (cat != null && !cat.isNull()) {
            String catStr = cat.asText();
            if (!SUPPORTED_MERCHANT_CATEGORIES.contains(catStr)) {
                result.addWarning(prefix + ".category='" + catStr + "' 非标准类目");
            }
        }
    }

    /**
     * 解析 amount 表达式为 [min, max]（max 可能为 null 表示无上界）
     */
    private BigDecimal[] parseAmountRange(String expr) {
        try {
            String e = expr.replaceAll("\\s+", "");
            if (e.contains("~")) {
                String[] parts = e.split("~");
                return new BigDecimal[]{new BigDecimal(parts[0]), new BigDecimal(parts[1])};
            }
            if (e.startsWith(">=") || e.startsWith("<=")
                    || e.startsWith(">") || e.startsWith("<")
                    || e.startsWith("==")) {
                String op = e.substring(0, e.startsWith("==") ? 2 : 1);
                BigDecimal v = new BigDecimal(e.substring(op.length()));
                return switch (op) {
                    case ">=" -> new BigDecimal[]{v, null};
                    case ">" -> new BigDecimal[]{v.add(new BigDecimal("0.01")), null};
                    case "<=" -> new BigDecimal[]{BigDecimal.ZERO, v};
                    case "<" -> new BigDecimal[]{BigDecimal.ZERO, v.subtract(new BigDecimal("0.01"))};
                    case "==" -> new BigDecimal[]{v, v};
                    default -> null;
                };
            }
            BigDecimal v = new BigDecimal(e);
            return new BigDecimal[]{v, v};
        } catch (Exception e) {
            return null;
        }
    }
}
