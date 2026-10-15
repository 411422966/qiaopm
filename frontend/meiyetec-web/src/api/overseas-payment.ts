import request from './request'

/**
 * 海外支付网关 API
 */
export const overseasPaymentApi = {
  // 网关管理
  gateways: (params: { gatewayCode?: string; status?: number; environment?: string }) =>
    request.get('/overseas-payment/gateway/list', { params }),
  gatewayDetail: (id: number) => request.get(`/overseas-payment/gateway/${id}`),
  createGateway: (data: any) => request.post('/overseas-payment/gateway', data),
  updateGateway: (data: any) => request.put(`/overseas-payment/gateway/${data.id}`, data),
  deleteGateway: (id: number) => request.delete(`/overseas-payment/gateway/${id}`),
  gatewayHealthCheck: (id: number) => request.post(`/overseas-payment/gateway/${id}/health-check`, {}),

  // 支付
  payments: (params: { orderId?: number; memberId?: number; gatewayCode?: string; status?: string; startTime?: string; endTime?: string }) =>
    request.get('/overseas-payment/payment/list', { params }),
  paymentDetail: (id: number) => request.get(`/overseas-payment/payment/${id}`),
  createPayment: (data: any) => request.post('/overseas-payment/payment/create', data),
  refundPayment: (id: number, amount: number, reason?: string) =>
    request.post(`/overseas-payment/payment/${id}/refund?amount=${amount}&reason=${reason || ''}`, {}),
  selectGateway: (params: { currency: string; countryCode?: string; paymentMethod?: string; amount: number }) =>
    request.get('/overseas-payment/payment/select-gateway', { params }),
  paymentStats: (startTime?: string, endTime?: string) =>
    request.get('/overseas-payment/payment/statistics', { params: { startTime, endTime } }),

  // 路由规则
  routeRules: () => request.get('/overseas-payment/route/list'),
  matchRoutes: (params: { currency?: string; countryCode?: string; paymentMethod?: string }) =>
    request.get('/overseas-payment/route/match', { params }),
  createRoute: (data: any) => request.post('/overseas-payment/route', data),
  updateRoute: (data: any) => request.put(`/overseas-payment/route/${data.id}`, data),
  deleteRoute: (id: number) => request.delete(`/overseas-payment/route/${id}`),

  // Webhook日志
  webhookLogs: (params: { gatewayCode?: string; processStatus?: string }) =>
    request.get('/overseas-payment/webhook/logs', { params })
}

/**
 * 网关选项
 */
export const gatewayOptions = [
  { value: 'stripe', label: 'Stripe', icon: 'CreditCard', color: '#635BFF', country: 'Global', description: '全球最大支付平台,支持130+币种' },
  { value: 'square', label: 'Square', icon: 'Money', color: '#3E4348', country: 'US/CA/JP/UK/AU', description: '美国线下POS+线上支付' },
  { value: 'paypal', label: 'PayPal', icon: 'Wallet', color: '#003087', country: 'Global', description: '全球普及钱包,2亿+用户' },
  { value: 'adyen', label: 'Adyen', icon: 'Connection', color: '#0ABF53', country: 'Global', description: '欧洲领先,统一商务平台' },
  { value: 'braintree', label: 'Braintree', icon: 'Coin', color: '#009CDE', country: 'Global', description: 'PayPal旗下' },
  // ⭐ P3.6 海外钱包支付（2026-09-05 实装）
  { value: 'apple_pay', label: 'Apple Pay', icon: 'Iphone', color: '#000000', country: 'Global', description: '基于 Stripe,iOS/Safari 用户一键支付' },
  { value: 'google_pay', label: 'Google Pay', icon: 'Cellphone', color: '#4285F4', country: 'Global', description: '基于 Stripe,Android/Chrome 用户一键支付' }
]

/**
 * 支付方式
 */
export const paymentMethodOptions = [
  { value: 'card', label: '信用卡', icon: 'CreditCard' },
  { value: 'apple_pay', label: 'Apple Pay', icon: 'Iphone' },
  { value: 'google_pay', label: 'Google Pay', icon: 'Cellphone' },
  { value: 'ideal', label: 'iDEAL', icon: 'Money' },
  { value: 'sofort', label: 'SOFORT', icon: 'Money' },
  { value: 'sepa', label: 'SEPA', icon: 'Money' },
  { value: 'klarna', label: 'Klarna', icon: 'Coin' },
  { value: 'afterpay', label: 'Afterpay', icon: 'Coin' }
]

/**
 * 币种
 */
export const currencyOptions = [
  { value: 'USD', label: '美元', symbol: '$' },
  { value: 'EUR', label: '欧元', symbol: '€' },
  { value: 'GBP', label: '英镑', symbol: '£' },
  { value: 'JPY', label: '日元', symbol: '¥' },
  { value: 'CAD', label: '加元', symbol: 'C$' },
  { value: 'AUD', label: '澳元', symbol: 'A$' },
  { value: 'HKD', label: '港币', symbol: 'HK$' },
  { value: 'SGD', label: '新币', symbol: 'S$' },
  { value: 'CNY', label: '人民币', symbol: '¥' }
]

/**
 * 支付状态
 */
export const paymentStatusOptions = [
  { value: 'pending', label: '待支付', type: 'info' },
  { value: 'succeeded', label: '已成功', type: 'success' },
  { value: 'failed', label: '失败', type: 'danger' },
  { value: 'canceled', label: '已取消', type: 'warning' },
  { value: 'refunded', label: '已退款', type: 'info' },
  { value: 'partially_refunded', label: '部分退款', type: 'warning' }
]

/**
 * 路由策略
 */
export const routeStrategyOptions = [
  { value: 'cost', label: '费率优先', description: '选择手续费最低的网关' },
  { value: 'speed', label: '速度优先', description: '选择响应最快的网关' },
  { value: 'coverage', label: '覆盖优先', description: '选择支持地区最多的网关' }
]

/**
 * 环境
 */
export const environmentOptions = [
  { value: 'sandbox', label: '沙箱', type: 'warning' },
  { value: 'production', label: '生产', type: 'danger' }
]