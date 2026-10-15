import request from './request'

/**
 * 多维提成规则引擎 API
 */
export const commissionRuleApi = {
  /** 阶梯列表 */
  tiersByRule: (ruleId: number) => request.get(`/commission/tier/by-rule/${ruleId}`),
  /** 批量保存阶梯 */
  saveTiers: (ruleId: number, tiers: any[]) => request.post(`/commission/tier/batch/${ruleId}`, tiers),
  /** 删除阶梯 */
  deleteTier: (id: number) => request.delete(`/commission/tier/${id}`),

  /** 条件列表 */
  conditionsByRule: (ruleId: number) => request.get(`/commission/condition/by-rule/${ruleId}`),
  /** 批量保存条件 */
  saveConditions: (ruleId: number, conditions: any[]) => request.post(`/commission/condition/batch/${ruleId}`, conditions),
  /** 删除条件 */
  deleteCondition: (id: number) => request.delete(`/commission/condition/${id}`),

  /** 模拟计算 */
  simulate: (params: {
    employeeId?: number
    employeeLevelId?: number
    serviceId?: number
    serviceCategoryId?: number
    serviceAmount: number
    customerType?: string
    memberLevel?: string
    simulationDate?: string
    storeId?: number
  }) => request.post('/commission/simulation', params),

  /** 员工最近模拟记录 */
  recentSimulations: (employeeId: number) => request.get(`/commission/simulation/recent/${employeeId}`)
}

/**
 * 条件类型定义
 */
export const conditionTypeOptions = [
  {
    value: 'service',
    label: '具体服务',
    description: '针对某个具体服务项目的提成规则',
    operators: ['eq'],
    valueType: 'service'
  },
  {
    value: 'service_category',
    label: '服务分类',
    description: '针对某个服务分类下的所有项目',
    operators: ['eq'],
    valueType: 'category'
  },
  {
    value: 'employee_level',
    label: '技师等级',
    description: '仅对特定等级技师生效',
    operators: ['eq'],
    valueType: 'level'
  },
  {
    value: 'customer_type',
    label: '客户类型',
    description: '区分新客户/老客户/VIP等',
    operators: ['eq'],
    valueType: 'string'
  },
  {
    value: 'member_level',
    label: '会员等级',
    description: '根据会员等级差异化提成',
    operators: ['eq'],
    valueType: 'string'
  },
  {
    value: 'min_amount',
    label: '最低金额',
    description: '订单金额需大于等于此值',
    operators: ['gte'],
    valueType: 'number'
  },
  {
    value: 'max_amount',
    label: '最高金额',
    description: '订单金额需小于等于此值',
    operators: ['lte'],
    valueType: 'number'
  },
  {
    value: 'time_slot',
    label: '时段',
    description: '指定时段内的订单（如9-12）',
    operators: ['eq'],
    valueType: 'timeSlot'
  },
  {
    value: 'weekday',
    label: '星期',
    description: '指定星期的订单（1-7）',
    operators: ['eq'],
    valueType: 'weekday'
  }
]

/**
 * 计算方式映射
 */
export const calculationMethodOptions = [
  { value: 'RATE', label: '按比例', description: '服务金额 × 提成比例', unit: '%' },
  { value: 'FIXED', label: '固定金额', description: '每单固定提成', unit: '元' },
  { value: 'TIER', label: '阶梯式', description: '按金额区间分段计算', unit: '阶梯' }
]

/**
 * 星期选项
 */
export const weekdayOptions = [
  { value: '1', label: '周一' },
  { value: '2', label: '周二' },
  { value: '3', label: '周三' },
  { value: '4', label: '周四' },
  { value: '5', label: '周五' },
  { value: '6', label: '周六' },
  { value: '7', label: '周日' }
]

/**
 * 客户类型选项
 */
export const customerTypeOptions = [
  { value: 'NEW', label: '新客户' },
  { value: 'RETURNING', label: '回头客' },
  { value: 'VIP', label: 'VIP客户' },
  { value: 'INACTIVE', label: '流失客户' }
]
