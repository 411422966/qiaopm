# I125 — Blockchain Record / Chain 菜单已上线但前端无路由（影子功能）

> 发现时间：2026-10-24
> 发现方式：浏览器实测 D2 分类（Playwright）+ DB 菜单绑定核验 + 后端端点核验
> 严重级别：**P1**（超级管理员可见菜单但点进去跳登录；后端能力完全可用）
> 状态：Open（未修复，仅诊断）
> 关联：I124（同批次发现）
> 基线：commit `77767e14c177a7512fc92ba6764e9d5427447969`（2026-10-23 23:38:44）

---

## 1. 一句话结论

DB 菜单表 `sys_menu_permission` 已绑定 `/blockchain/record` 与 `/blockchain/chain` 两条路由，
后端 `BlockchainController` 有完整对应端点（12 个），权限码 `blockchain:record` / `blockchain:chain`
**在 DB 中已有 ADMIN + SUPER_ADMIN 角色绑定**（是 I124 中少数"有绑定"的码），
但**前端 `router/index.ts` 从未注册这两条路由**，也没有对应视图文件。

→ 菜单渲染出来了，点击后 vue-router 无匹配 → 落到兜底路由 → 被守卫判定为未登录 → **跳 `/login`**。

---

## 2. 硬证据

### 2.1 DB 侧：菜单已绑定

```
$ mysql ... -e "SELECT mp.route_path, p.permission_code, p.permission_name
                 FROM sys_menu_permission mp JOIN sys_permission p ON p.id=mp.permission_id
                 WHERE mp.route_path LIKE '%blockchain%';"

route_path      permission_code   permission_name
/blockchain     blockchain         Blockchain
/blockchain/chain   blockchain:chain    Blockchain Chain
/blockchain/monitor blockchain:monitor  Blockchain Monitor
/blockchain/record  blockchain:record   Blockchain Record
/blockchain/trace   blockchain:trace    Blockchain Trace
```

5 条 blockchain 菜单全部已绑定，其中 2 条（`record` / `chain`）**没有前端路由**。

### 2.2 前端侧：只注册了 2 条

`frontend/meiyetec-web/src/router/index.ts` 中 blockchain 相关路由**仅 2 条**：

```
185: { path: 'blockchain/trace',   name: 'BlockchainTrace',   component: () => import('@/views/blockchain/trace.vue'),   meta: { ..., permission: 'blockchain:trace' } },
186: { path: 'blockchain/monitor', name: 'BlockchainMonitor', component: () => import('@/views/blockchain/monitor.vue'), meta: { ..., permission: 'blockchain:monitor' } }
```

`src/views/blockchain/` 目录只有 3 个文件：`monitor.vue`、`RecordForm.vue`、`trace.vue`
—— **无 `record.vue`、无 `chain.vue`**（`RecordForm.vue` 是表单组件，不是页面）。

### 2.3 后端侧：端点完整可用

`backend/src/main/java/com/meiyetec/controller/BlockchainController.java`（`@RequestMapping("/blockchain")`）：

| 行号 | 端点 | 权限码 |
|---|---|---|
| 26 | `POST /blockchain/record` | `blockchain:record` |
| 40 | `POST /blockchain/record/product` | `blockchain:record` |
| 53 | `POST /blockchain/record/drug` | `blockchain:record` |
| 66 | `POST /blockchain/record/device` | `blockchain:record` |
| 78 | `POST /blockchain/record/order` | `blockchain:record` |
| 89 | `POST /blockchain/record/consent` | `blockchain:record` |
| 101 | `GET /blockchain/record/{bizId}` | `blockchain:record` |
| 108 | `GET /blockchain/record/page` | `blockchain:record` |
| 117 | `GET /blockchain/chain/verify` | `blockchain:chain` |
| 123 | `GET /blockchain/chain/info` | `blockchain:chain` |
| 129 | `GET /blockchain/chain/overview` | `blockchain:chain` |
| 140 | `GET /blockchain/trace/{recordType}/{bizId}` | — |

前端 API 封装 `frontend/meiyetec-web/src/api/blockchain.ts` 也已写好
（`record` / `recordProduct` / `recordDrug` / `recordDevice` / `recordOrder` / `recordConsent` /
`getRecord` / `recordPage` / `verifyChain` / `chainInfo`）。

→ **API 层 100% 就绪，只缺"路由 + 视图"这一层接线。**

### 2.4 权限码有绑定（与 I124 的零绑定码不同）

