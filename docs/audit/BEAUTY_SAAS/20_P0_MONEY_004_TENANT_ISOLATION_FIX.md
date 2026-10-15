# P0-MONEY-004 Tenant Isolation Fix

**Status: DONE**
**Date: 2026-08-13**

---

## 1. Problem

`PosController.java` 中两处 crm_wallet_transaction INSERT 语句使用硬编码 `tenant_id = 1L`，导致任何租户触发的 POS 钱包消费 / 退款路径写入的 `crm_wallet_transaction.tenant_id` 都被错标为 1，破坏多租户隔离。

无论调用方来自哪个租户，钱包交易始终记录在 tenant_id=1 下，导致：
- 数据归属错误
- 跨租户报表污染
- 跨租户对账错位
- 多租户开启后所有租户的钱包交易被合并到租户 1 名下

---

## 2. Root Cause

历史代码在钱包交易 INSERT 路径上**直接硬编码 `1L`**，未经过任何 Tenant Resolution：

```java
// BEFORE (PosController.java ~ line 300)
jdbc.update(
    "INSERT INTO crm_wallet_transaction(tenant_id, ...) VALUES(?,?,?,...)",
    1L, ...  // ❌ 硬编码 tenant_id = 1
);

// BEFORE (PosController.java ~ line 395)
jdbc.update(
    "INSERT INTO crm_wallet_transaction(tenant_id, ...) VALUES(?,?,?,...)",
    1L, ...  // ❌ 硬编码 tenant_id = 1
);
```

这与项目内其它 60+ 处使用 `TenantContextHolder.get().getTenantId()` 的位置不一致。

---

## 3. Before

| 项目 | 状态 |
|------|------|
| Tenant 1 调用 POS 钱包消费 | 写入 `crm_wallet_transaction.tenant_id=1`（侥幸正确） |
| Tenant 2 调用 POS 钱包消费 | 写入 `crm_wallet_transaction.tenant_id=1`（**错误**） |
| Tenant 2 调用 POS 钱包退款 | 写入 `crm_wallet_transaction.tenant_id=1`（**错误**） |
| 跨租户查询 | 退化为"只看 tenant_id=1"，数据混合 |

---

## 4. Tenant Resolution

### 4.1 已采用的机制

```java
Long tenantId = TenantContextHolder.get().getTenantId();
```

### 4.2 信任来源解析

| 层级 | 来源 | 证据 |
|------|------|------|
| JWT 解析 | `util/JwtUtil.java` | `extractUserId(token)` |
| 拦截器 | `interceptor/JwtInterceptor.java:38-72` | 调用 `rbacContextLoader.load(userId)` |
| 上下文加载 | `security/RbacContextLoader.java:45-58` | `ctx.setTenantId(user.getTenantId())` |
| ThreadLocal 持有 | `security/TenantContextHolder.java:22,28-35` | `private static final ThreadLocal<TenantContext> CTX` |
| 调用点 | `controller/PosController.java:298, 394` | `TenantContextHolder.get().getTenantId()` |

### 4.3 拒绝的替代方案

| 方案 | 拒绝原因 |
|------|----------|
| 解析 JWT claims 取 tenantId | 项目 JWT 仅含 userId+username |
| 新增第二套 TenantContext | 违反统一原则 |
| Controller 自己解析 JWT | 与 JwtInterceptor 重复 |
| 前端传入 tenantId | 可被伪造，存在 P0 安全漏洞 |
| Operator/Store 推断 tenant | 增加 N+1 查询，且破坏已有契约 |

### 4.4 复用项目现有机制

项目内在 60+ 处使用此模式（12+ Service 类，6+ Controller）：

- `service/CustomerCareService.java` — 10 处
- `service/ReportV2Service.java` — 10 处
- `service/MarketingAutomationService.java` — 12 处
- `service/DcepService.java` — 6 处
- `service/InventoryConsumptionService.java` — 1 处
- `controller/WalletController.java:86,97,108` — 3 处
- `controller/BookingController.java:249` — 1 处
- …

**无新增第二套机制**，仅消除 PosController 中的离群值。

---

## 5. Code Changes

### 5.1 `backend/src/main/java/com/meiyetec/controller/PosController.java`

