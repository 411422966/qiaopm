package com.meiyetec.iot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IOT-006：联动条件表达式评估器测试
 */
@DisplayName("IOT-006 LinkConditionEvaluator")
class LinkConditionEvaluatorTest {

    @Test
    @DisplayName("null 或空条件 → 默认通过")
    void nullOrEmpty_alwaysMatches() {
        assertTrue(LinkConditionEvaluator.matches(null, new HashMap<>()));
        assertTrue(LinkConditionEvaluator.matches("", new HashMap<>()));
        assertTrue(LinkConditionEvaluator.matches("   ", new HashMap<>()));
    }

    @Test
    @DisplayName("metric.temperature > 35 (true)")
    void metric_singleComparison_true() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("temperature", new BigDecimal("36.5"));
        data.put("metrics", metrics);
        assertTrue(LinkConditionEvaluator.matches("metric.temperature > 35", data));
    }

    @Test
    @DisplayName("metric.temperature > 35 (false)")
    void metric_singleComparison_false() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("temperature", new BigDecimal("34"));
        data.put("metrics", metrics);
        assertFalse(LinkConditionEvaluator.matches("metric.temperature > 35", data));
    }

    @Test
    @DisplayName("metric.humidity >= 60 && metric.temperature > 30")
    void compoundAnd() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("humidity", new BigDecimal("65"));
        metrics.put("temperature", new BigDecimal("32"));
        data.put("metrics", metrics);
        assertTrue(LinkConditionEvaluator.matches(
                "metric.humidity >= 60 && metric.temperature > 30", data));
    }

    @Test
    @DisplayName("metric.humidity >= 60 && metric.temperature > 30 (false due to temp)")
    void compoundAnd_false() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("humidity", new BigDecimal("65"));
        metrics.put("temperature", new BigDecimal("25"));
        data.put("metrics", metrics);
        assertFalse(LinkConditionEvaluator.matches(
                "metric.humidity >= 60 && metric.temperature > 30", data));
    }

    @Test
    @DisplayName("metric.battery < 20 || onlineStatus == 'offline' (OR)")
    void compoundOr_true() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("battery", new BigDecimal("85"));
        data.put("metrics", metrics);
        data.put("onlineStatus", "offline");
        assertTrue(LinkConditionEvaluator.matches(
                "metric.battery < 20 || onlineStatus == \"offline\"", data));
    }

    @Test
    @DisplayName("字符串 == 比较")
    void stringComparison() {
        Map<String, Object> data = new HashMap<>();
        data.put("state", "idle");
        assertTrue(LinkConditionEvaluator.matches("state == \"idle\"", data));
        assertFalse(LinkConditionEvaluator.matches("state == \"busy\"", data));
    }

    @Test
    @DisplayName("字符串 != 比较")
    void stringNotEquals() {
        Map<String, Object> data = new HashMap<>();
        data.put("state", "running");
        assertTrue(LinkConditionEvaluator.matches("state != \"idle\"", data));
    }

    @Test
    @DisplayName("四则比较运算符")
    void fourOperators() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("x", new BigDecimal("10"));
        data.put("metrics", metrics);
        assertTrue(LinkConditionEvaluator.matches("metric.x > 9", data));
        assertTrue(LinkConditionEvaluator.matches("metric.x >= 10", data));
        assertTrue(LinkConditionEvaluator.matches("metric.x < 11", data));
        assertTrue(LinkConditionEvaluator.matches("metric.x <= 10", data));
        assertTrue(LinkConditionEvaluator.matches("metric.x == 10", data));
        assertTrue(LinkConditionEvaluator.matches("metric.x != 9", data));
    }

    @Test
    @DisplayName("非数字字符串无法做 > < 比较 → false")
    void nonNumericCompare_false() {
        Map<String, Object> data = new HashMap<>();
        data.put("state", "idle");
        assertFalse(LinkConditionEvaluator.matches("state > \"busy\"", data));
    }

    @Test
    @DisplayName("变量不存在 → 视为 false（fail-safe）")
    void unknownVar_false() {
        Map<String, Object> data = new HashMap<>();
        assertFalse(LinkConditionEvaluator.matches("nonexistent == \"x\"", data));
    }

    @Test
    @DisplayName("非法表达式 → 抛 IllegalArgumentException（被外层 catch 返回 false）")
    void invalidExpression() {
        assertFalse(LinkConditionEvaluator.matches("this is not valid", new HashMap<>()));
    }

    @Test
    @DisplayName("3 个比较：&& 与 || 混合")
    void threeConditionsMixed() {
        Map<String, Object> data = new HashMap<>();
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("a", new BigDecimal("5"));
        metrics.put("b", new BigDecimal("100"));
        metrics.put("c", new BigDecimal("0"));
        data.put("metrics", metrics);
        // (a > 1 && b == 100) || c == 0
        assertTrue(LinkConditionEvaluator.matches(
                "metric.a > 1 && metric.b == 100 || metric.c == 0", data));
    }
}
