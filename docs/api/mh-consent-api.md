# 知情同意 / 药械追溯 · 接口文档

> 维护者：美业 SaaS 研发组  
> 模块：P3 · T-P06 电子知情同意与药械追溯  
> 基线：`mh_consent_template`、`mh_consent_signature`  
> 鉴权：所有接口走 RBAC，需要登录后访问；`tenantId` 由后端从 `TenantContextHolder` 注入，禁止前端传入。

---

## 1. KPI 聚合

### `GET /api/mh/consent/stats`

合规记录页头部 KPI 卡片聚合。供前端 `refreshKpi()` 调用。

**入参**：无

**返回示例**：

```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "signTotal": 1248,
    "signValid": 1106,
    "signRevoked": 35,
    "signExpired": 22,
    "signNew30d": 87,
    "tplPending": 3,
    "expiringSoon": 12,
    "trend7d": [
      { "date": "2026-08-25", "count": 9 },
      { "date": "2026-08-26", "count": 14 },
      { "date": "2026-08-27", "count": 11 },
      { "date": "2026-08-28", "count": 17 },
      { "date": "2026-08-29", "count": 8 },
      { "date": "2026-08-30", "count": 13 },
      { "date": "2026-08-31", "count": 15 }
    ]
  }
}
```

**字段口径**：

| 字段 | 类型 | 口径 |
|---|---|---|
| `signTotal` | long | `mh_consent_signature.deleted = 0` 全部行数 |
| `signValid` | long | `status = 1`（已签署且未撤回/过期） |
| `signRevoked` | long | `status = 2`（已撤回） |
| `signExpired` | long | `status = 3`（已过期） |
| `signNew30d` | long | `create_time >= NOW() - 30d` |
| `tplPending` | long | `mh_consent_template.audit_status IN (1, 3)`（待审 + 驳回） |
| `expiringSoon` | long | `status = 1` 且 `effective_at + 180 天 ∈ [NOW(), NOW() + 7 天]` |
| `trend7d` | array | 近 7 天（含今天）每日新增签署数；缺日补 0 |

**业务约定**：

- 电子知情同意书默认有效期 **180 天**（业内通行做法，《医疗美容服务管理办法》未强制）。
- `expiringSoon` 与 `trend7d` 是计算字段，不存表，SQL 实时算。
- 所有统计强制带 `tenant_id = ?` 过滤。

**错误码**：

| code | 含义 |
|---|---|
| 200 | 成功 |
| 401 | 未登录 |
| 403 | 无权限（RBAC 拦截） |

---

## 2. 模板相关（既有）

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/mh/consent/template` | 新建模板 |
| GET | `/api/mh/consent/template/list` | 模板列表 |
| GET | `/api/mh/consent/template/{id}` | 模板详情 |

## 3. 签署相关（既有）

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/mh/consent/sign` | 创建签署 |
| GET | `/api/mh/consent/sign/page` | 签署分页 |
| POST | `/api/mh/consent/sign/{id}/revoke` | 撤回签署 |
| GET | `/api/mh/consent/stats` | **KPI 聚合（本轮新增）** |

## 4. 药械主档 + 批次（既有）

略，详见 `ConsentAndDrugDeviceService.java`。

---

## 5. 变更记录

| 日期 | 版本 | 内容 |
|---|---|---|
| 2026-08-31 | v1.1 | 新增 `/consent/stats` KPI 聚合接口 |
