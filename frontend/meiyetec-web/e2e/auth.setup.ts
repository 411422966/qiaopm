// P2.11 E2E — 认证 setup
//
// 通过生产 /api/auth/login 接口建立真实登录态，
// 将 token + user + rbac.session.v1 注入到 localStorage，
// 生成 Playwright storageState 文件供后续测试复用。
//
// 注意：不绕过任何生产认证守卫，仅复用前端的真实存储位置。
// storageState 会在 storageStatePath 指定的文件路径下生成。
//
// P0-T10：CI 严格模式 — 任何 /me/permissions 失败都直接抛错，
// 禁止 fallback RBAC session（不允许 skipped passed 隐藏真实失败）。

import { test as setup, expect } from '@playwright/test'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const BASE_URL = process.env.BASE_URL || 'http://localhost:5173'
const LOGIN_USERNAME = process.env.E2E_USERNAME || 'admin'
const LOGIN_PASSWORD = process.env.E2E_PASSWORD || '123456'

const STORAGE_STATE_PATH = path.join(__dirname, '.auth', 'admin.storage.json')

setup('authenticate via real /auth/login', async ({ page, request }) => {
  // 1) 直接调用后端登录接口（绕过 UI 表单，更稳定且不依赖 UI 渲染顺序）
  const resp = await request.post(`${BASE_URL}/api/auth/login`, {
    headers: { 'Content-Type': 'application/json' },
    data: { username: LOGIN_USERNAME, password: LOGIN_PASSWORD }
  })

  // 2) 校验登录响应
  expect(resp.status(), 'login HTTP status').toBe(200)
  const json = await resp.json()
  expect(json.code, 'login business code').toBe(200)
  expect(json.data?.token, 'login token').toBeTruthy()

  const token = json.data.token as string
  const userInfo = {
    id: json.data.id,
    username: json.data.username,
    realName: json.data.realName
  }

  // 3) 拉取当前会话的权限码 + 菜单树（与前端 stores/permission.refresh 行为一致）
  // P0-T10：CI 严格模式 — 失败即抛错，不接受任何 fallback RBAC session
  const permResp = await request.get(`${BASE_URL}/api/auth/me/permissions`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  expect(permResp.status(), '/auth/me/permissions HTTP status').toBe(200)
  const permJson = await permResp.json()
  expect(permJson.code, '/auth/me/permissions business code').toBe(200)
  const rbacSession: any = permJson.data

// 4) 在 origin 上把 localStorage 写好（router/index.ts:114 守卫读取 token）
  //    这里必须先打开同源页面再 addInitScript，因为 localStorage 按 origin 隔离。
  await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded' })

  // addInitScript 让后续每次 navigation 都重新写入，避免某些页面 onMounted 触发
  // permission store refresh → 401 → request 拦截器清掉 token 的副作用。
  await page.addInitScript(
    ({ token, user, session }) => {
      const t = localStorage.getItem('token')
      if (!t || t !== token) {
        localStorage.setItem('token', token)
        localStorage.setItem('user', JSON.stringify(user))
        localStorage.setItem('rbac.session.v1', JSON.stringify(session))
      }
      sessionStorage.setItem('traceId', 'e2e-setup-' + Date.now())
    },
    { token, user: userInfo, session: rbacSession }
  )

  // 显式再写一次，确保初始 load 时已就绪
  await page.evaluate(
    ({ token, user, session }) => {
      localStorage.setItem('token', token)
      localStorage.setItem('user', JSON.stringify(user))
      localStorage.setItem('rbac.session.v1', JSON.stringify(session))
      sessionStorage.setItem('traceId', 'e2e-setup-' + Date.now())
    },
    { token, user: userInfo, session: rbacSession }
  )

  // 5) 保存 storageState
  await page.context().storageState({ path: STORAGE_STATE_PATH })
})
