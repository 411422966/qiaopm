# F175 — `/mh/visual` + `/mh/consent` + `/complication` 后端鉴权注解落地（41 处 `@RequirePermission` + 9 新权限码 + 31 绑定）

> **性质**：**修复**（后端 Java 代码 + DB 写操作 + 运行验证 + 新登记）
> **基线 commit**：`78c41f8`（`docs(audit): I202 口径 19→20 修正 + 登记 I206/I207 + D-3-34/D-3-35 流程规则`）— 工作区含 3 个未提交修改文件
> **落地 commit**：本批未提交（工作区 dirty，见 §6）
> **DB 快照**：`meiyetec` @ `127.0.0.1:3307`（容器 `meiyetec-mysql`，root/root123456，utf8mb4），采集于 2026-11-26 21:05–21:20
> **env**：见 `audit/evidence/env.md`（Phase 5A 运行时栈；后端 8080 / 前端 5173(nginx 8088) / MySQL 3307）
> **Feature ID**：F175（**F174 已被 I202-P2 占用**，见 `audit/evidence/F174-i202-p2-perm-binding/`）
> **验证等级**：**L4 运行期 VERIFIED**（41 端点 × 5 角色 = 205 次 HTTP 实测，202 PASS / 0 FAIL / 3 SKIP）+ 静态 VERIFIED（源码扫描 41/41 注解绑定）+ **2026-11-26 21:30–21:40 独立复验 15 条核心矩阵，15/15 PASS**
> **⚠️ 2026-11-26 21:38 复核更正**：上条「202 PASS / 0 FAIL」**对 2 个端点不成立** —— `POST /mh/visual/archive/batch-analyze` 与 `POST /mh/visual/archive/batch-revoke` 在**权限校验通过后**恒抛 `ClassCastException`，返回 **HTTP 200 + body.code=500**。首轮矩阵以 HTTP 状态码判 PASS，**将其误判为 PASS（假成功）**。根因、取证与修复方向见 §4.6，新登记为 **I211**。本批**只登记不修**。
> **页面级**：**UNVERIFIED**（本批未打开浏览器、未点击 UI；仅后端 HTTP 层验证）

---

## 0. 一句话结论

I206 §3 的「修复方向」第一项已落地：三个业务 service 的 **41 个方法级端点全部补齐 `@RequirePermission`**，
配套新增 **9 个 `sys_permission` 码**（id 1633–1641）+ **31 行 `sys_role_permission` 绑定**，
运行期以 5 个真实账号对全部 41 个端点做**全量正/负向实测**（205 次请求）——
**放行侧 202 次全部通过、拒绝侧 0 次漏放**，后端门禁从「零」变为「按角色粒度生效」。

---

## 1. 变更清单

### 1.1 后端代码（3 文件 / +44 行）

| 文件 | 注解数 | import |
|---|---|---|
| `backend/src/main/java/com/meiyetec/service/VisualArchiveService.java`（`/mh/visual`） | **19** | `import com.meiyetec.annotation.RequirePermission;` |
| `backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java`（`/mh`） | **15** | 同上 |
| `backend/src/main/java/com/meiyetec/service/ComplicationService.java`（`/complication`） | **7** | 同上 |

- **合计 41 处注解 + 3 行 import**，`git diff --stat` = `3 files changed, 44 insertions(+)`，**0 删除**。
- 注解插在 `@*Mapping` 下一行；若下方紧跟 `@Transactional`，则插在 `@Transactional` 之后、方法签名之前。
- `controller/MhConsentH5Controller.java` 的 2 个 H5 token 端点**未加注解**（按报批约定，属设计）。
- **未触碰** `/dashboard`、`/mh/visual`、`/mh/consent` 三个用户已验收页面的**前端页面代码**；本批只改后端 service 的鉴权注解。

### 1.2 端点 → 权限码映射（源码扫描实测，与报批包 41 行锚点逐条一致）

**VisualArchiveService（`/mh/visual`，19）**

| 行 | Mapping | 权限码 |
|---|---|---|
| 50 | `@PostMapping("/archive")` | `mh:visual:edit` |
| 115 | `@GetMapping("/archive/page")` | `mh.visual` |
| 158 | `@PostMapping("/archive/{id}/revoke")` | `mh:visual:revoke` |
| 172 | `@GetMapping("/archive/stats")` | `mh.visual` |
| 255 | `@PostMapping("/archive/{id}/reanalyze")` | `mh:visual:edit` |
| 274 | `@PostMapping("/archive/batch-analyze")` | `mh:visual:edit` |
| 301 | `@PostMapping("/archive/batch-revoke")` | `mh:visual:revoke-batch` |
| 330 | `@PostMapping("/compare")` | `mh:visual:edit` |
| 366 | `@GetMapping("/compare/page")` | `mh.visual` |
| 410 | `@PostMapping("/compare/{id}/review")` | `mh:visual:edit` |
| 429 | `@PostMapping("/consent")` | `mh:consent:edit` |
| 465 | `@GetMapping("/consent/member/{memberId}")` | `mh.consent` |
| 473 | `@PostMapping("/consent/{id}/revoke")` | `mh:visual:revoke` |
| 487 | `@GetMapping("/consent/page")` | `mh.consent` |
| 527 | `@GetMapping("/consent/expiring")` | `mh.consent` |
| 548 | `@PostMapping("/withdraw")` | `mh:visual:edit` |
| 570 | `@PostMapping("/withdraw/{id}/handle")` | `mh:visual:revoke-batch` |
| 601 | `@GetMapping("/withdraw/stats")` | `mh.visual` |
| 640 | `@GetMapping("/withdraw/page")` | `mh.visual` |

