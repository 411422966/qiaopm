package com.meiyetec.service;

import com.meiyetec.entity.MktPointsRule;
import com.meiyetec.entity.PointGoods;
import com.meiyetec.entity.PointRecord;
import com.meiyetec.entity.PointRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * POINTS 模块实体字段验证（纯单元测试）
 *
 * 验证关键实体含 tenantId 字段：
 *  - PointRecord（market_point_record）
 *  - PointRule（market_point_rule）
 *  - MktPointsRule（mkt_points_rule，已有 tenantId）
 *  - PointGoods（point_goods）
 */
@DisplayName("POINTS 实体字段验证（纯单元测试）")
class PointsEntityFieldsTest {

    private static Field findField(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Test
    @DisplayName("POINTS-001~004: PointRecord 实体含 tenantId 字段")
    void pointRecord_hasTenantId() {
        Field f = findField(PointRecord.class, "tenantId");
        assertNotNull(f, "PointRecord 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("POINTS-005: PointRule 实体含 tenantId 字段")
    void pointRule_hasTenantId() {
        Field f = findField(PointRule.class, "tenantId");
        assertNotNull(f, "PointRule 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("POINTS-005: MktPointsRule 实体含 tenantId 字段")
    void mktPointsRule_hasTenantId() {
        Field f = findField(MktPointsRule.class, "tenantId");
        assertNotNull(f, "MktPointsRule 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("POINTS-006: PointGoods 实体含 tenantId 字段")
    void pointGoods_hasTenantId() {
        Field f = findField(PointGoods.class, "tenantId");
        assertNotNull(f, "PointGoods 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("PointRecord.memberId 必须是 Long 类型（不能是 String）")
    void pointRecord_memberId_isLong() {
        Field f = findField(PointRecord.class, "memberId");
        assertNotNull(f, "PointRecord.memberId 必须存在");
        assertEquals(Long.class, f.getType(), "PointRecord.memberId 必须是 Long");
    }

    @Test
    @DisplayName("POINTS-006: PointGoods 实例化 + setter/getter 正常")
    void pointGoods_pojo_instantiation() {
        PointGoods g = new PointGoods();
        g.setTenantId(1L);
        g.setName("积分兑换-护手霜");
        g.setCategory("GENERAL");
        g.setPointsPrice(500);
        g.setCashPrice(new BigDecimal("9.9"));
        g.setStock(100);
        g.setSoldCount(0);
        g.setStatus(1);
        assertEquals(1L, g.getTenantId());
        assertEquals("积分兑换-护手霜", g.getName());
        assertEquals(500, g.getPointsPrice());
    }

    @Test
    @DisplayName("POINTS-005: MktPointsRule 实例化 + setter/getter 正常")
    void mktPointsRule_pojo_instantiation() {
        MktPointsRule r = new MktPointsRule();
        r.setTenantId(1L);
        r.setRuleName("标准积分规则");
        r.setConsumeRate(new BigDecimal("1.00"));
        r.setRechargeMultiplier(new BigDecimal("2.00"));
        r.setPromotionMultiplier(new BigDecimal("3.00"));
        r.setPointsToCash(new BigDecimal("0.01"));
        r.setMaxUsePercent(new BigDecimal("30.00"));
        r.setEnabled(1);
        r.setEffectiveDate(LocalDate.now());
        r.setExpiryDate(LocalDate.now().plusYears(1));
        assertEquals("标准积分规则", r.getRuleName());
        assertEquals(new BigDecimal("2.00"), r.getRechargeMultiplier());
    }
}
