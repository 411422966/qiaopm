import http from '@/utils/http'

/**
 * 积分 API
 *  - 旧积分：/market/point/*（PointController，market_point_record）
 *  - 新积分：/mkt/points/*（MktPointsController，mkt_points_rule + mkt_points_log）
 *  - 积分商城：/market/point/goods/*（PointGoodsController，point_goods）
 */
export const pointApi = {
  // 旧积分（member 卡片余额 + 流水）
  get: (memberId: number) => http.get(`/market/point/${memberId}`),
  records: (memberId: number, params: any = {}) =>
    http.get(`/market/point/${memberId}/records`, { params }),
  add: (data: { memberId: number; source: string; points: number; refId?: number; remark?: string }) =>
    http.post('/market/point/add', data),
  consume: (data: { memberId: number; source: string; points: number; refId?: number; remark?: string }) =>
    http.post('/market/point/consume', data),

  // 积分规则 + 手动发分
  ruleList: () => http.get('/mkt/points/rule/list'),
  createRule: (data: any) => http.post('/mkt/points/rule', data),
  updateRule: (id: number, data: any) => http.put(`/mkt/points/rule/${id}`, data),
  award: (data: { memberId: number; amount: number; sourceType: string; remark?: string }) =>
    http.post('/mkt/points/award', data),

  // 积分商城
  goodsPage: (params: any = {}) => http.get('/market/point/goods/page', { params }),
  goodsList: (params: any = {}) => http.get('/market/point/goods/list', { params }),
  createGoods: (data: any) => http.post('/market/point/goods', data),
  updateGoods: (id: number, data: any) => http.put(`/market/point/goods/${id}`, data),
  removeGoods: (id: number) => http.delete(`/market/point/goods/${id}`),
  redeem: (goodsId: number, data: { memberId: number; remark?: string }) =>
    http.post(`/market/point/goods/${goodsId}/redeem`, data)
}

export default pointApi