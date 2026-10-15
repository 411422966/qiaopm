# P0_REPAIR_EXECUTION_PLAN

> **前提文档**：`docs/audit/BEAUTY_SAAS/00_CLEAN_CURRENT_REALITY_BASELINE.md`（独立审计基线）
> **本轮状态**：PLANNING ONLY — 不修改任何代码 / SQL / DB / 配置 / 测试
> **生成日期**：2026-08-15

---

## 0 · 任务清单速览

| 优先级 | Task ID | 一句话目标 | 状态 | 是否需要决策 |
|---|---|---|---|---|
| P0-1 | T01 | 修复 `pos_store.tenant_id` 列缺失，让 `/member/*` 不再 500 | READY | 否 |
| P0-1 | T02 | 新建 `sys_config` 表 + INIT 数据，消除 `LogRetentionTask` 每小时异常 | READY | 否 |
| P0-2 | T03 | `WalletMapper.insertIfAbsent` 写入 `tenant_id`，从 ctx 解析 | READY | 否 |
| P0-2 | T04 | 回填 `crm_wallet.tenant_id IS NULL` 的 29 行历史数据 | READY | 否 |
| P0-3 | T05 | 回填 `pos_refund_reverse_log.ref_id IS NULL` 的 691 行历史数据 | READY | 否 |
| P0-3 | T06 | `PosRefundService` 写 reverse log 时强制 `refId` 来源 | READY | 否 |
| P0-4 | T07 | 消除 12 处 `setTenantId(1L)` 硬编码旁路 | READY | 否（仅每处选 ctx 或抛错） |
| P0-5 | T08 | 让 `PosControllerTenantIsolationTest` 连续 5 次均通过 | READY | 否 |
| P0-5 | T09 | 让 `MultiTenantWalletTest` 不再因 `packageController ↔ walletServiceImpl` 循环依赖启动失败 | READY | 否 |
| P0-6 | T10 | 拆除 E2E 的 `checkBackendHealthy()` + `fallbackRbacSession()` 绕过，让全业务真正跑 | READY | 否 |
| P0-6 | T11 | 把 `/member/1` / `/pos/orders/page` 等关键 API 端到端跑通并落到 e2e 全链路断言 | READY | 否 |
| P0-7 | T12 | DCEP `mode` 启动校验 + 演示通道与 prod 通道明确分离 | READY | 否 |
| P0-7 | T13 | 真实银行 SDK 接入（CCB / ABC / ICBC） | **BLOCKED — DECISION REQUIRED** | **是** |
| P0-8 | T14 | P0 阶段终极回归验收（DB + API + 测试 + E2E + 资金链路） | READY | 否 |

**P0 总任务数：14** （13 个可内部执行 + 1 个 BLOCKED）

---

## 1 · 任务依赖关系图

```
T01 (pos_store.tenant_id)
T02 (sys_config 建表)
        ↓
    ┌───┴───────┐
    ↓           ↓
T03 (Wallet insertIfAbsent 带 tenant_id)
               ↓
        T04 (回填 crm_wallet NULL → 1)
    ┌───────────┴───────────┐
    ↓                       ↓
T05 (回填 pos_refund_reverse_log NULL ref_id)
    ↓
T06 (reverse log 写时强制 refId)
        ↓
    T07 (12 处 setTenantId(1L) 全部替换)
        ↓
    ┌───────┴───────┐
    ↓               ↓
T08 (PosControllerTenantIsolationTest 稳态)
T09 (MultiTenantWalletTest 启动)
    ↓
T10 (拆 E2E fallbackRbacSession + checkBackendHealthy)
    ↓
T11 (关键 API + Golden Path 真实断言)
    ↓
T12 (DCEP mode + 通道隔离)
    ↓
T13 (真实银行 SDK) ← BLOCKED — DECISION REQUIRED
    ↓
T14 (P0 终极回归)
```

---

## 2 · 任务详细卡（仅 P0）

### T01 — 修复 `pos_store.tenant_id` 列缺失
- **问题**：`/member/1` 实时 500 DB 异常。根因：`Unknown column 'tenant_id' in 'field list'` at `StoreServiceImpl.listByIds`。
- **根因**：`pos_store` 表 14 字段无 `tenant_id`，但 Store Service 实体 / Mapper 隐式 selectList(*) 把 `tenant_id` 也写入 SELECT 列。
- **涉及**：
  - 表 `pos_store`（缺列 `tenant_id BIGINT NULL` + 索引）
  - 后端：`Store.java`、`StoreServiceImpl.java`、`StoreMapper.java`
