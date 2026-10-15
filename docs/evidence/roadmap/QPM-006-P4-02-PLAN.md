# QPM-006: P4-02 会员生命周期营销 — 基线方案

**文档编号**: QPM-006
**编制日期**: 2026-08-17
**Phase**: P4 产品升级
**作者依据**: QPM-004 Gap Matrix G06 + QPM-003 Reality Re-Audit + 后端代码摸底

---

## 1. 任务定位

P4-02 是 P4 五个候选功能中 **优先级 P1 + ROI 最高 + 基础最扎实** 的任务：

| 维度 | 评估 |
|------|------|
| 业务价值 | 🔥 高 — 直接驱动复购率，影响门店营收 |
| 技术依赖 | 🟢 95% 就绪 — 营销自动化/旅程/触达/RFM 全部 REAL |
| 实施难度 | 🟢 低-中 — 主要工作是统一抽象 + 补齐 2 个缺口 + 前端集成 |
| 估时 | 6-10 周 (原计划 6-10 周，因基础扎实可压缩到 **4-6 周**) |
| ROI | 🔥 直接可见 — 会员日、生日提醒触发即可见提升 |

---

## 2. 现状摸底（关键发现）

### 2.1 已有能力（已 REAL — 不可重复开发）

| 模块 | 文件 | 完成度 | 关键能力 |
|------|------|--------|---------|
| **生日提醒** | `BirthdayReminderService.java` | 🟢 90% | `@Scheduled cron="0 30 8 * * ?"` 每天08:30扫；T-7/T-0/T+1三波；礼券自动发放；日志完整 |
| **会员日自动化** | `MemberDayService.java` | 🟢 85% | `@Scheduled cron="0 0 1 * * ?"` 每天01:00扫；3种类型(BIRTHDAY/MEMBER_DAY/ANNIVERSARY)；ROI 报告 |
| **RFM 分群** | `RfmService.java` | 🟢 REAL | 8 类分群；可作为复购唤醒分层基础 |
| **营销触达任务** | `MarketingAutomationService.java` | 🟢 REAL | 渠道/模板/任务/旅程/转化统计全套 |
| **会员旅程** | `mkt_journey` + `mkt_journey_instance` | 🟢 REAL | 旅程画布 + 实例推进 |
| **触达渠道** | `ThirdPartyIntegrationService.java` | 🟡 PARTIAL | 微信/小程序/美团/抖音（mock 实现） |

### 2.2 缺口（需补齐）

| 缺口 | 影响规则 | 严重度 | 估时 |
|------|---------|--------|------|
| 🔴 **RFM 自动入旅程机制** | 复购唤醒 (T+30/60/90) | P0 | 2 周 |
| 🔴 **裂变邀请模块** (invite_code + invite_reward) | 裂变邀请 | P0 | 2 周 |
| 🟡 **统一规则引擎抽象** (lifecycle_rule + lifecycle_log) | 全 4 规则 | P1 | 1 周 |
| 🟡 **前端集成** (会员生命周期看板 + 规则配置) | 全 4 规则 | P1 | 2 周 |
| 🟢 **监控埋点 + 灰度开关** | 全 4 规则 | P2 | 0.5 周 |

### 2.3 已具备依赖清单（无需新做）

- ✅ `Member` 实体（含 `birthday` 字段）
- ✅ `CrmRfmSnapshot` 实体
- ✅ `MarketingTouchLog` 触达日志
- ✅ `MarketingTouchConversion` 转化记录
- ✅ `MktJourney` / `MktJourneyInstance` 旅程画布
- ✅ `MktChannelTemplate` 渠道模板
- ✅ `MktChannelConfig` 渠道配置
- ✅ `MarketingAutomationService.createTask` 已支持 SMS / MINI_PROGRAM 触达

---

## 3. 数据库设计

### 3.1 复用现有表（不重建）

```
birthday_member          (生日档案)
birthday_send_log        (生日礼发放日志)
member_day_config        (会员日配置)
member_day_execution     (会员日执行记录)
crm_rfm_snapshot         (RFM 分群快照)
mkt_journey              (旅程画布)
mkt_journey_instance     (旅程实例)
marketing_touch_task     (触达任务)
marketing_touch_log      (触达日志)
marketing_touch_conversion (转化记录)
mkt_channel_template     (渠道模板)
mkt_channel_config       (渠道配置)
```

