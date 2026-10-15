import request from './request'

/**
 * P2-19 HIPAA 加固（后端 HipaaService @ /hipaa）
 */
export const hipaaApi = {
  // 加密密钥
  generateKey: (data: { alias: string; algorithm?: string }) => request.post('/hipaa/key', data),
  listKeys: () => request.get('/hipaa/key/list'),
  rotateKey: (id: number) => request.post(`/hipaa/key/${id}/rotate`),

  // PHI 访问控制
  logAccess: (data: any) => request.post('/hipaa/phi/access', data),
  listPhiAccess: (params?: { memberId?: number }) =>
    request.get('/hipaa/phi/list', { params }),

  // 加密 / 解密（演示）
  encrypt: (data: { plaintext: string; keyId?: number }) => request.post('/hipaa/encrypt', data),
  decrypt: (data: { ciphertext: string; keyId?: number }) => request.post('/hipaa/decrypt', data),

  // Dashboard
  dashboard: () => request.get('/hipaa/dashboard')
}