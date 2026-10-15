# DEPLOY-SOP：部署标准作业流程（R2.0-E T-E04）

> **定位**：本文件是部署**标准作业流程 SOP**，与 `RELEASE-GATE-发布门禁checklist.md` 配套使用。
>
> | 文档 | 职责 |
> |---|---|
> | `RELEASE-GATE-发布门禁checklist.md` | "发布前是否允许发布"的**判断门禁**（清单式 checkbox） |
> | `DEPLOY-SOP.md`（本文件） | "允许发布后如何发布"的**操作手册**（流程式 step-by-step） |
>
> 关系：先过 checklist → 再按 SOP 执行。

---

## 0. 适用范围 & 角色

| 角色 | 责任 |
|---|---|
| DBA | §1 SQL 备份 / 脚本执行 / 字段级灰度 |
| SRE | §2 后端 / §3 前端 / §4 监控部署 |
| QA | §5 冒烟用例 |
| PM/PD | §6 发布决议沟通 |

适用：美业 SaaS 系统所有生产环境（main、staging）发布。

---

## 1. 数据库变更（DBA 负责）

### 1.1 发布前 2 小时

```bash
# 1. 全量备份
mysqldump -h prod-db.internal -u root -p --single-transaction --routines \
  --triggers meiyetec > /backup/meiyetec_$(date +%Y%m%d_%H%M).sql
gzip /backup/meiyetec_*.sql

# 2. 备份校验
gunzip -c /backup/meiyetec_*.sql.gz | head -100 | grep -c "CREATE TABLE"
# 期望：非 0
ls -lh /backup/ | tail -3
```

### 1.2 发布窗口开启（22:00）

```bash
# 1. 按顺序执行 SQL（每个文件独立事务）
for f in /opt/sql/R2.0/*.sql; do
    echo "[$(date +%T)] 执行 $f"
    mysql -h prod-db.internal -u root -p meiyetec < "$f" || {
        echo "SQL 执行失败: $f"
        exit 1
    }
done

# 2. 验证关键表存在
mysql -h prod-db.internal -u root -p meiyetec -e "
SHOW TABLES LIKE 'pos_order_consumption';
SHOW TABLES LIKE 'pos_refund';
SHOW TABLES LIKE 'sys_idempotency_record';
"
```

### 1.2.1 全量重建或新部署（V0 Baseline — P0-DB-001 起强制）

> **2026-08-14 起**：所有根目录 `fix_*.sql` 已废弃，禁止在新部署中使用。
> 完整迁移基线已统一到版本化脚本 `db/sql/init_migration_v0_baseline.sql`。

```bash
# 全新数据库场景：一次性跑完所有模块 DDL + 种子 + 数据修复
mysql -h prod-db.internal -u root -p < backend/src/main/resources/db/sql/init_migration_v0_baseline.sql

# 冒烟验证（6 张 P0 表 + 7 个字段 + 0 孤儿 + 0 NULL tenant）
mysql -h prod-db.internal -u root -p meiyetec < backend/src/main/resources/db/sql/migration_smoke_test.sql

# 期望：T1 6/6 存在；T2 7/7 字段；T3 0 孤儿；T4 0 NULL tenant；T5 0 跨租户污染
```

**V0 Baseline 涵盖范围**：
- `init_m01_auth.sql` — 用户/角色/权限 + `sys_login_log`
- `init_m02_basic.sql` — 门店/员工/等级/分类 + `sys_log`
- `init_m03_member.sql` — 会员/标签/档案 + `crm_wallet` + `crm_wallet_transaction`
- `init_m06_booking.sql` — 预约/房间/排班（`biz_booking` / `biz_room` / `biz_employee_schedule`）
- `patch_p0_db_001_data_repair.sql` — 孤儿重派 + NULL tenant 回填（幂等）

**后续 P0-DB-002（FK 约束）将于 `init_migration_v1_fk.sql` 引入**。

### 1.3 发布后验证

```sql
-- 行数合理性
SELECT COUNT(*) FROM pos_order_consumption;
SELECT COUNT(*) FROM pos_refund WHERE deleted = 0;
SELECT COUNT(*) FROM sys_idempotency_record;

-- 兼容性检查
SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'meiyetec' AND table_name = 'stock_record' AND column_name = 'type';
```

### 1.4 回滚（DBA）

```bash
# 严重回滚（5 分钟内）
gunzip -c /backup/meiyetec_20260812_2200.sql.gz | mysql -h prod-db.internal -u root -p meiyetec

# 验证
mysql -h prod-db.internal -u root -p meiyetec -e "SELECT MAX(update_time) FROM stock_record;"
```

---

## 2. 后端部署（SRE 负责）

### 2.1 蓝绿发布

> **镜像来源（批次 1.2 起）**：生产镜像**不再**走 `docker pull meiyetec/api:2.0.${BUILD_NUMBER}`，
> 改为 `SRE 自部署 → 自构建 → 短 SHA 标签`。这样无需依赖镜像仓库预推送，且与本地 docker-compose 用同一份 Dockerfile（构建一致）。
>
> 完整流水线版（CI 自动构建 + ghcr.io 推送 + SRE 拉取）将作为后续 deploy job 引入（不在本批次范围）。

