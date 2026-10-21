package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.dto.PackagePurchaseDTO;
import com.meiyetec.dto.PackageRedeemDTO;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.dto.PackageSaveDTO;
import com.meiyetec.dto.PackageTransferDTO;
import com.meiyetec.dto.PackageSplitRedeemDTO;
import com.meiyetec.dto.PackageUpgradeDTO;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.PackageOrderMapper;
import com.meiyetec.mapper.PackageStatusLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.*;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 套餐次卡模块
 * M05 - T05-08 ~ T05-15
 */
@Slf4j
@RestController
@RequestMapping("/packages")
@RequiredArgsConstructor
@SysOpLog(module = "套餐")
public class PackageController {

    private final PackageItemService packageItemService;
    private final PackageBundleService packageBundleService;
    @Autowired
    private JdbcTemplate jdbc;
    private final PackageOrderService orderService;
    private final PackageRedemptionService redeemService;
    private final MemberService memberService;
    private final EmployeeService employeeService;
    private final WalletService walletService;
    private final WalletTransactionService walletTxnService;
    private final IdempotencyService idem;
    /** R3.0-B 补丁：跨店核销检查（R3.0-B+：服务迁移到 controller 包，类名同步调整） */
    private final CrossStoreController crossStoreController;
    /** M04 完整链路：转赠 / 退款 / 升级的 CAS 原子写 */
    private final PackageOrderMapper orderMapper;
    /** M04-04：套餐状态变更审计日志（冻结/解冻/挂起/恢复） */
    private final PackageStatusLogMapper statusLogMapper;

    private static final int ORDER_NORMAL    = 1;
    private static final int ORDER_USED      = 2;
    private static final int ORDER_EXPIRED   = 3;
    private static final int ORDER_FROZEN    = 4;
    private static final int ORDER_REFUNDED  = 5;   // 已退款
    private static final int ORDER_TRANSFER  = 6;   // 已转赠
    private static final int ORDER_UPGRADED  = 7;   // 已升级（M04-07）
    private static final int ORDER_SUSPENDED = 8;   // 已挂起（M04-04 新增）

