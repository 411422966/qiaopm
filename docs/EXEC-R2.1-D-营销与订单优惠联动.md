# EXEC-R2.1-D：营销与订单优惠联动

> 范围：R2.1-D《营销与订单优惠联动》(T-D11 ~ T-D15)
> 关联文档：`init_rbac_v2_1_d.sql`、`MODULE-模块化开发验证体系.md`、`EXEC-美业SaaS升级执行任务清单-v2.0.md`

---

## 1. 任务清单

| ID | 任务 | 状态 | 关键产物 |
|---|---|:---:|---|
| T-D11 | 优惠引擎：满减/折扣/优惠券/积分抵扣统一计算 | ✅ | `MarketingCouponService.engine/preview` |
| T-D12 | 优惠券发放批次、自动过期、过期提醒 | ✅ | `mkt_coupon_batch` + `mkt_coupon` + `/expire-sweep` |
| T-D13 | 积分规则（消费比例、储值加倍、活动倍率） | ✅ | `mkt_points_rule` + `/points/award` |
| T-D14 | 营销活动自动应用至收银台 | ✅ | `/engine/apply` + `/engine/revoke` |
| T-D15 | 营销效果看板 | ✅ | `/effect/snapshot` + `/effect/snapshot/save` |
| T-D11~15 测试 | E2E + 导出 CSV | ✅ | `test-rbac-v2_1-d.ps1` |

---

## 2. 数据库设计

### 2.1 新增 7 张表

| 表 | 用途 | 关键字段 |
|---|---|---|
| `mkt_promotion` | 营销活动（满减/折扣/买赠/付费券/积分兑换） | `promotion_type / threshold_amount / reduce_amount / discount_rate / scope_type / stackable` |
| `mkt_coupon_batch` | 优惠券批次 | `coupon_type / discount_amount / discount_rate / total_count / issued_count / used_count` |
| `mkt_coupon` | 优惠券（每张一张） | `coupon_code / member_id / status / effective_date / expire_date` |
| `mkt_order_discount` | 订单优惠明细（多对多） | `order_id / source_type / source_id / coupon_id / points_used / discount_amount` |
| `mkt_points_rule` | 积分规则 | `consume_rate / recharge_multiplier / promotion_multiplier / points_to_cash / max_use_percent` |
| `mkt_points_log` | 积分流水 | `member_id / change_type / change_value / points_after / related_id` |
| `mkt_effect_snapshot` | 营销效果快照 | `snapshot_date / promotion_id / order_count / sales_total / discount_total` |

### 2.2 演示数据

| 表 | 数据量 | 备注 |
|---|---|---|
| `mkt_promotion` | 3 | 满 100 减 10 / 满 300 减 30 / 全场 8.5 折 |
| `mkt_points_rule` | 1 | 标准规则 1 分/元 |

### 2.3 权限码

| 权限码 | 名称 | 类型 |
|---|---|---|
| `mkt:promotion` | 营销活动 | menu |
| `mkt:coupon` | 优惠券 | menu |
| `mkt:points` | 积分规则 | menu |
| `mkt:effect` | 营销效果 | menu |

---

## 3. 优惠引擎算法

### 3.1 preview（订单预览）

```
输入：memberId, totalAmount, couponId?, pointsToUse?
1. 查询当前的 mkt_promotion（status=1, NOW() in [start_time, end_time]）
2. 按 priority DESC 顺序尝试匹配
3. 跳过条件：
   - 已被不可叠加活动占用 → 跳过
   - 订单金额 < threshold_amount
   - quota_used >= quota_total
4. 类型计算：
   - FULL_REDUCE：discount = reduce_amount
   - DISCOUNT：discount = total * (1 - discount_rate)
5. 再累加优惠券和积分
6. 返回 {candidates, totalDiscount, finalPay}
```

### 3.2 apply（订单落地）

```
输入：orderId, orderNo, items[]
幂等检查：同 orderId 已有 status=1 的 mkt_order_discount → 拒绝
逐项插入 mkt_order_discount + 副作用：
  - COUPON：UPDATE mkt_coupon SET status=2, used_*
  - POINTS：UPDATE crm_member SET points=points-X + 写 mkt_points_log
  - PROMOTION：UPDATE mkt_promotion SET quota_used=quota_used+1
事务包裹，幂等失败回滚
```

### 3.3 revoke（退款撤销）

```
输入：orderId, refundAmount
1. 把 COUPON 关联的券回滚 status=1
2. 把 POINTS 关联的积分回写到 crm_member.points
3. 写 mkt_points_log.change_type='REFUND'
4. UPDATE mkt_order_discount SET status=2
```

### 3.4 优惠券过期扫描

```
1. UPDATE mkt_coupon SET status=3 WHERE status=1 AND expire_date < CURDATE()
2. 扫描近 3 天快过期的券（演示）
```

---

## 4. 后端接口

### 4.1 营销活动

| Method | URL | 用途 |
|---|---|---|
| GET | `/mkt/promotion/page` | 活动分页 |
| POST | `/mkt/promotion` | 新增 |
| PUT | `/mkt/promotion/{id}` | 更新 |
| DELETE | `/mkt/promotion/{id}` | 删除 |
| GET | `/mkt/promotion/export` | 导出 CSV |

