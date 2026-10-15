# 监控 Runbook — meiyetec-api

> Gate 3 批次 1.4b 建立，P0-B 采集侧覆盖 Prometheus + AlertManager。
> Grafana dashboard 留 P0-C 补全。

---

## §1 监控栈组件

### 1.1 组件一览

| 组件 | 版本 | 端口 | 用途 |
|---|---|---|---|
| Prometheus | v2.55.0 | 9090 | 指标采集 / 告警规则评估 / Web UI |
| AlertManager | v0.27.0 | 9093 | 告警去重 / 路由 / 通知分发 |

### 1.2 指标来源

| 目标 | metrics_path | 说明 |
|---|---|---|
| `meiyetec-api:8080` | `/actuator/prometheus` | JVM heap/GC/线程 + HTTP QPS/latency + HikariCP |
| Prometheus self | `/metrics` | Prometheus 自身状态 |

### 1.3 告警规则

| 文件 | 覆盖范围 |
|---|---|
| `infra/prometheus/alerts/api-alerts.yml` | 容器重启 / HTTP 5xx / P99 延迟 / JVM heap / GC / 死锁 |
| `infra/prometheus/alerts/db-alerts.yml` | MySQL down / 连接数 / 慢查询 / Redis down / 内存 / 命中率 |

---

## §2 启动 / 停止

### 2.1 启动监控栈

```bash
# 方式 1：与主 stack 一同启动（推荐）
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d

# 方式 2：仅启动监控栈（开发调试）
docker compose -f docker-compose.monitoring.yml up -d
```

### 2.2 停止监控栈

```bash
docker compose -f docker-compose.monitoring.yml down
# 数据卷保留（prometheus-data / alertmanager-data）

# 完全清除（谨慎）
docker compose -f docker-compose.monitoring.yml down -v
```

### 2.3 验证运行状态

```bash
# Prometheus Ready
curl http://localhost:9090/-/ready
# 期望：HTTP 200 + Prometheus is Ready.

# Prometheus targets 健康
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'
# 期望：全部 "up"

# AlertManager Ready
curl http://localhost:9093/-/ready
# 期望：HTTP 200

# AlertManager 告警状态
curl http://localhost:9093/api/v1/alerts | jq length
# 期望：>= 0（有告警时 > 0）
```

### 2.4 热重载配置

```bash
# Prometheus（无需重启，POST reload 信号）
curl -X POST http://localhost:9090/-/reload

# AlertManager
curl -X POST http://localhost:9093/-/reload
```

---

## §3 关键指标

### 3.1 API 可用性

| 指标 | 查询 | 正常范围 |
|---|---|---|
| 容器 Up | `up{job="meiyetec-api"}` | == 1 |
| HTTP 5xx 率 | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m]) * 100` | < 0.1% |
| HTTP P99 | `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket{job="meiyetec-api"}[5m])) * 1000` | < 1000ms |

### 3.2 JVM 健康

| 指标 | 查询 | 告警阈值 |
|---|---|---|
| Heap 使用率 | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100` | > 80% warning / > 90% critical |
| Full GC 频率 | `increase(jvm_gc_pause_seconds_count{action="full"}[5m])` | > 3 次 / 5m warning |
| BLOCKED 线程 | `jvm_threads_states_threads{state="BLOCKED"}` | > 0 critical |

### 3.3 数据库 / 缓存

| 指标 | 查询 | 告警阈值 |
|---|---|---|
| MySQL Up | `mysql_up` | == 0 → critical |
| MySQL 连接率 | `threads_connected / max_connections * 100` | > 80% warning |
| Redis Up | `redis_up` | == 0 → critical |
| Redis 内存率 | `redis_memory_used_bytes / redis_memory_max_bytes * 100` | > 80% warning |

---

## §4 告警响应

### 4.1 告警通道

