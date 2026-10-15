# Phase 5A — Runtime Verification Report (Batch 1.3 / Google Maps)

> **创建时间**：2026-09-14 00:42 (UTC+8)
> **Phase**：5A — Runtime Verification（运行时实证）
> **范围**：Batch 1.3 / EXT-22 Google Maps 侧（10 个 Feature：F056-F065）
> **起点 commit**：`e85ba8ba8540081f2f59c97120052b5bef5a29ef`（Batch 1.2 freeze）
> **不绑定业务代码改动**（规则 6）

---

## 〇、运行时栈核查

| 维度 | 状态 | 来源 |
|---|---|---|
| 后端 8080 | ✅ PID 7216，meiyetec-backend 启动成功 | `Get-NetTCPConnection -LocalPort 8080` |
| MySQL 3307 | ✅ PID 12616/15528，MySQL 8.0.46 | `mysql --version` + `SELECT VERSION()` |
| DB `meiyetec` | ✅ 可连 | `mysql -h 127.0.0.1 -P 3307 -uroot -proot123456` |
| 后端 `/google-maps/health` | ⚠️ 200 + body code=500（I029 暴露） | curl |
| Token 拿取 | ✅ admin (超管) + clerk01 (普通租户) | POST `/auth/login` |
| 后端日志 | ✅ `backend/logs/meiyetec.log` 活动 | LastWriteTime |

> **D10 铁律维持**：GCP 凭据缺失 → UNVERIFIED（F058/F059），不造证据。

---

## 一、Feature 实证汇总（F056-F065 + F066 修正）

| Feature | 状态 | 负向证据 | 关联 Issue |
|---|---|---|---|
| **F056** GoogleMapsConfig 实体 | **MISSING** | N1: 表不存在 (`TableNotFoundException`) | **I029** |
| **F057** GoogleMapsGeocodeLog 实体 | **MISSING** | N2: 表不存在；try/catch 静默丢日志 | **I029** |
| **F058** GoogleMapsGeocodingClient | **UNVERIFIED** | D10: GCP API Key 缺失 | D10 |
| **F059** GoogleMapsClientConfig | **UNVERIFIED** | D10 + 无 fallback 设计 | D10 |
| **F060** `/config` + `/health` | **PARTIAL-NON-BLOCKER** | N5(I036 代码层) + I029 暴露 + C1 缺字段 | **I036 / C1** |
| **F061** `/geocode` Mock 降级 | **MOCK-INTENTIONAL** | 代码层可达 (L335-348)；I029 优先暴露 | D13 |
| **F062** `/place/details` | **PARTIAL-NON-BLOCKER** | N6(代码层 PERMISSION_DENIED) + N7(代码层 100% 未写日志) + I029 暴露 | **I031 / I029** |
| **F063** `/geocode/batch` | **PARTIAL-NON-BLOCKER** | N8(geocode_batch_log 缺失) + I028 admin 守卫实证 + I029 暴露 | **I032 / I028** |
| **F064** `/stores` + `/heatmap` + `/geocode/log` | **PARTIAL-NON-BLOCKER** | N17(`/geocode/log` 真实 Shadow) + I029 暴露 | **N17 / I029** |
| **F065** `google-maps/index.vue` | **PARTIAL-NON-BLOCKER** | C1 + I036 + **EXT-22 el-switch 修正** | **C1 / I036 / 漏判修正** |
| **F066** `EcommercePlatform` 实体 | **PARTIAL-MISSING**（**修正**）| N3'(表存在，缺 app_id/kdt_id/sign_secret 3 列) | **I029 / I028** |

---

## 二、负向证据实证清单（N1-N8 + N17）

