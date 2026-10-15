# Enterprise Software Development Playbook

> **版本**：v1.0 | **日期**：2026-08-15  
> **来源**：美业 SaaS 项目实战全量复盘（美业 SaaS · 27 模块 · Spring Boot + Vue 3 · 118 表 · 47 脚本）  
> **适用**：企业级 SaaS / 管理后台 / 中台业务系统  
> **核心理念**：**模块闭环 × 资金安全 × 多租户优先 × AI 加速 × 实测驱动**

---

## 文档概览

本文档从"美业 SaaS"项目（约 18 个月、27 个业务模块、3 人规模）中提炼，是一份**经过实测验证**的企业级软件开发方法论。不是理论框架，而是从真实 bug、真实返工、真实 P0 事故中生长出来的实战手册。

### 阅读指南

| 章节 | 核心问题 | 受众 |
|------|---------|------|
| 一、项目开发生命周期 | 从零到上线分哪些阶段？每个阶段的输入输出是什么？ | 所有人 |
| 二、架构设计经验 | 哪些架构决策是对的？哪些是血的教训？ | Tech Lead / 架构师 |
| 三、需求到代码流程 | 一个需求如何变成高质量代码？ | 全栈工程师 |
| 四、AI 辅助开发最佳流程 | Cursor + AI 如何配合才能真正提效？ | 所有人 |
| 五、代码质量规范 | Backend / Frontend 每一层的标准是什么？ | 开发者 |
| 六、数据库设计经验 | 企业级系统的数据库设计方法论 | 全栈 / DBA |
| 七、测试体系策略 | 新项目如何设计测试策略？ | QA / 开发者 |
| 八、项目避坑指南 | 18 个月踩过的坑，条条致命 | 所有人 |
| 九、新项目启动模板 | Day 1–5 的标准动作 | Tech Lead |

---

## 一、项目开发生命周期总结

### 1.1 完整生命周期图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    企业级 SaaS 开发 9 阶段全生命周期                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Phase 0  产品定义     ── 四个灵魂拷问 / MVP 三层划分 / 成功指标              │
│       ↓                                                                  │
│  Phase 1  产品设计     ── 信息架构(IA) / 页面流程 / 领域模型 / 原型           │
│       ↓                                                                  │
│  Phase 2  技术架构     ── 技术选型 / 分层设计 / API 规范 / ER 图              │
│       ↓                                                                  │
│  Phase 3  工程准备     ── Docker 环境 / 脚手架 / 规范文档 / Git 初始化        │
│       ↓                                                                  │
│  Phase 4  模块开发     ── 5 步闭环 × 依赖排序 × 并行推进                     │
│       ↓                                                                  │
│  Phase 5  质量保障     ── 四层测试 / CR / 安全扫描 / 冒烟测试                 │
│       ↓                                                                  │
│  Phase 6  集成验证     ── 跨模块端到端 / 性能 / 租户隔离                     │
│       ↓                                                                  │
│  Phase 7  发布运维     ── CI/CD / 灰度 / 监控 / 回滚                        │
│       ↓                                                                  │
│  Phase 8  商业运营     ── 客户开通 / 培训 / 反馈驱动迭代                      │
│                                                                             │
│  ←─────────────────── 生命周期闭环 ───────────────────────                 │
│                     客户反馈 → 下一版本                                       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 每阶段详情

#### Phase 0：产品定义

| 要素 | 内容 |
|------|------|
| **目标** | 回答"做对的事"——不做全功能，做最小可行价值 |
| **输入** | 市场调研 / 客户访谈 / 竞品分析 |
| **输出** | PRD（MVP 三层：Must / Should / Later）/ 成功指标（上线 30 天） |
| **常见风险** | 功能蔓延：Must Have > 5 个核心流程 → 项目失控 |
| **最佳实践** | 四个灵魂拷问：用户是谁？痛点是什么？差异化在哪？为什么现在做？ |

#### Phase 1：产品设计

| 要素 | 内容 |
|------|------|
| **目标** | 设计先行，编码后动——在纸上/原型工具里把逻辑跑通 |
| **输入** | PRD / 成功指标 |
| **输出** | IA 信息架构图 / 页面流程 / 用户操作流 / 领域模型草图 / 原型 |
| **常见风险** | 直接画 UI，跳过领域建模 → 表结构逻辑混乱 |
| **最佳实践** | 先做 IA → 画 User Flow → 做轻量级领域建模（实体/值对象/聚合根）→ 原型确认 |

#### Phase 2：技术架构

| 要素 | 内容 |
|------|------|
| **目标** | 定技术栈 / 定分层 / 定 API 规范 / 出 ER 图 |
| **输入** | 原型 / 领域模型 |
| **输出** | 技术选型表 / 分层架构图 / API 设计文档（含 BizCode 枚举）/ ER 图 |
| **常见风险** | API 规范不提前定 → 联调阶段返工；不画 ER 图 → 数据库设计随意 |
| **最佳实践** | Phase 2 结束时必须输出：可执行的 SQL 建表脚本 + BizCode.java 枚举文件 |

#### Phase 3：工程准备

| 要素 | 内容 |
|------|------|
| **目标** | 磨刀不误砍柴工——环境搭好、代码骨架搭好、规范文档化 |
| **输入** | 技术选型 / 分层架构 |
| **输出** | Docker 环境 / 前后端脚手架 / 规范文档（Git 策略/API 规范/命名）/ 测试账号 |
| **常见风险** | 环境不一致 → 你的代码在我机器上跑不通 |
| **最佳实践** | docker-compose.yml 一键启动所有依赖；所有规范写在 `docs/CODING_STANDARDS.md` 中 |

#### Phase 4：模块开发（核心）

| 要素 | 内容 |
|------|------|
| **目标** | 每个模块前端 + 后端 + 数据库全链路打通，才能进入下一个模块 |
| **输入** | 模块功能清单 / API 列表 / ER 图 |
| **输出** | 数据库脚本 / 后端 API（含单元测试）/ 前端页面 / 联调记录 |
| **常见风险** | 只做完数据库就觉得模块完成了；前后端分离开发后不联调 |
| **最佳实践** | 见"模块 5 步闭环"（Phase 4.2 详述） |

#### Phase 5：质量保障

| 要素 | 内容 |
|------|------|
| **目标** | 代码质量不是最后一步，是每一步——问题发现越早，修复成本越低 |
| **输入** | 模块代码 / 测试用例 |
| **输出** | 测试报告 / CR 意见 / 安全扫描报告 / 技术债清单 |
| **常见风险** | 不写单元测试 → 改 A 模块坏 B 模块；跳过 CR 直接合并 |
| **最佳实践** | 每个 PR 必须包含：单元测试 + CR + lint 检查，三者缺一不可 |

#### Phase 6：集成验证

