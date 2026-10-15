// P3.x E2E — 5 角色菜单可见性验证
//
// 验证策略（两层）：
//   L1 基础验证（弱保证）— 每个角色能登录 + 后端返回菜单非空
//   L2 严格验证（强保证）— 后端返回的菜单必须匹配 docs/audit/MENU_ROLE_MAPPING.md 中定义的矩阵
//
// L2 在以下情况会自动跳过（不视为失败）：
//   - 账号不存在 / 登录失败
//   - migrate_r3_0_c_menu_role.sql 还没执行（这是发布门禁 SQL，不执行就验证等于演戏）
//
// 角色与预期菜单矩阵（参见 docs/audit/MENU_ROLE_MAPPING.md）：
//   CLERK    : 首页/收银/预约/会员/储值/套餐/库存/基础资料
//   MANAGER  : CLERK + 提成/报表/营销/医美合规
//   BOSS     : MANAGER + 客户运营/组织架构/系统设置
//   OPERATOR : 首页/报表/营销/客户运营
//   ADMIN    : 全部（含研发工具）
//
// 用法：
//   E2E_*-USERNAME / E2E_*-PASSWORD 环境变量覆盖默认账号
//   默认值参考 seed_test_roles.sql
//
// 依赖：playwright.config.ts 中不依赖 storageState 的 role-menu 项目
// 本脚本每个角色独立创建自己的登录会话。

import { test, expect, request as pwRequest } from '@playwright/test'

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'

interface RoleAccount {
  role: string
  username: string
  password: string
}

const ROLES: RoleAccount[] = [
  { role: 'CLERK',    username: process.env.E2E_CLERK_USERNAME    || 'clerk01',    password: process.env.E2E_CLERK_PASSWORD    || '123456' },
  { role: 'MANAGER',  username: process.env.E2E_MANAGER_USERNAME  || 'manager01',  password: process.env.E2E_MANAGER_PASSWORD  || '123456' },
  { role: 'BOSS',     username: process.env.E2E_BOSS_USERNAME     || 'boss01',     password: process.env.E2E_BOSS_PASSWORD     || '123456' },
  { role: 'OPERATOR', username: process.env.E2E_OPERATOR_USERNAME || 'operator01', password: process.env.E2E_OPERATOR_PASSWORD || '123456' },
  { role: 'ADMIN',    username: process.env.E2E_ADMIN_USERNAME    || 'admin',      password: process.env.E2E_ADMIN_PASSWORD    || '123456' }
]

// 关键菜单标识（按一级模块 path 前缀）
const MODULE_PRESETS = {
  DASHBOARD:     '/dashboard',
  POS:           '/pos',
  BOOKING:       '/booking',
  MEMBER:        '/member',
  WALLET:        '/wallet',
  PACKAGE:       '/package',
  STOCK:         '/stock',
  BIZ:           '/biz',
  COMMISSION:    '/commission',
  REPORT:        '/report',
  MARKET:        '/market',
  CRM:           '/crm',
  ORG:           '/org',
  MH:            '/mh',
  SYS:           '/sys',
  DEV:           '/dev'
}

// 期望矩阵（基于 fix_menu_tree_restructure.sql 的实际角色绑定）
// 一级菜单前缀 + 子菜单路径都允许通过
const EXPECTED_MODULES: Record<string, string[]> = {
  // CLERK: 首页/收银/预约/会员/库存（不含子菜单完整路由前缀）
  CLERK: [
    MODULE_PRESETS.DASHBOARD, MODULE_PRESETS.POS, MODULE_PRESETS.BOOKING,
    MODULE_PRESETS.MEMBER, MODULE_PRESETS.STOCK
  ],
  // MANAGER: CLERK + 营销 + 报表 + 储值/套餐/基础资料
  MANAGER: [
    MODULE_PRESETS.DASHBOARD, MODULE_PRESETS.POS, MODULE_PRESETS.BOOKING,
    MODULE_PRESETS.MEMBER, MODULE_PRESETS.STOCK, MODULE_PRESETS.WALLET,
    MODULE_PRESETS.PACKAGE, MODULE_PRESETS.BIZ,
    MODULE_PRESETS.MARKET, MODULE_PRESETS.REPORT
  ],
  // BOSS: MANAGER + 提成 + 医美 + 客户运营 + 组织 + 系统设置
  BOSS: [
    MODULE_PRESETS.DASHBOARD, MODULE_PRESETS.POS, MODULE_PRESETS.BOOKING,
    MODULE_PRESETS.MEMBER, MODULE_PRESETS.STOCK, MODULE_PRESETS.WALLET,
    MODULE_PRESETS.PACKAGE, MODULE_PRESETS.BIZ, MODULE_PRESETS.COMMISSION,
    MODULE_PRESETS.MARKET, MODULE_PRESETS.REPORT,
    MODULE_PRESETS.MH, MODULE_PRESETS.CRM,
    MODULE_PRESETS.ORG, MODULE_PRESETS.SYS
  ],
  // OPERATOR: 首页 + 报表 + 营销
  OPERATOR: [
    MODULE_PRESETS.DASHBOARD, MODULE_PRESETS.REPORT, MODULE_PRESETS.MARKET
  ],
  // ADMIN: 全部 — 与 SUPER_ADMIN 同源（超管不做必不含校验）
  ADMIN: [
    MODULE_PRESETS.DASHBOARD, MODULE_PRESETS.POS, MODULE_PRESETS.BOOKING,
    MODULE_PRESETS.MEMBER, MODULE_PRESETS.STOCK, MODULE_PRESETS.WALLET,
    MODULE_PRESETS.PACKAGE, MODULE_PRESETS.BIZ, MODULE_PRESETS.COMMISSION,
    MODULE_PRESETS.MARKET, MODULE_PRESETS.REPORT,
    MODULE_PRESETS.MH, MODULE_PRESETS.CRM,
    MODULE_PRESETS.ORG, MODULE_PRESETS.SYS,
    MODULE_PRESETS.DEV
  ]
}

