package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PosOrder;
import com.meiyetec.entity.PosOrderConsumption;
import com.meiyetec.entity.PosOrderItem;
import com.meiyetec.entity.StockInventory;
import com.meiyetec.entity.StockProduct;
import com.meiyetec.entity.StockRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizServiceMaterialMapper;
import com.meiyetec.mapper.PosOrderConsumptionMapper;
import com.meiyetec.mapper.PosOrderItemMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.mapper.StockInventoryMapper;
import com.meiyetec.mapper.StockProductMapper;
import com.meiyetec.mapper.StockRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存消耗自动写库服务（R2.0-D）
 *
 * <p>订单支付成功（status=2）后，按 biz_service_material（BOM）逐条扣减库存并写 stock_record(type=4 领用)。
 *
 * <p>幂等设计：pos_order_consumption 用 UNIQUE(idempotency_key) 兜底，重复进入只读不写。
 *
 * <p>边界：库存不足抛 STOCK_NOT_ENOUGH；CAS 失败抛 CONCURRENCY_CONFLICT。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryConsumptionService {

    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper orderItemMapper;
    private final PosOrderConsumptionMapper consumptionMapper;
    private final BizServiceMaterialMapper bomMapper;
    private final StockProductMapper productMapper;
    private final StockInventoryMapper inventoryMapper;
    private final StockRecordMapper recordMapper;
    private final StockDualVerifyService dualVerifyService;

    /**
     * 主入口：订单支付成功后调用
     * @return 写库条数（用于监控/日志）
     */
    @Transactional(rollbackFor = Exception.class)
    public int consumeByOrder(Long orderId) {
        if (orderId == null) return 0;
        PosOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[consumeByOrder] order not found id={}", orderId);
            return 0;
        }
        if (order.getStatus() == null || order.getStatus() != 2) {
            // 未完成支付的订单不扣库存
            log.debug("[consumeByOrder] order {} status={} skip", orderId, order.getStatus());
            return 0;
        }

        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) tenantId = order.getTenantId() == null ? 1L : order.getTenantId();

        // 拉订单明细
        List<PosOrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<PosOrderItem>()
                        .eq(PosOrderItem::getOrderId, orderId));
        if (items.isEmpty()) return 0;

        // 已写过的 idem keys（避免重复进入时白白查 BOM）
        List<String> alreadyKeys = new ArrayList<>();
        for (PosOrderItem it : items) {
            List<Map<String, Object>> bom = bomMapper.listSimpleBom(it.getServiceId());
            for (Map<String, Object> b : bom) {
                alreadyKeys.add(buildIdemKey(tenantId, orderId, it.getServiceId(), b.get("product_id")));
            }
        }
        List<PosOrderConsumption> exists = consumptionMapper.selectList(
                new LambdaQueryWrapper<PosOrderConsumption>()
                        .eq(PosOrderConsumption::getTenantId, tenantId)
                        .eq(PosOrderConsumption::getOrderId, orderId));
        Map<String, Boolean> keyDone = new HashMap<>();
        for (PosOrderConsumption oc : exists) {
            keyDone.put(oc.getIdempotencyKey(), Boolean.TRUE);
        }

        int written = 0;
        int hasBomMissing = 0;
        for (PosOrderItem item : items) {
            if (item.getServiceId() == null) continue;
            int itemQty = item.getQuantity() == null ? 1 : item.getQuantity();
            List<Map<String, Object>> bom = bomMapper.listSimpleBom(item.getServiceId());
            if (bom.isEmpty()) {
                hasBomMissing++;
                log.warn("[consumeByOrder] BOM missing serviceId={} serviceName={} orderId={} — 库存未扣减，请尽快在 BOM 表补录",
                        item.getServiceId(), item.getServiceName(), orderId);
                continue;
            }

            for (Map<String, Object> b : bom) {
                Long productId = ((Number) b.get("product_id")).longValue();
                BigDecimal qtyPerService = new BigDecimal(b.get("quantity").toString());
                int qty = qtyPerService.multiply(BigDecimal.valueOf(itemQty))
                        .setScale(0, RoundingMode.HALF_UP).intValue();
                if (qty <= 0) continue;

                String idemKey = buildIdemKey(tenantId, orderId, item.getServiceId(), productId);
                if (keyDone.containsKey(idemKey)) {
                    log.debug("[consumeByOrder] skip duplicate idemKey={}", idemKey);
                    continue;
                }

                StockProduct product = productMapper.selectById(productId);
                if (product == null) {
                    throw new BusinessException(BizCode.NOT_FOUND,
                            "服务 " + item.getServiceName() + " 关联的耗材 id=" + productId + " 已删除");
                }

                // FEFO 选批次（按到期日 ASC，缺货则顺序拿下一个）
                List<StockInventory> candidates = inventoryMapper.listFefoCandidates(productId, order.getStoreId());
                if (candidates.isEmpty()) {
                    throw new BusinessException(BizCode.STOCK_NOT_ENOUGH,
                            "耗材「" + product.getProductName() + "」无库存，服务 " + item.getServiceName());
                }

                StockInventory picked = null;
                int remaining = qty;
                List<StockInventory> pickedInvs = new ArrayList<>();
                for (StockInventory c : candidates) {
                    if (c.getQuantity() >= remaining) { picked = c; pickedInvs.add(c); remaining = 0; break; }
                    if (c.getQuantity() > 0) { pickedInvs.add(c); remaining -= c.getQuantity(); }
                }
                if (remaining > 0) {
                    throw new BusinessException(BizCode.STOCK_NOT_ENOUGH,
                            "耗材「" + product.getProductName() + "」总库存不足，需要 " + qty + "，可用 " + (qty - remaining));
                }

                // 多批次顺序 CAS（每批次一次 CAS，失败抛并发冲突由外层事务回滚）
                int remain = qty;
                int originalRemain = qty;
                List<StockRecord> records = new ArrayList<>();
                for (StockInventory inv : pickedInvs) {
                    int thisBatchQty = Math.min(remain, inv.getQuantity());
                    int rows = inventoryMapper.casOutbound(inv.getId(), inv.getVersion(), thisBatchQty);
                    if (rows == 0) {
                        throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
                                "库存并发扣减失败：耗材 " + product.getProductName() + " 批次 " + inv.getBatchNo());
                    }
                    remain -= thisBatchQty;

                    StockRecord rec = new StockRecord();
                    rec.setTenantId(tenantId);
                    rec.setStoreId(order.getStoreId());
                    rec.setProductId(productId);
                    rec.setBatchNo(inv.getBatchNo());
                    rec.setType(4); // 4=领用
                    rec.setQuantity(-thisBatchQty);
                    rec.setBeforeQty(inv.getQuantity());
                    rec.setAfterQty(inv.getQuantity() - thisBatchQty);
                    rec.setUnitPrice(product.getCostPrice());
                    rec.setTotalAmount(product.getCostPrice()
                            .multiply(BigDecimal.valueOf(thisBatchQty)));
                    rec.setOrderId(orderId);
                    rec.setOperatorId(order.getCashierId() == null ? 1L : order.getCashierId());
                    rec.setOperatorName(order.getCashierName() == null ? "system" : order.getCashierName());
                    rec.setRemark("订单支付自动扣减：" + order.getOrderNo()
                            + (item.getServiceName() == null ? "" : " / " + item.getServiceName()));
                    rec.setRecordTime(LocalDateTime.now());
                    recordMapper.insert(rec);
                    records.add(rec);
                }

                // P2-11 IND-05：订单消耗完成后检查是否超过双人核销阈值
                BigDecimal consumeTotal = product.getCostPrice()
                        .multiply(BigDecimal.valueOf(originalRemain));
                for (StockRecord r : records) {
                    dualVerifyService.createPendingRecordIfNeeded(
                            r.getId(), null,
                            "CONSUMPTION", "4",    // 消耗=4 领用
                            order.getCashierId() == null ? 1L : order.getCashierId(),
                            order.getCashierName() == null ? "system" : order.getCashierName(),
                            order.getStoreId(), consumeTotal);
                }

                // 幂等记录
                PosOrderConsumption oc = new PosOrderConsumption();
                oc.setTenantId(tenantId);
                oc.setOrderId(orderId);
                oc.setProductId(productId);
                oc.setServiceId(item.getServiceId());
                oc.setQuantity(originalRemain);
                oc.setIdempotencyKey(idemKey);
                if (!records.isEmpty()) {
                    oc.setStockRecordId(records.get(0).getId());
                }
                try {
                    consumptionMapper.insert(oc);
                } catch (DuplicateKeyException dup) {
                    log.info("[consumeByOrder] dup-key caught, rollback this material only. orderId={}, productId={}",
                            orderId, productId);
                    throw new BusinessException(BizCode.IDEMPOTENT_REPLAY,
                            "订单 " + order.getOrderNo() + " 的耗材已被并发扣减，请刷新页面");
                }
                written++;
            }
        }
        log.info("[consumeByOrder] orderId={} consumed {} materials (bom_missing_items={})",
                orderId, written, hasBomMissing);
        return written;
    }

    private static String buildIdemKey(Long tenantId, Long orderId, Long serviceId, Object productId) {
        return "pos_order_consume:" + tenantId + ":" + orderId + ":" + serviceId + ":" + productId;
    }
}
