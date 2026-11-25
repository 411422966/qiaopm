# I205 — `/commission/rule-list` 首屏无 loading 守卫 + 错误被吞为空态（前端设计缺陷）

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P3**
> **P3 理由**：最终数据一致（`tableData` 会在响应到达后被覆盖），**不影响数据正确性**，仅影响首屏体验与「加载中 / 无数据 / 加载失败」三态不可区分；无权限绕过、无数据泄露、无写操作风险
> **发现日期**：2026-11-25（I202 §7.2 批次派生）
> **原文件名**：`I205-commission-rule-list-async-race.md`（2026-11-25 改名为 `I205-commission-rule-list-no-loading-guard.md`）—— 初版按初步假设写作「异步竞态」，静态定位收敛后结论为「**首帧必然空态 + 失败被吞 → 设计缺陷，非竞态**」，故按结论改名（教训见 I202 §6.9）
> **登记人**：Cursor
> **绑定基线**：HEAD `89b63f2`（`docs(audit): 补 manager01 考勤/提成页复核 + I202 状态措辞收窄 + 流程偏差登记`）
> **验证等级**：**UNVERIFIED（静态 L4）** —— 本轮 CDP 调试未收敛（未取得可判定的运行期对照），按用户指示**降级为静态定位**落盘；现象层仅有一份非受控快照观察（见 §1.2）。**已确认部分**（静态可判定，不依赖运行期证据）：① 首帧必然空态；② catch 仅 `console.error` → 失败与无数据同形；③ 「权限 store 未就绪」不成立；④ `await import` 冗余。**未确认部分**：「首次进入 vs 刷新后」的差异归因（H1–H3）
> **关联**：**I202 §7.2**（发现处 —— `manager01` 补测提成页时观察到）、`audit/issues/I202-frontend-permission-code-drift-full.md` §7.2 第 6 项

---

## 0. 一句话描述

`frontend/meiyetec-web/src/views/commission/rule-list.vue` 的空态判定 `v-if="!tableData.length"` **没有 loading 守卫**，
而 `tableData` 的初始值就是空数组、且真实数据要等到 `onMounted → loadData` 走完一次「动态 import + HTTP 往返」后才回填 ——
因此**组件一挂载就先渲染「暂无提成规则」**，请求返回后才被规则卡替换；
用户观察到的「首次进入空态、刷新后正常」**静态上无法归因到权限 store 未就绪**（已排除，见 §2.3）。

**结论定性（静态可判定）**：本条**不是偶发竞态，而是设计缺陷** —— ① 空态判定无 loading 守卫（F1+F2+F3，必然渲染）；② 失败分支仅 `console.error`，**加载失败与无数据同形**（F6，**已确认事实**，见 §2.4）。剩余未确认项只影响「用户首次进入 vs 刷新后为何表现不同」的**感知差异归因**（见 §2.5）。

---

## 1. 现象

### 1.1 用户报告

- 进入 `/commission/rule-list`（提成规则列表页）时，页面先渲染 Element Plus 空态 **「暂无提成规则」**；
- 刷新同一页面后表现正常（能看到规则卡）。

### 1.2 已有的非受控观察（**不足以判 VERIFIED**）

上一轮浏览器快照中曾观察到：**进入 `/commission/rule-list` 后首帧呈「暂无提成规则」，数秒后规则卡出现**（「试算」按钮可见）
→ 即该空态**至少在观察时是瞬时的、可自愈的**。

⚠ **该观察不构成受控对照**：① 未做「冷缓存首次进入 vs 刷新」的成对采样；② 未记录空态持续时长与 `GET /api/commission/rules/list` 的时间线；
③ 未区分「数据到达后替换」与「请求失败后永久空置」两种成因 → **现象层部分成立、根因层 UNVERIFIED**。

---

## 2. 静态定位

### 2.1 页面代码事实（逐行核对）

