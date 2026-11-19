# I183 — 考勤模块 9 个权限码 100% 无角色绑定，考勤功能对全部业务角色不可用

> **状态**：**Open**（本批**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P0**（上线阻断级 —— 在绑定关系修复前，考勤模块 22 个 endpoint 对除持 `*` 的超管外的**所有角色**返回 403，含打卡、请假提交、审批、撤回、配置增改删等全部写接口）
> **发现日期**：2026-11-16（batch2-C 权限对齐只读调查）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2` — `docs(audit): I180 绑定基线同步 cfa7909 + I182 InAppSender 脏数据登记`
> **验证等级**：**VERIFIED（运行期 L5a + 静态）** —— 运行期以 `admin` / `manager01` / `boss01` / `clerk01` 四个账号实测 22 个 endpoint，并读取 `sys_permission` / `sys_role_permission` 实际绑定
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`（2026-11-16 02:00）
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/01-static-endpoint-matrix.md` §1、`raw/04b-db-permission-binding-verification.txt` V1、`raw/05-runtime-authz-matrix.txt`、`raw/09-frontend-route-and-permission-codes.txt` H 段）
> **复核快照（实测时间 2026-11-18 16:44:52 SQL / 16:40:06–16:40:14 HTTP，临时授权撤销后）**：`attendance:*` 9 码在 `sys_role_permission` 中绑定数**仍为 0**（`sys_role_permission` 总行数 1164、role 5 = 278、夹具残留 0）；manager01 对 `/attendance/records`、`/attendance/statistics`、`/attendance/leave/list`、`/attendance/config/list`、`/attendance/config/active` 实测**全部 403** → **I183 结论不受 11-16 23:34:53 临时授权、也不受 11-17 02:00 污染备份影响**（`raw/05` 证据采集于 11-16 00:57，早于授权窗口）。**实测命令**：`docker exec meiyetec-mysql mysql -uroot -proot123456 --default-character-set=utf8mb4 meiyetec -e "SELECT ... FROM sys_permission p LEFT JOIN sys_role_permission rp ..."`（绑定计数）+ `Invoke-WebRequest` 登录 `POST /auth/login`（manager01）取 token 后逐端点 `GET`，异常分支取 `$_.Exception.Response.StatusCode.value__`。详见 `audit/evidence/F173-i186-batch1-runtime-verification/raw/04-post-selfcheck-perm-recheck-20261118.txt` §1/§2/§3、`raw/13-perm-binding-timeline-20261117.txt` §8 与 `audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md`
> **关联**：I184（提成模块同类死码）、I185（前后端码双轨）、I117（`DataScopeInterceptor` 覆盖路径不全）、I115（权限注解覆盖率）

---

## 0. 一句话描述

考勤模块 9 个权限码（`attendance:check` / `attendance:list` / `attendance:stat` / `attendance:edit` / `attendance:config:list` / `attendance:config:edit` / `attendance:leave:submit` / `attendance:leave:approve` / `attendance:leave:list`）**在运行期 `sys_permission` 中全部存在，但 `sys_role_permission` 中绑定数一律为 0** →
这 9 个码是**死权限码**，除持有通配 `*` 的 `admin` 外，**任何角色调用考勤的 22 个 endpoint 一律 403**，其中 5 个写接口（打卡 / 打卡下班、请假提交 / 审批 / 撤回、考勤配置增改删）**完全不可用**。

---

## 1. 事实核实

### 1.1 运行期绑定实测（`raw/04b` V1）

| 权限码 | `permission_type` | `create_time` | 角色绑定数 | 绑定角色 |
|---|---|---|---|---|
| `attendance:check` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:config:edit` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:config:list` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:edit` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:leave:approve` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:leave:list` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:leave:submit` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:list` | button | 2026-10-21 22:33:46 | **0** | — |
| `attendance:stat` | button | 2026-10-21 22:33:46 | **0** | — |

> ⚠️ 9 个码的 `create_time` **完全一致**（`2026-10-21 22:33:46`）→ 是 `PermissionAutoRegistrar` 启动期批量自动注册的产物，**不是**任何种子 SQL 的产物（`init_attendance.sql:112-119` 插入的是 `employee:attendance*` 系，见 I185）。

### 1.2 鉴权机制（静态）

| 机制 | 事实 | 位置 |
|---|---|---|
| 切面判定 | 精确码匹配；集合含 `*` 直接放行；否则抛 `BizCode.PERMISSION_DENIED` | `backend/src/main/java/com/meiyetec/security/PermissionAspect.java:29-66`（放行判定在 `:47`） |
| 自动注册 | 启动期扫描 `@RequirePermission` → 缺失码 INSERT `sys_permission`（`permission_type='API'`），**不绑定任何角色** | `backend/src/main/java/com/meiyetec/security/PermissionAutoRegistrar.java:73-110`（`p.setPermissionType("API")` 在 `:105`，`permissionMapper.insert(p)` 在 `:109`） |
| 权限来源 | `sys_permission ⋈ sys_role_permission ⋈ sys_user_role`，**不按 `permission_type` 过滤** | `backend/src/main/java/com/meiyetec/mapper/SysPermissionMapper.java:13-18` |

### 1.3 运行期实测（`raw/05`、`raw/05b`）

