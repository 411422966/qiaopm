# I208 — `sys_permission.status=0` 的禁用权限码在鉴权链路中仍然生效

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P2**
> **P2 理由**：属**潜在缺陷**而非现网活跃缺陷 —— 实时库中 `deleted=0 AND status=0` 的权限码行数为 **0**，当前无任何码被禁用，因此尚未产生可观测的越权。但一旦运维用 `POST /sys/permission/{id}` 禁用某个码，该码**不会**从鉴权链路中移除，属于「禁用功能失效」的语义偏差；同时菜单树也会继续显示已禁用的菜单项。
> **发现日期**：2026-11-26（F175 批次 —— I206 §3 修复方向落地后的一致性复核）
> **登记人**：Cursor
> **绑定基线**：HEAD `78c41f8`（`docs(audit): I202 口径 19→20 修正 + 登记 I206/I207 + D-3-34/D-3-35 流程规则`）
> **验证等级**：**静态 VERIFIED**（3 处代码事实带文件路径 + 行号）+ **运行期 VERIFIED**（实时库 `status=0 AND deleted=0` 行数 = 0，故「尚未触发」这一前提经实测确认）
> **关联**：I206（同批 I206 §3 修复落地后的衍生复核）、I190（`GlobalExceptionHandler` 把客户端错误吞成 500 的同族语义失真）、I187（孤儿权限码家族）
> **登记状态**：**已登记于 `audit/issues/issue-register.md`**（2026-11-26，用户批准后写入）

---

## 0. 一句话描述

`sys_permission.status` 是权限码的**启用/禁用**开关（`0` = 禁用），但**鉴权链路三处均不过滤该字段** ——
`SysPermissionMapper.getPermissionsByUserId` 的 SQL 无 `p.status = 1`、`SysMenuPermissionMapper.listVisibleMenuByUserId` 同样无、`PermissionAspect.check` 只做字符串比对不校验 status。
结果：**被禁用的权限码依然通过 `@RequirePermission` 校验，被禁用的菜单依然出现在菜单树中。**

---

## 1. 事实

### 1.1 权限码集合加载：不过滤 `status`

`backend/src/main/java/com/meiyetec/mapper/SysPermissionMapper.java:13-18`

```java
@Select("SELECT p.* FROM sys_permission p " +
        "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
        "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
        "WHERE ur.user_id = #{userId} AND p.deleted = 0 " +
        "GROUP BY p.id")
List<SysPermission> getPermissionsByUserId(@Param("userId") Long userId);
```

→ WHERE 子句只有 `p.deleted = 0`，**缺 `AND p.status = 1`**。

调用方 `backend/src/main/java/com/meiyetec/security/RbacContextLoader.java:71-92`：

```java
List<SysPermission> perms = permissionMapper.getPermissionsByUserId(userId);
Set<String> codes = new HashSet<>();
for (SysPermission p : perms) {
    if (p.getPermissionCode() != null) {
        codes.add(p.getPermissionCode());      // ← 无 status 判断
    }
}
...
ctx.setPermissions(codes);
```

→ **所有** `deleted=0` 的码（含 `status=0` 的禁用码）全部进 `ctx.getPermissions()`。

### 1.2 菜单可见性：不过滤 `status`

`backend/src/main/java/com/meiyetec/mapper/SysMenuPermissionMapper.java:14-24`

```java
@Select("SELECT smp.* FROM sys_menu_permission smp " +
        "INNER JOIN sys_permission p ON smp.permission_id = p.id " +
        "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
        "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
        "WHERE ur.user_id = #{userId} " +
        "  AND p.permission_type = 'menu' " +
        "  AND p.deleted = 0 " +          // ← 有 deleted，无 status
        "  AND (smp.hidden IS NULL OR smp.hidden = 0) " +
        "GROUP BY smp.id " +
        "ORDER BY smp.sort_order ASC")
List<SysMenuPermission> listVisibleMenuByUserId(@Param("userId") Long userId);
```

