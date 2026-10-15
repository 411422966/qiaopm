# F043 — Plugin Runtime Controller 平台级 API（×6 端点 实证）

> **验证时间**：2026-09-13 20:43 (UTC+8)
> **Phase**：5B — API-only 实证
> **验证方式**：curl 6 端点直调
> **最终状态**：**PARTIAL-NON-BLOCKER → 保持 PARTIAL-NON-BLOCKER**（I024 Shadow + 端点行为符合预期）

---

## 一、6 端点实证

### 1.1 实证结果（来自 `probe-5b-f043.ps1`）

| # | 端点 | 预期 | 实际 code | 关键响应 |
|---|---|---|---|---|
| 1 | `GET /plugin/runtime/list` | 200, data=[]（pf4j 0 插件）| 200 | `{"code":200,"data":[]}` |
| 2 | `GET /plugin/runtime/dashboard` | 200, 摘要统计 | 200 | `{"runtimeEnabled":true,"pluginsRoot":"D:\\QiaoPM\\backend\\.\\plugins","total":0,"started":0,"created":0,"disabled":0,"failed":0,"stopped":0,"persistedRecords":0,"lastKnownFailed":0}` |
| 3 | `POST /plugin/runtime/demo-hello/load`（无 body）| 404（pluginsRoot 找不到 demo-hello）| 404 | `"message":"资源不存在: 插件不存在: demo-hello"` |
| 4 | `POST /plugin/runtime/demo-hello/load {version:"1.0.0"}` | 404（路径不匹配 + 未编译）| 404 | 同 #3 |
| 5 | `POST /plugin/runtime/demo-hello/enable` | 404（未 load）| 404 | `"message":"资源不存在: 插件未加载: demo-hello"` |
| 6 | `POST /plugin/runtime/demo-hello/disable` | 404（未 load）| 404 | 同 #5 |
| 7 | `POST /plugin/runtime/demo-hello/unload` | 404（未 load）| 404 | 同 #5 |
| 8 | `POST /plugin/runtime/non-existent-plugin/load` | 404 | 404 | `"message":"资源不存在: 插件不存在: non-existent-plugin"` |
| 9 | `POST /plugin/runtime/demo-hello/enable`（无 load）| 404 | 404 | 同 #5 |

### 1.2 响应行为分析

- ✅ **GET /list** / **GET /dashboard**：返回 200，结构符合 SoT 定义
- ✅ **POST .../load**：插件不在 pluginsRoot → 返回 404 "插件不存在"
- ✅ **POST .../enable|disable|unload**：插件未 load → 返回 404 "插件未加载"
- ⚠️ **request body 解析错误 → 500**：SoT 没列此场景，实际 @RequestBody Map<String, Object> body=null 时 OK，但 body 内容损坏时 Spring 直接抛 500（已在 probe-5b-f045-7 实测）

## 二、Shadow Feature 实证（I024）

### 2.1 grep 前端消费

```bash
$ grep -r "plugin/runtime" frontend/meiyetec-web/src/
# 0 命中（前端 api/plugin.ts 无 runtime 方法）
```

### 2.2 前端 plugin.ts 实证

```typescript
// frontend/meiyetec-web/src/api/plugin.ts 仅有：
export const pluginApi = {
  marketList, marketPublish, marketUpdate,
  install, uninstall, installList,
  review, reviewList,
  // ❌ 无 runtime/list, runtime/dashboard, runtime/{code}/load|enable|disable|unload
}
```

**结论**：6 个平台级 API **100% Shadow Feature**（I024 实证）—— 后端实现完整，前端无任何调用方。

## 三、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| 6 端点路由可达 | ✅ VERIFIED | curl 全部响应（404 因 pf4j 0 插件，符合预期）|
| 错误码使用 BizCode.NOT_FOUND | ✅ VERIFIED | `"资源不存在"` message 模板 |
| GET 端点 data 结构 | ✅ VERIFIED | list=`[]`, dashboard=10 字段 |
| POST body 缺 @Valid | ✅ 静态识别 | 解析失败返回 500（I006 同模式）|
| I024 Shadow Feature | ✅ 实证确认 | grep 前端 0 命中 |

### 最终状态

**F043 = PARTIAL-NON-BLOCKER**（6 端点行为符合 SoT + Shadow 实证）

---

## 四、Evidence 文件

| 文件 | 路径 |
|---|---|
| 测试脚本 | `audit/runtime/probe-5b-f043.ps1` |
| 原始响应 | `audit/runtime/probe-5b-f043.json` |
| 控制器 | `backend/src/main/java/com/meiyetec/controller/PluginRuntimeController.java:76-130` |
| 前端 API | `frontend/meiyetec-web/src/api/plugin.ts` |

---

**F043 端点行为正确。I024 Shadow 实证。**
