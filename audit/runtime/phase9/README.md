# Phase 9 — 8 Gatekeeper Scripts Re-run Manifest

> **执行时间**：2026-09-17 00:27:29-00:27:58（30 秒内完成）
> **执行人**：Phase 9 Regression / Git / Cleanup
> **基线**：Batch 1.1（首次落档于 audit/static-gap/01-08-*.txt）

## 重跑结果

8 个守门脚本全部命中数与 Batch 1.1 **完全一致**（零 Delta / 零新增缺口）。

| # | 脚本 | Batch 1.1 | Phase 9 | Delta | 落档位置 |
|---|---|---|---|---|---|
| 01 | `01-scan-fake-success.ps1` | 299 | 299 | 0 | `audit/static-gap/01-fake-success.txt` |
| 02 | `02-scan-mock.ps1` | 90 | 90 | 0 | `audit/static-gap/02-mock.txt` |
| 03 | `03-scan-disabled.ps1` | 94 | 94 | 0 | `audit/static-gap/03-disabled.txt` |
| 04 | `04-scan-endpoint-coverage.ps1` | 2013/859/441/1572/418 | 2013/859/441/1572/418 | 0/0/0/0/0 | `audit/static-gap/04-endpoint-coverage.txt` |
| 05 | `05-scan-contract-drift.ps1` | 1530 | 1530 | 0 | `audit/static-gap/05-contract-drift.txt` |
| 06 | `06-scan-soft-delete.ps1` | 77（63 HIGH + 14 OK）| 77 | 0 | `audit/static-gap/06-soft-delete.txt` |
| 07 | `07-scan-transaction.ps1` | 214 | 214 | 0 | `audit/static-gap/07-transaction.txt` |
| 08 | `08-scan-shadow-ghost.ps1` | 1572/418/3/8/2 | 1572/418/3/8/2 | 0/0/0/0/0 | `audit/static-gap/08-shadow-ghost.txt` |

## 执行命令

```powershell
# Phase 9 重跑（全部沿用默认参数）
.\audit\scripts\01-scan-fake-success.ps1
.\audit\scripts\02-scan-mock.ps1
.\audit\scripts\03-scan-disabled.ps1
.\audit\scripts\04-scan-endpoint-coverage.ps1
.\audit\scripts\05-scan-contract-drift.ps1
.\audit\scripts\06-scan-soft-delete.ps1
.\audit\scripts\07-scan-transaction.ps1
.\audit\scripts\08-scan-shadow-ghost.ps1
```

## 守门脚本输出落档位置

> ⚠️ 8 个守门脚本默认输出到 `audit/static-gap/01-08-*.txt`（覆盖式），即**当前 static-gap 内容已是 Phase 9 结果**。
> 本目录（`audit/runtime/phase9/`）仅存放 manifest，不复制 8 个详细输出文件（避免冗余 + 避免误导历史对比）。

## 关键判断

- ✅ **零新增缺口**：Phase 8 后没有引入新的 catch-log-swallow / mock / disabled / contract drift / soft-delete / transaction 问题
- ✅ **零 Shadow/Ghost 回归**：新增 5 个 Mkt*Controller + 1 Service **未引入新的 Shadow/Ghost**（前端路由已对齐）
- ✅ **零软删除回归**：77 张表 UNIQUE 不含 deleted 的问题集与 Batch 1.1 一致
- ✅ **零事务回归**：215 处 @Transactional 缺 rollbackFor（Phase 8 已识别，I009 沿用）

## 报告引用

- 主报告：`audit/reports/regression-report.md` §三
- 基线对比：`audit/reports/regression-report.md` §5.1
- 执行时间戳：见上述各 `audit/static-gap/*.txt` 头部 `Execution Time` 字段