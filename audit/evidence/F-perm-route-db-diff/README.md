# F-perm-route-db-diff 证据 — 前端路由权限码 × `sys_permission` 全量对账

> 本文件为 QIAOPM-Q-008 架构决策的证据附件。
> 审计时间：2026-10-24，commit 270e8e3。
> 只读审计，未执行应用，结论为静态事实，运行行为需人工确认。

---

## 0. 审计范围与证据基线

| 项 | 值 |
|---|---|
| Commit | `270e8e3d86414ea505913c893139f975d361a6e8`（2026-10-24） |
| DB 快照 | `backup/meiyetec_20261023_220648.sql` |
| 前端数据源 | `frontend/meiyetec-web/src/router/index.ts` |
| 后端交叉源 | `backend/src/main/java/**`（`@RequirePermission` 扫描 330 个去重码） |
| DB 解析结果 | `sys_permission` 454 行 / 454 个唯一码；`sys_role_permission` 360 行；`sys_role` 8 个角色 |
| 路由解析结果 | 152 个路由对象，其中 147 个带 `meta.permission`（5 个无权限码：`/login`、`/mh-consent-h5/:token`、`/403`、`/dashboard`、`/setting`）；去重后 97 个权限码 |

**审计方法**：以 Node 一次性内存脚本（未落盘、无脚本文件）解析 SQL dump 的 `INSERT INTO sys_permission` / `sys_role_permission` / `sys_role` 三个语句块（括号配平解析，454 + 360 + 8 行），并用正则扫描 `router/index.ts` 提取全部 `path` / `name` / `meta.permission`；两侧按 `permission_code` 字面精确匹配后统计角色绑定。**未执行应用、未连接运行库、未做人工验收。**

---

## 1. 路由级审计表（147 条）

判定依据：

- **BLOCKER** = 权限码在 `sys_permission` 中不存在 → 任何非超管用户访问必定被前端守卫拦到 `/403`
- **PARTIAL** = 权限码在 DB 中存在但零角色绑定 → 当前同样只有超管能进（可通过角色配置补授）
- **OK** = 已绑定角色

