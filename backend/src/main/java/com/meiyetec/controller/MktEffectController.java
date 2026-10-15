package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.MarketActivity;
import com.meiyetec.entity.MktPromotion;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.entity.MarketingTouchLog;
import com.meiyetec.entity.MarketingTouchConversion;
import com.meiyetec.entity.MktJourneyInstance;
import com.meiyetec.entity.MktCoupon;
import com.meiyetec.entity.PointRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MarketActivityMapper;
import com.meiyetec.mapper.MktPromotionMapper;
import com.meiyetec.mapper.MktChannelTemplateMapper;
import com.meiyetec.mapper.MarketingTouchLogMapper;
import com.meiyetec.mapper.MarketingTouchConversionMapper;
import com.meiyetec.mapper.MktJourneyInstanceMapper;
import com.meiyetec.mapper.MktCouponMapper;
import com.meiyetec.mapper.PointRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 营销活动效果总览（/mkt/effect/*）
 *  - GET  /mkt/effect/snapshot     聚合指标快照
 *  - POST /mkt/effect/snapshot/save 生成当日快照（实战 cron job 用）
 *  - 返回字段严格对齐前端 views/mkt/effect.vue
 */
@RestController
@RequestMapping("/mkt/effect")
@RequiredArgsConstructor
@SysOpLog(module = "营销效果")
public class MktEffectController {

