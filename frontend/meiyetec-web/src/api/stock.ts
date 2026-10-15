import request from './request'

// M08 库存管理 API
export const stockApi = {
  // 商品
  productList: (params: any) => request.get('/stock/products/list', { params }),
  productPage: (params: any) => request.get('/stock/products/page', { params }),
  productDetail: (id: number) => request.get(`/stock/products/${id}`),
  createProduct: (data: any) => request.post('/stock/products', data),
  updateProduct: (id: number, data: any) => request.put(`/stock/products/${id}`, data),
  deleteProduct: (id: number) => request.delete(`/stock/products/${id}`),

  // 库存导出（CSV，Excel 兼容）
  inventoryExport: (params: any) => request.get('/stock/inventory/export', { params, responseType: 'blob' }),

  // 库存
  inventoryPage: (params: any) => request.get('/stock/inventory/page', { params }),
  inventoryAlerts: (params: any) => request.get('/stock/inventory/alerts', { params }),

  // 入库/出库
  inbound: (data: any) => request.post('/stock/inbound', data),
  outbound: (data: any) => request.post('/stock/outbound', data),

  // 盘点
  check: (data: any) => request.post('/stock/check', data),
  checkPage: (params: any) => request.get('/stock/check/page', { params }),
  checkStats: (params: any) => request.get('/stock/check/stats', { params }),
  checkExport: (params: any) => request.get('/stock/check/export', { params, responseType: 'blob' }),
  checkDetail: (id: number) => request.get(`/stock/check/${id}`),
  checkConfirm: (id: number, data?: any) => request.post(`/stock/check/confirm/${id}`, data || {}),
  checkCancel: (id: number) => request.post(`/stock/check/cancel/${id}`),

  // 流水
  recordPage: (params: any) => request.get('/stock/record/page', { params }),

  // 消耗记录（美业特色：服务订单关联）
  consumptionPage: (params: any) => request.get('/stock/consumption/page', { params }),
  consumptionByOrder: (orderId: number) => request.get(`/stock/consumption/order/${orderId}`),
  consumptionOverview: (params: any) => request.get('/stock/consumption/overview', { params }),
  consumptionReplay: (orderId: number) => request.post(`/stock/consumption/replay/${orderId}`, {}),
  consumptionBom: (serviceId: number) => request.get(`/stock/consumption/bom/${serviceId}`),
  consumptionBomByProduct: (productId: number) => request.get(`/stock/consumption/bom-product/${productId}`),
  consumptionExport: (params: any) => request.get('/stock/consumption/export', { params, responseType: 'blob' }),

  // 库存预警
  alertPage: (params: any) => request.get('/stock/alert/page', { params }),
  alertStats: (params: any) => request.get('/stock/alert/stats', { params }),
  alertReplenish: (params: any) => request.get('/stock/alert/replenish', { params }),

  // 库存统计
  stats: (params: any) => request.get('/stock/stats', { params }),
  statsTrend: (params: any) => request.get('/stock/stats/trend', { params }),
  statsRanking: (params: any) => request.get('/stock/stats/ranking', { params }),

  // 商品批量导入（R2.0-D）
  productImport: (data: any, idemKey: string) =>
    request.post('/stock/products/import', data, { idempotencyKey: idemKey } as any),
  productImportTemplate: () => request.get('/stock/products/import-template', { responseType: 'blob' }),

  // ============================================================
  // P2-11 IND-05 双人核销 API
  // 后端：StockDualVerifyController
  // ============================================================
  dualVerify: (params: {
    dualVerifyId: number
    action?: 'VERIFY' | 'REJECT'
    reviewerId: number
    reviewerName: string
    reviewerGps?: string
    remark?: string
  }) => request.post('/stock/dual-verify', null, { params }),
  dualVerifyPage: (params: any) => request.get('/stock/dual-verify/page', { params }),
  dualVerifyPending: () => request.get('/stock/dual-verify/pending'),
  dualVerifyThreshold: () => request.get('/stock/dual-verify/threshold'),
  dualVerifyByCheck: (checkId: number) => request.get(`/stock/dual-verify/check/${checkId}`),
  dualVerifyByRecord: (recordId: number) => request.get(`/stock/dual-verify/record/${recordId}`)
}

// ============================================================
// M12-02 跨店库存统一调度 API
// 对应后端：CrossStoreInventoryService.java
// ============================================================
export const crossStoreApi = {
  // ---- 调度仪表盘 ----
  dashboard: () => request.get('/wms/cross-store/dashboard'),

  // ---- 跨店聚合视图 ----
  inventoryAggregate: (params?: {
    productId?: number
    keyword?: string
    alertOnly?: boolean | string
    limit?: number
  }) => request.get('/wms/cross-store/inventory/aggregate', { params }),

  // ---- 智能调度 ----
  scheduleScan: (data?: any) => request.post('/wms/cross-store/schedule/scan', data || {}),

  // ---- 调度建议 ----
  suggestionPage: (params: {
    pageNum?: number
    pageSize?: number
    accepted?: number
    priority?: string
  }) => request.get('/wms/cross-store/suggestion/page', { params }),
  acceptSuggestion: (id: number, data?: any) =>
    request.post(`/wms/cross-store/suggestion/${id}/accept`, data || {}),

  // ---- 调拨单工作流 ----
  createTransfer: (data: any) => request.post('/wms/cross-store/transfer', data),
  transferPage: (params: {
    pageNum?: number
    pageSize?: number
    status?: string
    priority?: string
    keyword?: string
  }) => request.get('/wms/cross-store/transfer/page', { params }),
  transferDetail: (id: number) => request.get(`/wms/cross-store/transfer/${id}`),
  approveTransfer: (id: number, data?: any) =>
    request.post(`/wms/cross-store/transfer/${id}/approve`, data || {}),
  shipTransfer: (id: number) => request.post(`/wms/cross-store/transfer/${id}/ship`),
  receiveTransfer: (id: number, data?: any) =>
    request.post(`/wms/cross-store/transfer/${id}/receive`, data || {}),
  cancelTransfer: (id: number, data?: any) =>
    request.post(`/wms/cross-store/transfer/${id}/cancel`, data || {}),

  // ---- 辅助 ----
  storeList: () => request.get('/wms/cross-store/store/list'),
  productList: (params?: { keyword?: string }) =>
    request.get('/wms/cross-store/product/list', { params })
}

export default stockApi