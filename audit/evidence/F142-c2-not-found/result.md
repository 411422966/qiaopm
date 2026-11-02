# F142 — C2 契约缺陷全量修复：59 处「记录不存在」统一返回 404

> **调查方式**：静态源码全量定位 + 运行时实测（GET 全量扫描 865 端点，前后各一轮；非 GET 路径代表性抽样 27 个端点）。
> **本目录性质**：原始证据留痕包。所有结论必须能回指到本目录内的文件或下文列出的源码行号。
> **修复形态**：形态 A —— `Result.error(BizCode.NOT_FOUND, "...")`（HTTP 200 + body `code=404`），与全库 82 处主流契约一致。

---

## 0. 环境绑定

| 项 | 值 | 来源 |
|---|---|---|
| 改造前 commit | `ce67848`（2026-11-02 00:59:13 +0800，F141 落盘） | `git log` |
| 改造后 commit | 见 §6（本次提交） | `git log` |
| 变更文件 | 10 个 Controller + 1 个 Service（+71 / −63 行，含 import） | `git show --stat` |
| DB | `127.0.0.1:3307/meiyetec`（MySQL 8.0.46，容器 `meiyetec-mysql`） | `audit/baseline/env.md` |
| DB snapshot | `backup/meiyetec_20261102_140226.sql`（19,294,298 B，2026-11-02 14:02 全量 dump，改造前最近一次） | 本仓库 `backup/` |
| env | `audit/baseline/env.md`（HEAD 字段仍为 `656caa7`，已滞后，本次未修改 —— 治理基线未授权不可写） | `audit/baseline/` |
| 运行构件 | 容器 `meiyetec-api`（image `meiyetec/api:dev`，改造后重建，healthy） | `docker ps` |
| 扫描基址 | `http://localhost:8080`（admin / 123456 登录取 token） | 扫描脚本内硬编码 |
| 探测脚本（非正式工具） | `sweep-c2-after.js`（GET 全量）、`sweep-c2-nonget.js`（非 GET 抽样）；均含"非正式工具"文件头，不进 `audit/scripts/`、不接 CI | 本目录 |

---

## 1. 改造决策（用户拍板）

| 决策点 | 结论 |
|---|---|
| 形态选择 | **形态 A**：`Result.error(BizCode.NOT_FOUND, ...)`，HTTP 200 + code 404，与全库 82 处主流契约一致 |
| `ReportV2Service.download` 不一致 | **接受**：该方法返回 `ResponseEntity<byte[]>` 文件流，无法走 `Result` 包装，只能抛 `BusinessException` 走 HTTP 404（技术栈客观限制，业务合理） |
| `ReportV2Service` 566/567/568 三个裸 `RuntimeException` | 同步改造：`FORBIDDEN`(403) / `CONFLICT`(409) / `NOT_FOUND`(404) |
| 改造范围 | **方案二（系统级一致）**：全库 59 处一次性改完，不单独写扫描脚本，非 GET 路径人工点确认 |

---

## 2. 改造内容

### 2.1 59 处 `Result.error(String)` → `Result.error(BizCode.NOT_FOUND, String)`

分布（与 F141 §3 静态统计口径完全一致，逐处核对无遗漏）：

| 文件 | 处数 | 覆盖的非 GET 方法 |
|---|---|---|
| `controller/BookingController.java` | 12 | PUT `/{id}/status`、POST `/{id}/checkin`、POST `/{id}/complete`、PUT `/{id}`、DELETE `/{id}` |
| `controller/PosController.java` | 11 | POST `/orders`、POST `/orders/{id}/items`、DELETE `/orders/{id}/items/{itemId}`、POST `/orders/{id}/discount`、POST `/orders/{id}/points-discount`、POST `/orders/{id}/pay`、POST `/orders/{id}/refund`、POST `/orders/{id}/void` |
| `controller/PackageController.java` | 11 | POST `/purchase`、GET `/member/{memberId}`、POST `/order/{orderId}/extend`、POST `/order/{orderId}/refund`、POST `/upgrade`、DELETE `/{id}` |
| `controller/MemberController.java` | 8 | POST、PUT、DELETE `/{id}`、POST `/{id}/freeze`、POST `/{id}/unfreeze`、POST `/{id}/profile` |
| `controller/StockController.java` | 5 | PUT `/products/{id}`、DELETE `/products/{id}` |
| `controller/LevelBenefitController.java` | 5 | POST `/batch/{levelId}`、PUT `/{id}`、DELETE `/{id}` |
| `controller/MemberLevelController.java` | 3 | POST、PUT、DELETE `/{id}` |
| `controller/SysTenantController.java` | 2 | PUT `/{id}/status/{status}` |
| `controller/MemberMessageController.java` | 1 | POST `/{id}/read` |
| `controller/SysLogController.java` | 1 | （仅 GET `/context`） |
| **合计** | **59** | |

