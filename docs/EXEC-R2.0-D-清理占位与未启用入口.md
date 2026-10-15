# R2.0-D：清理占位与未启用入口

> 范围：库存消耗从订单事件自动写库、Excel 导出（租户水印 + 审计）、批量导入（幂等）、补货建议。

## 1. 占位页面清理

| 页面 | 原状态 | 处理 |
|---|---|---|
| `stock/consumption.vue` | `featureEnabled=false` 占位 | 接入 `stockApi.consumptionPage` + `consumptionOverview` + `consumptionByOrder` + 导出按钮 |
| `stock/alert.vue` | `featureEnabled=false` 占位 | 接入 `alertReplenish` + `replenishExport` + 替换"补货建议开发中"提示 |
| `stock/stats.vue` | `featureEnabled=false` 占位 | 已具备 v-else 真实逻辑，无需修改 |

## 2. 库存消耗 → 订单事件自动写库（C2-D1）

### 数据流
```
订单支付成功(status=2)
       ↓
PosController.pay()  [R2.0-D 同步调用，确保原子性]
       ↓
InventoryConsumptionService.consumeByOrder(orderId)
       ↓
① 拉订单明细 pos_order_item
② 每个 serviceId 查 biz_service_material（BOM）
③ 计算消耗量 = 单次 BOM 数量 × 明细数量
④ FEFO 选批次 → casOutbound 扣库存
⑤ 写 stock_record(type=4 领用)
⑥ 写 pos_order_consumption(idempotency_key = pos_order_consume:{tenantId}:{orderId}:{serviceId}:{productId})
       ↓
发布 OrderPaidEvent [R2.0-D 扩展点]
       ↓
（异步 listener：报表/统计等非关键副作用消费；事务回滚则事件不投递）
```

### 同步调用 vs 异步事件的设计权衡
- 当前实装：**库存消耗走同步调用**（保留事务原子性，库存不足时订单回滚）
- 扩展点：定义 `OrderPaidEvent` 事件，发射但不强制库存依赖；监听者可注册 `@TransactionalEventListener(phase = AFTER_COMMIT)`
- 升级路径：未来若库存改为自动补给策略，再把库存消耗挪到 AFTER_COMMIT listener

### 幂等设计
- `pos_order_consumption` 表 UNIQUE(tenant_id, idempotency_key) 兜底
- 同订单二次支付不会重复扣减
- `DuplicateKeyException` → 返回 `IDEMPOTENT_REPLAY`

### 退款逆向
- `PosRefundService.reverseStock` 反向冲正不再依赖独立消耗表，而是从 `stock_record.type=4 AND order_id=?` 解析后回加 `casInbound`（库存快照法）；同时把 `pos_order_consumption.idempotency_key` 追加 `:refund:{refundNo}` 软标记，避免二次扣减

## 3. 补货建议（C2-D2）

公式：`suggestQty = max(0, max(avgDaily × safetyDays, minStock) - stockQty)`
紧急度：
- `URGENT`: stock ≤ 0 或 stock < avgDaily × 3
- `HIGH`: stock < minStock
- `MEDIUM`: stock < safetyTarget
- `LOW`: 正常

接口：`GET /stock/alert/replenish?days=30&safetyDays=14`

R2.0-D T-D04 升级：一键转采购入库
- 前端 `alert.vue` 的"入库"按钮调用 `stockApi.inbound({...recordType:6})`
- 后端 `StockController.inbound` 接受 body.recordType（1=普通入库 / 6=采购入库），写入 stock_record.type 区分来源
- stock_record.type 枚举：`1入库 2出库 3盘点 4领用 5报损 6采购入库`

## 4. 导出 + 租户水印 + 审计（C2-D3）

文件格式：`.xlsx`（Apache POI SXSSFWorkbook，UTF-8 单 sheet）
- 行 1：合并 A:H 加粗水印 `# 标题 · 租户：xxx · 生成时间：yyyy-MM-dd HH:mm:ss · 仅供内部使用`
- 行 2：表头加粗 + 灰底
- 行 3~N：数据

响应头：
- `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- `Content-Disposition: attachment; filename="<title>_<date>.xlsx"`
- `X-Tenant-Name: xxx`（前端可读取做预览）

权限与审计：
- 所有端点必须带 `@RequirePermission` 注解
- `Content-Disposition` 含中文走 ISO-8859-1 编码转义
- 类级 `@SysOpLog(module = "库存")` 自动写 `sys_op_log`，作为审计兜底

| 导出 | 端点 | 水印 | 审计 |
|---|---|---|---|
| 库存清单 | `GET /stock/inventory/export` | ✅ | ✅ (@RequirePermission("stock:export")) |
| 出入库流水 | `GET /stock/record/export` | ✅ | ✅ |
| 盘点记录 | `GET /stock/check/export` | ✅ | ✅ |
| 消耗记录 | `GET /stock/consumption/export` | ✅ | ✅ |
| 补货建议 | `GET /stock/alert/replenish/export` | ✅ | ✅ (@RequirePermission("stock:replenish")) |

## 5. 商品批量导入 + 幂等（C2-D4）

### 端点
- `GET /stock/products/import-template` → CSV 模板下载
- `POST /stock/products/import` (Body: `{mode, items}`, Header: `Idempotency-Key`)
  - `mode`: UPSERT（默认）/ INSERT
  - 幂等：依赖 `IdempotencyService` + `sys_idempotency_record` 通用幂等框架，同 key 重放同结果

### 返回结构
```json
{
  "code": 0,
  "data": {
    "total": 10,
    "inserted": 7,
    "updated": 3,
    "skipped": 0,
    "errorCount": 0,
    "errors": [],
    "codes": ["SP-001", "SP-002", ...]
  }
}
```

### 前端 UX
- 顶部工具栏 3 个按钮：`批量导入` / `下载模板` / `导出`
- 导入弹窗支持 CSV 文本粘贴 与 JSON 两种格式
- 解析后展示总览 + 错误列表（行号 + 原因）

## 6. SQL 变更

文件: `init_rbac_v2_d.sql`

```sql
-- 1. BOM 表
biz_service_material (id, tenant_id, service_id, product_id, quantity, unit, is_required, remark)

-- 2. 订单消耗幂等表
pos_order_consumption (id, tenant_id, order_id, stock_record_id, product_id, service_id, quantity, idempotency_key)

-- 3. 权限码
'stock:product:import' - 商品批量导入
'stock:replenish'      - 补货建议
'stock:export'         - 库存导出

-- 4. 演示 BOM
SVC-001 面部护理SPA → SP-BOT-001 玻尿酸精华液 0.5 瓶
SVC-001 面部护理SPA → SP-MSK-002 美白补水面膜 1 片
```

## 7. 验收清单

- [ ] 库存消耗页 SQL 验证：订单支付后 `stock_record` 出现 type=4 行
- [ ] 库存消耗页 SQL 验证：重复进入 `consumeByOrder` 不会重复扣减
- [ ] 退款时 `stock_record` 出现 type=2 冲正入库行
- [ ] 补货建议端点返回 `URGENT/HIGH/MEDIUM/LOW` 优先级排序
- [ ] 5 个导出端点 CSV 头部均有 `# 标题 · 租户：xxx` 行
- [ ] 批量导入同一 `Idempotency-Key` 重放返回相同结果
