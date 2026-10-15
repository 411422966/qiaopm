package com.meiyetec.service;

import com.meiyetec.entity.WmsStockTransfer;
import com.meiyetec.entity.WmsWarehouse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R3.0-D 补丁：多仓 + 调拨 Entity 字段读写测试（验证新加字段的 getter/setter 工作正常）
 */
class WmsEntityFieldsTest {

    @Test
    void warehouse_allFields_getterSetter() {
        WmsWarehouse w = new WmsWarehouse();
        assertNull(w.getId());
        assertNull(w.getWarehouseCode());

        w.setTenantId(1L);
        w.setBrandId(2L);
        w.setStoreId(10L);
        w.setWarehouseCode("WH-TEST-001");
        w.setWarehouseName("测试门店仓");
        w.setWarehouseType("STORE");
        w.setLocation("门店一楼");
        w.setManagerId(5L);
        w.setManagerName("张三");
        w.setEnabled(1);
        w.setRemark("测试备注");

        assertEquals(1L, w.getTenantId());
        assertEquals("WH-TEST-001", w.getWarehouseCode());
        assertEquals("STORE", w.getWarehouseType());
        assertEquals(1, w.getEnabled());
    }

    @Test
    void warehouse_centralType() {
        WmsWarehouse w = new WmsWarehouse();
        w.setWarehouseType("CENTRAL");
        w.setStoreId(null);  // 总部中央仓 storeId = null
        w.setWarehouseName("总部中央仓");
        assertEquals("CENTRAL", w.getWarehouseType());
        assertNull(w.getStoreId());
    }

    @Test
    void transfer_pendingStatus_default() {
        WmsStockTransfer t = new WmsStockTransfer();
        assertNull(t.getStatus());
        assertNull(t.getTransferNo());

        t.setTenantId(1L);
        t.setTransferNo("TR202608140001");
        t.setProductId(100L);
        t.setProductName("玻尿酸精华");
        t.setBatchNo("BN20260801");
        t.setQuantity(50);
        t.setFromWarehouseId(1L);
        t.setToWarehouseId(2L);
        t.setFromStoreId(10L);
        t.setToStoreId(20L);
        t.setStatus("PENDING");
        t.setOperatorName("admin");
        t.setRemark("测试调拨");
        t.setShippedAt(LocalDateTime.now());
        t.setReceivedAt(LocalDateTime.now());

        assertEquals("TR202608140001", t.getTransferNo());
        assertEquals("PENDING", t.getStatus());
        assertNotNull(t.getShippedAt());
    }

    @Test
    void transfer_statusTransitions() {
        WmsStockTransfer t = new WmsStockTransfer();
        // PENDING → SHIPPED → RECEIVED 状态机
        t.setStatus("PENDING");
        assertEquals("PENDING", t.getStatus());

        t.setStatus("SHIPPED");
        assertEquals("SHIPPED", t.getStatus());

        t.setStatus("RECEIVED");
        assertEquals("RECEIVED", t.getStatus());
    }
}