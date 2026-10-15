# 美业 SaaS 黄金路径真实性审计 v2

**审计时间**: 2026-08-13
**审计版本**: v2.0 (Reality Golden Path)
**审计原则**: CODE > SQL SCHEMA > MAPPER > ENTITY > RUNTIME
**关键关注**: 🔴 库存消耗路径（业务最容易出问题的环节）

---

## 一、🔴 黄金路径 1：服务完成 → 库存扣减（CRITICAL）

### 1.1 完整调用链（Code Evidence）

```text
┌─────────────────┐
│ 前端：收银台点击 │  POS 页面 → /pos/orders/{id}/pay
└────────┬────────┘
         ▼
┌─────────────────────────────────────────────┐
│ PosController.pay(@PathVariable id, ...)   │  Controller 层入口
│  - @Transactional(rollbackFor=Exception)   │  L199-254
│  - 写 pos_payment、写订单、扣钱包/积分/套餐  │
└────────┬────────────────────────────────────┘
         ▼ (status==2 已支付)
┌─────────────────────────────────────────────┐
│ PosController.pay() L234-246                 │
│  if (order.getStatus() == 2) {              │
│      commissionCalculationService            │
│          .calculateOrder(order.getId());    │
│      // R2.0-D T-D02：库存消耗同步调用        │
│      inventoryConsumptionService            │  ◀── 关键入口
│          .consumeByOrder(order.getId());    │
│      eventPublisher.publishEvent(            │
│          new OrderPaidEvent(this, order));  │
│  }                                            │
└────────┬────────────────────────────────────┘
         ▼
┌─────────────────────────────────────────────┐
│ InventoryConsumptionService.consumeByOrder() │  Service 层主入口
│  @Transactional(rollbackFor=Exception)       │  L60-204
└────────┬────────────────────────────────────┘
         ▼ 循环订单 item
�─────────────────────────────────────────────┐
│ BizServiceMaterialMapper.listSimpleBom()    │  BOM 查询
│  SELECT product_id, quantity FROM             │  L31-32
│  biz_service_material WHERE deleted=0          │
│  AND service_id = #{serviceId}                 │
└────────�────────────────────────────────────┘
         ▼ FEFO 选批次
┌─────────────────────────────────────────────�
│ StockInventoryMapper.listFefoCandidates()    │
│  SELECT * FROM stock_inventory                │  L18-23
│  WHERE product_id=#{p} AND store_id=#{s}     │
│  AND quantity > 0                              │
│  ORDER BY COALESCE(expiry_date,'9999-12-31') │
│  ASC, id ASC                                  │
└────────�────────────────────────────────────┘
         ▼ 多批次 CAS 扣减
┌─────────────────────────────────────────────┐
│ StockInventoryMapper.casOutbound()           │
│  UPDATE stock_inventory                       │  L28-39
│  SET quantity = quantity - #{qty},             │
│      version = version + 1,                   │
│      last_out_time = CURRENT_TIMESTAMP         │
│  WHERE id = #{id}                              │
│    AND version = #{version}                    │
│    AND quantity >= #{qty}                      │
│    AND deleted = 0                             │
└────────┬────────────────────────────────────�
         ▼ 写流水 + 幂等记录
┌─────────────────────────────────────────────┐
│ recordMapper.insert(StockRecord)              │  type=4 领用
│ consumptionMapper.insert(PosOrderConsumption)│  含 idempotency_key
└─────────────────────────────────────────────┘
```

### 1.2 12 个核心问题逐项审计

#### Q1. **服务完成触发点** - 服务完成后谁调用库存扣减？

**答案**: 不是"服务完成"触发，而是"订单支付成功"触发。

**Evidence**:
```java
// PosController.java L198-254
@PostMapping("/orders/{id:\\d+}/pay")
@Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> pay(...) {
    // ... 支付明细处理 ...
    if (totalPaid.compareTo(order.getFinalAmount()) >= 0 || hasPackage) {
        order.setStatus(2);  // 2=已支付
        order.setPayTime(LocalDateTime.now());
    }
    orderMapper.updateById(order);
    if (order.getStatus() == 2) {
        commissionCalculationService.calculateOrder(order.getId());
        // R2.0-D T-D02：库存消耗同步调用
        try {
            inventoryConsumptionService.consumeByOrder(order.getId());
        } catch (BusinessException be) {
            throw be;
        }
        eventPublisher.publishEvent(new OrderPaidEvent(this, order));
    }
}
```

