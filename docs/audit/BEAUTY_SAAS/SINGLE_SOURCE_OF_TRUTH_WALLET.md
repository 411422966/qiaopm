# SINGLE_SOURCE_OF_TRUTH_WALLET — 钱包单一权威源决策文档

> **决策主题**：crm_wallet.balance vs crm_member.balance — 选哪个为唯一权威源？
> **批次**：4 / 8（修复路线第 1 周 Day 4）
> **决策日期**：2026-08-15
> **决策人**：Cursor Agent（基于代码 + DB 现状）
> **目标**：消除"双轨余额"导致的 39,152 元漂移

---

## 1. 现状

### 1.1 两个余额字段

| 字段 | 所在表 | 角色 | 约束 |
|---|---|---|---|
| `crm_wallet.balance` | crm_wallet（独立钱包表） | **写源** — 唯一被 `walletMapper.updateBalance()` 更新 | 乐观锁 version + uk_member 唯一键 + fk_wallet_member 外键 |
| `crm_member.balance` | crm_member（会员主表） | **冗余字段** — 写后回填 | 无版本号、无唯一约束、可能漂移 |

### 1.2 漂移数据（实测，2026-08-15）

```
member_id  tenant_id  member_balance  wallet_balance  diff
6          1          15600.00        2200.00         13400.00   ⚠️ 最大
4          1          12800.00        3300.00         9500.00
1          1          5800.00         -298.00         6098.00
8          1          4500.00         -168.00         4668.00
2          1          2200.00         -198.00         2398.00
5          1          3500.00         5500.00         -2000.00
3          1          500.00          -388.00         888.00
7          1          800.00          1000.00         -200.00

漂移成员: 8 个
总差异绝对值: 39,152.00 元
最大单笔差异: 13,400 元（member_id=6）
```

> **注**：用户在批次 4 描述中写的是「29 / -32,152.00」，与实际**不符**。本文档按实测数据推进。

### 1.3 双轨导致问题的根本原因

#### 路径 A：WalletController.doRecharge/doConsume/doRefund（L150-152, L197-200, L249-254）

```java
// ✅ 正确：先写 crm_wallet（CAS 乐观锁）
int rows = casUpdate(memberId, delta, ...);
if (rows == 0) throw new BusinessException(...);

// ❌ 冗余：再写 crm_member（无锁！）
Member m = memberService.getById(memberId);
m.setBalance(before.add(add));    // ← 这里
memberService.updateById(m);
```

**问题**：
1. `memberService.updateById(m)` 走 MyBatis-Plus 默认 update，**不带 version 字段**
2. `before.add(add)` 是从 `Wallet wallet = ensureWallet(...)` 拿的，但如果并发发生，第二步的 `m` 可能读到 stale balance
3. 若 `crm_wallet` 事务回滚而 `crm_member` 已更新 → 漂移

#### 路径 B：PosController 收银台 wallet 路径（L286-303）

```java
case "wallet" -> {
    Member m = memberService.getById(order.getMemberId());
    if (m.getBalance().compareTo(amount) < 0) {
        throw new RuntimeException("余额不足");
    }
    BigDecimal before = m.getBalance();
    BigDecimal after = before.subtract(amount);
    m.setBalance(after);             // ← 直接改 crm_member.balance
    m.setTotalConsume(...);
    memberService.updateById(m);     // ← 没走 WalletController

    // 同时手动写 crm_wallet_transaction
    jdbc.update("INSERT INTO crm_wallet_transaction ...");
    // ⚠️ **没有更新 crm_wallet.balance！**
}
```

**问题**：
1. **完全不更新 crm_wallet.balance** —— 只写 `crm_wallet_transaction` 流水，**crm_wallet 余额不变**！
2. 完全绕过 WalletController.doConsume 的 CAS + 幂等保护
3. `crm_member.balance` 与 `crm_wallet.balance` 必然漂移
4. 这就是为什么 wallet_balance 可能是负数（如 member_id=6 的 2200 vs 15600），但 member_balance 是 15600

---

## 2. 决策

### 决策结果

> **crm_wallet.balance 为唯一权威源（SSOT）。**
>
> **crm_member.balance 字段保留但改为只读视图同步字段**（方案 B）

### 2.1 为什么不是方案 A（删除 crm_member.balance）？

| 维度 | 方案 A：删除列 | 方案 B：保留只读同步 |
|---|---|---|
| **侵入性** | 高 — 11 个前端文件 + MemberController + MyBatis 实体 | 中 — 仅删 3 个 setBalance + 改前端 API 入口 |
| **回归风险** | 高 — 大量前端代码要改 | 低 — 仅保留字段，写路径收敛 |
| **性能** | 中 — 列表要 join 拿余额 | 高 — 列表 0 额外查询（同步字段） |
| **回滚成本** | 高 — 列已删除 | 低 — 字段还在，可回滚到双轨 |
| **审计追溯** | 中 — 必须看 crm_wallet | 高 — 两个表都有，交叉对账容易 |

