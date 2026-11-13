# F162 · `POST /invite/{codeId}/report-share` 租户校验 + 幂等加固（I165）

> 状态：**VERIFIED**（跨租户计数写入被拒、幂等窗口内重复上报不重复计数，均有实际运行证据）
> 基线 commit：`1bc831f`（修复前 HEAD，工作树 clean）
> DB 快照：本目录 `raw/`
> 环境基线：`audit/baseline/env.md`
> 对应 Issue：`audit/issues/I165-invite-report-share-tenant-idor.md`
> 改动文件：
> - `backend/src/main/java/com/meiyetec/service/InviteService.java`（`reportShare` 重写 + 新增 `acquireShareIdempotency`）
> - `backend/src/main/java/com/meiyetec/controller/InviteController.java`（`reportShare` 增加 `HttpServletRequest` 入参，透传 `clientIp`）
> - `backend/src/main/resources/db/sql/migrate_m09_invite_share_idempotent_window.sql`（新增，`sys_config` 幂等窗口种子）

---

## 1. 三项缺陷与修复对照

| # | 缺陷 | 修复前代码 | 修复后 |
|---|---|---|---|
| **D1** | 无 tenantId 过滤 → 可给任意租户刷 `share_count` | `codeMapper.selectById(codeId)` 直接取，无比对 | 比对 `tid.equals(c.getTenantId())`，不一致返回 `NOT_FOUND`（与「不存在」同结果，不泄露资源是否存在） |
| **D2** | 无限流 | 无 | **本轮未做**（仓内无现成限流工具）→ 单独立 **I169** |
| **D3** | 无幂等 | 每次调用都 `+1` | Redis `SETNX`（`invite:share:{codeId}:{ip}`），TTL 取 `sys_config.invite.share.idempotent.window`（默认 600 秒）；命中则不计数但仍返回成功 |

### 1.1 幂等键与降级策略（实测口径）

| 项 | 实现 | 实测值 |
|---|---|---|
| 幂等键格式 | `invite:share:{codeId}:{ip}` | `invite:share:8:172.25.0.1`（容器内看到的客户端 IP 为 docker 网关） |
| TTL 来源 | `sys_config.invite.share.idempotent.window` | 新增行 `id=5, config_value=600` → 实测 `TTL = 579`（刚写入后） |
| Redis 不可用 | **fail-open**：捕获异常 → `log.warn` → 仍执行 `share_count + 1` | 代码路径 `acquireShareIdempotency` catch 分支（本次 Redis 正常，**fail-open 分支未做故障注入实测 → UNVERIFIED**） |
| 幂等命中返回 | `Result.success(null)`（200），不暴露命中与否 | ✅ 实测第 2、3 次调用返回 200 |

> ⚠️ 本轮**未**做限流（D2）：仓内检索无通用限流工具（`RedisTemplate` 仅 `NotifyReplayProtector` 一处使用），
> 按用户口径「没有现成限流工具就只做幂等，限流单独立 I169」执行。

---

## 2. 验证环境

| 项 | 值 |
|---|---|
| 令牌 | `operator01`（`tenant_id=1`） |
| 被测邀请码 | `id=8` `TESTI164A`（**tenant 1**，本租户）/ `id=9` `TESTI165B`（**tenant 2**，跨租户目标） |
| 不存在 ID | `99999999`（按项目规则使用绝对不存在的兜底 ID） |
| 隔离性 | 两张 `m09_invite_code` 表在本轮进入时为 0 行，测试行验证后已删除 |

---

## 3. 修复前后对照（原始报文 + DB 行级计数）

### 3.1 跨租户上报（codeId=9，属于 tenant 2）

| 阶段 | 响应 | `m09_invite_code.id=9.share_count` |
|---|---|---|
| 修复前 | `{"code":200,"message":"操作成功","data":null}` | 0 → **1**（越权写入成功） |
| 修复后 | `{"code":404,"message":"资源不存在: 邀请码不存在"}` | 0 → **0**（未计数） |

### 3.2 同租户重复上报（codeId=8，同 IP）

| 调用 | 修复前 | 修复后 |
|---|---|---|
| call#1 | 200，`share_count` 0 → 1 | 200，`share_count` 0 → **1** |
| call#2 | 200，`share_count` 1 → **2**（重复计数） | 200，`share_count` 保持 **1**（幂等命中，不计数） |
| call#3 | — | 200，`share_count` 保持 **1** |

