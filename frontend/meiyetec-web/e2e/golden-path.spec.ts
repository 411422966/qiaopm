// Phase 6 — Authenticated Business Golden Path Tests
//
// 覆盖会员/预约/收银/库存/套餐/提成/报表/营销/设置 9 大模块。
//
// 真实契约：
// - 路由通过 createWebHistory() 注册在 pathname 上
// - 页面身份由 router.beforeEach 设置的 document.title = meta.title 提供
// - 认证通过 e2e/auth.setup.ts 的真实 /api/auth/login 注入 storageState
//
// Phase 6 变更：
// - 登录模块已拆分到 e2e/auth.spec.ts（unauthenticated projects）
// - 页面身份断言从 .title/.page-title/h1/h2 改为 toHaveTitle(meta.title)
// - 移动端首页 title 改为 '首页'（meta.title 真实值）
// - beforeEach 保留 warm-up 到 /dashboard（Phase 5 已验证稳定）
//
// 当前验证范围：
//   9 大模块业务页面的路由可达性 + 页面身份正确性
//
// 尚未验证（明确技术债）：
//   - 创建会员
//   - 创建预约
//   - 生成订单
//   - 支付
//   - 报表数据因业务操作产生变化
//
// 这些真实业务操作属于未来 P3 阶段，不通过 mock 或 fake data 补齐。

import { test, expect } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  // 先访问 /dashboard 触发 permission store 加载完成（warm-up），
  // 避免在首次直接跳到深路径时 guard 与 App.vue onMounted 之间出现 race。
  await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
})

// ============================================================
// 2. 会员管理
// ============================================================

test.describe('会员管理', () => {
  test('会员菜单可见', async ({ page }) => {
    const memberLink = page.getByRole('menuitem', { name: /会员/i }).or(
      page.locator('a[href*="member"]')
    )
    await expect(memberLink.first()).toBeVisible({ timeout: 5000 })
  })

  test('会员列表页可访问', async ({ page }) => {
    await page.goto('/member')
    await expect(page.getByText(/会员|客户/i).first()).toBeVisible({ timeout: 8000 })
  })

  test('新建会员按钮存在', async ({ page }) => {
    await page.goto('/member')
    const addBtn = page.getByRole('button', { name: /新建|添加|新增/i })
    await expect(addBtn.first()).toBeVisible({ timeout: 8000 })
  })
})

// ============================================================
// 3. 预约管理
// ============================================================

test.describe('预约管理', () => {
  test('预约日历页可访问', async ({ page }) => {
    await page.goto('/booking/calendar')
    await expect(page).toHaveTitle('预约日历')
  })

  test('新建预约按钮存在', async ({ page }) => {
    await page.goto('/booking/list')
    const addBtn = page.getByRole('button', { name: /新建|添加|新增|创建/i })
    await expect(addBtn.first()).toBeVisible({ timeout: 8000 })
  })

  test('排班管理可访问', async ({ page }) => {
    await page.goto('/booking/schedule')
    await expect(page).toHaveTitle('排班管理')
  })
})

// ============================================================
// 4. 收银结算
// ============================================================

test.describe('收银结算', () => {
  test('收银台可访问', async ({ page }) => {
    await page.goto('/pos/checkout')
    await expect(page).toHaveTitle('收银台')
  })

  test('订单列表可访问', async ({ page }) => {
    await page.goto('/pos/orders')
    await expect(page).toHaveTitle('订单列表')
  })

  test('退款页可访问', async ({ page }) => {
    await page.goto('/pos/refund')
    await expect(page).toHaveTitle('退款单')
  })
})

// ============================================================
// 5. 库存管理
// ============================================================

test.describe('库存管理', () => {
  test('库存列表可访问', async ({ page }) => {
    await page.goto('/stock/list')
    await expect(page).toHaveTitle('库存列表')
  })

  test('入库记录可访问', async ({ page }) => {
    await page.goto('/stock/record')
    await expect(page).toHaveTitle('出入库记录')
  })

  test('库存预警可访问', async ({ page }) => {
    await page.goto('/stock/alert')
    await expect(page).toHaveTitle('库存预警')
  })
})

