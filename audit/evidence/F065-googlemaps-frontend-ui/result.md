# F065 — google-maps/index.vue 前端 4 tab

**状态**：PARTIAL-NON-BLOCKER（I028 + C1 + EXT-22 已实现 enablePlaceDetails el-switch）
**验证类型**：Static-Only（Playwright MCP 未配置，降级）
**测试时间**：2026-09-14 00:39 (UTC+8)

## 1. 文件 L0 静态

- 文件：`frontend/meiyetec-web/src/views/google-maps/index.vue`
- 总行数：175 行

## 2. 4 Tab 静态确认

| Tab | name | 行号 | 内容 |
|---|---|---|---|
| 配置 | `config` | L4 | API Key + 默认缩放 + **enablePlaceDetails el-switch**（EXT-22） + 保存按钮 |
| 门店地图 | `stores` | L48 | 5 门店列表 + heatmap 摘要 |
| 地理编码测试 | `geocode` | L60 | storeId + address 输入 + 解析按钮 |
| Place Details 测试 | `place-details` | L74 | placeId + language 输入 + 查询按钮 |

## 3. EXT-22 新增 el-switch 实证

L13-23：

```vue
<el-form-item label="Place Details（计费）">
  <el-switch
    v-model="enablePlaceDetails"
    :active-value="1"
    :inactive-value="0"
    active-text="启用（按次计费：$0.025/次）"
    inactive-text="关闭（仅 Geocoding）"
    inline-prompt
    style="--el-switch-on-color: #f56c6c;"
  />
  <el-tooltip content="启用后 Place Details 接口将按 Basic+Contact+Atmosphere 三 SKU 计费" placement="top">
    <el-icon style="margin-left: 8px;"><Warning /></el-icon>
  </el-tooltip>
</el-form-item>
```

→ **EXT-22 §0.2「前端无 UI 入口」**与代码不符 — **前端 el-switch 实际存在**。
→ 这是 EXT-22 Phase 3 静态分析时**漏判**的发现，应在 issue-register.md 追加 issue。

## 4. C1 configForm 字段（实测）

L138：

```typescript
const configForm = ref({ apiKey: '', defaultZoom: 13, enablePlaceDetails: 0 })
```

→ **3 字段**。**缺**：
- `defaultLanguage`（默认 zh-CN）
- `defaultRegion`（默认 cn）
- `quotaDaily`（默认 10000）
- `enabled`（默认 1）

→ 后端 service L62-65 默认填充，但用户**无法**自定义。
→ **C1 PARTIAL-NON-BLOCKER** 维持。

## 5. I036 Key 渲染（前端实证）

L40：

```vue
<el-alert v-if="config.configured" type="success" :closable="false" show-icon style="margin-top: 16px;">
  已配置 API Key (尾号 {{ config.apiKey }})
</el-alert>
```

→ 前端拿到后端响应 `apiKey: ***xxxx` 后渲染"尾号"。
→ **I036 视觉层实证**：即使后端表缺失，前端也已经把这个字段作为视觉字段渲染（I036 双层暴露）。

## 6. Phase 5 验证缺失

Playwright MCP 未配置 → Phase 5 走 Static-Only 路径。
D12 不加 UI → 不强制 Playwright 跑 F065 脚本。

## 7. 结论

| 维度 | 结果 |
|---|---|
| 4 tab 静态确认 | ✅ |
| EXT-22 el-switch | ✅ L13-23（**修正静态分析漏判**） |
| C1 configForm 缺字段 | ✅ PARTIAL-NON-BLOCKER |
| I036 Key 渲染层暴露 | ✅ L40 实证 |

**最终状态**：**PARTIAL-NON-BLOCKER**

**Phase 3 漏判修正**：
- 原结论：「前端无对应开关 UI」→ 实测**前端 el-switch 已实现**
- 影响：I028（前端缺字段 UI）的 EXT-22 子项应**部分反向**（已实现）
- 修正建议：在 issue-register.md §十九 I028 追加「google-maps enablePlaceDetails el-switch 已实现」备注

**修复 Phase**：Post-Audit（D12 不加 UI；I036 仅标注不改，D19）