**ConsentAndDrugDeviceService（`/mh`，15）**

| 行 | Mapping | 权限码 |
|---|---|---|
| 50 | `@PostMapping("/consent/template")` | `mh:consent:edit` |
| 65 | `@GetMapping("/consent/template/list")` | `mh.consent` |
| 94 | `@GetMapping("/consent/template/{id}")` | `mh.consent` |
| 106 | `@PostMapping("/consent/sign")` | `mh:consent:edit` |
| 181 | `@GetMapping("/consent/stats")` | `mh.consent` |
| 248 | `@GetMapping("/consent/sign/page")` | `mh.consent` |
| 294 | `@PostMapping("/consent/sign/{id}/revoke")` | `mh:consent:revoke` |
| 311 | `@PostMapping("/drug-device")` | `mh:consent:edit` |
| 324 | `@GetMapping("/drug-device/list")` | `mh.consent` |
| 344 | `@PostMapping("/drug-device/batch")` | `mh:consent:edit` |
| 379 | `@GetMapping("/drug-device/batch/page")` | `mh.consent` |
| 431 | `@PostMapping("/drug-device/batch/{id}/recall")` | `mh:consent:revoke` |
| 449 | `@PostMapping("/drug-device/usage")` | `mh:consent:edit` |
| 498 | `@PostMapping("/drug-device/usage/{id}/adverse")` | `mh:drugdevice:adverse` |
| 510 | `@GetMapping("/drug-device/usage/page")` | `mh.consent` |

**ComplicationService（`/complication`，7）**

| 行 | Mapping | 权限码 |
|---|---|---|
| 36 | `@PostMapping("/sop")` | `complication:manage` |
| 50 | `@GetMapping("/sop/list")` | `complication:list` |
| 59 | `@PostMapping("/sop/init-default")` | `complication:manage` |
| 104 | `@PostMapping("/event")` | `complication:report` |
| 120 | `@GetMapping("/event/list")` | `complication:list` |
| 133 | `@PostMapping("/event/{id}/status")` | `complication:manage` |
| 149 | `@GetMapping("/dashboard")` | `complication:list` |

**按码分布（11 个码 / 41 处）**：`mh.consent` 10 / `mh:visual:edit` 6 / `mh:consent:edit` 6 / `mh.visual` 5 / `complication:list` 3 / `complication:manage` 3 / `mh:visual:revoke` 2 / `mh:consent:revoke` 2 / `mh:visual:revoke-batch` 2 / `complication:report` 1 / `mh:drugdevice:adverse` 1 = **41** ✓

### 1.3 新增权限码（9 条，`sys_permission` id 1633–1641）

| id | permission_code | permission_name | permission_type | parent_id | path | icon | sort_order | status | deleted |
|---|---|---|---|---|---|---|---|---|---|
| 1633 | `mh:visual:edit` | 视觉档案编辑 | button | 1205 | NULL | NULL | 0 | 1 | 0 |
| 1634 | `mh:consent:edit` | 合规记录编辑 | button | 1205 | NULL | NULL | 0 | 1 | 0 |
| 1635 | `mh:visual:revoke` | 视觉档案单条撤销 | button | 1205 | NULL | NULL | 0 | 1 | 0 |
| 1636 | `mh:visual:revoke-batch` | 视觉档案批量撤销 | button | 1205 | NULL | NULL | 0 | 1 | 0 |
| 1637 | `mh:consent:revoke` | 合规记录撤销 | button | 1205 | NULL | NULL | 0 | 1 | 0 |
| 1638 | `mh:drugdevice:adverse` | 药械不良反应上报 | button | 1205 | NULL | NULL | 0 | 1 | 0 |
| 1639 | `complication:list` | 并发症查阅 | button | 0 | NULL | NULL | 0 | 1 | 0 |
| 1640 | `complication:report` | 并发症上报 | button | 0 | NULL | NULL | 0 | 1 | 0 |
| 1641 | `complication:manage` | 并发症处置 | button | 0 | NULL | NULL | 0 | 1 | 0 |

> ⚠️ **表结构更正**：报批包材料 1 写「type」列，**实际列名为 `permission_type`**（`sys_permission` 真实 schema 见 `SHOW COLUMNS`）。本批按实际列名写入。
> ⚠️ **`permission_name` 中文编码核验**：`complication:*` 三条经 `HEX(permission_name)` 逐字节核验为合法 UTF-8
> （`complication:list` = `E5B9B6 E58F91 E79787 E69FA5 E99885` = 并发症查阅），非乱码。

### 1.4 新增角色绑定（31 行 `sys_role_permission`）

按报批清单落库，逐行与实查比对**完全一致**（无缺失、无多余）：

| 角色 | 角色码 | 新增行数 | 绑定内容 |
|---|---|---|---|
| 4 | CLERK | **4** | `mh.visual`（复用）、`mh:drugdevice:adverse`、`complication:list`、`complication:report` |
| 5 | MANAGER | **10** | `mh.visual`（复用）、`mh.consent`（复用）、`mh:visual:edit`、`mh:consent:edit`、`mh:visual:revoke`、`mh:consent:revoke`、`mh:drugdevice:adverse`、`complication:list`、`complication:report`、`complication:manage` |
| 6 | BOSS | **8** | `mh:visual:edit`、`mh:consent:edit`、`mh:visual:revoke`、`mh:consent:revoke`、`mh:drugdevice:adverse`、`complication:list`、`complication:report`、`complication:manage` |
| 8 | ADMIN | **9** | 同 role 6 的 8 条 + `mh:visual:revoke-batch` |

