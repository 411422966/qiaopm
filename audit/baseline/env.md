# Baseline 环境清单

> 适用规则：`@project-audit.mdc` 规则 13（每次验证必须绑定 commit hash + DB snapshot + env.md）
> 创建时间：2026-09-12 20:50 (UTC+8)
> Phase：Phase -1 / Pre-flight

---

## 1. Git 信息

| 项 | 值 |
|---|---|
| 分支 | `main` |
| HEAD commit | `656caa72341c96d1b2e703dde993603dda9eebe1` |
| commit 短 SHA | `656caa7` |
| 仓库根 | `D:\QiaoPM` |
| 工作树状态 | dirty（详见 `git status`） |

> 说明：本仓库当前不是干净 working tree。大量 EXT-* 增量 + 营销模块改造在未提交状态
> （MktChannel / MktJourney / MktTemplate / MktTouchTask / MktConversion 控制器 + MarketActivity 下线 +
> CRM 同步改造 + ReportExportFileCleanup + 前端 crm-integration / ecommerce 视图修改）。
> **Baseline 锁定的是当前 HEAD 的代码状态，不等于"已上线版本"**。

---

## 2. 技术栈与端口

### 2.1 前端（`frontend/meiyetec-web`）

| 项 | 值 | 来源 |
|---|---|---|
| 框架 | Vue 3.5 | `package.json` → `"vue": "^3.5.40"` |
| 构建工具 | Vite 8 | `package.json` → `"vite": "^8.2.0"` |
| 语言 | TypeScript 6 | `package.json` → `"typescript": "~6.0.2"` |
| UI 库 | Element Plus 2.14 | `package.json` → `"element-plus": "^2.14.3"` |
| 状态管理 | Pinia 2.2 | `package.json` → `"pinia": "^2.2.0"` |
| 路由 | vue-router 4.6 | `package.json` → `"vue-router": "^4.6.4"` |
| 图表 | ECharts 5 | `package.json` → `"echarts": "^5.5.1"` |
| HTTP | axios 1.19 | `package.json` → `"axios": "^1.19.0"` |
| 自动按需引入 | unplugin-auto-import 21 | `vite.config.ts` |
| 测试 | vitest 2.1 | `package.json` |
| E2E | Playwright 1.62 | `package.json` |
| **端口** | **5173** | `vite.config.ts` → `server.port` |
| 后端代理 | `/api/*` → `http://localhost:8080`（去掉 /api 前缀） | `vite.config.ts` |

### 2.2 后端（`backend`）

| 项 | 值 | 来源 |
|---|---|---|
| 框架 | Spring Boot | `backend/pom.xml`（未读取，结构推断） |
| ORM | MyBatis-Plus | `application.yml` → `mybatis-plus.*` |
| DB 迁移 | Flyway（自动启动） | `application.yml` → `spring.flyway.*` |
| 软删除 | 字段 `deleted`（0/1） | `application.yml` → `mybatis-plus.global-config.db-config.logic-delete-field` |
| 缓存 | Spring Data Redis | `application.yml` → `spring.data.redis.*` |
| i18n | 自研 `BookingConfirmationTemplateResolver`（EXT-19） | `backend/src/main/resources/i18n/template/*.json` |
| **端口** | **8080** | `application.yml` → `server.port` |
| DCEP 模式 | dev（默认 mock；prod 需改 `app.dcep.mode=prod`） | `application.yml` |
| Fiscal 模式 | mock（默认；真实百望需 `fiscal.mode=baiwang`） | `application.yml` |
| JWT | 默认 dev 密钥（生产必须 `JWT_SECRET` 环境变量覆盖） | `application.yml` → `jwt.secret` |

> ⚠️ Spring Boot 具体版本号未在本表验证（需要读 `backend/pom.xml`）。
> Java 版本未在本表验证（需要读 `backend/pom.xml`）。

---

## 3. 数据库

