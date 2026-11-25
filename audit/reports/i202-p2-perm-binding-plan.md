# I202-P2 权限绑定方案 — 方案 A-mid（26 条）+ D2 方案 A

> **关联 Issue**：I202（前后端权限码漂移，全量口径）
> **方案状态**：**已批准执行**（用户 2026-11-26 逐项确认：P1=a / P2=b / P3=a / P4=a / P5=a / P6=方案 A）
> **落地范围**：`sys_role_permission` 补绑 26 行 + `frontend/meiyetec-web/src/router/index.ts` 三行 `meta.permission`
> **环境**：`audit/baseline/env.md`（MySQL 8.0.46 / `localhost:3307` / 库 `meiyetec`，经 Docker 容器 `meiyetec-mysql` 访问同一实例）
> **执行记录**：见 §9（实际运行输出归档于 `audit/evidence/F174-i202-p2-perm-binding/`）

---

## 0. 执行前提与事实基线（复核于 2026-11-26 本机开发库）

| 项 | 实测值 | 复核方式 |
|---|---|---|
| `sys_role_permission` 总行数 | **1267** | `SELECT COUNT(*)` |
| 分角色基线 | 1=118 / 2=47 / 3=0 / 4=54 / 5=304 / 6=224 / 7=105 / 8=415 | `GROUP BY role_id` |
| 孤儿行 | **3**（基线既有，本批不修不碰） | `LEFT JOIN sys_permission` |
| 重复绑定组 | **0** | `GROUP BY role_id, permission_id HAVING COUNT(*)>1` |
| 唯一键 | `uk_role_permission (role_id, permission_id)` | `SHOW CREATE TABLE` |
| 目标 11 码现有绑定行 | **全部 0 行**（`crm:care:recipe:list` 除外，已绑 5/6/8） | 按 `permission_id` 直查 |
| `/spa-scene` 菜单行 | **id=358** → `permission_id=1295`（`booking:spa-scene`），`route_path='/spa-scene'` | `sys_menu_permission` 实查（2026-11-26 复核，P4 确认**行号 358 属实**） |

> **基线口径说明（2026-11-26 实跑后补记）**：上表 1267 及分角色基线为**执行前**口径；26 条 INSERT 落地后为 **1293** / 118·47·0·55·314·229·105·425，差值恰为 +26 且逐角色增量与方案一致（见 §9.2）。

**permission_id 常量对照（本次定稿唯一事实源）**

| 权限码 | id | 本次动作 | 目标角色 |
|---|---|---|---|
| `spa:scene:list` | **1564** | INSERT | 4, 5, 6, 8 |
| `spa:scene:edit` | **1565** | INSERT | 5, 8 |
| `iot:link:list` | **1479** | INSERT | 5, 6, 8 |
| `iot:rule:list` | **1552** | INSERT | 5, 6, 8 |
| `iot:alert:list` | **1548** | INSERT | 5, 6, 8 |
| `iot:data:list` | **1550** | INSERT | 5, 6, 8 |
| `iot:rule:edit` | **1553** | INSERT | 5, 8 |
| `iot:alert:edit` | **1549** | INSERT | 5, 8 |
| `iot:data:edit` | **1551** | INSERT | 5, 8 |
| `iot:link:edit` | **1480** | INSERT | 5, 8 |
| `iot:link:execute` | **1481** | **不动**（P2=b） | — |
| `crm:care:recipe:list` | **1494** | **不动**（P1=a） | 已绑 5, 6, 8 |

---

## 1. 执行 SQL（A-mid 定稿，26 条，全字面量）

