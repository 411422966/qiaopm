import request from './request'

// ================================================================
// M09-02 生日提醒 API
// 对应后端：BirthdayReminderService.java
// ================================================================

export const birthdayApi = {
  // ---- 生日录入 ----
  /** 录入/更新会员生日 */
  register: (data: {
    memberId: number
    birthday: string         // yyyy-MM-dd
    birthdayType?: string    // SOLAR / LUNAR
    autoSend?: boolean      // 是否自动发送
    autoCoupon?: string     // 优惠券ID（自动发券时）
    autoCouponAmount?: number
    vipLevel?: number
    tags?: string
  }) => request.post('/birthday/member/register', data),

  /** 更新生日信息 */
  update: (memberId: number, data: Record<string, any>) =>
    request.put(`/birthday/member/${memberId}`, data),

  /** 查询已录入生日的会员列表 */
  listMembers: (params?: { autoSendOnly?: boolean; daysUntilBirthday?: number }) =>
    request.get('/birthday/member/list', { params }),

  /** 获取单个会员生日信息 */
  getMember: (memberId: number) =>
    request.get(`/birthday/member/${memberId}`),

  // ---- 即将到来的生日 ----
  /** 未来 N 天内生日的会员（默认 7 天）*/
  upcoming: (days = 7) => request.get('/birthday/upcoming', { params: { days } }),

  /** 预览发送效果 */
  preview: (days = 7) => request.get('/birthday/preview', { params: { days } }),

  // ---- 发送 ----
  /** 手动触发生日礼发放（真实通道）*/
  send: (data: {
    memberId: number
    giftType?: 'COUPON' | 'POINTS' | 'GIFT' | 'SMS_ONLY'
    couponId?: number
    points?: number
    channelType?: string   // SMS / MINI_PROGRAM / IN_APP（多选逗号分隔）
    templateId?: number
  }) => request.post('/birthday/send', data),

  /** 批量触发生日礼发放 */
  sendBatch: (data: {
    memberIds?: number[]
    daysBefore?: number
    giftType?: 'COUPON' | 'POINTS' | 'GIFT' | 'SMS_ONLY'
    couponId?: number
    channelType?: string
  }) => request.post('/birthday/send/batch', data),

  // ---- 发送记录 ----
  /** 发送记录列表 */
  logs: (params?: { limit?: number; year?: number; status?: string; giftType?: string }) =>
    request.get('/birthday/log/list', { params }),

  /** 发送统计 */
  logStat: (year?: number) =>
    request.get('/birthday/log/stat', { params: { year } }),

  // ---- 仪表盘 ----
  dashboard: () => request.get('/birthday/dashboard')
}
