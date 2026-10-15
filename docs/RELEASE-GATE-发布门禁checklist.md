# RELEASE-GATE-发布门禁checklist（R2.0-E T-E04）

> 适用范围：美业 SaaS 系统所有生产环境发布
> 配套：`.github/workflows/ci.yml`、`Run-All-Tests.ps1`、各模块四维状态表
> 原则：任何一项未通过 → 阻塞发布。例外需要 CTO/架构师书面授权。

---

## 1. 发布前 24 小时检查

### 1.1 代码质量

- [ ] 所有 PR 合并通过 `backend` CI job（单元测试 + BUILD）
- [ ] 所有 PR 合并通过 `frontend` CI job（TypeScript + Build）
- [ ] `Run-All-Tests.ps1` 输出 0 FAIL
- [ ] `./test-rbac-v2-a/b/c/d.ps1` 全部 0 FAIL
- [ ] 关键 Java 单元测试 `mvn -Dtest='BizCodeTest,RefundStatusTest' test` 全部通过

### 1.2 模块状态

- [ ] 涉及模块的 `implementationStatus` = ✅
- [ ] 涉及模块的 `apiTestStatus` = ✅
- [ ] 涉及模块的 `e2eStatus` = ✅
- [ ] 涉及模块的 `releaseStatus` = ✅
- [ ] 检查 `MODULE-STATUS-M01-M12-四维状态.md` 中无 `⚠️` 或 `⬜`

### 1.3 SQL 兼容性

- [ ] 所有新 SQL 脚本有 `IF NOT EXISTS` / `CREATE TABLE IF NOT EXISTS` 保护
- [ ] 向后兼容：不能删除旧字段（如 `pos_order.refunded_amount` 用 ADD COLUMN 添加）
- [ ] 新增字段允许 NULL，或提供默认值
- [ ] 在 staging 库预演 1 次，确认 5 分钟内执行完成

### 1.4 配置变更

- [ ] `application.yml` 改动已合并到 PR 并 review
- [ ] Key/Token/Secret 走环境变量，不入仓库
- [ ] 日志级别、监控告警、限流阈值更新
- [ ] 新增对外接口均已加 `@RequirePermission`

---

## 2. 发布窗口

### 2.1 时间窗口

- 业务低峰期：22:00 - 06:00（非必要不上班时间）
- 不在每周五 / 节假日前 1 天发布
- 重大变更（涉及支付/退款/库存）需双人 review

### 2.2 沟通

- [ ] 在 OA / 钉钉群发布预告（≥ 4 小时）
- [ ] 客服 / 运维 / DBA 已通知
- [ ] 上线联系人：__________ 备份：__________

---

## 3. 发布执行清单

### 3.1 数据库（DBA）

```sql
-- 1. 备份
mysqldump -h $DB_HOST -u $DB_USER -p$DB_PASS meiyetec > /backup/meiyetec_$(date +%Y%m%d_%H%M).sql

-- 2. 执行 SQL 脚本（按版本顺序）
USE meiyetec;
SOURCE /sql/init_rbac_v2_a.sql;
SOURCE /sql/init_rbac_v2_b.sql;
SOURCE /sql/init_rbac_v2_c.sql;
SOURCE /sql/init_rbac_v2_d.sql;
SOURCE /sql/init_rbac_v2_e.sql;

-- 3. 校验
SELECT 'pos_refund' tbl, COUNT(*) cnt FROM pos_refund
UNION ALL SELECT 'pos_refund_item', COUNT(*) FROM pos_refund_item
UNION ALL SELECT 'pos_reconciliation', COUNT(*) FROM pos_reconciliation
UNION ALL SELECT 'sys_idempotency_record', COUNT(*) FROM sys_idempotency_record
UNION ALL SELECT 'biz_service_material', COUNT(*) FROM biz_service_material
UNION ALL SELECT 'pos_order_consumption', COUNT(*) FROM pos_order_consumption;
```

- [ ] 备份完成，文件大小 > 0
- [ ] SQL 脚本全部执行成功
- [ ] 校验表行数 > 0

### 3.2 后端（SRE）

```bash
# 1. 拉镜像
docker pull meiyetec-api:1.0.0

# 2. 滚动升级（蓝绿）
docker-compose up -d --no-deps --scale meiyetec-api=2 meiyetec-api
# 等待新实例健康
sleep 30
curl -fsS http://localhost:8080/actuator/health

# 3. 切流量（按 10% / 50% / 100%）
# nginx upstream 调整
nginx -s reload
```

- [ ] 新实例启动成功
- [ ] `/actuator/health` 返回 UP
- [ ] 流量切到 100% 后老实例下线

### 3.3 前端（SRE）

```bash
# 1. 拉构建产物
aws s3 cp s3://meiyetec-web/build/ /var/www/meiyetec/web/ --recursive

# 2. 刷新 CDN
cdn-cli purge --domain meiyetec.com --path /web/

# 3. 健康检查
curl -fsS https://meiyetec.com/web/ | head -1
```

- [ ] CDN 缓存清理
- [ ] 主站首页 200 OK

---

## 4. 发布后 1 小时检查

### 4.1 业务验证

- [ ] 登录 → 拉菜单 → 进收银台 → 下单 → 支付 → 退款（happy path）
- [ ] 库存：入库 → 订单支付 → 自动扣减 → 退款冲正
- [ ] 补货建议：弹窗能列出商品
- [ ] CSV 导出：含租户水印 + 响应头 X-Tenant-Name
- [ ] 批量导入：同 Idempotency-Key 重放不重复

### 4.2 监控

- [ ] 看板误差率 < 0.5%
- [ ] 平均响应 < 500ms
- [ ] 错误日志无新 UnhandledException
- [ ] traceId 链路完整（任意接口 → 日志 → 响应均可追溯）
- [ ] MQ 队列堆积 < 100

### 4.3 回滚预案

```bash
# 后端回滚
docker-compose down
docker tag meiyetec-api:1.0.0 meiyetec-api:bad
docker pull meiyetec-api:0.9.9
docker-compose up -d

# 数据库回滚（仅紧急启用）
mysql -h $DB_HOST -u $DB_USER -p$DB_PASS meiyetec < /backup/meiyetec_20260808_2200.sql

# 前端回滚
aws s3 cp s3://meiyetec-web/build-prev/ /var/www/meiyetec/web/ --recursive
nginx -s reload
```

- [ ] 回滚脚本已写好并存档
- [ ] 演练过 1 次
- [ ] 灰度时长 ≥ 30 分钟，发现问题立即回滚

---

## 5. 例外授权

| 项 | 不通过原因 | 风险等级 | 授权人 |
|---|---|---|---|
| 单元测试部分跳过 | 已知遗留 bug | 中 | 后端 TL |
| E2E 部分未跑 | 跨日发布窗口不够 | 高 | SRE + QA 双签 |
| 回滚预案未演练 | 紧急热修 | 高 | CTO |
| SQL 字段删除 | 业务无依赖 | 中 | DBA + PD 双签 |

---

## 6. 签字

| 角色 | 姓名 | 签字 | 日期 |
|---|---|---|---|
| PD | — | — | — |
| 后端 TL | — | — | — |
| 前端 TL | — | — | — |
| QA | — | — | — |
| SRE | — | — | — |
| DBA | — | — | — |

---

## 7. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-08-08 | 初版（基于 R2.0-A~D 闭环） | R2.0-E |
