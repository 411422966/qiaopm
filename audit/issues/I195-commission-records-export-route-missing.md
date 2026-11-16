# I195 — 前端调用的 `/commission/records/export`、`/batch/settle`、`/batch/invalidate` 三个路由后端全部缺失

> **状态**：**Open（仅登记，不修）** —— 本条**独立批次**处理，I186 第 1 批**不包含**该修复
> **严重度**：**P2**（功能**必然失败**，不是「数据错」；但 3 个入口都是**用户主动点击**的按钮，失败可见、不产生数据污染）
> **发现日期**：2026-11-16（I186 第 1 批修复草案 §2.5 核对前端调用点时发现；同轮补充发现另外 2 个同族缺失路由）
> **登记人**：Cursor
> **绑定基线**：HEAD `d7e4a2c`（`docs(audit): I186 第 1 批修复草案（G0/G1/专项1/专项2/Q1）`）
> **验证等级**：**静态 VERIFIED**（前端 `api/commission.ts` 调用点 + 后端 `CommissionController` 路由清单逐条比对，**穷举**了 `/commission/records*` 全部后端路由）
> **关联**：**I152**（§A9 外溢清单，「幽灵功能」同类）、**I185**（前后端契约漂移）、**I186**（提成模块数据范围；本条的导出端点修复与 I186 第 1 批 Q1 耦合）
> **用户决策（2026-11-16）**：登记为独立 Issue；标题扩为「records 导出 / 批量结算 / 批量作废三个路由缺失」

---

## 0. 一句话描述

前端 `api/commission.ts` 封装了 **3 个** 提成记录相关接口，**后端 `CommissionController` 一个都没有** →
提成记录页的「导出」「批量结算」「批量作废」三个按钮**必然 404**，属于典型的**幽灵功能（契约漂移）**。

---

## 1. 事实核实

### 1.1 后端实际存在的 `/commission/records*` 路由（穷举）

`backend/src/main/java/com/meiyetec/controller/CommissionController.java`：

| 行 | 路由 | 方法 | 说明 |
|---|---|---|---|
| 212 | `/commission/records/page` | GET | ✅ 存在 |
| 247 | `/commission/records/stats` | GET | ✅ 存在 |

→ **后端只有这 2 个**。`CommissionController` 全文中 `records` 相关的 `@*Mapping` 仅此两处。

### 1.2 前端调用的 3 个缺失路由

`frontend/meiyetec-web/src/api/commission.ts`：

| 行 | 前端方法 | 请求 | 后端路由 | 状态 |
|---|---|---|---|---|
| 29 | `recordExport` | `GET /commission/records/export` | — | ❌ **缺失** |
| 30 | `recordBatchSettle` | `PUT /commission/records/batch/settle` | — | ❌ **缺失** |
| 31 | `recordBatchInvalidate` | `PUT /commission/records/batch/invalidate` | — | ❌ **缺失** |

### 1.3 页面实际调用点（按钮必然失败）

`frontend/meiyetec-web/src/views/commission/records.vue`：

| 行 | 交互 | 调用 | 用户可见结果 |
|---|---|---|---|
| 135 | 「导出」按钮 | `commissionApi.recordExport({ ...filter.value })` | 404；前端按 blob 解析 → **下载到一个装着错误 JSON 的空文件**或直接报错，**无明确提示** |
| 158 | 「批量结算」按钮 | `commissionApi.recordBatchSettle(ids)` | 404 → 「批量结算成功」**不会**出现（取决于前端错误处理） |
| 179 | 「批量作废」按钮 | `commissionApi.recordBatchInvalidate(ids)` | 404，同上 |

> ⚠️ **导出按钮的失败形态最隐蔽**：`recordExport` 声明了 `responseType: 'blob'`，后端 404 返回的 JSON 错误体会被当作 blob 存成本地文件 → 用户得到一个**打不开的 xlsx**（实际是 JSON 文本），而不是一个明确的错误提示。

### 1.4 对照：同模块内**存在**的导出端点（说明是遗漏而非设计）

| 端点 | 后端位置 | 前端调用 | 状态 |
|---|---|---|---|
| `GET /commission/salary/export` | `CommissionController.java:435` | `api/commission.ts` salaryExport | ✅ 存在（I186 第 1 批 Q1 已把它移出拦截器白名单） |
| `GET /commission/settlement/export` | `CommissionController.java:684` | `api/commission.ts` settlementExport | ✅ 存在（同上） |
| `GET /commission/records/export` | — | `api/commission.ts:29` | ❌ **缺失** |

→ **三个导出端点里唯独缺提成记录这一个** → 判定为**遗漏**，不是「有意不对外暴露」。

---

## 2. 修复方向

| 方案 | 做法 | 说明 |
|---|---|---|
| **A（推荐）** | **后端补 3 个端点** | 与 `/salary/export`、`/settlement/export` 对称；提成记录导出是既有产品需求（前端已写好导出 UI + 列映射） |
| **B** | **前端移除 3 个按钮** | 成本最低，但直接砍掉已实现的产品功能 |

### 2.1 若选方案 A，必须与 I186 第 1 批的 Q1 一起处理

`/commission/records/export` 是**裸 SQL + 导出**端点，与 `/commission/salary/export`、`/commission/settlement/export` 同族：

| 要求 | 说明 |
|---|---|
| 必须做**门店范围注入** | I186 第 1 批 G1 的 C8 / C11 已对 salary / settlement 导出做了集合过滤 → records 导出**必须同样处理**，否则等于新开一个「导出全租户」的洞 |
| 必须**移出拦截器白名单** | `DataScopeInterceptor.WHITELIST` 当前**不含** `/commission/records/export` → 新端点**不要**加入白名单（Q1 的结论） |
| 必须做**租户判空** | Q-H：裸 SQL 端点 `tenantId == null` → 400「未识别租户」 |
| 建议补**审计日志** | 导出是敏感操作，建议记录导出人 / 行数（`/report/export` 等目前均无 audit，见 I186 §6 N10 相关讨论） |

---

## 3. 与其他 Issue 的关系

| Issue | 关系 |
|---|---|
| **I152** | §A9「幽灵功能」外溢清单的同类（前端有 UI、后端无路由） |
| **I185** | 同属「前后端契约漂移」族 |
| **I186** | Q1 移除了 salary / settlement 导出的白名单豁免 → records 导出若补建，**必须带上门店范围过滤**，否则比现状更糟 |
| **I194** | 同在 `views/commission/` 目录；I194 修前端硬编码时，本条修后端路由 → 两条都落在提成模块，建议**同批次评审** |

---

## 4. 边界声明

- 本条**仅登记**：本轮**未修改任何前端代码、未修改任何后端代码、未写库、未执行 DDL**。
- 结论来自**穷举比对**（后端 `CommissionController` 的 `/commission/records*` 路由 vs 前端 `api/commission.ts` 的 3 个调用），**静态 VERIFIED**；**运行期 404 未实测**（需在浏览器点击复现，本轮**未打开浏览器**）。
- 兜底 ID 说明：本条**未做任何按 ID 的写探测**，**未使用任何真实业务主键**作为「不存在」样本。
- 本条**未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
