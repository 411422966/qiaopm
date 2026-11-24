# I204 — I202 P1 去 meta 落地后：18 个 Controller / 225 个端点对全体登录用户完全开放（前端无门禁 + 后端无鉴权）

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**
> **发现日期**：2026-11-24
> **登记人**：Cursor
> **绑定基线**：HEAD `e4465d0`（`fix(frontend): I203 spa-scene URL 双前缀 7 处 + I202 P1 权限码对齐`）
> **验证等级**：**VERIFIED（静态 L4 + 运行期 L5a + DB 实查）** —— 后端注解静态统计 + `POST /auth/login` 三账号权限载荷 + 17 个抽样端点 HTTP 实测 + 4 个 IoT 对照端点实测 + **2 个写端点实测（含 DB 落库核验）**
> **DB 快照**：**本轮 2026-11-24 运行期 `meiyetec-mysql` 容器实时查询**（MySQL 8.0.46 / 库 `meiyetec`）；本轮**未**新生成快照文件
> **环境**：`audit/baseline/env.md`（经 nginx `:8088` → `meiyetec-api:8080`；MySQL 经容器 `meiyetec-mysql` 访问）
> **关联**：**I202**（本条是 I202 P1 落地后的**衍生安全债**，根因见 I202 §1.4 实施前后对比）、I203（`/spa-scene` URL 双前缀）、I202 R2-2（IoT 码绑 0 角色，**与本条不同源**）、I183/I184（同类「码入库不绑角色 = 死码」家族）、I124（授权码体系分裂）、I115（权限注解覆盖率）

---

## 0. 一句话描述

I202 P1（commit `e4465d0`）按 D1/D3 决策移除了前端 `meta.permission` 守卫码（`dev:access` 16 处 + `pay:order` 5 处，共 21 处），
但**这些 Controller 的后端本就没有任何 `@RequirePermission`** —— 移除前端门禁后，等于**删除了唯一的一道（虽然是坏的）屏障**，
导致 **18 个 Controller / 225 个端点**对**全体登录用户**（含最低权限的 `clerk01` 店员）完全开放，
且**读、写均无门槛**（含资金风控、财税直连、国际支付、HIPAA 密钥、GDPR 同意、插件安装、灰度开关等敏感域）。

---

## 1. 事实核实

### 1.1 前端：守卫码已移除 → 登录即见

I202 §5 P1 改动清单中，D1（`dev:access` 16 处）与 D3（`pay:order` 5 处）均为**删除 `meta.permission` 行**，
改后这 21 条路由的 `meta` 中**不再有任何权限码** → 前端路由守卫 `hasPermission()` 无码可判 → **不拦任何角色**。

### 1.2 后端：18 个 Controller / 225 个端点，`@RequirePermission` 计数为 **0**

| Controller | 路径前缀 | 端点数 | `@RequirePermission` |
|---|---|---|---|
| `service/PosDeviceService.java:25-26` | `/pos-device` | 6 | **0** |
| `service/FiscalService.java:48-49` | `/fiscal` | 6 | **0** |
| `controller/FiscalConfigController.java:31-32` | `/fiscal/config` | 5 | **0** |
| `service/IntlPaymentService.java:17-18` | `/intl-payment` | 4 | **0** |
| `service/HipaaService.java:27-28` | `/hipaa` | 8 | **0** |
| `service/GoogleMapsService.java:29-30` | `/google-maps` | 9 | **0** |
| `service/GdprService.java:25-26` | `/gdpr` | 7 | **0** |
| `service/AiSchedulerService.java:26-27` | `/ai-scheduler` | 5 | **0** |
| `service/RiskControlService.java:17-18` | `/risk-control` | 6 | **0** |
| `service/PluginService.java:26-27` | `/plugin` | 8 | **0** |
| `controller/GrayReleaseController.java:48` | `/gray` | 12 | **0** |
| `service/DataHubService.java:32` | `/datahub` | 23 | **0** |
| `service/PriceListService.java:37` | `/intel/pricelist` | 19 | **0** |
| `service/P1SuiteService.java:26` | `/p1-suite` | 31 | **0** |
| `service/P2FeaturesService.java:30` | **（无类级前缀，见下方说明）** | 14 | **0** |
| `service/P2ComplianceSuiteService.java:32` | `/p2-suite` | 25 | **0** |
| `service/P3EcosystemSuiteService.java:30` | `/p3-suite` | 27 | **0** |
| `service/IoTDeviceService.java:37-38` | `/iot/device` | 10 | **0** |
| **合计** | — | **225** | **0** |

