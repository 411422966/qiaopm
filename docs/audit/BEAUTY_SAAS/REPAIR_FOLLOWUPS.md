# REPAIR_FOLLOWUPS

> **Scope control**: 本任务（P0-MONEY-004 Tenant Isolation Fix）范围之外发现的问题记录于此。
> **不修改，仅记录**。后续 P0 任务按 Roadmap 顺序处理。

---

## 发现日期: 2026-08-13

---

## F1. [UNVERIFIED] MyBatis-Plus 自动填充策略

**Source**: Step 1 数据审计过程中发现
**Symptoms**: `pos_order` / `pos_payment` / `pos_order_item` 三表共 188+186+108=482 行 tenant_id=1，但 `PosController` 显式代码中**没有** `setTenantId()`。
**Hypothesis**: 项目可能通过 MyBatis-Plus MetaObjectHandler 拦截器自动填 tenant_id；或 依赖 DB 列 DEFAULT 1。
**证据缺口**: 项目未发现 `MybatisFieldHandler` 类注册；DB DEFAULT 1 不足以解释 behaviour（如 `tenant_id=2` 加入后是否回填 2）。
**风险**: 未来加入 Tenant 2 时，pos_order 等表可能仍被错误填 1。
**关联**: 与 P0-MONEY-004 不同问题路径。
**不在 P0-MONEY-004 范围内**: 此问题不在 PosController 钱包 INSERT 路径上。

## F2. [P1] crm_wallet 44 行 tenant_id=NULL

**Source**: Step 1 SQL 审计
**Symptoms**: `crm_wallet` 表 102 行，其中 44 行 `tenant_id IS NULL`，58 行 tenant_id=1
**Root cause**: `WalletController.ensureWallet` 路径未填 tenant_id（与 PosController 钱包路径隔离）
**关联**: TASK-P0-MONEY-002 钱包双轨问题
**不在 P0-MONEY-004 范围内**: 不在 PosController 写入路径上。
**修复方案**: 由 `WalletController.ensureWallet` 单独修复（已知 issue，已记录）。

## F3. [P0] DCEP 数字人民币 Mock

**Source**: STA-002 Reality Audit
**Symptoms**: `service/DcepService.java` 的 `mockPrepay()` 与 `verifySign()` 是 mock 实现
**关联**: P0-MONEY-001
**状态**: 已记录，独立任务处理。

## F4. [P0] DCEP transaction ID 使用 Math.random()

**Source**: STA-002 Reality Audit
**Symptoms**: `PosController.java:321` `transactionId = "ECNY-" + System.currentTimeMillis() + "-" + (int)(Math.random()*10000);`
**修复方向**: 替换为 Snowflake ID 生成器
**关联**: P0-MONEY-003
**状态**: 已记录，独立任务处理。

## F5. [P0] 数据库 FK 缺失

**Source**: V3 Audit
**Symptoms**: 11 张表（crm_member, crm_package, crm_wallet, crm_package_order, pos_order, pos_order_item, pos_payment, pos_refund, pos_refund_item, sys_user, sys_role_data_scope）9 关键关系无 FK
**关联**: P0-DB-001
**状态**: 已记录，独立任务处理。

## F6. [P0] sys_login_log 表不存在

**Source**: Step 1 SQL 审计
**Symptoms**: `mysql> SHOW COLUMNS FROM sys_login_log` → `ERROR 1146 (42S02) at line 4: Table 'meiyetec.sys_login_log' doesn't exist`
**关联**: P0-DB-001
**状态**: 已记录，独立任务处理。

## F7. [P2] POS related Shell Services

**Source**: V2 Audit
**Symptoms**: 11 个 ServiceImpl 是 empty shell（仅 `extends ServiceImpl`）
**关联**: P1-V3-REPAIR
**状态**: 已记录。

## F8. [P2] E2E 测试缺失

**Source**: V2 Audit
**Symptoms**: 无 E2E 测试
**关联**: P2-TEST-001
**状态**: 已记录。

## F9. [P2] 测试覆盖率 < 5%

**Source**: V2 Audit
**Symptoms**: 413 source files 仅 5 个 test files
**关联**: P2-TEST-002
**状态**: 已记录。

## F10. [INFO] pre-existing bugs in PosController HTTP path

**Source**: Step 7 运行时验证
**Symptoms**:
- `POST /pos/orders` 返回 "会员不存在" 当 tenant=2 用户调用
- `POST /wallet/recharge` 触发 `MybatisParameterHandler` `MP_OPTLOCK_VERSION_ORIGINAL not found` 异常
**Root cause**: 与本次修复无关的预存 bug
**不在 P0-MONEY-004 范围内**: 验证修复时使用了 HTTP 路径触发的预存问题
**验证绕过**: P0-MONEY-004 的修复通过 JWT 链路 + 7 个单元测试已经验证

---

## 索引

| ID | 严重度 | 关联 P0 任务 | 状态 |
|----|--------|--------------|------|
| F1 | UNVERIFIED | (新任务) | 待评估 |
| F2 | P1 | P0-MONEY-002 | 已知 |
| F3 | P0 | P0-MONEY-001 | 待处理 |
| F4 | P0 | P0-MONEY-003 | 待处理 |
| F5 | P0 | P0-DB-001 | 待处理 |
| F6 | P0 | P0-DB-001 | 待处理 |
| F7 | P2 | (P1-V3-REPAIR) | 待处理 |
| F8 | P2 | (P2-TEST-001) | 待处理 |
| F9 | P2 | (P2-TEST-002) | 待处理 |
| F10 | P2 | (pre-existing) | 已记录 |

---

**最后更新**: 2026-08-13 23:42
**下次**: P0-DB-001 (按 Roadmap 顺序)