| 位置 | 改动 | Before → After |
|------|------|----------------|
| Line 20 | 新增 import | `import com.meiyetec.security.TenantContextHolder;` |
| Line 298 | 新增局部变量 | `Long walletTxnTenantId = TenantContextHolder.get().getTenantId();` |
| Line 302 | 替换参数 | `1L` → `walletTxnTenantId` |
| Line 394 | 新增局部变量 | `Long walletRefundTenantId = TenantContextHolder.get().getTenantId();` |
| Line 398 | 替换参数 | `1L` → `walletRefundTenantId`（**保留第二个 1L=operator_id，未触碰**） |

### 5.2 精确 Diff（关键部分）

```java
// 钱包消费路径 (processOnePayment "wallet" branch)
                    memberService.updateById(m);
                    Long walletTxnTenantId = TenantContextHolder.get().getTenantId();  // NEW
                    jdbc.update(
                        "INSERT INTO crm_wallet_transaction(tenant_id, member_id, type, ...) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        walletTxnTenantId, m.getId(), 2, amount, ...);  // CHANGED: 1L → walletTxnTenantId

// 钱包退款路径 (refund method)
            memberService.updateById(m);

            Long walletRefundTenantId = TenantContextHolder.get().getTenantId();  // NEW
            jdbc.update(
                "INSERT INTO crm_wallet_transaction(tenant_id, member_id, type, ...) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                walletRefundTenantId, m.getId(), 3, returnAmt, ..., 1L,  // CHANGED: 1L → walletRefundTenantId (operator_id 1L preserved)
                "订单退款:" + order.getOrderNo(), LocalDateTime.now());
```

### 5.3 编译验证

```
[INFO] BUILD SUCCESS
[INFO] Compiling 413 source files with javac [debug release 17] to target\classes
[INFO] Total time:  16.267 s
```

新增测试文件：

```java
// backend/src/test/java/com/meiyetec/controller/PosControllerTenantIsolationTest.java
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PosControllerTenantIsolationTest

// 7 个测试方法
// TEST-001: tenantAConsumeWritesTenantA
// TEST-002: tenantBConsumeWritesTenantB
// TEST-003: tenantACannotSeeTenantBData
// TEST-004: tenantBCannotSeeTenantAData
// TEST-005: spoofedTenantIdIgnored
// TEST-006: noTenantContextFails
// TEST-007: tenantOneNotSpecial
```

---

## 6. Historical Data Audit

**审计脚本**:
- `audit_tenant_pollution.sql`
- `audit_tenant_pollution_v2.sql`
- `TENANT_DATA_CONTAMINATION_AUDIT.md`

### 6.1 当前 sys_tenant 状态

```sql
SELECT * FROM sys_tenant;
-- 1	default	默认租户	1
-- 2	tenant_b	租户B	1  (Test setup)
```

**结论**: 生产中只有 1 个租户 (id=1)，多租户引入风险源自**未来写入**而非历史数据。

### 6.2 历史数据污染检查

| 表 | tenant_id=1 行数 | 跨租户污染 |
|----|------------------|------------|
| `crm_wallet_transaction` | 24 / 24 | 0（仅 1 个租户） |
| `crm_wallet` | 58 / 102 | 0（44 行 NULL 来自 `WalletController.ensureWallet`） |
| `pos_order` | 188 / 188 | 数据来源未审计（UNVERIFIED） |
| `pos_payment` | 186 / 186 | UNVERIFIED |
| `pos_order_item` | 108 / 108 | UNVERIFIED |

### 6.3 污染交叉验证

```sql
SELECT COUNT(*) FROM crm_wallet_transaction wt
JOIN crm_member m ON m.id = wt.member_id
WHERE wt.tenant_id <> m.tenant_id;
-- 0
```

**当前不存在显性跨租户污染**（仅因只有 1 个租户）。

### 6.4 历史数据处置

- **未修改任何历史数据**
- **未运行任何 UPDATE/DELETE**
- **未生成 TENANT_DATA_REMEDIATION_PLAN**（无需 — 当前未发现历史污染）
- **未处理的事务**已记录到 `REPAIR_FOLLOWUPS.md`（特别是 `crm_wallet` 44 行 NULL tenant_id）

---

## 7. Tests

### 7.1 新增测试文件

`backend/src/test/java/com/meiyetec/controller/PosControllerTenantIsolationTest.java`

### 7.2 测试矩阵

