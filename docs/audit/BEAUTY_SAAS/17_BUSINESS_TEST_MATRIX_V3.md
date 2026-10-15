# 17_BUSINESS_TEST_MATRIX_V3

> **基线建立日期**: 2026-08-13
> **审计目的**: 重新审计测试覆盖度，区分单元/集成/API/E2E/并发/幂等维度，识别真正空白

---

## 0. 测试资产盘点

```
$ find backend/src/test -name "*.java"
- ConcurrencyIntegrationTest.java        （3 Case：钱包幂等、积分并发、库存并发）
- PosRefundIntegrationTest.java          （5 Case：现金/三方/钱包/混合/套餐反向）
- RefundStatusTest.java                  （枚举测试）
- BizCodeTest.java                       （错误码枚举测试）
```

**总计 4 个测试类，8 个测试方法，集成测试 2 个**。

---

## 1. BUSINESS_TEST_MATRIX

| Domain | Unit | Integration | API | E2E | Concurrency | Idempotency | Status |
|--------|------|-------------|-----|-----|-------------|-------------|--------|
| Member | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Booking | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Check-in | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Service Item | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Package | ⚫ | 🟢 (Case 5 reverse) | ⚫ | ⚫ | ⚫ | ⚫ | **PARTIAL** |
| Redemption | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Inventory (Stock) | ⚫ | 🟢 (inventoryNoOversell) | ⚫ | ⚫ | 🟢 | 🟢 (idempotency_key) | **PARTIAL** |
| Order (POS) | ⚫ | 🟢 (PosRefundTest 4 cases) | ⚫ | ⚫ | ⚫ | 🟢 (Idempotency) | **PARTIAL** |
| Payment (Wallet) | ⚫ | 🟢 (walletIdempotent) | ⚫ | ⚫ | 🟢 | 🟢 | **PARTIAL** |
| Payment (DCEP) | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Refund | ⚫ | 🟢 (5 cases) | ⚫ | ⚫ | ⚫ | 🟢 | **PARTIAL** |
| Commission | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |
| Report | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | ⚫ | **EMPTY** |

图例：🟢=存在 🟡=部分 ⚫=无

---

## 2. 库存必须验证的 10 项 vs 实际覆盖

| # | 必测项 | 实际覆盖 | 测试类 |
|---|--------|----------|--------|
| 1 | 正常扣减 | ✅ | `PosRefundTest` 间接 |
| 2 | 并发扣减 | ✅ | `inventoryNoOversell`（10 并发 × 20 件） |
| 3 | CAS（version） | ✅ | 同上 |
| 4 | 乐观锁 | ✅ | 同上 |
| 5 | FEFO 批次选择 | ❌ | 无专门测试 |
| 6 | 扣减失败回滚 | 🟡 | `@Transactional` 保证，但无专门验证 |
| 7 | 重复请求幂等 | ✅ | `pos_order_consumption.UNIQUE(idempotency_key)` + 异常捕获 |
| 8 | 订单重复完成 | 🟡 | `duplicate-key caught, rollback this material only` 注释 |
| 9 | 退款回流 | ✅ | `PosRefundTest` Case 5（套餐次卡 reverse） |
| 10 | 多批次库存 | ❌ | 无测试 |

**评估**：库存测试覆盖 **6/10 = 60%** 必测项。FEFO 和多批次仍需补充。

---

## 3. 缺失测试清单

### 3.1 P0 必须补充（影响资金/数据完整性）

| 测试类 | 测试方法 | 必要性 |
|--------|----------|--------|
| `WalletRechargeConcurrencyTest` | 50 并发充值同一会员，验证余额准确 + version 提升 | P0 |
| `WalletConsumeConcurrencyTest` | 50 并发扣款，验证余额非负 + 不超额 | P0 |
| `WalletRefundIdempotentTest` | 同一订单号两次 refund，第二次拒绝 | P0 |
| `DcepMockVerifySignTest` | 验证 `sign.startsWith("MOCK-")` 真的能通过（红队测试） | P0 |
| `BookingCreateConflictTest` | 同一员工同一时段两次预约，第二次拒绝 | P0 |
| `BookingRoomConflictTest` | 同一房间同时段两次预约，第二次拒绝 | P0 |

### 3.2 P1 业务完整性

| 测试类 | 测试方法 |
|--------|----------|
| `PackageRedemptionConcurrencyTest` | 并发核销次卡，次数不超额 |
| `MemberPointsConcurrencyTest` | 已有（ConcurrencyIntegrationTest） |
| `PosOrderStateTransitionTest` | 订单状态机（待支付→已支付→已退款→已取消） |
| `CommissionCalculationTest` | 提成计算准确性 |

### 3.3 P2 E2E（前端验证）

| 测试类 | 覆盖范围 |
|--------|----------|
| `E2ECheckoutFlow` | 前端 → 后端 → DB 全链路 |
| `E2ERefundFlow` | 退款全链路 |
| `E2EBookingFlow` | 预约全链路 |

---

## 4. 测试运行证据

`backend-current.log` 和已有 `.out` 文件验证测试是否真的运行过：

```
Run-All-Tests.ps1  (存在)
```

但**未发现 CI 配置**（无 .github/workflows 下 Java 文件，除 `.github` 目录存在外）。

---

## 5. 覆盖率估算

| 维度 | 估算 |
|------|------|
| 代码覆盖率 | 未知（未配置 jacoco） |
| 业务覆盖 | ~30%（库存 + 退款 + 部分钱包）|
| 并发覆盖 | ~10%（仅 1 个库存 + 1 个积分）|
| E2E 覆盖 | 0% |

---

## 6. 结论

- 现有测试集中在**库存 + 退款**两大模块，是金子但**仅占业务 30%**
- **Booking/Member/Commission/Report/DCEP** 五大业务**完全无测试**
- E2E **完全空白**
- **测试覆盖率绝对值低**，但关键链路（库存、退款、钱包幂等）有真实集成测试

---

**审计完成时间**: 2026-08-13 23:20
**文档版本**: 3.0