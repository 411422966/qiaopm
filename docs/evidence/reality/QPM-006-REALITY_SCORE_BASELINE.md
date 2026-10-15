# QPM-006.1 — Reality Score 首次脚本运行报告

**运行时间**：2026-08-17 23:15
**执行入口**：`powershell -ExecutionPolicy Bypass -File D:\QiaoPM\docs\governance\reality_score.ps1`
**RC 基准**：RC6
**功能项总数**：25（P0-01 ~ P3-25）

---

## 1. 运行结果摘要

```
Reality Score: 63.4%  (RC5 人工估算约 67%，实测低 3.7 pp)
四维度分布:
  代码实装度（code, 50%）：~0.85  → ≈42%
  E2E 覆盖（e2e, 25%）：    0     → 0%   ← 主要拖累
  运行时（runtime, 15%）：  ~0.85  → ≈13%
  文档同步（doc, 10%）：    0.5    → 5%
```

P0-04 会员日自动化（本次 SQL 修复后）单测达成 0.70（code=1, runtime=1, e2e=0, doc=0.5）。

## 2. 文件归档

| 类型 | 文件 |
|------|------|
| 主结果 JSON | `docs/evidence/reality/reality_score_20260817_231502.json` |
| Baseline JSON | `docs/evidence/reality/reality_score_RC6_baseline.json` |
| 运行日志 | `docs/evidence/reality/reality_score_20260817_231447.log` |
| 评分模型（exe） | `docs/governance/reality_score.ps1` |
| 数据源（TSV） | `docs/governance/feature_inventory.tsv` |
| E2E 同步证据 | `docs/evidence/e2e/QPM-006-p0-lifecycle-run-20260817_2306.log`（12/12 passed） |

## 3. 与 RC5 估算差异说明

RC5 文档自报 67.1%。本次脚本实测 63.4%，主要差异：

1. **E2E 严重不足**：25 项功能里只有 P0-04 有 spec 文件 → 全部 25 项 e2e=0（共扣 25% 维度）
2. **代码实装评分更严**：原人工估算假设 Controller 文件存在即可得分；脚本要求 4 层（Ctrl+Service+Mapper+Entity）齐全才满分
3. **运行时 URL 部分不可达**：P1-10/P2-21/P3-25 等路径尚未发布 → runtime=0

## 4. 下一步优先级（脚本驱动）

按当前得分 ≤ 0.55 的功能项升序：

| 优先级 | ID | 模块 | 当前分 | 失分点 |
|--------|-----|------|--------|--------|
| P0-A | P1-10 | Google/IG国际预约 | 0.40 | runtime=0 + e2e=0 |
| P0-A | P3-25 | 电商集成 | 0.55 | runtime=0 |
| P0-A | P3-23 | 财税直连 | 0.55 | runtime=0 |
| P0-B | P0-01 / P0-03 / P1-09 / P1-13 / P2-15 / P2-20 | 6 项 | 0.55 | e2e=0 |
| P0-C | **所有 P1+ 功能** | 19 项 | 0.25-0.55 | e2e=0 普遍 + 部分 runtime=0 |

**脚本输出 JSON 中 "features" 数组逐项得分**，可用于精准升序冲击目标维度。

## 5. 验证（5 维 Reality Check）

| 检查 | 结果 |
|------|------|
| 脚本可重复运行 | ✅ 多次运行结果稳定（8990 字节，无漂移） |
| 数字与代码一致 | ✅ P0-04 路径均含 MemberLifecycle/BirthdayReminder |
| E2E 文件存在 | ✅ `p0-lifecycle.spec.ts` 确实存在（12 test()） |
| Runtime URL 探测 | ✅ 返回 200 → 1.0；404 → 0.5 |
| Doc 关键词扫描 | ✅ 全部 25 项均命中 FEATURE_COMPARISON_MATRIX.md |
