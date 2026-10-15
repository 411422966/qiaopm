# Phase 2.10 — I087 Fix Report

> **I087：MockEcommerceAdapter `@ConditionalOnMissingBean` 条件冲突 Bug 修复报告**
> 日期：2026-09-23
> 修复者：Cursor Agent
> 上一 Phase：Phase 2.9（I012 同类 Bug 修复）

---

## 1. Issue 摘要

| 字段 | 值 |
|---|---|
| **Issue ID** | **I087** |
| **严重度** | 🟡 P1（与 I012 完全同构 — 代码 Bug，非 SoT vs 代码冲突）|
| **问题** | `MockEcommerceAdapter.java:17` 标注 `@ConditionalOnMissingBean(name = "youzanEcommerceAdapter")`，但 `YouzanEcommerceAdapter.java:53` 无条件 `@Component`，导致任何部署中 Mock 永不注册 |
| **触发路径** | `EcommerceAdapterRouter.getAdapter("MOCK")` → `adapters.get("MOCK")` → **null** → **NPE** |
| **Feature** | F017 / F016 / F018（Ecommerce adapter/router）|
| **当前 DB 状态** | `ecommerce_platform` 无 MOCK 记录（Bug 尚未在运行时暴露，但代码缺陷存在）|

---

## 2. 修复方案

**方案 C：删除 `@ConditionalOnMissingBean`**

与 I012（Phase 2.9）完全相同的修复逻辑：

- `YouzanEcommerceAdapter` 无条件 `@Component` → 条件永远不满足
- 删除条件注解 → `MockEcommerceAdapter` 始终注册 → Router fallback 始终可用
- 最小改动（一行注解 + 一行 import）
- 与 SoT "Mock 是 fallback" 意图一致

---

## 3. 代码 Diff

### 3.1 commit `298f320` — 代码修复（-2 行）

**文件**：`backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java`

```diff
- import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
  import org.springframework.stereotype.Component;

  @Slf4j
  @Component
- @ConditionalOnMissingBean(name = "youzanEcommerceAdapter")
  public class MockEcommerceAdapter implements EcommerceAdapter {
```

**净改动**：-2 行（删 import + 删注解）

### 3.2 commit `8c70012` — javadoc 同步（+8 / -2 行）

**文件**：`backend/src/main/java/com/meiyetec/ecommerce/MockEcommerceAdapter.java`

```diff
  /**
   * EXT-16：Mock 电商适配器（dev fallback，无凭据时启用）
   *
-  * <p>激活条件：当未配置 Youzan 凭据 / 平台为 MOCK 时启用。
+  * <p>本 Adapter 始终注册（无条件 {@code @Component}），
+  * 按 {@code platform=MOCK} 路由时使用，供本地开发 + 联调，
+  * 避免无真实凭据导致排程阻塞。
+  *
+  * <p>历史：曾标注 {@code @ConditionalOnMissingBean(name="youzanEcommerceAdapter")}，
+  * 但 YouzanEcommerceAdapter 无条件 {@code @Component}，
+  * 导致 Mock 永不注册、{@code EcommerceAdapterRouter.getAdapter("MOCK")} 返回 null → NPE（详见 I087）。
+  * 该注解已于 commit 298f320（2026-09-23）移除。
   */
```

**净改动**：+8 行（新增始终注册描述 + 历史段落）/ -2 行（原激活条件描述）

---

## 4. 编译验证

| 步骤 | 命令 | 结果 |
|---|---|---|
| 编译 | `mvn -q -DskipTests compile` | ✅ EXIT=0 |

---

## 5. Git Log

```
8c70012 docs(I087): MockEcommerceAdapter javadoc 同步: 标注始终注册 + 引用 I087
298f320 fix(I087): MockEcommerceAdapter 删除 @ConditionalOnMissingBean，使其始终注册
9c5737f docs(I012/I087): Phase 2.9 修复报告 + I087 新登记
a41ab2b docs(I012): MockCrmAdapter javadoc 同步: 标注始终注册 + 引用 I012 历史
a6e2d7d fix(I012): MockCrmAdapter 删除 @ConditionalOnMissingBean,使其始终注册
```

---

## 6. 文档更新

| 文件 | 变更 |
|---|---|
| `audit/issues/I087-mock-ecommerce-adapter-bug.md` | 状态 OPEN → FIXED；填修复记录（commit hash、行号、修复者）|
| `audit/issues/issue-register.md` | Fix Phase: Post-Audit → Phase 2.10 已修复；表格更新 I087 行状态 + 行号修正 |

---

## 7. 待用户手动验证

以下验证需要用户在本地后端重启后执行：

### 7.1 启动日志验证

后端重启后，观察启动日志是否包含：

```
// 预期（EcommerceAdapterRouter 构造时收集 adapters）：
EcommerceAdapterRouter initialized with adapters: [YOUZAN, MOCK]
// 或类似日志，确认 MockEcommerceAdapter Bean 已注册
```

### 7.2 curl 验证（电商 sync 端点）

当前 `ecommerce_platform` 无 MOCK 记录，可通过以下方式触发 Mock 路径：

**方案 A：直接验证 Router（无 DB 依赖）**

