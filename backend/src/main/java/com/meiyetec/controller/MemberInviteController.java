package com.meiyetec.controller;

import com.meiyetec.entity.MemberInviteReward;
import com.meiyetec.service.MemberInviteService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * P4-02 · QPM-006: 会员裂变邀请 Controller
 *
 * 端点（/lifecycle/invite 前缀）：
 * - GET  /code                生成/获取我的邀请码
 * - GET  /my-invites          我的邀请记录
 * - GET  /rewards             我的奖励汇总
 * - POST /bind                绑定邀请关系（注册时调用）
 * - POST /first-order         首单完成后发放奖励
 */
@RestController
@RequestMapping("/lifecycle/invite")
@RequiredArgsConstructor
public class MemberInviteController {

    private final MemberInviteService service;

    @GetMapping("/code")
    public Result<Map<String, Object>> myCode(@RequestParam Long memberId) {
        return service.myInviteCode(memberId);
    }

    @GetMapping("/my-invites")
    public Result<List<Map<String, Object>>> myInvites(@RequestParam Long memberId) {
        return service.myInvites(memberId);
    }

    @GetMapping("/rewards")
    public Result<Map<String, Object>> myRewards(@RequestParam Long memberId) {
        return service.myRewardSummary(memberId);
    }

    @PostMapping("/bind")
    public Result<MemberInviteReward> bind(@RequestBody Map<String, Object> body) {
        String inviteCode = (String) body.get("inviteCode");
        Long inviteeId = body.get("inviteeId") == null ? null : ((Number) body.get("inviteeId")).longValue();
        String inviteeName = (String) body.get("inviteeName");
        String inviteePhone = (String) body.get("inviteePhone");
        if (inviteCode == null || inviteeId == null) {
            return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "inviteCode / inviteeId 必填");
        }
        return service.bindInvitee(inviteCode, inviteeId, inviteeName, inviteePhone);
    }

    @PostMapping("/first-order")
    public Result<Void> onFirstOrder(@RequestBody Map<String, Object> body) {
        Long inviteeId = body.get("inviteeId") == null ? null : ((Number) body.get("inviteeId")).longValue();
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        if (inviteeId == null || orderId == null) {
            return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "inviteeId / orderId 必填");
        }
        return service.grantRewardOnFirstOrder(inviteeId, orderId);
    }
}