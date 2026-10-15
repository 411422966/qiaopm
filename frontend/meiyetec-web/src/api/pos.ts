import request from './request'

/**
 * M07 收银结算 API
 * 端点全部对齐 PosController
 */

// ===== Service / 服务 =====
export const serviceApi = {
  list: (params?: any) => request.get('/pos/services/list', { params }),
  detail: (id: number) => request.get(`/pos/services/${id}`)
}

// ===== Order / 订单 =====
export const orderApi = {
  create: (data: any) => request.post('/pos/orders', data),
  detail: (id: number) => request.get(`/pos/orders/${id}`),
  page: (data: any) => request.post('/pos/orders/page', data),
  today: (storeId?: number) => request.get('/pos/orders/today', { params: { storeId } }),
  stats:  (storeId?: number) => request.get('/pos/orders/stats', { params: { storeId } }),
  addItem: (id: number, item: any) => request.post(`/pos/orders/${id}/items`, item),
  removeItem: (id: number, itemId: number) => request.delete(`/pos/orders/${id}/items/${itemId}`),
  applyDiscount: (id: number, discountAmount: number) =>
    request.post(`/pos/orders/${id}/discount`, { discountAmount }),
  pay: (id: number, body: any) => request.post(`/pos/orders/${id}/pay`, body),
  refund: (id: number, body: any) => request.post(`/pos/orders/${id}/refund`, body),
  voidOrder: (id: number, reason?: string) => request.post(`/pos/orders/${id}/void`, { reason })
}

// ===== 会员查找（前端用 memberApi） =====
export const posApi = { serviceApi, orderApi }
export default posApi

// ===== R2.0-C 退款单（PosRefundController） =====
export const refundApi = {
  create: (body: any, idemKey?: string) =>
    request.post('/pos/refund', body, idemKey ? { idempotencyKey: idemKey } as any : undefined),
  createCross: (body: any, idemKey?: string) =>
    request.post('/pos/refund/cross', body, idemKey ? { idempotencyKey: idemKey } as any : undefined),
  detail: (id: number) => request.get(`/pos/refund/${id}`),
  page: (params: any) => request.get('/pos/refund/page', { params }),
  /**
   * 旧版单步审核（PosRefundController.audit）：
   * 由 service 内部按金额自动选级；保留向后兼容
   */
  audit: (id: number, body: any) => request.post(`/pos/refund/${id}/audit`, body),
  process: (id: number) => request.post(`/pos/refund/${id}/process`, {}),
  cancel: (id: number, body?: any) => request.post(`/pos/refund/${id}/cancel`, body || {}),

  // ===== POS-008 多级审核工作流（RefundAuditController） =====
  /** 当前级别审核通过 → 自动进入下一级 / 终审通过 */
  approve: (id: number, body?: any) =>
    request.post(`/pos/refund/audit/${id}/approve`, body || {}),
  /** 任意级别可驳回 */
  reject: (id: number, body?: any) =>
    request.post(`/pos/refund/audit/${id}/reject`, body || {}),
  /** 驳回后重新提交（可修改金额/原因） */
  resubmit: (id: number, body?: any) =>
    request.post(`/pos/refund/audit/${id}/resubmit`, body || {}),
  /** 待我审核列表（按级别 + 可选门店过滤） */
  pending: (auditLevel: number, storeId?: number) =>
    request.get('/pos/refund/audit/pending', { params: { auditLevel, storeId } }),
  /** 审核看板统计 */
  stats: () => request.get('/pos/refund/audit/stats'),
  /** 审核时间线 */
  timeline: (id: number) => request.get(`/pos/refund/audit/timeline/${id}`),
  /** 当前租户的审核规则列表（无配置则返回默认规则） */
  rules: () => request.get('/pos/refund/audit/rules'),
  /** 创建/更新单条规则 */
  upsertRule: (rule: any) => request.post('/pos/refund/audit/rules', rule)
}

// ===== R2.0-C 支付对账 =====
export const reconciliationApi = {
  run: (date?: string) => request.post('/pos/reconciliation/run', null, { params: date ? { date } : {} }),
  page: (params: any) => request.get('/pos/reconciliation/page', { params }),
  handle: (id: number, body: any) => request.post(`/pos/reconciliation/${id}/handle`, body)
}
