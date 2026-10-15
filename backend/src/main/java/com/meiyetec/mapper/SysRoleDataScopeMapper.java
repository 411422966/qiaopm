package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.SysRoleDataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleDataScopeMapper extends BaseMapper<SysRoleDataScope> {

    @Select("SELECT srd.* FROM sys_role_data_scope srd " +
            "INNER JOIN sys_user_role ur ON srd.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<SysRoleDataScope> listByUserId(@Param("userId") Long userId);
}
