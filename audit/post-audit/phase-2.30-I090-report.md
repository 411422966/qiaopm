# Phase 2.30 — I090 EcommerceService 全 9 端点租户隔离 Fix Report

> **修复时刻**：2026-09-30 23:30 (UTC+8)
> **Commit**：`1c2c165`
> **执行人**：Cursor Assistant
> **关联 Issue**：I090（EcommerceService 租户隔离）
> **优先级**：P1 PARTIAL-BLOCKER（数据安全 / 跨租户访问）
> **修复范围**：全 9 个端点（用户决策 D1=B，超出 I090 issue 描述的 3 端点）
> **新登记 Issue**：I092（4 表历史 `tenant_id=0` 数据迁移）
> **验收标准**：mvn compile Exit 0

---

## 1. 背景与 I090 真实范围

### 1.1 I090 子项拆解（用户决策 D1=B）

| 端点 | I090 描述 | 实际修复 | Commit |
|---|---|---|---|
| `POST /platform` createPlatform | ✅ I090 已点 | ✅ | `1c2c165` |
| `GET /platform/list` listPlatforms | ✅ I090 已点 | ✅ | `1c2c165` |
| `POST /platform/{id}` updatePlatform | ✅ I090 已点 | ✅ | `1c2c165` |
| `GET /platform/{id}/health` platformHealth | ❌ I090 未提 | ✅ | `1c2c165` |
| `GET /product/list` listProducts | ❌ I090 未提 | ✅ | `1c2c165` |
| `POST /product/sync/{platformId}` syncProducts | ❌ I090 未提 | ✅ | `1c2c165` |
| `GET /order/list` listOrders | ❌ I090 未提 | ✅ | `1c2c165` |
| `POST /order/sync/{platformId}` syncOrders | ❌ I090 未提 | ✅ | `1c2c165` |
| `GET /dashboard` dashboard | ❌ I090 未提 | ✅ | `1c2c165` |

**用户决策**：选范围 B（全 9 端点），理由：CRM I005-C4 同时一并修了 8 端点，系统级修复避免分散修复。

---

## 2. 问题描述（改前）

### 2.1 createPlatform（L62-67）

```java
public Result<EcommercePlatform> createPlatform(@RequestBody EcommercePlatform p) {
    if (p.getPlatform() == null) return Result.error(BizCode.BAD_REQUEST, "platform 必填");
    if (!ALLOWED_PLATFORMS.contains(p.getPlatform())) ...
    if (p.getStatus() == null) p.setStatus("ACTIVE");
    p.setTenantId(0L);   // ⚠️ 写死 0L
    p.setId(null);
    platformMapper.insert(p);
}
```

### 2.2 listPlatforms（L68-72）

```java
public Result<List<EcommercePlatform>> listPlatforms() {
    return Result.success(platformMapper.selectList(new LambdaQueryWrapper<EcommercePlatform>()
            .orderByDesc(EcommercePlatform::getId)));  // ⚠️ 无 tenantId 过滤
}
```

### 2.3 updatePlatform（L74-79）

```java
public Result<EcommercePlatform> updatePlatform(@PathVariable Long id, @RequestBody EcommercePlatform p) {
    p.setId(id);
    platformMapper.updateById(p);   // ⚠️ 无 setTenantId 覆盖，可被入参穿透篡改
    return Result.success("已更新", platformMapper.selectById(id));
}
```

### 2.4 platformHealth（L82-87）

```java
public Result<EcommerceAdapter.HealthStatus> platformHealth(@PathVariable Long id) {
    EcommercePlatform pf = platformMapper.selectById(id);  // ⚠️ 无跨租户检查
    if (pf == null) return Result.error(BizCode.NOT_FOUND, "平台不存在");
    EcommerceAdapter adapter = adapterRouter.getAdapter(pf.getPlatform());
    return Result.success(adapter.healthCheck(pf));   // ⚠️ 可能泄露凭据
}
```

### 2.5 listProducts（L93-101）

```java
public Result<List<EcommerceProduct>> listProducts(@RequestParam(required = false) Long platformId) {
    LambdaQueryWrapper<EcommerceProduct> qw = new LambdaQueryWrapper<EcommerceProduct>()
            .orderByDesc(EcommerceProduct::getId);   // ⚠️ 无 tenantId 过滤
    if (platformId != null) {
        EcommercePlatform pf = platformMapper.selectById(platformId);   // ⚠️ 跨租户 selectById
        ...
    }
    return Result.success(productMapper.selectList(qw));
}
```

### 2.6 syncProducts（L107-132）

```java
EcommercePlatform pf = platformMapper.selectById(platformId);  // ⚠️ 跨租户
...
syncLog.setTenantId(0L);   // ⚠️ 写死 0L
```

