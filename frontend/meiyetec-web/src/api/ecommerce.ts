import request from './request'

/**
 * P3-25 电商集成 微店/有赞（后端 EcommerceService @ /ecommerce）
 */
export const ecommerceApi = {
  // 平台管理
  createPlatform: (data: any) => request.post('/ecommerce/platform', data),
  listPlatforms: () => request.get('/ecommerce/platform/list'),
  updatePlatform: (id: number, data: any) => request.post(`/ecommerce/platform/${id}`, data),

  // 商品
  listProducts: (params?: { platformId?: number }) =>
    request.get('/ecommerce/product/list', { params }),
  syncProducts: (platformId: number) => request.post(`/ecommerce/product/sync/${platformId}`),

  // 订单
  listOrders: (params?: { platformId?: number; status?: string }) =>
    request.get('/ecommerce/order/list', { params }),
  syncOrders: (platformId: number) => request.post(`/ecommerce/order/sync/${platformId}`),

  // Dashboard
  dashboard: () => request.get('/ecommerce/dashboard')
}