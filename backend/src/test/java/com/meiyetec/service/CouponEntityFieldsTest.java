package com.meiyetec.service;

import com.meiyetec.entity.Coupon;
import com.meiyetec.entity.CouponGrant;
import com.meiyetec.entity.MktCoupon;
import com.meiyetec.entity.MktCouponBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * COUPON 模块实体字段验证（纯单元测试，不依赖 Spring 上下文）
 *
 * 验证关键实体含 tenantId 字段：
 *  - Coupon / CouponGrant（/market/coupon/*）
 *  - MktCoupon / MktCouponBatch（/mkt/coupon/*）
 */
@DisplayName("COUPON 实体字段验证（纯单元测试）")
class CouponEntityFieldsTest {

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
    @DisplayName("COUPON-001~005: Coupon 实体含 tenantId 字段")
    void coupon_entity_hasTenantId() {
        Field f = findField(Coupon.class, "tenantId");
        assertNotNull(f, "Coupon 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("COUPON-006~008: CouponGrant 实体含 tenantId 字段")
    void couponGrant_entity_hasTenantId() {
        Field f = findField(CouponGrant.class, "tenantId");
        assertNotNull(f, "CouponGrant 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("/mkt/coupon: MktCoupon 实体含 tenantId 字段")
    void mktCoupon_entity_hasTenantId() {
        Field f = findField(MktCoupon.class, "tenantId");
        assertNotNull(f, "MktCoupon 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("/mkt/coupon: MktCouponBatch 实体含 tenantId 字段")
    void mktCouponBatch_entity_hasTenantId() {
        Field f = findField(MktCouponBatch.class, "tenantId");
        assertNotNull(f, "MktCouponBatch 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("实体实例化 + setter/getter 不报错（POJO 健全性）")
    void entities_canBeInstantiated() {
        Coupon c = new Coupon();
        c.setTenantId(1L); c.setName("测试券");
        c.setType(1); c.setRuleType(1); c.setRuleValue(new BigDecimal("50"));
        c.setMinAmount(BigDecimal.ZERO); c.setTotalCount(100); c.setPerLimit(1);
        c.setStartTime(LocalDateTime.now()); c.setEndTime(LocalDateTime.now().plusDays(30));
        c.setStatus(1); c.setRemark("备注");
        assertEquals(1L, c.getTenantId());
        assertEquals("测试券", c.getName());

        CouponGrant g = new CouponGrant();
        g.setTenantId(1L); g.setCouponId(10L); g.setMemberId(20L);
        g.setCode("ABC123"); g.setStatus(1); g.setSource(1);
        assertEquals(1L, g.getTenantId());
        assertEquals("ABC123", g.getCode());

        MktCouponBatch batch = new MktCouponBatch();
        batch.setTenantId(1L); batch.setStoreId(1L);
        batch.setBatchNo("B20260101"); batch.setCouponName("满减券");
        batch.setCouponType("CASH"); batch.setDiscountAmount(new BigDecimal("50"));
        batch.setMinConsume(BigDecimal.ZERO); batch.setDiscountScope("ALL");
        batch.setTotalCount(100); batch.setIssuedCount(0); batch.setUsedCount(0);
        batch.setEffectiveDays(30); batch.setStatus(1);
        batch.setExpireWarningDays(3);
        assertEquals("B20260101", batch.getBatchNo());

        MktCoupon mc = new MktCoupon();
        mc.setTenantId(1L); mc.setStoreId(1L); mc.setBatchId(100L);
        mc.setCouponCode("XYZ999"); mc.setMemberId(20L); mc.setMemberName("张三");
        mc.setCouponType("CASH"); mc.setDiscountAmount(new BigDecimal("50"));
        mc.setMinConsume(BigDecimal.ZERO); mc.setDiscountScope("ALL");
        mc.setStatus(1);
        mc.setEffectiveDate(LocalDate.now()); mc.setExpireDate(LocalDate.now().plusDays(30));
        mc.setGrantedAt(LocalDateTime.now()); mc.setGrantedBy("admin");
        assertEquals("XYZ999", mc.getCouponCode());
    }
}