```bash
# 1. 新建一条 MOCK 平台记录（测试用）
mysql -h 127.0.0.1 -P 3307 -u root -proot123456 meiyetec -e "
INSERT INTO ecommerce_platform (platform, status, tenant_id)
VALUES ('MOCK', 'ACTIVE', 0);"

# 2. 触发 sync（product sync 示例）
curl -X POST "http://127.0.0.1:8080/ecommerce/product/sync/1?force=1" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json"

# 期望：HTTP 200（不是 500 adapter is null）
```

**方案 B：验证 Router 自身（无需 DB）**

```bash
# 调用 EcommerceAdapterRouter 的 API（如果有 health 端点）
# 或通过 backend/src/test 单元测试验证
```

### 7.3 DB ecommerce_sync_log 查询

```bash
mysql -h 127.0.0.1 -P 3307 -u root -proot123456 meiyetec -e "
SELECT id, platform, status, success_count, fail_count, error_message, duration_ms
FROM ecommerce_sync_log ORDER BY id DESC LIMIT 3;"
```

**预期**：无 NPE 错误（`error_message` 不含 "NullPointerException" / "adapter is null"）。

### 7.4 清理测试数据（如用方案 A）

```bash
mysql -h 127.0.0.1 -P 3307 -u root -proot123456 meiyetec -e "
DELETE FROM ecommerce_platform WHERE platform='MOCK';"
```

---

## 8. 与 I012 的对称性

| 维度 | I012（CRM）| I087（Ecommerce）|
|---|---|---|
| 问题文件 | `MockCrmAdapter.java:16` | `MockEcommerceAdapter.java:17` |
| 条件目标 | `salesforceCrmAdapter` | `youzanEcommerceAdapter` |
| Router 类 | `CrmAdapterRouter.java` | `EcommerceAdapterRouter.java` |
| 触发路径 NPE | `getAdapter("MOCK")` → null | `getAdapter("MOCK")` → null |
| 修复 commit | `a6e2d7d`（代码）+ `a41ab2b`（javadoc）| `298f320`（代码）+ `8c70012`（javadoc）|
| 验证状态 | ✅ VERIFIED（id=70 同步成功）| ⏳ 待用户手动验证 |
| 当前 DB 有 MOCK 记录 | ✅ 有（id=2）| ❌ 无（潜在风险，非活跃）|

---

## 9. 结论

- **代码修复**：✅ 完成（commit `298f320` + `8c70012`）
- **编译验证**：✅ 通过（EXIT=0）
- **文档更新**：✅ 完成（I087 md + issue-register.md）
- **运行时验证**：✅ 完成（2026-09-23 23:39 — HTTP 200 + DB id=1 SUCCESS）
- **Issue 状态**：✅ **FIXED & VERIFIED**（代码层面 + 运行时层面均确认）

---

## 10. 关联

- I012（CRM 同类 Bug）— Phase 2.9 ✅ FIXED & VERIFIED
- F016 / F017 / F018（Ecommerce adapter/router）— 待 Phase 后续评估 Result
- Phase 2.9 报告：`audit/post-audit/phase-2.9-I012-fix-report.md`
- 完整 Issue 档案：`audit/issues/I087-mock-ecommerce-adapter-bug.md`

---

## 11. 运行时验证结果（用户手动）

| 字段 | 值 |
|---|---|
| **验证时间** | 2026-09-23 23:39 (UTC+8) |
| **验证人** | 用户 |
| **验证方式** | 重启后端 → 插入 MOCK 测试记录 → curl sync → 查 DB → 清理测试记录 |

### 11.1 curl 响应

```json
{
  "code": 200,
  "msg": "已同步",
  "data": {
    "platform": "MOCK",
    "syncType": "PRODUCT",
    "successCount": 3,
    "failCount": 0,
    "durationMs": 182,
    "status": "SUCCESS"
  }
}
```

- HTTP 状态码：**200**
- 业务码：**200 / "已同步"**
- 平台：**MOCK**
- 同步类型：**PRODUCT**
- 成功：**3** / 失败：**0**
- 耗时：**182 ms**
- 状态：**SUCCESS**

### 11.2 DB 记录（`ecommerce_sync_log.id=1`）

| 字段 | 值 |
|---|---|
| `platform` | `MOCK` |
| `status` | `SUCCESS` |
| `success_count` | `3` |
| `fail_count` | `0` |
| `error_message` | `NULL` |

→ 无 NPE / 无 `adapter is null` 错误。

### 11.3 清理

- ✅ `DELETE FROM ecommerce_platform WHERE platform='MOCK'` 已执行

### 11.4 验证结论

| 维度 | 状态 | 证据 |
|---|---|---|
| Mock Adapter 注册 | ✅ | EcommerceAdapterRouter 构造时收集到 MOCK Bean（运行时无 NPE 即间接证据） |
| Router fallback 链路 | ✅ | curl HTTP 200 正常返回 |
| 真实数据返回 | ✅ | successCount=3（与 MockEcommerceAdapter SAMPLE_PRODUCTS 三条一致） |
| DB 日志写入 | ✅ | ecommerce_sync_log id=1 字段齐全 |
| 失败 / 异常 | ✅ | failCount=0 / error_message=NULL |

→ **Phase 2.10 I087 完全关闭**：✅ **FIXED & VERIFIED**（代码层面 + 运行时层面均确认）