**Reality Status**: 🟢 REAL - 触发点明确

**⚠️ 问题**: "服务完成"和"订单支付"是不同概念。
- 真实业务应该是：技师做完服务 → 服务状态变更 → 触发耗材消耗
- 当前实现是：订单支付（status=2）→ 立即扣库存
- 影响：客户买了服务未到店，库存也会被扣（资金占用风险）

---

#### Q2. **消耗记录创建** - 是否写 `pos_order_consumption` / `stock_record` / `stock_inventory`？

**答案**: 三者都写。

**Evidence**:
```java
// InventoryConsumptionService.java L181-198
PosOrderConsumption oc = new PosOrderConsumption();
oc.setTenantId(tenantId);
oc.setOrderId(orderId);
oc.setProductId(productId);
oc.setServiceId(item.getServiceId());
oc.setQuantity(originalRemain);
oc.setIdempotencyKey(idemKey);
if (!records.isEmpty()) {
    oc.setStockRecordId(records.get(0).getId());  // 关联 stock_record
}
try {
    consumptionMapper.insert(oc);
} catch (DuplicateKeyException dup) {
    throw new BusinessException(BizCode.IDEMPOTENT_REPLAY, ...);
}
```

**同时** L158-177 写 `stock_record`（type=4 领用）：
```java
StockRecord rec = new StockRecord();
rec.setType(4); // 4=领用
rec.setQuantity(-thisBatchQty);
rec.setBeforeQty(inv.getQuantity());
rec.setAfterQty(inv.getQuantity() - thisBatchQty);
rec.setOrderId(orderId);
rec.setOperatorId(order.getCashierId() == null ? 1L : order.getCashierId());
rec.setOperatorName(order.getCashierName() == null ? "system" : order.getCashierName());
rec.setRemark("订单支付自动扣减：" + order.getOrderNo() + ...);
recordMapper.insert(rec);
```

**同时** L151 通过 `inventoryMapper.casOutbound()` 更新 `stock_inventory`。

**Reality Status**: 🟢 REAL - 三表完整写入

---

#### Q3. **事务边界** - 是否在 `@Transactional` 内？失败会回滚吗？

**答案**: 是，双重事务保护。

**Evidence**:
```java
// PosController.pay() L199 - 外层事务
@PostMapping("/orders/{id:\\d+}/pay")
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public Result<Map<String, Object>> pay(...) { ... }

// InventoryConsumptionService.consumeByOrder() L60 - 内层事务
@Transactional(rollbackFor = Exception.class)
public int consumeByOrder(Long orderId) { ... }
```

**回滚行为**:
- 内层 `consumeByOrder` 抛 `BusinessException(STOCK_NOT_ENOUGH)` → 外层事务捕获并重抛 → 整个 `pay()` 方法回滚
- 订单 `status` 不会变成 2，支付明细也不会落库
- ✅ "订单与库存同生共死"语义成立

**Evidence 注释**:
```java
// PosController.java L236-237
// R2.0-D T-D02：库存消耗同步调用，保留"订单与库存同生共死"的事务语义。
// 库存不足/CAS 失败抛 BusinessException → 事务回滚，订单也不落。
```

**Reality Status**: 🟢 REAL - 事务边界正确

---

#### Q4. **幂等性** - 同一订单能否被重复扣减？

**答案**: 不能，三层幂等防护。

**Evidence**:

**Layer 1 - 应用层预先检查** (InventoryConsumptionService L83-98):
```java
// 拉已写过的 idempotency_key
List<PosOrderConsumption> exists = consumptionMapper.selectList(
        new LambdaQueryWrapper<PosOrderConsumption>()
                .eq(PosOrderConsumption::getTenantId, tenantId)
                .eq(PosOrderConsumption::getOrderId, orderId));
Map<String, Boolean> keyDone = new HashMap<>();
for (PosOrderConsumption oc : exists) {
    keyDone.put(oc.getIdempotencyKey(), Boolean.TRUE);
}
```

**Layer 2 - DB 唯一约束** (init_rbac_v2_d.sql L38):
```sql
CREATE TABLE pos_order_consumption (
    ...
    idempotency_key VARCHAR(128) NOT NULL,
    UNIQUE KEY uk_idempotency (tenant_id, idempotency_key),
    ...
);
```

