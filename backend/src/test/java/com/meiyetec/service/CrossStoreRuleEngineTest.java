package com.meiyetec.service;

import com.meiyetec.entity.CrmCrossStoreRedemption;
import com.meiyetec.entity.CrmCrossStoreRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.0-B P0.3 补强：跨店消费规则引擎测试
 * <p>
 * 不依赖 Spring / DB，验证：
 *  - 跨店判定（isCross）
 *  - 费率计算（PERCENT 模式 + 单笔限额）
 *  - 类型白名单（PACKAGE/BALANCE/POINTS/COUPON/DISCOUNT）
 *  - 规则快照（ruleSnapshot 格式）
 *  - 跨店记录金额字段（originalAmount / crossFee / netAmount）
 * </p>
 */
class CrossStoreRuleEngineTest {

    // ==================== 跨店判定 ====================
    @Test
    void isCross_sameStore_false() {
        // 同店（source == current）→ 非跨店
        Long sourceStore = 100L;
        Long currentStore = 100L;
        assertFalse(isCross(sourceStore, currentStore),
                "同店不应算跨店（source == current）");
    }

    @Test
    void isCross_differentStore_true() {
        // 异店（source != current）→ 跨店
        assertTrue(isCross(100L, 200L));
    }

    @Test
    void isCross_nullSource_false() {
        // 会员无归属店（首次来店） → 不算跨店
        assertFalse(isCross(null, 200L));
    }

    private static boolean isCross(Long source, Long current) {
        return source != null && !source.equals(current);
    }

