# I190 — 全局异常处理把「客户端错误」统一吞成「系统繁忙」：`GlobalExceptionHandler.handleAny` 捕获全部 `Exception` → HTTP 200 + code 500

> **状态**：**Open**（**仅登记**，**未修改任何业务代码、未写库**）
> **严重度**：**P2**（契约漂移 + 故障淹没；不影响业务数据正确性，但**全站**的 4xx 语义失真，且监控/网关按 HTTP 状态码判断时会误判成功）
> **发现日期**：2026-11-16（batch2-C 第 1 批只读调查，由 I189 附带发现拆出）
> **登记人**：Cursor
> **绑定基线**：HEAD `56d59a2`
> **验证等级**：**部分 VERIFIED** —— 405 路径的「HTTP 200 + code 500」已**运行期实测 VERIFIED**（I189 §1.4，`raw/06b` C 段末行）；415 / 406 / 404 三项为**静态 VERIFIED**（handler 缺位可查），运行期**未逐一探测**
> **DB 快照**：`backup/meiyetec_20261116_020004.sql`
> **证据**：`audit/evidence/F171-attendance-commission-perm-alignment/`（`raw/06b` C 段末行）、I189 §1.4
> **关联**：**I189**（405 特判已决定纳入 I189 本批修复）、I134（缺失参数返回 500）、I088 / I095（catch 吞异常同族）、`SpaFallbackController`（404 语义已有一层接管）

---

## 0. 一句话描述

`GlobalExceptionHandler.handleAny`（`@ExceptionHandler(Exception.class)`）捕获**所有**未被特判的异常，一律返回
**HTTP 200 + body `code=500 "系统繁忙，请稍后重试"`**；
于是 **405 / 415 / 406 / 404 等纯客户端错误**全部伪装成「服务端内部错误」——
依赖 HTTP 状态码的网关重试策略、监控告警、SRE 排障都会误判。

---

## 1. 事实核实

### 1.1 现有异常处理器清单（静态）

`backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java`（`@RestControllerAdvice`，`:26`）：

| 行号 | 捕获类型 | 返回 HTTP | body code | 语义正确性 |
|---|---|---|---|---|
| `:29-35` | `BusinessException` | `code` 落在 [400,500) 时取该值，否则 **200** | `e.getCode()` | ✅ 4xx 正确；**5xxx 业务码（如 5001）也走 HTTP 200** ⚠️ |
| `:37-43` | `MethodArgumentNotValidException` | 200 | 400 | ⚠️ HTTP 与 body code 不一致 |
| `:45-49` | `MissingServletRequestParameterException` | 200 | 400 | ⚠️ 同上（I134 已登记） |
| `:51-55` | `MethodArgumentTypeMismatchException` | 200 | 400 | ⚠️ 同上 |
| `:57-61` | `HttpMessageNotReadableException` | 200 | 400 | ⚠️ 同上 |
| `:67-71` | `MultipartException` | 200 | 400 | ⚠️ 同上 |
| `:73-77` | `OptimisticLockingFailureException` | 200 | 409 | ⚠️ 同上 |
| `:79-83` | `DataAccessException` | 200 | 500 | ⚠️ DB 故障也报 200 |
| `:85-89` | `RuntimeException` | **200** | 500 | ⚠️ 同上 |
| **`:91-95`** | **`Exception`（兜底）** | **200** | **500** | ⚠️ **本 Issue 的根** |

```java
91: @ExceptionHandler(Exception.class)
92: public ResponseEntity<Result<Void>> handleAny(Exception e) {
93:     log.error("[SystemException]", e);
94:     return ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR));
95: }
```

> `ResponseEntity.ok(...)` = **HTTP 200 恒定**；`BizCode.SYSTEM_ERROR` = `code 500 / "系统繁忙，请稍后重试"`。

### 1.2 未被特判、因而全部落进兜底的 4xx 家族

| 异常 | HTTP 语义 | 当前实际返回 | 触发条件（示例） |
|---|---|---|---|
| `HttpRequestMethodNotSupportedException` | **405** | **200 + code 500** | 路径命中但方法不对（如 `GET /attendance/leave/99999999/cancel`，该路径只注册了 `PUT`） |
| `HttpMediaTypeNotSupportedException` | **415** | 200 + code 500 | `Content-Type` 不被接受（如给 JSON 端点发 `text/plain`） |
| `HttpMediaTypeNotAcceptableException` | **406** | 200 + code 500 | `Accept` 头不可满足 |
| `NoHandlerFoundException` / `NoResourceFoundException` | **404** | **取决于下述冲突**（见 §1.4） | 路由完全未命中 |
| `MissingRequestHeaderException` | 400 | 200 + code 500 | 缺必填请求头 |
| `MissingPathVariableException` | 400/500 | 200 + code 500 | 路径模板变量缺失 |
| `HandlerMethodValidationException` | 400 | 200 + code 500 | 方法级参数校验失败（Spring 6.1+） |

