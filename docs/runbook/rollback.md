# Runbook — Meiyetec 应用 / DB / 前端 / 数据 / 配置 五大回滚（R1.0-E  T-E05）

> **定位**：SRE / DBA 故障或发布回滚的 step-by-step 操作手册。
>
> **配套文档**：
> - `docs/runbook/backup-restore.md`（I099）— DB 物理恢复基础；本 Runbook §3 强引
> - `docs/DEPLOY-SOP.md` §1.4 / §2.3 / §3.2（片段式回滚，本 Runbook 是其升级版）
> - `docs/prod-env-manifest.md` — ENV 静态清单
> - `audit/issues/I100-rollback-gap.md`（本 Runbook 修复的缺口登记）
>
> **RTO 目标**（决策 1.6-1/6/7）：
>
> | 场景 | RTO | 演练频次 |
> |---|---|---|
> | 应用（容器） | ≤ 5 min | 月度 |
> | DB（Migration / 全量） | ≤ 30 min | 季度 |
> | 前端（S3） | ≤ 10 min | 季度 |
> | 数据（软删除 / binlog row） | ≤ 60 min | 半年度 |
> | 配置（ENV） | ≤ 15 min | 半年度 |
>
> **RPO**：1h（与 I099 备份策略一致；不做 binlog 增量）

---

## §1 触发场景 + 决策树

### 1.1 5 类回滚入口

| 场景 | 典型触发 | 入口 |
|---|---|---|
| **A. 应用回滚** | 发布后 5xx 飙升 / 关键 API P99 突变 | §2 |
| **B. DB 回滚** | 迁移后字段错 / 索引拖慢 / DROP 误删 | §3 |
| **C. 前端回滚** | 新版 SPA 引爆 bug / CDN purge 失败 | §4 |
| **D. 数据回滚** | 业务误删行（订单 / 会员 / 退款） | §5 |
| **E. 配置回滚** | ENV 改错（JWT_SECRET / DB 密码 / 支付密钥） | §6 |

### 1.2 决策树 — 先选场景，再进章节

```
[故障 / 误操作]
  │
  ├── 1. 是否应用层（容器 / 镜像）？
  │     ├─ 是 → §2 应用回滚（5min）
  │     └─ 否 ↓
  │
  ├── 2. 是否数据库结构（DDL 后字段错 / 索引拖慢）？
  │     ├─ 是 → §3.1 DDL 类型判断 → 加列/DROP 列/DROP 表各自路径
  │     └─ 否 ↓
  │
  ├── 3. 是否前端 SPA（新版本引爆 bug）？
  │     ├─ 是 → §4 前端回滚（10min）
  │     └─ 否 ↓
  │
  ├── 4. 是否业务数据行级误删？
  │     ├─ 是 → §5 数据回滚
  │     │       ├─ 软删除场景 → §5.1 方法 A（UPDATE deleted=0，5min）
  │     │       └─ 真删场景 → §5.2 方法 B（binlog row-level，30-60min）
  │     └─ 否 ↓
  │
  └── 5. 是否 ENV / 配置？
        ├─ 是 → §6 配置回滚
        │       ├─ 错改 ENV 值 → §6.1 revert 到 .env.production.YYYYMMDD.bak
        │       └─ 关键密钥轮换 → §6.2 JWT_SECRET / DB 密码 / 支付密钥
        └─ 否 → 升级 SRE TL + DBA TL
```

### 1.3 通用前置（所有场景必做）

- [ ] **通知**：PM / 业务方 / on-call 群（飞书），预估 RTO + 风险
- [ ] **回滚负责人**：DBA 主值班（DB / 数据 / ENV）/ SRE 主值班（应用 / 前端）
- [ ] **记录日志**：所有命令输出保存到 `/var/log/meiyetec/rollback-$(date +%Y%m%d_%H%M%S).log`
- [ ] **演练 / 真实**：明确本次是 DR drill 还是真实故障，**真实故障必须 ack "回滚决策" 再执行**

