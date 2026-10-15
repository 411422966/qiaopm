package com.meiyetec.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.CouponGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface CouponGrantMapper extends BaseMapper<CouponGrant> {

    IPage<CouponGrant> selectMemberCoupons(Page<?> page, @Param("memberId") Long memberId, @Param("status") Integer status);

    IPage<CouponGrant> selectMemberCouponsByTenant(Page<?> page, @Param("memberId") Long memberId,
                                                   @Param("status") Integer status,
                                                   @Param("tenantId") Long tenantId);

    int countUsed(@Param("couponId") Long couponId);

    int countGranted(@Param("couponId") Long couponId);

    /**
     * B-4 · 原子发放优惠券。
     * 总量超额或重复发放均返回 0 行，由调用方识别后抛友好错误。
     *
     * @return 1=新增发放成功；0=未插入（总量满 或 该会员已有记录）
     */
    int atomicGrantCoupon(@Param("couponId") Long couponId,
                          @Param("memberId") Long memberId,
                          @Param("tenantId") Long tenantId,
                          @Param("code") String code,
                          @Param("source") Integer source,
                          @Param("expireTime") LocalDateTime expireTime);

    /**
     * P0-MONEY-005 · 原子发放优惠券（条件 UPDATE 版本）。
     * 只修改 market_coupon.issued_count，返回影响行数：
     *   1 = 成功（总量未满 且 该会员未达限领）
     *   0 = 条件不满足
     */
    int atomicGrant(@Param("couponId") Long couponId, @Param("memberId") Long memberId);

    /**
     * B-5 · 原子核销优惠券（CAS version + status 双重守卫）。
     *
     * @return 1=核销成功；0=CAS 失败（status 不匹配 / 已过期 / version 已变更）
     */
    int casUseCoupon(@Param("grantId") Long grantId,
                     @Param("orderId") Long orderId,
                     @Param("version") Integer version);
}
