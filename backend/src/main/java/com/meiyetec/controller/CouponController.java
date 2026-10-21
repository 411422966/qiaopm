package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.Coupon;
import com.meiyetec.entity.CouponGrant;
import com.meiyetec.entity.Member;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.CouponGrantMapper;
import com.meiyetec.mapper.CouponMapper;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import com.meiyetec.util.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 优惠券模板 controller（/market/coupon/*）
 *
 * 与 MktCouponController（/mkt/coupon/*）并存：前者管「模板 + grant」，后者管「批次 + 实例」。
 * 所有端点都加 tenantId 隔离（拒绝跨租户访问）。
 */
@RestController
@RequestMapping("/market/coupon")
@RequiredArgsConstructor
@SysOpLog(module = "卡券")
public class CouponController {

    private final CouponMapper couponMapper;
    private final CouponGrantMapper couponGrantMapper;
    private final MemberMapper memberMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        return t;
    }

    @GetMapping("/page")
    @RequirePermission("market:coupon:list")
    public Result<PageResult<Map<String, Object>>> couponPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        Long t = tid();
        LambdaQueryWrapper<Coupon> q = new LambdaQueryWrapper<>();
        q.eq(Coupon::getTenantId, t);
        if (name != null && !name.isBlank()) q.like(Coupon::getName, name);
        if (status != null) q.eq(Coupon::getStatus, status);
        q.orderByDesc(Coupon::getCreatedAt);
        Page<Coupon> result = couponMapper.selectPage(new Page<>(pageNum, pageSize), q);
        List<Map<String, Object>> records = result.getRecords().stream().map(c -> {
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
            m.put("grantCount", couponGrantMapper.countGranted(c.getId()));
            m.put("usedCount", couponGrantMapper.countUsed(c.getId()));
            return m;
        }).collect(Collectors.toList());
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(records);
        return Result.success(PageResult.of(mapPage));
    }

    @GetMapping("/{id}")
    @RequirePermission("market:coupon:list")
    public Result<Map<String, Object>> getCoupon(@PathVariable Long id) {
        Long t = tid();
        Coupon c = couponMapper.selectById(id);
        if (c == null || !t.equals(c.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
        }
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
        return Result.success(m);
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("market:coupon:add")
    public Result<Coupon> createCoupon(@RequestBody Map<String, Object> params) {
        Long t = tid();
        if (params.get("name") == null || params.get("name").toString().isBlank()) {
            return Result.error(BizCode.BAD_REQUEST, "name 必填");
        }
        if (params.get("type") == null) return Result.error(BizCode.BAD_REQUEST, "type 必填");
        if (params.get("ruleType") == null) return Result.error(BizCode.BAD_REQUEST, "ruleType 必填");
        if (params.get("ruleValue") == null) return Result.error(BizCode.BAD_REQUEST, "ruleValue 必填");
        if (params.get("totalCount") == null) return Result.error(BizCode.BAD_REQUEST, "totalCount 必填");
        if (params.get("startTime") == null) return Result.error(BizCode.BAD_REQUEST, "startTime 必填");
        if (params.get("endTime") == null) return Result.error(BizCode.BAD_REQUEST, "endTime 必填");

        Coupon c = new Coupon();
        if (params.get("storeId") != null) c.setStoreId(((Number) params.get("storeId")).longValue());
        else c.setStoreId(1L);
        c.setTenantId(t);
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
        return Result.success("创建成功", c);
    }

    @PutMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("market:coupon:edit")
    public Result<Coupon> updateCoupon(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Long t = tid();
        Coupon c = couponMapper.selectById(id);
        if (c == null || !t.equals(c.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
        }
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
        return Result.success("更新成功", c);
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("market:coupon:delete")
    public Result<Void> deleteCoupon(@PathVariable Long id) {
        Long t = tid();
        Coupon c = couponMapper.selectById(id);
        if (c == null || !t.equals(c.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
        }
        // 检查是否已发放：已发放的不能删
        int granted = couponGrantMapper.countGranted(id);
        if (granted > 0) {
            throw new BusinessException(BizCode.CONFLICT,
                    "该优惠券已发放 " + granted + " 张，不能直接删除，请先作废所有发放记录");
        }
        couponMapper.deleteById(id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/member/{memberId}")
    @RequirePermission("market:coupon:list")
    public Result<PageResult<Map<String, Object>>> memberCoupons(
            @PathVariable Long memberId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long t = tid();
        // memberId 必须属于当前租户（防越权）
        Member m = memberMapper.selectById(memberId);
        if (m == null || (m.getTenantId() != null && !t.equals(m.getTenantId()))) {
            throw new BusinessException(BizCode.NOT_FOUND, "会员不存在");
        }
        Page<CouponGrant> page = new Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.metadata.IPage<CouponGrant> result =
                couponGrantMapper.selectMemberCouponsByTenant(page, memberId, status, t);
        List<Map<String, Object>> records = result.getRecords().stream().map(cg -> {
            Map<String, Object> m2 = new HashMap<>();
            m2.put("id", cg.getId());
            m2.put("couponId", cg.getCouponId());
            m2.put("couponName", cg.getCouponName());
            m2.put("code", cg.getCode());
            m2.put("status", cg.getStatus());
            m2.put("statusText", statusText(cg.getStatus()));
            m2.put("source", cg.getSource());
            m2.put("expireTime", cg.getExpireTime());
            m2.put("createdAt", cg.getCreatedAt());
            m2.put("usedAt", cg.getUsedAt());
            return m2;
        }).collect(Collectors.toList());
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(records);
        return Result.success(PageResult.of(mapPage));
    }

    @PostMapping("/grant")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("market:coupon:add")
    public Result<Map<String, Object>> grantCoupon(
            @RequestParam Long couponId,
            @RequestParam Long memberId,
            @RequestParam(required = false, defaultValue = "1") Integer source) {
        Long t = tid();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !t.equals(coupon.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
        }
        if (coupon.getStatus() != 1) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券已禁用");
        }
        if (coupon.getStartTime() != null && coupon.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券未开始");
        }
        if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券已过期");
        }

        // 校验 member 跨租户
        Member member = memberMapper.selectById(memberId);
        if (member == null || (member.getTenantId() != null && !t.equals(member.getTenantId()))) {
            throw new BusinessException(BizCode.NOT_FOUND, "会员不存在");
        }

        // P0-MONEY-005: 用条件 UPDATE 做原子发放，把"超发"和"重复"收敛在数据库侧，
        // 避免 check-then-insert 的 TOCTOU race（并发通过校验导致 issued_count 超发）。
        int updated = couponGrantMapper.atomicGrant(couponId, memberId);
        if (updated == 0) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券已达发放上限或每人限领");
        }

        // UPDATE 成功后插入发放记录
        String code = Keys.randomCode(12);
        CouponGrant grant = new CouponGrant();
        grant.setCouponId(couponId);
        grant.setMemberId(memberId);
        grant.setTenantId(t);
        grant.setCode(code);
        grant.setStatus(1);
        grant.setSource(source);
        grant.setExpireTime(coupon.getEndTime());
        couponGrantMapper.insert(grant);

        Map<String, Object> result = new HashMap<>();
        result.put("grantId", grant.getId());
        result.put("code", code);
        return Result.success("发放成功", result);
    }

    @PostMapping("/batch-grant-by-level")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("market:coupon:add")
    public Result<Integer> batchGrantByLevel(@RequestBody Map<String, Object> params) {
        Long t = tid();
        Long couponId = ((Number) params.get("couponId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) params.get("levelIds");
        int count = params.get("count") == null ? 1 : ((Number) params.get("count")).intValue();

        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || !t.equals(coupon.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
        }
        if (coupon.getStatus() != 1) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券已禁用");
        }

        List<Long> levelIds = rawIds == null ? java.util.Collections.emptyList()
                : rawIds.stream().map(Number::longValue).collect(Collectors.toList());

        LambdaQueryWrapper<Member> q = new LambdaQueryWrapper<>();
        q.eq(Member::getStatus, 1);
        if (!levelIds.isEmpty()) q.in(Member::getLevelId, levelIds);
        // 多租户安全：若 member 表带 tenantId 列，加 .eq(Member::getTenantId, t)
        try {
            Member.class.getDeclaredField("tenantId");
            q.eq(Member::getTenantId, t);
        } catch (NoSuchFieldException ignored) {
            // 老 member 实体无 tenantId，跳过（仅历史数据）
        }
        List<Member> members = memberMapper.selectList(q);
        if (members.isEmpty()) return Result.success(0);

        int success = 0;
        for (Member mb : members) {
            try {
                for (int i = 0; i < count; i++) {
                    CouponGrant grant = new CouponGrant();
                    grant.setCouponId(couponId);
                    grant.setMemberId(mb.getId());
                    grant.setTenantId(t);
                    grant.setCode(Keys.randomCode(12));
                    grant.setStatus(1);
                    grant.setSource(5);
                    grant.setExpireTime(coupon.getEndTime());
                    couponGrantMapper.insert(grant);
                }
                success++;
            } catch (Exception ignored) {
                // 单条失败不阻塞整批
            }
        }
        return Result.success(success);
    }

    @PostMapping("/use")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("market:coupon:edit")
    public Result<Map<String, Object>> useCoupon(
            @RequestParam Long grantId,
            @RequestParam(required = false) Long orderId) {
        Long t = tid();
        CouponGrant grant = couponGrantMapper.selectById(grantId);
        if (grant == null || (grant.getTenantId() != null && !t.equals(grant.getTenantId()))) {
            throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
        }
        if (grant.getStatus() != 1) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券状态异常（已使用或已过期）");
        }
        if (grant.getExpireTime() != null && grant.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(BizCode.CONFLICT, "优惠券已过期");
        }

        // P0-MONEY-005 · B-5: 核销改用 CAS 乐观锁，防止并发核销导致次数多扣
        int rows = couponGrantMapper.casUseCoupon(grant.getId(), orderId, grant.getVersion());
        if (rows == 0) {
            // CAS 失败：可能被其他请求核销了，再查一次给用户友好提示
            CouponGrant latest = couponGrantMapper.selectById(grantId);
            if (latest != null && latest.getStatus() == 2) {
                throw new BusinessException(BizCode.CONFLICT, "优惠券已被核销");
            }
            if (latest != null && latest.getStatus() == 3) {
                throw new BusinessException(BizCode.CONFLICT, "优惠券已过期");
            }
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "优惠券核销并发冲突，请重试");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("grantId", grant.getId());
        return Result.success("核销成功", result);
    }

    @GetMapping("/member/{memberId}/count")
    @RequirePermission("market:coupon:list")
    public Result<Integer> memberCouponCount(@PathVariable Long memberId) {
        Long t = tid();
        LambdaQueryWrapper<CouponGrant> q = new LambdaQueryWrapper<>();
        q.eq(CouponGrant::getMemberId, memberId)
                .eq(CouponGrant::getTenantId, t)
                .eq(CouponGrant::getStatus, 1);
        return Result.success(couponGrantMapper.selectCount(q).intValue());
    }

    private String typeText(Integer type) {
        if (type == null) return "未知";
        return switch (type) {
            case 1 -> "满减券";
            case 2 -> "折扣券";
            case 3 -> "礼品券";
            default -> "未知";
        };
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 1 -> "未使用";
            case 2 -> "已使用";
            case 3 -> "已过期";
            default -> "未知";
        };
    }
}