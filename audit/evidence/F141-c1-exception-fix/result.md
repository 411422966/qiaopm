# F141 — C1 异常处理改造：全量 GET 端点 500 收敛（74 → 11）

> **调查方式**：运行时实测（HTTP 全量 GET 扫描，865 端点，扫描前后各一轮）+ 只读静态分析（源码逐行定位）。
> **本目录性质**：原始证据留痕包。所有结论必须能回指到本目录内的文件或下文列出的源码行号。
> **重要限制**：本包只覆盖 **GET** 端点扫描。POST / PUT / DELETE 端点未纳入本轮扫描，非 GET 路径上的同类 `Result.error(String)` 缺陷**未验证**。

---

## 0. 环境绑定

| 项 | 值 | 来源 |
|---|---|---|
| 改造前 commit | `5c43426`（2026-11-01 22:59:00 +0800，F140 终态落盘） | `git log` |
| 改造后 commit | `210a4d0`（2026-11-02 00:42:21 +0800，C1 异常处理改造） | `git log` |
| 变更文件 | 仅 `backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java`（+23 / −2） | `git show 210a4d0 --stat` |
| DB | `127.0.0.1:3307/meiyetec`（MySQL 8.0.46，容器 `meiyetec-mysql`） | `audit/baseline/env.md` |
| DB snapshot | `backup/meiyetec_20261101_020004.sql`（18,446,058 B，2026-11-01 02:00 全量 dump，扫描前最近一次） | 本仓库 `backup/` |
| env | `audit/baseline/env.md`（HEAD 字段仍为 `656caa7`，已滞后，本次未修改） | `audit/baseline/` |
| 运行构件 | 容器 `meiyetec-api`（image `meiyetec/api:dev`，扫描期健康） | `docker ps` |
| 扫描基址 | `http://localhost:8080`（admin / 123456 登录取 token） | 扫描脚本内硬编码 |
| 探测脚本（非正式工具） | `sweep-c1-after.js`（含"非正式工具"文件头，不进 `audit/scripts/`、不接 CI） | 本目录 |

---

## 1. 扫描口径与前后对比

两次扫描使用**同一脚本、同一 URL 集合、同一账号**，URL 集合已校验完全一致（`onlyA=0 / onlyB=0`，865 个）。

| 项 | 改造前 | 改造后 |
|---|---|---|
| 扫描端点数 | 865 | 865 |
| **code=500 端点数** | **74** | **11** |
| 扫描时间戳 | `2026-11-01T14:26:48.660Z` | `2026-11-01T16:24:20.018Z` |

改造后 code 分布（`c1-diff-summary.json` → `after.codeDist`）：

| body code | 改造前 | 改造后 | 说明 |
|---|---|---|---|
| 200 | 642 | 642 | 正常业务响应 |
| 400 | 1 | **64** | 缺参 / 类型错误统一 400（C1 目标） |
| 404 | 82 | 82 | 业务 NOT_FOUND（HTTP 200 + code 404） |
| 500 | **74** | **11** | 见 §2 / §3 |
| 空（HTTP 404 无 handler） | 46 | 46 | 与本次改造无关 |
| n/a（非 JSON：CSV/Excel 下载） | 20 | 20 | 与本次改造无关 |

**净变化：74 → 11，减少 63；新增 500 = 0；404 / 非 JSON / 无 handler 端点数完全不变。**

原始数据：`sweep-c1-before.json`（改造前）、`sweep-c1-after.json`（改造后）、`c1-diff-summary.json`（逐条 diff）。

---

## 2. C1 改造内容与生效路径

改造点：`backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java`

| 异常 | 改造前 | 改造后 | 对应扫描端点数 |
|---|---|---|---|
| `MissingServletRequestParameterException` | 无 handler → 落到 `handleAny(Exception)` → code 500「系统繁忙」 | `handleMissingParam` → code **400**「缺少必填参数: {name}」 | 62 |
| `MethodArgumentTypeMismatchException` | 无 handler → code 500 | `handleTypeMismatch` → code **400**「参数类型错误: {name}」 | 1 |
| `HttpMessageNotReadableException` | 无 handler → code 500 | `handleUnreadableBody` → code **400**「请求体格式错误，无法解析」 | 本轮扫描未命中（GET 无 body） |
| `MethodArgumentNotValidException` | 无 handler → code 500 | `handleValidation` → code **400** | 本轮扫描未命中 |

改造后新增 64 个 400 端点，与改造前消失的 63 个 500 端点一一对应（差额 1 = 改造前本就存在的 1 个 400 端点）。

**63 个被修复端点全清单**（改造前 code500 → 改造后 code400），逐条见 `c1-diff-summary.json` → `fixed[]`：

