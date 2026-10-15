import request from './request'

/**
 * P3-22 插件市场（后端 PluginService @ /plugin）
 */
export const pluginApi = {
  // 上架管理
  publish: (data: any) => request.post('/plugin/market', data),
  listMarket: (params?: { category?: string; status?: string }) =>
    request.get('/plugin/market/list', { params }),
  updatePlugin: (id: number, data: any) => request.post(`/plugin/market/${id}`, data),

  // 安装
  install: (data: { pluginId: number; version?: string }) => request.post('/plugin/install', data),
  uninstall: (id: number) => request.post(`/plugin/install/${id}/uninstall`),
  listInstalls: () => request.get('/plugin/install/list'),

  // 评价
  review: (data: { pluginId: number; rating: number; comment?: string }) =>
    request.post('/plugin/review', data),
  listReviews: (pluginId: number) => request.get(`/plugin/review/list/${pluginId}`),

  // ===== EXT-23 Plugin Runtime =====
  listRuntime: () => request.get('/plugin/runtime/list'),
  dashboard: () => request.get('/plugin/runtime/dashboard'),
  loadPlugin: (code: string, version?: string) =>
    request.post(`/plugin/runtime/${code}/load`, version ? { version } : {}),
  enablePlugin: (code: string) => request.post(`/plugin/runtime/${code}/enable`),
  disablePlugin: (code: string) => request.post(`/plugin/runtime/${code}/disable`),
  unloadPlugin: (code: string) => request.post(`/plugin/runtime/${code}/unload`)
}