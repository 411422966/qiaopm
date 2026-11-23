# I201 — `DataScopeInterceptor` 只采集 Header / Query / Body，不采集 URL 路径变量 → 主键型端点（以 `GET /store/{id}` 为例）完全绕过数据范围校验

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**（越权读取；本机因数据单租户 / 主键不存在 → 返回 null，不可观测泄露内容，**属"潜伏式"高危**）
> **发现日期**：2026-11-23（I194-0 复核时与 I200 同轮实测发现）
> **登记人**：Cursor
> **绑定基线**：HEAD `1b617e2`
> **验证等级**：**静态 VERIFIED**（拦截器源码逐行核对）+ **运行期机制 VERIFIED**（决定性对照：query 参数 403 vs 路径变量 200）
> **关联**：**I200**（同一处暴露、修复面不同）、**I186**（数据范围统一入口）、**I117**（datascope 覆盖缺口）
> **来源**：I194-0 调查报告 §4.3「I200-2.6-D」

---

## 0. 一句话描述

`DataScopeInterceptor` 的 `storeId` / `tenantId` 采集源为 **Header → Query → Form → JSON Body** 四类，**不含 URL 路径变量**（`@PathVariable`）→ 所有 **`/{id}` 形态的主键型端点**（`GET/PUT/DELETE /store/{id}`、`GET/PUT /attendance/{id}`、`GET /attendance/config/{id}` 等）**永远不会被拦截器判定越权** —— 拦截器对这些端点**等于不存在**。

---

## 1. 事实核实

### 1.1 拦截器采集源清单（`security/DataScopeInterceptor.java`）

| 优先级 | 采集源 | 实现位置 |
|---|---|---|
| 1 | Header `X-Store-Id` / `X-Tenant-Id` | `extractStoreId` L97-111 / `extractTenantId` L78-95 |
| 2 | Query `?storeId=` / `?store_id=` / `?tenantId=` | `extractStoreId` L104-108 / `extractTenantId` L86-91 |
| 3 | JSON Body 正则匹配 | `matchLongFromBody` L118-133（仅 `Content-Type` 含 `json` 且 body ≤ 256KB） |

**不采集**：URL 路径模板变量（`HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`）—— **源码中无任何相关引用**。

> 📌 **补充（2026-11-23 核对）**：`extractTenantId` 的三级采集为 Header（L79-84）→ Query（L86-91）→ JSON Body（L94）；`extractStoreId` 为 Header（L98-103）→ Query（L104-108）→ JSON Body（L110）。`Form` 表单在源码层由 `req.getParameter(...)` 覆盖（Servlet 层面 form 参数也进 `getParameter`），但**与 Query 复用同一分支**，非独立采集。

### 1.2 运行期决定性对照（2026-11-23 16:49，同一 token、同一账号）

| 探测 | storeId 出现位置 | 结果 | 判读 |
|---|---|---|---|
| `GET /attendance/records?storeId=99999999` | **Query** | **403**「无权访问该门店数据」 | Query 采集 → 拦截器生效 ✅ |
| `GET /store/99999999` | **Path** | **200**，`data:null` | Path 不采集 → 拦截器**未参与** ❌ |

> 同一个 `99999999`（确认不存在的兜底 ID），**仅因参数位置不同就从 403 变 200** → 拦截器盲区**运行期坐实**，非推断。

### 1.3 受影响端点（主键型，`@PathVariable Long id`）

全仓 `controller/` 目录 `@PathVariable` 命中 **142 处**，其中 `/{id:\d+}` 形态的**读写端点**均落入盲区。典型：

| 路由 | 方法 | 文件:行 | 现状 |
|---|---|---|---|
| `/store/{id:\d+}` | GET / PUT / DELETE | `StoreController.java:97/112/121` | **无租户校验**（另见 **I200**） |
| `/attendance/{id:\d+}` | GET / PUT | `AttendanceController.java:117/125` | **有** `!tid().equals(r.getTenantId())` 判定 → **Service 层纵深防御**兜住（拦截器盲区未造成后果） |
| `/attendance/config/{id:\d+}` | GET / PUT / DELETE | `AttendanceConfigController.java:62/79/90` | **有** tenant 判定（同上） |
| `/commission/rules/{id}` / `/commission/salary/{id}` / `/commission/settlement/{id}` | GET / PUT / DELETE | `CommissionController.java:115/143/160/173/376/394/411/643/659/673` | **部分有** tenant 判定 |

