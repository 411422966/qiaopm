import request from './request'
import axios from 'axios'

/**
 * RFM 分群 API —— 后端 RfmController (/intel/rfm/*)
 *
 * 注意 axios 拦截器（request.ts）会在响应阶段解包 envelope，
 * 但返回的仍然是外层 envelope { code, data, message, traceId }。
 * 本模块统一再 unwrap 一次，调用方拿到的是真正的业务 data（List / Page / Map）。
 *
 * silent 选项：
 *  - 当 res.code !== 200 时，拦截器默认会弹 ElMessage.error
 *  - 当传 { silent: true } 时不弹 toast，由调用方自行处理（composable 内捕获 + el-alert）
 *
 * 由于 AxiosRequestConfig 没有 silent 字段，这里用 as any 绕过类型检查，
 * 运行时拦截器会按 (config as any).silent 读取。
 */

const silent = { silent: true } as any

// 类型
export type SegmentCode =
  | 'CHAMPION' | 'LOYAL' | 'POTENTIAL' | 'NEW'
  | 'AT_RISK' | 'HIBERNATING' | 'LOST' | 'GENERAL'

export interface SegmentOverviewItem {
  segment: SegmentCode | string
  segmentName: string
  memberCount: number
  avgFreq: number
  avgAmount: number
  avgRecency: number
}

export interface MemberRfm {
  memberId: number
  recencyDays: number
  frequency: number
  monetary: number
  rScore: number
  fScore: number
  mScore: number
  rfmScore: string
  segment: SegmentCode | string
  segmentName: string
  explanation: string
  durationMs: number
}

export interface BatchComputeResult {
  total: number
  success: number
  fail: number
  durationMs: number
}

export interface BatchTaskHandle {
  taskId: number
  action: 'BATCH' | 'SEED'
  limit: number
}

export interface BatchProgress {
  id: number
  tenant_id: number
  action: string
  brand_id?: number | null
  operator_name?: string | null
  status: 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED' | string
  total: number
  processed: number
  success: number
  fail: number
  start_time?: string
  end_time?: string | null
  remark?: string | null
}

export interface TagDictionary {
  id: number
  tagCode: string
  tagName: string
  category: string
  description?: string
  rule?: string
  color?: string
  icon?: string
  sortOrder?: number
  enabled?: number
}

export interface MemberTagRow {
  id: number
  tagCode: string
  tagName: string
  tagCategory: string
  tagValue?: string
  score?: number
  sourceType?: string
  effectiveAt?: string
  expireAt?: string
  description?: string
  rule?: string
}

export interface SegmentMemberRow {
  memberId: number
  memberNo?: string
  name?: string
  phone?: string
  levelId?: number
  segment: SegmentCode | string
  segmentName: string
  recencyDays: number
  frequency: number
  monetary: number
  rfmScore: string
  explanation?: string
}

export interface AiTagModelRow {
  id: number
  modelCode: string
  modelName: string
  modelType: string
  scheduleCron?: string
  enabled?: number
  status?: string
  description?: string
  auc?: number
  recall?: number
  precisionScore?: number
  operatorName?: string
  createTime?: string
}

export interface SegmentMemberPage {
  records: SegmentMemberRow[]
  total: number
  size: number
  current: number
  pages: number
}

export interface SeedResult {
  total: number
  success: number
  fail: number
}

export interface RfmHistoryPoint {
  snapshotDate: string
  segment?: SegmentCode | string
  segmentName?: string
  recencyDays: number
  frequency: number
  monetary: number
  rScore: number
  fScore: number
  mScore: number
  rfmScore?: string
  explanation?: string
}

export interface BatchHistoryRow {
  id: number
  action: string
  brandId?: number
  total: number
  success: number
  fail: number
  durationMs: number
  operatorName?: string
  remark?: string
  createTime?: string
}

/**
 * 8 大分群的中文名 + 柔和色卡（paletteSoft）
 * 与前端 SegmentCard / SegmentDrawer 共用。
 */
export const SEGMENT_META: Record<
  SegmentCode,
  { label: string; color: string; bg: string; description: string }
> = {
  CHAMPION:   { label: '冠军客户',   color: '#5B8DEF', bg: '#EAF1FE', description: '近期频繁高额消费，核心 VIP' },
  LOYAL:      { label: '忠诚客户',   color: '#67C23A', bg: '#EAF7E5', description: '高频消费金额中上，关系稳定' },
  POTENTIAL:  { label: '潜力客户',   color: '#E6A23C', bg: '#FBF1DE', description: '近期高额低频，可推送新项目' },
  NEW:        { label: '新客户',     color: '#909399', bg: '#F1F2F4', description: '新注册或首次消费' },
  AT_RISK:    { label: '流失风险',   color: '#F56C6C', bg: '#FCE6E6', description: '有过消费但近期未到店' },
  HIBERNATING:{ label: '冬眠客户',   color: '#B37FEB', bg: '#F1E9FC', description: '长期未消费，激活成本高' },
  LOST:       { label: '流失客户',   color: '#8E5A60', bg: '#F1E1E3', description: '长期未消费，挽回困难' },
  GENERAL:    { label: '普通客户',   color: '#5A6C7D', bg: '#EDF1F5', description: '近期 / 频次 / 金额均中等' }
}