| 测试 ID | 验证内容 | 期望 | 结果 |
|---------|----------|------|------|
| TEST-001 | Tenant A POS 钱包消费 | 写入 `crm_wallet_transaction.tenant_id = A` | ✅ PASS |
| TEST-002 | Tenant B POS 钱包消费 | 写入 `crm_wallet_transaction.tenant_id = B`，**不等于 1** | ✅ PASS |
| TEST-003 | Tenant A 查询 Tenant B | 0 行 | ✅ PASS |
| TEST-004 | Tenant B 查询 Tenant A | 0 行 | ✅ PASS |
| TEST-005 | 伪造 tenantId=A 但 ctx=B | 写库仍为 B（ThreadLocal 决定） | ✅ PASS |
| TEST-006 | 无认证 ctx | 不可静默写 tenant_id=1（必须报错或写 NULL） | ✅ PASS |
| TEST-007 | tenant_id=1 不特殊 | 与其他租户同等对待 | ✅ PASS |

### 7.3 完整测试套件结果

```
[INFO] Tests run: 7,  Failures: 0, Errors: 0 - PosControllerTenantIsolationTest
[INFO] Tests run: 13, Failures: 0, Errors: 0 - RefundStatusTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0 - BizCodeTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0 - ConcurrencyIntegrationTest
[INFO] Tests run: 5,  Failures: 0, Errors: 0 - PosRefundIntegrationTest
[INFO] Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 7.4 测试结果分类

| BASELINE FAILURE | REGRESSION |
|------------------|------------|
| 0 | 0 |

无 baseline failure（无既有测试失败），无 regression（本次修改未引入新失败）。

---

## 8. Runtime Evidence

### 8.1 Backend 已用新代码启动

```bash
$ Get-Process -Name java | Find Id=21380
Id    StartTime
21380 2026/8/13 23:34:12

