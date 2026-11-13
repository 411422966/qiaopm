# I165 — `report-share` 缺 tenantId 过滤 + 无限流 + 无幂等

> **状态**：**部分修复**（2026-11-14，修复前 HEAD `1bc831f`）→ D1 + D3 已修（证据：`audit/evidence/F162-invite-report-share-hardening/`，VERIFIED）；**D2 限流未做 → 转 I169**
> **严重度**：P1（**既存**越权写入 + 可任意刷计数；任意已登录用户可污染任意租户的分享计数）
> **发现日期**：2026-11-13（I146 C 端邀请端点匿名访问改造 · 只读调查 中的 Q7）
> **登记人**：Cursor
> **拆分依据**：用户 2026-11-13 决策 —— 该缺陷与匿名化无关，**现在就要修**，独立立项
> **绑定基线**：`audit/baseline/env.md` → HEAD commit `03173da0f72c4e0066fa407f96d083601ac1643c`（工作树 dirty，非上线版本）
> **验证等级**：静态代码分析 → 运行期行为 **UNVERIFIED**
> **关联**：I146（来源）、I164（同批拆分）、I166（同批拆分）、I124（权限码体系分裂）、I136（导出异步租户）

---

## 0. 一句话描述

`InviteService.reportShare(codeId)` 用 `codeMapper.selectById(codeId)` **无任何租户校验**取出邀请码，
然后直接 `share_count + 1` 写回 —— **没有 tenantId 过滤、没有频控、没有幂等**。

结果：任意租户的任意登录用户，只要枚举 `codeId`（自增主键），
即可**给任意租户的任意邀请码刷分享计数**；且无任何频控，可用脚本无限刷。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 方法 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `reportShare(Long codeId)`（**L796-801**） |
| 端点 | `POST /invite/{codeId}/report-share` → `backend/src/main/java/com/meiyetec/controller/InviteController.java` L246-249（**`reportShare`**，无 `@RequirePermission`） |
| 表 | `m09_invite_code`（`InviteCode.tenantId` 字段存在，`entity/InviteCode.java` L22；列 `tenant_id` NOT NULL，见 `backend/src/main/resources/db/sql/init_m09_invite.sql` L70） |
| 唯一键 | `uk_campaign_code (campaign_id, invite_code)` + `uk_campaign_member (campaign_id, member_id)`（同文件 L89-90）—— **`id` 为自增主键，可枚举** |

### 1.1 当前代码

`InviteService.java` L794-802：

```java
/**
 * 分享按钮上报（前端点击分享）
 */
@Transactional(rollbackFor = Exception.class)
public Result<Void> reportShare(Long codeId) {
    InviteCode c = codeMapper.selectById(codeId);          // ← ❌ 无 tenantId 校验
    if (c == null) return Result.error(BizCode.NOT_FOUND, "邀请码不存在");
    c.setShareCount((c.getShareCount() == null ? 0 : c.getShareCount()) + 1);   // ← ❌ 无频控 / 无幂等
    codeMapper.updateById(c);
    return Result.success(null);
}
```

`InviteController.java` L245-249：

```java
/**
 * 分享按钮上报（小程序/H5 点击分享按钮）
 */
@PostMapping("/invite/{codeId}/report-share")
public Result<Void> reportShare(@PathVariable Long codeId) {
    return inviteService.reportShare(codeId);
}
```

### 1.2 三项缺陷对照

| # | 缺陷 | 现状 | 后果 |
|---|---|---|---|
| **D1** | **无 tenantId 过滤** | `selectById(codeId)` 直接取，**不比对** `c.getTenantId()` 与当前 `TenantContextHolder` 的 tenantId | 跨租户写入他人数据（越权） |
| **D2** | **无限流** | 无任何频控；Nginx 无 `limit_req`（`frontend/meiyetec-web/nginx.conf`）；应用层无通用限流 | 可脚本无限刷 `share_count` |
| **D3** | **无幂等** | 每次调用都 `+1` | 单次真实点击被重复计数（前端重试 / 双击 / 弱网重发）；且无幂等键 → 无法在 DB 层兜底 |

---

## 2. 缺陷机制

### 2.1 攻击路径