| 要素 | 内容 |
|------|------|
| **目标** | 验证模块间协作正确性——每个模块独立 OK 不代表组合起来 OK |
| **输入** | 所有模块 / 测试脚本 |
| **输出** | 冒烟测试报告 / 租户隔离验证报告 / 性能报告 |
| **常见风险** | 跳过集成测试直接上线 → 跨模块 bug 在生产爆 |
| **最佳实践** | 必须有 PowerShell/API 脚本覆盖核心链路，且不能有 `test.skip()` 兜底跳过业务逻辑 |

#### Phase 7：发布运维

| 要素 | 内容 |
|------|------|
| **目标** | 上线不是终点，是开始——发布体系决定了你能不能睡好觉 |
| **输入** | 冒烟测试通过 / 技术债清单 / 回滚方案 |
| **输出** | 灰度策略 / 监控配置 / 回滚脚本 / 值班计划 |
| **常见风险** | 无回滚方案 → 生产事故无法快速恢复；不回滚脚本化 |
| **最佳实践** | 回滚必须在 5 分钟内完成，脚本化，演练过 |

#### Phase 8：商业运营

| 要素 | 内容 |
|------|------|
| **目标** | 软件写完没人用 = 零价值 |
| **输入** | 上线版本 / 用户行为数据 |
| **输出** | 客户成功指标 / 反馈池 / Roadmap 更新 |
| **常见风险** | 上线后不追踪 → 问题被隐藏直到客户流失 |
| **最佳实践** | 设定激活率 ≥ 60% / 30 天留存 ≥ 40% / NPS ≥ 30 三个黄金指标 |

---

## 二、架构设计经验提炼

### 2.1 为什么采用当前架构？

**美业 SaaS 的核心业务特征**决定了架构选择：

```
业务特征                          架构响应
──────────────────────────────────────────────────────────
高频预约 + 时段冲突检测        →  悲观锁(FOR UPDATE) + 内存锁
多租户数据隔离                 →  TenantContextHolder + 拦截器自动注入
储值/钱包（资金安全）          →  SSOT(单一真相来源) + CAS + 内存锁
数字人民币多通道（CCB/ABC/ICBC）→  Adapter Factory 模式
跨店会员 + 总部看板            →  HQ/Brand/Store 三级模型
数据查询性能                   →  CQRS-lite（读写分离的查询模型）
```

**架构选型原则**：选主流、选熟悉、选生态好。不追新技术。

| 层级 | 技术选型 | 选型理由 |
|------|---------|---------|
| 前端框架 | Vue 3 + Vite + Element Plus | 团队熟悉、组件丰富、TypeScript 支持好 |
| 后端框架 | Java 17 + Spring Boot 3 + MyBatis-Plus | 稳态业务首选、生态成熟、团队经验积累 |
| 数据库 | MySQL 8 + Redis 7 | 主库+缓存黄金组合，足够应对中小规模 SaaS |
| 认证 | JWT + TenantContextHolder | 无状态认证 + 多租户上下文注入 |
| 资金处理 | 钱包 SSOT + CAS + 内存锁 | 钱不能错，必须有最强的并发保护 |

### 2.2 正确的架构决策

#### ✅ 决策 1：模块按业务分包，不按层分包

```
错误： controller/ service/ entity/ mapper/
正确： auth/         booking/       pos/         wallet/
              ├─ controller       ├─ controller  ├─ controller
              ├─ service         ├─ service     ├─ service
              ├─ entity          ├─ entity      ├─ entity
              └─ mapper          └─ mapper      └─ mapper
```

**效果**：一个模块的代码集中，删除/交接/Code Review 都方便。改动一个模块不会意外影响另一个模块。

#### ✅ 决策 2：Money 的单一真相来源（SSOT）

```java
// 钱包是唯一的余额真相来源——禁止任何地方直接写余额
WalletServiceImpl.doRecharge() {
    // 1. CAS 更新余额（乐观锁）
    // 2. 写流水（balance_before + balance_after）
    // 3. 同步会员表 balance 字段
    // 4. 如果失败 → AUTO_FIX 自动修复
}
```

**效果**：历史上有 13400 元的钱包漂移，通过 SSOT + AUTO_FIX 才收敛。当前生产钱包链路零漂移。

#### ✅ 决策 3：Adapter Factory 模式处理多通道支付

```java
// 数字人民币三通道（CCB/ABC/ICBC）统一接口
public interface DcepAdapter {
    PrepayResult prepay(PrepayRequest req);
    QueryResult query(String transactionId);
    RefundResult refund(RefundRequest req);
    CloseResult close(String transactionId);
}

public class DcepAdapterFactory {
    public static DcepAdapter getAdapter(String bankCode) {
        return switch (bankCode) {
            case "CCB" -> new CcbDcepAdapter();
            case "ABC" -> new AbcDcepAdapter();
            case "ICBC" -> new IcbcDcepAdapter();
            default -> throw new UnsupportedOperationException();
        };
    }
}
```

**效果**：新增通道只需实现接口 + 注册 Factory，不破坏现有代码。

#### ✅ 决策 4：TenantContextHolder 贯穿全链路

```java
// 拦截器统一注入，Service/Mapper 无需手动传 tenant_id
public class TenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        Long tenantId = extractFromToken(request);
        TenantContextHolder.setTenantId(tenantId);
        return true;
    }
    @Override
    public void afterCompletion(...) {
        TenantContextHolder.clear(); // 必须清理！
    }
}
```

#### ✅ 决策 5：幂等性基础设施在前端就做

```typescript
// 前端：每个写操作带唯一 ID
const idempotencyKey = crypto.randomUUID()
// 后端：Redis 存 key，60s 过期，防重复提交
@Cacheable(value = "idempotency", key = "#idempotencyKey")
public Result idempotentProcess(IdempotentDTO dto, @RequestHeader("X-Idempotency-Key") String key)
```

**效果**：收银台快速点击不会产生重复订单。

#### ✅ 决策 6：Domain Event 解耦业务

```java
// 支付完成 → 发布事件 → 各模块自行订阅
public void onOrderPaid(OrderPaidEvent event) {
    // 提成计算订阅此事件
    commissionService.calculate(event.getOrderId());
    // 会员积分订阅此事件
    pointsService.award(event.getMemberId(), event.getAmount());
    // 库存消耗订阅此事件
    inventoryService.consumeByOrder(event.getOrderId());
}
```

### 2.3 避免未来扩展问题的设计

| 设计点 | 当前做法 | 为未来预留 |
|--------|---------|----------|
| 权限码体系 | RBAC v3，精确到按钮级别 | 预留 `data_scope` 字段支持行级数据权限 |
| 提成计算 | 快照字段保存当时的 `employee_name` | 员工离职改名后报表不变 |
| 套餐核销 | 用 `source_type + source_id` 区分 POS/Package | 未来新增渠道不需改 schema |
| 数字人民币 | 三家银行统一接口 | 未来新增农行/交行只需加 adapter |

### 2.4 架构原则总览

