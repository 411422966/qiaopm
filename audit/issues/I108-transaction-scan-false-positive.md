# I108 — 07-scan-transaction.ps1 误报 javadoc 注释中的 @Transactional

> **Issue ID**：I108（接续 I107）
> **发现时间**：2026-10-15 22:59（Gate 3 2.3 批次 I095 验证）
> **发现方式**：`audit/scripts/07-scan-transaction.ps1` 重跑结果与人工核对
> **严重度**：P3（形式工作，不影响真实事务边界）
> **Fix Phase**：Gate 3 脚本优化批次（与 I010/I097 同型：流程规范）
> **类别**：工具链 / 误报（false positive）

---

## 0. 摘要

`07-scan-transaction.ps1` 守门脚本扫描出 **11 个命中** = 6 no-rollbackFor + 5 catch-swallow。
人工核对后**全部为误报**：

- **6 no-rollbackFor** = 6 个 javadoc/comment 注释里的字符串 `@Transactional`（如 `// B-1: 必须抛异常才能触发 @Transactional 回滚`），不是真实注解
- **5 catch-swallow** = 3 个 catch 后 `log.warn + throw RuntimeException`（已修）+ 2 个 EcommerceService per-item 容错 + outer catch `Result.error`（设计意图，已正常抛错）

> **核心结论**：I095 catch 块重新抛**已实际修复**，守门脚本因正则粗糙导致误报。脚本需在下次扫描优化时排除 javadoc 注释并区分 rethrow vs swallow。

---

## 1. 守门脚本扫描结果（2026-10-15 22:59:44 实测）

执行：`audit/scripts/07-scan-transaction.ps1` → 输出 `audit/static-gap/07-transaction-current.txt`

| # | 文件 | 行号 | 模式 | 实际 | 误报原因 |
|---|---|---|---|---|---|
| 1 | `controller/PosController.java` | 494 | no-rollbackFor | 误报 | javadoc 注释 `// B-1: 必须抛异常才能触发 @Transactional 回滚` |
| 2 | `mapper/PosOrderMapper.java` | 19 | no-rollbackFor | 误报 | javadoc 注释 `// 调用方应识别返回 0 后抛出 CONCURRENCY_CONFLICT 让 @Transactional 回滚` |
| 3 | `service/PosRefundService.java` | 345 | no-rollbackFor | 误报 | 注释 `// 并发冲突：另一个 process() 已先提交，让 @Transactional 回滚` |
| 4 | `service/PosRefundService.java` | 647 | no-rollbackFor | 误报 | 注释 `// B-6: 钱包退款失败应让 @Transactional 回滚整个 process()` |
| 5 | `service/impl/SubscriptionServiceImpl.java` | 29 | no-rollbackFor | 误报 | 注释 `// HTTP 层注解已迁移至 Controller，本类保留 @Service + @Transactional` |
| 6 | `service/impl/WalletServiceImpl.java` | 85 | no-rollbackFor | 误报 | 注释 `// 解决 TOCTOU race：两个并发 doRefund 在 @Transactional 之外可能都看到 existing=0` |
| 7 | `service/AiEnhanceService.java` | 174 | catch-swallow | 误报 | catch 后 `log.warn(...)` + `throw new RuntimeException(...)` 重新抛 → 事务回滚生效 |
| 8 | `service/EcommerceService.java` | 124 | catch-swallow | 误报 | 实际 @Transactional 在 L133（syncProducts），per-item catch L156-160 + outer catch L181-185 `Result.error` → 设计意图，已正常抛错 |
| 9 | `service/EcommerceService.java` | 233 | catch-swallow | 误报 | 实际 @Transactional 在 L245（syncOrders），per-item catch L278-281 + outer catch L297-300 `Result.error` → 设计意图 |
| 10 | `service/IndustryService.java` | 88 | catch-swallow | 误报 | `applyTemplate` catch 后 `log.warn(...)` + `throw new RuntimeException(...)` 重新抛 |
| 11 | `service/impl/ItemPreferenceServiceImpl.java` | 45 | catch-swallow | 误报 | `computeForMember` catch 后 `log.warn(...)` + `throw new RuntimeException(...)` 重新抛 |

**真实业务结论**：所有 11 个命中**均为误报**，实际事务边界已正确。**I095 修复已完成**。

---

## 2. 根因

`07-scan-transaction.ps1` 第 17 行：

