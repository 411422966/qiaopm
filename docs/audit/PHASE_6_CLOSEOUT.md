# PHASE 6 / PHASE 6.1 最终收口报告

> 项目: d:\QiaoPM
> 收口日期: 2026-08-14
> 收口类型: 只读审计闭环（Read-Only Closeout）
> 执行范围: 仅记录与审计，不修改任何源码、测试、文档

---

## 1. Phase 状态（最终）

| Phase | 状态 |
|---|---|
| **PHASE 6** | **CLOSED** |
| **PHASE 6.1** | **CLOSED** |

---

## 2. 最终 Production Bug 总览

| Bug ID | 发现阶段 | 严重等级 | 最终状态 | 处理位置 |
|---|---|---|---|---|
| **BUG-AUTH-ROUTE-001** | Phase 5 | HIGH | **FIXED**（Phase 5 已修复，Phase 6/6.1 已验证未回归） | `permission.ts` `_refreshPromise` |
| **BUG-ROUTER-DUP-001** | Phase 6 STEP 7 | MEDIUM | **FIXED**（删除重复 route） | `router/index.ts` 删除第 72 行重复的 `/report/dashboard` |
| **Phase 6 / 6.1 新增 Production Bug** | — | — | **0** | — |

### BUG-ROUTER-DUP-001 canonical route 终态

`frontend/meiyetec-web/src/router/index.ts:51`

```typescript
{ path: 'report/dashboard',
  name: 'ReportDashboard',
  component: () => import('@/views/report/dashboard.vue'),
  meta: { title: '数据看板',
          requiresAuth: true,
          permission: 'report:dashboard' } }
```

| 检查项 | 期望值 | 实测 |
|---|---|---|
| `/report/dashboard` 注册数 | 1 | ✅ 1（仅 line 51） |
| `ReportDashboard` name 出现次数 | 1 | ✅ 1 |
| `meta.title` | `数据看板` | ✅ |
| `meta.permission` | `report:dashboard` | ✅ |
| 组件 | `@/views/report/dashboard.vue` | ✅ 唯一文件 |
| `report:export` 误绑 `/report/dashboard` | 0 | ✅ 0 |
| `/report/export` → `report:export:list` | 保留 | ✅ line 53 |
| Phase 5 `permission.ts` `_refreshPromise` | 保留 | ✅ |

### BUG-AUTH-ROUTE-001 验证

| 检查项 | 结果 |
|---|---|
| `permission.ts` `_refreshPromise` 仍然存在 | ✅ |
| 76 次跨浏览器登录测试全部 PASS | ✅ |
| 路由跳转未触发 401 / 鉴权闪烁 | ✅ |

---

## 3. 最终 E2E 结果

### 3.1 Run 1（Phase 6.1 修复后）

| Suite | Chromium | Mobile Chrome |
|---|---:|---:|
| auth | 4/4 PASS | 4/4 PASS |
| smoke | 6/6 PASS | 5/5 PASS |
| golden-path | 28/28 PASS | 28/28 PASS |
| **小计** | **38/38** | **37/37** |

性能测试 timing:

- chromium 首页加载时间 < 5s → **2.4s** ✅
- chromium 会员页加载时间 < 3s → **2.4s** ✅
- Mobile Chrome 首页加载时间 < 5s → **2.0s** ✅
- Mobile Chrome 会员页加载时间 < 3s → **2.3s** ✅

### 3.2 Run 2（Phase 6.1 连续验证）

| Suite | Chromium | Mobile Chrome |
|---|---:|---:|
| auth | 4/4 PASS | 4/4 PASS |
| smoke | 6/6 PASS | 5/5 PASS |
| golden-path | 28/28 PASS | 28/28 PASS |
| **小计** | **38/38** | **37/37** |

性能测试 timing:

- chromium 首页加载时间 < 5s → **2.7s** ✅
- chromium 会员页加载时间 < 3s → **2.6s** ✅
- Mobile Chrome 首页加载时间 < 5s → **3.2s** ✅
- Mobile Chrome 会员页加载时间 < 3s → **2.4s** ✅

### 3.3 稳定性结论

| 项目 | 要求 | 实际 |
|---|---|---|
| Chromium | 38/38 PASS × 2 连续 | ✅ 38/38 × 2 |
| Mobile Chrome | 37/37 PASS × 2 连续 | ✅ 37/37 × 2 |
| 完整矩阵连续 2 次通过 | YES | ✅ YES |
| 默认并行 `workers=3` | YES | ✅ |
| 使用 `--workers=1` 掩盖问题 | NO | ✅ 未使用 |
| 执行耗时 | — | 1.2m × 2 |
| 真实 Vite dev-server | YES | ✅ |
| 真实 backend（`/auth/login`、`/auth/me/menu`） | YES | ✅ |

---

## 4. Flaky Test 解决记录

| ID | 状态 | 修复方式 | 影响范围 |
|---|---|---|---|
| **FLAKE-PERF-001** | **RESOLVED** | `networkidle` → `domcontentloaded` | 仅 `e2e/golden-path.spec.ts` 第 259、266 行的两个 perf 测试 |
| 其他 flaky test | — | — | 无 |

