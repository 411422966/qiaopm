package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.InviteCampaign;
import com.meiyetec.entity.InviteCode;
import com.meiyetec.entity.InviteRecord;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.InviteService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * M09-02 裂变邀请 Controller
 *
 * B 端（运营后台）：
 *   GET    /m09/invite/campaign/page               活动分页
 *   GET    /m09/invite/campaign/{id}               活动详情
 *   POST   /m09/invite/campaign                    创建活动
 *   PUT    /m09/invite/campaign/{id}               编辑活动
 *   DELETE /m09/invite/campaign/{id}               删除活动
 *   POST   /m09/invite/campaign/{id}/toggle        启停活动
 *
 *   GET    /m09/invite/code/page                   邀请码列表（全部会员）
 *   GET    /m09/invite/record/page                 邀请记录分页
 *   GET    /m09/invite/record/{id}                 记录详情
 *   POST   /m09/invite/record/{id}/resend-reward   重发奖励
 *
 *   GET    /m09/invite/stats                       看板统计
 *   GET    /m09/invite/reward-log/page             奖励发放日志
 *
 * C 端（会员小程序 / H5）：
 *   GET    /invite/share-page?code=xxx             分享落地页数据
 *   POST   /invite/track                           上报浏览
 *   POST   /invite/bind                            注册时绑定邀请码
 *   POST   /invite/redeem                          首单后触发奖励（POS 回调）
 *   GET    /invite/my-codes                        我的邀请码列表
 *   GET    /invite/my-stats                        我的邀请统计
 *   POST   /invite/{codeId}/report-share           分享按钮点击上报
 */
@RestController
@RequiredArgsConstructor
@SysOpLog(module = "裂变邀请")
public class InviteController {

