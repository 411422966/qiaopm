-- =============================================================================
-- I137-P0  服务项目写操作权限码绑定
--
-- 背景：I137-P0 修复后，BizServiceItemService 的写端点统一挂
--       @RequirePermission("biz:service-item:edit")。
--       但 sys_permission 中 biz:service-item:edit (id=1440) 之前 **没有任何角色绑定**，
--       导致菜单可见的 ADMIN/BOSS/MANAGER 点击保存反而 403（伪保护变真误伤）。
--
-- 决策：biz:service-item:edit 绑定给「持有 biz.service-item 菜单」的同一批角色：
--       ADMIN / BOSS / MANAGER / SUPER_ADMIN
--       —— 与 sys_permission id=1233 (biz.service-item, menu) 的角色集合保持一致。
--
-- 不绑定 CLERK / OPERATOR / STORE_ADMIN：
--       这三个角色只有列表按钮码 biz:service-item:list (id=1439)，
--       但没有菜单入口（菜单/按钮角色集合不一致 = I137-P1，本批不修）。
--       写操作必须拒绝，operator01 实测必须 403。
--
-- 幂等：INSERT IGNORE + 唯一键 uk_role_permission(role_id, permission_id)
-- =============================================================================

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'biz:service-item:edit'
WHERE r.role_code IN ('ADMIN', 'BOSS', 'MANAGER', 'SUPER_ADMIN')
  AND r.deleted = 0
  AND p.deleted = 0;

-- 自检：期望 4 行（ADMIN / BOSS / MANAGER / SUPER_ADMIN）
-- SELECT r.role_code, p.permission_code
-- FROM sys_role_permission rp
-- JOIN sys_role r ON r.id = rp.role_id
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE p.permission_code = 'biz:service-item:edit';
