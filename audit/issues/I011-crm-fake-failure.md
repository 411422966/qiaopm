# I011 — CRM Update API Fake Failure（API 报错但 DB 已写）

| 维度 | 值 |
|---|---|
| **Issue ID** | I011 |
| **创建时间** | 2026-09-13 00:55 (UTC+8) |
| **Phase** | 5B — Runtime Verification |
| **类别** | Fake Failure（API 返回失败但 DB 已成功写入）|
| **严重度** | 🔴 **CRITICAL**（生产实证 — 数据漂移 + 用户感知失败双重风险）|
| **影响 Feature** | F003（CRM 集成 / 更新集成）|
| **关联 Issue** | **I008**（Fake Success — 反向对称） → 合并为 **"Fake Result 系列"** |
| **修复阶段** | **审计后**（用户决策 D4 = 不修，本 Phase 不动）|
| **修复状态** | ✅ **FIXED**（2026-09-17 commit `0b5607e` / Post-Audit 2.2-A4 / 方案 ①）|

---

## 1. 现象

```java
// backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java:58-66
@PostMapping("/{id}")
public Result<CrmIntegration> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
    Long tenantId = TenantContextHolder.get().getTenantId();
    i.setId(id);
    i.setTenantId(tenantId);
    integrationMapper.updateById(i);                                   // ← 步骤 1：成功（UPDATE 已提交）
    return Result.success("已更新", integrationMapper.selectById(id));   // ← 步骤 2：抛 I002（缺列）
}
```

### 关键 bug

`updateById` 已成功执行（**事务已提交**）→ `selectById` 抛 `BadSqlGrammarException`（I002 缺列）→ GlobalExceptionHandler 捕获 → 返回 `code=500, message="数据库异常"`。

**前端看到"失败"，但 DB 数据已变。**

---

## 2. Phase 5B 实测证据

**完整 evidence**：`audit/evidence/F003-update-integration/`

### 2.1 测试请求

```json
POST /crm-integration/2
{"shopName":"测试更新5B","syncDirection":"BIDIRECTIONAL"}
```

### 2.2 HTTP 响应（Fake Failure 关键证据）

```json
HTTP/1.1 200
{"code":500,"message":"数据库异常","data":null,"traceId":"e7d0d2d715574e4faa936cff2a8cf4be"}
```

→ **API 返回 500，但**：

### 2.3 DB 实际状态

```sql
SELECT id, platform, sync_direction FROM crm_integration WHERE id=2;
-- id platform sync_direction
--  2 MOCK    BIDIRECTIONAL      ← sync_direction 已被改为 BIDIRECTIONAL
```

→ **UPDATE 已生效**（事务已提交）

### 2.4 后端日志

```
at SqlSessionTemplate.selectOne
at MybatisMapperMethod.execute
at $Proxy314.selectById
at CrmIntegrationService.update(CrmIntegrationService.java:64)   ← selectById 抛 I002
```

→ 调用栈显示异常点在 `selectById`，不是 `updateById`

---

## 3. 后果分析

### 3.1 数据漂移风险

| 场景 | 用户感知 | DB 实际 | 后果 |
|---|---|---|---|
| API 报错后用户不重试 | 失败 | 已变 | 数据已变但 UI 旧值 |
| API 报错后用户重试 | 失败 | 已变（重复 UPDATE） | 数据重复变更（无 idempotent 保护）|
| 前端缓存旧值渲染 | 失败 | 已变 | 前后端不一致 |

### 3.2 为什么 I009 rollbackFor 救不了

`@Transactional` 默认 rollbackFor = RuntimeException。`BadSqlGrammarException` 是 RuntimeException 子类 → 抛错时会触发回滚。

**但**：F003 的 `updateById` **已成功执行**（SQL UPDATE 没抛错），只有**后续**的 `selectById` 抛错 → **UPDATE 已提交，无法回滚**。

→ I011 Fake Failure 是**逻辑层面**的不可逆，不是事务回滚能解决的。