### 3.3 不存在的 ID

| 场景 | 修复后 |
|---|---|
| `POST /invite/99999999/report-share` | `{"code":404,"message":"资源不存在: 邀请码不存在"}`，`share_count` 无变化 |

> 响应体为「跨租户」与「不存在」**完全一致**（均为 404 + 同一 message）→ 满足「静默防探测」口径。
> 注：用户原话「不一致返回 null」按「不执行计数、不泄露存在性」实现为 `NOT_FOUND`（与同文件
> `campaignDetail` / `recordDetail` 的写法一致），若严格按字面返回 `success(null)`，反而会因
> 「跨租户 200 / 不存在 404」的差异泄露资源是否存在。

原始文件：
- 修复前：`raw/01-before-report-share.txt`、`raw/02-before-db-counters.txt`
- 修复后：`raw/03-after-report-share.txt`、`raw/04-after-db-counters.txt`

---

## 3.1 独立复跑（2026-11-14，重建夹具后）

> 目的：不依赖上一轮「修复前」环境，独立确认**修复后的当前运行态**行为仍然成立。
> 做法：按 `raw/00-fixture-seed.sql` 重建隔离夹具 → 清空 Redis 幂等键 → 跑 `raw/verify-rerun.ps1` → 验证后删除夹具，4 张 `m09_*` 表回到 0 行基线。

| 场景 | 请求 | 期望 | 实测 |
|---|---|---|---|
| S3 | `POST /invite/9/report-share`（codeId=9 属 tenant 2，调用方 tenant 1） | 非 200 + `share_count` 不变 | ✅ PASS（`code=404 资源不存在: 邀请码不存在`，`share_count` 0→0） |
| S4 | `POST /invite/8/report-share` ×3（同租户同 IP） | 第 1 次 +1，后 2 次 200 且计数不变 | ✅ PASS（1 → 1 → 1） |
| S6 | `POST /invite/99999999/report-share` | 非 200 + 目标行不存在 | ✅ PASS（`code=404`，`id=99999999` 存在性检查 0 行） |

> 复跑时 DB 现场：`m09_invite_code.id=9.share_count` 前后均为 **0**；`id=8.share_count` 由 **0 → 1** 后保持不变。

原始文件：`raw/05-verify-rerun.txt`、`raw/05-redis-idempotency-keys.txt`、`raw/06-after-rerun-db-counters.txt`

---

## 4. 验证等级

| 项 | 等级 | 说明 |
|---|---|---|
| D1 跨租户写入被拒 | **L5a** | 实际运行报文 + DB 计数前后对照 |
| D3 幂等（同 codeId + 同 IP 窗口内只计一次） | **L5a** | 实际运行报文 + DB 计数 + Redis 键/TTL 实测 |
| 幂等 TTL 走 `sys_config` | **L5a** | `sys_config` 行 `id=5 = 600`；Redis `TTL=579` 实测 |
| 独立复跑（S3/S4/S6） | **L5a** | 2026-11-14 重建夹具后重新实测，7/7 PASS（与 I164/I166 同一批脚本） |
| D2 频控（60 次/分钟） | **未实施** | 仓内无现成限流工具 → 转 **I169** |
| Redis 故障 fail-open 分支 | **UNVERIFIED** | 未做故障注入；仅静态核对 catch 分支 |
| 幂等窗口过期后重新计数 | **UNVERIFIED** | 未等待 600 秒窗口过期复测 |

---

## 5. 边界声明

- 结论基于实际运行证据；未做浏览器侧验证（纯后端改动）。
- 测试数据为隔离行，已全部删除（`m09_invite_code` / `m09_invite_record` / `m09_invite_reward_log` / `m09_invite_campaign` 均回到 0 行）。
- 新增 DB 对象 1 条：`sys_config` 行 `invite.share.idempotent.window = 600`（运营可调）。
- 本 Feature 未处理历史 `share_count` 脏数据（I165 §3.5 取舍 4）→ 转 **I168**。
- `raw/00-fixture-seed.sql` 与 `raw/verify-rerun.ps1` 标注为**非正式工具**，仅供证据复现，**未接入 CI / PR 必跑清单**。
  > 注：仓库 `.gitignore` 第 143 行 `*.ps1` 会排除复跑脚本 → `verify-rerun.ps1` **仅本地留存、未入库**，他人复现需按同口径自建脚本。
