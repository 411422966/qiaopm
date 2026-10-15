# I087 — MockEcommerceAdapter 同类条件冲突 Bug

| 字段 | 值 |
|---|---|
| **Issue ID** | **I087** |
| **严重度** | 🟡 **P1**（与 I012 同类 — 代码 Bug，非 SoT vs 代码冲突）|
| **发现时间** | 2026-09-23 00:10 (UTC+8) |
| **发现方式** | Phase 2.9 修复 I012 时 `@ConditionalOnMissingBean` 同类用法审查 |
| **Feature** | F017（MockEcommerceAdapter）/ F016（YouzanEcommerceAdapter）/ F018（EcommerceAdapterRouter）|
| **Fix Phase** | **Phase 2.10（2026-09-23）— ✅ 已修复** |

---

## 1. 问题定位

### 1.1 触发路径

```
EcommerceAdapterRouter.getAdapter(platform)                           EcommerceAdapterRouter.java:21-29
  ├─ platform == null → getAdapter("MOCK")
  └─ platform == "MOCK" → adapters.get("MOCK") → null                  ← NPE
```

### 1.2 根因

```java
// MockEcommerceAdapter.java:17-18
@Component
@ConditionalOnMissingBean(name = "youzanEcommerceAdapter")   // ← 永真不成立
public class MockEcommerceAdapter implements EcommerceAdapter { ... }
```

但 `YouzanEcommerceAdapter.java:48` 是**无条件 `@Component`**：

```java
@Component
public class YouzanEcommerceAdapter implements EcommerceAdapter { ... }
```

→ 在任何包含 YouzanEcommerceAdapter 的部署（生产 / 联调 / dev 都含）中，`youzanEcommerceAdapter` Bean **永远存在** → `@ConditionalOnMissingBean` **永远不满足** → `MockEcommerceAdapter` **永远不被注册** → Router 找不到 MOCK Bean → `getAdapter("MOCK")` 返回 null → NPE。

### 1.3 与 I012 的对仗

| 维度 | I012（CRM） | I087（Ecommerce） |
|---|---|---|
| 目标 Bean | `salesforceCrmAdapter` | `youzanEcommerceAdapter` |
| 目标是否无条件 `@Component` | ✅ 是 | ✅ 是 |
| Mock 是否永不注册 | ✅ 是 | ✅ 是 |
| Router fallback 路径 NPE | ✅ 是 | ✅ 是 |
| 修复方案 | C：删除 `@ConditionalOnMissingBean` | C：删除 `@ConditionalOnMissingBean`（同方案）|

---

## 2. 业务影响

### 2.1 受影响调用路径

| 调用方 | 文件 | 风险 |
|---|---|---|
| （待验证：手动触发 / 排程 / webhook）| （待 grep）| `cfg.getPlatform()` 为 null / `"MOCK"` / 任何非 `YOUZAN` 值时命中 MOCK fallback → NPE |

### 2.2 受影响 Feature（推测，待核实）

| Feature | 影响 |
|---|---|
| F016 / F017（Youzan / Mock adapter）| F016 主 Result 不受影响（Youzan 始终注册）；F017 同 I012 / F015，主 Result 改为 PARTIAL-NON-BLOCKER |
| F018（EcommerceAdapterRouter）| 可能 PARTIAL-NON-BLOCKER（NPE 风险，但 Router 仍有代码）|

### 2.3 SoT 现状

`ext-22-crm-integration.mdc` §0.6 / `ext-22-ecommerce-*` 未明确声明 fallback 触发条件。

---

## 3. 推荐修复

**方案 C：删除 `@ConditionalOnMissingBean(name = "youzanEcommerceAdapter")` 注解**

理由与 I012 完全一致：
- YouzanEcommerceAdapter 无条件注册 → `@ConditionalOnMissingBean` 永远不满足
- 删除后 MockEcommerceAdapter 始终注册 → Router fallback 始终可用
- 最小改动（一行注解 + 对应 import）
- 与 SoT "Mock 是 fallback" 意图一致

**预计改动文件**：
1. `MockEcommerceAdapter.java` — 删 L18 注解 + L8 import（如该文件内仅此一处用）
2. 无 SoT 变更
3. 无 YAML 变更

---

## 4. 修复记录

| 字段 | 值 |
|---|---|
| **状态** | ✅ **FIXED & VERIFIED** |
| **修复日期** | 2026-09-23 |
| **验证日期** | 2026-09-23 |
| **修复 commit（代码）** | `298f320`（删 @ConditionalOnMissingBean + import） |
| **修复 commit（javadoc）** | `8c70012`（javadoc 同步：始终注册描述 + I087 引用） |
| **修复 commit（验证文档）** | `39cf515`（追加运行时验证证据） |
| **修复文件** | `backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java` |
| **修复内容** | 删 `L17: @ConditionalOnMissingBean(name = "youzanEcommerceAdapter")` + 删对应 import |
| **修复者** | Cursor Agent（Phase 2.10 I087 Fix） |
| **验证方式** | `mvn compile` ✅（EXIT=0）+ 后端重启 + curl `POST /ecommerce/product/sync/{id}?force=1`（用户实测）|

---

## 5. 运行时验证记录

| 字段 | 值 |
|---|---|
| **验证时间** | 2026-09-23 23:39 (UTC+8) |
| **验证人** | 用户（本地手动） |
| **验证步骤** | ① 重启后端 ② 插入 `ecommerce_platform` MOCK 测试记录 ③ `curl POST /ecommerce/product/sync/{id}?force=1` ④ `SELECT * FROM ecommerce_sync_log` ⑤ 删除 MOCK 测试记录 |
| **curl 响应** | `HTTP 200` / `code=200 "已同步"` / `platform=MOCK` / `syncType=PRODUCT` / `successCount=3` / `failCount=0` / `durationMs=182` / `status=SUCCESS` |
| **DB 记录** | `ecommerce_sync_log.id=1`：`platform=MOCK` / `status=SUCCESS` / `success_count=3` / `fail_count=0` / `error_message=NULL` |
| **结果** | ✅ **VERIFIED** — MockEcommerceAdapter 已始终注册，Router fallback 正常返回样本数据，无 NPE |
| **清理** | ✅ 已执行 `DELETE FROM ecommerce_platform WHERE platform='MOCK'` |

---

## 6. 关联

- **I012**（CRM 同类 Bug）— 已 Phase 2.9 修复方案 C
- **F016 / F017 / F018**（Ecommerce adapter/router）— 待 Phase 2.10 重新评估
- **Phase 2.9 报告**：`audit/post-audit/phase-2.9-I012-fix-report.md` §3（同类 Bug 表格）

## 7. 待办（全部完成）

- [x] Phase 2.10：执行 MockEcommerceAdapter 修复（方案 C） ✅ (`298f320`)
- [x] 验证 EcommerceAdapterRouter 启动日志收集到 2 个 adapter（Youzan + Mock） ✅（DB sync log id=1 成功）
- [x] 验证手动 syncNow(MOCK) 不再 NPE ✅（HTTP 200 / successCount=3 / durationMs=182）
- [ ] 同步更新 F016 / F017 / F018 主 Result（如需要）— 留待后续 Feature 重新评估
- [x] issue-register.md I087 状态：OPEN → FIXED → FIXED & VERIFIED ✅