```sql
-- ============================================================
-- I202-P2 权限补绑 — 方案 A-mid（26 条）
-- 基线 total = 1267 → 目标 total = 1293
-- 唯一键 uk_role_permission(role_id, permission_id) 保证不重复
-- 全部使用 permission_id 字面量，permission_code 仅作注释
-- ============================================================
START TRANSACTION;

-- ---------- A 段（18 条）----------

-- spa:scene:list (id=1564) → role 4,5,6,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (4, 1564);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1564);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (6, 1564);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1564);

-- spa:scene:edit (id=1565) → role 5,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1565);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1565);

-- iot:link:list (id=1479) → role 5,6,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1479);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (6, 1479);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1479);

-- iot:rule:list (id=1552) → role 5,6,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1552);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (6, 1552);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1552);

-- iot:alert:list (id=1548) → role 5,6,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1548);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (6, 1548);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1548);

-- iot:data:list (id=1550) → role 5,6,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1550);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (6, 1550);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1550);

-- ---------- A-mid 追加段（8 条）----------

-- iot:rule:edit (id=1553) → role 5,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1553);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1553);

-- iot:alert:edit (id=1549) → role 5,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1549);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1549);

-- iot:data:edit (id=1551) → role 5,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1551);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1551);

-- iot:link:edit (id=1480) → role 5,8
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (5, 1480);
INSERT INTO sys_role_permission (role_id, permission_id) VALUES (8, 1480);

-- ---------- 提交前断言（务必先看，再 COMMIT）----------
SELECT COUNT(*) AS inserted_should_be_26 FROM sys_role_permission
WHERE (role_id, permission_id) IN (
  (4,1564),(5,1564),(6,1564),(8,1564),
  (5,1565),(8,1565),
  (5,1479),(6,1479),(8,1479),
  (5,1552),(6,1552),(8,1552),
  (5,1548),(6,1548),(8,1548),
  (5,1550),(6,1550),(8,1550),
  (5,1553),(8,1553),
  (5,1549),(8,1549),
  (5,1551),(8,1551),
  (5,1480),(8,1480)
);   -- 期望 26

COMMIT;
```

> 断言不等于 26 时**必须 `ROLLBACK;`**（`uk_role_permission` 会直接报 1062，属于 fail-fast，不会静默写入脏数据）。
> 幂等性：不使用 `INSERT IGNORE`。重复执行会因唯一键报错终止，符合「写前先查存在性」的守门要求。

---

## 2. 回滚 SQL（26 条一一对应）

```sql
-- ============================================================
-- I202-P2 A-mid 回滚（26 条，与执行脚本逐条镜像）
-- 依据：uk_role_permission(role_id, permission_id)
-- 期望回滚后 total = 1267，分角色 118/47/0/54/304/224/105/415
-- ============================================================
START TRANSACTION;

-- spa:scene:list (1564)
DELETE FROM sys_role_permission WHERE role_id = 4 AND permission_id = 1564;
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1564;
DELETE FROM sys_role_permission WHERE role_id = 6 AND permission_id = 1564;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1564;

-- spa:scene:edit (1565)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1565;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1565;

-- iot:link:list (1479)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1479;
DELETE FROM sys_role_permission WHERE role_id = 6 AND permission_id = 1479;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1479;

-- iot:rule:list (1552)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1552;
DELETE FROM sys_role_permission WHERE role_id = 6 AND permission_id = 1552;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1552;

-- iot:alert:list (1548)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1548;
DELETE FROM sys_role_permission WHERE role_id = 6 AND permission_id = 1548;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1548;

-- iot:data:list (1550)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1550;
DELETE FROM sys_role_permission WHERE role_id = 6 AND permission_id = 1550;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1550;

-- iot:rule:edit (1553)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1553;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1553;

-- iot:alert:edit (1549)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1549;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1549;

-- iot:data:edit (1551)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1551;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1551;

-- iot:link:edit (1480)
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id = 1480;
DELETE FROM sys_role_permission WHERE role_id = 8 AND permission_id = 1480;

-- 回滚后断言
SELECT COUNT(*) AS remaining_should_be_0 FROM sys_role_permission
WHERE (role_id, permission_id) IN (
  (4,1564),(5,1564),(6,1564),(8,1564),
  (5,1565),(8,1565),
  (5,1479),(6,1479),(8,1479),
  (5,1552),(6,1552),(8,1552),
  (5,1548),(6,1548),(8,1548),
  (5,1550),(6,1550),(8,1550),
  (5,1553),(8,1553),
  (5,1549),(8,1549),
  (5,1551),(8,1551),
  (5,1480),(8,1480)
);   -- 期望 0

COMMIT;
```

