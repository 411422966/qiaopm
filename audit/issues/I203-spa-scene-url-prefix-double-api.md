# I203 — `/spa-scene` 页 7 处接口调用 URL 双 `/api` 前缀 → 页面数据全部 404（含超管）

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P2**（页面数据不可用；门禁层**将由 I202 P1 修复**（当前 P1 未落地，`/spa-scene` 守卫码 `biz:room` 仍恒 false）→ 不再是永久 403 的阻断型缺陷，故不升 P0/P1）
> **发现日期**：2026-11-24（I202 权限码全量复扫过程中发现）
> **登记人**：Cursor
> **绑定基线**：HEAD `ac5fdbe`
> **验证等级**：**VERIFIED（运行期 L5a + 静态 L4）** —— 前端源码逐行核对 + 经 nginx 的三账号对照实测
> **DB 快照**：不涉及（本条**不依赖 DB**，纯 URL 拼接与反向代理路径问题；`sys_permission` 相关事实见 I202）
> **环境**：`audit/baseline/env.md`（前端容器 `meiyetec-web` → nginx `:8088`；后端 `:8080`）
> **关联**：**I202**（发现处，§7 R8 / §6 教训 4）、**I202 R2-1**（`spa:scene:list` 绑 0 的 P2 补绑定，**必须与本条同批落地**）、I185（前后端码漂移）
> **来源**：I202 调查过程中的衍生发现

---

## 0. 一句话描述

`views/spa-scene/index.vue` 中 **7 处**接口调用写成 `http.get('/api/spa-scene/...')`，而 `http` 实例（`utils/http.ts` → `api/request.ts`）的 axios `baseURL` 已是 `'/api'` → axios 拼接为 `/api/api/spa-scene/...` → nginx `location /api/ { proxy_pass http://api:8080/; }` **只剥一层** `/api` → 后端收到 `/api/spa-scene/list` → **404**。
**结果是：即使后端 `spa:scene:list` 补上角色绑定，页面也仍然拿不到任何数据。**

---

## 1. 事实核实

### 1.1 调用侧：8 个 `http.*` 调用，其中 7 个带多余前缀

文件：`frontend/meiyetec-web/src/views/spa-scene/index.vue`（该目录内唯一文件）

| 行号 | 方法 | 实际写法 | 拼接后 URL | 判定 |
|---|---|---|---|---|
| `:320` | GET | `http.get('/api/spa-scene/list')` | `/api/api/spa-scene/list` | ❌ 双前缀 |
| `:325` | GET | `http.get('/room/list', { params: { status: 1 } })` | `/api/room/list` | ✅ **正确**（对照组） |
| `:330` | GET | `http.get(\`/api/spa-scene/${id}/recommend\`)` | `/api/api/spa-scene/{id}/recommend` | ❌ 双前缀 |
| `:336` | GET | `http.get(\`/api/spa-scene/${slotSceneId.value}/slots\`)` | `/api/api/spa-scene/{id}/slots` | ❌ 双前缀 |
| `:354` | PUT | `http.put(\`/api/spa-scene/${e.id}\`, e)` | `/api/api/spa-scene/{id}` | ❌ 双前缀 |
| `:357` | POST | `http.post('/api/spa-scene', e)` | `/api/api/spa-scene` | ❌ 双前缀 |
| `:367` | DELETE | `http.delete(\`/api/spa-scene/${scene.id}\`)` | `/api/api/spa-scene/{id}` | ❌ 双前缀 |
| `:396` | PUT | `http.put(\`/api/spa-scene/${slotSceneId.value}/slots\`, slots.value)` | `/api/api/spa-scene/{id}/slots` | ❌ 双前缀 |

> 该文件**唯一**的网络调用来源是 `import http from '@/utils/http'`（`:245`）；`utils/http.ts` 内容为 `import request from '@/api/request'; const http = request; export default http` → **与 `api/request.ts` 同一 axios 实例**。
> 拼接规则实测（Node + 仓库 `node_modules/axios`）：`axios.create({baseURL:'/api'}).getUri({url:'/api/spa-scene/list'})` → **`/api/api/spa-scene/list`**。
> **反引号模板字符串写法**（`:330` / `:336` / `:354` / `:367` / `:396` 共 5 处）与单引号写法（`:320` / `:357` 共 2 处）**同样命中** `baseURL` 拼接 → 排查正则必须同时覆盖两种引号。

### 1.2 代理侧：两层代理各只剥一层 `/api`

