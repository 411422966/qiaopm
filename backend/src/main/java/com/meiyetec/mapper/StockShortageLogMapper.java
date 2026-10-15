package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.StockShortageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockShortageLogMapper extends BaseMapper<StockShortageLog> {

    @Select("SELECT * FROM biz_stock_shortage_log "
            + "WHERE deleted = 0 "
            + "AND (#{status} IS NULL OR status = #{status}) "
            + "AND (#{storeId} IS NULL OR store_id = #{storeId}) "
            + "ORDER BY create_time DESC")
    List<StockShortageLog> queryList(@Param("status") String status,
                                     @Param("storeId") Long storeId);
}
