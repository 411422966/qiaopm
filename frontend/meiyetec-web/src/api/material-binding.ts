import request from './request'

/**
 * 耗材管理 API —— 后端 MaterialSubstituteController (/material/*)
 *
 * 覆盖范围：
 *   /material/binding/*   服务-耗材 BOM 绑定（ServiceMaterialDeduct）
 *   /material/group/*     用量分组（ServiceMaterialGroup）
 *   /material/substitute/* 替代规则（MaterialSubstitute）
 *   /material/consumption/* 消耗记录（ServiceMaterialDeductLog / MaterialConsumptionLog）
 *   /material/cost/*      成本分析（ServiceMaterialCost）
 */
export const materialApi = {
  // ========== 服务-耗材 BOM 绑定 ==========
  /** 查询服务的耗材列表 */
  materialsByService: (serviceId: number) =>
    request.get(`/material/binding/by-service/${serviceId}`),

  /** 分页查询绑定 */
  materialPage: (params: any) => request.post('/material/binding/page', params),

  /** 创建绑定 */
  createMaterial: (data: any) => request.post('/material/binding', data),

  /** 更新绑定 */
  updateMaterial: (data: any) => request.put(`/material/binding/${data.id}`, data),

  /** 删除绑定 */
  deleteMaterial: (id: number) => request.delete(`/material/binding/${id}`),

  // ========== 用量分组 ==========
  groupList: (serviceId?: number) =>
    request.get('/material/group/list', { params: { serviceId } }),

  createGroup: (data: any) => request.post('/material/group', data),
  updateGroup: (data: any) => request.put(`/material/group/${data.id}`, data),
  deleteGroup: (id: number) => request.delete(`/material/group/${id}`),

  // ========== 替代规则 ==========
  substituteList: () => request.get('/material/substitute/list'),
  substitutesByMain: (mainProductId: number) =>
    request.get(`/material/substitute/by-main/${mainProductId}`),
  createSubstitute: (data: any) => request.post('/material/substitute', data),
  updateSubstitute: (data: any) => request.put(`/material/substitute/${data.id}`, data),
  deleteSubstitute: (id: number) => request.delete(`/material/substitute/${id}`),

  // ========== 消耗记录 ==========
  consumptionByOrder: (orderId: number) =>
    request.get(`/material/consumption/by-order/${orderId}`),

  statisticsByProduct: (params: {
    storeId?: number
    startDate?: string
    endDate?: string
  }) => request.get('/material/consumption/statistics/by-product', { params }),

  statisticsByService: (params: {
    storeId?: number
    startDate?: string
    endDate?: string
  }) => request.get('/material/consumption/statistics/by-service', { params }),

  // ========== 成本分析 ==========
  costAnalysis: () => request.get('/material/cost/analysis'),
  serviceCost: (serviceId: number) =>
    request.get(`/material/cost/by-service/${serviceId}`)
}

/**
 * 维度类型选项
 */
export const dimensionOptions = [
  { value: 'employee_level', label: '技师等级', description: '不同等级技师用量不同' },
  { value: 'customer_type', label: '客户类型', description: '不同客户类型用量不同' },
  { value: 'service_duration', label: '服务时长', description: '按时长差异化用量' }
]

/**
 * 触发条件选项
 */
export const triggerOptions = [
  { value: 'stock_low', label: '库存不足', description: '当主耗材库存低于阈值时' },
  { value: 'price_high', label: '价格过高', description: '当主耗材采购价过高时' },
  { value: 'manual', label: '手动启用', description: '仅手动选择时使用' }
]

/**
 * 耗材类型选项
 */
export const materialTypeOptions = [
  { value: 'required', label: '必需耗材', type: 'success', icon: 'Star' },
  { value: 'optional', label: '可选耗材', type: 'info', icon: 'StarFilled' }
]