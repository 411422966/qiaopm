# Post-Audit 三期 Backlog（Phase 2.34 之后）

> **整理时间**：2026-10-03 00:29 (UTC+8)
> **整理人**：Cursor（按用户指令）
> **依据**：`audit/post-audit/phase-2.34-I088-3-report.md`（§5 已知遗留 + §8.4 守门脚本重扫）+ `audit/issues/issue-register.md`（现状）+ `audit/issues/I095-transactional-catch-swallow.md`（独立文件）
> **范围**：所有未关闭、待 Post-Audit 三期处理（含本轮 P1 BLOCKER 4 个 UTF 损坏文件 + I089/I093/I095 + 审计期 P3 遗留 + 其他 backlog）
> **约束**：本文档**不改 `issue-register.md`**，不改业务代码；仅为三期待办清单

---

## 0. 摘要

| 类别 | 数量 | 严重度分布 | 备注 |
|---|---|---|---|
| **P0 / P1 BLOCKER** | 1 簇（4 文件） | P1 BLOCKER | mvn compile 失败，未立项 |
| **I089 / I093 / I095（本轮新增）** | 3 | P2 / P3 / P2 | I095 仅独立文件，register 未登记 |
| **审计期遗留 P3** | 4 | 全 P3 | I008 / I011 / I012 / I047 |
| **其他 backlog（dead code / 契约漂移 / SoT CONFLICT）** | 13+ | 多 P2-P3 | I004 / I010 / I013 / I019 / I021 / I025 / I032 / I033 / I035 / I036 / I043 / I050 / I051 / I052 |
| **合计** | **22+** | — | — |

---

## 1. 遗留 1：4 个 UTF 损坏文件（~~P1 BLOCKER / 待立项 I096~~ → ✅ I096 已闭合 2026-10-08 23:55）

> **🟢 2026-10-08 23:55 闭合状态**：本节 4 个 UTF 损坏文件**全部已于 commit `11aa1b1`（I088-1-hotfix，2026-10-03 23:47）修复**。立项文件：`audit/issues/I096-utf-source-file-corrupt.md`，登记到 `audit/issues/issue-register.md`（✅ FIXED & VERIFIED），修订 `audit/post-audit/phase-2.34-I088-3-report.md` §5 表格全部置灰打 ✅。
>
> **L5b 验证证据**（2026-10-08 23:55，HEAD `e6bf350`）：
> - `mvn -q -o compile -DskipTests` EXIT=0
> - Grep mojibake 字符串（鑾峰緱/娑堣€/璧勮川/宸插叧闂/澶╀綋楠/7澶╀綋楠）0 命中
> - 4 文件 L5b 字节流实测：L80=`"7天体验"`、L64=`"获得"`/`"消耗"`、L68=`"资质审核中，暂不可修改"`、L131=合法 UTF-8
>
> **修复 commit 不可拆分**：与 I097（合并 commit 设计缺陷）共用 `11aa1b1`（74+/74-），用户决策 2：encoding + business 一并推 main，不拆分。

### 1.1 完整路径 + 损坏位置

| # | 完整路径 | 损坏位置 | phase-2.34 §5 描述 | 实际字节流观察 |
|---|---|---|---|---|
| 1 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\impl\DcepSubWalletServiceImpl.java` | L131,60 / L195,21 / L211,62 / L238,60 | "UTF-16 LE 编码（前期 turn 处理中遗留）" | 实测 UTF-8 无 BOM，**中文字符串字面量双重编码破坏**（如"已关闭的子钱包不可修改" → `宸插叧闂殑瀛愰挶鍖呬笉鍙慨鏀?`）|
| 2 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\impl\SubscriptionServiceImpl.java` | L80,62 / L80,66 / L80,70 / L80,76 / ... / L257,18 | "UTF-8 字节序列损坏（`"7天体�?`）" | 实测 L80 含 `"7澶╀綋楠?` mojibake |
| 3 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\impl\DcepMerchantQualificationServiceImpl.java` | L68,45 | "UTF-8 字节序列损坏" | 实测 L68 含 `"璧勮川瀹℃牳涓紝鏆備笉鍙慨鏀?` mojibake |
| 4 | `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\impl\PointServiceImpl.java` | L64,59 / L141,78 / L179,61 / L200,31 / L202,30 / L204,35 | "UTF-8 字节序列损坏" | 实测 L64 含 `"鑾峰緱"` / `"娑堣€?` mojibake |

