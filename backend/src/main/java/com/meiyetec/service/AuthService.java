package com.meiyetec.service;

import com.meiyetec.dto.LoginVO;
import com.meiyetec.dto.MenuNodeDTO;
import com.meiyetec.entity.SysPermission;
import com.meiyetec.entity.SysRole;
import com.meiyetec.entity.SysUser;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.SysPermissionMapper;
import com.meiyetec.mapper.SysRoleMapper;
import com.meiyetec.mapper.SysUserMapper;
import com.meiyetec.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final JwtUtil jwtUtil;
    private final SysTenantService tenantService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RbacService rbacService;

    public LoginVO login(String username, String password) {
        SysUser user = userMapper.getUserByUsername(username);
        if (user == null) {
            throw new BusinessException(BizCode.USERNAME_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException(BizCode.AUTH_USER_DISABLED);
        }

        // 校验所属租户是否可用（启用 + 未过期）
        tenantService.checkAvailable(user.getTenantId());

        if (password == null || password.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST.code, "密码不能为空");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("login failed: username={} (bcrypt mismatch)", username);
            throw new BusinessException(BizCode.PASSWORD_NOT_MATCH);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        List<SysRole> roles = roleMapper.getRolesByUserId(user.getId());
        List<SysPermission> permissions = permissionMapper.getPermissionsByUserId(user.getId());

        // 把菜单树也一起塞进登录响应里，前端 setFromLogin 拿来直接渲染侧边栏，
        // 避免再额外发 /auth/me/permissions 请求。
        // 同时给 SUPER_ADMIN / PLATFORM_ADMIN 注入通配权限码 *，与 RbacContextLoader 保持一致。
        List<String> permissionCodes = new ArrayList<>(permissions.stream()
                .map(SysPermission::getPermissionCode)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList()));
        boolean isSuper = roles.stream().anyMatch(r ->
                "SUPER_ADMIN".equals(r.getRoleCode()) || "PLATFORM_ADMIN".equals(r.getRoleCode()));
        if (isSuper) {
            permissionCodes.add("*");
        }

        LoginVO vo = new LoginVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setToken(token);
        vo.setRoles(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
        vo.setPermissions(permissionCodes);
        vo.setMenus(rbacService.buildMenuTree(user.getId()));
        return vo;
    }

    public SysUser getUserInfo(Long userId) {
        return userMapper.selectById(userId);
    }

    /**
     * 校验指定用户的密码是否正确（用于二次确认、修改密码等场景）。
     *
     * @param userId   当前操作用户的 ID（从登录态拿）
     * @param password 待校验的明文密码
     */
    public void checkPassword(Long userId, String password) {
        if (userId == null) {
            throw new BusinessException(BizCode.UNAUTHORIZED);
        }
        if (password == null || password.isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST.code, "密码不能为空");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BizCode.USERNAME_NOT_FOUND);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("checkPassword failed: userId={}", userId);
            throw new BusinessException(BizCode.PASSWORD_NOT_MATCH);
        }
    }
}