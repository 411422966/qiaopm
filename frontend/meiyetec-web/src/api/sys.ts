import request from './request'

export interface SysConfig {
  id?: number
  configKey: string
  configValue: string
  configGroup?: string
  description?: string
  sortOrder?: number
}

export interface SysTenant {
  id?: number
  tenantCode: string
  tenantName: string
  contactName?: string
  contactPhone?: string
  address?: string
  logoUrl?: string
  status?: number
  expireAt?: string
}

export interface SysLog {
  id?: number
  userId?: number
  username?: string
  module?: string
  action?: string
  description?: string
  requestUrl?: string
  requestMethod?: string
  requestParams?: string
  ip?: string
  userAgent?: string
  costMs?: number
  status?: number
  errorMsg?: string
  createdAt?: string
}

export interface SysLogExportQuery {
  username?: string
  module?: string
  action?: string
  status?: number
  startTime?: string
  endTime?: string
}

export interface DcepConfig {
  id?: number
  merchantId: string
  merchantName?: string
  appId?: string
  apiKey?: string
  privateKey?: string
  publicKey?: string
  notifyUrl?: string
  environment?: 'sandbox' | 'production'
  enabled?: number
  description?: string
  updatedAt?: string
  createdAt?: string
}

export interface DcepQualification {
  id?: number
  configId?: number
  legalPerson?: string
  legalIdNo?: string
  legalIdFront?: string
  legalIdBack?: string
  businessLicenseNo?: string
  businessLicenseImg?: string
  orgCode?: string
  taxNo?: string
  bankAccountName?: string
  bankAccountNo?: string
  bankName?: string
  bankBranch?: string
  contactPerson?: string
  contactPhone?: string
  contactEmail?: string
  address?: string
  remark?: string
  auditStatus?: number
  auditRemark?: string
  auditAt?: string
  createdAt?: string
  updatedAt?: string
  attachments?: DcepQualificationAttachment[]
}

export interface DcepQualificationAttachment {
  id?: number
  materialType?: string
  fileName?: string
  fileSize?: number
  contentType?: string
  url?: string
  sortNo?: number
  createdAt?: string
}