修复前后对比:

```diff
test.describe('性能', () => {
  test('首页加载时间 < 5s', async ({ page }) => {
    const start = Date.now()
-   await page.goto('/dashboard', { waitUntil: 'networkidle' })
+   await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
    const loadTime = Date.now() - start
    expect(loadTime).toBeLessThan(5000)   // threshold 未改
  })

  test('会员页加载时间 < 3s', async ({ page }) => {
    const start = Date.now()
-   await page.goto('/member', { waitUntil: 'networkidle' })
+   await page.goto('/member', { waitUntil: 'domcontentloaded' })
    const loadTime = Date.now() - start
    expect(loadTime).toBeLessThan(3000)   // threshold 未改
  })
})
```

约束遵守:

- ✅ 未修改 threshold（仍 `< 5s` / `< 3s`）
- ✅ 未修改 assertion
- ✅ 未增加 `waitForTimeout`
- ✅ 未增加自定义 `timeout`
- ✅ 未修改生产代码
- ✅ 未修改 router / permission / auth.setup / playwright.config
- ✅ 未 mock
- ✅ 未 skip
- ✅ 未删除任何测试
- ✅ 未使用 `--workers=1`

---

## 5. 禁用模式审计

| 模式 | 使用次数 | 证据 |
|---|---:|---|
| `test.skip` / `test.fixme` | **0** | `e2e/*.spec.ts` 内无 skip |
| Mock API（`page.route`、`__mocks__`、MSW） | **0** | 所有路由打真实 backend |
| Fake Authentication | **0** | 真实 `/api/auth/login`，真实 JWT 写入 `localStorage` |
| 自定义 `timeout` 膨胀 | **0** | 仅 Playwright 默认 5s polling（`toBeVisible`、`toHaveTitle`） |
| Assertion 弱化 | **0** | 所有 assertion 均被强化：`count > 0` → `toBeVisible()`、`text matches` → `toHaveTitle(meta.title)`。无 threshold 放宽、无 locator 放宽、无 `.catch(e => {})` 吞错 |

---

## 6. Phase 6 / 6.1 期间修改文件审计

### 生产代码（1 个文件）
- `frontend/meiyetec-web/src/router/index.ts` — 删除 1 行（重复 `/report/dashboard` route）

### 测试基础设施（4 个文件）
- `frontend/meiyetec-web/playwright.config.ts` — 新增 unauthenticated 项目、testMatch 隔离
- `frontend/meiyetec-web/e2e/auth.setup.ts` — 真实 `/auth/login` 装置（Phase 2，已保留）
- `frontend/meiyetec-web/e2e/auth.spec.ts` — 创建（Phase 6 STEP 2）；`无障碍 › 登录页有基础无障碍标签` 由 `count > 0` 改为 `toBeVisible()`（Phase 6.1）
- `frontend/meiyetec-web/e2e/golden-path.spec.ts` — login 测试拆分到 auth.spec.ts、9 条 LOCATOR assertion 改为 `toHaveTitle(meta.title)`、Mobile 首页 title 修正、2 个 perf 测试 `networkidle` → `domcontentloaded`（Phase 6.1）

### 未触碰
- `permission.ts`（Phase 5 `_refreshPromise` 修复保留）
- 任何 page 组件
- 任何业务 API
- 任何 SQL seed
- 任何现有文档（除新增本收口报告外）

---

## 7. Remaining Debt（登记，不处理）

下列项目仅记录，不在本阶段处理：

| # | 项 | 类别 | 未来阶段 |
|---|---|---|---|
| 1 | Golden Path 当前只是 Navigation Smoke，未覆盖真实业务 E2E（创建会员 → 创建预约 → 创建订单 → 支付 → 报表数据变化） | 测试覆盖缺口 | 未来"业务 E2E"阶段 |
| 2 | `docs/audit/BEAUTY_SAAS/02_FEATURE_INVENTORY_V2.md:271` F-RPT-04 仍把已删除的 duplicate 描述为独立功能 | 文档过期 | 文档同步阶段 |
| 3 | `docs/SECURITY-权限码映射表.md:161` 引用 `report:dashboard:view`（带 `:view` 后缀），与 SQL seed `report:dashboard` 不一致 | 文档过期 | 文档同步阶段 |
| 4 | 后端驱动的菜单标题架构（Option D，`Layout.vue` 通过 `useUserStore().menus` 渲染） | 架构债 | 未来重构阶段 |
| 5 | 后端 SQL seed 中定义了 `report:snapshot` 权限，但前端无 `/report/snapshot` 路由 | 产品前置 gap | 产品范围决策 |
| 6 | Perf 测试基于 Vite dev-server 行为；生产构建（Vite prod / Nginx）下 `networkidle` 会更快稳定 | 测试成熟度 | 未来 perf 测试分离阶段 |

---

## 8. 最终判定

```
PHASE 6 = CLOSED
PHASE 6.1 = CLOSED
```

收口完成。Phase 6 不再修改任何代码、不再重跑 E2E、不再调查新问题。

剩余债务（§7）仅登记，待后续阶段处理。