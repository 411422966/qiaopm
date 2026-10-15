# AlertManager 通道说明

> Gate 3 批次 2.2 — P0-C 告警通道
> AlertManager 版本：v0.27.0

## 1. 当前通道结构

| Receiver | 类型 | URL | 适用告警 | 状态 |
|---|---|---|---|---|
| `webhook-log` | webhook | `http://localhost:9095/alerts` | warning / 老 critical | ✅ 已启用 |
| `wechat-webhook` | webhook | `http://httpbin:80/post` (hardcode) | critical | ✅ 已启用（本地 httpbin） |
| `default` | webhook | `http://localhost:9095/alerts` | 兜底（无 severity） | ✅ 已启用 |
| `wechat-app` | wechat_configs | 企业微信自建应用 API | （备选方案，未启用） | ❌ 注释中 |
| `email-placeholder` | email_configs | SMTP | （未来） | ❌ 注释中 |

## 2. 路由逻辑

```
root (receiver=default)
├── match severity=critical → wechat-webhook + (continue=true → default)
├── match severity=warning  → webhook-log
└── match {}                → default
```

- **critical** 双重通知：wechat-webhook（企业微信通道）+ default（HTTP 兜底）
- **warning** 单通知：webhook-log（本地日志）
- **无 severity 标签** 兜底到 default

## 3. ⚠️ AlertManager 原生 JSON ≠ 企业微信群机器人格式

AlertManager 的 `webhook_configs` 发送的 JSON 形如：

```json
{
  "version": "4",
  "groupKey": "{}:{alertname=\"X\"}",
  "status": "firing",
  "receiver": "wechat-webhook",
  "alerts": [
    {
      "status": "firing",
      "labels": {"alertname": "X", "severity": "critical", "service": "api"},
      "annotations": {"summary": "...", "description": "..."}
    }
  ]
}
```

而企业微信群机器人要求的格式是：

```json
{
  "msgtype": "text",
  "text": {
    "content": "【CRITICAL】...\n服务：...\n..."
  }
}
```

**直接对接企业微信群机器人 URL 会被 400 拒绝**。当前批次验证的仅是 **网络连通性**（从 AlertManager 能把 JSON POST 到某个 URL），不是 **业务格式转换**。

## 4. 生产 adapter 方案（3 选 1，留 P1）

### 方案 A：用 prometheus-webhook-dingtalk 类似工具（找企业微信版）

- 部署一个 `telegram-bot-api` 风格的转换服务
- 接收 AlertManager JSON，转换成企业微信格式，再 POST 到群机器人
- 推荐开源：`prometheus-webhook-dingtalk` 改 1 行（替换 webhook URL 前缀）
- **优点**：开箱即用
- **缺点**：多一个服务要维护

### 方案 B：自建 adapter（Node/Python 20 行）

最小示例（Node）：

```js
const http = require('http');
const https = require('https');
const URL = require('url');

const WECHAT_WEBHOOK = process.env.WECHAT_WEBHOOK_URL;
const PORT = process.env.PORT || 9096;

http.createServer((req, res) => {
  let body = '';
  req.on('data', chunk => body += chunk);
  req.on('end', () => {
    try {
      const am = JSON.parse(body);
      const lines = am.alerts.map(a => {
        const l = a.labels || {};
        return `【${(am.status || '').toUpperCase()}】${l.alertname || '?'}\n` +
               `服务：${l.service || '?'} 层级：${l.tier || '?'}\n` +
               `${a.annotations?.summary || ''}\n${a.annotations?.description || ''}`;
      });
      const wechatPayload = JSON.stringify({
        msgtype: 'markdown',
        markdown: { content: lines.join('\n\n---\n\n') }
      });
      const u = new URL(WECHAT_WEBHOOK);
      const opts = { method: 'POST', headers: { 'Content-Type': 'application/json' } };
      const lib = u.protocol === 'https:' ? https : http;
      const r = lib.request(u, opts, () => res.writeHead(200).end('ok'));
      r.on('error', e => res.writeHead(500).end(String(e)));
      r.write(wechatPayload);
      r.end();
    } catch (e) {
      res.writeHead(400).end(String(e));
    }
  });
}).listen(PORT);
```

- 部署：写 Dockerfile，加到 `docker-compose.monitoring.yml`
- AlertManager 改 `WECHAT_WEBHOOK_URL=http://meiyetec-wechat-adapter:9096/`
- **优点**：完全可控，最小依赖
- **缺点**：要写代码、要测、要维护

### 方案 C：改用 wechat_configs（自建应用通道）

用 AlertManager 原生 `wechat_configs` 走企业微信自建应用 API：

```yaml
- name: 'wechat-app'
  wechat_configs:
    - api_url: 'https://qyapi.weixin.qq.com/cgi-bin/'
      corp_id: 'YOUR_CORP_ID'
      agent_id: 'YOUR_AGENT_ID'
      secret: 'YOUR_SECRET'
      to_user: '@all'
      message: |
        【{{ .Status | toUpper }}】{{ .GroupLabels.alertname }}
        ...
```

- 启用方式：取消 `alertmanager.yml` 中 `wechat-app` 注释 + 填入 corp_id / agent_id / secret
- routes 改 critical → wechat-app
- **优点**：原生支持，无中间件
- **缺点**：
  - 需要企业微信「自建应用」权限（不是群机器人）
  - 需申请 corp_id / agent_id / secret
  - 自建应用消息会出现在「应用消息」而非「群消息」，触达率略低

## 5. 决策记录

| 时间 | 决策 | 原因 |
|---|---|---|
| Gate 3 批次 2.2 | 用 `webhook_configs` + httpbin 验证通道 | AlertManager 原生 wechat_configs 需 corp_id/agent_id/secret，本地无凭据 |
| Gate 3 批次 2.2 | 生产 adapter 留 P1 | 涉及企业微信后台操作 + 凭据管理，超出 P0-C 范围 |
| Gate 3 批次 2.2 | 保留 `webhook-log` 兜底 | 防止 WECHAT_WEBHOOK_URL 配错时告警完全丢失 |

## 6. 本地测试方法

```powershell
# 1. 启动
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d alertmanager httpbin

# 2. 验证 AM ready
curl http://localhost:9093/-/ready
# 期望: OK

# 3. 验证 receivers 加载
curl http://localhost:9093/api/v2/status | jq '.config.original`
# 期望: 含 wechat-webhook / default / webhook-log 三个 receiver

# 4. 手工触发测试告警（amtool）
docker exec meiyetec-alertmanager amtool alert add \
  alertname=TestAlert severity=critical \
  --alertmanager.url=http://localhost:9093

# 5. 看 httpbin 是否收到
docker logs meiyetec-httpbin --tail 20
# 期望: POST /post 200
```

## 7. 已知限制

- AlertManager v0.27.0 `webhook_configs` 不支持 `http_config.timeout`（要靠反向代理层加超时）
- `WECHAT_WEBHOOK_URL` **未启用** — AlertManager v0.27.0 配置加载时 env 替换只支持 `${VAR}` 简单语法，不支持 bash 风格 `${VAR:-default}`。当 var 未设置时 `${VAR}` 替换为空字符串 → `url.Parse` 报 `unsupported scheme ""` → 启动失败。本地直接 hardcode `http://httpbin:80/post`，生产部署改 alertmanager.yml 第 ~92 行为真实企业微信 URL（或挂 format adapter）
- httpbin 容器无持久化 — 重启丢历史，仅用于测试连通性
- 抑制规则 `inhibit_rules` 只在 critical + warning 同 service/tier 时生效，跨告警不抑制