| # | 验证目标 | 预期 | 实际 | 状态 |
|---|---|---|---|---|
| **N1** | `google_maps_config` 表缺失 | `TableNotFoundException` | ✅ `ERROR 1146 (42S02) at line 1: Table 'meiyetec.google_maps_config' doesn't exist` | **PASS** |
| **N2** | `google_maps_geocode_log` 表缺失 | 同上 | ✅ `ERROR 1146 (42S02): Table 'meiyetec.google_maps_geocode_log' doesn't exist` | **PASS** |
| **N3** | `ecommerce_platform` 表缺失 | 同上 | ⚠️ **修正**：表实际**存在**（20 字段），缺 EXT-22 三个新列（app_id / kdt_id / sign_secret）→ N3' | **PARTIAL** |
| **N4** | db-snapshot grep | 0 hits | ✅ google_maps_config:0 / google_maps_geocode_log:0 / ecommerce_platform:0 / enable_place_details:0 / kdt_id:0 / sign_secret:0；app_id:1 (crm_integration 误命中，非 EXT-22) | **PASS** |
| **N5** | GCP API Key `getConfig` 泄露 | 响应 `***xxxx` | ⚠️ **降级**：I029 MISSING 导致 Phase 5 实证不了真实响应，但 **代码层 L87 实证**（字符串拼接） | **DEGRADED** |
| **N6** | `enable_place_details=0` → PERMISSION_DENIED | `BizCode.PERMISSION_DENIED` | ⚠️ **降级**：I029 MISSING 在 step 1（apiKey 检查）抛 SQL 异常覆盖了 step 2；但 **代码层 L218-221 实证**（PERMISSION_DENIED 逻辑可达） | **DEGRADED** |
| **N7** | Place Details 未写 log | log 表无新增行 | ✅ **代码层 100% 实证**：`placeDetails` L200-240 **完全不引用** logMapper（对比 geocode L160-176 有 try/catch insert） | **PASS（代码层）** |
| **N8** | `geocode_batch_log` 表缺失 | 表不存在 | ✅ `SHOW TABLES LIKE 'geocode_batch_log'` → `Empty set (0 hits)` | **PASS** |
| **N17** | `/geocode/log` 前端无消费 | grep 0 命中 | ✅ `google-maps/index.vue` 全文 175 行无 `geocode/log` 调用 | **PASS** |

### 2.1 Phase 5 实证降级说明（N5 / N6）

| 项目 | 降级原因 | 实证状态 |
|---|---|---|
| N5 I036 Key 泄露 | I029 MISSING → `/config` 返回 500 而非真实 config | **代码层 PASS / 运行时 DEGRADED** |
| N6 PERMISSION_DENIED | I029 MISSING → step 1 SQL 异常覆盖 step 2 | **代码层 PASS / 运行时 DEGRADED** |

→ 这两项 Issue **不撤销**（代码层证据已足够），但 Phase 5 运行时实证需建表后补一次。
→ 建议 Post-Audit Phase 7 在 I029 修复后**补一次 Phase 5.5 验证**。

---

## 三、跨 Feature 发现

### 3.1 Phase 3 漏判修正：EXT-22 前端 el-switch 已实现

**原结论**（plan-batch-1.3.md §0.2 + static-gap-report.md §二十）：
> "前端无对应开关 UI" / "前端缺 UI"

**Phase 5 实测**（`google-maps/index.vue:13-23`）：

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
  ...
