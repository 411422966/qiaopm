# I012 — Mock 适配器激活条件冲突（默认 NPE 风险）

| 维度 | 值 |
|---|---|
| **Issue ID** | I012 |
| **创建时间** | 2026-09-13 00:55 (UTC+8) |
| **Phase** | 5B — Runtime Verification |
| **类别** | 配置 / Spring Bean 条件冲突 |
| **严重度** | 🟠 中-高（生产环境 platform=MOCK 集成会 NPE）|
| **影响 Feature** | F015（Mock 适配器）、F018（Router 分发）、F012（known-ids 链路）|
| **关联 Issue** | **I001**（SoT 漂移 — EXT-24 §0.6 写"Mock fallback" 实测 NPE）|
| **修复阶段** | **审计后** |
| **状态** | ✅ **FIXED**（2026-09-23 15:13，Phase 2.9）|
| **修复 Commit** | `a6e2d7d`（删除注解）+ `a41ab2b`（javadoc 同步）|
| **验证证据** | `audit/post-audit/phase-2.9-I012-fix-report.md` |

---

## 1. 现象

### 1.1 Spring Bean 注册条件

```java
// backend/src/main/java/com/meiyetec/crm/MockCrmAdapter.java:34-36
@Component
@ConditionalOnMissingBean(name = "salesforceCrmAdapter")    // ← SF 存在时 Mock 不注册
public class MockCrmAdapter implements CrmAdapter { ... }

// backend/src/main/java/com/meiyetec/crm/SalesforceCrmAdapter.java
@Component                                                // ← 默认注册
public class SalesforceCrmAdapter implements CrmAdapter { ... }
```

### 1.2 实际激活结果

| 场景 | SalesforceCrmAdapter | MockCrmAdapter | Router 注入 |
|---|---|---|---|
| **当前（默认）** | ✅ 注册 | ❌ 不注册（因 SF 已存在）| 3 个（SF + HubSpot + Zoho），**无 Mock** |
| 关闭 SF | ❌ 不注册 | ✅ 注册 | 4 个（含 Mock）|

→ **当前生产环境默认无 Mock adapter！**

### 1.3 NPE 触发链

```java
// CrmAdapterRouter.java:28-36
public CrmAdapter getAdapter(String platform) {
    if (platform == null) return getAdapter("MOCK");
    CrmAdapter a = adapters.get(platform.toUpperCase());
    if (a == null) {
        // fallback to mock for unknown platforms in dev
        return adapters.get("MOCK");                       // ← MOCK 没注册时，返回 null
    }
    return a;
}
```

调用链：
1. `crmIntegrationApi.syncNow(id)` → 后端 `CrmIntegrationService.syncNow`
2. 同步链路调 `crmAdapterRouter.getAdapter(cfg.getPlatform())`
3. platform = `MOCK` → `adapters.get("MOCK")` → **null**
4. `null.fallback...` → 后续 adapter 调用 NPE

---

## 2. Phase 5B 实测证据

**完整 evidence**：`audit/evidence/F015-mock-adapter/`

### 2.1 启动日志观察

文件：`audit/evidence/F015-mock-adapter/log-startup-adapter.txt`

观察到 Router 注入的 adapter：
- `SalesforceCrmAdapter`（line 1）
- `HubSpotCrmAdapter`（line 2）
- `ZohoCrmAdapter`（line 3）
- **无 MockCrmAdapter**

### 2.2 mock 行为证据

文件：`audit/evidence/F015-mock-adapter/log-mock-evidence.txt`

```
Search "MockCrmAdapter" in startup logs:
- MockCrmAdapter 初始化  → 0 匹配
- @ConditionalOnMissingBean 触发  → SF bean 已存在
```

→ 启动时 `@ConditionalOnMissingBean(name = "salesforceCrmAdapter")` 判定失败，Mock **未注册**。

### 2.3 Router 行为证据

文件：`audit/evidence/F015-mock-adapter/log-router-evidence.txt`

```
Router.adapters = {
  SALESFORCE: SalesforceCrmAdapter@xxx,
  HUBSPOT: HubSpotCrmAdapter@yyy,
  ZOHO: ZohoCrmAdapter@zzz
}
adapters.get("MOCK") → null  ← fallback 仍返回 null
```

---

## 3. 后果分析

### 3.1 NPE 风险场景

| 集成 platform | 后果 |
|---|---|
| `SALESFORCE` | ✅ 正常（SF adapter 注册了）|
| `HUBSPOT` | ✅ 正常（HubSpot adapter 注册了）|
| `ZOHO` | ✅ 正常（Zoho adapter 注册了）|
| **`MOCK`** | ❌ **NPE**（Mock 未注册，Router fallback 仍 null）|
| 任意 platform（含小写 / 拼错）| ❌ 走 fallback → adapters.get("MOCK") → null → NPE |

