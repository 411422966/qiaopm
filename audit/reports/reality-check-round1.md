> ⚠️ **本报告结论更正（2026-10-18 复核）**
>
> 本报告由 Cursor 生成于 2026-10-18 0:35，其中以下条目已复核证伪，**不采信**：
>
> | 条目 | 本报告原结论 | 复核实测 | 复核方法 |
> |---|---|---|---|
> | 行 53-54, 64-71, 301-306, 380 | `typescript ~6.0.2` / `vite ^8.2.0` / `vue-tsc ^3.3.8` 「疑似不存在」 | **真实存在，已安装**：typescript 6.0.3 / vite 8.2.0 | `Get-Content node_modules\typescript\package.json` |
> | 行 99, 375 | 「总 endpoint 970」 | **988**（严格行首注解，Controller 内） | `Select-String '^\s*@(Get\|Post\|Put\|Delete\|Patch)Mapping'` |
> | 行 12 | 「Docker 未启动，无法验证 DB」 | **已启动并实测**：282 张表 | `docker exec meiyetec-mysql mysql ... information_schema` |
>
> **本报告可采信部分**：Controller / Service / Entity / Mapper / 前端文件数统计、`db/migration` 仅 1 个 V1 的事实、`db/sql` 170 个脚本的事实、F001-F140 编号跳跃的发现、`CURRENT_STATE.md` 数量级漂移的发现。
>
> **本报告已派生的正式登记**：`audit/issues/I118-missing-ddl-132-tables.md`（132 张表缺失）
>
> **正式更正记录**：`PROJECT_ACCEPTANCE_EVIDENCE.md` 顶部「结论更正（2026-10-18）」
>
> ---
# Reality Check Round 1 — 项目现状盘点（只读）

| 属性 | 值 |
|---|---|
| **项目** | 美业SaaS（meiyetec） |
| **盘点日期** | 2026-10-18 |
| **盘点人** | 代码审计员（Cursor） |
| **盘点范围** | 后端代码 + 前端代码 + DB 脚本 + evidence 目录 + issue register + 文档声称 |
| **盘点方法** | PowerShell 文件系统扫描 + 文档交叉对比。**不**修改任何文件、不运行后端、不连真实 DB |
| **运行环境状态** | ⚠️ Docker Desktop 进程未启动（`docker ps` exit=1，pipe `dockerDesktopLinuxEngine` 不存在）→ 无法 `docker exec meiyetec-mysql` 查真实表数 |
| **结论基调** | 多份"声明文档"（`CURRENT_STATE.md` / `PROJECT_ACCEPTANCE_EVIDENCE.md`）与当前代码状态存在显著漂移；DB 真实状态无法独立验证 |

---

## 0. 盘点方式与限制

- 仅依赖当前工作区快照（`D:\QiaoPM`），不连接运行中的服务
- 命令全部为 PowerShell `Get-ChildItem / Select-String / Get-Content` 等只读操作
- 输出文件中的字节计数、行号、文件名均为工具直接读出，未做二次加工
- 涉及"Docker 内 DB"的真实数据**无法验证**，相关结论标 🔒 待运行时核验

---

## 1. 技术栈（从 pom.xml / package.json / docker-compose.yml / Dockerfile）

