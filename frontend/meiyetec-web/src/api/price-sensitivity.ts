import request from './request'

/**
 * M15-03 价格敏感度 API（后端 PriceSensitivityController @ /intel/price-sensitivity）
 */
export const priceSensitivityApi = {
  overview: () => request.get('/intel/price-sensitivity/overview'),
  curve: (projectId: number) =>
    request.get('/intel/price-sensitivity/curve', { params: { projectId } }),
  segments: () => request.get('/intel/price-sensitivity/segments'),
  matrix: (topN = 20) =>
    request.get('/intel/price-sensitivity/matrix', { params: { topN } })
}

/**
 * 敏感度分群的中文标签 + 配色
 */
export const SENSITIVITY_SEGMENT_META: Record<
  'HIGH' | 'MEDIUM' | 'LOW',
  { label: string; color: string; bg: string; description: string }
> = {
  HIGH: {
    label: '高敏感',
    color: '#f56c6c',
    bg: '#fde8e8',
    description: '对折扣高度敏感，优惠券杀手 — 推荐大额折扣券唤醒复购'
  },
  MEDIUM: {
    label: '中敏感',
    color: '#e6a23c',
    bg: '#fdf3e1',
    description: '弹性客群，根据折扣力度决定是否消费 — 中等折扣即可激活'
  },
  LOW: {
    label: '低敏感',
    color: '#67c23a',
    bg: '#eaf7e5',
    description: '价格钝感，核心 VIP — 注重服务体验而非价格优惠'
  }
}

export const PRICE_TIER_LABEL: Record<'LOW' | 'MID' | 'HIGH', string> = {
  LOW: '低价档 (<¥200)',
  MID: '中价档 (¥200~500)',
  HIGH: '高价档 (≥¥500)'
}
