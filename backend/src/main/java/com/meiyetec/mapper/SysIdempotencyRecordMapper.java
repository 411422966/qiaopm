package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.SysIdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysIdempotencyRecordMapper extends BaseMapper<SysIdempotencyRecord> {

    @Update("UPDATE sys_idempotency_record SET status = #{status}, biz_id = #{bizId}, " +
            "response_code = #{responseCode}, response_message = #{responseMessage}, " +
            "response_data = #{responseData}, retry_count = retry_count + 1, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND status = 'PROCESSING'")
    int finish(@Param("id") Long id,
               @Param("status") String status,
               @Param("bizId") Long bizId,
               @Param("responseCode") Integer responseCode,
               @Param("responseMessage") String responseMessage,
               @Param("responseData") String responseData);
}
