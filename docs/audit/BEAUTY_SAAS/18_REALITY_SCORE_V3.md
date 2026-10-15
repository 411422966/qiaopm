# 18_REALITY_SCORE_V3

> **基线建立日期**: 2026-08-13
> **审计目的**: 在 V2 报告基础上重新核验分数，去除乐观估计，区分当前/修复后/补齐后 PROJECTED 分数

---

## 0. 评分方法论（V3 重新定义）

每个维度按"证据完整度"评分（0-100%）：

| 等级 | 标准 |
|------|------|
| 🟢 100 | 五层证据齐全（UI + API + Backend + DB + Test） |
| 🟡 50-80 | 三层证据，有缺口 |
| 🟠 20-50 | 一层证据，多处缺口 |
| 🔴 0-20 | 仅代码存在，无运行证据 |

**权重分配**（合计 100%）：

| 维度 | 权重 | V3 重新定义 |
|------|------|-------------|
| Feature Reality | 12% | 214 功能项的真实可运行比例 |
| Business Logic Reality | 15% | 业务逻辑真实下沉（避免空壳 Service） |
| Data Reality | 15% | DDL 完整性 + 运行时 DB 状态 + 漂移评估 |
| API Reality | 12% | 端点存在 + 业务实现 + 权限控制 |
| Runtime Reality | 15% | 10 个核心 API 验证 + 真实可调通 |
| Transaction Safety | 12% | 事务边界 + 乐观锁 + 幂等性 |
| Test Reality | 10% | 单测 + 集成 + API + E2E + 并发 |
| E2E Reality | 9% | 前端 → 后端 → DB 全链路 |

---

## 1. 各维度评分（V3）

### 1.1 Feature Reality (12%)

| 指标 | 数值 | 评分 |
|------|------|------|
| 功能项总数 | 214 | — |
| 🟢 REAL 五层齐全 | 24 | 11% |
| 🟡 PARTIAL 三层 | 138 | 65% |
| 🟠 SHELL/MOCK | 52 | 24% |
| 🔴 MISSING | 0 | 0% |

**维度评分**：24/214 + 138×0.5/214 + 52×0.1/214 = (24 + 69 + 5.2) / 214 = 45.9% → 取 46%

### 1.2 Business Logic Reality (15%)