| # | 路由 path | 标题 | meta.permission | DB 存在 | 绑定角色数 | 绑定角色 | 风险 |
|---|---|---|---|---|---|---|---|
| 1 | /member | 会员管理 | member:list | YES | 5 | ADMIN,BOSS,CLERK,MANAGER,SUPER_ADMIN | OK |
| 2 | /wallet/recharge | 储值管理 | wallet:list | YES | 4 | ADMIN,BOSS,MANAGER,SUPER_ADMIN | OK |
| 3 | /wallet/transactions | 储值流水 | wallet:list | YES | 4 | ADMIN,BOSS,MANAGER,SUPER_ADMIN | OK |
| 4 | /package | 套餐次卡 | package:list | YES | 0 | - | PARTIAL |
| 5 | /package/list | 套餐管理 | package:list | YES | 0 | - | PARTIAL |
| 6 | /package/member-orders | 会员套餐查询 | package:list | YES | 0 | - | PARTIAL |
| 7 | /package/share | 多人共享套餐 | package:list | YES | 0 | - | PARTIAL |
| 8 | /booking/calendar | 预约日历 | booking:list | YES | 0 | - | PARTIAL |
| 9 | /booking/list | 预约列表 | booking:list | YES | 0 | - | PARTIAL |
| 10 | /booking/schedule | 排班管理 | booking:schedule:list | YES | 0 | - | PARTIAL |
| 11 | /pos/checkout | 收银台 | pos:list | **NO** | 0 | - | **BLOCKER** |
| 12 | /pos/orders | 订单列表 | pos:order:list | YES | 0 | - | PARTIAL |
| 13 | /pos/stats | 收银统计 | pos:order:stat | YES | 0 | - | PARTIAL |
| 14 | /pos/refund | 退款单 | pos:refund | YES | 0 | - | PARTIAL |
| 15 | /pos/reconciliation | 支付对账 | pos:reconciliation:list | YES | 0 | - | PARTIAL |
| 16 | /stock/list | 库存列表 | stock:list | **NO** | 0 | - | **BLOCKER** |
| 17 | /stock/product | 商品耗材 | stock:list | **NO** | 0 | - | **BLOCKER** |
| 18 | /stock/record | 出入库记录 | stock:list | **NO** | 0 | - | **BLOCKER** |
| 19 | /stock/consumption | 消耗记录 | stock:list | **NO** | 0 | - | **BLOCKER** |
| 20 | /stock/check | 盘点管理 | stock:check | YES | 0 | - | PARTIAL |
| 21 | /stock/alert | 库存预警 | stock:alert | YES | 0 | - | PARTIAL |
| 22 | /stock/stats | 库存统计 | stock:stat | YES | 0 | - | PARTIAL |
| 23 | /stock/cross-store | 跨店库存调度 | wms:transfer | **NO** | 0 | - | **BLOCKER** |
| 24 | /commission/rules | 提成规则 | commission:rule:list | YES | 0 | - | PARTIAL |
| 25 | /commission/records | 提成记录 | commission:record:list | YES | 0 | - | PARTIAL |
| 26 | /commission/salary | 工资报表 | commission:salary:list | YES | 0 | - | PARTIAL |
| 27 | /commission/settlement | 结算管理 | commission:settlement:list | YES | 0 | - | PARTIAL |
| 28 | /market/coupon | 优惠券 | market:coupon:list | YES | 0 | - | PARTIAL |
| 29 | /market/points | 积分商城 | market:point:list | YES | 0 | - | PARTIAL |
| 30 | /market/activity | 营销活动 | market:activity:list | YES | 0 | - | PARTIAL |
| 31 | /report/dashboard | 数据看板 | report:dashboard | YES | 0 | - | PARTIAL |
| 32 | /report/biz | 营业报表 | report:biz:list | YES | 0 | - | PARTIAL |
| 33 | /report/kpi | 高级KPI看板 | report:dashboard | YES | 0 | - | PARTIAL |
| 34 | /report/export | 导出管理 | report:export:list | YES | 0 | - | PARTIAL |
| 35 | /stock/trace/:id | 批次追溯 | stock:list | **NO** | 0 | - | **BLOCKER** |
| 36 | /sys/role | 角色管理 | sys:role:list | YES | 0 | - | PARTIAL |
| 37 | /sys/setting | 系统设置 | setting | YES | 3 | ADMIN,BOSS,SUPER_ADMIN | OK |
| 38 | /sys/tenant | 租户管理 | sys:tenant:list | YES | 0 | - | PARTIAL |
| 39 | /sys/log | 操作日志 | sys:log:list | YES | 0 | - | PARTIAL |
| 40 | /sys/login-log | 登录日志 | sys:login-log:list | YES | 0 | - | PARTIAL |
| 41 | /sys/payment | 支付配置 | sys:config:edit | YES | 0 | - | PARTIAL |
| 42 | /sys/qualification | 商户资质材料 | sys:config:edit | YES | 0 | - | PARTIAL |
| 43 | /dev/gray-release | 灰度发布控制台 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 44 | /dev/forecast | 客流预测与智能排班 | booking:list | YES | 0 | - | PARTIAL |
| 45 | /dev/ltv-training | LTV流失预测 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 46 | /dev/care-plan | 护肤方案共享套餐 | biz:service-item | **NO** | 0 | - | **BLOCKER** |
| 47 | /dev/pricelist | 电子价目表卡项套餐 | biz:service-item | **NO** | 0 | - | **BLOCKER** |
| 48 | /dev/datahub | 数据中台 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 49 | /dev/care-memberday | 配方护理会员日 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 50 | /dev/p1-suite | 套件合集 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 51 | /dev/p2-suite | 套件合集 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 52 | /dev/p3-suite | 套件合集 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 53 | /dev/gap-suite | 套件合集 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 54 | /biz/service-item | 服务项目 | biz:service:item | **NO** | 0 | - | **BLOCKER** |
| 55 | /biz/treatment-plan | 疗程方案 | biz:treatment:plan | **NO** | 0 | - | **BLOCKER** |
| 56 | /biz/treatment-report | 效果报告 | biz:treatment:plan | **NO** | 0 | - | **BLOCKER** |
| 57 | /booking/resource | 预约资源 | biz:booking:resource | **NO** | 0 | - | **BLOCKER** |
| 58 | /booking/waitlist | 候补队列 | biz:booking:waitlist | **NO** | 0 | - | **BLOCKER** |
| 59 | /booking/no-show | 爽约管理 | biz:booking:no-show | **NO** | 0 | - | **BLOCKER** |
| 60 | /commission/batch | 提成结算批次 | commission:batch | YES | 0 | - | PARTIAL |
| 61 | /commission/reversal | 提成冲正 | commission:reversal | YES | 0 | - | PARTIAL |
| 62 | /mkt/promotion | 营销活动 | mkt:promotion | YES | 0 | - | PARTIAL |
| 63 | /mkt/coupon | 优惠券 | mkt:coupon | YES | 0 | - | PARTIAL |
| 64 | /mkt/points | 积分规则 | mkt:points | YES | 0 | - | PARTIAL |
| 65 | /mkt/effect | 营销效果 | mkt:effect | **NO** | 0 | - | **BLOCKER** |
| 66 | /report/task | 导出任务 | report:task | YES | 0 | - | PARTIAL |
| 67 | /report/caliber | 指标口径 | report:caliber | YES | 0 | - | PARTIAL |
| 68 | /org/hq | 总部看板 | org:hq | **NO** | 0 | - | **BLOCKER** |
| 69 | /org/brand | 品牌管理 | org:brand | **NO** | 0 | - | **BLOCKER** |
| 70 | /org/store | 门店管理 | org:store | **NO** | 0 | - | **BLOCKER** |
| 71 | /attendance/index | 考勤打卡 | employee:attendance | **NO** | 0 | - | **BLOCKER** |
| 72 | /attendance/records | 考勤记录 | employee:attendance:list | **NO** | 0 | - | **BLOCKER** |
| 73 | /attendance/leave | 请请管理 | employee:leave | **NO** | 0 | - | **BLOCKER** |
| 74 | /attendance/statistics | 考勤统计 | employee:attendance:stat | **NO** | 0 | - | **BLOCKER** |
| 75 | /attendance/config | 考勤规则配置 | employee:attendance:config | **NO** | 0 | - | **BLOCKER** |
| 76 | /technician/level | 技师等级 | employee:level | YES | 3 | ADMIN,STORE_ADMIN,SUPER_ADMIN | OK |
| 77 | /technician/benefit | 等级权益配置 | employee:level:benefit | YES | 3 | ADMIN,STORE_ADMIN,SUPER_ADMIN | OK |
| 78 | /technician/evaluation | 等级评定 | employee:level:evaluation | **NO** | 0 | - | **BLOCKER** |
| 79 | /commission/rule-list | 提成规则列表 | commission:rule | **NO** | 0 | - | **BLOCKER** |
| 80 | /commission/rules | 提成规则配置 | commission:rule:config | **NO** | 0 | - | **BLOCKER** |
| 81 | /commission/simulation | 提成模拟计算 | commission:simulation | **NO** | 0 | - | **BLOCKER** |
| 82 | /material/binding | 耗材绑定配置 | stock:material:binding | **NO** | 0 | - | **BLOCKER** |
| 83 | /material/statistics | 耗材消耗统计 | stock:material:stat | **NO** | 0 | - | **BLOCKER** |
| 84 | /material/expiry | 效期追踪管理 | stock:expiry | **NO** | 0 | - | **BLOCKER** |
| 85 | /material/service-material | 项目耗材扣减 | stock:service-material:rule | **NO** | 0 | - | **BLOCKER** |
| 86 | /resource/schedule | 双资源排程 | resource:schedule | **NO** | 0 | - | **BLOCKER** |
| 87 | /payment/overseas | 海外支付 | payment:overseas:gateway | **NO** | 0 | - | **BLOCKER** |
| 88 | /iot/management | IoT设备联动 | iot:device:manage | **NO** | 0 | - | **BLOCKER** |
| 89 | /payment/dcep-advanced | 数字人民币2.0 | dcep:hardware-wallet | **NO** | 0 | - | **BLOCKER** |
| 90 | /crm/cross-rule | 跨店规则 | crm:cross-rule | **NO** | 0 | - | **BLOCKER** |
| 91 | /crm/cross-record | 跨店核销 | crm:cross-record | **NO** | 0 | - | **BLOCKER** |
| 92 | /crm/care/overview | 客户运营看板 | crm:follow-up | **NO** | 0 | - | **BLOCKER** |
| 93 | /crm/care/follow-task | 回访任务 | crm:follow-up | **NO** | 0 | - | **BLOCKER** |
| 94 | /crm/care/follow-rule | 自动任务规则 | crm:follow-plan | **NO** | 0 | - | **BLOCKER** |
| 95 | /crm/care/follow-plan | 回访计划 | crm:follow-plan | **NO** | 0 | - | **BLOCKER** |
| 96 | /crm/care/suggestion | 护理建议生成 | crm:care:suggestion | **NO** | 0 | - | **BLOCKER** |
| 97 | /crm/detection | 肤质档案 | crm:detection | **NO** | 0 | - | **BLOCKER** |
| 98 | /crm/style-package | 款式偏好 | crm:style:library | **NO** | 0 | - | **BLOCKER** |
| 99 | /crm/care/complaint | 投诉工单 | crm:complaint | **NO** | 0 | - | **BLOCKER** |
| 100 | /crm/care/review | 客户评价 | crm:review | **NO** | 0 | - | **BLOCKER** |
| 101 | /crm/care/review-tpl | 评价模板 | crm:review-tpl | **NO** | 0 | - | **BLOCKER** |
| 102 | /mkt/channel | 渠道配置 | mkt:channel | **NO** | 0 | - | **BLOCKER** |
| 103 | /mkt/template | 消息模板 | mkt:template | **NO** | 0 | - | **BLOCKER** |
| 104 | /mkt/touch-task | 触达任务 | mkt:touch-task | **NO** | 0 | - | **BLOCKER** |
| 105 | /mkt/birthday | 生日提醒 | mkt:birthday | **NO** | 0 | - | **BLOCKER** |
| 106 | /mkt/journey | 营销旅程 | mkt:journey | **NO** | 0 | - | **BLOCKER** |
| 107 | /m09/invite | 裂变邀请 | mkt:invite:list | YES | 0 | - | PARTIAL |
| 108 | /mkt/conversion | 触达效果 | mkt:conversion | **NO** | 0 | - | **BLOCKER** |
| 109 | /mkt/pricing | 动态定价 | mkt:pricing | **NO** | 0 | - | **BLOCKER** |
| 110 | /m12/dispatch | 业务策略下发 | mkt:dispatch | **NO** | 0 | - | **BLOCKER** |
| 111 | /intel/rfm | RFM 分群 | crm:rfm | **NO** | 0 | - | **BLOCKER** |
| 112 | /intel/ltv | LTV 预测 | crm:tag | **NO** | 0 | - | **BLOCKER** |
| 113 | /intel/price-sensitivity | 价格敏感度 | crm:tag | **NO** | 0 | - | **BLOCKER** |
| 114 | /mh/visual | 视觉档案 | mh:visual | **NO** | 0 | - | **BLOCKER** |
| 115 | /mh/consent | 医美合规 | mh:consent | **NO** | 0 | - | **BLOCKER** |
| 116 | /pay/dcep | 数字人民币 | pay:dcep | **NO** | 0 | - | **BLOCKER** |
| 117 | /care | 配方护理建议 | biz:service-item | **NO** | 0 | - | **BLOCKER** |
| 118 | /member-day | 会员日自动化 | mkt:activity | **NO** | 0 | - | **BLOCKER** |
| 119 | /style-gallery | 款式图库 | mkt:activity | **NO** | 0 | - | **BLOCKER** |
| 120 | /style-gallery/quick-order | 快速下单 | mkt:activity | **NO** | 0 | - | **BLOCKER** |
| 121 | /lbs | 上门服务调度 | booking:list | YES | 0 | - | PARTIAL |
| 122 | /gift-card | 礼品卡 | wallet:list | YES | 4 | ADMIN,BOSS,MANAGER,SUPER_ADMIN | OK |
| 123 | /subscription | 订阅会员制 | mkt:activity | **NO** | 0 | - | **BLOCKER** |
| 124 | /ai-vision | AI 视觉识别 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 125 | /ltv-churn | LTV/Churn 分析 | crm:tag | **NO** | 0 | - | **BLOCKER** |
| 126 | /iot | IoT 设备管理 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 127 | /kiosk | 自助 Kiosk | booking:list | YES | 0 | - | PARTIAL |
| 128 | /complication | 并发症 SOP | mh:consent | **NO** | 0 | - | **BLOCKER** |
| 129 | /cohort | Cohort 队列 | crm:rfm | **NO** | 0 | - | **BLOCKER** |
| 130 | /gdpr | GDPR 合规 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 131 | /hipaa | HIPAA 加固 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 132 | /pos-device | 海外 POS | pay:order | **NO** | 0 | - | **BLOCKER** |
| 133 | /plugin | 插件市场 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 134 | /fiscal | 财税直连 | pay:order | **NO** | 0 | - | **BLOCKER** |
| 135 | /fiscal/config | 财税渠道配置 | pay:order | **NO** | 0 | - | **BLOCKER** |
| 136 | /fiscal/list | 发票列表 | pay:order | **NO** | 0 | - | **BLOCKER** |
| 137 | /crm-integration | CRM 集成 | crm:tag | **NO** | 0 | - | **BLOCKER** |
| 138 | /ecommerce | 电商集成 | mkt:activity | **NO** | 0 | - | **BLOCKER** |
| 139 | /intl-payment | 国际支付 | pay:order | **NO** | 0 | - | **BLOCKER** |
| 140 | /intl-booking | 国际预约 | booking:list | YES | 0 | - | PARTIAL |
| 141 | /ai-scheduler | AI 智能排班 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 142 | /risk-control | 资金风控 | dev:access | **NO** | 0 | - | **BLOCKER** |
| 143 | /google-maps | Google Maps | dev:access | **NO** | 0 | - | **BLOCKER** |
| 144 | /lifecycle | 会员生命周期看板 | mkt:activity | **NO** | 0 | - | **BLOCKER** |
| 145 | /spa-scene | SPA场景模式 | biz:room | **NO** | 0 | - | **BLOCKER** |
| 146 | /blockchain/trace | 区块链溯源 | blockchain:trace | YES | 2 | ADMIN,SUPER_ADMIN | OK |
| 147 | /blockchain/monitor | 区块链监控大屏 | blockchain:monitor | YES | 2 | ADMIN,SUPER_ADMIN | OK |

