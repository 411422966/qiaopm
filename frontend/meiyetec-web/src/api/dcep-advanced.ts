import request from './request'

/**
 * 数字人民币高级功能 API
 */
export const dcepAdvancedApi = {
  // 硬钱包
  wallets: (params: { memberId?: number; walletType?: string; status?: string }) =>
    request.get('/dcep/wallet/list', { params }),
  walletDetail: (id: number) => request.get(`/dcep/wallet/${id}`),
  walletByCode: (code: string) => request.get(`/dcep/wallet/by-code/${code}`),
  activateWallet: (data: any) => request.post('/dcep/wallet/activate', data),
  rechargeWallet: (id: number, amount: number) => request.post(`/dcep/wallet/${id}/recharge`, { amount }),

  // 离线交易
  offlineTransactions: (params: { walletId?: number; syncStatus?: string; merchantId?: string }) =>
    request.get('/dcep/offline/list', { params }),
  createOfflineTransaction: (data: any) => request.post('/dcep/offline/create', data),
  syncOfflineTransaction: (offlineTxNo: string) => request.post(`/dcep/offline/sync/${offlineTxNo}`, {}),
  detectDoubleSpend: (params: { walletId: number; offlineCert: string }) =>
    request.get('/dcep/offline/detect-double-spend', { params }),

  // 智能合约
  contracts: (params: { contractType?: string; status?: string; walletId?: number }) =>
    request.get('/dcep/contract/list', { params }),
  contractDetail: (id: number) => request.get(`/dcep/contract/${id}`),
  deployContract: (data: any) => request.post('/dcep/contract/deploy', data),
  triggerContract: (id: number, action: string) =>
    request.post(`/dcep/contract/${id}/trigger?action=${action}`, {}),
  autoExecuteExpired: () => request.post('/dcep/contract/auto-execute-expired', {}),
  contractLogs: (contractId?: number) =>
    request.get('/dcep/contract/log/list', { params: { contractId } })
}

/**
 * 钱包类型
 */
export const walletTypeOptions = [
  { value: 'nfc_sim', label: 'NFC-SIM卡', icon: 'Cellphone', color: '#409EFF', description: 'NFC-SIM硬钱包,运营商发行' },
  { value: 'se_chip', label: 'SE芯片', icon: 'Lock', color: '#67C23A', description: '可穿戴设备安全芯片(手表/手环)' },
  { value: 'iot_wallet', label: 'IoT钱包', icon: 'Cpu', color: '#9C27B0', description: '物联网设备钱包' },
  { value: 'biometric', label: '生物识别', icon: 'Fingerprint', color: '#E6A23C', description: '指纹/人脸硬钱包' }
]

/**
 * 合约类型
 */
export const contractTypeOptions = [
  { value: 'prepaid_card_escrow', label: '预付卡资金监管', icon: 'CreditCard', color: '#409EFF',
    description: '客户预充值 → 冻结 → 按消费释放' },
  { value: 'limited_storage', label: '限时储值', icon: 'Timer', color: '#E6A23C',
    description: '限时储值,到期自动过期退款' },
  { value: 'conditional_coupon', label: '条件触发优惠券', icon: 'Ticket', color: '#67C23A',
    description: '消费达额自动发券' }
]

/**
 * 合约动作
 */
export const contractActionOptions = [
  { value: 'release', label: '释放资金', type: 'success', description: '释放监管资金到钱包' },
  { value: 'refund', label: '退款', type: 'warning', description: '退还预付卡资金' },
  { value: 'coupon_grant', label: '发放优惠券', type: 'success', description: '触发优惠券发放' },
  { value: 'cancel', label: '取消合约', type: 'danger', description: '终止合约执行' }
]

/**
 * 钱包状态
 */
export const walletStatusOptions = [
  { value: 'active', label: '已激活', type: 'success' },
  { value: 'lost', label: '已挂失', type: 'warning' },
  { value: 'stolen', label: '被盗', type: 'danger' },
  { value: 'frozen', label: '已冻结', type: 'info' },
  { value: 'expired', label: '已过期', type: 'info' }
]

/**
 * 离线同步状态
 */
export const syncStatusOptions = [
  { value: 'pending', label: '待同步', type: 'warning' },
  { value: 'synced', label: '已同步', type: 'success' },
  { value: 'confirmed', label: '已确认', type: 'success' },
  { value: 'failed', label: '同步失败', type: 'danger' },
  { value: 'disputed', label: '争议中', type: 'danger' }
]

/**
 * 合约状态
 */
export const contractStatusOptions = [
  { value: 'pending', label: '待激活', type: 'info' },
  { value: 'active', label: '执行中', type: 'success' },
  { value: 'completed', label: '已完成', type: 'success' },
  { value: 'cancelled', label: '已取消', type: 'info' },
  { value: 'expired', label: '已过期', type: 'danger' }
]

/**
 * 离线交易类型
 */
export const offlineTxTypeOptions = [
  { value: 'consume', label: '消费', icon: 'Wallet' },
  { value: 'recharge', label: '充值', icon: 'TopUp' },
  { value: 'refund', label: '退款', icon: 'RefreshLeft' }
]