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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * R3.0-E P2 补丁：高级差异化
 *  - 多人共享套餐  /crm/package/share/...
 *  - 裂变邀请       /mkt/fission/...
 *  - A/B Test      /exp/...
 *
 * 服务前缀：/crm  + /mkt  + /exp
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class P2FeaturesService {

    private final CrmPackageShareMapper packageShareMapper;
    private final PackageOrderMapper packageOrderMapper;
    private final MktInvitationMapper invitationMapper;
    private final MktInvitationRuleMapper invitationRuleMapper;
    private final ExpExperimentMapper experimentMapper;
    private final ExpExperimentVariantMapper variantMapper;
    private final ExpAssignmentMapper assignmentMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 多人共享套餐
    // ====================================================================

    /**
     * 邀请共享：原套餐 owner 拉亲友进套餐
     */
    @PostMapping("/crm/package/share/invite")
    @Transactional(rollbackFor = Exception.class)
    public Result<CrmPackageShare> inviteShare(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long packageOrderId = body.get("packageOrderId") == null ? null
                : ((Number) body.get("packageOrderId")).longValue();
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        if (packageOrderId == null || memberId == null)
            return Result.error(BizCode.BAD_REQUEST, "packageOrderId / memberId 必填");

        // 查原套餐
        PackageOrder po = packageOrderMapper.selectById(packageOrderId);
        if (po == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");

        // 共享功能开关 + 人数限制
        Integer sharedEnabled = (Integer) po.getSharedEnabled();
        Integer sharedCount = po.getSharedCount();
        Integer maxMembers = po.getMaxShareMembers();
        if (sharedEnabled == null || sharedEnabled != 1)
            return Result.error(BizCode.BAD_REQUEST, "该套餐未开启共享");
        if (maxMembers != null && sharedCount != null && sharedCount >= maxMembers)
            return Result.error(BizCode.BAD_REQUEST,
                    "已达共享上限（" + maxMembers + "人），现有 " + sharedCount);

        // 拉会员信息
        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, name, phone FROM crm_member WHERE id=? AND deleted=0", memberId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "被邀请人不存在");

        CrmPackageShare share = new CrmPackageShare();
        share.setTenantId(tenantId);
        share.setPackageOrderId(packageOrderId);
        share.setMemberId(memberId);
        share.setMemberName((String) member.get("name"));
        share.setMemberPhone((String) member.get("phone"));
        share.setInvitedBy(po.getMemberId());
        share.setInviteAt(LocalDateTime.now());
        share.setUseTimes(0);
        share.setMaxTimes(body.get("maxTimes") == null ? null
                : ((Number) body.get("maxTimes")).intValue());
        share.setStatus(1);
        packageShareMapper.insert(share);

        // shared_count + 1
        jdbc.update(
            "UPDATE crm_package_order SET shared_count = IFNULL(shared_count,0)+1 WHERE id=?",
            packageOrderId);
        return Result.success("已邀请 " + member.get("name") + " 加入共享", share);
    }

    /** 共享核销：用 memberId 的份额去扣原套餐次数（原套餐 ownerId 不变） */
    @PostMapping("/crm/package/share/redeem")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> redeemShared(@RequestBody Map<String, Object> body) {
        Long packageOrderId = body.get("packageOrderId") == null ? null
                : ((Number) body.get("packageOrderId")).longValue();
        Long shareMemberId = body.get("shareMemberId") == null ? null
                : ((Number) body.get("shareMemberId")).longValue();
        Integer qty = body.get("qty") == null ? 1
                : ((Number) body.get("qty")).intValue();
        Integer version = body.get("version") == null ? null
                : ((Number) body.get("version")).intValue();
        if (packageOrderId == null || shareMemberId == null || version == null)
            return Result.error(BizCode.BAD_REQUEST, "packageOrderId / shareMemberId / version 必填");

        CrmPackageShare share = packageShareMapper.selectOne(
                new LambdaQueryWrapper<CrmPackageShare>()
                        .eq(CrmPackageShare::getPackageOrderId, packageOrderId)
                        .eq(CrmPackageShare::getMemberId, shareMemberId));
        if (share == null) return Result.error(BizCode.NOT_FOUND, "该用户无该套餐共享");
        if (share.getStatus() == null || share.getStatus() == 3 || share.getStatus() == 4)
            return Result.error(BizCode.CONFLICT, "共享已用尽或撤销");

        // 共享成员配额校验
        Integer used = share.getUseTimes() == null ? 0 : share.getUseTimes();
        Integer max = share.getMaxTimes();
        if (max != null && used + qty > max)
            return Result.error(BizCode.BAD_REQUEST,
                    "超过共享配额：" + used + "/" + (max == null ? "∞" : max));

        // CAS 扣原套餐次数
        int out = jdbc.update(
            "UPDATE crm_package_order SET remaining_times = remaining_times - ?, version = version+1 " +
            "WHERE id = ? AND version = ? AND remaining_times >= ? AND status = 1",
            qty, packageOrderId, version, qty);
        if (out == 0)
            return Result.error(BizCode.CONFLICT, "原套餐次数不足或版本冲突，请刷新");

        // 更新共享成员使用次数
        jdbc.update(
            "UPDATE crm_package_share SET use_times = IFNULL(use_times,0)+?, " +
            "       first_used_at = COALESCE(first_used_at, NOW()), " +
            "       status = IF(? >= ? AND ? > 0, 3, IFNULL(status,1)) " +
            "WHERE id=?",
            qty,
            (share.getMaxTimes() == null ? 999999 : share.getMaxTimes()), used + qty,
            (share.getMaxTimes() == null ? 999999 : share.getMaxTimes()),
            share.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("sharedMemberId", shareMemberId);
        data.put("packageOrderId", packageOrderId);
        data.put("qty", qty);
        data.put("newVersion", jdbc.queryForObject(
                "SELECT version FROM crm_package_order WHERE id=?", Integer.class, packageOrderId));
        data.put("sharedUseTimes", used + qty);
        return Result.success("共享核销成功", data);
    }

    /**
     * 查某套餐所有共享成员
     */
    @GetMapping("/crm/package/share/list")
    public Result<List<CrmPackageShare>> listShares(
            @RequestParam Long packageOrderId) {
        return Result.success(packageShareMapper.selectList(
                new LambdaQueryWrapper<CrmPackageShare>()
                        .eq(CrmPackageShare::getPackageOrderId, packageOrderId)
                        .orderByAsc(CrmPackageShare::getId)));
    }

    @PostMapping("/crm/package/{orderId}/enable-share")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> enableShare(@PathVariable Long orderId,
                                     @RequestParam(required = false, defaultValue = "5") Integer maxMembers) {
        PackageOrder po = packageOrderMapper.selectById(orderId);
        if (po == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");
        jdbc.update(
            "UPDATE crm_package_order SET shared_enabled=1, max_share_members=? WHERE id=?",
            maxMembers, orderId);
        return Result.success("已开启共享（上限 " + maxMembers + " 人）", null);
    }

    // ====================================================================
    // 2. 裂变邀请
    // ====================================================================

    /** 生成邀请码（基于 inviterId + 时间戳的 SHA-1 前 8 位） */
    private static String genInviteCode(long inviterId) {
        try {
            String raw = inviterId + "-" + System.currentTimeMillis();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] b = md.digest(raw.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02X", b[i]));
            return sb.toString();
        } catch (Exception e) {
            return "I" + System.currentTimeMillis() % 1000000;
        }
    }

    @PostMapping("/mkt/fission/invite")
    @Transactional(rollbackFor = Exception.class)
    public Result<MktInvitation> createInvite(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long inviterId = body.get("inviterId") == null ? null
                : ((Number) body.get("inviterId")).longValue();
        String channel = body.get("channel") == null ? "LINK" : (String) body.get("channel");
        if (inviterId == null)
            return Result.error(BizCode.BAD_REQUEST, "inviterId 必填");

        // 日邀请上限
        Integer limit = jdbc.queryForObject(
            "SELECT IFNULL(daily_limit_per_inviter, 50) FROM mkt_invitation_rule " +
            "WHERE deleted=0 AND enabled=1 AND tenant_id=? ORDER BY id DESC LIMIT 1",
            Integer.class, tenantId);
        if (limit != null) {
            Integer todayCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mkt_invitation WHERE deleted=0 AND inviter_id=? " +
                "AND DATE(create_time)=CURDATE()", Integer.class, inviterId);
            if (todayCount != null && todayCount >= limit)
                return Result.error(BizCode.BAD_REQUEST, "已达当日邀请上限（" + limit + "）");
        }

        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, name FROM crm_member WHERE id=? AND deleted=0", inviterId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "邀请人不存在");

        MktInvitation inv = new MktInvitation();
        inv.setTenantId(tenantId);
        inv.setInviterId(inviterId);
        inv.setInviterName((String) member.get("name"));
        inv.setInviteePhone((String) body.get("inviteePhone"));
        inv.setInviteeName((String) body.get("inviteeName"));
        inv.setInviteCode(genInviteCode(inviterId));
        inv.setChannel(channel);
        inv.setStatus("PENDING");
        inv.setInviteReward(BigDecimal.ZERO);
        inv.setInviteeReward(BigDecimal.ZERO);
        inv.setRewardStatus("PENDING");
        inv.setOperatorName((String) body.getOrDefault("operatorName", "system"));
        invitationMapper.insert(inv);
        return Result.success("邀请已生成", inv);
    }

    /** 被邀请人注册后调用：把邀请标记为 REGISTERED */
    @PostMapping("/mkt/fission/{inviteCode}/register")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> registerByCode(@PathVariable String inviteCode,
                                        @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MktInvitation inv = invitationMapper.selectOne(
                new LambdaQueryWrapper<MktInvitation>()
                        .eq(MktInvitation::getTenantId, tenantId)
                        .eq(MktInvitation::getInviteCode, inviteCode));
        if (inv == null) return Result.error(BizCode.NOT_FOUND, "邀请码不存在");
        if (!"PENDING".equals(inv.getStatus()))
            return Result.error(BizCode.CONFLICT, "邀请已生效，请勿重复绑定");

        Long inviteeId = body.get("inviteeId") == null ? null
                : ((Number) body.get("inviteeId")).longValue();
        if (inviteeId == null)
            return Result.error(BizCode.BAD_REQUEST, "inviteeId 必填");

        jdbc.update(
            "UPDATE mkt_invitation SET invitee_id=?, invitee_name=?, status='REGISTERED', registered_at=NOW() " +
            "WHERE id=?", inviteeId, body.get("inviteeName"), inv.getId());

        // 发注册礼包（这里只更新 invitee_reward；实际券发放留给营销模块）
        BigDecimal inviteeReward = jdbc.queryForObject(
            "SELECT IFNULL(invitee_reward_value,0) FROM mkt_invitation_rule " +
            "WHERE deleted=0 AND enabled=1 AND tenant_id=? ORDER BY id DESC LIMIT 1",
            BigDecimal.class, tenantId);
        if (inviteeReward != null && inviteeReward.compareTo(BigDecimal.ZERO) > 0) {
            jdbc.update(
                "UPDATE mkt_invitation SET invitee_reward=?, reward_status='SENT' WHERE id=?",
                inviteeReward, inv.getId());
        }
        return Result.success("绑定成功", null);
    }

    /** 被邀请人首单完成：触发邀请人奖励 */
    @PostMapping("/mkt/fission/{inviteCode}/first-order")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> markFirstOrder(@PathVariable String inviteCode,
                                        @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        MktInvitation inv = invitationMapper.selectOne(
                new LambdaQueryWrapper<MktInvitation>()
                        .eq(MktInvitation::getTenantId, tenantId)
                        .eq(MktInvitation::getInviteCode, inviteCode));
        if (inv == null) return Result.error(BizCode.NOT_FOUND, "邀请码不存在");
        if (!"REGISTERED".equals(inv.getStatus()))
            return Result.error(BizCode.CONFLICT, "被邀请人尚未注册");

        Long orderId = body.get("orderId") == null ? null
                : ((Number) body.get("orderId")).longValue();
        BigDecimal orderAmount = body.get("orderAmount") == null ? BigDecimal.ZERO
                : new BigDecimal(body.get("orderAmount").toString());
        if (orderId == null)
            return Result.error(BizCode.BAD_REQUEST, "orderId 必填");

        // 校验订单消费门槛
        BigDecimal minAmount = jdbc.queryForObject(
            "SELECT IFNULL(min_order_amount,0) FROM mkt_invitation_rule " +
            "WHERE deleted=0 AND enabled=1 AND tenant_id=? ORDER BY id DESC LIMIT 1",
            BigDecimal.class, tenantId);
        if (minAmount != null && orderAmount.compareTo(minAmount) < 0)
            return Result.error(BizCode.BAD_REQUEST, "未达首单门槛（需 ≥" + minAmount + "）");

        jdbc.update(
            "UPDATE mkt_invitation SET first_order_id=?, first_order_at=NOW(), status='ORDERED' WHERE id=?",
            orderId, inv.getId());

        // 发邀请人奖励
        BigDecimal inviterReward = jdbc.queryForObject(
            "SELECT IFNULL(inviter_reward_value,0) FROM mkt_invitation_rule " +
            "WHERE deleted=0 AND enabled=1 AND tenant_id=? ORDER BY id DESC LIMIT 1",
            BigDecimal.class, tenantId);
        if (inviterReward != null && inviterReward.compareTo(BigDecimal.ZERO) > 0) {
            jdbc.update(
                "UPDATE mkt_invitation SET invite_reward=?, status='REWARDED', reward_status='SENT' WHERE id=?",
                inviterReward, inv.getId());
        }
        return Result.success("首单奖励已发放", null);
    }

    @GetMapping("/mkt/fission/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(required = false) Long inviterId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) total, " +
            "SUM(CASE WHEN status='PENDING' THEN 1 ELSE 0 END) pending, " +
            "SUM(CASE WHEN status='REGISTERED' THEN 1 ELSE 0 END) registered, " +
            "SUM(CASE WHEN status='ORDERED' THEN 1 ELSE 0 END) ordered, " +
            "SUM(CASE WHEN status='REWARDED' THEN 1 ELSE 0 END) rewarded, " +
            "IFNULL(SUM(invite_reward),0) total_reward " +
            "FROM mkt_invitation WHERE deleted=0 AND tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (inviterId != null) { sql.append("AND inviter_id=? "); args.add(inviterId); }
        Map<String, Object> row = jdbc.queryForMap(sql.toString(), args.toArray());
        Map<String, Object> data = new HashMap<>(row);
        data.put("conversionRate",
                (Long) row.getOrDefault("ordered", 0) == 0 ? 0
                        : Math.round(((Long) row.getOrDefault("ordered", 0)) * 1000.0
                                / (Long) row.getOrDefault("total", 1)) / 10.0);
        return Result.success(data);
    }

    // ====================================================================
    // 3. A/B Test 平台
    // ====================================================================

    @PostMapping("/exp")
    @Transactional(rollbackFor = Exception.class)
    public Result<ExpExperiment> createExperiment(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String expCode = (String) body.get("expCode");
        String expName = (String) body.get("expName");
        if (expCode == null || expName == null)
            return Result.error(BizCode.BAD_REQUEST, "expCode / expName 必填");

        // 校验编码唯一
        Long dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM exp_experiment WHERE deleted=0 AND tenant_id=? AND exp_code=?",
            Long.class, tenantId, expCode);
        if (dup != null && dup > 0)
            return Result.error(BizCode.CONFLICT, "实验编码已存在");

        ExpExperiment exp = new ExpExperiment();
        exp.setTenantId(tenantId);
        exp.setBrandId(body.get("brandId") == null ? null
                : ((Number) body.get("brandId")).longValue());
        exp.setExpCode(expCode);
        exp.setExpName(expName);
        exp.setExpType((String) body.getOrDefault("expType", "UI"));
        exp.setDescription((String) body.get("description"));
        exp.setStatus("DRAFT");
        exp.setTargetMetric((String) body.get("targetMetric"));
        exp.setSampleSize((Integer) body.getOrDefault("sampleSize", 0));
        exp.setConfidenceLevel(new BigDecimal(body.getOrDefault("confidenceLevel", "95.00").toString()));
        exp.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        experimentMapper.insert(exp);

        // 默认 2 个变体
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variants = (List<Map<String, Object>>) body.get("variants");
        if (variants != null && !variants.isEmpty()) {
            for (Map<String, Object> v : variants) {
                ExpExperimentVariant vv = new ExpExperimentVariant();
                vv.setTenantId(tenantId);
                vv.setExperimentId(exp.getId());
                vv.setVariantCode((String) v.get("variantCode"));
                vv.setVariantName((String) v.get("variantName"));
                vv.setTrafficWeight((Integer) v.getOrDefault("trafficWeight", 50));
                vv.setPayload(v.get("payload") == null ? null : v.get("payload").toString());
                vv.setIsControl((Integer) v.getOrDefault("isControl", 0));
                vv.setExposureCount(0L);
                vv.setConversionCount(0L);
                variantMapper.insert(vv);
            }
        }
        return Result.success("实验已创建（含 " + (variants == null ? 0 : variants.size()) + " 变体）", exp);
    }

    @PostMapping("/exp/{id}/start")
    public Result<Void> startExperiment(@PathVariable Long id) {
        ExpExperiment exp = experimentMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        if (!"DRAFT".equals(exp.getStatus()) && !"PAUSED".equals(exp.getStatus()))
            return Result.error(BizCode.CONFLICT, "当前状态不可启动");
        jdbc.update(
            "UPDATE exp_experiment SET status='RUNNING', started_at=NOW() WHERE id=?",
            id);
        return Result.success("实验已开始", null);
    }

    @PostMapping("/exp/{id}/stop")
    public Result<Void> stopExperiment(@PathVariable Long id) {
        ExpExperiment exp = experimentMapper.selectById(id);
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        jdbc.update(
            "UPDATE exp_experiment SET status='FINISHED', ended_at=NOW() WHERE id=?",
            id);
        return Result.success("实验已结束", null);
    }

    /**
     * 分配 subjectId 到一个变体（按 trafficWeight 加权抽样）
     * GET /exp/{expCode}/assign?subjectId=xxx
     */
    @GetMapping("/exp/{expCode}/assign")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> assign(@PathVariable String expCode,
                                               @RequestParam String subjectId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        ExpExperiment exp = experimentMapper.selectOne(
                new LambdaQueryWrapper<ExpExperiment>()
                        .eq(ExpExperiment::getTenantId, tenantId)
                        .eq(ExpExperiment::getExpCode, expCode));
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");
        if (!"RUNNING".equals(exp.getStatus()))
            return Result.error(BizCode.CONFLICT, "实验未运行");

        // 已分配过？返回同一变体（保证一致性）
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT variant_id FROM exp_assignment WHERE experiment_id=? AND subject_id=?",
            exp.getId(), subjectId);
        if (!existing.isEmpty()) {
            Long existingVariantId = ((Number) existing.get(0).get("variant_id")).longValue();
            ExpExperimentVariant v = variantMapper.selectById(existingVariantId);
            Map<String, Object> data = new HashMap<>();
            data.put("experimentId", exp.getId());
            data.put("experimentCode", exp.getExpCode());
            data.put("variantId", v.getId());
            data.put("variantCode", v.getVariantCode());
            data.put("variantName", v.getVariantName());
            data.put("payload", v.getPayload());
            data.put("alreadyAssigned", true);
            return Result.success(data);
        }

        // 取所有变体按权重抽样（加权轮盘）
        List<ExpExperimentVariant> variants = variantMapper.selectList(
                new LambdaQueryWrapper<ExpExperimentVariant>()
                        .eq(ExpExperimentVariant::getExperimentId, exp.getId())
                        .eq(ExpExperimentVariant::getDeleted, 0));
        if (variants.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "实验未配置变体");

        int totalWeight = variants.stream().mapToInt(ExpExperimentVariant::getTrafficWeight).sum();
        int rand = new Random(subjectId.hashCode() ^ exp.getId().intValue()).nextInt(totalWeight);
        int cursor = 0;
        ExpExperimentVariant chosen = variants.get(0);
        for (ExpExperimentVariant v : variants) {
            cursor += v.getTrafficWeight();
            if (rand < cursor) { chosen = v; break; }
        }

        // 写分配记录
        ExpAssignment as = new ExpAssignment();
        as.setTenantId(tenantId);
        as.setExperimentId(exp.getId());
        as.setVariantId(chosen.getId());
        as.setSubjectId(subjectId);
        as.setExposureAt(LocalDateTime.now());
        as.setConverted(0);
        assignmentMapper.insert(as);
        // 曝光 +1
        jdbc.update("UPDATE exp_experiment_variant SET exposure_count=exposure_count+1 WHERE id=?",
                chosen.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("experimentId", exp.getId());
        data.put("experimentCode", exp.getExpCode());
        data.put("variantId", chosen.getId());
        data.put("variantCode", chosen.getVariantCode());
        data.put("variantName", chosen.getVariantName());
        data.put("payload", chosen.getPayload());
        data.put("alreadyAssigned", false);
        return Result.success(data);
    }

    /** 标记转化 */
    @PostMapping("/exp/{expCode}/convert")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> markConvert(@PathVariable String expCode,
                                     @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String subjectId = (String) body.get("subjectId");
        if (subjectId == null) return Result.error(BizCode.BAD_REQUEST, "subjectId 必填");

        ExpExperiment exp = experimentMapper.selectOne(
                new LambdaQueryWrapper<ExpExperiment>()
                        .eq(ExpExperiment::getTenantId, tenantId)
                        .eq(ExpExperiment::getExpCode, expCode));
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");

        int updated = jdbc.update(
            "UPDATE exp_assignment SET converted=1, converted_at=NOW(), metric_value=? " +
            "WHERE experiment_id=? AND subject_id=? AND converted=0",
            body.get("metricValue"), exp.getId(), subjectId);
        if (updated == 0)
            return Result.error(BizCode.CONFLICT, "未找到分配记录或已转化");

        // 该变体 conversion +1
        jdbc.update(
            "UPDATE exp_experiment_variant SET conversion_count = conversion_count + 1 " +
            "WHERE id = (SELECT variant_id FROM exp_assignment " +
            "  WHERE experiment_id=? AND subject_id=?)",
            exp.getId(), subjectId);
        return Result.success("已标记转化", null);
    }

    /** 实验结果：每个变体的转化率 + 简单显著性判断 */
    @GetMapping("/exp/{expCode}/result")
    public Result<Map<String, Object>> result(@PathVariable String expCode) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        ExpExperiment exp = experimentMapper.selectOne(
                new LambdaQueryWrapper<ExpExperiment>()
                        .eq(ExpExperiment::getTenantId, tenantId)
                        .eq(ExpExperiment::getExpCode, expCode));
        if (exp == null) return Result.error(BizCode.NOT_FOUND, "实验不存在");

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT v.id, v.variant_code, v.variant_name, v.exposure_count, v.conversion_count, " +
            "       CASE WHEN v.exposure_count > 0 " +
            "         THEN ROUND(v.conversion_count * 100.0 / v.exposure_count, 2) " +
            "         ELSE 0 END AS conv_rate " +
            "FROM exp_experiment_variant v WHERE v.experiment_id=? AND v.deleted=0 " +
            "ORDER BY v.is_control DESC, v.id ASC", exp.getId());

        // 简单显著性：对照组 vs 实验组（双比例 z 检验近似）
        Map<String, Object> data = new HashMap<>();
        data.put("experiment", exp);
        data.put("variants", rows);
        if (rows.size() == 2) {
            Map<String, Object> a = rows.get(0);
            Map<String, Object> b = rows.get(1);
            long exA = ((Number) a.get("exposure_count")).longValue();
            long cvA = ((Number) a.get("conversion_count")).longValue();
            long exB = ((Number) b.get("exposure_count")).longValue();
            long cvB = ((Number) b.get("conversion_count")).longValue();
            if (exA > 30 && exB > 30) {
                double pA = exA == 0 ? 0 : cvA * 1.0 / exA;
                double pB = exB == 0 ? 0 : cvB * 1.0 / exB;
                double p = (cvA + cvB) * 1.0 / (exA + exB);
                double se = Math.sqrt(p * (1 - p) * (1.0 / exA + 1.0 / exB));
                double z = se == 0 ? 0 : (pB - pA) / se;
                boolean significant = Math.abs(z) >= 1.96; // 95% 置信度
                data.put("zScore", Math.round(z * 1000.0) / 1000.0);
                data.put("significantAt95", significant);
                data.put("controlConvRate", Math.round(pA * 10000) / 100.0 + "%");
                data.put("variantConvRate", Math.round(pB * 10000) / 100.0 + "%");
                data.put("lift", Math.round((pA == 0 ? 0 : (pB - pA) * 100.0 / pA) * 100) / 100.0 + "%");
            }
        }
        return Result.success(data);
    }
}