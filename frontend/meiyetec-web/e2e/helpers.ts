// Playwright 测试辅助 matcher 扩展
// 提供 toBeCloseToNumber：处理浮点数比较（金额测试用）

import { expect } from '@playwright/test'

expect.extend({
  toBeCloseToNumber(received: number | undefined, expected: number, epsilon = 0.01) {
    if (received === undefined || received === null) {
      return {
        pass: false,
        message: () => `expected ${expected}, but received was ${received}`
      }
    }
    const diff = Math.abs(received - expected)
    const pass = diff <= epsilon
    return {
      pass,
      message: () =>
        pass
          ? `expected ${received} not to be close to ${expected}`
          : `expected ${received} to be close to ${expected} (diff=${diff}, epsilon=${epsilon})`
    }
  }
})
