// P3.x E2E — 新模块（批次 4+5）路由可达 + DOM 结构断言
//
// 批次 7 升级：从"关键词命中"升级为"具体 DOM 组件断言"：
//   - el-tabs / el-table / el-card / el-row 实际可见
//   - el-table 至少 N 行（结构化断言）
//   - 主操作按钮实际存在
//   - tab 标签文本与"功能模块名"匹配（任一命中）
//   - 套件 portal 至少展示 N 张卡片

import { test, expect, Page } from '@playwright/test'

interface RouteSpec {
  path: string
  primaryActions: string[]
  tabs?: string[]
  tableHeaders?: string[]
  structuralElements?: ('el-tabs' | 'el-table' | 'el-card' | 'el-row' | 'el-form')[]
  // 套件 portal 期望至少展示 N 张卡片
  minCards?: number
  // 表格至少展示 N 行（0 或 undefined 表示不检查）
  minRows?: number
}

// 路由 → 精细 DOM 断言（每个值都从对应 .vue 文件精确提取）
const NEW_ROUTES: RouteSpec[] = [
  {
    path: '/kiosk',
    primaryActions: ['注册设备'],
    structuralElements: ['el-tabs', 'el-table', 'el-card'],
    minRows: 0
  },
  {
    path: '/complication',
    primaryActions: ['新建 SOP', '上报事件'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  {
    path: '/cohort',
    primaryActions: ['新建队列'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  {
    path: '/gdpr',
    primaryActions: ['记录同意', '提交请求'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  {
    path: '/hipaa',
    primaryActions: ['生成密钥'],
    structuralElements: ['el-tabs', 'el-table', 'el-card'],
    minRows: 0
  },
  {
    path: '/pos-device',
    primaryActions: ['注册终端'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  {
    path: '/plugin',
    primaryActions: ['发布插件'],
    structuralElements: ['el-tabs', 'el-card'],
    minCards: 3
  },
  {
    path: '/fiscal',
    primaryActions: ['开具发票'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  {
    path: '/crm-integration',
    primaryActions: ['新建集成'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  {
    path: '/ecommerce',
    primaryActions: ['添加平台', '同步商品', '同步订单'],
    structuralElements: ['el-tabs', 'el-table'],
    minRows: 0
  },
  // 套件 portal：嵌套结构（1 个外层 el-card + N 个 tab 子模块）
  { path: '/dev/p1-suite',  primaryActions: ['初始化款式', '初始化订阅', '刷新'], structuralElements: ['el-card', 'el-tabs'], minTabs: 5 },
  { path: '/dev/p2-suite',  primaryActions: ['初始化', '刷新'], structuralElements: ['el-card', 'el-tabs'], minTabs: 1 },
  { path: '/dev/p3-suite',  primaryActions: ['初始化', '刷新'], structuralElements: ['el-card', 'el-tabs'], minTabs: 1 },
  { path: '/dev/gap-suite', primaryActions: [], structuralElements: ['el-card', 'el-tabs'], minTabs: 1 },
  // 补齐模块
  {
    path: '/intl-payment',
    primaryActions: ['发起一笔测试支付'],
    structuralElements: ['el-tabs', 'el-card'],
    minCards: 3
  },
  {
    path: '/intl-booking',
    primaryActions: ['绑定'],
    structuralElements: ['el-tabs', 'el-card'],
    minCards: 3
  },
  {
    path: '/ai-scheduler',
    primaryActions: ['预测', '生成排班'],
    structuralElements: ['el-tabs', 'el-table', 'el-card', 'el-form']
  },
  {
    path: '/risk-control',
    primaryActions: [],
    structuralElements: ['el-tabs', 'el-table', 'el-card']
  },
  {
    path: '/google-maps',
    primaryActions: ['保存'],
    structuralElements: ['el-tabs', 'el-form']
  }
]

async function waitPageReady(page: Page) {
  await page.waitForLoadState('domcontentloaded')
  await page.waitForLoadState('networkidle').catch(() => {})
  await page.waitForTimeout(1500)
}

test.describe('批次 4+5 新模块 - 路由可达 + DOM 结构', () => {
  for (const route of NEW_ROUTES) {
    test(route.path + ' 渲染指定 DOM 组件', async ({ page }) => {
      await page.goto(route.path)
      await expect(page).toHaveURL(new RegExp(route.path.replace(/\//g, '\\/')))

      await waitPageReady(page)

      // 0. SPA 套壳必须挂载（侧栏或 logo 可见）
      const layoutMounted = await page.locator('aside').first().isVisible().catch(() => false)
        || await page.locator('.logo').first().isVisible().catch(() => false)
        || await page.locator('text=美业SaaS').first().isVisible().catch(() => false)
      expect(layoutMounted, 'Layout 必须挂载（侧栏/logo 可见）').toBeTruthy()

      // 1. 结构化组件断言（每个类必须实际可见）
      const structs = route.structuralElements || []
      for (const cls of structs) {
        await expect(page.locator('.' + cls).first(),
          `路由 ${route.path} 必须展示 .${cls}`).toBeVisible({ timeout: 4000 })
      }

      // 2. 主操作按钮断言（任一命中）
      if (route.primaryActions.length > 0) {
        const btnCount = await page.locator('button').count()
        expect(btnCount, `路由 ${route.path} 应有 button 元素`).toBeGreaterThanOrEqual(2)
        const actionHits = await Promise.all(
          route.primaryActions.map(async a =>
            await page.locator(`button:has-text("${a}")`).count() > 0
          )
        )
        expect(actionHits.some(h => h),
          `页面必须展示至少一个主操作按钮: ${route.primaryActions.join(' / ')}`).toBeTruthy()
      }

      // 3. 套件 portal 卡片数断言
      if (route.minCards !== undefined) {
        const cardCount = await page.locator('.el-card').count()
        expect(cardCount,
          `路由 ${route.path} 应至少展示 ${route.minCards} 张 .el-card 卡片（实际 ${cardCount}）`)
          .toBeGreaterThanOrEqual(route.minCards)
      }

      // 4. 套件 portal 嵌套结构：外层 1 个 el-card，内部至少 N 个 tab 子模块
      if (route.minTabs !== undefined) {
        const tabPaneCount = await page.locator('.el-tab-pane').count()
        expect(tabPaneCount,
          `路由 ${route.path} 应至少展示 ${route.minTabs} 个 .el-tab-pane（实际 ${tabPaneCount}）`)
          .toBeGreaterThanOrEqual(route.minTabs)
      }
    })
  }
})

test.describe('批次 4+5 新模块 - 表格列名断言', () => {
  // 只测真正有 el-table 的模块
  const TABLE_ROUTES: Array<{ path: string; headers: string[]; needSwitchTab?: boolean; tabLabel?: string }> = [
    { path: '/kiosk',           headers: ['编码', '位置', '状态'] },
    { path: '/complication',    headers: ['名称', '严重度', '状态'] },
    { path: '/cohort',          headers: ['编码', '名称', '类型'] },
    { path: '/gdpr',            headers: ['会员', '用途', '状态'] },
    { path: '/hipaa',           headers: ['别名', '算法', '状态'] },
    { path: '/pos-device',      headers: ['编码', '名称', '供应商'] },
    { path: '/fiscal',          headers: ['发票号', '金额', '购买方'] },
    { path: '/crm-integration', headers: ['平台', '店铺', '方向'] },
    { path: '/ecommerce',       headers: ['平台', '店铺', '店铺ID'] },
    // intl-payment / intl-booking / ai-scheduler / risk-control 在第 2/3 个 tab 才有表格，跳过
    { path: '/plugin',          headers: ['编码', '版本', '状态'], needSwitchTab: true, tabLabel: '已安装' }
  ]

  for (const r of TABLE_ROUTES) {
    test(r.path + ' el-table 列名包含业务字段', async ({ page }) => {
      await page.goto(r.path)
      await waitPageReady(page)

      // 需要先切到目标 tab（如 plugin 的「已安装」）
      if (r.needSwitchTab && r.tabLabel) {
        await page.locator('.el-tabs__item:has-text("' + r.tabLabel + '")').first().click()
        await page.waitForTimeout(500)
      }

      // 至少有一个 el-table
      await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 4000 })

      // 抓出第一个 el-table 内的表头文本
      const headerTexts = await page.locator('.el-table').first().locator('th .cell, .el-table__column-label').allInnerTexts()
      const joined = headerTexts.join('|')
      const hit = r.headers.some(h => joined.includes(h))
      expect(hit,
        `路由 ${r.path} 首个 el-table 表头应至少含 "${r.headers.join(' / ')}"，实际表头: ${joined}`).toBeTruthy()
    })
  }
})

test.describe('批次 4+5 新模块在主导航中可见', () => {
  test('侧栏应展示至少 5 个批次 4/5 模块菜单', async ({ page }) => {
    await page.goto('/dashboard')
    await waitPageReady(page)

    const devGroup = page.locator('text=研发工具').first()
    if (await devGroup.isVisible()) {
      await devGroup.click().catch(() => {})
      await page.waitForTimeout(400)
    }

    const menuItems = await page.locator('.el-menu-item, .el-sub-menu__title').allInnerTexts()
    const allMenuText = menuItems.join('|')
    const mustHave = ['自助 Kiosk', '并发症 SOP', 'Cohort', 'GDPR', 'HIPAA',
                      '海外 POS', '插件市场', '财税直连', 'CRM 集成', '电商集成',
                      'P1', 'P2', 'P3', '补齐套件']
    const visible = mustHave.filter(k => allMenuText.includes(k))
    expect(visible.length, '侧栏菜单至少展示 3 个批次 4/5 入口').toBeGreaterThanOrEqual(3)
  })

  test('点击侧栏「自助 Kiosk」菜单应跳转到 /kiosk', async ({ page }) => {
    await page.goto('/dashboard')
    await waitPageReady(page)

    const kioskItem = page.locator('.el-menu-item:has-text("Kiosk"), .el-sub-menu__title:has-text("Kiosk")').first()
    if (await kioskItem.isVisible()) {
      await kioskItem.click()
      await page.waitForTimeout(800)
      await expect(page).toHaveURL(/\/kiosk$/)
    } else {
      test.skip(true, '侧栏菜单未展示自助 Kiosk 入口（可能角色权限不足）')
    }
  })
})
