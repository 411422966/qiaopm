# F009 — 跨租户拒绝修复验证（Phase 5A 补测）

> 5A 收尾补测 | 时间：2026-09-12 23:56-23:57 (UTC+8)
> 起点 commit hash: `12a97ea57a98d268c864fc7c010b82b305d7bb93`
> 目的：在 I002 阻塞环境下，确认 F009 跨租户判断代码**确实不可达**

---

## 1. 背景

5A 原始 F009 验证（`audit/evidence/F009-sync-cross-tenant/`）测了 2 个场景（A 同租户、B 跨租户 clerk01），但未单独测"**超管 (dataScope=ALL) 跨租户**"场景。

用户在 5B 启动前要求：**用超管 token 访问 tenant_id=2 集成，观察是否返回 403**。

---

## 2. 测试前提

- id=3, tenant_id=2, MOCK, ACTIVE（**5A F009 测试阶段 SQL 创建，仍存在**）
- 起点 commit hash: `12a97ea`
- 用户标签 vs token 实际权限（已修正）：
  - "超管 token" = admin (userId=1, dataScope=ALL)
  - "普通租户 token" = clerk01 (userId=900003, dataScope=SELF)
- admin dataScope=ALL → 按 EXT-24 §0.7，跨租户访问**应允许**，**不应返回 403**

---

## 3. 测试矩阵（5A 补测）

| # | token | integration | 预期（按 EXT-24）| 实测 |
|---|---|---|---|---|
| B.1 | admin (ALL) | id=3 (tenant=2), force=null | **允许**（不返回 403）| ❌ code=500 |
| B.2 | admin (ALL) | id=3 (tenant=2), force=1 | **允许**（不返回 403）| ❌ code=500 |
| B.3 | clerk01 (SELF) | id=3 (tenant=2), force=1 | **应返回 403** (FORBIDDEN) | ❌ code=500 |

完整 raw：
- `audit/evidence/F009-sync-cross-tenant/raw-response-admin-tenant3-force-null.txt`
- `audit/evidence/F009-sync-cross-tenant/raw-response-admin-tenant3-force1.txt`
- `audit/evidence/F009-sync-cross-tenant/raw-response-clerk-tenant3-force1.txt`

## 4. 响应详情

### 4.1 B.1 — admin force=null

| 字段 | 值 |
|---|---|
| HTTP 状态 | 200 |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `8dcfabb586274defb924f6969106f7da` |

### 4.2 B.2 — admin force=1

| 字段 | 值 |
|---|---|
| HTTP 状态 | 200 |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `2458fec1d84a46d58162dd7f05aaf5bb` |

### 4.3 B.3 — clerk01 force=1

| 字段 | 值 |
|---|---|
| HTTP 状态 | 200 |
| code | **500** |
| message | **"数据库异常"** |
| traceId | `4779eed1d5c64b0fb26d8f55726b9049` |

## 5. 后端日志（3 个 traceId 全文确认）

完整：`audit/evidence/F009-sync-cross-tenant/log-3-traces-summary.txt`

3 个 traceId 全部抛**同一异常**：

```
### Error querying database.  Cause: java.sql.SQLSyntaxErrorException:
    Unknown column 'sync_token' in 'field list'
### SQL: SELECT id,tenant_id,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes FROM crm_integration WHERE id=?
```

admin 调用栈（`audit/evidence/F009-sync-cross-tenant/log-stack-trace-admin.txt`）：

```
at $Proxy89.selectOne
at SqlSessionTemplate.selectOne
at MybatisMapperMethod.execute
at $Proxy314.selectById
at CrmIntegrationService.syncNow(CrmIntegrationService.java:88)
```

→ 所有 sync 入口都在 `syncNow:88` 的 `selectById` 阶段抛 I002，**跨租户判断（line 90）永远到不了**。

## 6. DB 断言（3 次测试后）

完整：`audit/evidence/F009-sync-cross-tenant/db-after-3-tests.txt`

| id | tenant_id | platform | status | error_message |
|---|---|---|---|---|
| 1 | 1 | SALESFORCE | ACTIVE | NULL |
| 2 | 1 | MOCK | ACTIVE | NULL |
| 3 | 2 | MOCK | ACTIVE | NULL |