### 1.2 损坏类型

| 维度 | 实际观察 | 与 phase-2.34 §5 描述差异 |
|---|---|---|
| **BOM** | 4 个文件均**无 BOM**（不是 UTF-16 LE）| 报告原标"UTF-16 LE 编码"→ **修正**：4 文件均为 UTF-8 无 BOM |
| **EOL** | 4 个文件均**为 LF**（无 CR）| 报告未标 EOL 问题 |
| **字符编码** | 中文字符串字面量**双重编码破坏**（mojibake）| 报告原标"UTF-8 字节序列损坏"→ **修正**：4 文件均为合法 UTF-8，但中文字符被错误重新编码（典型"GBK 字节 → UTF-8 错误解码 → 重新保存为 UTF-8"二次污染）|
| **mvn 编译** | **`mvn -q -o compile -DskipTests` 失败，exit=1** | 报告与实际一致（P1 BLOCKER） |

**根因推断**（待立项后正式分析）：这 4 个文件曾在某次 PowerShell / 旧编辑器编辑过程中，中文字符被以 GBK → UTF-8 错误路径保存，每处中文字符变成 6-9 字节无意义字节序列，导致 Java 编译器在字符串字面量边界上找不到合法 UTF-8 字符。

### 1.3 是否已登记

| 位置 | 状态（2026-10-08 23:55） |
|---|---|
| `audit/issues/issue-register.md` | **✅ 已登记 I096**（✅ FIXED & VERIFIED，Gate 3 批次 1.1）|
| `audit/issues/I096-utf-source-file-corrupt.md` 独立文件 | **✅ 已建立**（含 §0-§9 完整证据链，L5b 验证）|
| `audit/post-audit/phase-2.34-I088-3-report.md` §5 | **✅ 已修订**（5 项全部置灰打 ✅，加 2026-10-08 23:55 修订脚注）|
| `audit/post-audit/phase-2.34-I088-3-report.md` §5 严重度 | ~~P1 BLOCKER~~ → **✅ 已修复（commit `11aa1b1`）**|

### 1.4 是否需要新 Issue I096？

**结论**：~~**强烈建议立项 I096**~~ → **✅ 已立项（2026-10-08 23:55）**

| 维度 | 评估 |
|---|---|
| **严重度** | ~~**P1 BLOCKER**~~ → **✅ 已修复（commit `11aa1b1`，L5b 验证通过）**：`mvn compile` 失败已解除，后端构建恢复 |
| **可观测性** | 高（mvn 编译 30+ 错误信息明确，修复后 Grep mojibake 0 命中）|
| **修复成本** | 中：4 文件 × 平均 2-3 处损坏 × 字符串字面量重写 = 30-60 分钟（已在 `11aa1b1` 中执行，74+/74-）|
| **业务影响** | ~~**P0**~~ → **✅ 阻塞已解除**（Post-Audit 三期工作可继续推进）|
| **是否独立** | 是（与 I088 三期 179 处修复**完全独立**，phase-2.34 §5 注："I088 修复本身已 100% 完成，上述遗留是**独立**的 UTF-8 字节序列损坏问题"）|
| **修复形式** | **不可单独 revert**（与 I097 共用 `11aa1b1`；encoding + business 一并 push main，**用户决策 2 不拆分**）|

