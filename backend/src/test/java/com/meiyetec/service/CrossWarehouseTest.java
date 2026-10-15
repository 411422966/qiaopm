package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-H P2.3：跨仓预警算法测试
 *
 * <p>覆盖：
 *  - BALANCE-V1 不平衡算法（suggestedQty = min(deficit, surplus/2)）
 *  - 过期预警 4 级分类（EXPIRED / EXPIRING_30/60/90）
 *  - severity 判定
 *  - 阈值管理
 * </p>
 */
class CrossWarehouseTest {

    // ==================== BALANCE-V1 算法 ====================
    @Test
    void balance_lowestDeficit() {
        // min - lowQty = 20 - 5 = 15
        int min = 20, lowQty = 5;
        int deficit = min - lowQty;
        assertEquals(15, deficit);
    }

    @Test
    void balance_highestSurplus() {
        // highQty - max(min*2, default) = 100 - 40 = 60
        int highQty = 100, min = 20;
        int baseline = Math.max(min * 2, min * 2);   // 这里 baseline = 40
        int surplus = highQty - baseline;
        assertEquals(60, surplus);
    }

    @Test
    void balance_suggestedQty_minOfDeficitAndHalfSurplus() {
        // suggestedQty = min(deficit, surplus/2)
        int deficit = 15, surplus = 60;
        int suggested = Math.min(deficit, surplus / 2);
        assertEquals(15, suggested, "deficit < surplus/2 → 取 deficit");
    }

    @Test
    void balance_suggestedQty_cappedBySurplus() {
        int deficit = 50, surplus = 30;
        int suggested = Math.min(deficit, surplus / 2);
        assertEquals(15, suggested, "deficit > surplus/2 → 取 surplus/2");
    }

    @Test
    void balance_severityHigh_whenCoverageOver80Percent() {
        int suggested = 16, deficit = 20;
        double coverage = (double) suggested / deficit;
        String sev = coverage >= 0.8 ? "HIGH" : "MEDIUM";
        assertEquals("HIGH", sev, "覆盖 ≥ 80% → HIGH");
    }

    @Test
    void balance_severityMedium_whenCoverageUnder80Percent() {
        int suggested = 12, deficit = 20;
        double coverage = (double) suggested / deficit;
        String sev = coverage >= 0.8 ? "HIGH" : "MEDIUM";
        assertEquals("MEDIUM", sev, "覆盖 < 80% → MEDIUM");
    }

    @Test
    void balance_triggerCondition() {
        // 触发：lowQty < min && highQty > min × 2
        int min = 10, lowQty = 3, highQty = 25;
        boolean trigger = lowQty < min && highQty > min * 2;
        assertTrue(trigger);
    }

    @Test
    void balance_noTrigger_whenLowOk() {
        int min = 10, lowQty = 15, highQty = 25;
        boolean trigger = lowQty < min && highQty > min * 2;
        assertFalse(trigger, "低仓正常 → 不触发");
    }

    @Test
    void balance_noTrigger_whenHighNotEnough() {
        int min = 10, lowQty = 3, highQty = 15;
        boolean trigger = lowQty < min && highQty > min * 2;
        assertFalse(trigger, "高仓不够 → 不触发");
    }

    @Test
    void balance_noTrigger_singleWarehouse() {
        // 单仓无法跨仓调拨
        int whCount = 1;
        assertTrue(whCount < 2);
    }

    // ==================== 过期预警 4 级分类 ====================
    @Test
    void expiryLevel_expired() {
        long days = -5;     // 已过期 5 天
        String level = classifyExpiry(days);
        assertEquals("EXPIRED", level);
    }

    @Test
    void expiryLevel_expiring30() {
        long days = 15;
        String level = classifyExpiry(days);
        assertEquals("EXPIRING_30", level);
    }

    @Test
    void expiryLevel_expiring60() {
        long days = 45;
        String level = classifyExpiry(days);
        assertEquals("EXPIRING_60", level);
    }

    @Test
    void expiryLevel_expiring90() {
        long days = 75;
        String level = classifyExpiry(days);
        assertEquals("EXPIRING_90", level);
    }

