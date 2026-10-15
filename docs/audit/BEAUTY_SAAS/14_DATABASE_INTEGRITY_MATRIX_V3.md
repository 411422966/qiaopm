# 14_DATABASE_INTEGRITY_MATRIX_V3

> **基线建立日期**: 2026-08-13
> **审计目的**: 对 V2 报告"零外键"结论进行重新核验，分析是否应建立 FK、应用层 vs DB 层 FK 的取舍、删除策略、租户隔离、并发场景。

---

## 0. 核验方法

1. 全量扫描 `backend/src/main/resources/db/sql/*.sql` 寻找 `FOREIGN KEY` / `REFERENCES`
2. 从核心 Entity 字段提取候选 FK
3. 对每条候选 FK 评估：是否必须建立 / 可应用层约束 / 应建立但未建 / 应保留无 FK

---

## 1. 当前外键现状（V3 重新核验）

```
$ rg "FOREIGN KEY|REFERENCES" backend/src/main/resources/db/sql/
→ No matches found
```

**结论**：数据库 schema **0 条 FK 约束**，确认 V2 报告正确。

### 1.1 是否"故意采用应用层 FK"？

| 模式 | 存在性 | 证据 |
|------|--------|------|
| `existsByXxxId` 校验 | 部分存在 | `WalletController:117` `memberService.getById` 在操作前查 |
| 软删除级联逻辑 | 存在 | 多数 Entity 有 `deleted` 字段，业务层过滤 |
| `ON DELETE CASCADE` 模拟 | 否 | 未发现显式级联 |
| 跨表一致性保证 | 局部 | `WalletController:152` `memberService.updateById` 同步 `crm_member.balance` 与 `crm_wallet.balance` |

**判定**：项目**采用应用层 FK 模式**，但实现不完整，存在"双轨"（如 crm_member.balance 与 crm_wallet.balance 双写）。

---

## 2. FK_CANDIDATE_MATRIX

### 2.1 关键候选 FK 评估

#### 2.1.1 会员域（crm_member 相关）

| # | Child Table.Column | Parent Table.Column | Logical | Physical | Delete Strategy | Risk | Recommendation |
|---|--------------------|---------------------|---------|----------|-----------------|------|----------------|
| 1 | `pos_order.member_id` | `crm_member.id` | ✅ | ❌ | RESTRICT（订单不可删除）| P1 | **不建物理 FK**。订单需保留历史；并发插入 FK 检查有性能损耗；应用层 `memberService.getById` 已校验 |
| 2 | `pos_payment.member_id` | `crm_member.id` | ✅ | ❌ | RESTRICT | P1 | **不建**。同上 |
| 3 | `crm_wallet.member_id` | `crm_member.id` | ✅ | ❌ | CASCADE（会员删则钱包删）| P1 | **不建**。已有 UNIQUE KEY `uk_member`，钱包是会员附属数据 |
| 4 | `crm_wallet_transaction.member_id` | `crm_member.id` | ✅ | ❌ | RESTRICT（流水不可丢）| P1 | **不建**。审计要求保留流水 |
| 5 | `crm_member_credit.member_id` | `crm_member.id` | ✅ | ❌ | CASCADE | P1 | **不建**。同上 |
| 6 | `crm_member_tag_value.member_id` | `crm_member.id` | ✅ | ❌ | CASCADE | P1 | **不建** |

#### 2.1.2 预约域（biz_booking 相关）

| # | Child Table.Column | Parent Table.Column | Logical | Physical | Delete Strategy | Risk | Recommendation |
|---|--------------------|---------------------|---------|----------|-----------------|------|----------------|
| 7 | `biz_booking.member_id` | `crm_member.id` | ✅ | ❌ | RESTRICT | P1 | **不建**。预约需保留历史 |
| 8 | `biz_booking.employee_id` | `employee.id` | ✅ | ❌ | — | P1 | **不建**。员工有离职状态而非删除 |
| 9 | `biz_booking.room_id` | `biz_room.id` | ✅ | ❌ | — | P1 | **不建**。房间状态可变 |
| 10 | `biz_employee_schedule.employee_id` | `employee.id` | ✅ | ❌ | — | P1 | **不建** |

#### 2.1.3 订单域

| # | Child Table.Column | Parent Table.Column | Logical | Physical | Delete Strategy | Risk | Recommendation |
|---|--------------------|---------------------|---------|----------|-----------------|------|----------------|
| 11 | `pos_order_item.order_id` | `pos_order.id` | ✅ | ❌ | CASCADE | P0 | **强烈建议建立物理 FK**。订单明细无独立生命周期，必须跟随订单；但软删除场景下 ON DELETE 不触发，需要应用层保证 |
| 12 | `pos_payment.order_id` | `pos_order.id` | ✅ | ❌ | RESTRICT（支付记录不可丢）| P0 | **强烈建议**。支付流水与订单强关联，审计要求 |
| 13 | `pos_payment.refund_id` | `pos_refund.id` | ✅ | ❌ | — | P1 | 可建可不建 |
| 14 | `pos_order_consumption.order_id` | `pos_order.id` | ✅ | ❌ | CASCADE | P0 | **强烈建议**。库存消耗与订单是同一事务，物理 FK 可防止孤儿 |
| 15 | `pos_order_consumption.stock_record_id` | `stock_record.id` | ✅ | ❌ | RESTRICT | P1 | 可建 |
| 16 | `pos_refund.order_id` | `pos_order.id` | ✅ | ❌ | RESTRICT | P0 | **强烈建议**。退款单是订单子表 |
| 17 | `pos_refund_item.refund_id` | `pos_refund.id` | ✅ | ❌ | CASCADE | P0 | **强烈建议** |

