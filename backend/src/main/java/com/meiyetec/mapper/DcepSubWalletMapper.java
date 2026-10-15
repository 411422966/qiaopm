package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DcepSubWallet;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DcepSubWalletMapper extends BaseMapper<DcepSubWallet> {

    @Select("SELECT * FROM biz_dcep_sub_wallet WHERE deleted = 0 " +
            "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) " +
            "AND (#{storeId} IS NULL OR store_id = #{storeId}) " +
            "AND (#{parentWalletId} IS NULL OR parent_wallet_id = #{parentWalletId}) " +
            "AND (#{walletType} IS NULL OR wallet_type = #{walletType}) " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "ORDER BY priority DESC, create_time DESC")
    List<DcepSubWallet> queryList(
            @Param("tenantId") Long tenantId,
            @Param("storeId") Long storeId,
            @Param("parentWalletId") Long parentWalletId,
            @Param("walletType") String walletType,
            @Param("status") String status);

    @Select("SELECT * FROM biz_dcep_sub_wallet WHERE deleted = 0 AND sub_wallet_code = #{code} LIMIT 1")
    DcepSubWallet findByCode(@Param("code") String code);

    @Update("UPDATE biz_dcep_sub_wallet SET " +
            "  balance = balance + #{delta}, " +
            "  total_in = total_in + #{delta}, " +
            "  update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int creditBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    @Update("UPDATE biz_dcep_sub_wallet SET " +
            "  balance = balance - #{delta}, " +
            "  total_out = total_out + #{delta}, " +
            "  update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0 AND balance >= #{delta}")
    int debitBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    @Update("UPDATE biz_dcep_sub_wallet SET " +
            "  frozen_balance = frozen_balance + #{delta}, " +
            "  update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int creditFrozen(@Param("id") Long id, @Param("delta") BigDecimal delta);

    @Update("UPDATE biz_dcep_sub_wallet SET " +
            "  frozen_balance = frozen_balance - #{delta}, " +
            "  update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0 AND frozen_balance >= #{delta}")
    int debitFrozen(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
