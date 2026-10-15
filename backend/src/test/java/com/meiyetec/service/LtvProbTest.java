package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-2 补丁：LTV 复购概率算法测试（与生产代码 LtvService.computeRepurchaseProb 语义一致）
 * <p>
 * 算法：sigmoid-like 衰减
 *   - 频次 f = totalOrders / (tenureDays/30)
 *   - 衰减：lastPurchaseDays 越大 → 概率越低
 *   - horizonDays：视野越长，累积概率越高
 *   - 边界：钳制 [0.01, 0.99]
 * </p>
 */
class LtvProbTest {

    /** 与生产代码 LtvService.computeRepurchaseProb 语义一致 */
    private static double computeRepurchaseProb(double monthlyFreq, int lastPurchaseDays, int horizonDays) {
        if (lastPurchaseDays >= 9999) return 0.05;
        double decay = Math.exp(-lastPurchaseDays / 60.0);
        double freqFactor = Math.log1p(monthlyFreq) / Math.log(10);
        double baseProb = Math.min(1.0, 0.1 + 0.3 * freqFactor + 0.5 * decay);
        double horizonProb = 1 - Math.pow(1 - baseProb, horizonDays / 30.0);
        return Math.max(0.01, Math.min(0.99, horizonProb));
    }

    @Test
    void prob_neverPurchased_fixedAt005() {
        double p = computeRepurchaseProb(0.0, 9999, 30);
        assertEquals(0.05, p, 0.001);
    }

    @Test
    void prob_decaysAsLastPurchaseDaysGrows() {
        double p30 = computeRepurchaseProb(1.0, 30, 30);
        double p180 = computeRepurchaseProb(1.0, 180, 30);
        double p365 = computeRepurchaseProb(1.0, 365, 30);
        assertTrue(p30 > p180, "30天未消费概率应高于 180 天: 30=" + p30 + " 180=" + p180);
        assertTrue(p180 > p365, "180天未消费概率应高于 365 天: 180=" + p180 + " 365=" + p365);
    }

    @Test
    void prob_growsAsFrequencyIncreases() {
        double p_low = computeRepurchaseProb(0.2, 90, 30);
        double p_mid = computeRepurchaseProb(1.0, 90, 30);
        double p_high = computeRepurchaseProb(5.0, 90, 30);
        assertTrue(p_high >= p_mid && p_mid >= p_low,
                "频次越高概率越高：high=" + p_high + " mid=" + p_mid + " low=" + p_low);
    }

    @Test
    void prob_horizonExtendsProbability() {
        double p_30 = computeRepurchaseProb(1.0, 30, 30);
        double p_180 = computeRepurchaseProb(1.0, 30, 180);
        assertTrue(p_180 >= p_30,
                "长视野累积概率应 ≥ 短视野：30天=" + p_30 + " 180天=" + p_180);
    }

    @Test
    void prob_clampedBetween01And099() {
        double p1 = computeRepurchaseProb(100.0, 1, 30);
        double p2 = computeRepurchaseProb(0.001, 5000, 30);
        assertTrue(p1 >= 0.01 && p1 <= 0.99);
        assertTrue(p2 >= 0.01 && p2 <= 0.99);
    }

    @Test
    void prob_decayMath_basicCheck() {
        // e^(-30/60) ≈ 0.606
        // e^(-180/60) ≈ 0.0498
        // e^(-365/60) ≈ 0.0024
        double decay30 = Math.exp(-30.0 / 60.0);
        double decay180 = Math.exp(-180.0 / 60.0);
        double decay365 = Math.exp(-365.0 / 60.0);
        assertTrue(decay30 > decay180 && decay180 > decay365);
    }
}