**Layer 3 - 异常捕获兜底** (InventoryConsumptionService L191-198):
```java
try {
    consumptionMapper.insert(oc);
} catch (DuplicateKeyException dup) {
    log.info("[consumeByOrder] dup-key caught, rollback this material only. orderId={}, productId={}",
            orderId, productId);
    throw new BusinessException(BizCode.IDEMPOTENT_REPLAY,
            "订单 " + order.getOrderNo() + " 的耗材已被并发扣减，请刷新页面");
}
```

**幂等键格式** (L206-208):
```java
private static String buildIdemKey(Long tenantId, Long orderId, Long serviceId, Object productId) {
    return "pos_order_consume:" + tenantId + ":" + orderId + ":" + serviceId + ":" + productId;
}
```

**Reality Status**: 🟢 REAL - 三层幂等完善

---

#### Q5. **并发** - 库存数量是否有竞态保护？

**答案**: 有，乐观锁 + 条件 CAS 双重保护。

**Evidence**:
```sql
-- init_rbac_v2_b.sql L44-47 (迁移加 version 字段)
ALTER TABLE stock_inventory
    ADD COLUMN version INT DEFAULT 0 NOT NULL COMMENT '乐观锁版本号' AFTER quantity;
```

```java
// StockInventoryMapper.casOutbound() L28-39
@Update("UPDATE stock_inventory " +
        "SET quantity = quantity - #{qty}, " +
        "    version = version + 1, " +
        "    last_out_time = CURRENT_TIMESTAMP, " +
        "    update_time = CURRENT_TIMESTAMP " +
        "WHERE id = #{id} " +
        "  AND version = #{version} " +    // ◀── 乐观锁
        "  AND quantity >= #{qty} " +      // ◀── 条件防止负库存
        "  AND deleted = 0")
```

**并发失败处理** (InventoryConsumptionService L151-155):
```java
int rows = inventoryMapper.casOutbound(inv.getId(), inv.getVersion(), thisBatchQty);
if (rows == 0) {
    throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
            "库存并发扣减失败：耗材 " + product.getProductName() + " 批次 " + inv.getBatchNo());
}
```

**测试覆盖** (`ConcurrencyIntegrationTest.java` L163-237 `inventoryNoOversell`):
- 10 线程并发各扣 20 件，库存 100 件
- 验证：最终库存 >= 0，剩余 = 100 - 成功次数 × 20
- ✅ 无超卖

**Reality Status**: 🟢 REAL - 乐观锁 + 条件保护完整

---

#### Q6. **数量计算** - 消耗数量从哪里来？

**答案**: 来自 BOM（BOM 中 quantity × 订单 item.quantity）。

**Evidence** (InventoryConsumptionService L100-112):
```java
for (PosOrderItem item : items) {
    if (item.getServiceId() == null) continue;
    int itemQty = item.getQuantity() == null ? 1 : item.getQuantity();
    List<Map<String, Object>> bom = bomMapper.listSimpleBom(item.getServiceId());
    if (bom.isEmpty()) continue;

    for (Map<String, Object> b : bom) {
        Long productId = ((Number) b.get("product_id")).longValue();
        BigDecimal qtyPerService = new BigDecimal(b.get("quantity").toString());
        int qty = qtyPerService.multiply(BigDecimal.valueOf(itemQty))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        if (qty <= 0) continue;
        // ...
    }
}
```

**示例数据** (init_rbac_v2_d.sql L67-77):
```sql
-- 示例：面部护理SPA 每次消耗玻尿酸精华液 0.5 瓶
INSERT INTO biz_service_material (quantity, unit) VALUES (0.5, '瓶');
INSERT INTO biz_service_material (quantity, unit) VALUES (1, '片');
```

→ 客户购买面部护理SPA 1 次 → 消耗 0.5 瓶（向上取整 = 1 瓶）+ 1 片

**Reality Status**: 🟢 REAL - 数量计算正确

---

#### Q7. **失败处理** - 库存不足 / 商品不存在如何处理？

**答案**: 抛 `BusinessException`，事务回滚。

**Evidence**:

**Case A - 商品不存在** (InventoryConsumptionService L120-124):
```java
StockProduct product = productMapper.selectById(productId);
if (product == null) {
    throw new BusinessException(BizCode.NOT_FOUND,
            "服务 " + item.getServiceName() + " 关联的耗材 id=" + productId + " 已删除");
}
```

