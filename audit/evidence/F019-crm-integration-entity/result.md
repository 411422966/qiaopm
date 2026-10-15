# F019 — CrmIntegration Entity（Static + DB 比对）

> **Phase**：5B Runtime Verification
> **Feature ID**：F019
> **起点 commit hash**：`12a97ea57a98d268c864fc7c010b82b305d7bb93`

---

## 1. Entity 静态 L0

文件：`backend/src/main/java/com/meiyetec/entity/CrmIntegration.java`

### 1.1 类结构

```java
@Data
@TableName("crm_integration")
public class CrmIntegration {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String platform;            // SALESFORCE / HUBSPOT / MOCK
    private String appId;
    private String clientId;
    private String clientSecret;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpireAt;
    private String apiBaseUrl;
    private String portalId;
    private String syncDirection;       // PUSH / PULL / BIDIRECTIONAL
    private Integer syncMembers;
    private Integer syncOrders;
    private Integer syncContacts;
    private Integer syncDeals;
    private Integer autoSync;
    private LocalDateTime lastSyncAt;
    private String status;              // ACTIVE / DISABLED / ERROR
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;

    // EXT-15 扩展字段
    private String syncToken;
    private String syncSchedule;
    private Integer syncEnabled;
    private LocalDateTime lastIncrementalSyncAt;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) private String privateKey;
    private String username;
    private String loginUrl;
    private String objectMappings;
    private Integer syncWindowMinutes;

    // transient（不入库）
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) private transient String confirmPassword;
}
```

**统计**：Entity 声明 32 个持久化字段 + 1 个 transient 字段。

## 2. DB 实测（SHOW COLUMNS）

完整：`audit/evidence/F019-crm-integration-entity/db-columns.txt`

| DB 列 | 类型 | Entity 字段 | 匹配 |
|---|---|---|---|
| id | bigint PK | id | ✅ |
| tenant_id | bigint | tenantId | ✅ |
| platform | varchar(64) | platform | ✅ |
| app_id | varchar(128) | appId | ✅ |
| client_id | varchar(128) | clientId | ✅ |
| client_secret | varchar(255) | clientSecret | ✅ |
| access_token | varchar(500) | accessToken | ✅ |
| refresh_token | varchar(500) | refreshToken | ✅ |
| token_expire_at | datetime | tokenExpireAt | ✅ |
| api_base_url | varchar(255) | apiBaseUrl | ✅ |
| portal_id | varchar(64) | portalId | ✅ |
| sync_direction | varchar(32) | syncDirection | ✅ |
| sync_members | tinyint | syncMembers | ✅ |
| sync_orders | tinyint | syncOrders | ✅ |
| sync_contacts | tinyint | syncContacts | ✅ |
| sync_deals | tinyint | syncDeals | ✅ |
| auto_sync | tinyint | autoSync | ✅ |
| last_sync_at | datetime | lastSyncAt | ✅ |
| status | varchar(16) | status | ✅ |
| error_message | varchar(500) | errorMessage | ✅ |
| created_at | datetime | createdAt | ✅ |
| updated_at | datetime | updatedAt | ✅ |

**统计**：DB 22 列 ↔ Entity 22 个核心字段 = ✅ 完全匹配

## 3. ⚠️ I002 — Entity 缺 9 列 EXT-15 字段

| Entity 字段 | migrate_ext_15_crm_real.sql 定义 | DB 中存在？ |
|---|---|---|
| syncToken | `ADD COLUMN sync_token VARCHAR(255)` | ❌ 否 |
| syncSchedule | `ADD COLUMN sync_schedule VARCHAR(64) DEFAULT '0 */30 * * * *'` | ❌ 否 |
| syncEnabled | `ADD COLUMN sync_enabled TINYINT DEFAULT 1` | ❌ 否 |
| lastIncrementalSyncAt | `ADD COLUMN last_incremental_sync_at DATETIME` | ❌ 否 |
| privateKey | `ADD COLUMN private_key MEDIUMTEXT` | ❌ 否 |
| username | `ADD COLUMN username VARCHAR(128)` | ❌ 否 |
| loginUrl | `ADD COLUMN login_url VARCHAR(255)` | ❌ 否 |
| objectMappings | `ADD COLUMN object_mappings TEXT` | ❌ 否 |
| syncWindowMinutes | `ADD COLUMN sync_window_minutes INT DEFAULT 30` | ❌ 否 |

→ **9/9 EXT-15 字段全部缺失 = I002 完整验证**

## 4. Entity transient 字段

```java
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private transient String confirmPassword;
```

| 维度 | 评估 |
|---|---|
| `transient` 关键字 | ✅ 不参与序列化 |
| `@JsonProperty(WRITE_ONLY)` | ✅ 仅写入不读回 |
| 但有 setter/getter | ⚠ Lombok `@Data` 仍会生成 getter/setter（虽然 transient，但 JSON 反序列化可能因 setter 而被注入）|

→ **F019 静态层 PASS**（transient 声明正确）

## 5. 关键负向证据

### 5.1 MyBatis-Plus INSERT silent-ignored（5A 已确认）

5A F001 测试：插入 id=2 成功，但 10 列（shopName / syncWindowMinutes / objectMappings / privateKey / syncEnabled / lastIncrementalSyncAt / syncToken / syncSchedule / username / loginUrl）全部 silent-ignored（API 响应含值，DB 实际为 NULL）。

### 5.2 MyBatis-Plus SELECT BadSqlGrammarException（5A + 5B 已确认）

5A F002 测试：GET /crm-integration/list → `Unknown column 'sync_token'` → 500

5B 全部 sync 链路：F004 / F005 / F008 / F009 / F010 全部抛 `Unknown column 'sync_token'`

→ MyBatis-Plus 自动生成 SELECT 含全部 9 个缺列 → **任何一个缺列都让 SELECT 失败**

## 6. ✅/⚠ 验证结论

| 维度 | 状态 |
|---|---|
| Entity 核心字段 | ✅ 22/22 与 DB 完全匹配 |
| Entity EXT-15 字段 | ✅ 9/9 声明完整（但 DB 缺）|
| transient 字段 | ✅ 声明正确（不持久化）|
| @JsonProperty WRITE_ONLY（privateKey / confirmPassword）| ✅ 配置正确 |
| 静态层完整性 | ✅ VERIFIED |
| 运行时完整性 | ❌ BROKEN（I002 阻塞所有 SELECT）|

### **最终状态**：⚠ **PARTIAL-NON-BLOCKER**

（Entity 静态层完整 / DB 缺 9 列导致运行时所有 SELECT 失败）

## 7. 5B 继承式策略影响

- F019 = PARTIAL-NON-BLOCKER
- 不被继承
- **F020/F021** 应引用本文件作为 "BROKEN (inherited from I002 via 5B F019)" 的实证依据

## 8. 关联

- `audit/issues/issue-register.md` §十 I002
- `audit/issues/I002-crm-ddl-missing.md`（待建）
- `backend/src/main/resources/db/sql/migrate_ext_15_crm_real.sql`（DDL 已就绪未跑）
- `audit/runtime/plan.md` §一.2 F019
- `audit/runtime/report-5A.md` §1 F001（10 列 silent）+ §2 F002（select 失败）
