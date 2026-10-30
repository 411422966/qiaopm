package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.StockInventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface StockInventoryMapper extends BaseMapper<StockInventory> {

    /**
     * FEFO 候选批次：按 expiry_date ASC, id ASC 排序
     */
    @Select("SELECT * FROM stock_inventory " +
            "WHERE product_id = #{productId} AND store_id = #{storeId} " +
            "  AND deleted = 0 AND quantity > 0 " +
            "ORDER BY COALESCE(expiry_date, '9999-12-31') ASC, id ASC")
    List<StockInventory> listFefoCandidates(@Param("productId") Long productId,
                                             @Param("storeId") Long storeId);

    /**
     * 库存批次 CAS 扣减（quantity >= #{qty} + version）
     */
    @Update("UPDATE stock_inventory " +
            "SET quantity = quantity - #{qty}, " +
            "    version = version + 1, " +
            "    last_out_time = CURRENT_TIMESTAMP, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND quantity >= #{qty} " +
            "  AND deleted = 0")
    int casOutbound(@Param("id") Long id,
                    @Param("version") Integer version,
                    @Param("qty") Integer qty);

    /**
     * 库存批次入库（quantity = quantity + #{qty} + version）
     */
    @Update("UPDATE stock_inventory " +
            "SET quantity = quantity + #{qty}, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int casInbound(@Param("id") Long id, @Param("qty") Integer qty);

    /**
     * 盘点 CAS 调整：quantity + #{delta} >= 0
     */
    @Update("UPDATE stock_inventory " +
            "SET quantity = quantity + #{delta}, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND quantity + #{delta} >= 0 " +
            "  AND deleted = 0")
    int casAdjust(@Param("id") Long id,
                  @Param("version") Integer version,
                  @Param("delta") Integer delta);

    @Select("SELECT si.*, p.product_name AS product_name, p.category " +
            "FROM stock_inventory si " +
            "LEFT JOIN stock_product p ON si.product_id = p.id " +
            "WHERE si.deleted = 0 AND si.store_id = #{storeId} " +
            "  AND si.expiry_date IS NOT NULL " +
            "  AND si.expiry_date <= DATE_ADD(CURDATE(), INTERVAL #{withinDays} DAY) " +
            "ORDER BY si.expiry_date ASC")
    List<StockInventory> findNearExpiry(@Param("storeId") Long storeId, @Param("withinDays") Integer withinDays);

    /**
     * P0-M06: 单条库存详情（耗材自动扣减用）
     */
    @Select("SELECT si.*, p.product_name AS product_name, p.category, p.cost_price " +
            "FROM stock_inventory si " +
            "LEFT JOIN stock_product p ON si.product_id = p.id " +
            "WHERE si.id = #{id} AND si.deleted = 0")
    java.util.Map<String, Object> findInventoryById(@Param("id") Long id);

    /**
     * P0-M06: 耗材扣减（乐观锁扣到指定余量），返回影响行数 0/1
     * 注意：#{newQty} 必须 >= 0，由 service 层在调用前保证
     */
    @Update("UPDATE stock_inventory " +
            "SET quantity = #{newQty}, " +
            "    version = version + 1, " +
            "    last_out_time = CURRENT_TIMESTAMP, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} " +
            "  AND version = #{version} " +
            "  AND quantity >= #{newQty} " +
            "  AND deleted = 0")
    int deductWithOptimisticLock(@Param("id") Long id,
                                 @Param("version") Integer version,
                                 @Param("newQty") Integer newQty);

    /**
     * P0-M06: 耗材回滚（订单取消 / 服务取消时，把已扣数量还回去）
     */
    @Update("UPDATE stock_inventory " +
            "SET quantity = quantity + #{qty}, " +
            "    version = version + 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND deleted = 0")
    int rollbackDeduct(@Param("id") Long id, @Param("qty") Integer qty);
}