**执行状态**：
- ✅ **已立项 `audit/issues/I096-utf-source-file-corrupt.md`**（含 §0-§9 完整证据链，L5b 验证）
- ✅ **已登记 `audit/issues/issue-register.md`**（I096 ✅ FIXED & VERIFIED）
- ✅ **已修订 `audit/post-audit/phase-2.34-I088-3-report.md` §5**（5 项置灰打 ✅）
- ✅ **已修订本节 §1.3 / §1.4**（2026-10-08 23:55 形式工作）

### 1.5 临时绕过（如不能立即修）

如需立即进入 Post-Audit 三期其他工作，可：
1. **maven-compiler-plugin 加 `-encoding UTF-8`**：无效（错误字节本身就是非法 UTF-8，编译器无法猜测原字符）
2. **删除损坏字符串**（不推荐，破坏业务语义）
3. **回滚到历史 commit 寻找未损坏版本**（需 git log 排查，phase-2.34 §5 提到"前期 turn 处理中遗留"，说明在某次 commit 之后损坏）

---

## 2. 遗留 2：Post-Audit 三期范围（完整清单）

> ✅ **更新于 2026-10-04**：I088 100% FIXED 已确认（650/650 真注解带 rollbackFor）。
> 详见 §5.5「I088 100% 核实记录」与 §5.3「守门脚本状态」。

### 2.1 P0 / P1 BLOCKER（必先修）

| 编号 | 标题 | 严重度 | Fix Phase | 登记位置 | 文件 / 行号 |
|---|---|---|---|---|---|
| **I096**（待立项）| 4 个 UTF 损坏源文件 mvn compile 失败 | **P1 BLOCKER** | Post-Audit 三期（**前置**）| ❌ 未登记 | 详见 §1.1 |
| **I088** ✅ FIXED | 全后端 @Transactional 未指定 rollbackFor | ~~P2~~ → **FIXED** | — | ✅ issue-register.md | 179 处累计（I009 9 + I034 1 + I088-1 45 + I088-2 53 + I088-3 71）|

**I088 100% FIXED 备注**（2026-10-04 核实）：
- 全后端 650 个真 `@Transactional` 注解（行首）全部带 `rollbackFor = Exception.class`
- 全局 `@Transactional\b` 总出现次数 657 = 650 真注解 + 7 注释提及（误报源，已在 §5.5 详细分析）
- i088-3-remaining.txt 101 个历史位置已全部修复（94 处真修复 + 7 处注释误报）
- 守门脚本 baseline 214 → current 13（Delta = -201，-94.0%）
- **核实方法**：见 phase-2.34-I088-3-report.md §8.2 重扫证据 + 本文档 §5.5 4 项核实
- **原 backlog 描述"剩余 100 处 @Transactional 无 rollbackFor"已修正**：I088 实为 100% FIXED

### 2.2 本轮新增 / 累积（I089 / I093 / I095）

| 编号 | 标题 | 严重度 | Fix Phase | 登记位置 | 独立文件 | 备注 |
|---|---|---|---|---|---|---|
| **I089** | privateKey 编辑缺失（CRM 集成） | P2 | Post-Audit | ✅ issue-register.md L42 | ❌ 无独立文件 | 3 方案：① 重新上传覆盖 ② 拆 read 端点 ③ 改 READ_WRITE + 前端掩码 |
| **I093** | ecommerce 4 表 dev 测试残留数据清理 | P3 | Post-Audit 三期 | ✅ issue-register.md L44 | ✅ `audit/issues/I093-cleanup-ecommerce-test-data.md` | 7 行测试残留（3+3+0+1=7），全 `tenant_id=0` |
| **I095** | @Transactional catch 块吞异常导致事务不回滚 | P2 | Post-Audit 三期 | ⚠️ **register 未登记** | ✅ `audit/issues/I095-transactional-catch-swallow.md` | 7 处：AiEnhanceService:174 / EcommerceService:124,233 / IndustryService:85 / LbsDispatchService:102 / TraceService:52 / ItemPreferenceServiceImpl:45 |

