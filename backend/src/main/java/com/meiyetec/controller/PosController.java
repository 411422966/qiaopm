package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.dto.PageQueryDTO;
import com.meiyetec.dto.PosOrderCreateDTO;
import com.meiyetec.dto.PosPayDTO;
import com.meiyetec.entity.*;
import com.meiyetec.event.OrderPaidEvent;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BizServiceMapper;
import com.meiyetec.mapper.MktPointsRuleMapper;
import com.meiyetec.mapper.PosOrderItemMapper;
import com.meiyetec.mapper.PosOrderMapper;
import com.meiyetec.mapper.PosPaymentMapper;
import com.meiyetec.service.CommissionCalculationService;
import com.meiyetec.service.IdempotencyService;
import com.meiyetec.service.InventoryConsumptionService;
import com.meiyetec.service.MemberService;
import com.meiyetec.service.PackageRedeemService;
import com.meiyetec.service.PointService;
import com.meiyetec.service.PricingExperimentService;
import com.meiyetec.service.ServiceMaterialDeductService;
import com.meiyetec.service.WalletService;
import com.meiyetec.service.WalletTransactionService;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.IdGenerator;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M07 收银结算 Controller
 * 端点：/pos/orders, /pos/services
 */
@Slf4j
@RestController
@RequestMapping("/pos")
@RequiredArgsConstructor
@SysOpLog(module = "收银")
public class PosController {

    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper itemMapper;
    private final PosPaymentMapper paymentMapper;
    private final BizServiceMapper serviceMapper;
    private final MemberService memberService;
    private final WalletTransactionService txnService;
    private final WalletService walletService;
    private final CommissionCalculationService commissionCalculationService;
    private final com.meiyetec.util.IdGenerator idGenerator;
    private final InventoryConsumptionService inventoryConsumptionService;
    private final JwtUtil jwtUtil;
    /** B-2 · 套餐核销幂等 + CAS */
    private final PackageRedeemService packageRedeemService;
    /** B-2 · 套餐支付幂等键生成 */
    private final IdempotencyService idem;
    /** P0-M06 · 耗材自动扣减：订单支付成功后触发 */
    private final ServiceMaterialDeductService serviceMaterialDeductService;
    /** M09 · 积分抵扣：积分商城 */
    private final PointService pointService;
    private final MktPointsRuleMapper pointsRuleMapper;
    /** M14 · 定价 A/B 实验：POS 收银时按会员分组应用折扣 */
    private final PricingExperimentService pricingExperimentService;
    /** M16 · 营销事件发布：订单/消费事件发布到旅程引擎 */
    private final com.meiyetec.mkt.event.MarketingEventPublisher marketingEventPublisher;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== Service endpoints ====================

    @GetMapping("/services/list")
    public Result<List<BizService>> serviceList(@RequestParam(required = false) Long storeId,
                                                @RequestParam(required = false) Long categoryId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<BizService> w = new LambdaQueryWrapper<BizService>()
                .eq(BizService::getStatus, 1)
                .orderByAsc(BizService::getSortOrder);
        if (tenantId != null) w.eq(BizService::getTenantId, tenantId);
        if (storeId != null) w.eq(BizService::getStoreId, storeId);
        if (categoryId != null) w.eq(BizService::getCategoryId, categoryId);
        return Result.success(serviceMapper.selectList(w));
    }

    @GetMapping("/services/{id:\\d+}")
    public Result<BizService> serviceDetail(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BizService svc = serviceMapper.selectById(id);
        if (svc == null) return Result.error("服务不存在");
        if (tenantId != null && svc.getTenantId() != null && !svc.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该服务");
        return Result.success(svc);
    }

    // ==================== Order endpoints ====================

    @PostMapping("/orders")
    public Result<PosOrder> createOrder(@RequestBody PosOrderCreateDTO dto,
                                         HttpServletRequest request) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        // 验证会员
        Member member = memberService.getById(dto.getMemberId());
        if (member == null) return Result.error("会员不存在");

        // 创建订单
        PosOrder order = new PosOrder();
        order.setTenantId(tenantId);
        order.setOrderNo(generateOrderNo());
        order.setStoreId(dto.getStoreId() == null ? 1L : dto.getStoreId());
        order.setMemberId(member.getId());
        order.setMemberName(member.getName());
        order.setMemberPhone(member.getPhone());
        order.setBookingId(dto.getBookingId());
        order.setType(1);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(BigDecimal.ZERO);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setStatus(1); // 进行中

        // 设置收银员（从 token）
        try {
            String token = request.getHeader("Authorization").replace("Bearer ", "");
            Long cashierId = jwtUtil.extractUserId(token);
            order.setCashierId(cashierId);
        } catch (Exception ignored) {
            order.setCashierId(1L); // 默认 admin
        }
        order.setRemark(dto.getRemark());
        orderMapper.insert(order);

        // ===== M16 营销事件发布：订单创建 =====
        try {
            if (order.getMemberId() != null) {
                marketingEventPublisher.orderPlaced(order.getTenantId(), order.getMemberId(),
                        order.getMemberName(), order.getOrderNo(), order.getTotalAmount());
            }
        } catch (Exception ex) {
            log.warn("[M16-Event] orderPlaced 发布失败: orderId={} err={}", order.getId(), ex.getMessage());
        }

        // 添加初始明细（如果带 items）
        if (dto.getItems() != null) {
            for (PosOrderCreateDTO.ItemDTO itemDto : dto.getItems()) {
                addItemInternal(order, itemDto);
            }
            recalcOrder(order);
        }
        return Result.success("订单创建成功", loadOrderFull(order.getId()));
    }