---

## 3. 验证 SQL（V1–V6，A-mid 定稿）

```sql
-- V1 执行前快照：11 个目标码的 id 与现有绑定数
SELECT p.permission_code, p.id AS permission_id, COUNT(rp.id) AS binds
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('spa:scene:list','spa:scene:edit',
       'iot:link:list','iot:link:edit','iot:link:execute',
       'iot:rule:list','iot:rule:edit',
       'iot:alert:list','iot:alert:edit',
       'iot:data:list','iot:data:edit','crm:care:recipe:list')
GROUP BY p.permission_code, p.id ORDER BY p.permission_code;
-- 期望：除 crm:care:recipe:list=3 外，其余 11 码 binds=0
-- 执行后：spa:scene:list=4 / spa:scene:edit=2 / iot:link:list=3 / iot:rule:list=3
--         iot:alert:list=3 / iot:data:list=3 / iot:rule:edit=2 / iot:alert:edit=2
--         iot:data:edit=2 / iot:link:edit=2 / iot:link:execute=0 / crm:care:recipe:list=3

-- V2 目标 26 行逐条核对
SELECT rp.role_id, rp.permission_id, p.permission_code
FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
WHERE (rp.role_id, rp.permission_id) IN (
  (4,1564),(5,1564),(6,1564),(8,1564),
  (5,1565),(8,1565),
  (5,1479),(6,1479),(8,1479),
  (5,1552),(6,1552),(8,1552),
  (5,1548),(6,1548),(8,1548),
  (5,1550),(6,1550),(8,1550),
  (5,1553),(8,1553),
  (5,1549),(8,1549),
  (5,1551),(8,1551),
  (5,1480),(8,1480)
) ORDER BY rp.role_id, rp.permission_id;
-- 期望 26 行，且 permission_code 与上表一一对应

-- V3 分角色总数
SELECT rp.role_id, r.role_code, COUNT(*) AS cnt
FROM sys_role_permission rp LEFT JOIN sys_role r ON r.id = rp.role_id
GROUP BY rp.role_id, r.role_code ORDER BY rp.role_id;
-- 期望：1=118 / 2=47 / 4=55 / 5=314 / 6=229 / 7=105 / 8=425

-- V4 总数 + 交叉校验
SELECT COUNT(*) AS total FROM sys_role_permission;         -- 期望 1293
SELECT 118+47+0+55+314+229+105+425 AS cross_sum;           -- 期望 1293（与 V4 相等）

-- V5 孤儿行 / 重复组
SELECT COUNT(*) AS orphan_rows FROM sys_role_permission rp
LEFT JOIN sys_permission p ON p.id = rp.permission_id WHERE p.id IS NULL;  -- 期望仍为 3（基线既有，不修）
SELECT COUNT(*) AS dup_groups FROM (
  SELECT role_id, permission_id FROM sys_role_permission
  GROUP BY role_id, permission_id HAVING COUNT(*) > 1) t;                   -- 期望 0

-- V6 未被本批触碰的码确认未变（负向对照）
SELECT p.permission_code, COUNT(rp.id) AS binds
FROM sys_permission p LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id
WHERE p.permission_code IN ('iot:link:execute','crm:care:recipe:list')
GROUP BY p.permission_code;   -- 期望 iot:link:execute=0 / crm:care:recipe:list=3
```

**终值汇总（A-mid）**

| role_id | role_code | 基线 | A-mid | 增量 |
|---|---|---|---|---|
| 1 | SUPER_ADMIN | 118 | 118 | 0 |
| 2 | STORE_ADMIN | 47 | 47 | 0 |
| 3 | EMPLOYEE | 0 | 0 | 0 |
| 4 | CLERK | 54 | **55** | +1（`spa:scene:list`） |
| 5 | MANAGER | 304 | **314** | +10 |
| 6 | BOSS | 224 | **229** | +5 |
| 7 | OPERATOR | 105 | 105 | 0 |
| 8 | ADMIN | 415 | **425** | +10 |
| — | **合计** | **1267** | **1293** | **+26** |

