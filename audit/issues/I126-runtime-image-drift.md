# I126 — 运行中的容器镜像落后源码 4 天 / 14 天，运行态验证结论与源码不一致

> 发现时间：2026-10-24
> 发现方式：浏览器实测 → 反查容器字节码 → 与源码逐条比对
> 严重级别：**P1（方法论 BLOCKER）**
> 状态：Open（漂移本身未修复；守门动作 §4 已于 2026-10-24 实施，见 `audit/scripts/10-check-image-version.ps1`）
> 关联：I124（权限码体系分裂）、I125（blockchain 影子路由）
> 基线：commit `77767e14c177a7512fc92ba6764e9d5427447969`（2026-10-23 23:38:44）

---

## 1. 一句话结论

验收环境里**跑着的不是当前代码**：

| 组件 | 运行产物 | 构建时间 | 落后 HEAD |
|---|---|---|---|
| `meiyetec/api:dev` | `/app/app.jar` | **2026-10-20 17:14:10** | **57 个提交 / 118 个文件**（含 101 个后端 Controller） |
| `meiyetec/web:dev` | `nginx/html/assets/` | **2026-10-10 16:16:56** | 前端 `src/` 变更 **0 个文件**（见 §2.3 特例说明） |

关键量化：**容器内 143 个 Controller 类中只有 14 个含 `RequirePermission`，当前源码是 108/136 个文件 —— 差 94 个文件。**
即：I115 那一整轮"补权限注解"的成果（`5be108c` 起共 12 个 `feat:` 提交、~700 个注解）
**在运行实例里完全不存在**。

**影响**：任何"浏览器实测通过 / 后端接口实测通过"的结论，如果没标注产物版本，都可能不成立。
本轮 I124 的后端推演之所以只能标 UNVERIFIED，正是因为这一点。

---

## 2. 硬证据

### 2.1 镜像构建时间 vs 源码提交

```
$ docker images --format "{{.Repository}}:{{.Tag}}|{{.CreatedAt}}"
meiyetec/api:dev|2026-10-20 17:14:10 +0800 CST
meiyetec/web:dev|2026-10-10 16:16:56 +0800 CST

$ git log -1 --format="%H %ad %s" --date=iso
77767e14c177a7512fc92ba6764e9d5427447969 2026-10-23 23:38:44 +0800
```

api 镜像对应的源码提交：`a5df9694f1e02f6e55cfe3ef4e51ee12c476a3e9`（2026-10-20 15:56:05）
→ **镜像构建时点源码为 `a5df969`，HEAD 为 `77767e1`，中间 57 个提交**。

### 2.2 漂移区间的内容

```
提交数: 57
总变更文件: 118
  后端 controller/: 101
  后端非 controller/: 5
  前端 src/: 0
```

57 个提交里包含 I115 补权限注解的**全部 12 个 `feat:` 提交**：

| 提交 | 内容 | 补注解数 |
|---|---|---|
| `5be108c` | I115 第1批，8 个支付类 Controller | 69 |
| `b0e0d13` | I115 第2批，会员/预约/邀请 3 个 Controller | 26 |
| `c85f751` | I115 第3批，CRM/报表 7 个 Controller | 70 |
| `a3a132e` | I115 第4a批，营销 13 个 Controller | 80 |
| `1315844` | I115 第4b批，商品/服务/套餐 8 个 Controller | 57 |
| `fd55f6a` | I115 第4c批，门店/员工/房间/排班 8 个 Controller | 59 |
| `5902e39` | I115 第4d批，库存/耗材 2 个 Controller | 36 |
| `05c4aaf` | I115 第4f批，PluginRuntimeController | 10 |
| `6afd67e` | I115 第4g-1批，系统/日志/配置 5 个 Controller | 37 |
| `f4d1e5e` | I115 第4g-2批，IotDeviceLinkController | 10 |
| `0c9b625` | I115 第4g-3批，7 个杂项 Controller | 32 |
| `9849109` | I115 第4g-3批补漏，DomesticPayController | 5 |
| `bd32aca` | I115 第4g-3b批，关怀检测 10 个 Controller | 67 |
| `6b262e7` | I115 第4g-3c批，CRM会员类 10 个 Controller | 91 |
| `236d7fb` | I115 第4g-3d批，DCEP/IoT/其他 15 个 Controller | 80 |

### 2.3 字节码级证据（最硬的一条）

