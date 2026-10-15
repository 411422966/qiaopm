package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.ExpiryHandleLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExpiryHandleLogMapper extends BaseMapper<ExpiryHandleLog> {

    @Select("SELECT * FROM biz_expiry_handle_log WHERE deleted = 0 "
            + "AND (#{handleMethod} IS NULL OR handle_method = #{handleMethod}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "ORDER BY create_time DESC")
    List<ExpiryHandleLog> queryList(@Param("handleMethod") String handleMethod,
                                   @Param("storeId") Long storeId);

    @Select("SELECT handle_method AS handleMethod, "
            + "COUNT(*) AS count, "
            + "SUM(handle_quantity) AS total_quantity, "
            + "SUM(loss_amount) AS total_loss "
            + "FROM biz_expiry_handle_log "
            + "WHERE deleted = 0 "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "AND (#{startDate} IS NULL OR handle_date >= #{startDate}) "
            + "AND (#{endDate} IS NULL OR handle_date <= #{endDate}) "
            + "GROUP BY handle_method")
    List<Map<String, Object>> statisticsByMethod(@Param("storeId") Long storeId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    @Select("SELECT COALESCE(SUM(loss_amount), 0) AS total_loss FROM biz_expiry_handle_log "
            + "WHERE deleted = 0 AND (#{storeId} IS NULL OR store_id = #{storeId})")
    BigDecimal totalLoss(@Param("storeId") Long storeId);
}
