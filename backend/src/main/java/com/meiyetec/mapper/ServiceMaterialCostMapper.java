package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ServiceMaterialCost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ServiceMaterialCostMapper extends BaseMapper<ServiceMaterialCost> {

    @Select("SELECT bs.id AS service_id, bs.service_name, bs.price AS service_price, "
            + "COALESCE(SUM(sm.quantity * sp.cost_price), 0) AS material_cost, "
            + "COUNT(sm.id) AS item_count, "
            + "SUM(CASE WHEN sm.is_required = 1 THEN 1 ELSE 0 END) AS required_count "
            + "FROM biz_service bs "
            + "LEFT JOIN biz_service_material sm ON bs.id = sm.service_id AND sm.deleted = 0 "
            + "LEFT JOIN stock_product sp ON sm.product_id = sp.id "
            + "WHERE bs.deleted = 0 AND bs.status = 1 "
            + "GROUP BY bs.id, bs.service_name, bs.price "
            + "ORDER BY material_cost DESC")
    List<Map<String, Object>> getServiceCostAnalysis();

    @Select("SELECT bs.id AS service_id, bs.service_name, bs.price AS service_price, "
            + "COALESCE(SUM(sm.quantity * sp.cost_price), 0) AS material_cost, "
            + "COUNT(sm.id) AS item_count "
            + "FROM biz_service bs "
            + "LEFT JOIN biz_service_material sm ON bs.id = sm.service_id AND sm.deleted = 0 "
            + "LEFT JOIN stock_product sp ON sm.product_id = sp.id "
            + "WHERE bs.deleted = 0 AND bs.status = 1 AND bs.id = #{serviceId} "
            + "GROUP BY bs.id, bs.service_name, bs.price")
    Map<String, Object> getServiceCost(@Param("serviceId") Long serviceId);
}
