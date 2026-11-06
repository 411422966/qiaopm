# I138 — 文件上传接口 500 MultipartException（LogContextFilter 全量缓存请求体破坏了 multipart 流）

> **状态**：Fixed（2026-11-06，23:44 容器重建 → 23:49 首验通过 → 23:57 证据重跑复核通过）
> **证据**：`audit/evidence/F143-multipart-upload/`（修复前堆栈 + 修复后 3 场景响应 + JSON 回归 + 大报文回归 + 前后对比 JSON + 容器日志 + README 复现步骤）
> **绑定**：基线 commit `44679f7`（修复前代码状态）/ 修复 commit `1b0f86a` / DB `meiyetec@127.0.0.1:3307`（MySQL 8.0.46）/ env `audit/evidence/env.md`、快照 `audit/evidence/db-snapshot.md`
> **关联**：I137 §P0 备注（"属独立缺陷，本批不修，需另开 issue 单" → 本单即该 issue 单）

---

## 1. 一句话总结

`log/LogContextFilter` 对**所有**请求用 `security/CachedBodyRequestWrapper` 全量缓存请求体，导致 multipart 上传请求的输入流在进入 Spring MVC 之前就被读空并关闭，后续 Multipart 解析器拿不到流 → `MultipartException` → 全局兜底成 500 系统错误。

---

## 2. 影响面

| 范围 | 说明 |
|---|---|
| 受影响端点 | `POST /biz/service/item/import`、`POST /biz/treatment/plan/import`、`POST /member/import`、`POST /dcep/.../qualification` 等**所有 multipart 上传接口** |
| 受影响角色 | **全部角色**（admin 亦 500），与权限无关 |
| 表象 | 前端弹「系统繁忙，请稍后重试」，后端日志 `ERROR ... MultipartException: Failed to parse multipart servlet request` |
| 附带缺陷 | `CachedBodyRequestWrapper` 的 256KB 截断逻辑会**静默损坏**超过 256KB 或 chunked 的 JSON 请求体（比 multipart 500 更隐蔽） |

---

## 3. 根因链

| # | 位置 | 事实 |
|---|---|---|
| 1 | `backend/src/main/java/com/meiyetec/log/LogContextFilter.java:38-40`（修复前） | 无条件 `new CachedBodyRequestWrapper(request)`，无 multipart 判断 |
| 2 | `backend/src/main/java/com/meiyetec/security/CachedBodyRequestWrapper.java:43-48`（修复前） | 构造器内 `request.getInputStream()` 全量读取并 close |
| 3 | `DispatcherServlet.checkMultipart` → `StandardMultipartHttpServletRequest.parseRequest` → `request.getParts()` | Tomcat 读到已关闭的流 → `IOException` → `MultipartException` |
| 4 | `backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java:81-86`（修复前） | `handleRuntime` 兜底 → ERROR 日志 + `Result.error(SYSTEM_ERROR)` → 表现为 500 |

**排除项**：前端写法 `headers: { 'Content-Type': 'multipart/form-data' }` **不是**元凶 —— axios 1.x 的 xhr adapter 对 `FormData` 会执行 `headers.setContentType(undefined)`（`node_modules/axios/dist/browser/axios.cjs:3361-3368`）由浏览器补 boundary；fetch adapter 亦回填带 boundary 的 content-type（同文件 `:4272-4273`）。

---

## 4. 修复内容（5 个文件）

