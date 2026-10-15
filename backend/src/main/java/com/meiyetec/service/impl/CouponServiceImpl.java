package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.Coupon;
import com.meiyetec.entity.CouponGrant;
import com.meiyetec.entity.Member;
import com.meiyetec.mapper.CouponGrantMapper;
import com.meiyetec.mapper.CouponMapper;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.service.CouponService;
import com.meiyetec.util.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class CouponServiceImpl implements CouponService {

    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private CouponGrantMapper couponGrantMapper;
    @Autowired
    private MemberMapper memberMapper;

    @Override
    public IPage<Map<String, Object>> couponPage(int pageNum, int pageSize, String name, Integer status) {
        Page<Coupon> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Coupon> q = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) q.like(Coupon::getName, name);
        if (status != null) q.eq(Coupon::getStatus, status);
        q.orderByDesc(Coupon::getCreatedAt);
        
        IPage<Coupon> result = couponMapper.selectPage(page, q);
        
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("typeText", typeText(c.getType()));
            m.put("ruleType", c.getRuleType());
            m.put("ruleValue", c.getRuleValue());
            m.put("minAmount", c.getMinAmount());
            m.put("maxDiscount", c.getMaxDiscount());
            m.put("totalCount", c.getTotalCount());
            m.put("perLimit", c.getPerLimit());
            m.put("startTime", c.getStartTime());
            m.put("endTime", c.getEndTime());
            m.put("status", c.getStatus());
            m.put("statusText", c.getStatus() == 1 ? "启用" : "禁用");
            m.put("grantCount", couponGrantMapper.countGranted(Long.valueOf(c.getId().toString())));
            m.put("usedCount", couponGrantMapper.countUsed(Long.valueOf(c.getId().toString())));
            return m;
        }).toList());
        return mapPage;
    }

    @Override
    public Map<String, Object> getCoupon(Long id) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) throw new RuntimeException("优惠券不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("type", c.getType());
        m.put("ruleType", c.getRuleType());
        m.put("ruleValue", c.getRuleValue());
        m.put("minAmount", c.getMinAmount());
        m.put("maxDiscount", c.getMaxDiscount());
        m.put("totalCount", c.getTotalCount());
        m.put("perLimit", c.getPerLimit());
        m.put("startTime", c.getStartTime());
        m.put("endTime", c.getEndTime());
        m.put("status", c.getStatus());
        m.put("remark", c.getRemark());
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCoupon(Map<String, Object> params) {
        Coupon c = new Coupon();
        c.setStoreId(1L);
        c.setName((String) params.get("name"));
        c.setType(Integer.parseInt(params.get("type").toString()));
        c.setRuleType(Integer.parseInt(params.get("ruleType").toString()));
        c.setRuleValue(new BigDecimal(params.get("ruleValue").toString()));
        c.setMinAmount(params.get("minAmount") != null ? new BigDecimal(params.get("minAmount").toString()) : BigDecimal.ZERO);
        if (params.get("maxDiscount") != null) c.setMaxDiscount(new BigDecimal(params.get("maxDiscount").toString()));
        c.setTotalCount(Integer.parseInt(params.get("totalCount").toString()));
        c.setPerLimit(params.get("perLimit") != null ? Integer.parseInt(params.get("perLimit").toString()) : 1);
        c.setStartTime(LocalDateTime.parse(params.get("startTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        c.setEndTime(LocalDateTime.parse(params.get("endTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        c.setStatus(1);
        c.setRemark((String) params.get("remark"));
        couponMapper.insert(c);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCoupon(Long id, Map<String, Object> params) {
        Coupon c = couponMapper.selectById(id);
        if (c == null) throw new RuntimeException("优惠券不存在");
        if (params.get("name") != null) c.setName((String) params.get("name"));
        if (params.get("ruleType") != null) c.setRuleType(Integer.parseInt(params.get("ruleType").toString()));
        if (params.get("ruleValue") != null) c.setRuleValue(new BigDecimal(params.get("ruleValue").toString()));
        if (params.get("minAmount") != null) c.setMinAmount(new BigDecimal(params.get("minAmount").toString()));
        if (params.get("maxDiscount") != null) c.setMaxDiscount(new BigDecimal(params.get("maxDiscount").toString()));
        if (params.get("totalCount") != null) c.setTotalCount(Integer.parseInt(params.get("totalCount").toString()));
        if (params.get("perLimit") != null) c.setPerLimit(Integer.parseInt(params.get("perLimit").toString()));
        if (params.get("startTime") != null) c.setStartTime(LocalDateTime.parse(params.get("startTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (params.get("endTime") != null) c.setEndTime(LocalDateTime.parse(params.get("endTime").toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (params.get("status") != null) c.setStatus(Integer.parseInt(params.get("status").toString()));
        if (params.get("remark") != null) c.setRemark((String) params.get("remark"));
        couponMapper.updateById(c);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCoupon(Long id) {
        couponMapper.deleteById(id);
    }

    @Override
    public IPage<Map<String, Object>> memberCoupons(Long memberId, Integer status, int pageNum, int pageSize) {
        Page<?> page = new Page<>(pageNum, pageSize);
        IPage<CouponGrant> result = couponGrantMapper.selectMemberCoupons(page, memberId, status);
        
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(cg -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", cg.getId());
            m.put("couponId", cg.getCouponId());
            m.put("couponName", cg.getCouponName());
            m.put("code", cg.getCode());
            m.put("status", cg.getStatus());
            m.put("statusText", statusText(cg.getStatus()));
            m.put("source", cg.getSource());
            m.put("expireTime", cg.getExpireTime());
            m.put("createdAt", cg.getCreatedAt());
            m.put("usedAt", cg.getUsedAt());
            return m;
        }).toList());
        return mapPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> grantCoupon(Long couponId, Long memberId, Integer source) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new RuntimeException("优惠券不存在");
        if (coupon.getStatus() != 1) throw new RuntimeException("优惠券已禁用");
        if (coupon.getStartTime().isAfter(LocalDateTime.now())) throw new RuntimeException("优惠券未开始");
        if (coupon.getEndTime().isBefore(LocalDateTime.now())) throw new RuntimeException("优惠券已过期");
        
        int granted = couponGrantMapper.countGranted(couponId);
        if (granted >= coupon.getTotalCount()) throw new RuntimeException("优惠券已发完");
        
        LambdaQueryWrapper<CouponGrant> q = new LambdaQueryWrapper<>();
        q.eq(CouponGrant::getMemberId, memberId).eq(CouponGrant::getCouponId, couponId);
        long own = couponGrantMapper.selectCount(q);
        if (own >= coupon.getPerLimit()) throw new RuntimeException("该优惠券每人限领" + coupon.getPerLimit() + "张");
        
        CouponGrant grant = new CouponGrant();
        grant.setCouponId(couponId);
        grant.setMemberId(memberId);
        grant.setCode(Keys.randomCode(12));
        grant.setStatus(1);
        grant.setSource(source != null ? source : 1);
        grant.setExpireTime(coupon.getEndTime());
        couponGrantMapper.insert(grant);
        
        Map<String, Object> result = new HashMap<>();
        result.put("grantId", grant.getId());
        result.put("code", grant.getCode());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public int batchGrantByLevel(Map<String, Object> params) {
        Long couponId = ((Number) params.get("couponId")).longValue();
        java.util.List<Number> rawIds = (java.util.List<Number>) params.get("levelIds");
        int count = params.get("count") == null ? 1 : ((Number) params.get("count")).intValue();

        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) throw new RuntimeException("优惠券不存在");
        if (coupon.getStatus() != 1) throw new RuntimeException("优惠券已禁用");

        java.util.List<Long> levelIds = rawIds == null ? java.util.Collections.emptyList()
                : rawIds.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());

        LambdaQueryWrapper<Member> q = new LambdaQueryWrapper<>();
        q.eq(Member::getStatus, 1);
        if (!levelIds.isEmpty()) q.in(Member::getLevelId, levelIds);
        java.util.List<Member> members = memberMapper.selectList(q);
        if (members.isEmpty()) return 0;

        int success = 0;
        for (Member m : members) {
            try {
                for (int i = 0; i < count; i++) {
                    CouponGrant grant = new CouponGrant();
                    grant.setCouponId(couponId);
                    grant.setMemberId(m.getId());
                    grant.setCode(Keys.randomCode(12));
                    grant.setStatus(1);
                    grant.setSource(5);
                    grant.setExpireTime(coupon.getEndTime());
                    couponGrantMapper.insert(grant);
                }
                success++;
            } catch (Exception ignored) {}
        }
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> useCoupon(Long grantId, Long orderId) {
        CouponGrant grant = couponGrantMapper.selectById(grantId);
        if (grant == null) throw new RuntimeException("优惠券不存在");
        // B-5: 过期判定移到 CAS（数据库侧守卫），让原子 UPDATE 一起处理
        if (grant.getStatus() != null && grant.getStatus() != 1) {
            throw new RuntimeException("优惠券状态异常（已使用/已过期）");
        }
        Integer currentVersion = grant.getVersion() == null ? 0 : grant.getVersion();

        // B-5 · 原子 CAS：status=1 + version=? + expire_time>NOW() 同时满足才核销
        int rows = couponGrantMapper.casUseCoupon(grantId, orderId, currentVersion);
        if (rows == 0) {
            // CAS 失败 → 重读最新状态给用户友好错误
            CouponGrant latest = couponGrantMapper.selectById(grantId);
            if (latest == null) throw new RuntimeException("优惠券不存在");
            if (latest.getExpireTime() != null && latest.getExpireTime().isBefore(LocalDateTime.now())) {
                // 顺手把过期态刷成 3（CAS 失败说明守卫没通过，标记为过期）
                if (latest.getStatus() == 1) {
                    latest.setStatus(3);
                    latest.setVersion((latest.getVersion() == null ? 0 : latest.getVersion()) + 1);
                    couponGrantMapper.updateById(latest);
                }
                throw new RuntimeException("优惠券已过期");
            }
            throw new RuntimeException("优惠券已被使用或并发冲突，请重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("grantId", grantId);
        result.put("orderId", orderId);
        return result;
    }

    @Override
    public Integer getMemberCouponCount(Long memberId) {
        LambdaQueryWrapper<CouponGrant> q = new LambdaQueryWrapper<>();
        q.eq(CouponGrant::getMemberId, memberId).eq(CouponGrant::getStatus, 1);
        return couponGrantMapper.selectCount(q).intValue();
    }

    private String typeText(Integer type) {
        return switch (type) {
            case 1 -> "满减券";
            case 2 -> "折扣券";
            case 3 -> "礼品券";
            default -> "未知";
        };
    }

    private String statusText(Integer status) {
        return switch (status) {
            case 1 -> "未使用";
            case 2 -> "已使用";
            case 3 -> "已过期";
            default -> "未知";
        };
    }
}
