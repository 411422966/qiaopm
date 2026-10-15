package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * M09-02 裂变邀请核心服务
 *
 * 业务能力：
 *  1. 邀请活动管理（CRUD + 启停）
 *  2. 邀请码生成 / 统计（每会员每活动唯一码）
 *  3. 漏斗上报：浏览 → 注册 → 首单 → 奖励
 *  4. 奖励发放：优惠券 / 积分 / 储值 三通道
 *  5. 看板统计：裂变带新率 / 邀请转化漏斗
 *
 * 状态机：
 *   InviteRecord.status: PENDING → REGISTERED → FIRST_ORDER → REWARDED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteCampaignMapper campaignMapper;
    private final InviteCodeMapper codeMapper;
    private final InviteRecordMapper recordMapper;
    private final InviteRewardLogMapper rewardLogMapper;
    private final MemberMapper memberMapper;
    private final CouponMapper couponMapper;
    private final CouponGrantMapper couponGrantMapper;
    private final PointService pointService;
    private final WalletService walletService;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 邀请码长度（含字母数字，去除易混淆字符） */
    private static final int CODE_LENGTH = 8;
    /** 同一手机号绑定同一活动同一邀请人的最大次数（防刷） */
    private static final int MAX_BIND_PER_PHONE = 1;

    // ====================================================================
    // 1. 活动管理
    // ====================================================================

    public Result<PageResult<Map<String, Object>>> campaignPage(int pageNum, int pageSize, String keyword, Integer status) {
        Long tid = TenantContextHolder.get().getTenantId();
        Page<InviteCampaign> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InviteCampaign> q = new LambdaQueryWrapper<>();
        q.eq(InviteCampaign::getTenantId, tid);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(InviteCampaign::getCampaignCode, keyword)
                       .or().like(InviteCampaign::getCampaignName, keyword));
        }
        if (status != null) q.eq(InviteCampaign::getStatus, status);
        q.orderByDesc(InviteCampaign::getStartTime);
        Page<InviteCampaign> result = campaignMapper.selectPage(page, q);
        // 拼装返回：活动基础信息 + 漏斗汇总
        List<Map<String, Object>> records = new ArrayList<>();
        for (InviteCampaign c : result.getRecords()) {
            Map<String, Object> m = toCampaignMap(c);
            Map<String, Object> funnel = computeCampaignFunnel(c.getId());
            m.putAll(funnel);
            records.add(m);
        }
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(records);
        return Result.success(com.meiyetec.util.PageResult.of(mapPage));
    }

    public Result<Map<String, Object>> campaignDetail(Long id) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteCampaign c = campaignMapper.selectById(id);
        if (c == null || !tid.equals(c.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "活动不存在");
        }
        Map<String, Object> m = toCampaignMap(c);
        m.putAll(computeCampaignFunnel(c.getId()));
        return Result.success(m);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<InviteCampaign> createCampaign(InviteCampaign req) {
        Long tid = TenantContextHolder.get().getTenantId();
        if (req.getCampaignCode() == null || req.getCampaignCode().isBlank()) {
            return Result.error(BizCode.BAD_REQUEST, "campaignCode 必填");
        }
        if (req.getCampaignName() == null || req.getCampaignName().isBlank()) {
            return Result.error(BizCode.BAD_REQUEST, "campaignName 必填");
        }
        if (req.getInviterRewardType() == null) {
            return Result.error(BizCode.BAD_REQUEST, "inviterRewardType 必填");
        }
        if (req.getInviteeRewardType() == null) {
            return Result.error(BizCode.BAD_REQUEST, "inviteeRewardType 必填");
        }
        if (req.getStartTime() == null || req.getEndTime() == null) {
            return Result.error(BizCode.BAD_REQUEST, "活动时间必填");
        }
        if (req.getEndTime().isBefore(req.getStartTime())) {
            return Result.error(BizCode.BAD_REQUEST, "endTime 必须晚于 startTime");
        }
        // 唯一性
        Long dup = campaignMapper.selectCount(new LambdaQueryWrapper<InviteCampaign>()
                .eq(InviteCampaign::getTenantId, tid)
                .eq(InviteCampaign::getCampaignCode, req.getCampaignCode()));
        if (dup > 0) {
            return Result.error(BizCode.CONFLICT, "campaignCode 已存在");
        }
        req.setTenantId(tid);
        if (req.getStatus() == null) req.setStatus(1);
        if (req.getInviterQuotaPerDay() == null) req.setInviterQuotaPerDay(5);
        if (req.getInviterQuotaTotal() == null) req.setInviterQuotaTotal(50);
        if (req.getCampaignQuotaTotal() == null) req.setCampaignQuotaTotal(0);
        if (req.getInviteNeedRegister() == null) req.setInviteNeedRegister(true);
        if (req.getInviteeMinConsume() == null) req.setInviteeMinConsume(BigDecimal.ZERO);
        if (req.getCampaignType() == null) req.setCampaignType("INVITE_REWARD");

        // 当前操作员（从 TenantContext 拿）
        try {
            var ctx = TenantContextHolder.get();
            if (ctx != null && ctx.getUserId() != null) {
                req.setOperatorId(ctx.getUserId());
                req.setOperatorName(ctx.getUsername());
            }
        } catch (Exception ignored) {}

        campaignMapper.insert(req);
        return Result.success("创建成功", req);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<InviteCampaign> updateCampaign(Long id, InviteCampaign req) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteCampaign c = campaignMapper.selectById(id);
        if (c == null || !tid.equals(c.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "活动不存在");
        }
        if (req.getCampaignName() != null) c.setCampaignName(req.getCampaignName());
        if (req.getInviterRewardType() != null) c.setInviterRewardType(req.getInviterRewardType());
        if (req.getInviterRewardValue() != null) c.setInviterRewardValue(req.getInviterRewardValue());
        if (req.getInviterRewardMeta() != null) c.setInviterRewardMeta(req.getInviterRewardMeta());
        if (req.getInviteeRewardType() != null) c.setInviteeRewardType(req.getInviteeRewardType());
        if (req.getInviteeRewardValue() != null) c.setInviteeRewardValue(req.getInviteeRewardValue());
        if (req.getInviteeRewardMeta() != null) c.setInviteeRewardMeta(req.getInviteeRewardMeta());
        if (req.getInviteeMinConsume() != null) c.setInviteeMinConsume(req.getInviteeMinConsume());
        if (req.getInviteNeedRegister() != null) c.setInviteNeedRegister(req.getInviteNeedRegister());
        if (req.getInviterQuotaPerDay() != null) c.setInviterQuotaPerDay(req.getInviterQuotaPerDay());
        if (req.getInviterQuotaTotal() != null) c.setInviterQuotaTotal(req.getInviterQuotaTotal());
        if (req.getCampaignQuotaTotal() != null) c.setCampaignQuotaTotal(req.getCampaignQuotaTotal());
        if (req.getShareTitle() != null) c.setShareTitle(req.getShareTitle());
        if (req.getShareDesc() != null) c.setShareDesc(req.getShareDesc());
        if (req.getShareImageUrl() != null) c.setShareImageUrl(req.getShareImageUrl());
        if (req.getShareLandingUrl() != null) c.setShareLandingUrl(req.getShareLandingUrl());
        if (req.getStartTime() != null) c.setStartTime(req.getStartTime());
        if (req.getEndTime() != null) c.setEndTime(req.getEndTime());
        if (req.getStatus() != null) c.setStatus(req.getStatus());
        if (req.getRemark() != null) c.setRemark(req.getRemark());
        campaignMapper.updateById(c);
        return Result.success("更新成功", c);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> toggleCampaign(Long id) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteCampaign c = campaignMapper.selectById(id);
        if (c == null || !tid.equals(c.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "活动不存在");
        }
        c.setStatus(c.getStatus() != null && c.getStatus() == 1 ? 0 : 1);
        campaignMapper.updateById(c);
        return Result.success(c.getStatus() == 1 ? "已启用" : "已停用", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteCampaign(Long id) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteCampaign c = campaignMapper.selectById(id);
        if (c == null || !tid.equals(c.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "活动不存在");
        }
        // 校验：已有成功邀请记录 → 不能直接删
        Long succ = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                .eq(InviteRecord::getCampaignId, id)
                .eq(InviteRecord::getStatus, 4));
        if (succ > 0) {
            return Result.error(BizCode.CONFLICT,
                    "该活动已有 " + succ + " 条成功邀请记录，建议停用而非删除");
        }
        campaignMapper.deleteById(id);
        // 同步软删邀请码与记录
        for (InviteCode code : codeMapper.selectList(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCampaignId, id))) {
            codeMapper.deleteById(code.getId());
        }
        return Result.success("删除成功", null);
    }

    // ====================================================================
    // 2. 邀请码：生成 / 查询 / 漏斗统计
    // ====================================================================

    /**
     * 获取或生成会员在指定活动中的邀请码。
     * 若已存在则直接返回（保证 uk_campaign_member 唯一）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<InviteCode> getOrCreateCode(Long campaignId, Long memberId) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteCampaign c = campaignMapper.selectById(campaignId);
        if (c == null || !tid.equals(c.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "活动不存在");
        }
        // 校验活动状态
        if (c.getStatus() == null || c.getStatus() != 1) {
            return Result.error(BizCode.CONFLICT, "活动未启用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getStartTime() != null && c.getStartTime().isAfter(now)) {
            return Result.error(BizCode.CONFLICT, "活动尚未开始");
        }
        if (c.getEndTime() != null && c.getEndTime().isBefore(now)) {
            return Result.error(BizCode.CONFLICT, "活动已结束");
        }
        Member m = memberMapper.selectById(memberId);
        if (m == null || (m.getTenantId() != null && !tid.equals(m.getTenantId()))) {
            return Result.error(BizCode.MEMBER_NOT_FOUND);
        }
        InviteCode existing = codeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getCampaignId, campaignId)
                .eq(InviteCode::getMemberId, memberId));
        if (existing != null) return Result.success(existing);

        InviteCode code = new InviteCode();
        code.setTenantId(tid);
        code.setCampaignId(campaignId);
        code.setMemberId(memberId);
        code.setMemberName(m.getName());
        code.setMemberPhone(maskPhone(m.getPhone()));
        code.setInviteCode(generateUniqueCode(campaignId));
        code.setShareCount(0);
        code.setScanCount(0);
        code.setRegisterCount(0);
        code.setSuccessCount(0);
        // 短链可空（运营可自行映射）
        codeMapper.insert(code);
        return Result.success(code);
    }

    /**
     * 仅查活动（不创建记录）— 给分享落地页用
     */
    public Result<Map<String, Object>> getSharePage(String code, Long tid) {
        if (code == null || code.isBlank()) {
            return Result.error(BizCode.BAD_REQUEST, "邀请码必填");
        }
        InviteCode ic = codeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getInviteCode, code)
                .eq(InviteCode::getTenantId, tid));
        if (ic == null) return Result.error(BizCode.NOT_FOUND, "邀请码不存在");
        InviteCampaign c = campaignMapper.selectById(ic.getCampaignId());
        if (c == null) return Result.error(BizCode.NOT_FOUND, "活动不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("campaignId", c.getId());
        m.put("campaignName", c.getCampaignName());
        m.put("campaignType", c.getCampaignType());
        m.put("shareTitle", c.getShareTitle());
        m.put("shareDesc", c.getShareDesc());
        m.put("shareImageUrl", c.getShareImageUrl());
        m.put("shareLandingUrl", c.getShareLandingUrl());
        m.put("inviteCode", ic.getInviteCode());
        // 不暴露邀请人手机号，仅显示昵称首字 + 星号
        if (ic.getMemberName() != null && ic.getMemberName().length() > 0) {
            String nick = ic.getMemberName();
            String maskedName = nick.length() == 1 ? nick : nick.charAt(0) + "**";
            m.put("inviterNick", maskedName);
        }
        // 邀请人奖励描述（用于文案展示）
        m.put("inviterRewardDesc", formatRewardDesc(c.getInviterRewardType(), c.getInviterRewardValue(), c.getInviterRewardMeta()));
        m.put("inviteeRewardDesc", formatRewardDesc(c.getInviteeRewardType(), c.getInviteeRewardValue(), c.getInviteeRewardMeta()));
        m.put("inviteeMinConsume", c.getInviteeMinConsume());
        m.put("startTime", c.getStartTime());
        m.put("endTime", c.getEndTime());
        m.put("status", c.getStatus());
        return Result.success(m);
    }

    private static String formatRewardDesc(Integer type, BigDecimal value, String meta) {
        if (type == null) return "";
        String val = value == null ? "" : value.toPlainString();
        return switch (type) {
            case 1 -> "优惠券 ¥" + val;
            case 2 -> val + " 积分";
            case 3 -> "储值 ¥" + val;
            default -> "自定义奖励";
        };
    }

    /**
     * 我的邀请码列表（含每个活动的漏斗）
     */
    public Result<List<Map<String, Object>>> myCodes(Long memberId) {
        Long tid = TenantContextHolder.get().getTenantId();
        List<InviteCode> codes = codeMapper.selectList(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getMemberId, memberId)
                .eq(InviteCode::getTenantId, tid)
                .orderByDesc(InviteCode::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (InviteCode code : codes) {
            InviteCampaign c = campaignMapper.selectById(code.getCampaignId());
            if (c == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("codeId", code.getId());
            m.put("campaignId", code.getCampaignId());
            m.put("campaignName", c.getCampaignName());
            m.put("inviteCode", code.getInviteCode());
            m.put("shareCount", code.getShareCount());
            m.put("scanCount", code.getScanCount());
            m.put("registerCount", code.getRegisterCount());
            m.put("successCount", code.getSuccessCount());
            m.put("shareTitle", c.getShareTitle());
            m.put("shareDesc", c.getShareDesc());
            m.put("shareImageUrl", c.getShareImageUrl());
            m.put("endTime", c.getEndTime());
            m.put("status", c.getStatus());
            result.add(m);
        }
        return Result.success(result);
    }

    /**
     * 我的邀请统计汇总
     */
    public Result<Map<String, Object>> myStats(Long memberId) {
        Long tid = TenantContextHolder.get().getTenantId();
        Map<String, Object> stats = new HashMap<>();
        Long totalCodes = codeMapper.selectCount(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getMemberId, memberId).eq(InviteCode::getTenantId, tid));
        Long successCount = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                .eq(InviteRecord::getInviterMemberId, memberId).eq(InviteRecord::getStatus, 4));
        Long pendingReward = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                .eq(InviteRecord::getInviterMemberId, memberId)
                .in(InviteRecord::getStatus, 2, 3));
        Long rewardLogs = rewardLogMapper.selectCount(new LambdaQueryWrapper<InviteRewardLog>()
                .eq(InviteRewardLog::getMemberId, memberId).eq(InviteRewardLog::getStatus, 1));
        stats.put("totalCampaigns", totalCodes);
        stats.put("successInvites", successCount);
        stats.put("pendingRewards", pendingReward);
        stats.put("rewardReceived", rewardLogs);
        return Result.success(stats);
    }

    // ====================================================================
    // 3. 邀请记录：浏览 → 注册 → 首单 → 奖励 漏斗
    // ====================================================================

    /**
     * 上报浏览（C 端扫码 / 点链接）
     * - 若 inviteCode 不存在 → 静默忽略（不报错）
     * - 若已存在 phone 绑定 → 仅累加 scanCount
     * - 否则创建 PENDING 记录
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<InviteRecord> trackClick(String inviteCode, String phone, String channel, String ip, String ua) {
        Long tid = TenantContextHolder.get().getTenantId();
        if (inviteCode == null || inviteCode.isBlank()) {
            return Result.error(BizCode.BAD_REQUEST, "inviteCode 必填");
        }
        InviteCode code = codeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                .eq(InviteCode::getInviteCode, inviteCode)
                .eq(InviteCode::getTenantId, tid));
        if (code == null) {
            return Result.success(null); // 静默不报错：防刷
        }
        InviteCampaign c = campaignMapper.selectById(code.getCampaignId());
        if (c == null || c.getStatus() != 1) {
            return Result.success(null);
        }
        LocalDateTime now = LocalDateTime.now();
        if (c.getStartTime() != null && c.getStartTime().isAfter(now)) return Result.success(null);
        if (c.getEndTime() != null && c.getEndTime().isBefore(now)) return Result.success(null);

        // 累加扫码数（即使后续不注册也记录）
        code.setScanCount((code.getScanCount() == null ? 0 : code.getScanCount()) + 1);
        codeMapper.updateById(code);

        // 是否需要创建 record
        if (Boolean.FALSE.equals(c.getInviteNeedRegister())) {
            // 不需要注册的场景：扫码即奖励（特殊场景，如扫码关注公众号）
            // 当前版本默认要求注册，所以这里仅返回
            return Result.success(null);
        }
        if (phone == null || phone.isBlank()) {
            // 没有手机号只记录扫码，不建 record
            return Result.success(null);
        }
        // 检查活动配额
        if (!checkQuota(code, c)) {
            return Result.error(BizCode.CONFLICT, "邀请配额已用完");
        }
        // 防止同人同活动同一邀请人重复（手机号唯一）
        Long existSame = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                .eq(InviteRecord::getCampaignId, c.getId())
                .eq(InviteRecord::getInviterMemberId, code.getMemberId())
                .eq(InviteRecord::getInviteePhone, maskPhone(phone)));
        if (existSame >= MAX_BIND_PER_PHONE) {
            return Result.error(BizCode.CONFLICT, "该手机号已绑定过此活动的邀请");
        }
        // 创建 PENDING 记录
        InviteRecord record = new InviteRecord();
        record.setTenantId(tid);
        record.setCampaignId(c.getId());
        record.setCampaignName(c.getCampaignName());
        record.setInviteCode(inviteCode);
        record.setInviterMemberId(code.getMemberId());
        record.setInviterName(code.getMemberName());
        record.setInviteePhone(maskPhone(phone));
        record.setChannel(channel == null ? "LINK" : channel.toUpperCase());
        record.setStatus(1);
        record.setClickedAt(now);
        record.setIp(ip);
        record.setUa(ua);
        recordMapper.insert(record);

        // 累加注册数（标记注册数 = 触达数）
        code.setRegisterCount((code.getRegisterCount() == null ? 0 : code.getRegisterCount()) + 1);
        codeMapper.updateById(code);

        return Result.success(record);
    }

    /**
     * 受邀人注册后绑定（手机号注册 → 把 PENDING → REGISTERED）
     * 由 MemberService 注册成功后异步调用
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> bindOnRegister(String phone, Long newMemberId, String name) {
        if (phone == null || phone.isBlank()) return Result.success(0);
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid == null) return Result.success(0);

        String masked = maskPhone(phone);
        List<InviteRecord> pending = recordMapper.selectList(new LambdaQueryWrapper<InviteRecord>()
                .eq(InviteRecord::getInviteePhone, masked)
                .eq(InviteRecord::getStatus, 1)
                .eq(InviteRecord::getTenantId, tid));
        int updated = 0;
        for (InviteRecord r : pending) {
            // 校验活动是否仍在有效期
            InviteCampaign c = campaignMapper.selectById(r.getCampaignId());
            if (c == null || c.getStatus() != 1) continue;
            LocalDateTime now = LocalDateTime.now();
            if (c.getEndTime() != null && c.getEndTime().isBefore(now)) {
                r.setStatus(5);
                r.setFailReason("活动已结束");
                recordMapper.updateById(r);
                continue;
            }
            r.setInviteeMemberId(newMemberId);
            r.setInviteeName(name);
            r.setRegisterAt(now);
            r.setStatus(2);
            recordMapper.updateById(r);
            updated++;
        }
        return Result.success(updated);
    }

    /**
     * 受邀人首单完成 → 触发双方奖励发放。
     * 由 PosController 收银完成回调时调用。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> rewardOnFirstOrder(Long inviteeMemberId, Long orderId, BigDecimal orderAmount) {
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid == null) return Result.success(0);

        List<InviteRecord> records = recordMapper.selectList(new LambdaQueryWrapper<InviteRecord>()
                .eq(InviteRecord::getInviteeMemberId, inviteeMemberId)
                .eq(InviteRecord::getStatus, 2)
                .eq(InviteRecord::getTenantId, tid));
        int count = 0;
        for (InviteRecord r : records) {
            InviteCampaign c = campaignMapper.selectById(r.getCampaignId());
            if (c == null || c.getStatus() != 1) continue;
            LocalDateTime now = LocalDateTime.now();
            if (c.getEndTime() != null && c.getEndTime().isBefore(now)) {
                r.setStatus(5);
                r.setFailReason("活动已结束");
                recordMapper.updateById(r);
                continue;
            }
            // 验证最低消费
            BigDecimal min = c.getInviteeMinConsume() == null ? BigDecimal.ZERO : c.getInviteeMinConsume();
            if (orderAmount != null && orderAmount.compareTo(min) < 0) {
                // 不达门槛：状态切到 FIRST_ORDER（=3），等下一笔订单达门槛再发
                r.setStatus(3);
                r.setFirstConsumeAt(now);
                r.setFirstOrderId(orderId);
                recordMapper.updateById(r);
                continue;
            }
            r.setFirstConsumeAt(now);
            r.setFirstOrderId(orderId);
            r.setStatus(3);
            recordMapper.updateById(r);
            // 触发双方奖励
            try {
                grantInviterReward(c, r);
                grantInviteeReward(c, r);
                r.setStatus(4);
                recordMapper.updateById(r);
                // 累加邀请人成功邀请数
                InviteCode ic = codeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                        .eq(InviteCode::getCampaignId, c.getId())
                        .eq(InviteCode::getMemberId, r.getInviterMemberId()));
                if (ic != null) {
                    ic.setSuccessCount((ic.getSuccessCount() == null ? 0 : ic.getSuccessCount()) + 1);
                    codeMapper.updateById(ic);
                }
                count++;
            } catch (Exception e) {
                log.error("[INVITE] 发放奖励失败 recordId={}", r.getId(), e);
                r.setStatus(5);
                r.setFailReason("奖励发放失败：" + e.getMessage());
                recordMapper.updateById(r);
            }
        }
        return Result.success(count);
    }

    // ====================================================================
    // 4. 后台管理端：邀请码列表 / 邀请记录列表 / 看板
    // ====================================================================

    public Result<PageResult<Map<String, Object>>> codePage(int pageNum, int pageSize, Long campaignId, String keyword) {
        Long tid = TenantContextHolder.get().getTenantId();
        Page<InviteCode> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InviteCode> q = new LambdaQueryWrapper<>();
        q.eq(InviteCode::getTenantId, tid);
        if (campaignId != null) q.eq(InviteCode::getCampaignId, campaignId);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(InviteCode::getInviteCode, keyword)
                       .or().like(InviteCode::getMemberName, keyword)
                       .or().like(InviteCode::getMemberPhone, keyword));
        }
        q.orderByDesc(InviteCode::getSuccessCount).orderByDesc(InviteCode::getCreatedAt);
        Page<InviteCode> result = codeMapper.selectPage(page, q);
        List<Map<String, Object>> records = new ArrayList<>();
        for (InviteCode ic : result.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ic.getId());
            m.put("campaignId", ic.getCampaignId());
            m.put("memberId", ic.getMemberId());
            m.put("memberName", ic.getMemberName());
            m.put("memberPhone", ic.getMemberPhone());
            m.put("inviteCode", ic.getInviteCode());
            m.put("shareCount", ic.getShareCount());
            m.put("scanCount", ic.getScanCount());
            m.put("registerCount", ic.getRegisterCount());
            m.put("successCount", ic.getSuccessCount());
            m.put("createdAt", ic.getCreatedAt());
            InviteCampaign c = campaignMapper.selectById(ic.getCampaignId());
            if (c != null) m.put("campaignName", c.getCampaignName());
            records.add(m);
        }
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(records);
        return Result.success(com.meiyetec.util.PageResult.of(mapPage));
    }

    public Result<PageResult<Map<String, Object>>> recordPage(int pageNum, int pageSize, Long campaignId,
                                                          Integer status, String keyword) {
        Long tid = TenantContextHolder.get().getTenantId();
        Page<InviteRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InviteRecord> q = new LambdaQueryWrapper<>();
        q.eq(InviteRecord::getTenantId, tid);
        if (campaignId != null) q.eq(InviteRecord::getCampaignId, campaignId);
        if (status != null) q.eq(InviteRecord::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(InviteRecord::getInviteePhone, keyword)
                       .or().like(InviteRecord::getInviteeName, keyword)
                       .or().like(InviteRecord::getInviterName, keyword)
                       .or().like(InviteRecord::getInviteCode, keyword));
        }
        q.orderByDesc(InviteRecord::getCreatedAt);
        Page<InviteRecord> result = recordMapper.selectPage(page, q);
        List<Map<String, Object>> records = new ArrayList<>();
        for (InviteRecord r : result.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("campaignId", r.getCampaignId());
            m.put("campaignName", r.getCampaignName());
            m.put("inviteCode", r.getInviteCode());
            m.put("inviterMemberId", r.getInviterMemberId());
            m.put("inviterName", r.getInviterName());
            m.put("inviteeMemberId", r.getInviteeMemberId());
            m.put("inviteePhone", r.getInviteePhone());
            m.put("inviteeName", r.getInviteeName());
            m.put("channel", r.getChannel());
            m.put("status", r.getStatus());
            m.put("statusText", statusText(r.getStatus()));
            m.put("registerAt", r.getRegisterAt());
            m.put("firstConsumeAt", r.getFirstConsumeAt());
            m.put("inviterRewardedAt", r.getInviterRewardedAt());
            m.put("inviteeRewardedAt", r.getInviteeRewardedAt());
            m.put("failReason", r.getFailReason());
            m.put("createdAt", r.getCreatedAt());
            records.add(m);
        }
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(records);
        return Result.success(com.meiyetec.util.PageResult.of(mapPage));
    }

    public Result<Map<String, Object>> recordDetail(Long id) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteRecord r = recordMapper.selectById(id);
        if (r == null || !tid.equals(r.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "记录不存在");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("campaignId", r.getCampaignId());
        m.put("campaignName", r.getCampaignName());
        m.put("inviteCode", r.getInviteCode());
        m.put("inviterMemberId", r.getInviterMemberId());
        m.put("inviterName", r.getInviterName());
        m.put("inviteeMemberId", r.getInviteeMemberId());
        m.put("inviteePhone", r.getInviteePhone());
        m.put("inviteeName", r.getInviteeName());
        m.put("channel", r.getChannel());
        m.put("status", r.getStatus());
        m.put("statusText", statusText(r.getStatus()));
        m.put("clickedAt", r.getClickedAt());
        m.put("registerAt", r.getRegisterAt());
        m.put("firstConsumeAt", r.getFirstConsumeAt());
        m.put("firstOrderId", r.getFirstOrderId());
        m.put("inviterRewardedAt", r.getInviterRewardedAt());
        m.put("inviteeRewardedAt", r.getInviteeRewardedAt());
        m.put("failReason", r.getFailReason());
        m.put("remark", r.getRemark());
        m.put("createdAt", r.getCreatedAt());

        // 关联奖励日志
        List<InviteRewardLog> rewards = rewardLogMapper.selectList(new LambdaQueryWrapper<InviteRewardLog>()
                .eq(InviteRewardLog::getRecordId, id)
                .orderByDesc(InviteRewardLog::getCreatedAt));
        List<Map<String, Object>> rewardMaps = new ArrayList<>();
        for (InviteRewardLog rl : rewards) {
            Map<String, Object> r2 = new HashMap<>();
            r2.put("id", rl.getId());
            r2.put("rewardTo", rl.getRewardTo() == 1 ? "邀请人" : "受邀人");
            r2.put("rewardType", rewardTypeText(rl.getRewardType()));
            r2.put("rewardName", rl.getRewardName());
            r2.put("rewardValue", rl.getRewardValue());
            r2.put("status", rl.getStatus() == 1 ? "成功" : (rl.getStatus() == 2 ? "失败" : "已回滚"));
            r2.put("failReason", rl.getFailReason());
            r2.put("isAuto", rl.getIsAuto());
            r2.put("createdAt", rl.getCreatedAt());
            rewardMaps.add(r2);
        }
        m.put("rewards", rewardMaps);
        return Result.success(m);
    }

    public Result<Map<String, Object>> dashboardStats(Long campaignId) {
        Long tid = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new HashMap<>();
        // 基础计数（按 tenant 隔离；campaignId 非空时叠加）
        LambdaQueryWrapper<InviteCampaign> cq = new LambdaQueryWrapper<>();
        cq.eq(InviteCampaign::getTenantId, tid);
        if (campaignId != null) cq.eq(InviteCampaign::getId, campaignId);
        Long activeCampaigns = campaignMapper.selectCount(cq.eq(InviteCampaign::getStatus, 1));

        LambdaQueryWrapper<InviteCode> codeQ = new LambdaQueryWrapper<>();
        codeQ.eq(InviteCode::getTenantId, tid);
        if (campaignId != null) codeQ.eq(InviteCode::getCampaignId, campaignId);
        Long totalCodes = codeMapper.selectCount(codeQ);

        // 漏斗
        Map<String, Object> funnel = computeCampaignFunnel(campaignId);
        data.put("activeCampaigns", activeCampaigns);
        data.put("totalInviteCodes", totalCodes);
        data.putAll(funnel);

        // KPI：裂变带新率（最近 30 天）
        BigDecimal inviteNewRate = computeInviteNewRate(campaignId);
        data.put("inviteNewRate", inviteNewRate);
        return Result.success(data);
    }

    public Result<PageResult<Map<String, Object>>> rewardLogPage(int pageNum, int pageSize, Long campaignId,
                                                           Integer status, Integer rewardType) {
        Long tid = TenantContextHolder.get().getTenantId();
        Page<InviteRewardLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<InviteRewardLog> q = new LambdaQueryWrapper<>();
        q.eq(InviteRewardLog::getTenantId, tid);
        if (campaignId != null) q.eq(InviteRewardLog::getCampaignId, campaignId);
        if (status != null) q.eq(InviteRewardLog::getStatus, status);
        if (rewardType != null) q.eq(InviteRewardLog::getRewardType, rewardType);
        q.orderByDesc(InviteRewardLog::getCreatedAt);
        Page<InviteRewardLog> result = rewardLogMapper.selectPage(page, q);
        List<Map<String, Object>> records = new ArrayList<>();
        for (InviteRewardLog rl : result.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rl.getId());
            m.put("recordId", rl.getRecordId());
            m.put("campaignId", rl.getCampaignId());
            m.put("campaignName", rl.getCampaignName());
            m.put("rewardTo", rl.getRewardTo() == 1 ? "邀请人" : "受邀人");
            m.put("memberId", rl.getMemberId());
            m.put("memberName", rl.getMemberName());
            m.put("memberPhone", rl.getMemberPhone());
            m.put("rewardType", rl.getRewardType());
            m.put("rewardTypeText", rewardTypeText(rl.getRewardType()));
            m.put("rewardName", rl.getRewardName());
            m.put("rewardValue", rl.getRewardValue());
            m.put("couponGrantId", rl.getCouponGrantId());
            m.put("pointsRecordId", rl.getPointsRecordId());
            m.put("walletTxnId", rl.getWalletTxnId());
            m.put("status", rl.getStatus());
            m.put("statusText", rl.getStatus() == 1 ? "成功" : (rl.getStatus() == 2 ? "失败" : "已回滚"));
            m.put("failReason", rl.getFailReason());
            m.put("isAuto", rl.getIsAuto());
            m.put("createdAt", rl.getCreatedAt());
            records.add(m);
        }
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(records);
        return Result.success(com.meiyetec.util.PageResult.of(mapPage));
    }

    /**
     * 重发奖励（运营后台手工）：仅对 FAILED 记录起效
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Integer> resendReward(Long recordId) {
        Long tid = TenantContextHolder.get().getTenantId();
        InviteRecord r = recordMapper.selectById(recordId);
        if (r == null || !tid.equals(r.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "记录不存在");
        }
        if (r.getStatus() == 4) {
            return Result.error(BizCode.CONFLICT, "该记录已发放，无需重发");
        }
        if (r.getStatus() != 3 && r.getStatus() != 5) {
            return Result.error(BizCode.CONFLICT, "仅 FIRST_ORDER / FAILED 状态支持重发");
        }
        InviteCampaign c = campaignMapper.selectById(r.getCampaignId());
        if (c == null) return Result.error(BizCode.NOT_FOUND, "活动不存在");
        try {
            grantInviterReward(c, r);
            grantInviteeReward(c, r);
            r.setStatus(4);
            r.setFailReason(null);
            recordMapper.updateById(r);
            // 重新累加邀请人成功数
            InviteCode ic = codeMapper.selectOne(new LambdaQueryWrapper<InviteCode>()
                    .eq(InviteCode::getCampaignId, c.getId())
                    .eq(InviteCode::getMemberId, r.getInviterMemberId()));
            if (ic != null) {
                ic.setSuccessCount((ic.getSuccessCount() == null ? 0 : ic.getSuccessCount()) + 1);
                codeMapper.updateById(ic);
            }
            return Result.success("重发成功", 1);
        } catch (Exception e) {
            log.error("[INVITE] 重发失败 recordId={}", recordId, e);
            return Result.error(BizCode.SYSTEM_ERROR, "重发失败：" + e.getMessage());
        }
    }

    /**
     * 分享按钮上报（前端点击分享）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reportShare(Long codeId) {
        InviteCode c = codeMapper.selectById(codeId);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "邀请码不存在");
        c.setShareCount((c.getShareCount() == null ? 0 : c.getShareCount()) + 1);
        codeMapper.updateById(c);
        return Result.success(null);
    }

    // ====================================================================
    // 私有工具：奖励发放 / 配额检查 / 状态转换
    // ====================================================================

    /**
     * 邀请人奖励发放
     */
    private void grantInviterReward(InviteCampaign c, InviteRecord r) {
        Long inviterId = r.getInviterMemberId();
        Member inviter = memberMapper.selectById(inviterId);
        if (inviter == null) throw new BusinessException(BizCode.MEMBER_NOT_FOUND, "邀请人不存在");

        InviteRewardLog logRow = baseLogRow(c, r, 1, inviter);
        logRow.setRewardType(c.getInviterRewardType());
        logRow.setRewardValue(c.getInviterRewardValue());
        try {
            switch (c.getInviterRewardType()) {
                case 1 -> { // 优惠券
                    Long couponId = parseCouponIdFromMeta(c.getInviterRewardMeta());
                    if (couponId == null) {
                        throw new BusinessException(BizCode.BAD_REQUEST, "邀请活动未配置优惠券模板");
                    }
                    int updated = couponGrantMapper.atomicGrant(couponId, inviterId);
                    if (updated == 0) {
                        throw new BusinessException(BizCode.CONFLICT, "邀请券已达发放上限");
                    }
                    CouponGrant grant = new CouponGrant();
                    grant.setCouponId(couponId);
                    grant.setMemberId(inviterId);
                    grant.setTenantId(c.getTenantId());
                    grant.setCode(Keys.randomCode(12));
                    grant.setStatus(1);
                    grant.setSource(6); // 6 = 邀请奖励
                    grant.setExpireTime(LocalDateTime.now().plusDays(30));
                    couponGrantMapper.insert(grant);
                    logRow.setCouponGrantId(grant.getId());
                    logRow.setRewardName("邀请券");
                }
                case 2 -> { // 积分
                    Integer pts = c.getInviterRewardValue() == null ? 0
                            : c.getInviterRewardValue().setScale(0, RoundingMode.HALF_UP).intValue();
                    Long pid = pointService.addPointsReturningId(inviterId, "invite_reward", pts,
                            r.getId(), "邀请好友奖励");
                    logRow.setPointsRecordId(pid);
                    logRow.setRewardName(pts + "积分");
                }
                case 3 -> { // 储值
                    BigDecimal amt = c.getInviterRewardValue() == null ? BigDecimal.ZERO : c.getInviterRewardValue();
                    WalletOpDTO dto = new WalletOpDTO();
                    dto.setMemberId(inviterId);
                    dto.setAmount(amt);
                    dto.setGiftAmount(BigDecimal.ZERO);
                    dto.setPayMethod("INVITE_REWARD");
                    dto.setRemark("邀请好友奖励[source=INVITE_REWARD]");
                    Result<Map<String, Object>> rs = walletService.doRecharge(dto, c.getTenantId());
                    if (rs.getCode() != 200) {
                        throw new BusinessException(BizCode.SYSTEM_ERROR, "储值奖励失败：" + rs.getMessage());
                    }
                    Object txnId = rs.getData() == null ? null : rs.getData().get("txnId");
                    if (txnId != null) logRow.setWalletTxnId(((Number) txnId).longValue());
                    logRow.setRewardName("储值¥" + amt);
                }
                default -> throw new BusinessException(BizCode.BAD_REQUEST, "未支持的邀请人奖励类型");
            }
            logRow.setStatus(1);
            rewardLogMapper.insert(logRow);

            r.setInviterRewardLogId(logRow.getId());
            r.setInviterRewardedAt(LocalDateTime.now());
            // 注意：recordMapper.updateById 在外部事务
        } catch (BusinessException e) {
            logRow.setStatus(2);
            logRow.setFailReason(e.getMessage());
            logRow.setErrorCode(String.valueOf(e.getCode()));
            rewardLogMapper.insert(logRow);
            throw e;
        } catch (Exception e) {
            logRow.setStatus(2);
            logRow.setFailReason(e.getMessage());
            rewardLogMapper.insert(logRow);
            throw e;
        }
    }

    /**
     * 新人奖励发放
     */
    private void grantInviteeReward(InviteCampaign c, InviteRecord r) {
        if (r.getInviteeMemberId() == null) return; // 未注册，不发新人奖
        Long inviteeId = r.getInviteeMemberId();
        Member invitee = memberMapper.selectById(inviteeId);
        if (invitee == null) return;

        InviteRewardLog logRow = baseLogRow(c, r, 2, invitee);
        logRow.setRewardType(c.getInviteeRewardType());
        logRow.setRewardValue(c.getInviteeRewardValue());
        try {
            switch (c.getInviteeRewardType()) {
                case 1 -> {
                    Long couponId = parseCouponIdFromMeta(c.getInviteeRewardMeta());
                    if (couponId == null) {
                        throw new BusinessException(BizCode.BAD_REQUEST, "新人活动未配置优惠券模板");
                    }
                    int updated = couponGrantMapper.atomicGrant(couponId, inviteeId);
                    if (updated == 0) {
                        throw new BusinessException(BizCode.CONFLICT, "新人券已达发放上限");
                    }
                    CouponGrant grant = new CouponGrant();
                    grant.setCouponId(couponId);
                    grant.setMemberId(inviteeId);
                    grant.setTenantId(c.getTenantId());
                    grant.setCode(Keys.randomCode(12));
                    grant.setStatus(1);
                    grant.setSource(6);
                    grant.setExpireTime(LocalDateTime.now().plusDays(30));
                    couponGrantMapper.insert(grant);
                    logRow.setCouponGrantId(grant.getId());
                    logRow.setRewardName("新人体验券");
                }
                case 2 -> {
                    Integer pts = c.getInviteeRewardValue() == null ? 0
                            : c.getInviteeRewardValue().setScale(0, RoundingMode.HALF_UP).intValue();
                    Long pid = pointService.addPointsReturningId(inviteeId, "invite_reward", pts,
                            r.getId(), "新人邀请奖励");
                    logRow.setPointsRecordId(pid);
                    logRow.setRewardName(pts + "积分");
                }
                case 3 -> {
                    BigDecimal amt = c.getInviteeRewardValue() == null ? BigDecimal.ZERO : c.getInviteeRewardValue();
                    WalletOpDTO dto = new WalletOpDTO();
                    dto.setMemberId(inviteeId);
                    dto.setAmount(amt);
                    dto.setGiftAmount(BigDecimal.ZERO);
                    dto.setPayMethod("INVITE_REWARD_NEW");
                    dto.setRemark("新人邀请奖励[source=INVITE_REWARD_NEW]");
                    Result<Map<String, Object>> rs = walletService.doRecharge(dto, c.getTenantId());
                    if (rs.getCode() != 200) {
                        throw new BusinessException(BizCode.SYSTEM_ERROR, "新人储值失败：" + rs.getMessage());
                    }
                    Object txnId = rs.getData() == null ? null : rs.getData().get("txnId");
                    if (txnId != null) logRow.setWalletTxnId(((Number) txnId).longValue());
                    logRow.setRewardName("储值¥" + amt);
                }
                default -> throw new BusinessException(BizCode.BAD_REQUEST, "未支持的新人奖励类型");
            }
            logRow.setStatus(1);
            rewardLogMapper.insert(logRow);

            r.setInviteeRewardLogId(logRow.getId());
            r.setInviteeRewardedAt(LocalDateTime.now());
        } catch (BusinessException e) {
            logRow.setStatus(2);
            logRow.setFailReason(e.getMessage());
            logRow.setErrorCode(String.valueOf(e.getCode()));
            rewardLogMapper.insert(logRow);
            throw e;
        } catch (Exception e) {
            logRow.setStatus(2);
            logRow.setFailReason(e.getMessage());
            rewardLogMapper.insert(logRow);
            throw e;
        }
    }

    private InviteRewardLog baseLogRow(InviteCampaign c, InviteRecord r, int rewardTo, Member receiver) {
        InviteRewardLog row = new InviteRewardLog();
        row.setTenantId(c.getTenantId());
        row.setRecordId(r.getId());
        row.setCampaignId(c.getId());
        row.setCampaignName(c.getCampaignName());
        row.setRewardTo(rewardTo);
        row.setMemberId(receiver.getId());
        row.setMemberName(receiver.getName());
        row.setMemberPhone(maskPhone(receiver.getPhone()));
        row.setIsAuto(true);
        // 操作员
        try {
            var ctx = TenantContextHolder.get();
            if (ctx != null && ctx.getUserId() != null) {
                row.setOperatorId(ctx.getUserId());
                row.setOperatorName(ctx.getUsername());
            }
        } catch (Exception ignored) {}
        return row;
    }

    /**
     * activity 漏斗统计：scan → register → reward
     */
    private Map<String, Object> computeCampaignFunnel(Long campaignId) {
        Map<String, Object> m = new HashMap<>();
        if (campaignId == null) {
            m.put("scanCount", 0);
            m.put("registerCount", 0);
            m.put("firstOrderCount", 0);
            m.put("rewardCount", 0);
            m.put("conversionRate", BigDecimal.ZERO);
            return m;
        }
        Long tid = TenantContextHolder.getTenantIdOrNull();

        // 扫码数 / 注册数 = invite_code 表
        LambdaQueryWrapper<InviteCode> cq = new LambdaQueryWrapper<>();
        cq.eq(InviteCode::getCampaignId, campaignId);
        if (tid != null) cq.eq(InviteCode::getTenantId, tid);
        List<InviteCode> codes = codeMapper.selectList(cq);
        long scanCount = codes.stream().mapToLong(c -> c.getScanCount() == null ? 0 : c.getScanCount()).sum();
        long registerCount = codes.stream().mapToLong(c -> c.getRegisterCount() == null ? 0 : c.getRegisterCount()).sum();
        long successCount = codes.stream().mapToLong(c -> c.getSuccessCount() == null ? 0 : c.getSuccessCount()).sum();
        // 首单数 / 已奖励数 = invite_record 表
        LambdaQueryWrapper<InviteRecord> rq = new LambdaQueryWrapper<>();
        rq.eq(InviteRecord::getCampaignId, campaignId);
        if (tid != null) rq.eq(InviteRecord::getTenantId, tid);
        Long firstOrderCount = recordMapper.selectCount(rq.ge(InviteRecord::getStatus, 3));
        Long rewardCount = recordMapper.selectCount(rq.eq(InviteRecord::getStatus, 4));

        m.put("scanCount", scanCount);
        m.put("registerCount", registerCount);
        m.put("firstOrderCount", firstOrderCount);
        m.put("rewardCount", rewardCount);
        m.put("successCount", successCount);
        // 转化率：扫码→首单
        BigDecimal conv = scanCount == 0 ? BigDecimal.ZERO
                : new BigDecimal(firstOrderCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(scanCount), 2, RoundingMode.HALF_UP);
        m.put("conversionRate", conv);
        return m;
    }

    /**
     * 裂变带新率 = 邀请成功数 / 新增会员数（最近 30 天）
     */
    private BigDecimal computeInviteNewRate(Long campaignId) {
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid == null) return BigDecimal.ZERO;
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        LambdaQueryWrapper<InviteRecord> q = new LambdaQueryWrapper<>();
        q.eq(InviteRecord::getTenantId, tid)
         .eq(InviteRecord::getStatus, 4)
         .ge(InviteRecord::getCreatedAt, since);
        if (campaignId != null) q.eq(InviteRecord::getCampaignId, campaignId);
        Long inviteSucc = recordMapper.selectCount(q);

        // 新增会员数 = 同期 crm_member.create_time
        // 这里为简化用 invite_record.invitee_member_id 去重计数（同人首单不再计）
        LambdaQueryWrapper<InviteRecord> q2 = new LambdaQueryWrapper<>();
        q2.eq(InviteRecord::getTenantId, tid)
          .isNotNull(InviteRecord::getInviteeMemberId)
          .ge(InviteRecord::getRegisterAt, since);
        if (campaignId != null) q2.eq(InviteRecord::getCampaignId, campaignId);
        Long inviteeCount = recordMapper.selectCount(q2);
        if (inviteeCount == 0) return BigDecimal.ZERO;
        return new BigDecimal(inviteSucc).multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(inviteeCount), 2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> toCampaignMap(InviteCampaign c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("campaignCode", c.getCampaignCode());
        m.put("campaignName", c.getCampaignName());
        m.put("campaignType", c.getCampaignType());
        m.put("inviterRewardType", c.getInviterRewardType());
        m.put("inviterRewardTypeText", rewardTypeText(c.getInviterRewardType()));
        m.put("inviterRewardValue", c.getInviterRewardValue());
        m.put("inviterRewardMeta", c.getInviterRewardMeta());
        m.put("inviteeRewardType", c.getInviteeRewardType());
        m.put("inviteeRewardTypeText", rewardTypeText(c.getInviteeRewardType()));
        m.put("inviteeRewardValue", c.getInviteeRewardValue());
        m.put("inviteeRewardMeta", c.getInviteeRewardMeta());
        m.put("inviteeMinConsume", c.getInviteeMinConsume());
        m.put("inviteNeedRegister", c.getInviteNeedRegister());
        m.put("inviterQuotaPerDay", c.getInviterQuotaPerDay());
        m.put("inviterQuotaTotal", c.getInviterQuotaTotal());
        m.put("campaignQuotaTotal", c.getCampaignQuotaTotal());
        m.put("shareTitle", c.getShareTitle());
        m.put("shareDesc", c.getShareDesc());
        m.put("shareImageUrl", c.getShareImageUrl());
        m.put("shareLandingUrl", c.getShareLandingUrl());
        m.put("startTime", c.getStartTime());
        m.put("endTime", c.getEndTime());
        m.put("status", c.getStatus());
        m.put("statusText", c.getStatus() != null && c.getStatus() == 1 ? "启用" : "停用");
        m.put("remark", c.getRemark());
        m.put("createdAt", c.getCreatedAt());
        m.put("operatorName", c.getOperatorName());
        return m;
    }

    /**
     * 校验活动配额（inviter 单人 + 活动总配额）
     */
    private boolean checkQuota(InviteCode code, InviteCampaign c) {
        // 单人累计
        if (c.getInviterQuotaTotal() != null && c.getInviterQuotaTotal() > 0) {
            Long cnt = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                    .eq(InviteRecord::getCampaignId, c.getId())
                    .eq(InviteRecord::getInviterMemberId, code.getMemberId())
                    .in(InviteRecord::getStatus, 1, 2, 3, 4));
            if (cnt >= c.getInviterQuotaTotal()) return false;
        }
        // 单人每日
        if (c.getInviterQuotaPerDay() != null && c.getInviterQuotaPerDay() > 0) {
            LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
            Long today = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                    .eq(InviteRecord::getCampaignId, c.getId())
                    .eq(InviteRecord::getInviterMemberId, code.getMemberId())
                    .ge(InviteRecord::getCreatedAt, todayStart));
            if (today >= c.getInviterQuotaPerDay()) return false;
        }
        // 活动总配额
        if (c.getCampaignQuotaTotal() != null && c.getCampaignQuotaTotal() > 0) {
            Long cnt = recordMapper.selectCount(new LambdaQueryWrapper<InviteRecord>()
                    .eq(InviteRecord::getCampaignId, c.getId()));
            if (cnt >= c.getCampaignQuotaTotal()) return false;
        }
        return true;
    }

    /**
     * 生成活动内唯一邀请码（最多重试 5 次）
     */
    private String generateUniqueCode(Long campaignId) {
        for (int i = 0; i < 5; i++) {
            String code = Keys.randomCode(CODE_LENGTH);
            Long dup = codeMapper.selectCount(new LambdaQueryWrapper<InviteCode>()
                    .eq(InviteCode::getCampaignId, campaignId)
                    .eq(InviteCode::getInviteCode, code));
            if (dup == 0) return code;
        }
        // 极端兜底：用时间戳后缀
        return Keys.randomCode(CODE_LENGTH) + (System.currentTimeMillis() % 1000);
    }

    /**
     * 手机号脱敏：138****1234
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 解析优惠券模板 ID
     * 兼容格式：{"couponId": 123} 或纯数字字符串 "123"
     */
    private Long parseCouponIdFromMeta(String meta) {
        if (meta == null || meta.isBlank()) return null;
        try {
            JsonNode node = JSON.readTree(meta);
            if (node.has("couponId")) {
                return node.get("couponId").asLong();
            }
        } catch (Exception ignore) {
            // 尝试纯数字
        }
        try {
            return Long.parseLong(meta.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String statusText(Integer s) {
        if (s == null) return "未知";
        return switch (s) {
            case 1 -> "待注册";
            case 2 -> "已注册";
            case 3 -> "已首单";
            case 4 -> "已奖励";
            case 5 -> "已失效";
            case 6 -> "已拒绝";
            default -> "未知";
        };
    }

    private static String rewardTypeText(Integer t) {
        if (t == null) return "未知";
        return switch (t) {
            case 1 -> "优惠券";
            case 2 -> "积分";
            case 3 -> "储值";
            case 4 -> "自定义";
            default -> "未知";
        };
    }
}
