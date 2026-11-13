# I164 — `my-stats` 三个计数缺 tenantId 过滤，导致跨租户 IDOR

> **状态**：Open
> **严重度**：P1（**既存**越权读取，非匿名化引入；任意已登录用户可读其他租户的邀请统计）
> **发现日期**：2026-11-13（I146 C 端邀请端点匿名访问改造 · 只读调查 中的 Q6）
> **登记人**：Cursor
> **拆分依据**：用户 2026-11-13 决策 —— 该缺陷与匿名化无关，**现在就要修**，独立立项
> **绑定基线**：`audit/baseline/env.md` → HEAD commit `03173da0f72c4e0066fa407f96d083601ac1643c`（工作树 dirty，非上线版本）
> **验证等级**：静态代码分析 → 运行期行为 **UNVERIFIED**
> **关联**：I146（来源）、I149（M09 邀请奖励链路缺陷）、I165、I166、I117（DataScope 覆盖缺口）、F150（M09 解封证据）

---

## 0. 一句话描述

`InviteService.myStats(memberId)` 返回的 4 个计数中，**只有 `totalCodes` 带 `tenantId` 过滤**，
其余 3 个（`successCount` / `pendingReward` / `rewardLogs`）**只按 `memberId` + `status` 过滤**。

结果：租户 A 的任意登录用户只要传一个属于租户 B 的 `memberId`，即可读到 B 租户的
**成功邀请数 / 待发奖数 / 已发奖励数** —— 一个**既存的跨租户 IDOR**。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 方法 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `myStats(Long memberId)`（**L353**） |
| 端点 | `GET /invite/my-stats?memberId=` → `backend/src/main/java/com/meiyetec/controller/InviteController.java` L238-241（**`myStats`**，无 `@RequirePermission`） |
| 表 | `m09_invite_record`（`InviteRecord.tenantId` 字段存在，`entity/InviteRecord.java` L26）、`m09_invite_reward_log`（`InviteRewardLog.tenantId` 存在，`entity/InviteRewardLog.java` L24） |

### 1.1 当前代码（`InviteService.java` L353-366）

```java
public Result<Map<String, Object>> myStats(Long memberId) {
    Long tid = TenantContextHolder.get().getTenantId();
    Map<String, Object> stats = new HashMap<>();
    Long totalCodes = codeMapper.selectCount(new LambdaQueryWrapper<InviteCode>()
            .eq(InviteCode::getMemberId, memberId).eq(InviteCode::getTenantId, tid));   // ✅ 有 tenantId
    Long successCount = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
            .eq(InviteRecord::getInviterMemberId, memberId).eq(InviteRecord::getStatus, 4));  // ❌ 无 tenantId
    Long pendingReward = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
            .eq(InviteRecord::getInviterMemberId, memberId)
            .in(InviteRecord::getStatus, 2, 3));                                            // ❌ 无 tenantId
    Long rewardLogs = rewardLogMapper.selectCount(new LambdaQueryWrapper<InviteRewardLog>()
            .eq(InviteRewardLog::getMemberId, memberId).eq(InviteRewardLog::getStatus, 1));  // ❌ 无 tenantId
    stats.put("totalCampaigns", totalCodes);
    stats.put("successInvites", successCount);
    stats.put("pendingRewards", pendingReward);
    stats.put("rewardReceived", rewardLogs);
    return Result.success(stats);
}
```

### 1.2 逐项对照

| 返回字段 | 统计对象 | 过滤条件 | 是否含 `tenantId` |
|---|---|---|---|
| `totalCampaigns` | `m09_invite_code` | `memberId` + `tenantId` | ✅ 有 |
| `successInvites` | `m09_invite_record` | `inviterMemberId` + `status=4` | ❌ **缺** |
| `pendingRewards` | `m09_invite_record` | `inviterMemberId` + `status IN (2,3)` | ❌ **缺** |
| `rewardReceived` | `m09_invite_reward_log` | `memberId` + `status=1` | ❌ **缺** |

---

## 2. 缺陷机制

### 2.1 为什么租户隔离没有兜底

- `backend/src/main/java/com/meiyetec/config/MybatisPlusConfig.java` **未启用** `TenantLineInnerInterceptor`（注释明确：业务表 `tenant_id` 字段不一致，改由 Service 层显式 `eq(tenantId)`）→ **租户隔离 100% 依赖各 Service 手写条件**。
- `backend/src/main/java/com/meiyetec/security/DataScopeInterceptor.java` 的逻辑是「`ctx.getUserId() == null` → 直接 return true（完全放行）」；且它只校验**请求里携带的** `tenantId` / `storeId` 参数，**不改写 SQL**。`/invite/my-stats` 是 GET，query 里没有 `tenantId` 参数 → DataScope 层完全不介入。
- 结论：**Service 少写一个 `eq(tenantId)`，就是一处真实的跨租户泄露**，没有任何其他机制兜底。

### 2.2 攻击路径