export const sysApi = {
  // 配置
  configList: (group?: string) => request.get('/sys/config/list', { params: { group } }),
  configGrouped: () => request.get('/sys/config/grouped'),
  configCreate: (data: SysConfig) => request.post('/sys/config', data),
  configUpdate: (data: SysConfig) => request.put('/sys/config', data),
  configDelete: (id: number) => request.delete(`/sys/config/${id}`),
  configBatch: (data: SysConfig[]) => request.put('/sys/config/batch', data),
  // 租户
  tenantPage: (pageNum: number, pageSize: number, keyword?: string) =>
    request.get('/sys/tenant/page', { params: { pageNum, pageSize, keyword } }),
  tenantCreate: (data: SysTenant) => request.post('/sys/tenant', data),
  tenantUpdate: (data: SysTenant) => request.put('/sys/tenant', data),
  tenantDelete: (id: number) => request.delete(`/sys/tenant/${id}`),
  tenantStatus: (id: number, status: number) => request.put(`/sys/tenant/${id}/status/${status}`),
  tenantStats: (id: number) => request.get(`/sys/tenant/${id}/stats`),
  tenantConfig: (id: number) => request.get(`/sys/tenant/${id}/config`),
  tenantConfigSave: (id: number, configs: Record<string, string>) =>
    request.put(`/sys/tenant/${id}/config`, configs),
  // 日志
  logPage: (pageNum: number, pageSize: number, params: any = {}) =>
    request.get('/sys/log/page', { params: { pageNum, pageSize, ...params } }),
  logDelete: (id: number) => request.delete(`/sys/log/${id}`),
  logClear: (days: number) => request.delete('/sys/log/clear', { data: { days } }),
  logExportUrl: (params: SysLogExportQuery) => {
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') q.append(k, String(v))
    })
    return `/sys/log/export?${q.toString()}`
  },
  logStats: () => request.get('/sys/log/stats'),
  logTrend: (days = 7) => request.get('/sys/log/trend', { params: { days } }),
  logAnomaly: (days = 7, top = 5) => request.get('/sys/log/anomaly', { params: { days, top } }),
  logEnums: () => request.get('/sys/log/enums'),
  logContext: (id: number, minutes = 10, limit = 50) =>
    request.get('/sys/log/context', { params: { id, minutes, limit } }),
  logSession: (id: number, minutes = 30, limit = 50) =>
    request.get('/sys/log/session', { params: { id, minutes, limit } }),
  logSearch: (params: { q?: string; status?: number; startTime?: string; endTime?: string; pageNum?: number; pageSize?: number }) =>
    request.get('/sys/log/search', { params }),

  // 登录日志
  loginLogPage: (pageNum = 1, pageSize = 20, params: any = {}) =>
    request.get('/sys/login-log/page', { params: { pageNum, pageSize, ...params } }),
  loginLogStats: (days = 30) => request.get('/sys/login-log/stats', { params: { days } }),
  loginLogTrend: (days = 7) => request.get('/sys/login-log/trend', { params: { days } }),
  loginLogAnomaly: (days = 7, top = 5) =>
    request.get('/sys/login-log/anomaly', { params: { days, top } }),
  loginLogSearch: (params: any = {}) => request.get('/sys/login-log/search', { params }),
  loginLogDelete: (id: number) => request.delete(`/sys/login-log/${id}`),
  loginLogClear: (days: number) => request.post('/sys/login-log/clear', null, { params: { days } }),
  loginLogExportUrl: () => '/sys/login-log/export',
  loginLogClearUrl: () => '/sys/login-log/clear',
  loginLogDeleteUrl: (id: number) => `/sys/login-log/${id}`,
  // 日志保留
  logRetentionGet: () => request.get('/sys/log-retention'),
  logRetentionUpdate: (data: { days: number }) => request.put('/sys/log-retention', data),
  // 数字人民币
  dcepCurrent: () => request.get('/sys/dcep/current'),
  dcepSave: (data: DcepConfig) => request.post('/sys/dcep', data),
  dcepToggle: (id: number, enabled: number) => request.put(`/sys/dcep/${id}/toggle/${enabled}`),
  dcepTest: (override?: any) => request.post('/sys/dcep/test', override || {}),
  // 数字人民币 - 商户资质材料
  dcepQualCurrent: () => request.get('/sys/dcep/qualification/current'),
  dcepQualSave: (data: DcepQualification) => request.post('/sys/dcep/qualification', data),
  dcepQualUpload: (qualificationId: number | null, materialType: string, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('materialType', materialType)
    if (qualificationId) fd.append('qualificationId', String(qualificationId))
    return request.post('/sys/dcep/qualification/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  dcepQualDeleteAttachment: (id: number) => request.delete(`/sys/dcep/qualification/attachment/${id}`),
  dcepQualSubmit: () => request.post('/sys/dcep/qualification/submit'),
  // 灰度发布
  grayFlagList: () => request.get('/gray/flag/list'),
  grayFlagCreate: (data: any) => request.post('/gray/flag', data),
  grayFlagToggle: (key: string, data: any) => request.post(`/gray/flag/${key}/toggle`, data),
  grayFlagEvaluate: (key: string, userId: number) => request.get(`/gray/flag/${key}/evaluate`, { params: { userId } }),
  grayExpStats: (key: string) => request.get('/gray/experiment/stats', { params: { key } }),
  // 全链路追踪
  traceChain: (traceId: string) => request.get(`/trace/chain/${traceId}`),
  traceChainList: (params: any) => request.get('/trace/chain/list', { params }),
  traceVisual: (traceId: string) => request.get(`/trace/visual/${traceId}`),
  traceSlow: (limit?: number) => request.get('/trace/slow/top', { params: { limit: limit || 10 } }),
  traceError: (limit?: number) => request.get('/trace/error/top', { params: { limit: limit || 10 } }),
  // 数据治理
  govDiagnosis: () => request.get('/governance/quality/diagnosis'),
  govClean: (data: any) => request.post('/governance/clean', data),
  govLtvValidate: () => request.get('/governance/model/ltv/validate'),
  govChurnValidate: () => request.get('/governance/model/churn/validate'),
  govArchive: (data: any) => request.post('/governance/archive', data),
  // 压测
  perfStaircase: (data?: any) => request.post('/perf/v2/load/staircase', data || {}),
  perfModelList: () => request.get('/perf/v2/model/list'),
  perfReportList: (limit?: number) => request.get('/perf/v2/report/list', { params: { limit: limit || 20 } }),
  perfSlowTop: (limit?: number) => request.get('/perf/v2/slow/top', { params: { limit: limit || 10 } }),
  // 客流预测
  forecastCurve: (historyDays = 14, forecastDays = 14) =>
    request.get('/ai/forecast/curve', { params: { historyDays, forecastDays } }),
  forecastHeatmap: (windowDays = 30) =>
    request.get('/ai/forecast/heatmap', { params: { windowDays } }),
  forecastRunEtl: (data: any) => request.post('/ai/forecast/etl/run', data),
  forecastScheduleList: (days = 7) =>
    request.get('/ai/forecast/schedule/recommendations', { params: { days } }),
  forecastAccuracyTrend: (limit = 10) =>
    request.get('/ai/forecast/accuracy/trend', { params: { limit } }),
  // 模型 A/B 验证
  modelAbValidate: (modelType = 'LTV', windowDays = 90) =>
    request.post('/governance/model/ab/validate', { modelType, windowDays }),
  modelReport: () => request.get('/governance/model/report'),
  modelBatchPredict: (modelType = 'LTV', limit = 10000) =>
    request.post('/governance/model/batch-predict', { modelType, limit })
}