package com.meiyetec.service;

import com.meiyetec.controller.CrossStoreController.CrossStoreCheckResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3.0-B 补丁：跨店检查结果状态机测试（纯单元，不依赖 Spring / DB）
 * <p>
 * 验证 {@link CrossStoreCheckResult} 的静态构造方法语义。
 * 实际拦截逻辑（matchRule / 规则匹配）需要 Spring 集成测试覆盖。
 * </p>
 */
class CrossStoreCheckResultTest {

    @Test
    void ok_isNotCross_andAllowed() {
        CrossStoreCheckResult r = CrossStoreCheckResult.ok();
        assertFalse(r.isCross, "ok() 不视为跨店");
        assertTrue(r.allowed, "ok() 必须允许");
        assertEquals(0, r.crossFee.compareTo(BigDecimal.ZERO), "ok() 手续费为 0");
        assertNull(r.ruleId);
        assertNull(r.message);
    }

    @Test
    void reject_isNotCross_andNotAllowed() {
        CrossStoreCheckResult r = CrossStoreCheckResult.reject("套餐禁止跨店");
        assertFalse(r.isCross);
        assertFalse(r.allowed);
        assertEquals("套餐禁止跨店", r.message);
    }

    @Test
    void cross_isCross_andAllowed_withFee() {
        CrossStoreCheckResult r = CrossStoreCheckResult.cross(
                42L, new BigDecimal("5.00"));
        assertTrue(r.isCross);
        assertTrue(r.allowed);
        assertEquals(0, r.crossFee.compareTo(new BigDecimal("5.00")));
        assertEquals(Long.valueOf(42L), r.ruleId);
    }

    @Test
    void cross_zeroFee_isAllowed() {
        // 跨店但手续费=0 也是合法状态（无 fee_type=NONE 时）
        CrossStoreCheckResult r = CrossStoreCheckResult.cross(1L, BigDecimal.ZERO);
        assertTrue(r.isCross);
        assertTrue(r.allowed);
        assertEquals(0, r.crossFee.compareTo(BigDecimal.ZERO));
    }
}