**Case B - 库存为空** (L127-131):
```java
List<StockInventory> candidates = inventoryMapper.listFefoCandidates(productId, order.getStoreId());
if (candidates.isEmpty()) {
    throw new BusinessException(BizCode.STOCK_NOT_ENOUGH,
            "耗材「" + product.getProductName() + "」无库存，服务 " + item.getServiceName());
}
```

**Case C - 库存不够** (L140-143):
```java
if (remaining > 0) {
    throw new BusinessException(BizCode.STOCK_NOT_ENOUGH,
            "耗材「" + product.getProductName() + "」总库存不足，需要 " + qty + "，可用 " + (qty - remaining));
}
```

**Case D - CAS 失败** (L152-155):
```java
if (rows == 0) {
    throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
            "库存并发扣减失败：耗材 " + product.getProductName() + " 批次 " + inv.getBatchNo());
}
```

**BizCode 错误码** (BizCode.java):
```java
STOCK_NOT_ENOUGH(5301, "库存不足"),
CONCURRENCY_CONFLICT(409, "并发冲突，请重试"),
IDEMPOTENT_REPLAY(409, "幂等命中，已返回上次结果"),
NOT_FOUND(404, "资源不存在");
```

**Reality Status**: 🟢 REAL - 错误码 + 业务异常 + 事务回滚完善

---

#### Q8. **回滚** - 退款时是否还原库存？

**答案**: 是，PosRefundService.reverseStock() 完整实现。

**Evidence** (PosRefundService.java L449-520):
```java
private void reverseStock(PosRefund refund) {
    PosOrder order = orderMapper.selectById(refund.getOrderId());
    if (order == null) return;
    // 数据源 1：pos_order_consumption（新 R2.0-D 表）
    // 数据源 2：stock_record.type=4 AND order_id=?（兼容旧记录）
    // 收集 (productId, batchNo, qty) 列表
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT product_id, batch_no, SUM(quantity) AS qty " +
        "FROM stock_record " +
        "WHERE deleted = 0 AND type = 4 AND order_id = ? " +
        "GROUP BY product_id, batch_no",
        order.getId());
    for (Map<String, Object> row : rows) {
        // ... casAdjust(id, version, qty) 入库
        int rowsUpdated = inventoryMapper.casAdjust(inv.getId(), inv.getVersion(), qty);
        // 写 stock_record type=2 冲正入库
        StockRecord rec = new StockRecord();
        rec.setType(2);  // 2=出库；此处表示冲正入库
        rec.setQuantity(qty);
        rec.setRemark("refund:" + refund.getRefundNo() + " 冲正入库");
        rec.setOrderId(refund.getOrderId());
        stockRecordMapper.insert(rec);
    }
    // 把对应 pos_order_consumption 标 soft-deleted 防止再次扣减
    jdbc.update("UPDATE pos_order_consumption SET idempotency_key = CONCAT(idempotency_key, ':refund:', ?) " +
            "WHERE order_id = ?", refund.getRefundNo(), order.getId());
    refund.setReverseStock(1);
    refundMapper.updateById(refund);
    logReverse(refund, "STOCK", null, null);
}
```

**触发链**:
- PosRefundService.process() → handleReverse() → reverseStock()

**Reality Status**: 🟢 REAL - 回滚完整
**⚠️ 小瑕疵**: type=2 语义混淆（注释承认是"出库；此处表示冲正入库"）。建议改为 type=7 (退款入库) 或新增枚举值。

---

#### Q9. **BOM（物料清单）** - 是否支持多产品/服务-耗材关联？

**答案**: 是，支持多对多。

**Evidence**:

**BOM 表** (init_rbac_v2_d.sql L9-24):
```sql
CREATE TABLE biz_service_material (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    service_id BIGINT NOT NULL COMMENT '服务ID (biz_service.id)',
    product_id BIGINT NOT NULL COMMENT '耗材ID (stock_product.id)',
    quantity DECIMAL(10,2) NOT NULL DEFAULT 1,
    unit VARCHAR(16),
    is_required TINYINT DEFAULT 1,
    UNIQUE KEY uk_tenant_service_product (tenant_id, service_id, product_id),
    INDEX idx_service (service_id),
    INDEX idx_product (product_id)
);
```

**Mapper 多产品查询** (BizServiceMaterialMapper L18-32):
```java
@Select("SELECT product_id, quantity FROM biz_service_material WHERE deleted = 0 AND service_id = #{serviceId}")
List<Map<String, Object>> listSimpleBom(@Param("serviceId") Long serviceId);
```