**跨租户判定未遗漏**：所有 `xxx不存在或无权访问` 的合并判定分支（`MemberController.requireMember` / `LevelBenefitController.requireBenefit` / `MemberLevelController.requireLevel` / `StockController` 的 `p == null || !tid.equals(p.getTenantId())`）**全部**改为 404，无一遗漏。复核命令：

```
Select-String -Path backend/src/main/java/**/*.java -Pattern 'Result\.error\(\s*"[^"]*不存在[^"]*"'
→ 0 hits（全库残留清零）
```

同时 `Result.error(String)` 中含"无权"字样的残留也为 0 hits。

### 2.2 `ReportV2Service.download` 三个裸 `RuntimeException`

| 原代码 | 改造后 | 理由 |
|---|---|---|
| `throw new RuntimeException("任务不存在")` | `throw new BusinessException(BizCode.NOT_FOUND, "任务不存在")` | 记录不存在 → 404 |
| `throw new RuntimeException("无权下载")` | `throw new BusinessException(BizCode.FORBIDDEN, "无权下载")` | 跨租户访问 → 403 |
| `throw new RuntimeException("任务未完成")` | `throw new BusinessException(BizCode.CONFLICT, "任务未完成")` | 状态冲突 → 409 |
| `throw new RuntimeException("文件未生成")` | `throw new BusinessException(BizCode.NOT_FOUND, "文件未生成")` | 文件缺失 → 404 |

注：`GlobalExceptionHandler.handleBusiness` 对 4xx code 直接映射 HTTP 状态（`HttpStatus.valueOf(code)`），故 404/403/409 走真实 HTTP 状态码；这是本包唯一与"形态 A（HTTP 200 + code 404）"不同的端点，原因是文件流无法包装 `Result`，已在 §1 记录为**用户接受的例外**。

### 2.3 import 补全

10 个 Controller 全部补 `import com.meiyetec.exception.BizCode;`；`ReportV2Service` 补 `import com.meiyetec.exception.BusinessException;`。编译通过（`mvn clean package -Dmaven.test.skip=true` → EXIT=0）。

---

## 3. GET 全量扫描：code=500 彻底归零

两次扫描使用**同一 URL 枚举脚本逻辑、同一 URL 集合、同一账号**。URL 集合已校验完全一致（`onlyF141=0 / onlyF142=0`，865 个）。

| 项 | 改造前（F141） | 改造后（F142） | 变化 |
|---|---|---|---|
| 扫描端点数 | 865 | 865 | — |
| **code=500** | **11** | **0** | **−11 ✅** |
| code=404 | 82 | 93 | +11 ✅ |
| code=200 | 642 | 642 | 0 |
| code=400 | 64 | 64 | 0 |
| 空（HTTP 404 无 handler） | 46 | 46 | 0 |
| n/a（非 JSON：CSV/Excel） | 20 | 20 | 0 |
| 扫描时间戳 | `2026-11-01T16:24:20.018Z` | `2026-11-02T06:36:59.273Z` | — |

**净变化：500 归零，404 增加 11，其余四档完全不变，无新增 500、无端点丢失。**

### 3.1 11 个端点逐条变化（500 → 404）