#### 2.1.4 套餐域

| # | Child Table.Column | Parent Table.Column | Logical | Physical | Delete Strategy | Risk | Recommendation |
|---|--------------------|---------------------|---------|----------|-----------------|------|----------------|
| 18 | `crm_package_order.member_id` | `crm_member.id` | ✅ | ❌ | RESTRICT | P1 | **不建**。套餐订单需保留 |
| 19 | `crm_package_order.package_id` | `crm_package.id` | ✅ | ❌ | — | P1 | **不建** |
| 20 | `crm_package_redemption.order_id` | `pos_order.id` | ✅ | ❌ | RESTRICT | P1 | **不建** |
| 21 | `crm_package_redemption.member_id` | `crm_member.id` | ✅ | ❌ | RESTRICT | P1 | **不建** |

#### 2.1.5 库存域

| # | Child Table.Column | Parent Table.Column | Logical | Physical | Delete Strategy | Risk | Recommendation |
|---|--------------------|---------------------|---------|----------|-----------------|------|----------------|
| 22 | `stock_inventory.product_id` | `stock_product.id` | ✅ | ❌ | CASCADE | P1 | **不建**。库存聚合，删除产品应显式处理 |
| 23 | `stock_record.product_id` | `stock_product.id` | ✅ | ❌ | RESTRICT | P1 | **不建**。审计要求保留流水 |
| 24 | `stock_record.order_id` | `pos_order.id` | ✅ | ❌ | — | P1 | **不建** |

#### 2.1.6 支付域

| # | Child Table.Column | Parent Table.Column | Logical | Physical | Delete Strategy | Risk | Recommendation |
|---|--------------------|---------------------|---------|----------|-----------------|------|----------------|
| 25 | `pay_dcep_order.tenant_id` | `sys_tenant.id` | ✅ | ❌ | RESTRICT | P0 | **强烈建议**。租户级隔离 FK 可防止跨租户脏数据 |
| 26 | `pay_dcep_order.biz_order_id` | `pos_order.id` | ✅ | ❌ | RESTRICT | P0 | **强烈建议**。外部订单 ID 必须存在 |
| 27 | `pay_dcep_notify.dcep_order_id` | `pay_dcep_order.id` | ✅ | ❌ | CASCADE | P0 | **强烈建议**。回调与订单是父子关系 |

---

## 3. 风险矩阵汇总

| 风险等级 | 数量 | 说明 |
|----------|------|------|
| P0 | 8 | 强烈建议建立物理 FK（订单-订单明细/支付/退款；DCEP 回调链） |
| P1 | 19 | 可不建，应用层已校验或软删除兼容 |
| INFO | 0 | — |

---

## 4. 整改建议（不是盲目加 FK）

### 4.1 应建立的 8 条 P0 FK

```sql
-- 仅列参考方向，具体 DDL 留待 TASK-P0-DB-002

ALTER TABLE pos_order_item
  ADD CONSTRAINT fk_pos_order_item_order
  FOREIGN KEY (order_id) REFERENCES pos_order(id) ON DELETE RESTRICT;

ALTER TABLE pos_payment
  ADD CONSTRAINT fk_pos_payment_order
  FOREIGN KEY (order_id) REFERENCES pos_order(id) ON DELETE RESTRICT;

ALTER TABLE pos_order_consumption
  ADD CONSTRAINT fk_pos_order_consumption_order
  FOREIGN KEY (order_id) REFERENCES pos_order(id) ON DELETE RESTRICT;

ALTER TABLE pos_order_consumption
  ADD CONSTRAINT fk_pos_order_consumption_stock_record
  FOREIGN KEY (stock_record_id) REFERENCES stock_record(id) ON DELETE RESTRICT;

ALTER TABLE pos_refund
  ADD CONSTRAINT fk_pos_refund_order
  FOREIGN KEY (order_id) REFERENCES pos_order(id) ON DELETE RESTRICT;

ALTER TABLE pos_refund_item
  ADD CONSTRAINT fk_pos_refund_item_refund
  FOREIGN KEY (refund_id) REFERENCES pos_refund(id) ON DELETE CASCADE;

ALTER TABLE pay_dcep_order
  ADD CONSTRAINT fk_pay_dcep_order_tenant
  FOREIGN KEY (tenant_id) REFERENCES sys_tenant(id) ON DELETE RESTRICT;

ALTER TABLE pay_dcep_notify
  ADD CONSTRAINT fk_pay_dcep_notify_order
  FOREIGN KEY (dcep_order_id) REFERENCES pay_dcep_order(id) ON DELETE CASCADE;
```

### 4.2 应保留无 FK 的关系（理由）

| 场景 | 理由 |
|------|------|
| 会员-钱包/流水 | UNIQUE KEY 即可；CASCADE 风险太大（流水不可丢）|
| 会员-预约/订单 | 会员软删除，物理 FK 阻止软删操作 |
| 员工-排班 | 员工离职用 status 字段，不用 delete |
| 商品-库存 | 商品聚合数据，删除产品会触发复杂级联 |
| 房间-预约 | 房间状态变更不影响历史预约 |

---

## 5. 结论

- **不建 FK ≠ 错误**。项目采用应用层 FK + 软删除 + UNIQUE KEY 组合，是合理的工程选择
- **但应补齐 8 条 P0 FK**：订单明细/支付/退款/库存消耗/DCEP 回调链（这些是同一事务边界内的强聚合）
- **同时修复 crm_wallet vs crm_member.balance 双轨问题**（FK 无法解决，但应在应用层做互斥写入或合并字段）

---

**审计完成时间**: 2026-08-13 23:05
**文档版本**: 3.0