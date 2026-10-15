package com.meiyetec.controller;

import com.meiyetec.dto.RbacSessionDTO;
import com.meiyetec.dto.RoleUpsertDTO;
import com.meiyetec.entity.SysRole;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.RbacService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RBAC 会话 / 角色 / 权限码接口。
 * 典型请求：
 * <pre>
 *  GET  /auth/me/permissions   - 拉取菜单 + 权限码 + 角色
 *  GET  /sys/role/list         - 平台管理员查看角色
 *  POST /sys/role              - 新增角色
 *  PUT  /sys/role/{id}         - 更新角色
 *  DELETE /sys/role/{id}       - 删除角色
 * </pre>
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    @GetMapping("/auth/me/permissions")
    public Result<RbacSessionDTO> getMyPermissions() {
        Long userId = TenantContextHolder.require().getUserId();
        return Result.success(rbacService.loadSession(userId));
    }

    @GetMapping("/sys/role/list")
    public Result<List<SysRole>> listRoles() {
        return Result.success(rbacService.listRoles());
    }

    @PostMapping("/sys/role")
    public Result<SysRole> createRole(@RequestBody RoleUpsertDTO dto) {
        return Result.success("创建成功", rbacService.createRole(dto));
    }

    @PutMapping("/sys/role/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody RoleUpsertDTO dto) {
        rbacService.updateRole(id, dto);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/sys/role/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        rbacService.deleteRole(id);
        return Result.success("删除成功", null);
    }

    /**
     * 兼容老接口：登录后返回 LoginVO.permissions（已在前端 /auth/login 流程中提供）。
     * 该接口用作登录后再次拉取权限码（前端刷新或权限变更后）。
     */
    @GetMapping("/auth/me/menu")
    public Result<Object> getMyMenu() {
        Long userId = TenantContextHolder.require().getUserId();
        return Result.success(rbacService.buildMenuTree(userId));
    }
}