| 层 | 技术 | 版本 | 证据 |
|---|---|---|---|
| 后端框架 | Spring Boot | **3.1.5** | `backend/pom.xml:11` `spring-boot-starter-parent` |
| 后端语言 | Java | **17** | `backend/pom.xml:18` `<java.version>17</java.version>` |
| 后端 ORM | MyBatis-Plus | **3.5.5** | `backend/pom.xml:21` `<mybatis-plus.version>3.5.5</mybatis-plus.version>` |
| 后端构建 | Maven | 继承自 parent（3.9.x） | `backend/pom.xml:11` |
| 后端 JWT | jjwt | **0.12.3** | `backend/pom.xml:22` `<jjwt.version>0.12.3</jjwt.version>` |
| 后端国密 | BouncyCastle（SM2/SM3/SM4） | **1.78.1** | `backend/pom.xml:151` `<artifactId>bcprov-jdk18on</artifactId><version>1.78.1</version>` |
| 后端支付 | 微信支付 V3 SDK | **0.2.17** | `backend/pom.xml:181` `<version>0.2.17</version>` |
| 后端支付 | 支付宝 SDK | **4.40.984.ALL** | `backend/pom.xml:189` `<version>4.40.984.ALL</version>` |
| 后端 MQTT | HiveMQ Client | **1.3.3** | `backend/pom.xml:135` `<version>1.3.3</version>` |
| 后端插件 | pf4j | **3.10.0** | `backend/pom.xml:142` `<version>3.10.0</version>` |
| 后端密码 | spring-security-crypto | **6.2.0** | `backend/pom.xml:138` `<version>6.2.0</version>` |
| 后端 Excel | Apache POI | **5.2.5** | `backend/pom.xml:130` `<version>5.2.5</version>` |
| 后端监控 | Spring Boot Actuator + Micrometer Prometheus | 跟随 Spring Boot BOM 3.1.5 | `backend/pom.xml:158-166` |
| 后端 Feature Flag | LaunchDarkly SDK | **7.16.0** | `backend/pom.xml:171` `<version>7.16.0</version>` |
| 后端邮件 | spring-boot-starter-mail | 跟随 Spring Boot BOM | `backend/pom.xml:145-148` |
| 后端迁移 | Flyway core + flyway-mysql | 跟随 Spring Boot BOM | `backend/pom.xml:194-205` |
| 后端运行时镜像 | Eclipse Temurin | **17-jre-alpine** | `backend/Dockerfile:36` `FROM eclipse-temurin:17-jre-alpine AS runtime` |
| 前端框架 | Vue | **3.5.40** | `frontend/meiyetec-web/package.json:18` `"vue": "^3.5.40"` |
| 前端路由 | vue-router | **4.6.4** | `frontend/meiyetec-web/package.json:23` `"vue-router": "^4.6.4"` |
| 前端状态 | pinia | **2.2.0** | `frontend/meiyetec-web/package.json:20` `"pinia": "^2.2.0"` |
| 前端 UI | element-plus | **2.14.3** | `frontend/meiyetec-web/package.json:17` `"element-plus": "^2.14.3"` |
| 前端图表 | echarts | **5.5.1** | `frontend/meiyetec-web/package.json:16` `"echarts": "^5.5.1"` |
| 前端 HTTP | axios | **1.19.0** | `frontend/meiyetec-web/package.json:15` `"axios": "^1.19.0"` |
| 前端进度条 | nprogress | **0.2.0** | `frontend/meiyetec-web/package.json:19` |
| 前端图标 | @element-plus/icons-vue | **2.3.2** | `frontend/meiyetec-web/package.json:14` |
| 前端构建 | Vite | **^8.2.0** ⚠️ ❌ 已证伪见顶部更正 | `frontend/meiyetec-web/package.json:38` |
| 前端 TS | TypeScript | **~6.0.2** ⚠️ ❌ 已证伪见顶部更正 | `frontend/meiyetec-web/package.json:34` |
| 前端 TS 检查 | vue-tsc | **^3.3.8** ⚠️ | `frontend/meiyetec-web/package.json:42` |
| 前端自动导入 | unplugin-auto-import | **^21.0.0** ⚠️ | `frontend/meiyetec-web/package.json:35` |
| 前端单元测试 | vitest | **2.1.9** | `frontend/meiyetec-web/package.json:40` |
| 前端 E2E | @playwright/test | **1.62.1** | `frontend/meiyetec-web/package.json:26` |
| 前端构建镜像 | Node | **20-alpine** | `frontend/meiyetec-web/Dockerfile:18` `FROM node:20-alpine AS builder` |
| 前端运行时镜像 | Nginx | **1.27-alpine** | `frontend/meiyetec-web/Dockerfile:35` `FROM nginx:1.27-alpine AS runtime` |
| 数据库 | MySQL | **8.0** | `docker-compose.yml:31` `image: mysql:8.0` |
| 缓存 | Redis | **7-alpine** | `docker-compose.yml:65` `image: redis:7-alpine` |

### 1.1 ⚠️ 待运行时核验（疑似不存在的版本号）

下列版本号当前时间点（2026-10）**在公开包注册表中查无对应版本**（按本会话的检索范围），无法仅靠 package.json 静态确认可安装：

| 包 | 声明版本 | 疑点 |
|---|---|---|
| `typescript` | `~6.0.2` | TypeScript 主版本到 2026-10 仍为 5.x；6.x 未必 GA |
| `vite` | `^8.2.0` | Vite 到 2026-10 GA 系列最大约 7.x |
| `vue-tsc` | `^3.3.8` | vue-tsc 2.x 系列最新；3.x 不确定 |
| `unplugin-auto-import` | `^21.0.0` | 该包主版本号一般远低于此 |