→ **禁用状态的菜单项仍会出现在菜单树中**（`p.status` 未参与过滤）。

### 1.3 切面：只做字符串比对

`backend/src/main/java/com/meiyetec/security/PermissionAspect.java:44-58`

```java
Set<String> need = new HashSet<>();
for (String s : ann.value()) need.add(s);
boolean ok = ann.mode() == RequirePermission.Mode.ALL
        ? ctx.hasAllPermissions(ann.value())
        : need.stream().anyMatch(ctx::hasPermission);

if (!ok) {
    log.warn("[PermDenied] user={} need={} mode={}", ctx.getUserId(), need, ann.mode());
    throw new BusinessException(BizCode.PERMISSION_DENIED, "需要权限码：" + String.join(", ", ann.value()));
}
```

→ 切面**只比对码字符串**，`ctx.getPermissions()` 中是否含该码即决定放行；**不查询、不感知该码的 `status`**。
即：`status=0` 的码在 `ctx.getPermissions()` 里（见 §1.1）→ 字符串比对命中 → **放行**。

### 1.4 现网触发状态

```sql
SELECT COUNT(*) FROM sys_permission WHERE deleted = 0 AND status = 0;
-- → 0
```

→ **当前无任何被禁用的存活码**，故本条**尚未在现网产生可观测影响**，属**潜在缺陷**。

---

## 2. 影响

一旦运维通过 `POST /sys/permission/{id}`（`SysPermissionController`，支持更新 `status`）禁用某个权限码：

| 预期 | 实际 |
|---|---|
| 该码不再通过 `@RequirePermission` 校验 → 对应接口 403 | **仍然放行**（§1.1 + §1.3） |
| 该码对应的菜单从菜单树消失 | **仍然显示**（§1.2） |

即 **`status` 字段在鉴权与菜单两条链路上都是「装饰性」的** —— 写它不改变任何运行时行为。
对于以 `status=0` 作为「紧急下线某个功能」手段的运维流程，这是一个**假成功**（写成功、行为未变）。

---

## 3. 修复方向（不在本批范围）

- `SysPermissionMapper.getPermissionsByUserId` 的 WHERE 补 `AND p.status = 1`；
- `SysMenuPermissionMapper.listVisibleMenuByUserId` 的 WHERE 补 `AND p.status = 1`；
- `PermissionAutoRegistrar.scanAndRegister` 写入时已固定 `status=1`（`PermissionAutoRegistrar.java:106`），无需改动；
- **需评估兼容性**：现有租户若已在 `sys_permission.status` 上留有历史标记（如临时置 0 排查用），加过滤会**一次性收回**这些码的权限 → 上线前须先跑一遍 `SELECT permission_code, COUNT(*) FROM sys_role_permission ... WHERE p.status=0` 盘点。

---

## 4. 遗留标注

本批**只登记，不修**。

与 **I206** 无冲突：本批新增的 9 个权限码 `status` 均为 **1**（见 `audit/evidence/F175-mh-visual-consent-complication-perm-annotations/evidence.md` §1.3），
故 §1 的缺陷对本次落地的 41 处 `@RequirePermission` **不产生影响**。

---

## 5. 关联与交叉引用

- `backend/src/main/java/com/meiyetec/mapper/SysPermissionMapper.java:13-18`
- `backend/src/main/java/com/meiyetec/mapper/SysMenuPermissionMapper.java:14-24`
- `backend/src/main/java/com/meiyetec/security/RbacContextLoader.java:71-92`
- `backend/src/main/java/com/meiyetec/security/PermissionAspect.java:44-58`
- `backend/src/main/java/com/meiyetec/security/PermissionAutoRegistrar.java:100-116`
- `audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md`
- `audit/issues/I190-global-exception-handler-client-errors-as-system-error.md`（同族语义失真）
- Evidence：`audit/evidence/F175-mh-visual-consent-complication-perm-annotations/evidence.md`
