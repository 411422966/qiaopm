import request from './request'

/**
 * 区块链溯源（P2-003 / P2.5 区块链集成）
 * 路径：/blockchain
 *
 * <p>提供 4 链并存：meiyetec-main / meiyetec-cosmetic / meiyetec-drug / meiyetec-device
 * <p>支持 5 类存证：PRODUCT / DRUG / DEVICE / ORDER / CONSENT
 */
export const blockchainApi = {
  // =================== 存证 ===================

  /** 通用存证 */
  record: (data: {
    recordType: string
    bizId: number
    bizNo?: string
    payload?: string
    chainId?: string
    difficulty?: number
    operatorName?: string
  }) => request.post('/blockchain/record', data),

  /** 商品存证 */
  recordProduct: (data: any) => request.post('/blockchain/record/product', data),

  /** 药品存证 */
  recordDrug: (data: any) => request.post('/blockchain/record/drug', data),

  /** 器械存证 */
  recordDevice: (data: any) => request.post('/blockchain/record/device', data),

  /** 订单存证 */
  recordOrder: (data: any) => request.post('/blockchain/record/order', data),

  /** 同意书存证 */
  recordConsent: (data: any) => request.post('/blockchain/record/consent', data),

  // =================== 查询 ===================

  /** 按 bizId 查询 */
  getRecord: (recordType: string, bizId: number) =>
    request.get(`/blockchain/record/${bizId}`, { params: { recordType } }),

  /** 分页查询 */
  recordPage: (params: { pageNum?: number; pageSize?: number; recordType?: string }) =>
    request.get('/blockchain/record/page', { params }),

  // =================== 链维护 ===================

  /** 链完整性校验 */
  verifyChain: (chainId?: string) => request.get('/blockchain/chain/verify', { params: { chainId } }),

  /** 单链信息 */
  chainInfo: (chainId?: string) => request.get('/blockchain/chain/info', { params: { chainId } }),

  /** 4 链概览 */
  chainOverview: () => request.get('/blockchain/chain/overview'),

  /** 溯源 */
  trace: (recordType: string, bizId: number) =>
    request.get(`/blockchain/trace/${recordType}/${bizId}`),

  // =================== 统计 ===================

  /** 4 链统计 */
  chainStats: () => request.get('/blockchain/stats/chain'),

  /** 存证类型分布 */
  recordTypeStats: () => request.get('/blockchain/stats/record-type'),

  /** 每日趋势 */
  dailyTrend: (days = 7) => request.get('/blockchain/stats/trend', { params: { days } }),

  /** 总览 */
  overview: () => request.get('/blockchain/stats/overview'),

  // =================== 工具 ===================

  /** SHA-256 哈希 */
  hash: (text: string) => request.post('/blockchain/utils/hash', { text })
}
