# I106 — meiyetec-net 硬编码 name

> **状态**：✅ FIXED
> **严重度**：P3（耦合 / 不阻塞）
> **登记时间**：2026-10-13 00:57
> **修复时间**：2026-10-14 16:05（Gate 3 批次 2.4）
> **归属**：Gate 3 批次 2.4
> **关联**：`docker-compose.monitoring.yml:99-105`（networks 节） / `docker-compose.yml:131-133`（networks 节） / `9ec44d4` commit
> **相关 EXT**：无

---

## 1. 现象

`docker-compose.monitoring.yml` 中网络声明：

```yaml
networks:
  meiyetec-net:
    # 实际 docker 网络名（= project_name + 声明的网络名）
    name: meiyetec_meiyetec-net
    external: true
```

- **当前实际**：`docker compose ls` 显示 project = `meiyetec`，所以 docker 创建的网络名 = `meiyetec_meiyetec-net`
- **声明策略**：硬编码前缀 `meiyetec_`，**绑死 project name**

## 2. 影响

| 场景 | 后果 |
|---|---|
| 开发者用 `--project-name dev` 启动 | `meiyetec-api` 等容器仍然 join `meiyetec_meiyetec-net`（实际创建的是 `dev_meiyetec-net`）→ **跨网络隔离失败** |
| CI 多副本并行（如 `ci-pr123`）| 监控栈找不到 `meiyetec_meiyetec-net` → `network declared as external, but could not be found` |
| 未来拆多环境（`prod` / `staging` / `dev`）| 必须每个环境维护一份 monitoring.yml |
| 改名 `meiyetec` → `meiyetec-prod` | 监控栈立刻断 |

## 3. 修复（方案 A — 环境变量 + 默认值）

### 3.1 docker-compose.monitoring.yml（line 178）

```yaml
networks:
  meiyetec-net:
    name: ${COMPOSE_PROJECT_NAME:-meiyetec}_meiyetec-net
    external: true
```

### 3.2 docker-compose.yml（line 131-133，**新增**）

```yaml
networks:
  meiyetec-net:
    name: ${COMPOSE_PROJECT_NAME:-meiyetec}_meiyetec-net
    driver: bridge
```

- 两个 compose 的 `name` 都用 `${COMPOSE_PROJECT_NAME:-meiyetec}_meiyetec-net`
- 与主 `docker-compose.yml` 顶层 `name: ${COMPOSE_PROJECT_NAME:-meiyetec}` 保持一致
- 当用 `docker compose -p myproj ...` 启动时，网络名 = `myproj_meiyetec-net`（不再绑死 meiyetec）

## 4. 复现验证（修复前 vs 修复后）

```powershell
# 修复前：用 -p test 启动 → 监控栈找不到 meiyetec_meiyetec-net
docker compose -p test -f docker-compose.yml -f docker-compose.monitoring.yml up -d
# 期望：network declared as external, but could not be found

# 修复后：用 -p test 启动 → 网络名 = test_meiyetec-net，所有容器正常 join
docker compose -p test -f docker-compose.yml -f docker-compose.monitoring.yml up -d
# 期望：所有容器 Up，AM ready OK

# 验证 compose config：
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml config | Select-String "name:"
# 期望：name: meiyetec_meiyetec-net（默认 project=meiyetec）
```

## 6. 闭环记录

| 动作 | 日期 | 证据 |
|---|---|---|
| 发现 | 2026-10-13 00:46 | `9ec44d4` 修复时第一次 `up -d` 失败（network not found）|
| I106 登记 | 2026-10-13 00:57 | 本文档创建 |
| 修复 | 2026-10-14 16:05 | Gate 3 批次 2.4 — 两个 compose 的 networks 段都变量化 |
| 验证 | 2026-10-14 16:10 | `docker compose config | Select-String "name:"` → `name: meiyetec_meiyetec-net` |
| 关闭 | 2026-10-14 16:15 | ✅ FIXED |

---

**签署**：作为 P3 改进项保留，**不阻塞 Gate 3 收尾**。