---

## 2. 汇总

### 2.1 权限码维度（97 个去重码）

| 分类 | 数量 | 说明 |
|---|---|---|
| **DB 中不存在** | **57** | 路由引用了但 `sys_permission` 无此行 |
| **存在但零角色绑定** | **33** | `sys_permission` 有行，`sys_role_permission` 无引用 |
| **存在且有角色绑定** | **7** | member:list / wallet:list / setting / employee:level / employee:level:benefit / blockchain:trace / blockchain:monitor |

路由维度：**BLOCKER 95 条 / PARTIAL 43 条 / OK 9 条**（合计 147）。

### 2.2 不存在于 DB 的 57 个权限码

```
biz:booking:no-show           biz:booking:resource       biz:booking:waitlist
biz:room                      biz:service-item           biz:service:item
biz:treatment:plan            commission:rule             commission:rule:config
commission:simulation         crm:care:suggestion        crm:complaint
crm:cross-record              crm:cross-rule             crm:detection
crm:follow-plan               crm:follow-up              crm:review
crm:review-tpl                crm:rfm                    crm:style:library
crm:tag                       dcep:hardware-wallet       dev:access
employee:attendance           employee:attendance:config employee:attendance:list
employee:attendance:stat      employee:leave             employee:level:evaluation
iot:device:manage             mh:consent                 mh:visual
mkt:activity                  mkt:birthday               mkt:channel
mkt:conversion                mkt:dispatch               mkt:effect
mkt:journey                   mkt:pricing                mkt:template
mkt:touch-task                org:brand                  org:hq
org:store                     pay:dcep                   pay:order
payment:overseas:gateway      pos:list                   resource:schedule
stock:expiry                  stock:list                 stock:material:binding
stock:material:stat           stock:service-material:rule wms:transfer
```

