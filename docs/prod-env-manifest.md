# PROD ENV MANIFEST（生产环境变量清单）

> 事实源（SoT）：本文档是 `application-prod.yml` 全部 `${ENV:?}` 引用 + `.env.example` 所有条目的**对外交付版**。
> 配套文件：
> - `backend/src/main/resources/application-prod.yml` — 强约束实现
> - `backend/src/main/resources/application.yml` — base（dev 友好 default）
> - `.env.example` — 速查表（带默认值与分组）

---

## 0. 使用方法

1. 部署前对照本表准备所有 ENV（K8s ConfigMap / Secret / 容器 ENV）
2. **必须设 `SPRING_PROFILES_ACTIVE=prod`**（否则 application-prod.yml 不生效）
3. 启动后查启动日志 — 任何 `${ENV:?}` fail-fast 应**不出现**
4. 校验方式：参见 [§5 验证](#5-验证)

---

## 1. 关键标识

| ENV | 必填 | 校验规则 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | 字符串 = `prod` | 不切 prod → 走 base dev default | `prod` |

---

## 2. PROD-001 ~ 003：基础设施（DB / Redis / Flyway）

| ENV | 必填 | 校验规则 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `SPRING_DATASOURCE_URL` | ✅ | JDBC URL 合法 + 含 `useSSL=true` + 非 localhost | 启动失败（`?:` fail-fast） | `jdbc:mysql://prod-rds:3306/meiyetec?...&useSSL=true` |
| `SPRING_DATASOURCE_USERNAME` | ✅ | 非空 + 不等于 `root` | 启动失败 | `meiyetec_app` |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | 长度 ≥ 8 + 不等于 `root123456` | 启动失败 | `（K8s Secret 注入）` |
| `SPRING_DATA_REDIS_HOST` | ✅ | 非空 + 非 `localhost` | 启动失败 | `prod-redis-cluster.cache.amazonaws.com` |
| `SPRING_DATA_REDIS_PORT` | ⚠️ | 1-65535；缺省 `6379` | warn（应用 default 6379） | `6379` |
| `SPRING_DATA_REDIS_PASSWORD` | 推荐 | 非空 | warn（内网裸跑风险） | `（Secret 注入）` |
| `FLYWAY_ENABLED` | ✅ | 字符串 = `true`（prod 必开） | 启动失败（prod 强制 `true`） | `true` |
| `SERVER_PORT` | ⚠️ | 1-65535；缺省 `8080` | warn | `8080` |

---

## 3. PROD-004：安全（JWT）

| ENV | 必填 | 校验规则 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `JWT_SECRET` | ✅ | base64 解码 ≥ 32 字节 + 不含 `meiyetec/dev/test/not-for-production` 字串 | **启动失败**（JwtUtil 校验） | `openssl rand -base64 48` |
| `JWT_EXPIRATION` | ⚠️ | 数字 ms；prod 推荐 ≤ 7200000（2h） | 启动用 prod default `7200000` | `7200000` |
| `JWT_REFRESH_GRACE_MS` | ⚠️ | 数字 ms；缺省 `604800000`（7d） | warn | `604800000` |

---

## 4. PROD-005 ~ 011：业务模式（必须显式设置）

| ENV | 必填 | 校验规则 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `APP_DCEP_MODE` | ✅ | 字符串 = `prod`（禁止 `dev`） | 启动用 prod default `prod` | `prod` |
| `APP_ID_GENERATOR_WORKER_ID` | ✅ | 整数 0-31；多实例每 POD 不同 | **启动失败** | `0`（单实例）/ `3`（第 3 个 POD） |
| `APP_ID_GENERATOR_DATACENTER_ID` | ✅ | 整数 0-31 | **启动失败** | `0` |
| `APP_CN_PAY_ENV_MODE` | ✅ | 字符串 = `production`（禁止 `sandbox`） | 启动用 prod default `production` | `production` |
| `FISCAL_MODE` | ✅ | `baiwang` / `baiwang-sandbox`（prod 必 `baiwang`） | 启动用 prod default `baiwang` | `baiwang` |
| `PLUGIN_PLUGINS_ROOT` | ✅ | 绝对路径（以 `/` 或 `C:\` 开头） | **启动失败** | `/opt/meiyetec/plugins` |
| `PLUGIN_RUNTIME_ENABLED` | ⚠️ | `true` / `false`；prod 推荐 `true` | 启动用 prod default `true` | `true` |
| `PLUGIN_AUTOLOAD` | ⚠️ | prod 推荐 `false`（人工审核后再 load） | 启动用 prod default `false` | `false` |

---

## 5. 第三方平台（按需启用，启用时必填）

### 5.1 LaunchDarkly（灰度发布）

| ENV | 启用条件 | 必填 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `LAUNCHDARKLY_ENABLED` | 视部署 | 必填（true / false） | 启动用 prod default `false` | `false` |
| `LAUNCHDARKLY_SDK_KEY` | `LAUNCHDARKLY_ENABLED=true` | ✅ 必填 | LD SDK 启动失败 | `sdk-xxxxxxxx-xxxx-xxxx` |

### 5.2 BNPL（先买后付）

| ENV | 启用条件 | 必填 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `KLARNA_BASE_URL` | 启用 Klarna | ✅ 必填 | 走 mock（不报错但行为降级） | `https://api.klarna.com` |
| `KLARNA_CLIENT_ID` | 启用 Klarna | ✅ 必填 | 同上 | `klarna_client_xxx` |
| `KLARNA_CLIENT_SECRET` | 启用 Klarna | ✅ 必填 | 同上 | `（Secret 注入）` |
| `KLARNA_MERCHANT_ID` | 启用 Klarna | ✅ 必填 | 同上 | `merchant_xxx` |
| `KLARNA_TEST_MODE` | 启用 Klarna | 必填（true / false） | 启动用 prod default `false` | `false` |
| `AFTERPAY_BASE_URL` | 启用 Afterpay | ✅ 必填 | 走 mock | `https://api.afterpay.com` |
| `AFTERPAY_MERCHANT_ID` | 启用 Afterpay | ✅ 必填 | 同上 | `merchant_xxx` |
| `AFTERPAY_SECRET_KEY` | 启用 Afterpay | ✅ 必填 | 同上 | `（Secret 注入）` |
| `AFTERPAY_PUBLIC_KEY` | 启用 Afterpay | ✅ 必填 | 同上 | `（webhook 验签用）` |
| `AFTERPAY_TEST_MODE` | 启用 Afterpay | 必填 | 启动用 prod default `false` | `false` |

### 5.3 支付（微信 V3 / 支付宝 V4 / 银联）

| ENV | 启用条件 | 必填 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `APP_CN_PAY_NOTIFY_URL` | 启用支付 | ✅ 必填 | 回调地址错乱 | `https://api.meiyetec.com/domestic/webhook` |
| `APP_CN_PAY_WECHAT_APP_ID` | 启用微信 | ✅ 必填 | 走 PLACEHOLDER_ 默认 | `wx0000000000000000` |
| `APP_CN_PAY_WECHAT_MCH_ID` | 启用微信 | ✅ 必填 | 同上 | `1234567890` |
| `APP_CN_PAY_WECHAT_API_V3_KEY` | 启用微信 | ✅ 必填（32 字节） | 同上 | `（32 字节字符串）` |
| `APP_CN_PAY_WECHAT_KEY_PATH` | 启用微信 | 推荐 | 默认 classpath:keys/.../apiclient_key.pem | `/etc/meiyetec/keys/wechat/apiclient_key.pem` |
| `APP_CN_PAY_WECHAT_CERT_SERIAL` | 启用微信 | ✅ 必填 | PLACEHOLDER_ | `商户证书序列号` |
| `APP_CN_PAY_WECHAT_CERT_PATH` | 启用微信 | 推荐 | 默认 classpath:keys/.../apiclient_cert.pem | `/etc/meiyetec/keys/wechat/apiclient_cert.pem` |
| `APP_CN_PAY_ALIPAY_APP_ID` | 启用支付宝 | ✅ 必填 | 占位 | `2021000000000000` |
| `APP_CN_PAY_ALIPAY_PRIVATE_KEY` | 启用支付宝 | ✅ 必填 | 占位 | `（应用私钥）` |
| `APP_CN_PAY_ALIPAY_PUBLIC_KEY` | 启用支付宝 | ✅ 必填 | 占位 | `（支付宝公钥）` |
| `APP_CN_PAY_ALIPAY_SIGN_TYPE` | 启用支付宝 | ⚠️ 缺省 `RSA2` | warn | `RSA2` |
| `APP_CN_PAY_ALIPAY_GATEWAY` | 启用支付宝 | ⚠️ 缺省沙箱网关 | warn | `https://openapi.alipay.com/gateway.do` |
| `APP_CN_PAY_UNION_ENABLED` | 启用银联 | 必填 | 启动用 prod default `false` | `false` |
| `APP_CN_PAY_UNION_MER_ID` | 启用银联 | ✅ 必填 | 占位 | `商户号` |
| `APP_CN_PAY_UNION_SIGN_CERT` | 启用银联 | ✅ 必填 | 占位 | `/etc/meiyetec/keys/union/sign.pfx` |
| `APP_CN_PAY_UNION_SIGN_PWD` | 启用银联 | ✅ 必填 | 占位 | `（证书密码）` |
| `APP_CN_PAY_UNION_ENCRYPT_CERT` | 启用银联 | ✅ 必填 | 占位 | `/etc/meiyetec/keys/union/encrypt.cer` |
| `APP_CN_PAY_UNION_MIDDLE_CERT` | 启用银联 | ✅ 必填 | 占位 | `/etc/meiyetec/keys/union/middle.cer` |
| `APP_CN_PAY_UNION_ROOT_CERT` | 启用银联 | ✅ 必填 | 占位 | `/etc/meiyetec/keys/union/root.cer` |

### 5.4 DCEP AI 视觉（腾讯云 / 阿里云 / 本地）

| ENV | 启用条件 | 必填 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `APP_DCEP_AI_TENCENT_SECRET_ID` | 启用腾讯云 | ✅ 必填 | 走下一优先级（阿里云/本地/Mock） | `AKIDxxx` |
| `APP_DCEP_AI_TENCENT_SECRET_KEY` | 启用腾讯云 | ✅ 必填 | 同上 | `（Secret 注入）` |
| `APP_DCEP_AI_TENCENT_APP_ID` | 启用腾讯云 | ✅ 必填 | 同上 | `1250000000` |
| `APP_DCEP_AI_TENCENT_REGION` | 启用腾讯云 | ⚠️ 缺省 `ap-guangzhou` | warn | `ap-guangzhou` |
| `APP_DCEP_AI_ALIYUN_ACCESS_KEY_ID` | 启用阿里云 | ✅ 必填 | 走下一优先级 | `（AccessKey）` |
| `APP_DCEP_AI_ALIYUN_ACCESS_KEY_SECRET` | 启用阿里云 | ✅ 必填 | 同上 | `（Secret 注入）` |
| `APP_DCEP_AI_ALIYUN_REGION_ID` | 启用阿里云 | ⚠️ 缺省 `cn-shanghai` | warn | `cn-shanghai` |
| `APP_DCEP_AI_LOCAL_ENDPOINT` | 启用本地推理 | ✅ 必填（且非 localhost） | 走 Mock | `http://gpu-server:8081/infer` |
| `APP_DCEP_AI_LOCAL_TIMEOUT_MS` | 启用本地推理 | ⚠️ 缺省 `5000` | warn | `5000` |

### 5.5 IoT / MQTT（HiveMQ）

| ENV | 启用条件 | 必填 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `IOT_MQTT_ENABLED` | 视部署 | 必填 | 启动用 prod default `false` | `false` |
| `IOT_MQTT_HOST` | 启用 MQTT | ✅ 必填 | 连接失败 | `hivemq-cluster.hivemq.com` |
| `IOT_MQTT_PORT` | 启用 MQTT | ⚠️ prod 缺省 `8883`（TLS） | warn（明文风险） | `8883` |
| `IOT_MQTT_CLIENT_ID` | 启用 MQTT | ⚠️ 留空自动生成 | warn | `meiyetec-prod-1` |
| `IOT_MQTT_USERNAME` | 启用 MQTT | ✅ 必填 | 连接失败 | `mqtt_user` |
| `IOT_MQTT_PASSWORD` | 启用 MQTT | ✅ 必填 | 连接失败 | `（Secret 注入）` |

### 5.6 财税开票

| ENV | 必填 | 校验规则 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `FISCAL_MODE` | ✅ | `baiwang`（prod） | 启动用 prod default `baiwang` | `baiwang` |
| `FISCAL_CONNECT_TIMEOUT_MS` | ⚠️ | 缺省 `5000` | warn | `5000` |
| `FISCAL_READ_TIMEOUT_MS` | ⚠️ | 缺省 `15000` | warn | `15000` |
| `FISCAL_RETRY_MAX` | ⚠️ | 缺省 `2` | warn | `2` |

### 5.7 CRM 同步排程

| ENV | 必填 | 校验规则 | 缺失行为 | 示例 |
|---|---|---|---|---|
| `CRM_SYNC_TICK_MS` | ⚠️ | 缺省 `300000`（5 分钟） | warn | `300000` |

### 5.8 Kiosk 告警

| ENV | 必填 | 缺失行为 | 示例 |
|---|---|---|---|
| `KIOSK_ALERT_ENABLED` | ⚠️ | 缺省 `true` | `true` |
| `KIOSK_ALERT_INFO_MIN` | ⚠️ | 缺省 `5` | `5` |
| `KIOSK_ALERT_WARN_MIN` | ⚠️ | 缺省 `15` | `15` |
| `KIOSK_ALERT_CRITICAL_MIN` | ⚠️ | 缺省 `30` | `30` |
| `KIOSK_ALERT_SCAN_MS` | ⚠️ | 缺省 `60000` | `60000` |
| `KIOSK_ALERT_PUSH_ENABLED` | ⚠️ | 缺省 `true` | `true` |
| `KIOSK_ALERT_PUSH_CHANNEL` | ⚠️ | 缺省 `IN_APP` | `IN_APP` |
| `KIOSK_ALERT_COOLDOWN_MIN` | ⚠️ | 缺省 `30` | `30` |
| `KIOSK_DAILY_TARGET_DEFAULT` | ⚠️ | 缺省 `80` | `80` |

---

## 6. 验证

### 6.1 静态检查（不启动应用）

```bash
# 1) base application.yml 已 ENV 化（5 个高危点）
grep -E 'host: localhost|port: 6379|mode: dev$|worker-id: 0$|datacenter-id: 0$' \
  backend/src/main/resources/application.yml
# 期望：空

# 2) base application.yml 新增的 5 个 ENV 引用
grep -E 'APP_DCEP_MODE|APP_ID_GENERATOR|SPRING_DATA_REDIS' \
  backend/src/main/resources/application.yml
# 期望：5 行匹配

# 3) application-prod.yml 用 ${ENV:?} fail-fast
grep -E '\$\{[^}]+:\?[^}]+\}' backend/src/main/resources/application-prod.yml | wc -l
# 期望：≥ 9

# 4) .env.example 完整度
grep -E '^[A-Z_]+=' .env.example | wc -l
# 期望：≥ 30
```

### 6.2 启动期 fail-fast 验证

#### Case A：prod profile + 缺关键 ENV → 启动失败

```bash
# 不设任何 ENV，仅激活 prod profile
SPRING_PROFILES_ACTIVE=prod \
  java -jar target/meiyetec-api-1.0.0.jar
# 期望：5 秒内抛 IllegalArgumentException，至少包含：
#   - "SPRING_DATASOURCE_URL"
#   - "SPRING_DATASOURCE_PASSWORD"
#   - "JWT_SECRET"
#   - "APP_ID_GENERATOR_WORKER_ID"
#   - "PLUGIN_PLUGINS_ROOT"
```

#### Case B：base profile + 不设 ENV → dev 模式启动

```bash
# 不设任何 ENV
java -jar target/meiyetec-api-1.0.0.jar
# 期望：可能因 DB 连不上失败（localhost:3307 未起），
#       但**不**出现 "${ENV:?}" fail-fast 错误
#       （dev default 全部生效）
```

#### Case C：prod profile + 全部 ENV 正确 → 启动成功

```bash
SPRING_PROFILES_ACTIVE=prod \
  SPRING_DATASOURCE_URL=jdbc:mysql://prod-rds:3306/meiyetec?... \
  SPRING_DATASOURCE_USERNAME=meiyetec_app \
  SPRING_DATASOURCE_PASSWORD=$(cat /run/secrets/db_pass) \
  SPRING_DATA_REDIS_HOST=prod-redis \
  JWT_SECRET=$(cat /run/secrets/jwt_secret) \
  APP_ID_GENERATOR_WORKER_ID=0 \
  APP_ID_GENERATOR_DATACENTER_ID=0 \
  PLUGIN_PLUGINS_ROOT=/opt/meiyetec/plugins \
  java -jar target/meiyetec-api-1.0.0.jar
# 期望：启动成功 + 监听 8080
# 启动日志关键词：[PROD] profile active + 11 项 fail-fast 全部 bypass
```

### 6.3 启动后健康检查

```bash
# 进程是否真正进入 READY
curl -fsS http://localhost:8080/actuator/health
# 期望：{"status":"UP"}

# 没有 PLACEHOLDER_ 占位
curl -fsS http://localhost:8080/actuator/env | grep PLACEHOLDER
# 期望：空

# Flyway 是否真的跑 migration
curl -fsS http://localhost:8080/actuator/flyway
# 期望：appliedMigrations > 0
```

---

## 7. 变更历史

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0 | 2026-10-09 | 初版（批次 1.7，方案 B+） | Audit Batch 1.7 |
