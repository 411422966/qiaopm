import request from './request'

export interface Coupon {
  id?: number
  storeId?: number
  name: string
  type: number
  ruleType: number
  ruleValue: number
  minAmount?: number
  maxDiscount?: number
  totalCount: number
  perLimit?: number
  startTime: string
  endTime: string
  status: number
  remark?: string
}

export interface PointRecord {
  id?: number
  memberId: number
  type: number
  pointValue: number
  balanceBefore: number
  balanceAfter: number
  source: string
  sourceText?: string
  remark?: string
  createdAt: string
}

export interface MarketActivity {
  id?: number
  storeId?: number
  name: string
  type: number
  ruleConfig?: string
  startTime: string
  endTime: string
  status: number
}

export const marketApi = {
  // 优惠券
  couponPage: (params: Record<string, unknown>) => request.get('/market/coupon/page', { params }),
  couponGet: (id: number) => request.get(`/market/coupon/${id}`),
  couponCreate: (data: Coupon) => request.post('/market/coupon', data),
  couponUpdate: (id: number, data: Coupon) => request.put(`/market/coupon/${id}`, data),
  couponDelete: (id: number) => request.delete(`/market/coupon/${id}`),
  couponGrant: (couponId: number, memberId: number, source?: number) =>
    request.post('/market/coupon/grant', null, { params: { couponId, memberId, source } }),
  couponUse: (grantId: number, orderId?: number) =>
    request.post('/market/coupon/use', null, { params: { grantId, orderId } }),
  memberCoupons: (memberId: number, status?: number, pageNum = 1, pageSize = 10) =>
    request.get(`/market/coupon/member/${memberId}`, { params: { status, pageNum, pageSize } }),
  memberCouponCount: (memberId: number) => request.get(`/market/coupon/member/${memberId}/count`),

  // 积分
  memberPoints: (memberId: number) => request.get(`/market/point/${memberId}`),
  pointRecords: (memberId: number, pageNum = 1, pageSize = 10) =>
    request.get(`/market/point/${memberId}/records`, { params: { pageNum, pageSize } }),
  addPoints: (data: { memberId: number; source: string; points: number; refId?: number; remark?: string }) =>
    request.post('/market/point/add', data),
  consumePoints: (data: { memberId: number; source: string; points: number; refId?: number; remark?: string }) =>
    request.post('/market/point/consume', data),

  // 营销活动
  activityPage: (params: Record<string, unknown>) => request.get('/market/activity/page', { params }),
  activityGet: (id: number) => request.get(`/market/activity/${id}`),
  activityCreate: (data: MarketActivity) => request.post('/market/activity', data),
  activityUpdate: (id: number, data: MarketActivity) => request.put(`/market/activity/${id}`, data),
  activityDelete: (id: number) => request.delete(`/market/activity/${id}`),
  activityStats: () => request.get('/market/activity/stats'),
  activityDetail: (id: number) => request.get(`/market/activity/${id}/detail`)
}

export default marketApi
