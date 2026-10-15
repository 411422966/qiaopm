package com.meiyetec.service;

import com.meiyetec.entity.PackageOrder;
import com.meiyetec.entity.PackageRedemption;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R3.0-B 补丁：Entity 字段读写测试（验证新加字段的 getter/setter 工作正常）
 *
 * <p>集成测试覆盖项：</p>
 * <ul>
 *   <li>PackageOrder.storeId 读写</li>
 *   <li>PackageRedemption.storeId / crossFee / crossRuleId 读写</li>
 * </ul>
 */
class CrossStoreEntityFieldsTest {

    @Test
    void packageOrder_storeId_getterSetter() {
        PackageOrder o = new PackageOrder();
        assertNull(o.getStoreId(), "新对象 storeId 默认 null");

        o.setStoreId(100L);
        assertEquals(100L, o.getStoreId());

        o.setMemberId(1L);
        o.setOrderNo("PKG-2026-001");
        o.setPackageId(2L);
        o.setPackageName("面部护理");
        o.setTotalTimes(10);
        o.setRemainingTimes(8);
        o.setPrice(new BigDecimal("998.00"));
        o.setPayMethod("微信");
        o.setOperatorId(1L);
        o.setBuyTime(LocalDateTime.now());
        o.setExpireTime(LocalDateTime.now().plusDays(365));
        o.setStatus(1);
        o.setRemark("购买门店测试");

        assertEquals(100L, o.getStoreId());
        assertEquals("PKG-2026-001", o.getOrderNo());
    }

    @Test
    void packageRedemption_crossFields_getterSetter() {
        PackageRedemption r = new PackageRedemption();
        assertNull(r.getStoreId());
        assertNull(r.getCrossFee());
        assertNull(r.getCrossRuleId());

        r.setStoreId(200L);
        r.setCrossFee(new BigDecimal("5.50"));
        r.setCrossRuleId(42L);

        assertEquals(200L, r.getStoreId());
        assertEquals(0, r.getCrossFee().compareTo(new BigDecimal("5.50")));
        assertEquals(Long.valueOf(42L), r.getCrossRuleId());

        // 其它原有字段仍可用
        r.setOrderId(1L);
        r.setMemberId(2L);
        r.setPackageId(3L);
        r.setTimes(1);
        r.setServiceItem("面部护理");
        r.setBeauticianId(5L);
        r.setOperatorId(1L);
        r.setRedeemTime(LocalDateTime.now());
        r.setStatus(1);

        assertNotNull(r.getRedeemTime());
        assertEquals(1, r.getTimes());
    }
}
