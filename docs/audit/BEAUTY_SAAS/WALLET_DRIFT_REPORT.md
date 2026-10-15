# WALLET_DRIFT_REPORT — 钱包漂移对账报告

> **批次**：4 / 8 — P0-MONEY-002 钱包双轨收敛
> **生成时间**：2026-08-15
> **执行人**：Cursor Agent
> **状态**：✅ 全部修复（0 漂移）

---

## 1. 执行摘要

| 项目 | 值 |
|---|---|
| 漂移成员数（修复前） | **8** |
| 总差异绝对值 | **39,152.00 元** |
| 最大单笔差异 | **13,400.00 元**（member_id=6 赵晓某）|
| 最小单笔差异 | 200.00 元（member_id=5,7）|
| 修复行数 | 8 |
| 修复后剩余漂移数 | **0** |
| 数据备份 | `crm_wallet_drift_log.PRE_FIX_BACKUP` (8 条) |

---

## 2. 漂移明细（修复前）

| member_id | 姓名 | 手机 | member_balance (旧) | wallet_balance (SSOT) | diff |
|---|---|---|---:|---:|---:|
| 6 | 赵晓某 | 13800138006 | 15,600.00 | **2,200.00** | 13,400.00 ⚠️ |
| 4 | 陈丽某 | 13800138004 | 12,800.00 | **3,300.00** | 9,500.00 |
| 1 | 李雅某 | 13800138001 | 5,800.00 | **-298.00** | 6,098.00 |
| 8 | 周雪某 | 13800138008 | 4,500.00 | **-168.00** | 4,668.00 |
| 2 | 王晓某 | 13800138002 | 2,200.00 | **-198.00** | 2,398.00 |
| 5 | 刘莎某 | 13800138005 | 3,500.00 | **5,500.00** | -2,000.00 |
| 3 | 张敏某 | 13800138003 | 500.00 | **-388.00** | 888.00 |
| 7 | 孙美某 | 13800138007 | 800.00 | **1,000.00** | -200.00 |

**总差异绝对值**: 39,152.00 元
**SSOT 选择**: crm_wallet.balance（已加乐观锁 + 版本号）

---

## 3. 漂移根因分析

### 3.1 PosController 钱包路径（L286-303 旧代码）

**问题代码**：

```java
case "wallet" -> {
    Member m = memberService.getById(order.getMemberId());
    if (m.getBalance().compareTo(amount) < 0) { ... }
    BigDecimal before = m.getBalance();
    BigDecimal after = before.subtract(amount);
    m.setBalance(after);                          // ❌ 改 crm_member.balance
    m.setTotalConsume(...);
    memberService.updateById(m);                  // ❌ 无乐观锁
    
    jdbc.update("INSERT INTO crm_wallet_transaction ...");
    // ⚠️ **没有更新 crm_wallet.balance！**
}
```

**根因**：
1. **只写 crm_wallet_transaction 流水，完全不更新 crm_wallet.balance**
2. **绕过 WalletController.doConsume 的 CAS 乐观锁 + 幂等保护**
3. **改的是 crm_member.balance，crm_wallet.balance 永远不增加/减少**

**结果**：crm_member.balance 一直累加（看起来"充值多"），crm_wallet.balance 一直是初始值或负数。这就是为什么 member_id=6 显示 15,600 而 crm_wallet 只有 2,200。

### 3.2 WalletController 3 个 setBalance 块（L150-152, L197-200, L249-254 旧代码）

**问题**：写完 crm_wallet 后**再写** crm_member.balance，两次写非原子操作。

**根因**：
- `casUpdate` 成功后 `memberService.updateById(m)` 走 MyBatis-Plus 默认 update
- 中间无 `@Transactional` 包裹整个流程（虽然在 `doRecharge` 上有注解，但其中 `ensureWallet` 调用可能在事务外读 stale 数据）
- 若中途异常，`crm_wallet` 回滚而 `crm_member` 已更新 → 漂移

### 3.3 中间表多次写入冲突

历史订单导入、SQL 脚本修复（如批次 1 的 patch_p0_db_001_data_repair.sql）等多处直接操作 crm_member.balance，导致漂移累积。

---

## 4. 修复决策

### 4.1 SSOT 选择

**crm_wallet.balance** 选为唯一权威源（SSOT），理由：

| 维度 | crm_wallet | crm_member |
|---|---|---|
| 乐观锁 version | ✅ | ❌ |
| 唯一约束 uk_member | ✅ | ❌ |
| 外键 fk_wallet_member | ✅ | ❌ |
| 流水审计 (crm_wallet_transaction) | ✅ | ❌ |
| 写入频率 | 少（每次钱包操作） | 多（订单、导入、SQL 脚本） |
| 字段专用性 | 专用钱包字段 | 会员主表兼用 |

**结论**：crm_wallet 是设计上的"钱包主表"，crm_member.balance 是历史遗留的"冗余同步字段"。

### 4.2 crm_member.balance 处理（方案 B）

**保留字段**作为只读同步视图，理由：
- 前端列表/详情已有大量 `member.balance` 调用
- 删除列会引发 N 个前端文件改动 + MyBatis 实体清理 + MyBatis Mapper SQL 调整
- 回归风险高

**实施**：
- ✅ `WalletServiceImpl.syncMemberBalance(memberId)` 唯一同步入口
- ✅ 任何钱包写操作完成后自动调用此方法
- ❌ 严禁其他代码直接写 crm_member.balance

---

## 5. 修复执行

### 5.1 脚本

