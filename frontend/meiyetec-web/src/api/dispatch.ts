import request from './request'

/**
 * M12-05 业务策略统一下发（后端 BusinessPolicyDispatchController @ /m12/dispatch）
 *
 * 端点：
 *  - POST   /m12/dispatch                创建 DRAFT
 *  - POST   /m12/dispatch/{id}/publish   同步发布
 *  - POST   /m12/dispatch/{id}/publish-async  异步发布
 *  - POST   /m12/dispatch/{id}/cancel    取消
 *  - GET    /m12/dispatch/page           分页
 *  - GET    /m12/dispatch/{id}           详情
 *  - GET    /m12/dispatch/{id}/targets   目标门店
 *  - GET    /m12/dispatch/{id}/logs      执行日志
 *  - GET    /m12/dispatch/stats          看板统计
 */
export const dispatchApi = {
  // 创建下发单
  create: (data: any) => request.post('/m12/dispatch', data),
  // 同步发布
  publish: (id: number) => request.post(`/m12/dispatch/${id}/publish`),
  // 异步发布（>50 门店自动走这条）
  publishAsync: (id: number) => request.post(`/m12/dispatch/${id}/publish-async`),
  // 取消下发
  cancel: (id: number) => request.post(`/m12/dispatch/${id}/cancel`),
  // 分页查询
  page: (params: {
    pageNum?: number
    pageSize?: number
    dispatchType?: string
    dispatchSubtype?: string
    status?: string
  }) => request.get('/m12/dispatch/page', { params }),
  // 详情
  detail: (id: number) => request.get(`/m12/dispatch/${id}`),
  // 目标门店
  targets: (id: number) => request.get(`/m12/dispatch/${id}/targets`),
  // 执行日志
  logs: (id: number, status?: string) =>
    request.get(`/m12/dispatch/${id}/logs`, { params: status ? { status } : undefined }),
  // 看板统计
  stats: (month?: string) =>
    request.get('/m12/dispatch/stats', { params: month ? { month } : undefined })
}

/**
 * dispatchType → 中文名 + 颜色
 */
export const DISPATCH_TYPE_LABEL: Record<string, string> = {
  COUPON: '优惠券',
  PACKAGE: '套餐',
  DISCOUNT: '折扣',
  ACTIVITY: '营销活动',
  PRICING_RULE: '价格规则'
}

export const DISPATCH_SUBTYPE_LABEL: Record<string, string> = {
  PRICING_RULE: '价格规则下发',
  ACTIVITY: '活动下发',
  COUPON_BATCH: '批次下发',
  EXPERIMENT: '实验下发'
}

export const DISPATCH_STATUS_LABEL: Record<string, { label: string; tag: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  DRAFT: { label: '草稿', tag: 'info' },
  PUBLISHED: { label: '已发布', tag: 'primary' },
  EXECUTING: { label: '执行中', tag: 'warning' },
  DONE: { label: '已完成', tag: 'success' },
  CANCELLED: { label: '已取消', tag: 'danger' }
}
