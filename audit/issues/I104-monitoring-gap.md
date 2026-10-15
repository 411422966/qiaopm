# I104 — 监控采集侧空白（P0-B 未覆盖）

| 项 | 值 |
|---|---|
| **Issue ID** | I104 |
| **标题** | P0-B 监控采集侧空白，Prometheus / AlertManager / 告警规则均缺失 |
| **严重度** | **P0**（阻塞 Gate 3 prod 监控就绪） |
| **影响范围** | 无法观测 prod API 运行时状态 / 无告警通知 / 无法排查生产故障 |
| **发现批次** | Gate 3 批次 1.4b（2026-10-13 只读评估） |
| **修复批次** | Gate 3 批次 1.4b（本轮已修复） |
| **负责人** | DevOps 主值班 |
| **状态** | ✅ FIXED（批次 1.4b） |

---

## 1. 问题描述

### 1.1 现象

P0-A（批次 1.4a）已为 `meiyetec-api` 配置 Spring Boot Actuator + Micrometer Prometheus，端点 `/actuator/prometheus` 返回 JVM / HTTP / HikariCP 指标：

```
jvm_memory_used_bytes{area="heap",id="Eden Space"} ...
jvm_memory_used_bytes{area="heap",id="Survivor Space"} ...
jvm_memory_used_bytes{area="heap",id="Old Gen"} ...
http_server_requests_seconds_count{...} ...
hikaricp_connections_active{...} ...
```

**但没有任何组件去采集这些指标**，形成「暴露了但没人看」的僵尸端点。

### 1.2 根因

监控体系 P0-B（采集层）未与 P0-A（暴露层）同步建设，属于 Gate 3 批次 1.4 的规划遗漏。

### 1.3 影响范围

| 影响维度 | 描述 |
|---|---|
| **生产可观测性** | 无法观察 API 运行时状态（heap / QPS / 延迟 / 连接池） |
| **告警通知** | API 5xx / JVM OOM / MySQL DOWN 无任何通知 |
| **故障排查** | 无历史指标数据，无法做根因分析（Post-mortem） |
| **SLA 承诺** | 无法证明 API 可用性 > 99.9% |
| **Gate 3 阻塞** | prod 监控就绪是 Gate 3 验收的前置条件 |

---

## 2. 修复内容（本轮已交付）

### 2.1 交付物清单

| # | 路径 | 内容 |
|---|---|---|
| 1 | `infra/prometheus/prometheus.yml` | 全局配置 + meiyetec-api scrape + Prometheus self-scrape |
| 2 | `infra/prometheus/alerts/api-alerts.yml` | API 告警规则（容器 / HTTP / JVM / 连接池，5 类 11 条） |
| 3 | `infra/prometheus/alerts/db-alerts.yml` | DB/Cache 告警规则（MySQL / Redis / 磁盘，4 类 10 条） |
| 4 | `infra/alertmanager/alertmanager.yml` | AlertManager 路由 + webhook-log 接收者 + 抑制规则 |
| 5 | `docker-compose.monitoring.yml` | prometheus v2.55.0 + alertmanager v0.27.0 服务定义 |
| 6 | `docs/runbook/monitoring.md` | 监控 Runbook 骨架（启动 / 指标 / 响应 / FAQ / 扩展计划） |

### 2.2 关键设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 抓取频率 | 15s | 标准频率，足够观测 API 状态 |
| 告警评估 | 1m | 平衡敏感性与噪音 |
| 告警通道 | Webhook（打到 localhost:9095 占位） | 当前无告警接收服务，先打日志验证链路 |
| Grafana | 不含 | 留 P0-C（Grafana dashboard + 大盘） |
| 网络 | 复用 `meiyetec-net` | 与主 stack 网络互通，无需额外配置 |
| Prometheus 数据保留 | 15 天 | 平衡存储成本与历史分析需求 |

### 2.3 API 告警规则（api-alerts.yml）

| 告警名 | 条件 | severity |
|---|---|---|
| `ContainerRestartsHigh` | 10m 内重启 > 3 | warning |
| `ContainerDown` | up == 0 持续 1m | critical |
| `HttpServerErrorRateHigh` | 5xx 率 > 1% 持续 5m | critical |
| `HttpClientErrorRateHigh` | 4xx 率 > 10% 持续 5m | warning |
| `HttpServerLatencyP99High` | P99 > 3000ms 持续 5m | warning |
| `JvmHeapUsageHigh` | Heap > 80% 持续 5m | warning |
| `JvmHeapUsageCritical` | Heap > 90% 持续 2m | critical |
| `JvmThreadDeadlock` | BLOCKED 线程 > 0 持续 1m | critical |
| `JvmFullGcRateHigh` | 5m 内 Full GC > 3 | warning |
| `JvmMetaspaceUsageHigh` | Metaspace > 85% 持续 5m | warning |
| `HikariPoolConnectionPoolExhausted` | 池耗尽 pending > 0 持续 2m | warning |
| `HikariPoolConnectionWaitTimeHigh` | 连接超时 > 0 | warning |