### 1.3 运行期已 VERIFIED 的实例（405）

```
ADMIN [GET] /attendance/leave/99999999/cancel
  => HTTP 200 {"code":500,"message":"系统繁忙，请稍后重试","data":null,"traceId":"42925414fbbe49429942064f44f3e7f5","timestamp":"2026-11-16 03:10:06"}
```

- 真实异常：`HttpRequestMethodNotSupportedException`（`LeaveRequestController.java:54` 只注册了 `PUT`）。
- `99999999` 为**确认不存在的兜底 ID**（`attendance_leave_request` 表 0 行），本次为 **GET**，**无写副作用**。
- ⚠️ **假成功语义**：HTTP 200 会让依赖状态码的重试策略 / 监控告警**误判为成功**；只有解析 body 的前端才能察觉。

### 1.4 404 存在「两层 Advice 抢同一异常」的冲突（**本条最需要先决策**）

| 位置 | 行为 |
|---|---|
| `SpaFallbackController.java:59-64`（`@ControllerAdvice` 内嵌 `SpaFallbackExceptionHandler`） | `@ExceptionHandler(NoHandlerFoundException.class)` → 返回 **HTTP 200 + SPA HTML**（`SPA_FALLBACK_HTML`） |
| `GlobalExceptionHandler.java:91-95` | 兜底捕获 `Exception` → HTTP 200 + code 500 |

- `SpaFallbackController` 的类注释明确写着「Combined with `spring.mvc.throw-exception-if-no-handler-found=true`」，
  但**该属性在本仓未显式配置**：`application.yml` / `application-prod.yml` **均无 `spring.mvc` 段**（已 grep 确认）→
  运行期未命中路由时是抛 `NoHandlerFoundException`（→ SPA HTML 200）还是交给 `DefaultHandlerExceptionResolver`（→ HTTP 404），
  **需运行期一条 GET 确认（本批未探测）**。
- ⚠️ **不能简单在 `GlobalExceptionHandler` 里再加一个 `NoHandlerFoundException` 特判** —— 两个 `@ControllerAdvice` 同时声明同一异常处理器时，
  Spring 按 advice 顺序**只生效一个**，结果不确定；且一旦生效，**SPA 兜底会失效**，直接影响 `/dashboard` 等前端路由的后端直连兜底能力。
- 📌 **约束**：`/dashboard`、`/mh/visual`、`/mh/consent` 三个页面已由用户浏览器实测通过 → **SPA fallback 行为不得改动**。

### 1.5 与 `TraceResponseAdvice` 的关系

`TraceResponseAdvice`（`@RestControllerAdvice(basePackages = "com.meiyetec")`）给响应体补 `traceId` / `timestamp`；
任何新增的 4xx 特判若**不返回 `Result` 结构**，将丢掉这两个字段 → 前端排障链断裂。

---

## 2. 风险清单

| # | 风险 | 触发条件 | 影响 |
|---|---|---|---|
| R1 | **4xx 伪装成 5xx** | 405 / 415 / 406 / 400 类错误 | 客户端按 body 判定尚可，**网关 / 监控按 HTTP 判定会误判成功** |
| R2 | **真实故障被淹没** | 任何未特判异常（含 500 类真故障） | 一律提示「系统繁忙」，排障时无法从响应面区分「客户端用错」与「服务端炸了」 |
| R3 | **重试策略误伤** | 上游按 5xx 触发重试 | 405 这类**不可重试**错误被判 5xx → 无效重试放大流量 |
| R4 | **404 语义摇摆** | 未命中路由 | 可能是 200 + SPA HTML（SEO/监控失真）、404、或 200 + code 500 —— 三种行为并存 |
| R5 | **Advice 冲突** | 盲目加 404 特判 | SPA fallback 被抢占 → 前端路由直连兜底失效，波及**已验收页面** |

---

## 3. 建议方案（**仅建议，本批不实施**）

按用户决策 2026-11-16：**405 特判纳入 I189 本批**（作为其必要组成），**415 / 406 / 404 与 `handleAny` 收窄拆为 I190 单独批次**。

### 3.1 I189 本批（与 I189 一并落地）

```java
// GlobalExceptionHandler 新增（草案）
@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(Result.error(405, "请求方法不支持：" + e.getMethod()));
}
```

- `Result.error(int, String)` 存在于 `util/Result.java:51`；`BizCode` 暂无 405 枚举 → 可先用字面量，或新增 `METHOD_NOT_ALLOWED(405, "请求方法不支持")`。
- 响应体仍是 `Result` 结构 → `TraceResponseAdvice` 的 `traceId` / `timestamp` 保留。

### 3.2 I190 单独批次（建议顺序）

