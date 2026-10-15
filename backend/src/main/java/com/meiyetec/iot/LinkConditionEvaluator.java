package com.meiyetec.iot;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IOT-006：设备联动条件表达式评估器
 *
 * <p>支持以下 DSL：
 * <pre>
 *   metric.temperature &gt; 35
 *   metric.humidity &gt;= 60 &amp;&amp; metric.temperature &gt; 30
 *   metric.battery &lt; 20 || metric.onlineStatus == "offline"
 *   state == "idle"
 * </pre>
 *
 * <p>运算符：{@code > / >= / < / <= / == / != / && / ||}
 *
 * <p>变量来源：
 * <ul>
 *   <li>{@code metric.xxx}：来自 triggerData.metrics.xxx</li>
 *   <li>{@code state}：来自 triggerData.state</li>
 *   <li>{@code onlineStatus}：来自 triggerData.onlineStatus</li>
 * </ul>
 */
@Slf4j
public class LinkConditionEvaluator {

    /** 单个比较表达式正则：var op value（op 支持 2 字符） */
    private static final Pattern EXPR_PATTERN = Pattern.compile(
            "([a-zA-Z_.][a-zA-Z0-9_.]*)\\s*(>=|<=|==|!=|>|<)\\s*(\"[^\"]*\"|\\S+)");

    /** 顶层 AND/OR 切分（保留分隔符） */
    private static final Pattern TOP_LEVEL_AND_OR = Pattern.compile("\\s*(\\&\\&|\\|\\|)\\s*");

    public static boolean matches(String condition, Map<String, Object> triggerData) {
        if (condition == null || condition.trim().isEmpty()) return true;
        try {
            return evalBoolean(condition.trim(), triggerData);
        } catch (Exception e) {
            log.warn("[IOT-006] 条件表达式评估失败：expr='{}' err={}", condition, e.getMessage());
            return false;
        }
    }

    private static boolean evalBoolean(String expr, Map<String, Object> ctx) {
        // 顶层布尔（用 &&/|| 切分）
        List<String> tokens = splitTopLevel(expr);
        if (tokens.size() == 1) {
            // 单个比较
            return evalSingle(tokens.get(0).trim(), ctx);
        }
        boolean result = false;
        String pendingOp = null;
        for (String tok : tokens) {
            tok = tok.trim();
            if (tok.equals("&&") || tok.equals("||")) {
                pendingOp = tok;
                continue;
            }
            boolean val = evalSingle(tok, ctx);
            if (pendingOp == null) {
                result = val;
            } else if (pendingOp.equals("&&")) {
                result = result && val;
            } else {
                result = result || val;
                pendingOp = null;
            }
            pendingOp = pendingOp == null ? null : (tok.equals("&&") || tok.equals("||") ? pendingOp : null);
        }
        return result;
    }

    private static List<String> splitTopLevel(String expr) {
        // 用 Matcher 按顺序切分
        java.util.List<String> tokens = new java.util.ArrayList<>();
        int last = 0;
        Matcher m = TOP_LEVEL_AND_OR.matcher(expr);
        while (m.find()) {
            tokens.add(expr.substring(last, m.start()));
            tokens.add(m.group());
            last = m.end();
        }
        tokens.add(expr.substring(last));
        return tokens;
    }

    private static boolean evalSingle(String expr, Map<String, Object> ctx) {
        Matcher m = EXPR_PATTERN.matcher(expr);
        if (!m.matches()) {
            throw new IllegalArgumentException("条件表达式非法：" + expr);
        }
        String var = m.group(1);
        String op = m.group(2);
        String rawVal = m.group(3);
        // 去掉字符串值的引号
        Object literalVal = rawVal.startsWith("\"") && rawVal.endsWith("\"")
                ? rawVal.substring(1, rawVal.length() - 1)
                : parseNumber(rawVal);
        Object actual = resolve(var, ctx);
        return compare(actual, op, literalVal);
    }

    private static Object resolve(String var, Map<String, Object> ctx) {
        if (ctx == null) return null;
        // metric.xxx
        if (var.startsWith("metric.")) {
            Object metrics = ctx.get("metrics");
            if (metrics instanceof Map<?, ?> map) {
                return map.get(var.substring("metric.".length()));
            }
            return null;
        }
        return ctx.get(var);
    }

    private static Object parseNumber(String s) {
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return s;
        }
    }

    private static boolean compare(Object actual, String op, Object expected) {
        if (actual == null) {
            return op.equals("==") && expected == null
                    || op.equals("!=") && expected != null;
        }
        // BigDecimal 比较
        if (expected instanceof BigDecimal) {
            BigDecimal a = toBigDecimal(actual);
            if (a == null) return false;
            return switch (op) {
                case ">" -> a.compareTo((BigDecimal) expected) > 0;
                case ">=" -> a.compareTo((BigDecimal) expected) >= 0;
                case "<" -> a.compareTo((BigDecimal) expected) < 0;
                case "<=" -> a.compareTo((BigDecimal) expected) <= 0;
                case "==" -> a.compareTo((BigDecimal) expected) == 0;
                case "!=" -> a.compareTo((BigDecimal) expected) != 0;
                default -> false;
            };
        }
        // 字符串 / 等值比较
        String a = String.valueOf(actual);
        String e = String.valueOf(expected);
        return switch (op) {
            case "==" -> a.equals(e);
            case "!=" -> !a.equals(e);
            case ">", ">=", "<", "<=" -> false; // 不支持跨类型数值比较
            default -> false;
        };
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