| # | 位置 | 代码事实 | 推论 |
|---|---|---|---|
| F1 | `views/commission/rule-list.vue:54` | `<el-empty v-if="!tableData.length" description="暂无提成规则" />` | **空态判定只看数据长度，不看加载态** → 只要 `tableData` 为空就渲染空态 |
| F2 | `views/commission/rule-list.vue:85` | `const tableData = ref<any[]>([])` | 初始值即空数组 → 组件挂载瞬间 F1 条件**必然成立** |
| F3 | `views/commission/rule-list.vue:127-129` | `onMounted(() => { loadData() })` | 数据加载在**挂载之后**才发起 → 挂载帧与数据到达之间存在空窗 |
| F4 | `views/commission/rule-list.vue:96-104` | `loadData`：`await import('@/api/request')` → `await request.get('/commission/rules/list')` → `tableData.value = res.data \|\| []` | 数据回填**只发生在响应到达后**；期间 `tableData` 恒为 `[]` |
| F5 | `views/commission/rule-list.vue:98` | `const request = (await import('@/api/request')).default` | `loadData` 的**首个语句是一次动态 import** → 请求发出前多一个 chunk / 模块解析边界 |
| F6 | `views/commission/rule-list.vue:101-103` | `catch (e) { console.error('加载失败', e) }` —— **catch 块内仅此一条 `console.error`，无 `ElMessage`、无错误态变量、无重试、无 `loading` 复位** | **已确认事实**：失败被完全吞掉 → 「加载失败」与「真的无数据」在页面上**完全同形**，用户与排查者**均无法区分** |
| F7 | `views/commission/rule-list.vue:83` + `api/commission-rule.ts:1` | 页面静态 `import { commissionRuleApi } from '@/api/commission-rule'`，而该模块第 1 行已 `import request from './request'` | `request.ts` **早已在静态依赖图内** → F5 的动态 import **无收益**，只增加一个 chunk 边界与一个 await 边界 |
| F8 | `api/request.ts:50` | 拦截器内 `const token = localStorage.getItem('token')` | 请求鉴权**只读 localStorage 的 token**，**不读 permission store** |
| F9 | `api/request.ts:8` / `:99` | `baseURL: '/api'`；响应拦截器 `return res`（返回信封 `response.data`） | 页面取 `res.data` 的写法与信封语义一致，**不存在取错层导致空数组**的契约漂移 |
| F10 | `api/request.ts:143-145` | 403 / 500 / 网络错 → `ElMessage.error(msg)`（非 `silent` 请求） | 请求失败时**全局有 toast**；但页面自身仍停在「暂无提成规则」（F6），二者并存 |

### 2.2 初始化与请求的时序（谁先谁后）

| 步骤 | 触发点 | 是否 `await` | 对首屏的影响 |
|---|---|---|---|
| ① | `router/index.ts:206` `beforeEach` 开始；`:210` 读 `localStorage.token` | — | — |
| ② | `router/index.ts:222` `usePermissionStore()` —— **首次调用即触发 store `state` 工厂**：`stores/permission.ts:49` `loadCache()`、`:54` 同步恢复 `permissions`、`:56` `loaded: !!cached` | 否（**同步**） | 权限数据在守卫阶段即**同步可用**，守卫**不需要**等待网络即可判定 |
| ③ | `router/index.ts:223-225` 若 `!store.loaded`（`rbac.session.v1` 缓存缺失）→ `await store.refresh()`（`stores/permission.ts:92` → `:107` `_doRefresh` → HTTP `rbacApi.permissions()`） | **是** | 缓存缺失时**路由整体挂起**，用户此时看到的是旧页 / 白屏 |
| ④ | `router/index.ts:229` 校验 `commission:rule:list` → `next()` | — | 通过后路由确认 |
| ⑤ | 守卫链结束 → 组件 `commission/rule-list.vue` 创建 → 挂载 → **`onMounted`**（`:127`） | — | **此时 `tableData` 仍是初始 `[]`（F2）** |
| ⑥ | `onMounted` → `loadData()`（**未 await**，`:128`） | — | 请求发起 |
| ⑦ | `loadData` 首个语句 `await import('@/api/request')`（F5） | **是** | GET 尚未发出；冷缓存下这里还要等 chunk 下载 |
| ⑧ | `await request.get('/commission/rules/list')`（`:99`）→ 拦截器 `baseURL='/api'` → `GET /api/commission/rules/list` | **是** | 网络往返期间页面停在空态 |
| ⑨ | 响应 → `:100` `tableData.value = res.data \|\| []` | — | 空态被规则卡替换 |

**结论（结构性、静态可确认）**：⑤→⑨ 之间模板 `:54` 的空态**必然渲染**（F1 + F2 + F3）——
即「首帧假空态」是**必然**而非偶发，**是否存在 loading 守卫决定了它可见多久**。

### 2.3 假设排除：权限 store 未就绪 → **不成立**

用户给出的定位方向之一是「权限 store 未就绪」。静态核对结论：**该方向可排除**。

