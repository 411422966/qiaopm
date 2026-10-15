# CURRENT_TENANT_SOURCE

> **Step 0 — Evidence 阶段：仅记录事实，不做修改**

---

## 1. 项目现有 Tenant Resolution 机制

### 1.1 完整链路

```
HTTP Request
   ↓
Authorization: Bearer <token>
   ↓
JwtInterceptor.preHandle
   ├─ jwtUtil.isTokenExpired(token)
   ├─ jwtUtil.extractUserId(token) → Long userId
   └─ rbacContextLoader.load(userId)
        ├─ userMapper.selectById(userId)   → SysUser (含 tenantId)
        ├─ tenantService.checkAvailable(user.getTenantId())
        ├─ permissionMapper.getPermissionsByUserId(userId) → 权限码
        ├─ dataScopeMapper.listByUserId(userId) → 数据范围
        └─ TenantContext.setTenantId(user.getTenantId())
   ↓
TenantContextHolder.set(ctx)        ← ThreadLocal 写入
   ↓
业务代码调用 TenantContextHolder.get().getTenantId()
   ↓
请求结束 → TenantContextHolder.clear() + MDC remove
```

### 1.2 关键证据

| 组件 | 文件 | 行 | 行为 |
|------|------|----|------|
| JWT 解析入口 | `util/JwtUtil.java` | 29-44 | `generateToken(userId, username)` 把 userId 写入 claims |
| JWT claims 提取 | `util/JwtUtil.java` | 58-60 | `extractUserId(token)` 取出 userId |
| 拦截器入口 | `interceptor/JwtInterceptor.java` | 38-72 | 解析 → RbacContextLoader.load → set |
| 上下文加载 | `security/RbacContextLoader.java` | 45-58 | `ctx.setTenantId(user.getTenantId())` — **从 user.tenantId 取** |
| ThreadLocal 持有 | `security/TenantContextHolder.java` | 22, 24-26 | static ThreadLocal<TenantContext> CTX |
| 上下文清理 | `interceptor/JwtInterceptor.java` | 75-78 | afterCompletion: clear() |

### 1.3 结论

**TenantId 不来自 JWT claims**（JwtUtil.generateToken 只塞了 userId+username）。

**TenantId 来自数据库 user.tenantId**，在 JwtInterceptor 阶段通过 RbacContextLoader 实时加载到 TenantContext，再通过 TenantContextHolder 提供 ThreadLocal 访问。

### 1.4 项目内使用模式

| Pattern | 出现次数 | 示例 |
|---------|----------|------|
| `TenantContextHolder.get().getTenantId()` | 60+ 处 | `service/CustomerCareService.java:49,105,146,209,324,517,570,649,709,731` 等 |
| 同上但在 controller | 6 处 | `controller/WalletController.java:86,97,108`, `controller/BookingController.java:249` 等 |
| 硬编码 `1L` | **2 处** ⚠️ | `controller/PosController.java:300, 395` |
| 通过 request body | 0 处 | ❌ 禁止模式 |

---

## 2. 安全保证（Trust Boundary）

| 保证 | 机制 | 证据 |
|------|------|------|
| 未认证请求 → 直接 401 | `JwtInterceptor:49-52` | `writeUnauthorized` + return false |
| Token 过期 → 直接 401 | `JwtInterceptor:56-58` | `jwtUtil.isTokenExpired` |
| userId 缺失 → 401 | `JwtInterceptor:61-64` | `extractUserId == null` |
| 跨租户访问 → 403 | `TenantContextHolder.requireTenant()` | `TenantContextHolder.java:55-64`，仅 ALL 放行 |
| 租户被冻结 → 拒绝 | `RbacContextLoader:53` | `tenantService.checkAvailable()` |

### 2.1 跨租户防护