| 项 | 值 | 来源 |
|---|---|---|
| 类型 | MySQL 8.0.46 | 本机 `mysqldump --version` |
| Host | `localhost` | `application.yml` |
| **端口** | **3307** | `application.yml` |
| **库名** | **meiyetec** | `application.yml` |
| 用户 | `root` | `application.yml` |
| 字符集 | `utf8mb4` | `application.yml` → `connectionCollation=utf8mb4_bin` |
| 时区 | `Asia/Shanghai` | `application.yml` → `serverTimezone` |
| Flyway | enabled（`FLYWAY_ENABLED=true`） | `application.yml` |
| Flyway 表 | `flyway_schema_history` | `application.yml` |
| baseline-on-migrate | true（防破坏历史表） | `application.yml` |

> ⚠️ **本机 mysqldump 已连通**（实测 exit 0，dump 417KB）。
> 实际库 schema 状态详见 `audit/baseline/db-snapshot.sql`（schema-only dump，不含业务数据）。

---

## 4. Redis

| 项 | 值 | 来源 |
|---|---|---|
| Host | `localhost` | `application.yml` |
| Port | `6379` | `application.yml` |
| Database | `0` | `application.yml` |
| Timeout | `3000ms` | `application.yml` |

> ⚠️ **本机未验证 Redis 是否真的运行**（无 redis-cli 检测步骤）。
> 按规则 13 要求，实际跑测试前需手动确认 Redis 可达。

---

## 5. 启动命令

### 5.1 数据库 / Redis（前置依赖）
```bash
# MySQL（Windows 服务，假设已通过服务管理器启动）
#   服务名通常为 "MySQL80" 或 "MySQL"
#   默认端口 3307（本仓库配置非默认 3306）

# Redis（需先安装并启动，默认端口 6379）
redis-server
```

### 5.2 后端启动
```bash
cd D:\QiaoPM\backend
mvn spring-boot:run
# 或
mvn clean package -DskipTests
java -jar target\meiyetec-api-*.jar
```

**启动校验**：
- 监听 `http://localhost:8080`
- Flyway 自动 baseline + 跑 `db/migration/V*.sql`
- 出现 `Started MeiyetecApplication in X seconds` → 成功

### 5.3 前端启动
```bash
cd D:\QiaoPM\frontend\meiyetec-web
npm install        # 首次
npm run dev        # 启动 dev server
```

**启动校验**：
- 监听 `http://localhost:5173`
- 所有 `/api/*` 请求经 Vite proxy 转发到 `http://localhost:8080`

### 5.4 E2E 测试
```bash
cd D:\QiaoPM\frontend\meiyetec-web
npm run e2e:install    # 首次安装 chromium
npm run e2e:ci         # chromium + mobile chrome 并行跑
```

---

## 6. 当前 git 状态摘要（dirty 内容）

详见根目录 `git status`。**已暂存 / 修改 / 删除**的关键路径：

### 新增（??）
- `backend/src/main/java/com/meiyetec/controller/MktChannelController.java`
- `backend/src/main/java/com/meiyetec/controller/MktConversionController.java`
- `backend/src/main/java/com/meiyetec/controller/MktJourneyController.java`
- `backend/src/main/java/com/meiyetec/controller/MktTemplateController.java`
- `backend/src/main/java/com/meiyetec/controller/MktTouchTaskController.java`
- `backend/src/main/java/com/meiyetec/service/ReportExportFileCleanupService.java`
- `backend/src/main/resources/db/sql/migrate_mkt_002_drop_market_activity.sql`
- `backend/src/main/resources/db/sql/migrate_mkt_002_rollback.sql`
- `frontend/meiyetec-web/e2e/crm-integration.spec.ts`
- `frontend/meiyetec-web/e2e/mh-routes.spec.ts`
- `docs/LIMITATIONS.md`
- `docs/ROLLBACK-6.2-mkt_ddl.md`
- `audit\README.md`（本批新增）
- `docs\PROJECT_AUDIT_PLAYBOOK.md`（本批新增）
- `docs\README.md`（本批新增）
- `.cursor/rules/feature-id.mdc`（本批新增）
- `.cursor/rules/project-audit.mdc`（本批新增）