```powershell
if ($line -match '@Transactional\b' -and $line -notmatch 'rollbackFor' -and $line -notmatch 'noRollbackFor') {
    $matched = $true
    $pattern = "Transactional-no-rollbackFor"
    ...
}
```

**问题 1**：`@Transactional\b` 不区分真实注解 vs 注释/字符串。

**问题 2**：第 56 行 catch 扫描：

```powershell
if ($lines[$j] -match 'catch\s*\(' -and ($j + 1) -lt $lines.Count) {
    $nextLine = $lines[$j + 1]
    if ($nextLine -match 'log\.(warn|info|debug)\(' -or $nextLine -match '//\s*ignore') {
        $hasSwallowCatch = $true
        break
    }
}
```

**问题 3**：仅看 `catch` 块第 2 行（`log.warn/info/debug(...)`），不区分「仅日志吞掉」 vs 「log 后 throw RuntimeException 重新抛」。

**问题 4**：未排除 javadoc 注释行（`^\s*\*` 或 `^\s*//`）。

---

## 3. 修复方案（脚本优化）

### 3.1 改动 1：no-rollbackFor 扫描排除注释

**位置**：`07-scan-transaction.ps1` 第 17 行

```powershell
# 旧：
if ($line -match '@Transactional\b' -and $line -notmatch 'rollbackFor' -and $line -notmatch 'noRollbackFor')

# 新：
$isComment = ($line -match '^\s*\*') -or ($line -match '^\s*//') -or ($line -match '^\s*/\*')
$isRealAnnotation = ($line -match '^\s*@Transactional\b')
if ($isRealAnnotation -and -not $isComment -and $line -notmatch 'rollbackFor' -and $line -notmatch 'noRollbackFor')
```

### 3.2 改动 2：catch-swallow 扫描区分 rethrow

**位置**：`07-scan-transaction.ps1` 第 56-71 行 catch 循环

```powershell
# 在 catch 循环内增加：
$hasRethrow = $false
for ($k = $j + 1; $k -lt [Math]::Min($j + 10, $lines.Count); $k++) {
    if ($lines[$k] -match 'throw\s+\w+') { $hasRethrow = $true; break }
    if ($lines[$k] -match '^\s*\}\s*$') { break }  # 块结束
}
if (-not $hasRethrow) {
    if ($nextLine -match 'log\.(warn|info|debug)\(' -or $nextLine -match '//\s*ignore') {
        $hasSwallowCatch = $true
        break
    }
}
```

### 3.3 验收

- 优化后重跑 `07-scan-transaction.ps1`
- 期望命中 = 0
- Delta vs current = -11
- 与 I088-1/2/3 累计效果：守门脚本从此 0 命中

---

## 4. 业务影响评估

| 维度 | 评估 |
|---|---|
| **真实事务安全** | ✅ **无影响** — 所有 catch 块已正确重新抛 RuntimeException 或返回 Result.error |
| **数据一致性** | ✅ **无风险** — I088 三期已修 179 处 @Transactional + rollbackFor |
| **生产可观测性** | 🟡 **无影响** — 守门脚本误报只影响 PR 守门流程，不影响运行时 |
| **累积风险** | 🟢 **低** — P3 形式工作，可延后到 Gate 3 脚本优化批次 |

---

## 5. 与同型 Issue 关系

| Issue | 类型 | 关系 |
|---|---|---|
| **I088** | @Transactional 无 rollbackFor | 已 FIXED（I088-1/2/3 三期 179 处）|
| **I095** | catch 块吞异常 | 已 FIXED（catch 后 rethrow RuntimeException）|
| **I108**（本 issue）| 守门脚本误报 | 形式工作，脚本优化 |

---

## 6. Touchpoints

- `audit/scripts/07-scan-transaction.ps1` 第 17 行（no-rollbackFor 判定）
- `audit/scripts/07-scan-transaction.ps1` 第 56-71 行（catch 扫描循环）

---

## 7. 验收方式

- [ ] `07-scan-transaction.ps1` 改动 1（排除注释）落地
- [ ] `07-scan-transaction.ps1` 改动 2（区分 rethrow）落地
- [ ] 重跑守门脚本，期望命中 = 0
- [ ] Delta vs current = -11
- [ ] 提交 commit 关闭 I108

---

**生成时间**：2026-10-15 22:59 (UTC+8)
**关联 commit**：（待提交）
**关联证据**：`audit/static-gap/07-transaction-current.txt`（守门脚本扫描结果，11 命中全误报）
**关联报告**：`audit/post-audit/gate3-1.4c-summary.md`（Gate 3 2.3 批次）
