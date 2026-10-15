package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DcepSubWalletLedger;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DcepSubWalletLedgerMapper extends BaseMapper<DcepSubWalletLedger> {

    @Select("SELECT * FROM biz_dcep_sub_wallet_ledger WHERE sub_wallet_id = #{subWalletId} " +
            "AND (#{txType} IS NULL OR tx_type = #{txType}) " +
            "AND (#{startTime} IS NULL OR create_time >= #{startTime}) " +
            "AND (#{endTime} IS NULL OR create_time <= #{endTime}) " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<DcepSubWalletLedger> queryBySubWallet(
            @Param("subWalletId") Long subWalletId,
            @Param("txType") String txType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM biz_dcep_sub_wallet_ledger " +
            "WHERE sub_wallet_id = #{subWalletId} AND tx_type = #{txType} " +
            "AND create_time >= #{startTime} AND create_time < #{endTime}")
    BigDecimal sumAmount(
            @Param("subWalletId") Long subWalletId,
            @Param("txType") String txType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) FROM biz_dcep_sub_wallet_ledger WHERE sub_wallet_id = #{subWalletId}")
    long countBySubWallet(@Param("subWalletId") Long subWalletId);

    @Insert("INSERT INTO biz_dcep_sub_wallet_ledger " +
            "(tenant_id, sub_wallet_id, ledger_no, tx_type, amount, balance_before, balance_after, " +
            "fee, opposite_wallet_id, opposite_wallet_name, order_no, merchant_id, merchant_name, " +
            "operator_id, operator_name, remark, create_time) " +
            "VALUES (#{tenantId}, #{subWalletId}, #{ledgerNo}, #{txType}, #{amount}, " +
            "#{balanceBefore}, #{balanceAfter}, #{fee}, #{oppositeWalletId}, #{oppositeWalletName}, " +
            "#{orderNo}, #{merchantId}, #{merchantName}, #{operatorId}, #{operatorName}, #{remark}, NOW())")
    int insertLedger(DcepSubWalletLedger ledger);
}