```
$ docker exec meiyetec-api sh -c "cd /tmp/jj && unzip -q /app/app.jar \
    'BOOT-INF/classes/com/meiyetec/controller/*.class' && \
    grep -rl RequirePermission BOOT-INF/classes/com/meiyetec/controller/ | wc -l"
14
$ ... ls BOOT-INF/classes/com/meiyetec/controller/*.class | wc -l
143
```

单文件验证（`SysTenantController.class`，容器内解包后 `javap -v`）：

- 常量池中**完全没有** `RequirePermission` 引用，也没有 `sys:tenant:list` 字符串
- 源码 `backend/.../SysTenantController.java:44` 明确写着 `@RequirePermission("sys:tenant:list")`

→ **镜像里的字节码确实不含这批注解**，不是 grep 误判。

### 2.4 端点行为差异（运行时可观测）

实测 `GET /sys/tenant/list`（源码带 `@RequirePermission("sys:tenant:list")`）：

| 用户 | 是否持有 `sys:tenant:list` | 实测结果 |
|---|---|---|
| admin | 否（持 `*`） | 200 |
| boss01 | 否 | **200**（应为 403） |
| clerk01 | 否 | **200**（应为 403） |
| manager01 | 否 | **200**（应为 403） |
| operator01 | 否 | **200**（应为 403） |

对照实测 `GET /member/list`（`member:list` 在运行镜像内**已有**注解，且 operator01 确实不持有该码）：

| 用户 | 持有 `member:list` | 实测结果 |
|---|---|---|
| operator01 | 否 | **403**「权限不足: 需要权限码：member:list」✅ |

→ **证明 `PermissionAspect` 切面本身工作正常**（`member:list` 正确拦截），
`sys:tenant:list` 放行纯粹因为**镜像里没有这个注解**。

### 2.5 前端特例：路由权限码无漂移

前端容器虽然更旧（10-10），但**关键文件 `router/index.ts` 未发生实质变化**：

| 比对项 | 源码 | 容器 bundle | 差集 |
|---|---|---|---|
| 唯一 `permission` 码 | 97 | **97** | 双向 **0** |
| `path => permission` 组合 | 147 | 147 | 交集 146（唯一差异为正则跨条目误配，已核实 `setting` 两处一致） |

> 因此 **I124 的前端结论（77 处 D1）对当前源码有效**；
> 而 **I124 的后端推演（重建后 106 个 Controller 全面 403）尚未验证**。

---

## 3. 影响

### 3.1 对验收结论的影响

| 结论类型 | 是否可信 | 理由 |
|---|---|---|
| 前端路由守卫行为（菜单可见性、403 拦截） | ✅ 可信 | bundle 路由码 97/97 与源码一致 |
| 后端 `@RequirePermission` 拦截行为 | ❌ **不可信** | 镜像缺 94 个文件的注解，实测放行 ≠ 源码行为 |
| 后端接口业务逻辑 | ⚠️ 部分可信 | 118 个文件变更中 101 个是 Controller，Service 层仅 5 个文件 |
| 数据库 schema | ✅ 可信 | `meiyetec-mysql` 容器挂载的是实时库 |

### 3.2 对 I124 结论的影响

I124 §2.5 写的"按当前源码重建镜像后，106 个 Controller 将对非超管全面 403"是**静态推演**，
当前运行实例**无法证伪也无法证实**（镜像里这些注解根本不存在）。
在重建镜像之前，该推演必须保持 **UNVERIFIED** 标记。

### 3.3 对上线的影响 ⚠️

**这是本次最需要警惕的一点**：I115 补的 ~700 个权限注解，其对应的 308 个权限码在 DB 中**零角色绑定**（见 I124 §2.5）。
运行实例因为缺注解而"恰好没炸"。一旦按当前源码重建镜像并重启：

```
运行实例（旧镜像，无注解）  →  非超管正常访问接口  →  看似正常
重建镜像（有注解）          →  非超管被 PermissionAspect 全面 403  →  上线即全线不可用
```

→ **重建镜像与补齐权限码绑定，必须同批次完成，不能分两次上线。**

---

## 4. 修复方向（备选，未实施，待用户裁决）

| 方案 | 说明 |
|---|---|
| **A. 先统一权限码体系，再重建镜像** | 严格按此顺序：I124 定方案 → 补 DB 绑定 → 重建镜像 → 实测。**推荐** |
| **B. 重建镜像前先给零绑定码批量授权** | 用 I124 的 308 个码 + 现有角色语义（点号码的绑定）反推冒号码应绑给谁，写入 `sys_role_permission`，再重建 |
| **C. 验收环境加镜像版本标签** | 每次验收前记录 `docker images` 的 CreatedAt + 对应 commit，写进 evidence 目录，避免再次混淆 |
| **D. CI 加"镜像 commit 标签"** | 构建时把 git commit hash 写入镜像（`LABEL org.opencontainers.image.revision`），运行态可查 |