**循环扣减** (InventoryConsumptionService L100-200):
```java
for (Map<String, Object> b : bom) {
    Long productId = ...;
    int qty = qtyPerService.multiply(BigDecimal.valueOf(itemQty))...;
    // ... 扣减、写流水、写幂等
}
```

**演示数据** (init_rbac_v2_d.sql L67-77):
- 服务 SVC-001（面部护理SPA）→ 产品 SP-BOT-001（玻尿酸精华液）0.5 瓶 + 产品 SP-MSK-002（美白补水面膜）1 片
- 一次服务扣 2 种耗材 ✅

**Reality Status**: 🟢 REAL - 多产品 BOM 完整

---

#### Q10. **可追溯性** - 谁、何时、何门店、何订单？

**答案**: 全部字段记录。

**Evidence** (StockRecord L158-176):
```java
StockRecord rec = new StockRecord();
rec.setTenantId(tenantId);
rec.setStoreId(order.getStoreId());        // ◀── 门店
rec.setProductId(productId);
rec.setBatchNo(inv.getBatchNo());
rec.setType(4);                            // ◀── 领用
rec.setQuantity(-thisBatchQty);
rec.setBeforeQty(inv.getQuantity());       // ◀── 操作前库存
rec.setAfterQty(inv.getQuantity() - thisBatchQty);  // ◀── 操作后库存
rec.setUnitPrice(product.getCostPrice());
rec.setTotalAmount(product.getCostPrice().multiply(BigDecimal.valueOf(thisBatchQty)));
rec.setOrderId(orderId);                   // ◀── 关联订单
rec.setOperatorId(order.getCashierId() == null ? 1L : order.getCashierId());  // ◀── 谁
rec.setOperatorName(order.getCashierName() == null ? "system" : order.getCashierName());  // ◀── 谁
rec.setRemark("订单支付自动扣减：" + order.getOrderNo() + ...);
rec.setRecordTime(LocalDateTime.now());    // ◀── 何时
recordMapper.insert(rec);
```

**PosOrderConsumption 也记录**:
```java
oc.setTenantId(tenantId);
oc.setOrderId(orderId);
oc.setProductId(productId);
oc.setServiceId(item.getServiceId());
oc.setQuantity(originalRemain);
oc.setIdempotencyKey(idemKey);
oc.setStockRecordId(records.get(0).getId());
```

**Reality Status**: 🟢 REAL - 5W (Who/What/When/Where/Which-order) 全记录

---

#### Q11. **测试覆盖** - 是否有自动化测试？

**答案**: 有部分覆盖，但**没有专门的 `InventoryConsumptionService.consumeByOrder()` 单测/集成测试**。

**Evidence**:

**已有测试文件**:
- `ConcurrencyIntegrationTest.java` (248 行) - **有库存 CAS 测试**:
  ```java
  @Test @Order(3)
  @DisplayName("库存并发出库：10并发各扣20件（需求200），实际库存100，最终>=0，无超卖")
  void inventoryNoOversell() throws Exception { ... }
  ```
  测试了 `StockInventoryMapper.casOutbound()` 直接调用，**但未走 `InventoryConsumptionService.consumeByOrder()` 的完整路径**。

**缺失测试**:
- ❌ `consumeByOrder()` 单测：未测试 BOM 解析、FEFO、幂等、多产品
- ❌ `consumeByOrder()` 集成测试：未测试与 PosController.pay() 的串联
- ❌ 退款回滚库存测试：未测试 PosRefundService.reverseStock()

**Reality Status**: 🟡 PARTIAL - 单元覆盖薄弱

---

#### Q12. **REAL Status 综合**

| 子项 | Status |
|------|--------|
| 触发点 | 🟢 REAL |
| 三表写入 | 🟢 REAL |
| 事务边界 | 🟢 REAL |
| 幂等保护 | 🟢 REAL（三层） |
| 并发保护 | 🟢 REAL（乐观锁+CAS） |
| 数量计算 | 🟢 REAL（BOM×itemQty） |
| 失败处理 | � REAL（4 类异常码） |
| 回滚 | 🟢 REAL（reverseStock） |
| BOM 多产品 | 🟢 REAL |
| 可追溯性 | 🟢 REAL |
| 测试覆盖 | 🟡 PARTIAL |
| **Overall 库存消耗路径** | **🟢 REAL（92%）** |