---

## §2 应用回滚（容器）

### 2.1 触发条件

- 发布后 Grafana 5xx > 0.5% 或 P99 > 500ms 持续 5min（DEPLOY-SOP §4 阈值）
- Actuator `/actuator/health` 报 DOWN
- 关键业务接口 500 / 504 持续

### 2.2 前置 — 找上一个稳定 tag

```bash
# 查最近 10 个发布 tag（按提交时间倒序）
cd /opt/meiyetec
git log --grep='^chore(release)' --oneline -10
# 或
git log --oneline --tags -10
```

期望看到 2-3 个候选；选**上一个生产稳定 tag**（如 `v2.0.42`，当前线上 `v2.0.43` 出问题 → 回 `v2.0.42`）。

### 2.3 步骤（一键脚本）

```bash
# dry-run 预演
bash /opt/meiyetec/scripts/rollback-app.sh --to v2.0.42 --dry-run

# 正式回滚
bash /opt/meiyetec/scripts/rollback-app.sh --to v2.0.42
```

脚本行为（详见 `scripts/rollback-app.sh`）：

1. 记录当前 tag 到 `/var/log/meiyetec/rollback-app-*.log`
2. `sed` 改 `docker-compose.yml` 中 `meiyetec/api:${TAG}` → `meiyetec/api:v2.0.42`
3. `docker compose up -d --no-deps api`（仅重启 api，不动 mysql/redis）
4. 循环 `curl /actuator/health` 30 次（每次 2s，超时 60s）
5. **失败则自动恢复原 tag + `docker compose up -d --no-deps api` + exit 1**

### 2.4 验证

```bash
# 1. 健康检查
curl -f http://localhost:8080/actuator/health
# 期望：{"status":"UP"}

# 2. 看镜像 tag 已切
docker ps --filter "name=meiyetec-api" --format "{{.Image}}"
# 期望：meiyetec/api:v2.0.42

# 3. 关键接口冒烟（DEPLOY-SOP §5）
TOKEN=<your-jwt>
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/pos/order/list
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/crm/member/list
```

### 2.5 回滚失败 fallback

| 失败现象 | 排查 | 恢复 |
|---|---|---|
| `docker compose up` 报 image pull 错 | 确认目标 tag 镜像存在（`docker images \| grep meiyetec/api`） | 重新 build 或回退到上一个**已知 build 在本地的** tag |
| health check 60s 内全失败 | 看 `docker logs meiyetec-api --tail 200` | 脚本会自动恢复原 tag；人工再次验证 |
| 蓝绿回滚（如果有 green 节点存活） | 直接改 nginx upstream | `sed -i 's/server 127.0.0.1:8081/server 127.0.0.1:8080/' /etc/nginx/conf.d/meiyetec.conf && nginx -s reload` |

### 2.6 RTO

- 正常路径：**≤ 5 min**（sed + up + health check）
- 失败 fallback：**≤ 10 min**（含手动排查）

---

## §3 DB 回滚（Migration）

### 3.1 DDL 类型决策树

```
[迁移后问题]
  │
  ├── 加列 / 加索引 / 加表 / 改列宽（兼容）
  │     └─ §3.2 正向回滚（写配套 drop_column / drop_index / drop_table SQL）
  │        RTO ≤ 15min
  │
  ├── 删列 / 删表 / 改列类型（破坏性）
  │     └─ §3.3 走 backup-restore 路径（无正向 SQL 可逆）
  │        RTO ≤ 30min
  │
  └── 数据迁移（INSERT/UPDATE 批量）
        └─ §3.4 binlog / 备份点恢复
           RTO ≤ 30min
```

### 3.2 正向回滚（加列 / 加索引 / 加表）

**前提**：迁移脚本已写好配套 `rollback_*.sql`（参考 ROLLBACK-6.2-mkt_ddl.md 范式）。