```
租户 A 的普通登录用户（任意角色，接口无 @RequirePermission）
  → GET /invite/my-stats?memberId=<租户 B 的会员 ID>
  → JwtInterceptor 解析 token → TenantContext.tenantId = A
  → InviteService.myStats(memberId)
      ├─ totalCodes     : memberId + tenantId=A → 命中 0 条（正确隔离）
      ├─ successCount   : memberId + status=4   → 命中 B 租户的记录 ❌
      ├─ pendingReward  : memberId + status IN(2,3) → 命中 B 租户的记录 ❌
      └─ rewardLogs     : memberId + status=1   → 命中 B 租户的记录 ❌
  → 返回 B 租户的邀请成功数 / 待发奖数 / 已发奖励数
```

**信息泄露内容**：B 租户的裂变邀请运营数据（成功邀请数、待发奖数、已发奖励笔数）。虽非 PII，但属于**跨租户业务数据**，在多租户 SaaS 下属于越权。

**放大路径**：若 I146 的匿名化改造推进（`/invite/my-stats` 加入 JWT 白名单），本缺陷将从「已登录用户越权」升级为「**任意匿名用户遍历 memberId 读全平台统计**」。

### 2.3 前提条件

- 需存在**至少两个租户**且都有 `m09_invite_record` / `m09_invite_reward_log` 数据。**当前库是否满足此条件未核实**（本 Issue 未连接数据库，标 UNVERIFIED）。
- `memberId` 需可枚举。`crm_member.id` 是自增主键 → **可枚举**。

---

## 3. 修复方案

### 3.1 最小修复（三个查询各补一个 `tenantId` 条件）

`backend/src/main/java/com/meiyetec/service/InviteService.java` `myStats`：

```java
Long successCount = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
        .eq(InviteRecord::getInviterMemberId, memberId)
        .eq(InviteRecord::getTenantId, tid)            // ← 新增
        .eq(InviteRecord::getStatus, 4));
Long pendingReward = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
        .eq(InviteRecord::getInviterMemberId, memberId)
        .eq(InviteRecord::getTenantId, tid)            // ← 新增
        .in(InviteRecord::getStatus, 2, 3));
Long rewardLogs = rewardLogMapper.selectCount(new LambdaQueryWrapper<InviteRewardLog>()
        .eq(InviteRewardLog::getMemberId, memberId)
        .eq(InviteRewardLog::getTenantId, tid)         // ← 新增
        .eq(InviteRewardLog::getStatus, 1));
```

### 3.2 建议一并处理（可选，超出最小修复范围，需单独确认）

| # | 建议 | 说明 |
|---|---|---|
| 1 | `memberId` 与登录会员一致性校验 | 「我的统计」应只返回**当前登录会员自己**的数据；`memberId` 由 query 传入本身即为设计缺陷。建议 Controller 层从 `TenantContext` 取 `memberId`（或至少校验 `memberId` 属于当前租户），**不信任客户端传入的 memberId** |
| 2 | 端点权限注解 | `InviteController.myStats` 无 `@RequirePermission`，任何登录用户可调 |
| 3 | 统计口径一致性 | `myStats` 的 `totalCampaigns` 实际统计的是**邀请码数**，字段名与语义不符（`campaigns` vs `codes`） |

> ⚠️ 按项目规则，§3.2 属于**超出本次授权范围**的改动，需用户单独批准；本轮仅按 §3.1 修复。

---

## 4. 验证口径（修复后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 租户 A 用户传租户 B 的 memberId 调 `/invite/my-stats` | `successInvites` / `pendingRewards` / `rewardReceived` **均为 0**（或仅 A 租户自身数据） |
| 2 | 租户 A 用户传**自己**的 memberId | 三个计数与修复前**数值一致**（回归不退化） |
| 3 | 直接核对 SQL | 三个 `selectCount` 的 WHERE 均含 `tenant_id = ?` |
| 4 | 单租户场景回归 | 数值不变（`tenant_id` 恒定，过滤无副作用） |

> 证据落盘位置：`audit/evidence/FXXX-invite-my-stats-tenant-filter/`（Feature ID 由实施时分配）。

---

## 5. 边界声明

- 本 Issue 结论**全部来自静态代码分析**，**未连接数据库、未执行 SQL、未启动应用、未打开浏览器** → 运行期行为一律 **UNVERIFIED**。
- **未修改任何业务代码**（本 Issue 为登记文档；实际修复需用户单独批准并落盘证据）。
- 未核实：`crm_member` / `m09_invite_record` / `m09_invite_reward_log` 的**实际存量数据与租户分布**。
- 未核实：`/invite/my-stats` 在当前部署环境是否真实可达（`InviteController` 已于 F150 解封，见 `audit/evidence/F150-m09-invite-unseal/`）。

---

**下一步**：等待用户批准修复 → 实施 §3.1 → 按 §4 采集运行证据 → 落 `audit/evidence/FXXX-.../`。
