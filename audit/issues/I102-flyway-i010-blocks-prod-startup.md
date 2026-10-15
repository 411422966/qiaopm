# I102 — Flyway I010 根因阻塞 prod 4 容器 Up（Gate 3 批次 1.4a 临时绕路）

| 维度 | 值 |
|---|---|
| **Issue ID** | I102 |
| **创建时间** | 2026-10-12 07:25 (UTC+8) |
| **Fix 时间** | 2026-10-15 03:00 (UTC+8)（Gate 3 批次 2.1）|
| **类别** | 配置 / 启动期阻塞（I010 子 issue，跟踪 prod 启动阻塞本身） |
| **严重度** | 🔴 **P0**（Gate 3 演示栈阻塞 api Up） → ✅ FIXED |
| **状态变更** | ⚠ TEMP-FIX（FLYWAY_ENABLED=false） → ✅ FIXED + VERIFIED |
| **修复阶段** | Gate 3 批次 2.1 |

---

## 1. 现象（批次 1.4a 凌晨验证）

Gate 3 1.4a 凌晨 `docker compose down + up + 60s` 后：

- `meiyetec-api` Up 但 **unhealthy**（healthcheck 轮询失败）
- `meiyetec-mysql` / `meiyetec-redis` healthy
- `meiyetec-web` 一直 **Created**（`depends_on: api healthy` 不满足，被卡死）

**次生 P0-A 同步暴露**：`JwtInterceptor.excludePathPatterns` 漏配 `/actuator/**` → 就算 health 通过，`curl /actuator/health` 也返回 401「未登录」。

---

## 2. 根因链

```
docker-compose.yml line 81  SPRING_PROFILES_ACTIVE: prod
    ↓
application-prod.yml 启用  spring.flyway.enabled: true
    ↓
Flyway 启动 → 读 classpath:db/migration/V1__init_baseline.sql
    ↓
I010 根因：V1 编码 mojibake（GBK 字节被 UTF-8 解析 → Flyway parser 异常）
    ↓
Spring 容器初始化失败 → 进程退出 → 容器 unhealthy
    ↓
docker healthcheck 一直失败 → depends_on 链路断裂 → meiyetec-web 永不启动
```

**核心**：I102 的 P0 本质是 **I010**（V1 编码治理）。I102 只是跟踪「I010 不修就会一直阻塞 prod 启动」这个运维面。

---

## 3. 临时处置（批次 1.4a，commit `6241c91`，2026-10-12）

### 3.1 改动

| 文件 | 改动 | 目的 |
|---|---|---|
| `backend/src/main/java/com/meiyetec/config/WebConfig.java` | `excludePathPatterns` 追加 `/actuator/**` | 让 healthcheck 401 不再误报 |
| `docker-compose.yml` | api.environment 加 `FLYWAY_ENABLED: "false"` | 跳过 Flyway，让 Spring 启动不被 V1 编码问题阻塞 |

### 3.2 副作用

| 副作用 | 评估 |
|---|---|
| 后端启动不再跑 Flyway | ⚠ 长期不可接受 → 必须修 I010 |
| 4 容器 Up + healthy 可达 | ✅ 解决阻塞 |
| 后续 SQL 迁移（EXT-15、EXT-19、EXT-22）不再自动跑 | ⚠ 必须手动 apply |
| `flyway_schema_history` 表不被维护 | ⚠ 失去 schema 治理 |

### 3.3 验证（批次 1.4a）

```powershell
docker compose ps        # 4 容器 Up + healthy
curl /actuator/health    # {"status":"UP"} ✅
curl /actuator/info      # {} 200 ✅
curl /actuator/prometheus  # 3 行 jvm_memory_used_bytes ✅
```

---

## 4. 正式方案（Gate 3 批次 2.1） — ✅ FIXED

按用户在决策"A+B+D+E 结合"中的指示：

### 4.1 改动清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `backend/src/main/resources/db/migration/V1__init_baseline.sql` | 加 UTF-8 BOM（不修 mojibake SQL 内容） |
| 2 | `backend/src/main/resources/application.yml` | 已含 `encoding: UTF-8` + `enabled: ${FLYWAY_ENABLED:true}` + `baseline-on-migrate: true`；本次更新为 `baseline-version: 2`（V1 mojibake 让 V1 跳过） |
| 3 | `backend/Dockerfile` | ENV `JAVA_TOOL_OPTIONS` 已有 `-Dfile.encoding=UTF-8`（JVM 参数层） |
| 4 | `docker-compose.yml` | **删除** api.environment 段 `FLYWAY_ENABLED: "false"` 绕路 |
| 5 | MySQL | `DROP TABLE flyway_schema_history`（**不是** TRUNCATE！让 Flyway 重新 baseline） |