```bash
# 1. 停服（避免脏写）
sudo sed -i 's/server 127\.0\.0\.1:8080;/# server 127.0.0.1:8080;/' /etc/nginx/conf.d/meiyetec.conf
sudo nginx -s reload

# 2. 跑 rollback SQL
mysql -h $DB_HOST -u $DB_USER -p meiyetec < backend/src/main/resources/db/sql/rollback_xxx.sql

# 3. 验证
mysql -h $DB_HOST -u $DB_USER -p meiyetec -e "SHOW COLUMNS FROM table_name LIKE 'added_col';"
# 期望：Empty set（列已删除）

# 4. 起服（同 §3.2 §1 反向）
sudo sed -i 's/# server 127\.0\.0\.1:8080;/server 127.0.0.1:8080;/' /etc/nginx/conf.d/meiyetec.conf
sudo nginx -s reload
```

### 3.3 全量恢复（删列 / 删表）

> **强引**：`docs/runbook/backup-restore.md`（I099）— DB 物理恢复基础。
> 本节是它的"应用入口"，**底层实际调用** `scripts/restore-db.sh` + §3 决策树。

**一键脚本**：

```bash
# dry-run 预演
bash /opt/meiyetec/scripts/rollback-db-restore.sh \
    --from s3://meiyetec-prod-backups/daily/meiyetec_LATEST.sql.gz \
    --target-time "$(date -Iseconds)" \
    --dry-run

# 正式回滚
bash /opt/meiyetec/scripts/rollback-db-restore.sh \
    --from s3://meiyetec-prod-backups/daily/meiyetec_YYYYMMDD_HHMMSS.sql.gz \
    --target-time "$(date -Iseconds)"
```

脚本行为：

1. 提示用户停服（必须确认）
2. `mysqldump 当前库` → `/var/backups/meiyetec/pre-restore/`（防误覆盖）
3. 下载 + `gzip -t` 校验 + head 200 行含 `CREATE TABLE`
4. `gunzip | mysql` 导入
5. 跑 `migration_smoke_test.sql` 验证
6. 打印 5 张关键表行数
7. **报告 RTO 实际耗时**（与目标 30min 对比）

**完整步骤 + 验证清单** → 见 `docs/runbook/backup-restore.md` §3-§4。

### 3.4 数据迁移回滚（INSERT/UPDATE 批量）

```bash
# A. 找迁移前的备份点（同 §3.3）
# B. 同 §3.3 跑全量恢复（注意 RPO = 24h，迁移后产生的新数据会丢）
# C. 如果必须保留迁移后的新数据：
#    1. 在另一台机器恢复迁移前全量
#    2. mysqlbinlog --start-position=<before-migration-pos> --stop-position=<migration-end-pos>
#    3. 提取迁移产生的 SQL（不重放）
#    4. 在另一台机器 binlog row-level 过滤 → 写回生产
#    (此为应急手段，强烈建议演练 1 次后归档步骤)
```

### 3.5 关键约束

- ⚠️ **归档表被 DROP 后，专属 rollback SQL 失效**（参考 ROLLBACK-6.2-mkt_ddl.md §3 限制）→ 此时**只能**走 §3.3 全量恢复
- ⚠️ RPO = 24h（决策 1.6-1 / 1.3-1）→ 误删场景最坏丢 24h 数据，需业务方 ack

### 3.6 RTO

- 加列 / 加索引：**≤ 15 min**（停服 + 跑 SQL + 验证 + 起服）
- 全量恢复：**≤ 30 min**（下载 10min + 导入 15min + 验证 5min）
- 数据迁移回滚：**≤ 30 min**（同全量恢复）

---

## §4 前端回滚（S3）

### 4.1 触发条件

- 新版 SPA 引爆 bug（白屏 / 接口调错 / 路由死循环）
- CDN purge 失败导致用户长时间看到坏页
- index.html 引用了不存在的 hash 资源

### 4.2 前置 — 找 prev-build

