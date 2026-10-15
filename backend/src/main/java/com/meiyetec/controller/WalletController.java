package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.Member;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.Wallet;
import com.meiyetec.entity.WalletPromotion;
import com.meiyetec.entity.WalletTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.WalletMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.IdempotencyService;
import com.meiyetec.service.MemberService;
import com.meiyetec.service.WalletPromotionService;
import com.meiyetec.service.WalletService;
import com.meiyetec.service.WalletTransactionService;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
@SysOpLog(module = "钱包")
public class WalletController {

    private final WalletService walletService;
    private final WalletTransactionService txnService;
    private final MemberService memberService;
    private final WalletMapper walletMapper;
    private final IdempotencyService idem;
    private final WalletPromotionService promotionService;

    @GetMapping("/balance/{memberId}")
    @RequirePermission("wallet:list")
    public Result<Map<String, Object>> getBalanceByAlias(@PathVariable Long memberId) {
        return getBalance(memberId);
    }

    @GetMapping("/{memberId}")
    @RequirePermission("wallet:list")
    public Result<Map<String, Object>> getBalance(@PathVariable Long memberId) {
        Member member = memberService.getById(memberId);
        if (member == null) return Result.error(BizCode.MEMBER_NOT_FOUND);
        // S-5: 财务接口 tenant 隔离 — member 必须归属当前租户
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null && member.getTenantId() != null && !tid.equals(member.getTenantId())) {
            throw new BusinessException(BizCode.TENANT_DENIED, "无权访问该会员钱包（跨租户）");
        }

