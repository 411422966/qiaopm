import http from '@/utils/http'

/**
 * 优惠券 API（市场模块）
 *  - 模板：/market/coupon/*（CouponController）
 *  - 批次 + 实例：/mkt/coupon/*（MktCouponController）
 */
export const couponApi = {
  // ==================== 模板 ====================
  page: (params: any) => http.get('/market/coupon/page', { params }),
  detail: (id: number) => http.get(`/market/coupon/${id}`),
  create: (data: any) => http.post('/market/coupon', data),
  update: (id: number, data: any) => http.put(`/market/coupon/${id}`, data),
  remove: (id: number) => http.delete(`/market/coupon/${id}`),

  // 发放
  grant: (couponId: number, memberId: number, source = 1) =>
    http.post('/market/coupon/grant', null, { params: { couponId, memberId, source } }),
  batchGrantByLevel: (data: any) => http.post('/market/coupon/batch-grant-by-level', data),

  // 核销
  use: (grantId: number, orderId?: number) =>
    http.post('/market/coupon/use', null, { params: { grantId, orderId } }),

  // 会员相关
  memberCoupons: (memberId: number, params: any = {}) => http.get(`/market/coupon/member/${memberId}`, { params }),
  memberCount: (memberId: number) => http.get(`/market/coupon/member/${memberId}/count`),

  // ==================== 批次 + 实例（/mkt/coupon）====================
  batchPage: (data: any) => http.post('/mkt/coupon/batch/page', data),
  createBatch: (data: any) => http.post('/mkt/coupon/batch', data),
  publishBatch: (id: number) => http.post(`/mkt/coupon/batch/${id}/publish`),
  endBatch: (id: number) => http.post(`/mkt/coupon/batch/${id}/end`),
  grantBatch: (id: number, data: any) => http.post(`/mkt/coupon/batch/${id}/grant`, data),
  expireSweep: () => http.post('/mkt/coupon/expire-sweep'),
  export: (params: any = {}) => http.get('/mkt/coupon/export', { params })
}

export default couponApi