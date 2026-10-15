import request from './request'

/**
 * P2-21 海外 POS 终端对接（后端 PosDeviceService @ /pos-device）
 * 注意：与 pos.* 收银模块分离，专注于 Square / 海外硬件对接
 */
export const posDeviceApi = {
  // 设备管理
  register: (data: any) => request.post('/pos-device/register', data),
  listDevices: () => request.get('/pos-device/list'),
  updateDevice: (id: number, data: any) => request.post(`/pos-device/${id}`, data),

  // 同步交易
  syncTransaction: (data: any) => request.post('/pos-device/transaction/sync', data),
  listTransactions: (params?: { deviceId?: number; status?: string }) =>
    request.get('/pos-device/transaction/list', { params }),

  // Dashboard
  dashboard: () => request.get('/pos-device/dashboard')
}