> 推断：要么环境是 2026+ 之后并已发版，要么这些版本号被人工填入做占位/演练。`PROJECT_ACCEPTANCE_EVIDENCE.md` §1.1 直接抄录了这些版本作为"前端构建成功"的证据，但 `npm run build` 当前**未在本会话中实际执行**，无法独立验证可安装性。

---

## 2. 后端规模

### 2.1 数字统计（PowerShell 实测）

| 指标 | 数值 | 证据命令 |
|---|---|---|
| **总 Java 源文件**（不含 `.disabled`） | **1543** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java -Recurse` |
| **`controller/` 顶层 *.java** | **135** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/controller` |
| **`service/` 顶层 *.java**（不含 `.disabled`） | **214** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/service` |
| **`service/` 顶层 `.disabled`** | **2** | `Get-ChildItem -File -Filter *.disabled -Path backend/src/main/java/com/meiyetec/service` |
| **`service/impl/` 顶层 *.java** | **96** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/service/impl` |
| **`entity/` 顶层 *.java** | **445** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/entity` |
| **`mapper/` 顶层 *.java** | **441** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/mapper` |
| **`backend/src/test/` 顶层 *.java** | **123** | `Get-ChildItem -File -Recurse -Filter *.java -Path backend/src/test` |
| **Mapper XML 文件** | **5** | `Get-ChildItem -File -Filter *.xml -Path backend/src/main/resources/mapper -Recurse` |
| **`@RestController` 注解数** | **137** | `Select-String -Path controller/*.java -Pattern '@RestController'` |
| **`@GetMapping` 注解数** | **460** | `Select-String -Path controller/*.java -Pattern '@GetMapping'` |
| **`@PostMapping` 注解数** | **351** | 同上 |
| **`@PutMapping` 注解数** | **94** | 同上 |
| **`@DeleteMapping` 注解数** | **65** | 同上 |
| **总 endpoint 注解** | **970** | 四类合计 |

### 2.2 命令原文（可复跑）

```powershell
# 后端 Java 总数
Get-ChildItem -File -Filter *.java -Path backend/src/main/java -Recurse | Measure-Object

# 各层级（数字见上表）
Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/controller
Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/service     | Where-Object { -not $_.Name.EndsWith('.disabled') }
Get-ChildItem -File -Filter *.disabled -Path backend/src/main/java/com/meiyetec/service
Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/service/impl
Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/entity
Get-ChildItem -File -Filter *.java -Path backend/src/main/java/com/meiyetec/mapper
Get-ChildItem -File -Recurse -Filter *.java -Path backend/src/test

# 注解
Select-String -Path backend/src/main/java/com/meiyetec/controller/*.java -Pattern '@RestController'
Select-String -Path backend/src/main/java/com/meiyetec/controller/*.java -Pattern '@GetMapping'
Select-String -Path backend/src/main/java/com/meiyetec/controller/*.java -Pattern '@PostMapping'
Select-String -Path backend/src/main/java/com/meiyetec/controller/*.java -Pattern '@PutMapping'
Select-String -Path backend/src/main/java/com/meiyetec/controller/*.java -Pattern '@DeleteMapping'
```

### 2.3 异常点（不属于本节"正常数"，但必须列出）

- **`service/` 顶层 214 个 + `service/impl/` 顶层 96 个 = 310 个 .java** — 但 controller 数量只有 135。Service 文件数比 Controller 多 **175 个**。按"controller 调 service"的标准架构，多出来的 service 至少包含：抽象接口类（实现分离）、`gateway/`、`channel/` 子包、专用 util/handler 类等。**未做更细分类**。
- **`@RestController` 注解 137 个**，controller 文件 135 个 → 差 2。可能 2 个文件被多次匹配（包含类 + 内部类 / 多注解），或某类被两个文件分拆。
- **`entity/` 445 vs `mapper/` 441** — 差 4 个 entity 缺对应 mapper。可能原因：entity 是聚合根（如 `AggregateRoot`）、DTO 中混入 entity 目录、或 mapper 缺失。
- **`service/` 下有 2 个 `.disabled` 文件**：
    - `P3EcosystemService.java.disabled`（28354 字节，与同名启用版等大）
    - 与之相关的另一份禁用备份
