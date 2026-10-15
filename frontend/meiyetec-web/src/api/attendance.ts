import request from './request'

/**
 * 考勤打卡模块 API
 */
export const attendanceApi = {
  // ========== 打卡 ==========
  /** 上班打卡 */
  checkIn: (data: { employeeId: number; method?: string; location?: string; storeId?: number }) =>
    request.post('/attendance/check-in', data),

  /** 下班打卡 */
  checkOut: (data: { employeeId: number; method?: string; location?: string }) =>
    request.post('/attendance/check-out', data),

  /** 获取员工今日打卡状态 */
  getTodayStatus: (employeeId: number) =>
    request.get('/attendance/today', { params: { employeeId } }),

  // ========== 打卡记录 ==========
  /** 打卡记录分页查询 */
  page: (params: {
    pageNum?: number
    pageSize?: number
    employeeId?: number
    storeId?: number
    startDate?: string
    endDate?: string
  }) => request.post('/attendance/page', params),

  /** 打卡记录查询(带员工信息) */
  records: (params: {
    employeeId?: number
    storeId?: number
    startDate?: string
    endDate?: string
  }) => request.get('/attendance/records', { params }),

  /** 获取考勤统计 */
  statistics: (params: {
    storeId?: number
    startDate?: string
    endDate?: string
  }) => request.get('/attendance/statistics', { params }),

  /** 获取员工考勤月报 */
  monthlyReport: (employeeId: number, year: number, month: number) =>
    request.get('/attendance/monthly-report', { params: { employeeId, year, month } }),

  /** 获取单条打卡记录 */
  getById: (id: number) => request.get(`/attendance/${id}`),

  /** 更新打卡记录(补录) */
  update: (data: any) => request.put(`/attendance/${data.id}`, data),

  // ========== 考勤配置 ==========
  /** 获取生效的考勤配置 */
  getActiveConfig: (storeId?: number) =>
    request.get('/attendance/config/active', { params: { storeId } }),

  /** 考勤配置列表 */
  configList: (storeId?: number) =>
    request.get('/attendance/config/list', { params: { storeId } }),

  /** 获取考勤配置详情 */
  getConfigById: (id: number) => request.get(`/attendance/config/${id}`),

  /** 创建考勤配置 */
  createConfig: (data: any) => request.post('/attendance/config', data),

  /** 更新考勤配置 */
  updateConfig: (data: any) => request.put(`/attendance/config/${data.id}`, data),

  /** 删除考勤配置 */
  deleteConfig: (id: number) => request.delete(`/attendance/config/${id}`)
}

/**
 * 请假申请 API
 */
export const leaveApi = {
  /** 提交请假申请 */
  submit: (data: {
    employeeId: number
    leaveType: string
    startDate: string
    endDate: string
    reason?: string
    storeId?: number
  }) => request.post('/attendance/leave', data),

  /** 审批请假 */
  approve: (id: number, data: { approverId: number; remark?: string; approved: boolean }) =>
    request.put(`/attendance/leave/${id}/approve`, data),

  /** 取消请假申请 */
  cancel: (id: number, employeeId: number) =>
    request.put(`/attendance/leave/${id}/cancel`, { employeeId }),

  /** 查询请假记录 */
  list: (params: {
    employeeId?: number
    storeId?: number
    status?: string
    startDate?: string
    endDate?: string
  }) => request.get('/attendance/leave/list', { params }),

  /** 获取我的请假记录 */
  myList: (employeeId: number) => request.get('/attendance/leave/my', { params: { employeeId } }),

  /** 获取待审批数量 */
  pendingCount: (storeId: number) =>
    request.get('/attendance/leave/pending-count', { params: { storeId } }),

  /** 获取请假详情 */
  getById: (id: number) => request.get(`/attendance/leave/${id}`)
}

/**
 * 请假类型映射
 */
export const leaveTypeOptions = [
  { value: 'annual', label: '年假' },
  { value: 'sick', label: '病假' },
  { value: 'personal', label: '事假' },
  { value: 'marriage', label: '婚假' },
  { value: 'maternity', label: '产假' },
  { value: 'funeral', label: '丧假' },
  { value: 'other', label: '其他' }
]

/**
 * 请假状态映射
 */
export const leaveStatusOptions = [
  { value: 'pending', label: '待审批', type: 'warning' },
  { value: 'approved', label: '已通过', type: 'success' },
  { value: 'rejected', label: '已拒绝', type: 'danger' },
  { value: 'cancelled', label: '已取消', type: 'info' }
]

/**
 * 打卡状态映射
 */
export const checkInStatusOptions = [
  { value: 'normal', label: '正常', type: 'success' },
  { value: 'late', label: '迟到', type: 'warning' },
  { value: 'early', label: '早退', type: 'warning' },
  { value: 'absence', label: '旷工', type: 'danger' }
]
