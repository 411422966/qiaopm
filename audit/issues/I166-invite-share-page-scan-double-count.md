# I166 — `share-page` 隐式调用 `trackClick`，导致 `scanCount` 双计数

> **状态**：Open
> **严重度**：P2（**既存**数据口径缺陷，不报错、不报错码，只让运营看板的扫码数虚高一倍）
> **发现日期**：2026-11-13（I146 C 端邀请端点匿名访问改造 · 只读调查 中的 Q8）
> **登记人**：Cursor
> **拆分依据**：用户 2026-11-13 决策 —— 与匿名化无关，独立立项
> **绑定基线**：`audit/baseline/env.md` → HEAD commit `03173da0f72c4e0066fa407f96d083601ac1643c`（工作树 dirty，非上线版本）
> **验证等级**：静态代码分析 → 运行期行为 **UNVERIFIED**
> **关联**：I146（来源）、I164、I165（同批拆分）、I149（M09 邀请奖励链路缺陷）

---

## 0. 一句话描述

`InviteController.sharePage`（分享落地页数据）在返回结果前**隐式调用**了一次
`inviteService.trackClick(code, null, "LINK", null, null)`。

而 `trackClick` 内部**无条件执行** `code.setScanCount(+1)`（`InviteService.java` L403），
**与是否带 phone 无关**。

→ 前端若按常规做法同时调用 `share-page`（拿落地页数据）+ `track`（显式埋点），
**一次真实访问会让 `scan_count` +2**。

---

## 1. 缺陷位置

| 项 | 内容 |
|---|---|
| 方法 | `backend/src/main/java/com/meiyetec/controller/InviteController.java` — `sharePage`（**L161-171**） |
| 被隐式调用的方法 | `backend/src/main/java/com/meiyetec/service/InviteService.java` — `trackClick`（L383），其中 **L403** 执行 `scanCount + 1` |
| 计数器字段 | `InviteCode.scanCount`（`entity/InviteCode.java`，列 `scan_count`，表 `m09_invite_code`） |
| 前端 API | `frontend/meiyetec-web/src/api/invite.ts` L176-182：`sharePage(code)` 与 `trackClick({code, phone?, channel?})` **是两个独立方法** |
| 显式埋点端点 | `POST /invite/track` → `InviteController.trackClick`（L177-186） |

### 1.1 当前代码

`InviteController.java` L158-172：

```java
/**
 * 分享落地页数据：扫码/点链接进来后获取活动信息
 */
@GetMapping("/invite/share-page")
public Result<Map<String, Object>> sharePage(@RequestParam String code) {
    Long tid = tid();
    if (tid == null) return Result.error(401, "未识别租户");
    // 先触发扫码上报
    InviteCampaign activity = null;                       // ← 未使用的局部变量（死代码）
    try {
        inviteService.trackClick(code, null, "LINK", null, null);   // ← ❌ 隐式埋点：scanCount +1
    } catch (Exception ignore) {}                          // ← ❌ 异常被吞，无日志
    // 简化：直接通过 controller 内部获取（refactor 留待后续）
    return inviteService.getSharePage(code, tid);
}
```

`InviteService.trackClick` L395-406（关键片段）：

```java
InviteCode code = codeMapper.selectOne(...);
if (code == null) {
    return Result.success(null); // 静默不报错：防刷
}
InviteCampaign c = campaignMapper.selectById(code.getCampaignId());
if (c == null || c.getStatus() != 1) {
    return Result.success(null);
}
LocalDateTime now = LocalDateTime.now();
if (c.getStartTime() != null && c.getStartTime().isAfter(now)) return Result.success(null);
if (c.getEndTime() != null && c.getEndTime().isBefore(now)) return Result.success(null);

// 累加扫码数（即使后续不注册也记录）
code.setScanCount((code.getScanCount() == null ? 0 : code.getScanCount()) + 1);   // ← L403 无条件 +1
codeMapper.updateById(code);
```

