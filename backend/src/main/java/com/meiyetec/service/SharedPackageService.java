package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.SharedPackage;
import com.meiyetec.entity.SharedPackageInvite;
import com.meiyetec.entity.SharedPackageMember;
import com.meiyetec.entity.SharedPackageUsage;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.SharedPackageInviteMapper;
import com.meiyetec.mapper.SharedPackageMapper;
import com.meiyetec.mapper.SharedPackageMemberMapper;
import com.meiyetec.mapper.SharedPackageUsageMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P4.2.4 多人共享套餐
 *
 * 业务规则：
 * 1. 主购买者（owner）可邀请 N 人共享
 * 2. 每次使用扣减套餐总次数（不限制单个成员上限，但总次数有限）
 * 3. 共享价格 = 原总价 * 折扣系数（人数越多折扣越大）
 * 4. 邀请码 7 天有效
 * 5. 退出需 owner 同意
 */
@Slf4j
@RestController
@RequestMapping("/shared/package")
@RequiredArgsConstructor
public class SharedPackageService {

    private final SharedPackageMapper packageMapper;
    private final SharedPackageMemberMapper memberMapper;
    private final SharedPackageUsageMapper usageMapper;
    private final SharedPackageInviteMapper inviteMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ====================================================================
    // T2.1 创建共享套餐
    // ====================================================================

    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> createSharedPackage(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long ownerId = body.get("ownerMemberId") == null ? null
                : ((Number) body.get("ownerMemberId")).longValue();
        Long treatmentPlanId = body.get("treatmentPlanId") == null ? null
                : ((Number) body.get("treatmentPlanId")).longValue();
        Integer shareLimit = body.get("shareMemberLimit") == null ? 2
                : ((Number) body.get("shareMemberLimit")).intValue();
        Integer totalSessions = body.get("totalSessions") == null ? 10
                : ((Number) body.get("totalSessions")).intValue();

        if (ownerId == null) return Result.error(BizCode.BAD_REQUEST, "主持有人必填");
        if (shareLimit < 2 || shareLimit > 5) return Result.error(BizCode.BAD_REQUEST, "共享人数 2-5 人");

        // 原价 + 共享价计算
        BigDecimal originalPrice = body.get("originalPrice") == null ? BigDecimal.valueOf(5000)
                : new BigDecimal(body.get("originalPrice").toString());
        // 共享折扣：2人=95折，3人=9折，4人=85折，5人=8折
        double discount = shareLimit == 2 ? 0.95 : shareLimit == 3 ? 0.9
                : shareLimit == 4 ? 0.85 : 0.8;
        BigDecimal sharedPrice = originalPrice.multiply(BigDecimal.valueOf(discount)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perMemberPrice = sharedPrice.divide(BigDecimal.valueOf(shareLimit), 2, RoundingMode.HALF_UP);

        SharedPackage pkg = new SharedPackage();
        pkg.setTenantId(tenantId);
        pkg.setPackageCode("SHARED_" + System.currentTimeMillis());
        pkg.setPackageName("共享套餐（" + shareLimit + "人）");
        pkg.setTreatmentPlanId(treatmentPlanId);
        pkg.setTotalSessions(totalSessions);
        pkg.setUsedSessions(0);
        pkg.setShareMemberLimit(shareLimit);
        pkg.setOriginalPrice(originalPrice);
        pkg.setSharedPrice(sharedPrice);
        pkg.setPerMemberPrice(perMemberPrice);
        pkg.setOwnerMemberId(ownerId);
        pkg.setValidStartDate(LocalDate.now());
        pkg.setValidEndDate(LocalDate.now().plusDays(365));
        pkg.setExpireDays(365);
        pkg.setStatus("ACTIVE");
        packageMapper.insert(pkg);

        // 主人加入成员列表
        SharedPackageMember ownerMember = new SharedPackageMember();
        ownerMember.setSharedPackageId(pkg.getId());
        ownerMember.setMemberId(ownerId);
        ownerMember.setUsedSessions(0);
        ownerMember.setRelationship("OWNER");
        ownerMember.setJoinMethod("PURCHASE");
        ownerMember.setStatus("ACTIVE");
        memberMapper.insert(ownerMember);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("package", pkg);
        data.put("discount", discount);
        data.put("sharedDiscount", (int) ((1 - discount) * 100) + "%");
        return Result.success("已创建共享套餐", data);
    }

    // ====================================================================
    // T2.2 生成邀请码
    // ====================================================================

    @PostMapping("/invite/create")
    public Result<Map<String, Object>> createInvite(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long packageId = body.get("packageId") == null ? null
                : ((Number) body.get("packageId")).longValue();
        Long inviterId = body.get("inviterId") == null ? null
                : ((Number) body.get("inviterId")).longValue();
        if (packageId == null || inviterId == null)
            return Result.error(BizCode.BAD_REQUEST, "packageId/inviterId 必填");

        // 校验套餐存在 + 状态
        SharedPackage pkg = packageMapper.selectById(packageId);
        if (pkg == null) return Result.error(BizCode.NOT_FOUND, "套餐不存在");

        // 校验成员数是否已满
        Long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<SharedPackageMember>()
                .eq(SharedPackageMember::getSharedPackageId, packageId)
                .eq(SharedPackageMember::getStatus, "ACTIVE"));
        if (memberCount >= pkg.getShareMemberLimit()) {
            return Result.error(BizCode.BAD_REQUEST, "共享人数已满（" + pkg.getShareMemberLimit() + " 人）");
        }

        String code = generateInviteCode();

        SharedPackageInvite invite = new SharedPackageInvite();
        invite.setTenantId(tenantId);
        invite.setSharedPackageId(packageId);
        invite.setInviteCode(code);
        invite.setInviterId(inviterId);
        invite.setInviteePhone((String) body.get("inviteePhone"));
        invite.setInviteeName((String) body.get("inviteeName"));
        invite.setExpireAt(LocalDateTime.now().plusDays(7));
        invite.setStatus("PENDING");
        inviteMapper.insert(invite);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invite", invite);
        data.put("inviteUrl", "/shared/package/invite/" + code);
        return Result.success("已生成邀请码", data);
    }