| 权限码 | 绑定角色 |
|---|---|
| `blockchain:record` | ADMIN, SUPER_ADMIN |
| `blockchain:chain` | ADMIN, SUPER_ADMIN |
| `blockchain:trace` | ADMIN, SUPER_ADMIN |
| `blockchain:monitor` | ADMIN, SUPER_ADMIN |

→ 这两个码在 I124 分类里属于 **"存在且有绑定"** 的 7 个码之一，
说明 blockchain 模块的 RBAC 数据是**完整的**，问题纯粹在前端接线。

### 2.5 浏览器实测（admin 账号）

```
=== admin (["SUPER_ADMIN","ADMIN"]) 权限码=123 菜单项=95 ===
  D1 守卫403=0  D2 无路由跳登录=2  可进入=93
      D2 /blockchain/record         Blockchain Record  menu=blockchain:record
      D2 /blockchain/chain          Blockchain Chain   menu=blockchain:chain
```

admin 持 `*` 通配，**即使有路由守卫也会放行** —— 唯一卡住的就是路由不存在。
其余 4 个非超管角色看不到这两条菜单（未绑定），故 D2 计数为 0。

---

## 3. 故障链

```
sys_menu_permission 绑定 /blockchain/record + /blockchain/chain
        ↓
RbacService.buildMenuTree → 菜单节点进入 GET /auth/me/menu
        ↓
Layout.vue 渲染侧边栏 → 两项「可见」（admin）
        ↓
用户点击 → 前端 router 无该 path → vue-router 无匹配
        ↓
落到兜底路由（catch-all）→ 该路由 requiresAuth 且 token 判定分支未命中
        ↓
守卫 next('/login')  ← 用户观感：菜单点了就掉登录
```

---

## 4. 影响

| 角色 | 可见性 | 行为 |
|---|---|---|
| admin / SUPER_ADMIN | 可见 | **点击跳 `/login`**（误以为掉登录，需重新登录） |
| 其他角色 | 不可见 | 无影响（菜单不渲染） |

后端 blockchain 存证 / 链查询 / 溯源能力**完全无法从 Web 界面使用**，
属于典型的**影子功能（Shadow Feature）**：后端已实现 + API 已封装 + 菜单已上线，前端页面从未接线。

---

## 5. 修复方向（备选，未实施，待用户裁决）

| 方案 | 说明 | 工作量 |
|---|---|---|
| **A. 补前端页面** | 新建 `views/blockchain/record.vue` + `views/blockchain/chain.vue`，在 `router/index.ts` 注册两条路由 | 中（需设计表格/查询交互） |
| **B. 下线菜单** | 从 `sys_menu_permission` 移除 `/blockchain/record`、`/blockchain/chain` 两条绑定 | 小（一条 UPDATE，**但会丢失已上线菜单的可见性**） |
| **C. 暂时禁用入口** | 保留 DB 绑定但在前端菜单渲染时过滤无路由项 | 小（治标，I124 之外的独立改动） |

> ⚠️ 方案 B 属数据变更，需用户确认后执行。

---

## 6. 证据清单

| 文件 | 内容 |
|---|---|
| `audit/evidence/ACC002-authz-code-split/browser-deadlinks.json` | D2 明细（`/blockchain/record`、`/blockchain/chain` landed=`/login`） |
| `audit/evidence/ACC002-blockchain-shadow-route/` | 本单专属证据目录（见下） |

**绑定基线**：commit `77767e14c177a7512fc92ba6764e9d5427447969` /
DB `meiyetec@localhost:3307`（2026-10-24 00:30–00:50）/ env 见 `audit/baseline/env.md` /
运行产物 `meiyetec/api:dev`(2026-10-20 17:14) + `meiyetec/web:dev`(2026-10-10 08:16)

---

## 7. 未确认项

1. **`/blockchain/record` 与 `/blockchain/chain` 的原设计意图未确认** —— 现有 `views/blockchain/trace.vue` /
   `monitor.vue` 是否已部分覆盖这两块功能，未做页面级核对
2. **`RecordForm.vue` 的引用方未确认** —— 该组件存在于目录中，但 grep 未在任何路由或页面中找到引用，
   可能是未接线的残留
3. **P2-003「区块链溯源（5 类存证）」规划**（见 `PROJECT_ACCEPTANCE_EVIDENCE.md` §5）是否本就包含这两个页面，
   未对照 PRD 确认 —— 用户未提供 PRD 原文