> ⚠ **本条覆盖的是 0 注解的 18 个 Controller（225 端点）。IoT 4 个 Controller（`/iot/rule`、`/iot/alert`、`/iot/data`、`/iot/device/link`）有 26 处注解，其非超管 403 的原因是码绑 0 角色（属 I202 R2-2），不在本条范围。**

> 📌 **`P2FeaturesService`「（无前缀）」说明（2026-11-24 复核）**：该类**没有类级 `@RequestMapping`**（`P2FeaturesService.java:30` 只有 `@RestController`），
> 14 个端点各自在方法上声明**完整绝对路径**，并非挂在根路径：
> - 4 个 `/crm/package/*`：`/crm/package/share/invite`（`:52`）、`/crm/package/share/redeem`（`:105`）、`/crm/package/share/list`（`:166`）、`/crm/package/{orderId}/enable-share`（`:175`）
> - 4 个 `/mkt/fission/*`：`/mkt/fission/invite`（`:205`）、`/mkt/fission/{inviteCode}/register`（`:251`）、`/mkt/fission/{inviteCode}/first-order`（`:287`）、`/mkt/fission/stats`（`:332`）
> - 6 个 `/exp/*`：`/exp`（`:368`）、`/exp/{id}/start`（`:420`）、`/exp/{id}/stop`（`:432`）、`/exp/{expCode}/assign`（`:446`）、`/exp/{expCode}/convert`（`:519`）、`/exp/{expCode}/result`（`:550`）
>
> **与「其他 Controller 路径冲突风险」评估**：全仓类级 `@RequestMapping` 前缀清单比对结果 —— **当前无实际冲突**
> （`/crm` 下已存在 `/crm/care/*`、`/crm/cross`、`/crm/detection/*` 等 12 个前缀，均与 `/crm/package/*` 不重叠；
> `/mkt` 下已有 `/mkt/channel`、`/mkt/coupon` 等 15 个前缀，均与 `/mkt/fission/*` 不重叠；
> 含 `exp` 字样的类级前缀为 `/expiry`、`/stock/expiry/*`、`/mkt/pricing/experiment`，与 `/exp/*` 不重叠；
> 应用当前 **healthy 启动**本身即证明无 Spring 路由映射冲突）。
> **但存在两点残余风险**（非当前故障）：
> ① `/exp/*` 是**顶层裸前缀**，未带业务域前缀（其余 Controller 均带域前缀如 `/gray`、`/datahub`）→ 未来任何新增的 `/exp*` 顶级 Controller 会与之产生歧义；
> ② 该类**跨 3 个业务域**（CRM / MKT / AB 实验）混装在一个 Controller 内、且**无类级前缀**，导致权限码无法按类级前缀统一划分（新增注解时须逐方法指定所属域的码，见 §3 方案 D）。
> **本条不将其单列为独立缺陷**，仅作口径说明与修复时的注意事项。

### 1.3 运行期实测（2026-11-24，经 nginx `:8088`，三账号对照）

**登录载荷**（`POST /api/auth/login`）：

| 账号 | HTTP | code | 权限码数 |
|---|---|---|---|
| `admin` | 200 | 200 | **418**（含 `*` 通配） |
| `manager01` | 200 | 200 | **304** |
| `clerk01` | 200 | 200 | **54** |

**17 个读端点抽样，全部三账号 200**：

| 页面 | 端点 | admin | manager01 | clerk01 |
|---|---|---|---|---|
| dev gray-release | `GET /gray/flag/list` | 200 | 200 | **200** |
| dev risk-control | `GET /risk-control/rule/list` | 200 | 200 | **200** |
| dev ai-scheduler | `GET /ai-scheduler/forecasts` | 200 | 200 | **200** |
| dev gdpr | `GET /gdpr/consent/list` | 200 | 200 | **200** |
| dev hipaa | `GET /hipaa/key/list` | 200 | 200 | **200** |
| dev datahub | `GET /datahub/dim` | 200 | 200 | **200** |
| dev price-list | `GET /intel/pricelist/list` | 200 | 200 | **200** |
| dev p1-suite | `GET /p1-suite/payment/gateway/list` | 200 | 200 | **200** |
| dev p2-suite | `GET /p2-suite/gdpr/consent` | 200 | 200 | **200** |
| dev p3-suite | `GET /p3-suite/plugin/market` | 200 | 200 | **200** |
| dev google-maps | `GET /google-maps/config` | 200 | 200 | **200** |
| dev plugin | `GET /plugin/market/list` | 200 | 200 | **200** |
| dev iot-device | `GET /iot/device/list` | 200 | 200 | **200** |
| pay pos-device | `GET /pos-device/list` | 200 | 200 | **200** |
| pay fiscal | `GET /fiscal/list` | 200 | 200 | **200** |
| pay fiscal-config | `GET /fiscal/config/list` | 200 | 200 | **200** |
| pay intl-payment | `GET /intl-payment/gateways` | 200 | 200 | **200** |