- **修改范围**：
  - 新增 init SQL：`migrate_v1_0_pos_store_add_tenant_id.sql`
  - 实体已含 `tenantId` 字段（需 double-check 是否与现有字段对齐 snake_case）
- **前置**：T01 是后续所有 API 路径的根，先做
- **执行动作（READ-ONLY PLAN，仅描述）**：
  1. 检 Store 实体是否已有 `tenantId` 字段（已推断有，否则 0 行失败）
  2. 设计 SQL：`ALTER TABLE pos_store ADD COLUMN tenant_id BIGINT NULL, ADD INDEX idx_tenant (tenant_id);`
  3. 若希望强约束：`tenant_id BIGINT NOT NULL DEFAULT 1` 并回填
  4. 重启后端 → 验证 `/member/1` 不再 500
- **验证命令**：
  - `docker exec meiyetec-mysql mysql -uroot -proot123456 meiyetec -e "DESCRIBE pos_store"` —— 应有 tenant_id
  - `Invoke-RestMethod http://localhost:8080/member/1` —— 应返回 200
- **验收标准**：
  - `/member/1` 返回 200
  - 后端 error log 不再出现 `Unknown column 'tenant_id' in 'field list'`
- **风险**：DB 默认值选择（NULL vs 1）会影响历史数据，需人工决策
- **人工决策**：是否对历史数据 fillback 1L 或允许 NULL

---

### T02 — 新建 `sys_config` 表
- **问题**：`LogRetentionTask` 每小时抛 `Table 'meiyetec.sys_config' doesn't exist`
- **根因**：init SQL 中从未 `CREATE TABLE sys_config`，但 `SysConfigServiceImpl.getInt("log.retention-days")` 直接读
- **涉及**：
  - 表 `sys_config`（须建）
  - 文件 `backend/src/main/resources/db/sql/` 下新增 SQL
  - `SysConfigServiceImpl.java`（读逻辑已存在）
- **前置**：无
- **执行动作**：
  1. 设计 `CREATE TABLE sys_config(id, config_key VARCHAR(64) UNIQUE, config_value, config_group, description, sort_order, created_at, updated_at)`
  2. 插入默认值 `('log.retention-days','90','system','日志保留天数')` 与 `('idempotency.retention-hours','24','system','幂等保留小时数')`
  3. 重启后端 → 等下一次 scheduler 触发，确认无 Exception
- **验证命令**：
  - `docker exec meiyetec-mysql mysql -uroot -proot123456 meiyetec -e "SHOW TABLES LIKE 'sys_config'"`
  - `tail -100 logs/meiyetec-error.log` —— 不应再出现 `sys_config doesn't exist`
- **验收**：定时任务 1 次成功
- **风险**：表结构必须与 `SysConfigServiceImpl.selectList()` 期望一致（含 config_key / config_value / config_group 字段）
- **人工决策**：否

---

### T03 — `WalletMapper.insertIfAbsent` 写入 `tenant_id`
- **问题**：每次新建钱包落 tenant_id=NULL
- **根因**：`WalletMapper.xml:18-22` 的 INSERT 列仅 6 个，不含 tenant_id
- **涉及**：
  - `WalletMapper.java:26`
  - `WalletMapper.xml`
  - 调用方：`WalletController.ensureWallet:224`、`WalletServiceImpl.ensureWallet:296`
- **前置**：T01（无直接依赖，但 T02-T06 一组任务应并行进入）
- **执行动作**：
  1. 让 `insertIfAbsent(Long memberId, Long tenantId)` 接收 tenantId
  2. 在 SQL 中加 `tenant_id` 列
  3. 上层调用方从 `TenantContextHolder.get().getTenantId()` 取值，缺失则抛错（禁止 fallback 到 1）
- **验证**：
  - 创建会员 → 立刻查钱包 tenant_id 必须等于登录用户 ctx.tenantId
  - 跑 `WalletSingleSourceTest`
- **验收**：新创建的钱包无 NULL tenant_id
- **风险**：上线初期既有 29 行 NULL 需 T04 一并回填
- **人工决策**：否

---

### T04 — 回填 `crm_wallet` 29 行 NULL tenant_id
- **问题**：DB 现存 29 行 `tenant_id IS NULL`
- **根因**：T03 是为新数据；T04 是历史数据
- **涉及**：表 `crm_wallet`、`crm_wallet_transaction`（如有孤儿）
- **执行动作**：
  1. 备份：29 行 `CREATE TABLE crm_wallet_orphan_log` 或 dump
  2. 通过 `crm_wallet.member_id → crm_member.id → crm_member.tenant_id` 反查，UPDATE
  3. 未匹配上的留 `crm_wallet_orphan_log`