---

## 2. 缺陷机制

### 2.1 双计数路径

```
用户打开分享链接（H5/小程序落地页）
  → 前端调 GET /invite/share-page?code=XXX
      → sharePage 内部隐式 trackClick(code, null, "LINK", null, null)
          → phone == null → 不建 record
          → 但 L403 仍执行 scan_count + 1          ⇒ scanCount = 1
      → 返回落地页数据
  → 前端再调 POST /invite/track {code, phone?, channel}
      → trackClick 正常执行
      → L403 再次 scan_count + 1                   ⇒ scanCount = 2
  → 真实访问 1 次，scanCount = 2                     ⇒ ❌ 虚高一倍
```

### 2.2 为什么 `phone=null` 不能规避

`trackClick` 中 `scan_count` 的自增发生在**建 record 之前**、且**不依赖 phone**：

```
L403  scan_count + 1        ← 无条件
L437  if (phone == null || phone.isBlank()) return;   ← 提前返回点在自增之后
```

所以 `sharePage` 传的 `phone=null` **不会**阻止扫码数自增。

### 2.3 附带缺陷

| # | 缺陷 | 位置 | 影响 |
|---|---|---|---|
| S1 | **死代码局部变量** | `InviteController.java` L165 `InviteCampaign activity = null;` | 从未被使用（`getSharePage` 内部自己查活动），误导阅读者以为 Controller 层有用 |
| S2 | **异常被静默吞掉** | `InviteController.java` L168 `catch (Exception ignore) {}` | `trackClick` 内部抛异常时**无任何日志**，线上不可观测；且吞掉的是**写操作**失败 |
| S3 | **`scan_count` 被重复计入漏斗分母** | `InviteService.computeCampaignFunnel` 用 `scanCount` 作分母算 `conversionRate` | 分母虚高 → **转化率被系统性低估**（`conversionRate = firstOrderCount / scanCount`） |
| S4 | **`getSharePage` 本身完全够用** | `InviteService.getSharePage`（L272-311） | 落地页数据获取**不依赖** `trackClick`，隐式调用是纯副作用叠加，无功能必要 |

### 2.4 影响面

`scanCount` 的消费点：
- `InviteService.myCodes` L331 → C 端「我的邀请码」响应 `scanCount`
- `InviteService.codePage` L569 → B 端邀请码列表
- `InviteService.computeCampaignFunnel` L~700 → `scanCount` / `conversionRate` → `campaignPage` L80 / `campaignDetail` / `dashboardStats` L~700 → **B 端看板**
- `InviteService.trackClick` L445 → `registerCount` 自增（`registerCount` 不受影响，它只在有 phone 且建 record 时自增）

→ **只有 `scan_count` 与由它派生的 `conversionRate` 受影响**；`register_count` / `success_count` / `reward_count` 不受影响（后者按 `m09_invite_record.status` 统计）。

### 2.5 前端当前状态

`frontend/meiyetec-web/src/api/invite.ts` L176-182 已分别封装 `sharePage` 与 `trackClick`，
但按 I146 的 F1 发现，**这两个方法当前在前端没有任何调用方**（`frontend/**` 中 `invite` 仅命中 B 端后台 `views/m09-invite/index.vue` 与 B 端测试台 `views/lifecycle/index.vue`）。

→ **当前生产影响为 0**（没人调）**；但一旦 I146 推进、前端 C 端落地页上线，双计数立即生效**。这正是"必须在 C 端页面上线前修掉"的原因。

---

## 3. 修复方案

### 3.1 核心修复（用户指定口径）

**移除 `sharePage` 内部的隐式 `trackClick`，埋点只由显式 `POST /invite/track` 产生。**

`backend/src/main/java/com/meiyetec/controller/InviteController.java` L158-172 改为：

