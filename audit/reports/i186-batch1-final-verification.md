# I186 第 1 批 —— 综合最终验证报告

> **性质**：**综合汇总报告**（汇总散落在 F173 证据目录 / I198 / I194 / I186 各条中的运行期结果）
> **批次**：I186 第 1 批（数据范围「缺省按门店集合过滤」）—— 代码修复 commit `c37a549`
> **报告生成时间**：2026-11-18 **16:11:50**（文件 CreationTime 实测；**本治理轮内补生成**，非历史文件）
> **执行者**：Cursor
> **绑定基线**：HEAD `7a4e72a79263823a5be311f527ccd6e4d1d86233`（branch `main`）
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`（2026-11-16 02:00，**干净基线**）
> 　　　　　　　`backup/meiyetec_20261117_020004.sql`（2026-11-17 02:00，**已污染 —— 不可回滚/恢复**）
> **环境**：`audit/evidence/env.md`（docker `meiyetec-mysql` / MySQL 8.0 / 库 `meiyetec` / API `localhost:8080`）
> **主证据目录**：`audit/evidence/F173-i186-batch1-runtime-verification/`
> **登记条目**：`audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md`

---

## ⚠️ 0. 总体结论（先读这一段）

| # | 结论 | 等级 |
|---|---|---|
| 1 | **I186 第 1 批的代码修复已落地**（commit `c37a549`，20 个文件、+608/−88），后端「缺省按 `ctx.storeIds` 集合过滤」的实现存在 | **VERIFIED（静态 + 提交事实）** |
| 2 | **I186 第 1 批的运行期验收证据不可用**：主验证 PASS 是在 **2026-11-16 23:34:53 一次性授予 role 5 的 12 条权限码**的窗口内取得的，该授权已于 **2026-11-17 23:36:34 撤销**；撤销后同账号同端点复检出现 **11 处 200 → 403 状态翻转** | **VERIFIED（运行期 L5a）** |
| 3 | 依赖已删除夹具账号的 **5 组项**（主验证 V9 / V10 / V11、补测 2、补测 3）**无法复跑** | **UNVERIFIED（前提账号已删除）** |
| 4 | **I186 第 1 批尚未获得可复现的运行期验收证据** —— 必须在**基线授权状态**（补齐 I183 / I184 的权限码绑定）之下重跑 | **P0** |
| 5 | **11-17 02:00 的定时备份已被夹具污染，不可用于任何回滚/恢复** | **P0** |

> 🔴 **打标（按 I198 §A）**：**本批运行期 PASS 依赖临时授权、不可复现。**
> 下文 §3 的全部「主验证」数值**必须**与 `audit/evidence/F173-i186-batch1-runtime-verification/raw/03-post-cleanup-permission-recheck.txt` 一起阅读，**不可单独作为 I186 验收证据**。

> 📌 **本报告生成原因（§0 必读）**：I186 第 1 批的运行期验证结论**散落**在 F173 证据目录 / I198 / I194 / I186 各条 issue 文档中，且其中「主验证 PASS」已被证明落在临时授权窗口内、不可单独引用。生成一份**单一汇总文档**的目的有二：① 把 13 项主验证 + 2 项补测 + 夹具清理 + 撤销后复检的**原始数值与打标集中到一处**，避免被单独摘取误用；② 明确登记 **哪些结论成立（代码已落地）/ 哪些不成立（运行期不可复现）/ 哪些未决（重跑前置条件）**。
> ⚠️ **生成时间口径**：本文件 CreationTime = **2026-11-18 16:11:50**，属**本治理轮内补生成**，**不是**本次会话开始前就存在的历史文件；此后 §7 的工作区口径随落盘多次变化（D-3-14 → D-3-16 → D-3-17 → **D-3-26**，当前实测值 = **25 项**），阅读时以 §7 段内的采集时间标注为准。

---

## 1. 批次范围与代码变更

### 1.1 范围（I186 第 1 批，拍板口径见 `audit/reports/i186-batch1-fix-draft.md`）

| 编号 | 内容 | 状态 |
|---|---|---|
| **G0** | 统一范围解析基础设施（启用并补齐既有 `DataScopeService`，非新建 `StoreScopeResolver`） | 已落地 |
| **G1** | 列表 / 统计类接口缺省注入 `IN (ctx.storeIds)` | 已落地 |
| **专项 1** | `AttendanceConfigController.list` 的 `or()` 括号修正 | 已落地 |
| **专项 2** | `CommissionController:276` 硬编码 `1L`、`CommissionSettlementService` 缺 `tenantId` | 已落地 |
| **Q1** | 移除 `DataScopeInterceptor.WHITELIST` 的导出豁免 | 已落地 |
| — | **明确不在本批**：G2 / G3 / G4（详情/写/批量归属校验 → 第 2 批）、G5 / M3（等 I193）、I189（等 I192）、I191（等请假语义拍板） | 未开始 |

### 1.2 代码变更（`git show --stat c37a549`）

```
20 files changed, 608 insertions(+), 88 deletions(-)
```

| 类别 | 文件 |
|---|---|
| Controller | `AttendanceConfigController.java`（21） / `AttendanceController.java`（13） / `CommissionController.java`（133） / `LeaveRequestController.java`（2） |
| Mapper（Java） | `AttendanceConfigMapper.java`（15） / `AttendanceRecordMapper.java`（35） / `LeaveRequestMapper.java`（24） |
| Security | `DataScopeInterceptor.java`（4） / `DataScopeService.java`（138） / `StoreScope.java`（129，**新增**） |
| Service | `AttendanceConfigService.java`（12） / `AttendanceRecordService.java`（9） / `CommissionSettlementService.java`（2） / `LeaveRequestService.java`（3） / `AttendanceConfigServiceImpl.java`（27） / `AttendanceRecordServiceImpl.java`（25） / `LeaveRequestServiceImpl.java`（5） |
| Mapper XML | `AttendanceConfigMapper.xml`（24） / `AttendanceRecordMapper.xml`（43） / `LeaveRequestMapper.xml`（32） |

> ✅ **无前端改动**（前端硬编码 `storeId: 1` 属 **I194**，独立批次，必须紧随本批上线）。
> ✅ **无 DDL**、无迁移脚本。

---

## 2. 运行期验证原始结果（主验证 —— 已污染）

> **采集窗口**：2026-11-17 23:20:00 – 23:33:35（两轮：23:21 首轮 / 23:33 二轮）
> **原始输出**：`audit/evidence/F173-i186-batch1-runtime-verification/raw/01-runtime-verification.txt`（二轮）
> **控制台副本**：同目录 `raw/console.txt`
> **⚠️ 本节全部 PASS 落在 11-16 23:34:53 临时授权窗口内，撤销后不可复现（见 §4）**

| 项 | 端点 / 内容 | 主验证结果 |
|---|---|---|
| V1 | `GET /commission/records/page`（缺省，manager01） | 200 total=10 stores=[1] |
| V2 | `GET /commission/records/page?storeId=5` | **403** 无权访问该门店数据（越权拦截生效） |
| V3a/b | `GET /commission/salary/page` / `/settlement/page`（缺省） | 200 total=152 / 1，stores=[1] |
| V4a | `GET /commission/salary/export`（缺省） | 200 bytes=8319，数据行=152，与 DB(store_id=1,tenant=1,2026-10)=152 **一致=True** |
| V4b/d | `salary/export?storeId=5` / `settlement/export?storeId=5` | **403**（越权拦截生效） |
| V5 | `GET /attendance/records`（缺省） | 200 count=1 stores=[1] |
| V6 | DB 结算单重复检查 `(store_id, salary_month, tenant_id)` | 重复组数=0 → PASS（纯 SQL，不经 HTTP） |
| V7 | `GET /attendance/config/list`（缺省） | 200 count=3（id=2 storeId=1 / id=3、id=1 storeId 空） |
| V8 | `GET /attendance/config/active`（缺省） | 200 id=2 tenantId=1 storeId=1（本店行优先） |
| V9 | `POST /commission/settlement/generate` 跨租户不复用 | 200（tenant2 已有 99999998/2026-09 未被复用） |
| V10 | `POST /commission/salary/generate` 单店账号 `i186fix_s5` | 200 storeIds=[5] |
| V11 | `POST /commission/salary/generate` 多店账号 `i186fix_multi` | **400**「当前账号可访问多个门店，请指定 storeId」 |
| V12 | admin（`dataScope=ALL`）缺省全量语义 | records=14 / salary=154 / attendance=4；`salary/export` 200 bytes=8376，与 DB(tenant=1 全部门店)=154 **一致=True** |
| V13 | statistics 与 records 口径一致性 | manager01 1=1、i186fix_multi 2=2、admin 4=4，**全部一致=True** |
| 补测 2 | 多店账号（storeIds={1,5}）集合语义 **9 个端点** | records 缺省 total=12 stores=[1,5]；`?storeId=1` total=10；`?storeId=9` **403**；attendance/records count=2；leave/list count=1；salary/page total=153；settlement/page total=1；`salary/export` 200 bytes=8346，与 DB(store_id IN(1,5))=153 **一致=True** |
| 补测 3 | 写侧越权与多店写接口语义 **4 个端点** | `salary/generate?storeId=9` **403**；`settlement/generate` 缺省（多店）400「storeId 必填」；`settlement/generate` 缺省（单店 s5）400「storeId 必填」；`salary/generate?storeId=9`（单店越权）**403** |

### 2.1 首轮（23:21）与二轮（23:33）的差异

首轮输出在 `.audit-tmp/verify-result.txt`（**未入 git**），记录了 ITEM5 / ITEM12 FAIL；本报告 §2 采用二轮（`raw/01`）。
主验证的 `V4c settlement/export` 缺省 200 bytes=3522 与 **V9 / V10 / V11 的 200** 数值**仅见于首轮**（`raw/01` 只记 `settlement/export?storeId=5 http=403`，且不含三个 stats 端点）。

---

## 3. 清理记录（夹具清理 —— 11-17 23:37 执行完毕）

> **原始输出**：`audit/evidence/F173-i186-batch1-runtime-verification/raw/cleanup-output.txt`
> **清理脚本**：同目录 `raw/cleanup-i186-fixtures.ps1`（13 条 DELETE，含撤销 role 5 临时授权）
> **性质**：本批**唯一**一次夹具写入，已全部清理

| 计数项 | 值 | 判定 |
|---|---|---|
| `commission_settlement(169-172)` | 0 | ✅ 夹具已清 |
| `commission_settlement_record(169-172)` | 0 | ✅ |
| `commission_salary(all)` | 0 | ✅ |
| `commission_record(fixture 747-750)` | 0 | ✅ |
| `commission_record(total kept)` | 10 | ✅ 保留真实数据 |
| `attendance_record(total)` | 0 | ✅ |
| `attendance_leave_request(total)` | 0 | ✅ |
| `attendance_config(total)` / `(detail)` | 1 / 1 | ✅ 与基线一致 |
| `pos_employee(I186_FIX)` | 0 | ✅ |
| `pos_employee(total)` | 175 | ✅ |
| `sys_user(i186fix%)` | 0 | ✅ 夹具账号已删 |
| `sys_role(I186FIX%)` | 0 | ✅ 夹具角色已删 |
| `sys_role_data_scope(role>=900000)` | 0 | ✅ |
| `sys_user_role(user>=99999700)` | 0 | ✅ |
| `sys_role_permission(role>=900000)` | 0 | ✅ 夹具角色绑定已清 |
| `sys_role_permission(role=5 recent)` | 0 | ✅ role 5 临时授权已撤销 |

> ⚠️ **该输出缺 role 5 总行数断言**（基线应为 278）—— 现有断言只覆盖 `create_time >= 2026-11-16 23:00:00` 的 `recent` 子集，**无法据此判断是否回到 278**。
> 📌 **独立复核（2026-11-18 本轮）**：解析三份备份 `sys_role_permission` INSERT 按 `role_id` 分组 —— 11-16 基线 **1164** 行 / role 5 = **278**；11-17 污染 **2047** 行 / role 5 = **290**；11-18 **1164** 行 / role 5 = **278**、**无** 900004/900005/900006 任何绑定 → **污染窗口仅限 11-17 那一个备份文件，库当前状态已回到基线**。

---

## 4. 撤销后逐项复检（2026-11-18 01:11:50 – 01:12:17）—— **11 处状态翻转**

> **原始输出**：`audit/evidence/F173-i186-batch1-runtime-verification/raw/03-post-cleanup-permission-recheck.txt`
> **复检脚本**：`.audit-tmp/post-cleanup-recheck.ps1`（**非正式工具**，只发 GET + 登录取 token，零写操作）

| 端点 | 主验证 23:33 | 清理后 01:12 | 判定 | 403 缺失权限码 |
|---|---|---|---|---|
| `GET /commission/records/page`（缺省） | 200 total=10 | 200 total=10 | 一致 | — |
| `GET /commission/records/page?storeId=5` | 403 越权 | 403 越权 | 一致 | — |
| `GET /commission/salary/page` / `/settlement/page` | 200 total=152 / 1 | 200 total=0 / 0 | 状态一致（数据量随夹具清理归零） | — |
| `GET /commission/salary/export`（缺省） | 200 bytes=8319 | **403** | 🔴 **状态翻转** | `commission:salary:export`（1333） |
| `GET /commission/salary/export?storeId=5` | 403 越权 | 403 越权 | 一致 | — |
| `GET /commission/settlement/export`（缺省） | 200 bytes=3522（一轮） | **403** | 🔴 **状态翻转** | `commission:settlement:export`（1342） |
| `GET /commission/settlement/export?storeId=5` | 403 越权 | 403 越权 | 一致 | — |
| `GET /attendance/records` | 200 count=1 | **403** | 🔴 **状态翻转** | `attendance:list`（1454） |
| `GET /attendance/leave/list` | 200 count=1 | **403** | 🔴 **状态翻转** | `attendance:leave:list`（1459） |
| `GET /attendance/config/list` | 200 count=3 | **403** | 🔴 **状态翻转** | `attendance:config:list`（1451） |
| `GET /attendance/config/active` | 200 id=2 | **403** | 🔴 **状态翻转** | `attendance:config:list`（1451） |
| `GET /commission/records/stats` | 200（一轮） | **403** | 🔴 **状态翻转** | `commission:record:stat`（1330） |
| `GET /commission/salary/stats` | 200（一轮） | **403** | 🔴 **状态翻转** | `commission:salary:stat`（1334） |
| `GET /commission/settlement/stats` | 200（一轮） | **403** | 🔴 **状态翻转** | `commission:settlement:stat`（1338） |
| `GET /attendance/statistics`（带 / 不带日期） | 200 totalRecords=1 | **403** | 🔴 **状态翻转** | `attendance:stat`（1455） |

**反证**：`admin`（持 SUPER_ADMIN 通配 `*`）全部保持 200 → 翻转由**授权缺失**造成，**非代码回归**。

### 4.1 污染时间线（binlog.000139 / 140 / 141 解码）

| 时间 | 事件 | 来源 |
|---|---|---|
| 11-16 23:34:53 | **单事务**给 role 5（MANAGER）授予 **12 条**权限码（attendance 4：1451/1454/1455/1459；commission 8：1330/1332/1333/1334/1335/1338/1342/1345）；客户端 `charset=latin1`、`thread_id=2448`（**非应用 JDBC 线程**） | `binlog.000139`（事件 id 2025→2036） |
| 11-17 01:17:35 / 01:17:36 | 建 3 夹具角色（role_id 900004/900005/900006，role create_time @01:17:35）+ 3 夹具用户（user_id **99999704/99999705/99999706**，user create_time @01:17:36，密码明文已知）；并给 3 个夹具角色各授予约 290 条绑定（**合计 871 行**，其中 900004 多 1 条 `1453`） | `binlog.000140` + 备份 INSERT 文本统计 |
| 11-17 02:00:04 | 定时备份 `meiyetec_20261117_020004.sql` 落盘（**含全部夹具**） | `backup/logs/backup-20261117.log` |
| 11-17 23:20–23:33 | F173 主验证取得大量 PASS | `raw/01` / `.audit-tmp/verify-result.txt` |
| 11-17 23:36:34 | **单事务撤销**：删除 role 5 的 12 条 + 3 夹具角色的 871 行（**DELETE 883 行**），回到基线（role 5 = 278） | `binlog.000141` |
| 11-17 23:37 | 夹具清理脚本执行完毕 | `raw/cleanup-output.txt` |
| 11-17 23:46:27 | ITEM5 定点复验判定 `DEFECT_CONFIRMED` —— **误判**（脚本 `catch` 分支丢弃 HTTP 状态码，403 塌缩成 `data=$null`） | `raw/02-item5-recheck.txt` |
| 11-17 23:53 | 人工重新登录复测 5 个端点 —— **全部 403** | 本次会话只读 GET |
| 11-18 01:12 | 逐项复检：**11 处 200 → 403 翻转** | `raw/03` |

### 4.2 备份污染（不可回滚/恢复）

| 项 | 11-17 污染备份 | 11-16 基线 | 11-18 备份 |
|---|---|---|---|
| 文件 | `meiyetec_20261117_020004.sql`（24,964,139 字节） | `meiyetec_20261116_020004.sql`（25,507,804 字节） | `meiyetec_20261118_020003.sql` |
| `sys_role_permission` 行数 | **2047** | **1164** | **1164** |
| role 5 行数 | **290**（= 278 + 12 临时授权） | **278** | **278** |
| 夹具角色 900004/900005/900006 | **291 / 290 / 290**（合计 871） | 无 | 无 |
| 12 条临时码绑定 | 12 码 × 4 角色（role 5 + 3 夹具角色）= **48 条** | 全 0 | 全 0 |
| 夹具账号 / 角色 | 3 用户（99999704~6）+ 3 角色（900004~6），**密码明文已知** | 无 | 无 |

→ **恢复 11-17 备份 = 把已知密码的测试账号写回目标环境 + 把 883 行额外权限（含 I183/I184 认定为死码的 `attendance:*` 与 `commission:*:stat/export`）写回 `sys_role_permission` → 绕开 I183/I184 的修复。**
→ 警示已落地：`backup/README-DO-NOT-RESTORE.txt`。

---

## 5. 未验证项清单

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
| 10 | `GET /attendance/statistics`（带 / 不带日期） | `attendance:stat`（1455） | **403（不可复现）** | 同上（2 个变体） |
| 11 | MV-V9 跨租户结算单不复用 | `commission:settlement:add`（1345）+ 夹具账号 | **未复跑** | 夹具账号已删除（`login_ok=False`） |
| 12 | MV-V10 单店账号 `salary/generate` → 门店 5 | `commission:salary:add`（1335）+ `i186fix_s5` | **未复跑** | 同上 |
| 13 | MV-V11 多店账号 `salary/generate` → 400 | `commission:salary:add`（1335）+ `i186fix_multi` | **未复跑** | 同上 |
| 14 | 补测 2：多店账号集合语义（9 端点） | `i186fix_multi` | **无法复跑** | 同上 |
| 15 | 补测 3：写侧越权与多店写接口语义（4 端点） | 夹具账号 | **无法复跑** | 同上 |
| 16 | MV-V6 DB 结算单重复组检查 | 纯 SQL，不经 HTTP | **结论仍成立**（重复组 = 0） | 清理未触及 `commission_settlement` |
| 17 | **前端 → 后端完整链路**（前端不传 `storeId` → 后端按集合过滤） | **I194 未落地**（前端仍显式传 `storeId: 1`） | **UNVERIFIED（静态推导）** | I194 独立批次，紧随本批上线 |
| 18 | **浏览器端真实流量下的表现** | 真实前端 | **UNVERIFIED** | 本批**未打开浏览器**，未做人工验收 |
| 19 | 生产环境等价性 | — | **UNVERIFIED** | 本次**未连接生产库** |
| 20 | `attendance_record` 在 11-17 23:46 ~ 11-18 01:12 之间为何又出现 1 行 | — | **UNVERIFIED（来源未追查）** | 不影响 403 结论 |

### 5.1 证据链自身的不可复现项（版本管理缺口）

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| A | 一轮验证输出 `.audit-tmp/verify-result.txt` | ⚠️ **未入 git** | §2 中 `settlement/export` bytes=3522 与 V9/V10/V11 的 200 数值的唯一来源；克隆仓库后无法复核 |
| B | 复检脚本与输出 `.audit-tmp/post-cleanup-recheck.ps1` / `.out.txt` | ⚠️ **未入 git** | `raw/03` 是其整理版，原始脚本不可从仓库获得 |
| C | binlog 原始与解码产物 `.audit-tmp/binlog/*` | ⚠️ **未入 git** | §4.1 时间线的唯一原始依据 |
| D | F173 证据目录整体 | ✅ **已入 git**（2026-11-19 23:4x `git add`，7 个文件入索引） | 目录内 6 个 `.ps1` / `.xlsx` 仍被 `.gitignore:143/147` 排除（按项目规则 19 不入库）；`.audit-tmp/` 路径本身被 `.git/info/exclude:9` 忽略，故上方 A / B / C 三项**仍未入 git** |

---

## 6. 启动检查

> **采集时间**：2026-11-18 15:40（**本轮补采**，非 11-17 23:33 当时的快照）
> **命令**：`docker ps --format "{{.Names}}|{{.Status}}|{{.Ports}}"`

| 容器 | 状态 | 端口 |
|---|---|---|
| `meiyetec-api` | Up 2 hours (healthy) | `0.0.0.0:8080->8080/tcp` |
| `meiyetec-web` | Up 2 hours (healthy) | `0.0.0.0:8088->80/tcp` |
| `meiyetec-redis` | Up 2 hours (healthy) | `0.0.0.0:6379->6379/tcp` |
| `meiyetec-mysql` | Up 2 hours (healthy) | `0.0.0.0:3307->3306/tcp` |

**API 启动日志尾部**（`docker logs meiyetec-api --tail 15`，2026-11-18 15:40 ~ 16:05）：定时任务正常运行（`TenantFreezeTask`、`ComplaintSlaAutoEscalationJob`、`KioskOfflineAlertService`），**无启动异常**。

> ⚠️ **限制**：11-17 23:33 主验证当时的容器状态与启动日志**未留存**，无法追溯；上表为 11-18 补采，仅证明当前运行栈健康。

---

## 7. 工作区状态（`git status --porcelain`，2026-11-19 **23:4x 实测**；2026-11-18 23:4x 删除 `_diffstat.txt` 后由 26 → 25；2026-11-19 05:5x 执行 `git add` 后由 25 → 31；2026-11-19 23:4x 清理 15 个 backend stat 脏条目后由 31 → **16**）

**共 16 项**（5 个已跟踪文件被修改 + 11 个已暂存新增（`git add` 后进入索引）+ 0 个未跟踪项）：

> ⚠️ **本节数字随落盘变化**：① 写入 `audit/issues/issue-register.md`（I198 登记 + I183/I184/I194 追加 + 头部时间线）后，已跟踪修改由 19 → **20**、总数由 25 → **26**；② **2026-11-18 23:4x 删除根目录临时文件 `_diffstat.txt` 后，未跟踪由 6 → 5、总数由 26 → 25**（该文件为 0 字节空文件，删前已确认内容被本报告 §7 与 I198 吸收，登记见 I198 §D-3.2 D-3-26 行）；③ **2026-11-19 05:5x 经用户明确授权执行 `git add`**（F173 证据目录 7 个文件 + F171 `raw/13`/`raw/14`）后，「未跟踪」由 5 → **2**、但新增「**已暂存新增**」一类 **9** 项，总数 25 → **31**（登记见 I198 §D-3.2 D-3-27 行更新）；④ **2026-11-19 23:4x**：I198 文档 + 本报告经用户批准 `git add` 入索引（已暂存新增 9 → **11**、「未跟踪」2 → **0**），同时**清理 15 个 backend stat 脏条目**（`git diff --stat -- backend/` 实测为空 → `git checkout -- backend/`，`exit=0`），已跟踪修改 20 → **5**、总数 31 → **16**（登记见 I198 §D-3.2 D-3-30 行）。本节以**当前实测**为准。

> 📌 **「未跟踪」定义**：指**尚未纳入 git 版本管理**的工作区条目 —— 既**未登记**到 `audit/issues/issue-register.md`，也**未纳入** I186 第 1 批代码范围（纯审计产物 / 证据文件 / 本批新登记的 issue 文档）。与「已跟踪修改」（有 git 历史、本轮被改写）分列。

| 类别 | 数量 | 内容 | 登记状态 |
|---|---|---|---|
| 已修改（audit） | 5 | `audit/evidence/F171-.../README.md`、`audit/issues/I183-*.md`、`I184-*.md`、`I194-*.md`、`audit/issues/issue-register.md` | I183 / I184 / I194 / I198 均已在 register 登记 |
| 已暂存新增（`git add` 后入索引） | 11 | `audit/evidence/F173-i186-batch1-runtime-verification/`（README + `raw/01`~`raw/04` + `raw/cleanup-output.txt` + `raw/console.txt` = **7**）；`audit/evidence/F171-.../raw/13-perm-binding-timeline-20261117.txt`；`audit/evidence/F171-.../raw/14-perm-binding-timeline.sql`；`audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md`；`audit/reports/i186-batch1-final-verification.md`（本报告自身） | 证据文件不单独登记 issue-register；I198 已登记于 register（L125）；本报告为 evidence 文档，其结论引用 I198 |
| 未跟踪 | 0 | （无） | 本轮 2 个未跟踪项（I198 + 本报告）已于 2026-11-19 23:4x `git add` 入索引 |

**未跟踪 0 项** —— 原 2 项（I198 文档、本报告自身）已于 2026-11-19 23:4x 经用户批准 `git add` 入索引，现归入上方「已暂存新增 11」一类。下表保留登记状态说明备查：

| 未跟踪项 | 登记状态 |
|---|---|
| `audit/issues/I198-i186-verify-evidence-tainted-and-backup-pollution.md` | **本批新登记**，已写入 `audit/issues/issue-register.md`（L125，**2026-11-18 23:1x 写入，待用户复核** —— 未经用户逐字确认即落盘，流程偏差见 I198 §D-3-18）；**2026-11-19 23:4x 已 `git add` 入索引** |
| `audit/reports/i186-batch1-final-verification.md`（本报告自身） | **evidence 文档，不单独登记 issue-register**；其结论引用 I198；**2026-11-19 23:4x 已 `git add` 入索引** |

> ✅ **已转入「已暂存新增」的 2 项**：F171 `raw/13-perm-binding-timeline-20261117.txt` / `raw/14-perm-binding-timeline.sql` —— F171 既有证据目录的补充件，归属 I183 / I184（已在 register 登记），不新开条目；**2026-11-19 05:5x 已 `git add` 入索引**。

> ✅ **已清理**：根目录临时文件 `_diffstat.txt`（**0 字节空文件**，删前 `Get-Content` 确认内容已被本节与 I198 吸收）已于 **2026-11-18 23:4x** 删除 → 未跟踪 6 → 5、总数 26 → **25**（I198 §D-3.2 D-3-26 行）。

> ⚠️ `backup/README-DO-NOT-RESTORE.txt`（I198 §B 要求的「不可恢复」告示）位于 `backup/` 目录下，被 `.gitignore:506` 的 `backup/` 规则忽略 → **不会出现在 `git status` 中，也不会随仓库分发**。它是本地告示文件，不计入上述 16 项。

> ✅ **「已修改（backend）」15 项已清理（2026-11-19 23:4x）** —— 此前它们看似「已修改」，实为 **stat 脏伪修改**：逐文件比对 `git hash-object <工作区文件>` 与 `git rev-parse HEAD:<file>`，**15 个 backend 文件的工作区 blob 哈希与 HEAD 完全相同**（`AttendanceController.java` 等均为 `5e6b85cd…`），`git diff --stat -- backend/` 输出**为空** → 仅 mtime / 索引 stat 信息过期，**内容未变**。**清理前安全前提已确认**（`git diff --stat -- backend/` 为空 → `git checkout --` 不会丢失任何改动），执行 `git checkout -- backend/`（`exit=0`）后 15 个条目全部消失，**工作区总数由 31 → 16**；清理后 `git diff --stat` 仍为 `5 files changed, 122 insertions(+), 78 deletions(-)`（**内容零丢失**）。登记见 I198 §D-3.2 D-3-30 行。现在真正有内容变更的已跟踪文件为 **5 个**（5 个 audit 文档，含 `issue-register.md`），与 16 项中「已修改 5 + 已暂存新增 11」完全对账。

**`git diff --stat`（已跟踪文件）**：`5 files changed, 122 insertions(+), 78 deletions(-)`（指 5 个 audit 文档，含本轮写入的 `issue-register.md`；15 个 backend 文件原为 stat 脏、无内容差异，**已于 2026-11-19 23:4x 清理**，清理前后该数值不变）。

**`git diff --cached --stat`（已暂存新增，2026-11-19 23:4x 实测）**：`11 files changed, 1713 insertions(+)`（F173 证据目录 7 个 + F171 `raw/13` / `raw/14` 2 个 + I198 文档 446 行 + 本报告 318 行）。

> 📌 **`issue-register.md` 本轮改动点 = 5 处**（此前口头/行文曾写成「4 处」，已更正）：① **L3** 头部「最近更新」时间线压单段并追加本轮条目；② **L110** I183 追加实测复核说明（16:44:52 SQL / 16:40:06–16:40:14 HTTP + 实测命令）；③ **L111** I184 追加实测复核说明（同上）；④ **L121** I194 追加「运行期证据依赖临时授权，需重跑」；⑤ **L125** 新增 I198 登记单行（296 字符，单行无跨行）。
> 对应 `git diff --stat -- audit/issues/issue-register.md` = **9 行变更 = 5 增 4 删**（L3 / I183 / I184 / I194 为「1 删 1 增」各一，L125 为纯增 1 行）。

> 📌 **口径澄清（易误读点）**：① I186 第 1 批的**已提交**代码变更规模为 `git show --stat c37a549` = **20 files changed, 608 insertions(+), 88 deletions(-)**；② 本节 **16 项**是**当前工作区**状态（`git status --porcelain` 条目数 = 5 已跟踪修改 + 11 已暂存新增 + 0 未跟踪），**不是** `git diff --stat` 的文件数（后者仅 5 个已跟踪 audit 文档）；③ 原 15 个 backend 条目**无内容差异**（stat 脏），**已于 2026-11-19 23:4x 清理**（见上方 ✅ 与 I198 D-3-30 行）；④ **「23」从来不是 I186 第 1 批代码改动的口径** —— 报告中出现过的「23」是 §7 **工作区条目数**的旧值（19 已跟踪 + 4 未跟踪），已由 D-3-14 更正为 25、D-3-17 更正为 26、D-3-26（删除 `_diffstat.txt`）回到 25、D-3-27（`git add` 后）到 31、**再由 D-3-30（清理 backend stat 脏）到 16**（**16 是当前实测值**；26 / 25 / 31 均为中间快照）；**I186 第 1 批代码改动的唯一口径是 `git show --stat c37a549` = 20 files / +608 / −88**，该口径在 §0 第 1 行、§1.2 L45-L59、§7 本段三处**始终保留完好、未被覆盖**。**五个口径互不等价，不可混用。**

---

## 8. 问题清单（按优先级）

| P | 问题 | 归属 |
|---|---|---|
| **P0** | **I186 第 1 批运行期验收证据不可复现**（11 处 200→403）→ 必须重跑 | I198 §3-1 |
| **P0** | **11-17 02:00 备份被夹具污染**，不可回滚/恢复 | I198 §B |
| **P0** | **I183 / I184 未修** —— 考勤 9 码 / 提成 24 码仍零绑定，是本批不可复现的**根本前置** | I183 / I184 |
| **P1** | **I194 未落地** —— 前端仍显式传 `storeId: 1`，本批修复在真实链路上不可达 | I194（紧随本批） |
| **P1** | **验证脚本盲区** —— `catch` 分支丢弃 HTTP 状态码（已致 `raw/02` 误判）；主验证 V13 行未逐端点标状态码 | I198 §3-6 |
| **P1** | **证据链仍有版本管理缺口** —— F173 目录**已入 git**（2026-11-19 23:4x），但 `.audit-tmp/*`（`verify-result.txt` / `post-cleanup-recheck.ps1` / `binlog/*`）**仍未跟踪**（该路径被 `.git/info/exclude:9` 忽略），克隆仓库后**仍无法复核** §2 的 `settlement/export` bytes=3522 与 §4.1 时间轴原始依据 | I198 §D-12 / §5.1 A~C |
| **P1** | **11-16 23:34:53 临时授权来源未确认**（MySQL 命令行客户端，仓库内无脚本） | I198 §C |
| **P2** | **清理脚本缺 role 5 总行数（基线 278）断言** | I198 §3-5 |
| **P2** | **备份链路无污染检测** | I198 §3-7 |
| **P2** | **夹具账号密码为 `123456`** 且无 `deleted=1` / 租户隔离标记 | I198 §3-4 |

---

## 9. 重跑前置条件（全部满足方可重跑）

1. **I183 / I184 权限码绑定修复完成**（否则受限端点依旧 403，验证无意义）；
2. **避开每日 02:00 定时备份窗口**；
3. **先建立夹具账号与授权的登记台账**（谁、何时、给哪个角色哪些码、何时撤销），脚本与 SQL 一并入证据目录；
4. 验证脚本**记录每个端点的 HTTP 状态码 + 响应体**，断言层不得把 `null` 当 `0`；
5. 夹具账号使用一次性随机密码 + `deleted=1` 或专用租户隔离标记；
6. 补齐清理脚本的 `sys_role_permission` 总行数（基线 1164 / role 5 = 278）断言。

---

## 10. 边界声明

- 本报告为**汇总与再组织**，**未新增任何运行期探测**；§2 / §3 的数值全部转录自 F173 证据目录的既有落盘文件，§4 的数值转录自 `raw/03`，§4.2 的备份统计为**本轮 2026-11-18 对备份文件的只读文本解析**（不连库、不写库）。
- 本轮**未修改任何业务代码**、**未写库**、**未执行 DDL**、**未做任何 DELETE/UPDATE**；§6 的 `docker ps` / `docker logs` 均为只读命令。
- **未打开浏览器**、**未连接生产库**、**未替代人工验收** → §5 中前端链路（#17）、浏览器表现（#18）、生产等价性（#19）均标 **UNVERIFIED**。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面及其数据接口。
- 治理文件（`docs/governance/*.md`、`audit/baseline/*.md`、`PROJECT_ACCEPTANCE_EVIDENCE.md`、`.gitignore`）本轮**未写入**；`audit/issues/issue-register.md` 本轮**已写入**（**2026-11-18 23:1x 写入，待用户复核** —— 该治理保护文件未经用户逐字确认即落盘，属流程偏差，已登记 I198 §D-3-18）。

---

**结论一句话**：**I186 第 1 批代码已落地，但运行期 PASS 依赖一条已被撤销的临时授权、清理后 11 处 200→403 不可复现 → 本批尚未取得可复现的运行期验收证据；且 11-17 02:00 的定时备份已被夹具污染、不可回滚/恢复。**