### 3.2 新增 4 张表

#### 3.2.1 `member_lifecycle_rule` — 统一规则引擎

| 字段 | 类型 | 必填 | 说明 |
|------|------|-----|------|
| id | BIGINT PK | ✅ | 主键 |
| tenant_id | BIGINT | ✅ | 租户 ID |
| brand_id | BIGINT | | 品牌 ID |
| rule_code | VARCHAR(50) | ✅ | 规则编码（如 `BIRTHDAY_T7`、`MEMBER_DAY_MONTHLY`、`RETENTION_T30`、`INVITE_REWARD`） |
| rule_type | VARCHAR(20) | ✅ | 规则类型枚举：`BIRTHDAY` / `MEMBER_DAY` / `RETENTION` / `INVITE` |
| rule_name | VARCHAR(100) | ✅ | 规则名称 |
| description | VARCHAR(500) | | 规则描述 |
| trigger_config_json | JSON | ✅ | 触发条件 JSON（如 `{offsetDays:-7, sendType:"WECHAT"}`） |
| template_id | BIGINT | | 关联触达模板 ID |
| rfm_segment | VARCHAR(20) | | 适用 RFM 分群（RETENTION 类规则使用） |
| invoke_journey_id | BIGINT | | 关联旅程 ID（可选，规则可触发旅程） |
| invoke_coupon_amount | DECIMAL(10,2) | | 自动发放优惠金额 |
| invoke_points | INT | | 自动赠送积分 |
| schedule_cron | VARCHAR(50) | | 定时任务 cron（如 `0 30 8 * * ?`） |
| max_per_day | INT | | 每日最大触发数（限流） |
| enabled | TINYINT | ✅ | 1=启用 0=禁用 |
| status | VARCHAR(20) | ✅ | DRAFT/ACTIVE/PAUSED/ARCHIVED |
| priority | INT | | 优先级 |
| operator_name | VARCHAR(50) | | 操作人 |
| create_time | DATETIME | ✅ | 创建时间 |
| update_time | DATETIME | ✅ | 更新时间 |
| deleted | TINYINT | ✅ | 软删 |

#### 3.2.2 `member_lifecycle_log` — 统一触发日志

| 字段 | 类型 | 必填 | 说明 |
|------|------|-----|------|
| id | BIGINT PK | ✅ | 主键 |
| tenant_id | BIGINT | ✅ | 租户 ID |
| rule_id | BIGINT | ✅ | 规则 ID |
| rule_code | VARCHAR(50) | ✅ | 规则编码 |
| rule_type | VARCHAR(20) | ✅ | 规则类型 |
| member_id | BIGINT | ✅ | 目标会员 ID |
| member_name | VARCHAR(50) | | 会员姓名 |
| member_phone | VARCHAR(20) | | 会员手机 |
| trigger_date | DATE | ✅ | 触发日期 |
| trigger_reason | VARCHAR(200) | | 触发原因 |
| channel | VARCHAR(20) | ✅ | 触达渠道 |
| template_id | BIGINT | | 触达模板 |
| template_content | TEXT | | 模板内容快照 |
| coupon_code | VARCHAR(50) | | 发放的优惠码 |
| points_awarded | INT | | 发放的积分 |
| gift_value | VARCHAR(100) | | 礼物描述 |
| journey_instance_id | BIGINT | | 关联旅程实例 ID |
| status | VARCHAR(20) | ✅ | PENDING/SENT/FAILED/USED/EXPIRED |
| error_message | VARCHAR(500) | | 失败原因 |
| external_msg_id | VARCHAR(100) | | 外部消息 ID |
| converted_at | DATETIME | | 转化时间 |
| related_order_id | BIGINT | | 关联订单 |
| sent_at | DATETIME | | 发送时间 |
| used_at | DATETIME | | 使用时间 |
| create_time | DATETIME | ✅ | 创建时间 |
| deleted | TINYINT | ✅ | 软删 |

**索引**：
- `idx_tenant_rule_date (tenant_id, rule_id, trigger_date)`
- `idx_tenant_member (tenant_id, member_id, rule_type)`
- `idx_status_date (status, trigger_date)`

#### 3.2.3 `member_invite_code` — 裂变邀请码

