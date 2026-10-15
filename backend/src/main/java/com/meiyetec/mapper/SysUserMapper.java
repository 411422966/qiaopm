package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser getUserByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND deleted = 0")
    List<SysUser> listByTenantId(@Param("tenantId") Long tenantId);
}