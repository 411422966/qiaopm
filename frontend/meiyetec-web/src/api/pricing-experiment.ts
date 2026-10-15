import request from './request'

/**
 * M14 动态定价 A/B 实验 API
 * 对应后端 PricingExperimentController @ /mkt/pricing/experiment/*
 */
export const pricingExperimentApi = {
  page: (params: { pageNum?: number; pageSize?: number; status?: string; keyword?: string }) =>
    request.get('/mkt/pricing/experiment/page', { params }),

  get: (id: number) =>
    request.get(`/mkt/pricing/experiment/${id}`),

  create: (data: any) =>
    request.post('/mkt/pricing/experiment', data),

  update: (id: number, data: any) =>
    request.put(`/mkt/pricing/experiment/${id}`, data),

  start: (id: number) =>
    request.post(`/mkt/pricing/experiment/${id}/start`),

  pause: (id: number) =>
    request.post(`/mkt/pricing/experiment/${id}/pause`),

  complete: (id: number) =>
    request.post(`/mkt/pricing/experiment/${id}/complete`),

  stats: (id: number) =>
    request.get(`/mkt/pricing/experiment/${id}/stats`),

  distribution: (id: number) =>
    request.get(`/mkt/pricing/experiment/${id}/distribution`),

  recompute: (id: number) =>
    request.post(`/mkt/pricing/experiment/${id}/recompute`)
}

export default pricingExperimentApi
