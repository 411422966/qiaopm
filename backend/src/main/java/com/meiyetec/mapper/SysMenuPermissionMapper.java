package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.SysMenuPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuPermissionMapper extends BaseMapper<SysMenuPermission> {

    @Select("SELECT smp.* FROM sys_menu_permission smp " +
            "INNER JOIN sys_permission p ON smp.permission_id = p.id " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} " +
            "  AND p.permission_type = 'menu' " +
            "  AND p.deleted = 0 " +
            "  AND (smp.hidden IS NULL OR smp.hidden = 0) " +
            "GROUP BY smp.id " +
            "ORDER BY smp.sort_order ASC")
    List<SysMenuPermission> listVisibleMenuByUserId(@Param("userId") Long userId);
}