    private final InviteService inviteService;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        if (t == null) return null;
        return t;
    }

    // ====================== B 端：活动管理 ======================

    @GetMapping("/m09/invite/campaign/page")
    @RequirePermission("mkt:invite:list")
    public Result<PageResult<Map<String, Object>>> campaignPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return inviteService.campaignPage(pageNum, pageSize, keyword, status);
    }

    @GetMapping("/m09/invite/campaign/{id}")
    @RequirePermission("mkt:invite:list")
    public Result<Map<String, Object>> campaignDetail(@PathVariable Long id) {
        return inviteService.campaignDetail(id);
    }

    @PostMapping("/m09/invite/campaign")
    @RequirePermission("mkt:invite:edit")
    public Result<InviteCampaign> createCampaign(@RequestBody InviteCampaign req) {
        return inviteService.createCampaign(req);
    }

    @PutMapping("/m09/invite/campaign/{id}")
    @RequirePermission("mkt:invite:edit")
    public Result<InviteCampaign> updateCampaign(@PathVariable Long id, @RequestBody InviteCampaign req) {
        return inviteService.updateCampaign(id, req);
    }

    @DeleteMapping("/m09/invite/campaign/{id}")
    @RequirePermission("mkt:invite:edit")
    public Result<Void> deleteCampaign(@PathVariable Long id) {
        return inviteService.deleteCampaign(id);
    }

    @PostMapping("/m09/invite/campaign/{id}/toggle")
    @RequirePermission("mkt:invite:edit")
    public Result<Void> toggleCampaign(@PathVariable Long id) {
        return inviteService.toggleCampaign(id);
    }

    // ====================== B 端：邀请码 & 记录 ======================

    @GetMapping("/m09/invite/code/page")
    @RequirePermission("mkt:invite:list")
    public Result<PageResult<Map<String, Object>>> codePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) String keyword) {
        return inviteService.codePage(pageNum, pageSize, campaignId, keyword);
    }

    @GetMapping("/m09/invite/record/page")
    @RequirePermission("mkt:invite:list")
    public Result<PageResult<Map<String, Object>>> recordPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return inviteService.recordPage(pageNum, pageSize, campaignId, status, keyword);
    }

    @GetMapping("/m09/invite/record/{id}")
    @RequirePermission("mkt:invite:list")
    public Result<Map<String, Object>> recordDetail(@PathVariable Long id) {
        return inviteService.recordDetail(id);
    }

    @PostMapping("/m09/invite/record/{id}/resend-reward")
    @RequirePermission("mkt:invite:edit")
    public Result<Integer> resendReward(@PathVariable Long id) {
        return inviteService.resendReward(id);
    }

    @GetMapping("/m09/invite/stats")
    @RequirePermission("mkt:invite:list")
    public Result<Map<String, Object>> dashboardStats(@RequestParam(required = false) Long campaignId) {
        return inviteService.dashboardStats(campaignId);
    }

    @GetMapping("/m09/invite/reward-log/page")
    @RequirePermission("mkt:invite:list")
    public Result<PageResult<Map<String, Object>>> rewardLogPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long campaignId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer rewardType) {
        return inviteService.rewardLogPage(pageNum, pageSize, campaignId, status, rewardType);
    }

    // ====================== C 端：会员邀请操作 ======================

    /**
     * 分享落地页数据：扫码/点链接进来后获取活动信息
     */
    @GetMapping("/invite/share-page")
    public Result<Map<String, Object>> sharePage(@RequestParam String code) {
        Long tid = tid();
        if (tid == null) return Result.error(401, "未识别租户");
        // 先触发扫码上报
        InviteCampaign activity = null;
        try {
            inviteService.trackClick(code, null, "LINK", null, null);
        } catch (Exception ignore) {}
        // 简化：直接通过 controller 内部获取（refactor 留待后续）
        return inviteService.getSharePage(code, tid);
    }

    /**
     * 上报浏览（带手机号时同步建 PENDING 记录）
     */
    @PostMapping("/invite/track")
    public Result<Object> trackClick(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long tid = tid();
        if (tid == null) return Result.error(401, "未识别租户");
        String code = (String) body.get("code");
        String phone = (String) body.get("phone");
        String channel = (String) body.get("channel");
        String ip = clientIp(request);
        String ua = request.getHeader("User-Agent");
        Result<InviteRecord> rs = inviteService.trackClick(code, phone, channel, ip, ua);
        return Result.success(rs.getData());
    }

    /**
     * 注册后绑定邀请码（按手机号匹配 PENDING 记录）
     */
    @PostMapping("/invite/bind")
    public Result<Integer> bindOnRegister(@RequestBody Map<String, Object> body) {
        String phone = (String) body.get("phone");
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        String name = (String) body.get("name");
        return inviteService.bindOnRegister(phone, memberId, name);
    }

    /**
     * 首单完成触发奖励（POS 收银回调）
     */
    @PostMapping("/invite/redeem")
    public Result<Integer> rewardOnFirstOrder(@RequestBody Map<String, Object> body) {
        Long inviteeMemberId = body.get("inviteeMemberId") == null ? null
                : ((Number) body.get("inviteeMemberId")).longValue();
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        BigDecimal orderAmount = body.get("orderAmount") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("orderAmount").toString());
        return inviteService.rewardOnFirstOrder(inviteeMemberId, orderId, orderAmount);
    }

    /**
     * 获取 / 生成我的邀请码
     */
    @GetMapping("/invite/my-code")
    public Result<Object> myCode(@RequestParam Long campaignId, @RequestParam Long memberId) {
        Long tid = tid();
        if (tid == null) return Result.error(401, "未识别租户");
        Result<InviteCode> rs = inviteService.getOrCreateCode(campaignId, memberId);
        return Result.success(rs.getData());
    }

    /**
     * 我的所有活动邀请码
     */
    @GetMapping("/invite/my-codes")
    public Result<Object> myCodes(@RequestParam Long memberId) {
        Long tid = tid();
        if (tid == null) return Result.error(401, "未识别租户");
        Result<List<Map<String, Object>>> rs = inviteService.myCodes(memberId);
        return Result.success(rs.getData());
    }

    /**
     * 我的邀请汇总统计
     */
    @GetMapping("/invite/my-stats")
    public Result<Map<String, Object>> myStats(@RequestParam Long memberId) {
        return inviteService.myStats(memberId);
    }

    /**
     * 分享按钮上报（小程序/H5 点击分享按钮）
     */
    @PostMapping("/invite/{codeId}/report-share")
    public Result<Void> reportShare(@PathVariable Long codeId) {
        return inviteService.reportShare(codeId);
    }

    private static String clientIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            int idx = h.indexOf(',');
            return idx > 0 ? h.substring(0, idx).trim() : h.trim();
        }
        h = req.getHeader("X-Real-IP");
        if (h != null && !h.isBlank()) return h.trim();
        return req.getRemoteAddr();
    }
}
