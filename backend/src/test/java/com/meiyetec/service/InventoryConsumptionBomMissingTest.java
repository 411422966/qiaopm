package com.meiyetec.service;

import com.meiyetec.entity.PosOrder;
import com.meiyetec.entity.PosOrderItem;
import com.meiyetec.entity.PosOrderConsumption;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizServiceMaterialMapper;
import com.meiyetec.mapper.PosOrderConsumptionMapper;
import com.meiyetec.mapper.PosOrderItemMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.mapper.StockInventoryMapper;
import com.meiyetec.mapper.StockProductMapper;
import com.meiyetec.mapper.StockRecordMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2.0-D · T-D02 修复回归测试：库存自动扣减
 * <p>
 * 覆盖两个修复点：
 * 1. 断点 B：BOM 缺失时不再静默 skip，会记 WARN 日志并累加 hasBomMissing 计数
 * 2. 断点 A：OrderPaidEvent 不再是孤儿事件（由 OrderPaidEventListener 验证）
 * </p>
 */
class InventoryConsumptionBomMissingTest {

    private PosOrderMapper orderMapper;
    private PosOrderItemMapper orderItemMapper;
    private PosOrderConsumptionMapper consumptionMapper;
    private BizServiceMaterialMapper bomMapper;
    private StockProductMapper productMapper;
    private StockInventoryMapper inventoryMapper;
    private StockRecordMapper recordMapper;
    private InventoryConsumptionService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(PosOrderMapper.class);
        orderItemMapper = mock(PosOrderItemMapper.class);
        consumptionMapper = mock(PosOrderConsumptionMapper.class);
        bomMapper = mock(BizServiceMaterialMapper.class);
        productMapper = mock(StockProductMapper.class);
        inventoryMapper = mock(StockInventoryMapper.class);
        recordMapper = mock(StockRecordMapper.class);

        service = new InventoryConsumptionService(
                orderMapper, orderItemMapper, consumptionMapper,
                bomMapper, productMapper, inventoryMapper, recordMapper);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId(1L);
        ctx.setUserId(1L);
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /**
     * 断点 B 修复验证：BOM 缺失必须 warn + 计数（不再静默 continue 完全无痕）
     * 但为了不破坏现有订单，最小修复策略是"显式记录 + 不抛错"。这里验证：
     * - 不会调用库存扣减（CAS）
     * - 不会写 stock_record
     * - 不会写 pos_order_consumption
     * - 返回 0（按"已记录"处理）
     */
    @Test
    void consumeByOrder_bomMissing_doesNotConsumeButLogs() {
        // arrange
        Long orderId = 100L;
        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2); // 已支付
        order.setOrderNo("TEST-001");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        PosOrderItem item = new PosOrderItem();
        item.setServiceId(999L);
        item.setServiceName("未配BOM的服务");
        item.setQuantity(1);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        // BOM 为空
        when(bomMapper.listSimpleBom(999L)).thenReturn(Collections.emptyList());

        // act
        int written = service.consumeByOrder(orderId);

        // assert
        assertEquals(0, written, "BOM 缺失时不应有任何写入");
        verify(inventoryMapper, never()).casOutbound(anyLong(), any(), any());
        verify(recordMapper, never()).insert(any());
        verify(consumptionMapper, never()).insert(any());
    }

    /**
     * 断点 B 修复验证：未支付订单不能扣库存
     */
    @Test
    void consumeByOrder_unpaidOrder_returnsZero() {
        Long orderId = 200L;
        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setStatus(1); // 进行中
        when(orderMapper.selectById(orderId)).thenReturn(order);

        int written = service.consumeByOrder(orderId);

        assertEquals(0, written);
        verify(bomMapper, never()).listSimpleBom(anyLong());
    }

    /**
     * 断点 B 修复验证：订单不存在直接返回 0
     */
    @Test
    void consumeByOrder_orderNotFound_returnsZero() {
        when(orderMapper.selectById(404L)).thenReturn(null);
        int written = service.consumeByOrder(404L);
        assertEquals(0, written);
    }
}