这 57 个码中，**0 个**出现在后端 `@RequirePermission` 注解（330 个去重码扫描结果）。因此 `PermissionAutoRegistrar`（`backend/src/main/java/com/meiyetec/security/PermissionAutoRegistrar.java:147`）在应用启动时也**不会**把它们补写进 `sys_permission` —— 这批码在运行期同样是「孤儿码」。

其中 **21 个**在 DB 中存在对应点号 menu 码且已被角色绑定（命名约定漂移）：`biz:service-item`→`biz.service-item`、`crm:complaint`→`crm.complaint`、`crm:cross-record`→`crm.cross-record`、`crm:cross-rule`→`crm.cross-rule`、`crm:follow-plan`→`crm.follow-plan`、`crm:review`→`crm.review`、`crm:review-tpl`→`crm.review-tpl`、`crm:rfm`→`crm.rfm`、`mh:consent`→`mh.consent`、`mh:visual`→`mh.visual`、`mkt:channel`→`mkt.channel`、`mkt:conversion`→`mkt.conversion`、`mkt:effect`→`mkt.effect`、`mkt:journey`→`mkt.journey`、`mkt:pricing`→`mkt.pricing`、`mkt:template`→`mkt.template`、`mkt:touch-task`→`mkt.touch-task`、`org:brand`→`org.brand`、`org:hq`→`org.hq`、`org:store`→`org.store`、`stock:list`→`stock.list`。其余 **36 个**在 DB 中无任何点号 / 冒号对应项。