**IoT 有注解端点对照组（clerk01 应 403，实测确实 403）**：

| 端点 | admin | manager01 | clerk01 |
|---|---|---|---|
| `GET /iot/rule/list` | 200 | **403** | **403** |
| `GET /iot/alert/list` | 200 | **403** | **403** |
| `GET /iot/data/list` | 200 | **403** | **403** |
| `GET /iot/device/link/list` | 200 | **403** | **403** |

→ 印证 §1.2：**无注解 Controller 全开放，有注解 Controller 仍拦截**（拦截器本身工作正常，缺的是注解）。

### 1.3.1 浏览器页面级实测（2026-11-25，`clerk01` 登录，nginx `:8088`）

> 目的：把 §1.3 的「端点返回 200」升级为**页面级可交互**证据（路由守卫放行 + 页面实际渲染）。

| 页面 | 账号 | 实测表现 | 判定 |
|---|---|---|---|
| `/dev/gray-release` 灰度发布控制台 | clerk01 | 正常进入，Feature Key / 灰度比例 / 阶段按钮完整 | ✅ 路由守卫放行 |
| `/risk-control` 资金风控 | clerk01 | 正常进入，3 tab（规则管理 / 拦截日志 / 模拟检测） | ✅ |
| `/fiscal` 财税直连 | clerk01 | 正常进入 | ✅ |
| `/fiscal/config` 财税渠道配置 | clerk01 | 正常进入，表头 + **appKey / appSecret** 字段可见 | ✅ |
| `/intl-payment` 国际支付 | clerk01 | 正常进入，2 tab + 「发起一笔测试支付」 | ✅ |
| `/pos-device` 海外 POS | clerk01 | 正常进入，2 tab + 「注册终端」 | ✅ |
| `/gdpr` GDPR 合规 | clerk01 | 正常进入，3 tab | ✅ |
| `/hipaa` HIPAA 加固 | clerk01 | 正常进入，2 tab（加密密钥 / PHI 访问） | ✅ |
| `/plugin` 插件市场 | clerk01 | 正常进入，3 tab（市场 / 已安装 / 运行时） | ✅ |
| `/ai-scheduler` AI 智能排班 | clerk01 | 正常进入，3 tab | ✅ |
| `/google-maps` Google Maps | clerk01 | 正常进入，4 tab，**门店地图 5 店数据渲染** | ✅ |

**敏感读端点 clerk01 实测（经 nginx，含响应体长度）**：

| 端点 | HTTP | 响应体长度 |
|---|---|---|
| `GET /api/gray/flag/list` | 200 | 118 B |
| `GET /api/fiscal/config/list` | 200 | 118 B |
| `GET /api/fiscal/list` | 200 | 822 B |
| `GET /api/intl-payment/gateways` | 200 | 545 B |
| `GET /api/hipaa/key/list` | 200 | 345 B |
| `GET /api/gdpr/consent/list` | 200 | 1696 B |
| `GET /api/plugin/market/list` | 200 | 9103 B |

→ **页面级与端点级一致**：最低权限账号 `clerk01` 对 11 个敏感/开发域页面**全部可达且有数据**，**无任何一处前端门禁生效**。§1.6 的 DB 实查结论（高危域仅 3 条码且绑 0 角色、`pay:%` 0 条、`dev:*` 5 条只绑 ADMIN）在页面层得到印证。

### 1.4 写端点实测（2026-11-24，`clerk01` 账号，2 例 —— **写操作无门槛已实测确认，非静态推论**）

**例 1：`POST /gray/flag` + `POST /gray/flag/{key}/toggle`**（`GrayReleaseController.java:71` / `:92`，**写操作，实测落库**）