```
┌─────────────────────────────────────────────────────────────────┐
│                     架构设计原则（必须遵守）                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  应该：                                                         │
│  ✅ 模块化：按业务分包，不按层分包                                 │
│  ✅ 多租户隔离：TenantContextHolder + 拦截器，DB 层必须统一处理    │
│  ✅ 资金安全：SSOT + CAS + 幂等 + 流水快照                        │
│  ✅ 适配器模式：多通道/多实现统一接口                             │
│  ✅ 领域事件：业务解耦，模块间通过事件通信                        │
│  ✅ 幂等优先：写操作从第一天就考虑幂等                            │
│  ✅ 快照字段：历史数据必须快照，不依赖实时 Join                    │
│  ✅ CI/CD 脚本化：发布、回滚、环境切换全脚本化                    │
│                                                                 │
│  不要：                                                         │
│  ❌ Controller 直接操作数据库（必须过 Service 层）                │
│  ❌ 业务逻辑散落在工具类或 Util 中                               │
│  ❌ 无边界的 Service（一个 Service 超过 500 行必须拆分）         │
│  ❌ 硬编码 tenant_id（如 setTenantId(1L)）                     │
│  ❌ 直接改余额而不写流水（资金类操作必须有 balance_before/after）  │
│  ❌ 跨模块直接调用（通过 Domain Event 解耦）                      │
│  ❌ Schema drift（代码里的 Entity 必须与数据库实时一致）          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、需求到代码转换流程

### 3.1 标准转换流程

```
需求文档（PRD）
     │
     ▼
业务分析 ──────────────────────────────┐
  ├─ 角色与场景分析                     │  → 产出：用户故事卡（3 行）
  ├─ 业务流程图                         │  → 产出：流程图 + 文字描述
  └─ 关键决策点                        │  → 产出：决策清单
     │
     ▼
领域模型 ──────────────────────────────┐
  ├─ 识别实体（Entity）                 │  → 产出：实体清单
  ├─ 识别值对象（VO）                  │  → 产出：值对象清单
  ├─ 识别聚合根（Aggregate Root）       │  → 产出：聚合关系图
  └─ 识别业务规则                       │  → 产出：业务规则文档
     │
     ▼
数据库设计 ────────────────────────────┐
  ├─ 画 ER 图                          │  → 产出：ER 图
  ├─ 定义表结构（含 tenant_id）        │  → 产出：CREATE TABLE SQL
  ├─ 确认索引                          │  → 产出：索引清单
  └─ 审计字段（created_at/updated_at）  │  → 产出：审计字段规范
     │
     ▼
API 设计 ──────────────────────────────┐
  ├─ 定义接口（REST）                  │  → 产出：API 文档
  ├─ 定义 BizCode 枚举                 │  → 产出：BizCode.java
  ├─ 定义 DTO                          │  → 产出：Request/Response DTO
  └─ 定义错误处理                      │  → 产出：GlobalExceptionHandler
     │
     ▼
任务拆分 ──────────────────────────────┐
  ├─ 按模块拆（DB → 后端 → 前端 → 联调）│  → 产出：Task 清单
  └─ 按依赖排序                        │  → 产出：开发顺序
     │
     ▼
编码实现 ──────────────────────────────┐
  ├─ 数据库（建表 + 索引 + 种子数据）   │  → 产出：init_xxx.sql
  ├─ 后端（Entity → Mapper → Svc → Ctrl）│  → 产出：Java 代码
  └─ 前端（API → Page → Router）       │  → 产出：Vue 代码
     │
     ▼
测试验证 ──────────────────────────────┐
  ├─ 单元测试（Service 核心逻辑）       │  → 产出：XXXTest.java
  ├─ 集成测试（Mapper SQL）             │  → 产出：XXXMapperTest.java
  ├─ API 测试（Postman / PowerShell）  │  → 产出：test-xxx.ps1
  └─ 联调测试（前后端真实场景）         │  → 产出：联调记录
     │
     ▼
验收通过 ✅
```

### 3.2 每一步需要的文档

| 步骤 | 必需文档 | 格式 |
|------|---------|------|
| 业务分析 | 用户故事卡 | `docs/stories/Mxx_xxx_story.md` |
| 领域模型 | 实体关系图 + 聚合根说明 | `docs/domain/Mxx_domain_model.md` |
| 数据库设计 | CREATE TABLE SQL（含注释） | `backend/sql/init_mxx_xxx.sql` |
| API 设计 | OpenAPI 3.0 文档 | `docs/api/Mxx_api.md` |
| BizCode 枚举 | 错误码枚举文件 | `backend/src/.../common/BizCode.java` |
| 任务拆分 | Task 清单（见模板） | `docs/tasks/Mxx_tasks.md` |
| 编码实现 | 符合规范的源码 | `backend/src/.../` |
| 测试验证 | 测试报告 + 截图 | `docs/test/Mxx_test_report.md` |

### 3.3 AI 辅助下的加速流程

```
PRD 输入
   │
   ▼
Cursor 分析需求 → 生成技术方案（5 分钟）
   │
   ▼
人工确认方案 → 拆 Task 清单
   │
   ▼
Cursor 实现 ───────────────────┐
  ├─ 生成建表 SQL ✓（AI 擅长）  │
  ├─ 生成 Entity + Mapper ✓     │
  ├─ 生成 Controller + Service  │  → 人工 Review 每一步
  ├─ 生成 Vue 页面（骨架）✓     │
  └─ 生成单元测试（核心逻辑）✓  │
     │                           │
     ▼                           │
人工 Review ─────────────────────┘
   │
   ▼
自动测试（Postman 脚本跑全部接口）
   │
   ▼
AI Code Review（发现潜在 bug）
   │
   ▼
人工最终确认 ✅
```

---

## 四、AI 辅助开发最佳流程

### 4.1 AI 擅长 vs 不擅长的领域

```
┌─────────────────────────────────────────────────────────────────┐
│                    AI 能力矩阵                                   │
├───────────────────────────────┬─────────────────────────────────┤
│  AI 擅长（直接用，效果好）      │  AI 不擅长（人工做，不要依赖 AI）  │
├───────────────────────────────┼─────────────────────────────────┤
│ 样板代码：Controller/Entity/   │ 业务决策：状态机/权限逻辑/资金计算  │
│ Vue 页面骨架                  │ 多租户隔离的边界判断              │
│ API 封装：request.ts/xxx.ts  │ 性能优化：N+1 问题/索引设计       │
│ 工具类：DateUtil/IdGenerator  │ 安全敏感：JWT 签名/支付回调验签    │
│ SQL 基础：CRUD 建表           │ 架构决策：模块划分/分层设计        │
│ 代码补全和重构                 │ 集成测试场景设计                  │
│ 解释代码逻辑                   │ Bug 根因分析（需运行时证据）       │
│ 测试用例生成                   │                                 │
│ 文档生成                       │                                 │
└───────────────────────────────┴─────────────────────────────────┘
```

### 4.2 AI Coding Workflow（标准流程）

```
┌─────────────────────────────────────────────────────────────────┐
│                   AI Coding Workflow v2.0                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Step 1：PRD 输入                                                │
│  └→ 把需求文档 + 上下文文件（ER 图/API 文档）一起给 AI           │
│                                                                 │
│  Step 2：技术方案生成（AI）                                       │
│  └→ "根据以下需求，生成 Mxx 模块的技术方案，包含：数据表/         │
│       API 列表/后端文件清单/前端文件清单"                        │
│  └→ AI 输出后人工确认，不对的地方立即纠正                         │
│                                                                 │
│  Step 3：Task 拆分                                               │
│  └→ 基于确认的技术方案，拆成最小可执行 Task（每个 Task ≤ 4h）     │
│                                                                 │
│  Step 4：Cursor 实现                                             │
│  └→ 分层实现，每层完成后 AI Review：                             │
│       ① 数据库 SQL → ② Entity/Mapper → ③ Service →              │
│       ④ Controller → ⑤ DTO/BizCode → ⑥ Vue 页面               │
│                                                                 │
│  Step 5：单元测试（AI 生成 + 人工完善）                          │
│  └→ AI 生成基础测试用例 → 人工补充边界条件和异常路径               │
│                                                                 │
│  Step 6：API 测试脚本（PowerShell/curl）                         │
│  └→ 每个 API 至少 1 个测试脚本，纳入 CI                          │
│                                                                 │
│  Step 7：Code Review（AI + 人工）                                │
│  └→ AI 扫安全问题/逻辑错误 → 人工确认修复                         │
│                                                                 │
│  Step 8：提交 Git（feature 分支）                                │
│                                                                 │
│  Step 9：合并前检查                                              │
│  └→ ✅ 单元测试全过  ✅ lint 通过  ✅ API 脚本全过  ✅ CR 通过   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Cursor 最佳实践

