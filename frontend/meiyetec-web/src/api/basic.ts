import request from './request'

export const storeApi = {
  list: () => request.get('/store/list'),
  page: (data: any) => request.post('/store/page', data),
  stats: () => request.get('/store/stats'),
  getById: (id: number) => request.get(`/store/${id}`),
  create: (data: any) => request.post('/store', data),
  update: (data: any) => request.put('/store', data),
  delete: (id: number) => request.delete(`/store/${id}`)
}

export const employeeApi = {
  list: (params?: { storeId?: number; positionType?: number }) => request.get('/employee/list', { params }),
  page: (data: any) => request.post('/employee/page', data),
  stats: () => request.get('/employee/stats'),
  getById: (id: number) => request.get(`/employee/${id}`),
  create: (data: any) => request.post('/employee', data),
  update: (data: any) => request.put('/employee', data),
  delete: (id: number) => request.delete(`/employee/${id}`),
  resetPassword: (id: number, password: string) => request.post(`/employee/${id}/reset-password`, { password })
}

export const memberLevelApi = {
  list: () => request.get('/member-level/list'),
  page: (data: any) => request.post('/member-level/page', data),
  stats: () => request.get('/member-level/stats'),
  getById: (id: number) => request.get(`/member-level/${id}`),
  create: (data: any) => request.post('/member-level', data),
  update: (data: any) => request.put('/member-level', data),
  delete: (id: number) => request.delete(`/member-level/${id}`)
}

export const categoryApi = {
  list: () => request.get('/category/list'),
  tree: () => request.get('/category/tree'),
  sort: (ids: number[]) => request.post('/category/sort', ids),
  getById: (id: number) => request.get(`/category/${id}`),
  create: (data: any) => request.post('/category', data),
  update: (data: any) => request.put('/category', data),
  delete: (id: number) => request.delete(`/category/${id}`)
}

export const memberApi = {
  list: () => request.get('/member/list'),
  page: (data: any) => request.post('/member/page', data),
  stats: () => request.get('/member/stats', { silent: true } as any),
  getById: (id: number) => request.get(`/member/${id}`),
  tabs: (id: number) => request.get(`/member/${id}/tabs`),
  orders: (id: number, data?: any) => request.get(`/member/${id}/orders`, { params: data || {} }),
  create: (data: any) => request.post('/member', data),
  update: (data: any) => request.put('/member', data),
  delete: (id: number) => request.delete(`/member/${id}`),
  freeze: (id: number) => request.post(`/member/${id}/freeze`, {}),
  unfreeze: (id: number) => request.post(`/member/${id}/unfreeze`, {}),
  saveProfile: (id: number, data: any) => request.post(`/member/${id}/profile`, data),
  batchTag: (data: any) => request.post('/member/batch/tag', data),
  batchOperation: (data: any) => request.post('/member/batch/operation', data),
  exportCsv: async (params: any): Promise<Blob> => {
    const qs = new URLSearchParams()
    Object.keys(params || {}).forEach(k => {
      if (params[k] !== undefined && params[k] !== null && params[k] !== '') {
        qs.append(k, String(params[k]))
      }
    })
    const token = localStorage.getItem('token') || ''
    const url = '/member/export' + (qs.toString() ? '?' + qs.toString() : '')
    const res = await fetch(url, {
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!res.ok) throw new Error(`导出失败 HTTP ${res.status}`)
    const blob = await res.blob()
    // 解析文件名
    const dispo = res.headers.get('Content-Disposition') || ''
    const m = dispo.match(/filename\*?=(?:UTF-8'')?["']?([^";]+)/i)
    return { blob, filename: m ? decodeURIComponent(m[1]) : 'members.csv' } as any
  },
  import: (formData: FormData) => request.post('/member/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const memberTagApi = {
  list: () => request.get('/member/tag/list'),
  create: (data: any) => request.post('/member/tag', data),
  update: (data: any) => request.put('/member/tag', data),
  delete: (id: number) => request.delete(`/member/tag/${id}`)
}

export const walletApi = {
  getBalance: (memberId: number) => request.get(`/wallet/${memberId}`),
  recharge: (data: any) => request.post('/wallet/recharge', data),
  consume: (data: any) => request.post('/wallet/consume', data),
  refund: (data: any) => request.post('/wallet/refund', data),
  freeze: (data: any) => request.post('/wallet/freeze', data),
  unfreeze: (data: any) => request.post('/wallet/unfreeze', data),
  transactions: (memberId: number, params: any) =>
    request.get(`/wallet/${memberId}/transactions`, { params }),
  allTransactions: (params: any) =>
    request.get('/wallet/transactions/all', { params }),
  promotionRules: () => request.get('/wallet/promotion/rules'),
  promotionCalc: (amount: number) => request.get('/wallet/promotion/calc', { params: { amount } })
}

export const bookingApi = {
  stats: () => request.get('/booking/stats', { silent: true } as any)
}

export const careApi = {
  pendingCount: () => Promise.resolve({ data: { pending: 0 } })
}

export const stockApi = {
  alertStats: () => request.get('/stock/alert/stats', { silent: true } as any)
}

export const reportApi = {
  salesDaily: (days = 7) => request.get('/report/biz/daily', { params: { days }, silent: true } as any)
}