| 步骤 | 请求 | 结果 |
|---|---|---|
| 1 | `clerk01` → `POST /api/gray/flag`，body `{flagKey:"i204-write-probe-20261124", flagName:"I204 write probe", enabled:0, rolloutPercent:0, userRule:"HASH"}` | **HTTP 200**，`code=200`，`data.id=3`、`tenantId=1`、`enabled=0` |
| 2 | `clerk01` → `POST /api/gray/flag/i204-write-probe-20261124/toggle`，body `{enabled:1, rolloutPercent:50}` | **HTTP 200**，`code=200`，`data.enabled=1`、`rolloutPercent=50` |
| 3 | **DB 核验** `SELECT id,tenant_id,flag_key,enabled,rollout_percent FROM feature_flag WHERE flag_key LIKE 'i204-write-probe%'` | 1 行：`id=3, tenant_id=1, flag_key=i204-write-probe-20261124, enabled=1, rollout_percent=50` → **写操作真实落库** |
| 4 | **清理** `DELETE FROM feature_flag WHERE flag_key LIKE 'i204-write-probe%'` + `SELECT COUNT(*) FROM feature_flag` | **0 行** → 与操作前一致，**无残留**（操作前该表亦为 0 行） |

**例 2：`POST /risk-control/rule`**（`RiskControlService.java:35`，**资金风控规则创建**）

| 步骤 | 请求 | 结果 |
|---|---|---|
| 1 | `clerk01` → `POST /api/risk-control/rule`，body `{ruleCode:"I204_PROBE", ruleName:"I204 write probe rule", threshold:999999, action:"ALERT", level:"LOW", enabled:false}` | **HTTP 200**，`code=200`，`data.id=1795535064506`、`tenantId=1` |
| 2 | `clerk01` → `GET /api/risk-control/rule/list` | HTTP 200，`total=6`（默认 5 条 + 本次探测 1 条），`I204_PROBE` **可见且生效** |
| 3 | **落库核验** | 该 Controller 用**进程内 `static ConcurrentHashMap`**（`RiskControlService.java:17`），**不写 DB** → **无 DB 残留**；探测规则 `enabled=false` 且 `ruleCode=I204_PROBE` 不匹配 `check()` 的任何 `switch` 分支 → **对风控判定无影响**；容器重启即消失 |
| 4 | **清理（2026-11-25）** | 执行 `docker compose restart api`（**含一次 `docker compose restart api`**），重启后 `GET /risk-control/rule/list` 实测 **5 条**（`BIG_AMOUNT` / `FREQUENT` / `ROUND_AMOUNT` / `CROSS_STORE` / `REFUND_ABUSE`），`I204_PROBE` **已消失** → **内存残留已清零** |

> 📌 **与既有静态推论的一致性**：上述 2 例与 §1.2 的「`@RequirePermission` 计数 = 0」完全吻合 —— 写端点同样无鉴权。
> 📌 **方法学说明**：`clerk01` 为**权限码最少**的账号（54 条），若其可写成功，则任一登录账号均可写。

### 1.5 实施前后对比（净风险上升）

| 阶段 | 前端门禁 | 后端鉴权 | 实际效果 |
|---|---|---|---|
| `e4465d0` 之前 | `dev:access` / `pay:order` 守卫 → **恒 false → 非超管全部 403**（假门禁，I202 R1） | 无 | **非超管进不去页面**（误伤，但数据未泄露） |
| `e4465d0` 之后 | **无 meta → 登录即见** | **无** | **全体登录用户可读 + 可写** |

> **性质**：实施前是「假门禁 + 无鉴权」，实施后是「无门禁 + 无鉴权」。**实施前有前端门禁兜底，实施后完全没有** → **净风险上升**。这是 D1/D3 决策的未预见后果。

### 1.6 DB 实查：高危域**没有任何权限码**

| 查询 | 结果 |
|---|---|
| `sys_permission` 总行数 | **510** |
| `permission_type` 分布 | `button` 334 / `menu` 124 / `API` 52 |
| 匹配 `gray:%` / `fiscal:%` / `risk-control:%` / `risk:%` / `hipaa:%` / `intl-payment:%` / `plugin:%` / `gdpr:%` / `pos-device:%` 的码 | **仅 3 条**：`plugin:runtime:list`(1474) / `plugin:runtime:edit`(1475) / `plugin:runtime:tenant:edit`(1476) → **且 3 条全部绑 0 角色** |
| 匹配 `pay:%` 的码 | **0 条** |
| 匹配 `dev:%` 的码 | **5 条**（`dev:ai-scheduler` / `dev:google-maps` / `dev:intl-booking` / `dev:intl-payment` / `dev:risk-control`）→ 每条**仅绑 1 个角色（`ADMIN`）** → 对 `manager01` / `clerk01` 等价于 0 |

