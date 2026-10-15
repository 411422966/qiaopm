# audit/ — 项目审计工作目录

> 来源：《项目开发完成度与最终验收体系（最终融合版）》v1 + 《任务执行清单（修正版）》v3 §A
> 创建时间：2026-09-12（v3 A1 阶段）
> 维护：每批次 Phase 0 → Phase 6 写入对应子目录

---

## 1. 子目录职责

| 子目录 | 职责 | 主要产出物 |
|---|---|---|
| `baseline/` | 基线快照（不可变，启动时一次性写） | `preflight.md` / `commit.txt` / `env.md` / `db-snapshot-*.sql` / `scope-freeze.md` |
| `evidence/` | 证据沉淀（每批次追加） | `traceability-matrix.md` / E2E 截图 / SQL explain / 日志切片 |
| `scripts/` | 守门脚本（不可变，CI 入口） | 8 个守门脚本（`g1-scope.sh` ~ `g8-pre-deploy.sh`） |
| `static-gap/` | Phase 1 静态差距扫描结果 | `g0-static-gap.md` / diff 报告 |
| `runtime/` | Phase 4 运行时计划 + Phase 5 验证 | `plan.md` / `runtime-trace.jsonl` |
| `db/` | Phase 2 DB Schema Truth | `schema-truth.md` / `migration-diff.sql` |
| `business/` | Phase 3 业务测试矩阵 + Phase 6 业务 E2E | `business-test-matrix.md` / `business-e2e-report.md` |
| `non-functional/` | Phase 7 非功能（NFR）报告 | `nfr-report.md` / 压测数据 / 安全扫描结果 |
| `reports/` | Phase 8 收尾报告 | `final-acceptance-report.md` / `issue-register.md` |

---

## 2. ⚠️ 历史审计沉淀位置

**历史审计报告**（BEAUTY_SAAS 系列、P0/P1/P2 修复总结、P0-FREEZE、T13/T14 决策矩阵等）**不在本目录**。

它们位于：

```text
d:\QiaoPM\docs\audit\
├── BEAUTY_SAAS/          ← 28+ 个历史矩阵报告（00~22 系列）
├── *.sql                 ← 历史迁移 SQL（gap_p0_p3.sql 等）
├── *.txt                 ← 历史 raw 扫描（audit_*_raw.txt）
├── *.md                  ← P0/P1/P2 修复总结、PHASE_6_CLOSEOUT 等
└── ...
```

### 为什么不在本目录

- 本目录是 **v3 验收体系**（2026-09+）的产物，目录结构与 Phase 0~8 一一对应
- `docs/audit/` 是 **v1/v2 历史审计**（2025~2026-08）的沉淀，沿用历史命名（无统一子目录划分）
- v3 启动时**不迁移**历史文件（按 v3 任务清单 Q2 决定），保留原位以维持可追溯性

### 引用约定

- v3 报告**需要引用历史审计时**：使用相对路径 `../docs/audit/BEAUTY_SAAS/19_REPAIR_ROADMAP_V3.md`
- v3 报告**不复制历史内容**：以引用为主，避免双份维护导致漂移
- 新审计内容（v3 之后）**只写到本目录**

---

## 3. 引用入口

- **上游**：`.cursor/rules/project-audit.mdc`（v3 A3 待补）
- **平行**：`docs/README.md`（EXT-* SoT 指引）
- **下游**：每个批次在 `baseline/` 写 `preflight-batch-N.md` 后，本目录开始有内容

---

**本目录骨架为 v3 第一批（A1）落地，2026-09-12。**
