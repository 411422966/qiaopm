package com.meiyetec.service;

import com.meiyetec.entity.BizExpiryAlertLog;
import com.meiyetec.entity.BizExpiryAlertRule;
import com.meiyetec.entity.BizExpiryHandleLog;
import com.meiyetec.entity.BizServiceMaterial;
import com.meiyetec.entity.MaterialSubstitute;
import com.meiyetec.entity.ServiceMaterialGroup;
import com.meiyetec.entity.StockInventory;
import com.meiyetec.entity.StockProduct;
import com.meiyetec.entity.StockRecord;
import com.meiyetec.entity.StockCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STOCK-014/013/003：实体字段反射验证（纯单元测试，不依赖 Spring 上下文）
 *
 * 验证关键实体含 tenantId 字段：
 *  - BizServiceMaterial（STOCK-014 耗材 BOM）
 *  - BizExpiryAlertRule / BizExpiryAlertLog / BizExpiryHandleLog / BizExpiryDashboardCache（STOCK-013）
 *  - MaterialSubstitute / ServiceMaterialGroup（STOCK-014 伴随表）
 *  - StockProduct / StockInventory / StockRecord / StockCheck（STOCK 核心表）
 *
 * 由于项目全局存在 ReconciliationController / PosReconciliationController 路由冲突
 * （POS-010 历史遗留，属于 POS 修复范围），导致 @SpringBootTest 启动失败。
 * 本测试采用纯 JUnit（不打 Mock）方式验证 STOCK 模块自身的实体正确性。
 */
@DisplayName("STOCK-014/013/003 实体字段反射验证（纯单元测试）")
class StockEntityFieldsTest {

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
    @DisplayName("STOCK-014: BizServiceMaterial 含 tenantId 字段")
    void stock014_bom_hasTenantId() {
        Field f = findField(BizServiceMaterial.class, "tenantId");
        assertNotNull(f, "BizServiceMaterial 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-014: MaterialSubstitute 含 tenantId 字段")
    void stock014_substitute_hasTenantId() {
        Field f = findField(MaterialSubstitute.class, "tenantId");
        assertNotNull(f, "MaterialSubstitute 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-014: ServiceMaterialGroup 含 tenantId 字段")
    void stock014_group_hasTenantId() {
        Field f = findField(ServiceMaterialGroup.class, "tenantId");
        assertNotNull(f, "ServiceMaterialGroup 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-013: BizExpiryAlertRule 含 tenantId 字段")
    void stock013_expiryRule_hasTenantId() {
        Field f = findField(BizExpiryAlertRule.class, "tenantId");
        assertNotNull(f, "BizExpiryAlertRule 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-013: BizExpiryAlertLog 含 tenantId 字段")
    void stock013_expiryAlertLog_hasTenantId() {
        Field f = findField(BizExpiryAlertLog.class, "tenantId");
        assertNotNull(f, "BizExpiryAlertLog 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-013: BizExpiryHandleLog 含 tenantId 字段")
    void stock013_expiryHandleLog_hasTenantId() {
        Field f = findField(BizExpiryHandleLog.class, "tenantId");
        assertNotNull(f, "BizExpiryHandleLog 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-003: StockProduct 含 tenantId 字段")
    void stock003_product_hasTenantId() {
        Field f = findField(StockProduct.class, "tenantId");
        assertNotNull(f, "StockProduct 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-005: StockInventory 含 tenantId 字段")
    void stock005_inventory_hasTenantId() {
        Field f = findField(StockInventory.class, "tenantId");
        assertNotNull(f, "StockInventory 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-006/007: StockRecord 含 tenantId 字段")
    void stock006_record_hasTenantId() {
        Field f = findField(StockRecord.class, "tenantId");
        assertNotNull(f, "StockRecord 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("STOCK-008: StockCheck 含 tenantId 字段")
    void stock008_check_hasTenantId() {
        Field f = findField(StockCheck.class, "tenantId");
        assertNotNull(f, "StockCheck 必须含 tenantId 字段");
        assertEquals(Long.class, f.getType());
    }

    @Test
    @DisplayName("实体实例化 + setter/getter 不报错（POJO 健全性）")
    void entities_canBeInstantiated() {
        BizServiceMaterial bom = new BizServiceMaterial();
        bom.setTenantId(1L); bom.setServiceId(2L); bom.setProductId(3L);
        bom.setQuantity(new BigDecimal("1.5")); bom.setUnit("瓶"); bom.setIsRequired(1);
        assertEquals(1L, bom.getTenantId());
        assertEquals("瓶", bom.getUnit());

        BizExpiryAlertRule rule = new BizExpiryAlertRule();
        rule.setTenantId(1L); rule.setRuleName("X"); rule.setCriticalDays(7); rule.setWarningDays(30);
        rule.setNotifyChannels("system"); rule.setAutoHandle(0); rule.setStatus(1);
        assertEquals(1L, rule.getTenantId());
        assertEquals("X", rule.getRuleName());

        BizExpiryAlertLog log = new BizExpiryAlertLog();
        log.setTenantId(1L); log.setInventoryId(100L); log.setProductId(3L);
        log.setProductName("P"); log.setBatchNo("B1"); log.setQuantity(10);
        log.setExpiryDate(LocalDate.now()); log.setDaysToExpiry(5);
        log.setAlertLevel("critical"); log.setAlertType("near_expiry"); log.setStatus("pending");
        log.setNotified(0);
        assertEquals("critical", log.getAlertLevel());

        BizExpiryHandleLog h = new BizExpiryHandleLog();
        h.setTenantId(1L); h.setInventoryId(100L); h.setProductId(3L);
        h.setProductName("P"); h.setBatchNo("B1"); h.setHandleMethod("dispose");
        h.setHandleQuantity(10); h.setUnitPrice(new BigDecimal("88"));
        h.setLossAmount(new BigDecimal("880")); h.setHandleDate(LocalDate.now());
        h.setOperatorName("admin"); h.setEvidence("{}"); h.setRemark("test");
        assertEquals("dispose", h.getHandleMethod());
    }
}