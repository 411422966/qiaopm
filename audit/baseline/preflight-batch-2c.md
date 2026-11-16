# Preflight — batch2-C「考勤 + 员工提成 权限对齐」专项

> **性质**：**只读调查**批次前置检查（preflight）
> **批次标识**：batch2-C-权限对齐（考勤 attendance + 员工提成 commission）
> **基线 commit**：HEAD `56d59a2` — `docs(audit): I180 绑定基线同步 cfa7909 + I182 InAppSender 脏数据登记`
> **前置文件**：本批次属既有审计项目的续作批次，**不新建项目**；`audit/baseline/preflight.md` 为历史批次总纲，本文件为其 batch2-C 分册
> **执行者**：Cursor
> **落盘日期**：2026-11-16

---

## 1. 基线与代码状态

| 项 | 值 | 校验方式 |
|---|---|---|
| Git 仓库 | ✅ 是（`D:\QiaoPM`） | `git log --oneline -3` |
| 当前 HEAD | `56d59a2` | `git log --oneline -1` |
| 前两个 commit | `cfa7909` / `b09200f`（均为 audit 文档提交） | `git log --oneline -3` |
| 业务代码是否被本批改动 | ❌ 否 | 本批全程只读；`git status --short` 在调查前后一致 |
| 工作树 | clean（`git status --short` 无业务代码改动） | `git status --short` |

> ⚠️ 本批**不修改业务代码**，因此不触发「重构类变更需先跑 Phase 3.5 Diff Audit」。

---

## 2. 环境

| 维度 | 值 | 来源 |
|---|---|---|
| OS | Windows 10.0.26200 | User Info |
| Shell | PowerShell | User Info |
| Workspace | `D:\QiaoPM` | User Info |
| 后端容器 | `meiyetec-api`（image `meiyetec/api:dev`，healthy，up 4h） | `docker ps` |
| 部署产物 | `/app/app.jar`，140 MB，**构建于 2026-11-15 23:02**（容器启动 23:03） | `docker exec meiyetec-api ls -la /app` |
| jar 与源码一致性 | 已抽查：`AttendanceController` / `CommissionController` / `CommissionTierController` / `CommissionConditionController` / `CommissionSimulationController` **5 个 class 均在 jar 内**，时间戳 11-15 23:01 | `docker exec meiyetec-api unzip -l /app/app.jar` |
| 前端容器 | `meiyetec-web`（`meiyetec/web:dev`，healthy） | `docker ps` |
| 数据库容器 | `meiyetec-mysql`（MySQL 8.0，healthy） | `docker ps` |
| Redis | `meiyetec-redis`（redis:7-alpine，healthy） | `docker ps` |
| 后端端口 | `http://127.0.0.1:8080` | 运行期探测 |
| 环境档案 | `audit/evidence/env.md` | — |

> ✅ **部署产物与源码一致**：本批所有「运行期 404」结论均已排除「jar 陈旧」这一干扰项（与 I135 的历史情形不同）。

---

## 3. 数据库连通性与快照

| 项 | 值 | 校验方式 |
|---|---|---|
| 连通性 | ✅ `docker exec -i meiyetec-mysql mysql -uroot -p*** meiyetec` 正常 | 本批 7 组只读查询全部返回 |
| 库 | `meiyetec`（MySQL 8.0） | — |
| 只读纪律 | ✅ 全程仅 `SELECT` / `SHOW`；**无** `INSERT/UPDATE/DELETE/DDL` | `raw/00-readonly-sql-batch.sql` 全文可核 |
| 账号 | 开发容器 root（**仅本机开发库**，非生产） | — |
| 最新 DB 快照 | `backup/meiyetec_20261116_020004.sql`（2026-11-16 02:00，24.3 MB） | `Get-ChildItem backup` |
| 快照新鲜度 | 调查时点 2026-11-16 03:00 左右，快照约 1 小时前 → **新鲜** | — |
| 本批是否产生新快照 | ❌ 否（只读调查，无需快照） | — |

---

## 4. 账号与探测面

| 账号 | 角色 | 权限码数 | `dataScope` | `storeIds` | 用途 |
|---|---|---|---|---|---|
| `admin` | `SUPER_ADMIN` + `ADMIN` | 384（含 `*`） | ALL | `-1`（全门店） | 超管基线 |
| `manager01` | `MANAGER` | 278 | STORE | `{1}` | 店长（业务主力角色） |
| `boss01` | `BOSS` | 191 | STORE | `{1}` | 老板 |
| `clerk01` | `CLERK` | 50 | STORE | `{1}` | 店员（最小权限） |

> 账号口令沿用既有调查惯例（`123456`），均为**开发环境预置账号**；本批未创建、修改、禁用任何账号。