> 📌 **重要口径**：库里现存的 `dev:risk-control` / `dev:intl-payment` / `dev:google-maps` / `dev:ai-scheduler` 是**单码粗粒度页面码**（每域 1 条），**不是** `dev:access`（该码在 `e4465d0` 中被从前端移除，且**库中本就不存在**）。
> → **方案 C（前端补回 meta.permission 用现成码）在当前库状态下对 `manager01`/`clerk01` 依然完全无效**（这 5 条只绑 ADMIN），对 `admin` 才有效。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| **R1** | **225 个端点对全体登录用户开放** | 任意登录账号（含 `clerk01`） | 数据可读；写操作同样无门槛（`@PostMapping` / `@PutMapping` / `@DeleteMapping` 同样 0 注解） |
| **R2** | **敏感域首当其冲** | — | `/risk-control`（资金风控规则）、`/fiscal` + `/fiscal/config`（财税直连 + 渠道配置）、`/intl-payment`（国际支付网关 + 充值/退款）、`/hipaa`（HIPAA 密钥列表 + 轮换）、`/gdpr`（同意记录 + 撤销）、`/plugin`（插件市场 + 安装/卸载）、`/gray`（灰度开关） |
| **R3** | **写操作无门槛 —— 已实测确认** | 任意登录账号 | 见 §1.4：`clerk01` 实测 `POST /gray/flag` + `POST /gray/flag/{key}/toggle` **成功且 DB 落库**（`enabled` 由 0 改 1、`rollout_percent` 改 50）；`POST /risk-control/rule` **成功且规则生效**（list 可见）。同类可写端点还有 `PUT /hipaa/key/{id}/rotate`（密钥轮换）、`POST /plugin/install` + `DELETE /plugin/{id}/uninstall`（插件安装/卸载）、`POST /fiscal/config`（财税渠道配置） |
| **R4** | **`/iot/device/*` 与 `/iot` 路由混合** | — | `/iot` 页「设备管理」tab（`/iot/device/list`）**无鉴权**，另 3 tab **有鉴权** → **同页内权限不一致** |
| **R5** | **与「租户隔离」是两回事** | 跨租户账号 | 本条是**角色**维度缺失；`tenant_id` 维度另由 I200/I201 覆盖（`/store/*` 等）→ 两类风险叠加 |
| **R6** | **`spa:scene:list` 等 P2 补绑定未做** | — | 属 I202 R2 家族，与本条无关 |
| **R7** | **修复若只做「加注解」而不入库不绑角色 → 变成死码** | 方案 A / D 实施时 | 与 I183/I184/I199 同型：注解码不在 `sys_permission` 或绑 0 角色 → 全部非超管 403 → 从「全开放」翻转成「全锁死」，同样不可用。**必须在同批次完成「注解 + 入库 + 绑角色」三件事** |

---

## 3. 修复方向（待决策，未实施）

| 方案 | 内容 | 规模 | 需 DB | 优点 | 缺点 |
|---|---|---|---|---|---|
| **A（推荐）** | 给 18 个 Controller 的 225 个端点补 `@RequirePermission` + 码入库 + 绑角色 | 大 | **需要** | 前后端闭环，符合 I124「同源不同名」结论 | 需逐域设计码（18 个域 225 端点），工作量大；`PermissionAutoRegistrar` 不会自动绑角色（`PermissionAutoRegistrar.java:91-92` `if (existing.contains(code)) continue` —— 只补 `sys_permission` 行，**从不写 `sys_role_permission`**） |
| **B** | 回滚 D1/D3 去 meta 决策，恢复 `dev:access` / `pay:order` 守卫码 | 小 | 需要（补 2 码入库 + 绑角色） | 立即恢复实施前状态（假门禁但数据不裸奔） | 回到「非超管 100% 进不去」的死码状态；且这 2 个码入库本身又需决策 |
| **C** | 前端补回 `meta.permission`（用**已存在**的码，如 `dev:*` 5 条 / `sys.dcep`） | 小 | 否 | 不改后端，前端先收口 | 仍是**假门禁**（后端照样可直调），治标不治本；且**实测 `dev:*` 5 条只绑 `ADMIN`**（§1.6）→ 对 `manager01` / `clerk01` 等价于「进不去」，等于换一种「误伤」 |
| **D** | 分域分期：先补高危 4 域（`/risk-control`、`/fiscal*`、`/intl-payment`、`/hipaa`）的注解，其余延后 | 中 | 需要 | 优先止血 | 剩余域仍开放 |

