# I100 — 应用回滚缺口（修复前）

| 项 | 值 |
|---|---|
| **Issue ID** | I100 |
| **标题** | 5 类回滚（应用 / DB / 前端 / 数据 / 配置）均无统一 Runbook，仅有 DEPLOY-SOP 片段 |
| **严重度** | **P0**（阻塞 Gate 3 部署门禁） |
| **影响范围** | 生产 5 类回滚操作 + 故障 RTO + 误操作恢复能力 |
| **发现批次** | Gate 3 批次 1.6（只读评估，2026-10-11） |
| **修复批次** | Gate 3 批次 1.6（同上） |
| **负责人** | SRE 主值班 + DBA 主值班 |
| **状态** | ✅ 已修复（本 commit 闭环） |

---

## 1. 问题描述

### 1.1 现状（修复前）

| 回滚场景 | 现状 | 风险 |
|---|---|---|
| 应用（容器） | DEPLOY-SOP §2.3 给出 sed upstream + `docker rm meiyetec-blue` 两行命令 | ❶ 无 RTO 数字；❷ 无健康检查验证步骤；❸ 无蓝绿双节点前提确认；❹ 无历史 SHA 检索方法 |
| DB（Migration） | DEPLOY-SOP §1.4 一行 `gunzip \| mysql`；ROLLBACK-6.2-mkt_ddl.md 单次 DROP 案例 | ❶ 无决策树（加列/DROP 列/DROP 表各自能怎么回）；❷ 无 RTO；❸ 与 backup-restore.md **未互引**（rollback 严重依赖 restore） |
| 前端（S3） | DEPLOY-SOP §3.2 `aws s3 sync + cloudfront create-invalidation` 两行 | ❶ 无 `PREV_BUILD` 来源；❷ CDN purge 失败 fallback 缺失；❸ hash 资源回滚未与 index.html 同步验证 |
| 数据（软删除恢复） | **全缺失** | 业务行级误删**无回滚手段**，只能等全量 DB 恢复（最坏丢 24h） |
| 配置（ENV） | DEPLOY-SOP §2.1 注释指 `prod.env`（prod-env-manifest.md）；compose `env_file` 注入 | ❶ 无 ENV 版本化（`.env.production.YYYYMMDD.bak`）；❷ 无 ENV diff 工具；❸ 关键 ENV（JWT_SECRET/DB 密码/支付密钥）轮换流程缺失 |
| 演练记录 | **全缺失** | 5 类回滚均无 DR drill 计划 / 频率 / 记录模板 |

### 1.2 业务影响

- 应用发布后 5xx 飙升需要回滚：现场查 SOP 片段，RTO 不可控（30min+）
- DB 误删列 / 误 DROP 表：唯一回滚手段 = 全量恢复（24h RPO 兜不住）
- 前端新版 index.html 引爆 bug：CDN purge 失败 = 用户长时间看到坏页
- 业务运营误删一行会员档案：唯一手段 = DBA 现场 binlog grep（无 SOP）
- ENV 改错（如 `PAY_ENV_MODE`）：无 diff 工具，只能靠人回忆

### 1.3 关联上下文

