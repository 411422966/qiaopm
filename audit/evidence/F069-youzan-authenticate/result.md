# F069 YouzanEcommerceAdapter.authenticate — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification

---

## 1. 实证结论

**状态**：**UNVERIFIED**（D10 铁律 — 真实 OAuth2 凭据缺失）

## 2. 静态证据

`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java` L185-217：

| 维度 | 内容 |
|---|---|
| **鉴权协议** | OAuth2 Client-Credentials（client_id + client_secret → access_token）|
| **Token 端点** | `POST {apiBaseUrl}/api/oauthentry/grant/token` |
| **缓存策略** | `tokenExpireAt > now() + 2min` → 复用；否则重新签发 |
| **过期秒数** | 默认 7200s（2h）|
| **降级** | 无凭据 → 走 `MockEcommerceAdapter` fallback（@ConditionalOnMissingBean）|

## 3. 凭据依赖

| 字段 | 来源 |
|---|---|
| `appKey` (EcommercePlatform) | 用户手动填入 / DB seed |
| `appSecret` (EcommercePlatform) | 用户手动填入 / DB seed |

→ user decision #1：凭据缺失 → Mock 降级，**未来真实接入时再验证 OAuth2 链路**

## 4. Issue 状态

无新增 Issue。F069 静态层完整，仅缺真实凭据触发链路验证。
