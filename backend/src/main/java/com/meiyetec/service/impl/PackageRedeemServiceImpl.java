package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PackageOrder;
import com.meiyetec.entity.PackageRedemption;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.PackageOrderMapper;
import com.meiyetec.mapper.PackageRedemptionMapper;
import com.meiyetec.service.PackageRedeemService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * B-2 · 套餐核销实现。
 *
 * <p>设计原则：
 * <ul>
 *   <li>幂等由调用方（PosController）通过 IdempotencyService 包装，本方法不重复幂等</li>
 *   <li>CAS 走 PackageOrderMapper.casRedeem（version + remainingTimes 双重守卫）</li>
 *   <li>事务边界：扣次数 + 写核销记录在同一事务内，任何一步失败均回滚</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PackageRedeemServiceImpl implements PackageRedeemService {

    private final PackageOrderMapper packageOrderMapper;
    private final PackageRedemptionMapper redemptionMapper;

    /** 套餐状态：已用完 */
    private static final int STATUS_USED     = 2;
    /** 套餐状态：已过期 */
    private static final int STATUS_EXPIRED  = 3;
    /** 套餐状态：已冻结 */
    private static final int STATUS_FROZEN   = 4;
    /** 套餐状态：已退款 */
    private static final int STATUS_REFUNDED = 5;
    /** 套餐状态：已转赠 */
    private static final int STATUS_TRANSFER = 6;
    /** 核销记录状态：正常 */
    private static final int REDEEM_STATUS_OK = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> redeem(Long orderId, Long packageOrderId,
                                              Integer times, Long tenantId,
                                              Long operatorId, Long storeId) {
        if (orderId == null || packageOrderId == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "orderId 和 packageOrderId 不能为空");
        }
        int redeemTimes = (times == null || times <= 0) ? 1 : times;

        // 1. 读取套餐订单（含 version，用于 CAS）
        PackageOrder po = packageOrderMapper.selectById(packageOrderId);
        if (po == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "套餐卡不存在");
        }

        // 2. 租户隔离校验
        if (tenantId != null && po.getTenantId() != null && !tenantId.equals(po.getTenantId())) {
            throw new BusinessException(BizCode.FORBIDDEN, "无权核销该套餐");
        }

        // 3. 状态合法性校验（CAS 成功前先过滤明显错误）
        if (po.getStatus() != null) {
            switch (po.getStatus()) {
                case STATUS_USED     -> throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已用完");
                case STATUS_EXPIRED  -> throw new BusinessException(BizCode.PACKAGE_EXPIRED, "该套餐已过期");
                case STATUS_FROZEN   -> throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已冻结");
                case STATUS_REFUNDED -> throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已退款");
                case STATUS_TRANSFER -> throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已转赠");
            }
        }
        // 4. 过期时间校验
        if (po.getExpireTime() != null && po.getExpireTime().isBefore(LocalDateTime.now())) {
            po.setStatus(STATUS_EXPIRED);
            packageOrderMapper.updateById(po);
            throw new BusinessException(BizCode.PACKAGE_EXPIRED, "该套餐已过期");
        }

        // 5. B-2 · CAS 扣减（version + remainingTimes 双重守卫，防止超卖）
        int rows = packageOrderMapper.casRedeem(packageOrderId, po.getVersion(), redeemTimes);
        if (rows == 0) {
            // CAS 失败：重读状态给用户明确错误信息
            PackageOrder now = packageOrderMapper.selectById(packageOrderId);
            if (now == null) {
                throw new BusinessException(BizCode.NOT_FOUND, "套餐卡不存在");
            }
            if (now.getRemainingTimes() == null || now.getRemainingTimes() < redeemTimes) {
                throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH,
                        "剩余次数不足，当前剩余 " + now.getRemainingTimes());
            }
            // 并发冲突（version 不匹配），让前端重试
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
                    "套餐核销并发冲突，请重试");
        }

        // 6. 重读最新状态（CAS 成功后）
        PackageOrder latest = packageOrderMapper.selectById(packageOrderId);
        int remainingAfter = latest.getRemainingTimes() == null ? 0 : latest.getRemainingTimes();

        // 7. 写核销记录
        PackageRedemption redemption = new PackageRedemption();
        redemption.setTenantId(tenantId);
        redemption.setOrderId(orderId);
        redemption.setStoreId(storeId);
        redemption.setMemberId(po.getMemberId());
        redemption.setPackageId(po.getPackageId());
        redemption.setTimes(redeemTimes);
        redemption.setCrossFee(BigDecimal.ZERO);
        redemption.setCrossRuleId(null);
        redemption.setOperatorId(operatorId);
        redemption.setRedeemTime(LocalDateTime.now());
        redemption.setStatus(REDEEM_STATUS_OK);
        redemptionMapper.insert(redemption);

        log.info("[package-redeem] success orderId={} packageOrderId={} times={} remaining={}",
                orderId, packageOrderId, redeemTimes, remainingAfter);

        // 8. 返回结果
        Map<String, Object> data = new HashMap<>();
        data.put("packageRedemptionId", redemption.getId());
        data.put("packageOrderId", packageOrderId);
        data.put("timesRedeemed", redeemTimes);
        data.put("remainingTimes", remainingAfter);
        data.put("memberId", po.getMemberId());
        return Result.success("套餐核销成功", data);
    }
}