> 📌 **方案 C 的实测依据（2026-11-24 DB 实查）**：实测库中 `dev:*` 仅 **5 条**（`dev:ai-scheduler` / `dev:google-maps` / `dev:intl-booking` / `dev:intl-payment` / `dev:risk-control`），**每条只绑 1 个角色（`ADMIN`）** → 方案 C 对 `manager01` / `clerk01` 仍 **403**，只是把「无门禁」换成「**全员进不去**」，**不解决核心问题**。

> **建议**：**D → A**。高危 4 域先补注解与码，其余按域推进；B 作为紧急回滚备选。

### 3.1 方案 D 详述：「码从哪来」（2026-11-24 补充）

> 目标：把「加注解」拆成**三件不可分割的事** —— ① 选码名 ② `sys_permission` 入库 ③ `sys_role_permission` 绑角色。
> 缺 ③ 即 R7 死码；缺 ② 则 `PermissionAspect` 判定不通过（同样表现为全 403）。

**步骤 ①：码名建议（高危 4 域，按现有仓内命名惯例 `{域}:{资源}:{动作}` 三段式，冒号分隔，与 I124 结论一致）**

| 域 | Controller（端点数） | 建议码 | 建议绑角色 |
|---|---|---|---|
| 资金风控 | `RiskControlService`（6） | `risk:rule:list` / `risk:rule:edit` | list → `ADMIN, BOSS, MANAGER`；edit → `ADMIN, BOSS` |
| 财税直连 | `FiscalService`（6） | `fiscal:invoice:list` / `fiscal:invoice:issue` | list → `ADMIN, BOSS, MANAGER`；issue → `ADMIN, BOSS` |
| 财税渠道配置 | `FiscalConfigController`（5） | `fiscal:config:list` / `fiscal:config:edit` | list → `ADMIN, BOSS`；edit → `ADMIN`（含 `appKey`/`appSecret` 等凭据字段） |
| 国际支付 | `IntlPaymentService`（4） | `intl-payment:gateway:list` / `intl-payment:gateway:edit` | list → `ADMIN, BOSS`；edit → `ADMIN`（含充值/退款） |
| HIPAA 密钥 | `HipaaService`（8） | `hipaa:key:list` / `hipaa:key:rotate` | list → `ADMIN`；rotate → `ADMIN`（密钥轮换，**不绑 BOSS/MANAGER**） |

> 命名校验：`risk:` / `fiscal:` / `hipaa:` / `intl-payment:` 前缀**在 `sys_permission` 中均不存在**（§1.6 实查，仅 `plugin:runtime:*` 3 条存在）→ **无重名冲突**。
> ⚠ `pay:order` 前缀库中**完全不存在** → 若后续给 `pay` 域补码，不要复用 `pay:order`（会与 I202 §1.2 的「22 个缺失前端码」清单产生认知混淆），建议改用 `pay:fiscal:*` / `pay:intl-payment:*`。

**步骤 ②：`sys_permission` 入库**

- 库中 `sys_permission` 现有 **510 行**；`permission_type` 取值 `button`(334) / `menu`(124) / `API`(52) → **API 类权限码用 `permission_type='API'`**（与 `PermissionAutoRegistrar.java` 一致）。
- 表结构（`SHOW COLUMNS FROM sys_permission` 实查）：`id`(bigint, auto_increment, PK) / `permission_code`(varchar(100), **UNIQUE**) / `permission_name`(varchar(50), NOT NULL) / `permission_type`(varchar(20), default `'button'`) / `parent_id`(bigint, default 0) / `path` / `icon` / `sort_order`(int, default 0) / `status`(tinyint, default 1) / `create_time` / `update_time` / `deleted`(tinyint, default 0)。
- **插入示例（`risk:rule:list`）**：
  ```sql
  INSERT INTO sys_permission
    (permission_code, permission_name, permission_type, parent_id, sort_order, status, deleted)
  VALUES
    ('risk:rule:list',   '风控规则·查看', 'API', 0, 0, 1, 0),
    ('risk:rule:edit',   '风控规则·编辑', 'API', 0, 0, 1, 0),
    ('fiscal:invoice:list', '财税发票·查看', 'API', 0, 0, 1, 0),
    ('fiscal:invoice:issue','财税发票·开具', 'API', 0, 0, 1, 0),
    ('fiscal:config:list',  '财税渠道·查看', 'API', 0, 0, 1, 0),
    ('fiscal:config:edit',  '财税渠道·编辑', 'API', 0, 0, 1, 0),
    ('intl-payment:gateway:list','国际支付网关·查看','API', 0, 0, 1, 0),
    ('intl-payment:gateway:edit','国际支付网关·编辑','API', 0, 0, 1, 0),
    ('hipaa:key:list',  'HIPAA密钥·查看', 'API', 0, 0, 1, 0),
    ('hipaa:key:rotate','HIPAA密钥·轮换', 'API', 0, 0, 1, 0);
  ```
