import request from './request'

/**
 * 补齐-P0-1 AI 智能排班
 */
export const aiSchedulerApi = {
  // 客流预测
  forecastTraffic: (data: { storeId: number; date: string }) => request.post('/ai-scheduler/forecast', data),
  listForecasts: () => request.get('/ai-scheduler/forecasts'),
  // 排班生成
  generateSchedule: (data: { storeId: number; weekStart: string }) => request.post('/ai-scheduler/generate', data),
  listSchedules: () => request.get('/ai-scheduler/schedules'),
  // 排班人员负载
  getStaffLoad: () => request.get('/ai-scheduler/staff-load')
}