- **验证**：`SELECT COUNT(*) FROM crm_wallet WHERE tenant_id IS NULL → 0`
- **验收**：0 NULL
- **风险**：若 member 跨租户，需要先审计
- **人工决策**：orphan 行保留与否（默认保留到 log 表）

---

### T05 — 回填 `pos_refund_reverse_log` 691 行 NULL ref_id
- **问题**：691/691 = 100% `ref_id IS NULL`
- **根因**：`PosRefundService` 写 reverse log 时未传 refId 或没来源
- **涉及**：表 `pos_refund_reverse_log`、`pos_refund`（通过 `refund_id` 反查）
- **执行动作**：
  1. `UPDATE pos_refund_reverse_log r JOIN pos_refund f ON r.refund_id=f.id SET r.ref_id=...` （ref_id 应为 f.refund_no 或 pos_order_id）
  2. 不匹配的行 → `pos_refund_reverse_log_orphan` 表
- **验证**：`SELECT COUNT(*) FROM pos_refund_reverse_log WHERE ref_id IS NULL → 0`
- **验收**：0 NULL
- **风险**：ref_id 语义需要对齐历史代码（T06 会定）
- **人工决策**：ref_id 取 `pos_refund.refund_no` 还是 `pos_order.id`，需确认历史代码意图

---

### T06 — `PosRefundService` 写 reverse log 时强制 `refId`
- **问题**：与 T05 同根因
- **根因**：写 reverse log 时未取关联业务主键
- **涉及**：`PosRefundService.java`
- **执行动作**：在所有 `pos_refund_reverse_log.insert(...)` 点前置 `r.setRefId(...)`，从 payload 中的 refundId / orderId 推出
- **验证**：新退款场景 ref_id NOT NULL
- **验收**：新增数据严格非空
- **风险**：与 T05 数据回填后保持口径一致

---

### T07 — 消除 12 处 `setTenantId(1L)` 硬编码
- **问题**：多租户隔离有 12 处硬编码旁路
- **根因**：未统一 `resolveTenantId()` 或缺乏空 ctx 抛错策略
- **涉及文件**：
  - `BookingServiceImpl.java:81`
  - `CommissionSettlementService.java:60` `:89`
  - `StockController.java:119` `:181`
  - `PackageController.java:106` `:155` `:492` `:619`
  - `OrgHierarchyService.java:247`
  - `ChannelDispatcher.java:43`
  - `OpenPlatformEnhanceService.java:403`
  - `CommissionController.java:250` `:650`
- **执行动作**：每处替换为：
  - 优先 `TenantContextHolder.get().getTenantId()`
  - ctx 为 null 时 → 抛错 `BusinessException(BizCode.TENANT_REQUIRED)`，禁止 fallback 1L
- **验证**：grep `setTenantId(1L)` → 0 命中
- **验收**：CI 中加静态扫描规则禁止 `setTenantId(1L)`
- **风险**：单元测试可能依赖 ctx=null 路径，需要同步修测试基类
- **人工决策**：是否引入"ctx 缺失时自动用登录态填充"中间层，不在本轮范围

---

### T08 — 修复 `PosControllerTenantIsolationTest` 不稳定
- **问题**：`mvn_test_all_p004.log` 中 7 用例 4 错误 + 2 失败（`tenantACannotSeeTenantBData`、`tenantBCannotSeeTenantAData`、`DuplicateKey uk_phone`、`NullPointer orderAId/orderBId`）
- **根因**：
  - 测试间共享 DB 状态 → `crm_member.uk_phone` 重复
  - 测试代码 `orderAId==null` 时未做好防御
  - Controller 真实存在跨租户可见（看 issue 4-Detail）
- **涉及**：
  - `src/test/.../PosControllerTenantIsolationTest.java`
  - 真实 controller：`PosController.orderPage` / `wallet/detail` 的 wrapper 拼装
- **执行动作**：
  1. 在 `setUp`/`tearDown` 加 `@Transactional` + cleanup 强制清理测试数据
  2. `orderAId=null` 加 `@BeforeEach` 默认值，避免裸 NPE
  3. 真 PosController 在 page 时显式 `wrapper.eq(PosOrder::getTenantId, ctx)` 防御
- **验证**：
  - `mvn test -Dtest=PosControllerTenantIsolationTest` × 5 次，每次都 0 fail
- **验收**：连跑 5 次 BUILD SUCCESS
- **风险**：DB 状态污染，加 cleanup 可能影响其他测试
- **人工决策**：否

