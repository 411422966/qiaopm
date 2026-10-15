package com.meiyetec.service;

import com.meiyetec.entity.*;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * P0-1 补强：库存消耗链路集成测试
 * <p>
 * 覆盖"订单支付 → BOM 拉取 → CAS 扣库存 → stock_record 写入 → pos_order_consumption 幂等"
 * 的完整数据流。
 * </p>
 */
class InventoryConsumptionFlowTest {

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
        ctx.setUserId(100L);
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** 场景 1：完整的"服务单件 + 单耗材 + 单批次"标准流程 */
    @Test
    void consumeByOrder_singleService_singleProduct_fullFlow() {
        Long orderId = 1001L;
        Long serviceId = 200L;
        Long productId = 500L;
        Long inventoryId = 800L;
        Long cashierId = 9L;

        // ---------- arrange: order paid ----------
        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2);  // 已支付
        order.setOrderNo("PO-TEST-1001");
        order.setCashierId(cashierId);
        order.setCashierName("收银员-小明");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        // ---------- arrange: order item ----------
        PosOrderItem item = new PosOrderItem();
        item.setOrderId(orderId);
        item.setServiceId(serviceId);
        item.setServiceName("深层清洁");
        item.setQuantity(1);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        // ---------- arrange: BOM（1 件次用 3 片化妆棉） ----------
        Map<String, Object> bom = new HashMap<>();
        bom.put("product_id", productId);
        bom.put("quantity", BigDecimal.valueOf(3));
        when(bomMapper.listSimpleBom(serviceId)).thenReturn(Collections.singletonList(bom));

        // ---------- arrange: 已存在的 idempotency 记录（首次执行应为空） ----------
        when(consumptionMapper.selectList(any())).thenReturn(Collections.emptyList());

        // ---------- arrange: StockProduct 有效 ----------
        StockProduct product = new StockProduct();
        product.setId(productId);
        product.setProductName("化妆棉");
        product.setCostPrice(new BigDecimal("2.50"));
        when(productMapper.selectById(productId)).thenReturn(product);

        // ---------- arrange: FEFO 选批次（FEFO 1 个批次） ----------
        StockInventory inv = new StockInventory();
        inv.setId(inventoryId);
        inv.setProductId(productId);
        inv.setBatchNo("B20260801");
        inv.setQuantity(100);
        inv.setVersion(1);
        when(inventoryMapper.listFefoCandidates(productId, 1L))
                .thenReturn(Collections.singletonList(inv));

        // ---------- arrange: CAS 扣减成功 ----------
        when(inventoryMapper.casOutbound(inventoryId, 1, 3)).thenReturn(1);

        // ---------- arrange: insert stock_record + pos_order_consumption ----------
        when(recordMapper.insert(any())).thenAnswer(invocation -> {
            StockRecord rec = invocation.getArgument(0);
            rec.setId(9001L);
            return 1;
        });

        // ---------- act ----------
        int written = service.consumeByOrder(orderId);

        // ---------- assert ----------
        assertEquals(1, written, "应写 1 条 pos_order_consumption");