### 2.7 listOrders / syncOrders

**完全同上模式**（product/order 对称实现）。

### 2.8 dashboard（L394-405）

```java
@GetMapping("/dashboard")
public Result<Map<String, Object>> dashboard() {
    try {
        data.put("totalPlatforms", platformMapper.selectCount(null));   // ⚠️ 全表
        data.put("totalProducts", productMapper.selectCount(null));     // ⚠️ 全表
        data.put("totalOrders", orderMapper.selectCount(null));         // ⚠️ 全表
        ...
    } catch (Exception ignore) {}
}
```

### 2.9 实际后果

- **跨租户篡改**：租户 A 可 `POST /ecommerce/platform/{租户B的平台id}` 改 webhook URL / 凭据
- **跨租户读取**：租户 A 可 `GET /ecommerce/platform/list` 看到所有租户的平台
- **跨租户同步**：租户 A 可触发租户 B 的同步任务，污染 sync_log
- **跨租户统计**：`/ecommerce/dashboard` 返回全平台总数字
- **历史数据**：4 表 `tenant_id=0` 老数据，修复后 `listPlatforms` 仍可见，但 `selectPlatformByTenant` 跨租户 → 403（admin dataScope=ALL 例外）

---

## 3. 修复内容

### 3.1 helper（新增 L461-475）

```java
/**
 * I090：跨租户 selectById。tenantId 不匹配时抛 {@link BizCode} (403)；
 * admin {@code dataScope=ALL} 仍可跨租户读（与 CrmIntegrationService.syncNow 模式一致）。
 *
 * @return 平台对象；id 不存在返回 null；跨租户抛 BusinessException
 */
private EcommercePlatform selectPlatformByTenant(Long id, Long tenantId) {
    EcommercePlatform pf = platformMapper.selectById(id);
    if (pf == null) return null;
    if (!"ALL".equals(TenantContextHolder.get().getDataScope())
            && pf.getTenantId() != null && !pf.getTenantId().equals(tenantId)) {
        throw new BusinessException(BizCode.TENANT_DENIED,
                "跨租户访问被拒绝（platform id=" + id + "）");
    }
    return pf;
}
```

**模式参照**：
- `CrmIntegrationService.syncNow`：跨租户拒绝 + dataScope=ALL 例外
- `WalletController`：跨租户抛 `BizCode.TENANT_DENIED`（语义化优于 `FORBIDDEN`）
- `BusinessException.forbidden(msg)` / `BizCode.TENANT_DENIED` 是项目标准用法

### 3.2 9 端点改造

| # | 端点 | 改造点 |
|---|---|---|
| 1 | createPlatform | `p.setTenantId(tenantId)` 替换 `0L` |
| 2 | listPlatforms | 非 ALL dataScope → `qw.eq(...::getTenantId, tenantId)` |
| 3 | updatePlatform | `p.setTenantId(tenantId)` 覆盖入参 + `selectPlatformByTenant` 校验 |
| 4 | platformHealth | `selectPlatformByTenant` 跨租户拒绝（adapter.healthCheck 凭据风险） |
| 5 | listProducts | `qw.eq(...::getTenantId, tenantId)` + platformId 用 `selectPlatformByTenant` |
| 6 | syncProducts | `selectPlatformByTenant` + `syncLog.setTenantId(tenantId)` |
| 7 | listOrders | 同 listProducts 模式 |
| 8 | syncOrders | 同 syncProducts 模式 |
| 9 | dashboard | `selectCount(qw)` 替换 `selectCount(null)`，3 个 qw 都带 tenantId 条件 |

### 3.3 import 新增

```java
import com.meiyetec.exception.BusinessException;
```

（项目标准用法，PosRefundController、WalletController、MarketActivityServiceImpl 等均用此）

---

## 4. 决策记录（用户拍板）

| # | 决策 | 用户选择 | 实施 |
|---|---|---|---|
| **D1** | 修复范围 | 选 B：全 9 端点 | ✅ |
| **D2** | updatePlatform 跨租户 | setTenantId + 跨租户检查 | ✅ |
| **D3** | platformHealth 跨租户 | selectPlatformByTenant 检查 | ✅ |
| **D4** | listProducts/orders 数据泄露 | qw.eq(...tenantId) | ✅ |
| **D5** | syncProducts/orders | tenantId 替换 0L + 跨租户检查 | ✅ |
| **D6** | dashboard 全表统计 | selectCount(qw) 带条件 | ✅ |
| **D7** | 历史数据 tenant_id=0 | 不修 → 单独登记 I092 | ✅ 登记 |
| **D8** | 单元测试 | 不加（I090 范围外） | ✅ |

