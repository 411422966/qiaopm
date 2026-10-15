# I095 — @Transactional catch 块吞异常导致事务不回滚

> **Issue ID**：I095（I094 已被占用，从 I093 直接顺延）
> **发现时间**：2026-10-02 12:50（Phase 2.34 守门脚本重跑）
> **发现方式**：`audit/scripts/07-scan-transaction.ps1`（07-transaction.txt，2026-10-02 03:39:31 扫描）
> **严重度**：P2
> **Fix Phase**：Post-Audit 三期
> **类别**：契约漂移 / 事务边界 bug（与 I008 Fake Success / I011 Fake Failure / I031 Place Details 无降级 / I047 intl-ig webhook catch swallow 同类）

---

## 0. 摘要

7 处 `@Transactional(rollbackFor = Exception.class)` 标注的方法，**方法体内 catch 块静默吞掉异常**（`log.warn/info/debug(...)` 或 `// ignore`），导致：

- **异常未抛出** → Spring `@Transactional` 不会触发回滚
- **DB 写入已部分提交** → 数据不一致（"假成功"模式，与 I008 同型）
- **上游 / 调用方拿到 200** → 但实际数据可能部分写入或全部写入

这不是 I088（无 rollbackFor）问题——rollbackFor 已正确加 `Exception.class`，**根因在 catch 块**。

---

## 1. 命中清单（7 处，权威数据来自 `audit/static-gap/07-transaction.txt`）

| # | 文件 | 行号（@Transactional 注解行） | 方法签名（推断） | Catch 块位置（推断） |
|---|---|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java` | 174 | `generateForecast` | ~200-230 |
| 2 | `backend/src/main/java/com/meiyetec/service/EcommerceService.java` | 124 | 第一个 catch-swallow 方法 | ~130-150 |
| 3 | `backend/src/main/java/com/meiyetec/service/EcommerceService.java` | 233 | 第二个 catch-swallow 方法 | ~240-260 |
| 4 | `backend/src/main/java/com/meiyetec/service/IndustryService.java` | 85 | `applyTemplate` | ~95-115 |
| 5 | `backend/src/main/java/com/meiyetec/service/LbsDispatchService.java` | 102 | catch-swallow 方法 | ~110-130 |
| 6 | `backend/src/main/java/com/meiyetec/service/TraceService.java` | 52 | catch-swallow 方法 | ~60-80 |
| 7 | `backend/src/main/java/com/meiyetec/service/impl/ItemPreferenceServiceImpl.java` | 45 | `computeForMember` | ~75-95 |

> **行号说明**：以上 7 处行号为**用户口径权威**（2026-10-02 12:55 最终确认），守门脚本扫描报告的 `@Transactional` 注解所在行与之存在 ±3 行偏差（注解 vs catch 块 vs 方法签名位置差异）。

---

## 2. 根因分析

### 2.1 守门脚本判定逻辑

`audit/scripts/07-scan-transaction.ps1` 第 56-71 行：

```powershell
if ($line -match '@Transactional\b') {
    $hasSwallowCatch = $false
    for ($j = $i; $j -lt [Math]::Min($i + 40, $lines.Count); $j++) {
        # Check if method ends before catching
        if ($j -gt $i + 5 -and $lines[$j] -match '^\s*\}\s*$') { break }
        if ($lines[$j] -match 'catch\s*\(' -and ($j + 1) -lt $lines.Count) {
            $nextLine = $lines[$j + 1]
            if ($nextLine -match 'log\.(warn|info|debug)\(' -or $nextLine -match '//\s*ignore') {
                $hasSwallowCatch = $true
                break
            }
        }
    }
    if ($hasSwallowCatch) {
        $matched = $true
        $pattern = "Transactional-catch-swallow"
        $severity = "HIGH"
    }
}
```

**判定条件**：`@Transactional` 之后 40 行内出现 `catch (...)` + 紧接着的 `nextLine` 是 `log.warn/info/debug(...)` 或 `// ignore` 注释。

### 2.2 数据流

```
HTTP Request
  ↓
@Transactional method（标注 rollbackFor = Exception.class）
  ↓
try {
    业务操作
    mapper.insert/update/delete  ← 已写入 DB
    mapper.insert/update/delete  ← 已写入 DB
} catch (Exception e) {
    log.warn("xxx failed: " + e.getMessage());  // ⚠️ 吞掉！
    // 异常未抛出 → @Transactional 不知道要回滚
}
  ↓
return Result.success()  ← ⚠️ 上游拿到 200
```

### 2.3 与同型 Issue 的关系

| Issue | 模式 | 严重度 | 状态 |
|---|---|---|---|
| **I008** | Dashboard catch (Exception ignore) + Result.success | P1 CRITICAL | Phase 5A 5/35+ 异常被吞 |
| **I011** | update 抛 + GlobalExceptionHandler 500 | P1 CRITICAL | 5B 假失败 |
| **I031** | Place Details 无降级 → 直接报错 | P1 | FIXED 2026-09-25 |
| **I047** | intl-ig webhook catch swallow | P1 | OPEN |
| **I095**（本 issue） | 7 处 @Transactional catch log.warn/ignore | P2 | NEW |

