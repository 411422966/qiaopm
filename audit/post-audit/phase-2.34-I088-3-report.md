# Phase 2.34 — I088-3 报告（三期合并收尾）

> **时间**：2026-10-02 00:35 (UTC+8)
> **范围**：Phase 2 三期收尾 + I088 全后端 `@Transactional` rollbackFor 批量修复
> **关联 Issue**：I088（已 FIXED）
> **关联 Commit**：`3286fc3`（一期 `b3b284b` + 二期 `0e1c4f2` + 三期 `3286fc3`）
> **修复策略**：策略 1 一次性提交 + EOL 政策 C 精确 git add

---

## 1. 背景

Phase 2.17 静态扫描发现全后端 208 处 `@Transactional` 注解未指定 `rollbackFor`，默认仅回滚 RuntimeException，**checked exception（IOException / SQLException / JSONException）不回滚**。

按写密度分三期：
- **一期**（2026-10-01，commit `b3b284b`）：高写密度服务 45 处（Point / Coupon / Subscription / DCEP）
- **二期**（2026-10-01，commit `0e1c4f2`）：中写密度服务 53 处
- **三期**（2026-10-02，commit `3286fc3`）：剩余 71 处（本次执行）

---

## 2. 三期执行细节

### 2.1 扫描数据源

`audit/static-gap/i088-3-remaining.txt`：101 行（100 条记录 + 1 行 KioskService 已含 rollbackFor 的注释命中）

### 2.2 实际应用修复

| 指标 | 计划 | 实际 | Delta |
|---|---|---|---|
| 文件清单 | 50 + KioskService = 51 | 51（git add 全 51） | 0 |
| 实际修改文件 | — | 30 | — |
| 实际 `@Transactional` 加 rollbackFor | 100（用户预期） | **71**（扫描验证） | -29 |
| 注释行 / 已含 rollbackFor 命中 | — | 21 文件被识别为无需改 | — |

### 2.3 优先顺序（按处数递减）

| 排序 | 文件 | 处数 |
|---|---|---|
| 1 | `PriceListService.java` | 9 |
| 2 | `MemberDayService.java` | 6 |
| 3 | `DianpingSyncService.java` | 4 |
| 3 | `NoShowPredictionService.java` | 4 |
| 3 | `CareRecipeController.java` | 4 |
| 6 | `CareSuggestionService.java` | 3 |
| 6 | `GiftCardService.java` | 3 |
| 6 | `IoTDeviceService.java` | 3 |
| 6 | `LtvTrainingService.java` | 3 |
| 6 | `SharedPackageService.java` | 3 |
| 11 | `StyleGalleryController.java` | 2 |
| ... | （其余 19 文件，1-2 处/文件） | ... |

### 2.4 替换模式

- **裸 `@Transactional`** → `@Transactional(rollbackFor = Exception.class)`
- **带参 `@Transactional(propagation = Propagation.REQUIRED)`** → `@Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)`（保留参数）
- **注释行（`//` / `*` / `/*`）跳过**

### 2.5 不改其他行

每处替换只追加 `rollbackFor = Exception.class`，未触及任何业务逻辑、import、方法签名、注释。

---

## 3. 验证

### 3.1 重新扫描剩余 `@Transactional` 无 rollbackFor

```
=== Re-scan after fix ===
Remaining hits: 0
```

**期望 = 0 ✓**（KioskService.java 已在二期处理）

### 3.2 `mvn -q -o compile -DskipTests`

```
Exit code: 1（FAIL）
```

**失败原因**：4 个预先存在文件，与本次修复无关：
- `DcepSubWalletServiceImpl.java` — UTF-16 LE 编码（前期 turn 处理中遗留）
- `SubscriptionServiceImpl.java` L80/L257 — UTF-8 字节序列损坏（`"7天体�?`）
- `DcepMerchantQualificationServiceImpl.java` L68 — UTF-8 字节序列损坏
- `PointServiceImpl.java` L64/141/179/200-204 — UTF-8 字节序列损坏

**基线对比验证**：`git stash` 撤掉本次 71 处修改后，**同样 exit 1，相同 4 个文件失败** → 编译失败非本次修复引入。

### 3.3 EOL Policy C 精确 git add

```
$files = Get-Content audit/static-gap/i088-3-remaining.txt -Encoding UTF8 |
  ForEach-Object { ($_ -split ':')[0] } |
  Sort-Object -Unique
```

**注意**：用户给的命令有 bug — `($_ -split ':')[0]` 对 `D:\QiaoPM\backend\...:54` 取第一段会得 `D`（盘符）。改用 regex：

```powershell
$raw | ForEach-Object { if ($_ -match '^(.+):\d+$') { $Matches[1] } } | Sort-Object -Unique
```