**优点**:
1. 事务语义"订单与库存同生共死" 严格保证
2. 三层幂等（应用缓存 + DB UNIQUE + 异常捕获）
3. 乐观锁 + quantity>=? 双重防超卖
4. 退款回滚完整，含流水记录

**待改进**:
1. �️ 缺少 `consumeByOrder()` 单元测试
2. ⚠️ 退款时 stock_record.type=2 语义不清晰（应新增 type=7=退款入库）
3. ⚠️ "服务完成"和"订单支付"混淆（理想应拆为两阶段：下单锁定、签到扣减）

---

## 二、黄金路径 2：会员 → 预约 → 签到 → 服务 → 支付 → 提成

### 2.1 调用链分析

```text
[会员] → /booking  POST                 BookingController.create()
        → bookingService.save()
        → DB: biz_booking (status=1 待确认)        �── ⚠️ 表不存在！

[预约确认] → /booking/{id}/status PUT    BookingController.changeStatus()
            → bookingService.updateById()
            → DB: status=2

[签到] → /booking/{id}/checkin POST     BookingController.checkin()
        → DB: status=3, checkinTime=NOW         ◀── ⚠️ 表不存在！

[完成服务] → /booking/{id}/complete POST  BookingController.complete()
            → DB: status=4, completeTime=NOW    ◀── ⚠️ 表不存在！
            → ⚠️ 此处未触发任何库存扣减或提成计算（与黄金路径 1 分离）

[支付订单] → /pos/orders/{id}/pay POST   PosController.pay()
            → 触发库存扣减 + 提成计算              (见 黄金路径 1)
```

### 2.2 Reality Status

| 子路径 | 状态 | 备注 |
|--------|------|------|
| 会员预约创建 | 🔴 MISSING | biz_booking 表不存在 |
| 预约状态机变更 | 🔴 MISSING | 同上 |
| 签到/完成 | 🔴 MISSING | 同上 |
| 服务完成 → 支付 | 🟢 REAL | PosController.pay() 完整 |
| 支付 → 提成 | 🟢 REAL | CommissionCalculationService.calculateOrder() 实现 |
| **Overall 路径** | **🟡 PARTIAL** | 表缺失阻塞首段 |

**�️ 关键缺陷**: 服务"完成" (`/booking/{id}/complete`) 不会触发库存扣减或提成计算，必须再到 POS 收银台付款。这意味着：
- 提成计算依赖收银台付款动作，而不是实际服务交付
- 库存扣减只跟"付款"绑定，不跟"做服务"绑定

---

## 三、黄金路径 3：会员 → 购买套餐 → 核销

### 3.1 调用链分析

```text
[购买套餐] → PosController.createOrder() 创建订单 (type=2 充值 或 1 消费)
            + 添加 order_item (packageOrderId + packageTimes)
            + PosController.pay() 完成支付
            + 同时 INSERT INTO crm_package_order + crm_package_redemption ???

[核销套餐] → PosController.processOnePayment("package")
            → redeemPackage(order, cardId)
              → jdbc.update("UPDATE crm_package_order SET remaining_times=... WHERE id=?")
              → jdbc.update("INSERT INTO crm_package_redemption ...")
```

### 3.2 Evidence（PosController.java L303-361）：

```java
case "package" -> {
    Long cardId = it.getMemberCardId();
    if (cardId == null) {
        // 自动取订单中第一个有 packageOrderId 的明细对应的套餐卡
        List<PosOrderItem> orderItems = itemMapper.selectList(
                new LambdaQueryWrapper<PosOrderItem>().eq(PosOrderItem::getOrderId, order.getId()));
        PosOrderItem pkgItem = orderItems.stream()
                .filter(x -> x.getPackageOrderId() != null).findFirst().orElse(null);
        if (pkgItem == null) throw new RuntimeException("订单未关联套餐，无法核销");
        cardId = pkgItem.getPackageOrderId();
    }
    redeemPackage(order, cardId);
    p.setAmount(BigDecimal.ZERO); // 次卡本身免费
}
```