交叉校验：1267 + 26 = **1293** = 118+47+0+55+314+229+105+425 ✅

---

## 4. D2 方案 A — `router/index.ts` 三行 diff

**根因（实测）**：DB 里存在的是 `mh.visual`(id 1251) / `mh.consent`(id 1252)，绑定角色 **1, 6, 8**；前端守卫 meta 写的是 `mh:visual` / `mh:consent`（冒号），**这两个码在 `sys_permission` 中不存在** → 守卫必然 403。前端 `hasPermission` 只做 `permissions.includes(code)` 精确匹配（`stores/permission.ts:62`），不做冒号↔点号归一。

```diff
--- a/frontend/meiyetec-web/src/router/index.ts
+++ b/frontend/meiyetec-web/src/router/index.ts
@@ -153,1 +153,1 @@
-      { path: 'mh/visual', name: 'MhVisual', component: () => import('@/views/mh/visual.vue'), meta: { title: '视觉档案', requiresAuth: true, permission: 'mh:visual' } },
+      { path: 'mh/visual', name: 'MhVisual', component: () => import('@/views/mh/visual.vue'), meta: { title: '视觉档案', requiresAuth: true, permission: 'mh.visual' } },
@@ -154,1 +154,1 @@
-      { path: 'mh/consent', name: 'MhConsent', component: () => import('@/views/mh/consent.vue'), meta: { title: '医美合规', requiresAuth: true, permission: 'mh:consent' } },
+      { path: 'mh/consent', name: 'MhConsent', component: () => import('@/views/mh/consent.vue'), meta: { title: '医美合规', requiresAuth: true, permission: 'mh.consent' } },
@@ -167,1 +167,1 @@
-      { path: 'complication', name: 'Complication', component: () => import('@/views/complication/index.vue'), meta: { title: '并发症 SOP', requiresAuth: true, permission: 'mh:consent' } },
+      { path: 'complication', name: 'Complication', component: () => import('@/views/complication/index.vue'), meta: { title: '并发症 SOP', requiresAuth: true, permission: 'mh.consent' } },
```

**影响面核对**：全前端仅这 3 行引用 `mh:visual` / `mh:consent`（grep 结果唯一），改后 BOSS(6) 放行、MANAGER/CLERK/OPERATOR 仍 403；ADMIN(8)、SUPER_ADMIN(1) 不受影响（`*` 通配 + 已绑 `mh.visual`/`mh.consent`）。菜单侧 `/mh/visual` 在 `sys_menu_permission` 有两行（id 268 → `mh`，id 314 → `mh.visual`），BOSS 凭 `mh.visual`(1251) 已可见，**无需改菜单**。

---

## 5. 验收用例（A-mid 落地后预期；括号内为**属预期**而非失败）

### 5.1 R2-1 SPA 场景

| 账号 | 端点 | 修复前 | A-mid 后预期 |
|---|---|---|---|
| manager01 | `GET /spa-scene/list` | 403 | **200** |
| manager01 | `GET /spa-scene/{id}/recommend` | 403 | **200** |
| manager01 | `GET /spa-scene/{id}/slots` | 403 | **200** |
| manager01 | `POST /spa-scene` / `PUT /spa-scene/{id}` / `DELETE /spa-scene/{id}` | 403 | **200**（写操作；测试用不存在的 ID `99999999` 兜底，避免污染） |
| boss01 | `GET /spa-scene/list` | 403 | **200** |
| boss01 | `POST /spa-scene`（写） | 403 | **403**（`spa:scene:edit` 只绑 role 5/8，未绑 BOSS —— **属预期**） |
| clerk01 | `GET /spa-scene/list` | 403 | **200**（方案 A 已含 role 4） |
| operator01 | `GET /spa-scene/list` | 403 | **403**（未绑 role 7 —— **属预期**） |

### 5.2 R2-2 IoT

