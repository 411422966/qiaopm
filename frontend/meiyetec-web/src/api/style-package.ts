import request from './request'

/**
 * 款式库与材料包 API
 */
export const styleApi = {
  // ========== 款式库 ==========
  styles: (params: { styleType?: string; styleTag?: string; difficultyLevel?: number; status?: number; isNew?: number; isHot?: number }) =>
    request.get('/style/library/list', { params }),
  hotStyles: () => request.get('/style/library/hot'),
  styleDetail: (id: number) => request.get(`/style/library/${id}`),
  createStyle: (data: any) => request.post('/style/library', data),
  updateStyle: (data: any) => request.put(`/style/library/${data.id}`, data),
  deleteStyle: (id: number) => request.delete(`/style/library/${id}`),
  recordView: (id: number) => request.post(`/style/library/${id}/view`, {}),
  recommend: (params: { memberId?: number; styleType?: string; limit?: number }) =>
    request.get('/style/library/recommend', { params }),
  styleStats: (storeId?: number) => request.get('/style/library/statistics/type', { params: { storeId } }),

  // ========== 客户偏好 ==========
  preference: (memberId: number) => request.get(`/style/preference/${memberId}`),
  updatePreference: (memberId: number, data: any) => request.put(`/style/preference/${memberId}`, data),
  recordBrowse: (memberId: number, styleId: number) =>
    request.post(`/style/preference/${memberId}/browse/${styleId}`, {}),
  toggleFavorite: (memberId: number, styleId: number) =>
    request.post(`/style/preference/${memberId}/favorite/${styleId}`, {}),

  // ========== 材料包 ==========
  packages: (params: { packageType?: string; status?: number }) =>
    request.get('/material/package/list', { params }),
  hotPackages: (limit?: number) => request.get('/material/package/hot', { params: { limit } }),
  packageDetail: (id: number) => request.get(`/material/package/${id}`),
  createPackage: (data: any) => request.post('/material/package', data),
  updatePackage: (data: any) => request.put(`/material/package/${data.id}`, data),
  deletePackage: (id: number) => request.delete(`/material/package/${id}`),
  replaceItems: (id: number, items: any[]) => request.put(`/material/package/${id}/items`, items),
  recalculate: (id: number) => request.post(`/material/package/${id}/recalculate`, {}),
  packageStats: (storeId?: number) => request.get('/material/package/statistics/type', { params: { storeId } })
}

/**
 * 款式类型
 */
export const styleTypeOptions = [
  { value: 'nail_art', label: '美甲', icon: 'MagicStick', color: '#FF6B9D' },
  { value: 'eyelash', label: '美睫', icon: 'Sunny', color: '#FFB6C1' },
  { value: 'hair_style', label: '发型', icon: 'Brush', color: '#9C27B0' },
  { value: 'makeup', label: '妆容', icon: 'BrushFilled', color: '#E91E63' }
]

/**
 * 风格标签
 */
export const styleTagOptions = [
  { value: '简约', label: '简约', color: '#909399' },
  { value: '复古', label: '复古', color: '#8B4513' },
  { value: '甜美', label: '甜美', color: '#FFB6C1' },
  { value: 'ins风', label: 'ins风', color: '#FFC0CB' },
  { value: 'OL', label: 'OL', color: '#1C1F23' },
  { value: '中式', label: '中式', color: '#DC143C' },
  { value: '日式', label: '日式', color: '#FF7F50' },
  { value: '韩系', label: '韩系', color: '#FFE4E1' },
  { value: '欧美', label: '欧美', color: '#4169E1' },
  { value: '梦幻', label: '梦幻', color: '#DDA0DD' },
  { value: '纯色', label: '纯色', color: '#FF6347' },
  { value: '法式', label: '法式', color: '#F0F8FF' }
]

/**
 * 色系选项
 */
export const colorSchemeOptions = [
  { value: '红色', color: '#FF0000' },
  { value: '粉色', color: '#FFC0CB' },
  { value: '裸色', color: '#FFE4C4' },
  { value: '黑色', color: '#000000' },
  { value: '白色', color: '#FFFFFF' },
  { value: '金色', color: '#FFD700' },
  { value: '银色', color: '#C0C0C0' },
  { value: '蓝色', color: '#4169E1' },
  { value: '绿色', color: '#2E8B57' },
  { value: '紫色', color: '#9370DB' },
  { value: '棕色', color: '#8B4513' },
  { value: '灰色', color: '#808080' },
  { value: '透明', color: '#F0F8FF' }
]

/**
 * 美甲形状
 */
export const nailShapeOptions = [
  { value: 'round', label: '圆形', icon: '○' },
  { value: 'square', label: '方形', icon: '□' },
  { value: 'oval', label: '椭圆', icon: '⬭' },
  { value: 'almond', label: '杏仁', icon: '◇' },
  { value: 'coffin', label: '棺材', icon: '⌷' },
  { value: 'stiletto', label: '尖形', icon: '⇧' }
]

/**
 * 长度选项
 */
export const lengthOptions = [
  { value: 'short', label: '短' },
  { value: 'medium', label: '中' },
  { value: 'long', label: '长' },
  { value: 'extra_long', label: '特长' }
]

/**
 * 难度等级
 */
export const difficultyOptions = [
  { value: 1, label: '入门', color: '#67C23A' },
  { value: 2, label: '初级', color: '#409EFF' },
  { value: 3, label: '中级', color: '#E6A23C' },
  { value: 4, label: '高级', color: '#F56C6C' },
  { value: 5, label: '专家', color: '#9C27B0' }
]

/**
 * 材料包类型
 */
export const packageTypeOptions = [
  { value: 'starter', label: '开业包', icon: 'Box', color: '#67C23A', description: '新店开业必备' },
  { value: 'kit', label: '套装', icon: 'GoodsFilled', color: '#409EFF', description: '专业套装' },
  { value: 'combo', label: '组合装', icon: 'Sell', color: '#E6A23C', description: '优惠组合' },
  { value: 'seasonal', label: '季节包', icon: 'Sunny', color: '#9C27B0', description: '季节性主题' },
  { value: 'gift', label: '礼品包', icon: 'Gift', color: '#F56C6C', description: '节日送礼' }
]