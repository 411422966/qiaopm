package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.AttendanceConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttendanceConfigMapper extends BaseMapper<AttendanceConfig> {

    @Select("SELECT * FROM attendance_config "
            + "WHERE tenant_id = #{tenantId} "
            + "AND (store_id = #{storeId} OR store_id IS NULL) AND status = 1 AND deleted = 0 "
            + "ORDER BY store_id DESC LIMIT 1")
    AttendanceConfig findActiveConfig(@Param("tenantId") Long tenantId,
                                      @Param("storeId") Long storeId);

    /**
     * 按门店集合取生效配置（I186 第 1 批：缺省 storeId 时按有效门店集合取，门店行优先、全局行兜底）。
     *
     * <p>SQL 落在 {@code resources/mapper/AttendanceConfigMapper.xml}（落法 B2）。
     * storeIds 为 null 表示「不限制」（ALL 范围，取租户内任意一条生效配置，门店行优先）；
     * 非空时取 {@code store_id IN (...) OR store_id IS NULL}；
     * 空范围传 {@code [-9999]} → 只命中本租户全局行。</p>
     */
    AttendanceConfig findActiveConfigByStores(@Param("tenantId") Long tenantId,
                                              @Param("storeIds") List<Long> storeIds);
}