### 2.3 审计期遗留 P3（I008 / I011 / I012 / I047）

| 编号 | 标题 | 严重度 | Fix Phase | 登记位置 | 备注 |
|---|---|---|---|---|---|
| **I008** | dashboard catch-swallow Fake Success（CRM 集成） | P1 | Post-Audit (P1) | ✅ issue-register.md L18 | 75 处出现，F002/F005/F007/F008/F009/F010/F012 + F020 受影响 |
| **I011** | Fake Failure（API 成功但 DB 失败）| P1 | Post-Audit (P1) | ✅ issue-register.md L48 | F003 update + I009 rollbackFor 沿用 |
| **I012** | CRM Mock Bean 缺失（`@ConditionalOnMissingBean` 顺序）| P1 | Post-Audit (P1) | ✅ issue-register.md L49 | Mock @ConditionalOnProperty + Router fallback |
| **I047** | intl-ig webhook catch swallow | P2 | Post-Audit | ✅ issue-register.md L1632 | EXT-19 webhook 演示模式 catch 吞异常 |

### 2.4 其他 backlog（dead code / 契约漂移 / SoT CONFLICT / 重构）

| 编号 | 标题 | 类别 | 严重度 | Fix Phase | 登记位置 |
|---|---|---|---|---|---|
| **I088** ✅ FIXED | 全后端 @Transactional 未指定 rollbackFor | 事务边界 | ~~P2~~ → **FIXED** | — | ✅ issue-register.md（179 处累计）|
| **I004** | CrmIntegrationService 拆 Controller（`@RestController` 在 service）| 重构 backlog | P3 | Post-Audit | ✅ issue-register.md L35 |
| **I010** | Flyway V1__init_baseline.sql 编码不匹配 | Flyway 编码 | P1（启动阻塞）| Post-Audit | ✅ issue-register.md L47 |
| **I013** | CRM syncSchedule dead code | dead code | P3 | Post-Audit | ✅ issue-register.md L50 |
| **I019** | PluginService 拆 Controller（与 I004 同模式）| 重构 backlog | P3 | Post-Audit | ✅ issue-register.md L742 |
| **I021** | PluginRuntimeInfo.autoLoaded dead code | dead code | P3 | Post-Audit | ✅ issue-register.md L744 |
| **I025** | PluginSecurityPolicy.deniedPackages() dead code | dead code | P3 | Post-Audit | ✅ issue-register.md L733 |
| **I032** | batch geocode 无 audit log | 缺失 | P3 | Post-Audit | ✅ issue-register.md L1100 |
| **I033** | buildWebhookUrl 孤儿工具 | dead code | P3 | Post-Audit（Phase 7）| ✅ issue-register.md L1101 |
| **I035** | webhook 24h 时间窗缺失 | 数据安全 | P2 | Post-Audit | ✅ issue-register.md L1098 |
| **I036** | apiKeyLength 缺失（`getConfig` 遮蔽） | 契约漂移 | P3 | Post-Audit | ✅ issue-register.md L44 |
| **I043** | confirmation-template 硬编码 switcher vs Resolver 期望 | SoT CONFLICT（中立标）| P3 | Post-Audit | ✅ issue-register.md L1791 |
| **I050** | ChannelDispatcher.healthCheckAll 硬编码 tenant_id=1L | 多租户 | P3（T07-D4 例外声明）| Post-Audit | ✅ issue-register.md L1633 |
| **I051** | webhook 演示模式 tenantId=1L 硬编码 | 演示模式 | P3 | Post-Audit | ✅ issue-register.md L1632 |
| **I052** | mvn test 编译失败 | 测试基线 | P2 | Post-Audit | ✅ issue-register.md L1632 |

### 2.5 阶段统计