| # | 步骤 | 内容 | 备注 |
|---|---|---|---|
| 1 | **先跑一条运行期探测** | ① 未命中路由的 GET → 看是 200 + SPA HTML / 404 / 200 + code 500；② `Content-Type: text/plain` 打 JSON 端点 → 看 415 表现 | 只读 GET，无副作用；结论决定第 3 步怎么做 |
| 2 | **补 415 / 406 特判** | `HttpMediaTypeNotSupportedException` → HTTP 415；`HttpMediaTypeNotAcceptableException` → HTTP 406 | 与 405 同款写法 |
| 3 | **404 分流（**必须**先定口径）** | 二选一：<br>**方案 α（推荐，改动小）**：**不动** `SpaFallbackController`，只在 `GlobalExceptionHandler` 里处理**非 `NoHandlerFoundException`** 的 404 类异常（`NoResourceFoundException`），保持 SPA 兜底原样；<br>**方案 β（语义更正，改动大）**：按路径前缀分流（`/api`、`/auth`、各业务前缀 → 真 404；其余 → SPA HTML），需引入路径前缀清单 | **方案 β 会影响已验收页面的兜底路径 → 需用户单独批准** |
| 4 | **收窄 `handleAny` 的语义** | 至少把「未知异常」的 HTTP 状态码从 200 纠正为 **500**（body 仍是 `code=500`），使「服务端故障」与「客户端错误」在 HTTP 层可区分 | body 结构不变 → 前端按 `code` 判断的逻辑零改动；需回归全站异常路径 |
| 5 | **`BusinessException` 的 5xxx 分支** | `:32` 的 `code >= 400 && code < 500` → 5xxx 业务码也返回 HTTP 200；可考虑 5xxx 一律 HTTP 500，或保持 200 但在响应头标注 | 影响面全站，需单独回归 |
| 6 | **补 `BizCode` 枚举** | 新增 `METHOD_NOT_ALLOWED(405)` / `UNSUPPORTED_MEDIA_TYPE(415)` / `NOT_ACCEPTABLE(406)` / `NOT_FOUND(404)`（后三者部分已存在语义） | 枚举变更属低风险，但会改变 body `code` 取值来源 |

---

## 4. 验证口径（实施后需满足）

| # | 检查项 | 期望 |
|---|---|---|
| 1 | `GET /attendance/leave/99999999/cancel`（admin） | **HTTP 405**，body `code=405`，message 含「请求方法不支持」，**不再出现「系统繁忙」** |
| 2 | `GET /attendance/leave/99999999/cancel` 的 body | 保留 `traceId` / `timestamp` 字段 |
| 3 | 以 `Content-Type: text/plain` 打一个 JSON 端点 | **HTTP 415**（非 200 + 500） |
| 4 | 以 `Accept: application/xml` 打一个 JSON 端点 | **HTTP 406** |
| 5 | 未命中路由的 GET | 按 §3.2 第 3 步选定的口径执行，且**与 SPA 兜底结论一致**（不得出现「同一路径两种语义」） |
| 6 | 构造一个真实服务端异常（如非法 SQL） | **HTTP 500** + body `code=500`，日志出现 `[SystemException]` |
| 7 | `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面 | **后端直连兜底行为不变**（用户已实测通过，不得回归） |

---

## 5. 边界声明

- 本 Issue 结论来自**静态代码分析**（`GlobalExceptionHandler.java` / `SpaFallbackController.java` / `TraceResponseAdvice.java` / `application*.yml`）+ **一次运行期只读 GET 探测**（I189 §1.4 的 405 实例）。
- **未修改任何业务代码**、**未执行任何写操作**、**未执行任何 DDL**、**未新增任何入库脚本**。
- ⚠️ **运行期未逐一探测**：415 / 406 / 404 三项**只有静态证据**（handler 缺位可查），运行期表现**标 UNVERIFIED**，需按 §3.2 第 1 步补探测。
- ⚠️ **未核实**：`spring.mvc.throw-exception-if-no-handler-found` 在本仓的**实际生效值**（`application.yml` / `application-prod.yml` 均无 `spring.mvc` 段）。
- ⚠️ **未核实**：`SpaFallbackExceptionHandler` 与 `GlobalExceptionHandler` 同时存在时，Spring 实际选择哪个 advice（未做冲突注入测试）。
- 兜底 ID 说明：`99999999` 为**确认不存在**的兜底 ID（`attendance_leave_request` 表 0 行），本次为 **GET**，**无写副作用**；**未使用任何真实业务主键**作为「不存在」样本。
- 未触达 `/dashboard`、`/mh/visual`、`/mh/consent` 三个已验收页面。

---

**下一步**：I189 本批只做 405 特判；415 / 406 / 404 与 `handleAny` 收窄在本 Issue 单独排期 —— 需先完成 §3.2 第 1 步的运行期探测，并就 §3.2 第 3 步的 **404 口径**取得用户拍板。
