# F045 — Plugin Marketplace 上架管理（×3 端点 实证）

> **验证时间**：2026-09-13 20:44 (UTC+8)
> **Phase**：5B — API + DB 实证
> **验证方式**：curl + mysql 验证 plugin_market 写入
> **最终状态**：**PARTIAL-NON-BLOCKER → 保持 PARTIAL-NON-BLOCKER**（CRUD 正确 + I019 命名异味 + I009 @Valid 缺失）

---

## 一、3 端点实证

### 1.1 实证结果（来自 `probe-5b-f045-7.ps1`）

| # | 端点 | body | 响应 |
|---|---|---|---|
| 1 | `POST /plugin/market` 上架 | `{pluginCode:probe-mkt-5b, pluginName:probe plugin, ...}` | **200** `{code:200, message:"已上架", data:{id:20, tenantId:0, pluginCode:probe-mkt-5b, ...}}` |
| 2 | `POST /plugin/market` 缺字段（pluginCode=null）| `{pluginName:"缺编码"}` | **500** `JSON parse error: Unexpected end-of-input in VALUE_STRING` ⚠️ |
| 3 | `GET /plugin/market/list` | — | **200** 返回所有 PUBLISHED 状态插件，按 installCount 倒序 |
| 4 | `POST /plugin/market/{id}` 更新 | `{description:"更新后的描述", shortDesc:"updated"}` | **200** `{message:"已更新", data:{...更新后...}}` |

### 1.2 DB 实证

```sql
SELECT id, tenant_id, plugin_code, plugin_name, category, version, vendor, install_count, status
FROM plugin_market WHERE plugin_code='probe-mkt-5b';

-- id=20, tenant_id=0, plugin_code=probe-mkt-5b, plugin_name=probe plugin,
-- category=test, version=1.0.0, vendor=probe, install_count=0, status=PUBLISHED
```

`publish` 方法（`PluginService.java:39-50`）默认值：
- `tenantId=0`（强制）
- `status=PUBLISHED`（默认）
- `version="1.0.0"`（默认）
- `installCount=0`（默认）
- `rating=0`（默认）

## 二、I009 @Valid 缺失 实证

### 2.1 测试场景

`POST /plugin/market` 缺字段时：
- **预期**：`BizCode.BAD_REQUEST 400`（业务校验）
- **实际**：`500 JSON parse error`（Jackson 反序列化失败被 Spring 兜底）

### 2.2 根因

`PluginService.java:39-50`：
```java
@PostMapping("/market")
public Result<PluginMarket> publish(@RequestBody PluginMarket p) {
    // 没有 @Valid，没有 @Validated
    if (p.getPluginCode() == null || p.getPluginName() == null)
        return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
    ...
}
```

**问题**：`@RequestBody` 解析失败时（缺字段 + 字段类型不匹配）→ 抛 `HttpMessageNotReadableException` → Spring 默认 500。**不是** publish 方法内部的 `if (p.getPluginCode() == null...)` 校验生效。

但当 body 完整但字段为 null 时，publish 内部 `if` 校验会生效返回 400（SoT 描述的路径）。所以：
- 字段类型不匹配 / body 格式错误 → 500
- body 完整但字段为 null → 400（按 SoT 设计）

## 三、I019 命名异味 静态识别

`PluginService.java:30`：`@RestController` 类被命名为 `Service` → 命名异味（同 CRM I004 模式）。
- 业务层 Service 应该是 `@Service` 注解，被 Controller 调用
- 当前 Service 直接是 Controller，违反了 Spring 分层

**建议**：Post-Audit 拆分为 `PluginController` + `PluginMarketService` + `PluginInstallService` + `PluginReviewService`。

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 上架 + 列表 + 更新 | ✅ VERIFIED | 3 端点全部响应正确，DB 行写入正确 |
| 默认值填充 | ✅ VERIFIED | tenantId=0, status=PUBLISHED, version="1.0.0", installCount=0 |
| 业务校验（缺 pluginCode/pluginName）| ✅ 静态识别 | 内部 if 返回 400 OK |
| Jackson 解析失败 | ⚠️ 返回 500 | I009 @Valid 缺失证据（实测）|
| I019 命名异味 | ✅ 静态识别 | PluginService 直接是 @RestController |

### 最终状态

**F045 = PARTIAL-NON-BLOCKER**（CRUD 正确 + I019 命名异味 + I009 @Valid 缺失）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-f045-7.ps1` |
| 原始响应 | `audit/runtime/probe-5b-f045-7.json` |
| Service | `backend/src/main/java/com/meiyetec/service/PluginService.java:38-72` |

---

**F045 CRUD 行为正确。I019/I009 静态识别。**