| 字段 | 类型 | 必填 | 说明 |
|------|------|-----|------|
| id | BIGINT PK | ✅ | 主键 |
| tenant_id | BIGINT | ✅ | 租户 ID |
| inviter_id | BIGINT | ✅ | 邀请人会员 ID |
| invite_code | VARCHAR(20) | ✅ UQ | 邀请码（如 `INV` + 8位随机） |
| invite_channel | VARCHAR(20) | | 分享渠道（WX/QR/LINK/SMS） |
| invite_count | INT | ✅ | 被邀请人次 |
| success_count | INT | ✅ | 成功注册数 |
| reward_coupon_template | BIGINT | | 奖励券模板 |
| reward_coupon_amount | DECIMAL(10,2) | | 奖励券金额 |
| expire_at | DATETIME | | 失效时间 |
| status | VARCHAR(20) | ✅ | ACTIVE/EXPIRED/DISABLED |
| create_time | DATETIME | ✅ | 创建时间 |
| update_time | DATETIME | ✅ | 更新时间 |
| deleted | TINYINT | ✅ | 软删 |

**索引**：
- `uk_invite_code (invite_code)`
- `idx_tenant_inviter (tenant_id, inviter_id)`

#### 3.2.4 `member_invite_reward` — 裂变奖励记录

| 字段 | 类型 | 必填 | 说明 |
|------|------|-----|------|
| id | BIGINT PK | ✅ | 主键 |
| tenant_id | BIGINT | ✅ | 租户 ID |
| invite_code_id | BIGINT | ✅ | 邀请码 ID |
| inviter_id | BIGINT | ✅ | 邀请人 |
| invitee_id | BIGINT | ✅ | 被邀请人（新会员） |
| invitee_name | VARCHAR(50) | | 被邀请人姓名 |
| invitee_phone | VARCHAR(20) | | 被邀请人手机 |
| register_at | DATETIME | ✅ | 被邀请人注册时间 |
| first_order_id | BIGINT | | 首单 ID（用于发放首单奖励） |
| first_order_at | DATETIME | | 首单时间 |
| inviter_reward_type | VARCHAR(20) | ✅ | 邀请人奖励类型（COUPON/POINTS/CASH） |
| inviter_reward_value | VARCHAR(100) | ✅ | 邀请人奖励值 |
| inviter_reward_status | VARCHAR(20) | ✅ | PENDING/SENT/USED |
| invitee_reward_type | VARCHAR(20) | ✅ | 被邀请人奖励类型 |
| invitee_reward_value | VARCHAR(100) | ✅ | 被邀请人奖励值 |
| invitee_reward_status | VARCHAR(20) | ✅ | PENDING/SENT/USED |
| operator_name | VARCHAR(50) | | 操作人 |
| create_time | DATETIME | ✅ | 创建时间 |
| update_time | DATETIME | ✅ | 更新时间 |
| deleted | TINYINT | ✅ | 软删 |

**索引**：
- `idx_tenant_inviter (tenant_id, inviter_id)`
- `idx_tenant_invitee (tenant_id, invitee_id)`

---

## 4. 模块拆分（8 周交付计划）

### Phase 1 — 基础 & 复购唤醒（Week 1-3）

#### Week 1: 数据库 + 实体 + Mapper
- [ ] 创建 `member_lifecycle_rule` / `member_lifecycle_log` / `member_invite_code` / `member_invite_reward` 表 SQL 迁移
- [ ] 创建 4 个 Entity 类：`MemberLifecycleRule` / `MemberLifecycleLog` / `MemberInviteCode` / `MemberInviteReward`
- [ ] 创建 4 个 Mapper 类
- [ ] 单元测试：CRUD 通过

#### Week 2: 复购唤醒（RETENTION）规则
- [ ] `MemberLifecycleService.scanRetentionTargets()` — 扫描 RFM AT_RISK/LOST 会员
- [ ] `MemberLifecycleService.triggerRetention()` — 调用 RfmService.getRfmSnapshot() 拉分群 + 创建 journeyInstance
- [ ] `@Scheduled cron="0 0 2 * * ?"` 每日02:00 扫描执行
- [ ] 创建 2 个示例 RETENTION 规则（T+30 / T+60）默认配置

#### Week 3: 复购唤醒联调
- [ ] 复购唤醒 → 旅程 → 触达 → 转化 全链路打通
- [ ] E2E 测试：API + DB 验证

