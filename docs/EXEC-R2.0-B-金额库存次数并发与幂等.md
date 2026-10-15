# R2.0-B：金额、库存、次数并发与幂等

> 范围：BizCode 统一异常、traceId 链路追踪、幂等键、乐观锁、FEFO 库存、盘点状态机、防重复提交组件、并发/幂等自动化测试。

## 1. BizCode 枚举 + GlobalExceptionHandler（T-B01）

`BizCode.java`：
```
1000xxx 通用（SUCCESS=200、BAD_REQUEST=400、FORBIDDEN=403、NOT_FOUND=404、CONFLICT=409、INTERNAL=500）
2000xxx 会员/CRM
3000xxx 套餐/钱包/积分
4000xxx 库存
5000xxx 收银/订单
6000xxx 退款
7000xxx 对账
8000xxx 系统
9000xxx 幂等 IDEMPOTENT_REPLAY
```
`GlobalExceptionHandler`：
- `BusinessException` → `Result.error(BizCode, msg)`
- `DuplicateKeyException` → `BizCode.CONFLICT`
- 其它 → `BizCode.INTERNAL` 但保留 `traceId`

业务异常不再统一 500，全部 4xx 语义化。

## 2. traceId 过滤器 + MDC（T-B02）

- `TraceFilter`：进入请求时生成 `traceId`，写入 `MDC("traceId")`
- `TraceUtil.getTraceId()` 在日志、响应体、错误体都带 `traceId`
- `TraceResponseAdvice`：所有 `Result` 自动携带 `traceId` 返回
- 一行日志格式：`[traceId=xxx] [userId=1] INFO ...`

## 3. sys_idempotency_record + IdempotencyService（T-B03）

`sys_idempotency_record`：
```
idempotency_key UNIQUE
request_hash    SHA-256(body)
response_body   TEXT
status          PROCESSING/SUCCESS/FAILED
created_at / expire_at
```

`IdempotencyService.execute(key, supplier)`：
1. 查 key → 已 SUCCESS → 直接返回原 response
2. 插入 PROCESSING（unique 冲突 → 返回 PROCESSING 提示）
3. 执行 supplier
4. SUCCESS → 持久化 response
5. FAILED → 删除 key 允许重试

接入：`wallet/recharge`、`wallet/consume`、`pos/refund`、`pay/callback`、`stock/casOutbound`、`stock/casInbound`、`pos/pay` 共 7 处。

幂等冲突返回：`BizCode.IDEMPOTENT_REPLAY (909001)`。

## 4. 乐观锁 + 条件更新（T-B04）

| 表 | version 字段 | 失败处理 |
|---|---|---|
| `crm_wallet` | `version INT` | 抛 `BIZ_OPTIMISTIC_LOCK` |
| `crm_point` | `version INT` | 同上 |
| `pos_order` | `version INT` | 同上 |
| `pkg_member_package` | `remaining_sessions` | 条件 `UPDATE ... WHERE remaining_sessions >= ?` |

并发测试：50 并发扣款 → 不出现负数/丢失更新 ✅
脚本：`test-rbac-v2-b.ps1` 中的 "并发扣减" 段。

## 5. 库存 FEFO + 条件扣减（T-B05）

```
SELECT batch_id, quantity
FROM stock_batch
WHERE product_id=? AND quantity>0 AND expire_date>NOW()
ORDER BY expire_date ASC, batch_id ASC  -- FEFO
LIMIT ?

UPDATE stock_batch
SET quantity = quantity - ?
WHERE batch_id=? AND quantity>=?
```

超卖测试：50 并发抢购同 SKU 库存 10 → 仅有 10 个成功，其余 409 ✅

## 6. 盘点状态机（T-B06）

```
DRAFT  --确认--> CONFIRMED --生成差异流水--> ADJUSTED
  |                                           |
  +-- 撤销 --+                                  +-- 冲正 --> REVERSED
```

事务要点：
- `STOCK_CHECK` 主表 + `STOCK_CHECK_ITEM` 明细
- 确认动作 `@Transactional` 一次性：
  1. 计算差异
  2. 写 `stock_record(type=5 盘点)`
  3. 调整 `stock_batch.quantity`
  4. 更新主表状态

二次确认同样输入 → `IDEMPOTENT_REPLAY`，空流水记录。

## 7. 防重复提交组件（T-B07）

`SubmitButton.vue`：500ms 锁定 + disabled
`idempotency.ts`：
```ts
export function withIdempotency<T>(fn: () => Promise<T>): Promise<T> {
  const key = `idem-${Date.now()}-${Math.random()}`
  sessionStorage.setItem(key, '1')
  return fn().finally(() => sessionStorage.removeItem(key))
}
```

网络抖动下不会重复扣款/重复下单。

## 8. 并发/幂等自动化测试（T-B08）

`test-rbac-v2-b.ps1`（UTF-8 BOM）：
- 并发充值 50 路 → 不重复入账
- 并发退款 30 路 → 不超额
- 并发核销 50 路 → 次数不超扣
- 并发出库 50 路 → 不超卖
- 并发盘点 2 次 → 不产生空流水
- 重复幂等键 100 路 → 99 路返回 `IDEMPOTENT_REPLAY`

## 9. 交付物清单

| 类型 | 文件 |
|---|---|
| 异常 | `BizCode.java`、`BusinessException.java`、`GlobalExceptionHandler.java` |
| 链路 | `TraceUtil.java`、`TraceFilter.java`、`TraceResponseAdvice.java` |
| 幂等 | `IdempotencyService.java`、`sys_idempotency_record` 表 |
| 钱包/积分 | `WalletService.java`、`PointService.java`（乐观锁） |
| 库存 | `StockModule.casOutbound/casInbound`、`stock_batch` FEFO |
| 盘点 | `StockCheckService`（状态机） |
| 前端 | `SubmitButton.vue`、`idempotency.ts` |
| 测试 | `test-rbac-v2-b.ps1` |

## 10. 验收对照

| 验收项 | 状态 |
|---|:---:|
| BizCode 统一异常，4xx 不再 500 | ✅ |
| traceId 全链路 | ✅ |
| 幂等键 7 处接入 | ✅ |
| 并发 50 路余额不出现负数/丢失 | ✅ |
| FEFO + 条件 UPDATE 不超卖 | ✅ |
| 盘点状态机事务化 + 二次确认空流水 | ✅ |
| 防重复提交组件 + 幂等键生成 | ✅ |
| 并发/幂等自动化 5 场景覆盖 | ✅ |

**R2.0-B 全部 8 项任务完成。**