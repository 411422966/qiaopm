import request from './request'

/**
 * 技师等级管理 API
 */
export const technicianLevelApi = {
  /** 等级列表 */
  list: () => request.get('/technician/level/list'),

  /** 等级分页 */
  page: (params: { pageNum?: number; pageSize?: number; keyword?: string; status?: number }) =>
    request.post('/technician/level/page', params),

  /** 启用的等级 */
  activeList: () => request.get('/technician/level/active'),

  /** 等级详情 */
  getById: (id: number) => request.get(`/technician/level/${id}`),

  /** 创建等级 */
  create: (data: any) => request.post('/technician/level', data),

  /** 更新等级 */
  update: (data: any) => request.put(`/technician/level/${data.id}`, data),

  /** 删除等级 */
  delete: (id: number) => request.delete(`/technician/level/${id}`),

  /** 启停等级 */
  toggleStatus: (id: number, status: number) =>
    request.put(`/technician/level/${id}/status`, { status }),

  /** 等级技师分布 */
  statistics: () => request.get('/technician/level/statistics')
}

/**
 * 等级权益 API
 */
export const levelBenefitApi = {
  /** 权益列表(带等级信息) */
  list: (levelId?: number) => request.get('/technician/level/benefit/list', { params: { levelId } }),

  /** 指定等级的权益 */
  getByLevelId: (levelId: number) =>
    request.get(`/technician/level/benefit/by-level/${levelId}`),

  /** 权益详情 */
  getById: (id: number) => request.get(`/technician/level/benefit/${id}`),

  /** 创建权益 */
  create: (data: any) => request.post('/technician/level/benefit', data),

  /** 更新权益 */
  update: (data: any) => request.put(`/technician/level/benefit/${data.id}`, data),

  /** 删除权益 */
  delete: (id: number) => request.delete(`/technician/level/benefit/${id}`),

  /** 批量保存等级权益 */
  batchSave: (levelId: number, benefits: any[]) =>
    request.post(`/technician/level/benefit/batch/${levelId}`, benefits)
}

/**
 * 等级评定 API
 */
export const levelEvaluationApi = {
  /** 评定记录查询 */
  list: (params: { employeeId?: number; storeId?: number; status?: string }) =>
    request.get('/technician/level/evaluation/list', { params }),

  /** 员工的评定历史 */
  getByEmployee: (employeeId: number) =>
    request.get(`/technician/level/evaluation/by-employee/${employeeId}`),

  /** 自动评定 */
  autoEvaluate: (employeeId: number, params: { operatorId?: number; operatorName?: string; storeId?: number }) =>
    request.post(`/technician/level/evaluation/auto/${employeeId}`, params),

  /** 手动调整等级 */
  adjustLevel: (params: { employeeId: number; toLevelId: number; note?: string; operatorId?: number; operatorName?: string; storeId?: number }) =>
    request.post('/technician/level/evaluation/adjust', params),

  /** 撤销评定 */
  revert: (id: number) => request.put(`/technician/level/evaluation/${id}/revert`, {}),

  /** 计算员工评定指标 */
  getMetrics: (employeeId: number) =>
    request.get(`/technician/level/evaluation/metrics/${employeeId}`),

  /** 评定详情 */
  getById: (id: number) => request.get(`/technician/level/evaluation/${id}`)
}

/**
 * 权益类型映射
 */
export const benefitTypeOptions = [
  { value: 'discount', label: '服务折扣', icon: 'Discount', color: '#409EFF' },
  { value: 'commission', label: '提成加成', icon: 'Coin', color: '#67C23A' },
  { value: 'priority', label: '优先预约', icon: 'Star', color: '#E6A23C' },
  { value: 'exclusive', label: '专属项目', icon: 'StarFilled', color: '#F56C6C' },
  { value: 'training', label: '培训机会', icon: 'Reading', color: '#909399' }
]

/**
 * 评定结果映射
 */
export const evaluationResultOptions = [
  { value: 'upgrade', label: '晋升', type: 'success' },
  { value: 'downgrade', label: '降级', type: 'danger' },
  { value: 'keep', label: '维持', type: 'info' }
]

/**
 * 评定类型映射
 */
export const evaluationTypeOptions = [
  { value: 'auto', label: '自动评定', type: 'primary' },
  { value: 'manual', label: '手动调整', type: 'warning' }
]

/**
 * 评定状态映射
 */
export const evaluationStatusOptions = [
  { value: 'active', label: '已生效', type: 'success' },
  { value: 'reverted', label: '已撤销', type: 'info' },
  { value: 'pending', label: '待生效', type: 'warning' }
]
