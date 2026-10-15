package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.SysTenantConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysTenantConfigMapper extends BaseMapper<SysTenantConfig> {

    @Select("SELECT * FROM sys_tenant_config WHERE tenant_id = #{tenantId} ORDER BY id")
    List<SysTenantConfig> listByTenantId(@Param("tenantId") Long tenantId);
}
