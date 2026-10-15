/**
 * Helper: 读取 global-setup.ts 写入的 token，给 API 直调测试用。
 *
 * 用法：
 *   import { TOKEN } from './auth-token'
 *   const res = await request.get(url, { headers: { Authorization: `Bearer ${TOKEN}` } })
 *
 * 必须先跑 global-setup（Playwright 启动时自动执行）：
 *   npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts
 */

import { readFileSync, existsSync } from 'fs'
import { join } from 'path'

const TOKEN_FILE = join(__dirname, '.token.json')

function loadToken(): string {
  if (!existsSync(TOKEN_FILE)) {
    throw new Error(
      `[auth-token] .token.json 不存在: ${TOKEN_FILE}\n` +
      `  原因：global-setup 未跑。Playwright config 已配 globalSetup，正常启动会自动生成。\n` +
      `  如果你直接 ts-node 跑本文件，请先: cd audit/scripts/e2e && npx playwright test --list`
    )
  }
  let json: any
  try {
    json = JSON.parse(readFileSync(TOKEN_FILE, 'utf-8'))
  } catch (e: any) {
    throw new Error(`[auth-token] .token.json 解析失败: ${e?.message}`)
  }
  if (!json?.token || typeof json.token !== 'string') {
    throw new Error(`[auth-token] .token.json 缺少 "token" 字段`)
  }
  return json.token
}

export const TOKEN = loadToken()
