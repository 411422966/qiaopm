# T14_FINAL_REGRESSION_PLAN

> **T14 状态：🟡 PLAN_READY — 未启动**
> **生成日期**：2026-08-16 00:24 UTC+8
> **上游文档**：P0_FINAL_CLOSEOUT_REPORT.md · T13_PHASE1_COMPLETION_REPORT.md · T12_COMPLETION_REPORT.md
> **本文档定位**：仅计划；执行由人工批准后启动

---

## 0 · 计划原则

> **T14 不修改任何代码。**
> **T14 不引入新功能。**
> **T14 不扩展 DCEP 银行接入。**
> **T14 仅做回归验证 + 封版决策。**

---

## 1 · Regression Scope（回归范围）

### 1.1 代码范围（零修改）

| 范围 | 状态 |
|---|---|
| `backend/src/main/java/com/meiyetec/dcep/**` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/config/DcepConfigProperties.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/service/DcepService.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/dcep/impl/{Ccb,Abc,Icbc}DcepAdapter.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/dcep/{AbstractDcepAdapter,DcepChannelAdapter,DcepAdapterFactory}.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/entity/PayDcepChannel.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/service/impl/WalletServiceImpl.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/service/impl/BookingServiceImpl.java` | 🔒 FROZEN |
| `backend/src/main/java/com/meiyetec/service/PosRefundService.java` | 🔒 FROZEN |
| `backend/src/main/resources/db/**`（除 T12 迁移 SQL） | 🔒 FROZEN |
| `frontend/meiyetec-web/**` | 🔒 FROZEN |

### 1.2 数据库范围（只读）

| 范围 | 动作 |
|---|---|
| `pay_dcep_channel.env_mode` CHECK 约束 | 仅验证存在 |
| `crm_wallet.tenant_id` | 仅统计 NULL 数 |
| `pos_refund_reverse_log.ref_id` | 仅统计分布 |
| `sys_config` 表 | 仅验证可读 |
| 5 个 schema drift 表 | 仅验证状态（不修复） |

### 1.3 测试范围（执行）

| 测试集 | 期望 |
|---|---|
| DCEP 隔离 | 66/66 PASS |
| Wallet SSOT 隔离 | 13/13 PASS |
| T08 7 条租户隔离 | 7/7 PASS |
| 全量 `mvn test` | ≥ 95% PASS（接受 baseline drift） |
| E2E Golden Path | 5 pass + 1 flaky |

### 1.4 文档范围（产出）

| 文档 | 产出 |
|---|---|
| `T14_FINAL_REGRESSION_REPORT.md` | T14 执行后由人工创建 |
| 本计划文档 | 已存在 |

---

## 2 · Frozen Boundary Check（冻结边界验证）

### 2.1 边界清单（每条都需在 T14 验证 UNCHANGED）

| # | 文件/类 | 验证方式 | 期望 |
|---|---|---|---|
| F-1 | `DcepConfigProperties.validate()` | `grep` + diff | 无 diff |
| F-2 | `DcepService.verifySign()` dev 分支 | `grep "MOCK- prefix"` | 仍 fail-fast |
| F-3 | `pay_dcep_channel.env_mode` CHECK | `SHOW CREATE TABLE` | CHECK 存在 |
| F-4 | `dcepRuntimeReady` 字段 | `grep "dcepRuntimeReady"` | 仍存在 |
| F-5 | `DcepService` 5 个路由私有方法 | `grep` 方法签名 | 仍存在 |
| F-6 | `DcepAdapterFactory` 路由逻辑 | `grep` map | 仍按 channelCode |
| F-7 | `AbstractDcepAdapter.computeSign()` | `grep` SHA-256 | 仍占位 SHA-256 |
| F-8 | `CcbDcepAdapter` 5 方法 | `grep "NOT_IMPLEMENTED"` | 仍占位 |
| F-9 | `WalletServiceImpl.insertIfAbsent` | `grep "resolveTenantIdForInsert"` | 仍 ctx 注入 |
| F-10 | 14 处 `setTenantId(1L)` | `grep -c "setTenantId(1L)"` | 14 → D4 1 处 + WARN |
| F-11 | 9 个 DDL 迁移文件 | `ls migrate_*` | 仍存在 |
| F-12 | T12 SQL `ck_pay_dcep_channel_env_mode` | `grep "ck_pay_dcep_channel"` | 仍存在 |

### 2.2 边界外禁止

| 禁止 | 类别 |
|---|---|
| ❌ 修改任何 frozen 文件 | 强制 |
| ❌ 新增 DCEP 银行 SDK 依赖 | 强制（外部 BLOCKED） |
| ❌ 修改 `pay_dcep_channel` 数据 | 强制（T12 历史脏数据已修复） |
| ❌ 修改 tenant fixture | 强制（T08 已稳定） |
| ❌ 修改 E2E 测试代码 | 强制（T11 已稳定） |

---

## 3 · Test Matrix（测试矩阵）

### 3.1 必须通过的测试

| 优先级 | 测试 | 期望 | 阻断 P0 |
|---|---|---|---|
| 🔴 P0 | Dcep* 全套（66 用例） | 66/66 PASS | ✅ 失败即阻断 |
| 🔴 P0 | WalletSingleSourceTest（8） | 8/8 PASS | ✅ 失败即阻断 |
| 🔴 P0 | WalletTenantInsertTest（5） | 5/5 PASS | ✅ 失败即阻断 |
| 🔴 P0 | T08 PosControllerTenantIsolationTest（7） | 7/7 PASS | ✅ 失败即阻断 |
| 🔴 P0 | DcepStartupValidateTest（8） | 8/8 PASS | ✅ 失败即阻断 |
| 🟡 P1 | MultiTenantWalletTest（6） | 6/6 PASS | ⚠️ 失败需 review |
| 🟡 P1 | ConcurrencyIntegrationTest（3） | 2/3 PASS（1 error 已知） | ⚠️ |
| 🟡 P1 | PosRefundIntegrationTest（5） | 4/5 PASS（1 error 已知） | ⚠️ |
| � P1 | WalletNullTenantBackfillTest（10） | 8/10 PASS（baseline drift） | ⚠️ |
| 🟡 P1 | RefundReverseLogBackfillTest（12） | 7/12 PASS（baseline drift） | ⚠️ |
| 🟡 P1 | T06ReverseLogRefIdTest（10） | 9/10 PASS（baseline drift） | ⚠️ |
| 🟢 P2 | 全量 `mvn test`（933） | ≥ 95% PASS | ⚠️ |
| 🟢 P2 | E2E Golden Path（5+1） | 5 pass + 1 flaky | �️ |

### 3.2 不在测试范围

| 不测 | 原因 |
|---|---|
| 真实 CCB SDK 调用 | EXTERNAL BLOCKED |
| 真实 SM2 签名 | EXTERNAL BLOCKED |
| K8s + Vault 部署 | EXTERNAL BLOCKED |
| 生产 `app.dcep.mode=prod` | EXTERNAL BLOCKED |

### 3.3 测试执行顺序

1. **第一轮**：DCEP 隔离 → 期望 66/66
2. **第二轮**：Wallet SSOT 隔离 → 期望 13/13
3. **第三轮**：T08 租户隔离 → 期望 7/7
4. **第四轮**：联合 P0 关键链路（86 用例） → 期望全过
5. **第五轮**：全量回归（933 用例）→ 期望 ≥ 95%
6. **第六轮**：E2E Golden Path → 期望 5+1

---

## 4 · Acceptance Criteria（验收标准）

### 4.1 P0 封版验收（必须全部满足）

| # | 标准 | 验证 |
|---|---|---|
| AC-1 | T01-T12 全部 PASS 且 FROZEN | P0_FINAL_CLOSEOUT_REPORT §1 |
| AC-2 | T13 P1 Adapter Runtime PASS 且 FROZEN | T13_PHASE1_COMPLETION_REPORT |
| AC-3 | T13 P2 EXTERNAL BLOCKED 已记录 | P0_FINAL_CLOSEOUT_REPORT §6 |
| AC-4 | DCEP 隔离 66/66 PASS | mvn test -Dtest=Dcep* |
| AC-5 | Wallet SSOT 隔离 13/13 PASS | mvn test -Dtest=WalletSingleSourceTest,WalletTenantInsertTest |
| AC-6 | 4 类 frozen boundary 全部 UNCHANGED | Frozen Boundary Check |
| AC-7 | Reality Score ≥ 60% | P0_FINAL_CLOSEOUT_REPORT §4 |
| AC-8 | 治理文档已归档 | docs/audit/BEAUTY_SAAS/*.md |

### 4.2 已知可接受失败

| 测试 | 失败数 | 接受理由 |
|---|---|---|
| `ConcurrencyIntegrationTest` | ≤ 2 | baseline drift（非 P0 修复引入） |
| `PosRefundIntegrationTest` | ≤ 1 | optimistic lock CAS 共享状态 |
| `RefundReverseLogBackfillTest` | ≤ 5 | 历史回填累计行数变化 |
| `T06ReverseLogRefIdTest` | ≤ 1 | t05 fixture 共享 |
| `WalletNullTenantBackfillTest` | ≤ 2 | SSOT 互扰 |

### 4.3 不可接受失败（任一即阻断 P0）

| 失败类型 | 处理 |
|---|---|
| DCEP 隔离 66/66 失败 | ⛔ 立即停止；恢复 T13 P1 frozen |
| T12 测试失败 | ⛔ 立即停止；恢复 T12 frozen |
| 已知 baseline drift 扩大 | ⛔ 停止 P0 封版决策；进入数据修复 |
| 新增 schema drift | ⛔ 停止 P0 封版决策 |
| T08 租户隔离失败 | ⛔ 立即停止；恢复 T08 frozen |

---

## 5 · Release Freeze Criteria（发布冻结标准）

### 5.1 发布条件

| # | 条件 | 状态 |
|---|---|---|
| RF-1 | AC-1 ~ AC-8 全部满足 | T14 验证 |
| RF-2 | T14 执行报告已签发 | T14 输出 |
| RF-3 | 治理文档归档完成 | 已完成 |
| RF-4 | 外部依赖跟踪文件已建 | T13_EXTERNAL_DEPENDENCY_TRACKER.md |
| RF-5 | 决策 Gate 0/16 已记录 | T13_PHASE2_DECISION_GATE.md |
| RF-6 | `app.dcep.mode` 配置已确认 | `dev`（生产前必须改 `prod`） |

### 5.2 发布决策表

| 决策 | 触发 | 处置 |
|---|---|---|
| ✅ RELEASE CANDIDATE | RF-1~6 全满足 | 进入候选发布 |
| � CONDITIONAL | RF-3 缺一项 | 补齐后重审 |
| � BLOCKED | 任一 🔴 失败 | 停止发布；走修复 |

### 5.3 发布版本

| 字段 | 值 |
|---|---|
| Version | P0-FINAL-CANDIDATE-v1.0 |
| Build | T14 签发时填入 |
| Mode | dev / sandbox（生产前改 prod） |
| Adapter | 0 个真实 / 3 个占位 NOT_IMPLEMENTED |

### 5.4 发布后限制（重要）

| 限制 | 说明 |
|---|---|
| ⚠️ 生产部署必须改 `app.dcep.mode=prod` | 当前 dev 仅可 sandbox |
| ⚠️ 任何真实支付必须先解锁 T13 P2 | EXTERNAL BLOCKED |
| ⚠️ DCEP 数币支付仅 demo 可用 | 真实支付需银行依赖 |

---

## 6 · T14 执行步骤（计划）

### 6.1 启动条件

```
✅ P0 封版报告已签发
✅ T14 计划已生成（本文档）
✅ 人工批准启动
```

### 6.2 执行步骤

| Step | 内容 | 命令 | 期望 |
|---|---|---|---|
| 1 | 边界验证 | 12 项 grep/diff | F-1~F-12 全部 UNCHANGED |
| 2 | DCEP 隔离 | `mvn test -Dtest=Dcep*` | 66/66 PASS |
| 3 | Wallet SSOT 隔离 | `mvn test -Dtest=WalletSingleSourceTest,WalletTenantInsertTest` | 13/13 PASS |
| 4 | T08 验证 | `mvn test -Dtest=PosControllerTenantIsolationTest` | 7/7 PASS |
| 5 | 联合 P0 关键 | `mvn test -Dtest=Dcep*,WalletSingleSourceTest,WalletTenantInsertTest,PosControllerTenantIsolationTest` | 86/86 PASS |
| 6 | 全量回归 | `mvn test` | ≥ 95% PASS |
| 7 | E2E Golden Path | `npx playwright test --grep "Golden Path"` | 5 pass + 1 flaky |
| 8 | 输出 T14 报告 | `T14_FINAL_REGRESSION_REPORT.md` | 完整 |
| 9 | 签发决策 | RF-1~6 check | ✅ / 🟡 / ⛔ |

### 6.3 T14 输出文档结构（执行后产出）

```markdown
# T14_FINAL_REGRESSION_REPORT
## 1 · 执行时间
## 2 · Frozen Boundary 12 项验证结果
## 3 · 测试矩阵执行结果（按 AC-1~8）
## 4 · 已知 baseline drift 状态
## 5 · 决策（RELEASE / CONDITIONAL / BLOCKED）
## 6 · 下一步
```

---

## 7 · T14 不做的事

| 不做 | 原因 |
|---|---|
| ❌ 修改 frozen 代码 | 强制 |
| ❌ 新增治理文档 | P0 封版已完成 |
| ❌ 继续拆 T13 Phase 2 | EXTERNAL BLOCKED |
| ❌ 模拟银行 SDK | 强制 |
| ❌ 修改业务逻辑 | 强制 |
| ❌ 引入新依赖 | 强制 |

---

## 8 · 启动责任

| 角色 | 责任 |
|---|---|
| **人工** | 批准 T14 启动；执行步骤 1-9；签发 T14 报告 |
| **Cursor** | 仅执行 mvn test / grep 命令；不修改任何代码 |
| **DevOps** | 同步生产环境（如有） |

---

## 9 · T14 与后续阶段

| 阶段 | 状态 |
|---|---|
| T14 Final Regression | � PLAN_READY（本文档） |
| T13 P2.x | ⛔ EXTERNAL BLOCKED（待外部依赖） |
| P1 业务扩展 | ⏸ P0 封版后由 PM 决定 |

---

*END OF T14_FINAL_REGRESSION_PLAN — Generated 2026-08-16 00:24 UTC+8 — 仅计划；执行待人工批准*