package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DcepSmartContract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DcepSmartContractMapper extends BaseMapper<DcepSmartContract> {

    @Select("SELECT * FROM biz_dcep_smart_contract " +
            "WHERE deleted = 0 " +
            "AND (#{contractType} IS NULL OR contract_type = #{contractType}) " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "AND (#{walletId} IS NULL OR wallet_id = #{walletId}) " +
            "ORDER BY create_time DESC")
    List<DcepSmartContract> queryList(@Param("contractType") String contractType,
                                      @Param("status") String status,
                                      @Param("walletId") Long walletId);

    @Select("SELECT * FROM biz_dcep_smart_contract WHERE deleted = 0 AND status = 'active' " +
            "AND expired_time IS NOT NULL AND expired_time <= NOW()")
    List<DcepSmartContract> findExpiredActive();
}