```bash
# 列 S3 全部 build（按修改时间倒序）
aws s3 ls s3://meiyetec-static/ | sort -rk1,2 | head -10

# 或看 build manifest（如有）
aws s3 cp s3://meiyetec-static/build-manifest.json - | jq .
```

**约定**：每次发布的 build 路径为 `s3://meiyetec-static/v2.0.${BUILD_NUMBER}/`，`current/` 是 nginx / CDN 当前指向。

### 4.3 步骤（一键脚本）

```bash
# dry-run 预演
bash /opt/meiyetec/scripts/rollback-frontend.sh \
    --prev-build s3://meiyetec-static/v2.0.42 \
    --dry-run

# 正式回滚
bash /opt/meiyetec/scripts/rollback-frontend.sh \
    --prev-build s3://meiyetec-static/v2.0.42
```

脚本行为（详见 `scripts/rollback-frontend.sh`）：

1. `aws s3 ls` 验证 prev-build 存在
2. `aws s3 sync s3://meiyetec-static/v2.0.42/ s3://meiyetec-static/current/ --delete`
3. `aws cloudfront create-invalidation --paths '/*'`
4. `curl` 前端 URL 验证 `index.html` hash 与 prev-build 一致

### 4.4 验证

```bash
# 1. CDN 已刷新（30s 后）
curl -sI https://meiyetec.com/ | grep -i "x-amz-cf-id"
# 期望：返回新 ID

# 2. index.html 内容已切
curl -s https://meiyetec.com/ | md5sum
# 与 s3://meiyetec-static/v2.0.42/index.html 的 md5sum 对比

# 3. 关键页面冒烟（浏览器 / curl）
curl -f https://meiyetec.com/login
curl -f https://meiyetec.com/dashboard
```

### 4.5 回滚失败 fallback

| 失败现象 | 排查 | 恢复 |
|---|---|---|
| `aws s3 sync` 报 NoSuchBucket | 确认 S3 bucket 存在 + IAM 权限 | 切换到 backup S3 bucket；用 IAM 临时授权 |
| `cloudfront create-invalidation` 失败 | 看 CloudFront distribution ID 配置 | 手动登录 AWS Console 触发 invalidation |
| CDN purge 仍未生效（5min 后） | 看 invalidation 进度 | 加 `/index.html` + `/assets/*` 双 path 重试 |
| 旧版有 bug 不能回滚 | 找更早的 build | 改用 `--prev-build s3://meiyetec-static/v2.0.41` 重跑 |

### 4.6 RTO

- 正常路径：**≤ 10 min**（s3 sync + cloudfront invalidate + curl 验证）
- 失败 fallback：**≤ 15 min**（含手动排查）

---

## §5 数据回滚（软删除恢复）

### 5.1 触发条件

- 业务运营误删订单 / 会员 / 退款单（一般是 UI 操作）
- 程序 bug 导致批量误更新（如状态机错误迁移）
- 客服 / 财务对账发现数据"消失"

### 5.2 决策树 — 选方法 A 还是 B

```
[数据行级误删 / 误更新]
  │
  ├── 表有 `deleted` 字段（软删除）且本次删除走的是逻辑删除？
  │     └─ 是 → §5.1 方法 A（UPDATE deleted=0，5min）
  │
  └── 真删（DROP / TRUNCATE / DELETE 无 deleted 字段）？
        └─ §5.2 方法 B（binlog row-level，30-60min）
```

### 5.3 方法 A — 软删除恢复（首选，5min）

```sql
-- 例：恢复订单 id=12345
UPDATE pos_order_consumption
SET deleted = 0, update_time = NOW()
WHERE id = 12345 AND deleted = 1;

-- 验证
SELECT id, deleted, update_time FROM pos_order_consumption WHERE id = 12345;
-- 期望：deleted=0，update_time=now

-- 批量恢复（误删一批）
UPDATE pos_order_consumption
SET deleted = 0, update_time = NOW()
WHERE deleted = 1
  AND update_time BETWEEN '2026-10-11 14:00:00' AND '2026-10-11 14:05:00'
  AND store_id = 1;
```

