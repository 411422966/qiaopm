# 项目开发完成度与最终验收体系（最终融合版）

这是一份完整、独立、可直接落地的最终版本。它融合了：

- 最初"让 Cursor 分阶段理解项目"的思路
- Feature ID / L0–L5 / 7 种 Result 的验证体系
- 假成功、契约漂移、数据血缘、影子/幽灵/僵尸功能等隐性缺口检测
- Evidence 目录、回归对照、自动化守门、PARTIAL 子标签、L5a/L5b 分层、可观测性、Mock 子状态 7 个补强点
- `.cursor/rules` 落盘、Diff Audit、EXT-* SoT 三条落地机制

主干始终是 Phase 0 → Phase 10，没有推翻重构。

---

## 一、总目标

最终必须回答：

1. 项目到底开发完成到什么程度？
2. PRD/技术文档中的功能是否都已经实现？
3. 前端页面和交互是否完整？
4. API 是否真实可用？
5. 后端是否真正执行？
6. 数据库是否真正参与业务？
7. 用户点击操作后，数据是否真实产生、修改、查询、删除？
8. 前后端数据库是否真正形成业务闭环？
9. 哪些功能可以宣布完成？哪些只是代码存在？哪些是半成品、假实现、Mock、Shell、Broken？
10. 项目什么时候才可以宣布"开发完成"并进入正式验收/上线？

---

## 二、最高原则

```text
代码存在 ≠ 功能完成
页面存在 ≠ 功能完成
API 存在 ≠ API 可用
Controller 存在 ≠ 后端业务完成
数据库表存在 ≠ 数据库功能完成
HTTP 200 ≠ 业务成功
测试通过 ≠ 整个功能完成
```

真正的功能闭环应尽可能验证：

```text
PRD/需求
 ↓
Feature
 ↓
Frontend Page
 ↓
UI Block
 ↓
User Action
 ↓
Frontend Handler
 ↓
API
 ↓
Controller
 ↓
Service
 ↓
DAO/Mapper
 ↓
MySQL
 ↓
Transaction
 ↓
Business State Change
 ↓
API Response
 ↓
Frontend Refresh/Display
```

最终必须回答：

> 用户真实操作以后，系统是否按照需求完成了正确的业务动作，并且产生了正确、可查询、可追溯的数据？

---

## 三、最小验收单位：Feature ID

不能只按"模块"判断完成。必须把每一个可独立验证的业务能力拆成 Feature。

```text
F001 登录
F002 退出登录
F003 用户列表查询
F004 新增用户
F005 编辑用户
F006 删除用户
F007 用户详情
F008 权限配置
F009 商品新增
F010 商品库存调整
F011 创建订单
F012 支付订单
```

原则：

> 一个 Feature 必须能够独立回答"它到底有没有真正实现"。
> 模块只是统计单位。Feature 才是验收单位。

---

## 四、验证等级：L0–L5（L5 拆为 L5a / L5b）

| 等级 | 名称 | 含义 | 能否算完成 |
|---|---|---|---|
| L0 | UNKNOWN | 没有足够证据 | ❌ |
| L1 | STATIC VERIFIED | 代码结构存在，静态分析证明链路基本存在 | ❌ |
| L2 | RUNTIME VERIFIED | 真实运行环境中已执行：页面打开、按钮点击、请求真实发出、API 真实返回 | ⚠️ 部分 |
| L3 | DB VERIFIED | 真实 MySQL 中观察到 INSERT/UPDATE/DELETE/SELECT 正确结果 | ⚠️ 部分 |
| L4 | BUSINESS VERIFIED | 数据变化符合 PRD 定义的业务规则 | ✅ |
| L5a | 单功能 E2E | 单个 Feature 自身走通：页面 → API → DB → 回查 | ✅ |
| L5b | 跨模块业务流 E2E | 多 Feature 联动：下单 → 支付 → 库存 → 财务 | ✅ 最终 |

**核心业务必须达到 L5b，普通功能 L5a 即可。没有运行证据，只能是 L0 或 L1，不能写 VERIFIED。**

---

## 五、最终状态：Result（带子标签）

验证等级和最终状态是两件事。最终 Result 使用：