I095 比 I008 范围更广（跨 7 个 service/impl 文件），严重度从 P1 降到 P2 的理由：
- I008 集中在 dashboard 单一接口 → 极易触发 + 影响所有 CRM 集成
- I095 分散在 7 个独立业务方法 → 每个触发概率较低 + 影响范围有限
- 但**任一处触发**就会导致数据不一致，**累积风险高**

---

## 3. 修复方案（三选一，按风险递增）

### 方案 A：catch 块重新抛出（推荐，P2 → P2）

```java
} catch (Exception e) {
    log.warn("xxx failed: " + e.getMessage());
    throw new BusinessException("XXX_FAILED", e.getMessage());  // ← 重新抛出
}
```

**优点**：保留日志线索 + 事务回滚生效
**缺点**：调用方需要 catch 兜底（但应该能 catch）

### 方案 B：使用 `@Transactional(noRollbackFor = ...)` 显式声明

不推荐——会让"故意不回滚"和"疏忽不回滚"混淆，**审计无法区分**。

### 方案 C：try-with-resources + catch 块强制失败

```java
try (var tx = transactionTemplate.execute(...)) {
    ...
    tx.commit();
} catch (Exception e) {
    log.warn("failed", e);
    throw e;  // 显式抛出
}
```

不推荐——侵入式重构，影响面大。

---

## 4. 关联

- **I088**（已 FIXED）：@Transactional 无 rollbackFor → 已修 179 处
- **I009**（已 FIXED）：CrmIntegrationService.syncNow @Transactional → 与 I095 同型但已修
- **I034**（已 FIXED）：YouzanWebhookController @Transactional → 与 I095 同型但已修
- **EXT-19 0.3**：CONFIRMED 自动触发 sendConfirmation 行为变更 — 涉及 `try/catch` 静默吞异常（BookingConfirmationService.sendConfirmation）

**I095 是 I088 修复后**剩余的"事务边界 bug"，与 I088 正交：
- I088 解决 `@Transactional` 标注的"边界规则"
- I095 解决 `@Transactional` 内部 catch 的"行为规则"

---

## 5. 守门脚本建议优化

`07-scan-transaction.ps1` 第 67 行：

```powershell
if ($nextLine -match 'log\.(warn|info|debug)\(' -or $nextLine -match '//\s*ignore') {
```

**已知不足**：
- 仅匹配 `log.warn/info/debug` 的 `nextLine`（catch 后第一行），不识别 `if (e != null) log.warn(...)` 嵌套
- 不识别 `e.printStackTrace()`（虽然不是 ignore，但也是 swallow）
- 不识别 `Optional.empty() / return null` 替代 throw

**建议扩展**（未来 P3 backlog）：
- 匹配 `\.printStackTrace()`
- 匹配 catch 块内 `Optional.empty()` / `return null` 模式
- 区分"故意吞"vs"疏忽吞"（人工 + grep 辅助）

---

## 6. Touchpoints（涉及文件清单）

- `backend/src/main/java/com/meiyetec/service/AiEnhanceService.java`（catch @ L174）
- `backend/src/main/java/com/meiyetec/service/EcommerceService.java`（catch @ L124, L233）
- `backend/src/main/java/com/meiyetec/service/IndustryService.java`（catch @ L85）
- `backend/src/main/java/com/meiyetec/service/LbsDispatchService.java`（catch @ L102）
- `backend/src/main/java/com/meiyetec/service/TraceService.java`（catch @ L52）
- `backend/src/main/java/com/meiyetec/service/impl/ItemPreferenceServiceImpl.java`（catch @ L45）

---

## 7. 修复优先级

| 项 | 值 |
|---|---|
| P2 严重度 | 中（每次触发概率低，累积风险高） |
| Post-Audit 三期 | 与 I089 / I093 同批处理 |
| 与 I008 同源 | 同一守门脚本（`07-scan-transaction.ps1`）未来扫描可见 |
| Phase 5 验证 | 待 Phase 5A/5B E2E |

---

## 8. 验收方式

- [ ] 7 处 catch 块逐一改成"重新抛出"（方案 A）
- [ ] `mvn -q -o compile -DskipTests` exit 0
- [ ] 重跑 `audit/scripts/07-scan-transaction.ps1`，期望 `Transactional-catch-swallow` 命中 = 0
- [ ] 守门脚本 Delta vs current = -7
- [ ] 单元测试：mock catch 块，验证异常能透传

---

**生成时间**：2026-10-02 12:50 (UTC+8)
**关联 commit**：`3286fc3`（I088-3 完成后留下的 7 处 catch-swallow 命中）
**关联证据**：`audit/static-gap/07-transaction.txt`（守门脚本扫描）
**关联报告**：`audit/post-audit/phase-2.34-I088-3-report.md` §8.4