精确 `git add` 51 文件 + KioskService，未使用 `git add .`

---

## 4. Commit

```
commit 3286fc3aa85bc6cb3daf98bbfe0b71d5d14e8a99
Author: ZhiLianOS Developer <developer@zhilianos.com>
Date:   Fri Oct 2 00:50:23 2026 +0800

    fix(I088-3): 71 处 @Transactional 加 rollbackFor（30 文件 + KioskService）
```

**改文件数**：30（实际有变化的）+ 21（无变化的清单中已含 rollbackFor 或为注释）= 51 git add，但 git 仅暂存有变化的文件（30）

**Commit 累计 I088 修复**：
- I009（早期）：9 处
- I034（EXT-22 webhook）：1 处
- I088-1（一期）：45 处
- I088-2（二期）：53 处
- I088-3（三期）：71 处
- **合计：179 处**

---

## 5. 已知遗留 / 待办

| # | 问题 | 严重度 | Fix Phase | 状态 |
|---|---|---|---|---|
| 1 | `mvn compile` 因 4 个预先存在文件 UTF 损坏失败 | **P1 BLOCKER** | Post-Audit 优先 | ✅ **已于 11aa1b1 修复 + I096 闭合（2026-10-08 23:55）** |
| 2 | `DcepSubWalletServiceImpl.java` 仍为 UTF-16 LE 编码 | P1 | Post-Audit | ✅ **已于 11aa1b1 修复（实测 UTF-8 无 BOM，中文字符已恢复）** |
| 3 | `SubscriptionServiceImpl.java` L80/L257 字节损坏 | P1 | Post-Audit | ✅ **已于 11aa1b1 修复（L80 实际为 `"7天体验"`）** |
| 4 | `PointServiceImpl.java` L64/141/179/200-204 字节损坏 | P1 | Post-Audit | ✅ **已于 11aa1b1 修复（L64 实际为 `"获得"` / `"消耗"`）** |
| 5 | `DcepMerchantQualificationServiceImpl.java` L68 字节损坏 | P1 | Post-Audit | ✅ **已于 11aa1b1 修复（L68 实际为 `"资质审核中，暂不可修改"`）** |

**注**：

- I088 修复本身已 100% 完成（扫描 0 处遗留）。上述遗留是**独立**的 UTF-8 字节序列损坏问题，需后续单独修复。
- **2026-10-08 23:55 修订**：以上 5 项**全部已于 commit `11aa1b1`（I088-1-hotfix，2026-10-03 23:47）修复**。`mvn -q -o compile -DskipTests` EXIT=0 实测通过（HEAD `e6bf350`），Grep mojibake 字符串 0 命中。详见 `audit/issues/I096-utf-source-file-corrupt.md`（I096 已闭合）。

---

## 6. 守门脚本（Step 5）

待执行：`07-scan-transaction.ps1`（详见 audit/static-gap/07-transaction-current.txt），期望 Delta = -71（baseline = 当前 commit 之前的扫描数；本次修复后总数应下降 71）。

---

## 7. 三期合并统计

| Phase | Commit | 处数 | 范围 |
|---|---|---|---|
| 一期 | `b3b284b` | 45 | 高写密度：Point / Coupon / Subscription / DCEP |
| 二期 | `0e1c4f2` | 53 | 中写密度 |
| 三期 | `3286fc3` | **71** | 剩余：BookingForecast / CareFormula / MemberDay / PriceList / ... |
| **合计** | — | **169** | — |

加上前期 I009（9）+ I034（1）= **全后端累计 179 处 `@Transactional` 加 rollbackFor = Exception.class**。

---

**Phase 2.34 完。Post-Audit 二期封存待用户确认。**

---

## 8. Step 5 重扫确认（2026-10-02 12:50，选项 A 执行）

### 8.1 触发原因

用户原始指令为「对 50 文件逐个 StrReplace」。执行 Step 1 前先做尽职调查，发现：
- `i088-3-remaining.txt` 101 条记录**全部已在 commit `3286fc3` 中处理**（71 处真实修复 + 29 处注释/已含 rollbackFor）
- 当前工作区**无任何真实 `@Transactional` 注解行缺 `rollbackFor`**（0 命中）
- 守门脚本 `audit/static-gap/07-transaction-current.txt` 报告 13 处命中：6 处为注释行（grep 误报），7 处为 `Transactional-catch-swallow`（独立 issue）

若机械执行 50 文件 StrReplace，会出现：
- 命中已含 `rollbackFor = Exception.class` 的行 → 替换出 `@Transactional(rollbackFor = Exception.class)(rollbackFor = Exception.class)` 语法错误
- 命中注释行 → 破坏中文 UTF-8 注释
- 这违反「不改其他业务代码」约束

