# R2.0-C：退款单状态机 + 退款策略

> 设计目标：把现有 `POST /pos/orders/{id}/refund` 单笔链路升级为**多笔 / 多状态 / 多策略**的退款单体系。

---

## 1. 状态机（pos_refund.status）

```
       apply
   ┌───────────┐
   ▼           │
DRAFT ───────► PENDING ─────► AUDIT ─────► APPROVED ──► PROCESSING ──► SUCCESS
                │                              │  │                        │
                │(金额校验失败)                  │  ├─► REJECTED              └─► FAILED
                ▼                              │  │
              CANCELLED                        │  └─► CANCELLED
                                               │
                                               ▼
                                       (退款完成) ──► 部分退款继续走下一笔
```

| 状态 | 说明 | 允许的下一态 |
|---|---|---|
| `DRAFT` | 草稿（前端未提交） | `PENDING` / `CANCELLED` |
| `PENDING` | 已提交，待系统校验 | `AUDIT` / `CANCELLED` |
| `AUDIT` | 待人工审核（金额 > 阈值） | `APPROVED` / `REJECTED` |
| `APPROVED` | 审核通过 | `PROCESSING` / `CANCELLED` |
| `REJECTED` | 审核拒绝 | `CANCELLED`（终态） |
| `PROCESSING` | 已发往通道 | `SUCCESS` / `FAILED` |
| `SUCCESS` | 退款成功（终态） | — |
| `FAILED` | 退款失败（可重试） | `PROCESSING` / `CANCELLED` |
| `CANCELLED` | 取消（终态） | — |

**进入阈值**：单笔退款金额 `>= 5000` 元或 `>= 订单实付的 50%` 强制走 `AUDIT`；其余直接 `PENDING → APPROVED`。

---

## 2. 退款类型（refund_type）

| 类型 | 含义 | 触发 |
|---|---|---|
| `FULL` | 整单退款 | `POST /pos/refund` 不带 `amount` 或 `amount == paidAmount` |
| `PART` | 部分退款 | `POST /pos/refund` 带 `amount < paidAmount` |
| `CROSS` | 跨单退款（把多笔订单合并退款） | `POST /pos/refund/cross` 一次提交多 orderId |

---

## 3. 退款策略（refund_strategy）

| 策略 | 含义 | 适用 |
|---|---|---|
| `ORIGINAL` | 原路退回（现金 / 第三方 / 数字人民币） | 默认 |
| `WALLET` | 退至会员钱包余额 | 会员场景 |
| `MIXED` | 部分原路 + 部分储值 | 客户协商 |

策略决定 `pos_refund.cash_refund / wallet_refund / thirdparty_refund` 三个字段，及 `pos_refund_item` 中每条支付明细的回退路径。

---

## 4. 退款逆向补偿矩阵

| 业务侧 | 触发条件 | 动作 | 写流水 |
|---|---|---|---|
| 套餐（次卡） | 订单明细含 `packageOrderId` | `crm_package_order.remaining_times + N` + `packageOrder.status` 回退一档 | `crm_package_redemption` 写负向；`pos_refund_reverse_log` 记 `PACKAGE` |
| 积分 | `point_rule.consume` 触发过返积分 | `crm_member.points += points` | `market_point_record` 写 `type=2`（含 source=`refund_return`）；`pos_refund_reverse_log` 记 `POINTS` |
| 库存 | 订单含 `inventory_consumption` 已消耗耗材 | `stock_inventory.casInbound` 回退 | `stock_record` 写 `type=2`（备注 `refund:<refundNo>`）；`pos_refund_reverse_log` 记 `STOCK` |
| 提成 | 已计算过提成 | `commission_record.void` 写反向 | `commission_record` 写 `type=2`；`pos_refund_reverse_log` 记 `COMMISSION` |

**幂等**：逆向回退通过 `pos_refund_reverse_log` 去重（同一 refund_id + reverse_type 不会重复执行）。

