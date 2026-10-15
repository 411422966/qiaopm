import request from './request'

/**
 * P1-7 礼品卡（对应后端 GiftCardService @ /gift-card）
 * 模块：购买 / 转赠 / 核销 / 退款 / 流水
 */
export const giftCardApi = {
  purchase: (data: any) => request.post('/gift-card/purchase', data),
  gift: (id: number, data: any) => request.post(`/gift-card/${id}/gift`, data),
  redeem: (data: { cardNo: string; amount: number; orderId?: number; operatorId?: number }) =>
    request.post('/gift-card/redeem', data),
  refund: (id: number) => request.post(`/gift-card/${id}/refund`),
  list: (params?: { status?: string; phone?: string }) =>
    request.get('/gift-card/list', { params }),
  byCardNo: (cardNo: string) => request.get(`/gift-card/by-card-no/${cardNo}`),
  transactions: (id: number) => request.get(`/gift-card/${id}/transactions`)
}