- DEPLOY-SOP §2.3 / §1.4 / §3.2 已有片段式回滚
- ROLLBACK-6.2-mkt_ddl.md 是单次 SQL DROP 回滚案例参考
- backup-restore.md（I099）已建立 DB 物理恢复基础（脚本 + 验证 + 演练）
- prod-env-manifest.md 是静态 ENV 清单（无回滚步骤）
- scripts/recovery/* 是**已 disabled 控制器复活**（不同主题，不混用）

---

## 2. 决策记录（2026-10-11 修复前会议）

| # | 决策项 | 选择 | 理由 |
|---|---|---|---|
| 1 | RPO 目标 | **1h**（不加 binlog） | 与 I099 备份策略一致；业务方可接受 |
| 2 | 保留期 | S3 30d + Glacier 1y | 复用 I099 既有 S3 bucket 与 lifecycle |
| 3 | 存储后端 | **S3** | 不引入新依赖 |
| 4 | mysql-data host bind | **不改** | 生产 DB 不跑 compose（与 I099 决策一致） |
| 5 | Redis 异地备份 | **不做** | 与 I099 §6 一致 |
| 6 | 演练频率 | **DB 季度 / 应用月度 / 前端季度 / 数据+ENV 半年度** | 按风险等级分层（应用高频低风险；数据+ENV 低频高风险） |
| 7 | P0 范围缩小 | 5 项（Runbook + 3 脚本 + Issue） | 无编排环境跳过 Ansible；演练模板进 Runbook 附录 |
| 8 | 数据行级恢复方法 | 双轨（软删除 + binlog row-level） | 软删除覆盖 90% 场景；binlog 兜底 |
| 9 | ENV 轮换粒度 | 三类分开（JWT_SECRET / DB 密码 / 支付密钥） | 风险等级不同，轮换节奏不同 |
| 10 | Runbook 入口 | `docs/runbook/rollback.md` | 与 backup-restore.md 平级；不混进 DEPLOY-SOP |

---

## 3. 修复范围（本 commit 闭环）

### 3.1 交付清单（5 项）

| # | 路径 | 用途 | 估行数 |
|---|---|---|---|
| 1 | `docs/runbook/rollback.md` | 7 章节 Runbook（触发 / 应用 / DB / 前端 / 数据 / 配置 / 演练 checklist） | ~400 |
| 2 | `scripts/rollback-app.sh` | sed image tag → docker compose up -d --no-deps api → 30 次 health check → 失败回原 | ~80 |
| 3 | `scripts/rollback-db-restore.sh` | 复用 restore-db.sh；强制 pre-restore snapshot + smoke test + RTO 实测 | ~120 |
| 4 | `scripts/rollback-frontend.sh` | aws s3 sync prev → current → cloudfront invalidate → curl 验证 hash | ~60 |
| 5 | `audit/issues/I100-rollback-gap.md` | 本文件（治理闭环） | ~150 |

### 3.2 关键能力

- ✅ **统一入口**：`docs/runbook/rollback.md` §1 决策树按 5 类场景分发
- ✅ **可执行**：`rollback-app.sh` / `rollback-db-restore.sh` / `rollback-frontend.sh` 均含 `--dry-run`
- ✅ **可验证**：应用回滚 30 次 health check（60s 内）；DB 回滚跑 `migration_smoke_test.sql`；前端回滚 curl 验 hash
- ✅ **可回退**：所有脚本失败时自动恢复原状态（rollback 自身的回滚）
- ✅ **RTO 数字**：应用 ≤ 5min / DB ≤ 30min / 前端 ≤ 10min / 数据 ≤ 60min / 配置 ≤ 15min
- ✅ **演练 checklist**：4 频次模板（应用月度 / DB 季度 / 前端季度 / 数据+ENV 半年度）进 Runbook 附录
- ✅ **互引**：rollback.md §3 强引 `docs/runbook/backup-restore.md`（rollback-db-restore.sh 实质是后者的薄封装）

### 3.3 部署步骤（SRE 负责）

```bash
# 1. 安装脚本（与 I099 备份脚本同目录）
sudo install -m 0750 scripts/rollback-app.sh          /opt/meiyetec/scripts/
sudo install -m 0750 scripts/rollback-db-restore.sh   /opt/meiyetec/scripts/
sudo install -m 0750 scripts/rollback-frontend.sh     /opt/meiyetec/scripts/
sudo install -m 0644 docs/runbook/rollback.md         /opt/meiyetec/docs/runbook/

# 2. dry-run 三脚本
bash /opt/meiyetec/scripts/rollback-app.sh        --to HEAD~1 --dry-run
bash /opt/meiyetec/scripts/rollback-db-restore.sh --from s3://meiyetec-prod-backups/daily/meiyetec_LATEST.sql.gz --target-time "$(date -Iseconds)" --dry-run
bash /opt/meiyetec/scripts/rollback-frontend.sh   --prev-build s3://meiyetec-static/v2.0.LAST --dry-run

# 3. RTO 实测
#    - staging 跑一次完整 rollback-app 演练（应用月度）
#    - staging 跑一次完整 rollback-db-restore 演练（DB 季度）
#    记录到 audit/drills/YYYY-QX-rollback-drill.md
```

---

## 4. 未做（本 commit 范围外，升级触发）

| # | 项 | 原因 | 升级触发 |
|---|---|---|---|
| 1 | Ansible playbook 串联 3 脚本 | 当前生产无编排环境；脚本独立可串 | 引入 K8s / Ansible 时 |
| 2 | Binlog PIT（point-in-time）回滚 | 决策 1.6-1 显式不做（与 I099 一致） | RPO 要求 < 1h |
| 3 | 数据行级 binlog 恢复的封装脚本 | rollback.md §5 给出命令，但未做 `rollback-row.sh` | 误删频次 > 月度 1 次时 |
| 4 | Chaos engineering 演练 | 无 Chaos Monkey / Litmus 部署 | 引入 K8s 后 |
| 5 | ENV 轮换的自动化工单 | 关键 ENV 仍是 DBA 手工轮换 | 引入 Vault / Sealed Secrets |
| 6 | 跨 region 回滚（异地 K8s 切流） | 当前单 region | 业务要求 RTO < 15min |
| 7 | 前端 hash 资产单独回滚（不动 index.html） | 当前 sync 全量覆盖 | CDN 命中率达 90% 后的精细化 |
| 8 | 5 类回滚的 **SLO 仪表盘** | 当前仅 DEPLOY-SOP §4 监控阈值 | 引入 Prometheus + Grafana |
| 9 | 独立的 `rollback-drill-checklist.md` | 决策 1.6-7 并入 Runbook 附录 | 演练清单超过 1 页时拆分 |
| 10 | 失败注入 / 蓝绿切换的 SOP 化 | 蓝绿流程在 DEPLOY-SOP §2.2（正向），回滚在 §2.3（片段） | 引入 GitOps 时 |

---

## 5. 验证记录

### 5.1 静态验证（commit 前必做）

```bash
# 1) bash 语法
bash -n scripts/rollback-app.sh          # OK
bash -n scripts/rollback-db-restore.sh   # OK
bash -n scripts/rollback-frontend.sh     # OK

# 2) --dry-run
bash scripts/rollback-app.sh        --to HEAD~1 --dry-run    # EXIT=0
bash scripts/rollback-db-restore.sh --from s3://fake/latest.sql.gz --target-time "2026-10-11T00:00:00+08:00" --dry-run   # EXIT=0
bash scripts/rollback-frontend.sh   --prev-build s3://meiyetec-static/v2.0.PREV --dry-run   # EXIT=0

# 3) markdown 格式（如已装 markdownlint）
markdownlint docs/runbook/rollback.md
```

### 5.2 动态验证（部署后必做，本 commit 不执行）

- [ ] 应用月度演练：staging 跑 `rollback-app.sh --to HEAD~1` 实测 RTO
- [ ] DB 季度演练：staging 跑 `rollback-db-restore.sh --from s3://...` 实测 RTO + 5 张关键表行数
- [ ] 前端季度演练：S3 staging bucket 跑 `rollback-frontend.sh --prev-build ...` + CloudFront test 失效
- [ ] 数据半年度演练：故意软删 1 条 → 走 §5 方法 A 恢复
- [ ] ENV 半年度演练：故意写错 `JWT_SECRET` → 走 §6 revert 流程
- [ ] 首次完整 DR drill 记录登记到 `audit/drills/YYYY-QX-rollback-drill.md`

---

## 6. 关联文档

- `docs/runbook/backup-restore.md`（I099）— DB 物理恢复基础；本 Runbook §3 强引
- `docs/DEPLOY-SOP.md` §1.4 / §2.3 / §3.2（片段式回滚，本 Runbook 升级版）
- `docs/ROLLBACK-6.2-mkt_ddl.md`（单次 DROP 回滚案例参考）
- `docs/prod-env-manifest.md`（ENV 静态清单，§6 引用其字段名）
- `audit/issues/I099-backup-gap.md`（备份恢复缺口，本 issue 的姐妹篇）

---

## 7. 闭环证据

- 本 issue 与 commit `feat(gate3-1.6): 回滚 - Runbook + 3 脚本（app/db/frontend）` 同 PR
- 部署后由 SRE 提交 `audit/drills/2026-Q4-rollback-drill.md` 首次演练记录
- 频次复盘：应用月度 / DB 季度 / 前端季度 / 数据+ENV 半年度（DBA + SRE 共同签字）