**暂停 + 与用户对齐** → 用户选 A：跳过机械 StrReplace，按"重扫 + 文档 + 守门脚本"推进。

### 8.2 重扫证据

新文件：`audit/static-gap/i088-3-latest-scan.txt`（116 行）

| 指标 | 值 |
|---|---|
| 真实 `@Transactional` 注解行缺 rollbackFor | **0** |
| `i088-3-remaining.txt` 101 条记录状态 | **100/100 已修复** |
| `i088-3-residual.txt` 5 个 KioskService 残余位置 | **5/5 已修复** |
| 抽样 5 文件 / 7 行验证 | **7/7 当前含 `rollbackFor = Exception.class`** |

### 8.3 累计统计（与 issue-register.md I088 行对齐）

| 期次 | Commit | 处数 |
|---|---|---|
| I009（早期） | 多个 | 9 |
| I034（EXT-22 webhook） | `977f58e` | 1 |
| I088-1（一期） | `b3b284b` | 45 |
| I088-2（二期） | `0e1c4f2` | 53 |
| I088-3（三期） | `3286fc3` | **71** |
| **合计** | — | **179** |

> **注**：用户原文「累计 45+53+100=198」是基于 `i088-3-remaining.txt` 的过时估算（包含 29 处注释/已含 rollbackFor 命中）。**真实数字 = 179**，issue-register.md 已正确记录，无需修改。

### 8.4 守门脚本 Delta

| 时间点 | Total Hits | 来源 |
|---|---|---|
| baseline `dcaf07a` | **214** | `audit/static-gap/baseline-dcaf07a/07-transaction.txt` |
| current（本次重跑） | **13** | `audit/static-gap/07-transaction.txt` |
| **Delta** | **-201**（-94.0%） | — |

13 处当前命中分类：
- **6 处 `Transactional-no-rollbackFor`**：全部为 `//` / `*` / Javadoc 注释行（grep 误报，**无运行时风险**）
- **7 处 `Transactional-catch-swallow`**：`AiEnhanceService.java:174` / `EcommerceService.java:124,233` / `IndustryService.java:88` / `LbsDispatchService.java:102` / `TraceService.java:52` / `ItemPreferenceServiceImpl.java:45` — **独立 issue，建议立项 I094**

### 8.5 Phase 2.34 完结状态

| 项 | 状态 |
|---|---|
| I088 三期合并 | ✅ **100% 完成**（commit `3286fc3`） |
| 重扫验证 | ✅ 0 处真实遗留（`i088-3-latest-scan.txt`） |
| 守门脚本 Delta | ✅ -201（94% 命中下降） |
| 累计修复数 | ✅ 179 处（与 issue-register.md 对齐） |
| Post-Audit 二期封存 | 🕐 待用户确认 |

---

**Phase 2.34 完 + Step 5 重扫确认完成。Post-Audit 二期封存待用户确认。**

---

## 9. 守门脚本已知误报（2026-10-02 12:55 归档）

守门脚本 `audit/scripts/07-scan-transaction.ps1` 当前实现对所有 `@Transactional\b` 行做模式匹配（包括注释 / Javadoc），**6 处误报**已确认属于此情形，不构成运行时风险。

### 9.1 误报清单（6 处，全部为注释行）

| # | 文件 | 行号 | 命中模式 | 实际内容类型 |
|---|---|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/controller/PosController.java` | 474 | `Transactional-no-rollbackFor` | `// B-1: 业务异常...` 注释 |
| 2 | `backend/src/main/java/com/meiyetec/mapper/PosOrderMapper.java` | 16 | `Transactional-no-rollbackFor` | `* <p>...` Javadoc |
| 3 | `backend/src/main/java/com/meiyetec/service/PosRefundService.java` | 341 | `Transactional-no-rollbackFor` | `// ...` 注释 |
| 4 | `backend/src/main/java/com/meiyetec/service/PosRefundService.java` | 637 | `Transactional-no-rollbackFor` | `// ...` 注释 |
| 5 | `backend/src/main/java/com/meiyetec/service/impl/SubscriptionServiceImpl.java` | 29 | `Transactional-no-rollbackFor` | `* HTTP 层注解已迁移...` Javadoc |
| 6 | `backend/src/main/java/com/meiyetec/service/impl/WalletServiceImpl.java` | 77 | `Transactional-no-rollbackFor` | `* <p>...` Javadoc |

### 9.2 误报根因

`07-scan-transaction.ps1` 第 47-54 行：

```powershell
if ($line -match '@Transactional\b' -and $line -notmatch 'rollbackFor' -and $line -notmatch 'noRollbackFor') {
    # Skip class-level annotation with multi-line
    $matched = $true
    $pattern = "Transactional-no-rollbackFor"
    $severity = "MEDIUM"
}
```