| 账号 | 角色 | `dataScope` | `GET /attendance/today` | `GET /attendance/records` | `GET /attendance/statistics` | `GET /attendance/leave/list` |
|---|---|---|---|---|---|---|
| `admin` | SUPER_ADMIN + ADMIN | ALL | **200** | **200** | **200** | **200** |
| `manager01` | MANAGER | STORE | **403** | **403** | **403** | **403** |
| `boss01` | BOSS | STORE | **403** | **403** | **403** | **403** |
| `clerk01` | CLERK | STORE | **403** | **403** | **403** | **403** |

403 响应体原文（`raw/05b`）：
`{"code":403,"message":"权限不足: 需要权限码：attendance:list"}`

### 1.4 前端下发载荷（`raw/09` H 段）

`GET /auth/me/permissions` 实测：

| 账号 | `attendance*` 码 | `permCount` |
|---|---|---|
| `admin` | `[]`（但含 `*` → 全部放行） | 384 |
| `manager01` | `[]` | 278 |
| `boss01` | `[]` | 191 |
| `clerk01` | `[]` | 50 |

> 即：**前端没有任何角色持有任何 `attendance*` 码**（含前端自己用的 `employee:attendance*` 系，见 I185）。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | 考勤模块**整体不可用** | 任意非超管账号访问考勤 | 打卡 / 请假 / 考勤统计 / 规则配置在业务侧**完全不可达**；若超管账号未分配给门店人员，业务等于没有这个模块 |
| R2 | 门店员工无法自助打卡 | 员工想打上下班卡 | `attendance:check` 无绑定 → 403；**打卡功能等于不存在**（写操作，不可用） |
| R3 | 请假流程断链 | 员工提交请假 / 撤回申请 | `attendance:leave:submit` / `attendance:leave:approve` 均无绑定 → 403 |
| R4 | 考勤规则无法维护 | 新增 / 修改门店打卡规则 | `attendance:config:edit` 无绑定 → 403 |
| R5 | **静默空转** | 管理员在后台看角色权限配置 | 码在 `sys_permission` 里**看得见**（9 行 button），但**没有任何角色勾选** → 配置界面看不出「这批码谁都没给」的异常，容易误判为「已配置」 |
| R6 | 修复滞后被误判为「功能有 bug」 | 上线后业务反馈打卡失败 | 真实根因是**授权数据缺失**，不是代码缺陷 → 若按代码缺陷排查会走弯路 |

---

## 3. 建议方案（**仅建议，本批不实施**）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **补 `sys_role_permission` 绑定** | 为 MANAGER / BOSS / CLERK 按业务职责分配 9 个码：CLERK 需 `attendance:check` + `attendance:leave:submit` + `attendance:leave:list` + `attendance:list`；MANAGER/BOSS 追加 `attendance:stat` + `attendance:leave:approve` + `attendance:config:list`；`attendance:edit` / `attendance:config:edit` 建议只给 ADMIN | 需业务确认**各角色到底该不该打卡**（有些门店员工用小程序而非后台打卡） |
| 2 | **绑定走脚本，不走手工** | 新增一份 `migrate_*.sql`（`INSERT IGNORE INTO sys_role_permission ...`），与既有 `migrate_r3_0_c_menu_role.sql:133-137` 同款模式 | 本批**未入库**任何脚本（规则 19） |
| 3 | **确认种子脚本是否在部署流程内** | `init_attendance.sql` 只插 `employee:attendance*` 菜单码，**从未插 `attendance:*`**；`attendance:*` 完全靠自动注册 → 部署时若跳过自动注册环节，码会缺失（此时接口行为是「权限不足」而非 404） | 需与运维确认部署链路 |
| 4 | **补可观测性** | 在权限后台加「该权限码未被任何角色引用」的**醒目提示**（类似孤儿检测），避免 R5 | — |
| 5 | **回归验证口径** | 见 §4 | 修复前必须先跑，否则无法区分「权限修好」与「数据本来就没有」 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `SELECT permission_code, COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON ... WHERE p.permission_code LIKE 'attendance:%' GROUP BY permission_code` | 9 个码**每个** ≥ 1 个绑定 |
| 2 | `clerk01` `POST /attendance/check-in` | **200**（不再 403） |
| 3 | `manager01` `GET /attendance/statistics` | **200** |
| 4 | `manager01` `GET /attendance/records?storeId=5` | **403**「无权访问该门店数据」（数据范围不能因补绑定而被绕过 —— 见 I186） |
| 5 | `clerk01` `GET /attendance/leave/pending-count?storeId=5` | **403**（同上） |
| 6 | `admin` 全部 22 个 endpoint | 保持 **200** |
| 7 | `GET /auth/me/permissions`（`manager01`） | `attendance*` 码**非空** |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析** + **本机开发库只读查询**（`SELECT`/`SHOW`）+ **运行期只读 GET 探测**。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- 运行期探测仅使用 `GET`；写接口（打卡 / 请假 / 配置增改删）**一律未调用**。
- 未核实：生产环境 `sys_role_permission` 是否与本机开发库同构；生产库 `attendance_record`（本机 **0 行**）/ `attendance_leave_request`（**0 行**）是否有真实数据。
- 未核实：业务侧真实意图（哪些角色应当具备考勤能力）—— §3 步骤 1 的角色分配表是**建议**，需业务确认。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：与 I184（提成模块同类死码）合并为一个「权限码绑定」批次实施；修复前考勤模块应视为**未交付**。