| 依据 | 说明 |
|---|---|
| `stores/permission.ts:49 / :54 / :56` | 权限集合在 store `state` 工厂里**同步**从 `localStorage['rbac.session.v1']`（`:13` CACHE_KEY）恢复，守卫阶段即就绪；缺失时守卫会 `await store.refresh()`（`router/index.ts:223-225`）**并在刷新完成后才放行** |
| `api/request.ts:50` | 请求鉴权**只读 localStorage 的 token**，与 permission store **无数据依赖** |
| `views/commission/rule-list.vue` 全文 | 页面**未 import、未读取** permission store（无 `usePermissionStore` 引用） |

→ 不存在「store 未就绪 → 请求被跳过 / 被拒 → 页面永远空」这条链路。**唯一**与 store 相关的时序效应是 ③：缓存缺失时守卫多一次 HTTP 往返，**推迟整页出现**，属感知时长问题，不改变空态语义。

### 2.4 H3 提级：catch 吞错 —— **由「假设」升为「已确认事实」**

**提级依据**（静态代码级核对，无需运行期证据）：

| 项 | 核对结果 |
|---|---|
| catch 块位置 | `views/commission/rule-list.vue:101-103` |
| catch 块**全部**内容 | 仅 `console.error('加载失败', e)` —— **无 `ElMessage`、无错误态变量、无 `loading = false` 复位、无重试、无空态降级提示** |
| 同函数对照 | 同文件 `:122-124` 的 `doSimulate` catch 是 `ElMessage.error('计算失败')` → **同一文件内两个 catch 处理方式不一致**，证明 `:101-103` 的吞错是**遗漏而非约定** |
| 页面级错误态 | 全文**无** `error`/`failed` 状态变量、**无**错误态模板分支 → 失败后页面仍是「暂无提成规则」 |
| 全局提示 | `api/request.ts:143-145` 对 403/500/网络错有 `ElMessage.error`（非 silent）→ **有 toast，但不指向页面状态**，且 4xx/5xx 与「返回空数组 `res.data || []`（`:100`）」两种失败在页面上仍同形 |

**已确认结论（可作为结论引用）**：

> **catch 块仅 `console.error`，导致加载失败与无数据同形；用户与排查者均无法区分。**

**影响面提示**：**7 个无 loading 守卫页面若同样吞错，则属同型问题** —— 静态逐页初筛结果见 §2.6（**需逐页确认 catch 块**）：本轮已初筛 7 页，**4 个确认同型**（`rule-list.vue` / `care-overview.vue` / `RfmHistoryChart.vue` / `dashboard.vue`），3 个非吞错形态；`dashboard.vue` 属已验证页面仅登记不处理。

### 2.5 剩余假设（**均未确认**，需受控复现；仅解释「首次进入 vs 刷新后」的感知差异）

| # | 假设 | 机制 | 为什么能解释「首次进入空 / 刷新后正常」 | 证伪/证实方法 |
|---|---|---|---|---|
| H1 | **感知时机差异**（最可能） | 首屏刷新时，JS chunk + 守卫 ③ 期间用户看到白屏，数据到达后内容直接呈现；SPA 路由跳转进入时，旧页面已渲染，新组件挂载即刻把空态叠上去 → 空态更显眼 | 不需要请求行为差异，纯粹是「空态被画在可见页面上」的时长 | 成对采样：冷缓存首次进入 vs 刷新，记录空态可见时长 |
| H2 | **动态 import 的冷热 chunk 差异** | F5 的 `await import('@/api/request')` 在冷缓存下需先取 chunk，GET 发出被推迟 → 空窗更长；刷新后 chunk 已在 HTTP 缓存 → 空窗缩短 | 解释了「同一页面两次进入表现不同」 | 核对构建产物的 chunk 划分 + Network 面板时间线 |
| H3 | **守卫刷新拉长首次进入总时长** | ③：`rbac.session.v1` 缓存缺失 → 守卫多一次 HTTP → 首次进入整体变慢 | 只解释「首次进入更慢」，不解释「空态内容不同」 | 清缓存后首次进入计时 |

> **原 H3「失败被吞 → 永久空态」已提级为已确认事实**，移入 §2.4（catch 块内容静态可核对，无需运行期证据）。
> 剩余 H1–H3 **均未确认**，且**仅解释「首次进入 vs 刷新后」的感知差异**，不影响「首帧必然空态」与「失败被吞」这两条已成立的结论。

### 2.6 同型风险面（静态扫描，**已逐页初筛 catch 块**）

