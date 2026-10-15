import request from './request'

export interface CommissionRule {
  id?: number
  storeId: number
  ruleName: string
  businessType: 'SERVICE' | 'PRODUCT' | 'CARD' | 'RECHARGE'
  targetId?: number | null
  calculationMethod: 'RATE' | 'FIXED'
  rate?: number | null
  fixedAmount?: number | null
  priority: number
  effectiveDate: string
  expiryDate?: string | null
  status: number
  remark?: string
}

export const commissionApi = {
  rulePage: (params: Record<string, unknown>) => request.get('/commission/rules/page', { params }),
  ruleList: (params?: Record<string, unknown>) => request.get('/commission/rules/list', { params }),
  createRule: (data: CommissionRule) => request.post('/commission/rules', data),
  updateRule: (id: number, data: CommissionRule) => request.put(`/commission/rules/${id}`, data),
  deleteRule: (id: number) => request.delete(`/commission/rules/${id}`),
  toggleRuleStatus: (id: number) => request.put(`/commission/rules/${id}/toggle`),
  calculateOrder: (orderId: number) => request.post(`/commission/calculate/order/${orderId}`),
  recordPage: (params: Record<string, unknown>) => request.get('/commission/records/page', { params }),
  recordStats: (params: Record<string, unknown>) => request.get('/commission/records/stats', { params }),
  recordExport: (params: Record<string, unknown>) => request.get('/commission/records/export', { params, responseType: 'blob' }),
  recordBatchSettle: (ids: number[]) => request.put('/commission/records/batch/settle', { ids }),
  recordBatchInvalidate: (ids: number[]) => request.put('/commission/records/batch/invalidate', { ids }),
  generateSalary: (data: { storeId: number; salaryMonth: string }) => request.post('/commission/salary/generate', data),
  salaryPage: (params: Record<string, unknown>) => request.get('/commission/salary/page', { params }),
  salaryStats: (params: Record<string, unknown>) => request.get('/commission/salary/stats', { params }),
  salaryExport: (params: Record<string, unknown>) => request.get('/commission/salary/export', { params, responseType: 'blob' }),
  salaryBatchConfirm: (ids: number[]) => request.put('/commission/salary/batch/confirm', { ids }),
  salaryBatchPay: (ids: number[]) => request.put('/commission/salary/batch/pay', { ids }),
  updateAdjustment: (id: number, data: { adjustmentAmount: number; remark?: string }) => request.put(`/commission/salary/${id}/adjustment`, data),
  confirmSalary: (id: number) => request.post(`/commission/salary/${id}/confirm`),
  paySalary: (id: number) => request.post(`/commission/salary/${id}/pay`),
  // M09-C 结算管理
  generateSettlement: (data: { storeId: number; salaryMonth: string }) => request.post('/commission/settlement/generate', data),
  settlementPage: (params: Record<string, unknown>) => request.get('/commission/settlement/page', { params }),
  settlementDetail: (id: number) => request.get(`/commission/settlement/${id}`),
  settlementStats: (params: Record<string, unknown>) => request.get('/commission/settlement/stats', { params }),
  settlementExport: (params: Record<string, unknown>) => request.get('/commission/settlement/export', { params, responseType: 'blob' }),
  confirmSettlement: (id: number) => request.put(`/commission/settlement/${id}/confirm`),
  paySettlement: (id: number) => request.put(`/commission/settlement/${id}/pay`),
  settlementBatchConfirm: (ids: number[]) => request.put('/commission/settlement/batch/confirm', { ids }),
  settlementBatchPay: (ids: number[]) => request.put('/commission/settlement/batch/pay', { ids })
}

export default commissionApi