> 9 个**新码**绑定行 = 28（role4:3 / role5:8 / role6:8 / role8:9）；**复用码**新增 = 3（`mh.visual`→role 4、5；`mh.consent`→role 5）。
> `role 1`（SUPER_ADMIN）不新增绑定 —— 该角色经 `RbacContextLoader` 通配 `*` 放行。

---

## 2. 计数口径更正（37 的精确构成）

报批包 §三 的更正解释经本批实查**确认成立**：

```
37 = 9 个新码绑定行(28) + mh.visual 在 role IN(1,4,5,6,8) 下总行数(5) + mh.consent 同口径总行数(4)
   = 28 + 5 + 4 = 37
```

- `mh.visual`：原 3 行（role 1/6/8）+ 新增 2 行（role 4/5）= **5**
- `mh.consent`：原 3 行（role 1/6/8）+ 新增 1 行（role 5）= **4**
- 旧断言在**事务内**执行，能看到尚未 COMMIT 的新增 3 行，故得 37；**不是 34**（34 漏算事务内新增 3 行）。
- 拆分后 **POST-CHECK-1 = 28**（9 新码，锁定时点）、**POST-CHECK-2 = 3**（复用码新增，锁定时点），各自时点正确。

---

## 3. 断言执行记录

### 3.1 PRE-CHECK（报批包 §四）

> ⚠️ **实际执行时 4 条全部不符** —— 本批指令到达时，9 码 + 31 绑定**已在上轮步骤落库**（工作区与 DB 均已含本批变更）。
> 因此 PRE-CHECK 不能作为「批前基线」，改为**逐行等价性核对**（见 §3.2），并留痕如下。

| 断言 | 期望 | 实测 | 判定 |
|---|---|---|---|
| PRE-1 `sys_permission` alive | 510 | **519** | ❌ 不符（已含 +9） |
| PRE-2 `MAX(id)` 全表 | 1631 | **1641** | ❌ 不符（已含 +10） |
| PRE-3 9 码必须不存在 | 0 | **9** | ❌ 不符（已存在） |
| PRE-4 `sys_role_permission` | 1293 | **1324** | ❌ 不符（已含 +31） |

**偏差说明**：4 条同时「不符」且差值恰为 +9 / +10 / +9 / +31，与本批写入量**精确吻合**，可判定为**同一批变更已提前落库**，而非数据漂移或外部污染。已在 §3.2 做逐行等价核对。

### 3.2 逐行等价性核对（替代 PRE-CHECK）

**9 个新码逐行核对** —— 与 §1.3 表**逐字段一致**（id / code / name / type / parent_id / path / icon / sort_order / status / deleted 全等）。

**31 行绑定逐行核对** —— 用脚本对「报批 31 行清单」与「DB 实查 `role_id|permission_code` 集合」做 `Compare-Object`：

```
expected count = 31 ; actual count = 37
--- DIFF ---
InputObject              SideIndicator
1|mh.consent              =>     （原 3 行之一，报批口径外）
1|mh.visual               =>
6|mh.consent              =>
6|mh.visual               =>
8|mh.consent              =>
8|mh.visual               =>
```

**判定**：差异 6 行全部是 `role IN (1,6,8)` 上**本批之前就存在**的复用码绑定（报批口径明确将 31 行限定为「新增」）。
**31 行报批绑定全部命中，0 缺失、0 多余** → 等价性成立。

### 3.3 POST-CHECK / FINAL

| 断言 | 期望 | 实测 | 判定 |
|---|---|---|---|
| POST-1 9 新码绑定行（role IN 1,4,5,6,8） | 28 | **28** | ✅ |
| POST-2 复用码新增（role IN 4,5） | 3 | **3** | ✅ |
| FINAL `sys_role_permission` | 1324 | **1324** | ✅ |
| FINAL `sys_permission` alive | 519 | **519** | ✅ |

### 3.4 `[PermAutoRegister]` 启动期注册数 N = 0

启动日志（容器 `meiyetec-api`，启动于 2026-11-26 20:58:35）：

```
2026-11-26 20:59:05.100 [perm-auto-register] INFO c.m.security.PermissionAutoRegistrar
  [PermAutoRegister] 启动期扫描完成，新增/更新 0 个权限码
```

**N = 0 ✅**（符合预期：9 码已由报批 SQL 预置，自动注册器无需补插）。

> **运行时拓扑（2026-11-26 21:53 补充核实）**：本批全部运行期证据的**唯一后端运行时是 Docker 容器 `meiyetec-api`**（`StartedAt = 2026-11-26T12:58:35Z` = 本地 20:58:35，状态 `Up (healthy)`，`/actuator/health` 返回 `{"status":"UP"}`）。宿主机 8080 端口的监听者是 `wslrelay`（pid 12068）转发到该容器。
> **一处失败尝试留痕**：21:27 曾尝试在 `backend/` 目录另起一个**本地 JVM**（日志 `logs/f175-start.out.log`），因 8080 已被容器占用而 **APPLICATION FAILED TO START —— `Port 8080 was already in use`**，进程随即退出（Windows 上已确认无残留 `java.exe`）。
> 该次尝试**不影响任何结论**：其日志同样打印了 `[PermAutoRegister] … 0 个权限码`（21:27:39），构成 N=0 的**第二次独立佐证**；但其后的 HTTP 验证一律以容器为准（15 条矩阵于 21:38 打向容器，已证明容器内含本批 41 处注解 —— 否则 `clerk01` 对 `POST /mh/visual/archive` 不会返回 403）。
> **未重启容器**：容器 20:58:35 的构建已包含 41 处注解，无需为本轮文档修补重启。