    // ====================================================================
    // T2.3 接受邀请
    // ====================================================================

    @PostMapping("/invite/accept")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> acceptInvite(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("inviteCode");
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        if (code == null || memberId == null)
            return Result.error(BizCode.BAD_REQUEST, "inviteCode/memberId 必填");

        SharedPackageInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<SharedPackageInvite>()
                .eq(SharedPackageInvite::getInviteCode, code));
        if (invite == null) return Result.error(BizCode.NOT_FOUND, "邀请码无效");
        if (!"PENDING".equals(invite.getStatus())) return Result.error(BizCode.BAD_REQUEST, "邀请已失效");
        if (invite.getExpireAt() != null && invite.getExpireAt().isBefore(LocalDateTime.now()))
            return Result.error(BizCode.BAD_REQUEST, "邀请已过期");

        SharedPackage pkg = packageMapper.selectById(invite.getSharedPackageId());
        if (pkg == null || !"ACTIVE".equals(pkg.getStatus()))
            return Result.error(BizCode.NOT_FOUND, "套餐已失效");

        // 检查成员数
        Long memberCount = memberMapper.selectCount(new LambdaQueryWrapper<SharedPackageMember>()
                .eq(SharedPackageMember::getSharedPackageId, pkg.getId())
                .eq(SharedPackageMember::getStatus, "ACTIVE"));
        if (memberCount >= pkg.getShareMemberLimit())
            return Result.error(BizCode.BAD_REQUEST, "人数已满");

        // 添加成员
        SharedPackageMember existing = memberMapper.selectOne(new LambdaQueryWrapper<SharedPackageMember>()
                .eq(SharedPackageMember::getSharedPackageId, pkg.getId())
                .eq(SharedPackageMember::getMemberId, memberId));
        if (existing != null) {
            existing.setStatus("ACTIVE");
            memberMapper.updateById(existing);
        } else {
            SharedPackageMember newMem = new SharedPackageMember();
            newMem.setSharedPackageId(pkg.getId());
            newMem.setMemberId(memberId);
            newMem.setUsedSessions(0);
            newMem.setRelationship("FAMILY");
            newMem.setJoinMethod("INVITE");
            newMem.setStatus("ACTIVE");
            memberMapper.insert(newMem);
        }

        // 更新邀请
        invite.setStatus("ACCEPTED");
        invite.setAcceptedAt(LocalDateTime.now());
        invite.setAcceptedMemberId(memberId);
        inviteMapper.updateById(invite);

