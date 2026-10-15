import request from './request'

/**
 * P3-26 国际预约 / 海外预约（后端 IntlBookingService @ /intl-booking）
 *
 * <p>改造点（P3-26）：
 * <ul>
 *   <li>OAuth2 授权流：connectGoogle / connectInstagram 现在返回 authUrl，前端跳转授权</li>
 *   <li>callback 端点接收 OAuth code 换 token</li>
 *   <li>Webhook 端点用于接收 Google AppointmentNotification / Instagram messaging 推送</li>
 *   <li>convert 用于把预约转为本地会员 + 订单</li>
 *   <li>confirmation-template 根据 customerLocale 返回多语言确认模板</li>
 * </ul>
 */
export const intlBookingApi = {
  // OAuth2 授权
  connectGoogle: (data: { clientId: string; clientSecret: string }) =>
    request.post('/intl-booking/google/connect', data),
  googleCallback: (data: { code: string; clientId?: string; clientSecret?: string }) =>
    request.post('/intl-booking/google/callback', data),
  connectInstagram: (data: { appId: string; appSecret: string }) =>
    request.post('/intl-booking/instagram/connect', data),
  instagramCallback: (data: { code: string }) =>
    request.post('/intl-booking/instagram/callback', data),

  // 渠道列表
  listChannels: () => request.get('/intl-booking/channels'),

  // 预约拉取（演示模式）
  pullBookings: (channelId: number) => request.post(`/intl-booking/${channelId}/pull`),

  // 预约 CRUD
  listBookings: (params?: { status?: string; source?: string }) =>
    request.get('/intl-booking/list', { params }),
  getBooking: (id: number) => request.get(`/intl-booking/${id}`),
  updateStatus: (id: number, data: { status: string }) =>
    request.post(`/intl-booking/${id}/status`, data),

  // 转化
  convertBooking: (id: number) => request.post(`/intl-booking/${id}/convert`),

  // 多语言确认模板
  confirmationTemplate: (id: number) => request.get(`/intl-booking/${id}/confirmation-template`)
}