### 2.4 DB/Cache 告警规则（db-alerts.yml）

| 告警名 | 条件 | severity |
|---|---|---|
| `MySqlDown` | mysql_up == 0 持续 1m | critical |
| `MySqlSlowQueriesHigh` | 慢查询 > 5 QPS 持续 5m | warning |
| `MySqlConnectionUsageHigh` | 连接率 > 80% 持续 5m | warning |
| `MySqlInnodbBufferPoolUsageHigh` | Buffer Pool > 85% 持续 10m | warning |
| `RedisDown` | redis_up == 0 持续 1m | critical |
| `RedisMemoryUsageHigh` | 内存 > 80% 持续 5m | warning |
| `RedisMemoryUsageCritical` | 内存 > 90% 持续 2m | critical |
| `RedisConnectionsRejected` | 拒绝连接 > 0 | critical |
| `RedisHitRateLow` | 命中率 < 60% 持续 10m | warning |
| `DiskUsageHigh` | 磁盘可用 < 20% 持续 5m | warning |
| `DockerDiskUsageHigh` | Docker 盘可用 < 20% | warning |

---

## 3. 未纳入范围

以下内容留 P0-C 补全：

| 项目 | 说明 | 优先级 |
|---|---|---|
| Grafana dashboard | API Overview / JVM / MySQL / Redis 四个 Panel | P0-C |
| 企业微信 webhook | alertmanager.yml 有注释模板，取消注释即可 | P0-C |
| SLO/SLA 监控 | P99 < 500ms，月可用性 > 99.9% | P0-C |
| Recording Rules | 预计算常用指标，减少 PromQL 复杂度 | P0-C |
| 值班升级路径 | warning → 30m 无响应升级 critical | P0-C |
| Loki 日志聚合 | Grafana Explore 查业务日志 | P0-D |

---

## 4. 验证方法

### 4.1 启动验证

```bash
# 启动监控栈
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d

# Prometheus Ready
curl http://localhost:9090/-/ready
# 期望：HTTP 200

# Prometheus targets
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'
# 期望：meiyetec-api = "up"

# Prometheus 规则加载
curl http://localhost:9090/api/v1/rules | jq '.data.groups[].name'
# 期望：meiyetec-api-availability / meiyetec-api-http / meiyetec-api-jvm / meiyetec-mysql / meiyetec-redis

# AlertManager Ready
curl http://localhost:9093/-/ready
# 期望：HTTP 200
```

### 4.2 指标抓取验证

```bash
# 从 Prometheus 容器内抓 meiyetec-api 指标
docker exec meiyetec-prometheus wget -qO- http://meiyetec-api:8080/actuator/prometheus | head -3
# 期望：jvm_memory_used_bytes ...

# 在 Prometheus UI 查询
# Graph 页面输入：jvm_memory_used_bytes{job="meiyetec-api",area="heap"}
# 期望：出现堆内存时间序列

# Graph 页面输入：rate(http_server_requests_seconds_count{job="meiyetec-api"}[5m])
# 期望：出现 HTTP 请求率时间序列
```

---

## 5. 关联文档

- `infra/prometheus/prometheus.yml` — Prometheus 配置
- `infra/prometheus/alerts/api-alerts.yml` — API 告警规则
- `infra/prometheus/alerts/db-alerts.yml` — DB/Cache 告警规则
- `infra/alertmanager/alertmanager.yml` — AlertManager 配置
- `docker-compose.monitoring.yml` — 监控服务编排
- `docs/runbook/monitoring.md` — 监控 Runbook
- `backend/src/main/resources/application.yml` — P0-A actuator 配置

---

## 6. 闭环记录

| 动作 | 日期 | 证据 |
|---|---|---|
| 发现 | 2026-10-12 凌晨 | 只读评估报告（I104 登记前） |
| I104 登记 | 2026-10-13 00:04 | 本文档创建 |
| 修复（首版） | 2026-10-13 | infra/prometheus/ + infra/alertmanager/ + docker-compose.monitoring.yml |
| 修复（v2.55/v0.27 兼容） | 2026-10-13 | commit `999319e` — 移除 storage.retention.time / div() / http_config.timeout |
| 修复（compose 实际网络 + flag） | 2026-10-13 | commit `9ec44d4` — meiyetec_meiyetec-net + 移除 web.enable-metrics-openapi / web.enable-lifecycle |
| 端到端验证 | 2026-10-13 00:46 | meiyetec-api target = up / 7 组 23 规则 / AlertManager OK / 6 容器 healthy |
| I104 状态 | ✅ VERIFIED（闭环） | 见 §3 / §4 完整验证清单 |