---

## 5. 退款单接口

| Method | Path | 用途 |
|---|---|---|
| `POST` | `/pos/refund` | 创建退款单（FULL / PART） |
| `POST` | `/pos/refund/cross` | 跨单退款 |
| `GET` | `/pos/refund/{id}` | 详情（带 items + reverse_log） |
| `GET` | `/pos/refund/page` | 列表（条件：状态/时间/金额/会员） |
| `POST` | `/pos/refund/{id}/audit` | 审核通过 / 拒绝 |
| `POST` | `/pos/refund/{id}/process` | 触发 PROCESSING（调通道） |
| `POST` | `/pos/refund/{id}/cancel` | 取消（DRAFT/PENDING/AUDIT/APPROVED 状态） |
| `POST` | `/pos/refund/{id}/reverse` | 显式触发逆向补偿（一般随 PROCESSING→SUCCESS 自动） |

所有写接口支持 `Idempotency-Key` 头。

---

## 6. 退款单与 pos_order 字段映射

| pos_refund | pos_order |
|---|---|
| `paid_amount` | `paid_amount` |
| `refund_amount` | `refundAmount`（旧字段） |
| `refunded_amount` | `refunded_amount`（新增） |
| `refund_count` | `refund_count`（新增） |
| `status = SUCCESS` | `pos_order.status = 3`（已退款，全部退完时） |
| `status = SUCCESS` 且 `remaining > 0` | `pos_order.status = 2`（仍为已支付） |

---

## 7. 支付对账（pos_reconciliation）

### 7.1 对账维度

- 按 `tenant_id + reconcile_date + channel` 拉取通道侧流水 + 本地 `pos_payment` 流水；
- 按 `payment_id` 或 `channel_txn_id` 匹配 → 写 `MATCHED`；
- 不匹配 → 写 `DIFF_*` 并标记 `handle_status = PENDING`。

### 7.2 差异类型

| status | 含义 |
|---|---|
| `MATCHED` | 双侧一致 |
| `DIFF_LOCAL_MORE` | 本地有，通道无（漏单） |
| `DIFF_CHANNEL_MORE` | 通道有，本地无（未入账） |
| `DIFF_AMOUNT` | 都存在，金额不一致 |
| `MISSING_LOCAL` | 通道笔，本地笔完全查不到 |
| `MISSING_CHANNEL` | 本地笔，通道笔完全查不到 |

### 7.3 处理流程

1. 财务每日 02:00 跑对账任务，拉通道 + 本地数据；
2. 差异单写入 `pos_reconciliation`，前端差异告警页提示；
3. 财务点击"标记处理" → `handle_status = HANDLED` + `handle_note`；
4. 差异类型为 `DIFF_AMOUNT` 时，强提示需走售后确认。

---

## 8. 状态机代码映射

- `enum RefundStatus { DRAFT, PENDING, AUDIT, APPROVED, REJECTED, PROCESSING, SUCCESS, FAILED, CANCELLED }`
- `enum RefundType { FULL, PART, CROSS }`
- `enum RefundStrategy { ORIGINAL, WALLET, MIXED }`
- 状态迁移通过 `RefundStatusMachine.transit(from, to, ...)` 校验；非法迁移抛 `BusinessException(CONFLICT)`。

---

## 9. 验收口径

| 项 | 验收 |
|---|---|
| 状态机 | 非法迁移返回 409 |
| 幂等 | 同 key 二次访问返回上次结果 |
| 逆向 | 套餐 / 积分 / 库存 / 提成 至少 4 各 1 个测试用例 |
| 阈值 | 5000 元自动转 AUDIT |
| 部分退款 | 同一订单多次退，`refunded_amount` 累加不超 `paid_amount` |
| 跨单退款 | 一次合并 2+ 订单，所有子单 `pos_refund` 关联同一 `cross_group_id` |
| 对账差异 | 模拟差异单生成，UI 提示并可标处理 |