// ============================================================
// 6. 套餐次卡
// ============================================================

test.describe('套餐次卡', () => {
  test('套餐列表可访问', async ({ page }) => {
    await page.goto('/package')
    await expect(page).toHaveTitle('套餐次卡')
  })

  test('会员套餐查询可访问', async ({ page }) => {
    await page.goto('/package/member-orders')
    await expect(page).toHaveTitle('会员套餐查询')
  })
})

// ============================================================
// 7. 提成管理
// ============================================================

test.describe('提成管理', () => {
  test('提成规则可访问', async ({ page }) => {
    await page.goto('/commission/rules')
    await expect(page).toHaveTitle('提成规则')
  })

  test('提成记录可访问', async ({ page }) => {
    await page.goto('/commission/records')
    await expect(page).toHaveTitle('提成记录')
  })
})

// ============================================================
// 8. 报表
// ============================================================

test.describe('数据报表', () => {
  test('数据看板可访问', async ({ page }) => {
    await page.goto('/report/dashboard')
    await expect(page).toHaveTitle('数据看板')
  })

  test('营业报表可访问', async ({ page }) => {
    await page.goto('/report/biz')
    await expect(page).toHaveTitle('营业报表')
  })
})

// ============================================================
// 9. 营销
// ============================================================

test.describe('营销模块', () => {
  test('优惠券可访问', async ({ page }) => {
    await page.goto('/market/coupon')
    await expect(page).toHaveTitle('优惠券')
  })

  test('营销活动可访问', async ({ page }) => {
    await page.goto('/market/activity')
    await expect(page).toHaveTitle('营销活动')
  })
})

// ============================================================
// 10. 系统设置
// ============================================================

test.describe('系统设置', () => {
  test('店铺配置可访问', async ({ page }) => {
    await page.goto('/setting')
    await expect(page).toHaveTitle('系统设置')
  })

  test('租户管理可访问', async ({ page }) => {
    await page.goto('/sys/tenant')
    await expect(page).toHaveTitle('租户管理')
  })
})

// ============================================================
// 11. Golden Path — 导航链路 Smoke
//
// 明确范围：当前只验证 Member → Booking → POS → Report 4 个页面
// 导航可达性 + 页面身份正确性。
//
// 尚未验证：业务操作闭环（创建会员 → 创建预约 → 收银 → 报表变化）。
// ============================================================

test.describe('Golden Path 导航链路 Smoke', () => {
  test('会员 → 预约 → 收银 → 报表 导航链路', async ({ page }) => {
    // Step 1: 会员
    await page.goto('/member')
    await expect(page).toHaveTitle('会员管理')

    // Step 2: 预约
    await page.goto('/booking/calendar')
    await expect(page).toHaveTitle('预约日历')

    // Step 3: 收银
    await page.goto('/pos/checkout')
    await expect(page).toHaveTitle('收银台')

    // Step 4: 报表
    await page.goto('/report/dashboard')
    await expect(page).toHaveTitle('数据看板')
  })
})

// ============================================================
// 12. 响应式 — 移动端
// ============================================================

test.describe('移动端适配', () => {
  test.use({ viewport: { width: 375, height: 812 } })

  test('首页在移动端可正常加载', async ({ page }) => {
    await page.goto('/dashboard')
    // meta.title for /dashboard is '首页'
    await expect(page).toHaveTitle('首页')
  })

  test('会员页在移动端可访问', async ({ page }) => {
    await page.goto('/member')
    await expect(page.getByText(/会员/i).first()).toBeVisible({ timeout: 8000 })
  })
})

// ============================================================
// 13. 性能
// ============================================================

test.describe('性能', () => {
  test('首页加载时间 < 5s', async ({ page }) => {
    const start = Date.now()
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
    const loadTime = Date.now() - start
    expect(loadTime).toBeLessThan(5000)
  })

  test('会员页加载时间 < 3s', async ({ page }) => {
    const start = Date.now()
    await page.goto('/member', { waitUntil: 'domcontentloaded' })
    const loadTime = Date.now() - start
    expect(loadTime).toBeLessThan(3000)
  })
})
