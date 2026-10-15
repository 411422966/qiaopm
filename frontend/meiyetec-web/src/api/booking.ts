import request from './request'

/**
 * M06 预约管理 API
 * - 端点全部对齐 BookingController / ScheduleController / RoomController
 */
export const bookingApi = {
  list: (params?: any) => request.get('/booking/list', { params }),
  page: (params: any) => request.get('/booking/page', { params }),
  getById: (id: number) => request.get(`/booking/${id}`),
  create: (data: any) => request.post('/booking', data),
  update: (data: any) => request.put(`/booking/${data.id}`, data),
  delete: (id: number) => request.delete(`/booking/${id}`),
  changeStatus: (id: number, status: number, reason?: string) =>
    request.put(`/booking/${id}/status`, null, { params: { status, reason } }),
  checkin:   (id: number) => request.post(`/booking/${id}/checkin`, {}),
  complete:  (id: number) => request.post(`/booking/${id}/complete`, {}),
  assign:    (id: number, employeeId: number) =>
    request.put(`/booking/${id}/employee`, null, { params: { employeeId } }),
  available: (employeeId: number, date: string) =>
    request.get('/booking/available', { params: { employeeId, date } })
}

export const scheduleApi = {
  list: (params?: any) => request.get('/schedule/list', { params }),
  page: (params: any) => request.get('/schedule/page', { params }),
  getById: (id: number) => request.get(`/schedule/${id}`),
  create: (data: any) => request.post('/schedule', data),
  update: (data: any) => request.put(`/schedule/${data.id}`, data),
  delete: (id: number) => request.delete(`/schedule/${id}`)
}

export const roomApi = {
  list: (params?: any) => request.get('/room/list', { params }),
  getById: (id: number) => request.get(`/room/${id}`),
  create: (data: any) => request.post('/room', data),
  update: (data: any) => request.put(`/room/${data.id}`, data),
  delete: (id: number) => request.delete(`/room/${id}`)
}