### 3.2 当前 I002 掩盖

**当前环境（I002 未修）**：所有 sync 链路在 `CrmIntegrationService.syncNow:88` 的 `selectById` 抛 `BadSqlGrammarException` → I008/I011 chain 阻断 → Router.getAdapter **永远到不了**。

→ **I002 阻塞反而"保护"了 NPE 暴露** — I002 修好后 NPE 立刻暴露。

### 3.3 与 I001 SoT 漂移的关联

| 维度 | EXT-24 §0.6 / §5 SoT 写 | 实测代码 |
|---|---|---|
| HUBSPOT adapter | "未实现（前端可选会 500）" | HubSpotCrmAdapter.java 完整 5 方法 |
| ZOHO adapter | "未实现（前端可选会 500）" | ZohoCrmAdapter.java 完整 5 方法 |
| Mock fallback | "@ConditionalOnMissingBean(name = ...) → Mock 是 fallback" | 条件性激活（SF 存在时 Mock 不激活）|
| NPE | "router.getAdapter 返回 null → NPE" | 当前 NPE 风险存在（platform=MOCK）|

→ I001 SoT 漂移 + I012 激活冲突 = **同一类问题不同侧面**。

---

## 4. 严重度评估

| 标准 | I012 命中？ |
|---|---|
| 🟢 LOW（仅文档风险）| ❌ |
| 🟡 MEDIUM（单次失败，无感知）| ❌ |
| 🟠 **HIGH**（多次失败，可观测但无告警）| ✅ |
| 🔴 CRITICAL | ❌（暂被 I002 掩盖）|

### 4.1 升级路径

| 触发条件 | 严重度 |
|---|---|
| 当前（I002 阻塞）| 🟠 中-高（潜在风险）|
| I002 修复后 | 🔴 **CRITICAL**（NPE 即刻暴露）|

---

## 5. 修复建议（**审计后**）

| 方案 | 改动 | 优点 | 缺点 |
|---|---|---|---|
| **① Mock 改用显式条件** | `@ConditionalOnProperty(name="crm.mock.enabled", havingValue="true", matchIfMissing=true)` — 默认启用 | Mock 默认存在 | SF + Mock 共存（可能冲突）|
| **② Mock 改用 @Profile("dev")** | Mock 仅 dev profile 启用 | 明确 dev-only | 生产无 Mock fallback |
| **③ Router fallback 抛异常** | `if (a == null && adapters.get("MOCK") == null) throw BizCode.NOT_FOUND` | 安全兜底 | 改动 Router |
| **④ 接受现状** | 不改（生产建议禁用 platform=MOCK 集成）| 不改 | NPE 风险持续 |

**推荐**：① + ③ 组合（默认启用 Mock + Router 安全兜底）。

### 5.1 推荐实现

```java
// MockCrmAdapter.java
@Component
@ConditionalOnProperty(name = "crm.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockCrmAdapter implements CrmAdapter { ... }

// CrmAdapterRouter.java
public CrmAdapter getAdapter(String platform) {
    if (platform == null) return getAdapter("MOCK");
    CrmAdapter a = adapters.get(platform.toUpperCase());
    if (a == null) {
        a = adapters.get("MOCK");
        if (a == null) {
            throw new BizException(BizCode.NOT_FOUND, "平台不支持且 Mock 未启用: " + platform);
        }
    }
    return a;
}
```

---

## 6. Phase 5B 验证策略

| 验证方式 | 是否适用 |
|---|---|
| **Static-Only** | ✅ 关键。检查 `@ConditionalOnMissingBean` + `@Component` |
| **Startup Log 观察** | ✅ 关键。grep 启动日志确认 Mock adapter bean 是否注册 |
| **API-only（curl）** | ❌ 当前 I002 阻塞，NPE 不会暴露 |
| **DB-only** | ❌ 不适用 |

### 6.1 Phase 5B 已做的

- ✅ 代码静态审查（`MockCrmAdapter.java:34-36`）
- ✅ Router 代码审查（`CrmAdapterRouter.java:28-36`）
- ✅ 启动日志 grep（确认 Router 注入列表无 Mock）
- ✅ 5B 验证：`platform=MOCK` 集成存在（NPE 风险已识别）

---

## 7. 关联

- `audit/issues/issue-register.md` §九 I005 + §十一 决策变更 + §十三 I012 新登记
- `audit/issues/I001-ext24-sot-conflict.md`（SoT 漂移 — 同根因）
- `audit/evidence/F015-mock-adapter/result.md`（F015 验证完整报告 + I012 关键证据）
- `audit/evidence/F018-router-fallback/result.md`（F018 CONFLICT + I012 联动）
- `audit/evidence/F013-adapter-contract/result.md`（F013 4 adapter 全完整实证）
- `audit/runtime/plan.md` §一.2 F015 + §三 关键负向证据
- `audit/runtime/report-5B.md` §2.5 F015 + §2.11 F018 章节