- 💡 **可跳过手工 INSERT**：`PermissionAutoRegistrar` 启动期会扫描所有 `@RequirePermission` 注解，缺失的码**自动插入 `sys_permission`**（`permission_type='API'`、`status=1`、`sort_order=0`；`permissionName` 取 `模块·动作` 格式，见 `PermissionAutoRegistrar.java:105-107`）。
  → **但它只管入库，不管绑角色**（`PermissionAutoRegistrar.java:91-92`）。**因此步骤 ③ 无法省略。**

**步骤 ③：`sys_role_permission` 绑角色**

- 表结构（`SHOW COLUMNS FROM sys_role_permission` 实查）：`id`(bigint, auto_increment, PK) / `role_id`(bigint, NOT NULL, IDX) / `permission_id`(bigint, NOT NULL, IDX) / `create_time`(datetime, default CURRENT_TIMESTAMP)。
- **`sys_role` 实查 8 个角色**：`id` 与 `role_code` 对应关系 —— 1=`SUPER_ADMIN`（超级管理员）、2=`STORE_ADMIN`（门店管理员）、3=`EMPLOYEE`（员工）、4=`CLERK`（店员）、5=`MANAGER`（店长）、6=`BOSS`（老板）、7=`OPERATOR`（运营）、8=`ADMIN`（系统管理员）。
- **绑定示例（`risk:rule:list` 绑 `ADMIN` + `BOSS` + `MANAGER`）**：
  ```sql
  INSERT INTO sys_role_permission (role_id, permission_id)
  SELECT r.role_id, p.id
  FROM sys_role r, sys_permission p
  WHERE r.role_code IN ('ADMIN','BOSS','MANAGER')
    AND p.permission_code = 'risk:rule:list';
  ```
- ⚠ **`SUPER_ADMIN`（role_id=1）无需绑** —— 超管持 `*` 通配码（实测 418 条含 `*`），`PermissionAspect` 直接放行。
- ⚠ **注意「绑 0 角色 = 死码」陷阱**（I183 / I184 / I199 同型）：绑定后**必须回查**确认 `COUNT(*) > 0`：
  ```sql
  SELECT p.permission_code, COUNT(srp.id) AS bound_roles
  FROM sys_permission p LEFT JOIN sys_role_permission srp ON srp.permission_id = p.id
  WHERE p.permission_code IN ('risk:rule:list','risk:rule:edit','fiscal:config:edit', ...)
  GROUP BY p.permission_code, p.id;
  ```
  期望每条 `bound_roles >= 1`；`= 0` 即为死码（表现为该角色全 403）。
- ⚠ **`ADMIN` 之外的最小权限角色（`CLERK`/`EMPLOYEE`）建议一律不绑**写类码，只在业务明确要求时绑 `list` 类码。

**步骤 ④：前端同步（方案 D 高危 4 域）**

- 方案 D 落地后，前端 `meta.permission` 需与后端注解**同码**（I124 结论：同源不同名 = 系统性错配）。
- 建议直接复用上述码名（`risk:rule:list` / `fiscal:config:list` / `intl-payment:gateway:list` / `hipaa:key:list`），**不要**复用库中只绑 ADMIN 的 `dev:*` 5 条（§1.6）。
- 若前端暂不改（保留「登录即见」），后端注解生效后页面会**数据空但能进**（I202 R6 同型）—— 需产品拍板。

---