| 维度 | 实测 | 预期 |
|---|---|---|
| crm_sync_log | 0 行 | 事务回滚生效 ✅ |
| error_message | 全部 NULL | 异常太早未写 ✅ |
| status | 全部 ACTIVE | ✅ |

## 7. ✅/⚠ 验证结论

### ⚠ **F009 在当前环境下无法 e2e 验证（再确认）**

**3/3 测试全部 code=500**，**没有任何一个能区分出"超管允许跨租户" vs "普通租户被 403 拒绝"**：
- B.1 admin 期望允许（不返回 403） → 实测 500 → 无法判断 admin 是否真允许（也可能 admin 也被 403）
- B.2 admin force=1 期望允许 → 实测 500 → 同上
- B.3 clerk01 期望返回 403 → 实测 500 → **最关键证据**：如果 I002 修复，clerk01 应该返回 403，但当前 500 完全无法证明 clerk01 会 403

### ⚠ **I002 阻塞跨租户判断**

调用链分析：

```
syncNow(integrationId)             // Controller 入口
  → selectById(integrationId)        // line 88 — I002 抛错
    → (死路)
  → if dataScope != "ALL" && ...     // line 90 — 永远到不了
```

跨租户判断代码（CrmIntegrationService.java:90）静态可达 L0，运行时不可达（被 I002 在更早阶段阻塞）。

### ⚠ **I008 catch 吞异常仍生效**

3/3 都返回 `code=500, message="数据库异常"` — 与 F002/F005 一致：
- 前端看到模糊 "数据库异常"
- 无法区分 DB 异常 vs 权限拒绝 vs 业务异常
- traceId 在 body 中返回，可查日志（✅ 至少可定位）

### ⚠ **F009 修复路径（不实施）**

| 方案 | 优点 | 缺点 |
|---|---|---|
| 跑 `migrate_ext_15_crm_real.sql` 补 9 列 | 一键修复，F009 自动可测 | D5 决策暂不跑 |
| Controller 层加租户拦截器 | selectById 之前 early-return | 改业务代码 ❌ |
| Controller 加 `@PreAuthorize` | Spring Security 拦截 | 需重构权限体系 |
| 在 selectById 前用 @Query 查 tenant_id | 避开缺列 | 改业务代码 ❌ |

→ **规则 6 约束下，唯一可行 = 跑迁移（用户决策）**

## 8. 与 F009 原始 result.md 的关系

本文件**补充**原 `audit/evidence/F009-sync-cross-tenant/result.md`，不替代：
- 原 result.md §3 测试矩阵：3 个场景（admin 同租户、clerk01 跨租户、admin 跨租户 force=1）
- 本文件补充测试矩阵：B.1 / B.2 / B.3 3 个新场景（admin 跨租户 force=null、admin 跨租户 force=1、clerk01 跨租户 force=1）

**5A F009 最终测试总数**：6 个场景 × 全部 code=500，**UNVERIFIED** 状态强化。

## 9. 状态结论

| 维度 | 判定 |
|---|---|
| 跨租户拒绝 e2e 验证 | ❌ UNVERIFIED（I002 阻塞，6/6 场景抛 500）|
| 超管跨租户允许 e2e 验证 | ❌ UNVERIFIED（同上）|
| 静态代码可达 | ✅ CrmIntegrationService.java:90 |
| 运行时实际可达 | ❌ 不可达（I002 在 line 88 抛错）|
| 与 plan.md §三 预期 | ⚠ plan 标 "—" 表示 F009 不依赖 I002，**实测强依赖** |
| **最终状态** | **UNVERIFIED**（强化版 — 6/6 场景无法 e2e 验证跨租户行为） |

## 10. 5B 继承式策略影响

按用户在 5B 启动决策中的"**继承式**"策略：
- F009 = UNVERIFIED（强依赖 I002）
- I002 修复后 → 5B 应**重测 F009**（不再是"继承"，而是"修复后回归测试"）
- 修复方式：跑 `migrate_ext_15_crm_real.sql` 补 9 列 → 重跑 B.1 / B.3（admin 应允许 / clerk01 应 403）
