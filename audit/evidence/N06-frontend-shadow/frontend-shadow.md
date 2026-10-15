N6 Frontend Shadow evidence (Phase 5A 2026-09-16 02:55)
=============================================

grep pattern: http\.(get|post|put|delete).*(/mkt/)

| 文件 | 命中数 |
|---|---|
| coupon.vue | 8 |
| touch-task.vue | 8 |
| journey.vue | 7 |
| channel.vue | 5 |
| promotion.vue | 5 |
| pricing.vue | 5 |
| points.vue | 4 |
| template.vue | 3 |
| conversion.vue | 3 |
| effect.vue | 2 |
| **合计** | **50** |

--- 关键发现 ---
1. **0 个 vue 文件 import api/mkt** （封装层文件不存在）
2. 测试 False -> api/mkt.ts 存在？

--- 用户决策 (2026-09-16 01:05) ---
I069 NEW 决策: 登记，不修，与 I064 合并同类 (Frontend Shadow / 封装层违反)
Post-Audit 统一治理: 建 frontend/meiyetec-web/src/api/mkt.ts 集中导出 + 类型化

--- 风险评估 ---
| 维度 | 评估 |
|---|---|
| 功能性影响 | 0 — 所有端点工作 |
| 维护成本 | HIGH — 后端改 URL/参数时需改 10 个 vue 文件 |
| 接口漂移风险 | HIGH — 后端字段重命名 / 端点重命名时可能继续裸调不同形式 |
| TypeScript 类型校验 | 0 — 无类型约束，靠运行时人工对齐 |