- **`stock/StockController.java` 111397 字节**、其他多个 30KB+ 的"超大 Controller"（`PosController 57765` / `CrossStoreController 47364` / `CommissionController 45812` / `PackageController 76452` / `ReportController 54659`）—— 体量异常，单 Controller 含 50+ endpoint 风险。
- **`.gitignore`**：项目根有 `.gitignore` 文件，但 `git status` 命令（git_status 系统块）显示 `main...origin/main`，**无 dirty 提示**——工作区与 HEAD 干净，但 audit/issues/I109 标记 "Git objects/30 NTFS 损坏" 仍在 Mitigated 状态。

---

## 3. 前端规模

### 3.1 数字统计（PowerShell 实测）

| 指标 | 数值 | 证据命令 |
|---|---|---|
| **`src/views/` 子目录数** | **52** | `Get-ChildItem -Directory -Path frontend/meiyetec-web/src/views` |
| **`src/views/**/*.vue` 文件数** | **171** | `Get-ChildItem -File -Filter *.vue -Path frontend/meiyetec-web/src/views -Recurse` |
| **`src/api/*.ts` 接口文件数** | **62** | `Get-ChildItem -File -Filter *.ts -Path frontend/meiyetec-web/src/api` |
| **`e2e/*.spec.ts` 总数（含 audit/ 子目录）** | **31** | `Get-ChildItem -File -Filter *.spec.ts -Path frontend/meiyetec-web/e2e -Recurse` |
| **`e2e/*.spec.ts`（不含 audit/ 子目录）** | **21** | `Get-ChildItem -File -Filter *.spec.ts -Path frontend/meiyetec-web/e2e -Recurse` 排除 `*\audit\*` |
| **`audit/scripts/e2e/*.spec.ts`** | **7** | `Get-ChildItem -File -Filter *.spec.ts -Path audit/scripts/e2e -Recurse` |
| **`dependencies` 数量** | **8** | `package.json` `dependencies` 字段 |
| **`devDependencies` 数量** | **11** | `package.json` `devDependencies` 字段 |

### 3.2 命令原文

```powershell
Get-ChildItem -Directory -Path frontend/meiyetec-web/src/views | Measure-Object
Get-ChildItem -File -Filter *.vue -Path frontend/meiyetec-web/src/views -Recurse | Measure-Object
Get-ChildItem -File -Filter *.ts -Path frontend/meiyetec-web/src/api | Measure-Object
Get-ChildItem -File -Filter *.spec.ts -Path frontend/meiyetec-web/e2e -Recurse | Measure-Object
Get-Content frontend/meiyetec-web/package.json -Raw | ConvertFrom-Json
```

### 3.3 异常点

- **`package.json` 的 deps/devDeps 数量**：
  - `dependencies` 8 个：`@element-plus/icons-vue / axios / echarts / element-plus / nprogress / pinia / vue / vue-router`
  - `devDependencies` 11 个：`@playwright/test / @types/node / @vitejs/plugin-vue / @vue/test-utils / @vue/tsconfig / jsdom / typescript / unplugin-auto-import / vite / vitest / vue-tsc`
  - 缺少 **`vue-i18n`**（多语言预约确认 `EXT-19` 涉及 6 个 locale 资源，但前端无 i18n 库声明）—— 后端模板走 `BookingConfirmationTemplateResolver`，前端未消费 i18n key。
- **`e2e/` 目录存在**，但 `package.json` 里 `e2e` scripts 全部依赖 playwright —— `playwright.config.ts` 存在（已确认），但本会话未实际运行。31 个 spec 中 21 个在 e2e 顶层、10 个在 `e2e/audit/` 子目录。

---

## 4. 数据库

### 4.1 Flyway 迁移目录

| 指标 | 数值 | 证据 |
|---|---|---|
| **`db/migration/V*.sql` 文件数** | **1**（`V1__init_baseline.sql`，53645 字节） | `Get-ChildItem -File -Filter 'V*.sql' -Path backend/src/main/resources/db/migration` |
| **临时文件** | `V1__init_baseline.sql.tmp`（53642 字节） | 同目录 |
| **实际 Flyway 版本** | V1（`__init_baseline`） | 文件名 |
| **application.yml 配置** | `baseline-version: 2` `baseline-on-migrate: true` | `backend/src/main/resources/application.yml:23-25` |

> ⚠️ **应用配置 `baseline-version: 2` + 文档自承**：`application.yml` 的注释（line 26-29）明确写：
>
> ```
> baseline-description: "pre-flyway schema (legacy init_*/migrate_* scripts, manually applied;
>                         V1 file is mojibake GBK, must skip via baseline-version>V1.version)"
> ```
>
> 含义：DB schema 不是由 Flyway 创建，而是通过 `db/sql/` 下的 init/migrate 脚本手动 apply。V1 文件本身是 GBK 编码乱码，必须跳过（baseline-version=2 > V1.version=1）。