---

## 8. 修复记录（2026-09-23 · Phase 2.9）

### 8.1 选定方案

**方案 C：删除 `@ConditionalOnMissingBean(name = "salesforceCrmAdapter")` 注解**

理由：
- `SalesforceCrmAdapter.java:54` 无条件 `@Component` → 注解条件永远不满足
- Mock 必须始终注册 → 与 SoT 「Mock fallback」语义一致
- 最小改动（一行注解 + 对应 import）

### 8.2 实际改动

**文件 1：`backend/src/main/java/com/meiyetec/crm/MockCrmAdapter.java`**

```diff
 import com.meiyetec.entity.CrmIntegration;
 import lombok.extern.slf4j.Slf4j;
-import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
 import org.springframework.stereotype.Component;

 import java.util.*;
@@
 @Slf4j
 @Component
-@ConditionalOnMissingBean(name = "salesforceCrmAdapter")
 public class MockCrmAdapter implements CrmAdapter {
```

**Commit**：`a6e2d7d` — `fix(I012): MockCrmAdapter 删除 @ConditionalOnMissingBean,使其始终注册`（1 file, +0 -2）

**文件 2：javadoc 同步（修复后追加）**

```diff
- * <p>激活条件：当未配置 Salesforce 凭据 / 平台为 MOCK 时启用。
- * 用于本地开发 + 联调，避免无真实凭据导致排程阻塞。
+ * <p>本 Adapter 始终注册（无条件 {@code @Component}），
+ * 按 {@code platform=MOCK} 路由时使用，供本地开发 + 联调，
+ * 避免无真实凭据导致排程阻塞。
+ *
+ * <p>历史：曾标注 {@code @ConditionalOnMissingBean(name="salesforceCrmAdapter")}，
+ * 但 SalesforceCrmAdapter 无条件 {@code @Component}，导致 Mock 永不注册、
+ * {@code CrmAdapterRouter.getAdapter("MOCK")} 返回 null → NPE（详见 I012）。
+ * 该注解已于 2026-09-23 移除（commit {@code a6e2d7d}）。
```

**Commit**：`a41ab2b` — `docs(I012): MockCrmAdapter javadoc 同步: 标注始终注册 + 引用 I012 历史`（1 file, +8 -2）

### 8.3 验证证据

| 验证项 | 结果 |
|---|---|
| `mvn clean compile` | EXIT_CODE=0 / BUILD SUCCESS（1543 源文件）|
| `mvn compile`（二次） | EXIT_CODE=0 / BUILD SUCCESS |
| 启动日志 | MockCrmAdapter Bean 已注册（从 sync 成功推断）|
| `POST /crm-integration/2/sync`（id=2 = MOCK / BIDIRECTIONAL / syncEnabled=1）| **HTTP 200** / `code=200 "已同步"` / `platform=MOCK` / `status=SUCCESS` / `successCount=4` / `failCount=0` / `recordsTotal=4` / `durationMs=142` |
| `crm_sync_log` 新记录 | id=70 / `platform=MOCK` / `status=SUCCESS` / `success_count=4` / `fail_count=0` |

### 8.4 副作用审查

- ✅ SalesforceCrmAdapter 仍正常注册（`@Component` 未动）
- ✅ SALESFORCE 集成（id=1/4/5）不受影响
- ✅ 其他 Mock 适配器（MockEcommerceAdapter / MockBnplProvider / StubBleGattClient）未动
- ✅ 无 Yaml / SoT / Frontend 变更

### 8.5 同类 Bug 发现

修复 I012 时，对全后端 `@ConditionalOnMissingBean` 5 处用法做审查：

| # | 文件 | 同类 Bug? |
|---|---|---|
| 1 | `MockCrmAdapter.java:18` | ❌ 是 I012（已修）|
| 2 | `MockEcommerceAdapter.java:18` | ❌ 是 **I087**（已新登记，Phase 2.10+ 修）|
| 3 | `MockBnplProvider.java:21` | ✅ OK（目标 `realBnplProvider` 不存在）|
| 4 | `StubBleGattClient.java:79` | ✅ OK（按类检测，type-safe）|
| 5 | `SmCryptoConfig.java:21` | ✅ OK（按类检测）|

完整记录：`audit/issues/I087-mock-ecommerce-adapter-bug.md`

---

**I012 FIXED（Phase 2.9）。同类 Bug I087 已登记，Phase 2.10+ 修。**
