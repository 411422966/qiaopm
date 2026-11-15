# F169 — 渠道配额按品牌独立计数（I178 口径对齐）

> **Feature ID**：F169
> **状态**：**VERIFIED**（L5a — 实际运行 + DB 计数对照，9/9 CHECK PASS）
> **对应 Issue**：`audit/issues/I178-channel-dispatcher-coverage-gap.md`（**Partially Fixed** — 本次只修「多品牌配额口径错配」，「4 条发送路径闸门覆盖缺口」仍未修）
> **基线 commit**：`eb41278`（修复前 HEAD）
> **修复 commit**：`44746cc` — `fix(mkt): 渠道配额计数口径对齐到 tenant+brand+channel（I178 多品牌串味）`
> **DB 快照**：本目录 `raw/00-fixture-seed.sql`（夹具）+ `raw/01-verify-i178.txt`（实测输出）
> **环境基线**：`audit/baseline/env.md`
> **改动文件**：`backend/src/main/java/com/meiyetec/service/channel/ChannelDispatcher.java` — `rateLimitCheck`

---

## 1. 缺陷与修复

`ChannelDispatcher.rateLimitCheck(tenantId, brandId, channelType)` 的**额度配置**按
`tenant_id + brand_id + channel_type` 查找（`MktChannelConfig`），
但**计数**只按 `tenant_id + channel_type` 统计 `marketing_touch_log` → 多品牌租户下：

- 品牌 A 的发送量被计入品牌 B 的判定 → B 被提前限流；
- 单个品牌的额度被全租户同渠道流量消耗 → 限额被放大 N 倍（N = 租户下品牌数）。

修复：计数口径与配置口径对齐为 `tenant_id + brand_id + channel_type`。

| 项 | 修复前 | 修复后 |
|---|---|---|
| 每分钟计数 | `tenant_id + channel_type` | `tenant_id + brand_id + channel_type` |
| 每日计数 | `tenant_id + channel_type` | `tenant_id + brand_id + channel_type` |
| 品牌归属来源 | — | `marketing_touch_task.brand_id`（经 `task_id` 关联） |

> ⚠️ **实现约束（不是「加个 `AND brand_id = ?`」那么简单）**：
> `marketing_touch_log` 表**没有 `brand_id` 列**（只有 `task_id`），品牌归属在
> `marketing_touch_task.brand_id`。因此计数必须 JOIN 任务表：
> `JOIN marketing_touch_task tt ON tt.id = t.task_id AND tt.deleted = 0`，
> 品牌条件用 MySQL 的 NULL 安全等于 `tt.brand_id <=> ?`，
> 保证「无品牌任务」（`brand_id IS NULL`）只与「无品牌任务」互相计数。

---

## 2. 验证环境

| 项 | 值 |
|---|---|
| 运行时 | docker compose 栈：`meiyetec/api:dev`（重建自本分支）+ `meiyetec-mysql` + `meiyetec-redis` |
| 后端构建 | `mvn clean package "-Dmaven.test.skip=true"` → `docker compose build api` → `docker compose up -d api` |
| 令牌 | `operator01`（`userId=900006`，`tenant_id=1`，角色 OPERATOR 持 `mkt:touch:task:edit`） |
| 被测入口 | `POST /mkt/task`（建任务）+ `POST /mkt/task/{id}/start`（启动发送） |
| 测试品牌 | `brand_id` = 990301 / 990302 / 990303 / 990304（合成 ID，写入前 fail-fast 检查为 0 行） |
| 受众会员 | `crm_member.id` 1–5（只读，tenant 1） |
| 外部渠道 | **未触达任何真实渠道**：走 `IN_APP` 通道，receiver 为数字手机号 → `InAppSender` 写 `member_message`（无短信/邮件/SMS 调用，**零计费**） |

---

## 3. 实测结果（`raw/01-verify-i178.txt`）