```text
VERIFIED                 有足够证据证明该 Feature 已按需求完成
PARTIAL-BLOCKER          已实现一部分，核心链路未完成，阻塞上线
PARTIAL-NON-BLOCKER      已实现一部分，仅体验/边缘缺口，不阻塞上线
BROKEN                   存在明确错误，运行链路无法正常完成
MOCK-INTENTIONAL         设计上就是 fallback，有注解/配置说明，允许保留
MOCK-FAKE                假成功，必须修
SHELL-INTENTIONAL        P2 占位页，已知且计划内
SHELL-ABANDONED          已被废弃但未清理
MISSING                  需求存在，但实际没有对应实现
UNVERIFIED               代码可能存在，但没有足够运行/数据/业务证据证明完成
```

**统计时 PARTIAL-BLOCKER 单独一栏，直接决定 Gate 2 是否通过。UNVERIFIED 绝不能自动算 VERIFIED。**

---

## 六、每个 Feature 的最低验证要求

不是所有 Feature 都必须做完整 E2E。正确原则是：

> 每一个 Feature 都必须有验收结论。
> 但不同类型 Feature 可以使用不同的最低验证方式。

| Feature 类型 | 最低验证 |
|---|---|
| 列表查询 | Runtime + 真实 DB 读取 |
| 详情查询 | Runtime + 真实 DB 读取 |
| 新增 | Runtime + DB INSERT |
| 编辑 | Runtime + DB UPDATE |
| 删除 | Runtime + DB DELETE/软删除 |
| 权限 | Runtime + 权限实际验证 |
| 文件上传 | Runtime + 文件结果 |
| 导出 | Runtime + 实际文件结果 |
| 报表 | Runtime + 数据来源验证 |
| 定时任务 | Job Runtime + DB 结果 |
| MQ 消费者 | Consumer Runtime + DB/业务状态 |
| 核心订单 | L5b + DB + Business |
| 支付 | L5b + DB + Business |
| 库存 | L5b + DB + Business |
| 资金/钱包 | L5b + DB + Business + 事务 |
| 核心审批 | L5b + Business |
| 普通配置 | Runtime + DB |

---

## 七、Evidence 目录约定

**每个 Feature 的 Evidence 必须落在固定目录，编号固定，缺一张即视为该 Feature 证据不足，降级为 UNVERIFIED。**

```text
audit/
├── baseline/
│   ├── commit.txt
│   ├── db-snapshot-YYYYMMDD.sql
│   └── env.md
├── evidence/
│   └── F001-login/
│       ├── 01-page.png
│       ├── 02-api-request.json
│       ├── 03-api-response.json
│       ├── 04-mysql-before.sql
│       ├── 05-mysql-after.sql
│       └── 06-frontend-refresh.png
├── scripts/
│   ├── 01-scan-fake-success.sh
│   ├── 02-scan-mock.sh
│   ├── 03-scan-disabled.sh
│   ├── 04-scan-endpoint-coverage.sh
│   ├── 05-scan-contract-drift.sh
│   ├── 06-scan-soft-delete.sh
│   ├── 07-scan-transaction.sh
│   └── 08-scan-shadow-ghost.sh
├── static-gap/
├── runtime/
├── db/
├── business/
├── non-functional/
└── reports/
    └── final-acceptance.md
```

---

## 八、回归对照三件套

**Runtime / DB / Business 每一次验证都必须带：**

```text
commit hash
DB snapshot 路径
环境说明（env.md）
```

验收矩阵新增字段：

| 字段 | 说明 |
|---|---|
| Verified Commit | 本次验证绑定的 commit |
| DB Snapshot | 本次验证前/后的 SQL 快照路径 |
| Re-verifiable | 是否可用同一脚本复现 |
| Last Verified At | 最后验证时间 |

**任何一次 PR 改动后，未重跑对应 Feature，自动降级为 UNVERIFIED。**

---

## 八点五、Phase -1 — Pre-flight Check

### 目的
在冻结规则之前，先确认"体系能不能跑起来"的前置条件。
Phase -1 不是可选项，每换一个项目、每开一个新批次都要重跑。

### 输出物
audit/baseline/preflight.md

### 必须确认项（第一层，阻塞启动）
1. MCP 配置状态（MySQL / Playwright）
2. PRD / 业务规则文档是否存在
3. BLOCKER 判定阈值是否已定义

### 应在 Phase 0 前确认项（第二层，不阻塞但会返工）
4. git status 是否干净
5. 受保护页面清单
6. 守门脚本是否进 CI

### 可边跑边调项（第三层，降级风险）
7. 8 个守门脚本的有效性边界
8. 批次划分

### 与 Phase 0 的关系
Phase 0 读 preflight.md 的"已知约束"，再冻结规则。
未确认项写进 preflight.md，不阻塞 Phase 0，但必须在对应 Phase 前解决。