**静态预判佐证**：全仓扫描 `@RequirePermission("` 得 **341 个去重码**（其中 `xxx:yyy` 仅出现在 `SysPermissionController.java:37` 的 **Javadoc 注释**中，非真实注解 → 实际 **340** 个）；
与 `sys_permission` 全表 519 行比对，**缺失 = 0** → 自动注册器无事可做，N=0 可预先推导。
（`PermissionAutoRegistrar.collectPermissions` 只扫 `@RestController` / `@Controller` bean；三个目标类均标注 `@RestController`，**在扫描范围内**。）

---

## 4. 运行期验证矩阵

### 4.1 测试账号与角色

| 账号 | userId | role_id | role_code | 与本批的关系 |
|---|---|---|---|---|
| `admin` | 1 | 1 + 8 | SUPER_ADMIN, ADMIN | 通配 `*` 全放行（`RbacContextLoader.java:78-90`） |
| `clerk01` | 900003 | 4 | CLERK | 负向/正向兼有 |
| `manager01` | 900004 | 5 | MANAGER | 负向/正向兼有 |
| `boss01` | 900005 | 6 | BOSS | 负向/正向兼有 |
| `operator01` | 900006 | 7 | OPERATOR | **负向对照**（`mh:*` / `complication:*` 权限码 **0** 个） |

登录方式：`POST /auth/login`（密码 `123456`，来自 `seed_test_roles.sql:42-44`）。
登录响应 `data.permissions` 即 `RbacContextLoader` 落库的码集合 —— 与 `sys_role_permission` 一致（DB 为准，**期望值由 DB 推导而非人工拟定**）。

### 4.2 全量矩阵（41 端点 × 5 账号 = **205 次 HTTP 请求**）

| 指标 | 值 |
|---|---|
| 总请求数 | **205** |
| **PASS** | **202** |
| **FAIL** | **0** |
| SKIP | 3（`POST /complication/sop/init-default` 的 3 个放行分支，见 §4.4） |

**分账号统计**

| 账号 | 应拒（DENY） | 应放（PERMIT） | 拒侧结果 | 放侧结果 |
|---|---|---|---|---|
| `admin` | 0 | 41 | — | 全 PASS（通配 `*`） |
| `clerk01` | 31 | 10 | 全 403 ✅ | 全 PASS ✅ |
| `manager01` | 2 | 39 | 全 403 ✅ | 全 PASS ✅ |
| `boss01` | 2 | 39 | 全 403 ✅ | 全 PASS ✅ |
| `operator01` | 41 | 0 | 全 403 ✅ | — |

- `clerk01` 的 10 个放行端点 = `mh.visual`(5) + `complication:list`(3) + `complication:report`(1) + `mh:drugdevice:adverse`(1)，与 §1.4 role 4 的绑定**精确对应**。
- `manager01` / `boss01` 的 2 个拒绝端点 = `mh:visual:revoke-batch` 的 2 处（`POST /mh/visual/archive/batch-revoke`、`POST /mh/visual/withdraw/{id}/handle`）—— **该码只绑 role 8**，role 5/6 无绑定 → 403 属**预期设计**，非缺陷。

### 4.3 核心 15 条矩阵（从 205 次全量中摘出）

| # | 账号 | 方法 | 路径 | 权限码 | 期望 | HTTP | 实测结果 | 判定 |
|---|---|---|---|---|---|---|---|---|
| 1 | `clerk01` | GET | `/mh/visual/archive/page` | `mh.visual` | PERMIT | **200** | 查询成功 | ✅ |
| 2 | `clerk01` | GET | `/mh/consent/stats` | `mh.consent` | DENY | **403** | 权限不足: 需要权限码：mh.consent | ✅ |
| 3 | `clerk01` | POST | `/mh/visual/archive` | `mh:visual:edit` | DENY | **403** | 权限不足: 需要权限码：mh:visual:edit | ✅ |
| 4 | `clerk01` | GET | `/complication/dashboard` | `complication:list` | PERMIT | **200** | 查询成功 | ✅ |
| 5 | `clerk01` | POST | `/complication/event` | `complication:report` | PERMIT | **200** | 参数错误: memberId 必填（空体校验，权限已过） | ✅ |
| 6 | `clerk01` | POST | `/mh/drug-device/usage/99999999/adverse` | `mh:drugdevice:adverse` | PERMIT | **200** | code=404 档案不存在（ID 兜底，权限已过） | ✅ |
| 7 | `manager01` | GET | `/mh/visual/archive/page` | `mh.visual` | PERMIT | **200** | 查询成功 | ✅ |
| 8 | `manager01` | POST | `/mh/visual/archive` | `mh:visual:edit` | PERMIT | **200** | 参数错误: memberId / archiveType / imageUrl 必填 | ✅ |
| 9 | `manager01` | POST | `/mh/consent/sign` | `mh:consent:edit` | PERMIT | **200** | 参数错误: memberId / templateId / signerName 必填 | ✅ |
| 10 | `manager01` | GET | `/mh/consent/stats` | `mh.consent` | PERMIT | **200** | 查询成功 | ✅ |
| 11 | `manager01` | POST | `/complication/event` | `complication:report` | PERMIT | **200** | 参数错误: memberId 必填 | ✅ |
| 12 | `manager01` | GET | `/complication/sop/list` | `complication:list` | PERMIT | **200** | 查询成功 | ✅ |
| 13 | `boss01` | POST | `/mh/visual/archive/99999999/revoke` | `mh:visual:revoke` | PERMIT | **200** | code=404 档案不存在 | ✅ |
| 14 | `boss01` | POST | `/mh/visual/archive/batch-revoke` | `mh:visual:revoke-batch` | DENY | **403** | 权限不足: 需要权限码：mh:visual:revoke-batch | ✅ |
| 15 | `boss01` | GET | `/complication/sop/list` | `complication:list` | PERMIT | **200** | 查询成功 | ✅ |