| 账号 | 端点 | 修复前 | A-mid 后预期 |
|---|---|---|---|
| clerk01 | `GET /iot/rule/list` | 403 | **403**（未绑 CLERK —— **属预期**） |
| clerk01 | `GET /iot/alert/list` | 403 | **403**（同上 —— **属预期**） |
| clerk01 | `GET /iot/data/list` | 403 | **403**（同上 —— **属预期**） |
| clerk01 | `GET /iot/device/link/list` | 403 | **403**（同上 —— **属预期**） |
| manager01 | `GET /iot/rule/list` | 403 | **200** |
| manager01 | `GET /iot/alert/list` | 403 | **200** |
| manager01 | `GET /iot/data/list` | 403 | **200** |
| manager01 | `GET /iot/device/link/list` | 403 | **200** |
| manager01 | `POST /iot/rule` / `PUT /iot/rule/{id}` | 403 | **200**（A-mid 已补 `iot:rule:edit` → role 5/8） |
| **manager01** | **`POST /iot/device/link/execute`** | **403** | **403 —— 方案 A-mid 下仍 403（`iot:link:execute`(1481) 未绑角色，P2=b 决定不补），属预期，不是失败** |
| boss01 | `GET /iot/rule/list` | 403 | **200** |
| boss01 | `POST /iot/rule`（写） | 403 | **403**（`iot:rule:edit` 只绑 role 5/8，未绑 BOSS —— **属预期**） |
| operator01 | `GET /iot/rule/list` | 403 | **403**（未绑 role 7 —— **属预期**） |
| admin | `GET /iot/rule/list` | 200 | **200**（`*` 通配，不受本批影响） |

### 5.3 R2-3 CRM 配方

| 账号 | 端点 | 修复前 | A-mid 后预期 |
|---|---|---|---|
| clerk01 | `GET /care/recipe/list` | 403 | **403**（P1 决定 a：不补 `crm:care:recipe:list` → CLERK —— **属预期**） |
| clerk01 | `GET /care/assessment/by-member/{id}` | 403 | **403**（同上 —— **属预期**） |
| clerk01 | `POST /care/recommend` | 403 | **403**（同上；注：该 POST 端点注解码为 `recipe:list`，仍不覆盖 CLERK） |
| manager01 | `GET /care/recipe/list` | 200 | **200** |
| boss01 | `GET /care/recipe/list` | 200 | **200** |
| operator01 | `GET /care/recipe/list` | 403 | **403**（未绑 role 7 —— **属预期**） |

### 5.4 D2（mh / complication 路由）

| 账号 | 路由（`router/index.ts`） | 修复前 | D2 后预期 |
|---|---|---|---|
| boss01 | `/mh/visual`（:153） | **403**（库中有 `mh.visual`，前端要求 `mh:visual` → 判定 false） | **200** |
| boss01 | `/mh/consent`（:154） | **403**（同上） | **200** |
| boss01 | `/complication`（:167） | **403**（同上） | **200** |
| manager01 | `/mh/visual` | **403** | **403**（MANAGER 未绑 `mh.visual` —— **属预期**） |

---

## 6. I206 拟登记文本

### I206 — `/mh/visual` + `/mh/consent` + `/complication` 后端 0 个 `@RequirePermission`，前端守卫成为唯一门禁

| 项 | 内容 |
|---|---|
| **优先级** | P1 |
| **类型** | 鉴权缺口（Authorization Gap） |
| **关联** | I204（同型：后端无鉴权 + 前端门禁）、I202（本批 R2/D2 根因）、I207（`views/mh/consent.vue` 契约漂移） |
| **统计口径** | 下列端点数为**方法级端点，不含类级 `@RequestMapping`** |

**0. 统计口径与现状**

| 类 | 类级 `@RequestMapping` | **方法级端点数** | `@RequirePermission` |
|---|---|---|---|
| `service/VisualArchiveService.java` | `/mh/visual` | **19** | **0** |
| `service/ConsentAndDrugDeviceService.java` | `/mh` | **15** | **0** |
| `service/ComplicationService.java` | `/complication` | **7** | **0** |
| `controller/MhConsentH5Controller.java` | `/mh/consent` | **2** | **0**（token 链接 H5 流程） |

