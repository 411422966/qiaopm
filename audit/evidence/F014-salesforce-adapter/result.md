# F014 — Salesforce 适配器（Static-Only + I002 阻塞）

> **Phase**：5B Runtime Verification
> **Feature ID**：F014
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 1. 静态分析 L0

文件：`backend/src/main/java/com/meiyetec/crm/SalesforceCrmAdapter.java`

### 1.1 类结构

| 元素 | 行号 | 内容 |
|---|---|---|
| `@Component` | 54 | Spring 注册 |
| `implements CrmAdapter` | 55 | 实现接口 |
| `platformCode()` | 61 | 返回 `"SALESFORCE"` |
| `authenticate(CrmIntegration cfg)` | 70 | OAuth2 JWT Bearer Flow |
| `fetchContactsIncremental(CrmIntegration cfg, SyncCursor cursor)` | 229 | SOQL 增量同步 |
| `upsertContact(CrmIntegration cfg, Map contact)` | 307 | `/sobjects/Contact/{id}` upsert |
| `healthCheck(CrmIntegration cfg)` | 333 | 鉴权 + INSTANCE_OK |

### 1.2 OAuth2 JWT Bearer Flow（EXT-24 §0.6 完整实现）

```java
public AuthResult authenticate(CrmIntegration cfg) {
    long start = System.currentTimeMillis();
    if (cfg == null) return AuthResult.fail("CONFIG_MISSING", "配置缺失");
    if (cfg.getClientId() == null || cfg.getUsername() == null
            || cfg.getPrivateKey() == null || cfg.getLoginUrl() == null) {
        return AuthResult.fail("OAUTH2_JWT_INPUT_MISSING", ...);
    }
    
    // 1. 缓存命中
    if (cfg.getAccessToken() != null && cfg.getTokenExpireAt() != null
            && cfg.getTokenExpireAt().isAfter(LocalDateTime.now().plusMinutes(2))) {
        // 复用 token
    }
    
    // 2. 重新签发 JWT（RS256 + 5min exp）
    String jwt = buildJwt(cfg);
    String tokenUrl = cfg.getLoginUrl() + "/services/oauth2/token";
    String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer" + "&assertion=" + jwt;
    POST → parse access_token / instance_url / refresh_token / expires_in
}
```

✅ **完全符合 EXT-24 §0.6 描述**：
- OAuth2 JWT Bearer Flow (RS256)
- 4 必需字段：clientId / username / privateKey(PEM) / loginUrl
- 缓存：`tokenExpireAt > now() + 2min` 直接复用
- JWT exp = `Instant.now().getEpochSecond() + 300`（5 分钟）

### 1.3 SOQL 构造与字段映射

| 元素 | 行号 | 内容 |
|---|---|---|
| `DEFAULT_FIELD_MAP` | 165 | `Map.of("externalId","Id","firstName","FirstName",...,"updatedAtMs","SystemModstamp")` |
| `parseObjectMappings(CrmIntegration cfg)` | 190 | JSON 解析 + merge 默认（putIfAbsent 语义）|
| `buildSoqlSelectClause(Map)` | 216 | 强制加入 Id + SystemModstamp + 遍历 fieldMap |

✅ **完全符合 EXT-24 §0.2 描述**

## 2. 运行实测尝试

### 2.1 无 healthCheck API endpoint

**实测结论**：
```
$ grep -E "health|/health" CrmIntegrationService.java
→ No matches found
```

→ **F014 无独立 healthCheck API endpoint**（plan §一.2 假设的 `POST /{id}/health` 不存在）

### 2.2 tick 路径触发 SF

`CrmSyncScheduler.tick:46-66`：

```java
public void tick() {
    List<CrmIntegration> enabled;
    try {
        enabled = integrationMapper.selectList(...);  // ← I002 阻塞
    } catch (Exception e) {
        log.warn("[CRM-Scheduler] 拉取集成列表失败：{}", e.getMessage());
        return;                                       // ← tick 终止，SF adapter 永远到不了
    }
}
```

→ **I002 在 tick 第一阶段 selectList 抛错 → catch 吞 → return → SF adapter 永远不被调用**

### 2.3 syncNow 路径触发 SF

`CrmIntegrationService.syncNow:88`：
```java
@Transactional
public Result<Map<String, Object>> syncNow(...) {
    CrmIntegration cfg = integrationMapper.selectById(id);  // ← I002 阻塞
    ...
}
```

→ I002 在 syncNow:88 selectById 抛错 → SF adapter 永远到不了

## 3. SQL 旁路尝试（无效果）

由于 plan §一.2 F014 DB 断言为可选，且 DB 缺 3 列（`private_key`/`username`/`login_url`），即便用 SQL 直接 UPDATE 也无法触发 SF 鉴权链路（需配合真实 SF 沙箱）。

## 4. ✅/⚠ 验证结论

| 维度 | 状态 |
|---|---|
| SF 适配器静态实现 | ✅ 完整（OAuth2 JWT + SOQL + upsert + health）|
| 与 EXT-24 §0.6 一致性 | ✅ 完全一致 |
| e2e 测试可执行性 | ❌ 无 healthCheck API + I002 阻塞 tick/syncNow |
| 真实 SF 凭据 | ❌ 未提供（联调无法验证）|

### **最终状态**：⚠ **UNVERIFIED**（无法 e2e 验证 SF 真实链路）

## 5. 5B 继承式策略影响

- F014 = UNVERIFIED（无法 e2e + 无真实凭据）
- 与 plan §一.2 F014 "API-only（受限）" 描述一致
- **F014 修复路径（不实施）**：
  1. 跑 `migrate_ext_15_crm_real.sql`（补 9 列）→ I002 解除
  2. 添加 `POST /crm-integration/{id}/health` endpoint
  3. 提供真实 SF 沙箱凭据 → 联调验证

## 6. 关联

- `audit/issues/I002-crm-ddl-missing.md`（待建）
- `audit/runtime/plan.md` §一.2 F014
- `audit/runtime/report-5A.md` §0.1（5A 未测 F014）
- `audit/evidence/F013-adapter-contract/result.md`（F013 接口层验证 SF 实现完整）
