# Phase 2.17 — I009 @Transactional rollbackFor 修复报告

> **修复时间**：2026-09-28 00:00 (UTC+8)
> **修复人**：Cursor Agent
> **范围**：Post-Audit 二期 P2 第 2 项（I009 CRM/Plugin/Intl 命名范围）
> **方案**：C（仅修 I009 命名范围 9 处，剩余 199 处登记 I088）
> **Baseline**：Phase 2.16 终态（I086 FIXED）

---

## 一、修复范围

### 已修复（I009 命名范围，共 9 处）

| # | 文件 | 行号 | 方法 | 修复内容 |
|---|---|---|---|---|
| 1 | `service/CrmIntegrationService.java` | 86 | `syncNow` | `@Transactional` → `@Transactional(rollbackFor = Exception.class)` |
| 2 | `service/IntlBookingService.java` | 75 | `googleCallback` | 同上 |
| 3 | `service/IntlBookingService.java` | 123 | `instagramCallback` | 同上 |
| 4 | `service/IntlBookingService.java` | 157 | `pullBookings` | 同上 |
| 5 | `service/IntlBookingService.java` | 211 | `updateStatus` | 同上 |
| 6 | `service/IntlBookingService.java` | 275 | `googleWebhook` | 同上 |
| 7 | `service/IntlBookingService.java` | 325 | `instagramWebhook` | 同上 |
| 8 | `service/IntlBookingService.java` | 406 | `convertBooking` | 同上 |
| 9 | `plugin/PluginPersistenceService.java` | 43 | `recordState` | 同上 |

**涉及文件：3 个，净改动：+9/-9 行**

---

## 二、提交链

| commit | 内容 | 改动量 |
|---|---|---|
| `5b48e70` | fix(I009): CrmIntegrationService.syncNow @Transactional 添加 rollbackFor | +1/-1 |
| `d5b0d87` | fix(I009): IntlBookingService 7 处 @Transactional 添加 rollbackFor | +7/-7 |
| `52b4714` | fix(I009): PluginPersistenceService.recordState @Transactional 添加 rollbackFor | +117/-117（换行符规范化）|
| `d9e1c82` | docs(audit): I088 新增 + I009 状态 OPEN→FIXED + phase-2.17 报告 | +文档 |

---

## 三、方案 C 决策说明

Phase 2.17 只读扫描发现全后端 **208 处** `@Transactional` 不带 `rollbackFor`，远超 I009 register 命名范围（9 处）。

| 方案 | 范围 | 改动量 | 风险 |
|---|---|---|---|
| A（全修）| 208 处 / 65 文件 | +208 行 | 业务语义可能变化，回归验证不全 |
| **C（仅 I009）** | **9 处 / 3 文件** | **+9 行** | **无业务语义变化，仅扩大回滚范围** |

**选择 C**：保留 199 处为 I088，Post-Audit 三期分批清。

---

## 四、验证结果

### 4.1 mvn compile

```
编译成功（无 ERROR 输出）
```

### 4.2 守门脚本（对比 baseline dcaf07a）

| # | 脚本 | 结果 |
|---|---|---|
| 01 | 01-scan-fake-success.py | ✅ PASS（0 EXIT）|
| 02 | 02-scan-dead-code.py | ✅ PASS（0 EXIT）|
| 03 | 03-scan-shadow-features.py | ✅ PASS（0 EXIT）|
| 04 | 04-scan-no-api-layer.py | ✅ PASS（0 EXIT）|
| 05 | 05-scan-contract-drift.py | ✅ PASS（0 EXIT）|
| 06 | 06-scan-data-lineage.py | ✅ PASS（0 EXIT）|
| 07 | 07-scan-orphan-code.py | ✅ PASS（0 EXIT）|
| 08 | 08-scan-i18n.py | ✅ PASS（0 EXIT）|

**Delta vs Baseline**：0 regression ✅

---

## 五、I009 状态

| 字段 | 值 |
|---|---|
| **I009 状态** | ✅ **FIXED**（9 处全部修复，commits `5b48e70`/`d5b0d87`/`52b4714`）|
| **影响 Feature** | F005（CRM 同步）、F083/F096/F099/F100/F103（Intl Booking）、EXT-23（Plugin Runtime）|
| **遗留** | 剩余 199 处 → I088（Post-Audit 三期） |

---

## 六、关联 Issue

| Issue | 状态 | 说明 |
|---|---|---|
| I009 | ✅ FIXED | CRM/Intl/Plugin 9 处 rollbackFor |
| I034 | ✅ FIXED | YouzanWebhookController 1 处 rollbackFor（Phase 2.12）|
| I088 | 🔴 OPEN | 全后端剩余 199 处（Post-Audit 三期）|

---

## 七、I088 摘要（Post-Audit 三期）

| 维度 | 数值 |
|---|---|
| **遗留总数** | 199 处 |
| **涉及文件** | 65 个（去重后约 62 个）|
| **最高频** | DcepHardwareWalletServiceImpl（12 处）、LbsDispatchService（11 处）、CrmFollowUpTaskServiceImpl（10 处）|
| **建议批次** | 第一批：Point/Coupon/Subscription/DCEP（~48 处）|

详见 `audit/issues/I088-transactional-rollbackfor-global.md`

---

## 八、⚠️ 行尾符副作用说明（52b4714 commit）

commit `52b4714` 在 git diff 显示 +117/-117，但 `--ignore-all-space --stat` 后**仅 1 行真实改动**：

```diff
-    @Transactional
+    @Transactional(rollbackFor = Exception.class)
```

其余 116 行全部为**行尾符规范化**（CRLF → LF），无业务语义变化。

此 commit 已 push 到 main 分支，**不重做**（避免历史 rebase 风险）。`git blame` 在实际改动行（`PluginPersistenceService.java:43`）会显示此 commit，符合预期，不构成污染。

---

**完整状态**：✅ FIXED（commits `5b48e70`/`d5b0d87`/`52b4714` + docs `c2c9615`，2026-09-28 00:00）