| 场景 | 最佳做法 |
|------|---------|
| 生成新模块代码 | 打开相关 Entity 文件 + API 文档 → 然后让 AI 生成 |
| 调试 bug | 打开报错文件 + 错误日志 → 让 AI 分析原因 |
| 重构代码 | 先让 AI 解释代码 → 确认理解正确后 → 让 AI 重构 |
| 生成 SQL | 直接给 AI 表结构 → 让 AI 生成 DDL + 种子数据 |
| 生成测试 | 打开被测 Service → 让 AI 生成测试用例 → 人工补充边界 |
| **不要这样做** | 打开空文件让 AI 从零生成整模块（缺少上下文，质量差） |

---

## 五、代码质量规范

### 5.1 Backend 规范

#### Controller 规范

```
✅ 应该：
├── 每个方法不超过 20 行
├── 只做参数校验（@Valid） + 调用 Service
├── 返回统一 Result<T>，禁止直接返回实体
├── 所有接口加 @RequirePermission 或公开注释
└── 有 @ApiOperation 注解

❌ 禁止：
├── 直接操作 Mapper/数据库
├── 写业务逻辑
├── 返回 null（返回 Optional 或抛异常）
└── 在 Controller 里 new Service()
```

#### Service 规范

```
✅ 应该：
├── 每个 Service 方法不超过 100 行（超了就拆分私有方法或提新 Service）
├── 写操作加 @Transactional（读操作不加，减少事务开销）
├── 业务逻辑用有意义的方法名（doRecharge / calculateCommission）
├── 关键操作写日志（新增/修改/删除/支付）
└── 抛自定义 BizCode 异常，不直接抛 RuntimeException

❌ 禁止：
├── 在 Service 里处理 HTTP 请求（这是 Controller 的职责）
├── 用 String 拼接 SQL
├── 直接改余额而不写流水（资金类）
└── 在 Service 层开启多线程（除非非常确定没有事务问题）
```

#### Entity 规范

```
✅ 应该：
├── 表名和字段名加注释（@TableName / @TableField）
├── 每个表必须有 id（主键）、created_at、updated_at
├── 多租户表必须有 tenant_id（并在所有 CRUD 中使用）
├── 状态字段用 tinyint，不用 ENUM（方便扩展）
├── BigDecimal 用于金额，不用 Double
└── 字段变更必须同步修改 Entity + 数据库

❌ 禁止：
├── 字段名与数据库不一致（Schema Drift 的根源）
├── 使用 Lombok @Data 在 Entity 上（会导致 EQ/HASH 问题）
├── 在 Entity 里写业务逻辑
└── 直接暴露给前端（用 DTO）
```

#### Repository/Mapper 规范

```
✅ 应该：
├── 手写复杂 SQL（JOIN/聚合/子查询）写在 XML 中
├── 简单 CRUD 用 MyBatis-Plus 内置方法
├── 所有查询自动带上 tenant_id（用 TenantMetaObjectHandler）
├── 索引字段优先（高频查询字段必须有索引）
└── 大数据量查询用分页（禁止一次查全表）

❌ 禁止：
├── SELECT *（必须明确列出字段）
├── 在 Mapper 里写业务逻辑
└── 写没有 WHERE 条件的 UPDATE/DELETE
```

#### Exception 规范

```
✅ 应该：
├── 定义 BizCode.java 枚举（每个模块独立文件）
├── 业务异常统一抛 BizException(code, message)
├── GlobalExceptionHandler 统一处理所有异常
├── 日志记录 traceId（用于链路追踪）
└── 异常信息不包含敏感数据（密码/Token）

❌ 禁止：
├── 抛 Exception/e/Throwable（太宽泛）
├── 把异常信息暴露给前端（堆栈信息）
└── 吞掉异常不记录（catch 后什么都不做）
```

### 5.2 Frontend 规范

#### 页面结构规范

```
✅ 标准页面结构：
views/
  module/
    index.vue         ← 列表页（表格 + 搜索 + 分页）
    form.vue         ← 新增/编辑表单弹窗
    detail.vue        ← 详情页（可选）

❌ 禁止：
├── 一个 Vue 文件超过 500 行
├── 把 API 调用直接写在 Vue 文件里（必须抽离到 api/）
└── 用 any 类型（禁止！用 unknown + 类型守卫）
```

#### 状态管理规范

```
✅ 应该：
├── 按模块分 Store（stores/auth.ts / stores/permission.ts）
├── 敏感数据存内存，持久化 token 用 localStorage
├── Store 里有 loading 状态（防重复请求）
├── 退出登录必须清理所有 Store + localStorage
└── 401 响应自动跳转登录页（在 request 拦截器里处理）

❌ 禁止：
├── 在组件里随意修改 Store（必须用 action）
├── 在 computed 里发请求
└── 两个 Store 互相依赖
```

#### API 封装规范

```
✅ 标准 request.ts：
├── baseURL: '/api'
├── 请求拦截器：加 Bearer Token
├── 请求拦截器：加 traceId（UUID）
├── 写操作加 X-Idempotency-Key（幂等）
├── 响应拦截器：401 → 跳转登录 + 弹窗
├── 响应拦截器：非 200 → 统一 toast + 抛异常
└── 超时：10s（长请求单独处理）

✅ 标准 API 模块（api/xxx.ts）：
├── 每个业务模块一个文件
├── 导出具体方法名（不是统一 request）
└── 入参有 TypeScript 类型
```

#### 组件设计规范

