# F076 EcommerceService Sync — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：I034（rollbackFor 默认 RuntimeException 沿用 I009 模式）+ I029 PARTIAL-MISSING

---

## 1. 实证结论

**状态**：**PARTIAL-NON-BLOCKER**（双端点 500 数据库异常 + I034 + I029 暴露）

| 端点 | 期望 | 实际 | 状态 |
|---|---|---|---|
| **POST /ecommerce/product/sync/1** | 同步商品 | **500**（数据库异常）| ⚠️ 阻断 |
| **POST /ecommerce/order/sync/1** | 同步订单 | **500**（数据库异常）| ⚠️ 阻断 |

## 2. 实证证据

### 2.1 product/sync/1 500

```bash
POST /ecommerce/product/sync/1
Response: code=500 message="数据库异常"
```

### 2.2 order/sync/1 500

```bash
POST /ecommerce/order/sync/1
Response: code=500 message="数据库异常"
```

→ **同 F075 GET 500**：MyBatis-Plus selectList(platform) 触发 Unknown column 'app_id'（I029 PARTIAL-MISSING 阻断）

## 3. 静态层（@Transactional 配置）

`backend/src/main/java/com/meiyetec/service/EcommerceService.java`：

| 方法 | 行号 | @Transactional | rollbackFor |
|---|---|---|---|
| `syncProducts` | L97 | ✅ | 默认（RuntimeException）|
| `syncOrders` | L213 | ✅ | 默认（RuntimeException）|

**I034 沿用 I009 模式**：rollbackFor 未指定 Exception，checked exception 不会触发回滚。
D17 决策：维持 PARTIAL-NON-BLOCKER，不加 rollbackFor。

## 4. 副作用

按 plan §一 §二 §5.2 预测，F076 调用会触发 I029 暴露 → 实测 PASS。

但 Mock fallback 设计意图（同 F069/F070）也未能验证：MockEcommerceAdapter 走 productMapper.selectList 时也会触发同 Unknown column 异常，因为 router 返回 adapter 前已经触发了 platform SELECT。

→ **F076 完整链路需要 F066 I029 修复后才能验证**

## 5. Issue 状态

| Issue | 关联 | 状态 |
|---|---|---|
| **I029** | PARTIAL-MISSING 列缺失 | 维持 |
| **I034** | rollbackFor 默认 | 维持（D17 决策） |

## 6. Phase 7 决策建议

Post-Audit 阶段：
1. 跑 `migrate_ext_22_google_maps_place_details.sql` 修复 I029
2. 重跑 F075 → 期望 GET /list 200
3. 重跑 F076 → 期望 sync 调用走 Mock fallback，返 200 + syncCount

## 7. 证据文件

- `raw-product-sync.txt` — product/sync/1 500 响应
- `raw-order-sync.txt` — order/sync/1 500 响应