### 完整 preflight.md 模板
见《在 Cursor 中落地这套验收体系的操作指南》对应章节。

---

## 九、完整阶段流程

```text
Phase -1  Pre-flight Check
          + preflight.md 输出
          ↓
Phase 0   Audit Rules / Scope Freeze
          + Evidence 目录约定
          + EXT-* SoT 引用
          + .cursor/rules/project-audit.mdc 落盘
          ↓
Phase 1   Feature Inventory
          ↓
Phase 2   Frontend ↔ Backend ↔ DB Mapping
          ↓
Phase 3   Static Gap Audit
          + 自动化守门脚本
          ↓
Phase 3.5 Diff Audit（重构类变更专用）
          ↓
Phase 4   Runtime Verification Plan
          ↓
Phase 5   Runtime Verification
          + commit hash + DB snapshot 三件套
          + L5a 单功能 E2E
          ↓
Phase 6   Database Verification
          + 回归对照组
          ↓
Phase 7   Business Verification
          + L5b 跨模块业务流 E2E
          ↓
Phase 8   Non-Functional / Closure Readiness
          + 可观测性（traceId / 指标 / 埋点）
          ↓
Phase 9   Regression / Git / Cleanup
          + 自动化守门脚本每次 PR 跑
          ↓
Phase 10  Final Closure Assessment
          + EXT-* Compliance 栏
          + PARTIAL-BLOCKER 单独统计
```

---

## 十、各阶段执行内容

### Phase 0 — Audit Rules / Scope Freeze

冻结验收范围、Feature ID 规则、验证等级、最终状态、证据要求。明确 Cursor 能力边界：不能真打开浏览器、不能连生产库、不能替人工验收、无运行证据不得声称 VERIFIED。

落盘 `.cursor/rules/project-audit.mdc`，追加：

```text
11. EXT-* Operational Notes 为事实来源（SoT），
    审计结论与 EXT-* 冲突时，以 EXT-* 为准并标 ISSUE。
12. Evidence 必须落在 audit/evidence/FXXX-<slug>/，编号固定。
13. Result 必须带子标签：PARTIAL-BLOCKER / MOCK-INTENTIONAL 等。
14. L5 拆为 L5a（单功能）/ L5b（跨模块业务流）。
15. 每次验证必须绑定 commit hash + DB snapshot + env.md。
```

### Phase 1 — Feature Inventory

从四个来源交叉汇总：

```text
Frontend：菜单、路由、页面、Tab、Button、Form、Dialog、Table、Action、API调用、状态管理、Mock、TODO
Backend：Controller、API、Service、Mapper/DAO、Entity、DTO、VO、定时任务、MQ、Event、权限
Database：Tables、Columns、Index、FK、Unique、Enum/状态字段、Soft Delete、Audit Fields、Migration/DDL、Seed Data
Requirement：PRD、技术设计、API文档、数据库设计、功能说明
```

输出：Feature Inventory。

### Phase 2 — Full-Stack Mapping

对每个 Feature 建立 Traceability Matrix，字段：

| 字段 |
|---|
| Feature ID |
| Module |
| Requirement |
| Priority |
| Frontend Route |
| Page |
| UI Block |
| User Action |
| Frontend Handler |
| API |
| HTTP Method |
| Request |
| Response |
| Controller |
| Service |
| DAO/Mapper |
| DB Table |
| Related Tables |
| Read/Write |
| Transaction |
| Permission |
| Soft Delete |
| Audit Fields |
| Static Status |
| Runtime Status |
| DB Status |
| Business Status |
| E2E Status |
| Verified Commit |
| DB Snapshot |
| Last Verified At |
| Final Result |
| Evidence |
| Issue |

### Phase 3 — Static Gap Audit

主动寻找"看起来完成，实际上没有完成"的情况：

```text
Frontend without Backend
Backend without Frontend
Button without Handler
Handler without API
API without Controller
Controller without Service
Service without DAO
DAO without Table
Table without Business Usage
Mock API
Hardcoded Data
localStorage fake persistence
TODO
Empty Method
return null / return [] / return true
fake success
temporary fallback
disabled code / dead code
```

同时识别：

- **Shadow Feature**：代码存在，但没有真实入口。
- **Ghost Feature**：前端有入口，但后端不存在。
- **Zombie Feature**：旧接口/旧代码还存在，但已不属于当前架构。
- **Fake Feature**：表面能操作，实际没有产生真实业务数据。
- **Shell Feature**：只有页面或接口外壳。
- **Contract Drift**：URL、HTTP Method、参数、字段名、类型、Response、权限、状态码前后端不一致。

