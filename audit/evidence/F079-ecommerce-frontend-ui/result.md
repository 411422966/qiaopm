# F079 ecommerce/index.vue 前端 UI — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：I028（前端缺字段 UI EXT-22 子项）+ D20（真实 Shadow 不加 UI）

---

## 1. 实证结论

**状态**：**PARTIAL-NON-BLOCKER**（I028 EXT-22 子项实证 + 真实 Shadow）

| 维度 | 内容 | 状态 |
|---|---|---|
| **Tab 数** | 3（平台管理 / 商品同步 / 订单同步）| ✅ 符合 |
| **添加平台字段** | **仅 3 字段**（platform / shopName / shopId）| ❌ **I028 实证** |
| **缺哪些字段** | appId / kdtId / signSecret / appKey / appSecret | ❌ EXT-22 子项 |
| **update 按钮** | 平台列表无 update / edit / delete 按钮 | ⚠️ 真实 Shadow |
| **loading 态** | 添加按钮无 loading 态 | ⚠️ UX 问题 |
| **二次确认** | 同步按钮无确认对话框 | ⚠️ UX 问题 |

## 2. 静态证据

`frontend/meiyetec-web/src/views/ecommerce/index.vue`：

| 行号 | 内容 |
|---|---|
| **L3-30** | 3 个 el-tab-pane |
| **L19** | 平台列表无 update 按钮 |
| **L34** | 商品列表无 update 按钮 |
| **L60-77** | 添加平台对话框（**仅 platform/shopName/shopId 3 字段**）|
| **L66-70** | platform 选项：WEIDIAN/YOUZAN/TAOBAO/DOUYIN（无 MOCK 选项）|
| **L73** | shopName 输入框 |
| **L74** | shopId 输入框 |
| **L76** | 无 appId/kdtId/signSecret 字段 |

### 关键代码片段（L60-77）

```vue
<el-dialog v-model="addDialog" title="添加电商平台" width="500px">
  <el-form :model="addForm" label-width="120px">
    <el-form-item label="平台">
      <el-select v-model="addForm.platform">
        <el-option label="微店" value="WEIDIAN"/>
        <el-option label="有赞" value="YOUZAN"/>
        <el-option label="淘宝" value="TAOBAO"/>
        <el-option label="抖店" value="DOUYIN"/>
      </el-select>
    </el-form-item>
    <el-form-item label="店铺名称"><el-input v-model="addForm.shopName" /></el-form-item>
    <el-form-item label="店铺ID"><el-input v-model="addForm.shopId" /></el-form-item>
    <!-- 缺: appKey / appSecret / appId / kdtId / signSecret -->
  </el-form>
</el-dialog>
```

## 3. I028 EXT-22 子项实证

**I028 EXT-22 子项**：前端缺 `appId / kdtId / signSecret` 字段 UI

**实证**：
- ❌ 没有 `<el-form-item label="AppID">`
- ❌ 没有 `<el-form-item label="店铺ID(kdtId)">`
- ❌ 没有 `<el-form-item label="签名密钥(signSecret)">`
- ❌ 没有 `<el-form-item label="AppKey">`（OAuth2 client_id）
- ❌ 没有 `<el-form-item label="AppSecret">`（OAuth2 client_secret）

→ 用户在前端**只能填 platform/shopName/shopId**，无法配置：
- OAuth2 token 凭据（appKey/appSecret）— **F069 永远走 Mock fallback**
- 有赞消息推送凭据（appId/kdtId/signSecret）— **F077 webhook 永远 401**

## 4. 真实 Shadow（D20 决策）

- `/ecommerce/platform/{id}/health` 端点存在（F075 实证）但前端无 UI 调用
- 平台列表无 update / delete / sync health 按钮
- → 沿用 I006/I007 模式，**真实 Shadow = 后端有 + 前端无**

D20 决策：维持 PARTIAL-NON-BLOCKER，不加 UI。

## 5. 与 F065 google-maps/index.vue 对比

| 维度 | F065 google-maps | F079 ecommerce |
|---|---|---|
| Tab 数 | 4 | 3 |
| 缺字段 UI | C1（configForm 缺字段）| **I028 EXT-22 子项** |
| el-switch 已实现 | ✅（5A 漏判修正）| N/A |
| 真实 Shadow | /geocode/log 无前端 | /health 无前端 |

→ 两个前端都存在不同程度缺字段 UI 问题，但 F065 是 config 类，F079 是 CRUD 类

## 6. Issue 状态

| Issue | 关联 | 状态 |
|---|---|---|
| **I028 EXT-22 子项** | 前端缺 appId/kdtId/signSecret | ✅ 实证 PASS |
| **真实 Shadow** | /platform/{id}/health 无前端 | 维持 PARTIAL-NON-BLOCKER |

## 7. Phase 7 决策建议

Post-Audit 阶段可选：
1. 加 5 个字段 UI（appKey/appSecret/appId/kdtId/signSecret）
2. 加 update / delete 按钮
3. 加 sync / health 按钮

→ D20 决策：暂缓，建议生产前补全

## 8. 证据文件

- `views/ecommerce/index.vue`（无独立 evidence 文件，源代码即证据）
