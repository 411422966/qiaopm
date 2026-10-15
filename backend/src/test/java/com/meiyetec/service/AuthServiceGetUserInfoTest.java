package com.meiyetec.service;

import com.meiyetec.entity.SysUser;
import com.meiyetec.mapper.SysPermissionMapper;
import com.meiyetec.mapper.SysRoleMapper;
import com.meiyetec.mapper.SysUserMapper;
import com.meiyetec.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AUTH-003 获取用户信息 — 单元测试
 *
 * 不启动 Spring：Mockito 替换所有依赖，直接测试 AuthService.getUserInfo(Long)。
 * 覆盖点：
 *  1) userId 存在 → 返回 SysUser 实体（透传 userMapper.selectById）
 *  2) userId 不存在 → 返回 null（不抛异常）
 *  3) userId=null → 返回 null
 *  4) 确认 service 把入参原样传给 mapper
 */
class AuthServiceGetUserInfoTest {

    private AuthService newService(SysUserMapper userMapper) {
        return new AuthService(
                userMapper,
                mock(SysRoleMapper.class),
                mock(SysPermissionMapper.class),
                mock(JwtUtil.class),
                mock(SysTenantService.class),
                mock(BCryptPasswordEncoder.class),
                mock(RbacService.class)
        );
    }

    @Test
    void getUserInfo_existingUser_returnsUser() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setRealName("系统管理员");
        u.setPassword("HASHED-PWD");
        u.setTenantId(1L);

        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectById(1L)).thenReturn(u);
        AuthService svc = newService(mapper);

        SysUser got = svc.getUserInfo(1L);

        assertNotNull(got);
        assertEquals(1L, got.getId());
        assertEquals("admin", got.getUsername());
        assertEquals("系统管理员", got.getRealName());
        verify(mapper).selectById(1L);
    }

    @Test
    void getUserInfo_missingUser_returnsNull() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectById(999L)).thenReturn(null);
        AuthService svc = newService(mapper);

        SysUser got = svc.getUserInfo(999L);

        assertNull(got);
        verify(mapper).selectById(999L);
    }

    @Test
    void getUserInfo_nullUserId_returnsNull() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectById((Long) null)).thenReturn(null);
        AuthService svc = newService(mapper);

        SysUser got = svc.getUserInfo(null);

        assertNull(got);
        verify(mapper).selectById((Long) null);
    }
}