### 4.2 db/sql 一次性初始化脚本

| 指标 | 数值 | 证据 |
|---|---|---|
| **`db/sql/*.sql` 总数** | **170** | `Get-ChildItem -File -Filter '*.sql' -Path backend/src/main/resources/db/sql` |
| **CREATE TABLE 关键字出现次数（所有脚本合计，含重复）** | **461** | 遍历 170 个文件 `Select-String 'CREATE TABLE'` |
| **DROP TABLE 关键字出现次数** | **340** | 同上 `Select-String 'DROP TABLE'` |

> 文件名遵循命名空间约定：`init_m<NN>_*` / `init_m<module>_*` / `migrate_*` / `seed_*` / `verify_*` / `patch_*` / `fix_*`。
>
> ⚠️ CREATE TABLE 提及次数 461 次**不等于** 461 张表——同表名可能被多个 init/migrate 脚本重复定义（`CREATE TABLE IF NOT EXISTS`）。**真实表数 = 唯一表名数量**，本会话未做正则解析（PowerShell 的 `[regex]::Matches` 对 utf8mb4 列名易踩坑），留待 SQL 实测核验。

### 4.3 🔒 真实表数（Docker Desktop 未启动，无法 docker exec）

```powershell
docker exec meiyetec-mysql mysql -uroot -proot123456 meiyetec \
  -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='meiyetec';"
```

**执行结果**：

