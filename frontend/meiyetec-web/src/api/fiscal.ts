import request from './request'

/**
 * P3-23 财税直连（后端 FiscalService @ /fiscal）
 */
export const fiscalApi = {
  // 发票开具
  issue: (data: any) => request.post('/fiscal/issue', data),
  listInvoices: (params?: { status?: string; orderId?: number }) =>
    request.get('/fiscal/list', { params }),
  getInvoice: (id: number) => request.get(`/fiscal/${id}`),
  voidInvoice: (id: number, data?: { reason?: string }) =>
    request.post(`/fiscal/${id}/void`, data || {}),

  // 通道管理（只读 — 真实配置走 fiscalConfigApi）
  listChannels: () => request.get('/fiscal/channel/list'),

  // Dashboard
  dashboard: () => request.get('/fiscal/dashboard')
}

/**
 * FISCAL-001 财税开票渠道配置（后端 FiscalConfigController @ /fiscal/config）
 *
 * <p>凭据列（appKey / appSecret）只入参 + 服务端脱敏展示；
 * 敏感操作（修改凭据 / 启用）需要 confirmPassword 二次确认密码。
 */
export const fiscalConfigApi = {
  // 列表（脱敏）
  list: () => request.get('/fiscal/config/list'),
  // 按 channel 查单条
  getByChannel: (channel: string) => request.get(`/fiscal/config/by-channel/${channel}`),
  // 保存（新建或更新）
  save: (data: any) => request.post('/fiscal/config', data),
  // 启停（id + 0/1）
  toggle: (id: number, enabled: 0 | 1) =>
    request.put(`/fiscal/config/${id}/toggle/${enabled}`),
  // 测试连通性（mock 200~500ms 模拟延迟）
  test: (override?: any) => request.post('/fiscal/config/test', override || {})
}
