# I105 — docker-compose.yml 幽灵 SQLite 覆盖事件

> **状态**：📝 RECORDED（仅记录，已修复）
> **严重度**：P3（审计线索 / 不阻塞）
> **登记时间**：2026-10-13 00:57
> **归属**：Gate 3 批次 1.4b（伴随 I104 修复）
> **关联 commit**：`9ec44d4`（监控 compose 修复间接暴露此问题） / `a952eb7`（I104 VERIFIED 阶段确认）

---

## 1. 现象

P0-B 修复（`9ec44d4`）中执行 `docker compose -f docker-compose.yml -f docker-compose.monitoring.yml config` 时报控制字符错误。`Get-Content docker-compose.yml -TotalCount 5` 看到首行 `INDX(        gd?    ` —— 这是 **SQLite 索引头**的 16 进制表示。

- 当前文件前 4 字节：`49 4E 44 58`（"INDX"）
- 当前文件大小：7039 字节
- 当前文件内容：MyBatis Mapper class 索引（包含 `DcepSubWalletMapper.class` / `DCEPSU~1.CLA` 等字符串）

## 2. 根因分析

| 维度 | 现象 |
|---|---|
| **损坏时间窗口** | `d7a96aa`（1.4a 完成，FLYWAY_ENABLED 加入）→ `3f60ce0`（1.4b 首版完成）之间 |
| **HEAD 状态** | `d7a96aa:docker-compose.yml` 正常（`# ============...` 头 / 7039B） |
| **git index 状态** | 与 working tree 一致（都是 SQLite 二进制）→ `git status` 报 clean |
| **文件大小巧合** | 当前 7039B = HEAD 7039B，**纯字节覆盖**（同长度二进制替换，不是截断/追加） |
| **可疑成因** | 1) PowerShell `> ` 重定向误把 SQLite 当目标；2) IDE 自动保存时路径错；3) 某次 mvn test 时 MyBatis 自动生成 mapper 误写到了该路径；4) Windows 容器挂载冲突（极小概率） |
| **最大可能** | mvn test 触发 mybatis-generator 把 mapper class 索引重定向到 `docker-compose.yml` |

## 3. 处置

- `git checkout HEAD -- docker-compose.yml` 首次失败（文件被进程锁）
- `Remove-Item docker-compose.yml -Force` + `git restore docker-compose.yml` 成功
- 验证：头恢复 `23 20 3D 3D`（`# ===`）/ FLYWAY_ENABLED 第 133 行 / `docker compose config` 通过
- `git hash-object` 重新对比 working tree = HEAD `000dd1e72837faa0c050adeac34ca097000408d3`

## 4. 教训

> **所有 `> file` 重定向后必须 `git diff` 验证**

| 习惯 | 状态 |
|---|---|
| `> file` 后必看 head + size | ❌ 缺失 → 引入 I105 |
| 重写根级配置文件前先 `git status` | ❌ 缺失 |
| `mvn test` / `mybatis-generator` 输出目录硬编码保护 | ❌ 缺失（应配 `*.xml` / `*.yml` 白名单） |
| Windows 文件锁排查（`handle.exe` / `lsof`）| ⚠️ 知识缺位（本次 `git checkout` 失败靠 `Remove-Item` 兜底）|

## 5. 预防

| 措施 | 实施时点 |
|---|---|
| 在 `audit/baseline/preflight.md` 增「> file 后必 git diff」 | Phase 0 起 |
| `infra/.gitignore` 加 mapper 临时目录 | Gate 3 批次 2.x |
| 配合 I103（PluginRuntimeTest 编译失败）一并修复 mvn test 红 | Gate 3 批次 2.x |
| 提交 hook 监控根级 yaml 大小突变（> 1KB diff 即告警）| 后续批次（成本较高）|

## 6. 闭环记录

| 动作 | 日期 | 证据 |
|---|---|---|
| 发现 | 2026-10-13 00:46 | `9ec44d4` 修复时 `docker compose config` 失败 |
| I105 登记 | 2026-10-13 00:57 | 本文档创建 |
| 修复 | 2026-10-13 00:46 | `git restore docker-compose.yml`（HEAD = d7a96aa）|
| 验证 | 2026-10-13 00:46 | docker compose config 通过 / FLYWAY_ENABLED 命中 |
| commit | 2026-10-13 00:57 | `a952eb7` 间接完成（I104 VERIFIED 时一并验证）|
| I105 状态 | 📝 RECORDED（仅记录） | 损坏已修复，无业务影响 |

---

**签署**：作为审计线索保留，**不阻塞任何 Phase / Gate**。
