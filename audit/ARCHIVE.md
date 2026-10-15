# Post-Audit 一期归档说明

## 归档时间
2026-09-24

## 归档 Tag
v1.0-audit-archive（含清理 commit）

## 一期成果
- 140 Feature 审计完成
- 21 FIXED & VERIFIED
- 5 RESOLVED（SoT）
- 0 CRITICAL
- Gate 2: READY
- Gate 3: NOT READY

## 剩余 11 项 OPEN（Post-Audit 二期）
### P1（4 项）
- I020: 沙箱 assertCallerAllowed 弱
- I022: disablePlugin 清 lastError
- I031: Place Details 无降级
- I034: webhook rollbackFor 缺

### P2（4 项）
- I084/I085/I086 + 1（参考 issue-register.md）

### Backlog（3 项）
- I013 / I021 / I025

## Gate 3 待补项
- 环境配置（dev/staging/prod）
- 监控（Micrometer/Prometheus）
- 种子数据（菜单/角色/字典启动初始化器）
- 软删除与唯一索引（63 张表）
- 多租户隔离（body.tenantId 信任前端）
- 可观测性 traceId 全覆盖

## 恢复指南
1. 读 audit/post-audit/final-post-audit-report.md 了解全局
2. 读 audit/issues/issue-register.md 查剩余 Issue
3. 从 P1 开始启动 Post-Audit 二期
4. 二期完成后冲 Gate 3

## 关键文档索引
- 全收口报告: audit/post-audit/final-post-audit-report.md
- Issue 全清单: audit/issues/issue-register.md
- 最终验收: audit/reports/final-acceptance.md
- 事故复盘: audit/post-audit/phase-2.3-postmortem.md
- 审计体系: .cursor/rules/project-audit.mdc

---

## Post-Audit 二期归档

**归档时间**：2026-10-04
**Tag**：1.0-post-audit-2
**Commit**：837ce98（I088 100% 核实 + I097 登记）

### 二期成果

| 维度 | 状态 |
|---|---|
| P1 全清 | ✅ I035/I036/I005-C1/C2/C4/I003/I016/I028/I090/I091(CLOSED)/I092 |
| P2 全清 | ✅ I088（179 处，最终核实 650/650 = 100%）|
| 新增 Issue | 5 个（I089/I093/I095/I096/I097）|
| Post-Audit 三期范围 | I089/I093/I095/I097（P3）+ 历史遗留 I008/I011/I012/I047 |
| 守门脚本 Delta | 07-transaction: -201（-94.0%，baseline 214 → current 13）|

### 新增 Issue 清单

| ID | 严重度 | 标题 | Fix Phase |
|---|---|---|---|
| I089 | P3 | privateKey 编辑缺失（CRM 集成）| Post-Audit |
| I093 | P3 | ecommerce 4 表 dev 测试残留数据清理 | Post-Audit 三期 |
| I095 | P2 | @Transactional catch 块吞异常（7 处）| Post-Audit 三期 |
| I096 | P1 BLOCKER | 4 个 UTF mojibake 文件（已在 11aa1b1 修复）| — |
| I097 | P3 | commit 设计规范（11aa1b1 合并 commit 不可分离回退）| 未来规范 |

### I088 100% FIXED 核实记录

- 全局真 @Transactional 注解：650 个，**100% 带 rollbackFor = Exception.class**
- i088-3-remaining.txt 101 个历史位置：94 已修 + 7 注释误报 + 0 遗漏
- 累计修复：I009(9) + I034(1) + I088-1(45) + I088-2(53) + I088-3(71) = **179 处**
- 11aa1b1 合并 commit 风险：已登记 I097 + 记录于 phase-2.21-postmortem §7

### 三期待清

| 类别 | Issue | 严重度 | 备注 |
|---|---|---|---|
| P2 | I095 | P2 | 7 处 catch-swallow，独立文件已建 |
| P3 | I089/I093/I097 | P3 | privateKey / 测试残留 / commit 规范 |
| P1 历史遗留 | I008/I011/I012/I047 | P1 | dashboard catch / Fake Failure / Mock Bean / intl-ig webhook |
| 编码阻塞 | I096（已修 11aa1b1）| ~~P1 BLOCKER~~ | 4 UTF 文件已恢复，mvn compile 可通过 |

### 未清残留（不影响封存）

- 65 个 working tree EOL 漂移（frontend 文件，CRLF vs LF）
- 3 个 stash：@{0}=WIP、@{1}=i088-3-residual、@{2}=WIP on 11e50b6

### 恢复指南（三期）

1. 读 udit/post-audit/post-audit-phase3-backlog.md 了解三期范围
2. 读 udit/issues/issue-register.md 查剩余 Issue（含 I089/I093/I095/I097）
3. 优先处理 I096（4 UTF 文件已在 11aa1b1 修复，需验证 mvn compile）
4. 建议顺序：A 阶段（mvn compile 验证）→ B 阶段（P1 遗留）→ C 阶段（P2/P3）→ D/E 封存

### 关键文档索引

| 文档 | 路径 |
|---|---|
| 三期 backlog | udit/post-audit/post-audit-phase3-backlog.md |
| I088 核实报告 | udit/post-audit/phase-2.34-I088-3-report.md |
| I097 流程规范 | udit/issues/I097-commit-design-fix.md |
| postmortem（11aa1b1）| udit/post-audit/phase-2.21-postmortem.md |
| EOL 政策 | udit/post-audit/phase-2.24-eol-policy.md |
| issue-register | udit/issues/issue-register.md |
