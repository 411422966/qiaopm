package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DcepOfflineTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DcepOfflineTransactionMapper extends BaseMapper<DcepOfflineTransaction> {

    @Select("SELECT * FROM biz_dcep_offline_transaction " +
            "WHERE deleted = 0 " +
            "AND (#{walletId} IS NULL OR wallet_id = #{walletId}) " +
            "AND (#{syncStatus} IS NULL OR sync_status = #{syncStatus}) " +
            "AND (#{merchantId} IS NULL OR merchant_id = #{merchantId}) " +
            "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) " +
            "ORDER BY offline_tx_time DESC")
    List<DcepOfflineTransaction> queryList(@Param("walletId") Long walletId,
                                           @Param("syncStatus") String syncStatus,
                                           @Param("merchantId") String merchantId,
                                           @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM biz_dcep_offline_transaction WHERE deleted = 0 AND offline_tx_no = #{txNo} LIMIT 1")
    DcepOfflineTransaction findByTxNo(@Param("txNo") String txNo);

    @Update("UPDATE biz_dcep_offline_transaction SET sync_status = #{status}, sync_time = NOW(), sync_attempts = sync_attempts + 1 WHERE id = #{id}")
    void updateSyncStatus(@Param("id") Long id, @Param("status") String status);
}