**问题**：`@Transactional\b` 是字面量匹配，**无法区分注释 / 真实注解**。Java 注释行（`// ...@Transactional...` / `* ...@Transactional...`）也会命中。

### 9.3 误报影响

- **零运行时风险**：Spring AOP 不解析注释行，注释中的 `@Transactional` 不会被任何框架读取
- **数字噪音**：每次守门脚本扫描会拉高 6 处命中（占比 6/13 = 46%），**掩盖真实命中**
- **建议优化**（P3 backlog，未来 Post-Audit 优化项）：脚本可加 `^\s*(@|//|/\*|\*)` 排除注释前缀

### 9.4 归档决定

✅ **本次 Phase 2.34 关闭时记录为已知误报**，未来 6 处不再单独 issue 化。
🆕 **7 处 catch-swallow 真实命中**已登记为新 issue **I095**（见 issue-register.md + `audit/issues/I095-transactional-catch-swallow.md`）。

---

## 10. Phase 2.34 完结声明（2026-10-02 12:55）

### 10.1 最终状态

| 项 | 状态 |
|---|---|
| **I088 全后端 @Transactional rollbackFor** | ✅ **FIXED**（179 处累计） |
| **守门脚本 `07-transaction.txt` Total Hits** | 13（vs baseline 214，**Delta = -201**，**-94.0%**） |
| **真实 `@Transactional` 注解行缺 rollbackFor** | **0**（扫描确认） |
| **已知误报** | 6 处注释行（已记录于 §9.1） |
| **catch-swallow 真实命中** | 7 处（已登记为 **I095**，P2，Post-Audit 三期） |
| **issue-register.md I088 行** | 累计 179 处，与代码实际修复一致 |
| **i088-3-remaining.txt** | 过时清单（包含 29 处注释/已含 rollbackFor 命中），保留作审计证据 |
| **i088-3-latest-scan.txt** | 新证据（116 行），记录"0 处真实遗留" + 抽样验证 5 文件 / 7 行 |

### 10.2 三期合并统计

| 期次 | Commit | 处数 | 范围 | EOL 政策 C |
|---|---|---|---|---|
| I088-1（一期） | `b3b284b` | 45 | 高写密度：Point / Coupon / Subscription / DCEP | ✅ 精确 git add |
| I088-2（二期） | `0e1c4f2` | 53 | 中写密度 | ✅ 精确 git add |
| I088-3（三期） | `3286fc3` | **71** | 剩余：BookingForecast / CareFormula / MemberDay / PriceList / ... | ✅ 精确 git add |
| **合计** | — | **169** | — | — |

加上前期 I009（9）+ I034（1）= **全后端累计 179 处**（与 issue-register.md 对齐）。

### 10.3 docs commit 历史

| Commit | 内容 |
|---|---|
| `eda3786` | issue-register I088 → FIXED + phase-2.34 三期报告 |
| `19713af` | Step 5 重扫确认 + 守门 Delta=-201 |
| `（本次，待 commit）` | I095 登记 + 守门脚本已知误报归档 + Phase 2.34 完结声明 |

### 10.4 Post-Audit 二期封存检查表

- [x] I088 三期合并完成（commit `3286fc3` + `eda3786` + `19713af`）
- [x] 守门脚本 Delta vs baseline = -201（94% 命中下降）
- [x] 真实遗留 = 0（无未修复的 `@Transactional` 无 `rollbackFor`）
- [x] 已知误报已记录于本报告 §9（6 处注释行）
- [x] 新 issue I095 已登记（7 处 catch-swallow，P2，Post-Audit 三期）
- [x] issue-register.md I088 行累计 179 处与代码一致
- [ ] **Post-Audit 二期封存 — 待用户最终确认**

### 10.5 移交 Post-Audit 的待办清单

| Issue | 严重度 | 范围 | 建议批次 |
|---|---|---|---|
| **I093** | P3 | ecommerce 4 表 dev 测试残留清理（7 行） | Post-Audit 三期 |
| **I095**（NEW） | P2 | 7 处 @Transactional catch-swallow | Post-Audit 三期 |
| **I089** | PARTIAL-NON-BLOCKER | privateKey 编辑 UI 缺失 | Post-Audit |
| **I008** | P1 CRITICAL | Dashboard catch (Exception ignore) | Post-Audit (P1) |
| **I011** | P1 CRITICAL | CrmIntegrationService.update select-after-update | Post-Audit (P1) |
| **I012** | P1 | Mock @ConditionalOnMissingBean NPE | Post-Audit (P1) |
| **I047** | P1 CRITICAL | IntlBookingService.instagramWebhook catch | Post-Audit (P1) |

---

**Phase 2.34 完结 + I095 移交 + Post-Audit 二期封存待用户最终确认。**