```java
private void redeemPackage(PosOrder order, Long packageOrderId) {
    Map<String, Object> card = jdbc.queryForMap(
            "SELECT id, member_id, remaining_times, status FROM crm_package_order WHERE id=? AND deleted=0",
            packageOrderId);
    if (card == null) throw new RuntimeException("套餐卡不存在");
    int memberIdOfCard = ((Number) card.get("member_id")).intValue();
    if (order.getMemberId() != null && order.getMemberId().intValue() != memberIdOfCard) {
        throw new RuntimeException("套餐卡不属于该会员");
    }
    int remaining = ((Number) card.get("remaining_times")).intValue();
    if (remaining <= 0) throw new RuntimeException("套餐次数已用完");

    int updated = jdbc.update(
            "UPDATE crm_package_order SET remaining_times = remaining_times - 1, update_time = NOW() " +
            "WHERE id = ? AND remaining_times > 0",
            packageOrderId);
    if (updated == 0) throw new RuntimeException("套餐扣减失败");

    jdbc.update(
            "INSERT INTO crm_package_redemption(order_id, member_id, package_id, times, service_item, operator_id, redeem_time) " +
            "VALUES(?, ?, (SELECT package_id FROM crm_package_order WHERE id=?), 1, ?, ?, NOW())",
            packageOrderId, memberIdOfCard, packageOrderId,
            "POS 订单 " + order.getOrderNo(), order.getCashierId());
}
```

### 3.3 Reality Status

| 子路径 | 状态 | 备注 |
|--------|------|------|
| 套餐订单表 | � REAL | crm_package_order + version CAS |
| 套餐购买记录 | 🟢 REAL | INSERT 完整 |
| 套餐核销记录 | 🟢 REAL | INSERT 完整 |
| 核销次数 CAS | 🟡 PARTIAL | 使用 `remaining_times > 0` 但**没用 version**，并发可能扣成负数 |
| 购买端（创建套餐订单） | � PARTIAL | 演示数据靠 INSERT，业务上需要专门的购买接口 |
| **Overall 路径** | **🟡 PARTIAL** | 缺专门"购买套餐"接口，CAS 不用 version |

**⚠️ 缺陷**:
- `redeemPackage()` 用 `jdbc.update(... WHERE remaining_times > 0)` 而**没有用 `version`**，与 R2.0-B 设计的乐观锁方案不一致
- 套餐"购买"接口缺失：现有 demo 数据靠 SQL 手工 INSERT

**应该使用** (PackageOrderMapper.casRedeem L23-26):
```java
@Update("UPDATE crm_package_order SET remaining_times = remaining_times - #{times}, " +
        "    version = version + 1, status = CASE WHEN ... ELSE status END, " +
        "WHERE id = #{id} AND version = #{version} AND remaining_times >= #{times} " +
        "    AND status NOT IN (3,4,5,6)")
```

---

## 四、黄金路径 4：会员 → 钱包充值 → 订单支付 → 退款

### 4.1 调用链

```text
[钱包充值] → ? (没有专门的 WalletController.recharge 端点)
            PosController 仅写 crm_wallet_transaction 但不调 WalletMapper

[订单支付-钱包] → PosController.processOnePayment("wallet")
                 → jdbc.update("INSERT INTO crm_wallet_transaction ...")
                 → memberService.updateById(m) [扣减 member.balance]
                 ◀── 注意：扣减的是 crm_member.balance，不是 crm_wallet.balance

[退款-钱包] → PosRefundService.refundToWallet()
             → walletMapper.updateBalance() [CAS]
             → jdbc.update("INSERT INTO crm_wallet_transaction ...")
```

### 4.2 Evidence（PosController.java L286-302）：

```java
case "wallet" -> {
    if (order.getMemberId() == null) throw new RuntimeException("非会员不能用储值支付");
    Member m = memberService.getById(order.getMemberId());
    if (m == null || m.getBalance() == null || m.getBalance().compareTo(amount) < 0) {
        throw new RuntimeException("会员余额不足");
    }
    BigDecimal before = m.getBalance();
    BigDecimal after = before.subtract(amount);
    m.setBalance(after);
    m.setTotalConsume(m.getTotalConsume() == null ? amount : m.getTotalConsume().add(amount));
    memberService.updateById(m);   // ◀── 这里用 member.balance 而非 crm_wallet
    jdbc.update(
        "INSERT INTO crm_wallet_transaction(tenant_id, member_id, type, amount, balance_before, balance_after, pay_method, order_id, operator_id, remark, created_at) " +
        "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        1L, m.getId(), 2, amount, before, after, "wallet", order.getId(), order.getCashierId(),
        "订单消费:" + order.getOrderNo(), LocalDateTime.now());
}
```