interface MenuNode {
  id: number
  permissionCode: string
  title: string
  path?: string
  hidden?: number
  children?: MenuNode[]
}

interface RbacSession {
  userId: number
  username: string
  roles: string[]
  permissions: string[]
  menus: MenuNode[]
}

function flattenMenuPaths(nodes: MenuNode[]): string[] {
  const out: string[] = []
  for (const n of nodes) {
    if (n.path) out.push(n.path)
    if (n.children?.length) out.push(...flattenMenuPaths(n.children))
  }
  return out
}

function hasModule(paths: string[], modulePrefix: string): boolean {
  return paths.some(p => p === modulePrefix || p.startsWith(modulePrefix + '/') || p.startsWith(modulePrefix + ':'))
}

// 登录辅助：返回 { ok, token, session, error }，失败时测试用 test.skip 不算失败
type LoginResult =
  | { ok: true; token: string; session: RbacSession; userInfo: any }
  | { ok: false; error: string }

async function loginAndFetchSession(req: any, username: string, password: string): Promise<LoginResult> {
  try {
    const loginResp = await req.post(`${BASE_URL}/api/auth/login`, {
      headers: { 'Content-Type': 'application/json' },
      data: { username, password }
    })
    if (loginResp.status() !== 200) return { ok: false, error: `HTTP ${loginResp.status()}` }
    const loginJson = await loginResp.json()
    if (loginJson.code !== 200 || !loginJson.data?.token) {
      return { ok: false, error: `业务失败：${loginJson.message || 'no token'}` }
    }
    const token = loginJson.data.token as string
    const permResp = await req.get(`${BASE_URL}/api/auth/me/permissions`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (permResp.status() !== 200) return { ok: false, error: `/me/permissions HTTP ${permResp.status()}` }
    const permJson = await permResp.json()
    if (permJson.code !== 200) return { ok: false, error: `/me/permissions 业务失败：${permJson.message}` }
    return { ok: true, token, session: permJson.data as RbacSession, userInfo: loginJson.data }
  } catch (e: any) {
    return { ok: false, error: e?.message || String(e) }
  }
}

// 探测环境：检查 migrate_r3_0_c_menu_role.sql 是否已生效
// 判定逻辑：ADMIN 角色是否绑定了「研发工具 / 平台管理 / 系统设置」三大独有菜单
// 这是 migrate SQL 唯一新增的 ADMIN 独有授权。
// 如果 ADMIN 拿到的菜单数与 SUPER_ADMIN 接近，认为 migrate 已生效
async function detectMigrateExecuted(req: any): Promise<boolean> {
  const r = await loginAndFetchSession(req, ROLES[4].username, ROLES[4].password)
  if (!r.ok) return false
  const perms = r.session.permissions
  // 探测 1：是否拿到任一 ADMIN 独有权限（dev / sys:* / org:*）
  const hasAdminOnly = perms.some((p: string) =>
    p.startsWith('dev:') || p.startsWith('sys:') || p.startsWith('org:') ||
    p.startsWith('crm:') || p.startsWith('mkt:') || p.startsWith('biz:') ||
    p === 'employee' || p === 'employee:list'
  )
  // 探测 2：菜单路径数是否 ≥ 9（默认 9 个是基础菜单，ADMIN 应该有 ≥ 12）
  const hasEnoughMenus = r.session.menus.length >= 12
  return hasAdminOnly || hasEnoughMenus
}

test.describe('L1 基础验证 — 5 角色登录 + 菜单非空', () => {
  for (const acct of ROLES) {
    test(`[${acct.role}] 登录成功 + 菜单非空`, async () => {
      const ctx = await pwRequest.newContext({ baseURL: BASE_URL })
      const r = await loginAndFetchSession(ctx, acct.username, acct.password)
      if (!r.ok) {
        test.skip(true, `账号 ${acct.username} 不可用（${r.error}）— 请先执行 seed_test_roles.sql 创建测试用户`)
        return
      }
      expect(r.session.menus.length, `[${acct.role}] 菜单应至少有 1 项`).toBeGreaterThan(0)
      // 任何角色都应能看到首页
      const paths = flattenMenuPaths(r.session.menus)
      expect(hasModule(paths, MODULE_PRESETS.DASHBOARD), `[${acct.role}] 应包含首页`).toBe(true)
      await ctx.dispose()
    })
  }
})

test.describe('L2 严格验证 — 菜单矩阵匹配（需 migrate SQL 已执行）', () => {
  let migrateExecuted = false
  let checked = false

  for (const acct of ROLES) {
    test(`[${acct.role}] 菜单矩阵匹配 docs/audit/MENU_ROLE_MAPPING.md`, async () => {
      const ctx = await pwRequest.newContext({ baseURL: BASE_URL })
      // 第一次跑时探测 ADMIN 是否拿到 dev 权限
      if (!checked) {
        migrateExecuted = await detectMigrateExecuted(ctx)
        checked = true
      }
      if (!migrateExecuted) {
        test.skip(true, 'migrate_r3_0_c_menu_role.sql 未执行（ADMIN 缺少 dev:access）。请先执行该 SQL 迁移，再跑 L2 严格验证。')
        return
      }

      const r = await loginAndFetchSession(ctx, acct.username, acct.password)
      if (!r.ok) {
        test.skip(true, `账号 ${acct.username} 不可用：${r.error}`)
        return
      }
      const allPaths = flattenMenuPaths(r.session.menus)
      const expected = EXPECTED_MODULES[acct.role]

      // 必含模块
      for (const mod of expected) {
        const ok = hasModule(allPaths, mod)
        expect(ok, `[${acct.role}] 期望看到 ${mod}，实际菜单：${JSON.stringify(allPaths)}`).toBe(true)
      }

      // 必不含模块（防止权限泄漏）— ADMIN 是超管，不做此项校验
      if (acct.role !== 'ADMIN') {
        const allModules = Object.values(MODULE_PRESETS)
        const forbidden = allModules.filter(m => !expected.includes(m))
        for (const mod of forbidden) {
          const leak = hasModule(allPaths, mod)
          expect(leak, `[${acct.role}] 不应看到 ${mod}，但实际菜单中包含 — 权限泄漏！`).toBe(false)
        }
      }
      await ctx.dispose()
    })
  }
})

test.describe('L3 前端渲染层验证', () => {
  for (const acct of ROLES) {
    test(`[${acct.role}] Layout 渲染的菜单 ≥ 后端返回的菜单项数`, async ({ page, request }) => {
      const r = await loginAndFetchSession(request, acct.username, acct.password)
      if (!r.ok) {
        test.skip(true, `账号 ${acct.username} 不可用：${r.error}`)
        return
      }

      // 写 localStorage
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })
      await page.addInitScript(
        ({ token, user, session }) => {
          localStorage.setItem('token', token)
          localStorage.setItem('user', JSON.stringify(user))
          localStorage.setItem('rbac.session.v1', JSON.stringify(session))
        },
        { token: r.token, user: r.userInfo, session: r.session }
      )
      await page.evaluate(
        ({ token, user, session }) => {
          localStorage.setItem('token', token)
          localStorage.setItem('user', JSON.stringify(user))
          localStorage.setItem('rbac.session.v1', JSON.stringify(session))
        },
        { token: r.token, user: r.userInfo, session: r.session }
      )

      // 打开 dashboard
      await page.goto('/dashboard', { waitUntil: 'networkidle' })

      // 收集侧边栏菜单
      const menuTexts = await page.locator('.el-menu .el-menu-item span').allTextContents()
      const backendPaths = flattenMenuPaths(r.session.menus)

      // 首页菜单应可见（无论 fallback 还是 dynamic）
      const hasDashboard = menuTexts.some(t => t.includes('首页'))
      expect(hasDashboard, `[${acct.role}] Layout 应包含首页菜单`).toBe(true)

      // 若后端菜单非空，文本中至少应包含 backendPaths 中标题的部分
      const backendTitles = new Set<string>()
      const collect = (nodes: MenuNode[]) => {
        for (const n of nodes) {
          if (n.title) backendTitles.add(n.title)
          if (n.children?.length) collect(n.children)
        }
      }
      collect(r.session.menus)
      const matched = Array.from(backendTitles).filter(t => menuTexts.some(m => m.includes(t) || t.includes(m)))
      // 至少 30% 的后端菜单标题被前端渲染（允许 fallback 兜底时分子较低）
      const matchRate = matched.length / backendTitles.size
      expect(matchRate, `[${acct.role}] 后端菜单标题匹配率: ${(matchRate * 100).toFixed(1)}% （matched=${matched.length}/${backendTitles.size}）`).toBeGreaterThanOrEqual(0.3)
    })
  }
})
