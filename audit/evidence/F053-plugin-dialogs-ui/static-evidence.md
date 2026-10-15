# F053 — Plugin 前端 发布 + 评价对话框（Playwright 降级）

> **验证时间**：2026-09-13 20:50 (UTC+8)
> **Phase**：5B — Static + Playwright 降级
> **验证方式**：读源码 + 生成 Playwright 脚本
> **最终状态**：**PARTIAL-NON-BLOCKER**（静态识别 I006 模式 + 降级方案）

---

## 一、源码定位

`frontend/meiyetec-web/src/views/plugin/index.vue:34-78`

### 1.1 pubDialog（5 字段）

```vue
<el-dialog v-model="pubDialog" title="发布插件" width="500px">
  <el-form :model="pubForm" label-width="120px">
    <el-form-item label="编码">...</el-form-item>
    <el-form-item label="名称">...</el-form-item>
    <el-form-item label="类别">
      <el-select>
        <el-option value="MARKETING"/>
        <el-option value="PAYMENT"/>
        <el-option value="AI"/>
      </el-select>
    </el-form-item>
    <el-form-item label="版本">...</el-form-item>
    <el-form-item label="简介">...</el-form-item>
  </el-form>
  <template #footer>
    <el-button @click="pubDialog = false">取消</el-button>
    <el-button type="primary" @click="doPub">发布</el-button>
  </template>
</el-dialog>
```

### 1.2 reviewDialog（2 字段）

```vue
<el-dialog v-model="reviewDialog" title="评价插件" width="400px">
  <el-form :model="reviewForm" label-width="100px">
    <el-form-item label="评分"><el-rate v-model="reviewForm.rating"/></el-form-item>
    <el-form-item label="评价"><el-input v-model="reviewForm.comment"/></el-form-item>
  </el-form>
</el-dialog>
```

### 1.3 提交逻辑（script）

```typescript
async function doPub() {
  await pluginApi.publish(pubForm.value)
  ElMessage.success('已发布')          // 无 loading 态
  pubDialog.value = false              // 无二次确认
  loadMarket()
}

async function doReview() {
  await pluginApi.review(reviewForm.value)
  ElMessage.success('已评价')
  reviewDialog.value = false
}
```

## 二、I006 模式静态识别

### 2.1 已知问题（来自 Phase 3）

> 无 loading 态 / 无二次确认 / 无错误码展示 / 无 errorCode 字段

### 2.2 Phase 5B 实证旁证

F045 publish 实测：缺字段提交 → 500 JSON parse error → **前端无具体错误码**（ElMessage.success / ElMessage.error 也没拿到 BizCode）

### 2.3 影响

- 用户提交失败时看到的是通用错误，无业务码 → 调试困难
- 缺 loading 态 → 重复点击风险
- 缺二次确认 → 误操作风险（uninstall 等破坏性操作）

## 三、Playwright 降级脚本

### 3.1 脚本文件

`frontend/meiyetec-web/e2e/audit/F053-plugin-dialogs.spec.ts`（生成）

### 3.2 用户手动运行

```powershell
cd D:\QiaoPM\frontend\meiyetec-web
npx playwright test --config=playwright.audit.config.ts e2e/audit/F053-plugin-dialogs.spec.ts
```

### 3.3 测试目标

1. pubDialog 不填字段提交 → 验证错误反馈（dialog 关闭 vs toast vs inline 错误）
2. reviewDialog 字段验证
3. 验证无 loading 态（提交按钮无 spinner）

## 四、判定

| 维度 | 状态 | 备注 |
|---|---|---|
| pubDialog 5 字段 | ✅ Static-Only | 源码 L0 可达 |
| reviewDialog 2 字段 | ✅ Static-Only | 源码 L0 可达 |
| 无 loading 态 | ✅ Static-Only | 源码直接 await，无 disabled / loading 切换 |
| 无二次确认 | ✅ Static-Only | 无 el-popconfirm / el-dialog 嵌套 |
| 无错误码展示 | ✅ Static-Only + 实测 | F045 publish 缺字段 → 500，前端只 ElMessage.success / error |
| E2E 实际行为 | ⏸️ Playwright 降级 | 脚本已生成 |

### 最终状态

**F053 = PARTIAL-NON-BLOCKER**（静态识别 I006 模式 + 降级方案）

---

## 五、Evidence 文件

| 文件 | 路径 |
|---|---|
| 源码 | `frontend/meiyetec-web/src/views/plugin/index.vue:34-78` |
| Playwright 脚本 | `frontend/meiyetec-web/e2e/audit/F053-plugin-dialogs.spec.ts` |

---

**F053 静态判定稳健。运行时 E2E 降级为用户本地 Playwright。**