### 4.3 Reality Status

| 子路径 | 状态 | 备注 |
|--------|------|------|
| crm_wallet 表 | 🔴 MISSING | **表完全不存在** |
| crm_wallet_transaction 表 | 🔴 MISSING | **表完全不存在** |
| 钱包充值接口 | 🟠 SHELL | WalletServiceImpl 仅继承 ServiceImpl |
| 钱包支付扣减 | 🟡 PARTIAL | 扣的是 `crm_member.balance`（而非 `crm_wallet.balance`） |
| 钱包流水写入 | 🔴 MISSING | INSERT 会失败（表不存在） |
| 钱包退款入账 | 🔴 MISSING | walletMapper.updateBalance() 会失败 |
| 钱包乐观锁 | ❌ N/A | 表不存在 |
| **Overall 钱包路径** | **🔴 MISSING** | **核心表全部缺失** |

**⚠️ 严重缺陷**:
- `crm_wallet` 和 `crm_wallet_transaction` 两个表在任何 SQL 文件都不存在
- 但代码（PosController.pay、PosRefundService.refundToWallet、WalletMapper）到处调用
- 任何钱包支付 / 退款操作都会抛 `TableNotFoundException`

**🔴 设计不一致**:
- 支付时扣 `crm_member.balance`
- 退款时更新 `crm_wallet.balance` (WalletMapper)
- 两套不同步的钱包实现并存

---

## 五、黄金路径综合 Reality Matrix

| 黄金路径 | 完整度 | 主要问题 | Verdict |
|---------|--------|---------|---------|
| **服务完成 → 库存扣减** | 92% | 缺单测、type=2 语义混淆 | 🟢 REAL |
| **会员 → 预约 → 签到 → 完成 → 支付** | 65% | biz_booking 表不存在 | 🟡 PARTIAL |
| **会员 → 购买套餐 → 核销** | 70% | CAS 不用 version、缺购买接口 | 🟡 PARTIAL |
| **会员 → 钱包充值 → 订单支付 → 退款** | 30% | crm_wallet/transaction 表不存在 | 🔴 MISSING |
| **Overall Golden Path Reality** | **64%** | | **🟡 PARTIAL** |

---

## 六、P0 阻塞清单（上线前必须修复）

| # | 路径 | 问题 | 修复方案 |
|---|------|------|---------|
| 1 | 钱包 | crm_wallet / crm_wallet_transaction 表不存在 | 加 `init_m04_wallet.sql` |
| 2 | 预约 | biz_booking / biz_room / biz_employee_schedule 表不存在 | 加 `init_m06_full.sql` |
| 3 | 库存测试 | 缺 `consumeByOrder` 单测/集成测试 | 加 `InventoryConsumptionServiceTest.java` |
| 4 | 套餐核销 | `redeemPackage` 未用 `version` 乐观锁 | 改为 `packageOrderMapper.casRedeem()` |
| 5 | 设计不一致 | 钱包同时存于 `crm_member.balance` 和 `crm_wallet.balance` | 二选一，统一 |
| 6 | 退款语义 | stock_record.type=2 同时表示出库与冲正入库 | 新增 type=7=退款入库 |
| 7 | 服务完成 | /booking/complete 未触发库存扣减或提成 | 与 PosController.pay() 联动 |

---

## 七、P1 改进建议

1. 引入 `OrderPaidEvent` 监听器（已有事件但无消费者）
2. 拆分"下单锁定库存"vs"签到扣减库存"
3. 套餐"购买"专用接口（不依赖手工 SQL）
4. 库存预警补货建议接口（T-D04）
5. 多店会员共享积分/储值的跨店核销支持

---

## 八、Reality Score 总览

| 路径 | 现状 | 目标 |
|------|------|------|
| 库存消耗路径（核心） | 🟢 92% | 100% |
| 预约 → 支付 → 提成 | 🟡 65% | 90% |
| 套餐购买 → 核销 | 🟡 70% | 90% |
| 钱包 → 支付 → 退款 | 🔴 30% | 90% |
| **Overall** | **🟡 64%** | **90%** |

> **审计结论**: 项目**核心库存消耗链路已落地**，但其他三大黄金路径有严重阻塞（钱包表、预约表全缺失）。建议按 P0 优先级修复 7 个问题后再进入生产验证阶段。
