# F003 — 更新集成（I007 Shadow + I002 Silent Write）

> **Phase**：5B Runtime Verification
> **Feature ID**：F003
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`
> **DB snapshot**：`audit/evidence/db-snapshot.md`
> **Env**：`audit/evidence/env.md`

---

## 1. 预期 vs 实测

| 维度 | plan.md §一 F003 预期 | 5B 实测 | 是否一致 |
|---|---|---|---|
| 验证方式 | API-only | API-only + DB 断言 | ✅（增强） |
| 预期负向证据 | I007 Shadow + I002 silent | ✅ I007 + I002 + **新增：Fake Failure** | ⚠ 超预期 |

## 2. 实测

### 2.1 API 调用

请求体：`audit/evidence/F003-update-integration/body-update.json`
```json
{"shopName":"测试更新5B","syncDirection":"BIDIRECTIONAL"}
```

响应：`audit/evidence/F003-update-integration/raw-response-update.txt`

```
HTTP/1.1 200
{"code":500,"message":"数据库异常","data":null,"traceId":"e7d0d2d715574e4faa936cff2a8cf4be"}
```

### 2.2 DB 状态（关键负向证据）

完整：`audit/evidence/F003-update-integration/db-after-update.txt`

```sql
SELECT id, platform, sync_direction FROM crm_integration WHERE id=2;
-- id platform sync_direction
--  2 MOCK    BIDIRECTIONAL      ← sync_direction 已被改为 BIDIRECTIONAL
```

DB reset：`audit/evidence/F003-update-integration/db-reset.txt`（已还原为 PULL）

### 2.3 后端日志

完整：`audit/evidence/F003-update-integration/log-context.txt` + `log-stack-trace.txt` + `log-stack-trace-tail.txt`

调用栈：
```
at SqlSessionTemplate.selectOne
at MybatisMapperMethod.execute
at $Proxy314.selectById
at CrmIntegrationService.update(CrmIntegrationService.java:64)   ← selectById 抛 I002
```

→ updateById 已成功执行（无异常），selectById 抛 `Unknown column 'sync_token'` → GlobalExceptionHandler → code=500

## 3. ⚠️ **新增负向证据：Fake Failure（API 报错但 DB 已写）**

### 3.1 现象

**调用链分析**（`CrmIntegrationService.java:59-65`）：

```java
@PostMapping("/{id}")
public Result<CrmIntegration> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    i.setId(id);
    i.setTenantId(tenantId);
    integrationMapper.updateById(i);                             // ← 步骤 1：成功
    return Result.success("已更新", integrationMapper.selectById(id));  // ← 步骤 2：抛 I002
}
```

**5B 实测现象**：
1. ✅ updateById 已成功执行（DB 中 `sync_direction` 从 PULL → BIDIRECTIONAL）
2. ❌ selectById 抛 I002（`Unknown column 'sync_token'`）
3. ❌ GlobalExceptionHandler 捕获 → 返回 `code=500, message="数据库异常"`
4. ❌ 前端看到"失败"，**不会重试**
5. ⚠️ **数据已悄然变更**，但用户被告知失败

### 3.2 与 I008 Fake Success 的对比

| 维度 | I008 Fake Success | F003 Fake Failure |
|---|---|---|
| 异常类型 | catch 吞 + 返回 success | 抛错 + GlobalExceptionHandler |
| API 返回 | `code=200, success=true` | `code=500, message="数据库异常"` |
| 实际状态 | DB 操作失败但 API 报告成功 | DB 操作成功但 API 报告失败 |
| 用户感知 | "成功"（最坏） | "失败"（次坏 — 但数据已改）|
| 重试行为 | 不重试 | 不重试（但若重试 = 重复写入）|
| 数据一致性 | 客户端显示成功 → 不一致 | 客户端显示失败 → 不一致 |

**F003 Fake Failure 比 I008 Fake Success 更隐蔽**：
- I008 用户看到成功，无人质疑
- F003 用户看到失败，但实际数据已改 — 用户可能去查 DB 确认，但**若依赖前端缓存或未刷新**，数据漂移

### 3.3 与 I009 rollbackFor 的关系

`@Transactional` 默认 rollbackFor=RuntimeException。`BadSqlGrammarException` 是 RuntimeException 子类 → 异常抛出时 `@Transactional` 会触发回滚。

但 F003 的 updateById **已成功**（SQL UPDATE 没抛错），只有**后续**的 selectById 抛错 → **UPDATE 已提交，无法回滚**。

→ F003 Fake Failure 是**逻辑层面**的不可逆，不是事务回滚能解决的。

### 3.4 影响范围

| 调用链 | 是否 Fake Failure |
|---|---|
| F003 update | ✅ Fake Failure（实测）|
| F004 toggle | ❌ toggle 整链路阻塞（selectById 在 setStatus 之前），不会"假失败" |
| F005 sync | ❌ sync 整链路阻塞（selectById 在 syncNow:88），事务回滚生效（5A 已确认）|
| F002 list | ❌ list 一次性失败，无 UPDATE |
| F007 dashboard | ⚠️ Fake Success（5A 已确认）|

## 4. ✅/⚠ 验证结论

### ⚠ **F003 — PARTIAL-BLOCKER**

| 维度 | 状态 |
|---|---|
| update API 可用性 | ❌ 返回 500（I002 阻塞）|
| I007 Shadow | ✅ 完全暴露（前端无 update 按钮，但 API 可用且有数据漂移风险）|
| I002 Silent Write | ✅ 完全暴露（updateById 成功）|
| **新增 Fake Failure** | ✅ **完全暴露**（API 报错但 DB 已写）|
| 数据一致性 | ❌ 数据已改但前端显示失败 |

### **最终状态**：⚠ **PARTIAL-BLOCKER**

（API 不可用 + I007 + I002 + Fake Failure 4 重问题同时暴露）

## 5. 严重度评估

| 标准 | F003 命中 |
|---|---|
| 🟢 LOW | ❌ |
| 🟡 MEDIUM | ❌ |
| 🟠 HIGH | ❌ |
| 🔴 **CRITICAL** | ✅（数据漂移 + 用户感知失败 — 双重风险）|

→ 建议：**新登记 I011 = Fake Failure（API 报错但 DB 已写）**，优先级 P0。

## 6. 5B 继承式策略影响

- F003 自身 = PARTIAL-BLOCKER
- 不被 5B 后续 Feature 继承
- **建议 5C 重点**：F003 修复后必须做"事务边界重新设计"，把 selectById 放到 updateById 之前做"先读后写"语义，或捕获 SELECT 异常返回不带最新值的 result

## 7. 关联

- `audit/issues/issue-register.md` §八 I007 + §十 I002
- `audit/issues/I007-crm-update-shadow.md`
- `audit/issues/I008-dashboard-catch-swallow.md`（对比 Fake Success 模式）
- `audit/issues/I011-fake-failure-api-err-db-ok.md`（建议新登记 — **本次审计新发现**）
- `audit/runtime/plan.md` §一.2 F003 + §三 关键负向证据
- `audit/runtime/report-5A.md` §0.1（5A 未测 F003）
