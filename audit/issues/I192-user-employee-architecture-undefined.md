# I192 — `sys_user` 与 `pos_employee` 无任何关联，「员工是否登录系统」属业务架构未定义

> **状态**：**Open（待产品决策）**（本批**仅登记**，**未修改任何业务代码、未写库、未执行 DDL**）
> **严重度**：**P1**（不是「代码写错了」，而是**架构前提缺失** —— 两条身份体系并行且不相交，导致「本人操作」这一类需求在本仓**无法实现**，且 M1（阶段 2 员工↔用户映射）**没有可用的自动映射键**）
> **发现日期**：2026-11-16（batch2-C 第四轮：M1 映射键只读核验之后，结论从「映射键待定」升级为「架构未定义」）
> **登记人**：Cursor
> **绑定基线**：HEAD `7ad1380`（`docs(audit): M1 映射键只读查询结果`）
> **验证等级**：**静态 VERIFIED（L4）** + **只读数据核验 VERIFIED** —— 5 个候选映射键全部 0 交集、两表均无关联列、认证链路只走 `sys_user`（代码可核 + 数据可核）；**生产库未连接** → 生产存量分布标 **UNVERIFIED**
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/10-m1-mapping-key-phone.out.txt`、`raw/11-m1-mapping-key-alternatives.out.txt`、**`raw/12-i192-i193-followup.out.txt`**（K/L/M 段：关联列存在性查询为空结果 + `sys_user` 全量））
> **关联**：**I186**（§X.4 N11 = M1 前置事实；M3 本批先行）、**I189**（「本人撤回请假」依赖本条）、**I191**（请假落库，其 `employeeId` 语义同样受影响）、**I146**（C 端 token 方案）、I183（考勤权限码死码）
> **用户决策（2026-11-16）**：M1 暂缓；本条单独立项登记，**待产品三选一**（见 §3）

---

## 0. 一句话描述

系统里有**两套互不相交的身份体系**：
`sys_user`（**后台管理账号**，5 行：`admin` / `clerk01` / `manager01` / `boss01` / `operator01`）与
`pos_employee`（**门店员工花名册**，150 行：`EMP001~006` + 144 条 `UT3-*` 测试员工）；
两表**没有 `employee_id` / `user_id` 之类的关联列**，5 个候选映射键（手机号 / 姓名 / 用户名 / 工号 / 模糊匹配）**交集全部为 0 行**；
→ **「员工到底要不要登录系统」这件事，在本仓从未被定义过** —— 它不是某个字段填错，而是**一条缺失的产品决策**。

---

## 1. 事实核实

### 1.1 两表的规模与定位（只读核验）

| 表 | 行数（`deleted=0`） | 含义 | 样例 |
|---|---|---|---|
| `sys_user` | **5** | 后台**管理账号** | `admin`（系统管理员）、`clerk01`（测试店员）、`manager01`（测试店长）、`boss01`（测试老板）、`operator01`（测试运营） |
| `pos_employee` | **150** | 门店**员工花名册** | `EMP001~006`（王雪梅 / 赵晓彤 / 孙丽华 / 周敏 / 张丽 / 李娜，租户 1 门店 1）+ 144 条 `UT3-*`（`UT-3 测试员工 …`，租户 NULL 门店 1） |

`sys_user` 5 个账号的 `real_name` 全部是**角色化标签**（系统管理员 / 测试店员 / 测试店长 / 测试老板 / 测试运营），`phone` 为 `13800138000` 与 `13800000001~4`；
`pos_employee` 的 `employee_name` 为**中文人名**，`phone` 为 `13900139001~6` 与 `139000<hex>` 形态 —— **两表的命名与取值体系完全不属于同一套造数逻辑**。

### 1.2 候选映射键核验：5 条路径全部 0 交集

| # | 候选键 | 查询 | 结果 | 证据 |
|---|---|---|---|---|
| 1 | `sys_user.phone = pos_employee.phone` | 等值 JOIN | **0 行**（`matched_rows=0 / distinct_users=0 / distinct_employees=0`；5 个用户**全部命中 0 条**员工） | `raw/10` §2/§5/§6/§7 |
| 2 | `(tenant_id, phone)` 复合口径 | 等值 JOIN | **0 行** | `raw/11` §I |
| 3 | `sys_user.real_name = pos_employee.employee_name` | 等值 JOIN | **0 行** | `raw/11` §C |
| 4 | `sys_user.username = pos_employee.employee_code` | 等值 JOIN | **0 行**（`code_eq_username=0`，150 行员工全部无对应用户名） | `raw/10` §10 / `raw/11` §D |
| 5 | `real_name LIKE %employee_name%` | 模糊 JOIN | **0 行** | `raw/11` §E |

补充事实：两表 `phone` **各自内部唯一**（`sys_user` 5/5、`pos_employee` 150/150 无重复）→ 交集为 0 **不是**因为存在重复值歧义，而是**两套值域完全不相交**。

### 1.3 无关联列（DDL 级确认）

| 查询 | 结果 |
|---|---|
| `information_schema.columns`：`sys_user` 中列名含 `employee%` / `staff%` / `pos%` | **空结果** |
| `information_schema.columns`：`pos_employee` 中列名含 `user%` / `account%` / `login%` | **空结果** |

代码侧同源确认：`entity/SysUser.java` 字段全集为 `id / username / password / realName / phone / email / avatar / status / storeId / tenantId / createTime / updateTime / deleted` → **无 `employeeId`**；`entity/Employee.java` 字段全集含 `employeeCode / employeeName / phone / password / storeId / roleIds / tenantId / employeeLevel …` → **无 `userId`**。

### 1.4 值域形态对照（决定性）

| 来源 | 手机号形态 | 语义 |
|---|---|---|
| `sys_user` | `13800138000` / `13800000001~4` | 合成测试号（`1380000xxxx` 为脚本连号） |
| `pos_employee`（真实员工） | `13900139001~6` | 合成测试号（`13900139xxx` 连号） |
| `pos_employee`（UT 残留） | `13900018e70` / `139000f5943` … | 10 位 hex 形态 → **脚本随机生成** |

→ 三个号段**互不相交**，说明两张表**分别由不同的造数脚本生成**，从未共享同一「自然人」实体。

### 1.5 认证链路事实：只有 `sys_user` 能登录

| 环节 | 位置 | 事实 |
|---|---|---|
| 登录入口 | `service/AuthService.java:36-37` `login(username, password)` → `userMapper.getUserByUsername(username)` | **只查 `sys_user`** |
| 口令校验 | `AuthService.java:51` `passwordEncoder.matches(password, user.getPassword())`（BCrypt） | — |
| `pos_employee.password` 列 | `entity/Employee.java` 有该字段；全仓 `findstr` 检索 `Employee::getPassword` / `employee/login` / `employeeLogin` | **0 命中** → 该列**没有任何调用方** |

→ 员工表**自带密码列却无登录链路**；系统当前的「谁在操作」一律是 `sys_user` 账号。

### 1.6 因此可确定的事实边界

✅ **可确定**：本仓**不存在**任何「登录用户 ↔ 员工」的映射机制；
✅ **可确定**：I186 的 **M1**（`sys_user.employee_id` 显式列 + 回填）**必须先做 DDL**，且**无法自动回填**（无键可自动匹配）；
✅ **不可确定（需产品）**：「员工**应当**能否登录系统」—— 本仓既没有实现，也没有文档、注释或种子数据暗示答案。

---

## 2. 影响

| # | 受影响项 | 影响 |
|---|---|---|
| I1 | **I189「本人撤回请假」** | `PUT /attendance/leave/{id}/cancel` 的「无权取消他人申请」校验依赖**请求体传入的 `employeeId`** 与记录比对（`LeaveRequestServiceImpl.java:84`）——**这不是「校验本人」，而是「校验参数相等」**。要改成真正的「本人校验」，必须知道「当前登录用户 = 哪个员工」→ **依赖本条的产品答案**（选项 A 下该端点应改为「店长代撤回」，无需映射；选项 B/C 下才需要映射） |
| I2 | **I186 M1（阶段 2）** | M1 的全部技术前提是「`sys_user` ↔ `pos_employee` 有稳定映射」→ **本条未决前，M1 无法开工**（即使 DDL 加了列，**填什么值也只有产品知道**） |
| I3 | **I186 M3（阶段 1，本批）** | M3 = `pos_employee.tenant_id = ctx.tenantId AND pos_employee.store_id IN ctx.storeIds` → **只依赖员工表的门店/租户维度，不依赖 `sys_user` 映射** → **本条不阻塞 M3**（M3 另受 **I193** 阻塞，见该条） |
| I4 | **I191（请假落库）** | 请假单的 `employee_id` 由**请求体**指定（`LeaveRequestServiceImpl.submit`），审批后写入的考勤记录也挂在该 `employee_id` 上 → 若「员工不登录系统」（选项 A），则「谁来提交请假」本身就是未定义语义 |
| I5 | **I146（C 端匿名化）** | 若最终走向选项 C（员工走微信小程序登录、不走 `sys_user`），则 C 端 token 方案需要**新增第三套身份体系**（会员 / 员工 / 后台账号），与 I146 现有「会员 C 端 token」设计不兼容 → 需在 I146 拍板前确认 |
| I6 | **全局** | 「考勤打卡」目前是**任何人传任意 `employeeId` 都能替人打卡**（I186 N7）；在「员工不登录」的架构下，这个洞**无法用身份映射堵死**，只能靠「店长代打卡」之类的产品口径收口 |

---

## 3. 待产品确认（三选一）

| 选项 | 产品口径 | 直接后果 | 连带影响 |
|---|---|---|---|
| **A** | **员工不登录系统**（只有店长 / 管理员登录系统管考勤） | **M1 不需要做**；`sys_user` 与 `pos_employee` 继续保持两套平行体系 | ① **I189 改为「店长代撤回」**（`attendance:leave:cancel` 独立权限码 + 门店归属校验，不再谈「本人」）；② 打卡 / 请假改为「店长代操作」语义，需产品给出代操作入口与留痕要求；③ I146 不受影响；④ I191 的 `employee_id` 语义 = 「被代办的员工」，需在落库口径中体现 |
| **B** | **员工要登录系统**（走 `sys_user`） | **M1 必须做**：新增 `sys_user.employee_id` 列 + 回填 | 需产品指定：**5 个测试账号（或未来的 N 个账号）分别对应哪 5 个员工**；需确认「一个员工是否可多账号 / 一个账号是否可多员工」；需确认员工角色的权限码从哪来（现有 9 个考勤码目前 0 绑定，见 I183） |
| **C** | **员工用微信小程序登录**（**不走** `sys_user`） | **M1 不在 `sys_user` 上做**，改为第三套身份体系（员工小程序 openid ↔ `pos_employee`） | ① I146 的 C 端 token 方案需扩展为「会员端 / 员工端」两种主体；② 需要新的登录入口与凭证（openid 绑定表）；③ 与 EXT-19 的 `overseas_booking.mini_program_open_id` 属同一套小程序能力，需统一设计 |

> 📌 **决策建议（技术视角，不替业务拍板）**：**选项 A 的实现成本与风险最低**（不引入第三套身份、不引入回填脏数据、直接把「本人」语义改成「授权代办」语义），且与当前「`sys_user` = 后台管理账号」的实际形态一致；若产品确有「员工自助打卡」诉求，再走选项 B 并**严格限定首批账号范围**（5 个账号 ↔ 5 个员工，人工指定）。
> 无论选 A / B / C，**「代操作留痕」**（谁替谁操作）都是必须同时确定的一项 —— 否则 I186 N7 的伪造问题只是换了个名字。

---

## 4. 修复方案（各选项下的技术路径，**本批不实施**）

### 4.1 选项 A（员工不登录）

| 步骤 | 内容 | 涉及 |
|---|---|---|
| A1 | `attendance:leave:cancel` **独立权限码**（替代 I189 中复用 `attendance:leave:approve` 的做法），并在 Service 层加「该请假单的 `store_id ∈ ctx.storeIds`」校验 | I189（等本条拍板后一并做） |
| A2 | 打卡 / 请假提交端点明确为「**代操作**」：请求体中的 `employeeId` 必须属于 `ctx.storeIds` 内的员工（M3 口径）→ 把「替任意人打卡」收敛为「替本门店员工打卡」 | I186 第 1 批（M3） |
| A3 | 补一条**代操作留痕**字段（`attendance_record.operator_user_id` 或 `operator_name`）→ 否则事后无法区分「员工自己打的卡」与「店长代打卡」 | 新增 DDL，需产品确认是否必需 |
| A4 | I191 的请假落库同步带留痕 | 与 I191 合并 |

### 4.2 选项 B（员工登录 `sys_user`）

| 步骤 | 内容 | 备注 |
|---|---|---|
| B1 | **DDL**：`sys_user` 新增 `employee_id BIGINT NULL` + 索引 | ⚠️ MySQL 8.0.29+ 才能用 `ADD COLUMN IF NOT EXISTS`（I130 同族约束），低版本先 `SHOW COLUMNS` |
| B2 | **人工回填**：由产品给出「账号 ↔ 员工」对照表 → 一次性 `UPDATE` 或走管理界面 | ⚠️ 无自动匹配键（§1.2 已证 5 条路径全 0），**必须人工** |
| B3 | **归属校验**：M1 = `sys_user.employee_id → pos_employee.tenant_id = ctx.tenantId AND store_id IN ctx.storeIds` → 「同店他人」越权被堵死 | M1 上线后 I186 的「同店他人可查」缺口关闭 |
| B4 | 员工角色的**权限码绑定**必须与 I183 同批，否则员工登录后**什么也看不到** | 与 I183 / I184 的补绑定批次合并 |
| B5 | 回归：M1 落地后，M3 降级为「兜底校验」（防止映射缺失时漏判） | — |

### 4.3 选项 C（员工小程序登录）

| 步骤 | 内容 | 备注 |
|---|---|---|
| C1 | 新增员工身份表（如 `pos_employee_login`，`openid` ↔ `employee_id` ↔ `tenant_id`） | 与会员侧 `crm_member` / `wx_member_bind` 保持物理隔离 |
| C2 | 新增员工端登录端点 + token 主体类型（`MEMBER` / `EMPLOYEE` / `STAFF`） | I146 的 C 端 token 方案需扩展（见 I5） |
| C3 | 考勤 / 请假的「当前员工」从 token 主体取，不再信任请求体 `employeeId` | 一并关闭 I186 N7 |
| C4 | 与 EXT-19 `mini_program_open_id` 的设计对齐（同一套小程序 openid 体系） | — |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析**（`SysUser.java` / `Employee.java` / `AuthService.java` / `DataScopeInterceptor`）+ **本机开发库只读查询**（`raw/10`、`raw/11`、`raw/12`，**全程仅 SELECT / SHOW，无任何写操作、无 DDL**）。
- ⚠️ **生产库未连接** → 生产的 `sys_user` / `pos_employee` 规模与映射关系标 **UNVERIFIED**；本条的 0 交集结论仅对本机库成立。
- ⚠️ 「员工是否应当登录系统」属**产品定义**，本 Issue **不预设答案**，只给三条选项与各自的技术后果。
- ⚠️ §1.5「`pos_employee.password` 无调用方」结论基于全仓 `findstr` 检索 `Employee::getPassword` / `employee/login` / `employeeLogin` **0 命中**；若存在通过 Mapper XML 或反射使用该列的路径，本条该分句需修正（**静态检索层面**）。
- 兜底 ID 说明：本条**未做任何按 ID 的写探测**，**未使用任何真实业务主键**作为「不存在」样本。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
- **本批未修改任何业务代码**。

---

**下一步（待产品拍板）**：

1. 产品就 §3 的 **A / B / C** 三选一给出结论；
2. 结论为 A → **I189 改为「店长代撤回」**并与 I186 第 1 批（M3）合并实施；结论为 B → 启动 **M1**（DDL + 人工回填清单 + 与 I183 绑定批次合并）；结论为 C → 转入 **I146** 的 token 主体扩展设计；
3. 无论哪条结论，**「代操作留痕」**需一并拍板（是否新增 `operator_user_id` 字段）。
