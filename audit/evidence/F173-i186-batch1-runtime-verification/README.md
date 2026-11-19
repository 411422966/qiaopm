# F173 — I186 第 1 批「数据范围缺省注入」运行期验证证据

> **性质**：**运行期验证证据目录**（含只读探测与清理脚本；本次未修改任何业务代码）
> **验证日期**：2026-11-17 23:20 - 23:33（主验证两轮） / 2026-11-17 23:37 - 23:53（清理与定点复验） / 2026-11-18 01:12（清理后逐项复检）
> **执行者**：Cursor
> **绑定基线**：HEAD `7a4e72a`（含 I186 第 1 批 `c37a549`）
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`（2026-11-16 02:00，干净基线）
> **环境**：`audit/evidence/env.md`（docker `meiyetec-mysql` / MySQL 8.0 / 库 `meiyetec` / API `localhost:8080`）
> **关联**：**I186**（被验证的批次）、**I198**（本目录证据被临时授权污染的登记条目）、I183 / I184 / I185 / I187 / I191 / I194

---

## 0. 目录用途

本目录是 **I186 第 1 批**（考勤 / 提成模块数据范围「缺省按门店集合过滤」）在 **2026-11-17 夜间** 的运行期验证原始输出与配套脚本目录。

它承担两件事：

1. **留证** —— 保存主验证脚本 `raw/verify-i186-batch1.ps1` 的逐端点原始输出（`raw/01`）、控制台副本（`raw/console.txt`）与导出样本 xlsx。
2. **留证（反证）** —— 保存临时授权撤销后的逐项复检（`raw/03`），用于证明主验证 PASS **不可复现**。

本目录**不是** I186 第 1 批的验收结论载体。结论载体是 `audit/issues/I186-*.md`；综合验证报告见 `audit/reports/i186-batch1-final-verification.md`。

---

## 1. ⚠️ 污染声明

> **本目录中的运行期 PASS 依赖一条后来被撤销的临时授权，清理后不可复现。**
> **登记条目**：`audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md`
> **权威复检**：`raw/03-post-cleanup-permission-recheck.txt`

| 项 | 内容 |
|---|---|
| 临时授权窗口 | **2026-11-16 23:34:53** — 单事务给 `role_id = 5`（MANAGER）授予 12 条权限码（attendance 4 条：1451 / 1454 / 1455 / 1459；commission 8 条：1330 / 1332 / 1333 / 1334 / 1335 / 1338 / 1342 / 1345）。客户端 `charset=latin1`、`thread_id=2448`（**非应用 JDBC 线程**），仓库内无脚本产生 |
| 撤销时间 | **2026-11-17 23:36:34** — 单事务 DELETE 883 行（role 5 的 12 条 + 3 个夹具角色的 871 行） |
| 撤销后实测 | **11 处 200 → 403 状态翻转**（manager01），403 响应体全部为 `权限不足: 需要权限码：X`，且 X 精确命中上述 12 条码 |
| 反证 | `admin`（持 SUPER_ADMIN 通配 `*`）全部保持 200 → 翻转由**授权缺失**造成，非代码回归 |
| 夹具账号 | `i186fix_s1` / `i186fix_s5` / `i186fix_multi`（**user_id** 99999704 / 99999705 / 99999706；**role_id** 900004 / 900005 / 900006），密码明文已知（`123456`），**已于 11-17 23:37 清理删除** |
| 连带影响 | **11-17 02:00 的定时备份 `backup/meiyetec_20261117_020004.sql` 被污染**（+883 行权限绑定 + 3 个夹具角色/账号）→ **不可用于任何回滚/恢复**，见 `backup/README-DO-NOT-RESTORE.txt` |

**已打标记的文件**：`raw/01-runtime-verification.txt`（文件头部前 22 行「⚠️ 污染声明 / POLLUTION NOTICE」区块）。
**误判文件**：`raw/02-item5-recheck.txt` 的 `DEFECT_CONFIRMED` 为**误判** —— 脚本 `GetJson` 的 `catch` 分支返回 `http=-1`，丢弃了 HTTP 状态码，把 403 塌缩成 `data=$null`。

---

## 2. 文件清单

| 文件 | 内容 | 状态 |
|---|---|---|
| `raw/01-runtime-verification.txt` | **主验证原始输出（二轮，23:33）** —— V1~V13 + 补测 2（多店账号集合语义 9 端点）+ 补测 3（写侧越权与多店写接口语义 4 端点），逐端点带 `http=` 状态码 | ✅ 已打污染标记（头部 22 行） |
| `raw/console.txt` | 主验证的控制台副本（内容与 `raw/01` 同源，含 5 个账号的 `/auth/me/permissions` 作用域） | ✅ 已打污染标记（头部 22 行） |
| `raw/02-item5-recheck.txt` | ITEM5 定点复验输出（23:46:27）—— 判定 `DEFECT_CONFIRMED` | ⚠️ **误判**（脚本吞状态码），结论以 `raw/03` 为准 |
| `raw/03-post-cleanup-permission-recheck.txt` | **清理后逐项只读复检（11-18 01:12）** —— 11 处 403 响应体原文 + 逐端点对照表 + 脚本盲区复刻 + 编号冲突警告 + 结论 | ✅ **权威复检** |
| `raw/cleanup-i186-fixtures.ps1` | 夹具清理脚本（含 13 条 DELETE：夹具业务表 / 夹具角色权限 / `role_id=5 AND create_time >= '2026-11-16 23:00:00'` / 夹具账号） | ⚠️ 非正式工具，未入 CI |
| `raw/cleanup-output.txt` | 清理后复核计数输出（夹具类计数归 0 + `sys_role_permission(role=5 recent) 0` + `sys_role_permission(role>=900000) 0`） | ⚠️ **缺 role 5 总行数（基线 278）断言** |
| `raw/verify-i186-batch1.ps1` | 主验证脚本（记录 `http=` 与响应体，但 V13 行未逐端点标状态码） | ⚠️ 非正式工具，未入 CI |
| `raw/recheck-item5.ps1` | ITEM5 复检脚本（`GetJson` 的 `catch` 分支丢弃 HTTP 状态码 —— 误判根因） | ⚠️ 非正式工具，未入 CI |
| `raw/_tmp_export_admin.xlsx` | admin 导出样本（8376 字节） | 样本 |
| `raw/_tmp_export_m01.xlsx` | manager01 导出样本（8319 字节） | 样本 |
| `raw/_tmp_export_multi.xlsx` | 多店账号导出样本（8346 字节） | 样本 |

---

## 3. 不可复现项清单

> **含义**：主验证中取得 PASS、但**在当前基线授权状态下无法再复跑**的项。授权撤销后即不可复现 —— 复跑必须先完成 I183 / I184 的权限码绑定。

| # | 项 | 依赖 | 当前状态 | 阻塞原因 |
|---|---|---|---|---|
| 1 | `GET /commission/records/stats` | `commission:record:stat`（1330） | **403（不可复现）** | 临时授权已撤销，码无角色绑定 |
| 2 | `GET /commission/salary/stats` | `commission:salary:stat`（1334） | **403（不可复现）** | 同上 |
| 3 | `GET /commission/settlement/stats` | `commission:settlement:stat`（1338） | **403（不可复现）** | 同上 |
| 4 | `GET /commission/salary/export`（缺省） | `commission:salary:export`（1333） | **403（不可复现）** | 同上 |
| 5 | `GET /commission/settlement/export`（缺省） | `commission:settlement:export`（1342） | **403（不可复现）** | 同上 |
| 6 | `GET /attendance/records` | `attendance:list`（1454） | **403（不可复现）** | 同上 |
| 7 | `GET /attendance/leave/list` | `attendance:leave:list`（1459） | **403（不可复现）** | 同上 |
| 8 | `GET /attendance/config/list` | `attendance:config:list`（1451） | **403（不可复现）** | 同上 |
| 9 | `GET /attendance/config/active` | `attendance:config:list`（1451） | **403（不可复现）** | 同上 |
| 10 | `GET /attendance/statistics`（带日期 / 缺省） | `attendance:stat`（1455） | **403（不可复现）** | 同上（2 个变体） |
| 11 | MV-V9 跨租户结算单不复用 | `commission:settlement:add`（1345）+ 夹具账号 | **未复跑** | 夹具账号已删除（`login_ok=False`） |
| 12 | MV-V10 单店账号 `salary/generate` → 门店 5 | `commission:salary:add`（1335）+ `i186fix_s5` | **未复跑** | 同上 |
| 13 | MV-V11 多店账号 `salary/generate` → 400 | `commission:salary:add`（1335）+ `i186fix_multi` | **未复跑** | 同上 |
| 14 | 补测 2：多店账号集合语义（9 个端点） | `i186fix_multi` | **无法复跑** | 同上 |
| 15 | 补测 3：写侧越权与多店写接口语义（4 个端点） | 夹具账号 | **无法复跑** | 同上 |
| 16 | MV-V6 DB 结算单重复组检查 | 纯 SQL，不经 HTTP | **结论仍成立**（重复组 = 0） | 清理未触及 `commission_settlement` |

### 3.1 证据链自身的不可复现项（版本管理缺口）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| A | 一轮验证输出 `.audit-tmp/verify-result.txt` | ⚠️ **未入 git** | `git ls-files .audit-tmp` = **0**；该文件是 V4c `bytes=3522` 与 V9/V10/V11 三个 `200` 数值的**唯一来源**（`raw/01` 中不含这些值）→ 克隆仓库后无法复核 |
| B | 复检脚本与输出 `.audit-tmp/post-cleanup-recheck.ps1` / `.out.txt` | ⚠️ **未入 git** | `raw/03` 是其输出的整理版，原始脚本不可从仓库获得 |
| C | binlog 原始与解码产物 `.audit-tmp/binlog/binlog.000139/140/141` + `bl139/140/141.txt` | ⚠️ **未入 git** | §1.1 授权时间线的唯一原始依据 |
| D | F173 目录整体 | ⚠️ **未入 git** | `git ls-files audit/evidence/F173-i186-batch1-runtime-verification` = **0**；本目录当前为未跟踪状态 |

> ⚠️ 上述 A~D 均为**本地工作区**文件，不在版本管理内。若本目录要作为 I186 的正式验收证据提交，**必须先解决 A~D 的入库问题**，否则证据链无法在克隆后复现。

---

## 4. 边界声明

- 本目录内的脚本（`verify-i186-batch1.ps1` / `cleanup-i186-fixtures.ps1` / `recheck-item5.ps1`）为**调查附带脚本**，**非正式工具**，**不进入 `audit/scripts/` 正式守门序列、不接入 CI**。
- 本次验证**修改了业务数据**（创建并已删除夹具角色 / 账号 / 考勤配置 / 提成记录 / 结算单）—— 这是**唯一**一次夹具写入，全部在 11-17 23:37 由 `raw/cleanup-i186-fixtures.ps1` 清理。
- **未打开浏览器**、**未连接生产库**、**未替代人工验收**。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
- **11-17 02:00 的备份已被本目录的夹具污染**，不可恢复 —— 见 `backup/README-DO-NOT-RESTORE.txt` 与 I198 §B。

---

**下一步**：在 **I183 / I184 的权限码绑定修复完成**之后，按 I198 §3 的 8 条建议重跑 I186 第 1 批运行期验证；重跑时须①避开每日 02:00 备份窗口、②记录每个端点的 HTTP 状态码与响应体、③登记夹具账号与授权台账。