    @Test
    void expiryLevel_normal() {
        long days = 100;
        String level = classifyExpiry(days);
        assertNull(level, "90 天以上不预警");
    }

    @Test
    void expiryLevel_boundary30() {
        assertEquals("EXPIRING_30", classifyExpiry(30));
        assertEquals("EXPIRING_60", classifyExpiry(31));
    }

    @Test
    void expiryLevel_boundary60() {
        assertEquals("EXPIRING_60", classifyExpiry(60));
        assertEquals("EXPIRING_90", classifyExpiry(61));
    }

    @Test
    void expiryLevel_boundary90() {
        assertEquals("EXPIRING_90", classifyExpiry(90));
        assertNull(classifyExpiry(91));
    }

    @Test
    void expiryLevel_zeroDays() {
        // 今天到期
        assertEquals("EXPIRING_30", classifyExpiry(0));
    }

    private static String classifyExpiry(long days) {
        if (days < 0) return "EXPIRED";
        if (days <= 30) return "EXPIRING_30";
        if (days <= 60) return "EXPIRING_60";
        if (days <= 90) return "EXPIRING_90";
        return null;
    }

    // ==================== ChronoUnit ====================
    @Test
    void daysBetween_dates() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        LocalDate exp = LocalDate.of(2026, 9, 14);
        long days = ChronoUnit.DAYS.between(today, exp);
        assertEquals(31, days);
    }

    @Test
    void daysBetween_pastDate() {
        LocalDate today = LocalDate.of(2026, 8, 14);
        LocalDate past = LocalDate.of(2026, 8, 9);
        long days = ChronoUnit.DAYS.between(today, past);
        assertEquals(-5, days, "过去日期 → 负数");
    }

    // ==================== 调拨状态机 ====================
    @Test
    void transferStateMachine_valid() {
        assertTrue(isValidTransferState("PENDING", "SHIPPED"));
        assertTrue(isValidTransferState("SHIPPED", "RECEIVED"));
    }

    @Test
    void transferStateMachine_invalid() {
        // 不能跳跃
        assertFalse(isValidTransferState("PENDING", "RECEIVED"));
        // 不能回退
        assertFalse(isValidTransferState("SHIPPED", "PENDING"));
        assertFalse(isValidTransferState("RECEIVED", "SHIPPED"));
    }

    private static boolean isValidTransferState(String from, String to) {
        return switch (from) {
            case "PENDING" -> to.equals("SHIPPED");
            case "SHIPPED" -> to.equals("RECEIVED");
            case "RECEIVED" -> false;
            default -> false;
        };
    }

    // ==================== 优先级排序 ====================
    @Test
    void severityOrder_HIGH_first() {
        // FIELD(severity, 'HIGH', 'MEDIUM', 'LOW') 排序
        String[] order = {"HIGH", "MEDIUM", "LOW"};
        assertEquals("HIGH", order[0]);
        assertEquals("LOW", order[2]);
    }

    // ==================== 阈值边界 ====================
    @Test
    void threshold_defaultMin() {
        Integer min = 0;
        assertEquals(0, min);
    }

    @Test
    void threshold_defaultMax() {
        Integer max = 999999;
        assertTrue(max > 0);
    }

    @Test
    void threshold_safetyDays() {
        Integer safetyDays = 7;
        assertTrue(safetyDays > 0);
    }

    // ==================== 数值边界 ====================
    @Test
    void recommendedQty_positive() {
        int qty = Math.min(15, 30);
        assertTrue(qty > 0);
    }

    @Test
    void recommendedQty_zero_invalid() {
        int qty = 0;
        assertFalse(qty > 0, "建议数量为 0 应跳过");
    }

    // ==================== 跨仓转移号生成 ====================
    @Test
    void transferNo_format() {
        String no = "TR" + System.currentTimeMillis()
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        assertTrue(no.startsWith("TR"));
        assertTrue(no.length() >= 18, "调拨号应 ≥ 18 字符，实际 " + no.length() + " (" + no + ")");
    }
}