| 类别 | 总数 | 已登记 register | 独立文件 | 完全空白 |
|---|---|---|---|---|
| P0 / P1 BLOCKER | 1 簇（4 文件）| 0 | 0 | **1 簇**（I096 待立项）|
| I089 / I093 / I095（本轮）| 3 | 2（I089 / I093）| 2（I093 / I095）| 0（**I095 缺 register 登记**）|
| 审计期 P3（I008/I011/I012/I047）| 4 | 4 | 0 | 0 |
| 其他 backlog | 14 | 14 | 0 | 0 |
| **合计** | **22 + 1 簇** | **20 + 0 簇** | **2** | **1 簇（I096）+ 1 项（I095 register）** |

---

## 3. 三期执行建议顺序（**仅建议**，最终以用户决策为准）

### 3.1 阶段 A：必修前置（首日必做）

| 序 | 任务 | 依赖 | 估时 |
|---|---|---|---|
| A1 | 立项 **I096**：新建 `audit/issues/I096-utf-source-file-corrupt.md` + 登记 issue-register.md | 无 | 30 分钟 |
| A2 | **修复 4 个 UTF 损坏文件**：用编辑器「另存为 UTF-8 无 BOM + LF」+ 重写中文字符串字面量 | A1 | 60 分钟 |
| A3 | 验证 `mvn -q -o compile -DskipTests` → **EXIT=0** | A2 | 5 分钟 |
| A4 | 同步更新 `audit/post-audit/phase-2.34-I088-3-report.md` §5 表格：删除 4 个遗留行（已修）| A3 | 5 分钟 |

**A 阶段总估时**：~1.5-2 小时

### 3.2 阶段 B：P1 必修（首周末）

| 序 | 任务 | 估时 |
|---|---|---|
| B1 | I095（7 处 catch-swallow）：方案 A — catch 块重新抛 `throw new BusinessException(...)` | 4-6 小时 |
| B2 | I008（dashboard catch swallow）| 4-6 小时 |
| B3 | I011（Fake Failure）| 4-6 小时 |
| B4 | I012（CRM Mock Bean 缺失）| 2-3 小时 |
| B5 | I010（Flyway V1 encoding）| 1-2 小时 |

**B 阶段总估时**：~1-1.5 周

### 3.3 阶段 C：P2 优化（次周）

| 序 | 任务 | 估时 |
|---|---|---|
| C1 | I089（privateKey 编辑缺失）| 1-2 天 |
| C2 | I047（intl-ig webhook catch swallow）| 0.5-1 天 |
| C3 | I035（webhook 24h 时间窗）| 0.5-1 天 |
| C4 | I052（mvn test 编译失败）| 1-2 天 |

### 3.4 阶段 D：P3 清理（三期后段）

| 序 | 任务 | 估时 |
|---|---|---|
| D1 | I093（ecommerce 4 表 dev 测试残留清理）| 0.5 天 |
| D2 | dead code 清理（I013 / I021 / I025 / I033）| 1-2 天 |
| D3 | I050 / I051 硬编码 tenant_id=1L 改造 | 1-2 天 |
| D4 | I004 / I019 Controller 拆分重构 | 3-5 天 |
| D5 | I043 confirmation-template 硬编码 vs Resolver（中立标，需人类决策）| 1-2 天 |
| D6 | I032 batch geocode audit log | 0.5-1 天 |
| D7 | I036 apiKeyLength 缺失 | 0.5 天 |

**D 阶段总估时**：~2 周

### 3.5 阶段 E：封存

| 序 | 任务 | 估时 |
|---|---|---|
| E1 | 守门脚本重跑（基线对比） | 0.5 天 |
| E2 | `audit/post-audit/final-post-audit-report.md` 更新到三期收口 | 1 天 |
| E3 | `audit/post-audit-summary*.md` 三期版 | 0.5 天 |
| E4 | `git tag` 重打 v1.0-audit-archive 三期 | 0.1 天 |

---

## 4. 待用户决策