**关键**：必须**先确认误删时间窗口**（看 `update_time` 段）→ 避免覆盖其他正常删除。

### 5.4 方法 B — binlog row-level 恢复（应急，30-60min）

**前提**：MySQL 开启 `binlog_format = ROW`（生产默认）。

```bash
# 1. 找误删时间段的 binlog 文件
ls -lh /var/log/mysql/binlog.* | tail -10
# 或
mysql -h $DB_HOST -u root -p -e "SHOW BINARY LOGS;"

# 2. 提取 binlog 中的 row-level 事件
mysqlbinlog \
    --start-datetime='2026-10-11 14:00:00' \
    --stop-datetime='2026-10-11 14:05:00' \
    --verbose --base64-output=DECODE-ROWS \
    /var/log/mysql/binlog.000123 \
    > /tmp/binlog-extract.txt

# 3. 过滤出目标表的 DELETE / UPDATE 事件
grep -A 5 "### DELETE FROM.*pos_order_consumption" /tmp/binlog-extract.txt
grep -A 5 "### UPDATE.*pos_order_consumption" /tmp/binlog-extract.txt

# 4. 手动构造反向 SQL（DELETE → INSERT，UPDATE → 反向 UPDATE）
# 5. 在 staging 验证
# 6. 在生产 dry-run 跑（先 SELECT 不 commit，核对行数）
# 7. 业务方 ack → 正式执行
```

**风险**：

- ⚠️ binlog 提取必须精准到表 + 时间窗口，否则会污染其他事务
- ⚠️ 反向 SQL 必须人工核对（机器不会自动生成可靠的反向 SQL）
- ⚠️ 强烈建议在演练中跑过至少 1 次再真实使用

### 5.5 RTO

- 方法 A：**≤ 5 min**（5min 是 soft delete 表的常见恢复耗时）
- 方法 B：**≤ 60 min**（binlog 提取 + 构造 + 验证 + 执行）

---

## §6 配置回滚（ENV）

### 6.1 触发条件

- ENV 改错（如 `PAY_ENV_MODE=sandbox` 误改 `production`）
- 关键密钥需要轮换（JWT_SECRET / DB 密码 / 支付密钥）
- 第三方 API 密钥过期需更新

### 6.2 ENV 版本化约定

> **决策 1.6-9**：每次 ENV 修改前，**必须**先备份当前 `.env` 为 `.env.production.YYYYMMDD.bak`。

```bash
# /opt/meiyetec/env-prod/
├── .env.production                    # 当前生产 ENV（symlink 或 active）
├── .env.production.20261010.bak       # 10/10 备份（v2.0.43 发布前）
├── .env.production.20261011.bak       # 10/11 备份（v2.0.44 发布前）
└── .env.production.20261011_rollback.bak  # 紧急回滚保留 1 周
```

**修改 ENV 流程**（DEPLOY-SOP §2.1 引用）：

```bash
# 1. 备份当前
sudo cp -p /opt/meiyetec/env-prod/.env.production \
          /opt/meiyetec/env-prod/.env.production.$(date +%Y%m%d).bak

# 2. 编辑
sudo vim /opt/meiyetec/env-prod/.env.production

# 3. 应用（重启 api）
bash /opt/meiyetec/scripts/rollback-app.sh --to $(git rev-parse --short HEAD)  # 仅重启，不动 image
# 或
docker compose -f /opt/meiyetec/docker-compose.yml --env-file /opt/meiyetec/env-prod/.env.production up -d --no-deps api
```

### 6.3 关键 ENV 轮换流程

#### 6.3.1 JWT_SECRET 轮换