---

## 5. 验证

| 检查项 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `mvn -q compile -DskipTests` | ✅ Exit 0 |
| Staged 文件数 | `git status --short` | 仅 1 个 `M `（EcommerceService.java） |
| EOL 漂移污染 | F079 等 spec + e2e-result.md | ✅ 全部 unstaged，未混入 |
| 业务改动行数 | `git diff --cached` | +73 -13（净 +60） |

---

## 6. 改动文件清单

| # | 文件 | 改动类型 | 行变化 |
|---|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/service/EcommerceService.java` | 9 端点 tenantId 隔离 + selectPlatformByTenant helper + import BusinessException | +73 -13 |
| 2 | `audit/issues/issue-register.md` | I090 P 表 FIXED + 新增 I092 行 | — |
| 3 | `audit/post-audit/phase-2.30-I090-report.md` | 本报告 | 新建 |

---

## 7. I090 整体收尾

| 子项 | 状态 | Commit |
|---|---|---|
| **9 端点租户隔离** | ✅ FIXED（本轮） | `1c2c165` |
| **历史 tenant_id=0 数据迁移** | 🆕 **I092 P1 OPEN** | — |
| **admin dataScope=ALL 跨租户读** | ✅ 已支持（helper 内） | `1c2c165` |

**I090 拆分结论**：
- ✅ **代码层 CLOSED**（9 端点全部 tenantId 隔离）
- ⚠️ **数据层 I092 仍需单独处理**（4 表 `tenant_id=0` 老数据）

---

## 8. 新登记 Issue

### I092 — ecommerce 4 表历史 tenant_id=0 数据迁移

| 字段 | 内容 |
|---|---|
| **Issue ID** | **I092** 🆕 |
| **类型** | 数据迁移 / P1 |
| **描述** | `ecommerce_platform` / `ecommerce_product` / `ecommerce_order` / `ecommerce_sync_log` 4 表 DDL 现有 `tenant_id BIGINT DEFAULT 0`，服务上线前老数据全为 0。修复后 `listPlatforms` 仍可见这些行（qw.eq 条件 `tenant_id=0 != current_tenant_id` → 不可见），但 `selectPlatformByTenant` 命中 `tenant_id=0` 时与 current tenantId 不匹配 → **403 跨租户拒绝**，admin 也无法救回 |
| **影响 Feature** | I090 衍生 |
| **修复方案** | ① `audit/sql/migrate_ecommerce_tenant_id.sql` 一次性 UPDATE（指定 admin_tenant_id）<br>② admin 后台迁移脚本<br>③ 一次性 UPDATE WHERE `tenant_id IS NULL OR tenant_id=0` |
| **Fix Phase** | Post-Audit |

---

## 9. 语义表（I090 修复后）

| 场景 | 结果 |
|---|---|
| 租户 A `POST /ecommerce/platform` 创建 | ✅ `tenant_id=租户A`，不是 0 |
| 租户 A `GET /ecommerce/platform/list` | ✅ 仅返回 tenant_id=租户A 的平台 |
| 租户 A `GET /ecommerce/platform/list`（admin dataScope=ALL） | ✅ 返回所有租户 |
| 租户 A `POST /ecommerce/platform/{租户B的id}` | ❌ `403 TENANT_DENIED` |
| 租户 A `GET /ecommerce/platform/{租户B的id}/health` | ❌ `403 TENANT_DENIED` |
| 租户 A `GET /ecommerce/dashboard` | ✅ 仅统计 tenant_id=租户A 的数字 |
| 租户 A `GET /ecommerce/platform/list` 历史 `tenant_id=0` 行 | ❌ 不可见（除非 admin dataScope=ALL） |
| 租户 A `POST /ecommerce/platform/{历史id}`（tenant_id=0） | ❌ `403 TENANT_DENIED` |

---

## 10. 未涉及范围

| 项目 | 状态 |
|---|---|
| I092 历史数据迁移 | 🆕 OPEN（P1） |
| 其他模块（CRM / GoogleMaps / Stock / Wallet） | 已有 tenantId 隔离，无需改 |
| 前端 | 无 tenantId 概念，不动 |
| entity / DB schema | 已就绪，不动 |
| unit test | D8 不加 |

---

## 11. 决策签字

- **代码作者**：Cursor Assistant
- **Fix Commit**：`1c2c165`
- **Fix 时间**：2026-09-30 23:30 (UTC+8)
- **Backend Compile**：✅ mvn compile exit 0
- **EOL 政策**：✅ 仅精确 add 1 个业务文件
- **I090 状态**：✅ 代码层 FIXED + I092 数据层 OPEN
- **新 Issue**：✅ I092 已登记