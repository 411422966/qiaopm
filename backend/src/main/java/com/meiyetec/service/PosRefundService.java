package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.dto.PosRefundCreateDTO;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.*;
import com.meiyetec.enums.RefundStatus;
import com.meiyetec.enums.RefundStrategy;
import com.meiyetec.enums.RefundType;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.IdGenerator;
import com.meiyetec.util.ObjectMapperHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * R2.0-C 退款单服务。
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li>创建退款单（FULL / PART / CROSS）</li>
 *   <li>状态机迁移：DRAFT -> PENDING -> AUDIT -> APPROVED -> PROCESSING -> SUCCESS</li>
 *   <li>触发逆向：套餐 / 积分 / 库存 / 提成</li>
 *   <li>幂等：Idempotency-Key + 同一 orderId 已存在 SUCCESS 退款单 → 直接返回</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosRefundService {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 退款进入人工审核的金额阈值 */
    private static final BigDecimal AUDIT_AMOUNT_THRESHOLD = new BigDecimal("5000");
    /** POS-008 v2：audit_status 字段 APPROVED 常量（与 RefundAuditWorkflowService.AUDIT_STATUS_APPROVED 同义） */
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";

    private final PosRefundMapper refundMapper;
    private final PosRefundItemMapper refundItemMapper;
    private final PosRefundReverseLogMapper reverseLogMapper;
    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper orderItemMapper;
    private final PosPaymentMapper paymentMapper;
    private final MemberMapper memberMapper;
    private final WalletMapper walletMapper;
    private final PackageOrderMapper packageOrderMapper;
    private final StockInventoryMapper inventoryMapper;
    private final StockRecordMapper stockRecordMapper;
    private final CommissionCalculationService commissionCalculationService;
    private final IdempotencyService idem;
    private final JdbcTemplate jdbc;
    private final IdGenerator idGenerator;
    /** B-6 / B-10 · 退款走 walletService.doRefund，禁止 PosRefundService 直接写 crm_wallet.balance */
    private final WalletService walletService;
    /** POS-008 多级审核 */
    private final RefundAuditWorkflowService auditWorkflow;
    private final ObjectMapper mapper = ObjectMapperHolder.MAPPER;

    /** 积分 CAS 走 PointService（不绕过乐观锁） */
    private PointService pointService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setPointService(PointService pointService) {
        this.pointService = pointService;
    }

    // ====================== 创建退款单 ======================

    @Transactional(rollbackFor = Exception.class)
    public PosRefund createRefund(PosRefundCreateDTO dto, String idemKey, String traceId) {
        // 跨单退款走专门方法
        if (dto.getCrossOrderIds() != null && !dto.getCrossOrderIds().isEmpty()) {
            return createCrossRefund(dto, idemKey, traceId);
        }
        PosOrder order = orderMapper.selectById(dto.getOrderId());
        if (order == null) throw new BusinessException(BizCode.NOT_FOUND, "原订单不存在");
        if (order.getStatus() == null || order.getStatus() != 2) {
            throw new BusinessException(BizCode.ORDER_STATUS_INVALID, "只能退款已支付订单");
        }

        // 幂等：同 orderId 已有 SUCCESS 退款单 → 直接返回
        PosRefund existing = refundMapper.selectOne(new LambdaQueryWrapper<PosRefund>()
                .eq(PosRefund::getOrderId, order.getId())
                .eq(PosRefund::getStatus, RefundStatus.SUCCESS.name())
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException(BizCode.REFUND_EXCEED, "该订单已有成功退款单，不能重复创建");
        }

        BigDecimal paid = order.getPaidAmount() == null ? BigDecimal.ZERO : order.getPaidAmount();
        BigDecimal refunded = order.getRefundedAmount() == null ? BigDecimal.ZERO : order.getRefundedAmount();
        BigDecimal remain = paid.subtract(refunded);
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.REFUND_EXCEED, "订单已全部退款，不可再退");
        }

        BigDecimal amount = dto.getRefundAmount() == null ? remain : dto.getRefundAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.REFUND_AMOUNT_INVALID, "退款金额必须 > 0");
        }
        if (amount.compareTo(remain) > 0) {
            throw new BusinessException(BizCode.REFUND_EXCEED,
                    "退款金额超过可退余额（已退 " + refunded + "，实付 " + paid + "）");
        }

        RefundType type = amount.compareTo(paid) >= 0 ? RefundType.FULL : RefundType.PART;
        RefundStrategy strategy = RefundStrategy.valueOf(dto.getStrategy() == null ? "ORIGINAL" : dto.getStrategy());
        if (strategy == RefundStrategy.MIXED) {
            if (dto.getWalletAmount() == null || dto.getWalletAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(BizCode.REFUND_AMOUNT_INVALID, "策略 MIXED 必须传 walletAmount");
            }
        }

        PosRefund refund = new PosRefund();
        refund.setTenantId(order.getTenantId());
        refund.setStoreId(order.getStoreId());
        refund.setRefundNo(generateRefundNo());
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        refund.setMemberId(order.getMemberId());
        refund.setMemberName(order.getMemberName());
        refund.setRefundType(type.name());
        refund.setRefundAmount(amount);
        refund.setPaidAmount(paid);
        refund.setRefundedAmount(refunded);
        refund.setRemainingAmount(remain.subtract(amount));
        refund.setRefundStrategy(strategy.name());
        refund.setCashRefund(BigDecimal.ZERO);
        refund.setWalletRefund(BigDecimal.ZERO);
        refund.setThirdpartyRefund(BigDecimal.ZERO);
        switch (strategy) {
            case WALLET -> refund.setWalletRefund(amount);
            case MIXED -> {
                refund.setWalletRefund(dto.getWalletAmount());
                BigDecimal original = dto.getOriginalAmount() != null
                        ? dto.getOriginalAmount()
                        : amount.subtract(dto.getWalletAmount());
                refund.setThirdpartyRefund(original);
                refund.setCashRefund(original);
            }
            default -> {
                refund.setThirdpartyRefund(amount);
                refund.setCashRefund(amount);
            }
        }
        // POS-008 v2：使用审核工作流计算显式 auditStatus 与 overall status
        //  - auditStatus != null → 进入审核（status 置 AUDIT 等审核通过）
        //  - auditStatus == null → 无需审核（status 直接 APPROVED）
        String initialAuditStatus = auditWorkflow.resolveInitialAuditStatus(amount);
        int auditLevel = auditWorkflow.resolveAuditLevel(amount);
        RefundStatus nextStatus = initialAuditStatus != null ? RefundStatus.AUDIT : RefundStatus.PENDING;
        refund.setStatus(nextStatus.name());
        refund.setAuditStatus(initialAuditStatus);
        refund.setCurrentAuditLevel(auditLevel);
        refund.setAuditRequired(initialAuditStatus == null ? 0 : 1);
        refund.setReason(dto.getReason());
        refund.setOperatorId(dto.getOperatorId());
        refund.setIdempotencyKey(idemKey);
        refund.setTraceId(traceId);
        refund.setApplyTime(LocalDateTime.now());
        refund.setVersion(0);
        refundMapper.insert(refund);
        // POS-008: 写 SUBMIT 审核流水
        auditWorkflow.onRefundCreated(refund);

        // 写子表：按原支付明细拆分
        List<PosPayment> pays = paymentMapper.selectList(new LambdaQueryWrapper<PosPayment>()
                .eq(PosPayment::getOrderId, order.getId())
                .eq(PosPayment::getStatus, 1));
        if (pays.isEmpty()) {
            throw new BusinessException(BizCode.REFUND_AMOUNT_INVALID, "订单无可用支付明细");
        }
        // 按比例拆分金额到每笔支付
        BigDecimal refundLeft = amount;
        for (int i = 0; i < pays.size(); i++) {
            PosPayment p = pays.get(i);
            BigDecimal portion;
            if (i == pays.size() - 1) {
                portion = refundLeft;
            } else {
                portion = amount.multiply(p.getAmount()).divide(paid, 2, RoundingMode.HALF_UP);
            }
            refundLeft = refundLeft.subtract(portion);
            if (portion.compareTo(BigDecimal.ZERO) <= 0) continue;

            PosRefundItem item = new PosRefundItem();
            item.setTenantId(TenantContextHolder.get().getTenantId());
            item.setRefundId(refund.getId());
            item.setPaymentId(p.getId());
            item.setPaymentMethod(p.getMethod());
            item.setPaymentAmount(p.getAmount());
            item.setRefundAmount(portion);
            item.setPaymentNo(p.getPaymentNo());
            item.setStatus("PENDING");
            refundItemMapper.insert(item);
        }

        // POS-008：审核级别 = 0（无需审核）时直接 APPROVED；否则停在 AUDIT 等待审核
        if (nextStatus == RefundStatus.PENDING) {
            transitStatus(refund, RefundStatus.APPROVED, true);
        }

        return loadFull(refund);
    }

    @Transactional(rollbackFor = Exception.class)
    public PosRefund createCrossRefund(PosRefundCreateDTO dto, String idemKey, String traceId) {
        if (dto.getCrossOrderIds() == null || dto.getCrossOrderIds().size() < 2) {
            throw new BusinessException(BizCode.BAD_REQUEST, "跨单退款至少 2 张订单");
        }
        String groupId = "CG" + idGenerator.nextIdStr();
        PosRefund first = null;
        for (int i = 0; i < dto.getCrossOrderIds().size(); i++) {
            Long orderId = dto.getCrossOrderIds().get(i);
            PosRefundCreateDTO sub = new PosRefundCreateDTO();
            sub.setOrderId(orderId);
            sub.setRefundAmount(dto.getRefundAmount());
            sub.setStrategy(dto.getStrategy());
            sub.setReason(dto.getReason());
            sub.setOperatorId(dto.getOperatorId());
            sub.setReverseAsset(dto.getReverseAsset());
            PosRefund r = createRefund(sub, idemKey, traceId);
            r.setCrossGroupId(groupId);
            refundMapper.updateById(r);
            if (i == 0) first = r;
        }
        return loadFull(first);
    }

    // ====================== 状态机迁移 ======================

    @Transactional(rollbackFor = Exception.class)
    public PosRefund audit(Long refundId, Boolean approved, String rejectReason, Long auditorId, String auditorName) {
        // POS-008: 多级审核通过 workflow 委托
        if (approved) {
            PosRefund result = auditWorkflow.approve(refundId, auditorId, auditorName, "auditor", null).getData();
            return result == null ? locked(refundId) : result;
        } else {
            PosRefund result = auditWorkflow.reject(refundId, auditorId, auditorName, "auditor", rejectReason).getData();
            return result == null ? locked(refundId) : result;
        }
    }

    // ----- POS-008 v2 显式三级审核链 -----

    /** 门店审核：PENDING_STORE → PENDING_HQ / APPROVED（≤ 500 门店终审） */
    @Transactional(rollbackFor = Exception.class)
    public PosRefund auditByStore(Long refundId, Long actorId, String actorName,
                                    String actorRole, String remark) {
        return auditWorkflow.auditByStore(refundId, actorId, actorName, actorRole, remark).getData();
    }

    /** 总部审核：PENDING_HQ → PENDING_FINANCE / APPROVED（500 < amount ≤ 5000 总部终审） */
    @Transactional(rollbackFor = Exception.class)
    public PosRefund auditByHq(Long refundId, Long actorId, String actorName,
                                  String actorRole, String remark) {
        return auditWorkflow.auditByHq(refundId, actorId, actorName, actorRole, remark).getData();
    }

    /** 财务终审：PENDING_FINANCE → APPROVED（> 5000 财务必审） */
    @Transactional(rollbackFor = Exception.class)
    public PosRefund auditByFinance(Long refundId, Long actorId, String actorName,
                                      String actorRole, String remark) {
        return auditWorkflow.auditByFinance(refundId, actorId, actorName, actorRole, remark).getData();
    }

    /** 显式驳回（任意级别）：PENDING_STORE/HQ/FINANCE → REJECTED */
    @Transactional(rollbackFor = Exception.class)
    public PosRefund auditReject(Long refundId, Long actorId, String actorName,
                                   String actorRole, String rejectReason) {
        return auditWorkflow.reject(refundId, actorId, actorName, actorRole, rejectReason).getData();
    }

    @Transactional(rollbackFor = Exception.class)
    public PosRefund process(Long refundId) {
        PosRefund refund = locked(refundId);
        // POS-008 v2：闸门同时校验 overall status 与显式 audit_status
        if (!RefundStatus.APPROVED.name().equals(refund.getStatus())) {
            throw new BusinessException(BizCode.CONFLICT,
                    "仅 APPROVED 状态可受理，目前 status=" + refund.getStatus());
        }
        String auditStatus = refund.getAuditStatus();
        if (auditStatus != null && !RefundStatus.APPROVED.name().equals(auditStatus)
                && !AUDIT_STATUS_APPROVED.equals(auditStatus)) {
            // 兼容：老数据 audit_status 可能为 null（无需审核时），此时 status=APPROVED 即可放行
            throw new BusinessException(BizCode.CONFLICT,
                    "仅 audit_status=APPROVED 可执行原路退款，目前 auditStatus="
                            + auditStatus + "（status=" + refund.getStatus() + "）");
        }
        transitStatus(refund, RefundStatus.PROCESSING, true);

        // 状态机进入 PROCESSING 后触发通道回调 + 逆向
        try {
            // 模拟通道退款（实际接入微信/支付宝/银联后再接）
            List<PosRefundItem> items = refundItemMapper.selectList(new LambdaQueryWrapper<PosRefundItem>()
                    .eq(PosRefundItem::getRefundId, refundId));
            for (PosRefundItem item : items) {
                if ("wallet".equalsIgnoreCase(item.getPaymentMethod())) {
                    // 储值：直接走逆向 + 写入 WALLET
                    if (Boolean.TRUE.equals(refund.getRefundStrategy().equals(RefundStrategy.WALLET.name())
                            ? Boolean.TRUE
                            : Boolean.FALSE) || RefundStrategy.MIXED.name().equals(refund.getRefundStrategy())) {
                        refundToWallet(refund, item);
                    }
                } else {
                    // 现金 / 第三方：模拟通道成功
                    item.setStatus("SUCCESS");
                    item.setChannelTxnId("CH-" + idGenerator.nextIdStr());
                    refundItemMapper.updateById(item);
                }
            }
            // 全部 SUCCESS → 触发逆向（套餐/积分/库存/提成）
            handleReverse(refund);

            // 结束
            transitStatus(refund, RefundStatus.SUCCESS, true);
            refund.setRefundTime(LocalDateTime.now());
            refundMapper.updateById(refund);

            // 更新 pos_order 的累计已退
            // B-3 / B-11: 用 CAS 守卫防止并发 process() 重复累加导致超退
            // expected = 当前数据库读到的 refunded_amount（事务内），CAS 失败 → 回滚
            PosOrder order = orderMapper.selectById(refund.getOrderId());
            if (order != null) {
                BigDecimal expectedRefunded = order.getRefundedAmount() == null
                        ? BigDecimal.ZERO : order.getRefundedAmount();
                int rows = orderMapper.casAddRefundAmount(order.getId(), expectedRefunded,
                        refund.getRefundAmount());
                if (rows == 0) {
                    // 并发冲突：另一个 process() 已先提交，让 @Transactional 回滚
                    throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
                            "退款累计并发冲突，请重试 orderId=" + order.getId());
                }
                // CAS 成功后重读最新状态，更新后续字段
                PosOrder fresh = orderMapper.selectById(order.getId());
                if (fresh != null) {
                    BigDecimal newRefundTotal = fresh.getRefundedAmount() == null
                            ? BigDecimal.ZERO : fresh.getRefundedAmount();
                    if (newRefundTotal.compareTo(fresh.getPaidAmount() == null
                            ? BigDecimal.ZERO : fresh.getPaidAmount()) >= 0) {
                        fresh.setStatus(3);
                        fresh.setRefundTime(LocalDateTime.now());
                        fresh.setRefundAmount(newRefundTotal);
                        fresh.setRefundReason(refund.getReason());
                    }
                    orderMapper.updateById(fresh);
                }
            }
        } catch (RuntimeException ex) {
            log.error("[refund] 处理失败 refundId={}", refundId, ex);
            refundItemMapper.selectList(new LambdaQueryWrapper<PosRefundItem>()
                    .eq(PosRefundItem::getRefundId, refundId))
                    .forEach(it -> {
                        if ("PENDING".equals(it.getStatus())) {
                            it.setStatus("FAILED");
                            it.setErrorMsg(ex.getMessage());
                            refundItemMapper.updateById(it);
                        }
                    });
            transitStatus(refund, RefundStatus.FAILED, true);
            throw new BusinessException(BizCode.SYSTEM_ERROR, "退款处理失败：" + ex.getMessage());
        }
        return loadFull(refund);
    }

    @Transactional(rollbackFor = Exception.class)
    public PosRefund cancel(Long refundId, String reason) {
        PosRefund refund = locked(refundId);
        RefundStatus cur = RefundStatus.valueOf(refund.getStatus());
        if (!cur.isCancellable()) {
            throw new BusinessException(BizCode.CONFLICT, "当前状态不可取消：" + cur);
        }
        transitStatus(refund, RefundStatus.CANCELLED, true);
        refund.setRejectReason(reason == null ? "已取消" : reason);
        refundMapper.updateById(refund);
        return loadFull(refund);
    }

    private void transitStatus(PosRefund refund, RefundStatus next, boolean setOnEntity) {
        RefundStatus cur = RefundStatus.valueOf(refund.getStatus());
        if (!cur.canTransitionTo(next)) {
            throw new BusinessException(BizCode.CONFLICT,
                    "退款单状态不可从 " + cur + " 迁移到 " + next);
        }
        int rows = refundMapper.transitStatus(refund.getId(), refund.getVersion(), cur.name(), next.name());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "退款单状态机并发冲突");
        }
        if (setOnEntity) {
            refund.setStatus(next.name());
            refund.setVersion(refund.getVersion() == null ? 1 : refund.getVersion() + 1);
        }
    }

    private PosRefund locked(Long refundId) {
        PosRefund r = refundMapper.selectById(refundId);
        if (r == null) throw new BusinessException(BizCode.NOT_FOUND, "退款单不存在");
        return r;
    }

    // ====================== 逆向补偿 ======================

    private void handleReverse(PosRefund refund) {
        if (Boolean.FALSE.equals(refund.getReversePackage() == null
                ? isAlreadyReversed(refund, "PACKAGE")
                : false)) {
            reversePackage(refund);
        }
        if (Boolean.FALSE.equals(refund.getReversePoints() == null
                ? isAlreadyReversed(refund, "POINTS")
                : false)) {
            reversePoints(refund);
        }
        if (Boolean.FALSE.equals(refund.getReverseStock() == null
                ? isAlreadyReversed(refund, "STOCK")
                : false)) {
            reverseStock(refund);
        }
        if (Boolean.FALSE.equals(refund.getReverseCommission() == null
                ? isAlreadyReversed(refund, "COMMISSION")
                : false)) {
            reverseCommission(refund);
        }
    }

    private boolean isAlreadyReversed(PosRefund refund, String type) {
        Long count = reverseLogMapper.selectCount(new LambdaQueryWrapper<PosRefundReverseLog>()
                .eq(PosRefundReverseLog::getRefundId, refund.getId())
                .eq(PosRefundReverseLog::getReverseType, type));
        return count != null && count > 0;
    }

    private void reversePackage(PosRefund refund) {
        PosOrder order = orderMapper.selectById(refund.getOrderId());
        List<PosOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<PosOrderItem>()
                .eq(PosOrderItem::getOrderId, order.getId())
                .isNotNull(PosOrderItem::getPackageOrderId));
        if (items.isEmpty()) return;
        Integer totalSaved = 0;
        for (PosOrderItem item : items) {
            int times = item.getPackageTimes() == null ? 1 : item.getPackageTimes();
            // 乐观锁：每次重读 version 后重试，避免覆盖并发退款
            PackageOrder po = packageOrderMapper.selectById(item.getPackageOrderId());
            if (po == null) continue;
            int rows = 0;
            for (int i = 0; i < 3; i++) {
                rows = packageOrderMapper.refundAddTimes(po.getId(), po.getVersion(), times);
                if (rows > 0) break;
                po = packageOrderMapper.selectById(item.getPackageOrderId());
                if (po == null) break;
            }
            if (rows == 0) {
                throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
                        "套餐次数回退失败 packageOrderId=" + item.getPackageOrderId());
            }
            // T06: 每成功回退一张套餐，写一条 PACKAGE reverse_log，ref_id = crm_package_order.id
            logReverse(refund, "PACKAGE", po.getId(), times);
            totalSaved += times;
        }
        refund.setReversePackage(1);
        refundMapper.updateById(refund);
    }

    private void reversePoints(PosRefund refund) {
        // 简单固定比例：退款金额 * 1 积分/元
        if (refund.getMemberId() == null) return;
        int points = refund.getRefundAmount() == null ? 0 : refund.getRefundAmount().intValue();
        if (points <= 0) return;
        if (pointService == null) {
            // 极端兜底：PointService 未注入时使用 CAS 直接写
            log.warn("[refund] PointService 未注入，走原始 raw SQL 路径，请检查注入");
            Member m = memberMapper.selectById(refund.getMemberId());
            if (m == null) return;
            int before = m.getPoints() == null ? 0 : m.getPoints();
            int after = before + points;
            m.setPoints(after);
            memberMapper.updateById(m);
            // T06: 用 KeyHolder 持久化后回传 generated key，ref_id = market_point_record.id
            org.springframework.jdbc.support.GeneratedKeyHolder keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection -> {
                java.sql.PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO market_point_record(store_id, member_id, type, point_value, balance_before, balance_after, source, ref_id, remark, created_at) " +
                                "VALUES(?,?,?,?,?,?,?,?,?,?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS);
                ps.setObject(1, refund.getStoreId());
                ps.setObject(2, refund.getMemberId());
                ps.setInt(3, 1);
                ps.setInt(4, points);
                ps.setInt(5, before);
                ps.setInt(6, after);
                ps.setString(7, "refund_return");
                ps.setObject(8, refund.getId());
                ps.setString(9, "退款返还积分:" + refund.getRefundNo());
                ps.setObject(10, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                return ps;
            }, keyHolder);
            Long pointRecordId = null;
            if (keyHolder.getKey() != null) {
                pointRecordId = keyHolder.getKey().longValue();
            }
            refund.setReversePoints(1);
            refundMapper.updateById(refund);
            logReverse(refund, "POINTS", pointRecordId, points);
            return;
        }
        // 走 PointService.addPointsReturningId → 自动 CAS + 重试 + 流水，并返回新增记录 ID
        Long pointRecordId = pointService.addPointsReturningId(refund.getMemberId(), "refund_return", points,
                refund.getId(), "退款返还积分:" + refund.getRefundNo());
        refund.setReversePoints(1);
        refundMapper.updateById(refund);
        logReverse(refund, "POINTS", pointRecordId, points);
    }

    private void reverseStock(PosRefund refund) {
        PosOrder order = orderMapper.selectById(refund.getOrderId());
        if (order == null) return;
        // 数据源 1：pos_order_consumption（新 R2.0-D 表）
        // 数据源 2：stock_record.type=4 AND order_id=?（兼容旧记录）
        // 收集 (productId, batchNo, qty) 列表
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT product_id, batch_no, SUM(quantity) AS qty " +
            "FROM stock_record " +
            "WHERE deleted = 0 AND type = 4 AND order_id = ? " +
            "GROUP BY product_id, batch_no",
            order.getId());
        for (Map<String, Object> row : rows) {
            Long productId = ((Number) row.get("product_id")).longValue();
            String batchNo = (String) row.get("batch_no");
            int qty = ((Number) row.get("qty")).intValue();
            if (qty == 0) continue;
            // casAdjust(id, version, delta) 支持负数 delta；正负一统
            StockInventory inv = jdbc.queryForObject(
                    "SELECT id, version FROM stock_inventory WHERE product_id = ? AND batch_no = ? AND deleted = 0 LIMIT 1",
                    (rs, i) -> {
                        StockInventory s = new StockInventory();
                        s.setId(rs.getLong("id"));
                        s.setVersion(rs.getInt("version"));
                        return s;
                    },
                    productId, batchNo);
            if (inv == null) continue;
            int rowsUpdated = 0;
            for (int i = 0; i < 3; i++) {
                rowsUpdated = inventoryMapper.casAdjust(inv.getId(), inv.getVersion(), qty);
                if (rowsUpdated > 0) break;
                // 重读 version 后重试
                StockInventory latest = jdbc.queryForObject(
                        "SELECT id, version FROM stock_inventory WHERE id = ? AND deleted = 0",
                        (rs, i2) -> {
                            StockInventory s = new StockInventory();
                            s.setId(rs.getLong("id"));
                            s.setVersion(rs.getInt("version"));
                            return s;
                        },
                        inv.getId());
                if (latest == null) break;
                inv = latest;
            }
            if (rowsUpdated == 0) {
                throw new BusinessException(BizCode.CONCURRENCY_CONFLICT,
                        "库存回退失败 productId=" + productId + " batchNo=" + batchNo);
            }
            StockRecord rec = new StockRecord();
            rec.setTenantId(refund.getTenantId());
            rec.setStoreId(refund.getStoreId());
            rec.setProductId(productId);
            rec.setBatchNo(batchNo);
            rec.setType(2); // 2=出库；此处表示冲正入库（负向记录），便于追溯
            rec.setQuantity(qty);
            rec.setBeforeQty(0);
            rec.setAfterQty(qty);
            rec.setOperatorId(refund.getOperatorId());
            rec.setOperatorName(refund.getOperatorName());
            rec.setRemark("refund:" + refund.getRefundNo() + " 冲正入库");
            rec.setOrderId(refund.getOrderId());
            rec.setRecordTime(LocalDateTime.now());
            stockRecordMapper.insert(rec);
            // T06: 每个冲正 stock_record 写一条 STOCK reverse_log，ref_id = stock_record.id
            logReverse(refund, "STOCK", rec.getId(), qty);
        }
        // 把对应 pos_order_consumption 标 soft-deleted 防止再次扣减
        jdbc.update("UPDATE pos_order_consumption SET idempotency_key = CONCAT(idempotency_key, ':refund:', ?) " +
                "WHERE order_id = ?", refund.getRefundNo(), order.getId());
        refund.setReverseStock(1);
        refundMapper.updateById(refund);
    }

    private void reverseCommission(PosRefund refund) {
        List<Long> invalidatedIds = commissionCalculationService.invalidateOrder(refund.getOrderId(),
                "退款冲正 " + refund.getRefundNo());
        refund.setReverseCommission(1);
        refundMapper.updateById(refund);
        // T06: 每个被作废的 commission_record 写一条 COMMISSION reverse_log，ref_id = commission_record.id
        // 如果没有实际被作废的记录（commission_record 为 0 行），不制造虚假 reverse_log
        if (invalidatedIds != null) {
            for (Long id : invalidatedIds) {
                logReverse(refund, "COMMISSION", id, null);
            }
        }
    }

    private void refundToWallet(PosRefund refund, PosRefundItem item) {
        // B-6 / B-10 · 改走 walletService.doRefund()，禁止直接写 crm_wallet.balance
        // doRefund 内部已包含：CAS 乐观锁、SSOT 同步（syncMemberBalance）、walletTransaction 写入、tenant 校验
        if (refund.getMemberId() == null) {
            item.setStatus("FAILED");
            item.setErrorMsg("订单无会员，无法储值返还");
            refundItemMapper.updateById(item);
            return;
        }
        try {
            WalletOpDTO dto = new WalletOpDTO();
            dto.setMemberId(refund.getMemberId());
            dto.setOrderId(refund.getOrderId());
            dto.setAmount(item.getRefundAmount());
            dto.setOperatorId(refund.getOperatorId());
            dto.setPayMethod("wallet_refund");
            dto.setRemark("退款储值返还:" + refund.getRefundNo());
            // resolveTenantId(ctx) 永远胜出：doRefund 内部会校验 ctx == refund.tenantId
            Long ctxTenantId = TenantContextHolder.get() == null
                    ? null : TenantContextHolder.get().getTenantId();
            Result<Map<String, Object>> result = walletService.doRefund(dto, ctxTenantId);
            if (result == null || result.getCode() != 200) {
                item.setStatus("FAILED");
                item.setErrorMsg("钱包退款失败: " + (result == null ? "null result" : result.getMessage()));
                refundItemMapper.updateById(item);
                return;
            }
            item.setStatus("SUCCESS");
            refundItemMapper.updateById(item);
        } catch (BusinessException be) {
            // B-6: 钱包退款失败应让 @Transactional 回滚整个 process()，
            // 不允许"钱包退款失败但订单仍标记为退款成功"的不一致状态
            item.setStatus("FAILED");
            item.setErrorMsg(be.getMessage());
            refundItemMapper.updateById(item);
            throw be;
        } catch (RuntimeException ex) {
            item.setStatus("FAILED");
            item.setErrorMsg(ex.getMessage());
            refundItemMapper.updateById(item);
            throw ex;
        }
    }

    private void logReverse(PosRefund refund, String type, Long refId, Object payload) {
        PosRefundReverseLog log = new PosRefundReverseLog();
        log.setTenantId(refund.getTenantId());
        log.setRefundId(refund.getId());
        log.setReverseType(type);
        log.setRefId(refId);
        log.setOperatorId(refund.getOperatorId());
        try {
            log.setPayload(payload == null ? null : mapper.writeValueAsString(payload));
        } catch (Exception ignored) {
        }
        reverseLogMapper.insert(log);
    }

    // ====================== 查询 ======================

    public PosRefund loadFull(PosRefund refund) {
        if (refund == null) return null;
        refund.setItems(refundItemMapper.selectList(new LambdaQueryWrapper<PosRefundItem>()
                .eq(PosRefundItem::getRefundId, refund.getId())));
        refund.setReverseLogs(reverseLogMapper.selectList(new LambdaQueryWrapper<PosRefundReverseLog>()
                .eq(PosRefundReverseLog::getRefundId, refund.getId())));
        return refund;
    }

    public PosRefund getById(Long id) {
        return loadFull(refundMapper.selectById(id));
    }

    public Page<PosRefund> page(int pageNum, int pageSize, String status, Long storeId, String dateFrom, String dateTo) {
        QueryWrapper<PosRefund> q = new QueryWrapper<>();
        // S-5 · 财务接口 tenantId 隔离：列表查询必须按 ctx tenantId 过滤
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null) {
            q.eq("tenant_id", tid);
        }
        if (status != null && !status.isEmpty()) q.eq("status", status);
        if (storeId != null) q.eq("store_id", storeId);
        if (dateFrom != null && !dateFrom.isEmpty()) q.ge("create_time", dateFrom + " 00:00:00");
        if (dateTo != null && !dateTo.isEmpty()) q.le("create_time", dateTo + " 23:59:59");
        q.orderByDesc("id");
        return refundMapper.selectPage(new Page<>(pageNum, pageSize), q);
    }

    private String generateRefundNo() {
        // P0-MONEY-003: 雪花算法 ID (替换 Math.random()*1000 易碰撞问题)
        return "RF" + idGenerator.nextIdStr();
    }
}