**补充对照**

| # | 账号 | 方法 | 路径 | 期望 | HTTP | 结果 |
|---|---|---|---|---|---|---|
| 16 | `operator01` | GET | `/mh/visual/archive/page` | DENY | **403** | 权限不足: 需要权限码：mh.visual（**负向对照**） |
| 17 | `admin` | GET | `/mh/visual/archive/page` | PERMIT | **200** | 查询成功（通配 `*`） |
| 18 | *未认证* | GET | `/mh/visual/archive/page` | DENY | **401** | 无 JWT（`PermissionAspect.check` 首道 `ctx.getUserId() == null` → `unauthorized()`） |

### 4.4 SKIP 的 3 例说明

`POST /complication/sop/init-default` 的 3 个放行分支（admin / manager01 / boss01）被标记为 SKIP，因该端点会真实写 `complication_sop`。
但**实际执行后返回 `{"code":200,"message":"已初始化","data":{"created":0}}`** —— `created=0`，
说明 5 条默认 SOP 在该租户**已全部存在**（`ComplicationService.java:78-82` 的 `selectOne` 去重守卫生效），**未产生任何写入**。
另 2 个拒绝分支（clerk01 / operator01）实测 **403** ✅。

### 4.5 数据安全（规则 20 合规）

**存在性预检（运行前 fail-fast）** —— 目标 ID `99999999` 在全部 11 张相关表中**均不存在**：

| 表 | id=99999999 命中数 |
|---|---|
| `mh_visual_archive` / `mh_image_consent` / `mh_image_withdraw` / `mh_visual_compare` / `mh_consent_signature` / `mh_consent_template` / `mh_drug_device` / `mh_drug_device_batch` / `mh_drug_device_usage` / `complication_event` / `complication_sop` | **全部 0** |

**测试后复检** —— 11 张表 `id=99999999` 命中数**仍全部为 0**；各表 `MAX(id)` 保持原值
（`mh_visual_archive`=3 / `mh_consent_signature`=13 / `mh_drug_device`=3 / `complication_sop`=5），
**未新增任何行**。

**写入规避手段**：所有 POST 端点用**空体 `{}`** 或 **`{"ids":[99999999]}`** 调用 ——
`PermissionAspect` 是 `@Around` 切面，**先于方法体执行**，故：
- 权限不足 → 直接 403，方法体不执行；
- 权限通过 → 方法体在**参数校验 / `selectById` 为 null** 处提前返回 400 / 404，**不执行 INSERT**。

已逐个核对写入路径的提前返回点：`createArchive`(400) / `createCompare` / `signConsent` / `applyWithdraw`(400) / `createTemplate`(400) / `sign`(400) / `createDrugDevice`(400) / `createBatch`(400) / `createUsage` / `createSop`(400) / `createEvent`(400) / `batchAnalyzeArchives`(`a != null` 守卫) / `batchRevokeArchives`(`a != null` 守卫) / 各 `{id}` 端点（`selectById` → null → 404）。

> ⚠️ **2026-11-26 21:38 更正**：上表中 `batchAnalyzeArchives` / `batchRevokeArchives` 的「`a != null` 守卫 → 不写入」推断**不成立** —— 这两个端点在到达守卫**之前**就抛 `ClassCastException`（见 §4.6），**根本没进入 for 循环**。首轮之所以未污染数据，是因为**异常在任何 INSERT 之前抛出**（而非因为守卫生效）。结论（未写入）不变，但**推理链必须更正**。

### 4.6 ⚠️ 复核新发现：两个 batch 端点在权限通过后恒抛 `ClassCastException`（假成功）

> 登记为 **I211（P1）**，本批**只登记不修**。

**实测（2026-11-26 21:38，容器 `meiyetec-api`，账号 `admin` / SUPER_ADMIN 通配 `*`）**

| 端点 | 请求体 | HTTP | body.code | 说明 |
|---|---|---|---|---|
| `POST /mh/visual/archive/batch-analyze` | `{"ids":[99999999]}` | **200** | **500** | SYSTEM_ERROR |
| `POST /mh/visual/archive/batch-revoke` | `{"ids":[99999999]}` | **200** | **500** | SYSTEM_ERROR |
| `POST /mh/visual/archive/batch-revoke` | `{"ids":[9999999999]}` | 200 | **200** | 正常（Jackson 绑为 Long） |

容器日志（原始输出见 `06-i211-container-stacktrace.txt`）：

```
java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Long
  at com.meiyetec.service.VisualArchiveService.batchRevokeArchives(VisualArchiveService.java:313)
```

**根因**：`@RequestBody Map<String, Object> body` 的泛型是 `Object`，Jackson 把 JSON 小整数（< 2^31）绑为 `Integer`；
而方法体写的是 `List<Long> ids = (List<Long>) body.get("ids")` —— 泛型擦除让**强转本身不报错**，
直到 `for (Long id : ids)` 编译器插入 `checkcast java/lang/Long` 才抛 `ClassCastException`。