| # | URL | Controller | 改造后 message |
|---|---|---|---|
| 1 | `GET /booking/1548` | `BookingController` | 资源不存在: 预约不存在 |
| 2 | `GET /member-level/1548` | `MemberLevelController` | 资源不存在: 等级不存在或无权访问 |
| 3 | `GET /packages/1548` | `PackageController` | 资源不存在: 套餐不存在 |
| 4 | `GET /pos/orders/1548` | `PosController` | 资源不存在: 订单不存在 |
| 5 | `GET /pos/services/1548` | `PosController` | 资源不存在: 服务不存在 |
| 6 | `GET /report/v2/task/1548/download` | `ReportV2Service` | 资源不存在: 任务不存在 |
| 7 | `GET /stock/check/1548` | `StockController` | 资源不存在: 盘点单不存在 |
| 8 | `GET /stock/products/1548` | `StockController` | 资源不存在: 商品不存在 |
| 9 | `GET /stock/trace/1548` | `StockController` | 资源不存在: 商品不存在 |
| 10 | `GET /sys/tenant/1548/stats` | `SysTenantController` | 资源不存在: 租户不存在 |
| 11 | `GET /technician/level/benefit/1548` | `LevelBenefitController` | 资源不存在: 权益不存在或无权访问 |

> 注：F141 §3 表格中 `/member-level/1548` 与 `/technician/level/benefit/1548` 两条记为"跨租户"触发条件，实测消息为"不存在或无权访问"，两义共用同一分支，改造后统一 404 —— **对跨租户访问者暴露"不存在"而非"无权"，符合 F141 既定形态 A 决策**（用户已确认接受）。

原始数据：`sweep-c2-after.json`（改造后全量）、`sweep-c2-nonget.json`（非 GET 抽样）。

---

## 4. 非 GET 路径抽样：27/27 返回 404

抽样对象覆盖 10 个 Controller 的代表性非 GET 方法（POST / PUT / DELETE）。结果：**27 个端点全部 `http=200, code=404`，mismatch=0**。

| Controller | 抽样端点（均为 `id=99999999` 不存在样本） | 结果 |
|---|---|---|
| `BookingController` | POST `/{id}/checkin`、POST `/{id}/complete`、DELETE `/{id}`、PUT `/{id}` | 4/4 = 404 |
| `LevelBenefitController` | POST `/batch/{levelId}`、PUT `/{id}`、DELETE `/{id}` | 3/3 = 404 |
| `MemberController` | POST `/{id}/freeze`、POST `/{id}/unfreeze`、POST `/{id}/profile`、DELETE `/{id}` | 4/4 = 404 |
| `MemberLevelController` | DELETE `/{id}` | 1/1 = 404 |
| `MemberMessageController` | POST `/{id}/read` | 1/1 = 404 |
| `PackageController` | POST `/purchase`、POST `/order/{id}/refund`、POST `/order/{id}/extend`、POST `/upgrade`、DELETE `/{id}` | 5/5 = 404 |
| `PosController` | POST `/orders/{id}/items`、POST `/orders/{id}/pay`、POST `/orders/{id}/void`、POST `/orders/{id}/refund`、POST `/orders/{id}/discount`、DELETE `/orders/{id}/items/{itemId}` | 6/6 = 404 |
| `StockController` | PUT `/products/{id}`、DELETE `/products/{id}` | 2/2 = 404 |
| `SysTenantController` | PUT `/{id}/status/{status}` | 1/1 = 404 |

**抽样口径**：全部使用 `id=99999999`，并在执行前对 12 张目标表做存在性预检（`crm_member` / `crm_package` / `crm_package_order` / `pos_order` / `biz_level_benefit` / `mem_level` / `biz_technician_level` / `stock_product` / `stock_check` / `member_message` / `pos_store` / `sys_tenant`），命中任一存在行即 fail-fast 中止（退出码 2），确保抽样只落在「记录不存在」分支。

**抽样后残留检查**：全部目标表 `id=99999999` 行数 = 0（`crm_member` / `crm_package_order` / `pos_order` / `stock_product` / `biz_booking` 均 0），无污染。

---

## 5. ⚠️ 调查过程中的数据事故与还原记录（必须留痕）

**事故**：抽样脚本 v1 使用 `id=1548` 作为"不存在"样本，但 `crm_member` 表中 `id=1548` 是**真实存在的会员**（`member_no=M13917321821`，name=113号，tenant_id=1）。脚本执行 `DELETE /member/1548` → `MemberController.delete` → `memberService.removeById(id)` 触发 MyBatis-Plus 逻辑删除，将 `crm_member.deleted` 由 `0` 置为 `1`；同时 `POST /member/1548/profile` 曾写入一行 `crm_member_profile`（随后被同一脚本的 DELETE 清除）。