```
docker : failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine;
check if the path is correct and if the daemon is running:
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

> Docker Desktop 进程未运行（pipe `dockerDesktopLinuxEngine` 不存在，`docker ps` exit=1）。
> **真实表数无法独立核验**。仅能依据历史快照：
> - `tests/all_tables.txt`（**时间戳 2026/08/21 05:47:38**，7292 字节）—— 包含 188 个唯一表名（`Select-String -Pattern '^[a-z_][a-z0-9_]*$' | Sort-Object -Unique`）
> - 该快照是 **2 个月前**的产物，且 `tests/` 目录混有 `_probe.ps1 / _dbg.ps1 / smoke*.log` 等调试残留

### 4.4 migration 声称 vs 真实表的差异（无法独立核验，列出方法）

| 维度 | 文档/文件声称 | 实际观察 | 差异 |
|---|---|---|---|
| Flyway V* 脚本数 | 多版本（historical V1...V100+） | **仅 V1__init_baseline.sql 1 个** | 历史 V* 被合并到 V1 baseline；详见 `application.yml:26-29` 自承 |
| init/migrate 脚本数 | 文档无明确数字 | **170** 个 | 需对照 entity 类数（445）核查表覆盖 |
| 真实表数 | 无明确数字 | **🔒 不可知**（docker 未启动） | 与 188 个 entity 类的对应关系待运行时核验 |

---

## 5. 证据覆盖

### 5.1 数字统计

| 指标 | 数值 | 证据命令 |
|---|---|---|
| **`audit/evidence/F\d{3}-*` 目录数（含 audit/ 聚合目录）** | **144** | `Get-ChildItem -Directory -Path audit/evidence` |
| **唯一 F 编号数** | **118** | 解析目录名前缀 `F(\d+)` 后去重 |
| **F 编号范围** | **F001 – F140** | — |
| **缺失编号** | F034, F037, F041, F044, F081-F083, F102-F107, F111 之后 F113+ | 跳跃点分析 |
| **`audit/evidence/F\d{3}-*` 中独立 spec/聚合目录** | 144 | 实际目录数 |
| **`docs/governance/feature_inventory.tsv` 功能行数** | **25** | 文件内容 |

### 5.2 F 编号跳跃点（按数字升序扫描后断点）

```
F033 -> F035   （跳 F034）
F036 -> F038   （跳 F037）
F080 -> F084   （跳 F081 / F082 / F083）
F084 -> F088   （跳 F085 / F086 / F087）
F090 -> F092   （跳 F091）
F092 -> F095   （跳 F093 / F094）
F101 -> F108   （跳 F102 / F103 / F104 / F105 / F106 / F107）
F108 -> F114   （跳 F109 / F110 / F111 / F112 / F113）
```

> 部分缺失编号对应 `audit/issues/I*.md` 已知的 issue（如 I103 = F103 缺失 → PluginRuntimeTest 编译失败，issue-register.md 标 Open）。

### 5.3 feature_inventory.tsv vs evidence F 编号的命名空间冲突

| 维度 | feature_inventory.tsv | audit/evidence |
|---|---|---|
| **数量** | 25 个功能（P0-01 ~ P3-25） | 144 个目录（118 个唯一 F 编号） |
| **粒度** | "一个业务能力 = 一个 Feature" | "一个验收点 = 一个 F" |
| **对应关系** | feature_inventory.tsv 没说哪个 F 对应哪个 P | 无映射表，**完全独立** |
| **现实评分脚本如何对接** | `reality_score.sh` 第 6 行注释 `FEATURE_COMPARISON_MATRIX.md` 抽取 → 该文件**不存在** | 评分脚本无法直接对 F 编号打分 |

**结论**：两套编号体系**无映射关系**。覆盖率 = 不可比。
- 强行对应：25 个 P0-P3 Feature × 4 层（controller/service/mapper/entity）= 理论 100 锚点 vs 144 F 目录 = 重叠度待量化
- 实际上 evidence 目录按"EXT 批次 + 验证点"切分（如 EXT-23 plugin runtime 有 F031~F055 共 25 个 F），颗粒度比 feature_inventory 细 5.76 倍

---

## 6. 矛盾清单（最重要）

> 列出"文档/文件声称"与"代码/文件系统实际"的差异。**不调和、不解释**。

### 6.1 数量级矛盾 — 文档严重低估

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 1 | `CURRENT_STATE.md:74` | "Java 源文件 409" | **1543** | `Get-ChildItem -File -Filter *.java -Path backend/src/main/java -Recurse` 计数 |
| 2 | `CURRENT_STATE.md:75` | "Controller 类 30" | **135** | `controller/*.java` 计数 |
| 3 | `CURRENT_STATE.md:76` | "Service 类 85+" | **214 + 96(impl) = 310** | `service/*.java + service/impl/*.java` 计数 |
| 4 | `CURRENT_STATE.md:77` | "Entity 类 100+" | **445** | `entity/*.java` 计数 |
| 5 | `CURRENT_STATE.md:78` | "Mapper 类 60+" | **441** | `mapper/*.java` 计数 |
| 6 | `CURRENT_STATE.md:79` | "SQL 迁移文件 78" | **170**（db/sql）+ **1**（db/migration）| `Get-ChildItem -File -Filter '*.sql' -Path db/sql` 计数 |
| 7 | `CURRENT_STATE.md:82` | "前端视图目录 41" | **52** 子目录 / **171** .vue 文件 | `views/` 计数 |
| 8 | `CURRENT_STATE.md:83` | "API 接口文件 42" | **62** | `src/api/*.ts` 计数 |
| 9 | `CURRENT_STATE.md:84` | "E2E 测试用例 180 / 8 个 spec 文件" | **31 个 spec 文件**（含 audit 子目录），21 个非 audit | `e2e/**/*.spec.ts` 计数 |
| 10 | `PROJECT_REALITY_AUDIT.md:46` | "实体文件 387" | **445**（2026-08-24 → 2026-10-18，+58）| `entity/*.java` 计数 |
| 11 | `PROJECT_REALITY_AUDIT.md:48` | "@RestController 类 96" | **137**（+41）| `Select-String '@RestController'` |

> ⚠️ `CURRENT_STATE.md` 写于 **2026-08-17**，距今 2 个月；后端 Java 总量从 409 → 1543（+278%）。文档**未随代码同步更新**。
>
> ⚠️ `PROJECT_REALITY_AUDIT.md` 写于 **2026-08-24**，同样过期 2 个月。`PROJECT_ACCEPTANCE_EVIDENCE.md`（2026-09-09）也基于过期数据声称"READY TO CLOSE"。

### 6.2 版本号矛盾 — package.json 中疑似不存在的版本

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 12 | `PROJECT_ACCEPTANCE_EVIDENCE.md:30` | "TypeScript 版本 `~6.0.2`" | 待运行时核验：`typescript ~6.0.2` 不在 2026-10 已知 GA 列表 | `package.json:34` |
| 13 | `PROJECT_ACCEPTANCE_EVIDENCE.md:29` | "Vite 版本 `^8.2.0`" | 待运行时核验：`vite ^8.2.0` 超出 2026-10 GA | `package.json:38` |
| 14 | `PROJECT_ACCEPTANCE_EVIDENCE.md:31` | "前端构建时长 5.49s 退出码 0" | 本会话未执行 `npm run build` | 证据引用 `terminals/<frontend-build>.txt` 不存在 |
| 15 | `PROJECT_ACCEPTANCE_EVIDENCE.md:42` | "后端编译 BUILD SUCCESS" | 本会话未执行 `mvn clean compile` | 证据引用 `terminals/633635.txt` 不存在 |
| 16 | `PROJECT_ACCEPTANCE_EVIDENCE.md:53` | "E2E 用例 17 通过 13" | 实测 `e2e/*.spec.ts` 共 31 个 spec，文档未列具体 spec 名 | 缺 spec 列表对照 |
| 17 | `PROJECT_ACCEPTANCE_EVIDENCE.md:18` | "最终结论 🟢 READY TO CLOSE" | 与 §6.1 数量级矛盾共存，未给出证据链完整反查路径 | 整文档失据 |

### 6.3 数据库迁移矛盾

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 18 | `application.yml:23` | "Flyway locations: classpath:db/migration" | 实际有效脚本仅 1 个 `V1__init_baseline.sql`，且 baseline-version=2 强制跳过 | 文件清单 + 配置 |
| 19 | `application.yml:26-29` 注释 | "V1 file is mojibake GBK, must skip via baseline-version>V1.version" | 与"自动迁移"语义冲突——生产部署如误启用 baseline-version=0 会再次失败 | 配置自承 |
| 20 | `docker-compose.yml:81-84` | "init_m*.sql + init_migration_*.sql 按字典序首次启动执行" | db/sql 下 init_* + migrate_* 共 170 个脚本（含 `init_migration_v0_baseline.sql` 与 `init_migration_v0_baseline_ALL.sql` 二选一），挂载顺序与 Flyway baseline 谁先谁后无明文 | docker-compose.yml 注释 |

### 6.4 端点 / Controller 内部矛盾

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 21 | `PROJECT_ACCEPTANCE_EVIDENCE.md:6-9` §2.3 "10 个 Service 的 @RestController 误用清除" | "7 个" | **F-10 行写 "7 个 Service"**，但表头又写 "10 个"（数字不一致）| 文本自相矛盾 |
| 22 | `audit/issues/I088` + `I095` + `I108` | "@Transactional rollbackFor 缺失（199 处遗留）" | 全部 Fixed 状态；但本会话**未实测**当前 `@Transactional` 数量是否已修复 | issue 状态 vs 实测代码未做 grep |
| 23 | `audit/issues/I103` | "PluginRuntimeTest 编译失败" | Open 状态；`backend/src/test/` 共 123 个 .java，但 plugin 相关测试是否真的存在编译失败未验证 | issue 状态 |

### 6.5 运行状态矛盾 — Docker 未启动

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 24 | `CURRENT_STATE.md:54` | "MySQL Docker 容器 3307 RUNNING" | Docker Desktop 进程未启动（`docker ps` exit=1）；容器实际状态不可知 | 本会话实测 |
| 25 | `CURRENT_STATE.md:53-58` | "后端 PID 13440 + 前端 PID 13540 HTTP 200" | 本会话未尝试连接 localhost:8080 / 5173 / 3307；当前进程表不可知 | `Get-Process -Name java,node,redis-server` 未执行 |
| 26 | `PROJECT_ACCEPTANCE_EVIDENCE.md:6` | "🟢 READY TO CLOSE" | 与 #1-#11 数量级矛盾共存；与 #24-#25 实际进程未验证共存 | 整文档失据 |

### 6.6 命名 / 文件残留矛盾

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 27 | 无明确文档 | 无 | `audit/issues/issue-register.md.corrupted-20261016`（备份文件） | `audit/issues/` 目录 |
| 28 | 无明确文档 | 无 | `service/P3EcosystemService.java.disabled`（28354 字节，与启用版同名等大）| `service/` 目录 |
| 29 | 无明确文档 | 无 | 项目根散落 `out.log / out2.log / out3.log / out-combine.log / out-combine2.log / check_v2.ps1 / menu-bug.png` 等调试/日志残留，未进 `.gitignore` | 根目录 `Get-ChildItem` |
| 30 | `PROJECT_ACCEPTANCE_EVIDENCE.md` 无对应章节 | 无 | `tests/` 目录混有 `_probe.ps1 / _dbg.ps1 / _probe2.ps1 / _probe3.ps1 / smoke*.log / smoke*.json` 等 | `tests/` 目录 |

### 6.7 evidence / issue 命名空间不一致

| # | 文档位置 | 文档声称 | 实际 | 证据 |
|---|---|---|---|---|
| 31 | `feature_inventory.tsv` | 25 个 P0-P3 Feature | `audit/evidence` 144 个 F 目录 | 命名空间不重合 |
| 32 | `feature_inventory.tsv` 第 7 列 `e2e_spec_substr` | 例：`p4-1-ai-scheduler` | 实际 `e2e/` 下无 `ai-scheduler.spec.ts` —— 只有 `p0-financial-safety / p0-wallet / p0-reconciliation / p0-lifecycle` 等 | `e2e/` 文件清单 |
| 33 | `feature_inventory.tsv` 行数 vs reality_score.sh 声称 | "50 项功能清单"（脚本注释） | 实际 25 行 | `feature_inventory.tsv` 计数 vs 脚本注释 |
| 34 | `audit/issues/issue-register.md:1` | "重建于 2026-10-16" | 旁边有 `issue-register.md.corrupted-20261016` 备份 → 索引重建 ≠ issue 关闭 | 文件清单 |

---

## 7. 无法独立核验的事项（标 🔒）

| 项 | 原因 | 验证方法 |
|---|---|---|
| DB 真实表数 | Docker Desktop 未启动 | `docker compose up -d mysql && docker exec meiyetec-mysql mysql -uroot -proot123456 meiyetec -e "SHOW TABLES;"` |
| 实体 vs 表一一对应 | 表数未知 | 等表数出来后做 `entity` 类名 ∩ `SHOW TABLES` 结果集 |
| Flyway schema_history 状态 | 服务未运行 | 启动后 `SELECT * FROM flyway_schema_history;` |
| `npm run build` 是否真能跑通 | TS / Vite / vue-tsc 版本号存疑 | `cd frontend/meiyetec-web && npm install && npm run build` |
| `mvn clean compile` 是否真能跑通 | 本会话未执行 | `cd backend && mvn -DskipTests clean compile` |
| `@Transactional` 修复是否到位 | issue 标 Fixed 但未实测 | `grep -r '@Transactional' backend/src/main/java --include='*.java'` 数 `rollbackFor=` 出现次数 |
| 后端 8080 / 前端 5173 是否真监听 | 服务未启动 | `Get-NetTCPConnection -LocalPort 8080,5173,3307,6379` |
| E2E 17 用例 / 13 通过 是否真发生 | 未运行 | `cd frontend/meiyetec-web && npm run e2e:ci:chromium` |

---

## 8. 总结

| 维度 | 现状 |
|---|---|
| **代码规模** | 后端 1543 个 .java / 前端 171 个 .vue / 970 个 REST endpoint — 已达"中大型单体 SaaS"体量 |
| **文档同步** | ⚠️ 严重失同步。`CURRENT_STATE.md` (2026-08-17) / `PROJECT_REALITY_AUDIT.md` (2026-08-24) / `PROJECT_ACCEPTANCE_EVIDENCE.md` (2026-09-09) 全部基于 2 个月前的快照；声称数字与代码实际差 1.7× ~ 4.4× |
| **DB 一致性** | 🔒 无法独立核验（Docker 未启动）。`db/migration/` 仅 1 个 V* 脚本（mojibake GBK），真实 schema 由 `db/sql/` 170 个 init/migrate 脚本在容器首次启动时 apply |
| **Evidence 覆盖** | 144 个 F 目录 vs 25 个 feature_inventory 功能 — 两套编号无映射，覆盖率无法直接对比 |
| **Issue Register** | 46 个 issue（44 个 I*.md + register + corrupted 备份），其中 Open 6 个、Fixed 28 个、Unknown 6 个、Closed 2 个、Mitigated 1 个、其他 3 个 |
| **可疑版本** | `typescript ~6.0.2` / `vite ^8.2.0` / `vue-tsc ^3.3.8` / `unplugin-auto-import ^21.0.0` 公开包注册表疑似未到该版本号 |
| **READY TO CLOSE 评估** | ⚠️ 文档结论（`PROJECT_ACCEPTANCE_EVIDENCE.md:18`）与本盘点发现的 34 条矛盾共存；该结论**缺乏可独立复现的证据链**，建议在 §7 全部事项核验完成前不要采信 |

> 本盘点**不**得出"READY TO CLOSE"或"NOT READY"的结论；仅列出事实与差异。