| # | 文件 | 变更 |
|---|---|---|
| A | `backend/src/main/java/com/meiyetec/log/LogContextFilter.java` | 新增 `isCacheable` / `isMultipart`；**multipart 请求不再包装 body**；JWT 解析（TenantContext userId/username）保持不变，仍对上传请求生效 |
| B | `backend/src/main/java/com/meiyetec/security/CachedBodyRequestWrapper.java` | 新增 `MAX_BODY_SIZE` 与 `isCacheable(request)`；**删除截断逻辑**；不可缓存时构造器直接抛 `IllegalArgumentException`（fail-fast），杜绝"读一半"的损坏请求体 |
| C-1 | `backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java` | 新增 `@ExceptionHandler(MultipartException.class)` → `code=400, message="文件上传失败，请重新选择文件上传"`，日志降为 WARN |
| C-2 | `backend/src/main/java/com/meiyetec/service/BizServiceItemService.java` | `importItem` 包裹 `XlsxExportUtil.importRows` → 解析失败返回 `code=400, message="文件格式错误，请使用 .xlsx 模板文件上传"`（WARN 日志） |
| C-2 | `backend/src/main/java/com/meiyetec/service/BizTreatmentService.java` | `importPlan` 同样处理（同一份 `XlsxExportUtil.importRows` 调用方，同一缺陷） |

### 隔离强度论证（放行 multipart 后不变弱）

| 关注点 | 结论 |
|---|---|
| 日志记录 | `LogAspect` 记录的是 controller 方法参数，multipart 场景 body 本就为 `null`（`LogAspect.java:148-151` 已排除 `MultipartFile`）；URL / 方法 / IP / 耗时 / 操作人 / traceId 均不依赖 body 包装 |
| 租户上下文 | `LogContextFilter` 的 JWT 解析读 **header**，与 body 包装无关 → 上传请求仍写入 `TenantContext.userId/username` |
| 数据范围拦截 | `DataScopeInterceptor.matchLongFromBody` 对非 JSON content-type 本就直接返回 null（`:123`）；multipart 的租户/门店参数本来就取不到，**隔离强度不变**；`X-Tenant-Id` / `X-Store-Id` header 与 query 参数仍照常校验 |
| 解析时机 | `DataScopeInterceptor` 是 `HandlerInterceptor`，运行在 `DispatcherServlet.checkMultipart` 之后，此时 Tomcat 已缓存 parts，调用 `getParameter` 安全 |

---

## 5. 验证结果（运行时，2026-11-06 23:44–23:49）

容器：`meiyetec-api`（镜像 `meiyetec/api:dev`，重建后 healthy）。

| 场景 | 修复前 | 修复后 |
|---|---|---|
| 上传 0 字节文件 | 500 `MultipartException` | `code=400, message="请求参数错误: 请上传文件"` |
| 上传非 Excel 内容的 .xlsx | 500 `MultipartException` | `code=400, message="请求参数错误: 文件格式错误，请使用 .xlsx 模板文件上传"` |
| 上传真实模板（编码改为唯一值） | 500 `MultipartException` | `code=200, message="导入完成，成功 2 条，失败 0 条"` |
| 容器日志中 `MultipartException` 计数 | — | **0** |
| JSON 回归 `POST /biz/service/item`（字段类型正确） | — | `code=200 创建成功`（id=40） |
| JSON >256KB（300 146 字节） | 截断 → 解析失败 | `code=200 创建成功`（id=41） |
| JSON >256KB 且写入 `description varchar(500)` | 截断 → 解析失败 | `code=500 数据库异常`（字段超长，说明 body 已完整到达 DB 层，而非被截断后解析失败） |

> **二次复核（23:54–23:57，同一修复后容器）**：初次落盘的证据文件存在 UTF-8 编码损坏（中文被截断为 `?`），已用 `curl -o` 原始字节重写全部响应文件，并新增 `after-05-json-over-256kb.json` / `after-05b-json-over-256kb-field-too-long.json` / `after-06-container-logs.txt` / `after-07-before-after-compare.json` / `README.md`。结论与首次验证一致，`MultipartException` 计数仍为 0。
>
> **证据索引与复现步骤**：见 `audit/evidence/F143-multipart-upload/README.md`。

验证产生的服务项目（id=34/35/38/39/40/41，编码 `F143SV001`~`F143SV004` / `F143JSON001` / `F143JSONBIG003`）已通过业务接口 DELETE 软删除。