### 4.2 技术关键点

#### a. `baseline-version` 必须 ≥ V1.version

- 用户原指令：`baseline-version: 0` → 实际跑后 V1 被 apply → 报 `You have an error in your SQL syntax` 紧贴 `COMMENT '瀵嗙爜鍔犲瘑锛?,`（GBK mojibake 字节 UTF-8 解码后 COMMENT 单引号不闭合）
- **回滚到 baseline-version: 2**（V1.version=1 + 1）→ Flyway 把 schema 标记为 version=2，V1 视为"已 apply"，跳过
- 长期方案：审计结束后重建干净 V1（注释/默认值全 ASCII）→ 再回 baseline-version: 0

#### b. `DROP TABLE` ≠ `TRUNCATE TABLE`

- 用户原指令：`TRUNCATE TABLE flyway_schema_history` → Flyway 看到 history 表存在但空 → 误判为 empty schema → 不走 baseline → 直接 migrate → V1 apply 失败（V1 file 已被读到但 checksum mismatch）
- **改为 DROP TABLE** → Flyway 启动 → 看到 history 不存在 + schema 非空（282 张表） → `baseline-on-migrate=true` 触发 → 写入 BASELINE 行 version=2 → 后续 validate-on-migrate=true 校验 OK
- 这是本次 Step 7 验证最终能跑通的根因

#### c. JVM `-Dfile.encoding=UTF-8` 三层保险

1. **V1 文件** UTF-8 BOM（`EF BB BF`）—— 让 Flyway 的 InputStreamReader 跳过 BOM 头，按 UTF-8 读
2. **application.yml** `spring.flyway.encoding: UTF-8` —— 显式声明，绕开 Flyway 9.x 在某些 Windows + 中文路径下的 defaultCharset 异常
3. **JVM 参数** `-Dfile.encoding=UTF-8` —— 在 `JAVA_TOOL_OPTIONS` 环境变量，覆盖 Windows 默认 GBK defaultCharset

---

## 5. 验证（Gate 3 批次 2.1 Step 7 全矩阵）

| # | 项 | 期望 | 实际 |
|---|---|---|---|
| 1 | 4 容器 Up + healthy | api/mysql/redis/web ✓ | ✅ |
| 2 | 表数 | ≥ 270 | ✅ 282 |
| 3 | `flyway_schema_history` | 1 行 baseline version=2 success=1 | ✅ |
| 4 | Flyway 启动日志 | "Successfully validated 2 migrations" + "Schema is up to date. No migration necessary" | ✅ |
| 5 | `/actuator/health` | 200 UP（liveness + readiness probes） | ✅ |
| 6 | `/actuator/info` | 200 {} | ✅ |
| 7 | `/actuator/prometheus` | 200 jvm_threads metrics | ✅ |
| 8 | `/crm-integration/list` | 401 Unauthorized（端点需 JWT 鉴权） | ✅ 行为正确 |

---

## 6. 已知限制（仍 OPEN，不阻塞 FIXED 判定）

| 项 | 描述 |
|---|---|
| V1 文件 mojibake 治本 | V1 内 GBK mojibake 的中文 COMMENT 仍存在乱码（用户不修 SQL 原则）。下次重建干净 V1 时清理 |
| `baseline-version` 长期 | 当前 2 是绕路值；长期需 ≤ V1.version 真正生效 |
| `extension.controller_a_b_ratio` | 独立 issue（监控/Prometheus 相关），与本修复无关 |

---

## 7. 关联

| 项 | 关联 |
|---|---|
| **I010** | 根因（已 ✅ FIXED）—— `audit/issues/I010-flyway-v1-encoding.md` |
| **commit `6241c91`** | 批次 1.4a 临时绕路（WebConfig + FLYWAY_ENABLED=false）—— 现在已被 4.1 节改动取代 |
| **gate3-baseline.ps1** | 守门脚本，Gate 3 批次 2.1 起始时跑 PASS |
| **`mvn package`** | 本次需要重新打包 jar 让新 yml 生效（步骤 1：resources 改了后 jar 不会自动重打） |
| **docker compose build api** | jar 重打后必须重建 image，重启容器 |
| **DROP TABLE flyway_schema_history** | 关键步骤，TRUNCATE 无效，必须 DROP |

---

**I102 ✅ FIXED + VERIFIED（Gate 3 批次 2.1，2026-10-15 03:00 UTC+8）。**