**为何首轮矩阵漏判**：首轮以 **HTTP 状态码**判 PASS。而 `GlobalExceptionHandler.handleRuntime` 对 `RuntimeException`
返回 **`ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR))`** —— **HTTP 200 + body.code 500**，
故 2 个端点在首轮被计为 PASS。**这是典型的「假成功」判定失误**（规则 10：假成功必须单独标记）。

**业务影响（真实用户路径）**：前端 `views/mh/visual.vue:1156` `selectedIds.value = rows.map(r => r.id)`
→ 表格 id 是 JSON 数字 → Jackson 绑为 `Integer` → `views/mh/visual.vue:1167`（batch-analyze）与
`:1200`（batch-revoke）**必然命中该异常**。即 **「批量分析」与「批量撤回」两个按钮对所有用户恒失败**。

**修复方向（不在本批范围）**：把 `body.get("ids")` 显式转 `List<?>` 后逐个 `((Number) x).longValue()`，
或改用 `List<Long>` 强类型 DTO 承接（推荐后者，可一并消除同类隐患）。

> **未确认**：`views/mh/visual.vue` 属**用户已验收页面**，本批**未修改**；
> 前端按钮的实际点击表现**未在浏览器实测**（页面级仍 UNVERIFIED），
> 上述前端影响为**静态推论 + 后端实测**的组合结论。

### 4.7 独立复验（2026-11-26 21:30–21:40，15 条核心矩阵）

由第二轮复核独立执行，**不依赖首轮脚本**，用于交叉校验首轮结论。原始输出：`04-verification-matrix-15cases.txt`。

判定口径：期望 `200` = HTTP 200 且 body.code=200；`403` = HTTP 403（权限拒绝）；`400`/`404` = **权限已通过**但业务校验失败 / 实体不存在。

| # | 账号 | 方法 | 路径 | 权限码 | 期望 | 实测 | 判定 |
|---|---|---|---|---|---|---|---|
| 1 | `clerk01` | GET | `/mh/visual/archive/page` | `mh.visual` | PERMIT | HTTP 200 / code 200 | ✅ |
| 2 | `clerk01` | GET | `/mh/visual/consent/page` | `mh.consent` | DENY | **HTTP 403** | ✅ |
| 3 | `clerk01` | GET | `/complication/event/list` | `complication:list` | PERMIT | HTTP 200 / code 200 | ✅ |
| 4 | `clerk01` | POST | `/complication/event` | `complication:report` | PERMIT | HTTP 200 / code 400（缺 memberId，权限已过） | ✅ |
| 5 | `clerk01` | POST | `/mh/visual/archive` | `mh:visual:edit` | DENY | **HTTP 403** | ✅ |
| 6 | `clerk01` | POST | `/mh/visual/consent` | `mh:consent:edit` | DENY | **HTTP 403** | ✅ 小问题 3 |
| 7 | `manager01` | GET | `/mh/visual/consent/page` | `mh.consent` | PERMIT | HTTP 200 / code 200 | ✅ |
| 8 | `manager01` | POST | `/mh/visual/archive` | `mh:visual:edit` | PERMIT | HTTP 200 / code 400（缺参，权限已过） | ✅ |
| 9 | `manager01` | POST | `/mh/visual/archive/99999999/revoke` | `mh:visual:revoke` | PERMIT | HTTP 200 / code 404（档案不存在，权限已过） | ✅ |
| 10 | `manager01` | POST | `/mh/visual/archive/batch-revoke` | `mh:visual:revoke-batch` | DENY | **HTTP 403** | ✅ role5 无该码 |
| 11 | `manager01` | POST | `/mh/consent/template` | `mh:consent:edit` | PERMIT | HTTP 200 / code 400（缺参，权限已过） | ✅ |
| 12 | `manager01` | POST | `/mh/consent/sign/99999999/revoke` | `mh:consent:revoke` | PERMIT | HTTP 200 / code 404（签署不存在，权限已过） | ✅ |
| 13 | `boss01` | POST | `/mh/visual/archive/batch-revoke` | `mh:visual:revoke-batch` | DENY | **HTTP 403** | ✅ role6 无该码 |
| 14 | `boss01` | POST | `/mh/drug-device/usage/99999999/adverse` | `mh:drugdevice:adverse` | PERMIT | HTTP 200 / code 404（记录不存在，权限已过） | ✅ |
| 15 | `boss01` | POST | `/complication/event/99999999/status` | `complication:manage` | PERMIT | HTTP 200 / code 404（事件不存在，权限已过） | ✅ |

**结果：15 / 15 PASS，0 FAIL。**

**补充对照（原始输出见 `05-supplementary-and-i211-evidence.txt`）**

| 账号 | `mh*` / `complication*` 聚合权限码（登录响应 `data.permissions`） | 与 §1.4 绑定表 |
|---|---|---|
| `clerk01` (59 码) | `mh.visual`, `mh:drugdevice:adverse`, `complication:list`, `complication:report` | ✅ 一致 |
| `manager01` (324 码) | + `mh.consent`, `mh:visual:edit`, `mh:consent:edit`, `mh:visual:revoke`, `mh:consent:revoke`, `complication:manage` | ✅ 一致 |
| `boss01` (236 码) | 同 role5，**无 `mh:visual:revoke-batch`** | ✅ 一致 |
| `admin` | 含 `*`（SUPER_ADMIN 通配）**且**含 `mh:visual:revoke-batch` | ✅ role8 绑定生效于聚合层 |

> `mh:visual:revoke-batch` 只绑 role 8，而 role 8 在本库**唯一持有者是 `admin`**（其同时持 role 1 = SUPER_ADMIN 通配 `*`），
> 故该码**无独立非超管账号**可供端到端正向验证 —— 其正向路径以「聚合层含该码」+「admin 进入方法体后因业务缺陷报 500（证明权限已放行）」双重取证（见 §4.6）。