```
✅ 应该：
├── 原子组件：Button / Input / Table / Modal（可复用）
├── 业务组件：RefundDialog / MemberSelect（有业务含义）
├── 组件 props 有 TypeScript 类型
├── 组件有默认插槽 + 命名插槽
├── 大数据列表用虚拟滚动（el-table-v2）
└── 表单校验用 async-validator

❌ 禁止：
├── 在组件里 hardcode API URL（用 props 或 inject）
├── 用 setTimeout 等待数据（用 v-if / watch / watchEffect）
└── 用 eval()（安全风险）
```

---

## 六、数据库设计经验

### 6.1 企业级系统数据库设计方法

#### 表设计标准

```sql
-- 标准表模板
CREATE TABLE `{table_name}` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tenant_id`     BIGINT       NOT NULL                COMMENT '租户ID',
  
  -- 业务字段（按业务重要性排序）
  `xxx_code`     VARCHAR(32)  NOT NULL                COMMENT '编码',
  `xxx_name`     VARCHAR(128) NOT NULL                COMMENT '名称',
  `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态:0禁用 1正常',
  `remark`       VARCHAR(255)                          COMMENT '备注',
  
  -- 审计字段
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP 
                    ON UPDATE CURRENT_TIMESTAMP               COMMENT '更新时间',
  `created_by`   BIGINT                                   COMMENT '创建人',
  `updated_by`   BIGINT                                   COMMENT '更新人',
  
  -- 软删除（可选）
  `deleted`      TINYINT      NOT NULL DEFAULT 0       COMMENT '删除标记:0未删 1已删',
  
  PRIMARY KEY (`id`),
  -- 业务唯一键（必须）
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `xxx_code`),
  -- 常用查询字段加索引
  INDEX `idx_tenant_id` (`tenant_id`),
  INDEX `idx_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务含义';
```

#### 字段规范

| 字段类型 | 规范 | 示例 |
|---------|------|------|
| 主键 | `id BIGINT AUTO_INCREMENT` | `id BIGINT PRIMARY KEY AUTO_INCREMENT` |
| 租户ID | `tenant_id BIGINT NOT NULL` | 所有多租户表必须有 |
| 金额 | `DECIMAL(12,2)` 不用 Double | `balance DECIMAL(12,2)` |
| 时间 | `DATETIME` 不用 VARCHAR | `created_at DATETIME` |
| 状态 | `TINYINT` 不用 ENUM | `status TINYINT DEFAULT 1 COMMENT '0禁用 1正常'` |
| 手机号 | `VARCHAR(20)` | 国际格式兼容 |
| JSON 字段 | `JSON` 类型 | `tags JSON COMMENT '标签ID列表'` |
| 软删除 | `deleted TINYINT DEFAULT 0` | `UPDATE SET deleted=1` 而非 `DELETE` |

#### 状态设计模式

```
反模式：status ENUM('pending','processing','completed')  ← 禁止

正确做法：
status TINYINT  COMMENT '状态:1待处理 2处理中 3已完成 4已取消 5已关闭'
  + BizStatusEnum.java 枚举（代码里用枚举，数据库里存数字）

状态流转必须画状态机图：
[待支付] --支付成功--> [已支付] --退款申请--> [退款中] --确认--> [已退款]
                └--超时--> [已关闭]
```

#### 租户隔离规范

```
核心原则：每个多租户表都有 tenant_id，且 tenant_id 永远不能为 NULL

✅ 正确：
CREATE TABLE crm_member (
  tenant_id BIGINT NOT NULL,
  ...
  INDEX idx_tenant_id (tenant_id)
)

