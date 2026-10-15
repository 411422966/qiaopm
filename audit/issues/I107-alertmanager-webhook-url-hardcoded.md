# I107 — AlertManager wechat-webhook URL 硬编码

> **状态**：✅ FIXED
> **严重度**：P3（部署期可观测性 / 不阻塞 P0）
> **登记时间**：2026-10-14 15:37
> **修复时间**：2026-10-14 16:10（Gate 3 批次 2.4）
> **归属**：Gate 3 批次 2.4
> **关联**：`infra/alertmanager/alertmanager.yml`（wechat-webhook receiver）/ `docker-compose.monitoring.yml`（alertmanager service）/`d2a8d4e` commit（批次 2.2）
> **相关 Issue**：I106（同类硬编码：`meiyetec-net` 网络名）

---

## 1. 现象

`infra/alertmanager/alertmanager.yml` 第 92 行 `wechat-webhook` receiver：

```yaml
- name: 'wechat-webhook'
  webhook_configs:
    - url: 'http://httpbin:80/post'   # ← 硬编码
      send_resolved: true
```

- **当前**：hardcode `http://httpbin:80/post`（本地开发用 echo-server）
- **生产期望**：指向企业微信群机器人 webhook URL（`https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx`）

## 2. 根因（AlertManager v0.27.0 URL 字段限制）

AlertManager v0.27.0 的 `webhook_configs.url` 字段：
- **不支持** bash 风格 `${VAR:-default}` 语法 → AM 报 `colon in first path segment`
- **不支持** 简单 `${VAR}` 语法 → AM 报 `unsupported scheme ""`（把 `${VAR}` 当作 URL 解析）
- **不支持** Go template `{{ env "VAR" }}` 语法 → 同样报 `unsupported scheme ""`
- `webhook_configs.url` 是纯字符串，**不展开任何模板或 env var**

## 3. 修复方案（sed wrapper + env_file）

### 3.1 alertmanager.yml（wechat-webhook receiver）

```yaml
- name: 'wechat-webhook'
  webhook_configs:
    - url: '__WECHAT_WEBHOOK_URL__'   # ← 占位符
      send_resolved: true
```

用 `__WECHAT_WEBHOOK_URL__` 作为占位符，由 sed wrapper 动态替换。

### 3.2 docker-compose.monitoring.yml（alertmanager service）

```yaml
alertmanager:
  env_file:
    - ./infra/alertmanager/alertmanager.env   # WECHAT_WEBHOOK_URL=http://httpbin:80/post
  entrypoint: []   # 覆盖默认 ENTRYPOINT
  command:
    - /bin/sh
    - -c
    - |
      set -e
      sed "s|__WECHAT_WEBHOOK_URL__|$${WECHAT_WEBHOOK_URL}|g" \
        /etc/alertmanager/alertmanager.yml > /tmp/alertmanager.generated.yml &&
      exec /bin/alertmanager --config.file=/tmp/alertmanager.generated.yml \
        --storage.path=/alertmanager --log.level=info
```

- `entrypoint: []` 覆盖镜像默认 ENTRYPOINT，让 shell command 直接接管
- `env_file` 把 `WECHAT_WEBHOOK_URL` 注入到容器 env
- sed 把 `__WECHAT_WEBHOOK_URL__` 替换为实际 URL 后写 `/tmp/alertmanager.generated.yml`
- AM 加载生成的配置文件

### 3.3 infra/alertmanager/alertmanager.env

```
WECHAT_WEBHOOK_URL=http://httpbin:80/post
```

### 3.4 .gitignore

```
infra/alertmanager/alertmanager.env   # 不入 git，生产时复制自 .example
```

## 4. 生产部署步骤

1. 复制 `infra/alertmanager/alertmanager.env.example` 为 `infra/alertmanager/alertmanager.env`
2. 把 `WECHAT_WEBHOOK_URL=https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=YOUR_KEY` 填入
3. `docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d alertmanager`
4. 验证：`docker exec meiyetec-alertmanager sh -c 'echo WECHAT=$WECHAT_WEBHOOK_URL'`

## 5. 复现验证（修复后）

```bash
# 验证 env_file 注入
docker exec meiyetec-alertmanager sh -c 'echo WECHAT=$WECHAT_WEBHOOK_URL'
# 期望：WECHAT=http://httpbin:80/post

# 验证 sed 替换生效
docker exec meiyetec-alertmanager sh -c 'grep wechat /tmp/alertmanager.generated.yml | head -5'
# 期望：url: 'http://httpbin:80/post'

# 验证 AM ready
curl http://localhost:9093/-/ready
# 期望：OK
```

## 6. 闭环记录

| 动作 | 日期 | 证据 |
|---|---|
| 发现 | 2026-10-14 14:55 | 批次 2.2 实施时 AM 启动循环重启 |
| 根因定位 | 2026-10-14 15:20 | docker logs 抓取两次错误（colon / unsupported scheme）|
| 临时方案落地 | 2026-10-14 15:30 | hardcode `http://httpbin:80/post` + commit `d2a8d4e` |
| I107 登记 | 2026-10-14 15:37 | 本文档创建 |
| **修复** | **2026-10-14 16:10** | **Gate 3 批次 2.4 — sed wrapper + env_file + 占位符方案** |
| **验证** | **2026-10-14 16:15** | **AM healthy，WECHAT env OK，grep 显示 http://httpbin:80/post** |
| **关闭** | **2026-10-14 16:15** | **✅ FIXED** |

---

**签署**：作为 P3 改进项保留，**不阻塞 Gate 3 批次 2.2 收尾**。与 I106（`meiyetec-net` 硬编码）同模式（环境耦合硬编码），建议**两 issue 一并修复**（方案 A 通用）。