```bash
# 1. 生成新 secret（256-bit）
NEW_SECRET=$(openssl rand -base64 48 | tr -d '/+=' | head -c 64)
echo "new secret length: ${#NEW_SECRET}"

# 2. 备份当前
sudo cp -p /opt/meiyetec/env-prod/.env.production \
          /opt/meiyetec/env-prod/.env.production.jwt_rotate.$(date +%Y%m%d).bak

# 3. 改 ENV
sudo sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$NEW_SECRET|" /opt/meiyetec/env-prod/.env.production

# 4. 重启 api + 强制所有用户重新登录（无法热切，旧 token 全失效）
docker compose -f /opt/meiyetec/docker-compose.yml \
               --env-file /opt/meiyetec/env-prod/.env.production up -d --no-deps api

# 5. 通知 PM：所有用户需重新登录（约 5min 内全部踢出）
```

#### 6.3.2 DB 密码轮换

```bash
# 1. MySQL 端加新用户 / 改密码
mysql -h $DB_HOST -u root -p -e "ALTER USER '$DB_USER'@'%' IDENTIFIED BY '$NEW_PASS';"
mysql -h $DB_HOST -u root -p -e "FLUSH PRIVILEGES;"

# 2. 改 ENV
sudo sed -i "s|^SPRING_DATASOURCE_PASSWORD=.*|SPRING_DATASOURCE_PASSWORD=$NEW_PASS|" \
         /opt/meiyetec/env-prod/.env.production

# 3. 重启 api（一次重启窗口约 30s，连接池会重连）
docker compose -f /opt/meiyetec/docker-compose.yml \
               --env-file /opt/meiyetec/env-prod/.env.production up -d --no-deps api

# 4. 验证：连续 5min 无 DB 报错
docker logs meiyetec-api --since 5m | grep -i "datasource\|connection refused" || echo "OK"
```

#### 6.3.3 支付密钥轮换

支付密钥（微信 / 支付宝 / PayPal 等）轮换需与第三方平台协同：

1. 通知支付平台 + PM 业务方（至少提前 24h）
2. 在第三方平台生成新密钥（保留旧密钥 7d 灰度）
3. 更新 ENV（保留新旧双密钥 → 见下方示例）
4. 验证 24h 旧密钥调用 = 0
5. 撤销旧密钥

```bash
# 双密钥过渡（部分平台支持）
# WECHAT_PAY_SECRET_V2=$NEW_SECRET  # 新
# WECHAT_PAY_SECRET=$OLD_SECRET     # 旧（7d 后撤）
```

### 6.4 一键 revert 脚本

```bash
# 列出可用 .bak
ls -lt /opt/meiyetec/env-prod/.env.production.*.bak | head -10

# revert 到指定日期
sudo cp -p /opt/meiyetec/env-prod/.env.production.20261010.bak \
          /opt/meiyetec/env-prod/.env.production

# 重启 api
docker compose -f /opt/meiyetec/docker-compose.yml \
               --env-file /opt/meiyetec/env-prod/.env.production up -d --no-deps api
```

### 6.5 ENV diff 工具（如已装）

```bash
# 装 diffenv（如未装）
# pip install diffenv  # 不一定存在，按需替代

# 简单 diff
diff -u /opt/meiyetec/env-prod/.env.production \
        /opt/meiyetec/env-prod/.env.production.20261010.bak
```

### 6.6 RTO

- 普通 ENV revert：**≤ 15 min**（含 restart）
- JWT_SECRET 轮换：**≤ 15 min**（含踢出所有用户）
- 支付密钥轮换：**24-72h**（与第三方平台协同灰度）

---

## §7 附录 — 演练 Checklist 模板

> **决策 1.6-6**：DB 季度 / 应用月度 / 前端季度 / 数据+ENV 半年度。
> 演练结果登记到 `audit/drills/YYYY-MM-{scenario}.md`。

### 7.1 应用回滚月度演练 checklist