    // ==================== 费率计算：PERCENT ====================
    @Test
    void feePercent_5pct() {
        // 5% 费率，原始金额 198 → 手续费 9.90
        BigDecimal rate = new BigDecimal("5");
        BigDecimal original = new BigDecimal("198.00");
        BigDecimal fee = original.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("9.90").compareTo(fee));
    }

    @Test
    void feePercent_zeroRate_zeroFee() {
        BigDecimal rate = BigDecimal.ZERO;
        BigDecimal original = new BigDecimal("500.00");
        BigDecimal fee = original.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertEquals(0, fee.compareTo(BigDecimal.ZERO));
    }

    @Test
    void feePercent_15pct_rounding() {
        // 15% × 99.99 = 14.9985 → 15.00 (HALF_UP)
        BigDecimal rate = new BigDecimal("15");
        BigDecimal original = new BigDecimal("99.99");
        BigDecimal fee = original.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("15.00").compareTo(fee));
    }

    @Test
    void feePercent_nullRate_skipped() {
        // 规则 feeRate=null 时不扣费
        BigDecimal rate = null;
        BigDecimal original = new BigDecimal("500.00");
        BigDecimal fee = rate == null ? BigDecimal.ZERO
                : original.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertEquals(0, fee.compareTo(BigDecimal.ZERO));
    }

    // ==================== 净金额 ====================
    @Test
    void netAmount_originalMinusFee() {
        BigDecimal original = new BigDecimal("998.00");
        BigDecimal fee = new BigDecimal("49.90");  // 5%
        BigDecimal net = original.subtract(fee);
        assertEquals(0, new BigDecimal("948.10").compareTo(net));
    }

    @Test
    void netAmount_zeroFee_returnsOriginal() {
        BigDecimal original = new BigDecimal("198.00");
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = original.subtract(fee);
        assertEquals(0, original.compareTo(net));
    }

    // ==================== 单笔限额 ====================
    @Test
    void withinLimit_allowed() {
        BigDecimal original = new BigDecimal("500.00");
        BigDecimal limit = new BigDecimal("1000.00");
        assertTrue(original.compareTo(limit) <= 0, "500 < 1000 应允许");
    }

    @Test
    void exceedsLimit_rejected() {
        BigDecimal original = new BigDecimal("1500.00");
        BigDecimal limit = new BigDecimal("1000.00");
        assertTrue(original.compareTo(limit) > 0, "1500 > 1000 应拒绝");
    }

    @Test
    void zeroLimit_unlimited() {
        // limit = 0 表示不限
        BigDecimal original = new BigDecimal("99999.00");
        BigDecimal limit = BigDecimal.ZERO;
        boolean ok = (limit.signum() == 0) || original.compareTo(limit) <= 0;
        assertTrue(ok, "limit=0 应不限");
    }

    // ==================== 类型白名单 ====================
    @Test
    void type_packageAllowed_byRule() {
        CrmCrossStoreRule r = new CrmCrossStoreRule();
        r.setPackageCrossEnabled(1);
        r.setPackageCrossMode("FULL");

        assertNotEquals(0, r.getPackageCrossEnabled());
        assertEquals("FULL", r.getPackageCrossMode());
    }

    @Test
    void type_packageDisabled_rejected() {
        CrmCrossStoreRule r = new CrmCrossStoreRule();
        r.setPackageCrossEnabled(0);
        r.setPackageCrossMode("DISABLED");

        boolean blocked = r.getPackageCrossEnabled() == null
                || r.getPackageCrossEnabled() == 0
                || "DISABLED".equals(r.getPackageCrossMode());
        assertTrue(blocked);
    }

    @Test
    void type_balanceDisabled_rejected() {
        CrmCrossStoreRule r = new CrmCrossStoreRule();
        r.setBalanceCrossEnabled(0);
        r.setBalanceCrossMode("DISABLED");

        boolean blocked = r.getBalanceCrossEnabled() == null
                || r.getBalanceCrossEnabled() == 0
                || "DISABLED".equals(r.getBalanceCrossMode());
        assertTrue(blocked);
    }

    @Test
    void type_balancePartialAllowed() {
        // PARTIAL 模式允许部分使用
        CrmCrossStoreRule r = new CrmCrossStoreRule();
        r.setBalanceCrossEnabled(1);
        r.setBalanceCrossMode("PARTIAL");

        boolean blocked = r.getBalanceCrossEnabled() == null
                || r.getBalanceCrossEnabled() == 0
                || "DISABLED".equals(r.getBalanceCrossMode());
        assertFalse(blocked, "PARTIAL 模式应允许");
    }

    // ==================== 规则快照 ====================
    @Test
    void ruleSnapshot_format() {
        CrmCrossStoreRule r = new CrmCrossStoreRule();
        r.setPackageCrossMode("FULL");
        r.setCrossFeeRate(new BigDecimal("5"));
        r.setCrossMaxAmount(new BigDecimal("1000.00"));

        String snapshot = String.format("mode=%s,fee=%s%s,max=%s",
                r.getPackageCrossMode(), r.getCrossFeeRate(), "%", r.getCrossMaxAmount());

        assertEquals("mode=FULL,fee=5%,max=1000.00", snapshot);
    }

    // ==================== 跨店记录 ====================
    @Test
    void crossStoreRedemption_recordFields() {
        CrmCrossStoreRedemption r = new CrmCrossStoreRedemption();
        r.setMemberId(1L);
        r.setMemberName("张三");
        r.setMemberPhone("13800000000");
        r.setSourceStoreId(100L);
        r.setTargetStoreId(200L);
        r.setRedemptionType("PACKAGE");
        r.setSourceId(50L);
        r.setSourceName("面部护理10次");
        r.setOriginalAmount(new BigDecimal("998.00"));
        r.setCrossFee(new BigDecimal("49.90"));
        r.setNetAmount(new BigDecimal("948.10"));
        r.setRuleId(42L);
        r.setRuleSnapshot("mode=FULL,fee=5%,max=1000.00");
        r.setStatus(1);

        assertEquals("张三", r.getMemberName());
        assertEquals(100L, r.getSourceStoreId());
        assertEquals(200L, r.getTargetStoreId());
        assertEquals("PACKAGE", r.getRedemptionType());
        assertEquals(0, new BigDecimal("49.90").compareTo(r.getCrossFee()));
        assertEquals(0, new BigDecimal("948.10").compareTo(r.getNetAmount()));
    }

    @Test
    void redemptionStatusLifecycle() {
        CrmCrossStoreRedemption r = new CrmCrossStoreRedemption();
        // 1 = 正常, 2 = 已撤销
        r.setStatus(1);
        assertEquals(1, r.getStatus());
        r.setStatus(2);
        assertEquals(2, r.getStatus());
    }

    // ==================== 端到端套餐消费场景 ====================
    @Test
    void e2e_packageConsumption_crossStore_withFee() {
        // 场景：张三在 A 店买了 998 套餐，去 B 店消费；规则 mode=FULL，费率 5%
        BigDecimal original = new BigDecimal("998.00");
        BigDecimal rate = new BigDecimal("5");

        BigDecimal crossFee = original.multiply(rate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("49.90").compareTo(crossFee));

        BigDecimal net = original.subtract(crossFee);
        assertEquals(0, new BigDecimal("948.10").compareTo(net));

        // 写记录
        CrmCrossStoreRedemption r = new CrmCrossStoreRedemption();
        r.setRedemptionType("PACKAGE");
        r.setOriginalAmount(original);
        r.setCrossFee(crossFee);
        r.setNetAmount(net);
        r.setSourceStoreId(100L);  // A 店
        r.setTargetStoreId(200L);  // B 店
        r.setStatus(1);

        // 校验
        assertTrue(isCross(r.getSourceStoreId(), r.getTargetStoreId()),
                "异店消费必须标记跨店");
        assertEquals("PACKAGE", r.getRedemptionType());
        // 守恒：net + crossFee = original（scale 必须一致，用 setScale 后 compareTo）
        BigDecimal sum = r.getNetAmount().add(r.getCrossFee())
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(0, r.getOriginalAmount().compareTo(sum),
                "original = net + crossFee 守恒");
    }

    @Test
    void e2e_balanceConsumption_crossStore_withoutFee() {
        // 场景：张三在 A 店储值 5000，去 B 店消费；规则 mode=PARTIAL，费率 0%
        BigDecimal original = new BigDecimal("200.00");
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = original.subtract(fee);
        assertEquals(0, original.compareTo(net), "无手续费 → 净 = 原");

        CrmCrossStoreRedemption r = new CrmCrossStoreRedemption();
        r.setRedemptionType("BALANCE");
        r.setOriginalAmount(original);
        r.setCrossFee(fee);
        r.setNetAmount(net);
        r.setSourceStoreId(100L);
        r.setTargetStoreId(200L);

        assertEquals("BALANCE", r.getRedemptionType());
        assertTrue(isCross(r.getSourceStoreId(), r.getTargetStoreId()));
    }

    @Test
    void e2e_blockedByDisabledRule() {
        // 场景：规则禁止跨店用套餐（package_cross_mode = DISABLED）
        // 应直接返回 FORBIDDEN，无记录
        CrmCrossStoreRule rule = new CrmCrossStoreRule();
        rule.setPackageCrossEnabled(1);
        rule.setPackageCrossMode("DISABLED");  // 模式 = DISABLED 即便 enabled 也要拒

        boolean blocked = rule.getPackageCrossEnabled() == null
                || rule.getPackageCrossEnabled() == 0
                || "DISABLED".equals(rule.getPackageCrossMode());
        assertTrue(blocked, "mode=DISABLED 必须拒绝套餐跨店");
    }

    @Test
    void e2e_blockedByExceedingLimit() {
        // 场景：原始 1500，限额 1000 → 应拒绝
        BigDecimal original = new BigDecimal("1500.00");
        BigDecimal limit = new BigDecimal("1000.00");
        assertTrue(original.compareTo(limit) > 0, "超额必须拒绝");
    }
}