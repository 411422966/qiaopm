# I134 — 契约缺陷 C1：请求参数缺失/类型错误统一返回 500「系统繁忙」

> **编号说明**：用户 2026-10-30 19:23 指定为 `I132-missing-request-param-returns-500.md`，但 `I132` 已被 `I132-pos-checkout-missing-tendered-ui.md` 占用（不覆盖既有登记），故顺延为 **I134**。
> **严重度**：P1（契约缺陷；本次扫描中 79 个 500 里有 64 个属于此类）
> **状态**：BROKEN（未修复；用户 2026-10-30 19:23 指示「暂不修改 C1 / C2」）

---

## 1. 现象

客户端缺必填参数、或参数类型无法转换时，服务端返回 **HTTP 200 + `code: 500` + `message: 系统繁忙`**，而不是 400。

实测样本（2026-10-30 18:19 容器日志，原始片段见 `audit/evidence/F140-i118-ddl-landing/r8-npe-stacks.txt` 与同目录 `sweep-get2.json`）：

| traceId | 请求 | 日志 |
|---|---|---|
| `fd46802d70a24399bde9b7bda70082c5` | `/attendance/monthly-report` | `MissingServletRequestParameterException: Required request parameter 'employeeId' for method parameter type Long is not present` → `GlobalExceptionHandler [SystemException]` |
| `3f3cb66420294117b3e6ab448d81f4f8` | `/ai/recommend` | 同上（`memberId`） |
| `98c7a70f85154f84951bf25cba3baeb5` | `/sla/uptime/list` | 同上（`serviceCode`） |

响应体统一是 `{"code":500,"message":"系统繁忙"}`，与「数据库炸了」「服务不可用」完全同形。

## 2. 根因

`backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java` 缺三类参数绑定异常的处理器：

| 缺失的处理器 | 触发异常 | 现状落点 |
|---|---|---|
| — | `MissingServletRequestParameterException` | `handleAny(Exception)` |
| — | `MethodArgumentTypeMismatchException` | `handleAny(Exception)` |
| — | `HttpMessageNotReadableException` | `handleAny(Exception)` |

```59:63:backend/src/main/java/com/meiyetec/exception/GlobalExceptionHandler.java
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception e) {
        log.error("[SystemException]", e);
        return ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR));
    }
```

`handleAny` 固定返回 `BizCode.SYSTEM_ERROR`（= 500 / 「系统繁忙」），因此**参数错误被伪装成系统故障**。

对比：已有的 `MethodArgumentNotValidException`（`@RequestBody` 校验失败）走 `handleValidation`，返回 `BizCode.BAD_REQUEST`（400）——说明 400 语义在框架内已存在，只是参数绑定路径没接上。

## 3. 影响面

- **运维**：真故障与调用方参数错误混在同一错误码里，告警无法区分。
- **前端**：前端只能按 `code===500` 统一弹「系统繁忙」，无法对 400 做表单级回填/高亮。
- **审计口径**：GET 全量扫描中 64 个 500 属于此类（探测未带必填参数），**其中一部分可能是真实契约缺陷**（前端确实少传了必填参数），目前无法区分 → 本次扫描的 500 数量对「真实故障」的估计偏高。

## 4. 修复方向（本次**不落地**，用户指示暂缓）

新增一个 handler，把三类参数绑定异常统一映射为 400：

```java
@ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
})
public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
    log.warn("[BadRequest] {}", e.getMessage());
    return ResponseEntity.ok(Result.error(BizCode.BAD_REQUEST.code, "请求参数不合法: " + e.getMessage()));
}
```

配套建议（可选，**需用户单独批准**）：
- `handleRuntime` 只打 `e.getMessage()`，不打堆栈 → R8 的 5 处 NPE 定位不到行号（本次靠逐个端点读源码定位）。建议改为 `log.warn("[RuntimeException]", e)`（P1）。
- `Result.error(String)` 硬编码 `code=500`（`util/Result.java:44-49`）→ 业务「不存在」返回 500，与 `BizCode.NOT_FOUND=404` 语义冲突；同语义在系统里有 3 种表现（HTTP200+code404 / HTTP404+code404 / HTTP200+code500）。归入 **C2**，本次一并暂缓。

## 5. 证据

- `audit/evidence/F140-i118-ddl-landing/sweep-get2.json` —— 79 个 500 的端点级记录
- `audit/evidence/F140-i118-ddl-landing/500-classified.json` —— 每个 500 的 traceId
- 容器日志原件：`.audit-tmp/api-full2.log`（扫描期完整日志）
- 环境：commit `cc95a00`，DB `127.0.0.1:3307/meiyetec`，env `audit/baseline/env.md`

## 6. 状态

- **BROKEN**（契约缺陷已定位，未修复）
- 关联：C2（`util/Result.java:44-49`）同批次暂缓