1. **是否立项 I096？**（强烈建议）
2. **是否在 issue-register.md 登记 I095？**（独立文件已存在，但 register 未登记）
3. **A 阶段是否立即执行**（修复 4 个 UTF 损坏文件）？
4. **是否接受 §3 执行顺序**（A → B → C → D → E）？
5. **本轮完成（指本 backlog 文档）后，是否进入 A 阶段？**

---

## 5. 附录

### 5.1 mvn compile 失败实证（2026-10-03 00:29 重跑）

```
[ERROR] PointServiceImpl.java:[64,59] [141,78] [179,61] [200,31] [202,30] [204,35] - 未结束的文字字符串
[ERROR] DcepSubWalletServiceImpl.java:[131,60] [195,21] [211,62] [238,60] - 未结束的文字字符串
[ERROR] SubscriptionServiceImpl.java:[80,62-136] - 大量 ')'; ';' 期待 / 非法字符
[ERROR] DcepMerchantQualificationServiceImpl.java:[68,45] - 未结束的文字字符串
[ERROR] Failed to execute goal maven-compiler-plugin:3.11.0:compile
[ERROR] Total: 30+ 编译错误，exit code 1
```

### 5.2 4 个文件实际字节损坏样本

| 文件 | 行号 | 实际字节序列 | 应为（推断）|
|---|---|---|---|
| SubscriptionServiceImpl.java | L80 | `"7澶╀綋楠?` | `"7天体验?"` |
| DcepMerchantQualificationServiceImpl.java | L68 | `"璧勮川瀹℃牳涓紝鏆備笭鍙慨鏀?` | `"资质审核中，暂不可修改"` |
| PointServiceImpl.java | L64 | `"鑾峰緱"` / `"娑堣€?` | `"获得"` / `"消费"` |
| DcepSubWalletServiceImpl.java | L131 | `"宸插叧闂殑瀛愰挶鍖呬笉鍙慨鏀?` | `"已关闭的子钱包不可修改"` |

### 5.3 守门脚本当前状态（Phase 2.34 重扫）

| 脚本 | baseline (dcaf07a) | current (2026-10-02 12:50) | Delta |
|---|---|---|---|
| 07-transaction | 214 | 13 | -201（-94.0%）|

13 处当前命中：
- 6 处 `Transactional-no-rollbackFor` → 注释行误报（无运行时风险；详见 phase-2.34-I088-3-report.md §9.1 误报清单）
- 7 处 `Transactional-catch-swallow` → 已登记为 **I095**（独立文件已建）

> ✅ **2026-10-04 核实更新**：I088 100% FIXED 已确认。
> - 全局 650 个真 `@Transactional` 注解（行首）全部带 `rollbackFor = Exception.class`
> - 全局 `@Transactional\b` 总出现次数 657 = 650 真注解 + 7 注释提及（误报源）
> - i088-3-remaining.txt 101 个历史位置：94 已修 + 7 注释误报 + 0 遗漏
> - 11aa1b1 合并 commit 风险已记录（详见 I097 + phase-2.21-postmortem.md §7）

### 5.4 issue-register.md 登记状态

- 总 Issue 数：88（含 I001-I093，I094 报告建议未采纳，**I095 仅独立文件未登记**）
- **P0 / P1 BLOCKER 缺登记：1 簇（I096 待立项）**
- **P2 缺登记：1 项（I095 register）**

### 5.5 I088 100% 核实记录（2026-10-04）

> **核实触发**：Post-Audit 二期封存前 4 项只读核实（用户指令）
> **核实方法**：phase-2.34-I088-3-report.md §8.2 重扫证据 + 4 项独立 regex 验证
> **结论**：I088 100% FIXED，**无真实遗留**

#### 5.5.1 总数核实

| 维度 | 数值 | 来源 |
|---|---|---|
| 全局 `@Transactional\b` 总出现次数 | **657** | regex 全文匹配 |
| ├─ 真注解（行首）| **650** | 精细行首匹配 |
| │　├─ 带 `rollbackFor = Exception` | **650 (100%)** | 注解块级扫描 |
| │　└─ 不带 rollbackFor | **0** | 同上 |
| └─ 注释中提及 | **7** | javadoc/`//` 注释（误报源）|