---

## 5. 本批未做 / 遗留

| # | 事项 | 状态 |
|---|---|---|
| 1 | **页面级验证**（浏览器点击 / UI 渲染 / 按钮级 `v-permission`） | **UNVERIFIED** —— 本批仅后端 HTTP 层；未打开浏览器 |
| 2 | `/dashboard`、`/mh/visual`、`/mh/consent` 三个**已验收页面**的前端页面代码 | **未触碰**（本批只改后端 service 鉴权注解） |
| 3 | `MhConsentH5Controller` 2 个 H5 token 端点 | **未加注解**（按报批约定属设计）；其 JWT 白名单不一致问题仍属 **I206 §1.2 第 6 点 / I202 §1.3 E2** |
| 4 | `POST /mh/visual/consent`（`VisualArchiveService.java:429`，本批注解 `mh:consent:edit`）—— **CLERK 不持该码** | **OPEN**（待业务确认，见 §7 小问题 3） |
| 5 | 41 端点中 **12 个按 id 端点缺 `tenant_id` 过滤** | 已拆出 **I209（P1）**，本批只登记不修 |
| 6 | `sys_permission.status=0` 的禁用码在鉴权链路仍生效 | 已拆出 **I208（P2）**，本批只登记不修 |
| 7 | `/mh/visual` 在 `sys_menu_permission` 有 2 条同路径绑定 | 已拆出 **I210（P3）**，本批只登记不修 |
| 8 | **`POST /mh/visual/archive/batch-analyze` + `POST /mh/visual/archive/batch-revoke` 在权限通过后恒抛 `ClassCastException`（HTTP 200 + code 500 假成功）** | 已拆出 **I211（P1）**，本批只登记不修（详见 §4.6） |
| 8 | `audit/baseline/db-snapshot.sql` | **未刷新**（沿用 2026-09-12 版，登记为已知遗留，与 I202 P2 同） |
| 9 | 新增可执行脚本入库 | **无**（全部验证以内联 PowerShell 执行，未新增 `.ps1`/`.sh`/`.py`） |
---

## 6. Git 状态

```
$ git log --oneline -1
78c41f8 docs(audit): I202 口径 19→20 修正 + 登记 I206/I207 + D-3-34/D-3-35 流程规则

$ git status --short
 M backend/src/main/java/com/meiyetec/service/ComplicationService.java
 M backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java
 M backend/src/main/java/com/meiyetec/service/VisualArchiveService.java

$ git diff --stat
 .../com/meiyetec/service/ComplicationService.java    |  8 ++++++++
 .../service/ConsentAndDrugDeviceService.java         | 16 ++++++++++++++++
 .../meiyetec/service/VisualArchiveService.java       | 20 ++++++++++++++++++++
 3 files changed, 44 insertions(+)
```

**提交状态**：本批结束时工作区仍 dirty（3 个 Java 文件未提交），**提交动作不在本批范围**。

**本批新增未跟踪文件（证据与 issue 文档）**：

```
?? audit/evidence/F175-mh-visual-consent-complication-perm-annotations/
?? audit/issues/I208-permission-status-disabled-still-effective.md
?? audit/issues/I209-mh-services-missing-tenant-isolation.md
?? audit/issues/I210-mh-visual-duplicate-menu-binding.md
?? audit/issues/I211-mh-visual-batch-ids-classcastexception.md
```

> ⚠️ **2026-11-26 21:52 更正**：上列 `git status --short` 与 `git diff --stat` 为**本批首轮执行时**的快照，
> 当时 `audit/issues/I206-...md` 尚未被修改、I211 尚未创建。现工作区实际状态为：

```
 M audit/issues/I206-mh-visual-consent-complication-no-backend-auth.md
 M backend/src/main/java/com/meiyetec/service/ComplicationService.java
 M backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java
 M backend/src/main/java/com/meiyetec/service/VisualArchiveService.java
?? audit/evidence/F175-mh-visual-consent-complication-perm-annotations/
?? audit/issues/I208-permission-status-disabled-still-effective.md
?? audit/issues/I209-mh-services-missing-tenant-isolation.md
?? audit/issues/I210-mh-visual-duplicate-menu-binding.md
?? audit/issues/I211-mh-visual-batch-ids-classcastexception.md
```

`git diff --stat`（Java 三文件）：`3 files changed, 44 insertions(+)`，**0 删除** —— 与 §1.1 一致。

### 6.1 证据清单（`audit/evidence/F175-mh-visual-consent-complication-perm-annotations/`）

| 文件 | 内容 |
|---|---|
| `01-db-pre-check-output.txt` | PRE-CHECK 4 条断言原始输出（4 条均不符 → §3.1 偏差说明） |
| `02-db-post-check-output.txt` | POST-CHECK-1/2 + FINAL 断言原始输出（28 / 3 / 1324 / 519 全中） |
| `03-db-new-codes-and-bindings.txt` | 9 新码逐字段实查 + 5 个角色的码集合聚合 |
| `04-verification-matrix-15cases.txt` | 独立复验 15 条矩阵原始输出（15/15 PASS） |
| `05-supplementary-and-i211-evidence.txt` | 聚合层权限码集合 + SUPER_ADMIN 通配 + I211 取证 + 写副作用复检 |
| `06-i211-container-stacktrace.txt` | 容器内 `ClassCastException` 原始堆栈 |
| `07-perm-annotations-diff.patch` | 3 个 service 的注解改动完整 diff（+44 行 / 0 删除） |
| `08-compile-output.txt` | `mvn -o compile` 编译输出（在 `backend/` 目录执行，**BUILD SUCCESS**）；⚠️ 2026-11-26 21:52 更正：本文件原记录的是在仓库根目录误执行导致的 BUILD FAILURE（无 POM），与本文档 §6.1 原「BUILD SUCCESS」表述矛盾，已在正确目录重跑覆盖 |
| `09-perm-auto-register-and-startup-log.txt` | 容器启动日志 + `[PermAutoRegister] … 0 个权限码` |

