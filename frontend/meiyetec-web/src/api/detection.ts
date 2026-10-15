import request from './request'

/**
 * 美业档案检测 API
 */
export const detectionApi = {
  // ========== 肤质 ==========
  skinList: (params: { memberId?: number; storeId?: number; skinType?: string }) =>
    request.get('/crm/detection/skin/list', { params }),
  skinLatest: (memberId: number) => request.get(`/crm/detection/skin/latest/${memberId}`),
  skinCreate: (data: any) => request.post('/crm/detection/skin', data),
  skinUpdate: (data: any) => request.put(`/crm/detection/skin/${data.id}`, data),
  skinDelete: (id: number) => request.delete(`/crm/detection/skin/${id}`),
  skinStats: (storeId?: number) => request.get('/crm/detection/skin/statistics/type', { params: { storeId } }),

  // ========== 发质 ==========
  hairList: (params: { memberId?: number; storeId?: number; hairType?: string }) =>
    request.get('/crm/detection/hair/list', { params }),
  hairLatest: (memberId: number) => request.get(`/crm/detection/hair/latest/${memberId}`),
  hairCreate: (data: any) => request.post('/crm/detection/hair', data),
  hairUpdate: (data: any) => request.put(`/crm/detection/hair/${data.id}`, data),
  hairDelete: (id: number) => request.delete(`/crm/detection/hair/${id}`),
  hairStats: (storeId?: number) => request.get('/crm/detection/hair/statistics/type', { params: { storeId } }),

  // ========== 甲面 ==========
  nailList: (params: { memberId?: number; storeId?: number; nailType?: string }) =>
    request.get('/crm/detection/nail/list', { params }),
  nailLatest: (memberId: number) => request.get(`/crm/detection/nail/latest/${memberId}`),
  nailCreate: (data: any) => request.post('/crm/detection/nail', data),
  nailUpdate: (data: any) => request.put(`/crm/detection/nail/${data.id}`, data),
  nailDelete: (id: number) => request.delete(`/crm/detection/nail/${id}`),
  nailStats: (storeId?: number) => request.get('/crm/detection/nail/statistics/type', { params: { storeId } }),

  // ========== 报告 ==========
  reportList: (params: { memberId?: number; storeId?: number; reportType?: string; status?: string }) =>
    request.get('/crm/detection/report/list', { params }),
  reportDetail: (id: number) => request.get(`/crm/detection/report/${id}`),
  reportGenerate: (data: { memberId: number; reportType: string }) =>
    request.post('/crm/detection/report/generate', data),
  reportUpdate: (data: any) => request.put(`/crm/detection/report/${data.id}`, data),
  reportDelete: (id: number) => request.delete(`/crm/detection/report/${id}`),

  // ========== 方案 ==========
  solutionList: (params: { applyType?: string; status?: number }) =>
    request.get('/crm/detection/solution/list', { params }),
  solutionCreate: (data: any) => request.post('/crm/detection/solution', data),
  solutionUpdate: (data: any) => request.put(`/crm/detection/solution/${data.id}`, data),
  solutionDelete: (id: number) => request.delete(`/crm/detection/solution/${id}`),

  // ========== 诊断 ==========
  diagnoseAll: (memberId: number) => request.get(`/crm/detection/diagnose/all/${memberId}`)
}

/**
 * 肤质类型
 */
export const skinTypeOptions = [
  { value: 'dry', label: '干性', color: '#FFB6C1', description: '水分<40,易紧绷' },
  { value: 'oily', label: '油性', color: '#FFD700', description: '油脂>60,易泛油' },
  { value: 'combination', label: '混合性', color: '#87CEEB', description: 'T区油U区干' },
  { value: 'sensitive', label: '敏感性', color: '#FF6B6B', description: '敏感度>50,易泛红' },
  { value: 'normal', label: '中性', color: '#90EE90', description: '水油平衡' },
  { value: 'mixed', label: '混合性偏干', color: '#DDA0DD', description: 'U区偏干' }
]

/**
 * 发质类型
 */
export const hairTypeOptions = [
  { value: 'dry', label: '干性', color: '#FFB6C1', description: '水分<40,毛躁' },
  { value: 'oily', label: '油性', color: '#FFD700', description: '油脂>60,贴头皮' },
  { value: 'combination', label: '混合性', color: '#87CEEB', description: '头皮油发尾干' },
  { value: 'damaged', label: '受损', color: '#FF6B6B', description: '染烫损伤' },
  { value: 'normal', label: '中性', color: '#90EE90', description: '健康' }
]

/**
 * 指甲类型
 */
export const nailTypeOptions = [
  { value: 'strong', label: '坚硬', color: '#90EE90', description: '硬度>70' },
  { value: 'soft', label: '柔软', color: '#FFB6C1', description: '硬度<50' },
  { value: 'brittle', label: '易脆', color: '#FF6B6B', description: '频繁断裂' },
  { value: 'peeling', label: '分层', color: '#DDA0DD', description: '甲层分离' }
]

/**
 * 报告类型
 */
export const reportTypeOptions = [
  { value: 'skin', label: '肤质报告', icon: 'MagicStick', color: '#FF6B9D' },
  { value: 'hair', label: '发质报告', icon: 'Scissor', color: '#FFB6C1' },
  { value: 'nail', label: '甲面报告', icon: 'Star', color: '#DDA0DD' },
  { value: 'comprehensive', label: '综合报告', icon: 'DataAnalysis', color: '#409EFF' }
]

/**
 * 报告状态
 */
export const reportStatusOptions = [
  { value: 'draft', label: '草稿', type: 'info' },
  { value: 'final', label: '已确认', type: 'success' },
  { value: 'shared', label: '已分享', type: 'warning' },
  { value: 'archived', label: '已归档', type: 'info' }
]

/**
 * 等级选项
 */
export const levelOptions = [
  { value: 0, label: '无', type: 'success' },
  { value: 1, label: '轻微', type: 'info' },
  { value: 2, label: '中度', type: 'warning' },
  { value: 3, label: '严重', type: 'danger' }
]

/**
 * 检测设备
 */
export const deviceOptions = [
  { value: 'visia', label: 'VISIA' },
  { value: 'wood_lamp', label: '伍德灯' },
  { value: 'microscope', label: '显微镜' },
  { value: 'moisture_meter', label: '水分仪' },
  { value: 'manual', label: '人工检测' }
]