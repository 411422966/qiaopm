# ACC002 证据 — 运行产物漂移 + 影子路由（I125 / I126）

> 关联问题单：
> - `audit/issues/I125-blockchain-shadow-route.md`（blockchain 菜单影子路由）
> - `audit/issues/I126-runtime-image-drift.md`（运行镜像落后源码）
> 批次：ACC002（2026-10-24）
> 源码 commit：`77767e14c177a7512fc92ba6764e9d5427447969`（2026-10-23 23:38:44）
> DB：`meiyetec@localhost:3307`（`meiyetec-mysql` 容器实时库）
> 环境：见 `audit/baseline/env.md` §9

---

## 1. 本目录证明了什么

| 结论 | 等级 | 证据文件 |
|---|---|---|
| `meiyetec/api:dev` 镜像构建于 **2026-10-20 17:14**，对应源码 `a5df969`，落后 HEAD **57 个提交 / 118 个文件** | **VERIFIED** | `image-drift.json` |
| 容器内 **143 个 Controller 只有 14 个含 `RequirePermission`**，源码为 **108 / 136 个文件** → 差 **94** | **VERIFIED** | `image-drift.json` / `image-version-check.json` |
| 漂移区间内含 I115 补权限注解的全部 **12 个 `feat:` 提交**（~700 个注解） | **VERIFIED** | `image-drift.json` |
| `GET /sys/tenant/list` 非持码用户实测 **200**（应为 403），而同镜像内 `GET /member/list` 正确 **403** → 切面正常，缺的是注解 | **VERIFIED** | `../ACC002-authz-code-split/api-authz.json` |
| DB 菜单已绑定 `/blockchain/record` 与 `/blockchain/chain`，前端无对应路由 → 点进去跳登录 | **VERIFIED** | `browser-deadlinks.json` |
| `blockchain:record` / `blockchain:chain` 在 DB 中**已有** ADMIN + SUPER_ADMIN 绑定 | **VERIFIED** | `../ACC002-authz-code-split/authz-cross.json` |
| 按当前源码重建镜像后 106 个 Controller 全面 403 | **UNVERIFIED** | 静态推演，当前运行镜像无这些注解，无法证伪也无法证实 |

---

## 2. 文件清单

| 文件 | 类型 | 内容 |
|---|---|---|
| `image-drift.js` | 脚本 | 镜像构建时间 → 源码 commit 反查 + 漂移区间文件统计 + 注解覆盖对比（可复跑） |
| `image-drift.json` | 结果 | 镜像时间、对应 commit、57 提交 / 118 文件（controller 101 / 其他后端 5 / 前端 0）、注解覆盖 14·143 vs 108·136、漂移 94 |
| `image-version-check.json` | 结果 | **守门脚本产物**（`audit/scripts/10-check-image-version.ps1`）：DRIFT / UNKNOWN 判定 + DeepCheck 覆盖率对比，`ExitCode=1` |
| `browser-deadlinks.json` | 结果 | 5 角色菜单遍历结果副本；admin 的 2 处 `D2_NO_ROUTE` 即 `/blockchain/record` 与 `/blockchain/chain` |

---

## 3. 守门脚本

`audit/scripts/10-check-image-version.ps1` —— 出具 VERIFIED 前必须跑。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File audit\scripts\10-check-image-version.ps1 `
  -DeepCheck `
  -OutputJson audit\evidence\ACC002-blockchain-shadow-route\image-version-check.json
```

判定逻辑：

| 状态 | 含义 | 是否可出 VERIFIED |
|---|---|---|
| `OK` | 镜像构建时间对应的源码 commit == HEAD | ✅ |
| `DRIFT` | 镜像落后 HEAD | ❌ |
| `UNKNOWN` | 构建时间在 git 可见历史之外（I109 历史损坏） | ❌ |
| `MISMATCH` | 运行容器实际镜像 ≠ 镜像 tag 的 Image ID | ❌ |
| DeepCheck 差异 | 容器内含 `RequirePermission` 的 Controller 数 < 源码数 | ❌ |

退出码：`0` = 通过；`1` = 不一致（禁止 VERIFIED）；`2` = 环境不可用（无法判定）。

**本机当前实测输出（2026-10-24 01:06）**

```
[DRIFT   ] meiyetec/api:dev   SrcCommit=a5df9694…  HEAD=77767e14…  running=aab709859b0a match=True
[UNKNOWN ] meiyetec/web:dev   SrcCommit=<unknown> HEAD=77767e14…  running=7f307b72f025 match=True
DeepCheck: 源码 136 文件 / 108 含注解   容器 143 类 / 14 含注解
FAIL: 3 项不一致 —— 运行态结论禁止标 VERIFIED
```

---

## 4. 复跑方式

```powershell
# 漂移量化
node audit/evidence/ACC002-blockchain-shadow-route/image-drift.js
# 守门校验（含字节码级注解覆盖率比对）
powershell -NoProfile -ExecutionPolicy Bypass -File audit\scripts\10-check-image-version.ps1 -DeepCheck
```

> 字节码验证依赖容器内 `unzip`（`meiyetec-api` 镜像内 `/usr/bin/unzip` 已确认存在；该镜像**无** `jar` 命令）。
> 反查镜像构建时间对应的源码 commit 用 `git rev-list -1 --before="<CreatedAt>" main`，
> 该命令在 2026-10-16 之前的提交因 I109 损坏而返回空 → 判定为 `UNKNOWN`。
