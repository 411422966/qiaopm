import request from './request'

/**
 * P3-24 CRM 集成（后端 CrmIntegrationService @ /crm-integration）
 */
export const crmIntegrationApi = {
  // 集成配置
  create: (data: any) => request.post('/crm-integration', data),
  list: (params?: { provider?: string; status?: string }) =>
    request.get('/crm-integration/list', { params }),
  update: (id: number, data: any) => request.post(`/crm-integration/${id}`, data),
  toggle: (id: number, data: { status: string }) => request.post(`/crm-integration/${id}/toggle`, data),

  // 同步
  // EXT-24 + I005-C2：force=true 时 URL 加 ?force=1，覆盖 syncWindowMinutes 窗口限制
  syncNow: (id: number, force?: boolean) => {
    const url = force
      ? `/crm-integration/${id}/sync?force=1`
      : `/crm-integration/${id}/sync`
    return request.post(url)
  },
  listSyncLogs: (params?: { integrationId?: number }) =>
    request.get('/crm-integration/sync-log/list', { params }),

  // Dashboard
  dashboard: () => request.get('/crm-integration/dashboard')
}