| 扫描项 | 结果 |
|---|---|
| `views/**` 中使用 `el-empty` 但**无任何 loading 守卫**（无 `v-loading` / `:loading` / `const loading`）的页面 | **7 个**：`cohort/index.vue`、`commission/rule-list.vue`、`crm/care-overview.vue`、`dashboard.vue`、`intel/rfm/ResultPanel.vue`、`intel/rfm/RfmHistoryChart.vue`、`spa-scene/index.vue` |
| 同域对照（提成模块内已正确处理的页面） | `commission/records.vue:48` `v-if="!loading && rows.length === 0"`；`commission/salary.vue:56` 同款；`commission/rules.vue:37` `v-loading="loading"` → **`rule-list.vue` 是提成模块内唯一带 `el-empty` 且无 loading 守卫的页面** |
| `await import('@/api/request')` 动态导入写法 | **13 处 / 7 文件**（`commission/rule-list.vue`、`commission/rules.vue`×3、`commission/simulation.vue`×2、`crm/care-suggestion.vue`、`material/binding.vue`×2、`material/expiry-tracking.vue`×2、`material/statistics.vue`） |

> ⚠ `dashboard.vue` 属**用户已验证页面**（`/dashboard`），按项目规则**不得主动修改**，此处**仅登记不处理**。

**7 个同型候选页面的 catch 块初筛**（逐页 `catch` / `console.error` / `ElMessage.error` 检索，2026-11-25）：

| 页面 | catch 块位置与内容 | 是否「吞错」 | 判定 |
|---|---|---|---|
| `commission/rule-list.vue` | `:101-103` `catch (e) { console.error('加载失败', e) }` | **是** | ✅ **同型确认**（本 Issue 主体） |
| `crm/care-overview.vue` | `:523` `catch (e: any) { console.warn('[care-overview] loadOverview failed:', e?.message) }` | **是**（`console.warn` 而非 `error`，级别更弱） | ✅ **同型确认** |
| `intel/rfm/RfmHistoryChart.vue` | `:51` `} catch {`（**空 catch，无任何输出**） | **是**（静默丢弃，最严重） | ✅ **同型确认** |
| `dashboard.vue` | `:238` `:248` `:257` `:267` `:282` `} catch {`（**空 catch**）；`:321` `catch (e) { console.warn('[dashboard chart]', e) }` | **是**（5 处空 catch） | ⚠ **同型确认，但属用户已验证页面 `/dashboard` —— 仅登记，禁止改动** |
| `cohort/index.vue` | 无 catch 块；`loadDefs` 等（`:78`/`:80`/`:85`/`:93`/`:137`）为裸 async，异常走未捕获 Promise rejection | **否**（未捕获 ≠ 吞错，用户能看到控制台报错） | ⚠ 非同型（失败形态不同） |
| `spa-scene/index.vue` | 仅 `:365-370` 删除确认流程的 `catch { /* cancelled */ }`（取消分支，非加载失败）；`loadScenes`/`loadRooms` 等（`:319`/`:324`/`:329`/`:334`）无 catch | **否**（同上） | ⚠ 非同型（失败形态不同） |
| `intel/rfm/ResultPanel.vue` | 全文无 `catch` / 无 `await` / 无 `onMounted` —— 纯 props 渲染组件 | **不适用** | ⚠ 非同型（无数据加载） |

> **结论**：7 个候选中 **4 个确认同型**（`rule-list.vue` / `care-overview.vue` / `RfmHistoryChart.vue` / `dashboard.vue`），其中 `dashboard.vue` 属已验证页面仅登记。
> 另 3 个（`cohort/index.vue` / `spa-scene/index.vue` / `ResultPanel.vue`）**非吞错形态**，但仍缺 loading 守卫 —— 属**首帧空态**问题而非错误可观测性问题，本 Issue 不覆盖。

### 2.7 已知冗余登记：`await import('@/api/request')`

**13 处 / 7 文件**（`commission/rule-list.vue`、`commission/rules.vue`×3、`commission/simulation.vue`×2、`crm/care-suggestion.vue`、`material/binding.vue`×2、`material/expiry-tracking.vue`×2、`material/statistics.vue`）使用了 `await import('@/api/request')` 动态导入写法，
但 `request.ts` **早已在静态依赖图内**（`rule-list.vue:83` → `commission-rule.ts:1` → `import request from './request'`）→ **动态写法无收益**，只额外引入一个 chunk 边界与一个 `await` 边界（并拉长首帧空窗，见 F5 / H2）。

→ **属已知冗余**，不单独开 Issue；建议在后续治理批次中统一改为顶层静态 `import`（本轮**未改任何代码**）。