**必须补的守门动作**（无论选哪个方案）：

| # | 守门动作 | 状态 |
|---|---|---|
| 1 | 在 `audit/baseline/env.md` 增加"运行产物版本"字段（镜像 tag + CreatedAt + 对应 commit） | ✅ **已实施**（§9，2026-10-24） |
| 2 | 验收脚本启动前自动断言"镜像 commit == HEAD"，不一致即拒绝出具 VERIFIED | ✅ **已实施**（`audit/scripts/10-check-image-version.ps1`） |
| 3 | 把守门脚本接入 CI / 每次 PR 的必跑清单 | ⬜ 未实施（仓库当前无 `.github/workflows`，需先建 CI 通道） |

**守门脚本已实施说明**（2026-10-24）

- 脚本：`audit/scripts/10-check-image-version.ps1`
- 判定：`OK`（一致）/ `DRIFT`（落后 HEAD）/ `UNKNOWN`（构建时间在 git 可见历史之外）/ `MISMATCH`（运行容器镜像 ≠ tag）
- 退出码：`0` 通过 / `1` 不一致（**禁止出 VERIFIED**）/ `2` 环境不可用
- `-DeepCheck` 开关会解包容器内 `app.jar` 的 Controller 字节码，与源码做注解覆盖率逐项比对
- 本机实跑结果（2026-10-24 01:06）：`DRIFT`（api）+ `UNKNOWN`（web）+ DeepCheck 覆盖差（容器 14/143 vs 源码 108/136）→ **退出码 1**
- 产物：`audit/evidence/ACC002-blockchain-shadow-route/image-version-check.json`

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File audit\scripts\10-check-image-version.ps1 -DeepCheck `
  -OutputJson audit\evidence\ACC002-blockchain-shadow-route\image-version-check.json
```

---

## 5. 证据清单

| 文件 | 内容 |
|---|---|
| `audit/evidence/ACC002-authz-code-split/api-authz.json` | 后端端点实测（`member:list` 403 vs `sys:tenant:list` 放行 200） |
| `audit/evidence/ACC002-authz-code-split/bundle-vs-src.json` | 前端 bundle 路由码 97/97 一致 |
| `audit/evidence/ACC002-authz-code-split/README.md` | ACC002 权限码分裂证据索引 |
| `audit/evidence/ACC002-blockchain-shadow-route/image-drift.json` | 镜像构建时间、57 提交、118 文件、14 vs 108 注解覆盖差 |
| `audit/evidence/ACC002-blockchain-shadow-route/image-drift.js` | 漂移量化脚本（可复跑） |
| `audit/evidence/ACC002-blockchain-shadow-route/image-version-check.json` | **守门脚本产物**（DRIFT / UNKNOWN / DeepCheck，`ExitCode=1`） |
| `audit/evidence/ACC002-blockchain-shadow-route/README.md` | ACC002 运行产物漂移证据索引 |
| `audit/baseline/env.md` §9 | 运行产物版本固化字段 + 强制约束 |
| `audit/scripts/10-check-image-version.ps1` | 运行产物版本守门脚本（可复跑） |

**绑定基线**：commit `77767e14c177a7512fc92ba6764e9d5427447969` /
DB `meiyetec@localhost:3307`（2026-10-24 00:30–00:55）/ env 见 `audit/baseline/env.md` /
运行产物 `meiyetec/api:dev`(2026-10-20 17:14) + `meiyetec/web:dev`(2026-10-10 16:16)

---

## 6. 未确认项

1. **web 镜像为何 2026-10-10 后未重建** —— 未确认是"无需重建"还是"遗漏"。
   `git log --before=2026-10-10 16:16:56` 返回空，说明该日期**在 git 可见历史之外**
   （2026-10-16 前的历史因 NTFS objects/30 损坏丢失，见 I109）→ **无法用 git 判定当时的前端源码状态**。
   本单的前端"无漂移"结论基于 **bundle 产物 vs 当前源码**的直接比对，而非 git 历史。
2. **生产环境用的是哪个产物版本** —— 未确认（本单只覆盖本机验收环境）。
3. **101 个 Controller 在漂移区间的具体行为变更是否引入回归** —— 未逐个核对，
   静态分析只能确认"注解层缺失"，不能排除其他改动的影响。
