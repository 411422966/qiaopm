# F070 YouzanEcommerceAdapter.fetchProducts/fetchOrders/healthCheck — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification

---

## 1. 实证结论

**状态**：**UNVERIFIED**（D10 铁律 — 真实有赞 API 凭据缺失，Mock fallback 维持）

## 2. 静态证据

`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java`：

| 方法 | 行号 | 行为 |
|---|---|---|
| `fetchProducts` | L233-262 | kdtapi `/items/list` + 鉴权 + 解析 `data.items[]` |
| `fetchOrders` | L266-303 | kdtapi `/trades/sold_get` + 时间窗 + 解析 `data.trades[]` |
| `healthCheck` | L307-318 | 鉴权 + 返 HealthStatus |

| 维度 | 内容 |
|---|---|
| **鉴权联动** | 每次 fetch 前先 `authenticate()` |
| **失败降级** | 鉴权失败 → 返回空 page（不抛异常） |
| **限流** | `Math.min(pageSize, 100)` |
| **超时** | connect 5s / read 15s |
| **降级 fallback** | 走 `MockEcommerceAdapter`（见 F073）|

## 3. Mock fallback 行为

用户无 appKey/appSecret 时 → `EcommerceAdapterRouter.getAdapter("YOUZAN")` 返回 MockEcommerceAdapter：
- 3 商品样本 + 2 订单样本
- 50-80ms 模拟 sleep
- → **F070 实际运行时走的是 Mock 路径**，不影响业务阻塞

## 4. Issue 状态

无新增 Issue。F070 静态层 + Mock 路径完整。