❌ 错误（实践中发现的真实问题）：
- 表缺 tenant_id 列（pos_store.tenant_id 不存在 → /member/* API 全 500）
- insert 语句不写 tenant_id（WalletMapper.xml → 29 行 tenant_id=NULL）
- 硬编码 setTenantId(1L)（12 处 → 跨租户数据污染）

验证 SQL：
SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL  -- 必须是 0
```

#### 审计字段规范

```
✅ 每个表必须有：
created_at  DATETIME  -- 谁也改不了，系统自动维护
updated_at  DATETIME  -- 谁也改不了，系统自动维护

✅ 资金/订单类表必须有：
balance_before  DECIMAL  -- 变动前余额（快照）
balance_after   DECIMAL  -- 变动后余额（快照）

✅ 高敏感操作表必须有：
created_by  BIGINT  -- 操作人 ID（从 Token 解析）
操作日志表  -- 记录所有敏感操作（和时间戳、操作人、IP）
```

#### 数据生命周期规范

```
数据策略必须在设计阶段就定义：

1. 会员/订单数据 → 永久保留
2. 操作日志     → 保留 90 天（LogRetentionTask 自动清理）
3. 临时文件     → 保留 7 天（上传后转 OSS，本地立即删除）
4. 草稿数据     → 保留 30 天后删除
5. 报表快照     → 按月/季度归档

实现：
- LogRetentionTask 定时任务（每小时检查）
- DELETE FROM sys_log WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)
```

### 6.2 实际踩过的 Schema 坑

| 坑 | 后果 | 预防方法 |
|----|------|---------|
| `pos_store` 表缺 `tenant_id` 列 | `/member/*` API 全部 500 | 每个新表必须有 tenant_id + 建表后立即测试 |
| `WalletMapper.xml` insert 不写 tenant_id | 29 行 tenant_id=NULL，余额漂移 | 写 SQL 必须对着 Entity 字段逐个核对 |
| `sys_config` 表从未建 | 定时任务每小时启动失败 | 所有代码引用的表必须有 init SQL |
| FK 覆盖率仅 14.4%（17/118） | 数据一致性无 DB 约束保障 | 关键外键必须加 FK（非关键可省略以保留性能） |

---

## 七、测试体系策略

### 7.1 四层测试金字塔

```
┌─────────────────────────────────────────────────────────────────┐
│                    四层测试体系（金字塔）                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                           ▲                                     │
│                          /█\        ④ 性能测试                   │
│                         / █ \       P99 ≤ 500ms / 500 并发      │
│                        /  █  \                                   │
│                       /   █   \      ③ E2E 测试（Playwright）   │
│                      /────█────\    Happy Path × 5 个关键流程    │
│                     /     █     \                                 │
│                    /      █      \    ② 集成测试（@SpringBootTest）│
│                   /       █       \  Controller→Service→Mapper │
│                  /________█________\                            │
│                 /         █         \   ① 单元测试（JUnit5）     │
│                /__________█__________\  核心业务逻辑 × 3 个用例  │
│                                                                 │
│   速度：快★★★★★ → 慢★☆☆☆☆                                     │
│   成本：低 ★☆☆☆☆ → 高 ★★★★★                                    │
│   覆盖率：局部 ★★★☆☆ → 全局 ★★★★★                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 每层测试策略

#### ① 单元测试（快速，高频）

```
工具：JUnit 5 + Mockito
范围：Service 层核心业务逻辑 / 工具类 / 状态机
覆盖率：核心逻辑 100%，边缘逻辑 ≥ 80%

必须覆盖的场景（以钱包为例）：
✅ 正常充值 → 余额增加正确
✅ 充值金额为负 → 抛 BizException
✅ 余额不足时消费 → 抛 BizException
✅ 充值后流水记录正确（balance_before/after）
✅ 并发充值幂等（同一 key 不重复处理）
✅ 雪花 ID 生成不重复

禁止：
❌ Mock 所有东西（包括被测对象的内部逻辑）
❌ 测试互相依赖（A 测试依赖 B 测试先跑）
❌ 不测边界条件（0 / 负数 / null / 最大值）
```

#### ② 集成测试（中速，关键路径）

```
工具：@SpringBootTest + H2 内存数据库（或 Testcontainers）
范围：Controller → Service → Mapper 全链路
覆盖：每个 Controller 至少 3 个用例（正常 + 异常 + 边界）

必须覆盖的场景：
✅ 完整 CRUD 流程
✅ 参数校验（@Valid 触发）
✅ 事务回滚（异常时数据未入库）
✅ 多租户隔离（两个租户数据互不可见）
✅ 幂等性（同一请求两次，第二次返回相同结果）
```

#### ③ E2E 测试（慢速，核心流程）

```
工具：Playwright（真实浏览器）
范围：核心 5 个业务流程（登录→业务→退出）
频率：每次 PR 触发 CI

核心 5 个用例（必须，不能跳过）：
1. 登录 → 创建会员 → 充值 → 支付 → 核销
2. 登录 → 新建预约 → 签到 → 完成服务
3. 登录 → 创建套餐 → 会员购买 → 核销
4. 登录 → 创建员工 → 员工排班 → 预约该技师
5. 登录 → 创建订单 → 混合支付 → 退款

⚠ 重要原则：
- 禁止用 test.skip() 跳过业务逻辑验证
- E2E 必须在干净的测试数据上跑
- 失败必须截图 + 控制台日志
```

#### ④ 性能测试（上线前，必做）

```
工具：JMeter / k6
范围：P0 接口（登录/列表/支付/退款）
指标：
  - P99 响应时间 ≤ 500ms
  - 500 并发下错误率 ≤ 1%
  - 数据库连接池不耗尽

触发时机：MVP 完成后 / 重大重构前
```

### 7.3 资金类测试特殊要求

```
钱包/支付/退款是资金类操作，测试要求比普通模块高：

1. 并发安全测试（必须）
   - 10 个并发线程同时充值同一账户
   - 验证余额 = 期望值（无重复追加）
   - 验证流水记录数 = 10（无丢失）

2. 幂等测试（必须）
   - 同一 idempotencyKey 发两次充值请求
   - 第二次返回相同结果，余额只增加一次

3. 边界测试（必须）
   - 充值金额 = 0 → 抛异常
   - 充值金额 = 负数 → 抛异常
   - 余额 = 消费金额 → 扣到 0 正确
   - 余额 < 消费金额 → 抛余额不足

4. 租户隔离测试（必须）
   - 租户 A 充值，租户 B 余额不变
   - 租户 A 看不到租户 B 的流水

本项目的经验教训：
- WalletConsumeConcurrencyTest 34/34 PASS → 生产并发安全
- PosControllerTenantIsolationTest 6 失败 → 测试不稳定不上线
```

### 7.4 新项目测试策略模板

```markdown
## [项目名] 测试策略

### 测试分层
| 层级 | 工具 | 覆盖目标 | 每次 PR |
|------|------|---------|---------|
| 单元测试 | JUnit5 + Mockito | Service 核心逻辑 ≥ 3 个用例/方法 | ✅ 必须 |
| 集成测试 | @SpringBootTest | Controller → Service → Mapper 全链路 | ✅ 必须 |
| API 测试 | PowerShell/Postman | 每个接口至少 1 个用例 | ✅ 必须 |
| E2E 测试 | Playwright | 核心 5 个业务流程 | ⚠ 建议 |
| 性能测试 | JMeter | P0 接口 P99/并发 | 🔲 上线前 |

### 通过标准
- 单元测试：核心逻辑 100% 通过
- 集成测试：全部通过
- API 测试：全部通过
- E2E：核心链路全部通过（不允许 test.skip）
- 安全扫描：0 高危漏洞

### 测试数据管理
- 开发/测试：seed SQL 脚本
- E2E：Playwright setup 在每个 spec 前运行
- 禁止用生产数据跑测试
```

---

## 八、项目避坑指南

> 以下每一条都来自真实事故、真实 bug 或真实返工。

### 8.1 数据库 Schema 相关

#### 坑 1：Schema Drift（代码与数据库不一致）

```
问题：Entity 类里的字段，数据库里没有
原因：开发时改了代码但没同步改数据库，或反之
后果：API 500，紧急修数据库，加班到凌晨

问题：Entity 类里的字段，数据库里没有
原因：开发时改了代码但没同步改数据库，或反之
后果：API 500，紧急修数据库，加班到凌晨

解决：
✅ 新表上线前：对照 Entity 逐字段核对 DDL
✅ 表结构变更：必须写 migration SQL，不能直接 ALTER TABLE
✅ 禁止手动改生产数据库（必须走 migration）
✅ 每张表必须有对应的 init SQL 文件（放在 backend/sql/init_xxx.sql）
✅ CI 加入 Schema 校验（跑一次 SELECT * FROM table LIMIT 0 确认表存在）

未来避免方式：
┌────────────────────────────────────────────────────────────────┐
│  新增字段标准流程：                                             │
│  1. 在 Entity.java 加字段（含注释）                             │
│  2. 写 migration SQL（ALTER TABLE ADD COLUMN）                  │
│  3. 执行 migration 并验证                                      │
│  4. 代码里使用该字段                                          │
│  5. 禁止：先改代码，后补 SQL                                   │
└────────────────────────────────────────────────────────────────┘
```

#### 坑 2：tenant_id 为 NULL（多租户污染）

```
问题：crm_wallet 表 29 行 tenant_id=NULL
原因：WalletMapper.xml 的 insertIfAbsent 没有写 tenant_id 字段
后果：跨租户数据混乱，资金流向不对
发现方式：SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL

解决：
✅ 所有 INSERT 语句，对照 Entity 逐字段核对
✅ Mapper XML 里的 INSERT 必须显式列出所有字段（禁止遗漏）
✅ CI 加租户 NULL 检查：
   SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL  -- 必须 = 0

未来避免方式：
- 每个 Mapper 接口必须有对应的 MapperTest
- MapperTest 验证 INSERT 后 SELECT 出来的 tenant_id 不为 NULL
```

#### 坑 3：硬编码 tenantId = 1L

```
问题：12 处 setTenantId(1L) 硬编码兜底
位置：BookingServiceImpl / CommissionSettlementService / 
      PackageController / StockController 等
后果：多租户隔离失效，租户 A 的数据会出现在租户 B

解决：
✅ TenantContextHolder.resolveTenantId() 是唯一合法的租户 ID 来源
✅ 禁止在业务代码里写死任何 tenantId
✅ 拦截器层兜底：如果 Token 里没有 tenantId，返回 401（而不是默认 1）
✅ CI 加静态检查：grep "setTenantId(1L)" → 报错，禁止提交

未来避免方式：
- 在 Code Review Checklist 里加"是否硬编码 tenantId"检查项
```

### 8.2 业务逻辑相关

#### 坑 4：数字人民币（资金）通道 NOT_IMPLEMENTED

```
问题：CCB / ABC / ICBC 三家银行 Adapter 全返回 NOT_IMPLEMENTED
后果：资金通道未对接，上线后无法真实收款
原因：开发阶段用 Mock 跑通后，未跟进真实 SDK 对接

解决：
✅ Mock 和真实实现必须用接口隔离
✅ 每个 Adapter 必须有至少一个真实集成测试
✅ 上线标准：NOT_IMPLEMENTED 不允许进入生产构建

未来避免方式：
- 财务类模块的上线标准比普通模块高一个等级
- 支付/退款/钱包模块必须有资金安全 checklist
```

#### 坑 5：退款逆向记录缺失 ref_id

```
问题：pos_refund_reverse_log 表 691 行，100% ref_id=NULL
后果：逆向操作无法追溯对应哪笔退款
原因：退款时只插了 reverse log，没有关联原退款单

解决：
✅ 逆向日志必须包含业务主键（ref_id = 原退款单ID）
✅ 退款操作必须是原子性的（原单状态变 + 逆向记录插，同时成功或同时回滚）

未来避免方式：
- 资金类表必须有数据完整性验证 SQL
- 每次退款后跑一次：SELECT COUNT(*) WHERE ref_id IS NULL  -- 必须 = 0
```

#### 坑 6：E2E 测试跳过业务验证

```
问题：full-flow.spec.ts 里 checkBackendHealthy() 失败就 test.skip()
后果：E2E 永远 pass，但实际上业务没跑通

解决：
✅ E2E 失败必须解决，不能 skip
✅ E2E 测试数据必须在测试开始前初始化（用 setup spec）
✅ CI 里的 E2E stage 不允许 any skip

未来避免方式：
- E2E 报告进发布门禁（不通过不让上线）
```

### 8.3 架构相关

#### 坑 7：循环依赖导致应用无法启动

```
问题：MultiTenantWalletTest 启动失败，packageController ↔ walletServiceImpl 循环依赖
后果：测试阶段应用都起不来
原因：WalletServiceImpl 依赖 PackageService，PackageService 依赖 WalletService

解决：
✅ 架构设计阶段画出模块依赖图，禁止循环依赖
✅ 循环依赖解决：用接口抽象一层，或合并到同一个模块
✅ CI 阶段必须跑一次 mvn test（启动不起来 = CI 失败）

未来避免方式：
- 代码提交前本地跑 mvn test（不只是 mvn compile）
```

#### 坑 8：定时任务依赖不存在的表

```
问题：LogRetentionTask 每小时执行一次，每次都因 sys_config 表不存在而报错
后果：日志里有大量噪音错误，掩盖真正的报警
原因：定时任务在设计时假设 sys_config 表存在，但 init SQL 里没有建这个表

解决：
✅ 所有定时任务依赖的表/配置必须在 init SQL 中初始化
✅ 定时任务启动时做自检（dependency check），依赖不满足则告警而非静默失败

未来避免方式：
- 定时任务代码提交前，检查所有依赖的表是否存在
```

### 8.4 避坑检查清单（每个模块完成前必过）

```
┌─────────────────────────────────────────────────────────────────┐
│                   模块发布前必过检查清单                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【Schema 检查】                                                 │
│  □ Entity 字段与数据库字段一一对应（无新增/缺失/类型不一致）       │
│  □ 每个多租户表的 INSERT 都显式包含 tenant_id                      │
│  □ SELECT tenant_id IS NULL 结果 = 0（所有涉及表）                │
│  □ 所有表都有 init SQL 文件                                       │
│  □ 没有手动 ALTER TABLE（必须走 migration SQL）                    │
│                                                                 │
│  【业务逻辑检查】                                                 │
│  □ 没有硬编码 setTenantId(1L)                                    │
│  □ 资金类操作有 balance_before/after 快照                        │
│  □ 退款有逆向日志且 ref_id 不为空                                 │
│  □ 支付/退款/钱包有幂等保护                                       │
│                                                                 │
│  【测试检查】                                                     │
│  □ 单元测试核心逻辑 100% 通过                                     │
│  □ 并发测试（钱包/预约）至少跑 10 个线程                         │
│  □ 租户隔离测试通过（连续 5 次 PASS）                            │
│  □ API 测试脚本全部通过                                           │
│  □ E2E 无 test.skip() 跳过业务逻辑                               │
│                                                                 │
│  【安全检查】                                                     │
│  □ 无 SQL 拼接（参数化查询）                                      │
│  □ 敏感数据（密码/Token）不出现在日志/URL/响应中                  │
│  □ CORS 只允许已知域名                                            │
│  □ JWT 验证签名和过期时间                                         │
│                                                                 │
│  【部署检查】                                                     │
│  □ 回滚方案已准备好                                              │
│  □ 定时任务依赖的表/配置已初始化                                  │
│  □ 无 NOT_IMPLEMENTED 遗留                                       │
│                                                                 │
│  ✅ 全部通过 = 模块可发布                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 九、新项目启动标准模板

> Day 1–5 标准动作，适用于所有企业级 SaaS / 管理后台项目。

### Day 1：环境准备

```
□ 安装开发工具（JDK 17+ / Node 18+ / Git / Docker Desktop）
□ 验证工具版本：java -version / node -v / git --version / docker --version
□ 配置 Git（用户名/邮箱/SSH Key）
□ Clone 项目代码（或初始化新项目）
□ 一键启动依赖服务：docker-compose up -d
□ 验证 MySQL 3306 / Redis 6379 / Backend 8080 / Frontend 5173
□ 初始化数据库（执行 init SQL）
□ 登录测试账号（验证认证链路通）
□ 创建 docs/ 目录结构（见下方目录模板）
□ 初始化 Git 仓库并做第一次提交
```

### Day 2：架构初始化

```
□ 确定技术选型（参考本文档 Phase 2 选型表）
□ 创建前后端脚手架（Spring Boot Initializr / Vite）
□ 配置 CORS / JWT / Redis 连接
□ 搭建项目骨架（按模块分包，不是按层分包）
□ 制定 API 规范（统一响应格式 / BizCode 枚举）
□ 制定数据库命名规范
□ 配置 CI/CD（GitHub Actions / GitLab CI）
□ 跑通第一个 API（登录接口，带 Token）
□ 创建 docs/ 目录结构：
  docs/
  ├── PRD/                    # 产品需求文档
  ├── ARCHITECTURE/           # 架构设计
  ├── API/                    # API 文档
  ├── DOMAIN/                 # 领域模型
  ├── SQL/                    # 建表脚本
  └── TESTS/                  # 测试报告
```

### Day 3：领域建模

```
□ 识别核心业务实体（Entity）
□ 识别值对象（VO）
□ 画出聚合关系图
□ 设计核心表结构（租户/用户/角色 必做）
□ 编写 init_m01_auth.sql（认证相关表）
□ 创建 RBAC 实体（SysUser / SysRole / SysPermission）
□ 跑通登录流程（前后端联调）
□ 编写第一个模块的技术方案文档
□ 第一个 Commit：Auth 模块完成
```

### Day 4：基础能力

```
□ 完成 M01 登录认证（含 JWT + RBAC）
□ 完成 M02 基础数据（含门店/员工/分类）
□ 完成核心前端 Layout + 路由 + 权限控制
□ 完成 Pinia 状态管理（user store + permission store）
□ 完成前端 API 封装（request.ts 拦截器）
□ 跑通第一个 CRUD（员工管理）
□ 第一个 E2E 测试通过（登录 → 增删改查）
□ Commit：Auth + Basic 模块完成
```

### Day 5：核心业务

```
□ 完成核心业务模块（M03 会员管理 作为第一个核心模块）
□ 完成会员相关数据库设计 + 后端 API + 前端页面
□ 完成会员 CRUD 全链路联调
□ 编写模块验证报告
□ Commit：Member 模块完成
□ 第一次 Code Review（Tech Lead Review）
□ 整理技术债清单（记入 docs/TECH_DEBT.md）
□ 更新 README.md（项目启动说明）
```

### 启动 Checklist

```
┌─────────────────────────────────────────────────────────────────┐
│                    新项目启动 Checklist                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Day 1 ✅                                                       │
│  □ 开发工具齐全（JDK 17+ / Node 18+ / Docker / Git）            │
│  □ Docker Compose 一键启动（MySQL + Redis）                     │
│  □ Git 初始化完成，第一次 Commit                                 │
│  □ docs/ 目录结构建立                                          │
│                                                                 │
│  Day 2 ✅                                                       │
│  □ 前后端脚手架可运行                                           │
│  □ API 规范文档定稿                                             │
│  □ 数据库命名规范定稿                                            │
│  □ CI/CD 流水线配置完成                                         │
│                                                                 │
│  Day 3 ✅                                                       │
│  □ 领域模型图画出                                               │
│  □ 核心表结构设计完成                                           │
│  □ M01 认证模块完成                                             │
│                                                                 │
│  Day 4 ✅                                                       │
│  □ M02 基础数据完成                                             │
│  □ 前端 Layout + 路由 + 权限完成                                 │
│  □ 第一个 CRUD 全链路通过                                       │
│                                                                 │
│  Day 5 ✅                                                       │
│  □ M03 会员管理完成                                             │
│  □ 第一次 Code Review 通过                                     │
│  □ 技术债清单建立                                               │
│  □ README.md 更新                                               │
│                                                                 │
│  🎯 里程碑：Day 5 完成 = MVP 核心闭环 30%                        │
│             （M01+M02+M03 是所有后续模块的基础）                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 附录 A：核心文档清单

| 文档 | 路径 | 说明 |
|------|------|------|
| 产品研发方法论 | `docs/METHOD-通用项目快速开发方法论.md` | 8 阶段完整流程 |
| 模块开发验证体系 | `docs/MODULE-模块化开发验证体系.md` | 模块闭环模板 |
| 避坑指南（实时更新） | `docs/HANDLING-避坑指南.md` | 本文档第八章 + 每次复盘更新 |
| 技术债看板 | `docs/TECH_DEBT.md` | 每个版本结束后更新 |
| 发布门禁 | `docs/RELEASE-GATE-发布门禁checklist.md` | 上线前必过检查 |
| API 设计规范 | `docs/API-设计规范.md` | REST + BizCode 枚举模板 |
| 测试策略 | `docs/TEST-测试策略.md` | 本文档第七章 + 模板 |
| 架构决策记录 | `docs/ADR-架构决策记录.md` | 每个重大架构变更记录 |

## 附录 B：Git 分支策略

```
main ──────────────────────────────── 生产环境（受保护，禁止直接推送）
  │
  └─ develop ─────────────────────── 开发主分支
        │
        ├─ feature/M01-auth          M01 功能分支
        ├─ feature/M02-basic         M02 功能分支
        ├─ feature/M03-member        M03 功能分支
        │
        └─ fix/P0-xxx                P0 紧急修复分支

提交规范：
feat(M01): 完成登录认证模块
fix(M07): 修复钱包充值并发问题
docs(module): 更新 M09 技术债清单
chore(ci): 添加 E2E 测试到 CI
```

## 附录 C：技术债看板

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      技术债看板（每个版本结束后更新）                       │
├──────┬────┬────────────────────────────────────────┬────┬──────────────┤
│ ID   │ P  │ 问题描述                               │模块│ 计划版本     │
├──────┼────┼────────────────────────────────────────┼────┼──────────────┤
│ TD-01│ P1 │ Schema drift：pos_store.tenant_id 缺失   │M01 │ R3.1         │
│ TD-02│ P1 │ 12 处硬编码 setTenantId(1L)            │全局 │ R3.1         │
│ TD-03│ P1 │ DCEP 三家银行 NOT_IMPLEMENTED           │M12 │ R3.2         │
│ TD-04│ P2 │ E2E 测试有 test.skip() 兜底             │测试 │ R3.1         │
│ TD-05│ P2 │ pos_refund_reverse_log ref_id 100% NULL │M07 │ R3.1         │
│ TD-06│ P3 │ sys_config 表从未建                      │M12 │ R3.2         │
│ TD-07│ P3 │ 命名不一致：crm_member.balance vs crm_wallet.balance │M03 │ R4.0  │
└──────┴────┴────────────────────────────────────────┴────┴──────────────┘

维护规则：
- P1 → 排进下一版本
- P2 → 排进近 3 个版本
- P3 → 视情况安排
- Code Review 发现新债 → 立即追加到此看板，禁止只写 TODO 注释
```

---

## 附录 D：项目关键数字参考

> 来源：美业 SaaS 项目实测数据

| 指标 | 数据 | 说明 |
|------|------|------|
| MVP 核心模块 | M01–M07（7 个） | 可上线运营 |
| 完整模块 | M01–M27（27 个） | 约 18 个月 |
| 数据库表 | 118 张 | FK 覆盖率 14.4% |
| 后端 Java 文件 | 720+ | 17 个包 |
| 前端 Vue 页面 | 90+ | 12 个 API 模块 |
| PowerShell 脚本 | 47 个 | 覆盖所有模块 |
| 测试类 | 54 个 | 含并发/租户隔离测试 |
| SQL 脚本 | 176 个 | 含 init/migrate/patch |

---

> **最后一句**：本文档不是终点，是起点。每个版本结束后，在附录 C 更新技术债看板，在附录 A 更新避坑指南。文档不更新 = 经验不积累 = 同样的坑会再踩一次。
>
> 方法论是护栏，不是枷锁。根据项目规模灵活裁剪：**MVP 可以跳过 Phase 5/6/7/8 的部分细节快速验证，但 Phase 0（产品定义）和 Phase 4（模块闭环）不可跳过**。
