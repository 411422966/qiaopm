import { defineConfig, devices } from '@playwright/test'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const STORAGE_STATE_PATH = path.join(__dirname, 'e2e', '.auth', 'admin.storage.json')

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html', { outputFolder: 'e2e/reports' }],
    ['list']
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  // Phase 6 架构：
  //   setup      → 通过真实 /api/auth/login 生成 storageState
  //   chromium   → 使用 storageState 跑业务 Golden Path
  //   Mobile Chrome → 使用 storageState 跑业务 Golden Path（移动端）
  //   unauthenticated-chromium → 不使用 storageState 跑登录页测试
  //   unauthenticated-mobile   → 不使用 storageState 跑登录页测试（移动端）
  //   P0-E2E-001: 新增 full-flow.spec.ts（会员→预约→签到→收银→退款 全业务闭环）
  projects: [
    {
      name: 'setup',
      testMatch: /.*\.setup\.ts/
    },
    {
      name: 'chromium',
      testMatch: /.*(golden-path|smoke|full-flow|new-modules|business-flow)\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: STORAGE_STATE_PATH
      },
      dependencies: ['setup']
    },
    {
      name: 'Mobile Chrome',
      testMatch: /.*(golden-path|smoke|full-flow|new-modules|business-flow)\.spec\.ts/,
      use: {
        ...devices['Pixel 5'],
        storageState: STORAGE_STATE_PATH
      },
      dependencies: ['setup']
    },
    {
      name: 'role-menu',
      // 5 角色菜单可见性：每个角色独立登录，不复用 admin storageState
      testMatch: /.*role-menu\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome']
      }
    },
    {
      name: 'unauthenticated-chromium',
      testMatch: /.*(auth|auth-logout|auth-userinfo|auth-refresh|auth-session-recovery|login-log|rbac-permissions|rbac-menu)\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome']
      }
    },
    {
      name: 'unauthenticated-mobile',
      testMatch: /.*(auth|auth-logout|auth-userinfo|auth-refresh|auth-session-recovery|login-log|rbac-permissions|rbac-menu)\.spec\.ts/,
      use: {
        ...devices['Pixel 5']
      }
    },
    // QPM-005 · P0 财务安全 E2E（API-driven，仅跑后端 API，不依赖前端 UI）
    {
      name: 'p0-financial-safety',
      // 包含 qpm-005-03 套餐 / qpm-005-04 钱包 / qpm-005-05 对账 / qpm-006 生命周期
      testMatch: /.*p0-(financial-safety|wallet|reconciliation|lifecycle)\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: STORAGE_STATE_PATH
      },
      // 资金安全测试必须 sequential（避免 worker 间的并发写竞争影响余额差额校验）
      fullyParallel: false,
      workers: 1,
      dependencies: ['setup']
    }
  ],
  webServer: process.env.CI ? undefined : {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000
  }
})
