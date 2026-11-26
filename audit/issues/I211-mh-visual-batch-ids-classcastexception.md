# I211 — `/mh/visual/archive/batch-analyze` 与 `batch-revoke` 在权限通过后恒抛 `ClassCastException`（HTTP 200 + code 500 假成功）

> **状态**：**Open（仅登记，不修）**
> **严重度**：**P1**
> **P1 理由**：两个**批量操作按钮对所有用户恒失败**，且失败以 **HTTP 200 + `body.code=500`** 的「假成功」形态返回 —— 前端若只看 HTTP 状态码会误判为成功。本批 F175 首轮 205 端点验证矩阵正是因此把这两个端点误判为 PASS（详见 §3），属规则 10「假成功必须单独标记」的典型案例。
> **发现日期**：2026-11-26（F175 批次 —— 41 处 `@RequirePermission` 落地后的独立复验阶段）
> **登记人**：Cursor
> **绑定基线**：HEAD `78c41f8`（`docs(audit): I202 口径 19→20 修正 + 登记 I206/I207 + D-3-34/D-3-35 流程规则`）+ 工作区未提交的 41 处注解改动
> **验证等级**：**运行期 VERIFIED**（容器 `meiyetec-api` 实测 HTTP 200 + `body.code=500` + 原始堆栈已取证）+ **静态 VERIFIED**（源码根因定位到具体行）
> **关联**：I206（三个 mh service 的鉴权注解缺失 —— 本条是 I206 修复落地后**新暴露**的运行时缺陷）、I190（`GlobalExceptionHandler` 把客户端错误吞成 500 的同族语义失真，本条叠加其上放大为假成功）
> **登记状态**：已登记于 `audit/issues/issue-register.md`（2026-11-26 写入，**待用户事后复核**；写入时未经逐字确认，流程偏差见 I198 §D-3-36）

---

## 0. 一句话描述

`POST /mh/visual/archive/batch-analyze` 与 `POST /mh/visual/archive/batch-revoke` 的请求体声明为
`@RequestBody Map<String, Object> body`，Jackson 把 JSON 中小于 `2^31` 的整数 id 绑为 `Integer`，
而方法体写的是 `List<Long> ids = (List<Long>) body.get("ids")` —— 泛型擦除让强转**在编译期与运行期都不报错**，
直到 `for (Long id : ids)` 处编译器插入的 `checkcast java/lang/Long` 才抛 `ClassCastException`。
结果：**权限校验通过后必然 500**，两个批量按钮对所有用户恒失败。

---

## 1. 事实

### 1.1 端点与源码

`backend/src/main/java/com/meiyetec/service/VisualArchiveService.java`

| 端点 | 方法 | 注解（权限码） | 抛异常行 | 当前行号 |
|---|---|---|---|---|
| `POST /mh/visual/archive/batch-analyze` | `batchAnalyzeArchives` | `mh:visual:edit` | `for (Long id : ids)` | **284** |
| `POST /mh/visual/archive/batch-revoke` | `batchRevokeArchives` | `mh:visual:revoke-batch` | `for (Long id : ids)` | **313** |

`batchAnalyzeArchives`（`VisualArchiveService.java:274-300`）：

```java
@PostMapping("/archive/batch-analyze")
@Transactional(rollbackFor = Exception.class)
@RequirePermission("mh:visual:edit")
public Result<Void> batchAnalyzeArchives(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Long> ids = (List<Long>) body.get("ids");   // ← 擦除，无实际检查
    ...
    for (Long id : ids) {                              // ← L284 checkcast 失败
```

`batchRevokeArchives`（`VisualArchiveService.java:301-320`）同型，`for (Long id : ids)` 位于 **L313**。

### 1.2 根因

`Map<String, Object>` 的值类型是 `Object`，Jackson 默认把 JSON 整数字面量按数值大小选择
`Integer`（可容纳时）或 `Long`（超出 `Integer.MAX_VALUE` 时）。

- `body.get("ids")` 的静态类型是 `Object`，**强转为 `List<Long>` 在运行期只检查到 `List`**（因泛型擦除，`List` 的实际元素类型不参与检查）→ **不抛异常**；
- 真正抛异常的是 `for (Long id : ids)` 的迭代 —— 编译器为元素读取插入了 `checkcast java/lang/Long`；
- 元素实际是 `java.lang.Integer` → `ClassCastException`。

**这不是「某些 id 触发」**：只要 `ids` 数组里有**任意一个** id 的 JSON 值小于 `2^31`，整个请求即失败。
真实业务 id 全部远小于 `2^31`，因此**该端点对所有真实用户恒失败**。

### 1.3 运行期实测（2026-11-26 21:38，容器 `meiyetec-api`，账号 `admin` / SUPER_ADMIN 通配 `*`）