    // ==================== T05-09 套餐列表 ====================
    @GetMapping
    @RequirePermission("package:list")
    public Result<PageResult<PackageItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Page<PackageItem> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PackageItem> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) wrapper.eq(PackageItem::getTenantId, tenantId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(PackageItem::getName, keyword)
                    .or().like(PackageItem::getCode, keyword));
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(PackageItem::getCategory, category);
        }
        if (status != null) wrapper.eq(PackageItem::getStatus, status);
        wrapper.orderByDesc(PackageItem::getId);
        Page<PackageItem> result = packageItemService.page(page, wrapper);
        result.getRecords().forEach(p -> p.setStatusLabel(statusLabel(p.getStatus())));
        return Result.success(PageResult.of(result));
    }

    // ==================== T05-10 套餐详情 ====================
    @GetMapping("/{id:\\d+}")
    @RequirePermission("package:list")
    public Result<PackageItem> detail(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PackageItem p = packageItemService.getById(id);
        if (p == null) return Result.error("套餐不存在");
        if (tenantId != null && p.getTenantId() != null && !p.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该套餐");
        p.setStatusLabel(statusLabel(p.getStatus()));
        // M04 bundle: 解析子项目快照，供前端展示组合套餐明细
        if (p.getBundleType() != null && p.getBundleType() == 1) {
            p.setBundleItems(packageBundleService.parseBundle(p.getBundleConfig()));
        }
        return Result.success(p);
    }

    // ==================== T05-11 创建/更新套餐（M04 bundle 支持） ====================
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:add")
    public Result<PackageItem> save(@Valid @RequestBody PackageSaveDTO dto) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        Integer bundleType = dto.getBundleType() == null ? 0 : dto.getBundleType();
        if (bundleType == 1) {
            // 组合套餐：校验子项目
            try {
                packageBundleService.validateBundle(dto.getBundleItems());
            } catch (IllegalArgumentException ex) {
                return Result.error(BizCode.BAD_REQUEST, ex.getMessage());
            }
        }
        if (dto.getId() == null) {
            PackageItem p = new PackageItem();
            p.setTenantId(tenantId);
            p.setName(dto.getName());
            p.setCode(dto.getCode());
            p.setCategory(dto.getCategory() == null ? "通用" : dto.getCategory());
            p.setDescription(dto.getDescription());
            p.setPrice(dto.getPrice());
            p.setCostPrice(dto.getCostPrice() == null ? java.math.BigDecimal.ZERO : dto.getCostPrice());
            p.setValidDays(dto.getValidDays() == null ? 365 : dto.getValidDays());
            p.setSaleCount(0);
            p.setRedeemCount(0);
            p.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            p.setRemark(dto.getRemark());
            // M04 bundle: 写入组合结构
            if (bundleType == 1) {
                packageBundleService.applyBundleToPackage(p, dto.getBundleItems());
            } else {
                p.setTotalTimes(dto.getTotalTimes());
                p.setBundleType(0);
                p.setBundleConfig(null);
                p.setBundleItemCount(0);
            }
            packageItemService.save(p);
            return Result.success("套餐创建成功", p);
        } else {
            PackageItem p = packageItemService.getById(dto.getId());
            if (p == null) return Result.error("套餐不存在");
            if (tenantId != null && p.getTenantId() != null && !p.getTenantId().equals(tenantId))
                return Result.error(BizCode.FORBIDDEN, "无权修改该套餐");
            p.setName(dto.getName());
            p.setCode(dto.getCode());
            p.setCategory(dto.getCategory());
            p.setDescription(dto.getDescription());
            p.setPrice(dto.getPrice());
            if (dto.getCostPrice() != null) p.setCostPrice(dto.getCostPrice());
            if (dto.getValidDays() != null) p.setValidDays(dto.getValidDays());
            if (dto.getStatus() != null) p.setStatus(dto.getStatus());
            p.setRemark(dto.getRemark());
            // M04 bundle: 更新组合结构
            if (bundleType == 1) {
                packageBundleService.applyBundleToPackage(p, dto.getBundleItems());
            } else {
                p.setBundleType(0);
                p.setBundleConfig(null);
                p.setBundleItemCount(0);
                if (dto.getTotalTimes() != null) p.setTotalTimes(dto.getTotalTimes());
            }
            packageItemService.updateById(p);
            return Result.success("套餐更新成功", p);
        }
    }

    // ==================== T05-12 购买套餐 ====================
    @PostMapping("/purchase")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:purchase")
    public Result<Map<String, Object>> purchase(@Valid @RequestBody PackagePurchaseDTO dto) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        Member member = memberService.getById(dto.getMemberId());
        if (member == null) return Result.error("会员不存在");
        if (member.getStatus() != null && member.getStatus() != 1) {
            return Result.error("会员已冻结，不可购买");
        }
        PackageItem p = packageItemService.getById(dto.getPackageId());
        if (p == null) return Result.error("套餐不存在");
        // S-5: 财务接口 tenant 隔离 — Package 必须归属当前租户
        if (tenantId != null && p.getTenantId() != null && !tenantId.equals(p.getTenantId())) {
            throw new BusinessException(BizCode.TENANT_DENIED, "无权购买该套餐（跨租户）");
        }
        if (p.getStatus() != null && p.getStatus() != 1) {
            return Result.error("套餐已下架");
        }

        PackageOrder order = new PackageOrder();
        order.setTenantId(tenantId);
        order.setOrderNo(genOrderNo());
        order.setMemberId(dto.getMemberId());
        order.setStoreId(TenantContextHolder.get().getStoreId());  // R3.0-B 补丁：记录购买门店
        order.setPackageId(p.getId());
        order.setPackageName(p.getName());
        order.setTotalTimes(p.getTotalTimes());
        order.setRemainingTimes(p.getTotalTimes());
        order.setPrice(dto.getPrice());
        order.setPayMethod(dto.getPayMethod() == null ? "微信" : dto.getPayMethod());
        order.setOperatorId(dto.getOperatorId());
        order.setBuyTime(LocalDateTime.now());
        order.setExpireTime(LocalDateTime.now().plusDays(p.getValidDays() == null ? 365 : p.getValidDays()));
        order.setStatus(ORDER_NORMAL);
        order.setRemark(dto.getRemark() == null ? "购买套餐" : dto.getRemark());
        orderService.save(order);

        p.setSaleCount(p.getSaleCount() == null ? 1 : p.getSaleCount() + 1);
        packageItemService.updateById(p);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("memberId", order.getMemberId());
        data.put("packageId", order.getPackageId());
        data.put("packageName", order.getPackageName());
        data.put("totalTimes", order.getTotalTimes());
        data.put("remainingTimes", order.getRemainingTimes());
        data.put("price", order.getPrice());
        data.put("expireTime", order.getExpireTime());
        return Result.success("购买成功", data);
    }

    // ==================== T05-13 套餐核销（R2.0-B 升级版） ====================
    @PostMapping("/redeem")
    @RequirePermission("package:redeem")
    public Result<Map<String, Object>> redeem(@Valid @RequestBody PackageRedeemDTO dto,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "package.redeem", key, dto, () -> doRedeem(dto, tenantId));
        return r.result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doRedeem(PackageRedeemDTO dto, Long tenantId) {
        Long orderId = dto.getOrderId();
        int times = dto.getTimes() == null ? 1 : dto.getTimes();
        if (times <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "核销次数必须 > 0");
        }

        PackageOrder order = orderService.getById(orderId);
        if (order == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "套餐订单不存在");
        }
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId)) {
            throw new BusinessException(BizCode.FORBIDDEN, "无权核销该套餐");
        }

        // R3.0-B 补丁：跨店核销拦截（在主流程最开始，避免 CAS 成功后又失败）
        CrossStoreController.CrossStoreCheckResult csr = crossStoreController.checkCrossStoreAndQuote(
                order.getMemberId(), order.getStoreId(), "PACKAGE",
                order.getPrice() == null ? BigDecimal.ZERO : order.getPrice());
        if (!csr.allowed) {
            throw new BusinessException(BizCode.FORBIDDEN, "跨店核销被拒绝：" + csr.message);
        }
        BigDecimal crossFee = csr.crossFee;
        Long crossRuleId = csr.isCross ? csr.ruleId : null;

        if (order.getStatus() != null) {
            switch (order.getStatus()) {
                case 2: throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已用完");
                case 3: throw new BusinessException(BizCode.PACKAGE_EXPIRED, "该套餐已过期");
                case 4: throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已冻结，无法核销");
                case 5: throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已退款，无法核销");
                case 6: throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已转赠，无法核销");
                case 8: throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐已挂起，请先恢复挂起后再核销");
                default: break;
            }
        }
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            order.setStatus(ORDER_EXPIRED);
            orderService.updateById(order);
            throw new BusinessException(BizCode.PACKAGE_EXPIRED, "该套餐已过期");
        }

        PackageOrderMapper orderMapper = (PackageOrderMapper) orderService.getBaseMapper();
        int rows = orderMapper.casRedeem(orderId, order.getVersion(), times);
        if (rows == 0) {
            // 版本过期或次数不足：抛具体错误
            PackageOrder now = orderService.getById(orderId);
            if (now == null) throw new BusinessException(BizCode.NOT_FOUND, "套餐订单不存在");
            if (now.getRemainingTimes() == null || now.getRemainingTimes() < times) {
                throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH,
                        "剩余次数不足，当前剩余 " + now.getRemainingTimes());
            }
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "套餐核销并发冲突，请重试");
        }

        // CAS 成功后重读最新状态
        PackageOrder latest = orderService.getById(orderId);

        PackageRedemption r = new PackageRedemption();
        r.setTenantId(tenantId);
        r.setOrderId(orderId);
        r.setStoreId(TenantContextHolder.get().getStoreId());  // R3.0-B 补丁：核销门店
        r.setMemberId(latest.getMemberId());
        r.setPackageId(latest.getPackageId());
        r.setTimes(times);
        r.setCrossFee(crossFee);             // R3.0-B 补丁：跨店手续费
        r.setCrossRuleId(crossRuleId);       // R3.0-B 补丁：命中规则
        r.setServiceItem(dto.getServiceItem());
        r.setBeauticianId(dto.getBeauticianId());
        r.setNote(dto.getNote());
        r.setOperatorId(dto.getOperatorId());
        r.setRedeemTime(LocalDateTime.now());
        r.setStatus(1);
        redeemService.save(r);

        // 累计 redeemCount
        PackageItem p = packageItemService.getById(latest.getPackageId());
        if (p != null) {
            p.setRedeemCount(p.getRedeemCount() == null ? times : p.getRedeemCount() + times);
            packageItemService.updateById(p);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("redemptionId", r.getId());
        data.put("orderId", orderId);
        data.put("redeemTimes", times);
        data.put("remainingTimes", latest.getRemainingTimes());
        data.put("orderStatus", latest.getStatus());
        return Result.success("核销成功", data);
    }

    // ==================== T05-14 会员套餐查询 ====================
    @GetMapping("/member/{memberId}")
    @RequirePermission("package:list")
    public Result<PageResult<PackageOrder>> memberPackages(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (memberService.getById(memberId) == null) return Result.error("会员不存在");
        Page<PackageOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PackageOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PackageOrder::getMemberId, memberId);
        if (tenantId != null) wrapper.eq(PackageOrder::getTenantId, tenantId);
        if (status != null) wrapper.eq(PackageOrder::getStatus, status);
        wrapper.orderByDesc(PackageOrder::getId);
        Page<PackageOrder> result = orderService.page(page, wrapper);
        result.getRecords().forEach(o -> {
            o.setStatusLabel(ordeStatusLabel(o.getStatus()));
            if (o.getMemberId() != null) {
                Member m = memberService.getById(o.getMemberId());
                if (m != null) { o.setMemberName(m.getName()); o.setMemberPhone(m.getPhone()); }
            }
        });
        return Result.success(PageResult.of(result));
    }

    // ==================== T05-15 套餐冻结/解冻（M04-04 改造版） ====================
    /**
     * M04-04 套餐冻结/解冻（完整链路）
     *
     * <p>语义区分：
     * <ul>
     *   <li>冻结（FREEZE，{@code freeze=true}）：硬性停止使用，常见于欠款 / 违规 / 投诉冻结，
     *       解冻需人工审核或还款。</li>
     *   <li>解冻（UNFREEZE，{@code freeze=false}）：回到正常状态。</li>
     * </ul>
     *
     * <p>完整校验：
     * <ol>
     *   <li>源订单存在 + 同租户（防跨租户）</li>
     *   <li>{@code freeze=true} 时：当前状态必须是 1（正常）</li>
     *   <li>{@code freeze=false} 时：当前状态必须是 4（已冻结）</li>
     *   <li>CAS 原子写（{@code casFreeze} / {@code casUnfreeze}）：version + status 双守卫</li>
     *   <li>审计日志：写 {@code crm_package_status_log}</li>
     * </ol>
     *
     * <p>权限：{@code package:freeze}（RBAC）。
     *
     * <p>调用样例：
     * <pre>{@code
     *   POST /packages/order/{orderId}/freeze
     *   body: { "freeze": true, "reason": "投诉冻结", "operatorId": 1 }
     *   POST /packages/order/{orderId}/freeze?freeze=false   // 兼容旧接口
     * }</pre>
     */
    @PostMapping("/order/{orderId}/freeze")
    @RequirePermission("package:freeze")
    @Transactional(rollbackFor = Exception.class)
    public Result<PackageOrder> freeze(
            @PathVariable Long orderId,
            @RequestParam(required = false, defaultValue = "true") Boolean freeze,
            @RequestBody(required = false) com.meiyetec.dto.PackageFreezeDTO body,
            HttpServletRequest request) {
        Long currentTenantId = TenantContextHolder.get().getTenantId();
        // body 可能为空（旧接口用 query string 传 ?freeze=xxx），向后兼容
        boolean wantFreeze = freeze;
        Long operatorId = null;
        String reason = null;
        if (body != null) {
            if (body.getFreeze() != null) wantFreeze = body.getFreeze();
            if (body.getOperatorId() != null) operatorId = body.getOperatorId();
            if (body.getReason() != null && !body.getReason().isBlank()) reason = body.getReason();
        }

        // ---------- 1. 源订单存在 + 跨租户拦截 ----------
        PackageOrder order = orderService.getById(orderId);
        if (order == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");
        if (currentTenantId != null && order.getTenantId() != null
                && !currentTenantId.equals(order.getTenantId())) {
            log.warn("[package-freeze] cross-tenant blocked: orderId={} orderTenant={} currentTenant={}",
                    orderId, order.getTenantId(), currentTenantId);
            return Result.error(BizCode.TENANT_DENIED, "无权操作其它租户的套餐");
        }

        Integer srcVersion = order.getVersion() == null ? 0 : order.getVersion();
        Integer beforeStatus = order.getStatus();
        String action = wantFreeze ? "FREEZE" : "UNFREEZE";
        String operatorName = resolveOperatorName(operatorId, request);

        // ---------- 2-3. 状态合法性 + CAS ----------
        int rows;
        if (wantFreeze) {
            if (beforeStatus != null && beforeStatus != ORDER_NORMAL) {
                return Result.error(BizCode.ORDER_STATUS_INVALID,
                        "仅「正常」状态的订单可冻结（当前状态：" + ordeStatusLabel(beforeStatus) + "）");
            }
            if (reason == null || reason.isBlank()) {
                return Result.error(BizCode.BAD_REQUEST, "请填写冻结原因（reason 必填）");
            }
            rows = orderMapper.casFreeze(orderId, srcVersion);
            if (rows == 0) {
                return Result.error(BizCode.CONCURRENCY_CONFLICT, "冻结并发冲突，请刷新页面后重试");
            }
        } else {
            if (beforeStatus == null || beforeStatus != ORDER_FROZEN) {
                return Result.error(BizCode.ORDER_STATUS_INVALID,
                        "仅「已冻结」状态的订单可解冻（当前状态：" + ordeStatusLabel(beforeStatus) + "）");
            }
            rows = orderMapper.casUnfreeze(orderId, srcVersion);
            if (rows == 0) {
                return Result.error(BizCode.CONCURRENCY_CONFLICT, "解冻并发冲突，请刷新页面后重试");
            }
        }

        // ---------- 4. 审计日志 ----------
        writeStatusLog(order, action, beforeStatus,
                wantFreeze ? ORDER_FROZEN : ORDER_NORMAL,
                reason, null, operatorId, operatorName, request);

        // ---------- 5. 重读返回最新数据 ----------
        PackageOrder latest = orderService.getById(orderId);
        if (latest != null) {
            latest.setStatusLabel(ordeStatusLabel(latest.getStatus()));
            String tag = appendRemark(latest.getRemark(),
                    action + " @" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            + " by " + (operatorName == null ? "system" : operatorName)
                            + (reason == null ? "" : " | " + reason));
            latest.setRemark(tag);
            orderService.updateById(latest);
        }

        log.info("[package-{}] orderId={} orderNo={} operator={}({}) reason={}",
                action.toLowerCase(), orderId, order.getOrderNo(), operatorName, operatorId, reason);
        return Result.success(wantFreeze ? "已冻结该套餐" : "已解冻该套餐", latest);
    }

    // ==================== M04-04 套餐挂起（暂停使用） ====================
    /**
     * M04-04 套餐挂起（暂停使用，与冻结语义分离）
     *
     * <p>使用场景：
     * <ul>
     *   <li>会员出差 / 怀孕 / 短期无法到店：临时暂停套餐使用</li>
     *   <li>系统排查期临时暂停某个会员的套餐</li>
     * </ul>
     *
     * <p>与冻结的区别：
     * <ul>
     *   <li>冻结（FREEZE）：硬性停止，通常因欠款/违规，解冻需走人工审核</li>
     *   <li>挂起（SUSPEND）：可携带到期时间（{@code suspendUntil}），到期可自动恢复；
     *       不带到期时间则需人工恢复</li>
     * </ul>
     *
     * <p>挂起期间行为：
     * <ul>
     *   <li>❌ 不可核销（{@code redeem} 状态机拦截）</li>
     *   <li>❌ 不可转让（{@code transfer} 要求 status=1）</li>
     *   <li>❌ 不可退款（{@code refund} 要求 status=1）</li>
     *   <li>✅ 可恢复（{@code /order/{id}/resume}）</li>
     * </ul>
     *
     * <p>权限：{@code package:suspend}（RBAC）
     */
    @PostMapping("/order/{orderId}/suspend")
    @RequirePermission("package:suspend")
    @Transactional(rollbackFor = Exception.class)
    public Result<PackageOrder> suspend(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) com.meiyetec.dto.PackageSuspendDTO dto,
            HttpServletRequest request) {
        Long currentTenantId = TenantContextHolder.get().getTenantId();

        // 默认值：dto 可能为空（单纯挂起，不指定到期时间）
        String reason = null;
        LocalDateTime suspendUntil = null;
        Long operatorId = null;
        if (dto != null) {
            reason = dto.getReason();
            suspendUntil = dto.getSuspendUntil();
            operatorId = dto.getOperatorId();
        }

        // ---------- 1. reason 必填（即使会员主动申请也要说明） ----------
        if (reason == null || reason.isBlank()) {
            return Result.error(BizCode.BAD_REQUEST, "请填写挂起原因（reason 必填）");
        }

        // ---------- 2. 订单存在 + 跨租户拦截 ----------
        PackageOrder order = orderService.getById(orderId);
        if (order == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");
        if (currentTenantId != null && order.getTenantId() != null
                && !currentTenantId.equals(order.getTenantId())) {
            log.warn("[package-suspend] cross-tenant blocked: orderId={} orderTenant={} currentTenant={}",
                    orderId, order.getTenantId(), currentTenantId);
            return Result.error(BizCode.TENANT_DENIED, "无权操作其它租户的套餐");
        }

        Integer beforeStatus = order.getStatus();
        // 已挂起不可重复挂起
        if (beforeStatus != null && beforeStatus == ORDER_SUSPENDED) {
            return Result.error(BizCode.ORDER_STATUS_INVALID, "该套餐已处于挂起状态，请先恢复挂起");
        }
        // 已冻结不可挂起（先解冻再挂起；保持状态机单向收敛）
        if (beforeStatus != null && beforeStatus == ORDER_FROZEN) {
            return Result.error(BizCode.ORDER_STATUS_INVALID, "该套餐已冻结，请先解冻再挂起");
        }
        // 仅正常可挂起
        if (beforeStatus == null || beforeStatus != ORDER_NORMAL) {
            return Result.error(BizCode.ORDER_STATUS_INVALID,
                    "仅「正常」状态的订单可挂起（当前状态：" + ordeStatusLabel(beforeStatus) + "）");
        }

        // ---------- 3. CAS 原子写 ----------
        Integer srcVersion = order.getVersion() == null ? 0 : order.getVersion();
        int rows = orderMapper.casSuspend(orderId, srcVersion);
        if (rows == 0) {
            return Result.error(BizCode.CONCURRENCY_CONFLICT, "挂起并发冲突，请刷新页面后重试");
        }

        // ---------- 4. 审计日志 ----------
        String operatorName = resolveOperatorName(operatorId, request);
        writeStatusLog(order, "SUSPEND", beforeStatus, ORDER_SUSPENDED,
                reason, suspendUntil, operatorId, operatorName, request);

        // ---------- 5. 同步更新 remark + 重读 ----------
        PackageOrder latest = orderService.getById(orderId);
        if (latest != null) {
            String suspendUntilText = suspendUntil == null ? "永久挂起"
                    : "挂起到 " + suspendUntil.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            latest.setStatusLabel(ordeStatusLabel(latest.getStatus()));
            latest.setRemark(appendRemark(latest.getRemark(),
                    "SUSPEND @" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            + " by " + (operatorName == null ? "system" : operatorName)
                            + " | " + suspendUntilText + " | " + reason));
            orderService.updateById(latest);
        }

        log.info("[package-suspend] orderId={} orderNo={} suspendUntil={} operator={}({}) reason={}",
                orderId, order.getOrderNo(), suspendUntil, operatorName, operatorId, reason);
        String msg = suspendUntil == null
                ? "已挂起该套餐（永久挂起，需人工恢复）"
                : "已挂起该套餐，到期时间：" + suspendUntil.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return Result.success(msg, latest);
    }

    // ==================== M04-04 套餐恢复挂起 ====================
    /**
     * M04-04 套餐恢复挂起（{@code status=8 → 1}）
     *
     * <p>权限：{@code package:suspend}（与挂起共用权限码）
     *
     * <p>调用样例：
     * <pre>{@code
     *   POST /packages/order/{orderId}/resume
     *   body: { "reason": "会员已回归，恢复使用", "operatorId": 1 }
     * }</pre>
     */
    @PostMapping("/order/{orderId}/resume")
    @RequirePermission("package:suspend")
    @Transactional(rollbackFor = Exception.class)
    public Result<PackageOrder> resume(
            @PathVariable Long orderId,
            @RequestBody(required = false) com.meiyetec.dto.PackageSuspendDTO dto,
            HttpServletRequest request) {
        Long currentTenantId = TenantContextHolder.get().getTenantId();

        String reason = null;
        Long operatorId = null;
        if (dto != null) {
            reason = dto.getReason();
            operatorId = dto.getOperatorId();
        }
        if (reason == null || reason.isBlank()) {
            reason = "人工恢复挂起";
        }

        PackageOrder order = orderService.getById(orderId);
        if (order == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");
        if (currentTenantId != null && order.getTenantId() != null
                && !currentTenantId.equals(order.getTenantId())) {
            log.warn("[package-resume] cross-tenant blocked: orderId={} orderTenant={} currentTenant={}",
                    orderId, order.getTenantId(), currentTenantId);
            return Result.error(BizCode.TENANT_DENIED, "无权操作其它租户的套餐");
        }

        Integer beforeStatus = order.getStatus();
        if (beforeStatus == null || beforeStatus != ORDER_SUSPENDED) {
            return Result.error(BizCode.ORDER_STATUS_INVALID,
                    "仅「已挂起」状态的订单可恢复（当前状态：" + ordeStatusLabel(beforeStatus) + "）");
        }

        Integer srcVersion = order.getVersion() == null ? 0 : order.getVersion();
        int rows = orderMapper.casResume(orderId, srcVersion);
        if (rows == 0) {
            return Result.error(BizCode.CONCURRENCY_CONFLICT, "恢复并发冲突，请刷新页面后重试");
        }

        String operatorName = resolveOperatorName(operatorId, request);
        writeStatusLog(order, "RESUME", beforeStatus, ORDER_NORMAL,
                reason, null, operatorId, operatorName, request);

        PackageOrder latest = orderService.getById(orderId);
        if (latest != null) {
            latest.setStatusLabel(ordeStatusLabel(latest.getStatus()));
            latest.setRemark(appendRemark(latest.getRemark(),
                    "RESUME @" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            + " by " + (operatorName == null ? "system" : operatorName)
                            + " | " + reason));
            orderService.updateById(latest);
        }

        log.info("[package-resume] orderId={} orderNo={} operator={}({}) reason={}",
                orderId, order.getOrderNo(), operatorName, operatorId, reason);
        return Result.success("已恢复挂起，套餐回到「正常」状态", latest);
    }

    // ==================== M04-04 套餐状态变更历史查询 ====================
    /**
     * 查询指定套餐订单的全部状态变更历史（按时间倒序）
     *
     * <p>返回字段含 action / beforeStatus / afterStatus / reason / operator / suspendUntil，
     * 便于前端展示「时间线」组件。
     */
    @GetMapping("/order/{orderId}/status-logs")
    @RequirePermission("package:list")
    public Result<List<com.meiyetec.entity.PackageStatusLog>> statusLogs(@PathVariable Long orderId) {
        Long currentTenantId = TenantContextHolder.get().getTenantId();
        PackageOrder order = orderService.getById(orderId);
        if (order == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");
        if (currentTenantId != null && order.getTenantId() != null
                && !currentTenantId.equals(order.getTenantId())) {
            return Result.error(BizCode.TENANT_DENIED, "无权查看其它租户的套餐");
        }
        List<com.meiyetec.entity.PackageStatusLog> logs = statusLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.meiyetec.entity.PackageStatusLog>()
                        .eq(com.meiyetec.entity.PackageStatusLog::getOrderId, orderId)
                        .orderByDesc(com.meiyetec.entity.PackageStatusLog::getId));
        logs.forEach(l -> l.setAfterStatusLabel(ordeStatusLabel(l.getAfterStatus())));
        return Result.success(logs);
    }

    // ==================== M04-04 辅助：写状态变更审计日志 ====================
    private void writeStatusLog(PackageOrder order, String action,
                                Integer beforeStatus, Integer afterStatus,
                                String reason, LocalDateTime suspendUntil,
                                Long operatorId, String operatorName,
                                HttpServletRequest request) {
        try {
            com.meiyetec.entity.PackageStatusLog log = new com.meiyetec.entity.PackageStatusLog();
            log.setTenantId(order.getTenantId());
            log.setOrderId(order.getId());
            log.setOrderNo(order.getOrderNo());
            log.setMemberId(order.getMemberId());
            log.setPackageId(order.getPackageId());
            log.setAction(action);
            log.setBeforeStatus(beforeStatus);
            log.setAfterStatus(afterStatus);
            log.setReason(reason);
            log.setSuspendUntil(suspendUntil);
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorName);
            if (request != null) {
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
                log.setClientIp(ip);
            }
            statusLogMapper.insert(log);
        } catch (Exception ex) {
            // 审计日志失败不应阻塞主业务（审计回滚主事务反而更糟）
            this.log.warn("[package-status-log] write failed but main biz kept: orderId={} action={} err={}",
                    order.getId(), action, ex.getMessage());
        }
    }

    /**
     * 解析操作人姓名（优先 DTO 显式传入，否则尝试从 TenantContext 拿用户ID映射）
     */
    private String resolveOperatorName(Long operatorId, HttpServletRequest request) {
        if (operatorId != null) {
            // 直接通过 ID 查 sys_user（这里简化用 Employee/共用 employeeService 兜底）
            try {
                com.meiyetec.entity.Employee emp = employeeService.getById(operatorId);
                if (emp != null && emp.getEmployeeName() != null) return emp.getEmployeeName();
            } catch (Exception ignore) {}
        }
        // 兜底：使用 ctx 中的用户ID
        try {
            com.meiyetec.security.TenantContext ctx = TenantContextHolder.get();
            if (ctx != null && ctx.getUserId() != null) {
                com.meiyetec.entity.Employee emp = employeeService.getById(ctx.getUserId());
                if (emp != null && emp.getEmployeeName() != null) return emp.getEmployeeName();
                return "user#" + ctx.getUserId();
            }
        } catch (Exception ignore) {}
        return null;
    }

    // ==================== 单笔订单核销记录 ====================
    @GetMapping("/order/{orderId}/redemptions")
    @RequirePermission("package:list")
    public Result<PageResult<PackageRedemption>> redemptions(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<PackageRedemption> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PackageRedemption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PackageRedemption::getOrderId, orderId);
        wrapper.orderByDesc(PackageRedemption::getId);
        Page<PackageRedemption> result = redeemService.page(page, wrapper);
        Map<Long, String> beauticianMap = employeeService.list().stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getEmployeeName, (a, b) -> a));
        result.getRecords().forEach(r -> {
            if (r.getMemberId() != null) {
                Member m = memberService.getById(r.getMemberId());
                if (m != null) r.setMemberName(m.getName());
            }
            if (r.getPackageId() != null) {
                PackageItem p = packageItemService.getById(r.getPackageId());
                if (p != null) r.setPackageName(p.getName());
            }
            if (r.getBeauticianId() != null) {
                r.setBeauticianName(beauticianMap.get(r.getBeauticianId()));
            }
        });
        return Result.success(PageResult.of(result));
    }

    // ==================== 会员全部核销记录 ====================
    @GetMapping("/redemption/member/{memberId}")
    @RequirePermission("package:list")
    public Result<PageResult<PackageRedemption>> memberRedemptions(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Page<PackageRedemption> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PackageRedemption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PackageRedemption::getMemberId, memberId);
        wrapper.orderByDesc(PackageRedemption::getId);
        Page<PackageRedemption> result = redeemService.page(page, wrapper);
        Map<Long, String> beauticianMap = employeeService.list().stream()
                .filter(e -> e.getEmployeeName() != null)
                .collect(Collectors.toMap(Employee::getId, Employee::getEmployeeName, (a, b) -> a));
        result.getRecords().forEach(r -> {
            if (r.getMemberId() != null) {
                Member m = memberService.getById(r.getMemberId());
                if (m != null) r.setMemberName(m.getName());
            }
            if (r.getPackageId() != null) {
                PackageItem p = packageItemService.getById(r.getPackageId());
                if (p != null) r.setPackageName(p.getName());
            }
            if (r.getBeauticianId() != null) {
                r.setBeauticianName(beauticianMap.get(r.getBeauticianId()));
            }
        });
        return Result.success(PageResult.of(result));
    }

    // ==================== 续期（顺延 N 天） ====================
    @PostMapping("/order/{orderId}/extend")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:edit")
    public Result<PackageOrder> extend(@PathVariable Long orderId,
                                       @RequestParam(defaultValue = "30") Integer days) {
        PackageOrder order = orderService.getById(orderId);
        if (order == null) return Result.error("套餐订单不存在");
        if (order.getStatus() == null || order.getStatus() != ORDER_NORMAL) {
            return Result.error("仅「正常」状态的订单可续期");
        }
        LocalDateTime base = order.getExpireTime() != null && order.getExpireTime().isAfter(LocalDateTime.now())
                ? order.getExpireTime()
                : LocalDateTime.now();
        LocalDateTime newExpire = base.plusDays(days == null ? 30 : days);
        order.setExpireTime(newExpire);
        orderService.updateById(order);
        order.setStatusLabel(ordeStatusLabel(order.getStatus()));
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("newExpireTime", newExpire);
        return Result.success("已顺延 " + days + " 天，新到期时间：" + newExpire, order);
    }

    // ==================== 套餐列表汇总统计 ====================
    @GetMapping("/stats")
    @RequirePermission("package:list")
    public Result<Map<String, Object>> stats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PackageItem> itemWrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) itemWrapper.eq(PackageItem::getTenantId, tenantId);
        List<PackageItem> all = packageItemService.list(itemWrapper);
        long listed = all.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();
        long unlisted = all.stream().filter(p -> p.getStatus() != null && p.getStatus() == 0).count();
        int totalSale = all.stream().filter(p -> p.getSaleCount() != null).mapToInt(PackageItem::getSaleCount).sum();
        int totalRedeem = all.stream().filter(p -> p.getRedeemCount() != null).mapToInt(PackageItem::getRedeemCount).sum();

        LambdaQueryWrapper<PackageOrder> orderWrapper = new LambdaQueryWrapper<PackageOrder>()
                .in(PackageOrder::getStatus, ORDER_NORMAL, ORDER_FROZEN);
        if (tenantId != null) orderWrapper.eq(PackageOrder::getTenantId, tenantId);
        List<PackageOrder> orders = orderService.list(orderWrapper);
        long activeOrders = orders.size();
        int totalRemaining = orders.stream().filter(o -> o.getRemainingTimes() != null)
                .mapToInt(PackageOrder::getRemainingTimes).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("listed", listed);
        result.put("unlisted", unlisted);
        result.put("totalSale", totalSale);
        result.put("totalRedeem", totalRedeem);
        result.put("activeOrders", activeOrders);
        result.put("totalRemaining", totalRemaining);
        return Result.success(result);
    }

    // ==================== M05-二期 #6 套餐退款（按剩余次数比例折现退钱包） ====================
    @PostMapping("/order/{orderId}/refund")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:refund")
    public Result<Map<String, Object>> refund(@PathVariable Long orderId,
                                              @RequestParam(required = false) BigDecimal amount,
                                              @RequestParam(defaultValue = "1") Long operatorId) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        PackageOrder order = orderService.getById(orderId);
        if (order == null) return Result.error("套餐订单不存在");
        if (order.getStatus() == null || order.getStatus() != ORDER_NORMAL) {
            return Result.error("仅「正常」状态的订单可退款（当前状态：" + ordeStatusLabel(order.getStatus()) + "）");
        }
        int remaining = order.getRemainingTimes() == null ? 0 : order.getRemainingTimes();
        if (remaining <= 0) {
            return Result.error("剩余次数为 0，无需退款");
        }
        // 幂等：该订单只能退一次
        Long dup = walletTxnService.count(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getOrderId, orderId)
                        .eq(WalletTransaction::getType, 3));
        if (dup != null && dup > 0) {
            return Result.error("该套餐订单已退款，不能重复操作");
        }
        // 计算折算金额：price * (remaining / total)，默认退到钱包
        BigDecimal price = order.getPrice() == null ? BigDecimal.ZERO : order.getPrice();
        BigDecimal refund;
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            refund = amount;
        } else {
            refund = price.multiply(BigDecimal.valueOf(remaining))
                    .divide(BigDecimal.valueOf(order.getTotalTimes()), 2, RoundingMode.HALF_UP);
        }
        if (refund.compareTo(price) > 0) refund = price;  // 防止超过原价

        // 钱包加钱
        Wallet wallet = walletService.getOne(
                new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, order.getMemberId()));
        if (wallet == null) {
            Wallet w = new Wallet();
            w.setMemberId(order.getMemberId());
            w.setBalance(BigDecimal.ZERO);
            w.setTotalRecharge(BigDecimal.ZERO);
            w.setTotalConsume(BigDecimal.ZERO);
            w.setFrozenAmount(BigDecimal.ZERO);
            walletService.save(w);
            wallet = w;
        }
        BigDecimal before = wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
        wallet.setBalance(before.add(refund));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletService.updateById(wallet);

        // 同步 crm_member.balance
        Member member = memberService.getById(order.getMemberId());
        if (member != null) {
            member.setBalance(before.add(refund));
            memberService.updateById(member);
        }

        // 写钱包流水
        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(order.getMemberId());
        txn.setType(3);  // 退款
        txn.setAmount(refund);
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(before.add(refund));
        txn.setPayMethod("原路返回");
        txn.setOrderId(orderId);
        txn.setOperatorId(operatorId);
        txn.setRemark("套餐退款：剩余 " + remaining + "/" + order.getTotalTimes() + " 次，自动折算 ¥" + refund);
        walletTxnService.save(txn);

        // 套餐订单标为已退款（保留流水便于审计）
        order.setStatus(ORDER_REFUNDED);
        order.setRemainingTimes(0);
        orderService.updateById(order);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("orderNo", order.getOrderNo());
        data.put("refundAmount", refund);
        data.put("refundedRemaining", remaining);
        data.put("balanceBefore", before);
        data.put("balanceAfter", before.add(refund));
        data.put("txnId", txn.getId());
        return Result.success("退款 ¥" + refund + " 已退至会员钱包", data);
    }

    // ==================== M05-二期 #7 套餐转赠（剩余次数一并过户） ====================
    /**
     * M04 · 套餐转让完整权益校验链路（2026-09-06 修复）
     *
     * <p>按顺序执行 11 项校验，任一不通过立即中断并返回明确错误码：
     * <ol>
     *   <li>{@code orderId} 存在性</li>
     *   <li>{@code src.tenantId} 与当前租户一致（防跨租户）</li>
     *   <li>{@code src.status == ORDER_NORMAL}（非已用完/已过期/已冻结/已退款/已转赠）</li>
     *   <li>{@code src.remainingTimes > 0}（剩余次数 > 0）</li>
     *   <li>{@code src.expireTime} 未过期（已过期则先标 EXPIRED 后报错）</li>
     *   <li>{@code dto.toMemberId} 非空</li>
     *   <li>{@code dto.toMemberId != src.memberId}（不能转给自己）</li>
     *   <li>目标会员存在 + 状态正常（非冻结） + 同租户</li>
     *   <li>源会员存在 + 状态正常（非冻结）</li>
     *   <li>CAS 原子写：{@link PackageOrderMapper#casTransfer}（version + status=NORMAL + remaining>0 三重守卫）</li>
     *   <li>克隆新订单给目标会员（含 expireTime 继承 / fallback 30 天）</li>
     * </ol>
     *
     * <p>权限：{@code package:transfer}（RBAC）。审计：{@code [package-transfer]} 业务日志（含 from/to/operator/原单号/新单号）。
     */
    @PostMapping("/order/{orderId}/transfer")
    @RequirePermission("package:transfer")
    @Transactional(rollbackFor = Exception.class)
    public Result<PackageOrder> transfer(@PathVariable Long orderId,
                                         @Valid @RequestBody PackageTransferDTO dto) {
        Long currentTenantId = TenantContextHolder.get().getTenantId();

        // ---------- 1. 源订单存在性 ----------
        PackageOrder src = orderService.getById(orderId);
        if (src == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");

        // ---------- 2. 跨租户拦截 ----------
        if (currentTenantId != null && src.getTenantId() != null
                && !currentTenantId.equals(src.getTenantId())) {
            log.warn("[package-transfer] cross-tenant blocked: orderId={} orderTenant={} currentTenant={}",
                    orderId, src.getTenantId(), currentTenantId);
            return Result.error(BizCode.TENANT_DENIED, "无权操作其它租户的套餐");
        }

        // ---------- 3. 状态合法性（仅「正常」可转赠） ----------
        if (src.getStatus() == null || src.getStatus() != ORDER_NORMAL) {
            return Result.error(BizCode.ORDER_STATUS_INVALID,
                    "仅「正常」状态的订单可转赠（当前状态：" + ordeStatusLabel(src.getStatus()) + "）");
        }

        // ---------- 4. 剩余次数 > 0 ----------
        if (src.getRemainingTimes() == null || src.getRemainingTimes() <= 0) {
            return Result.error(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "剩余次数为 0，无法转赠");
        }

        // ---------- 5. 过期校验（已过期先标 EXPIRED 后报错） ----------
        if (src.getExpireTime() != null && src.getExpireTime().isBefore(LocalDateTime.now())) {
            src.setStatus(ORDER_EXPIRED);
            orderService.updateById(src);
            return Result.error(BizCode.PACKAGE_EXPIRED, "该套餐已过期，无法转赠");
        }

        // ---------- 6. 目标会员 ID 非空 ----------
        if (dto.getToMemberId() == null) return Result.error(BizCode.BAD_REQUEST, "目标会员ID不能为空");

        // ---------- 7. 不能转给自己 ----------
        if (dto.getToMemberId().equals(src.getMemberId())) {
            return Result.error(BizCode.BAD_REQUEST, "不能转赠给自己");
        }

        // ---------- 8. 目标会员：存在 + 状态正常 + 同租户 ----------
        Member toMember = memberService.getById(dto.getToMemberId());
        if (toMember == null) return Result.error(BizCode.MEMBER_NOT_FOUND, "目标会员不存在");
        if (toMember.getStatus() != null && toMember.getStatus() != 1) {
            return Result.error(BizCode.MEMBER_FROZEN, "目标会员已冻结，不能接收转赠");
        }
        if (currentTenantId != null && toMember.getTenantId() != null
                && !currentTenantId.equals(toMember.getTenantId())) {
            log.warn("[package-transfer] cross-tenant to-member blocked: toMemberId={} memberTenant={} currentTenant={}",
                    dto.getToMemberId(), toMember.getTenantId(), currentTenantId);
            return Result.error(BizCode.TENANT_DENIED, "目标会员不在当前租户");
        }

        // ---------- 9. 源会员：存在 + 状态正常 ----------
        Member fromMember = src.getMemberId() == null ? null : memberService.getById(src.getMemberId());
        if (src.getMemberId() != null && fromMember == null) {
            return Result.error(BizCode.MEMBER_NOT_FOUND, "源订单关联的会员已不存在");
        }
        if (fromMember != null && fromMember.getStatus() != null && fromMember.getStatus() != 1) {
            return Result.error(BizCode.MEMBER_FROZEN, "源会员已冻结，不能转赠其套餐");
        }

        Integer srcVersion = src.getVersion() == null ? 0 : src.getVersion();
        Integer transferredTimes = src.getRemainingTimes();

        // ---------- 10. CAS 原子转赠（version + status=NORMAL + remaining>0 三重守卫） ----------
        int rows = orderMapper.casTransfer(orderId, srcVersion);
        if (rows == 0) {
            // CAS 失败：重读源订单给出明确错误码
            PackageOrder now = orderService.getById(orderId);
            if (now == null) return Result.error(BizCode.NOT_FOUND, "套餐订单不存在");
            if (now.getStatus() != null && now.getStatus() == ORDER_TRANSFER) {
                return Result.error(BizCode.ORDER_STATUS_INVALID, "该套餐已被转赠，请刷新页面");
            }
            if (now.getRemainingTimes() == null || now.getRemainingTimes() <= 0) {
                return Result.error(BizCode.PACKAGE_TIMES_NOT_ENOUGH, "该套餐剩余次数已为 0，无法转赠");
            }
            return Result.error(BizCode.CONCURRENCY_CONFLICT, "套餐转赠并发冲突，请重试");
        }

        // CAS 成功：写业务审计日志（必须在写新订单前，便于溯源）
        log.info("[package-transfer] src orderId={} orderNo={} remaining={} fromMember={}({}) → toMember={}({}) operator={}",
                src.getId(), src.getOrderNo(), transferredTimes,
                src.getMemberId(), fromMember == null ? "?" : fromMember.getName(),
                toMember.getId(), toMember.getName(),
                dto.getOperatorId());

        // ---------- 11. 克隆新订单给目标会员 ----------
        PackageOrder neo = new PackageOrder();
        neo.setTenantId(src.getTenantId());
        neo.setOrderNo(genOrderNo());
        neo.setMemberId(toMember.getId());
        neo.setStoreId(TenantContextHolder.get().getStoreId());  // R3.0-B 补丁：转赠落当前门店
        neo.setPackageId(src.getPackageId());
        neo.setPackageName(src.getPackageName());
        neo.setTotalTimes(transferredTimes);       // 总额度等于"剩余"
        neo.setRemainingTimes(transferredTimes);    // 剩余 = 总额度
        neo.setPrice(BigDecimal.ZERO);              // 转赠金额为 0
        neo.setPayMethod("转赠");
        neo.setOperatorId(dto.getOperatorId());
        neo.setBuyTime(LocalDateTime.now());
        // 到期时间继承原订单，未过期按原 expireTime；已过期 fallback 30 天
        LocalDateTime expire = src.getExpireTime() != null && src.getExpireTime().isAfter(LocalDateTime.now())
                ? src.getExpireTime()
                : LocalDateTime.now().plusDays(30);
        neo.setExpireTime(expire);
        neo.setStatus(ORDER_NORMAL);
        neo.setRemark("来自【" + (fromMember == null ? "?" : fromMember.getName()) + "】转赠，原订单号 " + src.getOrderNo());
        orderService.save(neo);

        // 源订单的 remark 也补一笔（CAS 已改 status=6/remaining=0，这里只追加备注）
        // 重新读源订单防止 stale instance（CAS 后 version 已 +1）
        PackageOrder srcLatest = orderService.getById(orderId);
        if (srcLatest != null) {
            srcLatest.setRemark(appendRemark(srcLatest.getRemark(),
                    "转赠给【" + toMember.getName() + "】@" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                            "，新订单号 " + neo.getOrderNo()));
            orderService.updateById(srcLatest);
        }

        neo.setStatusLabel(ordeStatusLabel(neo.getStatus()));
        log.info("[package-transfer] success newOrderId={} newOrderNo={} remaining={}",
                neo.getId(), neo.getOrderNo(), neo.getRemainingTimes());
        return Result.success("已转赠「" + neo.getPackageName() + "」" + neo.getRemainingTimes() + " 次给【" + toMember.getName() + "】，新订单号 " + neo.getOrderNo(), neo);
    }

    // ==================== M04-07 套餐差价补款/升级 ====================
    /**
     * 套餐差价补款/升级
     *
     * <p>计算逻辑：
     * <pre>
     * 剩余价值 = 原套餐原价 × (剩余次数 / 总次数)
     * 差价     = 新套餐价格 - 剩余价值
     * - 差价 > 0：会员补缴差价（至钱包或第三方）
     * - 差价 <= 0：降级/同档，退款至钱包（|差价|）
     * </pre>
     *
     * <p>原套餐：状态变为 UPGRADED(7)
     * <p>新套餐：新建订单，次数 = 新套餐总次数，到期日从购买日重新计算
     */
    @PostMapping("/upgrade")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:edit")
    public Result<Map<String, Object>> upgrade(@Valid @RequestBody PackageUpgradeDTO dto) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }

        // 1. 校验原套餐
        PackageOrder src = orderService.getById(dto.getFromOrderId());
        if (src == null) return Result.error("原套餐订单不存在");
        if (src.getStatus() == null || src.getStatus() != ORDER_NORMAL) {
            return Result.error("仅「正常」状态的套餐可升级（当前状态：" + ordeStatusLabel(src.getStatus()) + "）");
        }
        if (src.getRemainingTimes() == null || src.getRemainingTimes() <= 0) {
            return Result.error("剩余次数为 0，无法升级");
        }

        // 2. 校验目标套餐
        PackageItem toPkg = packageItemService.getById(dto.getToPackageId());
        if (toPkg == null) return Result.error("目标套餐不存在");
        if (toPkg.getStatus() != null && toPkg.getStatus() != 1) {
            return Result.error("目标套餐已下架");
        }
        if (tenantId != null && toPkg.getTenantId() != null && !tenantId.equals(toPkg.getTenantId())) {
            throw new BusinessException(BizCode.TENANT_DENIED, "无权使用该套餐（跨租户）");
        }

        // 3. 服务端独立计算差价（不依赖前端估算值）
        BigDecimal srcPrice = src.getPrice() == null ? BigDecimal.ZERO : src.getPrice();
        int srcTotal = src.getTotalTimes() == null ? 1 : src.getTotalTimes();
        int srcRemaining = src.getRemainingTimes() == null ? 0 : src.getRemainingTimes();
        BigDecimal creditValue = srcPrice
                .multiply(BigDecimal.valueOf(srcRemaining))
                .divide(BigDecimal.valueOf(srcTotal), 2, RoundingMode.HALF_UP);

        BigDecimal newPrice = toPkg.getPrice() == null ? BigDecimal.ZERO : toPkg.getPrice();
        BigDecimal diff = newPrice.subtract(creditValue); // 正=补缴，负=退款

        // 4. 处理支付/退款
        BigDecimal actualPaid = BigDecimal.ZERO; // 实际额外支出
        BigDecimal actualRefund = BigDecimal.ZERO; // 实际退款

        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            // 4a. 补差价：扣钱包或第三方
            String payMethod = dto.getPayMethod() == null ? "wallet" : dto.getPayMethod();
            if ("wallet".equals(payMethod)) {
                if (src.getMemberId() == null) return Result.error("非会员无法使用储值支付差价");
                BigDecimal balance = jdbc.queryForObject(
                        "SELECT balance FROM crm_wallet WHERE member_id = ? AND deleted = 0",
                        BigDecimal.class, src.getMemberId());
                if (balance == null || balance.compareTo(diff) < 0) {
                    return Result.error("余额不足（需要 ¥" + diff + "，当前 ¥" + (balance == null ? "0" : balance) + "）");
                }
                walletService.doConsume(buildWalletOp(
                        src.getMemberId(), diff, src.getId(), src.getOperatorId(),
                        "wallet", "套餐升级补差价:" + src.getOrderNo() + "→" + toPkg.getName()), tenantId);
                actualPaid = diff;
            } else if ("cash".equals(payMethod) || "wechat".equals(payMethod)
                    || "alipay".equals(payMethod)) {
                // 第三方：仅记录，不做实际扣款（待接入真实通道）
                actualPaid = diff;
            }
        } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
            // 4b. 降级：退差价至钱包
            BigDecimal refundAmt = diff.abs();
            if (dto.getRefundToWallet() == null || dto.getRefundToWallet()) {
                walletService.doRecharge(buildWalletRecharge(
                        src.getMemberId(), refundAmt, src.getId(), src.getOperatorId(),
                        "套餐降级退款:" + src.getPackageName() + "→" + toPkg.getName()), tenantId);
            }
            actualRefund = refundAmt;
        }

        // 5. 原套餐标记为升级
        src.setStatus(7); // UPGRADED
        src.setRemainingTimes(0);
        src.setRemark(appendRemark(src.getRemark(),
                "升级为【" + toPkg.getName() + "】@" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                "，折算价值 ¥" + creditValue + "，差价 ¥" + diff));
        orderService.updateById(src);

        // 6. 创建新套餐订单
        PackageOrder neo = new PackageOrder();
        neo.setTenantId(src.getTenantId());
        neo.setOrderNo(genOrderNo());
        neo.setMemberId(src.getMemberId());
        neo.setStoreId(TenantContextHolder.get().getStoreId());
        neo.setPackageId(toPkg.getId());
        neo.setPackageName(toPkg.getName());
        neo.setTotalTimes(toPkg.getTotalTimes());
        neo.setRemainingTimes(toPkg.getTotalTimes());
        neo.setPrice(newPrice);
        neo.setPayMethod(dto.getPayMethod() == null ? "差价补款" : dto.getPayMethod());
        neo.setOperatorId(dto.getOperatorId());
        neo.setBuyTime(LocalDateTime.now());
        int validDays = toPkg.getValidDays() == null ? 365 : toPkg.getValidDays();
        neo.setExpireTime(LocalDateTime.now().plusDays(validDays));
        neo.setStatus(ORDER_NORMAL);
        neo.setRemark("从【" + src.getPackageName() + "】升级，原订单号 " + src.getOrderNo() +
                "，补差价 ¥" + (diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO) +
                "，退款 ¥" + (diff.compareTo(BigDecimal.ZERO) < 0 ? diff.abs() : BigDecimal.ZERO));
        orderService.save(neo);

        // 7. 套餐累计
        toPkg.setSaleCount(toPkg.getSaleCount() == null ? 1 : toPkg.getSaleCount() + 1);
        packageItemService.updateById(toPkg);

        neo.setStatusLabel(ordeStatusLabel(neo.getStatus()));
        Map<String, Object> data = new HashMap<>();
        data.put("newOrder", neo);
        data.put("creditValue", creditValue);
        data.put("newPrice", newPrice);
        data.put("diff", diff);
        data.put("actualPaid", actualPaid);
        data.put("actualRefund", actualRefund);
        data.put("message", diff.compareTo(BigDecimal.ZERO) > 0
                ? "升级成功，需补差价 ¥" + diff
                : diff.compareTo(BigDecimal.ZERO) < 0
                        ? "降级成功，已退 ¥" + diff.abs() + " 至钱包"
                        : "平价换购成功，无需补退");
        return Result.success("套餐升级完成", data);
    }

    private com.meiyetec.dto.WalletOpDTO buildWalletOp(Long memberId, BigDecimal amount,
            Long orderId, Long cashierId, String payMethod, String remark) {
        com.meiyetec.dto.WalletOpDTO dto = new com.meiyetec.dto.WalletOpDTO();
        dto.setMemberId(memberId);
        dto.setAmount(amount);
        dto.setOrderId(orderId);
        dto.setOperatorId(cashierId);
        dto.setPayMethod(payMethod);
        dto.setRemark(remark);
        return dto;
    }

    private com.meiyetec.dto.WalletOpDTO buildWalletRecharge(Long memberId, BigDecimal amount,
            Long orderId, Long cashierId, String remark) {
        com.meiyetec.dto.WalletOpDTO dto = new com.meiyetec.dto.WalletOpDTO();
        dto.setMemberId(memberId);
        dto.setAmount(amount);
        dto.setOrderId(orderId);
        dto.setOperatorId(cashierId);
        dto.setPayMethod("recharge");
        dto.setRemark(remark);
        return dto;
    }

    // ==================== M05-二期 #8 套餐拆分核销（一次操作多员工次） ====================
    @PostMapping("/redeem-split")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:redeem")
    public Result<Map<String, Object>> redeemSplit(@Valid @RequestBody PackageSplitRedeemDTO dto) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            return Result.error("拆分子项不能为空");
        }
        PackageOrder order = orderService.getById(dto.getOrderId());
        if (order == null) return Result.error("套餐订单不存在");
        if (order.getStatus() == null || order.getStatus() != ORDER_NORMAL) {
            return Result.error("仅「正常」状态的订单可核销");
        }

        // R3.0-B 补丁：跨店核销拦截
        CrossStoreController.CrossStoreCheckResult csr = crossStoreController.checkCrossStoreAndQuote(
                order.getMemberId(), order.getStoreId(), "PACKAGE",
                order.getPrice() == null ? BigDecimal.ZERO : order.getPrice());
        if (!csr.allowed) {
            return Result.error("跨店核销被拒绝：" + csr.message);
        }

        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            order.setStatus(ORDER_EXPIRED);
            orderService.updateById(order);
            return Result.error("该套餐已过期");
        }

        int total = dto.getItems().stream()
                .mapToInt(i -> i.getTimes() == null ? 1 : i.getTimes()).sum();
        if (total <= 0) return Result.error("拆分总次数必须大于 0");
        if (order.getRemainingTimes() == null || order.getRemainingTimes() < total) {
            return Result.error("剩余次数不足，需要 " + total + " 次，剩余 " + order.getRemainingTimes());
        }

        // 美容师姓名映射
        Map<Long, String> beauticianMap = employeeService.list().stream()
                .filter(e -> e.getEmployeeName() != null)
                .collect(Collectors.toMap(Employee::getId, Employee::getEmployeeName, (a, b) -> a));

        List<Map<String, Object>> created = new java.util.ArrayList<>();
        for (PackageSplitRedeemDTO.SplitItem it : dto.getItems()) {
            int t = it.getTimes() == null ? 1 : it.getTimes();
            PackageRedemption r = new PackageRedemption();
            r.setTenantId(tenantId);
            r.setOrderId(order.getId());
            r.setStoreId(TenantContextHolder.get().getStoreId());  // R3.0-B 补丁
            r.setMemberId(order.getMemberId());
            r.setPackageId(order.getPackageId());
            r.setTimes(t);
            r.setCrossFee(csr.crossFee);                        // R3.0-B 补丁
            r.setCrossRuleId(csr.isCross ? csr.ruleId : null);  // R3.0-B 补丁
            r.setServiceItem(it.getServiceItem());
            r.setBeauticianId(it.getBeauticianId());
            r.setNote(it.getNote() == null ? dto.getNote() : it.getNote());
            r.setOperatorId(dto.getOperatorId());
            r.setRedeemTime(LocalDateTime.now());
            r.setStatus(1);
            redeemService.save(r);

            Map<String, Object> row = new HashMap<>();
            row.put("id", r.getId());
            row.put("times", t);
            row.put("serviceItem", r.getServiceItem());
            row.put("beauticianId", r.getBeauticianId());
            row.put("beauticianName", beauticianMap.get(r.getBeauticianId()));
            row.put("note", r.getNote());
            created.add(row);
        }

        // 一次扣总次数
        order.setRemainingTimes(order.getRemainingTimes() - total);
        if (order.getRemainingTimes() <= 0) order.setStatus(ORDER_USED);
        orderService.updateById(order);

        // 套餐主表累计核销
        PackageItem p = packageItemService.getById(order.getPackageId());
        if (p != null) {
            p.setRedeemCount((p.getRedeemCount() == null ? 0 : p.getRedeemCount()) + total);
            packageItemService.updateById(p);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("totalRedeemed", total);
        data.put("remainingTimes", order.getRemainingTimes());
        data.put("orderStatus", order.getStatus());
        data.put("items", created);
        return Result.success("已拆分核销 " + created.size() + " 项，共 " + total + " 次", data);
    }

    // ==================== 逻辑删除套餐 ====================
    @DeleteMapping("/{id:\\d+}")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("package:delete")
    public Result<Void> delete(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PackageItem p = packageItemService.getById(id);
        if (p == null) return Result.error("套餐不存在");
        if (tenantId != null && p.getTenantId() != null && !p.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权删除该套餐");

        // 业务保护：若该套餐还有「正常」或「已冻结」的会员订单，禁止删除，避免历史核销追溯断裂
        long activeOrders = orderService.count(
                new LambdaQueryWrapper<PackageOrder>()
                        .eq(PackageOrder::getPackageId, id)
                        .in(PackageOrder::getStatus, ORDER_NORMAL, ORDER_FROZEN));
        if (activeOrders > 0) {
            return Result.error("该套餐仍有 " + activeOrders + " 笔有效会员订单未清账，不能删除（请先将订单处理完）");
        }

        packageItemService.removeById(id);   // MyBatis-Plus 逻辑删除：deleted=1
        return Result.success("套餐已删除", null);
    }

    // ==================== 内部辅助 ====================
    private String genOrderNo() {
        return "PKG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String statusLabel(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 1 -> "上架";
            case 0 -> "下架";
            default -> "其他";
        };
    }

    private String ordeStatusLabel(Integer status) {
        if (status == null) return "";
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "已用完";
            case 3 -> "已过期";
            case 4 -> "已冻结";
            case 5 -> "已退款";
            case 6 -> "已转赠";
            case 7 -> "已升级";
            case 8 -> "已挂起";
            default -> "其他";
        };
    }

    private String appendRemark(String original, String add) {
        if (original == null || original.isBlank()) return add;
        return original + " || " + add;
    }

    // ==================== M05-附 最近订单/核销 ====================
    @GetMapping("/orders/recent")
    @RequirePermission("package:list")
    public Result<PageResult<PackageOrder>> recentOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<PackageOrder> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PackageOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PackageOrder::getId);
        Page<PackageOrder> result = orderService.page(page, wrapper);
        result.getRecords().forEach(o -> {
            o.setStatusLabel(ordeStatusLabel(o.getStatus()));
            if (o.getMemberId() != null) {
                Member m = memberService.getById(o.getMemberId());
                if (m != null) { o.setMemberName(m.getName()); o.setMemberPhone(m.getPhone()); }
            }
        });
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/redemptions/recent")
    @RequirePermission("package:list")
    public Result<PageResult<PackageRedemption>> recentRedemptions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<PackageRedemption> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PackageRedemption> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PackageRedemption::getId);
        Page<PackageRedemption> result = redeemService.page(page, wrapper);
        result.getRecords().forEach(r -> {
            PackageOrder o = r.getOrderId() != null ? orderService.getById(r.getOrderId()) : null;
            if (o != null) {
                r.setPackageName(o.getPackageName());
                if (o.getMemberId() != null) {
                    Member m = memberService.getById(o.getMemberId());
                    if (m != null) r.setMemberName(m.getName());
                }
            }
        });
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/members/recent-with-orders")
    @RequirePermission("package:list")
    public Result<List<Map<String, Object>>> recentMembersWithOrders(
            @RequestParam(defaultValue = "8") Integer limit) {
        // 取最近有订单的会员
        LambdaQueryWrapper<PackageOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(PackageOrder::getMemberId, PackageOrder::getId, PackageOrder::getBuyTime);
        wrapper.isNotNull(PackageOrder::getMemberId);
        wrapper.orderByDesc(PackageOrder::getId);
        Page<PackageOrder> page = orderService.page(new Page<>(1, 200), wrapper);
        LinkedHashMap<Long, Map<String, Object>> uniq = new LinkedHashMap<>();
        for (PackageOrder o : page.getRecords()) {
            if (uniq.containsKey(o.getMemberId())) continue;
            Member m = memberService.getById(o.getMemberId());
            if (m == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("name", m.getName());
            row.put("phone", m.getPhone());
            row.put("memberNo", m.getMemberNo());
            row.put("lastOrderId", o.getId());
            row.put("lastBuyTime", o.getBuyTime());
            uniq.put(m.getId(), row);
            if (uniq.size() >= limit) break;
        }
        return Result.success(new ArrayList<>(uniq.values()));
    }
}