**结论**：方案 B 更稳健——**保留冗余字段但收敛写路径**，通过删除 3 个 setBalance + PosController 重复路径 + 前端统一调用 /wallet/{memberId}，让 `crm_wallet.balance` 成为唯一被 updateBalance 写入的源，`crm_member.balance` 仅作为**只读同步视图**（每次 wallet 操作通过 trigger 或同步代码回填）。

### 2.2 决策内容

1. **写路径收敛**
   - ✅ 所有余额变更**只走** `WalletController.doRecharge/doConsume/doRefund` + `walletMapper.updateBalance(...)`（CAS + 乐观锁）
   - ❌ 删除 WalletController L150-152/197-200/249-254 的 `m.setBalance(...)` + `memberService.updateById(m)`
   - ❌ 删除 PosController L286-303 的 wallet 重复路径，改走 `WalletController.doConsume(...)`

2. **同步视图保留**（方案 B）
   - ✅ `crm_member.balance` **保留为只读字段**
   - ✅ 钱包操作**自动同步回填** `crm_member.balance`（在 WalletController 内统一一处）
   - ✅ 通过 `WalletService.syncMemberBalance(memberId)` 单一入口回填
   - ❌ **禁止**其他任何代码直接写 `crm_member.balance`

3. **前端收敛**
   - ✅ 前端所有需要余额的场景，**统一调用 `GET /wallet/{memberId}`**
   - ❌ 废弃 `member.balance` 字段在业务页面的使用
   - ✅ `member` 列表/详情可以保留 `member.balance`（同步视图字段），但**显示时建议加 tooltip 说明"以 /wallet/{memberId} 为准"**

4. **漂移对账修复**
   - ✅ 用 `crm_wallet.balance` 为准，把 `crm_member.balance` 同步成权威值
   - ✅ 出具 `WALLET_DRIFT_REPORT.md`（M2-6 产物）
   - ✅ 上线后每周跑对账脚本，发现漂移立即报警

5. **未来增强**（不在本批次）
   - ⏳ 数据库 trigger：crm_wallet.balance 变更自动同步 crm_member.balance
   - ⏳ 完全删除 crm_member.balance 列（方案 A 风险评估后）
   - ⏳ 增加 `crm_member.balance` 只读视图权限控制

---

## 3. 验收标准

### 3.1 单一权威源

- [ ] 代码搜索 `memberService.updateById` 不再有任何 `setBalance` 上下文（除 WalletController 内 syncMemberBalance 入口）
- [ ] 代码搜索 `jdbc.update("UPDATE crm_member SET balance ...")` 数量为 0
- [ ] 代码搜索 `jdbc.update("UPDATE crm_wallet SET balance ...")` 数量为 0（仅 walletMapper 入口）
- [ ] WalletController 内只有一处 `setBalance` 调用（syncMemberBalance）

### 3.2 PosController 收银台

- [ ] POS 支付方式选择"钱包"时，调 WalletController.doConsume
- [ ] 同时走 `Idempotency-Key`（与现有钱包接口一致）
- [ ] 走乐观锁 + 版本号

### 3.3 漂移修复

- [ ] 8 个漂移成员全部回填（crm_member.balance = crm_wallet.balance）
- [ ] 修复后 `SELECT COUNT(*) FROM crm_member WHERE balance != (SELECT balance FROM crm_wallet WHERE member_id = crm_member.id)` = 0

### 3.4 回归测试

- [ ] WalletController 充值/消费/退款 3 个测试通过
- [ ] PosController 钱包支付路径测试通过
- [ ] 端到端：创建会员 → 充值 → 消费 → 退款 → 余额对账
- [ ] 并发：同一会员 100 个并发钱包消费，余额正确

---

## 4. 风险与回滚

### 风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| 删除 setBalance 后遗漏某些代码路径 | 中 | 高 | 单测覆盖 + 集成测试 + 灰度 |
| PosController 改调用后接口性能下降 | 低 | 低 | WalletController.doConsume 内部已有缓存 |
| 漂移对账脚本写错导致数据破坏 | 低 | 极高 | 修复脚本先 dry-run，再正式执行；执行前备份 |
| 前端调用 /wallet/{memberId} 增加 N+1 | 中 | 中 | 列表用 crm_member.balance（同步视图）+ 详情用 /wallet/{id} |

### 回滚

- 删除的 3 个 setBalance 在 git 历史中可恢复
- PosController 重复路径同样可恢复
- 数据库漂移已修复，回滚代码不影响数据正确性

---

## 5. 不在本批次范围

| 事项 | 说明 |
|---|---|
| 数据库 trigger 同步 crm_member.balance | 需要 DBA 评审，本批次仅做应用层同步 |
| 删除 crm_member.balance 列 | 风险评估中，建议 R2.1 或 R3.0 实施 |
| 钱包历史流水压缩 | crm_wallet_transaction 数据已 142 条，体积小 |
| 多租户分库后余额路由 | 高级特性，等 P3 实施 |

---

## 6. 决策签字

| 角色 | 签字 | 日期 |
|---|---|---|
| 技术负责人 | Cursor Agent | 2026-08-15 |
| 待评审 | BE Lead | |
| 待评审 | DBA | |
| 待评审 | 业务 | |