### 2.3 存在但零角色绑定的 33 个权限码

```
booking:list            booking:schedule:list   commission:batch
commission:record:list  commission:reversal     commission:rule:list
commission:salary:list  commission:settlement:list market:activity:list
market:coupon:list      market:point:list       mkt:coupon
mkt:invite:list         mkt:points              mkt:promotion
package:list            pos:order:list          pos:order:stat
pos:reconciliation:list pos:refund              report:biz:list
report:caliber          report:dashboard        report:export:list
report:task             stock:alert             stock:check
stock:stat              sys:config:edit         sys:log:list
sys:login-log:list      sys:role:list           sys:tenant:list
```

DB 全表 454 个码中有 **332 个零绑定**（占 73%）；按类型分布：`button` 281 个零绑定 / 11 个已绑定，`API` 51 个全部零绑定，`menu` 111 个全部已绑定。**结论：除菜单码外，DB 中几乎所有按钮级权限码都没有任何角色持有。**

### 2.4 存在且有角色绑定的 7 个权限码

| 权限码 | 绑定角色数 | 角色 |
|---|---|---|
| member:list | 5 | ADMIN, BOSS, CLERK, MANAGER, SUPER_ADMIN |
| wallet:list | 4 | ADMIN, BOSS, MANAGER, SUPER_ADMIN |
| setting | 3 | ADMIN, BOSS, SUPER_ADMIN |
| employee:level | 3 | ADMIN, STORE_ADMIN, SUPER_ADMIN |
| employee:level:benefit | 3 | ADMIN, STORE_ADMIN, SUPER_ADMIN |
| blockchain:trace | 2 | ADMIN, SUPER_ADMIN |
| blockchain:monitor | 2 | ADMIN, SUPER_ADMIN |

---

## 3. 按菜单分组的影响面

### 3.1 按前端路由首段分组（受影响路由数降序）

| 菜单组 | 带权限路由 | 不存在 | 零绑定 | 正常 |
|---|---|---|---|---|
| crm | 12 | **12** | 0 | 0 |
| dev | 11 | **10** | 1 | 0 |
| mkt | 11 | **8** | 3 | 0 |
| stock | 9 | 6 | 3 | 0 |
| commission | 9 | 3 | 6 | 0 |
| sys | 7 | 0 | 6 | 1 |
| booking | 6 | 3 | 3 | 0 |
| report | 6 | 0 | 6 | 0 |
| pos | 5 | 1 | 4 | 0 |
| attendance | 5 | **5** | 0 | 0 |
| package | 4 | 0 | 4 | 0 |
| material | 4 | **4** | 0 | 0 |
| market | 3 | 0 | 3 | 0 |
| biz | 3 | 3 | 0 | 0 |
| org | 3 | 3 | 0 | 0 |
| intel | 3 | 3 | 0 | 0 |
| fiscal | 3 | 1 | 0 | 0 |
| payment | 2 | 2 | 0 | 0 |
| iot | 2 | 2 | 0 | 0 |
| mh | 2 | 2 | 0 | 0 |
| style-gallery | 2 | 2 | 0 | 0 |
| technician | 3 | 1 | 0 | 2 |
| 其余 41 个单路由组 | 41 | 41 | 0 | 0 |

### 3.2 按 DB 菜单树（`sys_permission` type=menu, parent_id=0）分组

| DB 菜单组 | 相关路由 | 不存在 | 零绑定 | 正常 |
|---|---|---|---|---|
| crm（客户运营） | 9 | **9** | 0 | 0 |
| marketing（营销工具） | 7 | **7** | 0 | 0 |
| inventory（库存管理） | 7 | 5 | 2 | 0 |
| booking（预约管理） | 6 | 0 | 6 | 0 |
| report（数据报表） | 4 | 0 | 4 | 0 |
| package（套餐次卡） | 4 | 0 | 4 | 0 |
| biz（基础资料） | 3 | 3 | 0 | 0 |
| mh（医美合规） | 3 | 3 | 0 | 0 |
| org（组织架构） | 3 | 3 | 0 | 0 |
| commission（员工提成） | 2 | 0 | 2 | 0 |
| pos（收银结算） | 1 | 0 | 1 | 0 |
| blockchain | 2 | 0 | 0 | **2** |

**受影响最严重**：

1. **crm / marketing / mh / org** —— 菜单组下**每一条**路由的权限码在 DB 中都不存在，且该组在 DB 侧的 menu 码使用点号命名（`crm.follow-plan` / `mkt.effect` / `mh.visual` / `org.hq`），前端路由使用冒号命名（`crm:follow-plan` / `mkt:effect` / `mh:visual` / `org:hq`）→ **命名约定漂移**是主要成因。这 21 个码在 DB 中存在对应点号 menu 码，且**已被角色绑定**（SUPER_ADMIN / ADMIN / BOSS 等 2–3 个角色），但因码字面不同而完全不被前端识别。
2. **dev（研发工具，10 条）/ attendance（考勤，5 条）/ material（耗材，4 条）/ crm（12 条）** —— 点号变体在 DB 中也不存在，属于纯新增缺口。
3. **booking / report / package / commission / pos / market** —— DB 有码但**零角色绑定**，属授权缺口而非命名缺口；其中 `booking.list`、`report.dashboard`、`package.list`、`commission.salary`、`market.activity` 等点号 menu 码在 DB 中均已绑定 3–5 个角色，唯独冒号变体（`booking:list`、`report:dashboard`、`package:list`）无人持有。

