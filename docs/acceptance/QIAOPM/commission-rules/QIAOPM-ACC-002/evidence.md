# QIAOPM-ACC-002 提成规则 403 验收记录

- 时间：2026-10-23
- 环境：本地 Docker（http://localhost:8088）
- 账号：boss01 / 123456
- 操作：登录 → 点击左侧“提成规则”菜单
- 预期：进入提成规则页面
- 实际：跳转 403 页面，提示“您没有访问该页面的权限（权限码：commission:rule:config）”
- 账号实际权限：commission.rules | commission.settlement | commission | commission.salary | commission.records（均为菜单级权限，无按钮级权限）
- 截图：boss01-before-fix-403.png