### 修改（M）
- `backend/src/main/java/com/meiyetec/controller/MktEffectController.java`
- `backend/src/main/java/com/meiyetec/crm/CrmSyncScheduler.java`
- `backend/src/main/java/com/meiyetec/entity/EcommercePlatform.java`
- `backend/src/main/java/com/meiyetec/service/CrmIntegrationService.java`
- `backend/src/main/java/com/meiyetec/service/EcommerceService.java`
- `backend/src/main/java/com/meiyetec/service/MarketingAutomationService.java`
- `backend/src/main/java/com/meiyetec/service/ReportV2Service.java`
- `backend/src/main/java/com/meiyetec/service/impl/BusinessPolicyDispatchServiceImpl.java`
- `backend/src/test/java/com/meiyetec/service/MktEntityFieldsTest.java`
- `frontend/meiyetec-web/playwright.config.ts`
- `frontend/meiyetec-web/src/api/crm-integration.ts`
- `frontend/meiyetec-web/src/api/ecommerce.ts`
- `frontend/meiyetec-web/src/views/crm-integration/index.vue`
- `frontend/meiyetec-web/src/views/ecommerce/index.vue`

### 删除（D）
- `backend/src/main/java/com/meiyetec/controller/MarketActivityController.java`
- `backend/src/main/java/com/meiyetec/entity/MarketActivity.java`
- `backend/src/main/java/com/meiyetec/mapper/MarketActivityMapper.java`
- `backend/src/main/java/com/meiyetec/service/MarketActivityService.java`
- `backend/src/main/java/com/meiyetec/service/impl/MarketActivityServiceImpl.java`

### 目录级产物（不计入 git）
- `docs\任务执行清单（修正版）`、`项目开发完成度与最终验收体系（最终融合版）`、
  `在 Cursor 中落地这套验收体系的操作指南` — 项目管理层文档

---

## 7. 已验证 / 未验证 / 待补项

### ✅ 已验证（有运行证据）
- 本机 `mysqldump 8.0.46` 可执行，能连 `localhost:3307/meiyetec` 并导出 schema（417KB）
- 本机 `mysql 8.0.46` 客户端可用
- 本机 `git 2.53.0.windows.2` 可用，能跑 `git rev-parse HEAD`
- `audit/baseline/` 目录已存在（B1 已建）

### ⚠️ 未验证（按规则 13 应在 Phase 0 前补齐）
- Redis 是否真的在 `localhost:6379` 运行（无 redis-cli 探测）
- Spring Boot / Java / Maven 版本号（未读 `backend/pom.xml`）
- 前端 `npm install` 在本机是否能成功（依赖体积较大）
- 后端 `mvn spring-boot:run` 启动后 Flyway 是否一次过 baseline
- 当前脏 working tree 中的所有 M / ?? 是否能编译过

### 🛠 待用户确认
- DB snapshot 用 `--no-data`（schema-only）是否符合规则 13 的语义？
  如需 full snapshot（含业务数据），需明确说一声再补做。
- 是否要把 `audit/baseline/` 提交到 git？
- dirty working tree 是否要先 commit / stash 再开始 Phase 0？

---

## 8. 文件清单

| 文件 | 大小 | 用途 |
|---|---|---|
| `audit/baseline/README.md` | — | baseline 目录说明（B1） |
| `audit/baseline/commit.txt` | 41 B | `git rev-parse HEAD` 输出 |
| `audit/baseline/db-snapshot.sql` | 417149 B | mysqldump schema-only dump |
| `audit/baseline/env.md` | 本文件 | 环境清单 |
| `audit/baseline/preflight.md` | — | Pre-flight 检查记录（B2，已存在） |

---

> 维护说明：env.md 任何字段变化（端口、库名、启动命令）→ 必须同步更新本文件，
> 否则后续审计结论的可重现性无法保证（违反规则 13）。
