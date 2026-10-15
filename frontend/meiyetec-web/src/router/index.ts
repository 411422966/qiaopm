import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { usePermissionStore } from '@/stores/permission'

NProgress.configure({ showSpinner: false })

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/mh-consent-h5/:token',
    name: 'MhConsentH5',
    component: () => import('@/views/mh/consent-h5.vue'),
    meta: { title: '知情同意签署', requiresAuth: false }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/403.vue'),
    meta: { title: '无权限', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('@/components/Layout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard.vue'), meta: { title: '首页', requiresAuth: true } },
      { path: 'setting', name: 'Setting', component: () => import('@/views/setting/index.vue'), meta: { title: '系统设置', requiresAuth: true } },
      { path: 'member', name: 'Member', component: () => import('@/views/member/index.vue'), meta: { title: '会员管理', requiresAuth: true, permission: 'member:list' } },
      { path: 'wallet/recharge', name: 'WalletRecharge', component: () => import('@/views/wallet/recharge.vue'), meta: { title: '储值管理', requiresAuth: true, permission: 'wallet:list' } },
      { path: 'wallet/transactions', name: 'WalletTransactions', component: () => import('@/views/wallet/transactions.vue'), meta: { title: '储值流水', requiresAuth: true, permission: 'wallet:list' } },
      { path: 'package', name: 'PackageList', component: () => import('@/views/package/index.vue'), meta: { title: '套餐次卡', requiresAuth: true, permission: 'package:list' } },
      { path: 'package/list', name: 'PackageListItem', component: () => import('@/views/package/index.vue'), meta: { title: '套餐管理', requiresAuth: true, permission: 'package:list' } },
      { path: 'package/member-orders', name: 'PackageMemberOrders', component: () => import('@/views/package/member-orders.vue'), meta: { title: '会员套餐查询', requiresAuth: true, permission: 'package:list' } },
      { path: 'package/share', name: 'PackageShare', component: () => import('@/views/package/share.vue'), meta: { title: '多人共享套餐', requiresAuth: true, permission: 'package:list' } },
      { path: 'booking/calendar', name: 'BookingCalendar', component: () => import('@/views/booking/calendar.vue'), meta: { title: '预约日历', requiresAuth: true, permission: 'booking:list' } },
      { path: 'booking/list', name: 'BookingList', component: () => import('@/views/booking/list.vue'), meta: { title: '预约列表', requiresAuth: true, permission: 'booking:list' } },
      { path: 'booking/schedule', name: 'BookingSchedule', component: () => import('@/views/booking/schedule.vue'), meta: { title: '排班管理', requiresAuth: true, permission: 'booking:schedule:list' } },
      { path: 'pos/checkout', name: 'PosCheckout', component: () => import('@/views/pos/checkout.vue'), meta: { title: '收银台', requiresAuth: true, permission: 'pos:list' } },
      { path: 'pos/orders', name: 'PosOrders', component: () => import('@/views/pos/orders.vue'), meta: { title: '订单列表', requiresAuth: true, permission: 'pos:order:list' } },
      { path: 'pos/stats', name: 'PosStats', component: () => import('@/views/pos/stats.vue'), meta: { title: '收银统计', requiresAuth: true, permission: 'pos:order:stat' } },
      { path: 'pos/refund', name: 'PosRefund', component: () => import('@/views/pos/refund.vue'), meta: { title: '退款单', requiresAuth: true, permission: 'pos:refund' } },
      { path: 'pos/reconciliation', name: 'PosReconciliation', component: () => import('@/views/pos/reconciliation.vue'), meta: { title: '支付对账', requiresAuth: true, permission: 'pos:reconciliation:list' } },
      { path: 'stock/list', name: 'StockList', component: () => import('@/views/stock/list.vue'), meta: { title: '库存列表', requiresAuth: true, permission: 'stock:list' } },
      { path: 'stock/product', name: 'StockProduct', component: () => import('@/views/stock/product.vue'), meta: { title: '商品耗材', requiresAuth: true, permission: 'stock:list' } },
      { path: 'stock/record', name: 'StockRecord', component: () => import('@/views/stock/record.vue'), meta: { title: '出入库记录', requiresAuth: true, permission: 'stock:list' } },
      { path: 'stock/consumption', name: 'StockConsumption', component: () => import('@/views/stock/consumption.vue'), meta: { title: '消耗记录', requiresAuth: true, permission: 'stock:list' } },
      { path: 'stock/check', name: 'StockCheck', component: () => import('@/views/stock/check.vue'), meta: { title: '盘点管理', requiresAuth: true, permission: 'stock:check' } },
      { path: 'stock/alert', name: 'StockAlert', component: () => import('@/views/stock/alert.vue'), meta: { title: '库存预警', requiresAuth: true, permission: 'stock:alert' } },
      { path: 'stock/stats', name: 'StockStats', component: () => import('@/views/stock/stats.vue'), meta: { title: '库存统计', requiresAuth: true, permission: 'stock:stat' } },
      { path: 'stock/cross-store', name: 'StockCrossStore', component: () => import('@/views/stock/crossStore.vue'), meta: { title: '跨店库存调度', requiresAuth: true, permission: 'wms:transfer' } },
      { path: 'commission/rules', name: 'CommissionRules', component: () => import('@/views/commission/rules.vue'), meta: { title: '提成规则', requiresAuth: true, permission: 'commission:rule:list' } },
      { path: 'commission/records', name: 'CommissionRecords', component: () => import('@/views/commission/records.vue'), meta: { title: '提成记录', requiresAuth: true, permission: 'commission:record:list' } },
      { path: 'commission/salary', name: 'CommissionSalary', component: () => import('@/views/commission/salary.vue'), meta: { title: '工资报表', requiresAuth: true, permission: 'commission:salary:list' } },
      { path: 'commission/settlement', name: 'CommissionSettlement', component: () => import('@/views/commission/settlement.vue'), meta: { title: '结算管理', requiresAuth: true, permission: 'commission:settlement:list' } },
      { path: 'market/coupon', name: 'MarketCoupon', component: () => import('@/views/mkt/coupon.vue'), meta: { title: '优惠券', requiresAuth: true, permission: 'market:coupon:list' } },
      { path: 'market/points', name: 'MarketPoints', component: () => import('@/views/mkt/points.vue'), meta: { title: '积分商城', requiresAuth: true, permission: 'market:point:list' } },
      { path: 'market/activity', name: 'MarketActivity', component: () => import('@/views/mkt/promotion.vue'), meta: { title: '营销活动', requiresAuth: true, permission: 'market:activity:list' } },
      { path: 'report/dashboard', name: 'ReportDashboard', component: () => import('@/views/report/dashboard.vue'), meta: { title: '数据看板', requiresAuth: true, permission: 'report:dashboard' } },
      { path: 'report/biz', name: 'ReportBiz', component: () => import('@/views/report/biz.vue'), meta: { title: '营业报表', requiresAuth: true, permission: 'report:biz:list' } },
      { path: 'report/kpi', name: 'ReportKpiOverview', component: () => import('@/views/report/kpi-overview.vue'), meta: { title: '高级KPI看板', requiresAuth: true, permission: 'report:dashboard' } },
      { path: 'report/export', name: 'ReportExport', component: () => import('@/views/report/export.vue'), meta: { title: '导出管理', requiresAuth: true, permission: 'report:export:list' } },
      { path: 'stock/trace/:id', name: 'StockTrace', component: () => import('@/views/stock/trace.vue'), meta: { title: '批次追溯', requiresAuth: true, permission: 'stock:list' } },
      { path: 'sys/role', name: 'SysRole', component: () => import('@/views/system/role.vue'), meta: { title: '角色管理', requiresAuth: true, permission: 'sys:role:list' } },
      { path: 'sys/setting', name: 'SysSetting', component: () => import('@/views/system/setting.vue'), meta: { title: '系统设置', requiresAuth: true, permission: 'setting' } },
      { path: 'sys/tenant', name: 'SysTenant', component: () => import('@/views/system/tenant.vue'), meta: { title: '租户管理', requiresAuth: true, permission: 'sys:tenant:list' } },
      { path: 'sys/log', name: 'SysLog', component: () => import('@/views/system/log.vue'), meta: { title: '操作日志', requiresAuth: true, permission: 'sys:log:list' } },
      { path: 'sys/login-log', name: 'SysLoginLog', component: () => import('@/views/system/login-log.vue'), meta: { title: '登录日志', requiresAuth: true, permission: 'sys:login-log:list' } },
      { path: 'sys/payment', name: 'SysPayment', component: () => import('@/views/system/payment.vue'), meta: { title: '支付配置', requiresAuth: true, permission: 'sys:config:edit' } },
      { path: 'sys/qualification', name: 'SysQualification', component: () => import('@/views/system/qualification.vue'), meta: { title: '商户资质材料', requiresAuth: true, permission: 'sys:config:edit' } },
      { path: 'dev/gray-release', name: 'DevGrayRelease', component: () => import('@/views/dev/GrayRelease.vue'), meta: { title: '灰度发布控制台', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/forecast', name: 'DevForecast', component: () => import('@/views/dev/Forecast.vue'), meta: { title: '客流预测与智能排班', requiresAuth: true, permission: 'booking:list' } },
      { path: 'dev/ltv-training', name: 'DevLtvTraining', component: () => import('@/views/dev/LtvTraining.vue'), meta: { title: 'LTV流失预测', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/care-plan', name: 'DevCarePlan', component: () => import('@/views/dev/CarePlan.vue'), meta: { title: '护肤方案共享套餐', requiresAuth: true, permission: 'biz:service-item' } },
      { path: 'dev/pricelist', name: 'DevPriceList', component: () => import('@/views/dev/PriceList.vue'), meta: { title: '电子价目表卡项套餐', requiresAuth: true, permission: 'biz:service-item' } },
      { path: 'dev/datahub', name: 'DevDataHub', component: () => import('@/views/dev/DataHub.vue'), meta: { title: '数据中台', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/care-memberday', name: 'DevCareMemberDay', component: () => import('@/views/dev/CareMemberDay.vue'), meta: { title: '配方护理会员日', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/p1-suite', name: 'DevP1Suite', component: () => import('@/views/dev/P1Suite.vue'), meta: { title: '套件合集', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/p2-suite', name: 'DevP2Suite', component: () => import('@/views/dev/P2Suite.vue'), meta: { title: '套件合集', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/p3-suite', name: 'DevP3Suite', component: () => import('@/views/dev/P3Suite.vue'), meta: { title: '套件合集', requiresAuth: true, permission: 'dev:access' } },
      { path: 'dev/gap-suite', name: 'DevGapSuite', component: () => import('@/views/dev/GapSuite.vue'), meta: { title: '套件合集', requiresAuth: true, permission: 'dev:access' } },
      { path: 'biz/service-item', name: 'BizServiceItem', component: () => import('@/views/biz/service-item.vue'), meta: { title: '服务项目', requiresAuth: true, permission: 'biz:service:item' } },
      { path: 'biz/treatment-plan', name: 'BizTreatmentPlan', component: () => import('@/views/biz/treatment-plan.vue'), meta: { title: '疗程方案', requiresAuth: true, permission: 'biz:treatment:plan' } },
      { path: 'biz/treatment-report', name: 'BizTreatmentReport', component: () => import('@/views/biz/treatment-report.vue'), meta: { title: '效果报告', requiresAuth: true, permission: 'biz:treatment:plan' } },
      { path: 'booking/resource', name: 'BookingResource', component: () => import('@/views/booking/resource.vue'), meta: { title: '预约资源', requiresAuth: true, permission: 'biz:booking:resource' } },
      { path: 'booking/waitlist', name: 'BookingWaitlist', component: () => import('@/views/booking/waitlist.vue'), meta: { title: '候补队列', requiresAuth: true, permission: 'biz:booking:waitlist' } },
      { path: 'booking/no-show', name: 'BookingNoShow', component: () => import('@/views/booking/no-show.vue'), meta: { title: '爽约管理', requiresAuth: true, permission: 'biz:booking:no-show' } },
      { path: 'commission/batch', name: 'CommissionBatch', component: () => import('@/views/commission/batch.vue'), meta: { title: '提成结算批次', requiresAuth: true, permission: 'commission:batch' } },
      { path: 'commission/reversal', name: 'CommissionReversal', component: () => import('@/views/commission/reversal.vue'), meta: { title: '提成冲正', requiresAuth: true, permission: 'commission:reversal' } },
      { path: 'mkt/promotion', name: 'MktPromotion', component: () => import('@/views/mkt/promotion.vue'), meta: { title: '营销活动', requiresAuth: true, permission: 'mkt:promotion' } },
      { path: 'mkt/coupon', name: 'MktCoupon', component: () => import('@/views/mkt/coupon.vue'), meta: { title: '优惠券', requiresAuth: true, permission: 'mkt:coupon' } },
      { path: 'mkt/points', name: 'MktPoints', component: () => import('@/views/mkt/points.vue'), meta: { title: '积分规则', requiresAuth: true, permission: 'mkt:points' } },
      { path: 'mkt/effect', name: 'MktEffect', component: () => import('@/views/mkt/effect.vue'), meta: { title: '营销效果', requiresAuth: true, permission: 'mkt:effect' } },
      { path: 'report/task', name: 'ReportTask', component: () => import('@/views/report/task.vue'), meta: { title: '导出任务', requiresAuth: true, permission: 'report:task' } },
      { path: 'report/caliber', name: 'ReportCaliber', component: () => import('@/views/report/caliber.vue'), meta: { title: '指标口径', requiresAuth: true, permission: 'report:caliber' } },
      { path: 'org/hq', name: 'OrgHq', component: () => import('@/views/org/hq.vue'), meta: { title: '总部看板', requiresAuth: true, permission: 'org:hq' } },
      { path: 'org/brand', name: 'OrgBrand', component: () => import('@/views/org/brand.vue'), meta: { title: '品牌管理', requiresAuth: true, permission: 'org:brand' } },
      { path: 'org/store', name: 'OrgStore', component: () => import('@/views/org/store.vue'), meta: { title: '门店管理', requiresAuth: true, permission: 'org:store' } },
      { path: 'attendance/index', name: 'AttendanceIndex', component: () => import('@/views/attendance/index.vue'), meta: { title: '考勤打卡', requiresAuth: true, permission: 'employee:attendance' } },
      { path: 'attendance/records', name: 'AttendanceRecords', component: () => import('@/views/attendance/records.vue'), meta: { title: '考勤记录', requiresAuth: true, permission: 'employee:attendance:list' } },
      { path: 'attendance/leave', name: 'AttendanceLeave', component: () => import('@/views/attendance/leave.vue'), meta: { title: '请假管理', requiresAuth: true, permission: 'employee:leave' } },
      { path: 'attendance/statistics', name: 'AttendanceStatistics', component: () => import('@/views/attendance/statistics.vue'), meta: { title: '考勤统计', requiresAuth: true, permission: 'employee:attendance:stat' } },
      { path: 'attendance/config', name: 'AttendanceConfig', component: () => import('@/views/attendance/config.vue'), meta: { title: '考勤规则配置', requiresAuth: true, permission: 'employee:attendance:config' } },
      { path: 'technician/level', name: 'TechnicianLevel', component: () => import('@/views/technician/level.vue'), meta: { title: '技师等级', requiresAuth: true, permission: 'employee:level' } },
      { path: 'technician/benefit', name: 'TechnicianBenefit', component: () => import('@/views/technician/benefit.vue'), meta: { title: '等级权益配置', requiresAuth: true, permission: 'employee:level:benefit' } },
      { path: 'technician/evaluation', name: 'TechnicianEvaluation', component: () => import('@/views/technician/evaluation.vue'), meta: { title: '等级评定', requiresAuth: true, permission: 'employee:level:evaluation' } },
      { path: 'commission/rule-list', name: 'CommissionRuleList', component: () => import('@/views/commission/rule-list.vue'), meta: { title: '提成规则列表', requiresAuth: true, permission: 'commission:rule' } },
      { path: 'commission/rules', name: 'CommissionRules', component: () => import('@/views/commission/rules.vue'), meta: { title: '提成规则配置', requiresAuth: true, permission: 'commission:rule:config' } },
      { path: 'commission/simulation', name: 'CommissionSimulation', component: () => import('@/views/commission/simulation.vue'), meta: { title: '提成模拟计算', requiresAuth: true, permission: 'commission:simulation' } },
      { path: 'material/binding', name: 'MaterialBinding', component: () => import('@/views/material/binding.vue'), meta: { title: '耗材绑定配置', requiresAuth: true, permission: 'stock:material:binding' } },
      { path: 'material/statistics', name: 'MaterialStatistics', component: () => import('@/views/material/statistics.vue'), meta: { title: '耗材消耗统计', requiresAuth: true, permission: 'stock:material:stat' } },
      { path: 'material/expiry', name: 'MaterialExpiry', component: () => import('@/views/material/expiry-tracking.vue'), meta: { title: '效期追踪管理', requiresAuth: true, permission: 'stock:expiry' } },
      { path: 'material/service-material', name: 'MaterialServiceMaterial', component: () => import('@/views/material/service-material.vue'), meta: { title: '项目耗材扣减', requiresAuth: true, permission: 'stock:service-material:rule' } },
      { path: 'resource/schedule', name: 'ResourceSchedule', component: () => import('@/views/resource/resource-schedule.vue'), meta: { title: '双资源排程', requiresAuth: true, permission: 'resource:schedule' } },
      { path: 'payment/overseas', name: 'PaymentOverseas', component: () => import('@/views/payment/overseas-payment.vue'), meta: { title: '海外支付', requiresAuth: true, permission: 'payment:overseas:gateway' } },
      { path: 'iot/management', name: 'IotManagement', component: () => import('@/views/iot/iot-management.vue'), meta: { title: 'IoT设备联动', requiresAuth: true, permission: 'iot:device:manage' } },
      { path: 'payment/dcep-advanced', name: 'DcepAdvanced', component: () => import('@/views/payment/dcep-advanced.vue'), meta: { title: '数字人民币2.0', requiresAuth: true, permission: 'dcep:hardware-wallet' } },
      { path: 'crm/cross-rule', name: 'CrmCrossRule', component: () => import('@/views/crm/cross-rule.vue'), meta: { title: '跨店规则', requiresAuth: true, permission: 'crm:cross-rule' } },
      { path: 'crm/cross-record', name: 'CrmCrossRecord', component: () => import('@/views/crm/cross-record.vue'), meta: { title: '跨店核销', requiresAuth: true, permission: 'crm:cross-record' } },
      { path: 'crm/care/overview', name: 'CrmCareOverview', component: () => import('@/views/crm/care-overview.vue'), meta: { title: '客户运营看板', requiresAuth: true, permission: 'crm:follow-up' } },
      { path: 'crm/care/follow-task', name: 'CrmFollowTask', component: () => import('@/views/crm/care-follow-task.vue'), meta: { title: '回访任务', requiresAuth: true, permission: 'crm:follow-up' } },
      { path: 'crm/care/follow-rule', name: 'CrmFollowRule', component: () => import('@/views/crm/care-follow-rule.vue'), meta: { title: '自动任务规则', requiresAuth: true, permission: 'crm:follow-plan' } },
      { path: 'crm/care/follow-plan', name: 'CrmFollowPlan', component: () => import('@/views/crm/care-follow-plan.vue'), meta: { title: '回访计划', requiresAuth: true, permission: 'crm:follow-plan' } },
      { path: 'crm/care/suggestion', name: 'CrmCareSuggestion', component: () => import('@/views/crm/care-suggestion.vue'), meta: { title: '护理建议生成', requiresAuth: true, permission: 'crm:care:suggestion' } },
      { path: 'crm/detection', name: 'CrmDetection', component: () => import('@/views/crm/detection.vue'), meta: { title: '肤质档案', requiresAuth: true, permission: 'crm:detection' } },
      { path: 'crm/style-package', name: 'CrmStylePackage', component: () => import('@/views/crm/style-package.vue'), meta: { title: '款式偏好', requiresAuth: true, permission: 'crm:style:library' } },
      { path: 'crm/care/complaint', name: 'CrmComplaint', component: () => import('@/views/crm/care-complaint.vue'), meta: { title: '投诉工单', requiresAuth: true, permission: 'crm:complaint' } },
      { path: 'crm/care/review', name: 'CrmReview', component: () => import('@/views/crm/care-review.vue'), meta: { title: '客户评价', requiresAuth: true, permission: 'crm:review' } },
      { path: 'crm/care/review-tpl', name: 'CrmReviewTpl', component: () => import('@/views/crm/care-review-tpl.vue'), meta: { title: '评价模板', requiresAuth: true, permission: 'crm:review-tpl' } },
      { path: 'mkt/channel', name: 'MktChannel', component: () => import('@/views/mkt/channel.vue'), meta: { title: '渠道配置', requiresAuth: true, permission: 'mkt:channel' } },
      { path: 'mkt/template', name: 'MktTemplate', component: () => import('@/views/mkt/template.vue'), meta: { title: '消息模板', requiresAuth: true, permission: 'mkt:template' } },
      { path: 'mkt/touch-task', name: 'MktTouchTask', component: () => import('@/views/mkt/touch-task.vue'), meta: { title: '触达任务', requiresAuth: true, permission: 'mkt:touch-task' } },
      { path: 'mkt/birthday', name: 'MktBirthday', component: () => import('@/views/mkt/birthday.vue'), meta: { title: '生日提醒', requiresAuth: true, permission: 'mkt:birthday' } },
      { path: 'mkt/journey', name: 'MktJourney', component: () => import('@/views/mkt/journey.vue'), meta: { title: '营销旅程', requiresAuth: true, permission: 'mkt:journey' } },
      { path: 'm09/invite', name: 'M09Invite', component: () => import('@/views/m09-invite/index.vue'), meta: { title: '裂变邀请', requiresAuth: true, permission: 'mkt:invite:list' } },
      { path: 'mkt/conversion', name: 'MktConversion', component: () => import('@/views/mkt/conversion.vue'), meta: { title: '触达效果', requiresAuth: true, permission: 'mkt:conversion' } },
      { path: 'mkt/pricing', name: 'MktPricing', component: () => import('@/views/mkt/pricing.vue'), meta: { title: '动态定价', requiresAuth: true, permission: 'mkt:pricing' } },
      // M12-05 业务策略统一下发
      { path: 'm12/dispatch', name: 'M12Dispatch', component: () => import('@/views/m12/dispatch/index.vue'), meta: { title: '业务策略下发', requiresAuth: true, permission: 'mkt:dispatch' } },
      { path: 'intel/rfm', name: 'IntelRfm', component: () => import('@/views/intel/rfm.vue'), meta: { title: 'RFM 分群', requiresAuth: true, permission: 'crm:rfm' } },
      { path: 'intel/ltv', name: 'IntelLtv', component: () => import('@/views/intel/ltv.vue'), meta: { title: 'LTV 预测', requiresAuth: true, permission: 'crm:tag' } },
      // M15-03 价格敏感度
      { path: 'intel/price-sensitivity', name: 'IntelPriceSensitivity', component: () => import('@/views/intel/price-sensitivity.vue'), meta: { title: '价格敏感度', requiresAuth: true, permission: 'crm:tag' } },
      { path: 'mh/visual', name: 'MhVisual', component: () => import('@/views/mh/visual.vue'), meta: { title: '视觉档案', requiresAuth: true, permission: 'mh:visual' } },
      { path: 'mh/consent', name: 'MhConsent', component: () => import('@/views/mh/consent.vue'), meta: { title: '医美合规', requiresAuth: true, permission: 'mh:consent' } },
      { path: 'pay/dcep', name: 'PayDcep', component: () => import('@/views/pay/dcep.vue'), meta: { title: '数字人民币', requiresAuth: true, permission: 'pay:dcep' } },
      { path: 'care', name: 'Care', component: () => import('@/views/care/index.vue'), meta: { title: '配方护理建议', requiresAuth: true, permission: 'biz:service-item' } },
      { path: 'member-day', name: 'MemberDay', component: () => import('@/views/member-day/index.vue'), meta: { title: '会员日自动化', requiresAuth: true, permission: 'mkt:activity' } },
      { path: 'style-gallery', name: 'StyleGallery', component: () => import('@/views/style-gallery/index.vue'), meta: { title: '款式图库', requiresAuth: true, permission: 'mkt:activity' } },
      { path: 'style-gallery/quick-order', name: 'StyleGalleryQuickOrder', component: () => import('@/views/style-gallery/quick-order.vue'), meta: { title: '快速下单', requiresAuth: true, permission: 'mkt:activity' } },
      { path: 'lbs', name: 'Lbs', component: () => import('@/views/lbs/index.vue'), meta: { title: '上门服务调度', requiresAuth: true, permission: 'booking:list' } },
      { path: 'gift-card', name: 'GiftCard', component: () => import('@/views/gift-card/index.vue'), meta: { title: '礼品卡', requiresAuth: true, permission: 'wallet:list' } },
      { path: 'subscription', name: 'Subscription', component: () => import('@/views/subscription/index.vue'), meta: { title: '订阅会员制', requiresAuth: true, permission: 'mkt:activity' } },
      { path: 'ai-vision', name: 'AIVision', component: () => import('@/views/ai-vision/index.vue'), meta: { title: 'AI 视觉识别', requiresAuth: true, permission: 'dev:access' } },
      { path: 'ltv-churn', name: 'LtvChurn', component: () => import('@/views/ltv-churn/index.vue'), meta: { title: 'LTV/Churn 分析', requiresAuth: true, permission: 'crm:tag' } },
      { path: 'iot', name: 'Iot', component: () => import('@/views/iot/index.vue'), meta: { title: 'IoT 设备管理', requiresAuth: true, permission: 'dev:access' } },
      { path: 'kiosk', name: 'Kiosk', component: () => import('@/views/kiosk/index.vue'), meta: { title: '自助 Kiosk', requiresAuth: true, permission: 'booking:list' } },
      { path: 'complication', name: 'Complication', component: () => import('@/views/complication/index.vue'), meta: { title: '并发症 SOP', requiresAuth: true, permission: 'mh:consent' } },
      { path: 'cohort', name: 'Cohort', component: () => import('@/views/cohort/index.vue'), meta: { title: 'Cohort 队列', requiresAuth: true, permission: 'crm:rfm' } },
      { path: 'gdpr', name: 'Gdpr', component: () => import('@/views/gdpr/index.vue'), meta: { title: 'GDPR 合规', requiresAuth: true, permission: 'dev:access' } },
      { path: 'hipaa', name: 'Hipaa', component: () => import('@/views/hipaa/index.vue'), meta: { title: 'HIPAA 加固', requiresAuth: true, permission: 'dev:access' } },
      { path: 'pos-device', name: 'PosDevice', component: () => import('@/views/pos-device/index.vue'), meta: { title: '海外 POS', requiresAuth: true, permission: 'pay:order' } },
      { path: 'plugin', name: 'Plugin', component: () => import('@/views/plugin/index.vue'), meta: { title: '插件市场', requiresAuth: true, permission: 'dev:access' } },
      { path: 'fiscal', name: 'Fiscal', component: () => import('@/views/fiscal/index.vue'), meta: { title: '财税直连', requiresAuth: true, permission: 'pay:order' } },
      { path: 'fiscal/config', name: 'FiscalConfig', component: () => import('@/views/fiscal/config.vue'), meta: { title: '财税渠道配置', requiresAuth: true, permission: 'pay:order' } },
      { path: 'fiscal/list', name: 'FiscalList', component: () => import('@/views/fiscal/list.vue'), meta: { title: '发票列表', requiresAuth: true, permission: 'pay:order' } },
      { path: 'crm-integration', name: 'CrmIntegration', component: () => import('@/views/crm-integration/index.vue'), meta: { title: 'CRM 集成', requiresAuth: true, permission: 'crm:tag' } },
      { path: 'ecommerce', name: 'Ecommerce', component: () => import('@/views/ecommerce/index.vue'), meta: { title: '电商集成', requiresAuth: true, permission: 'mkt:activity' } },
      { path: 'intl-payment', name: 'IntlPayment', component: () => import('@/views/intl-payment/index.vue'), meta: { title: '国际支付', requiresAuth: true, permission: 'pay:order' } },
      { path: 'intl-booking', name: 'IntlBooking', component: () => import('@/views/intl-booking/index.vue'), meta: { title: '国际预约', requiresAuth: true, permission: 'booking:list' } },
      { path: 'ai-scheduler', name: 'AiScheduler', component: () => import('@/views/ai-scheduler/index.vue'), meta: { title: 'AI 智能排班', requiresAuth: true, permission: 'dev:access' } },
      { path: 'risk-control', name: 'RiskControl', component: () => import('@/views/risk-control/index.vue'), meta: { title: '资金风控', requiresAuth: true, permission: 'dev:access' } },
      { path: 'google-maps', name: 'GoogleMaps', component: () => import('@/views/google-maps/index.vue'), meta: { title: 'Google Maps', requiresAuth: true, permission: 'dev:access' } },
      // P4-02 会员生命周期营销
      { path: 'lifecycle', name: 'LifecycleDashboard', component: () => import('@/views/lifecycle/index.vue'), meta: { title: '会员生命周期看板', requiresAuth: true, permission: 'mkt:activity' } },
      // P4-03 SPA 场景模式
      { path: 'spa-scene', name: 'SpaSceneMode', component: () => import('@/views/spa-scene/index.vue'), meta: { title: 'SPA场景模式', requiresAuth: true, permission: 'biz:room' } },
      // P2-003 区块链集成
      { path: 'blockchain/trace', name: 'BlockchainTrace', component: () => import('@/views/blockchain/trace.vue'), meta: { title: '区块链溯源', requiresAuth: true, permission: 'blockchain:trace' } },
      { path: 'blockchain/monitor', name: 'BlockchainMonitor', component: () => import('@/views/blockchain/monitor.vue'), meta: { title: '区块链监控大屏', requiresAuth: true, permission: 'blockchain:monitor' } }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  NProgress.start()
  document.title = (to.meta.title as string) || '美业SaaS系统'

  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth !== false && !token) {
    // 未登录：把当前 URL 作为 redirect 参数带给登录页，登录后再跳回
    // 避免出现"页面要先发请求再被 401 拦截回登录"的体验问题（这时 DevTools 会把请求误显为 404）
    const redirect = to.fullPath !== '/login' ? to.fullPath : undefined
    next({ path: '/login', query: redirect ? { redirect } : undefined })
    NProgress.done()
    return
  }

  // 已登录状态：保证 permission store 已加载
  if (token) {
    const store = usePermissionStore()
    if (!store.loaded) {
      await store.refresh()
    }
    const need = to.meta.permission as string | undefined
    // 兜底白名单：首页 / 登录 / 404 始终放行，避免死循环
    const safePath = to.path === '/dashboard' || to.path === '/login' || to.path.startsWith('/403') || to.path.startsWith('/404')
    if (need && !safePath && !store.hasPermission(need)) {
      next({ path: '/403', query: { denied: need } })
      NProgress.done()
      return
    }
  }
  next()
  NProgress.done()
})

export default router
