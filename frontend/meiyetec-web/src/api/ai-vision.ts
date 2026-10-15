import request from './request'

/**
 * P2-15 AI 视觉识别算法（对应后端 AIVisionService @ /ai/vision）
 * 模块：模型管理 / 皮肤检测 / 美甲试戴 / 人脸识别 / 统计
 */
export const aiVisionApi = {
  // 模型管理
  createModel: (data: any) => request.post('/ai/vision/model', data),
  listModels: () => request.get('/ai/vision/model/list'),
  initDefaultModels: () => request.post('/ai/vision/model/init-default'),

  // 识别调用
  recognizeSkin: (data: { imageUrl: string; memberId?: number }) =>
    request.post('/ai/vision/recognize/skin', data),
  recognizeFace: (data: { imageUrl: string; memberId?: number }) =>
    request.post('/ai/vision/recognize/face', data),
  nailTryon: (data: { handImageUrl: string; styleImageUrl: string; memberId?: number }) =>
    request.post('/ai/vision/recognize/nail-tryon', data),

  // 日志 / 统计
  listLogs: (params?: { bizType?: string }) => request.get('/ai/vision/log/list', { params }),
  stats: () => request.get('/ai/vision/stats')
}