package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R3.0-D 补丁：WmsService.reviewAnalysis 的纯算法测试
 * <p>
 * 实际 SQL 聚合需要 @SpringBootTest 集成测试覆盖。
 * 这里只测试 NPS / round1 数学逻辑（与生产代码一致）。
 * </p>
 */
class WmsReviewAnalysisTest {

    /** 与 WmsService.round1 语义一致（1 位小数四舍五入） */
    private static double round1(Object v) {
        if (v == null) return 0;
        return BigDecimal.valueOf(((Number) v).doubleValue())
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Test
    void round1_oneDecimal() {
        assertEquals(3.5, round1(3.456), 0.001);
        assertEquals(0.0, round1(0), 0.001);
        assertEquals(5.0, round1(4.99), 0.001);
        assertEquals(0.0, round1((Object) null), 0.001);
    }

    @Test
    void npsFormula_arithmetic() {
        // NPS = (positive - negative) / total * 100
        // 这里直接验证数学逻辑（不是通过 Service）
        long positive = 80, negative = 10, total = 100;
        double nps = Math.round(((positive - negative) * 1000.0 / total)) / 10.0;
        assertEquals(70.0, nps, 0.001);

        // 全部负评
        positive = 0; negative = 100; total = 100;
        nps = Math.round(((positive - negative) * 1000.0 / total)) / 10.0;
        assertEquals(-100.0, nps, 0.001);

        // total = 0 边界
        total = 0;
        nps = total == 0 ? 0 : Math.round(((positive - negative) * 1000.0 / total)) / 10.0;
        assertEquals(0.0, nps, 0.001);
    }
}