```bash
# 1. 自构建生产镜像（短 SHA 标签，便于回滚定位）
cd ${PROJECT_ROOT}
GIT_SHA=$(git rev-parse --short=8 HEAD)
docker build -t meiyetec/api:${GIT_SHA} \
    -f backend/Dockerfile \
    --build-arg SPRING_PROFILES_ACTIVE=prod \
    backend/

# 2. 启动蓝（100% 流量前先用蓝节点接收灰度流量）
docker run -d --name meiyetec-blue \
  -p 8081:8080 \
  --env-file /opt/meiyetec/prod.env \
  meiyetec/api:${GIT_SHA}

# 3. 健康检查（容器内有 wget + Actuator, gateway/Spring Boot /actuator/health）
sleep 30
curl -f http://localhost:8081/actuator/health || {
    docker logs meiyetec-blue --tail 200
    docker rm -f meiyetec-blue
    exit 1
}
```

> ⚠️ **回滚到任意历史版本**：用对应 SHA 重 build（git checkout ${OLD_SHA} && docker build ...）即可，
> 不依赖外部镜像仓库。`prod.env` 集中管理 ENV（参见 `docs/prod-env-manifest.md`）。

### 2.2 切流量（蓝绿切换）

```bash
# 1. NGINX 上游加入 blue 节点（10% 灰度）
sed -i 's/server 127.0.0.1:8080;/server 127.0.0.1:8080;\n    server 127.0.0.1:8081 weight=1;/' /etc/nginx/conf.d/meiyetec.conf
nginx -s reload

# 2. 监控 30 分钟（无 5xx 异常、错误率 < 0.5%）
sleep 1800

# 3. 切 50%
sed -i 's/weight=1/weight=5/' /etc/nginx/conf.d/meiyetec.conf
nginx -s reload
sleep 1800

# 4. 切 100%（停掉绿）
docker stop meiyetec-green
sed -i '/8081/d' /etc/nginx/conf.d/meiyetec.conf
nginx -s reload
```

### 2.3 后端回滚

```bash
# 1. 切回旧版本
sed -i 's/server 127.0.0.1:8081/server 127.0.0.1:8080/' /etc/nginx/conf.d/meiyetec.conf
nginx -s reload

# 2. 停掉蓝节点
docker rm -f meiyetec-blue
```

---

## 3. 前端部署（SRE 负责）

### 3.1 构建产物上传

```bash
# 1. 构建
cd frontend/meiyetec-web
npm ci || npm install
npm run build

# 2. 上传到 S3
aws s3 sync dist/ s3://meiyetec-static/v2.0.${BUILD_NUMBER}/ --delete

# 3. CDN purge
aws cloudfront create-invalidation \
    --distribution-id E123ABC \
    --paths "/index.html" "/assets/*"
```

### 3.2 回滚

```bash
# 切回上一个版本
aws s3 sync s3://meiyetec-static/v2.0.${PREV_BUILD}/ s3://meiyetec-static/current/ --delete
aws cloudfront create-invalidation --distribution-id E123ABC --paths "/index.html" "/assets/*"
```

---

## 4. 发布后 1 小时监控（SRE）

| 指标 | 阈值 | 监控方式 |
|---|---|---|
| 后端 5xx 错误率 | < 0.5% | Grafana → `http_server_requests_seconds_count{status=~"5.."}` |
| API P99 响应时间 | < 500ms | Grafana → `http_server_requests_seconds{quantile="0.99"}` |
| DB 连接池使用率 | < 80% | HikariCP metrics |
| 库存并发 CAS 失败率 | < 1% | `stock_record` 类型 4 的速率 vs `consumeByOrder` 调用速率 |
| 幂等命中数 / 总调用 | > 5% | sys_idempotency_record 写入速率 |
| 退款失败率 | < 0.1% | pos_refund status=4 速率 |
| Kafka / MQ 积压 | < 100 | Kafka lag 监控 |

**异常阈值触发 → 立即启动回滚流程 §1.4 / §2.3 / §3.2**

---

## 5. 上线 On-Call 轮值

| 周次 | 主联系人 | 备份 |
|---|---|---|
| 2026-W33 | 张 DBA | 李 DBA |
| 2026-W34 | 王 SRE | 赵 SRE |

每周一 09:00 换班，确认飞书/钉钉 on-call 群里有手机号。

---

## 6. 发布后 24 小时业务验证

按 `RELEASE-R2.0-上线包评审决议.md` §5.3 列表验证：
1. 退款失败率是否上升
2. 库存并发扣减有无出现负数
3. 幂等键命中率是否 > 5%
4. traceId 全链路是否完整
5. 任何 `BizCode.5xxx` 错误率

每项都要在飞书群里回报"✅ 正常"或具体异常值。

---

## 7. 变更日志

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-12 | R2.0-E T-E04 首次发布 | SRE TL |