### Phase 2 — 裂变邀请（Week 4-5）

#### Week 4: 邀请码模块
- [ ] `MemberInviteService.generateCode()` — 生成邀请码
- [ ] `MemberInviteService.bindInvitee()` — 绑定被邀请人
- [ ] `MemberInviteController` — API（生成/查询/绑定）
- [ ] 单元测试 + E2E

#### Week 5: 裂变奖励发放
- [ ] `MemberInviteService.grantReward()` — 注册后双方奖励
- [ ] 监听会员注册事件（如果有事件机制） / 或调用 MemberController.create() 后插入
- [ ] E2E：邀请 → 注册 → 奖励发放

### Phase 3 — 前端集成（Week 6）

#### Week 6: 前端会员生命周期看板
- [ ] 新增菜单：**会员营销 → 生命周期看板**
- [ ] 看板页：
  - 总览卡片（本月触达 / 转化率 / 唤醒会员数 / 裂变新增）
  - 4 个 Tab：生日提醒 / 会员日 / 复购唤醒 / 裂变邀请
  - 每个 Tab：触发趋势图 + 转化漏斗 + 最近执行列表
- [ ] 规则配置页：CRUD 4 类规则
- [ ] 移动端：会员端查看我的邀请码/我的奖励

### Phase 4 — E2E + 灰度（Week 7-8）

#### Week 7: E2E 测试
- [ ] `p0-lifecycle.spec.ts` — 4 大规则 P0 链路（21 测试）
- [ ] `p1-lifecycle-business.spec.ts` — 业务流（触发 → 触达 → 转化）
- [ ] CI 门禁集成

#### Week 8: 灰度开关 + 监控
- [ ] 在 `sys_config` 添加 `lifecycle.rule.enabled.{rule_type}` 灰度开关
- [ ] 埋点：触发次数 / 触达成功次数 / 转化次数 / 客单价提升
- [ ] 监控看板：触发率 / 失败率 / 转化率

---

## 5. API 设计

### 5.1 后端 API（`/lifecycle` 前缀）

| Method | Path | 说明 | 权限 |
|--------|------|------|------|
| GET | `/lifecycle/dashboard` | 总览看板 | `lifecycle:view` |
| GET | `/lifecycle/rule/page` | 规则分页 | `lifecycle:rule` |
| POST | `/lifecycle/rule` | 创建规则 | `lifecycle:rule` |
| PUT | `/lifecycle/rule/{id}` | 更新规则 | `lifecycle:rule` |
| POST | `/lifecycle/rule/{id}/toggle` | 启用/禁用 | `lifecycle:rule` |
| DELETE | `/lifecycle/rule/{id}` | 删除规则 | `lifecycle:rule` |
| GET | `/lifecycle/log/page` | 触发日志分页 | `lifecycle:view` |
| GET | `/lifecycle/funnel` | 转化漏斗 | `lifecycle:view` |
| POST | `/lifecycle/trigger/{ruleId}` | 手动触发规则 | `lifecycle:rule` |
| GET | `/lifecycle/invite/code` | 生成我的邀请码 | 会员端 |
| GET | `/lifecycle/invite/my-invites` | 我的邀请记录 | 会员端 |
| POST | `/lifecycle/invite/bind` | 绑定邀请关系（注册时） | 会员端 |
| GET | `/lifecycle/invite/rewards` | 我的裂变奖励 | 会员端 |

### 5.2 复用现有 API（不重建）

| 现有 API | 用途 |
|---------|------|
| `/birthday/*` (BirthdayReminderService) | 生日规则 |
| `/member-day/*` (MemberDayService) | 会员日规则 |
| `/intel/rfm/*` (RfmService) | RFM 分群 |
| `/mkt/journey/*` (MarketingAutomationService) | 旅程 |
| `/mkt/task/*` (MarketingAutomationService) | 触达任务 |

---

## 6. 前端菜单与路由

### 6.1 新增菜单

```
会员营销 (已有)
├── 会员列表
├── 会员标签
├── 会员分群
└── 生命周期看板 (新增)  <-- 此处新增
    ├── 总览
    ├── 生日提醒 (复用 BirthdayReminder)
    ├── 会员日 (复用 MemberDay)
    ├── 复购唤醒 (新建)
    ├── 裂变邀请 (新建)
    └── 规则配置
```

