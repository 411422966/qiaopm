import request from './request'

/**
 * P1-12 并发症 SOP 医美（后端 ComplicationService @ /complication）
 */
export const complicationApi = {
  // SOP 模板
  createSop: (data: any) => request.post('/complication/sop', data),
  listSops: () => request.get('/complication/sop/list'),
  initDefaultSops: () => request.post('/complication/sop/init-default'),

  // 事件
  createEvent: (data: any) => request.post('/complication/event', data),
  listEvents: (params?: { status?: string; severity?: string }) =>
    request.get('/complication/event/list', { params }),
  updateEventStatus: (id: number, data: { status: string; resolution?: string }) =>
    request.post(`/complication/event/${id}/status`, data),
  dashboard: () => request.get('/complication/dashboard')
}