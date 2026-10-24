# ACC002 证据 — 权限码体系分裂（I124）

> 关联问题单：`audit/issues/I124-authz-code-system-split.md`
> 批次：ACC002（2026-10-24）
> 源码 commit：`77767e14c177a7512fc92ba6764e9d5427447969`（2026-10-23 23:38:44）
> DB：`meiyetec@localhost:3307`（`meiyetec-mysql` 容器实时库）
> 环境：见 `audit/baseline/env.md` §9（运行产物版本单独记录在 `ACC002-blockchain-shadow-route/`）
> ⚠️ **运行产物漂移**：后端 api 镜像落后 HEAD 57 个提交，**后端推演结论一律标 UNVERIFIED**（见 I126）

---

## 1. 本目录证明了什么

| 结论 | 等级 | 证据文件 |
|---|---|---|
| 前端权限码用**点号**（`sys.tenant`），后端注解用**冒号**（`sys:tenant:list`），两套体系无映射 | **VERIFIED** | `authz-cross.json` |
| DB 中 454 个权限码，**308 个在 `sys_role_permission` 里零角色绑定** | **VERIFIED** | `backend-authz-impact.json` |
| 前端路由 `permission` 码 **97 个**，与容器内 bundle **双向差集为 0** | **VERIFIED** | `bundle-vs-src.json` |
| 5 个角色登录后，菜单项中 **D1（可见但点进去被 403）** 合计 77 处 | **VERIFIED** | `browser-deadlinks.json` |
| `PermissionAspect` 切面本身工作正常（`member:list` 正确 403） | **VERIFIED** | `api-authz.json` |
| 若按当前源码重建镜像，**106 个 Controller 将对非超管全面 403** | **UNVERIFIED** | 静态推演，运行镜像无这些注解，无法证伪也无法证实（见 I126） |

---

## 2. 文件清单

| 文件 | 类型 | 内容 |
|---|---|---|
| `authz-cross.js` | 脚本 | 前后端权限码交叉比对（可复跑） |
| `authz-cross.json` | 结果 | 路由 152 条 / 有效 151 条；唯一 guard 码 96 个；DB 分布 359 存在 · 78 缺失 · 17 重复；角色绑定 454 · 0 · 122；菜单码 110 个，缺失 57 / 存在无绑定 32 / 已绑定 7 |
| `backend-authz-impact.js` | 脚本 | 后端注解 → 权限码 → DB 绑定影响面扫描 |
| `backend-authz-impact.json` | 结果 | 唯一权限码 324 个：DB 缺失 1 / 存在但无角色绑定 308 / 已绑定 15；含 324 条明细 |
| `bundle-vs-src.js` | 脚本 | 前端源码路由码 vs 容器 bundle 路由码比对 |
| `bundle-vs-src.json` | 结果 | 源码 97 / bundle 97，`onlySrc` 与 `onlyBundle` 均为 0 |
| `browser-deadlinks.js` | 脚本 | Playwright 登录 5 个角色 → 遍历菜单 → 记录落地页与 403 提示 |
| `browser-deadlinks.json` | 结果 | 5 个用户各自的 `rows[]`：每项含 `target / title / menuCode / routePermission / component / landed / kind / deniedCode`；`kind ∈ {OK, D1_GUARD_403, D2_NO_ROUTE}` |
| `api-authz.js` | 脚本 | 后端端点权限实测 |
| `api-authz.json` | 结果 | 6 组用例：`GET /member/list` → 403「权限不足: 需要权限码：member:list」；`GET /sys/tenant/list` → 非持码用户 200 |
| `perm-aspect-check.js` | 脚本 | `PermissionAspect` 切面是否生效的对照验证 |

---

## 3. 关键实测数据

### 3.1 权限码分布（DB `sys_permission` / `sys_role_permission`）

| 分类 | 数量 |
|---|---|
| DB 中存在的唯一权限码 | 359 |
| DB 中不存在的权限码（前端/后端引用但库里没有） | 78 |
| DB 中重复的权限码 | 17 |
| `sys_role_permission` 绑定行数 | 454 |
| **存在但零角色绑定的权限码** | **308** |

### 3.2 浏览器实测（5 个角色）

| 用户 | 角色 | 权限码数 | 菜单项 | D1（点进去 403） | D2（无路由） | 正常 |
|---|---|---|---|---|---|---|
| admin | SUPER_ADMIN + ADMIN | 123 | 95 | 0 | 2 | 93 |
| boss01 | BOSS | 49 | 40 | **36** | 0 | 4 |
| clerk01 | CLERK | 19 | 15 | **13** | 0 | 2 |
| manager01 | MANAGER | 37 | 27 | **23** | 0 | 4 |
| operator01 | OPERATOR | 8 | 6 | **5** | 0 | 1 |
| **合计** | — | — | 183 | **77** | 2 | 104 |

> admin 的 2 处 D2 是 `Blockchain Record / Chain` —— 菜单已上线但前端无路由（见 `audit/issues/I125-blockchain-shadow-route.md`）。
> 完整逐条数据见 `browser-deadlinks.json`（5 个用户对象，各含 `rows[]`）。

---

## 4. 复跑方式

```powershell
# 前端权限码交叉比对
node audit/evidence/ACC002-authz-code-split/authz-cross.js
# 后端注解影响面
node audit/evidence/ACC002-authz-code-split/backend-authz-impact.js
# 前端 bundle vs 源码
node audit/evidence/ACC002-authz-code-split/bundle-vs-src.js
# 浏览器实测（需后端 + 前端已启动）
node audit/evidence/ACC002-authz-code-split/browser-deadlinks.js
# 后端端点权限实测
node audit/evidence/ACC002-authz-code-split/api-authz.js
```

> 前端 bundle 产物路径：`meiyetec/web:dev` 容器内 `/usr/share/nginx/html/assets/`。
> 该容器构建于 2026-10-10，早于 git 可见历史边界，**前端"无漂移"结论基于 bundle 与当前源码的直接比对**，非 git 历史比对（见 I126 §2.5）。