### 3.3 与 I008 Fake Success 对比

| 维度 | I008 Fake Success | I011 Fake Failure |
|---|---|---|
| 异常类型 | catch 吞 + 返回 success | 抛错 + GlobalExceptionHandler |
| API 返回 | `code=200, success=true` | `code=500, message="数据库异常"` |
| 实际状态 | DB 操作失败但 API 报告成功 | DB 操作成功但 API 报告失败 |
| 用户感知 | "成功"（最坏）| "失败"（次坏 — 但数据已改）|
| 重试行为 | 不重试 | 不重试（但若重试 = 重复写入）|
| 数据一致性 | 客户端显示成功 → 不一致 | 客户端显示失败 → 不一致 |
| **隐蔽性** | 低（用户无感）| **高**（数据漂移 + 前端不刷新）|

**I011 比 I008 更隐蔽**：
- I008 用户看到成功，无人质疑
- I011 用户看到失败，但实际数据已改 — 用户可能去查 DB 确认，但**若依赖前端缓存或未刷新**，数据漂移

---

## 4. 触发条件矩阵

| 调用链 | 是否 Fake Failure | 备注 |
|---|---|---|
| F003 update | ✅ Fake Failure（实测）| 5B F003 已确认 |
| F004 toggle | ❌ toggle 整链路阻塞（selectById 在 setStatus 之前），不会"假失败" | selectById 先抛 → setStatus 没执行 |
| F005 sync | ❌ sync 整链路阻塞（selectById 在 syncNow:88），事务回滚生效 | selectById 先抛 → runIncremental 没执行 |
| F002 list | ❌ list 一次性失败，无 UPDATE | 无写操作 |
| F007 dashboard | ⚠️ Fake Success（5A 已确认）| I008 范畴 |
| F006 sync log list | ❌ list 一次性失败，无 UPDATE | 无写操作 |

→ **F003 是唯一 Fake Failure 风险点**（update 端点的 select-after-update 模式）

---

## 5. 严重度评估

| 标准 | I011 命中？ |
|---|---|
| 🟢 LOW（仅文档风险）| ❌ |
| 🟡 MEDIUM（单次失败，无感知）| ❌ |
| 🟠 HIGH（多次失败，可观测但无告警）| ❌ |
| 🔴 **CRITICAL**（**生产持续静默失败 + 业务核心功能失效 + 数据漂移 + 无任何告警**）| ✅ **全中** |

### 5.1 升级依据

| 维度 | 评估 |
|---|---|
| 风险类型 | **生产实证**（5B F003 实测 DB 已变）|
| 触发场景 | 任何含"select-after-update"的 endpoint |
| 隐蔽性 | **高**（用户看到失败但数据已变）|
| 运维告警 | **无** |
| 数据一致性破坏 | **是**（UPDATE 已提交）|

---

## 6. 修复建议（**审计后** / 用户决策 D4 = 不修）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① select-after-update 重构** | `Result.success("已更新", i)` — 用入参 `i`（已 UPDATE）做返回，而不是重新 selectById | 不再依赖 selectById 成功 | 返回的 data 不含数据库默认值 / auto-fill 字段 |
| **② try-catch selectById** | try { selectById } catch (Exception e) { log.warn; return Result.success("已更新", i); } | 保留完整 data | 仍是 Fake Success（变种）|
| **③ 拆分 endpoint** | `POST /{id}/update` + `GET /{id}` — update 只返 logId；客户端主动 GET 拿最新 | 接口语义清晰 | 改动多 |
| **④ 接受现状** | 当 catch-only-log 不视为 bug（设计意图）| 不改 | 数据漂移风险持续 |

**推荐**：①（最小改动，消除 Fake Failure，不引入 Fake Success）。

### 6.1 适用场景