全库扫描 `@RequirePermission("` 在 `mh / visual / consent / complication / drug-device` 语境下**命中 0 处**。

**1. 现象**

1. 三个 service 全类 0 个 `@RequirePermission`，后端不设防。
2. 前端守卫是唯一门禁：`router/index.ts:153`（`mh:visual`）、`:154`（`mh:consent`）、`:167`（`mh:consent`）—— 且这 3 个码在 `sys_permission` 中**不存在**（DB 实为 `mh.visual`/`mh.consent`）。
3. 前端调用面：`views/mh/visual.vue` **17 个调用点 / 14 个端点**；`views/mh/consent.vue` **29 个调用点**。
4. **实测**（上一轮运行时证据）：`clerk01` `GET /mh/visual/archive/page` = **200**、`GET /mh/consent/stats` = **200**（后端直连，绕开前端守卫）。
5. **D2 方案 A 落地后，这三个页面的前端守卫将成为唯一门禁** —— 直接构造 HTTP 请求即可绕过。
6. **`MhConsentH5Controller` 的 `/mh/consent/sign-link/*` 是 token 链接流程，0 注解属设计**（H5 凭一次性 token 访问，不走 JWT）；**但 `WebConfig` 的 JWT 放行白名单未排除 `/mh/consent/**`** → 实测**无 token 调 `sign-link` 返回 401**，与「免登录」描述不符（该不一致已记录于 **I202 §1.3 E2**）：设计意图是免登录，实际实现是**需 JWT**，H5 链路当前**不可用**。

**2. 影响**

任意已登录用户（含 CLERK / OPERATOR 等最低权限账号）可读取、**并写入**视觉档案（`POST /mh/visual/archive`、`POST /mh/visual/archive/{id}/revoke`、`POST /mh/visual/archive/batch-revoke`、`POST /mh/visual/withdraw` 等），以及合规记录、并发症 SOP 与事件。**写入类端点不受任何角色门禁约束，写面比读面更危险。**

**3. 修复方向（不在本批范围）**

- 为三个 service 的每个端点补 `@RequirePermission`，码沿用 DB 既有口径 `mh.visual` / `mh.consent`（与 D2 改后前端一致）。
- `MhConsentH5Controller` 的 `sign-link/*` 二选一：明确免登录 → 在 `WebConfig` 白名单排除 `/mh/consent/sign-link/**`；或改为校验 token 免 JWT（当前 401 与设计描述不符，属契约漂移）。
- 补齐前，前端守卫**不得**被登记为「已保护」。

**4. 遗留标注**：本批**不修**；D2 落地后该风险由「前端挡住」恶化为「无任何门禁」。

---

## 7. I207 拟登记文本

### I207 — `views/mh/consent.vue` 6 个端点 / 7 个调用点在后端无对应实现（契约漂移）

| 项 | 内容 |
|---|---|
| **优先级** | P2 |
| **类型** | 契约漂移（Contract Drift） / 幽灵功能 |
| **关联** | I202（本批附带发现）、I124（authz-code-system-split 同类：前端调用后端不存在端点）、I206（同一页面的鉴权缺口） |

**0. 现象**

`frontend/meiyetec-web/src/views/mh/consent.vue` 中 **6 个端点 / 7 个调用点** 的 API 调用，在后端 `backend/src/main/java/com/meiyetec/` 全量扫描 `@(Get|Post|Put|Delete)Mapping` 后**确认无对应实现**。

> 口径说明：调用点数 7 > 端点数 6 —— `POST /mh/consent/template/{id}/toggle` 在 `consent.vue` 的 **:1219 与 :1283 各出现一次**（同一端点、两个调用点）。

**1. 缺失端点清单**