---

### T09 — 修复 `packageController ↔ walletServiceImpl` 循环依赖
- **问题**：2026-08-15 05:52 `MultiTenantWalletTest` 启动失败（`APPLICATION FAILED TO START`）
- **根因**：`PackageController` 注入 `WalletServiceImpl`，而 `WalletServiceImpl` 内部 `@Lazy setSelf` 可能不一致，导致构造期 bean 解析死锁
- **涉及**：
  - `WalletServiceImpl.java`（自注入逻辑）
  - `PackageController.java`（注入侧）
- **执行动作**：
  1. 移除 `PackageController` 对 `WalletServiceImpl` 的直接依赖，改注入 `WalletService`（interface）
  2. 或在 `WalletServiceImpl` 构造器参数加 `@Lazy`
- **验证**：`mvn test -Dtest=MultiTenantWalletTest` —— 启动 + 通过
- **验收**：测试可启动 + 0 fail
- **风险**：低（仅生命周期）
- **人工决策**：否

---

### T10 — 拆除 E2E 兜底
- **问题**：
  - `auth.setup.ts:28-37` `fallbackRbacSession()` 用 `permissions: ['*']`
  - `full-flow.spec.ts:77-86` `checkBackendHealthy()` 检测 `/member/1` 返回失败即 `test.skip(!backendHealthy)`
- **根因**：E2E 设计上认 schema drift 是常态、用兜底让"假装能跑"
- **涉及**：
  - `frontend/meiyetec-web/e2e/auth.setup.ts`
  - `frontend/meiyetec-web/e2e/full-flow.spec.ts`
- **前置**：T01–T08 完成后 schema 已对齐
- **执行动作**：
  1. 删除 `fallbackRbacSession`，改为 strict-mode：权限接口失败立即 fail
  2. 删除 `checkBackendHealthy`，让 `test.skip` 改为 `test.fail`
  3. 全业务必须真实跑通
- **验证**：`npx playwright test --project=chromium` —— 全部 passed、不出现 `skipped because !backendHealthy`
- **验收**：CI 拒收任何 skipped passed
- **风险**：本地 dev 环境会因为 T01-T08 没修仍然失败
- **人工决策**：CI 严格 vs 本地宽松（建议 CI 严格、本地用本地 docker compose）

---

### T11 — 关键 API + Golden Path 真实断言
- **问题**：Golden Path 在 `pom + e2e` 双层只是声明，未真实断言
- **涉及**：
  - E2E `full-flow.spec.ts` 8 步骤
  - 后端测试用例
- **前置**：T01-T10
- **执行动作**：
  1. 把 Step 1~8 改为：每个 step 必须落库 → DB SELECT 真实断言
  2. 加 commission 端到端断言（commission_record 行数+tenant_id）
  3. 加 wallet balance before/after 真实断言
- **验证**：`npx playwright test --grep "P0-E2E-001"` + `mvn test` 全过
- **验收**：所有 step 真实落库、断言成立
- **风险**：测试用例需要给每个步骤写 fixture
- **人工决策**：否

---

### T12 — DCEP `mode` 启动校验 + 演示通道隔离
- **问题**：`app.dcep.mode=dev` 允许 `mockPrepay` 接受任意 `MOCK-` 前缀签名；prod 完全 NOT_IMPLEMENTED
- **根因**：dev 模式是开发态、prod 模式阻塞——但 prod 没有真实 SDK 等价于功能缺失
- **涉及**：
  - `application.yml`（已有 `mode: dev`）
  - `DcepConfigProperties.java`
  - `DcepService.mockPrepay`、`DcepService.verifySign`、`DcepService.notify`
  - `CcbDcepAdapter.java` / `AbcDcepAdapter.java` / `IcbcDcepAdapter.java`
- **执行动作**：
  1. 启动校验：`@PostConstruct` 强制 `mode` 必须是显式值；启动 prod 时若 adapter 全 NOT_IMPLEMENTED 必须 WARN + 阻断业务（不是只 WARN）
  2. 物理隔离：`pay_dcep_channel.env_mode` 只有 MOCK 与生产值两类；开发态 demo 通道仅在非 prod 注册
  3. `verifySign` dev 模式拒绝任何 dev mode 外部真实签名为合法——即 dev mode 完全 demo-only、真实银行签名 fail-fast
- **验证**：
  - 启动时 dev + 有通道 → OK
  - 启动时 prod + 仅有 NOT_IMPLEMENTED adapter → 启动失败