    @PostMapping("/orders/{id:\\d+}/items")
    public Result<PosOrder> addItem(@PathVariable Long id, @RequestBody PosOrderCreateDTO.ItemDTO itemDto) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该订单");
        if (order.getStatus() != 1) return Result.error("订单状态不允许修改");
        addItemInternal(order, itemDto);
        recalcOrder(order);
        return Result.success("已添加明细", loadOrderFull(id));
    }

    private void addItemInternal(PosOrder order, PosOrderCreateDTO.ItemDTO itemDto) {
        PosOrderItem item = new PosOrderItem();
        item.setOrderId(order.getId());

        BigDecimal unitPrice = itemDto.getUnitPrice();
        String serviceName = itemDto.getServiceName();
        BizService svc = null; // 提前声明，给 M14 定价实验复用

        // 服务ID 有效：从服务表取价
        if (itemDto.getServiceId() != null) {
            svc = serviceMapper.selectById(itemDto.getServiceId());
            if (svc != null) {
                serviceName = svc.getServiceName();
                if (unitPrice == null) {
                    // 会员价优先
                    unitPrice = (order.getMemberId() != null && svc.getMemberPrice() != null)
                            ? svc.getMemberPrice() : svc.getUnitPrice();
                }
            }
        }
        item.setServiceId(itemDto.getServiceId());
        item.setServiceName(serviceName == null ? "未命名服务" : serviceName);
        item.setQuantity(itemDto.getQuantity() == null ? 1 : itemDto.getQuantity());
        item.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        item.setDiscountAmount(itemDto.getDiscountAmount() == null ? BigDecimal.ZERO : itemDto.getDiscountAmount());
        item.setFinalPrice(item.getTotalPrice().subtract(item.getDiscountAmount()));
        item.setEmployeeId(itemDto.getEmployeeId());
        item.setEmployeeName(itemDto.getEmployeeName());
        item.setPackageOrderId(itemDto.getPackageOrderId());
        item.setPackageTimes(itemDto.getPackageTimes());
        item.setRemark(itemDto.getRemark());

        // ==================== M14 定价实验折扣（叠加在会员价之上） ====================
        // 规则：
        //   1. 仅当订单有会员 + 服务ID 有效时参与实验（避免匿名订单产生脏数据）
        //   2. 实验按 tenantId + serviceId + storeId + amount 匹配，单笔订单只命中 1 个进行中实验
        //   3. 实验命中后按会员确定性分组（assignVariant 幂等），计算折扣后叠加到 item.discountAmount
        //   4. 写入 pricing_experiment_result（RECORDED 状态），pay 成功后再 markConverted
        //   5. 失败 / 无实验 / 实验无折扣均静默（不阻断 POS 主链路）
        if (order.getMemberId() != null && svc != null && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            try {
                PricingExperiment exp = pricingExperimentService.findActiveExperiment(
                        order.getTenantId(),
                        svc.getId(),
                        order.getStoreId(),
                        null,
                        item.getUnitPrice());
                if (exp != null) {
                    String variant = pricingExperimentService.assignVariant(order.getMemberId(), exp);
                    BigDecimal discounted = pricingExperimentService.computeDiscountedPrice(
                            item.getUnitPrice(), variant, exp);
                    if (discounted.compareTo(item.getUnitPrice()) < 0) {
                        BigDecimal expDiscount = item.getUnitPrice().subtract(discounted);
                        item.setDiscountAmount(item.getDiscountAmount().add(expDiscount));
                        item.setFinalPrice(item.getFinalPrice().subtract(expDiscount));
                        // 标记实验引用，便于 pay 时追溯 + 实验报表分组
                        item.setRemark(appendRemark(item.getRemark(),
                                "[EXP:" + exp.getId() + ":" + variant + ":" + svc.getId() + "]"));
                        pricingExperimentService.recordResult(
                                order.getTenantId(),
                                exp.getId(),
                                exp.getExpKey(),
                                order.getMemberId(),
                                order.getStoreId(),
                                svc.getId(),
                                variant,
                                "A".equals(variant),
                                item.getUnitPrice(),
                                discounted,
                                null,
                                exp.getExpKey());
                        log.info("[M14-EXP] POS 应用实验折扣: orderId={} memberId={} expKey={} variant={} expDiscount={}",
                                order.getId(), order.getMemberId(), exp.getExpKey(), variant, expDiscount);
                    }
                }
            } catch (Exception ex) {
                // 实验失败绝不影响 POS 主链路
                log.warn("[M14-EXP] 应用实验折扣失败: orderId={} err={}", order.getId(), ex.getMessage());
            }
        }

        itemMapper.insert(item);
    }

    @DeleteMapping("/orders/{id:\\d+}/items/{itemId:\\d+}")
    public Result<PosOrder> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该订单");
        if (order.getStatus() != 1) return Result.error("订单状态不允许修改");
        itemMapper.deleteById(itemId);
        recalcOrder(order);
        return Result.success("已移除明细", loadOrderFull(id));
    }

    @PostMapping("/orders/{id:\\d+}/discount")
    public Result<PosOrder> applyDiscount(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该订单");
        if (order.getStatus() != 1) return Result.error("订单状态不允许修改");

        BigDecimal newDiscount = new BigDecimal(String.valueOf(body.get("discountAmount")));
        if (newDiscount.compareTo(BigDecimal.ZERO) < 0) return Result.error("折扣金额不能为负");
        if (newDiscount.compareTo(order.getTotalAmount()) > 0) return Result.error("折扣不能超过原价");

        order.setDiscountAmount(newDiscount);
        order.setFinalAmount(order.getTotalAmount().subtract(newDiscount));
        orderMapper.updateById(order);
        return Result.success("折扣已应用", loadOrderFull(id));
    }

    // ==================== M14 定价实验 quote ====================
    /**
     * M14 · 定价实验 quote（服务端权威分组 / 折扣）
     *
     * <p>前端 {@code checkout.vue} 选完会员后可调本端点拿到：
     * <ol>
     *   <li>该会员在当前 tenant 的进行中实验里被分到哪一组（A/B/C/D）</li>
     *   <li>按该组折扣模式计算后的折扣价</li>
     *   <li>实验元信息（expId/expKey）</li>
     * </ol>
     *
     * <p>无进行中实验 → {@code {active: false}}，前端不展示 A/B 标签。
     *
     * <p>幂等：多次调用同 memberId 永远返回同 variant（依赖 {@code ab_assignment} 表）。
     *
     * @param memberId  会员 ID（必填）
     * @param projectId 服务/项目 ID（用于实验适用范围匹配，可选）
     * @param storeId   门店 ID（用于实验适用范围匹配，可选）
     * @param amount    订单金额（用于最低金额条件匹配，可选）
     */
    @GetMapping("/experiment/quote")
    public Result<Map<String, Object>> experimentQuote(
            @RequestParam Long memberId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) BigDecimal amount) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) return Result.error(BizCode.UNAUTHORIZED, "未识别租户");

        Map<String, Object> data = new LinkedHashMap<>();
        PricingExperiment exp = pricingExperimentService.findActiveExperiment(
                tenantId, projectId, storeId, null, amount);
        if (exp == null) {
            data.put("active", false);
            return Result.success("当前无进行中实验", data);
        }
        String variant = pricingExperimentService.assignVariant(memberId, exp);
        BigDecimal original = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal discounted = pricingExperimentService.computeDiscountedPrice(original, variant, exp);

        data.put("active", true);
        data.put("expId", exp.getId());
        data.put("expKey", exp.getExpKey());
        data.put("expName", exp.getExpName());
        data.put("variant", variant);
        data.put("originalPrice", original);
        data.put("discountedPrice", discounted);
        data.put("discountAmount", original.subtract(discounted).max(BigDecimal.ZERO));
        return Result.success(data);
    }

    // ==================== M09 积分抵扣 ====================
    /**
     * M09 积分抵扣：会员用积分折抵订单金额
     *
     * <p>规则：
     * <ol>
     *   <li>读取 `mkt_points_rule.pointsToCash`（每 N 积分抵扣 1 元），默认 100:1</li>
     *   <li>扣减积分（暂挂，等到支付成功时才真正 consume）</li>
     *   <li>把积分折算金额加到 order.discountAmount，重算 finalAmount</li>
     *   <li>积分在 {@code pay} 成功时真正扣减</li>
     * </ol>
     *
     * <p>幂等：同 orderId + memberId 只允许一次（用 remark 标记防重）
     */
    @PostMapping("/orders/{id:\\d+}/points-discount")
    public Result<Map<String, Object>> applyPointsDiscount(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权操作该订单");
        if (order.getStatus() != 1) return Result.error("订单状态不允许修改");

        Long memberId = body.get("memberId") == null ? null
                : ((Number) body.get("memberId")).longValue();
        Integer pointsToUse = body.get("pointsToUse") == null ? null
                : ((Number) body.get("pointsToUse")).intValue();

        if (memberId == null) return Result.error("memberId 必填");
        if (pointsToUse == null || pointsToUse <= 0)
            return Result.error("pointsToUse 必须 > 0");

        // 幂等：检查是否已使用积分抵扣
        if (order.getRemark() != null && order.getRemark().contains("[积分抵扣")) {
            return Result.error("该订单已使用积分抵扣，不能重复操作");
        }

        // 积分余额检查
        Member member = memberService.getById(memberId);
        if (member == null) return Result.error("会员不存在");
        Integer currentPoints = member.getPoints() == null ? 0 : member.getPoints();
        if (currentPoints < pointsToUse) {
            return Result.error("积分不足，当前 " + currentPoints + " 分，需要 " + pointsToUse + " 分");
        }

        // 读取转换率：pointsToCash = N 积分抵 1 元（默认 100:1）
        BigDecimal pointsToCash = BigDecimal.valueOf(100); // 默认 100 分 = 1 元
        try {
            MktPointsRule rule = pointsRuleMapper.selectOne(new LambdaQueryWrapper<MktPointsRule>()
                    .eq(MktPointsRule::getTenantId, tenantId)
                    .eq(MktPointsRule::getEnabled, 1)
                    .last("LIMIT 1"));
            if (rule != null && rule.getPointsToCash() != null
                    && rule.getPointsToCash().compareTo(BigDecimal.ZERO) > 0) {
                pointsToCash = rule.getPointsToCash();
            }
        } catch (Exception ignored) { }

        // 积分折算现金折扣（向上取整到角）
        BigDecimal cashDiscount = BigDecimal.valueOf(pointsToUse)
                .divide(pointsToCash, 2, RoundingMode.HALF_UP);
        // 折扣上限：不能超过订单剩余应付
        BigDecimal remaining = order.getTotalAmount()
                .subtract(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount());
        if (cashDiscount.compareTo(remaining) > 0) {
            cashDiscount = remaining;
            // 反算实际使用积分
            pointsToUse = cashDiscount.multiply(pointsToCash)
                    .setScale(0, RoundingMode.HALF_UP).intValue();
        }

        // 更新订单折扣
        BigDecimal newDiscount = (order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount())
                .add(cashDiscount);
        order.setDiscountAmount(newDiscount);
        order.setFinalAmount(order.getTotalAmount().subtract(newDiscount));
        order.setRemark(appendRemark(order.getRemark(),
                "[积分抵扣 " + pointsToUse + " 分抵 ¥" + cashDiscount + "]"));
        orderMapper.updateById(order);

        // 记录本次抵扣快照（用于 pay 时还原）
        // 用 PosOrderItem 承载（无额外表改动）
        PosOrderItem pointsItem = new PosOrderItem();
        pointsItem.setOrderId(order.getId());
        pointsItem.setServiceName("[积分抵扣] " + pointsToUse + " 分抵 ¥" + cashDiscount);
        pointsItem.setQuantity(1);
        pointsItem.setUnitPrice(BigDecimal.ZERO);
        pointsItem.setFinalPrice(BigDecimal.ZERO);
        pointsItem.setDiscountAmount(cashDiscount);
        pointsItem.setRemark("POINTS:" + pointsToUse + ":" + memberId);
        itemMapper.insert(pointsItem);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getId());
        data.put("pointsUsed", pointsToUse);
        data.put("cashDiscount", cashDiscount);
        data.put("totalDiscount", newDiscount);
        data.put("finalAmount", order.getFinalAmount());
        return Result.success("积分抵扣成功", data);
    }

    @PostMapping("/orders/{id:\\d+}/pay")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> pay(@PathVariable Long id, @RequestBody PosPayDTO payDto) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权支付该订单");
        if (order.getStatus() != 1) return Result.error("订单状态不允许支付");

        // ===== M02-04 找零服务端独立计算（金额敏感）=====
        // 规则：
        //   1. 现金（method=cash）PaymentItem 必须传 tenderedAmount（实收）
        //   2. 非现金 PaymentItem 一律禁止传 tenderedAmount（防御前端误传/恶意构造）
        //   3. 服务端独立计算 changeAmount = tenderedAmount - itemAmount，前端不得传入
        //   4. 混合支付聚合：cashSum ≤ remaining，totalChange 写入订单
        List<PosPayDTO.PaymentItem> items = normalizePayments(payDto, order);
        if (items == null || items.isEmpty()) return Result.error("支付明细不能为空");

        // 先做金额/字段防御性校验
        Result<ChangeCalcResult> calcResult = computeChangeServerSide(items, order);
        if (calcResult.getCode() != 200) return Result.error(calcResult.getMessage());
        ChangeCalcResult calc = calcResult.getData();

        BigDecimal remaining = calc.remaining;
        BigDecimal cashSum = calc.cashSum;
        BigDecimal totalTendered = calc.totalTendered;
        BigDecimal totalChange = calc.totalChange;

        List<PosPayment> paymentList = new ArrayList<>();
        for (PosPayDTO.PaymentItem it : items) {
            PosPayment p = processOnePayment(order, it, calc.perItemChange.get(it));
            if (p == null) {
                // B-1: 必须抛异常才能触发 @Transactional 回滚；
                // 不允许 "部分支付失败但订单已落库" 的静默不一致状态。
                throw new BusinessException(BizCode.PAYMENT_FAILED, "支付失败：" + it.getMethod());
            }
            paymentList.add(p);
        }

        // 累计已付
        BigDecimal totalPaid = (order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount())
                .add(cashSum);
        order.setPaidAmount(totalPaid);
        // M02-04：记录现金实收/找零汇总到订单（服务端权威值，前端照此找零给顾客）
        if (totalTendered.compareTo(BigDecimal.ZERO) > 0) {
            order.setTenderedAmount(totalTendered);
        }
        if (totalChange.compareTo(BigDecimal.ZERO) > 0) {
            order.setChangeAmount(totalChange);
        }
        // 次卡核销/全额支付 都算订单完成
        boolean hasPackage = items.stream().anyMatch(i -> "package".equals(i.getMethod()));
        if (totalPaid.compareTo(order.getFinalAmount()) >= 0 || hasPackage) {
            order.setStatus(2);
            order.setPayTime(LocalDateTime.now());
        }
        orderMapper.updateById(order);
        if (order.getStatus() == 2) {
            // ===== M16 营销事件发布：订单已支付 =====
            try {
                if (order.getMemberId() != null) {
                    BigDecimal paidAmt = order.getPaidAmount() == null ? order.getFinalAmount() : order.getPaidAmount();
                    marketingEventPublisher.orderPaid(order.getTenantId(), order.getMemberId(),
                            order.getMemberName(), order.getOrderNo(), paidAmt);
                }
            } catch (Exception ex) {
                log.warn("[M16-Event] orderPaid 发布失败: orderId={} err={}", order.getId(), ex.getMessage());
            }
            // ===== M16 营销事件发布：消费（订单支付完成） =====
            try {
                if (order.getMemberId() != null) {
                    marketingEventPublisher.consume(order.getTenantId(), order.getMemberId(),
                            order.getMemberName(), order.getOrderNo(),
                            order.getPaidAmount() == null ? order.getFinalAmount() : order.getPaidAmount());
                }
            } catch (Exception ex) {
                log.warn("[M16-Event] consume 发布失败: orderId={} err={}", order.getId(), ex.getMessage());
            }
            commissionCalculationService.calculateOrder(order.getId());
            // M09: 积分抵扣 — 扫描备注中的积分记录，真实扣减
            if (order.getRemark() != null && order.getRemark().contains("[积分抵扣")) {
                try {
                    for (PosOrderItem it : order.getItems()) {
                        if (it.getRemark() != null && it.getRemark().startsWith("POINTS:")) {
                            String[] parts = it.getRemark().substring(7).split(":");
                            if (parts.length >= 2) {
                                int pts = Integer.parseInt(parts[0]);
                                Long mid = Long.parseLong(parts[1]);
                                pointService.consumePoints(mid, "POS_POINTS_DISCOUNT", pts,
                                        order.getId(), "收银积分抵扣#" + order.getOrderNo());
                                log.info("[POS-POINTS] 支付成功扣减积分: memberId={} points={} orderId={}",
                                        mid, pts, order.getId());
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("[POS-POINTS] 积分扣减失败 orderId={}: {}", order.getId(), ex.getMessage());
                    // 不阻断支付（积分损失不导致订单失败）
                }
            }
            // R2.0-D T-D02：库存消耗同步调用，保留"订单与库存同生共死"的事务语义。
            // 库存不足/CAS 失败抛 BusinessException → 事务回滚，订单也不落。
            try {
                inventoryConsumptionService.consumeByOrder(order.getId());
            } catch (com.meiyetec.exception.BusinessException be) {
                throw be;
            }
            // P0-M06：耗材自动扣减 — 按每个服务项目触发
            // deduplicate by serviceId（同一服务多次购买只扣一次耗材规则）
            order.getItems().stream()
                .collect(Collectors.toMap(
                    item -> item.getServiceId() != null ? item.getServiceId() : item.getId(),
                    item -> item,
                    (a, b) -> a))
                .values()
                .forEach(item -> {
                    if (item.getServiceId() == null) return;
                    ServiceMaterialDeductService.DeductContext ctx = new ServiceMaterialDeductService.DeductContext();
                    ctx.orderId = order.getId();
                    ctx.orderNo = order.getOrderNo();
                    ctx.orderItemId = item.getId();
                    ctx.serviceId = item.getServiceId();
                    ctx.serviceName = item.getServiceName();
                    ctx.employeeId = item.getEmployeeId() != null ? item.getEmployeeId() : order.getCashierId();
                    ctx.memberId = order.getMemberId();
                    ctx.storeId = order.getStoreId();
                    ctx.serviceQuantity = item.getQuantity() != null ? item.getQuantity() : 1;
                    try {
                        var dr = serviceMaterialDeductService.deductForService(ctx);
                        if (!dr.success) {
                            log.warn("[MATERIAL-DEDUCT] 部分耗材扣减失败 orderId={}, errors={}",
                                order.getId(), dr.errorMessages);
                        }
                    } catch (Exception e) {
                        log.error("[MATERIAL-DEDUCT] 耗材扣减异常 orderId={}, item={}", order.getId(), item.getServiceId(), e);
                        // 不阻断支付流程，只打日志
                    }
                });
            // M14：定价实验转化 — 把本次加车时 RECORDED 的结果标记 CONVERTED
            if (order.getMemberId() != null) {
                try {
                    pricingExperimentService.markConverted(
                            order.getMemberId(), order.getId(),
                            order.getPaidAmount() == null ? order.getFinalAmount() : order.getPaidAmount());
                    log.info("[M14-EXP] markConverted: orderId={} memberId={} amount={}",
                            order.getId(), order.getMemberId(), order.getPaidAmount());
                } catch (Exception ex) {
                    // 实验转化失败不阻断支付
                    log.warn("[M14-EXP] markConverted 失败: orderId={} err={}",
                            order.getId(), ex.getMessage());
                }
            }
            // R2.0-D T-D02 扩展点：订单支付成功事件（AFTER_COMMIT 监听）
            // 当前只有报表/统计类 listener 消费；同步库存消耗不依赖本事件。
            eventPublisher.publishEvent(new OrderPaidEvent(this, order));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("payments", paymentList);
        result.put("order", loadOrderFull(order.getId()));
        result.put("paidAmount", order.getPaidAmount());
        result.put("remaining", order.getFinalAmount().subtract(order.getPaidAmount()));
        // M02-04：找零金额（服务端权威值，前端照此找零给顾客）
        if (totalTendered.compareTo(BigDecimal.ZERO) > 0) {
            result.put("tenderedAmount", totalTendered);
            result.put("changeAmount", totalChange);
        }
        return Result.success("支付成功", result);
    }

    /**
     * M02-04 找零服务端独立计算（不信任前端传入的金额）。
     * <p>
     * 输入：归一化后的 PaymentItem 列表 + 订单上下文
     * 输出：每笔的找零（perItemChange）、现金汇总（cashSum）、实收汇总（totalTendered）、
     *       总找零（totalChange）、订单剩余应付（remaining）
     * <p>
     * 校验：
     * <ol>
     *   <li>非 cash method 禁止传 tenderedAmount（防御前端误传/恶意构造）</li>
     *   <li>cash method 必须传 tenderedAmount，且 tenderedAmount ≥ amount（实收 < 应付直接拒绝）</li>
     *   <li>单笔金额上限保护（amount ≤ 999999.99，防止溢出/异常输入）</li>
     *   <li>现金汇总 ≤ 剩余应付（cashSum 不会让顾客多付）</li>
     * </ol>
     */
    private Result<ChangeCalcResult> computeChangeServerSide(List<PosPayDTO.PaymentItem> items, PosOrder order) {
        ChangeCalcResult out = new ChangeCalcResult();
        BigDecimal remaining = order.getFinalAmount().subtract(
                order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount());
        out.remaining = remaining;

        BigDecimal cashSum = BigDecimal.ZERO;
        BigDecimal totalTendered = BigDecimal.ZERO;
        BigDecimal totalChange = BigDecimal.ZERO;
        Map<PosPayDTO.PaymentItem, BigDecimal> perItemChange = new IdentityHashMap<>();

        for (PosPayDTO.PaymentItem it : items) {
            BigDecimal itemAmount = it.getAmount() == null ? BigDecimal.ZERO : it.getAmount();
            // 单笔金额上限保护
            if (itemAmount.compareTo(BigDecimal.ZERO) < 0) {
                return Result.error("支付金额不能为负：" + it.getMethod());
            }
            if (itemAmount.compareTo(new BigDecimal("999999.99")) > 0) {
                return Result.error("单笔支付金额超过上限 999999.99：" + it.getMethod());
            }

            boolean isCash = "cash".equals(it.getMethod());

            // 防御：非 cash 禁止传 tenderedAmount
            if (!isCash && it.getTenderedAmount() != null) {
                return Result.error("非现金支付方式 [" + it.getMethod() + "] 禁止传 tenderedAmount");
            }

            if (isCash) {
                BigDecimal tendered = it.getTenderedAmount();
                if (tendered == null) {
                    return Result.error("现金支付必须传 tenderedAmount（实收金额）");
                }
                if (tendered.compareTo(BigDecimal.ZERO) < 0) {
                    return Result.error("现金实收金额不能为负");
                }
                if (tendered.compareTo(new BigDecimal("999999.99")) > 0) {
                    return Result.error("现金实收金额超过上限 999999.99");
                }
                if (tendered.compareTo(itemAmount) < 0) {
                    return Result.error("现金实收 ¥" + tendered.setScale(2, RoundingMode.HALF_UP)
                            + " 不足应付 ¥" + itemAmount.setScale(2, RoundingMode.HALF_UP));
                }
                // 服务端权威计算找零
                BigDecimal change = tendered.subtract(itemAmount).setScale(2, RoundingMode.HALF_UP);
                perItemChange.put(it, change);
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    totalChange = totalChange.add(change);
                }
                totalTendered = totalTendered.add(tendered);
            }
            cashSum = cashSum.add(itemAmount);
        }

        // 现金汇总（含 cash 与非 cash 的实际入账）≤ 剩余应付
        if (cashSum.compareTo(remaining) > 0) {
            return Result.error("支付金额 ¥" + cashSum.setScale(2, RoundingMode.HALF_UP)
                    + " 超过剩余应付 ¥" + remaining.setScale(2, RoundingMode.HALF_UP));
        }

        out.cashSum = cashSum;
        out.totalTendered = totalTendered.setScale(2, RoundingMode.HALF_UP);
        out.totalChange = totalChange.setScale(2, RoundingMode.HALF_UP);
        out.perItemChange = perItemChange;
        return Result.success(out);
    }

    /**
     * M02-04 找零计算结果 DTO（包内可见，避免污染 service 层）
     */
    private static class ChangeCalcResult {
        BigDecimal remaining;        // 剩余应付
        BigDecimal cashSum;          // 本次支付合计（不含找零）
        BigDecimal totalTendered;    // 现金实收合计
        BigDecimal totalChange;      // 总找零（服务端权威计算）
        Map<PosPayDTO.PaymentItem, BigDecimal> perItemChange;  // 每笔 cash 的找零
    }

    /** 把单笔/混合统一成 List<PaymentItem> */
    private List<PosPayDTO.PaymentItem> normalizePayments(PosPayDTO dto, PosOrder order) {
        if (dto.getPayments() != null && !dto.getPayments().isEmpty()) {
            return dto.getPayments();
        }
        if (dto.getMethod() != null) {
            PosPayDTO.PaymentItem one = new PosPayDTO.PaymentItem();
            one.setMethod(dto.getMethod());
            one.setAmount(dto.getAmount());
            one.setTenderedAmount(dto.getTenderedAmount()); // M02-04
            one.setTransactionId(dto.getTransactionId());
            return Collections.singletonList(one);
        }
        return Collections.emptyList();
    }

    /** 处理单笔支付（含次卡/数字人民币等特殊 method）。{@code serverSideChange} 为服务端权威计算的找零（仅 cash 有效），前端不可信 */
    private PosPayment processOnePayment(PosOrder order, PosPayDTO.PaymentItem it, BigDecimal serverSideChange) {
        String method = it.getMethod();
        BigDecimal amount = it.getAmount() == null ? BigDecimal.ZERO : it.getAmount();
        PosPayment p = new PosPayment();
        p.setOrderId(order.getId());
        p.setPaymentNo(generatePaymentNo());
        p.setMethod(method);
        p.setAmount(amount);
        p.setStatus(1);
        p.setPayTime(LocalDateTime.now());
        p.setTransactionId(it.getTransactionId());

        // M02-04：现金支付记录实收金额 + 找零（服务端权威计算，前端不得传入 changeAmount）
        if ("cash".equals(method)) {
            BigDecimal tendered = it.getTenderedAmount();
            // tendered 已经过 computeChangeServerSide 校验必填且 ≥ amount
            p.setTenderedAmount(tendered);
            p.setChangeAmount(serverSideChange == null ? BigDecimal.ZERO : serverSideChange);
        }

        try {
            switch (method) {
                case "wallet" -> {
                    // P0-MONEY-002：钱包支付改走 WalletService.doConsume，避免双轨漂移
                    if (order.getMemberId() == null) throw new RuntimeException("非会员不能用储值支付");
                    com.meiyetec.dto.WalletOpDTO walletDto = new com.meiyetec.dto.WalletOpDTO();
                    walletDto.setMemberId(order.getMemberId());
                    walletDto.setAmount(amount);
                    walletDto.setOrderId(order.getId());
                    walletDto.setOperatorId(order.getCashierId());
                    walletDto.setPayMethod("wallet");
                    walletDto.setRemark("订单消费:" + order.getOrderNo());
                    Result<Map<String, Object>> walletResult = walletService.doConsume(
                            walletDto, TenantContextHolder.get().getTenantId());
                    if (walletResult.getCode() != 200) {
                        throw new RuntimeException("钱包扣款失败: " + walletResult.getMessage());
                    }
                }
                case "package" -> {
                    // P1-PACKAGE-001: 次卡核销必须由用户在 POS 界面选择具体哪张次卡
                    // 不再自动取订单明细中的 packageOrderId，防止误核销
                    Long cardId = it.getMemberCardId();
                    if (cardId == null) {
                        throw new BusinessException(BizCode.BAD_REQUEST,
                            "请在支付时选择要核销的次卡（memberCardId 必填）");
                    }
                    // B-2: 幂等键 = "pos.package" + orderId + cardId，防止前端重复提交
                    String idemKey = "pos.package:" + order.getId() + ":" + cardId;
                    final Integer times = 1;
                    final Long finalCardId = cardId;
                    var pkgResult = idem.execute("pos.package", idemKey,
                            new PosPayDTO.PackageRedeemPayload(order.getId(), cardId, times),
                            () -> packageRedeemService.redeem(
                                    order.getId(), finalCardId, times,
                                    TenantContextHolder.get().getTenantId(),
                                    order.getCashierId(),
                                    order.getStoreId()));
                    if (pkgResult.result.getCode() != 200) {
                        throw new RuntimeException("套餐核销失败: " + pkgResult.result.getMessage());
                    }
                    p.setAmount(BigDecimal.ZERO); // 次卡本身免费
                }
                case "e_cny_soft", "e_cny_hard" -> {
                    // 数字人民币：模拟对接（实际接入央行/运营机构网关 TODO）
                    if (it.getTransactionId() == null) {
                        // P0-MONEY-003: 雪花算法 transactionId (替换 Math.random()*10000)
                        p.setTransactionId("ECNY-" + idGenerator.nextIdStr());
                    }
                }
                case "cash", "wechat", "alipay", "unionpay" -> { /* 现金/第三方，记录即可 */ }
                default -> throw new RuntimeException("不支持的支付方式: " + method);
            }
        } catch (RuntimeException ex) {
            p.setStatus(2);
            p.setErrorMsg(ex.getMessage());
            paymentMapper.insert(p);
            return null;
        }
        paymentMapper.insert(p);
        return p;
    }

    /**
     * B-2 · 已迁移到 {@link com.meiyetec.service.PackageRedeemService}。
     * 旧实现不安全（无双重检查 + 无 version CAS），保留签名仅防编译错误。
     */
    @Deprecated
    private void redeemPackage(PosOrder order, Long packageOrderId) {
        throw new UnsupportedOperationException(
                "redeemPackage 已迁移，请使用 PackageRedeemService.redeem()");
    }

    @PostMapping("/orders/{id:\\d+}/refund")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<PosOrder> refund(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权退款该订单");
        if (order.getStatus() != 2) return Result.error("只能退款已支付订单");

        BigDecimal refundAmt = body.get("amount") == null ? order.getPaidAmount() :
                new BigDecimal(String.valueOf(body.get("amount")));
        String reason = (String) body.get("reason");
        if (refundAmt.compareTo(order.getPaidAmount()) > 0) {
            return Result.error("退款金额超过实付");
        }

        // 如果用 wallet 支付，退回余额
        QueryWrapper<PosPayment> pw = new QueryWrapper<>();
        pw.eq("order_id", id).eq("method", "wallet").eq("status", 1);
        PosPayment walletPayment = paymentMapper.selectOne(pw);
        if (walletPayment != null && order.getMemberId() != null) {
            // 按比例退
            BigDecimal ratio = refundAmt.divide(order.getPaidAmount(), 4, RoundingMode.HALF_UP);
            BigDecimal returnAmt = walletPayment.getAmount().multiply(ratio).setScale(2, RoundingMode.HALF_UP);

            // P0-MONEY-004 + P0-MONEY-002: 改走 WalletService.doRefund
            // 原因：(1) tenantId 不再硬编码 1L；(2) 不再绕过 WalletService 的 CAS + 幂等
            //      (3) crm_member.balance 由 syncMemberBalance 自动同步
            Long memberId = order.getMemberId();
            BigDecimal beforeRefundBalance = walletService != null
                    ? jdbc.queryForObject("SELECT balance FROM crm_wallet WHERE member_id = ?",
                            BigDecimal.class, memberId)
                    : null;

            com.meiyetec.dto.WalletOpDTO refundDto = new com.meiyetec.dto.WalletOpDTO();
            refundDto.setMemberId(memberId);
            refundDto.setAmount(returnAmt);
            refundDto.setOrderId(order.getId());
            refundDto.setOperatorId(order.getCashierId());
            refundDto.setPayMethod("wallet_refund");
            refundDto.setRemark("订单退款:" + order.getOrderNo());
            Result<Map<String, Object>> walletRefundResult = walletService.doRefund(
                    refundDto, TenantContextHolder.get().getTenantId());
            if (walletRefundResult.getCode() != 200) {
                throw new RuntimeException("钱包退款失败: " + walletRefundResult.getMessage());
            }

            walletPayment.setStatus(3);
            paymentMapper.updateById(walletPayment);
        }

        order.setStatus(3);
        order.setRefundTime(LocalDateTime.now());
        order.setRefundAmount(refundAmt);
        order.setRefundReason(reason);
        orderMapper.updateById(order);
        commissionCalculationService.invalidateOrder(order.getId(), reason == null ? "订单退款" : reason);
        return Result.success("退款成功", loadOrderFull(id));
    }

    @PostMapping("/orders/{id:\\d+}/void")
    public Result<PosOrder> voidOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return Result.error("订单不存在");
        if (tenantId != null && order.getTenantId() != null && !order.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权作废该订单");
        if (order.getStatus() != 1) return Result.error("只能撤销未支付订单");

        order.setStatus(4);
        order.setRefundReason(body == null ? null : (String) body.get("reason"));
        orderMapper.updateById(order);
        return Result.success("已撤销", loadOrderFull(id));
    }

    @GetMapping("/orders/{id:\\d+}")
    public Result<PosOrder> orderDetail(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PosOrder o = loadOrderFull(id);
        if (o == null) return Result.error("订单不存在");
        if (tenantId != null && o.getTenantId() != null && !o.getTenantId().equals(tenantId))
            return Result.error(BizCode.FORBIDDEN, "无权访问该订单");
        return Result.success(o);
    }

    @PostMapping("/orders/page")
    public Result<PageResult<PosOrder>> orderPage(@RequestBody PageQueryDTO query) {
        Page<PosOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        // 性能护栏：单次最多查 200 单，超过就截断（避免一次拉 1 万单把内存打爆）
        if (query.getPageSize() > 200) {
            page.setSize(200);
            query.setPageSize(200);
        }
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PosOrder> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) wrapper.eq(PosOrder::getTenantId, tenantId);
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            String kw = query.getKeyword();
            wrapper.and(w -> w.like(PosOrder::getOrderNo, kw)
                    .or().like(PosOrder::getMemberName, kw)
                    .or().like(PosOrder::getMemberPhone, kw));
        }
        if (query.getStatus() != null) wrapper.eq(PosOrder::getStatus, query.getStatus());
        if (query.getStoreId() != null) wrapper.eq(PosOrder::getStoreId, query.getStoreId());
        if (query.getMemberId() != null) wrapper.eq(PosOrder::getMemberId, query.getMemberId());
        wrapper.orderByDesc(PosOrder::getId);
        Page<PosOrder> result = orderMapper.selectPage(page, wrapper);

        // 批量补全 items + payments + cashierName（IN 查询，一次拉完，避免 N+1）
        List<PosOrder> orders = result.getRecords();
        if (orders != null && !orders.isEmpty()) {
            enrichOrders(orders);
        }
        return Result.success(PageResult.of(result));
    }

    /**
     * 批量补全 items + payments + cashierName 到订单列表
     * 性能：1 次主查询 + 3 次 IN 查询（与订单数无关）
     */
    private void enrichOrders(List<PosOrder> orders) {
        List<Long> orderIds = orders.stream().map(PosOrder::getId).collect(Collectors.toList());

        // 1. 一次性查所有 items
        Map<Long, List<PosOrderItem>> itemMap = itemMapper.selectList(
                new LambdaQueryWrapper<PosOrderItem>().in(PosOrderItem::getOrderId, orderIds)
        ).stream().collect(Collectors.groupingBy(PosOrderItem::getOrderId));

        // 2. 一次性查所有 payments
        Map<Long, List<PosPayment>> paymentMap = paymentMapper.selectList(
                new LambdaQueryWrapper<PosPayment>().in(PosPayment::getOrderId, orderIds)
        ).stream().collect(Collectors.groupingBy(PosPayment::getOrderId));

        // 3. 一次性查所有 cashierName（避免每条订单单独查 N+1）
        Set<Long> cashierIds = orders.stream()
                .map(PosOrder::getCashierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> cashierNameMap = new HashMap<>();
        if (!cashierIds.isEmpty()) {
            try {
                List<Map<String, Object>> rows = namedJdbc.queryForList(
                        "SELECT id, real_name, username FROM sys_user WHERE id IN (:ids)",
                        new MapSqlParameterSource("ids", cashierIds));
                for (Map<String, Object> r : rows) {
                    Object idObj = r.get("id");
                    if (idObj == null) continue;
                    long uid = ((Number) idObj).longValue();
                    String name = (String) r.get("real_name");
                    if (name == null || name.isEmpty()) name = (String) r.get("username");
                    if (name != null) cashierNameMap.put(uid, name);
                }
            } catch (Exception ignored) {
                // sys_user 表可能尚未就绪（演示库缺失），静默兜底
            }
        }

        // 4. 回填到每个订单
        orders.forEach(o -> {
            o.setItems(itemMap.getOrDefault(o.getId(), new ArrayList<>()));
            o.setPayments(paymentMap.getOrDefault(o.getId(), new ArrayList<>()));
            if (o.getCashierId() != null) {
                o.setCashierName(cashierNameMap.getOrDefault(o.getCashierId(), o.getCashierName()));
            }
        });
    }

    @GetMapping("/orders/today")
    public Result<List<PosOrder>> todayOrders(@RequestParam(required = false) Long storeId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<PosOrder> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null) wrapper.eq(PosOrder::getTenantId, tenantId);
        if (storeId != null) wrapper.eq(PosOrder::getStoreId, storeId);
        wrapper.ge(PosOrder::getCreateTime, today.atStartOfDay())
               .lt(PosOrder::getCreateTime, today.plusDays(1).atStartOfDay())
               .orderByDesc(PosOrder::getId);
        return Result.success(orderMapper.selectList(wrapper));
    }

    @GetMapping("/orders/stats")
    public Result<Map<String, Object>> stats(@RequestParam(required = false) Long storeId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<PosOrder> base = new LambdaQueryWrapper<>();
        if (tenantId != null) base.eq(PosOrder::getTenantId, tenantId);
        if (storeId != null) base.eq(PosOrder::getStoreId, storeId);

        List<PosOrder> all = orderMapper.selectList(base.clone());
        BigDecimal todayRevenue = all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .filter(o -> o.getPayTime() != null && o.getPayTime().toLocalDate().equals(today))
                .map(o -> o.getPaidAmount() == null ? BigDecimal.ZERO : o.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long todayCount = all.stream()
                .filter(o -> o.getPayTime() != null && o.getPayTime().toLocalDate().equals(today))
                .count();
        long pendingCount = all.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 1).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOrders", all.size());
        stats.put("todayRevenue", todayRevenue);
        stats.put("todayOrderCount", todayCount);
        stats.put("pendingCount", pendingCount);
        return Result.success(stats);
    }

    // ==================== Helper methods ====================

    private void recalcOrder(PosOrder order) {
        List<PosOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PosOrderItem>().eq(PosOrderItem::getOrderId, order.getId()));
        BigDecimal total = items.stream()
                .map(i -> i.getTotalPrice() == null ? BigDecimal.ZERO : i.getTotalPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemDiscount = items.stream()
                .map(i -> i.getDiscountAmount() == null ? BigDecimal.ZERO : i.getDiscountAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finalAmt = items.stream()
                .map(i -> i.getFinalPrice() == null ? BigDecimal.ZERO : i.getFinalPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(total);
        order.setDiscountAmount(itemDiscount);
        order.setFinalAmount(finalAmt);
        orderMapper.updateById(order);
    }

    private PosOrder loadOrderFull(Long id) {
        PosOrder order = orderMapper.selectById(id);
        if (order == null) return null;
        order.setItems(itemMapper.selectList(
                new LambdaQueryWrapper<PosOrderItem>().eq(PosOrderItem::getOrderId, id)
                        .orderByAsc(PosOrderItem::getId)));
        order.setPayments(paymentMapper.selectList(
                new LambdaQueryWrapper<PosPayment>().eq(PosPayment::getOrderId, id)
                        .orderByAsc(PosPayment::getId)));
        // 补 cashierName（单条详情，1 次查询）
        if (order.getCashierId() != null && order.getCashierName() == null) {
            try {
                List<Map<String, Object>> rows = namedJdbc.queryForList(
                        "SELECT real_name, username FROM sys_user WHERE id = :id",
                        new MapSqlParameterSource("id", order.getCashierId()));
                if (!rows.isEmpty()) {
                    Map<String, Object> r = rows.get(0);
                    String name = (String) r.get("real_name");
                    if (name == null || name.isEmpty()) name = (String) r.get("username");
                    if (name != null) order.setCashierName(name);
                }
            } catch (Exception ignored) { /* 兜底：保持 null */ }
        }
        return order;
    }

    private String generateOrderNo() {
        // P0-MONEY-003: 雪花算法 ID (替换 new Random().nextInt(999999) 易碰撞问题)
        return "PO" + idGenerator.nextIdStr();
    }

    private String generatePaymentNo() {
        // P0-MONEY-003: 雪花算法 ID
        return "PAY" + idGenerator.nextIdStr();
    }

    /** 追加备注（null-safe） */
    private String appendRemark(String original, String add) {
        if (original == null || original.isBlank()) return add;
        return original + " || " + add;
    }
}

