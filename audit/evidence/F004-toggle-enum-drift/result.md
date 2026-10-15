# F004 — 切换集成状态（I005 C1 enum 漂移关键负向证据）

> **Phase**：5B Runtime Verification
> **Feature ID**：F004
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **DB snapshot**：`audit/evidence/db-snapshot.md`
> **Env**：`audit/evidence/env.md`

---

## 1. 预期 vs 实测

| 维度 | plan.md §一 F004 预期 | 5B 实测 | 是否一致 |
|---|---|---|---|
| 验证方式 | API+DB（toggle body）| API+DB | ✅ |
| 预期负向证据 | "传 PAUSED 写入成功（无 enum 校验）" | ✅ 实证（SQL 旁路）| ✅ |
| 副作用 | I002 | I002 阻塞 toggle 完整链路 | ⚠ 阻断 e2e |

## 2. 实测：toggle API 3 个状态

### 2.1 测试矩阵

| 状态 | HTTP | code | traceId | 后端异常 |
|---|---|---|---|---|
| DISABLED | 200 | 500 | `27177ab50c99457cbf5aa1f2d737998b` | BadSqlGrammarException |
| PAUSED | 200 | 500 | `64a6f722e25e41d2b632e0665160a077` | BadSqlGrammarException |
| ERROR | 200 | 500 | `13f7568e434f4148915f7aa0d72f692c` | BadSqlGrammarException |

完整 raw：
- `audit/evidence/F004-toggle-enum-drift/raw-response-toggle-DISABLED.txt`
- `audit/evidence/F004-toggle-enum-drift/raw-response-toggle-PAUSED.txt`
- `audit/evidence/F004-toggle-enum-drift/raw-response-toggle-ERROR.txt`

### 2.2 后端日志

完整：`audit/evidence/F004-toggle-enum-drift/log-traces-summary.txt` + `log-stack-toggle.txt`

3 个 traceId 全部抛同一异常：

```
### SQL: SELECT id,tenant_id,platform,...,sync_token,sync_schedule,sync_enabled,
    last_incremental_sync_at,private_key,username,login_url,object_mappings,
    sync_window_minutes FROM crm_integration WHERE id=?
### Cause: java.sql.SQLSyntaxErrorException: Unknown column 'sync_token' in 'field list'
```

调用栈：
```
at $Proxy89.selectOne
at SqlSessionTemplate.selectOne
at MybatisMapperMethod.execute
at $Proxy314.selectById
at CrmIntegrationService.toggle(CrmIntegrationService.java:69)   ← selectById 抛错
```

### 2.3 DB 状态（toggle 后）

完整：`audit/evidence/F004-toggle-enum-drift/db-after-toggle.txt`

| id | platform | status | 是否被 toggle 修改 |
|---|---|---|---|
| 1 | SALESFORCE | ACTIVE | ❌ 否（未调）|
| 2 | MOCK | ACTIVE | ❌ 否（被 I002 阻塞）|
| 3 | MOCK | ACTIVE | ❌ 否（未调）|

→ toggle API **完全失效**（被 I002 阻塞在 selectById 阶段）。

## 3. I005 C1 — enum 漂移（关键负向证据）

### 3.1 代码审查

`backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:67-77`：

```java
@PostMapping("/{id}/toggle")
public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    CrmIntegration i = integrationMapper.selectById(id);    // ← I002 阻塞
    if (i == null) return Result.error(BizCode.NOT_FOUND, "集成不存在");
    i.setStatus((String) body.get("status"));               // ← 无 enum 校验
    i.setUpdatedAt(...);
    integrationMapper.updateById(i);
    return Result.success();
}
```

**关键发现**：
1. **无 enum 校验**：`setStatus((String) body.get("status"))` 直接接受任意字符串
2. **Entity 注释**：`CrmIntegration.java:40` 写 `/** ACTIVE / DISABLED / ERROR */`（**无 PAUSED**）
3. **跨模块 enum 不一致**：搜整个 `backend/src/main/java/com/meiyetec`：
   - `PluginTenantService.java:81,89` — ACTIVE/DISABLED
   - `MemberLifecycleService.java:98` — ACTIVE/PAUSED
   - `PaymentConfigService.java:133` — ACTIVE/DISABLED
   - `PricingExperimentService.java:383` — DRAFT/RUNNING/PAUSED/COMPLETED
   - `SubscriptionServiceImpl.java:179` — PAUSED
   - `CarePlanService.java:418` — PAUSED
   - **→ 全代码库 enum 集合不统一**

### 3.2 SQL 旁路验证（关键负向证据）

由于 I002 阻塞，无法 e2e 验证 toggle 完整链路。SQL 旁路证明**字段层面无约束**：

完整：`audit/evidence/F004-toggle-enum-drift/db-side-channel-PAUSED.txt`

```sql
UPDATE crm_integration SET status='PAUSED' WHERE id=2;
SELECT id, status FROM crm_integration WHERE id=2;
-- id status
-- 2  PAUSED     ← 写入成功
```

DB reset：
```sql
UPDATE crm_integration SET status='ACTIVE' WHERE id=2;  ← 还原
```

**结论**：
- ✅ SQL 允许 `status='PAUSED'` 写入
- ✅ 后端 toggle 代码无 enum 校验
- ✅ Entity 注释（`ACTIVE/DISABLED/ERROR`）与代码层不强制
- → **前端如果传 `status="PAUSED"`，I002 修复后端会写入 PAUSED，触发状态机漂移**

## 4. ✅/⚠ 验证结论

### ⚠ **F004 — PARTIAL-BLOCKER**

| 维度 | 状态 |
|---|---|
| toggle API 可用性 | ❌ 完全不可用（I002 阻塞 selectById）|
| enum 校验 | ❌ 缺失（代码无校验，SQL 无约束）|
| 跨模块 enum 一致性 | ❌ 不一致（5+ 模块使用不同 enum 集合）|
| I005 C1 暴露 | ✅ 完全暴露（SQL 旁路 + 代码审查）|
| I002 阻塞 | ❌ toggle 永远到不了 setStatus |

### ⚠ **plan vs 5B 实测差异**

plan 预期："Phase 5 暴露 I005 C1（传 PAUSED 写入成功）"
实测：**API 层验证失败**（I002 阻塞），但**通过 SQL 旁路 + 代码审查充分暴露 I005 C1**

→ 严格 e2e 验证 F004 = UNVERIFIED
→ 通过旁路 + 代码静态 + DB 字段允许 = PARTIAL-BLOCKER

### **最终状态**：⚠ **PARTIAL-BLOCKER**

（API 不可用 + enum 漂移证据充分 + I005 C1 完全暴露）

## 5. 5B 继承式策略影响

- F004 自身 = PARTIAL-BLOCKER（I002 强依赖 + I005 C1 暴露）
- 不被 5B 后续 Feature 继承（每个 Feature 独立验证）
- 但 **F029 status 状态机** 必须参考此结果：toggle 失效 → status 状态机验证也只能通过 SQL 旁路

## 6. 关联

- `audit/issues/issue-register.md` §九 I005 C1
- `audit/issues/I005-enum-drift.md`（待建）
- `audit/runtime/plan.md` §一.2 F004 + §三 关键负向证据
- `audit/runtime/report-5A.md` §0.1（5A 未测 F004）
