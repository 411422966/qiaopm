# R2.0-A 数据隔离验证场景（前端可验证）

> 后端已经跑通，账号 / 数据范围 / 越权拦截均已确认。
> 浏览器侧只需要登录对应账号即可看到效果。

## 测试账号

| 用户名 | 密码 | 角色 | 门店 | dataScope | 期望结果 |
|---|---|---|---|---|---|
| `store_admin_a` | `admin123` | STORE_ADMIN | 1 | STORE | 看到 7 个会员，全是门店 1 |
| `store_admin_b` | `admin123` | STORE_ADMIN | 2 | STORE | 看到 5 个会员，全是门店 2 |

> 备注：后端 `AuthService.login` 当前是**明文密码**比对（开发期），不是 BCrypt。

## 验证步骤

### 1. 登录两个账号，对比"会员列表"

1. 浏览器隐身窗口 1 → `http://localhost:5173`（前端 dev server）→ 登录 `store_admin_a`
2. 进入「会员」菜单 → 列表页
3. 浏览器隐身窗口 2 → 登录 `store_admin_b`
4. 进入「会员」菜单 → 列表页
5. **对比两边行数 / 门店字段**

预期：
- A 看到 7 行，`所属门店=美业SaaS示范店（id=1）`
- B 看到 5 行，`所属门店=Store B（id=2）`

### 2. 越权拦截（DEV 工具）

DEV 工具 → Network → 任意 `/member/page` 请求 → 把 URL 改成：

```
POST http://localhost:8080/member/page?storeId=2
Authorization: Bearer <A的token>
```

预期：**HTTP 403**，body `{"code":403,"message":"无权访问该门店"}`。

### 3. session 信息核验

浏览器控制台：
```js
JSON.parse(localStorage.getItem('rbac.session.v1'))
```

预期：
```json
{
  "userId": 100,
  "username": "store_admin_a",
  "tenantId": 1,
  "roleCode": "STORE_ADMIN",
  "dataScope": "STORE",
  "storeIds": [1]
}
```

## 已落地代码

| 路径 | 作用 |
|---|---|
| `backend/src/main/java/com/meiyetec/entity/Member.java` | 加 `tenantId` 字段 |
| `backend/src/main/java/com/meiyetec/controller/MemberController.java` | page 接口按 `ctx.tenantId + ctx.storeIds` 自动过滤 |
| `backend/src/main/resources/db/sql/patch_r2_0_a_tenant_id.sql` | 给 `sys_user`/`crm_member` 加 `tenant_id` 列 |
| `backend/src/main/resources/db/sql/seed_r2_0_a_cross_store.sql` | 跨门店测试数据 |
| `docs/SECURITY-权限码映射表.md` | T-A01 权限码命名规范 v1 |

## 未做事项（下一轮）

- [ ] **T-A03 余下 11 个模块**：BaseEntityService 自动注入，只对 MemberController 试点。
  其它 Controller 仍依赖业务层手动拼接 `tenant_id/store_id`。
- [ ] **T-A05 sys_menu_permission**：菜单级权限接入 RBAC。当前前端 Layout 仍走 fallback。
- [ ] **T-A06/T-A07 前端**：菜单动态渲染 + `v-permission` 指令。
- [ ] **T-A08 跨租户联调**：补一个 tenantId=2 的租户，跑跨租户访问 403 验证。

## 接口侧验证脚本（curl）

```bash
# 登录 A
TOKA=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"store_admin_a","password":"admin123"}' | jq -r .data.token)

# 列表（自动 scope）
curl -s -X POST http://localhost:8080/member/page \
  -H "Authorization: Bearer $TOKA" -H 'Content-Type: application/json' \
  -d '{"pageNum":1,"pageSize":50}' | jq .data.total

# 越权（应 403）
curl -i -X POST 'http://localhost:8080/member/page?storeId=2' \
  -H "Authorization: Bearer $TOKA" -H 'Content-Type: application/json' \
  -d '{"pageNum":1,"pageSize":50}'
```