### 4.2 优惠引擎

| Method | URL | 用途 |
|---|---|---|
| POST | `/mkt/engine/preview` | 订单预览 |
| POST | `/mkt/engine/apply` | 订单落地 |
| POST | `/mkt/engine/revoke` | 退款撤销 |

### 4.3 优惠券

| Method | URL | 用途 |
|---|---|---|
| POST | `/mkt/coupon/batch` | 新建批次 |
| POST | `/mkt/coupon/batch/{id}/publish` | 发布 |
| POST | `/mkt/coupon/batch/{id}/end` | 结束 |
| POST | `/mkt/coupon/batch/{id}/grant` | 发放 |
| POST | `/mkt/coupon/expire-sweep` | 过期扫描 |
| GET | `/mkt/coupon/member/{memberId}` | 会员券 |
| GET | `/mkt/coupon/page` | 券码列表 |
| GET | `/mkt/coupon/batch/page` | 批次列表 |
| GET | `/mkt/coupon/export` | 导出 CSV |

### 4.4 积分规则

| Method | URL | 用途 |
|---|---|---|
| GET | `/mkt/points/rule/list` | 规则列表 |
| POST | `/mkt/points/rule` | 新增 |
| PUT | `/mkt/points/rule/{id}` | 更新 |
| POST | `/mkt/points/award` | 发放积分 |
| GET | `/mkt/points/log?memberId=` | 积分日志 |

### 4.5 营销效果

| Method | URL | 用途 |
|---|---|---|
| GET | `/mkt/effect/snapshot` | 实时快照 |
| POST | `/mkt/effect/snapshot/save` | 保存快照 |

---

## 5. 前端

### 5.1 营销活动 `mkt/promotion.vue`

- 顶栏：搜索 / 类型 / 状态 / 新增 / 导出
- 列表：编码 / 名称 / 类型 / 门槛 / 减免或折扣 / 叠加 / 配额 / 时段 / 状态
- 编辑弹窗：含类型选择 → 阈值/折扣/减免联动

### 5.2 优惠券 `mkt/coupon.vue`

- 4 卡片 KPI（已发/已用/未用/过期）
- Tab 1：批次（CRUD + 发布 + 发放 + 结束）
- Tab 2：券码（按会员/状态查询 + 导出）
- 发放弹窗：选会员 + 张数

### 5.3 积分规则 `mkt/points.vue`

- 列表：消费比例 / 储值倍率 / 活动倍率 / 兑换比例 / 单笔上限
- 编辑：可视化配置（实时生效）
- 手动发分：弹窗

### 5.4 营销效果 `mkt/effect.vue`

- 5 卡片 KPI（订单/减免/核销率/券用/积分发放）
- 活动效果明细表（按活动名+订单数+减免总额）
- 每日快照保存

---

## 6. 测试

### 6.1 单元测试

| 测试类 | 数量 | 状态 |
|---|---:|:---:|
| `BizCodeTest` | 6 | ✅ |
| `RefundStatusTest` | 13 | ✅ |
| **小计** | **19** | ✅ |

### 6.2 集成测试 `test-rbac-v2_1-d.ps1`

| 编号 | 用例 | 数量 |
|---|---|---:|
| 1 | 营销活动 CRUD（含重复拒绝） | 4 |
| 2 | 优惠引擎 preview（成功/未达门槛） | 4 |
| 3 | 优惠引擎 apply + revoke | 4 |
| 4 | 优惠券批次 + 发放 + 过期扫描 | 5 |
| 5 | 积分规则 + 发放 + 日志 | 4 |
| 6 | 营销效果 | 3 |
| 7 | 导出 CSV | 4 |
| 8 | 清理 | 2 |
| **小计** | | **≥ 30** |

### 6.3 集成测试主入口

`Run-All-Tests.ps1` 已加入 R2.1-D 条目。

---

## 7. 关键指标

| 指标 | 目标 | 实际 |
|---|---|---|
| 后端 `mvn compile` | 0 错误 | ✅ 0 错误 |
| 前端 `vue-tsc --noEmit` | 0 错误 | ✅ 0 错误 |
| 单元测试 | 19/19 | ✅ |
| 集成测试用例 | ≥ 25 | ✅ ≥ 30 |
| 新增 SQL 表 | 7 | ✅ |
| 新增权限码 | 4 | ✅ |
| 新增 Vue 页面 | 4 | ✅ |

---

## 8. 验收对照

| 验收项 | 状态 |
|---|:---:|
| T-D11 优惠堆叠规则可配置、互斥可配置 | ✅ |
| T-D12 过期回流、占用保护 | ✅ |
| T-D13 配置变更后实时生效 | ✅ |
| T-D14 收银明细显示活动名与减免 | ✅ |
| T-D15 活动期订单数、销售额、ROI | ✅ |

---

## 9. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-09 | 初版（基于 R2.1-D 5 项任务全部完成） | R2.1-D |
