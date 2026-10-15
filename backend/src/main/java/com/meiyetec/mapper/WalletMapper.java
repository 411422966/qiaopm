package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.meiyetec.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface WalletMapper extends BaseMapper<Wallet> {

    /**
     * 原子更新余额（CAS 防止并发）
     * @return 影响行数 0/1
     */
    int updateBalance(@Param("memberId") Long memberId,
                      @Param("delta") BigDecimal delta,
                      @Param("rechargeDelta") BigDecimal rechargeDelta,
                      @Param("consumeDelta") BigDecimal consumeDelta,
                      @Param("version") Integer version);

    /**
     * 创建钱包（如果不存在）
     *
     * <p>P0-T03: 同步写入 tenant_id，上层调用方必须从
     * {@code TenantContextHolder.getTenantIdOrNull()} 取值。
     * ctx 缺失时禁止 fallback 到 1L，由调用方负责校验。
     */
    int insertIfAbsent(@Param("memberId") Long memberId,
                       @Param("tenantId") Long tenantId);

    /**
     * P0-TEST-001: SELECT ... FOR UPDATE 锁住钱包行（事务内）
     * 用于 casUpdate 第一步，避免纯乐观锁在高并发下活锁
     */
    Wallet selectByMemberIdForUpdate(@Param("memberId") Long memberId);

    /**
     * P0-M05: 冻结金额单调变化（CAS + version）
     * @param freezeDelta 正数=冻结，负数=解冻
     * @return 影响行数 0/1
     */
    int updateFrozenAmount(@Param("memberId") Long memberId,
                           @Param("freezeDelta") BigDecimal freezeDelta,
                           @Param("version") Integer version);
}