### 3.3 无任何 DB menu 对应项的「孤儿」路由

**93 条**带权限路由在 `sys_permission` 的 menu 树中找不到对应节点（无论点号还是冒号形式）。分布：dev 8、commission 7、sys 6、attendance 5、crm 5、booking 4、pos 4、mkt 4、material 4、market 3、biz 3、fiscal 3，其余 39 个组各 1–2 条。

---

## 4. 其他事实发现 F1~F9（仅陈述，未提修复建议）

| # | 事实 | 证据位置 |
|---|---|---|
| F1 | 路由守卫在 `hasPermission(code)` 失败即跳 `/403`；`state.permissions` 来自 `GET /auth/me/permissions`。SUPER_ADMIN / PLATFORM_ADMIN 由后端注入通配 `*`（前端 `includes('*')` 恒真） | `frontend/meiyetec-web/src/router/index.ts` 守卫段；`frontend/meiyetec-web/src/stores/permission.ts:46-50`；`backend/src/main/java/com/meiyetec/security/RbacContextLoader.java:78-88`；`backend/src/main/java/com/meiyetec/service/AuthService.java:63-71` |
| F2 | 按角色推算的路由可达率：SUPER_ADMIN 147/147；ADMIN 9/147（6.1%）；BOSS 5/147；MANAGER 4/147；STORE_ADMIN 2/147；CLERK 1/147；OPERATOR 0/147；EMPLOYEE 0/147 | 本次 DB 绑定关系 + 路由码交叉计算 |
| F3 | `sys_role_permission` 存在 3 条**悬空引用**：`permission_id = 9` 在 `sys_permission` 中不存在（行 id 9 / 45 / 62，分别属 role 1 / 6 / 8） | SQL dump `sys_role_permission` INSERT 段 |
| F4 | `sys_permission` 中有 122 个码被绑定、332 个未被绑定；未绑定的 332 个中含全部 51 个 `API` 类型码 | 本次统计 |
| F5 | `sys_permission` 存在**双套命名体系**：menu 类型用点号（`booking.list`、`mkt.effect`、`mh.visual`），button / API 类型用冒号（`booking:list`、`pos:order:list`）。前端路由 97 个码中 21 个是「点号 menu 码的冒号变体」 | SQL dump `sys_permission` + 前端路由交叉 |
| F6 | 前端路由 `commission/rules` 被**重复定义两次**（同名 `CommissionRules`），两次 `meta.permission` 分别为 `commission:rule:list`（DB 存在、零绑定）与 `commission:rule:config`（DB 不存在） | `frontend/meiyetec-web/src/router/index.ts` 两条 `path: 'commission/rules'` |
| F7 | 5 条路由无 `meta.permission`：`/login`、`/mh-consent-h5/:token`、`/403`、`/dashboard`、`/setting`（`/setting` 与 `/sys/setting` 指向不同组件，前者无权限码） | 同上 |
| F8 | 后端 `@RequirePermission` 去重 330 码，与前端 97 码的交集仅覆盖已在 DB 中的部分；57 个缺失码中 **0 个**被后端声明 → `PermissionAutoRegistrar` 运行期不会自动补写 | `backend/src/main/java/**` 注解扫描 |
| F9 | `sys_menu_permission` 有 110 行；`sys_menu` 表 0 行 —— 前端菜单树由 `sys_permission.parent_id` 推导 | SQL dump |

---

## 5. 状态标注

本轮为**静态只读审计**，无运行证据（未执行应用、未连接运行库、未做人工验收），结论全部基于代码与 DB 快照解析，按验收规则第 3 条标注为 **UNVERIFIED**（静态事实层面可复核；运行时行为需人工确认）。

本轮未修改任何业务代码、任何权限码、任何路由 `permission`，未修改 `audit/issues/issue-register.md` 与 `PROJECT_ACCEPTANCE_EVIDENCE.md`，未创建任何可执行脚本。

---

## 6. 建议落盘（历史记录，本轮已按授权执行）

本证据文件（`audit/evidence/F-perm-route-db-diff/README.md`）与 `docs/governance/QIAOPM_PROJECT_TECHNICAL_BASELINE.md` 附录 D.3 Q-008 行「临时处理」列、0.2 版本记录 v0.6 行系依据用户 2026-10-24 明确授权写入。

后续如需登记问题单，需另行逐条授权后再写入 `audit/issues/issue-register.md`。

---

# 附录 A：数据快照

## A.1 DB 不存在的 57 个权限码（含对应路由）