`backend/src/main/resources/db/sql/migrate_v0_p0_money_002_wallet_reconciliation.sql`

**7 个步骤**：
1. DRY-RUN 漂移检测（建视图 v_wallet_drift）
2. 备份漂移数据到 `crm_wallet_drift_log` 表（8 条 PRE_FIX_BACKUP）
3. AUTO-FIX：以 crm_wallet.balance 更新 crm_member.balance
4. 验证漂移 = 0
5. 写 AUTO_FIX 修复日志
6. 输出对账报告（人读）
7. 提供后续 crontab 巡检建议

### 5.2 关键 SQL

```sql
UPDATE crm_member m
INNER JOIN crm_wallet w ON w.member_id = m.id AND w.deleted = 0
SET m.balance = w.balance
WHERE m.deleted = 0
  AND (m.balance IS NULL OR m.balance != w.balance);
```

### 5.3 修复结果

```
drift_type | cnt | total_diff_abs
DRIFT      |  8  | 39152.00
─────────────────────────────────
fixed_rows |  8
POST_FIX_RECHECK remaining_drift_count | 0  ✅
```

---

## 6. 代码层修复

### 6.1 WalletController.java（M2-2）

- 删除 L150-152（recharge 内 m.setBalance）
- 删除 L197-200（consume 内 m.setBalance）
- 删除 L249-254（refund 内 m.setBalance）
- 改为统一调用 `syncMemberBalance(memberId)`（单一入口）
- 保留 totalConsume 累加（会员累计消费非钱包余额范畴）

### 6.2 WalletService.java + WalletServiceImpl.java

**新建接口扩展**：

```java
public interface WalletService extends IService<Wallet> {
    Result<Map<String, Object>> doRecharge(WalletOpDTO dto, Long tenantId);
    Result<Map<String, Object>> doConsume(WalletOpDTO dto, Long tenantId);
    Result<Map<String, Object>> doRefund(WalletOpDTO dto, Long tenantId);
    void syncMemberBalance(Long memberId);  // P0-MONEY-002 SSOT 唯一入口
}
```

**WalletServiceImpl 实现**：3 个写操作下沉到 Service 层，WalletController 退化为 thin wrapper。

### 6.3 PosController.java（M2-3）

**删除** L286-303 钱包重复路径（直接改 crm_member.balance + 写 crm_wallet_transaction 但不更新 crm_wallet.balance）

**改为**调用 `WalletService.doConsume(dto, tenantId)`：
- 走 CAS 乐观锁
- 走 Idempotency-Key
- 走 syncMemberBalance 自动同步 crm_member.balance

### 6.4 前端 checkout.vue（M2-5）

- 选会员后立即调用 `walletApi.getBalance(memberId)` 拿 SSOT 权威值
- `member.balance = res.data.balance` 覆盖冗余字段
- 余额展示加 tooltip："余额来自 crm_wallet.balance（SSOT）"
- 兜底：拉取失败时保留 `member.balance`（不阻断选会员流程）

---

## 7. 后续预防

### 7.1 数据库巡检（建议加入 crontab）

```bash
0 3 * * 1  /usr/local/bin/mysql ... -e \
  "SELECT COUNT(*) FROM v_wallet_drift;" | \
  [ "$drift" -gt 0 ] && alert "钱包漂移告警"
```

### 7.2 应用层监控

- 在 `syncMemberBalance` 内增加 Prometheus 指标
- 记录每次同步操作：`[WALLET-SSOT] sync crm_member.balance=X from crm_wallet memberId=Y`
- 监控 `crm_member.balance != crm_wallet.balance` 的事件数

### 7.3 评审闸门

- 任何 PR 修改 crm_member.balance 必须 @技术负责人审批
- 任何 PR 在 crm_wallet 之外引入"balance"字段必须评审
- WalletService 是唯一可写 crm_wallet.balance 的入口

---

## 8. 验证清单

- [x] 漂移修复脚本可重入（`v_wallet_drift` 视图每次查询实时结果）
- [x] 备份数据完整（PRE_FIX_BACKUP 8 条）
- [x] 修复后漂移数 = 0
- [x] WalletController.doRecharge/doConsume/doRefund 编译通过
- [x] PosController 钱包路径改走 WalletService.doConsume
- [x] WalletServiceImpl 注入并可用
- [x] 前端 checkout.vue 拉取 /wallet/{memberId} 显示余额
- [ ] WalletSingleSourceTest 集成测试（M2-7）
- [ ] 端到端：充值 → 消费 → 退款 → 余额对账
- [ ] 并发：100 并发钱包消费

---

## 9. 相关文档

- 决策文档：`docs/audit/BEAUTY_SAAS/SINGLE_SOURCE_OF_TRUTH_WALLET.md`
- 修复脚本：`backend/src/main/resources/db/sql/migrate_v0_p0_money_002_wallet_reconciliation.sql`
- 漂移日志表：`crm_wallet_drift_log`（数据库内）
- 执行报告：`docs/EXEC-R2.0-FIX-P0-MONEY-002.md`（M2-7 完成后生成）

---

## 10. 备注

- 用户原描述「29 / -32,152」与实际不符，实际为 **8 / 39,152**
- 历史漂移累积原因主要是 PosController 钱包路径长期不更新 crm_wallet.balance
- 修复策略：保留 crm_member.balance 作只读视图，写路径收敛到 WalletService
- 长期方案：考虑 R2.1 删除 crm_member.balance 列（需前端 + MyBatis 调整 N 个文件）