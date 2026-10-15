import request from './request'

/**
 * 双资源排程 API
 */
export const resourceScheduleApi = {
  // 冲突检测
  checkConflict: (params: { roomId?: number; employeeId?: number; startTime: string; endTime: string; excludeBookingId?: number }) =>
    request.get('/resource/schedule/check-conflict', { params }),

  // 智能推荐
  suggest: (params: { serviceId: number; startTime: string; durationMinutes?: number }) =>
    request.get('/resource/schedule/suggest', { params }),

  // 创建排程
  createSchedule: (data: any, conflictResolveAction: string = 'manual_resolve') =>
    request.post(`/resource/schedule/create?conflictResolveAction=${conflictResolveAction}`, data),

  // 冲突日志
  conflicts: (params: { storeId?: number; conflictType?: string }) =>
    request.get('/resource/conflict/list', { params }),

  // 利用率
  utilization: (params: { storeId?: number; resourceType?: string; resourceId?: number; startDate?: string; endDate?: string }) =>
    request.get('/resource/utilization/list', { params }),
  topUtilization: (params: { storeId?: number; startDate?: string; endDate?: string }) =>
    request.get('/resource/utilization/top', { params }),
  recalculate: (data: { storeId: number; date: string }) =>
    request.post('/resource/utilization/recalculate', data),

  // 匹配规则
  matchRules: () => request.get('/resource/match-rule/list'),
  matchRuleByService: (serviceId: number) => request.get(`/resource/match-rule/service/${serviceId}`)
}

/**
 * 冲突类型
 */
export const conflictTypeOptions = [
  { value: 'room_conflict', label: '房间冲突', type: 'danger' },
  { value: 'technician_conflict', label: '技师冲突', type: 'warning' },
  { value: 'both', label: '双重冲突', type: 'danger' }
]

/**
 * 冲突解决方式
 */
export const resolveActionOptions = [
  { value: 'auto_replace', label: '自动替换', description: '系统自动找替代资源' },
  { value: 'manual_resolve', label: '手动处理', description: '人工调整时间/资源' },
  { value: 'allow_overlap', label: '允许重叠', description: '极少数情况下允许' },
  { value: 'cancel', label: '取消排程', description: '放弃此次排程' }
]

/**
 * 资源类型
 */
export const resourceTypeOptions = [
  { value: 'room', label: '房间', icon: 'OfficeBuilding', color: '#409EFF' },
  { value: 'technician', label: '技师', icon: 'User', color: '#67C23A' }
]