- **验收**：可保证 demo mode 与真实生产模式行为不交叉
- **风险**：开发体验下降（dev 必须显式启用）；可通过 ENV 开关
- **人工决策**：是否引入 `mode=staging` 灰度模式

---

### T13 — 真实银行 SDK 接入（CCB / ABC / ICBC）—— **BLOCKED / DECISION REQUIRED**

> 该任务不在本轮范围。仅标记现状与所需决策。

- **当前状态**：
  - `Ccb/Abc/Icbc DcepAdapter` 4 个方法 × 3 行 = 12 处 `NOT_IMPLEMENTED`
  - PosController 行 322-328 e_cny_soft/hard 仅生成 `ECNY-` 雪花号
  - `pay_dcep_order` 78 行多走 MOCK 通道
- **阻塞原因**：
  - DCEP 真实接入需要 **运营机构 / 央行网关资质**
  - 需要 **商户号 + 公私钥 + 双向证书 + 同城专线**
  - 需要 **与每家银行签订支付服务协议**
  - CCB / ABC / ICBC 真实 SDK 是商业资源，部分需要在 NDA 下
- **可选路径（需决策）**：
  - **路径 A**：签约 1 家（如建行 CCB）做 POC，半年可上线
  - **路径 B**：接入聚合支付平台（如银联商务 / 通联支付）作为统一前置
  - **路径 C**：维持现状（dev mock）接受不可生产
  - **路径 D**：与现有微信 / 支付宝同源（数币 API 走第三方聚合）
- **人工决策**：必选路径

---

### T14 — P0 终极回归验收
- **问题**：所有 P0 任务完成后需统一回归
- **涉及**：DB、API、测试、E2E、资金链路
- **前置**：T01-T12 全部完成（T13 是 BLOCKED 可以并列）
- **执行动作**：
  1. 重建 dev DB（清空 → init_*.sql 顺序跑通）一次
  2. 后端 mvn test 全过
  3. E2E 全过（无 skipped）
  4. 关键业务 curl 验证（auth → member → booking → pay → refund → commission）
  5. 多租户二租户维度验证（要求在 dev 临时插入 tenant=2 复测一次）
- **验证**：
  - `mvn test` 全过
  - `npx playwright test` 全过
  - DB `tenant_id NULL = 0`、`ref_id NULL = 0`、`FK ≥ 17`
- **验收**：Green
- **风险**：可能暴露出尚未发现的新 drift
- **人工决策**：否

---

## 3 · BLOCKED / DECISION REQUIRED 事项

| # | 阻塞点 | 需要决策的内容 | 影响 |
|---|---|---|---|
| D1 | T13 真实银行 SDK | 选 A/B/C/D 哪一条路径 | 决定 DCEP 是否生产可用 |
| D2 | T04 是否允许 crm_wallet 保留 NULL | 选 NULL + log / 或 NOT NULL DEFAULT 1 | 影响 schema 兼容性 |
| D3 | T05 ref_id 取 `pos_refund.refund_no` 还是 `pos_order.id` | 历史代码意图不明时需先 grep 全部历史 commit | T05 / T06 数据一致性 |

---

## 4 · 推荐的第一项执行任务

### ✅ **T01 — 修复 `pos_store.tenant_id` 列缺失**

**理由**：

1. **它是当前唯一让前端所有 `/member/*` API 直接挂掉的问题**，影响 Gold Path 演示
2. **它是 narrowest scope**（只动 1 张表 + 1 个 migration SQL + 1 个重启动作）
3. **不依赖任何其它 P0 任务**——可以做"原子级修复"
4. **完成即可立即 unblock T03/T04/T08/T10/T11 后续 E2E 与 Golden Path 跑通**
5. **风险最低**——失败回滚只需要 `ALTER TABLE ... DROP COLUMN`
6. **可独立验证**——`/member/1` 200 即达标
7. **不需任何人类决策**（除 D2 在 T04 才出现）
8. **完成时间可以 < 1 小时**（DDL + 重启 + verify）

完成 T01 后，下一项为 T02（同样独立、窄范围、低风险）。

---

## 5 · 不在本轮范围

- **不启动任何 Phase 3 修复**
- **不修改任何文件**
- **不创建 Sprint**
- **不创建 Roadmap**
- **不创建自动化修复脚本**

本文件为只读规划文档。下一轮决策者即可按 P0-1 → P0-2 → ... → P0-8 顺序，依本卡的"前置 / 验证 / 验收"逐项推进。

---

*END OF P0_REPAIR_EXECUTION_PLAN — Generated 2026-08-15 — PLANNING ONLY.*