```java
// TenantContextHolder.java:55-64
public static TenantContext requireTenant(Long tenantId) {
    TenantContext ctx = require();
    if (tenantId != null && ctx.getTenantId() != null 
        && !ctx.getTenantId().equals(tenantId)) {
        if (!"ALL".equals(ctx.getDataScope())) {
            throw BusinessException.forbidden("跨租户访问被拒绝");
        }
    }
    return ctx;
}
```

**注意**：当前 PosController 写入路径**没有调用 requireTenant**，但因为这是写而非读，且 tenant_id 取自 ThreadLocal 而非前端输入，**Tx 写入不存在直接跨租户漏洞**，但破坏了多租户隔离的纪律——所有其他租户的数据都被错标记为 tenant_id=1L。

---

## 3. 推荐 Tenant Resolution

| Source | File | Method | Evidence | Recommended |
|--------|------|--------|----------|-------------|
| JWT 直接 claims | `util/JwtUtil.java` | `extractUserId` only | claims 仅含 userId+username | ❌ 不推荐（JWT 无 tenantId） |
| SecurityContext | — | — | 项目不用 spring security principal | ❌ 不推荐 |
| TenantContextHolder | `security/TenantContextHolder.java` | `get().getTenantId()` | ThreadLocal，由 JwtInterceptor 注入 | ✅ **唯一推荐** |
| User → Tenant | `security/RbacContextLoader.java` | `user.getTenantId()` | 加载源头 | 与 TenantContextHolder 一致 |
| Store → Tenant | — | — | 项目用 store_id 不直接映射 tenant | ❌ N+1 取，效率低 |
| 前端传入 | — | — | 无项目授权模式 | ❌ 禁止（可被伪造） |

### 3.1 唯一推荐来源

```java
Long tenantId = TenantContextHolder.get().getTenantId();
```

### 3.2 推荐理由

1. **项目已有统一机制**（60+ 处使用，6 个 controller，12+ service）
2. **JwtInterceptor 已拦截所有未认证请求**（401）
3. **RbacContextLoader 已校验租户可用性**（冻结租户被拒）
4. **与 JWT 解耦**（重启服务 / 租户禁用可以即时生效）
5. **没有第二套机制**（避免引入新的不一致）

---

## 4. PosController 当前态

```java
// PosController.java:297-301（POS 订单钱包消费路径）
jdbc.update(
    "INSERT INTO crm_wallet_transaction(tenant_id, member_id, type, amount, balance_before, balance_after, pay_method, order_id, operator_id, remark, created_at) " +
    "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
    1L, m.getId(), 2, amount, before, after, "wallet", order.getId(), order.getCashierId(),
    "订单消费:" + order.getOrderNo(), LocalDateTime.now());

// PosController.java:392-396（POS 退款钱包回流路径）
jdbc.update(
    "INSERT INTO crm_wallet_transaction(tenant_id, member_id, type, amount, balance_before, balance_after, pay_method, order_id, operator_id, remark, created_at) " +
    "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
    1L, m.getId(), 3, returnAmt, before, m.getBalance(), "wallet_refund", order.getId(), 1L,
    "订单退款:" + order.getOrderNo(), LocalDateTime.now());
```

两处硬编码 `1L` 是 tenant_id 字段位置：
- 第一处：参数序号 1（对应 SQL 第一列 `tenant_id`）
- 第二处：同上
- 第 11 处 `1L` 是 `operator_id`（不是 tenant_id）⚠️ 注意区分

---

## 5. 初步结论

✅ **可信 Tenant Resolution 来源**：`TenantContextHolder.get().getTenantId()`

✅ **修复策略**：把两处 `1L` 替换为 `TenantContextHolder.get().getTenantId()`，与项目 60+ 处现有用法保持一致

⚠️ **第二处的 `1L` 是 operator_id**（不是 tenant_id）—— 不在本次修复范围内（本任务只处理 tenant_id 硬编码）

---

**Evidence 保存时间**: 2026-08-13 23:35
**仅记录事实**