```
租户 A 的任意登录用户
  → POST /invite/invite-code-id/report-share   （codeId 自增，可枚举）
  → JwtInterceptor 解析 token → TenantContext.tenantId = A
  → InviteService.reportShare(codeId)
      ├─ codeMapper.selectById(codeId)   → 命中租户 B 的 m09_invite_code 行
      ├─ 没有任何 tenantId 比对           → ❌ 直接通过
      ├─ c.setShareCount(旧值 + 1)        → B 租户该邀请码的分享数 +1
      └─ codeMapper.updateById(c)         → 落库
```

**无任何报错**：返回 HTTP 200 + `Result.success`，调用方无法察觉越权已发生。

### 2.2 为什么租户隔离没有兜底

- `backend/src/main/java/com/meiyetec/config/MybatisPlusConfig.java` **未启用** `TenantLineInnerInterceptor`（注释：业务表 `tenant_id` 字段不一致，改由 Service 层显式 `eq(tenantId)`）→ **完全依赖 Service 手写条件**。
- `backend/src/main/java/com/meiyetec/security/DataScopeInterceptor.java`：`ctx.getUserId() == null` → 直接放行；且只校验**请求参数里携带的** `tenantId` / `storeId`，**不改写 SQL**。`/invite/{codeId}/report-share` 的 codeId 在 path 里，`tenantId` 不在 query/body → DataScope 层不介入。

> 对比同文件的 `InviteService.myCodes`（L321-323）、`codePage`（L555-557）、`recordPage`（L591-593）等方法**都写了** `eq(InviteCode::getTenantId, tid)` —— `reportShare` 是**同文件内的漏写**，而非全局设计缺失。这佐证了 D1 是**缺陷**而非有意设计。

### 2.3 数据可信度影响

`share_count` 被消费在：
- `InviteService.myCodes` L331 → 响应字段 `shareCount`
- `InviteService.codePage` L569 → B 端邀请码列表
- `InviteCode` 的漏斗统计（`computeCampaignFunnel` 汇总 `scanCount` / `registerCount` / `successCount`，`shareCount` 未进漏斗，但 B 端看板直接展示）

→ 被污染的 `share_count` 会直接出现在运营看板上，**无任何数据血缘标记**说明其不可信。

### 2.4 放大路径

若 I146 的匿名化改造推进（`/invite/*/report-share` 加入 JWT 白名单），本缺陷将从「已登录用户越权」升级为「**任意匿名用户可刷任意租户计数**」。

---

## 3. 修复方案

### 3.1 D1 —— 补 tenantId 校验（必修）

`InviteService.reportShare`：

```java
@Transactional(rollbackFor = Exception.class)
public Result<Void> reportShare(Long codeId) {
    Long tid = TenantContextHolder.get().getTenantId();
    if (tid == null) return Result.error(BizCode.FORBIDDEN, "未识别租户");
    InviteCode c = codeMapper.selectById(codeId);
    if (c == null || !tid.equals(c.getTenantId())) {          // ← 新增租户比对
        return Result.error(BizCode.NOT_FOUND, "邀请码不存在");
    }
    ...
}
```

> 与同文件其他方法的写法一致（如 `campaignDetail` L90-95、`recordDetail` L635-640：先查后比对 tenantId，不一致返回 `NOT_FOUND` 而非 `FORBIDDEN`，避免泄露"资源存在"）。

### 3.2 D3 —— Redis 幂等（用户指定口径）

**幂等键**：`同 codeId + 同 ip + 10 分钟窗口内只计 1 次`。

**实现参考**：仓内已有 Redis 原子幂等范式 —— `backend/src/main/java/com/meiyetec/dcep/operator/NotifyReplayProtector.java` L62-64：

```java
Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(ttlDays));
```

**建议实现**（新增 `InviteService` 私有依赖 `StringRedisTemplate`，`@Autowired(required = false)` 或构造注入）：