| # | 场景 | 期望 | 实测 | 判定 |
|---|---|---|---|---|
| S0 | fail-fast：合成 brand id 写入前必须为 0 行 | 0 行 | 0 行；并检查受众会员 5 条齐全 | ✅ PASS |
| S1 | 品牌 A（`per_min=1000 / per_day=2`）5 人发送 | 发出 2 条，拦下 3 条 | `successCount=2`，日志 `sent=2 pending=3` | ✅ PASS |
| S1b | 计数口径对照（同刻两套 SQL） | 旧口径 tenant+channel 计入 A；新口径 A=2、B=0 | `old=2 newA=2 newB=0` | ✅ PASS |
| S2 | 品牌 B（同额度）发送 | **A 满额不影响 B**，发出 2 条 | `successCount=2`，`sent=2 pending=3` | ✅ PASS |
| S3 | 品牌 C（`per_min=2 / per_day=1000`） | 分钟闸门在 2 条处生效 | `sent=2 pending=3` | ✅ PASS |
| S4 | 品牌 D（`per_min=1000 / per_day=1`） | 日闸门在 1 条处生效 | `sent=1 pending=4` | ✅ PASS |
| S5 | 无 `brandId` 的任务 | 查不到配置 → 不限流（行为不变） | `sent=5 pending=0` | ✅ PASS |
| S6 | 夹具清理 | 配置 / 任务 / 明细 / 站内信全部归零 | `cfg=0 task=0 log=0 msg=0` | ✅ PASS |

**OVERALL: ALL PASS（9/9 CHECK）**

---

## 4. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| 品牌间配额互不串味 | **L5a** | 实际运行（两个品牌各跑一轮）+ DB 计数对照 |
| 每分钟闸门仍生效 | **L5a** | 实际运行 + 日志 status 分布对照 |
| 每日闸门仍生效 | **L5a** | 实际运行 + 日志 status 分布对照 |
| 无品牌任务行为不变 | **L5a** | 实际运行对照 |
| 配额**覆盖缺口**（EXT-19 预约确认 / 生日通知 / 设备告警 3 条路径绕过闸门） | **UNVERIFIED / 未修** | 仍属 I178 §1.3，**本批未实施** |
| 「失败是否计入配额」 | **UNVERIFIED** | 现状：只统计 `sent_at` 非空（发送成功）的行，失败不计入；未与渠道商确认计费规则 |
| 前端端到端 | **UNVERIFIED** | 纯后端改动；`/mkt/task` 前端页面未做浏览器侧验证 |

---

## 5. 口径变更声明与影响面

- **语义变化**：渠道配额从「租户内同渠道共享池」改为「按品牌独立配额」。
  - 修复前：某品牌超发会挤占同租户其他品牌的额度（并且限额被放大 N 倍）。
  - 修复后：各品牌额度互不影响，**单个品牌的可用额度在多品牌租户下会变多**（恢复到配置本来声明的水平）。
- **历史数据未回溯**：本次只改判定逻辑，历史 `marketing_touch_log` 不做重算；历史发送量在新的品牌维度下自然归入各自品牌。
- **孤儿明细行**：`marketing_touch_log` 中有 **8 行** `task_id` 指向已不存在的任务；改用关联计数后这 8 行不再计入任何品牌的配额（原先会计入租户级计数）。
- **共享池哨兵值仍未生效**：库里存在 3 条 `brand_id = NULL` 的有效配置（SMS 3/5、PUSH 60/5000、IN_APP 1000/99999），
  但 `rateLimitCheck` 用 `eq(getBrandId, brandId)` 取配置，`brandId` 为 null 时 SQL 为 `brand_id = null` → **永远查不到** →
  这批「租户级共享池」额度实际是**死配置**（等效于不限流）。修复该行为属独立决策，已登记为 **I180**（本批只登记，未修）。

---

## 6. 边界声明

- 结论基于**本地 docker 栈实测**；未做浏览器侧验证（纯后端改动）。
- 测试数据为隔离行（`brand_id` = 990301–990304、`campaign_name` 前缀 `I178-TEST%`、站内信内容标记 `I178 quota probe`），验证后已全部删除，
  相关表回到基线（`mkt_channel_config` / `marketing_touch_task` / `marketing_touch_log` / `member_message` 中本次痕迹均为 0 行）。
- 受众会员 `crm_member.id` 1–5 **只读**，未新建、未修改。
- **验证期副产物（已清理，但值得登记）**：`InAppSender.parseMemberId` 对数字串一律按 memberId 解析，
  任务流程传入的 receiver 是**手机号**（`13800138001`）→ 被当作 memberId 写进 `member_message.member_id`，
  产生指向不存在会员的脏行。属**既有缺陷**（与 I178 无关），本批未修。
- `raw/00-fixture-seed.sql` 与 `raw/verify-i178.ps1.txt` 标注为**非正式工具**，仅供证据复现，
  **未接入 CI / PR 必跑清单**。脚本为 **ASCII-only**（Windows PowerShell 5.1 无 BOM 时会把非 ASCII 源码按 ANSI 读成乱码）。