| 通道 | receiver 名 | 当前状态 | 健康检查命令 |
|---|---|---|---|
| Webhook 占位 | `webhook-log` | 打到 `http://localhost:9095/alerts`（待接业务日志） | `curl http://localhost:9093/api/v2/receivers \| jq` |
| 群机器人（测试）| `wechat-webhook` | 打到 `http://httpbin:80/post`（echo-server，I107 hardcode）| `docker logs meiyetec-httpbin --tail 3` |
| 兜底 | `default` | 复用 `webhook-log` URL（接 critical 通知）| 同上 |
| Email | `email-placeholder` | alertmanager.yml 注释未启用 | 部署时取消注释 + 配 SMTP |
| 短信 / 电话 | — | 未配置 | Phase 7+ 按需 |

**通道健康检查步骤**：

```bash
# 1. AM receivers 列表
curl -sS http://localhost:9093/api/v2/receivers
# 期望：[{"name":"webhook-log"},{"name":"wechat-webhook"},{"name":"default"}]

# 2. 手动触发测试告警（amtool）
docker exec meiyetec-alertmanager amtool alert add \
  alertname=ManualTest severity=critical service=api \
  --alertmanager.url=http://localhost:9093

# 3. 验证通道收到（webhook-log 看 9095 业务服务日志 / wechat-webhook 看 httpbin 日志）
docker logs meiyetec-httpbin --tail 3
# 期望：POST /post body=XXX  total=N+1

# 4. 清理测试告警
curl -X POST http://localhost:9093/api/v2/silences \
  -H "Content-Type: application/json" \
  -d '{"matchers":[{"name":"alertname","value":"ManualTest","isRegex":false}],"createdBy":"cleanup","comment":"manual test cleanup","startsAt":"2026-10-14T00:00:00Z","endsAt":"2026-10-15T00:00:00Z"}'
```

### 4.2 值班流程（5 级响应）

| 级别 | 时限 | 动作 |
|---|---|---|
| **L1 ACK** | 5 min 内 | 收到告警 → 在 IM 群里回复「ACK <alertname>」 → 停止重复通知 |
| **L2 排查** | 15 min 内 | 查 Prometheus metrics + docker logs + 业务 traceId → 定位根因（应用 / 依赖 / 配置）|
| **L3 升级** | 30 min 无进展 | 升级到 L2 同事 / TL → 同步「现状 + 已尝试动作 + 卡点」|
| **L4 修复** | 视根因 | 应用重启 / 扩容 / 回滚 / 配置修复 / DBA 协助 → 持续到告警 resolved |
| **L5 复盘** | 24h 内 | 输出 Incident Report 到 `docs/runbook/incidents/YYYY-MM-DD-<alertname>.md` → 触发 Issue 登记（如有流程类 P3）|

### 4.3 升级路径

| 告警 severity | ACK 时限 | 无 ACK 升级对象 | 无修复升级对象 |
|---|---|---|---|
| `warning` | 30 min | L2 同事（IM 群 @ L2 标签）| L1（团队 lead，IM 群 @ TL）|
| `critical` | 5 min | L2 同事 + L1（同时 @ L2 + @ TL）| L1 + 产品 / 业务方（影响面涉及用户时）|

> **联系人占位**：参考 `docs/runbook/backup-restore.md §5`（联系人）；升级触发条件参考 `docs/runbook/rollback.md §9`（升级触发）。

**P0 / 用户影响**：
- 任一告警确认影响线上租户 → 立即在业务 IM 群「#incident」开 incident 频道
- 同步 devops 群 + product 群，每 15 min 一次进度更新
- 修复后 24h 内输出完整 incident report

### 4.4 SOP 模板（9 类常见告警）

每类告警的应急 SOP = 4 步：**判断影响面 → 快速止血 → 根因定位 → 后续跟踪**。

#### 4.4.1 `Http5xxRate`（HTTP 5xx 率 > 0.1%）

1. **影响面**：所有租户的 API 调用；HTTP 5xx = 用户可见错误
2. **快速止血**：
   - 看错误码分布：504（依赖超时）→ 拉起依赖；500（代码 bug）→ 考虑回滚
   - 若错误集中在某个 service path → 临时 disable 该 endpoint
3. **根因定位**：
   - Prometheus：`rate(http_server_requests_seconds_count{status=~"5.."}[5m])` 按 service / uri 分组
   - Logs：`docker compose logs backend --tail 200 \| grep ERROR`
   - Trace：业务侧 traceId
4. **后续跟踪**：输出 incident report，标 P0 时同步开 Issue