```
/ai/recommend                          /attendance/leave/my               /attendance/leave/pending-count
/attendance/monthly-report             /attendance/today                   /biz/booking/member-credit
/biz/booking/resource/available        /biz/booking/resource/available-slots
/biz/booking/resource/check-conflict   /blockchain/record/1548             /bnpl/credit
/booking/available                     /cloud/discovery                    /cloud/health/recent
/complaint/escalate/list               /crm/care/loop/note/list            /crm/care/loop/task/my-today
/crm/care/loop/touch/list              /crm/cross/member/lookup            /crm/cross/rule/preview-match
/crm/cross/rule/test                   /crm/package/share/list             /dcep/offline/detect-double-spend
/device/list                           /device/session/list                /es/event/replay
/es/snapshot/latest                    /exp/1548/assign                    /frontend/mp/latest
/frontend/mp/version/list              /gray/experiment/1548/assign        /gray/experiment/stats
/gray/flag/1548/evaluate               /huabei/credit                      /i18n/bundle
/intel/forecast/list                   /intel/price-sensitivity/curve       /iot/data/member/1548/trend
/kiosk/stats/staff-trend               /mapi/booking/slots                 /mapi/member/card
/member/message/unread-count           /mkt/preference/by-type             /mkt/preference/recommend
/mkt/preference/summary                /mkt/preference/top                 /monitor/metric/recent
/open/api/v2/quota/status              /open/api/v2/secret/audit           /overseas-payment/payment/select-gateway
/pos/experiment/quote                  /push/record/list                    /report/export/download
/resource/schedule/check-conflict      /resource/schedule/suggest           /schedule/ai/list
/sla/uptime/list                       /sync/member/list                   /sys/log/context
/technician/level/promote/evaluate     /wallet/promotion/calc              /wallet/transactions
/wx/booking/list
```

---

## 3. 剩余 11 个 code=500（C2 范畴，本次未处理）

改造后仍有 **11** 个 GET 端点返回 body `code=500`。逐条证据见 `c1-diff-summary.json` → `still500[]`（含 traceId）。

| # | URL | Controller | 当前 message | 触发条件 |
|---|---|---|---|---|
| 1 | `GET /booking/1548` | `BookingController` | 预约不存在 | id 不存在 |
| 2 | `GET /member-level/1548` | `MemberLevelController` | 等级不存在或无权访问 | id 不存在或跨租户 |
| 3 | `GET /packages/1548` | `PackageController` | 套餐不存在 | id 不存在 |
| 4 | `GET /pos/orders/1548` | `PosController` | 订单不存在 | id 不存在 |
| 5 | `GET /pos/services/1548` | `PosController` | 服务不存在 | id 不存在 |
| 6 | `GET /report/v2/task/1548/download` | `ReportV2Service` | 系统繁忙，请稍后重试 | id 不存在 → `throw new RuntimeException` 被 `handleRuntime` 兜成 500 |
| 7 | `GET /stock/check/1548` | `StockController` | 盘点单不存在 | id 不存在或跨租户 |
| 8 | `GET /stock/products/1548` | `StockController` | 商品不存在 | id 不存在或跨租户 |
| 9 | `GET /stock/trace/1548` | `StockController` | 商品不存在 | id 不存在或跨租户 |
| 10 | `GET /sys/tenant/1548/stats` | `SysTenantController` | 租户不存在 | id 不存在 |
| 11 | `GET /technician/level/benefit/1548` | `LevelBenefitController` | 权益不存在或无权访问 | id 不存在或跨租户 |

**根因**：这些端点用 `Result.error(String)` 返回"记录不存在"。该重载在 `util/Result.java:44-49` 硬编码 `code = 500`，把业务语义「不存在」错误表达成系统故障。

**同时暴露的 C2 全量规模**（静态统计，源码 `Result.error("...不存在...")` 计数）：

| 文件 | 处数 |
|---|---|
| `controller/BookingController.java` | 12 |
| `controller/PosController.java` | 11 |
| `controller/PackageController.java` | 11 |
| `controller/MemberController.java` | 8 |
| `controller/StockController.java` | 5 |
| `controller/LevelBenefitController.java` | 5 |
| `controller/MemberLevelController.java` | 3 |
| `controller/SysTenantController.java` | 2 |
| `controller/MemberMessageController.java` | 1 |
| `controller/SysLogController.java` | 1 |
| **合计** | **59** |

本轮 GET 扫描只命中其中 11 处；其余 48 处位于 POST / PUT / DELETE 路径或未被 GET 扫描触发的分支，**当前状态 UNVERIFIED**。

---

## 4. 本包无法确认的项

| 项 | 原因 |
|---|---|
| 非 GET 端点上的同类 500 | 本轮扫描脚本只发 GET（`sweep-c1-after.js` 仅枚举 `@GetMapping`），POST/PUT/DELETE 上的 48 处 `Result.error("...不存在...")` 未验证 |
| 11 个剩余端点改造后的响应形态 | 本包只含改造前证据；改造后需重跑扫描并另行归档 |
| 400 契约对前端的影响 | 前端 `frontend/meiyetec-web/src/api/request.ts:88-100` 对 `res.code !== 200` 统一 `ElMessage.error` + reject，`400` 与原 `500` 在前端表现一致（均弹错误提示），但**具体文案由后端 message 决定**，需人工在浏览器确认弹窗文案可读 |
| `env.md` 的 HEAD 字段 | 仍为 `656caa7`，与本次验证 commit `210a4d0` 不一致（治理基线滞后，本次未修改） |
| DB 是否有并发写入影响本轮结果 | 扫描为只读 GET，未验证 DB 在扫描窗口内是否有其他写入 |

---

## 5. 结论

| 项 | 状态 |
|---|---|
| C1（缺参 / 类型错误 → 400）改造已落地并生效 | **VERIFIED**（运行时实测：74 → 11，且新增 500 = 0） |
| 剩余 11 个 C2（业务"不存在" → code 500） | **PARTIAL-NON-BLOCKER**（已定位到源码行，本包未改造） |
| 48 处非 GET 路径的同类 C2 | **UNVERIFIED**（本轮扫描口径未覆盖） |
