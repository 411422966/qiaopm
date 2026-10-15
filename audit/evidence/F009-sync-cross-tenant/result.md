# F009 — 手动 sync 跨租户拒绝

> Phase 5A | API-only | P0
> 时间：2026-09-12 23:20 (UTC+8)

---

## 1. 验证方式

**API-only**：POST `/crm-integration/{id}/sync`，用不同租户 token 调别人的 integration
（来源 `CrmIntegrationService.java:88-90`，依据 plan.md §1.1）

## 2. 测试前提

admin 和 clerk01 都是 `tenant_id = 1`（来自 `sys_user` 表查询），但 plan §1.1 要求"用 2 个不同租户的 token 测"。
**用户标签有误** — 见 `audit/evidence/env.md §5`。

为模拟跨租户场景，**SQL 直接插入** `tenant_id=2` 的测试集成（不通过 API，绕过 I002 silent 链路）：

```sql
INSERT INTO crm_integration (tenant_id, platform, sync_direction, auto_sync, status, created_at)
VALUES (2, 'MOCK', 'PULL', 0, 'ACTIVE', NOW());
```

| id | tenant_id | platform | status |
|---|---|---|---|
| 3 | 2 | MOCK | ACTIVE |

完整：`audit/evidence/F009-sync-cross-tenant/db-setup-tenant2.txt`

## 3. 测试矩阵

| # | 场景 | token | token 解析 | integration | 期望 |
|---|---|---|---|---|---|
| A | 同租户 | admin | tenantId=1, dataScope=ALL | id=2 (tenant=1) | 成功（实测 I002） |
| B | **跨租户** | clerk01 | tenantId=1, dataScope=SELF | id=3 (tenant=2) | **FORBIDDEN 403** |
| C | **跨租户+超管** | admin | tenantId=1, dataScope=ALL | id=3 (tenant=2) | 成功（实测 I002）|

## 4. 实测结果

### 4.1 B — clerk01 跨租户调 id=3（关键测试）

完整 raw：`audit/evidence/F009-sync-cross-tenant/raw-response-clerk-tenant3.txt`

| 字段 | 值 |
|---|---|
| HTTP 状态 | **200** |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `78ee7c0ac0b44b6b9ef80cd4f91e1193` |
| data | null |

完整 log：`audit/evidence/F009-sync-cross-tenant/log-context-clerk.txt`

```
BadSqlGrammarException: Unknown column 'sync_token' in 'field list'
SQL: SELECT id,tenant_id,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes
    FROM crm_integration WHERE id=?
```

→ **未到达跨租户判断路径**（代码第 88-90 行 tenantId 检查之前就抛错）

### 4.2 C — admin 跨租户调 id=3

完整 raw：`audit/evidence/F009-sync-cross-tenant/raw-response-admin-tenant3-force1.txt`

| 字段 | 值 |
|---|---|
| HTTP 状态 | **200** |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `432ded392c6a4c8ebb28571730f9755e` |
| data | null |

→ admin 跨租户也抛 I002 — 同样 selectById 阶段。

## 5. DB 断言

完整：`audit/evidence/F009-sync-cross-tenant/db-after-sync.txt`

| 维度 | 实测 | 预期 | 备注 |
|---|---|---|---|
| `crm_integration` 总行 | 3 | — | id=1/2/3 |
| `crm_integration.id=3.status` | ACTIVE（未变）| 不变 ✅ | F009 抛错，无副作用 |
| `crm_sync_log` | 0 行 | 不写 ✅ | 事务回滚 |

## 6. ✅/⚠ 验证结论

### ⚠ **关键负向证据：F009 在 I002 阻塞下无法 e2e 验证**

`CrmIntegrationService.syncNow` 执行顺序：

```java
// 第 88 行
CrmIntegration integration = crmIntegrationMapper.selectById(id);  // ← I002 抛错
if (integration == null) return Result.error(NOT_FOUND);

// 第 90 行（跨租户判断 — 永远到不了）
if (dataScope != "ALL" && integration.getTenantId() != currentTenantId) {
    return Result.error(FORBIDDEN, "无权访问");
}
```

由于 `selectById` 在 I002 下抛 `BadSqlGrammarException`，**跨租户判断代码虽然存在但不可达**。

### ⚠ 用户标签不一致

| 项 | 用户输入 | 实际 token 解析 |
|---|---|---|
| "超管 token" | clerk01 | clerk01 (tenantId=1) |
| "普通租户 token" | admin | admin (tenantId=1, dataScope=ALL) |

实际**两个 token 都是 tenant_id=1**，无法用现有数据测跨租户拒绝 → 需 SQL 插入 tenant_id=2 测试数据。

### ⚠ clerk01 的 dataScope 是 SELF 还是 ALL？

clerk01 role=CLERK，按 RBAC 预期应是 `dataScope=SELF`。但 admin 是 SUPER_ADMIN/ADMIN，应是 ALL。
- A admin → id=2(tenant=1): 同租户，应直接到 shouldRun 阶段（实测 I002 阻塞）
- B clerk01 → id=3(tenant=2): 跨租户，应返回 FORBIDDEN（实测 I002 阻塞）
- C admin → id=3(tenant=2): 跨租户 + dataScope=ALL，应到 shouldRun 阶段（实测 I002 阻塞）

**实测三场景都抛 500**，无法 e2e 验证跨租户判断逻辑。

### ⚠ I002 — DDL 缺列（直接抛错）

三场景都被同一根因阻塞。

### ⚠ I008 — catch 吞异常

返回 `code=500, message="数据库异常"` — 模糊消息 + 前端无法区分"DB 异常" vs "FORBIDDEN"。

## 7. 状态结论

| 维度 | 判定 |
|---|---|
| HTTP 行为 | ❌ BROKEN（3/3 场景都 code=500）|
| 跨租户拒绝路径 | ⚠ **代码存在但不可达**（静态可达 L0，运行时被 I002 阻塞）|
| 超管跨租户允许 | ⚠ **代码存在但不可达**（同上）|
| 端到端 e2e 验证 | ❌ **无法在当前环境下完成** |
| 与 plan.md §三 预期 | ⚠ plan §1.1 标 "—" 表示 F009 不依赖 I002 修复，实测**强依赖** I002 |
| **最终状态** | **UNVERIFIED**（被 I002 阻塞，无法 e2e 验证跨租户拒绝行为） |

## 8. 修复方向（不实施，仅登记）

🛠 跑 `migrate_ext_15_crm_real.sql` 补齐 9 列 → selectById 恢复 → 跨租户判断可达
🛠 或者在 `selectById` 之前加 early-return：先按 integrationId 查 `tenant_id`（仅这一列，避开缺列），再判断跨租户
🛠 或者把跨租户判断移到 Controller 层（用 @PreAuthorize 或租户拦截器），不依赖 service 层查 DB

## 9. 副作用

- `crm_integration` 新增 1 行（id=3, tenant_id=2）— **保留作为 F009 测试证据**
- `crm_sync_log`: 0 行
- 现有测试用 integrationId：2（tenant=1）+ 3（tenant=2）共存

## 10. 跨租户判断静态可达性（L0 证据）

`backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:88-90`：

```java
CrmIntegration integration = crmIntegrationMapper.selectById(id);  // line 88
if (integration == null) return Result.error(NOT_FOUND, "...");
if (dataScope != null && !"ALL".equals(dataScope)
    && !integration.getTenantId().equals(currentTenantId)) {       // line 90
    return Result.error(FORBIDDEN, "无权访问该集成");
}
```

代码静态可达 L0，运行时被 I002 阻塞 → **Static-Only 维度可达，Runtime 维度不可达**。