        // 1. CAS 必须用正确参数
        ArgumentCaptor<Integer> qtyCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryMapper, times(1)).casOutbound(eq(inventoryId), eq(1), qtyCaptor.capture());
        assertEquals(3, qtyCaptor.getValue(), "扣减数量应为 3");

        // 2. stock_record 字段正确
        ArgumentCaptor<StockRecord> recordCaptor = ArgumentCaptor.forClass(StockRecord.class);
        verify(recordMapper, times(1)).insert(recordCaptor.capture());
        StockRecord rec = recordCaptor.getValue();
        assertEquals(productId, rec.getProductId());
        assertEquals("B20260801", rec.getBatchNo());
        assertEquals(4, rec.getType(), "type=4 表示领用");
        assertEquals(-3, rec.getQuantity(), "应为负数");
        assertEquals(100, rec.getBeforeQty());
        assertEquals(97, rec.getAfterQty());
        assertEquals(0, new BigDecimal("7.50").compareTo(rec.getTotalAmount()),
                "总价应为 2.5 × 3 = 7.5");
        assertEquals(orderId, rec.getOrderId());
        assertEquals(cashierId, rec.getOperatorId());
        assertEquals("收银员-小明", rec.getOperatorName());
        assertTrue(rec.getRemark().contains(order.getOrderNo()));

        // 3. pos_order_consumption 必须包含 idempotencyKey
        ArgumentCaptor<PosOrderConsumption> ocCaptor = ArgumentCaptor.forClass(PosOrderConsumption.class);
        verify(consumptionMapper, times(1)).insert(ocCaptor.capture());
        PosOrderConsumption oc = ocCaptor.getValue();
        assertEquals(orderId, oc.getOrderId());
        assertEquals(productId, oc.getProductId());
        assertEquals(serviceId, oc.getServiceId());
        assertEquals(3, oc.getQuantity(), "应记录原扣减数量 3");
        assertEquals(9001L, oc.getStockRecordId(), "应关联 stock_record");
        assertNotNull(oc.getIdempotencyKey());
        assertTrue(oc.getIdempotencyKey().startsWith("pos_order_consume:1:" + orderId + ":"));
    }

    /** 场景 2：FEFO 多批次跨批次合并（先扣到期近的） */
    @Test
    void consumeByOrder_multipleBatches_consumeFefoInOrder() {
        Long orderId = 1002L;
        Long serviceId = 201L;
        Long productId = 501L;

        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2);
        order.setOrderNo("PO-TEST-1002");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        PosOrderItem item = new PosOrderItem();
        item.setOrderId(orderId);
        item.setServiceId(serviceId);
        item.setServiceName("测试服务");
        item.setQuantity(1);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        Map<String, Object> bom = new HashMap<>();
        bom.put("product_id", productId);
        bom.put("quantity", BigDecimal.valueOf(10));  // 需要 10
        when(bomMapper.listSimpleBom(serviceId)).thenReturn(Collections.singletonList(bom));
        when(consumptionMapper.selectList(any())).thenReturn(Collections.emptyList());

        StockProduct product = new StockProduct();
        product.setId(productId);
        product.setProductName("面膜");
        product.setCostPrice(new BigDecimal("5.00"));
        when(productMapper.selectById(productId)).thenReturn(product);

        // 批次 A：剩 4，批次 B：剩 8 → 应 A→B 顺序扣（共 12 够）
        StockInventory invA = new StockInventory();
        invA.setId(8001L);
        invA.setProductId(productId);
        invA.setBatchNo("B-A");
        invA.setQuantity(4);
        invA.setVersion(1);
        StockInventory invB = new StockInventory();
        invB.setId(8002L);
        invB.setProductId(productId);
        invB.setBatchNo("B-B");
        invB.setQuantity(8);
        invB.setVersion(1);
        when(inventoryMapper.listFefoCandidates(productId, 1L))
                .thenReturn(Arrays.asList(invA, invB));

        when(inventoryMapper.casOutbound(eq(8001L), eq(1), eq(4))).thenReturn(1);
        when(inventoryMapper.casOutbound(eq(8002L), eq(1), eq(6))).thenReturn(1);
        when(recordMapper.insert(any())).thenAnswer(inv -> {
            StockRecord r = inv.getArgument(0);
            r.setId(9000L + r.getBatchNo().hashCode());
            return 1;
        });

        int written = service.consumeByOrder(orderId);

        assertEquals(1, written);
        // 两次 CAS：A 扣 4 + B 扣 6
        verify(inventoryMapper, times(1)).casOutbound(8001L, 1, 4);
        verify(inventoryMapper, times(1)).casOutbound(8002L, 1, 6);
        // 两次 stock_record
        verify(recordMapper, times(2)).insert(any());
    }

    /** 场景 3：库存总库存不足 → 抛 STOCK_NOT_ENOUGH */
    @Test
    void consumeByOrder_notEnoughStock_throws() {
        Long orderId = 1003L;
        Long serviceId = 202L;
        Long productId = 502L;

        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2);
        order.setOrderNo("PO-TEST-1003");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        PosOrderItem item = new PosOrderItem();
        item.setOrderId(orderId);
        item.setServiceId(serviceId);
        item.setServiceName("需求服务");
        item.setQuantity(1);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        Map<String, Object> bom = new HashMap<>();
        bom.put("product_id", productId);
        bom.put("quantity", BigDecimal.valueOf(100));
        when(bomMapper.listSimpleBom(serviceId)).thenReturn(Collections.singletonList(bom));
        when(consumptionMapper.selectList(any())).thenReturn(Collections.emptyList());

        StockProduct product = new StockProduct();
        product.setId(productId);
        product.setProductName("稀缺品");
        product.setCostPrice(BigDecimal.TEN);
        when(productMapper.selectById(productId)).thenReturn(product);

        // 只有 5 个，不够
        StockInventory inv = new StockInventory();
        inv.setId(8003L);
        inv.setProductId(productId);
        inv.setBatchNo("B-S");
        inv.setQuantity(5);
        when(inventoryMapper.listFefoCandidates(productId, 1L))
                .thenReturn(Collections.singletonList(inv));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.consumeByOrder(orderId));
        assertEquals(com.meiyetec.exception.BizCode.STOCK_NOT_ENOUGH.code, ex.getCode());
        assertTrue(ex.getMessage().contains("总库存不足"),
                "消息应说明总库存不足：" + ex.getMessage());
        // CAS 不应执行
        verify(inventoryMapper, never()).casOutbound(anyLong(), any(), any());
    }

    /** 场景 4：CAS 失败（版本冲突）→ 抛 CONCURRENCY_CONFLICT */
    @Test
    void consumeByOrder_casConflict_throws() {
        Long orderId = 1004L;
        Long serviceId = 203L;
        Long productId = 503L;

        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2);
        order.setOrderNo("PO-TEST-1004");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        PosOrderItem item = new PosOrderItem();
        item.setServiceId(serviceId);
        item.setServiceName("并发测试服务");
        item.setQuantity(1);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        Map<String, Object> bom = new HashMap<>();
        bom.put("product_id", productId);
        bom.put("quantity", BigDecimal.valueOf(1));
        when(bomMapper.listSimpleBom(serviceId)).thenReturn(Collections.singletonList(bom));
        when(consumptionMapper.selectList(any())).thenReturn(Collections.emptyList());

        StockProduct product = new StockProduct();
        product.setId(productId);
        product.setProductName("P");
        product.setCostPrice(BigDecimal.ONE);
        when(productMapper.selectById(productId)).thenReturn(product);

        StockInventory inv = new StockInventory();
        inv.setId(8004L);
        inv.setProductId(productId);
        inv.setBatchNo("B-CC");
        inv.setQuantity(10);
        inv.setVersion(99);
        when(inventoryMapper.listFefoCandidates(productId, 1L))
                .thenReturn(Collections.singletonList(inv));
        when(inventoryMapper.casOutbound(8004L, 99, 1)).thenReturn(0);  // CAS 失败

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.consumeByOrder(orderId));
        assertEquals(com.meiyetec.exception.BizCode.CONCURRENCY_CONFLICT.code, ex.getCode());
    }

    /** 场景 5：Idempotent replay — 第二次调用跳过写入 */
    @Test
    void consumeByOrder_replayIdempotent() {
        Long orderId = 1005L;
        Long serviceId = 204L;
        Long productId = 504L;

        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2);
        order.setOrderNo("PO-REPLAY");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        PosOrderItem item = new PosOrderItem();
        item.setServiceId(serviceId);
        item.setServiceName("幂等服务");
        item.setQuantity(1);
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        Map<String, Object> bom = new HashMap<>();
        bom.put("product_id", productId);
        bom.put("quantity", BigDecimal.valueOf(2));
        when(bomMapper.listSimpleBom(serviceId)).thenReturn(Collections.singletonList(bom));

        // 已存在 pos_order_consumption 记录
        String existingKey = "pos_order_consume:1:" + orderId + ":" + serviceId + ":" + productId;
        PosOrderConsumption exist = new PosOrderConsumption();
        exist.setTenantId(1L);
        exist.setOrderId(orderId);
        exist.setProductId(productId);
        exist.setServiceId(serviceId);
        exist.setIdempotencyKey(existingKey);
        when(consumptionMapper.selectList(any()))
                .thenReturn(Collections.singletonList(exist));

        int written = service.consumeByOrder(orderId);

        // 已存在 → 不写任何 stock_record / consumption
        assertEquals(0, written);
        verify(productMapper, never()).selectById(anyLong());
        verify(inventoryMapper, never()).casOutbound(anyLong(), any(), any());
        verify(recordMapper, never()).insert(any());
        verify(consumptionMapper, never()).insert(any());
    }

    /** 场景 6：quantity=2 服务 1 件 = 扣 2 × bom_qty */
    @Test
    void consumeByOrder_quantityMultiple_multipliesBom() {
        Long orderId = 1006L;
        Long serviceId = 205L;
        Long productId = 505L;

        PosOrder order = new PosOrder();
        order.setId(orderId);
        order.setTenantId(1L);
        order.setStoreId(1L);
        order.setStatus(2);
        order.setOrderNo("PO-QTY2");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        PosOrderItem item = new PosOrderItem();
        item.setServiceId(serviceId);
        item.setServiceName("多件服务");
        item.setQuantity(2);  // 数量 2
        when(orderItemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        Map<String, Object> bom = new HashMap<>();
        bom.put("product_id", productId);
        bom.put("quantity", BigDecimal.valueOf(3));  // 每次 3
        when(bomMapper.listSimpleBom(serviceId)).thenReturn(Collections.singletonList(bom));
        when(consumptionMapper.selectList(any())).thenReturn(Collections.emptyList());

        StockProduct product = new StockProduct();
        product.setId(productId);
        product.setProductName("P");
        product.setCostPrice(BigDecimal.ONE);
        when(productMapper.selectById(productId)).thenReturn(product);

        StockInventory inv = new StockInventory();
        inv.setId(8006L);
        inv.setProductId(productId);
        inv.setBatchNo("B-MULTI");
        inv.setQuantity(100);
        inv.setVersion(1);
        when(inventoryMapper.listFefoCandidates(productId, 1L))
                .thenReturn(Collections.singletonList(inv));
        when(inventoryMapper.casOutbound(eq(8006L), eq(1), eq(6))).thenReturn(1);  // 期望 3 × 2 = 6
        when(recordMapper.insert(any())).thenAnswer(inv2 -> {
            StockRecord r = inv2.getArgument(0);
            r.setId(9999L);
            return 1;
        });

        int written = service.consumeByOrder(orderId);

        assertEquals(1, written);
        // CAS 必须扣 6
        verify(inventoryMapper, times(1)).casOutbound(8006L, 1, 6);
        // 库存记录字段
        ArgumentCaptor<StockRecord> cap = ArgumentCaptor.forClass(StockRecord.class);
        verify(recordMapper).insert(cap.capture());
        assertEquals(-6, cap.getValue().getQuantity());
    }
}