配套自动化守门脚本：

```bash
audit/scripts/
├── 01-scan-fake-success.sh       # return Result.success / catch 吞异常
├── 02-scan-mock.sh               # mock / fake / hardcode / localStorage
├── 03-scan-disabled.sh           # @Disabled / TODO / XXX / dead code
├── 04-scan-endpoint-coverage.sh  # 前端 API vs 后端 @RequestMapping 差集
├── 05-scan-contract-drift.sh     # Swagger vs Controller vs 前端 API
├── 06-scan-soft-delete.sh        # is_deleted + unique index 冲突
├── 07-scan-transaction.sh        # @Transactional 自调用 / private / 吞异常
└── 08-scan-shadow-ghost.sh       # 反向查未调用接口 / 无入口页面
```

**每个脚本输出 `audit/static-gap/<name>.json`，CI 里作为守门项。**

### Phase 3.5 — Diff Audit（重构类变更专用）

针对"删旧加新"的重构（如 MKT 模块）：

| 检查项 | 方法 | 输出 |
|---|---|---|
| 旧 API 端点是否被新 controller 全覆盖 | URL 路径 grep 对比 | 差集表 |
| 旧 entity 是否仍被引用 | `grep import OldEntity` | 残留引用表 |
| 关联 Service / Report / Dispatch 是否被破坏 | diff + 回归 | 影响面表 |
| 删表 SQL 与回滚 SQL 是否对得上 | SQL 双向比对 | 对称性表 |

**规则：重构批次必须先通过 Diff Audit，才进入 Runtime 验证。**

### Phase 4 — Runtime Verification Plan

优先验证：

```text
P0、核心业务、资金、订单、库存、权限、登录、状态流转、高风险功能
Mock/Shell 疑似 Feature
Static Gap 发现的异常 Feature
```

### Phase 5 — Runtime Verification

真实启动项目，验证：

```text
页面是否能打开 → 按钮是否存在 → 是否可点击 → 事件是否触发
→ API 是否真实发出 → API 是否成功 → 返回数据是否正确
→ 前端是否正确展示 → 错误是否正确处理
```

必须保存 Evidence。必须绑定 commit hash + DB snapshot + env.md。没有运行证据，只能标 UNVERIFIED。

### Phase 6 — Database Verification

对产生持久化数据的 Feature，验证真实 MySQL：

```text
INSERT / UPDATE / DELETE / SELECT
关联表 / 事务 / 唯一约束 / 外键 / 状态字段 / 软删除 / 审计字段 / 创建时间 / 更新时间
```

重点识别 **Fake Success**：

```text
catch 吞异常仍 return success
HTTP 200 但 body 是业务错误
Service 直接 return success 但没执行 DB 操作
前端点击后只改本地状态，没请求后端
Mock interceptor 拦截真实 API
事务没提交
前端显示旧缓存，数据库没更新
```

### Phase 7 — Business Verification

数据库变化还不够，必须判断变化是否符合业务规则。

例如"创建订单"不能只证明 `orders` INSERT 成功，还要确认：

```text
会员关系正确
订单明细正确
金额正确
状态正确
员工关系正确
库存关系正确（如需求要求）
支付关系正确（如需求要求）
```

**DB VERIFIED ≠ BUSINESS VERIFIED。核心业务必须做 L5b 跨模块业务流验证。**

数据血缘检查：

```text
页面输入 → API Request → DTO → Service → Entity → SQL → MySQL
MySQL → Mapper → Service → VO → API Response → Frontend → 页面展示
```

防止"页面字段A → 后端字段B → 数据库字段C"实际没有正确映射。

### Phase 8 — Non-Functional / Closure Readiness

```text
权限（菜单/页面/按钮/数据/API/字段脱敏）
异常处理
事务边界
并发
幂等
软删除与唯一索引冲突
数据隔离 / 多租户
审计字段
缓存
环境配置（dev/test/prod）
第三方服务
定时任务
MQ
日志
种子数据与字典完整性
```

增加 **可观测性**：

| 检查项 | 要求 |
|---|---|
| traceId | 前后端一致，贯穿 API → Service → Mapper → 日志 |
| 关键路径埋点 | 登录、支付、下单、审批必须有 |
| 指标 | QPS、错误率、慢 SQL、事务回滚数 |
| 日志 | 结构化，可检索，含 userId / tenantId / featureId |