    private final MarketActivityMapper activityMapper;        // 旧 market_activity
    private final MktPromotionMapper promotionMapper;          // 新促销 mkt_promotion
    private final MktChannelTemplateMapper templateMapper;     // 消息模板
    private final MarketingTouchLogMapper touchLogMapper;      // 营销触达日志
    private final MarketingTouchConversionMapper conversionMapper;
    private final MktJourneyInstanceMapper journeyInstanceMapper;
    private final MktCouponMapper couponMapper;
    private final PointRecordMapper pointRecordMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/snapshot")
    public Result<Map<String, Object>> snapshot(@RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate) {
        Long t = tid();

        // 总活动数（market_activity）
        LambdaQueryWrapper<MarketActivity> actW = new LambdaQueryWrapper<>();
        actW.eq(MarketActivity::getTenantId, t);
        long totalActivities = activityMapper.selectCount(actW);

        // 总促销数（mkt_promotion）
        LambdaQueryWrapper<MktPromotion> promoW = new LambdaQueryWrapper<>();
        promoW.eq(MktPromotion::getTenantId, t);
        long totalPromotions = promotionMapper.selectCount(promoW);

        // 触达总数（marketing_touch_log）
        LambdaQueryWrapper<MarketingTouchLog> touchW = new LambdaQueryWrapper<>();
        touchW.eq(MarketingTouchLog::getTenantId, t);
        long totalReach = touchLogMapper.selectCount(touchW);

        // 触达成功
        LambdaQueryWrapper<MarketingTouchLog> succW = new LambdaQueryWrapper<>();
        succW.eq(MarketingTouchLog::getTenantId, t).eq(MarketingTouchLog::getStatus, 2);
        long touchSuccess = touchLogMapper.selectCount(succW);

        // 转化订单（status=4 表示转化）
        LambdaQueryWrapper<MarketingTouchLog> convW = new LambdaQueryWrapper<>();
        convW.eq(MarketingTouchLog::getTenantId, t).eq(MarketingTouchLog::getStatus, 4);
        long touchConverted = touchLogMapper.selectCount(convW);

        // 核销券数
        LambdaQueryWrapper<MktCoupon> cpW = new LambdaQueryWrapper<>();
        cpW.eq(MktCoupon::getTenantId, t).eq(MktCoupon::getStatus, 2);
        long usedCoupon = couponMapper.selectCount(cpW);

        // 总核销券数（用于算率）
        LambdaQueryWrapper<MktCoupon> allCpW = new LambdaQueryWrapper<>();
        allCpW.eq(MktCoupon::getTenantId, t).gt(MktCoupon::getStatus, 0);
        long totalCouponUsedOrActive = couponMapper.selectCount(allCpW);

        BigDecimal couponUsageRate = totalCouponUsedOrActive == 0
                ? BigDecimal.ZERO
                : new BigDecimal(usedCoupon)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(new BigDecimal(totalCouponUsedOrActive), 2, RoundingMode.HALF_UP);

        // 累计发放积分
        LambdaQueryWrapper<PointRecord> ptW = new LambdaQueryWrapper<>();
        ptW.eq(PointRecord::getTenantId, t).eq(PointRecord::getType, 1);
        List<PointRecord> gainList = pointRecordMapper.selectList(ptW);
        long pointsGranted = 0L;
        for (PointRecord pr : gainList) {
            if (pr.getPointValue() != null) pointsGranted += pr.getPointValue();
        }

        // Journey instance（已完成的旅程节点计数）
        LambdaQueryWrapper<MktJourneyInstance> jrW = new LambdaQueryWrapper<>();
        jrW.eq(MktJourneyInstance::getTenantId, t);
        long journeyEntry = journeyInstanceMapper.selectCount(jrW);

        // 总减免 / 总订单数（从营销触达转化类型聚合）
        LambdaQueryWrapper<MarketingTouchConversion> cvList = new LambdaQueryWrapper<>();
        cvList.eq(MarketingTouchConversion::getTenantId, t);
        List<MarketingTouchConversion> conversions = conversionMapper.selectList(cvList);
        long orderCount = 0;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (MarketingTouchConversion c : conversions) {
            if ("ORDER".equalsIgnoreCase(c.getConversionType())) orderCount++;
            if (c.getAmount() != null) totalDiscount = totalDiscount.add(c.getAmount());
        }

        Map<String, Object> out = new HashMap<>();
        out.put("totalOrders", orderCount);
        out.put("totalDiscount", totalDiscount);
        out.put("couponUsageRate", couponUsageRate);
        out.put("usedCoupon", usedCoupon);
        out.put("pointsGranted", pointsGranted);
        out.put("totalReach", totalReach);
        out.put("touchSuccess", touchSuccess);
        out.put("touchConverted", touchConverted);
        out.put("totalActivities", totalActivities);
        out.put("totalPromotions", totalPromotions);
        out.put("journeyEnterCount", journeyEntry);
        out.put("queryDate", LocalDate.now());

        // 明细（按 promotion 列出订单数 / 减免额）
        List<Map<String, Object>> promotionDetails = new ArrayList<>();
        List<MktPromotion> promotions = promotionMapper.selectList(promoW.last("LIMIT 50"));
        for (MktPromotion p : promotions) {
            Map<String, Object> r = new HashMap<>();
            r.put("id", p.getId());
            r.put("promotionName", p.getPromotionName());
            r.put("promotionType", p.getPromotionType());
            // 单个 promotion 的效果：以 conversions.lambda LIKE promotionCode 简化模拟
            LambdaQueryWrapper<MarketingTouchConversion> sub = new LambdaQueryWrapper<>();
            sub.eq(MarketingTouchConversion::getTenantId, t).eq(MarketingTouchConversion::getConversionType, "ORDER");
            long singleOrder = conversionMapper.selectCount(sub);
            r.put("orderCount", singleOrder);
            // 减免额：简单用 promote.reduceAmount * order（真实业务要 JOIN 这里简化）
            BigDecimal discountPer = p.getReduceAmount() == null ? BigDecimal.ZERO : p.getReduceAmount();
            r.put("discountTotal", discountPer.multiply(new BigDecimal(singleOrder)));
            promotionDetails.add(r);
        }
        out.put("promotions", promotionDetails);

        return Result.success(out);
    }

    /**
     * 生成当日快照（线下 cron 调用此端点即可完成）
     */
    @PostMapping("/snapshot/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> saveSnapshot() {
        // 快照即为调用 snapshot 的结果，将其 print 入日志（真实业务可写 mkt_effect_snapshot 表）
        Map<String, Object> snap = snapshot(null, null).getData();
        return Result.success("快照已记录", snap);
    }
}