⚠️ 附带发现：`biz_service_item.uk_tenant_code` 唯一键为 `(tenant_id, service_code)`，**不含 `deleted`** → 应用层唯一性校验（MyBatis-Plus 逻辑删除自动过滤 `deleted=0`）认为编码可用，DB 层却报 `Duplicate entry`。这是**独立缺陷**，本次未修（见 §8）。

---

## 6. 安全折衷（**已知约束，未来审计请复核**）

`Content-Length` 未知（chunked 传输）或超过 256KB 的请求**不再缓存 body**，因此：

- `DataScopeInterceptor` 无法从这类 JSON body 读取 `tenantId` / `storeId`；
- **退回只校验 header（`X-Tenant-Id` / `X-Store-Id`）与 query 参数**。

**风险评估：可接受。** 理由：

1. 多租户隔离**不依赖 body** —— body 里的 `tenantId` / `storeId` 本就不可信，正式数据范围以 Token 解析出的 `RbacContext`（`dataScope` / `storeIds`）为准；
2. 修复前该场景更糟：超限请求被**截断**成非法 JSON → 直接解析失败（静默故障），现在至少请求能被正常处理；
3. 受影响面极小：正常前端 axios / nginx 均为 `Content-Length` 已知且远小于 256KB。

---

## 7. 修复记录

| 时间 | 动作 |
|---|---|
| 2026-11-06 23:38 | 用户批准变更 A / B / C-1 / C-2，并要求 `BizTreatmentService` 一并修、证据目录用 `F143-multipart-upload` |
| 2026-11-06 23:42 | `mvn "-Dmaven.test.skip=true" package` → BUILD SUCCESS（1547 源文件） |
| 2026-11-06 23:44 | `docker compose build api` + `docker compose up -d api` → 容器 healthy |
| 2026-11-06 23:47 | 三场景 curl 验证通过，日志 `MultipartException` 计数 0 |
| 2026-11-06 23:49 | JSON 回归 + >256KB 大报文回归通过；测试数据已清理 |
| 2026-11-06 23:53 | 复核发现证据文件 UTF-8 编码损坏，重跑三场景 + JSON 回归，证据以原始字节重写 |
| 2026-11-06 23:56 | 三场景复跑通过（空文件 400 / 非 Excel 400 / 真模板 200 成功 2 条） |
| 2026-11-06 23:57 | JSON 回归 + 300KB 大报文回归通过；验证数据软删清理 |
| 2026-11-06 23:58 | 证据落盘 `audit/evidence/F143-multipart-upload/`（含 README.md 复现步骤）；提交 `1b0f86a` fix(security): 修复 Multipart 上传 500，优化 body 缓存策略（I138） |

---

## 8. 遗留 / 未在本单处理

| 项 | 说明 |
|---|---|
| `MemberController.importCsv`（`POST /member/import`） | CSV 解析异常未捕获，仍会落到 500 兜底（本单只修了 XLSX 的两个调用方） |
| `DcepMerchantQualificationController` 上传 | 无文件格式校验 |
| `biz_service_item.uk_tenant_code` 唯一键不含 `deleted` | 逻辑删除后编码仍被占用：应用层校验（`selectCount` 自动过滤 `deleted=0`）放行，DB 层报 `Duplicate entry '1-F143SV001'` → 导入结果 0 成功 2 失败。**本单未修**（属 schema 设计与逻辑删除的通用冲突，建议另开 issue 统一评估所有 `uk_*` 唯一键是否需带 `deleted`） |
| `POST /biz/treatment/plan/import` 未做 curl 验证 | `BizTreatmentService.importPlan` 与本单修的 `importItem` 共用 `XlsxExportUtil.importRows`、改动完全同构（同样的 try/catch + 400 返回），但**未做运行时 curl 验证** → 状态 UNVERIFIED |
| chunked 传输（`Content-Length` 未知）分支未做运行时验证 | 当前 HTTP 客户端与 nginx 默认均带 `Content-Length`，该分支仅经代码路径推演 → 状态 UNVERIFIED（详见 §6） |
| `audit/issues/issue-register.md` | **受保护文件**，本单未写入（如需登记本单状态请单独批准） |