</el-form-item>
```

→ **el-switch 实际存在**，含 tooltip 警告 + 计费说明。
→ 影响范围：
  - F065 维持 PARTIAL-NON-BLOCKER（C1 + I036 仍存在）
  - 但 I028（前端缺字段 UI）的 EXT-22 子项**部分反向**（已实现）

**修正建议**：
1. `audit/issues/issue-register.md` §十九 I028 追加「google-maps enablePlaceDetails el-switch 已实现」备注
2. `audit/reports/static-gap-report.md` §二十 修正对应描述
3. Phase 3.5 复审建议：F065 el-switch 已实现部分从 PARTIAL-NON-BLOCKER 降为 MOCK-INTENTIONAL（与 F061 一致）

### 3.2 I036 双层暴露（代码层 + 前端渲染层）

| 层 | 位置 | 实证 |
|---|---|---|
| 后端 service | `GoogleMapsService.java:87` | ✅ 字符串拼接 `"***" + apiKey.substring(...)` |
| 前端 view | `google-maps/index.vue:40` | ✅ `已配置 API Key (尾号 {{ config.apiKey }})` |

→ I036 不止后端泄露，**前端**也把 `***xxxx` 直接渲染给用户。
→ 攻击模型：用户登录即可看到自己租户的 apiKey 尾 4 位 — 配合 N5 阶段 1 实证的代码层证据，I036 维持。

### 3.3 I028 admin 守卫实证（F063）

`GoogleMapsService.geocodeBatch` L259-263：

```java
String role = TenantContextHolder.getRoleCode();
if (!"SUPER_ADMIN".equals(role) && !"PLATFORM_ADMIN".equals(role) && !"HQ_ADMIN".equals(role)) {
    return Result.error(BizCode.PERMISSION_DENIED, "批量 geocode 仅管理员可用");
}
```

→ admin token (SUPER_ADMIN) → 200（虽 I029 暴露 500）
→ clerk01 token (非 SUPER) → **403 PERMISSION_DENIED** ← **I028 实证成功**

### 3.4 I029 暴露链路

后端启动 → Mapper 注入成功 → Service 调用 `configMapper.selectOne(...)` → SQL 抛 `BadSqlGrammarException` → Controller 返回 500。
→ 这条链路在以下端点都触发：
- `/config` (F060) L88
- `/health` (F060) L113
- `/geocode` (F061) L145
- `/place/details` (F062) L206
- `/geocode/batch` (F063) L272
- `/geocode/log` (F064) L379

→ **I029 暴露在所有 6 个端点**，DB MISSING 影响范围广。

---

## 四、Feature 单一归属校验

| Feature | 单一归属 | 复核 |
|---|---|---|
| F056 | MISSING | ✅ 维持 |
| F057 | MISSING | ✅ 维持 |
| F058 | UNVERIFIED | ✅ 维持（D10） |
| F059 | UNVERIFIED | ✅ 维持（D10） |
| F060 | PARTIAL-NON-BLOCKER（I036 + I029 + C1） | ✅ 维持 |
| F061 | MOCK-INTENTIONAL | ✅ 维持 |
| F062 | PARTIAL-NON-BLOCKER（I031 + I029 + C1） | ✅ 维持 |
| F063 | PARTIAL-NON-BLOCKER（I032 + I028 + I029） | ✅ 维持 |
| F064 | PARTIAL-NON-BLOCKER（N17 + I029） | ✅ 维持 |
| F065 | PARTIAL-NON-BLOCKER（C1 + I036 + el-switch 修正） | ✅ 维持（漏判修正） |

---

## 五、5A 输出文件清单

```
audit/evidence/
├── env-token-admin.txt
├── env-token-clerk.txt
├── env-login-admin.txt (临时)
├── F056-googlemaps-config-entity/
│   ├── db-describe.txt
│   └── result.md
├── F057-googlemaps-geocode-log-entity/
│   ├── db-describe.txt
│   └── result.md
├── F058-googlemaps-geocoding-client/
│   └── result.md
├── F059-googlemaps-client-config/
│   └── result.md
├── F060-googlemaps-service-config-health/
│   ├── raw-health.txt
│   ├── raw-config.txt
│   └── result.md
├── F061-googlemaps-service-geocode-mock/
│   ├── raw-geocode-post.txt
│   └── result.md
├── F062-googlemaps-service-place-details/
│   ├── raw-place-details.txt
│   ├── db-log-count.txt
│   └── result.md
├── F063-googlemaps-service-geocode-batch/
│   ├── raw-batch-admin.txt
│   ├── raw-batch-clerk.txt
│   ├── db-batch-log-check.txt
│   └── result.md
├── F064-googlemaps-service-stores-heatmap-log/
│   ├── raw-stores.txt
│   ├── raw-heatmap.txt
│   ├── raw-geocode-log.txt
│   ├── raw-stores-noauth.txt
│   ├── raw-geocode-log-noauth.txt
│   └── result.md
├── F065-googlemaps-frontend-ui/
│   └── result.md
└── F066-platform-entity-stub/  (5B 前置验证)
    └── db-describe.txt
