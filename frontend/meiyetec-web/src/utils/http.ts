/**
 * 通用 axios 工具，代理自 @/api/request（共享 baseURL / 拦截器 / 401 跳转逻辑）。
 * 用于 views 中需要直发 URL 而不走 api/* 包装函数的场景。
 */
import request from '@/api/request'

const http = request

export default http