### 6.2 新增路由

```typescript
{
  path: '/member/lifecycle',
  component: () => import('@/views/member/LifecycleDashboard.vue'),
  meta: { title: '会员生命周期看板', icon: 'DataLine' }
},
{
  path: '/member/lifecycle/rules',
  component: () => import('@/views/member/LifecycleRules.vue'),
  meta: { title: '生命周期规则配置' }
}
```

---

## 7. P4-02 子任务清单（与本会话 todo 对应）

| 编号 | 任务 | 估时 | 状态 |
|------|------|------|------|
| P4-02-01 | 摸清现有营销自动化/会员分群/触达渠道 | 1d | ✅ |
| P4-02-02 | 定位 4 大规则代码缺口 | 0.5d | ✅ |
| P4-02-03 | 设计 lifecycle_rule + lifecycle_log + invite 表 | 0.5d | ✅ |
| P4-02-04 | 生成 P4-02 基线文档 | 0.5d | ✅ 进行中 |
| P4-02-05 | 后端基础 - 4 张表迁移 + 4 个 Entity + 4 个 Mapper | 1d | ⏳ |
| P4-02-06 | MemberLifecycleService 框架 + 4 大规则接口 | 2d | ⏳ |
| P4-02-07 | MemberLifecycleController + MemberInviteController + DTO | 1d | ⏳ |
| P4-02-08 | 前端会员生命周期看板 + 规则配置页 | 3d | ⏳ |
| P4-02-09 | E2E 测试 (p0-lifecycle + p1-lifecycle-business) | 2d | ⏳ |
| P4-02-10 | 灰度开关 + 监控埋点 + 文档交付 | 1d | ⏳ |

---

## 8. 风险与缓解

| 风险 | 等级 | 缓解策略 |
|------|------|---------|
| 触达 mock 真实渠道失败 | 中 | 保留 mock 作为 fallback，加 `sender.type=mock` 标识 |
| 定时任务在高并发下重复触发 | 中 | lifecycle_log 唯一索引 (rule_id+member_id+trigger_date) 防重 |
| 灰度上线误触达真实会员 | 高 | 默认 `enabled=0` 灰度开关；先在测试租户验证 |
| RFM 分群表为空（无历史数据） | 中 | 兼容：无 RFM 快照时跳过 RETENTION 规则 |
| 邀请码被刷 | 中 | 单会员最大 1 个有效邀请码 + 限流 |

---

## 9. 退出标准（Definition of Done）

- [ ] 4 张新表已迁移 + 后端启动成功
- [ ] 4 大规则引擎全部就绪（生日/会员日/复购/裂变）
- [ ] MemberLifecycleService + MemberInviteService 单测覆盖率 ≥ 60%
- [ ] 前端会员生命周期看板可访问、4 Tab 数据完整
- [ ] E2E：p0-lifecycle 21 测试通过
- [ ] E2E：p1-lifecycle-business 完整业务流通过
- [ ] 监控看板：触发率/转化率可见
- [ ] 灰度开关：4 类规则可独立启停
- [ ] CURRENT_STATE.md 更新 RC6 Baseline
- [ ] docs/evidence/reality/QPM-006-DELIVERY.md 生成

---

## 10. 与其他 P4 任务的关系

```
P4-02 (本任务)
    ↓ 复用
P4-01 AI 智能排班 — 需要 RFM 分群作为输入
P4-03 配方中心 — 需要会员画像
P4-04 护理建议 — 需要 RFM 分层
P4-05 多人共享套餐 — 需要会员生命周期数据
```

P4-02 是其他 4 个 P4 任务的基础数据层，先完成可降低后续任务风险。

---

## 11. 结论

P4-02 是 **P4 阶段最高 ROI + 最低风险** 的任务：
- ✅ 90% 基础设施已就绪
- ✅ 只需补齐 2 个核心缺口（复购唤醒 + 裂变）
- ✅ 4 周可交付 MVP，6 周可交付完整版
- ✅ 直接驱动会员复购率，ROI 可量化

**推荐执行：立即开始 P4-02-05（后端基础）。**

---

*文档生成依据: QPM-004 P4-02 深度分析 + 后端代码实勘 (BirthdayReminderService, MemberDayService, RfmService, MarketingAutomationService)*
*下次更新: P4-02-10 完成时 (RC6 Baseline)*