#### 4.4.2 `JvmHeapUsage`（heap > 80% / > 90%）

1. **影响面**：API 延迟升高 → 间接影响所有租户
2. **快速止血**：
   - 80% warning：观察 5 min，看 GC 后能否回收
   - 90% critical：`docker compose restart backend`（释放内存）
   - 临时加大 heap：修改 `JAVA_OPTS=-Xmx2g`（默认 1g）
3. **根因定位**：
   - Prometheus：`jvm_memory_used_bytes{area="heap",job="meiyetec-api"}` + GC 频率
   - jmap heap dump：`docker exec meiyetec-api jmap -dump:format=b,file=/tmp/heap.hprof 1` → 拉回分析
   - 看代码：最近是否有大对象 / 缓存未设上限
4. **后续跟踪**：分析 dump → 标 P1 时开 Issue 跟踪修复

#### 4.4.3 `ApiP99`（P99 延迟 > 1s）

1. **影响面**：用户感知的卡顿 / 超时
2. **快速止血**：
   - 看慢接口：`http_server_requests_seconds{quantile="0.99"}` 按 uri 分组
   - 若是 DB 慢查询 → 临时加索引 / DBA 协助
   - 若是下游服务慢 → 拉起依赖 / 启用熔断
3. **根因定位**：
   - Prometheus：DB 慢查询 metrics + Redis hit rate + 外部 API 延迟
   - Logs：traceId 关联的慢 trace
   - 业务：最近是否上线新功能 / 数据量暴涨
4. **后续跟踪**：性能分析报告，标 P2 时进 Post-Audit 三期

#### 4.4.4 `ContainerRestarts`（容器 5 min 内重启 ≥ 2 次）

1. **影响面**：服务暂时不可用 + 数据可能未持久化
2. **快速止血**：
   - 看重启原因：`docker inspect meiyetec-api \| grep -A 5 "RestartCount"`
   - OOM：增大内存 / 优化内存
   - 启动失败：回滚到上一个 commit
3. **根因定位**：
   - Logs：`docker logs meiyetec-api --tail 200`（看崩溃前最后一行）
   - 启动命令：`docker inspect meiyetec-api \| grep -A 10 "Cmd"`
   - 资源：`docker stats meiyetec-api --no-stream`（看启动时是否资源不足）
4. **后续跟踪**：必须开 Issue 跟踪（避免反复重启）

#### 4.4.5 `ThreadDeadlock`（JVM 死锁线程 > 0）

1. **影响面**：被锁线程不返回 → 接口超时 / 部分功能卡死
2. **快速止血**：
   - **`docker compose restart backend`**（死锁状态无法靠业务代码解）
   - 若是已知死锁代码路径 → 临时 disable 该 endpoint
3. **根因定位**：
   - 死锁前抓：`docker exec meiyetec-api jstack 1 \| grep -A 30 "Found .* deadlock"`
   - 看代码：两个 service 互相调用 / synchronized 嵌套
4. **后续跟踪**：**P0**（死锁通常有数据竞争）→ 必须开 Issue + 24h 修复

#### 4.4.6 `MysqlDown`（MySQL 不可达）

1. **影响面**：**所有**依赖 MySQL 的 API 不可用 = 全站瘫痪
2. **快速止血**：
   - `docker compose ps meiyetec-mysql`（看是否 up）
   - 容器 down：`docker compose up -d meiyetec-mysql`
   - 容器 up 但连接拒绝：看 `max_connections`（默认 151，可能耗尽）
3. **根因定位**：
   - Logs：`docker logs meiyetec-mysql --tail 100`（看是否有 OOM / crash）
   - 资源：`docker stats meiyetec-mysql --no-stream`
   - 慢查询：登录 MySQL `SHOW PROCESSLIST` 看是否有长事务锁表
4. **后续跟踪**：**P0** → 24h 内出 incident report

#### 4.4.7 `RedisDown`（Redis 不可达）

1. **影响面**：缓存层不可用，DB 压力激增（读穿透）→ 间接影响 API
2. **快速止血**：
   - `docker compose up -d meiyetec-redis`
   - 业务层应有「Redis 不可用降级到 DB」逻辑（看代码确认）
