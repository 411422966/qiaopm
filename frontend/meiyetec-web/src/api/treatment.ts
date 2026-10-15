import request from './request'

/**
 * 疗程方案 API —— 后端 BizTreatmentController (/biz/treatment/*)
 */

// ==================== 方案（Plan） ====================
export const treatmentApi = {
  // 方案列表（分页）
  planPage: (params: {
    pageNum?: number
    pageSize?: number
    storeId?: number
    categoryId?: number
    keyword?: string
    status?: number
  }) => request.get<any>('/biz/treatment/plan/page', { params }),

  // 方案统计
  planStats: () => request.get<any>('/biz/treatment/plan/stats'),

  // 方案详情
  planDetail: (id: number) => request.get<any>(`/biz/treatment/plan/${id}`),

  // 新建方案
  createPlan: (data: Record<string, unknown>) => request.post<any>('/biz/treatment/plan', data),

  // 更新方案
  updatePlan: (id: number, data: Record<string, unknown>) =>
    request.put<any>(`/biz/treatment/plan/${id}`, data),

  // 删除方案
  deletePlan: (id: number) => request.delete<void>(`/biz/treatment/plan/${id}`),

  // 方案导出 Excel
  exportPlan: (params?: { storeId?: number; keyword?: string; status?: number }) =>
    request.get<Blob>('/biz/treatment/plan/export', { params, responseType: 'blob' }),

  // 方案导入模板下载
  planImportTemplate: () =>
    request.get<Blob>('/biz/treatment/plan/template', { responseType: 'blob' }),

  // 方案批量导入
  importPlan: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post<any>('/biz/treatment/plan/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // ==================== 阶段（Phase） ====================
  createPhase: (data: Record<string, unknown>) => request.post<any>('/biz/treatment/phase', data),
  updatePhase: (id: number, data: Record<string, unknown>) =>
    request.put<any>(`/biz/treatment/phase/${id}`, data),
  deletePhase: (id: number) => request.delete<void>(`/biz/treatment/phase/${id}`),

  // ==================== 进度（Progress） ====================
  /** 开通疗程（会员购买套餐后） */
  createProgress: (data: { memberId: number; planId: number; operatorName: string }) =>
    request.post<any>('/biz/treatment/progress', data),

  /** 疗程打卡（记录一次阶段完成） */
  recordSession: (progressId: number, data: { phaseNo: number; remark?: string }) =>
    request.post<any>(`/biz/treatment/progress/${progressId}/session`, data),

  /** 会员的疗程进度列表 */
  memberProgress: (memberId: number) =>
    request.get<any[]>(`/biz/treatment/progress/member/${memberId}`),

  /** 单条进度详情（含阶段 + 打卡记录） */
  progressDetail: (progressId: number) =>
    request.get<any>(`/biz/treatment/progress/${progressId}`),

  /** 停止疗程 */
  stopProgress: (progressId: number, reason?: Record<string, unknown>) =>
    request.post<void>(`/biz/treatment/progress/${progressId}/stop`, reason || {}),

  /** 进度导出 Excel */
  exportProgress: (params?: { memberId?: number; status?: number }) =>
    request.get<Blob>('/biz/treatment/progress/export', { params, responseType: 'blob' })
}
