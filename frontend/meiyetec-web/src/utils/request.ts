/**
 * utils/request - 兼容 @/utils/request 和 @/utils/http 导入路径
 *
 * 项目代码中部分视图使用 `import request from '@/utils/request'`，
 * 部分使用 `import { http } from '@/utils/request'`，而 axios 实例实际
 * 定义在 `@/api/request` 中。这里做一层兼容转发。
 *
 * 不改变任何业务逻辑，仅满足模块解析路径。
 */
import request from '@/api/request'

export default request
export { request }
// 兼容 lifecycle 等视图的命名导入：`import { http } from '@/utils/request'`
export const http = request