export const SEGMENT_FALLBACK = { label: '未分类', color: '#909399', bg: '#F4F4F5', description: '' }

// ---- API ----
// 拦截器返回 envelope：{ code, data, message, traceId }
// 这里统一 unwrap data，调用方拿到业务数据本身。
async function unwrap<T>(p: Promise<any>): Promise<T> {
  const env = await p
  return env?.data as T
}

export const rfmApi = {
  segmentOverview: (brandId?: number) =>
    unwrap<SegmentOverviewItem[]>(request.get('/intel/rfm/segment/overview', {
      params: brandId ? { brandId } : {},
      ...silent
    })),

  computeMember: (memberId: number) =>
    unwrap<MemberRfm>(request.get(`/intel/rfm/compute/${memberId}`, silent)),

  batchCompute: (body: { brandId?: number; limit?: number } = {}) =>
    unwrap<BatchComputeResult>(request.post('/intel/rfm/batch/compute', body, silent)),

  memberTags: (memberId: number) =>
    unwrap<MemberTagRow[]>(request.get(`/intel/rfm/tag/member/${memberId}`, silent)),

  memberRfmHistory: (memberId: number, days = 90) =>
    unwrap<RfmHistoryPoint[]>(request.get(`/intel/rfm/member/${memberId}/history`, {
      params: { days },
      ...silent
    })),

  upsertTag: (body: Record<string, unknown>) =>
    request.post('/intel/rfm/tag', body),

  deleteTag: (id: number) =>
    request.delete(`/intel/rfm/tag/${id}`),

  upsertDict: (body: Record<string, unknown>) =>
    request.post('/intel/rfm/dict', body),

  deleteDict: (id: number) =>
    request.delete(`/intel/rfm/dict/${id}`),

  dictList: (category?: string) =>
    unwrap<TagDictionary[]>(request.get('/intel/rfm/dict/list', { params: { category }, ...silent })),

  segmentMembers: (segment: string, pageNum = 1, pageSize = 20) =>
    unwrap<SegmentMemberPage>(request.get(`/intel/rfm/segment/${segment}/members`, {
      params: { pageNum, pageSize },
      ...silent
    })),

  /**
   * 导出分群下钻为 CSV（blob 下载）。
   *  - 走裸 axios，避开 envelope 拦截器（CSV 不是 JSON）
   *  - 后端 Content-Disposition 已带 UTF-8 文件名
   */
  exportSegmentMembers: async (segment: string): Promise<Blob> => {
    const resp = await axios.get(
      `/api/intel/rfm/segment/${encodeURIComponent(segment)}/members/export`,
      {
        responseType: 'blob',
        headers: {
          Authorization: `Bearer ${localStorage.getItem('token') ?? ''}`,
          'X-Trace-Id': sessionStorage.getItem('traceId') ?? ''
        }
      }
    )
    return resp.data as Blob
  },

  segmentList: () =>
    request.get('/intel/rfm/segment/list', silent),

  createSegment: (body: Record<string, unknown>) =>
    request.post('/intel/rfm/segment', body),

  modelList: () =>
    unwrap<AiTagModelRow[]>(request.get('/intel/rfm/model/list', silent)),

  seed: (limit = 30) =>
    unwrap<SeedResult>(request.post('/intel/rfm/seed', { limit }, silent)),

  batchHistory: (limit = 20, brandId?: number) =>
    unwrap<BatchHistoryRow[]>(request.get('/intel/rfm/batch/history', {
      params: brandId ? { limit, brandId } : { limit },
      ...silent
    })),

  clearSeed: () =>
    request.delete('/intel/rfm/seed'),

  /**
   * 异步批量计算（带实时进度，可取消）
   */
  runBatchAsync: (body: { brandId?: number; limit?: number; operatorName?: string } = {}) =>
    unwrap<BatchTaskHandle>(request.post('/intel/rfm/batch/run', body, silent)),

  /**
   * 异步 seed 重算（带实时进度，可取消）
   */
  runSeedAsync: (body: { limit?: number; operatorName?: string } = {}) =>
    unwrap<BatchTaskHandle>(request.post('/intel/rfm/seed/run', body, silent)),

  /**
   * 取消批量/seed 任务
   */
  cancelBatch: (taskId: number) =>
    unwrap<{ taskId: number; status: string }>(request.post(`/intel/rfm/batch/cancel/${taskId}`, {}, silent)),

  /**
   * 查询任务进度（前端轮询用）
   */
  batchProgress: (taskId: number) =>
    unwrap<BatchProgress>(request.get(`/intel/rfm/batch/progress/${taskId}`, silent))
}