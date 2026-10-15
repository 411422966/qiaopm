import request from './request'

export interface ReportConfig {
  id?: number
  storeId?: number
  name: string
  type: number
  dimension?: string
  dateRange?: string
  startDate?: string
  endDate?: string
  isPinned?: number
  sortOrder?: number
  createdBy?: number
}

export interface ReportExportLog {
  id?: number
  storeId?: number
  name: string
  type: number
  dateRange?: string
  fileUrl?: string
  fileSize?: number
  rowCount?: number
  status?: number
  errorMsg?: string
  exportBy?: number
  finishedAt?: string
  createdAt?: string
}

export const reportApi = {
  // 看板
  kpi: (storeId?: number) => request.get('/report/dashboard/kpi', { params: { storeId } }),
  /** P1-6 耗材差异率详情：POS 实耗 vs 系统出库 */
  consumablesVariance: (storeId?: number, days = 30) =>
    request.get('/report/dashboard/consumables-variance', { params: { storeId, days } }),
  /** P1-6 库存周转月趋势（近 12 个月） */
  inventoryTurnoverTrend: (storeId?: number, months = 12) =>
    request.get('/report/dashboard/inventory-turnover-trend', { params: { storeId, months } }),

  // 营业
  bizDaily: (days = 30, storeId?: number) =>
    request.get('/report/biz/daily', { params: { days, storeId } }),
  /** 首页趋势图：日报数据 */
  salesDaily: (days = 30, storeId?: number) =>
    request.get('/report/biz/daily', { params: { days, storeId } }),
  bizMonthly: (months = 12, storeId?: number) =>
    request.get('/report/biz/monthly', { params: { months, storeId } }),
  payType: (days = 30, storeId?: number) =>
    request.get('/report/biz/payType', { params: { days, storeId } }),

  // 会员分析
  memberAnalysis: (storeId?: number) =>
    request.get('/report/member/analysis', { params: { storeId } }),

  // 员工绩效
  employeePerformance: (days = 30, storeId?: number) =>
    request.get('/report/employee/performance', { params: { days, storeId } }),

  // 配置
  configList: (storeId?: number, userId?: number) =>
    request.get('/report/config/list', { params: { storeId, userId } }),
  configSave: (data: ReportConfig) => request.post('/report/config', data),
  configDelete: (id: number) => request.delete(`/report/config/${id}`),

  // 导出
  exportList: (pageNum = 1, pageSize = 20, type?: number, status?: number) =>
    request.get('/report/export/list', { params: { pageNum, pageSize, type, status } }),
  exportCreate: (data: { name: string; type: number; dateRange: string }) =>
    request.post('/report/export/create', data),
  exportDownload: (id: number) => `/report/export/download?id=${id}`,
  exportDownloadUrl: (type: number, dateRange = 'last_30_days') =>
    `/report/export/download?type=${type}&dateRange=${dateRange}`
}

export default reportApi
