# I122 — payment_webhook_log 表不存在，支付回调必 500（P0）

## 现象
- `PaymentWebhookLogServiceImpl.processWebhook` 有完整幂等逻辑（验签 → 解析 eventId → 查重 → 处理）
- 但 `payment_webhook_log` 表在 DB 中不存在（`ERROR 1146`）
- 调用 `POST /overseas-payment/webhook/{gatewayCode}` 时，`webhookLogMapper.insert(logRecord)` 直接 500

## 影响
- 境外支付（Stripe / Square / PayPal）回调全部失败
- 即使回调失败，支付网关会重试，但每次重试都 500，最终可能触发网关熔断/告警
- 与 I118 同类：Entity + Mapper + Service 齐全，DDL 缺失

## 定位
- Entity: `com.meiyetec.entity.PaymentWebhookLog`
- Mapper: `PaymentWebhookLogMapper`
- Service: `PaymentWebhookLogServiceImpl:36`
- Controller: `PaymentWebhookController`（`/overseas-payment/webhook/{gatewayCode}`）

## 幂等逻辑（已实现，表补后即生效）
1. 验签 `adapter.verifyWebhookSignature`
2. 解析 `adapter.parseWebhookEvent` → 拿 eventId
3. `webhookLogMapper.findByEventId(eventId)` 查重
4. 已存在 → 标记 `skipped` 返回
5. 不存在 → `handleEvent` + 标记 `processed`

## 处置
- [ ] 补 `payment_webhook_log` 建表脚本（参考 Entity 字段 + 索引）
- [ ] 补 `findByEventId` 的 DB 索引（`gateway_event_id` 或类似字段）
- [ ] 建议加 `UNIQUE(gateway_event_id, gateway_code)` 双保险
- [ ] 归档到 `db/sql/` 并执行
- [ ] 运行时验证：模拟回调 → 表有记录 + 重复回调返回 skipped

## 关联
- 来源：P0-03 收银核验
- 同类：I118（132 张表 DDL 缺失）

## 状态
待办
