import request from './request'

/**
 * 护理建议 API
 */
export const careSuggestionApi = {
  // ========== 模板管理 ==========
  templates: (params: { templateType?: string; status?: number }) =>
    request.get('/crm/care/suggestion/template/list', { params }),
  getTemplate: (id: number) => request.get(`/crm/care/suggestion/template/${id}`),
  createTemplate: (data: any) => request.post('/crm/care/suggestion/template', data),
  updateTemplate: (data: any) => request.put(`/crm/care/suggestion/template/${data.id}`, data),
  deleteTemplate: (id: number) => request.delete(`/crm/care/suggestion/template/${id}`),

  // ========== 规则管理 ==========
  rules: () => request.get('/crm/care/suggestion/rule/list'),
  activeRules: () => request.get('/crm/care/suggestion/rule/active'),
  getRule: (id: number) => request.get(`/crm/care/suggestion/rule/${id}`),
  createRule: (data: any) => request.post('/crm/care/suggestion/rule', data),
  updateRule: (data: any) => request.put(`/crm/care/suggestion/rule/${data.id}`, data),
  deleteRule: (id: number) => request.delete(`/crm/care/suggestion/rule/${id}`),

  // ========== 建议记录 ==========
  logs: (params: { memberId?: number; status?: string; suggestionType?: string }) =>
    request.get('/crm/care/suggestion/log/list', { params }),
  getLog: (id: number) => request.get(`/crm/care/suggestion/log/${id}`),
  updateLogStatus: (id: number, status: string) =>
    request.put(`/crm/care/suggestion/log/${id}/status`, { status }),
  acceptLog: (id: number) => request.put(`/crm/care/suggestion/log/${id}/accept`, {}),
  viewLog: (id: number) => request.put(`/crm/care/suggestion/log/${id}/view`, {}),
  rejectLog: (id: number) => request.put(`/crm/care/suggestion/log/${id}/reject`, {}),

  // ========== 生成引擎 ==========
  generate: (memberId: number, params: { operatorId?: number; operatorName?: string; triggerType?: string }) =>
    request.post(`/crm/care/suggestion/generate/${memberId}`, params),
  generateBatch: (params: { memberIds: number[]; operatorId?: number; operatorName?: string }) =>
    request.post('/crm/care/suggestion/generate-batch', params),

  // ========== 肌肤档案 ==========
  skinProfile: (memberId: number) => request.get(`/crm/care/suggestion/skin-profile/${memberId}`),
  saveSkinProfile: (data: any) => request.post('/crm/care/suggestion/skin-profile', data)
}

/**
 * 模板类型选项
 */
export const templateTypeOptions = [
  { value: 'skin', label: '肌肤护理', icon: 'Sunny', color: '#409EFF' },
  { value: 'body', label: '身体护理', icon: 'GoldMedal', color: '#67C23A' },
  { value: 'hair', label: '头发护理', icon: 'Star', color: '#E6A23C' },
  { value: 'nail', label: '美甲', icon: 'StarFilled', color: '#F56C6C' },
  { value: 'custom', label: '自定义', icon: 'MagicStick', color: '#909399' }
]

/**
 * 条件类型选项
 */
export const conditionTypeOptions = [
  { value: 'tag', label: '客户标签', description: '匹配客户的特定标签(如VIP/敏感肌)' },
  { value: 'skin_type', label: '肤质类型', description: '根据肤质推荐护理方案' },
  { value: 'age_range', label: '年龄段', description: '如18-25, 26-35' },
  { value: 'season', label: '季节', description: '春/夏/秋/冬 差异化建议' },
  { value: 'service_history', label: '服务历史', description: '基于最近N次服务情况' }
]

/**
 * 建议动作选项
 */
export const suggestionActionOptions = [
  { value: 'recommend', label: '推荐项目', type: 'primary', icon: 'Present' },
  { value: 'remind', label: '温馨提醒', type: 'info', icon: 'Bell' },
  { value: 'upsell', label: '升级推荐', type: 'warning', icon: 'Top' },
  { value: 'cross_sell', label: '交叉销售', type: 'success', icon: 'MagicStick' }
]

/**
 * 建议状态选项
 */
export const suggestionStatusOptions = [
  { value: 'pending', label: '待发送', type: 'info' },
  { value: 'sent', label: '已发送', type: 'primary' },
  { value: 'viewed', label: '已查看', type: 'warning' },
  { value: 'accepted', label: '已采纳', type: 'success' },
  { value: 'rejected', label: '已拒绝', type: 'danger' },
  { value: 'expired', label: '已过期', type: 'info' }
]

/**
 * 肤质类型选项
 */
export const skinTypeOptions = [
  { value: 'dry', label: '干性', color: '#E6A23C' },
  { value: 'oily', label: '油性', color: '#67C23A' },
  { value: 'combination', label: '混合性', color: '#409EFF' },
  { value: 'sensitive', label: '敏感性', color: '#F56C6C' },
  { value: 'normal', label: '中性', color: '#909399' }
]

/**
 * 季节选项
 */
export const seasonOptions = [
  { value: 'spring', label: '春季', icon: 'Cherry' },
  { value: 'summer', label: '夏季', icon: 'Sunny' },
  { value: 'autumn', label: '秋季', icon: 'Leaf' },
  { value: 'winter', label: '冬季', icon: 'ColdDrink' }
]

/**
 * 模板变量说明
 */
export const templateVarOptions = [
  { value: 'member_name', label: '客户姓名', example: '{member_name}' },
  { value: 'member_id', label: '客户ID', example: '{member_id}' },
  { value: 'member_level', label: '会员等级', example: '{member_level}' },
  { value: 'skin_type', label: '肤质', example: '{skin_type}' },
  { value: 'sensitivity_score', label: '敏感度评分', example: '{sensitivity_score}' },
  { value: 'season', label: '当前季节', example: '{season}' },
  { value: 'last_visit_days', label: '上次访问天数', example: '{last_visit_days}' },
  { value: 'current_date', label: '当前日期', example: '{current_date}' }
]