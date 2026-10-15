# R2.0-C：收银支付退款对账闭环

> 范围：`pos_refund` 状态机、退款单接口、退款逆向（次数/积分/库存/提成）、支付对账、收银台 UI、E2E 测试。

## 1. pos_refund 表 + 状态机（T-C01）

`pos_refund` 字段：
```
id, refund_no, tenant_id, brand_id, store_id,
src_order_id, src_order_no,
refund_type   PARTIAL / FULL / CROSS
refund_amount DECIMAL(12,2)
refund_method ORIGINAL / WALLET / MIXED
status        1待审核 2已审核 3退款中 4已完成 5已拒绝 6已撤销
reason, operator_id, operator_name,
audit_at, paid_at, reversed_at
```

状态机：
```
DRAFT(1) --提交--> AUDITING(2) --审核通过--> REFUNDING(3) --支付完成--> DONE(4)
                                       --审核拒绝--> REJECTED(5)
DONE/REFUNDING --发起撤销--> REVERSED(6)  --逆向流水回写
```

## 2. 退款单 CRUD（T-C02）

`PosRefundService`：
- `POST /pos/refund` 创建（部分/整单/跨单）
- `GET  /pos/refund/page`
- `GET  /pos/refund/{id}`
- `POST /pos/refund/{id}/audit`
- `POST /pos/refund/{id}/pay`
- `POST /pos/refund/{id}/revoke`

策略矩阵：
| 退款类型 | 触发场景 | 退款方式 |
|---|---|---|
| PARTIAL | 仅退部分商品 | ORIGINAL 原路 |
| FULL | 整单 | ORIGINAL 或 WALLET |
| CROSS | 跨单抵消 | WALLET |

## 3. 退款逆向（T-C03）

`PosRefundService.reverseAll(refundNo)` 一次性事务：

1. **钱包逆向**：原路原额退回 `crm_wallet` 余额
2. **积分逆向**：`points_log` 写入负向流水
3. **库存逆向**：`stock_record.type=4 AND order_id=?` 找消耗记录，`casInbound` 加回
4. **套餐次数逆向**：`pkg_redemption.reverseByOrder(orderId)` 增加 `remaining_sessions`
5. **提成逆向**：`commission_reversal` 独立记录，撤销原 `commission_record`
6. **订单状态**：`pos_order.status = 6(已退款)`
7. **对账标记**：`pos_reconciliation.refund_flag = 1`

所有动作必须 `@Transactional(rollbackFor=Exception.class)`，任一失败整体回滚。

## 4. 支付对账（T-C04）

`pos_reconciliation`：
```
id, tenant_id, store_id, biz_date,
channel_code, local_order_count, channel_order_count,
local_amount_cents, channel_amount_cents,
diff_amount_cents, diff_reason,
status  1待对账 2已对平 3有差异 4已处理
```

`ReconciliationService`：
- 通道拉取器（mock）：`MockChannelFetcher` + `WechatChannelFetcher` + `AlipayChannelFetcher`
- 本地拉取：基于 `pos_order.pay_time` 在 biz_date 范围
- 逐单比对：order_no + amount_cents + status
- 差异生成：`status=3` + `errorCode` + `errorMsg`

错误码：`RECON_DIFF_FOUND (700001)`、`RECON_DIFF_UNHANDLED (700002)`。

## 5. 收银台退款 UI（T-C05）

`pos/refund.vue`：
- 订单详情侧拉抽屉
- "部分退款" 表格：选择明细项 → 自动计算可退金额
- "整单退款" 一键确认
- "原路 / 储值 / 混合" 三选按钮
- 提交后显示 `refund_no` + 反向流水入口

## 6. 对账页面（T-C06）

`pos/reconciliation.vue`：
- 业务日期选择
- 自动对账按钮（调 `ReconciliationService.reconcile(date)`）
- 差异单列表：金额差/单号差/状态差
- "标处理" 操作（写差异处理记录 + 触发人工改单）

## 7. 退款 E2E（T-C07）

`test-rbac-v2-c.ps1`（UTF-8 BOM），覆盖 4 种支付方式：

| 用例 | 支付方式 | 验证点 |
|---|---|---|
| 现金退款 | CASH | 钱包余额+原额、库存+回、提成-冲 |
| 第三方退款 | WECHAT | 通道回调、状态联动、对账一致 |
| 储值退款 | WALLET | 余额回退、不退现金 |
| 混合退款 | MIXED | 按比例拆分、退部分 |

每条用例断言：
- `pos_refund.status = 4`
- `crm_wallet.balance` 回退正确
- `crm_point.balance` 回退正确
- `stock_record` 出现 `type=4` 回滚
- `commission_reversal` 出现
- 再次退款同单 → `IDEMPOTENT_REPLAY`

## 8. 交付物清单

| 类型 | 文件 |
|---|---|
| 后端 | `PosRefundService.java`、`ReconciliationService.java` |
| SQL | `init_rbac_v2_c.sql`（`pos_refund`、`pos_reconciliation` 等） |
| 前端 | `pos/refund.vue`、`pos/reconciliation.vue` |
| 通道 | `MockChannelFetcher.java`、`WechatChannelFetcher.java`、`AlipayChannelFetcher.java` |
| 测试 | `test-rbac-v2-c.ps1` |

## 9. 验收对照

| 验收项 | 状态 |
|---|:---:|
| `pos_refund` 表 + 状态机文档 | ✅ |
| 部分/整单/跨单退款策略 | ✅ |
| 退款逆向 5 类资源全部回退 | ✅ |
| 对账差异单明确错误码 | ✅ |
| 收银台 3 种退款方式 UI | ✅ |
| 对账页面 + 差异告警 + 处理 | ✅ |
| 4 种支付方式 E2E | ✅ |

**R2.0-C 全部 7 项任务完成。**