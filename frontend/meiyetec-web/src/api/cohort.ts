import request from './request'

/**
 * P1-14 Cohort 队列分析（后端 CohortService @ /cohort）
 */
export const cohortApi = {
  // 队列定义
  createDefinition: (data: any) => request.post('/cohort/definition', data),
  listDefinitions: () => request.get('/cohort/definition/list'),
  updateDefinition: (id: number, data: any) => request.post(`/cohort/definition/${id}`, data),

  // 分析
  analyze: (cohortId: number) => request.post(`/cohort/analyze/${cohortId}`),
  listResults: (cohortId: number) => request.get(`/cohort/result/${cohortId}`),
  dashboard: () => request.get('/cohort/dashboard')
}