| 端点 | 请求体 | HTTP | body.code | 说明 |
|---|---|---|---|---|
| `POST /mh/visual/archive/batch-analyze` | `{"ids":[99999999]}` | **200** | **500** | SYSTEM_ERROR |
| `POST /mh/visual/archive/batch-revoke` | `{"ids":[99999999]}` | **200** | **500** | SYSTEM_ERROR |
| `POST /mh/visual/archive/batch-revoke` | `{"ids":[9999999999]}` | 200 | **200** | 正常（`9999999999 > 2^31-1`，Jackson 绑为 `Long`） |

第 3 行是**决定性对照**：同一端点、同一路径，仅 id 数值超过 `Integer.MAX_VALUE` 即恢复正常 → 精确锁定根因在「Jackson 数值类型选择 + 泛型擦除」，而非业务逻辑。

容器日志原始堆栈（见 `audit/evidence/F175-mh-visual-consent-complication-perm-annotations/06-i211-container-stacktrace.txt`）：

```
java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Long
  at com.meiyetec.service.VisualArchiveService.batchRevokeArchives(VisualArchiveService.java:313)
java.lang.ClassCastException: class java.lang.Integer cannot be cast to class java.lang.Long
  at com.meiyetec.service.VisualArchiveService.batchAnalyzeArchives(VisualArchiveService.java:284)
```

### 1.4 写副作用复检

以 `99999999` / `9999999999` 作为不存在的兜底 id，在 `mh_visual_archive`、`mh_image_consent`、`complication_event`、
`mh_drug_device_usage`、`mh_image_withdraw`、`mh_consent_signature` 六张表做存在性检查 → **全部 0 行**，未产生脏数据。

> 本条**未触发任何写操作**（异常发生在 `selectById` 之前的事务方法入口，且 id 不存在）。

---

## 2. 为何首轮 205 端点矩阵漏判

首轮 F175 验证矩阵（`audit/evidence/F175-mh-visual-consent-complication-perm-annotations/`）以 **HTTP 状态码**判定 PASS。
而 `GlobalExceptionHandler.handleRuntime` 对 `RuntimeException` 返回
`ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR))` —— **HTTP 200 + `body.code = 500`**。

故 `ClassCastException` 落在两个端点上时，首轮被判为 PASS（假成功）。第二轮独立复验改用 **`body.code`** 判定后暴露。

> **判定规则修正**：验收类 HTTP 断言必须**同时**检查 HTTP 状态码与响应体业务码 `code`，
> 二者任一表示失败即判 FAIL。当前 F175 证据的 15 条核心矩阵已采用 `biz=` 双判（第 4 条列即 `HTTP=200 biz=400` 记 PASS）。

---

## 3. 业务影响

前端 `frontend/meiyetec-web/src/views/mh/visual.vue`：

- `:1156` `selectedIds.value = rows.map(r => r.id)` —— 表格 id 为 JSON 数字 → Jackson 绑为 `Integer`；
- `:1167` 调用 `POST /mh/visual/archive/batch-analyze`；
- `:1200` 调用 `POST /mh/visual/archive/batch-revoke`。

即 **「批量分析」与「批量撤回」两个按钮对所有用户必然失败**。

> **未确认**：`views/mh/visual.vue` 属**用户已验收页面**，本条**未修改**该文件；
> 前端按钮的实际点击表现**未在浏览器实测**（页面级仍 UNVERIFIED），
> 上述前端影响为**静态推论 + 后端实测**的组合结论。

---

## 4. 修复方向（不在本批范围）

优先级从高到低：

1. **推荐**：改用强类型 DTO 承接请求体，例如
   `public Result<Void> batchRevokeArchives(@RequestBody BatchIdsRequest req)`，
   其中 `BatchIdsRequest` 含 `List<Long> ids` —— 由 Jackson 直接完成 `Integer → Long` 的宽化转换，
   并可一并消除 `reason` 等字段的类型不安全强转。
2. **最小改动**：保留 `Map<String, Object>`，改为
   `List<?> raw = (List<?>) body.get("ids");` 后逐个 `((Number) x).longValue()` 转换。

> ⚠️ **同类隐患排查建议**：全仓检索 `@RequestBody Map<String, Object>` + `(List<...>)` 强转组合，
> 确认是否存在其他同型端点（本条**仅登记 `VisualArchiveService` 这 2 处已实测触发的**，未做全仓扫描 → 标记 **未确认**）。

---

## 5. 证据

| 项 | 位置 |
|---|---|
| 运行期矩阵（含 `9999999999` 对照行） | `audit/evidence/F175-mh-visual-consent-complication-perm-annotations/04-verification-matrix-15cases.txt` |
| 补充取证（含 I211 实测与写副作用复检） | `audit/evidence/F175-mh-visual-consent-complication-perm-annotations/05-supplementary-and-i211-evidence.txt` |
| 原始堆栈 | `audit/evidence/F175-mh-visual-consent-complication-perm-annotations/06-i211-container-stacktrace.txt` |
| F175 批次总证据 | `audit/evidence/F175-mh-visual-consent-complication-perm-annotations/evidence.md` §4.6 |
