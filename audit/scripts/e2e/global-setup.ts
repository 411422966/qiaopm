/**
 * Audit E2E global setup.
 *
 * Login once via /auth/login and persist:
 *  - .auth-state.json  → Playwright storageState (UI specs auto inject localStorage.token)
 *  - .token.json       → raw token for APIRequestContext (API specs read ./auth-token.ts)
 *
 * Run order (Playwright):
 *   1. load playwright.config.ts
 *   2. run this global-setup.ts
 *   3. start workers; each browser context loads storageState from .auth-state.json
 *
 * Required env (with defaults):
 *   BACKEND_URL   http://127.0.0.1:8080
 *   FRONTEND_URL  http://127.0.0.1:5173
 *   E2E_USER      admin
 *   E2E_PASS      123456
 */

import { request } from '@playwright/test'
import { writeFileSync } from 'fs'
import { join } from 'path'

const BACKEND  = (process.env.BACKEND_URL  || 'http://127.0.0.1:8080').replace(/\/+$/, '')
const FRONTEND = (process.env.FRONTEND_URL || 'http://127.0.0.1:5173').replace(/\/+$/, '')
const USER     = process.env.E2E_USER || 'admin'
const PASS     = process.env.E2E_PASS || '123456'

export default async function globalSetup() {
  const ctx = await request.newContext({ baseURL: BACKEND })

  let res
  try {
    res = await ctx.post('/auth/login', {
      data: { username: USER, password: PASS }
    })
  } catch (e: any) {
    await ctx.dispose()
    throw new Error(`[global-setup] login 网络失败: ${e?.message || e}; BACKEND=${BACKEND}`)
  }

  if (!res.ok()) {
    const txt = await res.text()
    await ctx.dispose()
    throw new Error(
      `[global-setup] login HTTP ${res.status()}: ${txt.slice(0, 200)}\n` +
      `  USER=${USER} BACKEND=${BACKEND}\n` +
      `  可能原因：账号密码错 / 账号锁定（5次失败锁15分钟）/ 后端未启动`
    )
  }

  const body = await res.json()
  if (body.code !== 200 || !body.data?.token) {
    await ctx.dispose()
    throw new Error(
      `[global-setup] login 业务失败: code=${body.code} message=${body?.message}\n` +
      `  USER=${USER}`
    )
  }

  const token = body.data.token as string
  const user = {
    id: body.data.id,
    username: body.data.username,
    realName: body.data.realName
  }

  // 1. storageState for browser context (UI specs)
  const storageState = {
    cookies: [],
    origins: [
      {
        origin: FRONTEND,
        localStorage: [
          { name: 'token', value: token },
          { name: 'user',  value: JSON.stringify(user) }
        ]
      }
    ]
  }
  writeFileSync(join(__dirname, '.auth-state.json'), JSON.stringify(storageState, null, 2))

  // 2. token cache for APIRequestContext (API specs)
  writeFileSync(
    join(__dirname, '.token.json'),
    JSON.stringify({ token, user, issuedAt: new Date().toISOString() }, null, 2)
  )

  await ctx.dispose()
  // eslint-disable-next-line no-console
  console.log(`[global-setup] OK user=${USER} token.len=${token.length} state→.auth-state.json`)
}
