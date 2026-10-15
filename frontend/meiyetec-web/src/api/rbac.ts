import request from './request'

export interface MenuNode {
  id: number
  permissionCode: string
  title: string
  path: string
  component?: string
  redirect?: string
  icon?: string
  sortOrder?: number
  hidden?: number
  children?: MenuNode[]
}

export interface RbacSession {
  userId: number
  username: string
  tenantId: number | null
  dataScope: string
  storeIds: number[]
  roles: string[]
  permissions: string[]
  menus: MenuNode[]
}

export const rbacApi = {
  /** 拉取当前用户会话（含菜单、权限码、数据范围） */
  permissions: () => request.get('/auth/me/permissions'),
  /** 仅拉取菜单 */
  menu: () => request.get('/auth/me/menu'),
  /** 角色管理 */
  listRoles: () => request.get('/sys/role/list'),
  createRole: (data: any) => request.post('/sys/role', data),
  updateRole: (id: number, data: any) => request.put(`/sys/role/${id}`, data),
  deleteRole: (id: number) => request.delete(`/sys/role/${id}`),
  /** 权限分配 */
  listPermissions: () => request.get('/sys/permission/list'),
  getRolePermissions: (id: number) => request.get(`/sys/role/${id}/permissions`),
  assignPermissions: (id: number, permissionIds: number[]) =>
    request.put(`/sys/role/${id}/permissions`, { permissionIds })
}