        return Result.success("已加入共享套餐", null);
    }

    // ====================================================================
    // T2.4 使用共享套餐（核心）
    // ====================================================================

    /**
     * 使用共享套餐：扣减总次数 + 记录使用
     */
    @PostMapping("/use")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> usePackage(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long packageId = body.get("packageId") == null ? null
                : ((Number) body.get("packageId")).longValue();
        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        Long serviceId = body.get("serviceId") == null ? null
                : ((Number) body.get("serviceId")).longValue();
        Integer sessions = body.get("sessions") == null ? 1
                : ((Number) body.get("sessions")).intValue();
        Long bookingId = body.get("bookingId") == null ? null
                : ((Number) body.get("bookingId")).longValue();

        if (packageId == null || memberId == null)
            return Result.error(BizCode.BAD_REQUEST, "packageId/memberId 必填");

        SharedPackage pkg = packageMapper.selectById(packageId);
        if (pkg == null) return Result.error(BizCode.NOT_FOUND, "套餐不存在");
        if (!"ACTIVE".equals(pkg.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "套餐已" + pkg.getStatus());
        if (pkg.getUsedSessions() + sessions > pkg.getTotalSessions())
            return Result.error(BizCode.BAD_REQUEST,
                    "剩余次数不足（剩 " + (pkg.getTotalSessions() - pkg.getUsedSessions()) + " 次）");
        if (pkg.getValidEndDate() != null && pkg.getValidEndDate().isBefore(LocalDate.now()))
            return Result.error(BizCode.BAD_REQUEST, "套餐已过期");

        // 校验成员是否在共享内
        SharedPackageMember mem = memberMapper.selectOne(new LambdaQueryWrapper<SharedPackageMember>()
                .eq(SharedPackageMember::getSharedPackageId, packageId)
                .eq(SharedPackageMember::getMemberId, memberId)
                .eq(SharedPackageMember::getStatus, "ACTIVE"));
        if (mem == null) return Result.error(BizCode.FORBIDDEN, "您不在此共享套餐内");

        // 写使用记录
        SharedPackageUsage usage = new SharedPackageUsage();
        usage.setTenantId(tenantId);
        usage.setSharedPackageId(packageId);
        usage.setMemberId(memberId);
        usage.setServiceId(serviceId);
        usage.setBookingId(bookingId);
        usage.setSessionsUsed(sessions);
        usage.setEmployeeId(body.get("employeeId") == null ? null
                : ((Number) body.get("employeeId")).longValue());
        usage.setStoreId(body.get("storeId") == null ? null
                : ((Number) body.get("storeId")).longValue());
        usage.setRemark((String) body.get("remark"));
        usageMapper.insert(usage);

        // 扣减总次数
        pkg.setUsedSessions(pkg.getUsedSessions() + sessions);
        if (pkg.getUsedSessions() >= pkg.getTotalSessions()) {
            pkg.setStatus("DEPLETED");
        }
        packageMapper.updateById(pkg);

        // 更新个人已使用
        mem.setUsedSessions((mem.getUsedSessions() == null ? 0 : mem.getUsedSessions()) + sessions);
        memberMapper.updateById(mem);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("package", pkg);
        data.put("remainingSessions", pkg.getTotalSessions() - pkg.getUsedSessions());
        data.put("usage", usage);
        data.put("message", pkg.getStatus().equals("DEPLETED") ? "套餐已用完" : "已扣减 " + sessions + " 次");
        return Result.success("已使用", data);
    }

    // ====================================================================
    // 查询接口
    // ====================================================================

    @GetMapping("/detail/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        SharedPackage pkg = packageMapper.selectById(id);
        if (pkg == null) return Result.error(BizCode.NOT_FOUND, "套餐不存在");

        List<SharedPackageMember> members = memberMapper.selectList(new LambdaQueryWrapper<SharedPackageMember>()
                .eq(SharedPackageMember::getSharedPackageId, id));
        List<SharedPackageUsage> usages = usageMapper.selectList(new LambdaQueryWrapper<SharedPackageUsage>()
                .eq(SharedPackageUsage::getSharedPackageId, id)
                .orderByDesc(SharedPackageUsage::getUsageDate));

        // 填充会员姓名
        if (!members.isEmpty()) {
            Set<Long> memberIds = members.stream().map(SharedPackageMember::getMemberId).collect(Collectors.toSet());
            List<Map<String, Object>> memberInfos = jdbc.queryForList(
                    "SELECT id, name FROM crm_member WHERE id IN (" +
                            memberIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
            Map<Long, String> idToName = new HashMap<>();
            for (Map<String, Object> m : memberInfos) {
                idToName.put(((Number) m.get("id")).longValue(), (String) m.get("name"));
            }
            for (SharedPackageMember m : members) {
                m.setRelationship(idToName.getOrDefault(m.getMemberId(), "Member#" + m.getMemberId()));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("package", pkg);
        data.put("members", members);
        data.put("usages", usages);
        data.put("remainingSessions", pkg.getTotalSessions() - pkg.getUsedSessions());
        data.put("utilizationRate", pkg.getTotalSessions() > 0
                ? Math.round(pkg.getUsedSessions() * 100.0 / pkg.getTotalSessions()) : 0);
        return Result.success(data);
    }

    @GetMapping("/list/my/{memberId}")
    public Result<List<SharedPackage>> listMy(@PathVariable Long memberId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 找出 memberId 加入的所有共享套餐
        List<Long> packageIds = memberMapper.selectList(new LambdaQueryWrapper<SharedPackageMember>()
                .eq(SharedPackageMember::getMemberId, memberId)
                .eq(SharedPackageMember::getStatus, "ACTIVE"))
                .stream().map(SharedPackageMember::getSharedPackageId).collect(Collectors.toList());
        if (packageIds.isEmpty()) return Result.success(new ArrayList<>());
        return Result.success(packageMapper.selectBatchIds(packageIds));
    }

    /**
     * 共享套餐概览统计
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            long total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shared_package WHERE tenant_id = ?", Long.class, tenantId);
            long active = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shared_package WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Long.class, tenantId);
            long depleted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shared_package WHERE tenant_id = ? AND status = 'DEPLETED'",
                    Long.class, tenantId);

            BigDecimal totalRevenue = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(shared_price), 0) FROM shared_package WHERE tenant_id = ?",
                    BigDecimal.class, tenantId);

            data.put("totalPackages", total);
            data.put("activePackages", active);
            data.put("depletedPackages", depleted);
            data.put("totalRevenue", totalRevenue);
        } catch (Exception e) {
            log.warn("共享套餐概览降级: {}", e.getMessage());
        }
        return Result.success(data);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private String generateInviteCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}