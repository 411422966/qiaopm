# I009 — @Transactional rollbackFor 缺失

| 维度 | 值 |
|---|---|
| **Issue ID** | I009 |
| **创建时间** | 2026-09-12 22:03 (UTC+8) |
| **Phase** | 3 — Static Gap Audit |
| **类别** | Transaction 事务边界（rollbackFor 默认仅 RuntimeException）|
| **严重度** | 🟡 中 |
| **影响 Feature** | F005（CRM 集成 / 手动触发同步 ⭐）|
| **修复阶段** | **审计后**（用户决策 D4 = 不修，本 Phase 4 不动）|

---

## 1. 现象

```java
// backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:79-128
@PostMapping("/{id}/sync")
@Transactional                              // ← 默认 rollbackFor = {RuntimeException.class}
public Result<Map<String, Object>> syncNow(@PathVariable Long id,
                                           @RequestParam(required = false) Integer force) {
    ...
    CrmSyncLog realLog = crmSyncScheduler.runIncremental(i, "MANUAL");
    ...
}
```

### 07-transaction 输出

`audit/static-gap/07-transaction.txt:1`：
```
D:\QiaoPM\backend\src\main\java\com\meiyetec\service\CrmIntegrationServicejava  79  Transactional-no-rollbackFor
```

---

## 2. 默认行为

Spring `@Transactional` 默认 `rollbackFor = {RuntimeException.class}`，`Error` 也回滚；但 **checked exception（包括 `Exception.class`）不触发回滚**，仅标记事务为 rollback-only。

### 2.1 当前主路径

| 异常类型 | 是否回滚？ | 路径 |
|---|---|---|
| `RuntimeException`（含 `BizException` / `NullPointerException`）| ✅ 是 | 当前主路径 |
| `Error` | ✅ 是 | — |
| checked `Exception`（`SQLException` / `IOException` / `ClassNotFoundException` 等）| ❌ 否 | 风险路径 |

### 2.2 实际风险场景

`CrmSyncScheduler.runIncremental` 内部涉及：
- `mapper.selectById`（MyBatis — 抛 `PersistenceException` → 内部包 `RuntimeException`）→ ✅ 触发回滚
- `mapper.updateById` / `mapper.insert`（同上）→ ✅ 触发回滚
- HTTP 调用 Salesforce / HubSpot / Zoho（可能抛 `IOException` — checked）→ ❌ **不触发回滚**
- JWT 签名（`NoSuchAlgorithmException` — checked）→ ❌ **不触发回滚**

**当前**：当前 checked 异常都在 try-catch 内被捕获并转为 `BizException`（RuntimeException 派生），所以**实际主路径回滚正常**。

**风险**：未来扩展（如增加重试机制、增加 async webhook、增加 OAuth2 refresh）若引入未包装的 checked 异常，**`crm_integration.status` 改了 + `crm_sync_log` 落库失败 → 数据不一致**。

---

## 3. 后果

- 当前主路径：事务回滚正常，**实际无问题**
- 未来扩展风险：checked exception 不会触发回滚 → 数据不一致隐患
- 防御性编程：应显式声明 `rollbackFor = Exception.class`

---

## 4. 修复建议（**审计后** / 用户决策 D4 = 不修）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① 加 rollbackFor** | `@Transactional(rollbackFor = Exception.class)` | 显式全捕获 checked 异常 | 业务级拒绝也回滚（如 FORBIDDEN） |
| **② 加 rollbackFor + noRollbackFor** | `@Transactional(rollbackFor = Exception.class, noRollbackFor = BizCode.FORBIDDEN.class)` | 业务级拒绝不滚（语义最完整）| 需 import 全限定 |
| **③ 用 TransactionTemplate 显式编程** | 手写 `transactionTemplate.execute(...)` + 在 callback 内 try/catch 显式回滚 | 最灵活 | 重构面大 |
| **④ 接受现状** | 当前主路径 OK，未来扩展时再处理 | 不动 | 隐性风险 |

**推荐**：②（最完整 + 改动最小）。

---

## 5. Phase 5 验证策略

| 验证方式 | 是否适用 |
|---|---|
| **API-only（curl）** | ⚠️ 部分。手动调 sync → 大概率触发 I002 列缺失（`BadSqlGrammarException` 是 RuntimeException）→ 主路径回滚 OK |
| **DB-only** | ⚠️ 部分。可观察 sync 失败时 `crm_integration.last_incremental_sync_at` 是否更新（F005 失败时不更新 — 验证反向）|
| **Playwright E2E** | ❌ 不适用 |
| **实际期望** | **当前主路径**：sync 失败 → `crm_integration.last_incremental_sync_at` 应保持不变 + `crm_sync_log.status='FAILED'`，**应** 触发回滚。**未来**：引入 checked exception 时 I009 浮现 |

---

## 6. 关联

- `audit/issues/issue-register.md` §九
- `audit/feature-inventory.md` §1.1.2 row 5（F005）
- `audit/traceability-matrix.md` §F005
- `audit/reports/static-gap-report.md` §2.8（F005 transaction）+ §三 I009
- `audit/static-gap/07-transaction.txt:1`