| 权限码 | 对应路由 | DB 中点号变体 | 点号变体绑定角色数 |
|---|---|---|---|
| biz:booking:no-show | /booking/no-show | 无 | - |
| biz:booking:resource | /booking/resource | 无 | - |
| biz:booking:waitlist | /booking/waitlist | 无 | - |
| biz:room | /spa-scene | 无 | - |
| biz:service-item | /dev/care-plan, /dev/pricelist, /care | biz.service-item | 3 |
| biz:service:item | /biz/service-item | 无 | - |
| biz:treatment:plan | /biz/treatment-plan, /biz/treatment-report | 无 | - |
| blockchain:monitor | /blockchain/monitor | blockchain.monitor | 2 |
| blockchain:trace | /blockchain/trace | blockchain.trace | 2 |
| commission:rule | /commission/rule-list | 无 | - |
| commission:rule:config | /commission/rules | 无 | - |
| commission:simulation | /commission/simulation | 无 | - |
| crm:care:suggestion | /crm/care/suggestion | 无 | - |
| crm:complaint | /crm/care/complaint | crm.complaint | 3 |
| crm:cross-record | /crm/cross-record | crm.cross-record | 2 |
| crm:cross-rule | /crm/cross-rule | crm.cross-rule | 3 |
| crm:detection | /crm/detection | 无 | - |
| crm:follow-plan | /crm/care/follow-rule, /crm/care/follow-plan | crm.follow-plan | 2 |
| crm:follow-up | /crm/care/overview, /crm/care/follow-task | 无 | - |
| crm:review | /crm/care/review | crm.review | 3 |
| crm:review-tpl | /crm/care/review-tpl | crm.review-tpl | 2 |
| crm:rfm | /intel/rfm, /cohort | crm.rfm | 3 |
| crm:style:library | /crm/style-package | 无 | - |
| crm:tag | /intel/ltv, /intel/price-sensitivity, /ltv-churn, /crm-integration | 无 | - |
| dcep:hardware-wallet | /payment/dcep-advanced | 无 | - |
| dev:access | /dev/gray-release, /dev/ltv-training, /dev/datahub, /dev/care-memberday, /dev/p1-suite, /dev/p2-suite, /dev/p3-suite, /dev/gap-suite, /ai-vision, /iot, /gdpr, /hipaa, /plugin, /ai-scheduler, /risk-control, /google-maps | 无 | - |
| employee:attendance | /attendance/index | 无 | - |
| employee:attendance:config | /attendance/config | 无 | - |
| employee:attendance:list | /attendance/records | 无 | - |
| employee:attendance:stat | /attendance/statistics | 无 | - |
| employee:leave | /attendance/leave | 无 | - |
| employee:level:evaluation | /technician/evaluation | 无 | - |
| iot:device:manage | /iot/management | 无 | - |
| mh:consent | /mh/consent, /complication | mh.consent | 3 |
| mh:visual | /mh/visual | mh.visual | 3 |
| mkt:activity | /member-day, /style-gallery, /style-gallery/quick-order, /subscription, /ecommerce, /lifecycle | 无 | - |
| mkt:birthday | /mkt/birthday | 无 | - |
| mkt:channel | /mkt/channel | mkt.channel | 3 |
| mkt:conversion | /mkt/conversion | mkt.conversion | 3 |
| mkt:dispatch | /m12/dispatch | 无 | - |
| mkt:effect | /mkt/effect | mkt.effect | 3 |
| mkt:journey | /mkt/journey | mkt.journey | 3 |
| mkt:pricing | /mkt/pricing | mkt.pricing | 3 |
| mkt:template | /mkt/template | mkt.template | 3 |
| mkt:touch-task | /mkt/touch-task | mkt.touch-task | 3 |
| org:brand | /org/brand | org.brand | 3 |
| org:hq | /org/hq | org.hq | 3 |
| org:store | /org/store | org.store | 3 |
| pay:dcep | /pay/dcep | 无 | - |
| pay:order | /pos-device, /fiscal, /fiscal/config, /fiscal/list, /intl-payment | 无 | - |
| payment:overseas:gateway | /payment/overseas | 无 | - |
| pos:list | /pos/checkout | 无 | - |
| resource:schedule | /resource/schedule | 无 | - |
| stock:expiry | /material/expiry | 无 | - |
| stock:list | /stock/list, /stock/product, /stock/record, /stock/consumption, /stock/trace/:id | stock.list | 4 |
| stock:material:binding | /material/binding | 无 | - |
| stock:material:stat | /material/statistics | 无 | - |
| stock:service-material:rule | /material/service-material | 无 | - |
| wms:transfer | /stock/cross-store | 无 | - |

## A.2 零角色绑定的 33 个权限码（含对应路由）

| 权限码 | 对应路由 | DB 点号变体绑定角色数 |
|---|---|---|
| booking:list | /booking/calendar, /booking/list, /dev/forecast, /lbs, /kiosk, /intl-booking | booking.list = 4 |
| booking:schedule:list | /booking/schedule | booking.schedule = 4 |
| commission:batch | /commission/batch | commission.batch = 2 |
| commission:record:list | /commission/records | 无 |
| commission:reversal | /commission/reversal | commission.reversal = 2 |
| commission:rule:list | /commission/rules | 无 |
| commission:salary:list | /commission/salary | commission.salary = 3 |
| commission:settlement:list | /commission/settlement | commission.settlement = 3 |
| market:activity:list | /market/activity | market.activity = 5 |
| market:coupon:list | /market/coupon | market.coupon = 5 |
| market:point:list | /market/points | 无 |
| mkt:coupon | /mkt/coupon | 无 |
| mkt:invite:list | /m09/invite | 无 |
| mkt:points | /mkt/points | 无 |
| mkt:promotion | /mkt/promotion | 无 |
| package:list | /package, /package/list, /package/member-orders, /package/share | package.list = 3 |
| pos:order:list | /pos/orders | 无 |
| pos:order:stat | /pos/stats | 无 |
| pos:reconciliation:list | /pos/reconciliation | pos.reconciliation = 4 |
| pos:refund | /pos/refund | pos.refund = 4 |
| report:biz:list | /report/biz | report.biz = 5 |
| report:caliber | /report/caliber | report.caliber = 2 |
| report:dashboard | /report/dashboard, /report/kpi | report.dashboard = 5 |
| report:export:list | /report/export | report.export = 4 |
| report:task | /report/task | report.task = 2 |
| stock:alert | /stock/alert | stock.alert = 2 |
| stock:check | /stock/check | stock.check = 4 |
| stock:stat | /stock/stats | 无 |
| sys:config:edit | /sys/payment, /sys/qualification | 无 |
| sys:log:list | /sys/log | sys.log = 3 |
| sys:login-log:list | /sys/login-log | sys.login-log = 3 |
| sys:role:list | /sys/role | 无 |
| sys:tenant:list | /sys/tenant | sys.tenant = 3 |

