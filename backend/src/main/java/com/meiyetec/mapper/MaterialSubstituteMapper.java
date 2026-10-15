package com.meiyetec.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.MaterialSubstitute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface MaterialSubstituteMapper extends BaseMapper<MaterialSubstitute> {
    /**
     * 查询主耗材的所有替代耗材（按租户过滤）
     */
    @Select("SELECT ms.*, sp1.product_name AS main_product_name, sp2.product_name AS substitute_product_name " +
            "FROM biz_material_substitute ms " +
            "LEFT JOIN stock_product sp1 ON ms.main_product_id = sp1.id " +
            "LEFT JOIN stock_product sp2 ON ms.substitute_product_id = sp2.id " +
            "WHERE ms.deleted = 0 AND ms.tenant_id = #{tenantId} AND ms.status = 1 AND ms.main_product_id = #{mainProductId} " +
            "ORDER BY ms.priority ASC")
    List<MaterialSubstitute> findByMainProduct(@Param("mainProductId") Long mainProductId,
                                               @Param("tenantId") Long tenantId);

    /**
     * 查询所有替代规则（按租户过滤）
     */
    @Select("SELECT ms.*, sp1.product_name AS main_product_name, sp2.product_name AS substitute_product_name " +
            "FROM biz_material_substitute ms " +
            "LEFT JOIN stock_product sp1 ON ms.main_product_id = sp1.id " +
            "LEFT JOIN stock_product sp2 ON ms.substitute_product_id = sp2.id " +
            "WHERE ms.deleted = 0 AND ms.tenant_id = #{tenantId} " +
            "ORDER BY ms.main_product_id, ms.priority ASC")
    List<MaterialSubstitute> findAllWithDetails(@Param("tenantId") Long tenantId);
}