```markdown
## Meiyetec 应用回滚月度演练 — YYYY-MM

- [ ] **1. 前置**
  - 选目标 tag：v2.0.XX（当前线上 v2.0.XY）
  - 通知 SRE TL + 业务方（窗口 ≤ 5min）
  - staging 环境就绪（docker compose up -d）

- [ ] **2. dry-run 验证**
  - `bash scripts/rollback-app.sh --to v2.0.XX --dry-run` → EXIT=0
  - 打印命令列表包含：sed + docker compose up + curl

- [ ] **3. 正式回滚**
  - `bash scripts/rollback-app.sh --to v2.0.XX`（无 --dry-run）
  - 记录：实际 RTO = ___ min（目标 ≤ 5min）
  - 健康检查 30 次全 UP

- [ ] **4. 验证**
  - `curl /actuator/health` → 200 UP
  - 关键接口冒烟（DEPLOY-SOP §5）全 OK
  - Grafana 5xx 持续 5min < 0.5%

- [ ] **5. 清理**
  - 切回当前线上 tag v2.0.XY
  - 记录日志到 /var/log/meiyetec/rollback-app-*.log

- [ ] **结论**
  - 通过 / 不通过
  - 改进项：____
  - 下次演练时间：YYYY-MM+1
```

### 7.2 DB 回滚季度演练 checklist

```markdown
## Meiyetec DB 回滚季度演练 — YYYY-QX

- [ ] **1. 备份完整性**
  - 拉 S3 最新 .sql.gz
  - `gzip -t` 通过
  - 文件大小 > 100KB
  - head 200 行含 CREATE TABLE

- [ ] **2. 决策树选择**
  - 加列 / 加索引 / 加表 → §3.2
  - 删列 / 删表 / 改列类型 → §3.3
  - 数据迁移 → §3.4
  - 选定路径：____

- [ ] **3. dry-run**
  - `bash scripts/rollback-db-restore.sh --from s3://... --target-time "$(date -Iseconds)" --dry-run` → EXIT=0
  - 打印命令列表

- [ ] **4. 正式回滚（staging）**
  - `bash scripts/rollback-db-restore.sh --from s3://...`（无 --dry-run）
  - 记录：download X min / import Y min / verify Z min
  - 评估：X+Y+Z ≤ 30 min？

- [ ] **5. 数据对账**
  - 5 张关键表行数 vs 上周报告（差异 < 30%）
  - 6 张 P0 表存在
  - tenant_id NULL = 0

- [ ] **6. 报警链路**
  - 故意 kill mysql → on-call 群收到报警
  - 恢复 mysql

- [ ] **结论**
  - 通过 / 不通过
  - 改进项：____
  - 下次演练时间：YYYY-QX+1
```

### 7.3 前端回滚季度演练 checklist

```markdown
## Meiyetec 前端回滚季度演练 — YYYY-QX

- [ ] **1. 前置**
  - 选 prev-build：s3://meiyetec-static/v2.0.XX
  - 通知前端负责人 + SRE TL

- [ ] **2. dry-run**
  - `bash scripts/rollback-frontend.sh --prev-build s3://meiyetec-static/v2.0.XX --dry-run` → EXIT=0

- [ ] **3. 正式回滚（staging bucket）**
  - `bash scripts/rollback-frontend.sh --prev-build s3://meiyetec-static-staging/v2.0.XX`
  - 记录：s3 sync X min / cloudfront invalidate Y min
  - 评估：X+Y ≤ 10 min？

- [ ] **4. CDN 验证**
  - `curl https://staging.meiyetec.com/ | md5sum` 与 prev-build index.html md5 一致
  - 关键页面：/login /dashboard 正常渲染
  - CloudFront 监控 invalidation 状态 = Completed

- [ ] **5. 清理**
  - 切回当前线上 build v2.0.XY
  - 记录日志

- [ ] **结论**
  - 通过 / 不通过
  - 改进项：____
  - 下次演练时间：YYYY-QX+1
```

### 7.4 数据回滚半年度演练 checklist

```markdown
## Meiyetec 数据回滚半年度演练 — YYYY-HX