```java
private static final String REPORT_SHARE_IDEM_PREFIX = "m09:invite:report-share:";
private static final Duration REPORT_SHARE_IDEM_TTL = Duration.ofMinutes(10);

private boolean acquireReportShareIdempotency(Long codeId, String ip) {
    String key = REPORT_SHARE_IDEM_PREFIX + codeId + ":" + (ip == null ? "unknown" : ip);
    try {
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", REPORT_SHARE_IDEM_TTL);
        return Boolean.TRUE.equals(ok);        // true = 首次，放行；false = 窗口内重复，丢弃
    } catch (Exception e) {
        log.warn("[INVITE] Redis 幂等不可用，降级为不拦截: {}", e.getMessage());
        return true;                            // ← 见 §3.5 的取舍
    }
}
```

**IP 获取**：`InviteController` 已有 `clientIp(HttpServletRequest)` 私有方法（L250-258，支持 `X-Forwarded-For` / `X-Real-IP` / `getRemoteAddr`）→ `reportShare` 端点需新增 `HttpServletRequest` 入参并把 ip 传给 Service。

**幂等命中时的返回**：建议返回 `Result.success("重复上报已忽略", null)`（HTTP 200），**不报错** —— 前端重复点击不应被当作错误。

### 3.3 D2 —— 频控

**建议**：在 §3.2 的 Redis 键上追加**按 ip 的计数限流**（与幂等键分开），例如：
- 幂等键：`m09:invite:report-share:idem:{codeId}:{ip}`，TTL 10 分钟（用户指定）
- 频控键：`m09:invite:report-share:rate:{ip}`，用 `INCR` + `EXPIRE`，阈值建议 **60 次/小时**（待用户确认）

> ⚠️ 限流阈值属业务参数，**需用户拍板**；本 Issue 只给方案与建议默认值，不擅自定值。

### 3.4 修复后的完整流程（示意）

```
POST /invite/{codeId}/report-share
  → tid = TenantContextHolder.get().getTenantId()          // 必须非 null
  → code = codeMapper.selectById(codeId)
  → code == null || !tid.equals(code.getTenantId()) → NOT_FOUND
  → !acquireReportShareIdempotency(codeId, ip) → success("重复上报已忽略")   // 不计数
  → 频控检查（Redis INCR） → 超限 → 拒绝
  → code.setShareCount(+1); codeMapper.updateById(code)
  → success
```

### 3.5 需用户拍板的取舍

| # | 取舍点 | 选项 | 建议 |
|---|---|---|---|
| 1 | **Redis 不可用时的降级方向** | (a) fail-closed：Redis 挂 → 拒绝上报；(b) fail-open：Redis 挂 → 放行计数 | **(b) fail-open**。Redis 挂时拒绝上报会误伤正常用户分享行为（计数是运营指标，非资金）；但需 `log.warn` 留痕。⚠️ 注意 `NotifyReplayProtector` 选的是 fail-closed，因为那是**资金/通知**场景 —— 两者场景性质不同，不可照搬 |
| 2 | **限流阈值与维度** | 按 ip / 按 ip+codeId / 按 codeId | **按 ip**（防脚本批量）；具体阈值待定 |
| 3 | **幂等窗口是否可配** | 硬编码 10 分钟 / 走 `application.yml` 配置 | 建议**走配置**（`mkt.invite.report-share.idempotent-minutes`，默认 10），便于运营调整 |
| 4 | **`share_count` 已有脏数据是否需要校正** | 修复后重算 / 保留现状 | 需先查库统计（**本 Issue 未连接数据库**）。若历史数据已被刷脏，建议加 `share_count` 数据来源标记或一次性重置 |

---

## 4. 验证口径（修复后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 租户 A 用户调租户 B 的 codeId | 返回 **NOT_FOUND**（或 FORBIDDEN），**B 的 `share_count` 不变** |
| 2 | 租户 A 用户调自己的 codeId | `share_count` +1，返回 200 |
| 3 | 同一 ip 同 codeId **10 分钟内重复调用** | 第 2 次起**不计数**，返回 200 且提示已忽略 |
| 4 | 同一 ip 同 codeId **超过 10 分钟**再调 | 正常 +1 |
| 5 | 高频调用触发频控 | 超阈值被拒绝，计数不再增长 |
| 6 | Redis 不可用（模拟） | 端点仍可用（fail-open），日志有 warn |
| 7 | 回归：租户 A 调自己 codeId 的数值变化 | 与修复前一致（仅 +1 一次） |

