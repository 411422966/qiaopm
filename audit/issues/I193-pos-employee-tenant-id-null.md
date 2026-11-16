# I193 — `pos_employee` 144/150 存活行 `tenant_id` 为 NULL，M3 双过滤口径的硬前置

> **状态**：**Open（待业务确认）**（本批**仅登记**，**未修改任何业务代码、未写库、未执行 DDL**）
> **严重度**：**P1**（`tenant_id` 是员工表的**租户归属列**，96% 存活行为 NULL → 任何「`tenant_id = ctx.tenantId`」口径都会把绝大多数员工行判为「不在本租户」）
> **发现日期**：2026-11-16（batch2-C 第三轮 I186 §X.4 S6 首次发现 → 第四轮补充核验并精确定性）
> **登记人**：Cursor
> **绑定基线**：HEAD `7ad1380`
> **验证等级**：**只读数据核验 VERIFIED**（分布、样本、引用关系均已用只读 SQL 核实）—— **但结论仅对本机开发库成立**，**生产库未连接** → 生产存量分布标 **UNVERIFIED**
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/11-m1-mapping-key-alternatives.out.txt` §H、`raw/04c-db-store-employee-distribution.txt`、**`raw/12-i192-i193-followup.out.txt`**（A/B/C/D/E/F/G/H/I/J 段））
> **关联**：**I186**（§X.4 S6 / M3 判定口径 / G5 员工归属校验；本条是 **M3 的实施前置**）、**I192**（员工身份体系未定义）、I183（考勤权限码死码）、I191（请假落库，其 `employee_id` 指向本表）
> **用户决策（2026-11-16）**：M3 暂缓，先登记本条并解决「144 行 `tenant_id` 该填什么 / 是否为脏数据」

---

## 0. 一句话描述

`pos_employee` 的**存活行（`deleted=0`）共 150 行，其中 144 行 `tenant_id IS NULL`**（全部 `store_id = 1`），只有 6 行 `tenant_id = 1`；
而 I186 的 M3 判定口径是 **`tenant_id = ctx.tenantId` AND `store_id IN ctx.storeIds`** → 对 `tenant_id IS NULL` 的行**恒为 FALSE** → **必须先决定这批行的归属口径**，否则 M3 一落地就把它们全判为「越权」。

> ⚠️ **本轮对「96% 是谁」的精确定性**（原始登记口径的修正）：
> 这 144 行**不是普通员工的漏填租户**，而是 **UT-3 压测脚本残留**（`employee_code` 形如 `UT3-A-854130`、姓名 `UT-3 测试员工 …`、手机号 `139000<10 位 hex>`、`salary` 一律 `5000.00`、`entry_date` 全 NULL、`create_time` 集中在 **2026-08-15 06:42:55 ~ 06:58:29** 共 16 分钟内批量生成）。
> **本机真实存活员工只有 6 行**（`EMP001~EMP006`，`tenant_id=1`、`store_id=1`）→ **M3 双过滤对真实员工无影响**；
> 但**生产库分布未核实**，且「把压测残留原样回填 `tenant_id=1`」反而会把假员工转正 → **方案排序需要调整**（见 §3）。

---

## 1. 事实核实

### 1.1 分布（只读核验，`raw/12` A/B/C 段）

| `tenant_id` | `store_id` | `status` | `deleted` | 行数 | id 区间 | `employee_code LIKE 'UT%'` | `entry_date IS NULL` |
|---|---|---|---|---|---|---|---|
| **NULL** | 1 | 1 | **0（存活）** | **144** | 219–423 | **144（100%）** | **144（100%）** |
| 1 | 1 | 1 | 1（软删） | 14 | 582–627 | 0 | 14 |
| 1 | 1 | 2 | 1（软删） | 7 | 581–625 | 0 | 7 |
| 1 | 1 | 1 | **0（存活）** | **6** | 1–6 | 0 | 0 |

- 存活行合计 **150**；其中 `tenant_id IS NULL` **144 行（96%）**、`tenant_id = 1` **6 行（4%）**。
- **全部存活员工的 `store_id` 都是 1**（`raw/12` I 段：门店维度只有 `store_id=1` 一个分组，150 行）。
- 另有 **21 行已软删**（`tenant_id=1`，ids 581–627）—— 是压测/改数过程中的历史版本，不参与任何 `@TableLogic` 查询。

### 1.2 144 行的性质判定（`raw/12` D/E 段）

| 字段 | 观测值 | 判定 |
|---|---|---|
| `employee_code` | `UT3-A-854130` / `UT3-B-29c9d3` …（**全部 `UT3-` 前缀**） | 脚本生成 |
| `employee_name` | `UT-3 测试员工 UT3-A-854130` | 压测命名 |
| `phone` | `13900018e70` / `139000f5943` …（10 位 hex） | 脚本随机 |
| `salary` | 一律 `5000.00` | 脚本常量 |
| `entry_date` | 全部 NULL | 未赋值 |
| `create_time` | **全部落在 2026-08-15 06:42:55 ~ 06:58:29**（16 分钟） | 一次性批量生成 |

→ 结论：这 144 行是**一次压测（UT-3）的员工夹具残留**，**不是**「真实员工漏填租户」。

### 1.3 这 144 行没有被任何业务数据引用（`raw/12` G/H 段）

| 表 | 行数 |
|---|---|
| `commission_salary` | **0** |
| `commission_settlement` | **0** |
| `commission_settlement_record` | **0** |
| `attendance_record` | **0** |
| `attendance_leave_request` | **0** |
| `commission_record` | 10 行，`distinct employee_id = 1`，**引用的是 `employee_id = 4`**（真实员工 `EMP004`，`tenant_id=1`）；`ut_range_refs`（employee_id ∈ 219–423）= **0** |

→ **144 条 UT 员工是「孤儿行」**：没有任何考勤、提成、工资、结算数据挂在它们身上。

### 1.4 门店维度同样存在压测残留（`raw/12` J 段）

`pos_store` 共 6 行（ids `1 / 5 / 9 / 13 / 17 / 21`，`tenant_id` 全为 1），其中 **5 行门店名形如「软删除测…-1787645628107」**（时间戳后缀）→ **真实门店只有 1 个**（`美业SaaS示范…`，id=1）。

### 1.5 与 M3 口径的关系

M3 判定口径（I186 §X.4 S6，用户 2026-11-16 拍板）：**`pos_employee.tenant_id = ctx.tenantId` AND `pos_employee.store_id IN ctx.storeIds`**。

| 员工集合 | `tenant_id = 1` 判定 | M3 结果 |
|---|---|---|
| 6 行真实员工（`EMP001~006`，`tenant_id=1`、`store_id=1`） | TRUE | ✅ 正常纳入 |
| 144 行 UT 残留（`tenant_id=NULL`、`store_id=1`） | **FALSE**（SQL 三值逻辑：NULL = 1 → NULL → 非真） | ❌ 全部判为「不在本租户」 |

> 📌 **精确表述（与原始登记口径的差异）**：原始登记为「M3 双过滤会排除 96% 员工」——
> 在**本机开发库**上，这 96% 是**压测残留的假员工**，因此 M3 双过滤**对真实业务数据无损失**；
> 但① **生产库分布未核实**（生产若存在真实的 `tenant_id IS NULL` 员工行，M3 才会真正造成业务数据不可见）；
> ② 无论哪种情况，M3 的**口径必须显式写明「`tenant_id IS NULL` 行如何处理」**，否则同一段代码在不同环境行为不同。

---

## 2. 影响与风险

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **M3 在生产可能误判真实员工** | 生产库存在 `tenant_id IS NULL` 的真实员工行 | 打卡 / 请假 / 提成的员工维度校验会把他们判为越权 → **员工维度接口对绝大多数员工失效** |
| R2 | **反向风险：若放宽为「只判 `store_id`」** | 采用「方案 B」 | 跨租户同 `store_id` 的员工会被放行 → **跨租户越权**（I186 S6 的原始担忧） |
| R3 | **压测残留污染员工维度聚合** | 任何「按门店取员工」的查询（`CommissionController.generateSalary` `:277-280`、`CommissionSettlementService.generate` `:51-54` 均按 `store_id` 取 `status != 0` 的员工，**不过滤 `tenant_id`**） | 工资生成 / 结算生成会把 **144 个不存在的员工**算进去 → 生成 144 条幽灵工资行（当前 `commission_salary` 0 行，故尚未发生） |
| R4 | **环境间行为漂移** | 本机（真实员工 6 行）vs 生产（分布未知） | 同一套 M3 代码在两个环境表现不同 → **测试通过 ≠ 生产正确** |
| R5 | **误回填造成「假员工转正」** | 采用「方案 A」直接 `UPDATE pos_employee SET tenant_id = 1` | 144 条压测残留获得正式租户归属 → **此后所有按门店取员工的查询都会命中它们**（R3 从「潜在」变「必然」） |
| R6 | **`tenant_id` 列可空且无默认值** | DDL 层面 `pos_employee.tenant_id` 允许 NULL、无 DEFAULT | 任何新建员工路径若不显式写入租户 → **再产生一批 NULL 行**（本条修完仍会复发） |

---

## 3. 修复方案（**仅建议，本批不实施**）

> 📌 **与原始登记的口径修正**：原始登记给出 A / B / C 三案，其中「方案 A：批量 UPDATE 回填 `tenant_id = 1`」在本轮核验后**排序下调** —— 因为这 144 行已被确认为**压测残留**（R5：会把假员工转正并触发 R3）。
> **建议的新顺序：C（先定性清理）→ A（仅对「确认为真实员工」的行回填）→ B（永不采用）**。

| 方案 | 内容 | 评价 |
|---|---|---|
| **C（推荐先行）** | **先定性、再清理**：已核实这 144 行是 UT-3 压测残留且**无任何业务引用**（§1.2 / §1.3）→ 建议**软删除**（`UPDATE pos_employee SET deleted = 1 WHERE tenant_id IS NULL AND deleted = 0`）或按「压测夹具」口径归档；清理后 `pos_employee` 存活行 = 6（真实员工，全部 `tenant_id=1`）→ **M3 双过滤天然可用，无需回填** | ✅ 消除 R3 / R5；⚠️ 需用户批准写操作；⚠️ 必须在**隔离库或生产只读副本**先复核 |
| **A** | **仅对「确认为真实员工」的行回填**：`UPDATE pos_employee SET tenant_id = 1 WHERE tenant_id IS NULL AND employee_code NOT LIKE 'UT%' AND deleted = 0` | ⚠️ 本机该语句**命中 0 行**（144 行全是 `UT%`）→ 在本机等价于「无需回填」；生产若存在真实 NULL 行，这条才是正解 |
| **B（不推荐）** | M3 过滤条件退化为仅 `store_id IN ctx.storeIds` | ❌ 直接放弃跨租户隔离（I186 S6 的原始担忧），**不建议** |
| **D（补根因）** | **给 `tenant_id` 加约束/默认值**，堵住 R6 的复发：① 实体层强制写入 `tenant_id`（所有新建员工路径）；② DDL 层把 `pos_employee.tenant_id` 改为 `NOT NULL DEFAULT 0`（需先清 NULL）；③ 至少加一条「新增员工必须带租户」的**应用层校验** | ✅ 与 C / A 配套，避免修完再复发 |

### 3.1 执行前置（安全约束）

| # | 前置 | 说明 |
|---|---|---|
| 1 | **先跑只读复核**：`SELECT COUNT(*) FROM pos_employee WHERE tenant_id IS NULL AND deleted = 0;` 与 `SELECT employee_code, COUNT(*) FROM pos_employee WHERE tenant_id IS NULL AND deleted = 0 GROUP BY employee_code LIMIT 20;` | 确认目标行集合，再决定 C / A |
| 2 | **备份**：任何写操作前先对本机库做一次 dump（`backup/` 目录规范见 `audit/evidence/env.md`） | 遵循既有备份纪律 |
| 3 | **写操作分两步**：① 先 `UPDATE ... SET deleted = 1`（软删，可回滚）→ ② 观察 24h 业务无异常后再考虑物理清理 | 避免不可逆 |
| 4 | **生产库**：本条结论仅本机成立 → 生产必须**只读复核**后再决定 | 生产未连接 → UNVERIFIED |
| 5 | **M3 口径在代码中显式化**：即使做了回填，M3 的 SQL 仍应**显式写出 `tenant_id` 条件**（而不是依赖「数据都填好了」的假设），并在注释中标明「NULL 行视为越权」 | 可审计 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `SELECT COUNT(*) FROM pos_employee WHERE tenant_id IS NULL AND deleted = 0` | 清理/回填后为 **0**（或与「确认为真实员工且待回填」的剩余数一致，且有台账） |
| 2 | `SELECT COUNT(*) FROM pos_employee WHERE deleted = 0` | 与清理前口径一致说明（软删）或减少（物理清理），且**真实员工 6 行不受影响** |
| 3 | M3 判定：真实员工 `EMP001~006` | 6 行**全部**落在 `tenant_id = ctx.tenantId AND store_id IN ctx.storeIds` 内 → 打卡 / 请假 / 提成接口对其**不报错** |
| 4 | M3 判定：跨租户同 `store_id` 的员工 | **403**（双条件均生效，未退化为只判 `store_id`） |
| 5 | 工资生成 `POST /commission/salary/generate`（`store_id=1`） | 生成对象**只含 6 个真实员工**（修复前会含 150 个，其中 144 个为 UT 残留） |
| 6 | 结算生成 `POST /commission/settlement/generate` | 同上，结算单 `employee_count` 与真实员工数一致 |
| 7 | 新建员工路径（若有） | `tenant_id` **必填且等于当前租户** → 不再产生 NULL 行（R6） |

---

## 5. 边界声明

- 本 Issue 结论来自**只读 SQL 查询**（`raw/12` 全部 SELECT / SHOW，**无任何写操作、无 DDL、无 UPDATE**）+ 静态代码核对（`CommissionController.java:277-280`、`CommissionSettlementService.java:51-54` 的员工查询**只按 `store_id`、不过滤 `tenant_id`**）。
- ⚠️ **生产库未连接** → 生产的 `tenant_id` 分布 **UNVERIFIED**；本条的「144 行是 UT-3 压测残留」结论**仅对本机开发库成立**。
- ⚠️ 「144 行是压测残留」是基于**字段形态 + 创建时间集中度 + 无业务引用**的三重推断，**未找到生成它们的脚本文件**（脚本可能不在仓内）→ 标 **静态 VERIFIED（数据形态层面）**，脚本溯源 **UNVERIFIED**。
- ⚠️ `raw/04c` 与 `raw/11` 的分布口径（含 `deleted=0` 过滤）与本条一致；`raw/11` §H 未带 `deleted` 列展示时呈现为「NULL 144 / 1 → 6」，本条补齐 `deleted` 维度后口径不变（144 行确为**存活**行）。
- 兜底 ID 说明：本条**未做任何按 ID 的写探测**，**未使用任何真实业务主键**作为「不存在」样本。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
- **本批未修改任何业务代码、未执行任何写操作**。

---

**下一步（待业务确认）**：

1. 业务确认：**这 144 行可以按「压测残留」清理/归档吗？**（本机已核实为 UT-3 残留且无业务引用；生产需先只读复核）
2. 确认后执行 **方案 C**（软删优先）→ 再执行 **方案 D**（堵住 `tenant_id` 可空的复发）；
3. **M3 的落地时机**改为「C 或 A 完成之后」，以保证 `tenant_id` 口径在所有环境一致。
