# F075 EcommerceService 平台 CRUD — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：C4（tenantId=0L 强制）+ C4（updatePlatform 无 tenant 校验）+ 真实 Shadow（/health 无前端）+ I029 PARTIAL-MISSING

---

## 1. 实证结论

**状态**：**PARTIAL-NON-BLOCKER**（POST 200 + GET 500 + DB 写入验证 + C4 实证 + I029 暴露）

| 端点 | 期望 | 实际 | 状态 |
|---|---|---|---|
| **POST /ecommerce/platform** | INSERT 成功 | **200**（DB 写入 2 行）| ✅ 实证 |
| **GET /ecommerce/platform/list** | list 全表 | **500**（I029 PARTIAL-MISSING）| ⚠️ 阻断 |
| **GET /ecommerce/platform/1/health** | health check | **500**（I029 PARTIAL-MISSING）| ⚠️ 阻断 |

## 2. 实证证据

### 2.1 POST /platform 200

```bash
POST /ecommerce/platform
Body: {"platform":"YOUZAN","shopName":"test-shop","shopId":"test-kdt-5B"}
Response: code=200 message="已添加"
```

DB 写入验证：
```
id  tenant_id  platform  shop_name    shop_id       status  created_at
3   0          YOUZAN    test-shop    test-kdt-5B   ACTIVE  2026-09-14 01:01:50
2   0          YOUZAN    test-shop    test-kdt-5B   ACTIVE  2026-09-14 01:01:25
1   0          WEIDIAN   S            WD1           ACTIVE  2026-08-17 00:49:18
```

### 2.2 GET /list 500 — I029 PARTIAL-MISSING 暴露

后端日志：
```
Caused by: java.sql.SQLSyntaxErrorException: Unknown column 'app_id' in 'field list'
```

→ MyBatis-Plus `selectList` 默认 SELECT * → 触发实体声明的 `appId` 列 → DB 缺列 → SQL 异常

### 2.3 C4 实证（N18/N19）

- ✅ **N18 tenantId=0L 强制**：DB 行 2/3 都 tenant_id=0，证明 `EcommerceService.java:69` `p.setTenantId(0L)` 生效
- ✅ **N19 updatePlatform 无 tenant 校验**：`EcommerceService.java:82-89` 不读 TenantContextHolder，直接 `updateById`，任何租户的 token 都能改任何 platform

## 3. 真实 Shadow（D20）

- `/platform/{id}/health` 后端有完整实现，前端**无任何 update 按钮**触发
- → 沿用 I006 / I007 / Batch 1.2 P3EcosystemSuiteService 真实 Shadow 模式
- D20 决策：维持 PARTIAL-NON-BLOCKER，不加 UI

## 4. 双重阻断（5B 新发现）

F075 暴露 **I029 PARTIAL-MISSING 的双层影响**：
1. **POST 成功**（INSERT 不读 app_id 列）— 表能写
2. **GET 失败**（SELECT * 触发 app_id 缺失）— 表不可读

→ **与 5A MISSING 不同**：整表缺失 → 所有路径 500；列缺失 → 仅 SELECT 路径 500

## 5. Issue 状态

| Issue | 关联 | 状态 |
|---|---|---|
| **I029** | 表存在，缺 3 列 | PARTIAL-MISSING（已修正 5A） |
| **C4** | tenantId=0L 强制 | ✅ 实证 PASS |
| **C4** | updatePlatform 无 tenant 隔离 | ✅ 实证 PASS |
| **真实 Shadow** | /platform/{id}/health 无前端 | 维持 PARTIAL-NON-BLOCKER |

## 6. 证据文件

- `raw-create.txt` — POST 200 响应
- `raw-list.txt` — GET 500 响应
- `raw-health.txt` — /health 500 响应
- `db-row-check.txt` — DB 写入验证（3 行）
- `backend-log-tail.txt` — Unknown column 'app_id' 异常栈
