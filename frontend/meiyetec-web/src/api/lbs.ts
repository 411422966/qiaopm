import request from './request'

/**
 * Phase 3 上门服务 LBS 派单 API
 * 对应后端 LbsDispatchService @ /lbs
 * 模块：技师管理 / 自动派单 / 订单状态机 / 路线优化 / 派单日志 / 派单配置
 */
export const lbsApi = {
  // 技师管理
  registerTech: (data: any) => request.post('/lbs/technician', data),
  listTechs: () => request.get('/lbs/technician/list'),
  getTech: (id: number) => request.get(`/lbs/technician/${id}`),
  updateTechLocation: (id: number, data: { lat: number; lng: number; source?: string }) =>
    request.post(`/lbs/technician/${id}/location`, data),
  updateTechStatus: (id: number, data: { onlineStatus: string }) =>
    request.post(`/lbs/technician/${id}/status`, data),
  getTechRoute: (id: number, date: string) =>
    request.get(`/lbs/technician/${id}/route`, { params: { date } }),
  generateTechRoute: (id: number, data: { date: string }) =>
    request.post(`/lbs/technician/${id}/route/generate`, data),

  // 技师技能
  addTechSkill: (techId: number, data: { skillCode: string; skillName?: string; skillLevel?: number }) =>
    request.post(`/lbs/technician/${techId}/skill`, data),
  removeTechSkill: (skillId: number) =>
    request.delete(`/lbs/technician/skill/${skillId}`),

  // 服务区域
  listZones: () => request.get('/lbs/zone/list'),
  createZone: (data: any) => request.post('/lbs/zone', data),
  updateZone: (id: number, data: any) => request.put(`/lbs/zone/${id}`, data),
  deleteZone: (id: number) => request.delete(`/lbs/zone/${id}`),

  // 派单
  dispatch: (data: any) => request.post('/lbs/order/dispatch', data),
  preview: (data: any) => request.post('/lbs/order/preview', data),
  batchDispatch: (data: { orderIds: number[]; autoAssign?: boolean }) =>
    request.post('/lbs/order/batch-dispatch', data),
  acceptOrder: (id: number, data?: any) => request.post(`/lbs/order/${id}/accept`, data || {}),
  updateOrderStatus: (id: number, data: { status: string }) =>
    request.post(`/lbs/order/${id}/status`, data),
  reassign: (id: number) => request.post(`/lbs/order/${id}/reassign`),
  listOrders: (params?: { status?: string }) => request.get('/lbs/order/list', { params }),

  // 派单日志
  dispatchLog: (params?: { orderId?: number; technicianId?: number; limit?: number }) =>
    request.get('/lbs/dispatch-log', { params }),

  // 派单配置
  getConfig: () => request.get('/lbs/dispatch-config'),
  updateConfig: (data: any) => request.put('/lbs/dispatch-config', data),

  // 调度看板
  dashboard: () => request.get('/lbs/dashboard'),
}