$ Get-NetTCPConnection -LocalPort 8080 -State Listen
LocalPort  OwningProcess
8080       21380
```

### 8.2 JWT 登录链路验证

| 步骤 | 命令 | 结果 |
|------|------|------|
| 1. POST /auth/login admin/admin123 | HTTP 200, token returned | ✅ |
| 2. POST /auth/login store1/admin123 (tenant=2) | HTTP 200, token returned | ✅ |
| 3. JWT 解析：`userId=2` | Token decoded | ✅ |
| 4. JwtInterceptor → RbacContextLoader.load(2) | User.id=2, tenant_id=2 loaded | ✅ |
| 5. 提供给业务代码 | `TenantContextHolder.get().getTenantId() = 2` | ✅ |

### 8.3 数据库验证

- 修复前历史数据保持不变（24 行 + 188 pos_order + 186 pos_payment + 108 pos_order_item 全部保留 tenant_id=1）
- 新增测试运行不污染生产数据（测试已在 `@AfterAll` 清理）
- 修复后未来写入将根据 ThreadLocal 自动使用正确 tenantId

### 8.4 Runtime Evidence Files

- `mvn_compile3.log` — 全量编译成功
- `mvn_test_full.log` — 34 个测试全部通过
- `audit_tenant_pollution.out` — 历史数据审计
- `verify_runtime_tenant2_final.ps1` — JWT 登录链路验证
- `setup_tenant_b.sql` — 测试用 Tenant 2 建表

---

## 9. Regression

| 类别 | 详情 |
|------|------|
| POS 路径 | 无 regression。`PosController` 仅替换两处硬编码 `1L`，SQL 参数顺序未变 |
| Wallet 路径 | 无 regression。`WalletController` 本就使用 `TenantContextHolder`，未触碰 |
| DB Schema | 无修改 |
| Build | BUILD SUCCESS |
| Existing Tests | 27/27 PASS（13 RefundStatus + 6 BizCode + 3 Concurrency + 5 PosRefund） |
| New Tests | 7/7 PASS（Tenant Isolation） |

---

## 10. Scope Control

### 10.1 本任务范围内

✅ 修改 `PosController.java` 中的两处 `tenant_id=1L` 硬编码
✅ 新增 `TenantContextHolder` import
✅ 新增 7 个 Tenant Isolation 测试
✅ 运行新增测试和现有测试
✅ 运行时验证

### 10.2 本任务范围外（已记录到 REPAIR_FOLLOWUPS.md）

- ❌ `crm_wallet` 44 行 tenant_id 为 NULL 的数据（P0-MONEY-002 双轨问题）
- ❌ `pos_order` / `pos_payment` / `pos_order_item` PosController 未显式 setTenantId（MyBatis 拦截器审计不属于 P0-MONEY-004）
- ❌ DCEP mock 实现（P0-MONEY-001）
- ❌ `Math.random()` 用于 DCEP transaction ID（P0-MONEY-003）
- ❌ 数据库 FK 缺失（P0-DB-001）
- ❌ Service 重构
- ❌ E2E 测试
- ❌ 前端重构
- ❌ 任何其他业务优化

### 10.3 阻塞项

无 — 未发现需要扩大 scope 的需求。

---

## 11. Remaining Risks

| 风险 | 严重度 | 状态 |
|------|--------|------|
| `pos_order` / `pos_payment` / `pos_order_item` 是否被 MyBatis 拦截器自动填 tenant_id | **UNVERIFIED** | 需另开 TASK |
| `crm_wallet` 44 行 NULL tenant_id | P1 | 需修复但不属于 P0-MONEY-004 |
| 单元测试仍是基础 Spring 集成测试，未做完整 E2E | P2 | 需后续覆盖 |
| `TenantContextHolder.get().getTenantId()` 在 ctx 不存在时返回 null，可能导致 SQL 写入 NULL | 已记录 | 测试 TEST-006 验证此场景不会静默写 1 |

### 11.1 P0-MONEY-004 修复后仍存在的潜在风险

| 风险 | 缓解 |
|------|------|
| 未鉴权请求可能携带伪造 tenantId | JwtInterceptor 已拦截所有未认证请求 |
| 跨租户读 | 不在 P0-MONEY-004 范围；`TenantContextHolder.requireTenant()` 提供 403 防护 |
| 已存在的 tenant_id=1 历史数据 | 未发现显性污染；但仍可能存在 MyBatis 自动填的间接污染（UNVERIFIED） |

---

## 12. Definition of Done

| 条件 | 状态 |
|------|------|
| [x] 两处 `tenant_id=1L` 已消除 | ✅ |
| [x] 使用统一可信 Tenant Resolution | ✅ `TenantContextHolder.get().getTenantId()` |
| [x] 没有新增第二套租户机制 | ✅ 仅复用 |
| [x] Tenant A/B 隔离测试通过 | ✅ 7/7 |
| [x] request tenantId spoofing 测试通过 | ✅ TEST-005 |
| [x] unauthenticated request 测试通过 | ✅ TEST-006 |
| [x] 原有 POS/WALLET 测试无 regression | ✅ 27/27 |
| [x] Runtime verification 通过 | ✅ JWT-chain 验证 |
| [x] 没有修改无关业务 | ✅ scope 控制 |
| [x] 没有修改历史数据 | ✅ read-only |
| [x] Evidence 已保存 | ✅ 8 个 evidence 文件 |

---

## 13. Status

**DONE**

所有 Definition of Done 项目已满足，所有测试通过，无 regression，无 scope 蔓延。

---

## 附录 A: Evidence Files

| 文件 | 用途 |
|------|------|
| `docs/audit/BEAUTY_SAAS/CURRENT_TENANT_SOURCE.md` | Step 0 — Tenant Resolution 来源证据 |
| `docs/audit/BEAUTY_SAAS/TENANT_DATA_CONTAMINATION_AUDIT.md` | Step 1 — 历史数据污染审计 |
| `audit_tenant_pollution.sql` / `.out` | Step 1 — SQL 审计脚本与输出 |
| `audit_tenant_pollution_v2.sql` / `_v2.out` | Step 1 — 扩展 SQL 审计 |
| `backend/src/test/java/com/meiyetec/controller/PosControllerTenantIsolationTest.java` | Step 5 — 7 个 Tenant Isolation 测试 |
| `mvn_compile3.log` | Step 4 — 编译成功 |
| `mvn_test_full.log` | Step 6 — 34/34 测试通过 |
| `mvn_test_p004_v4.log` | Step 6 — Tenant Isolation 单独运行 |
| `setup_tenant_b.sql` / `.out` | Step 7 — Tenant 2 测试数据 |
| `verify_runtime_tenant2.ps1` | Step 7 — Tenant 2 运行时验证 |
| `verify_runtime_tenant2_final.ps1` | Step 7 — JWT 链路验证 |
| `cleanup_test_members.sql` | Step 6 — 测试清理 |

## 附录 B: Related Documents

- `docs/audit/BEAUTY_SAAS/REPAIR_FOLLOWUPS.md` — 后续未修复项记录
- v3 P0 优先级 Roadmap — 后续 P0 任务清单
