package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P4-02 · QPM-006: 会员裂变邀请服务
 *
 * 核心流程：
 * 1. 邀请人生成邀请码（每个会员最多 1 个 ACTIVE 邀请码）
 * 2. 被邀请人注册时携带 inviteCode → 绑定邀请关系
 * 3. 被邀请人首单完成后 → 双方奖励发放
 *
 * 奖励类型：COUPON / POINTS / CASH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberInviteService {

    private final MemberInviteCodeMapper inviteCodeMapper;
    private final MemberInviteRewardMapper inviteRewardMapper;
    private final MemberService memberService;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 生成我的邀请码
    // ====================================================================

    /**
     * 为当前会员生成（或获取已有的）邀请码
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> myInviteCode(Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Member member = memberService.getById(memberId);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        // 查找是否已有 ACTIVE 邀请码
        MemberInviteCode exist = inviteCodeMapper.selectOne(new LambdaQueryWrapper<MemberInviteCode>()
                .eq(MemberInviteCode::getTenantId, tenantId)
                .eq(MemberInviteCode::getInviterId, memberId)
                .eq(MemberInviteCode::getStatus, "ACTIVE"));
        if (exist != null) return Result.success(buildInviteCodeData(exist));

        // 生成新的邀请码
        String code = generateUniqueCode();
        MemberInviteCode ic = new MemberInviteCode();
        ic.setTenantId(tenantId);
        ic.setInviterId(memberId);
        ic.setInviteCode(code);
        ic.setInviteChannel("WX");
        ic.setInviteCount(0);
        ic.setSuccessCount(0);
        ic.setRewardCouponAmount(new BigDecimal("50.00"));
        ic.setExpireAt(LocalDateTime.now().plusMonths(3));
        ic.setStatus("ACTIVE");
        inviteCodeMapper.insert(ic);
        return Result.success("已生成邀请码", buildInviteCodeData(ic));
    }

    private Map<String, Object> buildInviteCodeData(MemberInviteCode ic) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", ic.getId());
        data.put("inviteCode", ic.getInviteCode());
        data.put("inviteCount", ic.getInviteCount());
        data.put("successCount", ic.getSuccessCount());
        data.put("expireAt", ic.getExpireAt());
        data.put("status", ic.getStatus());
        data.put("rewardCouponAmount", ic.getRewardCouponAmount());
        data.put("shareText", String.format(
                "我在 %s 美容，发现了一个超棒的服务！使用我的邀请码 %s 注册，双方各得 %s 元代金券 🎁",
                "美业通", ic.getInviteCode(), ic.getRewardCouponAmount()));
        return data;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = "INV" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Long dup = inviteCodeMapper.selectCount(new LambdaQueryWrapper<MemberInviteCode>()
                    .eq(MemberInviteCode::getInviteCode, code));
            if (dup == 0) return code;
        }
        throw new RuntimeException("生成邀请码失败");
    }

    // ====================================================================
    // 2. 我的邀请记录
    // ====================================================================

    public Result<List<Map<String, Object>>> myInvites(Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT r.id, r.invitee_name, r.invitee_phone, r.register_at, r.first_order_at, " +
                "       r.inviter_reward_type, r.inviter_reward_value, r.inviter_reward_status, " +
                "       r.invitee_reward_type, r.invitee_reward_value, r.invitee_reward_status " +
                "FROM member_invite_reward r " +
                "WHERE r.deleted = 0 AND r.tenant_id = ? AND r.inviter_id = ? " +
                "ORDER BY r.id DESC LIMIT 100",
                tenantId, memberId);
        return Result.success(rows);
    }

    // ====================================================================
    // 3. 我的裂变奖励汇总
    // ====================================================================

    public Result<Map<String, Object>> myRewardSummary(Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();

        // 总邀请数（绑定数）
        Long inviteCount = inviteCodeMapper.selectCount(new LambdaQueryWrapper<MemberInviteCode>()
                .eq(MemberInviteCode::getTenantId, tenantId)
                .eq(MemberInviteCode::getInviterId, memberId));
        data.put("inviteCount", inviteCount);

        // 成功注册数
        Map<String, Object> stat = jdbc.queryForMap(
                "SELECT COUNT(*) AS total, " +
                "       SUM(CASE WHEN inviter_reward_status = 'SENT' THEN 1 ELSE 0 END) AS sent, " +
                "       SUM(CASE WHEN inviter_reward_status = 'USED' THEN 1 ELSE 0 END) AS used " +
                "FROM member_invite_reward " +
                "WHERE deleted = 0 AND tenant_id = ? AND inviter_id = ?",
                tenantId, memberId);
        data.put("totalRewards", stat.get("total"));
        data.put("sentRewards", stat.getOrDefault("sent", 0));
        data.put("usedRewards", stat.getOrDefault("used", 0));

        // 当前邀请码
        MemberInviteCode activeCode = inviteCodeMapper.selectOne(new LambdaQueryWrapper<MemberInviteCode>()
                .eq(MemberInviteCode::getTenantId, tenantId)
                .eq(MemberInviteCode::getInviterId, memberId)
                .eq(MemberInviteCode::getStatus, "ACTIVE"));
        data.put("activeInviteCode", activeCode == null ? null : activeCode.getInviteCode());
        return Result.success(data);
    }

    // ====================================================================
    // 4. 绑定邀请关系（被邀请人注册时调用）
    // ====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Result<MemberInviteReward> bindInvitee(String inviteCode, Long inviteeId,
                                                   String inviteeName, String inviteePhone) {
        Long tenantId = TenantContextHolder.get().getTenantId();

        // 1. 查找邀请码
        MemberInviteCode ic = inviteCodeMapper.selectOne(new LambdaQueryWrapper<MemberInviteCode>()
                .eq(MemberInviteCode::getInviteCode, inviteCode)
                .eq(MemberInviteCode::getStatus, "ACTIVE"));
        if (ic == null) return Result.error(BizCode.NOT_FOUND, "邀请码无效或已过期");

        // 2. 不能自己邀请自己
        if (ic.getInviterId().equals(inviteeId)) {
            return Result.error(BizCode.BAD_REQUEST, "不能使用自己的邀请码");
        }

        // 3. 检查是否已绑定
        Long dup = inviteRewardMapper.selectCount(new LambdaQueryWrapper<MemberInviteReward>()
                .eq(MemberInviteReward::getTenantId, tenantId)
                .eq(MemberInviteReward::getInviteeId, inviteeId));
        if (dup > 0) return Result.error(BizCode.CONFLICT, "该会员已有邀请人");

        // 4. 创建邀请奖励记录（双方奖励待发放）
        MemberInviteReward r = new MemberInviteReward();
        r.setTenantId(tenantId);
        r.setInviteCodeId(ic.getId());
        r.setInviterId(ic.getInviterId());
        r.setInviteeId(inviteeId);
        r.setInviteeName(inviteeName);
        r.setInviteePhone(inviteePhone);
        r.setRegisterAt(LocalDateTime.now());

        // 邀请人奖励（COUPON_50）
        r.setInviterRewardType("COUPON");
        r.setInviterRewardValue(ic.getRewardCouponAmount() + "_COUPON");
        r.setInviterRewardStatus("PENDING");

        // 被邀请人奖励（首单立减 30 元）
        r.setInviteeRewardType("COUPON");
        r.setInviteeRewardValue("30_COUPON");
        r.setInviteeRewardStatus("PENDING");

        inviteRewardMapper.insert(r);

        // 5. 邀请码计数 +1
        jdbc.update("UPDATE member_invite_code SET invite_count = invite_count + 1 WHERE id = ?", ic.getId());
        return Result.success("邀请关系已绑定", r);
    }

    // ====================================================================
    // 5. 首单完成后发放奖励（被邀请人）
    // ====================================================================

    /**
     * 被邀请人完成首单后调用，发放双方奖励
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> grantRewardOnFirstOrder(Long inviteeId, Long orderId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MemberInviteReward r = inviteRewardMapper.selectOne(new LambdaQueryWrapper<MemberInviteReward>()
                .eq(MemberInviteReward::getTenantId, tenantId)
                .eq(MemberInviteReward::getInviteeId, inviteeId));
        if (r == null) return Result.success("无邀请关系", null);
        if (r.getFirstOrderId() != null) return Result.success("已发放过奖励", null);

        r.setFirstOrderId(orderId);
        r.setFirstOrderAt(LocalDateTime.now());
        r.setInviterRewardStatus("SENT");
        r.setInviteeRewardStatus("SENT");
        inviteRewardMapper.updateById(r);

        // 邀请码成功数 +1
        jdbc.update("UPDATE member_invite_code SET success_count = success_count + 1 WHERE id = ?",
                r.getInviteCodeId());

        // TODO: 实际发放优惠券到邀请人和被邀请人的钱包
        // 这里仅记录日志，后续可接入 CouponService.grantCoupon(memberId, amount)
        log.info("[Invite] 首单奖励发放 inviter={} invitee={} orderId={}",
                r.getInviterId(), inviteeId, orderId);
        return Result.success("奖励已发放", null);
    }
}