---

## 3. 影响

| 维度 | 判定 |
|---|---|
| 数据正确性 | ✅ 不受影响 —— 数据最终一致（`:100`），无脏写、无重复提交 |
| 首屏体验 | ⚠ **受影响** —— 「暂无提成规则」在数据到达前必然出现，无 loading 指示，用户易误判为「该门店没有提成规则」 |
| 错误可观测性 | ⚠ **受影响（已确认事实）** —— F6 catch 仅 `console.error`，**加载失败与无数据同形，用户与排查者均无法区分**；F10 的全局 toast 是唯一提示，且不指向页面状态 |
| 权限 / 数据泄露 | ✅ 不受影响 —— 后端 `CommissionController.java:100-101` 的 `GET /commission/rules/list` 有 `@RequirePermission("commission:rule:list")` 兜底 |
| 波及范围 | ⚠ **已逐页初筛**（§2.6）：7 个同型候选中 **4 个确认吞错同型**（`rule-list.vue` / `care-overview.vue` / `RfmHistoryChart.vue` / `dashboard.vue`），**3 个非吞错形态**（`cohort/index.vue` / `spa-scene/index.vue` / `ResultPanel.vue`，仅缺 loading 守卫）；`dashboard.vue` 属已验证页面，**不得改动** |

**规则 10 单列标记**：本条含**「错误被伪装为空态」**（F6 / F10）—— 属独立于「首屏无 loading 守卫」的问题类别，修复时**不可只加 loading 守卫而保留吞错的 catch**。

---

## 4. 建议修复方向（**本轮未改任何代码**）

| # | 建议 | 依据 |
|---|---|---|
| 4.1 | 引入 `loading` 状态量，`rule-list.vue:54` 改为 `v-if="!loading && !tableData.length"`，并在 `el-card` 上加 `v-loading` | 与同域 `records.vue:48` / `salary.vue:56` / `rules.vue:37` 的既有写法对齐 |
| 4.2 | `catch`（`:101-103`）改为：保留错误态变量 + `ElMessage.error('加载失败')` + 页面渲染**独立的失败态**，与空态区分 | F6（已确认事实）/ F10；对应 §3 的规则 10 标记 |
| 4.3 | 把 `:98` 的动态 import 换成顶层静态 `import request from '@/api/request'` | F7 / §2.7 —— 模块本就静态在依赖图内，动态写法无收益，只多一个 await 边界 |
| 4.4 | 若受控复现确认首帧假空态仍明显，可在布局层加骨架屏 | H1 / H2（均未确认） |
| 4.5 | §2.6 中确认同型的 3 个非已验证页面（`crm/care-overview.vue` / `intel/rfm/RfmHistoryChart.vue` / `commission/rule-list.vue`）**另立专项复核**；`dashboard.vue` 属已验证页面**仅登记** | §2.6 逐页初筛表 |
| 4.6 | 13 处 `await import('@/api/request')` 可在后续治理批次统一改为顶层静态 import | §2.7 已知冗余登记 |

---

## 5. 边界声明

- 本条**仅登记，不修** —— **未修改任何前端代码 / 后端代码**，未写库、未执行 DDL、未新增入库脚本、**未新增可执行脚本**。
- **未执行任何写操作**（未新增 / 编辑 / 删除提成规则）。
- 本轮 CDP 调试**未收敛**（未取得可判定的运行期对照）→ 按用户指示**降级为静态定位**；本文所有结论**均为静态源码级核对**，标注为 **UNVERIFIED**。
- §1.2 的浏览器观察为**非受控、单次**，**不作为 VERIFIED 依据**；§2.5 的 H1–H3 **全部未确认**。
- **已确认事实**（静态可判定，不依赖运行期证据）：① 首帧空态必然渲染（F1+F2+F3）；② catch 仅 `console.error`，加载失败与无数据同形（F6 / §2.4）；③ 「权限 store 未就绪」不成立（§2.3）；④ `await import('@/api/request')` 属已知冗余（§2.7）。
- §2.6 的同型页面 catch 块结论为**静态源码级初筛**，**未做运行期复现**，**不构成对这些页面的缺陷判定**（尤其 `dashboard.vue` 仅登记）。
- **未触达** `/dashboard`、`/mh/visual`、`/mh/consent` 三个用户已验证页面（本条仅**登记** `dashboard.vue` 的同型风险，**未做任何改动**）。
- 本条**未修改** `audit/issues/issue-register.md`（治理保护文件，规则 18）—— 登记待用户单独批准。