| 环节 | 位置 | 规则 | 对 `/api/api/spa-scene/list` 的处理 |
|---|---|---|---|
| 生产 nginx | `frontend/meiyetec-web/nginx.conf:41-42` | `location /api/ { proxy_pass http://api:8080/; }` | 剥掉**一层** `/api/` → 后端收到 `/api/spa-scene/list` |
| Vite dev proxy | `frontend/meiyetec-web/vite.config.ts:21` | `rewrite: (path) => path.replace(/^\/api/, '')` | 剥掉**一层** `/api` → 后端收到 `/api/spa-scene/list` |
| 后端 | `backend/src/main/resources/application.yml:60-62` | `server.port: 8080`，**无 `context-path`** | `/api/spa-scene/list` **无对应路由 → 404** |

> 后端真实路径：`SpaSceneModeController.java:15` `@RequestMapping("/spa-scene")` → 端点为 `/spa-scene/list` 等，**不带 `/api` 前缀**。

### 1.3 运行期实测（2026-11-24，经 nginx `:8088`，三账号对照）

| 账号 | `/api/spa-scene/list` | `/api/api/spa-scene/list`（前端真实路径） | `/api/room/list`（正确写法对照） |
|---|---|---|---|
| admin（超管，持 `*`，权限码 418） | **200** | **404** | **200** |
| manager01（304 码） | **403** | **404** | **200** |
| clerk01（54 码） | **403** | **404** | **200** |

**决定性对照**：同一账号、同一时刻，**唯一区别是调用侧有没有多写一层 `/api`** → `/room/list` 200 / `/spa-scene/list` 404。
另：**超管（持 `*`）走双前缀路径同样 404** → 证明这不是权限拦截，而是**路径不存在**。

> ⚠️ **探测口径说明**：以上 404 为**绕过前端守卫的直接探测**（直接打 HTTP 路径，未经过路由守卫）；页面当前实际表现为**守卫 403 无法进入**（守卫码 `biz:room` 恒 false，见 §2），**I202 P1 落地后**才会暴露为**页面内 404**。即：本条缺陷在当前基线下被守卫层「遮住」，不会在页面上可见。

---

## 2. 影响面

| 维度 | 事实 |
|---|---|
| 页面可达性 | **不可达**（守卫码 `biz:room` → `room:list`，id=1460，绑 6 角色，含 CLERK/MANAGER；**当前 P1 未落地 → 守卫码仍恒 false → 页面进不去**；I202 P1 落地后才可达） |
| 页面数据 | **完全不可用** → 场景列表、推荐方案、房间组合、增删改**全部失败** |
| 失败模式 | **静默**：`loadScenes()` 等函数**无 try/catch**（`:319-322`、`:329-332`、`:334-338`）→ 异常向上冒泡至 `onMounted` 回调 → **控制台报错、页面空白表格**，无用户可见提示 |
| 超管是否可绕过 | **不可**（`/api/api/*` 后端无路由，超管也 404） |
| 与 P2 补绑定的关系 | **必须先修本条**，再补 `spa:scene:list`(1564) / `spa:scene:edit`(1565) 的角色绑定；**只补绑定不修前缀 → 页面仍然 404** |

---

## 3. 修复方向

**范围：1 个文件，7 行**（`frontend/meiyetec-web/src/views/spa-scene/index.vue`）—— 去掉调用侧多余的 `/api` 前缀，与同文件 `:325` 的正确写法对齐。

| 行 | 现状 | 修复后 |
|---|---|---|
| `:320` | `http.get('/api/spa-scene/list')` | `http.get('/spa-scene/list')` |
| `:330` | `http.get(\`/api/spa-scene/${id}/recommend\`)` | `http.get(\`/spa-scene/${id}/recommend\`)` |
| `:336` | `http.get(\`/api/spa-scene/${slotSceneId.value}/slots\`)` | `http.get(\`/spa-scene/${slotSceneId.value}/slots\`)` |
| `:354` | `http.put(\`/api/spa-scene/${e.id}\`, e)` | `http.put(\`/spa-scene/${e.id}\`, e)` |
| `:357` | `http.post('/api/spa-scene', e)` | `http.post('/spa-scene', e)` |
| `:367` | `http.delete(\`/api/spa-scene/${scene.id}\`)` | `http.delete(\`/spa-scene/${scene.id}\`)` |
| `:396` | `http.put(\`/api/spa-scene/${slotSceneId.value}/slots\`, slots.value)` | `http.put(\`/spa-scene/${slotSceneId.value}/slots\`, slots.value)` |