---

## 5. 已知约束（不阻塞本批，但影响结论口径）

| # | 约束 | 对本批结论的影响 |
|---|---|---|
| C1 | **`attendance_record` / `attendance_leave_request` / `commission_salary` / `commission_settlement` 均为 0 行**；`commission_record` 10 行**全部集中在 `store_id=1`** | 「跨门店/跨员工泄露实数据」**无运行样本** → 相关结论停在**授权层结构性确认**，实数据后果标 **UNVERIFIED** |
| C2 | `pos_store` 有 6 家门店（id 1/5/9/13/17/21），`pos_employee` 150 人 | 跨门店场景在**结构上**存在，但无对应提成/考勤数据可观测 |
| C3 | `feature.legacy-missing-ddl.enabled = false`（`application.yml:151-152`） | 12 个门控 Controller（含本批 3 个提成控制器）**运行期不注册** → 其 endpoint 一律 404；「写接口裸奔」当前**不可复现**，改判为**潜伏风险** |
| C4 | I135 已明确警告「不要把 `feature.legacy-missing-ddl.enabled` 整体改成 `true`」 | 本批发现的「门控控制器无鉴权」必须**先补鉴权**再谈解封 |
| C5 | `SysPermissionMapper.getPermissionsByUserId` **不按 `permission_type` 过滤** | menu/button/API 三类码都会下发给前端 → 前端 `meta.permission` 用 `button` 型码在技术上可行，但与 `sys_menu_permission` 的菜单树是两套机制 |
| C6 | `PermissionAutoRegistrar` 自动注册的码 `permission_type='API'`、**不绑定角色** | 「库里存在」≠「可用」；本次全部差异分析均以「**绑定关系**」为口径，而非「码是否存在」 |
| C7 | 超管通配 `*` 的触发条件为**复合条件**（见 F14） | 新建 `SUPER_ADMIN` 角色若无 `booking`/`member`/`setting` 且用户名非 `admin` → 拿不到 `*` |
| C8 | `DataScopeInterceptor.WHITELIST` 含 `/commission/salary/export`、`/commission/settlement/export` | 导出接口**完全跳过**数据范围校验（当前因权限码死，仅超管可达） |
| C9 | 本批为**只读调查**，不实施修复 | 所有 Issue 只给「修复方向」，**不动业务代码** |
| C10 | `v-permission` 指令已定义但**全仓 0 处使用** | 前端按钮级权限**完全缺失** → 前端只有路由级门禁，无细粒度按钮门禁 |

---

## 6. 本批方法与产出

| 步骤 | 手段 | 产出 |
|---|---|---|
| 1 | 静态梳理 7 个控制器 + 4 个服务实现 + 3 个安全组件 | `raw/01-static-endpoint-matrix.md`（59 个 endpoint） |
| 2 | 静态梳理前端 `router/index.ts` / `views/**` / `api/**` | `raw/09` A–G 段 |
| 3 | 本机开发库只读查询（7 组 SELECT / SHOW） | `raw/02`–`raw/04c` |
| 4 | 运行期只读探测（GET + 纯读 POST，全部 4 个账号） | `raw/05`–`raw/07` |
| 5 | 运行期 RBAC 载荷采集（`GET /auth/me/permissions`） | `raw/09` H 段 |
| 6 | 差异清单 + 新 Issue 编号 I183–I189 | `audit/issues/I183-*.md` … `I189-*.md` + `issue-register.md` |

> ✅ **写操作**：**无**（写接口一律未调用；按 ID 探测统一用 `99999999`，运行前已确认不存在）。
> ✅ **入库脚本**：**无**（`raw/00-readonly-sql-batch.sql` 仅为只读查询合集，文件头已标注「非正式工具」，不进入 `audit/scripts/` 正式守门序列，不接入 CI）。

---

## 7. 本批交付物清单

| 类型 | 路径 | 治理文件 | 是否需单独批准 |
|---|---|---|---|
| preflight | `audit/baseline/preflight-batch-2c.md` | ✅ 是 | ✅ 已获用户批准 |
| evidence | `audit/evidence/F171-attendance-commission-perm-alignment/**` | 否 | — |
| issue | `audit/issues/I183`–`I189`（7 个） | 否 | — |
| register | `audit/issues/issue-register.md`（追加 7 行） | ✅ 是 | ✅ 已获用户批准 |

---

## 8. Phase 0 结论

✅ **通过**。库连通、基线明确、账号可用、快照新鲜、只读纪律可核。
约束 C1（数据稀疏）已在结论口径中显式承接，相关项标 **UNVERIFIED** 而非 VERIFIED。