```java
// F003 update 重构示例
@PostMapping("/{id}")
public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody CrmIntegration i) {
    i.setId(id);
    i.setTenantId(TenantContextHolder.get().getTenantId());
    integrationMapper.updateById(i);
    // 不再调 selectById — 用入参 + 副作用字段构造响应
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", id);
    data.put("updated", true);
    return Result.success("已更新", data);
}
```

---

## 7. Phase 5B 验证策略

| 验证方式 | 是否适用 |
|---|---|
| **API-only（curl）** | ✅ 关键。手动调 `POST /crm-integration/{id}` — 期望：**Phase 5B 看到 HTTP 200 + code=500 + DB 已变** = I011 Fake Failure 链证据 |
| **DB-only** | ✅ 关键。SELECT 验证 UPDATE 副作用 |
| **Playwright E2E** | ❌ 关联 I007：无前端 edit 按钮 |
| **实际期望** | **预期 Phase 5B 暴露**：API 报错 + DB 已 UPDATE — 这是审计的**合法负向证据** |

---

## 8. 关联

- `audit/issues/issue-register.md` §八 I008（反向对称） + §十一 决策变更 + §十三 I011 新登记
- `audit/issues/I008-dashboard-catch-swallow.md`（Fake Success — 反向对称）
- `audit/evidence/F003-update-integration/result.md`（F003 验证完整报告，含 §3 I011 关键负向证据）
- `audit/evidence/F002-list-integration/result.md`（I002 触发条件）
- `audit/runtime/plan.md` §一.2 F003 + §三 关键负向证据
- `audit/runtime/report-5B.md` §2.1 F003 章节（PARTIAL-BLOCKER 判定 + I011 新发现）

---

## 9. 审计可见性证据

| 路径 | I011 是否生效 | 实测 traceId | DB 副作用 |
|---|---|---|---|
| `POST /crm-integration/2` body `syncDirection=BIDIRECTIONAL` | ✅ | `e7d0d2d715574e4faa936cff2a8cf4be` | `sync_direction=PULL → BIDIRECTIONAL` |

---

## 10. Phase 5B E2E 二次实证（dashboard 反向推算）

**提交**：commit `12a97ea57a98d268c864fc7c010b82b305d7bb93`
**触发 Feature**：F025「填表保存 → 列表新增行」E2E
**时间窗**：2026-09-13 17:37:35 → 17:44:31 (UTC+8)

### 10.1 dashboard totalIntegrations 时序

| 时间点 | 触发事件 | totalIntegrations | 来源 |
|---|---|---|---|
| 17:37:35 | E2E 第二轮跑前 | **2** | dashboard API 响应 |
| 17:44:31 | E2E 第三轮跑（含 F025 填表保存） | **3** | dashboard API 响应 |

**Δ = +1 行**（中间 7 分钟内）

### 10.2 时间窗内事件链（推断）

| 序号 | 事件 | API 返回 | dialog 关闭 | DB 实际 | 性质 |
|---|---|---|---|---|---|
| 1 | F025 填表（店铺=E2E测试集成5B, 方向=PULL）→ 点保存 | ❌ 500 | ❌ 不关 | ✅ **多 1 行** | **Fake Failure** |

### 10.3 实证结论

✅ **I011 Fake Failure 在 F025 端点（create/update 类）得到 E2E 级二次实证**：

1. 用户/前端 **看到失败**（dialog 未关 + ElMessage 报错）
2. **DB 已多出 1 行**（dashboard 数字 2→3 反推）
3. **无任何告警**（事务已提交，selectById 抛错被 GlobalExceptionHandler 捕获）

### 10.4 与 F003 update 端点的对比

| 维度 | F003 update（5B 首次实证） | F025 create（5B 二次实证）|
|---|---|---|
| endpoint | `POST /crm-integration/{id}` | `POST /crm-integration` |
| 后端 INSERT/UPDATE 时机 | 先 `updateById` 再 `selectById` | 先 `insert` 再 `selectById`（推断同模式） |
| E2E 验证手段 | 手动 curl + DB SELECT | dashboard totalIntegrations 计数 |
| 失败表现 | code=500 | dialog 不关 + ElMessage 报错 |
| 数据漂移证据 | `sync_direction` 已变 | `totalIntegrations` +1 |