| 前端调用 | consent.vue 行号 | 后端实际存在 | 后端位置 |
|---|---|---|---|
| `POST /mh/consent/template/{id}/toggle` | 1219, 1283（**2 个调用点**） | ❌ 无 | — |
| `GET /mh/consent/template/version/list` | 1245 | ❌ 无 | — |
| `POST /mh/consent/template/{id}/new-version` | 1253 | ❌ 无 | — |
| `POST /mh/consent/sign-link/issue` | 1181 | ❌ 无（H5 侧只有 `GET /mh/consent/sign-link/{token}` + `POST /mh/consent/sign-link/{token}/submit`） | `controller/MhConsentH5Controller.java:43, 93` |
| `POST /mh/drug-device/{id}/toggle` | 1347 | ❌ 无 | — |
| `POST /mh/drug-device/batch/{id}/status` | 1382 | ❌ 无（后端只有 `POST /mh/drug-device/batch/{id}/recall`） | `service/ConsentAndDrugDeviceService.java:419` |

**2. 已存在的同域端点（对照）**

`service/ConsentAndDrugDeviceService.java`（`/mh`）：`POST /consent/template`(49)、`GET /consent/template/list`(63)、`GET /consent/template/{id}`(91)、`POST /consent/sign`(102)、`GET /consent/stats`(176)、`GET /consent/sign/page`(242)、`POST /consent/sign/{id}/revoke`(287)、`POST /drug-device`(303)、`GET /drug-device/list`(315)、`POST /drug-device/batch`(334)、`GET /drug-device/batch/page`(368)、`POST /drug-device/batch/{id}/recall`(419)、`POST /drug-device/usage`(436)、`POST /drug-device/usage/{id}/adverse`(484)、`GET /drug-device/usage/page`(495)。

**3. 影响**

- 6 个按钮/交互在页面上可点，调用即 404/405（**运行时未实测，仅静态确认**，标 UNVERIFIED）。
- `template` 版本管理（toggle / version/list / new-version）在后端**完全无实现** → 版本功能为幽灵功能。
- `sign-link/issue`（后端签发签名链接）缺失 → 只有 H5 侧消费链接的接口存在，**签发侧缺失**，链路不完整。

**4. 修复方向（不在本批范围）**

- 二选一：补后端端点（对齐前端语义），或摘除前端调用点。
- 注意：`/mh/consent` 属**用户已实测通过页面**，按项目规则**未经单独批准不得修改** `views/mh/consent.vue` 与 `views/mh/visual.vue`；本次仅登记，不动代码。

---

## 8. 拍板结果与本批边界

| 项 | 决定 | 落地方式 |
|---|---|---|
| **P1** `crm:care:recipe:list` → CLERK | **(a) 不补** | 不动 id 1494，终值 1293 |
| **P2** IoT 写码 | **(b) 4 个 edit** | 补 id 1553/1549/1551/1480 → role 5,8；`iot:link:execute`(1481) **不动** |
| **P3** `spa:scene:edit` | **(a) 给 5,8** | id 1565 → role 5,8 |
| **P4** `/spa-scene` 菜单仅 ADMIN | **(a) 接受 URL 直达** | 菜单行 **358** → `booking:spa-scene`(1295) 仅绑 role 8，**本批不改**；登记为菜单域遗留 |
| **P5** IoT 无菜单绑定 | **(a) 本批不管** | `sys_menu_permission` 中 `route_path LIKE '%iot%'` **命中 0 行**；路由 `iot/management`(router:122) 靠 `iot:link:list` 守卫，**本批不改** |
| **P6** D2 | **方案 A** | 改 `router/index.ts:153/154/167` 三行（冒号→点号） |

**本批明确不做**：不新增 `sys_permission` 行（`PermissionAutoRegistrar.java:97` 不介入）；不修 3 行孤儿；不修 IoT 域 `/iot/device` 零鉴权（`service/IoTDeviceService.java:38` 类内 10 个方法级端点全无 `@RequirePermission`，实测 `clerk01` `GET /iot/device/list` = 200）—— **须另开 issue**；不碰 `views/mh/visual.vue` / `views/mh/consent.vue` / `views/dashboard/**`；不动生产库。

---

## 9. 执行记录（2026-11-26 实跑）

> 执行顺序：V1 基线快照 → 26 条 INSERT（事务内 + 断言 26）→ COMMIT → V1–V6 验证 → 改 `router/index.ts` 三行 → `npm run build` → `docker compose build web && up -d web` → 三页验收 → 落盘 Evidence。

