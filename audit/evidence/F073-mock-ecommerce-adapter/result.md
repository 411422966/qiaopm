# F073 MockEcommerceAdapter — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification

---

## 1. 实证结论

**状态**：**MOCK-INTENTIONAL**（user decision #1 — 凭据缺失时 Mock 降级）

## 2. 静态证据

`backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java` 全文 60 行：

| 维度 | 内容 |
|---|---|
| **激活条件** | `@ConditionalOnMissingBean(name = "youzanEcommerceAdapter")` L21 |
| **platformCode** | "MOCK" |
| **商品样本** | 3 条（焕颜保湿面膜/头皮舒缓精华/经典美甲护理）|
| **订单样本** | 2 条（WAIT_SELLER_SEND_GOODS / TRADE_SUCCESS）|
| **authenticate** | sleep(50) + 返 "MOCK-TOKEN-{UUID}"，expiresIn=7200 |
| **fetchProducts** | sleep(80) + 返样本 |
| **fetchOrders** | sleep(80) + 返样本 |
| **healthCheck** | healthy=true + stage="MOCK_OK" |

## 3. 行为合规

- ✅ **不抛异常**：所有调用返正常结果
- ✅ **业务可观察**：sample 数据让前端能渲染表格
- ✅ **不阻塞排程**：避免无凭据时 cron 卡死

## 4. 与 CrmMockAdapter 同源

| 维度 | MockCrmAdapter | MockEcommerceAdapter |
|---|---|---|
| 触发条件 | @ConditionalOnMissingBean | @ConditionalOnMissingBean |
| 平台代码 | "MOCK" | "MOCK" |
| 样本数 | 3 + 3 | 3 + 2 |
| sleep | 50/80ms | 50/80ms |

## 5. Issue 状态

无新增 Issue。F073 设计合规，沿用 user decision #1。