**后端不动**（`SpaSceneModeController` 的 `@RequestMapping("/spa-scene")` 是对的）。

**同批必须一起做（否则无效）**：I202 §7 R2-1 的 P2 补绑定（`spa:scene:list` / `spa:scene:edit` 各绑 3–6 角色）。
**顺序**：先修前缀（前端 1 文件 7 行）→ 再补绑定（DB）→ 页面才真正可用。

### 3.1 同类问题的排查建议（防复发）

全仓 `src/` 扫描调用侧多余的 `/api` 前缀，**必须同时覆盖单引号与反引号模板字符串**两种写法：

```
(http|request)\.(get|post|put|delete)\(\s*['`]\/api\/
```

> 该正则同时命中单引号（`'/api/...'`）与反引号（`` `/api/...` ``）两种字面量写法，**覆盖本条 7 处全部命中点**（`:320` / `:357` 为单引号，`:330` / `:336` / `:354` / `:367` / `:396` 为反引号）。

**本轮实际排查已覆盖模板字符串**（`:330` / `:336` / `:354` / `:367` / `:396` 为反引号写法，已全部识别并列入修复清单），**但验收标准第 7 条的正则需同步覆盖，否则守门脚本会漏**。

全仓 `src/` 扫描命中：

| 文件:行 | 判定 |
|---|---|
| `views/spa-scene/index.vue`（7 处） | ❌ **本条缺陷** |
| `api/rfm.ts:255-256` `axios.get('/api/intel/rfm/...')` | ✅ 正确 —— 走**裸 axios**（`api/rfm.ts:2` `import axios from 'axios'`，非 `request` 实例），无 `baseURL` 拼接 |
| `api/request.ts:27` `fetch('/api/auth/refresh')` | ✅ 正确 —— 走**原生 fetch**，不走 axios |
| `views/system/setting.vue:41` `action="/api/upload"` | ✅ 正确 —— **el-upload 表单 action**，非 axios |

→ **全仓仅 `spa-scene/index.vue` 一处命中**，修复面为 1 文件 7 行。

> ⚠️ 关于裸 axios 写法的误报：该正则**不会**命中 `api/rfm.ts:255` —— 因 `axios` 不在 `(http|request)` 捕获组内（`axios.get(` 不匹配 `(http|request)\.`），且裸 axios 无 `baseURL` 拼接 → 行为正确。反之若未来有人把该处改用 `request.get('/api/...')` 而非裸 axios，则**会命中** → 正确拦截。

---

## 4. 验收标准

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `views/spa-scene/index.vue` 全文检索 `'/api/` | **0 命中** |
| 2 | 浏览器 Network 面板观察 `/spa-scene` 页请求 | URL 为 `/api/spa-scene/list`（**单前缀**），非 `/api/api/...` |
| 3 | 超管打开 `/spa-scene` 页 | 场景列表**正常渲染**（当前为空白 + 控制台 404） |
| 4 | manager01 打开 `/spa-scene` 页（**在 I203 前缀修复 + R2-1 补绑定都落地后**） | 场景列表正常渲染（仅补绑定未修前缀 → 仍 404，属预期；仅修前缀未补绑定 → 仍 403，属预期） |
| 5 | 新增 / 编辑 / 删除场景 | 请求打到 `/api/spa-scene*`（单前缀），后端不再 404 |
| 6 | 保存房间组合 | `PUT /api/spa-scene/{id}/slots` 成功 |
| 7 | 仓库守门脚本新增一条静态检查 | 全仓 `src/**` 内不得出现 `(http\|request)\.(get\|post\|put\|delete)\(\s*['\`]\/api\/` 组合（裸 axios / 原生 fetch / el-upload action 除外） |

---

## 5. 边界声明

- 本条结论来自**前端源码逐行核对** + **经 nginx 的三账号运行期对照实测**，**未打开浏览器**、**未做前端交互验收**。
- **未修改任何业务代码**、**未写库**、**未新增可执行脚本**（实测均为一次性内联命令）。
- 「页面数据恢复可用」属**代码级推论**（前缀去掉后 URL 与 `:325` 已验证可行的 `/api/room/list` 同构）→ 按验收规则标 **UNVERIFIED**，需人工在浏览器复核。
- 本条**不涉及 DB**，因此无 `sys_permission` 绑定变更；角色绑定属 I202 §7 R2-1 的 P2 范畴。
- 未核实：是否有其他部署形态（静态托管 / CDN 回源）走了不同前缀规则。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。