## A.3 已绑定角色的 7 个权限码

| 权限码 | 绑定角色数 | 角色 | 对应路由 |
|---|---|---|---|
| member:list | 5 | ADMIN, BOSS, CLERK, MANAGER, SUPER_ADMIN | /member |
| wallet:list | 4 | ADMIN, BOSS, MANAGER, SUPER_ADMIN | /wallet/recharge, /wallet/transactions, /gift-card |
| setting | 3 | ADMIN, BOSS, SUPER_ADMIN | /sys/setting |
| employee:level | 3 | ADMIN, STORE_ADMIN, SUPER_ADMIN | /technician/level |
| employee:level:benefit | 3 | ADMIN, STORE_ADMIN, SUPER_ADMIN | /technician/benefit |
| blockchain:trace | 2 | ADMIN, SUPER_ADMIN | /blockchain/trace |
| blockchain:monitor | 2 | ADMIN, SUPER_ADMIN | /blockchain/monitor |

## A.4 各角色可达率表

| 角色 | role id | 权限绑定总行数 | 绑定了前端引用的权限码数 | 可访问路由数（共 147） | 可达率 |
|---|---|---|---|---|---|
| SUPER_ADMIN | 1 | 116 | 7 | 147 | 100.0% |
| ADMIN | 8 | 119 | 7 | 9 | 6.1% |
| BOSS | 6 | 50 | 3 | 5 | 3.4% |
| MANAGER | 5 | 37 | 2 | 4 | 2.7% |
| CLERK | 4 | 19 | 1 | 1 | 0.7% |
| STORE_ADMIN | 2 | 11 | 2 | 2 | 1.4% |
| OPERATOR | 7 | 8 | 0 | 0 | 0.0% |
| EMPLOYEE | 3 | 0 | 0 | 0 | 0.0% |

> 说明：SUPER_ADMIN 的 147/147 依赖后端注入的通配权限码 `*`（`RbacContextLoader.java:78-88` / `AuthService.java:63-71`），非基于本表 116 条实际绑定行。PLATFORM_ADMIN 角色在 `sys_role` 表中不存在，故不可达率无法给出。

## A.5 F1~F9 发现清单

| # | 事实 | 证据位置 |
|---|---|---|
| F1 | 守卫 `hasPermission(code)` 失败即跳 `/403`；权限码来自 `GET /auth/me/permissions`；SUPER_ADMIN / PLATFORM_ADMIN 注入通配 `*` | `router/index.ts` 守卫段；`stores/permission.ts:46-50`；`RbacContextLoader.java:78-88`；`AuthService.java:63-71` |
| F2 | 非超管角色可达率 ≤6.1%（ADMIN 最高 9/147） | 本次 DB 绑定关系 + 路由码交叉计算 |
| F3 | `sys_role_permission` 3 条悬空引用指向不存在的 `permission_id = 9` | SQL dump `sys_role_permission` INSERT 段（行 id 9 / 45 / 62） |
| F4 | `sys_permission` 122 个码有绑定 / 332 个零绑定；51 个 `API` 类型码全部零绑定 | 本次统计 |
| F5 | DB 内双套命名体系并存（menu 点号 vs button/API 冒号）；前端 97 码中 21 个为点号 menu 码的冒号变体 | SQL dump `sys_permission` + `router/index.ts` 交叉 |
| F6 | `commission/rules` 路由重复注册两次，name 同为 `CommissionRules`，permission 分别为 `commission:rule:list` / `commission:rule:config` | `router/index.ts` 两条 `path: 'commission/rules'` |
| F7 | 5 条路由无 `meta.permission`：`/login`、`/mh-consent-h5/:token`、`/403`、`/dashboard`、`/setting` | `router/index.ts` |
| F8 | 57 个缺失码中 0 个被后端 `@RequirePermission` 声明（后端去重 330 码），`PermissionAutoRegistrar` 运行期不会补写 | `backend/src/main/java/**` 注解扫描；`PermissionAutoRegistrar.java:147` |
| F9 | `sys_menu_permission` 110 行、`sys_menu` 0 行；前端菜单树由 `sys_permission.parent_id` 推导 | SQL dump |

## A.6 DB 侧原始统计

| 指标 | 值 |
|---|---|
| `sys_permission` 行数 / 唯一码数 | 454 / 454 |
| `sys_role_permission` 行数 | 360（其中 3 行 `permission_id` 悬空） |
| `sys_role` 行数 | 8（SUPER_ADMIN / STORE_ADMIN / EMPLOYEE / CLERK / MANAGER / BOSS / OPERATOR / ADMIN） |
| 有角色绑定的码数 | 122 |
| 零角色绑定的码数 | 332 |
| 按类型：menu | 111（全部已绑定） |
| 按类型：button | 292（281 零绑定 / 11 已绑定） |
| 按类型：API | 51（全部零绑定） |
| 前端路由码去重数 | 97 |
| 前端 97 码在 DB 中的分布 | 不存在 57 / 存在零绑定 33 / 存在已绑定 7 |
| 后端 `@RequirePermission` 去重码数 | 330 |
| 57 个缺失码中被后端声明的数量 | 0 |