**649 vs 657 差异解释**：
- 649 是按 `@Transactional\(rollbackFor` 字符串子串匹配（仅匹配单行带 `(rollbackFor`）
- 650 是按真注解 `@Transactional\(` 起始匹配（覆盖多行注解）
- 差 1 = 一个真注解的 `(` 在下一行（多行格式），两者语义等价
- phase-2.34 报的 657 包含 7 个注释提及，**实际只有 650 个真注解**

#### 5.5.2 i088-3-remaining.txt 101 行历史快照核实

| 状态 | 数量 | 说明 |
|---|---|---|
| 当前位置已带 rollbackFor | **94** | I088-3 批次已修 |
| 命中点为注释提及（非真注解）| **7** | 历史扫描误报 |
| 仍是裸 @Transactional | **0** | **全部已修** |
| 文件/行号无效 | **0** | — |

5 个候选文件抽查（`PriceListService` / `MemberDayService` / `KioskService` / `DianpingSyncService` / `NoShowPredictionService`）全部 100% 带 rollbackFor（9/6/5/4/4）。

#### 5.5.3 3 种正则对比

| 正则 | 命中 | 备注 |
|---|---|---|
| A. `^\s*@Transactional\s*$`（行尾单行无参）| 0 | 只匹配单行无 `@` 注解 |
| B. `@Transactional\b`（任意位置）| 657 | 包括真注解 + 注释提及 |
| C. `@Transactional\b(?![^{}]*rollbackFor)` | 6 | **全部是注释误报** |
| D. `@Transactional\(` | 650 | 真注解带括号 |

**之前用 A 报 0 裸 = "100% 完成"**：结论对但**方法错**。A 只覆盖单行无参形式；B/C/D 联合验证才能 100% 准确。

#### 5.5.4 11aa1b1 合并 commit 风险

| 文件 | diff 行 | 内容 |
|---|---|---|
| `DcepMerchantQualificationServiceImpl.java` | 8 行 | 主要是 mojibake 修复 + 1 处替换 |
| `DcepSubWalletServiceImpl.java` | 68 行 | 主要是 mojibake 修复 + 7 处替换 |
| `PointServiceImpl.java` | 30 行 | 主要是 mojibake 修复 + 5 处替换 |
| `SubscriptionServiceImpl.java` | 42 行 | 主要是 mojibake 修复 + 8 处替换 |
| **合计** | **74+ / 74-** | mojibake 修复**淹没了** 21 处 rollbackFor 替换的 diff 行 |

**commit 设计缺陷**：`11aa1b1` 把 mojibake 修复 + 21 处 rollbackFor 替换合并成一次 commit（因 amend 机制）。回退时无法单独 revert 21 处替换 — 必须 revert 整个 11aa1b1。

**用户决策 2（不拆分）**：
- 11aa1b1 已 push main，**拆分风险大**
- 当前代码状态 100% 正确（650/650 + 4 文件中文已恢复）
- **未来规范**（I097）：批量修改必须分两个 commit（encoding + business）

#### 5.5.5 核实结论

| 结论 | 状态 |
|---|---|
| I088 全后端 `@Transactional` 无 rollbackFor 修复 | ✅ **100% FIXED**（650/650）|
| 守门脚本 baseline 214 → current 13 | ✅ Delta = -201（-94.0%）|
| i088-3-remaining.txt 101 个历史位置 | ✅ 94 已修 + 7 注释 + 0 遗漏 |
| 11aa1b1 commit 设计缺陷 | ⚠️ 已记录（I097 + phase-2.21-postmortem §7）|

---

**整理完成。等用户决策：① 是否立项 I096 ② 是否登记 I095 ③ 是否进入 A 阶段。**
