import request from './request'

/**
 * P2-18 GDPR 合规（后端 GdprService @ /gdpr）
 */
export const gdprApi = {
  // 同意管理
  grantConsent: (data: any) => request.post('/gdpr/consent', data),
  revokeConsent: (id: number) => request.post(`/gdpr/consent/${id}/revoke`),
  listConsents: (params?: { memberId?: number; purpose?: string }) =>
    request.get('/gdpr/consent/list', { params }),

  // 数据请求（导出/删除）
  createRequest: (data: { memberId: number; requestType: 'EXPORT' | 'DELETE'; remark?: string }) =>
    request.post('/gdpr/data-request', data),
  processRequest: (id: number, data: { status: 'PROCESSING' | 'COMPLETED' | 'REJECTED' }) =>
    request.post(`/gdpr/data-request/${id}/process`, data),
  listRequests: (params?: { status?: string }) => request.get('/gdpr/data-request/list', { params }),

  // 审计日志
  listAuditLogs: (params?: { memberId?: number }) =>
    request.get('/gdpr/audit-log/list', { params })
}