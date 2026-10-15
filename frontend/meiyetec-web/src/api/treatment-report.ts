import request from './request'

/**
 * M06 疗程效果报告 API
 * 后端 BizTreatmentReportController (/biz/treatment/report/*)
 *
 * 设计要点：
 * - 快照模式：每次 generate 都是一行新记录，方便"第 5 次 / 第 10 次"对比
 * - 多租户硬隔离：后端 SQL 全部带 tenant_id，前端无需显式传
 * - phases_summary_json / session_ids_json 字段已在后端解析，可直接读 .data
 */
export const treatmentReportApi = {
  /**
   * 触发生成一张效果报告（聚合 sessions / phases / care-log / skin-profile）。
   * 同 memberId + planId 多次调用，每次追加一行历史快照。
   */
  generate: (params: {
    memberId: number | string
    planId: number | string
    generatedBy?: string
  }) =>
    request.post<any>('/biz/treatment/report/generate', null, { params }),

  /** 单条报告详情 */
  getById: (id: number | string) =>
    request.get<any>(`/biz/treatment/report/${id}`),

  /** 会员维度历史报告列表 */
  listByMember: (memberId: number | string) =>
    request.get<any>('/biz/treatment/report/list', { params: { memberId } }),

  /** 最近的若干条（租户内）,默认 20 */
  recent: (limit: number = 20) =>
    request.get<any>('/biz/treatment/report/recent', { params: { limit } }),

  /** KPI 聚合：总报告数 / 今日生成 / 平均遵守率 / 平均效果分差 */
  stats: () =>
    request.get<any>('/biz/treatment/report/stats')
}

/**
 * 效果评分公式镜像（与后端 TreatmentReportService#computeEffectScore 保持一致）
 * = moisture*0.4 + oil*0.15 + pigmentation*0.15 + wrinkle*0.15 + pore*0.15
 * 用于前端"无后端数据时"的实时预览
 */
export function computeEffectScore(skin: {
  moisture?: number
  oil?: number
  pigmentation?: number
  wrinkle?: number
  pore?: number
} | null | undefined): number | null {
  if (!skin) return null
  const m = skin.moisture ?? 0
  const o = skin.oil ?? 0
  const p = skin.pigmentation ?? 0
  const w = skin.wrinkle ?? 0
  const po = skin.pore ?? 0
  const sum = m * 0.4 + o * 0.15 + p * 0.15 + w * 0.15 + po * 0.15
  return Math.round(sum * 100) / 100
}

/**
 * 推荐规则引擎（前端镜像，后端权威）
 * 5 阈值：empty / compliance>=80% / compliance<50% / delta>=+5 / delta<=-5 / default
 */
export function recommendText(input: {
  hasData: boolean
  complianceRate: number | null
  delta: number | null
  completed: number
  total: number
}): string {
  if (!input.hasData || input.total === 0) {
    return '尚未产生足够疗程数据，待首次 session 完成后即可生成首份报告。'
  }
  const compliance = input.complianceRate ?? 0
  if (compliance >= 0.8) {
    return '客户依从性良好，已坚持完成大部分护理建议，建议继续保持当前节奏。'
  }
  if (compliance < 0.5) {
    return '护理依从性偏低，建议运营人员加强触达与提醒，必要时重新评估方案可行性。'
  }
  const delta = input.delta ?? 0
  if (delta >= 5) {
    return '效果分显著提升（≥ 5 分），可建议客户续单或转介绍。'
  }
  if (delta <= -5) {
    return '效果分出现下降趋势（≤ -5 分），建议复盘方案 / 调整技师 / 复核肤质档案。'
  }
  return '效果分小幅波动，仍在正常区间内，建议持续观察 2~3 个 session 后再生成下份报告。'
}

/**
 * 合规率等级 → 颜色
 */
export function complianceColor(rate: number | null): string {
  if (rate == null) return '#909399'
  if (rate >= 0.8) return '#67C23A'
  if (rate >= 0.5) return '#E6A23C'
  return '#F56C6C'
}

/**
 * Delta 等级 → 颜色 + 描述
 */
export function deltaMeta(delta: number | null): { color: string; arrow: string; tag: string } {
  if (delta == null) return { color: '#909399', arrow: '—', tag: '暂无基线' }
  if (delta >= 5) return { color: '#67C23A', arrow: '↑', tag: '显著改善' }
  if (delta >= 1) return { color: '#67C23A', arrow: '↑', tag: '轻微改善' }
  if (delta > -1) return { color: '#909399', arrow: '→', tag: '基本持平' }
  if (delta > -5) return { color: '#F56C6C', arrow: '↓', tag: '轻微下滑' }
  return { color: '#F56C6C', arrow: '↓↓', tag: '显著下滑' }
}