3. **根因定位**：
   - Logs：`docker logs meiyetec-redis --tail 50`
   - 内存：`docker stats meiyetec-redis --no-stream`（是否 OOM）
   - 持久化：是否 RDB 写盘阻塞（看 `latency` 部分）
4. **后续跟踪**：通常 P1，但持续 > 30 min 升级 P0

#### 4.4.8 `HikariPoolUsage`（连接池使用率 > 80%）

1. **影响面**：新连接获取慢 → API 整体延迟上升
2. **快速止血**：
   - 看是否有慢 SQL 持有连接：`SELECT * FROM information_schema.PROCESSLIST WHERE COMMAND != "Sleep" AND TIME > 5`
   - kill 慢查询：`KILL <id>`
3. **根因定位**：
   - Prometheus：`hikaricp_connections_usage_seconds` + `hikaricp_connections_acquire_seconds`
   - 应用日志：看是否有连接泄漏（acquire 不 release）
4. **后续跟踪**：连接泄漏 → **P1** 必须修复

#### 4.4.9 `RedisMemoryUsage`（Redis 内存 > 80%）

1. **影响面**：Redis 触发 maxmemory-policy 驱逐 → 命中率下降 → DB 压力
2. **快速止血**：
   - `docker exec meiyetec-redis redis-cli INFO memory`（看 detail）
   - 临时扩容：修改 `maxmemory 2gb`（compose restart）
3. **根因定位**：
   - 看大 key：`redis-cli --bigkeys`
   - 看 key 分布：`redis-cli INFO keyspace`
   - 应用：是否有批量写入未分批
4. **后续跟踪**：标 P2 进 Post-Audit 三期

---

## §5 常见问题

### Q1：Prometheus target 显示 DOWN

```bash
# 检查 meiyetec-api 是否运行
docker compose ps meiyetec-api

# 检查 actuator 端点
curl http://localhost:8080/actuator/prometheus | head -5

# 检查网络连通性（从 prometheus 容器内）
docker exec meiyetec-prometheus wget -qO- http://meiyetec-api:8080/actuator/prometheus | head -3
```

### Q2：AlertManager 告警未发出

```bash
# 检查 AlertManager 日志
docker compose logs -f alertmanager

# 检查 AlertManager 配置是否正确加载
curl http://localhost:9093/api/v1/status/config | jq .data

# 检查静默规则（silence）是否覆盖了告警
curl http://localhost:9093/api/v1/silences | jq length
```

### Q3：Prometheus OOM（内存不足）

```bash
# Prometheus 内存使用
docker stats meiyetec-prometheus --no-stream

# 减少保留时间或缩小 scrape 范围
# 参考：prometheus.yml 中 storage.retention.time
```

### Q4：如何导出 Prometheus 数据做二次分析

```bash
# 快照 API（保留当前 TSDB 快照供导出）
curl -X POST http://localhost:9090/api/v1/admin/tsdb/snapshot

# 查询结果导出为 CSV（用 promtool）
docker exec meiyetec-prometheus promtool query instant \
  'jvm_memory_used_bytes{job="meiyetec-api"}' > metrics.csv
```

---

## §6 扩展计划

- [x] P0-C：企业微信 webhook 通道 — **批次 2.2 已实现**（hardcode http://httpbin:80/post，I107 待修）+ 验证通过（httpbin 收到 2 POST）
- [ ] P0-C：Grafana dashboard（API Overview / JVM / MySQL / Redis 四个 Panel）
- [x] P0-C：值班告警升级路径 — **§4.3 已落地**（warning 30m / critical 5m）
- [ ] P0-C：SLO/SLA 监控（P99 < 500ms，月可用性 > 99.9%）
- [ ] P0-C：录制规则（Recording Rules）预计算常用指标
- [ ] P0-D：Loki 日志聚合（Grafana Explore 查业务日志）

---

## 参考资料

- Prometheus 配置：`infra/prometheus/prometheus.yml`
- API 告警规则：`infra/prometheus/alerts/api-alerts.yml`
- DB 告警规则：`infra/prometheus/alerts/db-alerts.yml`
- AlertManager 配置：`infra/alertmanager/alertmanager.yml`
- 启动编排：`docker-compose.monitoring.yml`
