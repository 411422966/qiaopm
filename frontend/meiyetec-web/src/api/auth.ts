import request from './request'

export function login(data: { username: string; password: string }) {
  return request.post('/auth/login', data)
}

export function logout(): Promise<unknown> {
  return request.post('/auth/logout')
}

/** 获取当前登录用户信息 */
export function getUserInfo(): Promise<{ data: any }> {
  return request.get('/auth/userinfo') as unknown as Promise<{ data: any }>
}
