package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.BizServiceMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BizServiceMaterialMapper extends BaseMapper<BizServiceMaterial> {

    /**
     * 取一次服务的全部 BOM（包含 unit / productId / quantity）
     * 关键查询：用 LEFT JOIN 把 stock_product 的库存单位带回来，供展示
     */
    @Select("SELECT m.id, m.tenant_id, m.service_id, m.product_id, m.quantity, m.unit AS bom_unit, " +
            "       m.is_required, m.remark, " +
            "       p.product_code, p.product_name, p.category, p.spec, p.unit AS product_unit, " +
            "       p.cost_price, p.sale_price, p.min_stock, p.max_stock, p.status AS product_status, " +
            "       IFNULL((SELECT SUM(quantity) FROM stock_inventory i WHERE i.product_id = m.product_id AND i.deleted = 0), 0) stock_qty " +
            "FROM biz_service_material m " +
            "JOIN stock_product p ON p.id = m.product_id AND p.deleted = 0 " +
            "WHERE m.deleted = 0 AND m.service_id = #{serviceId}")
    List<java.util.Map<String, Object>> listBomByService(@Param("serviceId") Long serviceId);

    /**
     * 取服务 BOM（仅 product_id + quantity，供消耗计算）
     */
    @Select("SELECT product_id, quantity FROM biz_service_material WHERE deleted = 0 AND service_id = #{serviceId}")
    List<java.util.Map<String, Object>> listSimpleBom(@Param("serviceId") Long serviceId);
}
