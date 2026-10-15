import { defineConfig } from '@playwright/test'
import { resolve } from 'path'

// Audit E2E standalone config (no dependency on frontend project).
// Run from frontend/meiyetec-web/:
//   npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts
//   npx playwright test --config=../../audit/scripts/e2e/playwright.config.ts --list
// Options:
//   FRONTEND_URL  baseURL (default http://localhost:5173)
//   BACKEND_URL   backend base (default http://localhost:8080)
//   E2E_USER      login user (default admin)
//   E2E_PASS      login pass (default 123456)
//   HEADED=1      headed browser (default headless)
//
// Auth wiring:
//   - global-setup.ts logs in once → writes .auth-state.json (storageState) + .token.json
//   - UI specs auto get localStorage.token via storageState → axios interceptor adds Bearer
//   - API specs import TOKEN from ./auth-token.ts and add Authorization header manually
//     (APIRequestContext does NOT inherit storageState localStorage)
//
// ⚠️ storageState / token 文件路径必须**绝对**：
//   global-setup.ts 用 __dirname 写到 audit/scripts/e2e/；
//   storageState 若用相对路径会被 Playwright 解析到 cwd（用户跑命令的目录）→ ENOENT。

const CONFIG_DIR = __dirname
const STATE_FILE = resolve(CONFIG_DIR, '.auth-state.json')

export default defineConfig({
  globalSetup: require.resolve('./global-setup'),
  testDir: '.',
  testMatch: /\.spec\.ts$/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  reporter: [['list']],
  use: {
    baseURL: process.env.FRONTEND_URL || 'http://127.0.0.1:5173',
    headless: !process.env.HEADED,
    viewport: { width: 1280, height: 720 },
    storageState: STATE_FILE,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 10000,
    navigationTimeout: 15000
  }
})
