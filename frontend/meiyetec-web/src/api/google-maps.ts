import request from './request'

/**
 * 补齐-P2-20 Google Maps 集成
 */
export const googleMapsApi = {
  // 配置
  saveConfig: (data: { apiKey: string; defaultZoom?: number; enablePlaceDetails?: number }) =>
    request.post('/google-maps/config', data),
  getConfig: () => request.get('/google-maps/config'),

  // 门店标注
  geocodeStore: (data: { storeId: number; address: string }) => request.post('/google-maps/geocode', data),
  listStores: () => request.get('/google-maps/stores'),

  // Place Details（EXT-22 计费接口）
  getPlaceDetails: (data: { placeId: string; language?: string }) =>
    request.post('/google-maps/place/details', data),

  // 客户分布
  getMemberHeatmap: () => request.get('/google-maps/heatmap')
}