**没有 traceId 的非功能验证，只能标 UNVERIFIED。**

### Phase 9 — Regression / Git / Cleanup

```text
Git status
未提交修改
临时文件
debug 代码
TODO
console.log
测试数据
Mock
临时开关
disabled 代码
```

建立 Regression Baseline。自动化守门脚本每次 PR 自动跑。

### Phase 10 — Final Closure Assessment

输出最终报告和四个结论。新增 **EXT-* Compliance 栏**。

---

## 十一、最终 Feature Acceptance Matrix

必须形成一张总表：

| Feature | Module | Requirement | FE | API | BE | DB | Runtime | Business | E2E | Result | Evidence | Issue | Verified Commit |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

示例：

```text
F001 登录
FE ✓ / API ✓ / BE ✓ / DB ✓ / Runtime ✓ / Business ✓ / E2E L5a ✓
Result = VERIFIED
Evidence = audit/evidence/F001-login/

F015 新增商品
FE ✓ / API ✓ / BE ✓ / DB ✓ / Runtime ✓ / Business ✗ / E2E ✗
Result = PARTIAL-BLOCKER
Issue = 库存初始化逻辑缺失

F021 导出报表
FE ✓ / API ✓ / BE ✓ / DB ? / Runtime ✗ / Business ? / E2E ✗
Result = UNVERIFIED
```

---

## 十二、最终统计

```text
验收基线：
commit: abc1234
DB snapshot: audit/baseline/db-snapshot-20260912.sql
env: env.md

总功能点：200

VERIFIED               120
PARTIAL-BLOCKER          8   ← 阻塞上线
PARTIAL-NON-BLOCKER     22
BROKEN                  15
MOCK-INTENTIONAL         4
MOCK-FAKE                6
SHELL-INTENTIONAL        3
SHELL-ABANDONED          2
MISSING                 10
UNVERIFIED              10

Runtime Verified %      = 157/200
DB Verified %           = 135/200
Business Verified %     = 120/200
E2E Verified %          = 108/200   (L5a 88, L5b 20)

P0 功能：50
  VERIFIED 45 / PARTIAL-BLOCKER 3 / MISSING 2
  → P0 完成率 90%，但有 5 个阻塞上线

EXT-* Compliance：
  EXT-19  PASS
  EXT-22  PASS
  EXT-23  VIOLATION（多租户隔离缺失）
  EXT-24  PASS
```

---

## 十三、项目什么时候才算"开发完成"？

**Development Complete 不是"代码写完"，而是：**

```text
PRD Feature Inventory 完成
+ 所有 Feature 都有结论
+ P0/P1 核心功能没有未解决 BLOCKER
+ 前后端映射完成
+ 核心 API 运行正常
+ 持久化功能真实写入 MySQL
+ 核心业务规则验证通过
+ 核心业务完成 L5b E2E
+ 不存在已知 Fake Success
+ 不存在关键 Mock-Fake / Shell-Abandoned
+ 权限/事务/数据一致性达到要求
+ 回归测试通过
```

---

## 十四、三个 Gate

### Gate 1 — Development Complete

开发团队确认：功能已经实现，不再处于持续开发状态。允许存在低优先级问题、P2/P3 优化项、非核心体验问题，但不能存在核心 BLOCKER。

### Gate 2 — QA / Acceptance Ready

```text
所有 Feature 有结论
P0/P1 已验证
核心 L5b E2E 通过
真实数据库验证通过
核心业务规则验证通过
回归通过
PARTIAL-BLOCKER = 0
```

### Gate 3 — Production Ready

```text
生产配置
数据库 Migration
备份恢复
权限
日志
监控
告警
安全
第三方服务
部署
回滚
性能
数据初始化
```

全部达到上线要求后，才允许 Production GO。

---

## 十五、最终必须输出四个结论

### 1. Development Status

```text
NOT COMPLETE / PARTIALLY COMPLETE / DEVELOPMENT COMPLETE
```

### 2. Acceptance Readiness

```text
NOT READY / CONDITIONAL READY / READY
```

### 3. Production Readiness

```text
NOT READY / CONDITIONAL GO / GO
```

### 4. Closure Recommendation

```text
继续开发 → 边修边验 → 冻结功能 → 正式验收 → 上线准备 → Production GO
```

---

## 十六、最终交付物（13 项）

```text
1.  Feature Inventory
2.  Full-Stack Traceability Matrix
3.  Static Gap Report
4.  Diff Audit Report
5.  Runtime Verification Report
6.  Database Verification Report
7.  Business Verification Report
8.  Non-Functional Report（含可观测性）
9.  Issue Register
10. Regression Report
11. Final Feature Acceptance Matrix
12. Project Closure Readiness Report
13. EXT-* Compliance Report
```