| 指标 | 数值 |
|------|------|
| Service 总数（service/*.java 接口） | 53 |
| ServiceImpl 总数 | 25 |
| 空壳 Impl（仅 `extends ServiceImpl`） | **16** |
| 含真实业务 Impl | 9 |
| Controller 直接调用 Mapper 的次数 | 24 处（PosController 4, StockController 6, ReportController 8, CommissionController 5, DcepConfig 1）|

**评分**：9/25 = 36%（真实业务下沉率）

### 1.3 Data Reality (15%)

| 指标 | 评分 |
|------|------|
| DDL 完整性 | 75%（112 表存在 DDL）|
| Schema Drift 表数 | **5**（crm_wallet, crm_wallet_transaction, biz_booking, biz_room, biz_employee_schedule） |
| FK 完整性 | 0%（0 条 FK）|
| 索引覆盖度 | 80%（主要查询字段均有索引）|
| 软删除一致性 | 70%（部分表有，部分缺失）|

**评分**：综合 (75% + 0% + 80% + 70%) / 4 = 56%（drift 扣分）

### 1.4 API Reality (12%)

| 指标 | 数值 |
|------|------|
| 总 endpoints | 180+ |
| 真实业务实现 | ~150 |
| 权限控制（@RequirePermission）覆盖 | ~70% |
| 文档/前端调用对齐 | ~85% |

**评分**：~75%

### 1.5 Runtime Reality (15%)

| 指标 | 数值 | 证据 |
|------|------|------|
| 后端服务可启动 | ✅ | backend-current.log 滚动中 |
| 数据库可连接 | ✅ | meiyetec schema 存在 |
| 核心 API 验证 | 10/10 | test-*.ps1 输出 |
| Wallet API 可调通 | ⚠️ 部分 | 历史 500 错误后通过 fix 修复 |
| Booking API 可调通 | ⚠️ | 历史 500 错误后通过 fix 修复 |

**评分**：~80%（依赖 ad-hoc 修复后可运行）

### 1.6 Transaction Safety (12%)

| 指标 | 评分 |
|------|------|
| @Transactional 覆盖 | ~80% |
| 乐观锁实现 | ✅ wallet/package/inventory/stock 均有 version |
| 幂等性 | ✅ Idempotency-Key + DB UNIQUE 双重 |
| 事务边界 | 🟠 部分场景（inventory + order 同生共死但 refund 双轨）|
| 双轨一致性问题 | 🟠 crm_member.balance vs crm_wallet.balance |

**评分**：~70%

### 1.7 Test Reality (10%)

| 指标 | 数值 |
|------|------|
| 测试类 | 4 |
| 测试方法 | 8 |
| 集成测试 | 2（ConcurrencyIntegrationTest, PosRefundIntegrationTest）|
| 单元测试 | 2（RefundStatusTest, BizCodeTest）|
| 并发测试 | 1（10 并发库存）|
| E2E | 0 |

**评分**：~25%（集中在两个模块，其余空白）

### 1.8 E2E Reality (9%)

| 指标 | 评分 |
|------|------|
| E2E 框架 | ❌ 无 Cypress/Playwright |
| 前端调用后端 | ✅ 已在 controller 测试 |
| 后端调数据库 | ✅ |
| 全链路断言 | ❌ |

**评分**：5%（仅有零散脚本验证 `test-*.ps1` 间接覆盖）

---

## 2. Reality Score V3

| 维度 | 权重 | V3 评分 | 加权 |
|------|------|---------|------|
| Feature Reality | 12% | 46% | 5.5 |
| Business Logic Reality | 15% | 36% | 5.4 |
| Data Reality | 15% | 56% | 8.4 |
| API Reality | 12% | 75% | 9.0 |
| Runtime Reality | 15% | 80% | 12.0 |
| Transaction Safety | 12% | 70% | 8.4 |
| Test Reality | 10% | 25% | 2.5 |
| E2E Reality | 9% | 5% | 0.45 |
| **合计** | **100%** | — | **51.65% ≈ 52%** |

### 2.1 V2 vs V3 对比

| 维度 | V2 | V3 | 差异 |
|------|----|----|------|
| Reality Score | 43.3% | **52%** | +8.7 |
| Feature Reality | 28% | 46% | +18 |
| Business Logic Reality | 19% | 36% | +17 |
| Data Reality | 100% | 56% | -44 ⚠️ V2 高估 |
| API Reality | 72% | 75% | +3 |
| Runtime Reality | 100% | 80% | -20 ⚠️ V2 高估 |
| Transaction Safety | — | 70% | 新增 |
| Test Reality | 5% | 25% | +20 ⚠️ V2 低估 |
| E2E Reality | 0% | 5% | +5 |

**V2 主要偏差**：
- Data Reality V2 给 100% 但实际只有 56%（SCHEMA_DRIFT 扣分）
- Runtime Reality V2 给 100% 但实际只有 80%（依赖 ad-hoc 修复）
- Test Reality V2 给 5% 但实际有 25%（已有 2 个完整集成测试）

---

## 3. PROJECTED 分数

> 以下分数为预测值，**不是当前 Reality**。明确标记 PROJECTED。

### 3.1 完成 P0 修复后 PROJECTED

| 任务 | 维度影响 | PROJECTED 分数 |
|------|----------|----------------|
| TASK-P0-DB-001（迁移脚本整合 wallet/booking/room/schedule DDL） | Data Reality +20 | +3.0 |
| TASK-P0-DB-002（增加 8 条 P0 FK） | Data Reality +20 | +3.0 |
| TASK-P0-MONEY-001（DCEP 替换为银行 SDK 或强制 MOCK 仅 dev） | Transaction Safety +15 | +1.8 |
| TASK-P0-MONEY-002（消除 crm_member.balance vs crm_wallet.balance 双轨） | Transaction Safety +10 | +1.2 |
| TASK-P0-MONEY-003（强随机 ID 改雪花算法） | Transaction Safety +5 | +0.6 |

**修复 P0 后 PROJECTED**：52% + 9.6 ≈ **62%**

### 3.2 补齐 Golden Path 后 PROJECTED

| 任务 | 影响 |
|------|------|
| TASK-P0-TEST-001（Wallet 并发 + Booking 冲突测试） | Test Reality +30% |
| TASK-P0-TEST-002（Commission + Report 单元测试） | Test Reality +20% |
| TASK-P0-E2E-001（Cypress/Playwright 配置 + 1 个完整 E2E） | E2E Reality +50% |

**补齐后 PROJECTED**：62% + 5 ≈ **67%**

### 3.3 全面 E2E 后 PROJECTED

| 任务 | 影响 |
|------|------|
| TASK-P1-E2E-001（5 个核心流程 E2E） | E2E Reality +90% |
| TASK-P1-LOGIC-001（空壳 Service 业务下沉） | Business Logic Reality +30% |
| TASK-P1-FEATURE-001（功能完整性补齐） | Feature Reality +20% |

**全面 E2E 后 PROJECTED**：67% + 12 ≈ **79%**

---

## 4. 关键洞察

- 当前 **52%**：基础业务可用，但测试覆盖、E2E、DCEP 真实化仍是短板
- **PROJECTED 62%**：修复 P0 后达到 6 成可用
- **PROJECTED 79%**：全面补齐后达到 8 成可用
- 完全 100% 不可能（AI/报表/营销自动化等模块天然留扩展位）

---

**审计完成时间**: 2026-08-13 23:25
**文档版本**: 3.0