**关键结论**：拦截器盲区**未**导致已确认的越权读取 —— 那些端点靠 Service/Controller 层的 `tenantId` 归属判定兜住了 —— 但这是**逐端点各自记得写**的偶然安全，**不是系统性保障**：一旦某个新端点（或 I200 的 `/store/{id}`）漏写归属判定，**没有任何机制会拦住它**。

> 📌 **补充（2026-11-23）**：当前未造成已确认的越权读取（`/attendance/{id}` 等靠 Service 层 tenant 判定兜住）；但 I200 的 `/store/{id}` 正是「**路径变量 + Service 无判定**」的组合，两者必须同批修复。

---

## 2. 修复方向建议（仅建议，未执行）

| 方案 | 内容 | 评价 |
|---|---|---|
| **A（推荐）** | `DataScopeInterceptor` 增加**路径变量采集**：从 `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE` 取 `storeId`（若路径中有名为 `storeId`/`store_id` 的变量）；**同时**保留 Service 层归属判定作为第二道闸 | 覆盖"路径变量语义就是 storeId"的端点；**不覆盖**"路径变量是 id、需再查归属"的端点 → 只能补一部分 |
| **B（推荐，与 A 并行）** | 明确**分层约定并写入治理文件**：① 拦截器负责**参数驱动**的范围校验（Query/Header/Body）；② **主键型端点必须在 Service 层做归属判定**（`tid().equals(entity.getTenantId())`）；③ 对**归属判定缺失**的主键型端点**逐个登记**并排期修复 | A 解决"显式 storeId 在路径里"的场景，B 兜住"路径是 id"的场景 —— **两者缺一不可** |
| C | 拦截器对**所有** `/{id}` 端点做"查库验证归属" | 需要拦截器知道每个端点对应的表与租户字段 → 需元数据注册机制，工作量与风险最大 |

**特别提示**：I200 的 `GET/PUT/DELETE /store/{id}` 正是 A 与 B 都覆盖不到的"**路径变量是 id、且 Service 层无归属判定**"的组合 → 修复时**A、B、I200 必须同批落地**，否则修了拦截器盲区仍然漏。

---

## 3. 验收标准（修复后）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `GET /store/99999999`（tenant 1 账号） | **403**（若走 A：路径变量被判越权）或 **404**（若走 B：Service 层归属判定不通过）—— **不得为 200** |
| 2 | `GET /attendance/records?storeId=99999999` | 仍 **403**（不得因现有 Query 路径行为） |
| 3 | `GET /attendance/{其他租户考勤记录id}` | 仍 **404**（Service 层判定未被 A 破坏） |
| 4 | `GET /attendance/config/{其他租户配置id}` | 仍 **404** |
| 5 | 任意主键型端点新增时未做归属判定 | 守门脚本能检出（**需新增检查项**，见 §5） |
| 6 | `GET /store/accessible` | 仅返回 `ctx.storeIds` 内门店（与 I200 同批验收） |

---

## 4. 未验证项 / 已知限制

| # | 未验证项 | 原因 |
|---|---|---|
| 1 | 跨租户**内容**泄露的运行期复现 | 本机无其他租户 `pos_store` / `attendance_record` / `pos_employee` 样本；`GET /store/99999999` 返回 `data:null` 只能证明"拦截器没拦"，**不能证明"读到了其他租户数据"** → **泄露内容维度标 UNVERIFIED** |
| 2 | 142 处 `@PathVariable` 中真正缺失归属判定的端点精确清单 | 本条只列了已核对的高题样本，**全量清单缺失** → UNVERIFIED |
| 3 | 拦截器对 `Form` 表单的采集是否真的可用 | 源码层级分支可读，但本轮未实测（端点均用 JSON） |
| 4 | `LogContextFilter` / `CachedBodyRequestWrapper` 是否对所有路径生效（body 采集依赖它） | 本轮未逐路径核对 |

---

## 5. 建议的自动化守门项（新增，待另行批准）

| 检查项 | 判定 |
|---|---|
| G-1 | 全仓扫描 `@PathVariable Long id` 形态的控制器方法 → 逐个核对所在方法体内是否包含 tenant 归属判定；缺失则报错 |
| G-2 | 全仓扫描 Controller 内 `service.getById(id)` / `removeById(id)` / `updateById(...)` 且**同方法内无 tenant 判定** → 报错 |
| G-3 | `DataScopeInterceptor` 采集源清单变更时，同步更新本 issue §1.1 |
