import request from './request'

/**
 * 补齐-P1-9 国际支付网关 Stripe/Square/BNPL
 */
export const intlPaymentApi = {
  listGateways: () => request.get('/intl-payment/gateways'),
  createCharge: (data: { gateway: string; amount: number; currency: string; orderId: number }) =>
    request.post('/intl-payment/charge', data),
  listCharges: () => request.get('/intl-payment/charges'),
  refund: (id: number, data: { amount?: number; reason?: string }) =>
    request.post(`/intl-payment/${id}/refund`, data)
}