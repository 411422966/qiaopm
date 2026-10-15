package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.DcepHardwareWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DcepHardwareWalletMapper extends BaseMapper<DcepHardwareWallet> {

    @Select("SELECT * FROM biz_dcep_hardware_wallet " +
            "WHERE deleted = 0 " +
            "AND (#{memberId} IS NULL OR member_id = #{memberId}) " +
            "AND (#{walletType} IS NULL OR wallet_type = #{walletType}) " +
            "AND (#{status} IS NULL OR status = #{status}) " +
            "AND (#{tenantId} IS NULL OR tenant_id = #{tenantId}) " +
            "ORDER BY create_time DESC")
    List<DcepHardwareWallet> queryList(@Param("memberId") Long memberId,
                                       @Param("walletType") String walletType,
                                       @Param("status") String status,
                                       @Param("tenantId") Long tenantId);

    @Select("SELECT * FROM biz_dcep_hardware_wallet WHERE deleted = 0 AND wallet_code = #{code} LIMIT 1")
    DcepHardwareWallet findByCode(@Param("code") String walletCode);

    @Update("UPDATE biz_dcep_hardware_wallet SET balance = balance + #{amount}, total_recharge = total_recharge + #{amount}, last_sync_time = NOW() WHERE id = #{id}")
    void rechargeBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);

    @Update("UPDATE biz_dcep_hardware_wallet SET balance = balance - #{amount}, total_consumption = total_consumption + #{amount} WHERE id = #{id}")
    void deductBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
}