## 4. 验收标准（修复后）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | 18 个 Controller 的 `@RequirePermission` 计数 | 从 0 提升至覆盖全部写端点 |
| 2 | `clerk01` 调 §1.3 表中 17 个端点 | 全部 **403** |
| 3 | `manager01` 调同表 | 按绑定角色区分（不应全部 403） |
| 4 | `admin` 调同表 | 全部 200 |
| 5 | 新增码入库 | `sys_permission` 有对应行 + `sys_role_permission` 有绑定（非入库不绑角色 = 死码，I183/I184 同型） |
| 6 | `PermissionAutoRegistrar` 行为 | 确认不会自动绑角色（`:91-92` `if (existing.contains(code)) continue`） |
| 7 | 写端点 | 至少全部 `@PostMapping` / `@PutMapping` / `@DeleteMapping` 带注解 |
| 8 | 写端点复测（对标 §1.4） | `clerk01` 调 `POST /gray/flag` + `POST /gray/flag/{key}/toggle` + `POST /risk-control/rule` → 全部 **403** |

---

## 5. 边界声明

- 本 Issue 结论来自**前端路由静态梳理** + **后端注解静态统计** + **运行期三账号实测**（登录载荷 + 17 个读端点 + 4 个 IoT 对照端点 + **2 个写端点含 DB 落库核验**）+ **`sys_permission` / `sys_role_permission` / `sys_role` 实查**。
- **本轮为验证「写操作无门槛」，在开发库执行了 3 次写探测**：`POST /gray/flag`（创建 1 行）、`POST /gray/flag/{key}/toggle`（更新该行）、`POST /risk-control/rule`（进程内 Map 加 1 条）。前两例的 `feature_flag` 探测行**已 DELETE 并核验回到 0 行**（操作前亦为 0 行）；第三例不落 DB，**已于本轮重启 api 容器清空**（重启后 `GET /risk-control/rule/list` 实测 `total=5`，仅剩 5 条默认规则，`I204_PROBE` 已不存在）。**未修改任何业务代码、未执行 DDL、未写 `sys_permission` / `sys_role_permission`。**
- **未新增可执行脚本**；**未改动除 §1.4 两处探测对象之外的任何业务数据**（`feature_flag` 探测行已清理并核验；`/risk-control` 探测规则已随容器重启清除）。
- **未打开浏览器**：路由守卫层（登录即见）的最终页面表现属**代码级推论**，运行期以「登录载荷 + 端点实测」双证据支撑 → 页面级仍标 **UNVERIFIED**，需人工浏览器复核。
- **敏感数据维度的实际内容泄露范围 UNVERIFIED**：本轮仅验证「端点返回 200」，**未逐字段核验响应体是否含跨域敏感数据**（如 `/hipaa/key/list` 是否回显密钥明文、`/fiscal/config/list` 是否含税号/渠道凭据、`/intl-payment/gateways` 是否含商户密钥）→ 需专项复核。
- **未核实**：生产环境 `sys_permission` / `sys_role_permission` 是否与本机一致；若生产已为这些 Controller 补过码，本条结论需重估。
- 本轮**未修改** `audit/issues/issue-register.md` 与 `audit/issues/I202-frontend-permission-code-drift-full.md`（治理基线文件，规则 18，需单独批准）—— 已在获批后同步落盘。
- 本轮**未修改** I202 / I203 的状态字段（按用户指示，待浏览器复核后再回写）。
- 对 **I202 §7 R3 拟登记文本的一处纠正**：R3 原拟称「`IoTDeviceService` 等」全部 0 注解 —— 实测 IoT **4 个 Controller 有 26 处注解**，仅 `IoTDeviceService`（10 端点）无注解。本条按实测口径登记。

---

## 6. 对 I202 §7 R3 定性的影响

I202 §7 R3 行已同步更新为「**已生效**」，并指向本条。定性要点：
- **R3 本身（去 meta → 后端无鉴权）不是新问题，而是 D1/D3 决策的兑现**；I202 原本就把它列为「潜在风险」，`e4465d0` 把它变成了**已生效的现实**。
- **净风险方向为「上升」**：实施前有前端假门禁兜底（非超管进不去页面，数据未泄露），实施后前端门禁消失 + 后端鉴权本无 → 全体登录用户可读可写。
- **与 I202 R2-2 的分工**：R2-2 是「IoT 3 个 tab 的码绑 0 角色 → 非超管 403」（**锁死**方向）；R3 / 本条是「无注解 → 全开放」（**放开**方向）。二者成因不同、修法不同，**不可合并处理**。