---

## 十七、最高规则（19 条）

```text
1.  每一个 Feature 必须有最终结论。
2.  没有证据，不得声称完成。
3.  静态代码存在，只能证明 STATIC，不代表完成。
4.  Runtime 没有证据，不得写 VERIFIED。
5.  持久化 Feature 没有真实 MySQL 证据，不得写 DB VERIFIED。
6.  DB 发生变化但不符合业务规则，不得写 BUSINESS VERIFIED。
7.  核心业务必须进行 L5b E2E 验证。
8.  不同 Feature 可以采用不同最低验证等级，但不能因为没做 E2E 就自动判定完成。
9.  Mock、Shell、Hardcode、Fake Success 必须明确标记子状态。
10. 前端、API、Controller、Service、DAO、DB 必须检查契约一致性。
11. 不允许用代码量、文件数量、Controller 数量、测试数量直接推导项目完成度。
12. 不允许因为测试通过就宣布项目完成。
13. 不允许因为页面能打开就宣布功能完成。
14. 不允许因为 API 返回 HTTP 200 就宣布业务成功。
15. 不允许因为数据库表存在就宣布数据库功能完成。
16. 最终必须以真实用户操作和真实业务结果作为核心验收依据。
17. 所有发现的问题必须进入 Feature Matrix 或 Issue Register，不得只写在审计文字中。
18. 所有 P0/P1 必须有明确结论。
19. 最终报告必须能够回答："项目现在到底完成了多少，还有什么没完成，为什么，下一步做什么，什么时候可以正式验收，什么时候可以上线。"
```

---

## 十八、Cursor 能力边界（写进规则）

```text
Cursor 不能：
1. 真正打开浏览器点击（除非配置 Playwright MCP）
2. 连接生产数据库执行查询
3. 判断业务是否符合 PRD（除非提供 PRD）
4. 替代人工验收
5. 在没有运行证据时声称 VERIFIED

Cursor 只能：
1. 静态分析代码
2. 生成测试代码和 SQL
3. 根据看到的内容推断
4. 标注 UNVERIFIED
```

`.cursor/rules/project-audit.mdc`：

```text
规则：
1.  所有分析以 Feature ID 为单位。
2.  严格区分 L0–L5a/L5b 验证等级。
3.  没有运行证据只能标 UNVERIFIED。
4.  所有结论必须带文件路径、类名、方法名、路由、API、表名、行号。
5.  无法确认标注"未确认"，禁止猜测。
6.  不要修改业务代码。
7.  最终状态只允许：VERIFIED / PARTIAL-BLOCKER / PARTIAL-NON-BLOCKER /
    BROKEN / MOCK-INTENTIONAL / MOCK-FAKE / SHELL-INTENTIONAL /
    SHELL-ABANDONED / MISSING / UNVERIFIED。
8.  输出必须是 Markdown 表格 + 问题清单。
9.  不能声称自己能打开浏览器、连生产库、替人工验收。
10. 发现假成功、契约漂移、数据血缘缺失、影子/幽灵/僵尸功能必须单独标记。
11. EXT-* Operational Notes 为事实来源（SoT），审计结论与 EXT-* 冲突时，
    以 EXT-* 为准并标 ISSUE。
12. Evidence 必须落在 audit/evidence/FXXX-<slug>/，编号固定。
13. 每次验证必须绑定 commit hash + DB snapshot + env.md。
14. 重构类变更必须先跑 Phase 3.5 Diff Audit。
15. 自动化守门脚本每次 PR 必须跑。
16. 每次开新批次或新项目，必须先输出 audit/baseline/preflight.md，
    再进入 Phase 0。未确认项写入"已知约束"，不阻塞 Phase 0，
    但必须在对应 Phase 前解决。
```

---

## 十九、一句话总结

**以 Feature ID 为最小单元，以 L0–L5a/L5b 为验证等级，以带子标签的 Result 为最终状态，按 Phase 0 → Phase 10 执行，覆盖静态缺口、Diff 审计、运行验证、数据库验证、业务闭环、非功能闭环、Git 回归，最终输出一份绑定 Git commit 与 DB snapshot、可统计、可复验、可作为项目最终验收依据的 Feature Acceptance System。**

这套方法不依赖具体项目，可直接作为任何前后端项目的开发完成度验收标准。