        Wallet wallet = ensureWallet(memberId);
        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("name", member.getName());
        data.put("phone", member.getPhone());
        data.put("balance", wallet.getBalance());
        data.put("totalRecharge", wallet.getTotalRecharge());
        data.put("totalConsume", wallet.getTotalConsume());
        data.put("frozenAmount", wallet.getFrozenAmount());
        data.put("version", wallet.getVersion());
        return Result.success(data);
    }

    @PostMapping("/recharge")
    @RequirePermission("wallet:recharge")
    public Result<Map<String, Object>> recharge(@Valid @RequestBody WalletOpDTO dto,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "wallet.recharge", key, dto, () -> walletService.doRecharge(dto, tenantId));
        return r.result;
    }

    @PostMapping("/consume")
    @RequirePermission("wallet:consume")
    public Result<Map<String, Object>> consume(@Valid @RequestBody WalletOpDTO dto,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "wallet.consume", key, dto, () -> walletService.doConsume(dto, tenantId));
        return r.result;
    }

    @PostMapping("/refund")
    @RequirePermission("wallet:refund")
    public Result<Map<String, Object>> refund(@Valid @RequestBody WalletOpDTO dto,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "wallet.refund", key, dto, () -> walletService.doRefund(dto, tenantId));
        return r.result;
    }

    @PostMapping("/freeze")
    @RequirePermission("wallet:freeze")
    public Result<Map<String, Object>> freeze(@Valid @RequestBody WalletOpDTO dto,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "wallet.freeze", key, dto, () -> walletService.doFreeze(dto, tenantId));
        return r.result;
    }

    @PostMapping("/unfreeze")
    @RequirePermission("wallet:freeze")
    public Result<Map<String, Object>> unfreeze(@Valid @RequestBody WalletOpDTO dto,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String key = idemKey == null || idemKey.isEmpty() ? null : idemKey;
        IdempotencyService.IdempotencyResult<Map<String, Object>> r = idem.execute(
                "wallet.unfreeze", key, dto, () -> walletService.doUnfreeze(dto, tenantId));
        return r.result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doRecharge(WalletOpDTO dto, Long tenantId) {
        return walletService.doRecharge(dto, tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doConsume(WalletOpDTO dto, Long tenantId) {
        return walletService.doConsume(dto, tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doRefund(WalletOpDTO dto, Long tenantId) {
        return walletService.doRefund(dto, tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doFreeze(WalletOpDTO dto, Long tenantId) {
        return walletService.doFreeze(dto, tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doUnfreeze(WalletOpDTO dto, Long tenantId) {
        return walletService.doUnfreeze(dto, tenantId);
    }

    // === 先注册具体路径，避免被 /{memberId} 兜底捕获 ===

    @GetMapping("/transactions")
    @RequirePermission("wallet:list")
    public Result<PageResult<WalletTransaction>> transactionsByQuery(
            @RequestParam Long memberId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer type) {
        return transactions(memberId, pageNum, pageSize, type);
    }

    @GetMapping("/{memberId}/transactions")
    @RequirePermission("wallet:list")
    public Result<PageResult<WalletTransaction>> transactions(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer type) {
        Member member = memberService.getById(memberId);
        if (member == null) {
            throw new BusinessException(BizCode.MEMBER_NOT_FOUND);
        }
        // S-5: 财务接口 tenant 隔离 — member 必须归属当前租户
        Long tid = TenantContextHolder.getTenantIdOrNull();
        if (tid != null && member.getTenantId() != null && !tid.equals(member.getTenantId())) {
            throw new BusinessException(BizCode.TENANT_DENIED, "无权访问该会员钱包流水（跨租户）");
        }
        Page<WalletTransaction> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WalletTransaction::getMemberId, memberId);
        if (tid != null) wrapper.eq(WalletTransaction::getTenantId, tid);
        if (type != null) wrapper.eq(WalletTransaction::getType, type);
        wrapper.orderByDesc(WalletTransaction::getId);
        Page<WalletTransaction> result = txnService.page(page, wrapper);

        result.getRecords().forEach(t -> {
            t.setTypeLabel(typeLabelOf(t.getType()));
            if (t.getMemberId() != null) {
                Member m = memberService.getById(t.getMemberId());
                if (m != null) t.setMemberName(m.getName());
            }
        });
        if (result.getTotal() == 0L && result.getRecords() != null && !result.getRecords().isEmpty()) {
            long realTotal = txnService.count(wrapper);
            result.setTotal(realTotal);
            result.setPages((realTotal + pageSize - 1) / pageSize);
        }
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/transactions/all")
    @RequirePermission("wallet:list")
    public Result<PageResult<WalletTransaction>> allTransactions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer type) {
        Page<WalletTransaction> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WalletTransaction> wrapper = new LambdaQueryWrapper<>();
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId != null) wrapper.eq(WalletTransaction::getTenantId, tenantId);
        if (type != null) wrapper.eq(WalletTransaction::getType, type);
        wrapper.orderByDesc(WalletTransaction::getId);
        Page<WalletTransaction> result = txnService.page(page, wrapper);

        result.getRecords().forEach(t -> {
            t.setTypeLabel(typeLabelOf(t.getType()));
            if (t.getMemberId() != null) {
                Member m = memberService.getById(t.getMemberId());
                if (m != null) t.setMemberName(m.getName());
            }
        });
        if (result.getTotal() == 0L && result.getRecords() != null && !result.getRecords().isEmpty()) {
            long realTotal = txnService.count(wrapper);
            result.setTotal(realTotal);
            result.setPages((realTotal + pageSize - 1) / pageSize);
        }
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/promotion/rules")
    public Result<List<Map<String, Object>>> promotionRules() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WalletPromotion> w = new LambdaQueryWrapper<>();
        w.eq(WalletPromotion::getStatus, 1);
        // 优先租户自定义规则，其次全局规则（tenantId IS NULL）
        if (tenantId != null) {
            w.and(q -> q.eq(WalletPromotion::getTenantId, tenantId)
                    .or().isNull(WalletPromotion::getTenantId));
        } else {
            w.isNull(WalletPromotion::getTenantId);
        }
        w.and(q -> q.isNull(WalletPromotion::getStartTime)
                .or().le(WalletPromotion::getStartTime, now));
        w.and(q -> q.isNull(WalletPromotion::getEndTime)
                .or().ge(WalletPromotion::getEndTime, now));
        w.orderByAsc(WalletPromotion::getSortOrder);
        List<WalletPromotion> promos = promotionService.list(w);
        List<Map<String, Object>> out = new ArrayList<>(promos.size());
        for (WalletPromotion p : promos) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("rule", p.getRuleName());
            m.put("recharge", p.getRechargeAmount());
            m.put("gift", p.getGiftAmount());
            m.put("startTime", p.getStartTime());
            m.put("endTime", p.getEndTime());
            out.add(m);
        }
        return Result.success(out);
    }

    @GetMapping("/promotion/calc")
    public Result<Map<String, Object>> calcPromotion(@RequestParam BigDecimal amount) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WalletPromotion> w = new LambdaQueryWrapper<>();
        w.eq(WalletPromotion::getStatus, 1);
        if (tenantId != null) {
            w.and(q -> q.eq(WalletPromotion::getTenantId, tenantId)
                    .or().isNull(WalletPromotion::getTenantId));
        } else {
            w.isNull(WalletPromotion::getTenantId);
        }
        // P0-M05: 限时活动时间窗口
        w.and(q -> q.isNull(WalletPromotion::getStartTime)
                .or().le(WalletPromotion::getStartTime, now));
        w.and(q -> q.isNull(WalletPromotion::getEndTime)
                .or().ge(WalletPromotion::getEndTime, now));
        w.le(WalletPromotion::getRechargeAmount, amount);
        w.orderByDesc(WalletPromotion::getRechargeAmount);
        w.last("LIMIT 1");
        WalletPromotion matched = promotionService.getOne(w);

        BigDecimal gift = matched == null ? BigDecimal.ZERO : matched.getGiftAmount();
        String rule = matched == null ? "无赠送" : matched.getRuleName();
        Map<String, Object> r = new HashMap<>();
        r.put("recharge", amount);
        r.put("gift", gift);
        r.put("rule", rule);
        r.put("total", amount.add(gift));
        r.put("matchedPromotionId", matched == null ? null : matched.getId());
        r.put("matchedStartTime", matched == null ? null : matched.getStartTime());
        r.put("matchedEndTime", matched == null ? null : matched.getEndTime());
        r.put("activeNow", matched != null);
        return Result.success(r);
    }

    // ===== 内部辅助 =====

    private Wallet ensureWallet(Long memberId) {
        Wallet w = walletService.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        if (w == null) {
            Long tenantId = TenantContextHolder.getTenantIdOrNull();
            if (tenantId == null) {
                throw new BusinessException(BizCode.BAD_REQUEST,
                        "创建钱包需要 tenantId，但当前会话无 ctx（TenantContextHolder 为空）");
            }
            walletMapper.insertIfAbsent(memberId, tenantId);
            w = walletService.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        }
        return w;
    }

    private String typeLabelOf(Integer type) {
        if (type == null) return "";
        return switch (type) {
            case 1 -> "充值";
            case 2 -> "消费";
            case 3 -> "退款";
            case 4 -> "赠送";
            case 5 -> "冻结";
            case 6 -> "解冻";
            default -> "其它";
        };
    }
}
