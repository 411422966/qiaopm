import request from './request'

/**
 * P0-4 会员日自动化（对应后端 MemberDayService @ /member-day）
 * 模块：会员日配置 / 自动化触发 / 优惠核销 / ROI 报告
 */
export const memberDayApi = {
  // 配置管理
  createConfig: (data: any) => request.post('/member-day/config', data),
  updateConfig: (id: number, data: any) => request.post(`/member-day/config/${id}`, data),
  listConfigs: () => request.get('/member-day/config/list'),
  deleteConfig: (id: number) => request.post(`/member-day/config/${id}/delete`),
  initDefault: () => request.post('/member-day/config/init-default'),

  // 触发（手动 / 按配置）
  trigger: (data?: { date?: string }) => request.post('/member-day/trigger', data || {}),
  triggerByConfig: (configId: number, data?: { date?: string }) =>
    request.post(`/member-day/trigger-by-config/${configId}`, data || {}),

  // 执行记录
  listExecutions: (params?: { date?: string; status?: string }) =>
    request.get('/member-day/executions', { params }),
  markUsed: (id: number, data?: { orderId?: number }) =>
    request.post(`/member-day/executions/${id}/mark-used`, data || {}),

  // ROI 报告 / 即将到来
  roi: (days = 30) => request.get('/member-day/roi', { params: { days } }),
  upcoming: (days = 7) => request.get('/member-day/upcoming', { params: { days } })
}