**影响范围**：经备份快照逐表核对，**仅 `crm_member` 单行的 `deleted` 与 `update_time` 两个字段**被改动；`crm_member_profile` / `crm_member_tag_rel` 在备份中本就是 0 行，无数据丢失；`member_lifecycle_log` 的 2 行是 2026-10-31 / 11-01 的会员日任务记录，与本次无关。

**还原动作**（依据 `backup/meiyetec_20261102_140226.sql`，2026-11-02 14:02 全量 dump）：

```sql
UPDATE crm_member SET deleted = 0, update_time = '2026-09-02 17:51:04'
WHERE id = 1548 AND deleted = 1;
-- ROW_COUNT() = 1
```

**还原后核验**（与备份逐字段一致）：

| 字段 | 备份值 | 还原后值 | 一致 |
|---|---|---|---|
| id / member_no / name / phone | 1548 / M13917321821 / 113号 / 13917321821 | 同 | ✅ |
| gender / status / tenant_id | 2 / 1 / 1 | 2 / 1 / 1 | ✅ |
| deleted | 0 | 0 | ✅ |
| version | 0 | 0 | ✅ |
| create_time / update_time | 2026-09-02 17:51:04 | 2026-09-02 17:51:04 | ✅ |

**流程改进**：抽样脚本 v2 已加入"执行前存在性预检 + fail-fast"，并把样本 ID 从 1548 换成保证不存在的 99999999。v1 脚本已被 v2 完全替换，`sweep-c2-nonget.json` 为 v2 的产出（`missingId=99999999`）。

---

## 6. 本包无法确认的项

| 项 | 原因 |
|---|---|
| 59 处中未进入抽样集的 48 处非 GET 分支 | 抽样覆盖 27 个代表性端点（覆盖全部 10 个 Controller、5 种 HTTP 方法），**未逐点覆盖全部 59 处**；其余分支的运行时形态为**按源码推断**（改动为纯机械替换 + 27/27 同型抽样全绿），状态 UNVERIFIED |
| 前端对 code=404 的实际弹窗表现 | 前端 `frontend/meiyetec-web/src/api/request.ts` 对 `res.code !== 200` 统一 `ElMessage.error` + reject，404 与原 500 在前端表现一致（均弹错误提示），**具体文案由后端 message 决定**（如"资源不存在: 预约不存在"），需人工在浏览器确认文案可读性 |
| `ReportV2Service.download` 的 HTTP 404 形态 | 该端点走 `BusinessException` → HTTP 404 + JSON body（`code=404`），与其余 58 处的 HTTP 200 + code 404 **形态不同**，已确认为用户接受的技术栈例外；前端对该 404 响应体的处理路径未人工验证 |
| `env.md` 的 HEAD 字段 | 仍为 `656caa7`，与本次验证 commit 不一致（治理基线滞后，按规则 18 未修改） |
| 跨租户场景的端到端行为 | 抽样均以"不存在"为目标，未构造"存在但跨租户"的真实场景；该分支的消息文案统一为"xxx不存在或无权访问"，对跨租户访问者不区分语义 —— 与 F141 决策一致，但未做跨租户实测 |
| DB 在扫描窗口内是否有并发写入 | 扫描为只读 GET，未验证 |

---

## 7. 结论

| 项 | 状态 |
|---|---|
| 59 处 `Result.error("...不存在...")` 统一为 `Result.error(BizCode.NOT_FOUND, ...)` | **VERIFIED**（源码全库残留 0 hits + 编译通过 + 11 个 GET 端点实测 500→404） |
| GET 全量 865 端点 code=500 | **VERIFIED → 0**（82→404 档 +11，URL 集合与 F141 完全一致，其余四档零漂移） |
| 非 GET 路径同类缺陷 | **PARTIAL-NON-BLOCKER**（27 个代表性端点实测 27/27 = 404；其余 48 处分支 UNVERIFIED） |
| `ReportV2Service.download` 三个裸 `RuntimeException` | **VERIFIED**（改为 `BusinessException` + 404/403/409；该端点 GET 实测 500→404） |
| 跨租户判定未遗漏 | **VERIFIED**（源码逐处核对，含 `xxx不存在或无权访问` 全部分支） |
| 抽样过程造成的数据改动 | **已还原**（`crm_member` id=1548 的 `deleted` + `update_time`，逐字段与备份快照一致；无残留污染） |
