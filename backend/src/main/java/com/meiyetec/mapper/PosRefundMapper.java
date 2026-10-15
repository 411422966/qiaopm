package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.PosRefund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PosRefundMapper extends BaseMapper<PosRefund> {

    /**
     * 状态机迁移（带 version 乐观锁）
     */
    @Update("UPDATE pos_refund SET status = #{to}, version = version + 1, update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND version = #{version} AND status = #{from}")
    int transitStatus(@Param("id") Long id,
                      @Param("version") Integer version,
                      @Param("from") String from,
                      @Param("to") String to);
}
