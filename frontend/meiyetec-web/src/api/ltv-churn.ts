import request from './request'

/**
 * P2-16 LTV / Churn 真实数据算法验证（对应后端 LtvChurnService @ /ltv-churn）
 * 模块：LTV 计算 / Churn 流失预测 / Dashboard / 定时调度
 */
export const ltvChurnApi = {
  // LTV
  memberLtv: (memberId: number) => request.get(`/ltv-churn/ltv/member/${memberId}`),
  topLtv: (limit = 100) => request.get('/ltv-churn/ltv/top', { params: { limit } }),
  computeLtv: () => request.post('/ltv-churn/ltv/compute'),

  // Churn
  memberChurn: (memberId: number) => request.get(`/ltv-churn/churn/member/${memberId}`),
  churnRiskList: (limit = 100) => request.get('/ltv-churn/churn/risk-list', { params: { limit } }),
  computeChurn: () => request.post('/ltv-churn/churn/compute'),

  // 汇总
  dashboard: () => request.get('/ltv-churn/dashboard')
}