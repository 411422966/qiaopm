package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.AttendanceConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttendanceConfigMapper extends BaseMapper<AttendanceConfig> {

    @Select("SELECT * FROM attendance_config "
            + "WHERE tenant_id = #{tenantId} "
            + "AND (store_id = #{storeId} OR store_id IS NULL) AND status = 1 AND deleted = 0 "
            + "ORDER BY store_id DESC LIMIT 1")
    AttendanceConfig findActiveConfig(@Param("tenantId") Long tenantId,
                                      @Param("storeId") Long storeId);
}