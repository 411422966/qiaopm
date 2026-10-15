package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ServiceMaterialRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ServiceMaterialRuleMapper extends BaseMapper<ServiceMaterialRule> {

    @Select("SELECT r.*, s.service_name AS serviceName, p.product_name AS productName, p.spec "
            + "FROM biz_service_material_rule r "
            + "LEFT JOIN service_item s ON r.service_id = s.id "
            + "LEFT JOIN stock_product p ON r.product_id = p.id "
            + "WHERE r.deleted = 0 "
            + "AND (#{serviceId} IS NULL OR r.service_id = #{serviceId}) "
            + "AND (#{productId} IS NULL OR r.product_id = #{productId}) "
            + "ORDER BY r.id DESC")
    List<ServiceMaterialRule> queryList(@Param("serviceId") Long serviceId,
                                        @Param("productId") Long productId);

    @Select("SELECT r.*, s.service_name AS serviceName, p.product_name AS productName, p.spec, p.category "
            + "FROM biz_service_material_rule r "
            + "LEFT JOIN service_item s ON r.service_id = s.id "
            + "LEFT JOIN stock_product p ON r.product_id = p.id "
            + "WHERE r.deleted = 0 AND r.service_id = #{serviceId}")
    List<ServiceMaterialRule> findByServiceId(@Param("serviceId") Long serviceId);
}
