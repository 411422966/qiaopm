package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MaterialPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialPackageMapper extends BaseMapper<MaterialPackage> {

    @Select("SELECT * FROM biz_material_package "
            + "WHERE deleted = 0 "
            + "AND (#{packageType} IS NULL OR package_type = #{packageType}) "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "ORDER BY sales_count DESC, create_time DESC")
    List<MaterialPackage> queryList(@Param("packageType") String packageType,
                                    @Param("status") Integer status);

    @Select("SELECT * FROM biz_material_package WHERE deleted = 0 AND status = 1 "
            + "ORDER BY sales_count DESC LIMIT #{limit}")
    List<MaterialPackage> findHotPackages(@Param("limit") Integer limit);

    @Select("SELECT package_type AS packageType, "
            + "COUNT(*) AS count, "
            + "SUM(sales_count) AS total_sales, "
            + "SUM(final_price * sales_count) AS total_revenue "
            + "FROM biz_material_package "
            + "WHERE deleted = 0 AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "GROUP BY package_type")
    List<Map<String, Object>> statisticsByType(@Param("storeId") Long storeId);
}