### 10.5 严重度升级

原严重度：🔴 **CRITICAL**（5B F003 单端点实证）

升级依据：**E2E 级二次实证确认 I011 在 create 端点同样存在**：
- 不再是「仅 update 端点的理论风险」
- 是「**任何含 select-after-write 模式的写端点**」的共同问题
- 影响面从 F003 单点 → F001/F003/F029（凡有 select-after-write 的端点）

| 维度 | 评估 |
|---|---|
| 影响端点范围 | F001 create + F003 update + F029 status-update（待逐一验证）|
| 风险类型 | **生产二次实证**（5B E2E dashboard 数字反推）|
| 修复阶段 | **审计后**（用户决策 D4 = 不修）|

完整证据：`audit/evidence/F025-create-form/e2e-result.md` §3 + `audit/reports/e2e-summary.md` §A

**关键观察**：I011 触发后**无任何前端 UI 反馈**（关联 I007 — 前端无 edit 按钮），所以**用户根本不会主动调此 endpoint**。但**后端代码可被任何能访问 /crm-integration/* 的人触发**，包括：
- 直接 curl（联调 / 灰度）
- 第三方集成（CRM 内部 API 调用）
- 未来加 edit UI 后（I007 修复后）

→ **风险持续存在**，等用户修 I011。

---

**I011 登记完成。与 I008 共同构成"Fake Result 系列"，建议修复阶段合并处理。**

---

## 11. Post-Audit 修复实录（批次 2.2-A4）

### 11.1 修复动作

| 维度 | 内容 |
|---|---|
| **commit** | `0b5607e` fix(crm): I011 update selectById 移除（消除 Fake Failure）|
| **修改文件** | `CrmIntegrationService.java` 单文件，+3 / -1 行 |
| **改动** | `Result.success("已更新", integrationMapper.selectById(id))` → `Result.success("已更新", i)`（用入参）|
| **mvn compile** | ✅ BUILD SUCCESS（exit code 0）|
| **运行时验证** | ✅ API HTTP 200 + code=200 + DB UPDATE 生效 + 不再因 selectById 抛错 |

### 11.2 选择方案 ① 的理由

| 方案 | 是否选 | 理由 |
|---|---|---|
| **① select-after-update 重构** | ✅ | **消除根因（不调 selectById），不引入 Fake Success** |
| ② try-catch selectById + 降级 | ❌ | 仍是 Fake Success 变种 |
| ③ 拆分 endpoint | ❌ | 改动多，破坏现有 API 契约 |
| ④ 接受现状 | ❌ | Issue 必须关 |

### 11.3 修复前后对比

```java
// ❌ 修复前：Fake Failure 风险
integrationMapper.updateById(i);
return Result.success("已更新", integrationMapper.selectById(id));
//   ↑ updateById 已成功 → selectById 抛 I002 → API code=500 → DB 已变 → 数据漂移

// ✅ 修复后：用入参 i（已 UPDATE）作为 data
integrationMapper.updateById(i);
return Result.success("已更新", i);
//   ↑ 不会调 selectById → 永远不抛 → API code=200 + data 与提交字段一致
```

### 11.4 影响范围

| Feature | 旧行为 | 新行为 |
|---|---|---|
| F001 create | 同模式 → 沿用 I011 修复 | 未改（create 不在本次） |
| **F003 update** | API 500 / DB 已变 | **API 200 / DB 已变 / data = 入参** |
| F029 status-update | 同模式 | 待 5B 复测 |

### 11.5 一修一验清单

- [x] mvn compile 通过
- [x] git commit 独立（`0b5607e`）
- [x] I011 状态更新：OPEN → ✅ FIXED
- [x] A4 报告落档：`audit/post-audit/phase-2.2-A4-report.md`

---

**I011 修复完成（Post-Audit 2.2-A4）。**