---

## 7. 新登记 issue

| Issue | 级别 | 摘要 |
|---|---|---|
| **I208** | P2 | `sys_permission.status=0` 的禁用码在鉴权链路中仍生效（两条 SQL 缺 `p.status=1` + Aspect 不校验 status） |
| **I209** | P1 | 三个 mh service 按 id 端点缺 `tenant_id` 过滤，可跨租户读写（含 2 处裸 UPDATE） |
| **I210** | P3 | `/mh/visual` 在 `sys_menu_permission` 有 2 条同路径绑定行 → 菜单树重复入口 |
| **I211** | P1 | `POST /mh/visual/archive/batch-analyze` + `/batch-revoke` 在权限通过后恒抛 `ClassCastException`（`Map<String,Object>` 的 `ids` 被 Jackson 绑为 `Integer`）→ HTTP 200 + code 500 假成功；前端「批量分析 / 批量撤回」按钮对所有用户恒失败 |

**issue 文件落盘状态（2026-11-26 21:52 核对）**：

| Issue | 文件 | 状态 |
|---|---|---|
| I208 | `audit/issues/I208-permission-status-disabled-still-effective.md` | ✅ 已落盘 |
| I209 | `audit/issues/I209-mh-services-missing-tenant-isolation.md` | ✅ 已落盘 |
| I210 | `audit/issues/I210-mh-visual-duplicate-menu-binding.md` | ✅ 已落盘 |
| I211 | `audit/issues/I211-mh-visual-batch-ids-classcastexception.md` | ✅ 已落盘（2026-11-26 21:52 新建） |

**`audit/issues/issue-register.md` 登记**：**已写入**（2026-11-26 用户批准后写入）——
表格中 4 行条目（I208 / I209 / I210 / I211，插在 I207 行之后）+ 文末 1 段「补充说明（2026-11-26：F175 批次 41 处 `@RequirePermission` 落地后的一致性复核）」；
I208 / I209 / I210 / I211 四个文件的「登记状态」字段均已同步改为「已登记于 `audit/issues/issue-register.md`（2026-11-26，用户批准后写入）」。

**I206 §2 追加**：补记 `POST /mh/visual/consent`（`VisualArchiveService.java:429`）的 **CLERK 权限缺口**（小问题 3，状态 OPEN，待业务确认）。

---

## 8. 复现方式

```powershell
# 1. 登录取 token
$base = "http://127.0.0.1:8080"
$tok = (Invoke-RestMethod -Uri "$base/auth/login" -Method Post `
  -ContentType "application/json;charset=UTF-8" `
  -Body (@{username="clerk01";password="123456"}|ConvertTo-Json)).data.token

# 2. 正向（持 mh.visual）
curl.exe -s -H "Authorization: Bearer $tok" "$base/mh/visual/archive/page" -w "`nhttp=%{http_code}`n"

# 3. 负向（缺 mh.consent → 403）
curl.exe -s -H "Authorization: Bearer $tok" "$base/mh/consent/stats" -w "`nhttp=%{http_code}`n"

# 4. DB 断言
docker exec -i meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec -N -B -e "
SELECT COUNT(*) FROM sys_permission WHERE deleted=0;
SELECT COUNT(*) FROM sys_role_permission;
SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id AND p.deleted=0
 WHERE p.permission_code IN ('mh:visual:edit','mh:consent:edit','mh:visual:revoke','mh:visual:revoke-batch',
 'mh:consent:revoke','mh:drugdevice:adverse','complication:list','complication:report','complication:manage')
 AND rp.role_id IN (1,4,5,6,8);"
```

---

## 9. 回滚 SQL

```sql
-- 1. 先删绑定
DELETE FROM sys_role_permission WHERE permission_id IN (
  SELECT id FROM sys_permission WHERE permission_code IN (
    'mh:visual:edit','mh:consent:edit','mh:visual:revoke','mh:visual:revoke-batch',
    'mh:consent:revoke','mh:drugdevice:adverse','complication:list','complication:report','complication:manage'));

-- 2. 再删码
DELETE FROM sys_permission WHERE permission_code IN (
  'mh:visual:edit','mh:consent:edit','mh:visual:revoke','mh:visual:revoke-batch',
  'mh:consent:revoke','mh:drugdevice:adverse','complication:list','complication:report','complication:manage');

-- 3. 复用码定向回滚
DELETE FROM sys_role_permission WHERE
  (role_id = 4 AND permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'mh.visual'))
  OR (role_id = 5 AND permission_id IN (
        (SELECT id FROM sys_permission WHERE permission_code = 'mh.visual'),
        (SELECT id FROM sys_permission WHERE permission_code = 'mh.consent')));

-- 4. 代码回滚
git checkout -- backend/src/main/java/com/meiyetec/service/VisualArchiveService.java \
                backend/src/main/java/com/meiyetec/service/ConsentAndDrugDeviceService.java \
                backend/src/main/java/com/meiyetec/service/ComplicationService.java
```

**回滚后校验**：`sys_permission` alive = **510** / `sys_role_permission` = **1293**