```java
/**
 * 分享落地页数据：扫码/点链接进来后获取活动信息
 *
 * <p>注意：本端点为「只读」语义，不做任何埋点副作用。
 * 扫码计数（scan_count）只由显式 {@code POST /invite/track} 产生，
 * 否则前端同时调用 share-page + track 会造成一次访问计数两次（见 I166）。
 */
@GetMapping("/invite/share-page")
public Result<Map<String, Object>> sharePage(@RequestParam String code) {
    Long tid = tid();
    if (tid == null) return Result.error(401, "未识别租户");
    return inviteService.getSharePage(code, tid);
}
```

改动点：
1. 删除 `inviteService.trackClick(code, null, "LINK", null, null)` 调用及其 `try/catch`
2. 删除死代码局部变量 `InviteCampaign activity = null;`（S1）
3. 补注释说明「本端点无埋点副作用」的口径约定，避免后续被再加回来

### 3.2 需一并处理的口径约定（写入注释 / 文档）

| 端点 | 副作用 | 约定 |
|---|---|---|
| `GET /invite/share-page` | **无** | 只读；不写任何计数 |
| `POST /invite/track` | `scan_count + 1`（无 phone 时）；有 phone 时另建 record + `register_count + 1` | **埋点的唯一来源** |
| `POST /invite/{codeId}/report-share` | `share_count + 1` | 见 I165 |

### 3.3 前端配合（若 C 端落地页上线）

- 落地页在**首次渲染时调用一次** `POST /invite/track`（带 `code` + `channel`），**不得**依赖 `share-page` 顺带计数。
- ⚠️ 前端**当前无任何调用方**，因此**前端零改动**即可修复本 Issue。

### 3.4 历史数据校正（可选，需用户拍板）

修复只对**新流量**生效；历史 `scan_count` 已含双计数部分。
- 无法从现有数据反推真实扫码数（同一 code 的「share-page 次数」与「track 次数」未分别记录）
- 可选方案：(a) 保留现状 + 在看板标注口径变更时间点；(b) 按上线时间点做**近似折算**（不可靠）
- **建议 (a)**，并在本 Issue 留一条数据口径变更记录。

---

## 4. 验证口径（修复后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 调 `GET /invite/share-page?code=X` 前后查 `m09_invite_code.scan_count` | **不变** |
| 2 | 调 `POST /invite/track {code:X}` 一次 | `scan_count` **+1**（不是 +2） |
| 3 | 完整模拟真实落地页访问（share-page + track 各一次） | `scan_count` 合计 **+1** |
| 4 | `GET /invite/share-page` 响应内容 | 与修复前**完全一致**（`getSharePage` 未改） |
| 5 | 回归：`track` 带 phone 时建 record + `register_count + 1` | 行为不变 |
| 6 | 看板 `conversionRate` | 分母口径随 `scan_count` 修正而变化，**不报错** |

> 证据落盘位置：`audit/evidence/FXXX-invite-share-page-track-fix/`（Feature ID 由实施时分配）。
> ⚠️ 按项目数据安全规则，**写操作测试使用绝对不存在的兜底 ID（如 `99999999`）或隔离测试数据**，运行前先做存在性检查。

---

## 5. 边界声明

- 本 Issue 结论**全部来自静态代码分析**，**未连接数据库、未执行 SQL、未启动应用、未打开浏览器** → 运行期行为一律 **UNVERIFIED**。
- **未修改任何业务代码**（本 Issue 为登记文档；实际修复需用户单独批准）。
- 未核实：`m09_invite_code.scan_count` 的**实际存量数据**（历史双计数的具体幅度无法量化）。
- 未核实：`/invite/share-page` 在当前部署环境的**真实可达性**（`InviteController` 已于 F150 解封，见 `audit/evidence/F150-m09-invite-unseal/`）。
- 历史数据校正方案（§3.4）**未拍板**。

---

**下一步**：等待用户批准修复（§3.1 + §3.2）→ 实施 → 按 §4 采集运行证据 → 落 `audit/evidence/FXXX-.../`。