- [ ] **1. 准备演练数据**
  - 选 1 条测试订单（如 test_order_id=99999）
  - 方法 A 演练：UPDATE deleted=1 → 验证 → UPDATE deleted=0
  - 方法 B 演练：故意 DELETE → binlog row-level 提取 → 人工构造 INSERT → staging 验证

- [ ] **2. 决策树判断**
  - 软删除场景？→ §5.1
  - 真删场景？→ §5.2
  - 选定方法：____

- [ ] **3. 时间窗**
  - 方法 A 实际耗时 ___ min（目标 ≤ 5min）
  - 方法 B 实际耗时 ___ min（目标 ≤ 60min）

- [ ] **4. 风险点**
  - binlog 提取精准度（只命中目标表 / 时间窗口）
  - 反向 SQL 构造正确性
  - 业务方 ack 流程

- [ ] **结论**
  - 通过 / 不通过
  - 改进项：____
  - 下次演练时间：YYYY-HX+1
```

### 7.5 ENV 回滚半年度演练 checklist

```markdown
## Meiyetec ENV 回滚半年度演练 — YYYY-HX

- [ ] **1. 准备**
  - 选测试 ENV：JWT_SECRET / SPRING_DATASOURCE_PASSWORD / PAY_ENV_MODE（任选 1）
  - 通知 SRE TL + DBA

- [ ] **2. 故意改错**
  - 改 JWT_SECRET 为 invalid value
  - 触发 api 重启 → 验证登录失败

- [ ] **3. revert 流程**
  - cp -p .env.production.2026XXXX.bak .env.production
  - 重启 api → 验证登录恢复
  - 实际耗时 ___ min（目标 ≤ 15min）

- [ ] **4. 关键密钥轮换（独立演练）**
  - JWT_SECRET 轮换：生成新 secret → 改 ENV → 重启 → 用户全部重新登录
  - DB 密码轮换：MySQL 端改 → 改 ENV → 重启 → 验证 5min 无 DB 报错
  - 支付密钥：与第三方平台协同（演练时仅验证流程，不真轮换）

- [ ] **结论**
  - 通过 / 不通过
  - 改进项：____
  - 下次演练时间：YYYY-HX+1
```

---

## §8 联系人 + 升级路径

| 角色 | 主联系人 | 备份 | 响应 SLA |
|---|---|---|---|
| SRE 主值班 | 见 on-call 群 | 见 on-call 群 | 工作日 15min / 非工作日 30min / 灾难 5min |
| DBA 主值班 | 见 on-call 群 | 见 on-call 群 | 同上 |
| PM / 业务方 | 业务 TL | PMO | 工作日 30min / 非工作日 1h |
| 升级 | SRE TL | SRE Director | 5 类回滚均失败 / 1h 内未恢复 |

**升级触发**（任一即升）：

- 任一类回滚 RTO 超目标 50%
- 演练连续 2 次不通过
- 真实故障 + 回滚 + 业务影响 > 1h
- 关键密钥泄露（JWT / DB / 支付）

---

## §9 升级触发（本 Runbook 不足时）

| # | 现象 | 升级方案 |
|---|---|---|
| 1 | 5 类回滚均超 RTO 目标 50% | 引入 Ansible playbook 串联（决策 1.6-7 跳过项） |
| 2 | DB RPO 业务方要求 < 1h | 加 binlog 增量（决策 1.6-1 跳过项） |
| 3 | 数据误删 > 月度 1 次 | 写 `rollback-row.sh` 封装（决策 1.6-4 跳过项） |
| 4 | 关键密钥轮换 > 季度 1 次 | 引入 Vault / Sealed Secrets |
| 5 | 跨 region 故障 | K8s + 异地切流（决策 1.6-10 跳过项） |

---

*文档版本: 1.0 · 2026-10-11*
*下次审查: 季度（Q1 2027）或下一次部署架构变更前*