```

---

## 六、未完成 / 等待用户确认

### 6.1 Phase 5B 准备状态

| 项 | 状态 |
|---|---|
| N3 `ecommerce_platform` 表缺失 | ✅ 5A 已前置实证 |
| admin token | ✅ |
| clerk01 token | ✅ |
| 后端日志 | ✅ |

### 6.2 Phase 5B 范围预告

- **F066** `EcommercePlatform` 实体（DB-only，N3 已证）
- **F067** `EcommerceOrder + EcommerceProduct + EcommerceSyncLog`（DB-only）
- **F068-F070** EcommerceAdapter / Youzan authenticate / fetch（UNVERIFIED，有赞凭据缺失）
- **F071** `verifyWebhookSignature` 签名算法（jshell 单元测试 — **Cursor 不能跑 JVM 启动，但 jshell 可作为外部脚本调用**：可通过把字节码 attach 到运行中的 JVM，或用独立 Java 进程模拟）
- **F072** `buildWebhookUrl` 孤儿（Static-Only）
- **F073/F074** Mock + Router（Static + 启动期反推）
- **F075** EcommercePlatform CRUD（API + DB，N18/N19 C4/C4 实证）
- **F076** Ecommerce Sync（API + DB，I034 实证）
- **F077** `POST /webhook/youzan`（Mock 推送样本 + API + DB，I027/I030 实证）
- **F078** webhook 幂等去重（Mock 推送重复 2 次 + DB）
- **F079** `ecommerce/index.vue` 前端 3 tab（Playwright 降级，I028 实证）
- **F080** Frontend API + DB 迁移（DB-only）

### 6.3 关键前置确认

- [ ] **后端 8080 仍在跑？**（Phase 5A 期间确认 ✅）
- [ ] **MySQL 3307 可连？**（Phase 5A 期间确认 ✅）
- [ ] **admin + clerk01 token 仍有效？**（token 24h 过期，Phase 5B 开始需重登）
- [ ] **有赞 push_url 未注册** → F069/F070/F077 维持 UNVERIFIED（D10 铁律不变）
- [ ] **Cursor 不跑 jshell** → F071 算法验证需用户在本地跑（提供 PS 脚本）

---

## 七、修正建议（落到 issue-register.md + static-gap-report.md）

### 7.1 issue-register.md §十九 I028 追加

```markdown
### I028b — google-maps enablePlaceDetails el-switch 已实现（Phase 3 漏判修正）

> **发现时间**：2026-09-14 00:39 (UTC+8)
> **关联 Feature**：F065
> **Phase 5A 实证**：`google-maps/index.vue:13-23` el-switch 实际存在

**原 Phase 3 结论**：「前端无对应开关 UI」（plan-batch-1.3.md §0.2 + static-gap-report.md §二十）

**Phase 5A 修正**：el-switch 已实现（含 tooltip + 计费说明）

**修正动作**：
1. I028 在 EXT-22 google-maps 子项**部分反向**（已实现）
2. F065 el-switch 部分从 PARTIAL-NON-BLOCKER 降为 MOCK-INTENTIONAL（与 F061 一致）
3. static-gap-report.md §二十 修正对应描述
```

### 7.2 static-gap-report.md §二十 修正

```markdown
#### 7. EXT-22 google-maps 前端开关（**Phase 5A 修正**）

**原结论**：前端无对应开关 UI
**Phase 5A 实测**：`google-maps/index.vue:13-23` el-switch 已实现
**修正**：I028 在 google-maps 子项**部分反向**
```

---

## 八、停止点

✅ **5A 完成，等用户确认后启动 5B**。