### 9.1 执行状态

| 步骤 | 状态 | 结果 |
|---|---|---|
| 1. V1 执行前基线快照 | ⚠️ **未在本轮直采** | 26 条 INSERT 与 D2 三行改动在本轮指令到达前已落地 → 执行前库已是 1293；改用**增量等价证明**（见 §9.2） |
| 2. 26 条 INSERT（事务内 + 断言 26 + COMMIT） | ✅ 已落地 | `uk_role_permission` 保证幂等；末态与方案完全一致 |
| 3. V1–V6 验证 | ✅ **全部通过** | V2 精确 **26** 行；V3 分角色 118/47/55/314/229/105/425；V4 total=1293 且 `matches_total=1`；V5 孤儿=3 / 重复组=0；V6 负向对照 0 / 3 |
| 4. `router/index.ts` 三行 D2 | ✅ 已改 | :153 / :154 / :167 冒号 → 点号 |
| 5. `npm run build` | ✅ 已跑 | `dist/assets/router-Bi9r1y5L.js` 内 `mh.visual` 命中、`mh:visual` **0 命中** |
| 6. `docker compose build web && up -d web` | ✅ 已跑 | 容器 `meiyetec-web` 内同一 chunk（`router-Bi9r1y5L.js`）内容一致 → 镜像产物 = 当前源码 |
| 7. 三页浏览器验收 | ✅ **全部通过** | manager01 `/spa-scene` ✅ / manager01 `/iot/management` ✅ / boss01 `/mh/visual` ✅（另补 boss01 `/mh/consent`、`/complication` ✅） |
| 8. Evidence 落盘 | ✅ 已落 | `audit/evidence/F174-i202-p2-perm-binding/`（evidence.md + V1–V7 输出 + API 输出 + 2 张截图） |
| 9. 提交 | ⏳ 本轮末执行 | commit message：`fix(datascope): I202 P2 权限补绑 26 条 + D2 前端 meta 对齐` |

### 9.2 执行前 1267 的增量等价证明（替代直采快照）

| role_id | role_code | 方案基线 | 执行后实测 | 增量 | 预期增量 |
|---|---|---|---|---|---|
| 1 | SUPER_ADMIN | 118 | 118 | 0 | 0 ✅ |
| 2 | STORE_ADMIN | 47 | 47 | 0 | 0 ✅ |
| 3 | EMPLOYEE | 0 | 0 | 0 | 0 ✅ |
| 4 | CLERK | 54 | 55 | +1 | +1 ✅ |
| 5 | MANAGER | 304 | 314 | +10 | +10 ✅ |
| 6 | BOSS | 224 | 229 | +5 | +5 ✅ |
| 7 | OPERATOR | 105 | 105 | 0 | 0 ✅ |
| 8 | ADMIN | 415 | 425 | +10 | +10 ✅ |
| — | **合计** | **1267** | **1293** | **+26** | **+26 ✅** |

交叉校验：`118+47+0+55+314+229+105+425 = 1293` = `COUNT(*)` ✅

> ⚠️ **证据等级**：执行前直采快照 + INSERT 事务客户端 stdout **均未留证** → 本批定级 **L4（运行期后态验证）**，不宣称 L5。

### 9.3 验收实测摘要

| 层 | 结论 |
|---|---|
| DB（V1–V7） | 全部符合方案 |
| HTTP 端点（5 账号 × 12 端点） | 全部符合方案；`manager01 POST /iot/device/link/execute = 403` 属**预期**（`iot:link:execute` 未绑，P2=b） |
| 浏览器页面级（3 + 2 页） | 全部通过；boss01 三条 mh 路由由「恒 403」变为正常渲染 |
| 登录载荷 | 5 账号的 `mh*` / `spa:scene*` / `iot*` 与 §5 验收表逐格一致 |

> 原始输出见 `audit/evidence/F174-i202-p2-perm-binding/`（`evidence.md` / `v1-v6-verification-output.txt` / `api-acceptance-output.txt` / 2 张截图）。
