package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.StockDualVerifyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * P2-11 IND-05 双人核销记录 Mapper
 */
@Mapper
public interface StockDualVerifyRecordMapper extends BaseMapper<StockDualVerifyRecord> {
}
