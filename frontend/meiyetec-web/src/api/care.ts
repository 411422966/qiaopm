import request from './request'

/**
 * P0-2 配方/护理建议（对应后端 CareSuggestionService @ /care）
 * 模块：配方管理 / 皮肤检测 / 智能推荐 / 跟进追踪
 */
export const careApi = {
  // 配方 CRUD
  createRecipe: (data: any) => request.post('/care/recipe', data),
  updateRecipe: (id: number, data: any) => request.post(`/care/recipe/${id}`, data),
  listRecipes: (params?: { skinType?: string; concern?: string }) =>
    request.get('/care/recipe/list', { params }),
  getRecipe: (id: number) => request.get(`/care/recipe/${id}`),
  initDefaultRecipes: () => request.post('/care/recipe/init-default'),

  // 配方步骤
  addRecipeItem: (recipeId: number, data: any) =>
    request.post(`/care/recipe/${recipeId}/item`, data),

  // 皮肤检测
  submitAssessment: (data: any) => request.post('/care/assessment', data),
  historyByMember: (memberId: number) =>
    request.get(`/care/assessment/by-member/${memberId}`),

  // 推荐（基于肤质 + 问题）
  recommend: (data: { skinType?: string; concerns?: string }) =>
    request.post('/care/recommend', data)
}