> 证据落盘位置：`audit/evidence/FXXX-invite-report-share-hardening/`（Feature ID 由实施时分配）。
> ⚠️ 按项目数据安全规则，**写操作测试必须使用绝对不存在的兜底 ID（如 `99999999`）**，或先做存在性检查再执行，避免污染真实数据。

---

## 5. 边界声明

- 本 Issue 结论**全部来自静态代码分析**，**未连接数据库、未执行 SQL、未启动应用、未打开浏览器** → 运行期行为一律 **UNVERIFIED**。
- **未修改任何业务代码**（本 Issue 为登记文档；实际修复需用户单独批准）。
- 未核实：`m09_invite_code` 的**实际存量数据**（`share_count` 是否已被刷脏、是否存在多租户数据）。
- 未核实：当前部署环境 Redis 是否可用（`docker compose` 中 `meiyetec-redis` 健康状态未在本轮确认）。
- 限流阈值、幂等窗口是否可配、Redis 降级方向 —— **均未拍板**（见 §3.5）。

---

**下一步**：等待用户拍板 §3.5 四项取舍 → 实施修复 → 按 §4 采集运行证据 → 落 `audit/evidence/FXXX-.../`。

---

## 6. 修复记录（2026-11-14）

| 项 | 内容 |
|---|---|
| 修复 commit | 修复前 HEAD `1bc831f`（本批 commit：`fix(invite): 修复 my-stats 跨租户 IDOR、report-share 无租户校验、share-page 双计数（I164/I165/I166）`） |
| 改动文件 | `backend/src/main/java/com/meiyetec/service/InviteService.java`（`reportShare` 重写 + 新增 `acquireShareIdempotency`）、`backend/src/main/java/com/meiyetec/controller/InviteController.java`（`reportShare` 增加 `HttpServletRequest` 入参）、`backend/src/main/resources/db/sql/migrate_m09_invite_share_idempotent_window.sql`（新增，`sys_config` 种子） |
| **D1** 修复 | `codeMapper.selectById(codeId)` 后比对 `tid.equals(c.getTenantId())`，不一致返回 `BizCode.NOT_FOUND` +「邀请码不存在」——与「不存在」同结果，不泄露资源是否存在 |
| **D3** 修复 | Redis `SETNX` 幂等，键 `invite:share:{codeId}:{ip}`，TTL 取 `sys_config.invite.share.idempotent.window`（新增行 `id=5 = 600` 秒）；幂等命中不计数但仍返回 200；Redis 不可用 **fail-open**（捕获异常 → `log.warn` → 仍计数） |
| **D2** 限流 | **未实施** —— 仓内检索无通用限流工具（`RedisTemplate` 仅 `NotifyReplayProtector` 一处使用），按用户口径「没有现成工具就只做幂等」→ 单独立 **I169** |
| §3.5 取舍拍板结果 | ① Redis 降级 = **fail-open**（采纳建议 b）；② 限流维度/阈值 = 移至 I169；③ 幂等窗口 = **走 `sys_config`**（采纳建议 3，配置 key `invite.share.idempotent.window`）；④ 历史脏数据 = 不校正 → 转 **I168** |
| 验证 | **VERIFIED（L5a）**：跨租户 codeId=9 修复前 200 且 `share_count` 0→1，修复后 404 且计数不变；同租户 codeId=8 连续 3 次调用 `share_count` 只 0→1；`codeId=99999999` → 404；Redis 键 `invite:share:8:172.25.0.1` 实测 `TTL=579` |
| 证据 | `audit/evidence/F162-invite-report-share-hardening/` |
| **本轮复跑**（2026-11-14 重建夹具后） | **7/7 PASS**：`raw/05-verify-rerun.txt` —— S1/S2（I164 my-stats）、S3 跨租户 404 且 `share_count` 0→0、S4 同租户 3 次调用仅首次 +1、S5 `share-page` 后 `scan_count` 0→0、S6 `codeId=99999999` → 404、EXTRA 显式 `POST /invite/track` 仍 +1。Redis 键 `invite:share:8:172.25.0.1` 实测 `TTL=584`（`raw/05-redis-idempotency-keys.txt`） |
| 遗留 UNVERIFIED | Redis 故障 fail-open 分支未做故障注入实测；幂等窗口过期后重新计数未等满 600 秒复测 |
