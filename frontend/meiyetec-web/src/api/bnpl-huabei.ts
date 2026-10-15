import request from './request'

/**
 * BNPL & 花呗分期 API
 * 后端：
 *   BnplController   → /bnpl/*
 *   HuabeiController  → /huabei/*
 */
export const bnplHuabeiApi = {
  // ==================== BNPL ====================
  bnpl: {
    credit: (provider: string, phone: string) =>
      request.get<any>('/bnpl/credit', { params: { provider, phone } }),
    createOrder: (data: {
      memberId: number
      provider: string
      bizOrderNo: string
      amount: number
      currency?: string
      installments?: number
    }) => request.post('/bnpl/order', null, { params: data }),
    listOrders: (params?: {
      provider?: string
      memberId?: number
      status?: number
      current?: number
      size?: number
    }) => request.get<any>('/bnpl/orders', { params }),
    cancel: (id: number) =>
      request.post<any>(`/bnpl/order/${id}/cancel`),
    statistics: (params?: { startDate?: string; endDate?: string }) =>
      request.get<any>('/bnpl/statistics', { params: params || {} }),
    simulateNotify: (orderId: number, eventType = 'order.confirmed') =>
      request.post('/bnpl/notify/simulate', null, { params: { orderId, eventType } })
  },

  // ==================== 花呗分期 ====================
  huabei: {
    credit: (memberId: number, phone: string) =>
      request.get<any>('/huabei/credit', { params: { memberId, phone } }),
    createOrder: (data: {
      memberId: number
      bizOrderNo: string
      amount: number
      period?: number
    }) => request.post('/huabei/order', null, { params: data }),
    listOrders: (params?: {
      memberId?: number
      status?: number
      current?: number
      size?: number
    }) => request.get<any>('/huabei/orders', { params }),
    cancel: (id: number) =>
      request.post<any>(`/huabei/order/${id}/cancel`),
    simulateNotify: (orderId: number, status = 'paid') =>
      request.post('/huabei/notify/simulate', null, { params: { orderId, status } })
  }
}
