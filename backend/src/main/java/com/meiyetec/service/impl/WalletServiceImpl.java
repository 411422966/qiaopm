package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.Member;
import com.meiyetec.entity.Wallet;
import com.meiyetec.entity.WalletPromotion;
import com.meiyetec.entity.WalletTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.WalletMapper;
import com.meiyetec.service.MemberService;
import com.meiyetec.service.WalletPromotionService;
import com.meiyetec.service.WalletService;
import com.meiyetec.service.WalletTransactionService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.meiyetec.security.TenantContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * P0-MONEY-002 · 钱包服务实现
 *
 * <p>SSOT 收敛：钱包的 3 个写操作（recharge/consume/refund）全部在此处实现，
 * <p>统一走 CAS 乐观锁 + 幂等键 + 唯一同步入口 syncMemberBalance。
 *
 * <p>旧 WalletController.doRecharge/doConsume/doRefund 已被 thin wrapper 替换为调用此处。
 */
@Slf4j
@Service
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements WalletService {

    // P0-TEST-001: 自注入 Bean 用于走 Spring AOP 代理（解决 self-call 事务失效问题）
    // 使用 @Lazy 打破循环依赖；必须 setter 注入（非 final + 非构造器）
    private WalletServiceImpl self;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    public void setSelf(WalletServiceImpl self) {
        this.self = self;
    }

    private final WalletMapper walletMapper;
    private final WalletTransactionService txnService;
    private final MemberService memberService;
    private final WalletPromotionService promotionService;
    private final com.meiyetec.mkt.event.MarketingEventPublisher marketingEventPublisher;

    public WalletServiceImpl(WalletMapper walletMapper,
                             WalletTransactionService txnService,
                             MemberService memberService,
                             WalletPromotionService promotionService,
                             com.meiyetec.mkt.event.MarketingEventPublisher marketingEventPublisher) {
        this.walletMapper = walletMapper;
        this.txnService = txnService;
        this.memberService = memberService;
        this.promotionService = promotionService;
        this.marketingEventPublisher = marketingEventPublisher;
    }

    private static final int TYPE_RECHARGE = 1;
    private static final int TYPE_CONSUME  = 2;
    private static final int TYPE_REFUND   = 3;
    /**
     * P0-TEST-001：CAS 重试次数从 3 提升到 100。
     * <p>50 并发场景下，最大失败概率 = (49/50)^3 ≈ 6%，50 并发下大部分线程会在 3 次内成功；
     * <p>但极端场景（100+ 并发）下 3 次不够。100 次重试可覆盖 99.99% 场景。
     */
    private static final int MAX_OPTIMISTIC_RETRY = 100;

    /**
     * P0-TEST-001 · 退款幂等并发锁：按 orderId 加锁（单实例内存锁）
     *
     * <p>解决 TOCTOU race：两个并发 doRefund 在 @Transactional 之外可能都看到 existing=0。
     * <p>在 Service 入口处对 orderId 加锁，让"幂等检查 + 钱包 CAS + 写流水"三步在锁内完成。
     * <p>注：分布式场景需升级为 Redisson 或 DB SELECT FOR UPDATE；当前 SaaS 单实例部署够用。
     */
    private final ConcurrentHashMap<String, ReentrantLock> refundLocks = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doRecharge(WalletOpDTO dto, Long tenantId) {
        // P0-MONEY-004: ctx 中的 tenantId 永远胜出（防止上层伪造）
        tenantId = resolveTenantId(tenantId);
        if (memberService.getById(dto.getMemberId()) == null) {
            throw new BusinessException(BizCode.MEMBER_NOT_FOUND);
        }
        // P0-M05: 若客户端未指定 giftAmount，自动按"当前生效"的限时活动叠加赠送金额
        // 优先级：客户端显式 giftAmount > 当前时间窗口内最高档赠送
        BigDecimal gift;
        if (dto.getGiftAmount() != null) {
            gift = dto.getGiftAmount();
        } else {
            gift = lookupActivePromotionGift(dto.getAmount(), tenantId);
        }
        if (gift.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "赠送金额不能为负");
        }
        BigDecimal add = dto.getAmount().add(gift);
        if (add.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "充值金额必须大于 0");
        }

        Wallet wallet = ensureWallet(dto.getMemberId());
        BigDecimal before = wallet.getBalance();
        int rows = casUpdate(dto.getMemberId(), add, dto.getAmount(), BigDecimal.ZERO, wallet.getVersion());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "钱包并发更新失败");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(dto.getMemberId());
        txn.setType(TYPE_RECHARGE);
        txn.setAmount(dto.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(before.add(add));
        txn.setGiftAmount(gift);
        txn.setPayMethod(dto.getPayMethod() == null ? "现金" : dto.getPayMethod());
        txn.setOperatorId(dto.getOperatorId());
        txn.setRemark(dto.getRemark());
        txnService.save(txn);

        // P0-MONEY-002：crm_member.balance 由 syncMemberBalance 唯一入口同步
        syncMemberBalance(dto.getMemberId());

        // ===== M16 营销事件发布：储值充值 =====
        try {
            com.meiyetec.entity.Member m = memberService.getById(dto.getMemberId());
            String memberName = m != null ? m.getName() : null;
            marketingEventPublisher.recharge(
                    tenantId, dto.getMemberId(), memberName,
                    "TXN" + txn.getId(), add);
        } catch (Exception ex) {
            log.warn("[M16-Event] recharge 发布失败: memberId={} err={}", dto.getMemberId(), ex.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", dto.getMemberId());
        data.put("rechargeAmount", dto.getAmount());
        data.put("giftAmount", gift);
        data.put("totalAdded", add);
        data.put("balanceBefore", before);
        data.put("balanceAfter", before.add(add));
        data.put("txnId", txn.getId());
        return Result.success("充值成功", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doConsume(WalletOpDTO dto, Long tenantId) {
        // P0-MONEY-004: ctx 中的 tenantId 永远胜出（防止上层伪造）
        tenantId = resolveTenantId(tenantId);
        Member member = memberService.getById(dto.getMemberId());
        if (member == null) throw new BusinessException(BizCode.MEMBER_NOT_FOUND);
        if (member.getStatus() != null && member.getStatus() != 1) {
            throw new BusinessException(BizCode.MEMBER_FROZEN, "会员已冻结，不可消费");
        }
        Wallet wallet = ensureWallet(dto.getMemberId());
        // P0-M05: 可用余额 = balance - frozenAmount
        BigDecimal frozen = wallet.getFrozenAmount() == null ? BigDecimal.ZERO : wallet.getFrozenAmount();
        BigDecimal available = wallet.getBalance().subtract(frozen);
        if (available.compareTo(dto.getAmount()) < 0) {
            throw new BusinessException(BizCode.WALLET_BALANCE_NOT_ENOUGH,
                    "余额不足，可用余额: " + available + " (总额=" + wallet.getBalance()
                            + ", 冻结=" + frozen + ")");
        }
        BigDecimal before = wallet.getBalance();

        int rows = casUpdate(dto.getMemberId(), dto.getAmount().negate(), BigDecimal.ZERO,
                dto.getAmount(), wallet.getVersion());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "钱包并发更新失败");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(dto.getMemberId());
        txn.setType(TYPE_CONSUME);
        txn.setAmount(dto.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(before.subtract(dto.getAmount()));
        txn.setOrderId(dto.getOrderId());
        txn.setOperatorId(dto.getOperatorId());
        txn.setRemark(dto.getRemark() == null ? "消费扣款" : dto.getRemark());
        txnService.save(txn);

        // 同步 crm_member.totalConsume (会员累计消费，仅消费时累加)
        BigDecimal consume = member.getTotalConsume() == null ? BigDecimal.ZERO : member.getTotalConsume();
        member.setTotalConsume(consume.add(dto.getAmount()));
        memberService.updateById(member);

        // P0-MONEY-002：crm_member.balance 由 syncMemberBalance 唯一入口同步
        syncMemberBalance(dto.getMemberId());

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", dto.getMemberId());
        data.put("consumeAmount", dto.getAmount());
        data.put("balanceBefore", before);
        data.put("balanceAfter", before.subtract(dto.getAmount()));
        data.put("txnId", txn.getId());
        return Result.success("扣款成功", data);
    }

    @Override
    public Result<Map<String, Object>> doRefund(WalletOpDTO dto, Long tenantId) {
        // P0-MONEY-004: ctx 中的 tenantId 永远胜出（防止上层伪造）
        tenantId = resolveTenantId(tenantId);
        if (dto.getOrderId() == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "退款必须提供原订单ID (orderId)");
        }
        if (memberService.getById(dto.getMemberId()) == null) {
            throw new BusinessException(BizCode.MEMBER_NOT_FOUND);
        }

        // P0-TEST-001 · 按 orderId 加内存锁，把幂等检查 + CAS + 写流水三步串行化
        // 锁必须包裹整个事务（事务提交后才释放），避免其他线程读到未提交数据导致重复退款
        String key = String.valueOf(dto.getOrderId());
        ReentrantLock lock = refundLocks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            // 锁内调 self.doRefundInTx() 走 Spring AOP 代理，开启新事务
            return self.doRefundInTx(dto, tenantId);
        } finally {
            lock.unlock();
            // 清理已无竞争的锁，避免内存泄漏
            if (!lock.hasQueuedThreads()) {
                refundLocks.remove(key, lock);
            }
        }
    }

    /**
     * P0-TEST-001 · 事务内的退款逻辑（独立 Bean 方法，由 AOP 代理开启事务）。
     *
     * <p>包内可见 + 通过 self() 获取代理 Bean，绕过 Spring 自调用失效问题。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doRefundInTx(WalletOpDTO dto, Long tenantId) {
        // 幂等校验：同一订单只能退一次（锁内检查）
        Long existing = txnService.count(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getOrderId, dto.getOrderId())
                        .eq(WalletTransaction::getType, TYPE_REFUND));
        if (existing != null && existing > 0) {
            throw new BusinessException(BizCode.REFUND_EXCEED, "该订单已退款，不能重复操作");
        }
        Wallet wallet = ensureWallet(dto.getMemberId());
        BigDecimal before = wallet.getBalance();

        int rows = casUpdate(dto.getMemberId(), dto.getAmount(), BigDecimal.ZERO, BigDecimal.ZERO,
                wallet.getVersion());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "钱包并发更新失败");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(dto.getMemberId());
        txn.setType(TYPE_REFUND);
        txn.setAmount(dto.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(before.add(dto.getAmount()));
        txn.setPayMethod(dto.getPayMethod() == null ? "原路返回" : dto.getPayMethod());
        txn.setOrderId(dto.getOrderId());
        txn.setOperatorId(dto.getOperatorId());
        txn.setRemark(dto.getRemark() == null ? "退款原路返回" : dto.getRemark());
        txnService.save(txn);

        // 同步 crm_member.totalConsume (退款时扣减)
        Member m = memberService.getById(dto.getMemberId());
        BigDecimal consume = m.getTotalConsume() == null ? BigDecimal.ZERO : m.getTotalConsume();
        m.setTotalConsume(consume.subtract(dto.getAmount()).compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO : consume.subtract(dto.getAmount()));
        memberService.updateById(m);

        // P0-MONEY-002：crm_member.balance 由 syncMemberBalance 唯一入口同步
        syncMemberBalance(dto.getMemberId());

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", dto.getMemberId());
        data.put("refundAmount", dto.getAmount());
        data.put("balanceBefore", before);
        data.put("balanceAfter", before.add(dto.getAmount()));
        data.put("orderId", dto.getOrderId());
        data.put("txnId", txn.getId());
        return Result.success("退款成功", data);
    }

    // ============================================================
    // P0-M05 余额冻结 / 解冻
    // ============================================================
    private static final int TYPE_FREEZE   = 5;
    private static final int TYPE_UNFREEZE = 6;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doFreeze(WalletOpDTO dto, Long tenantId) {
        tenantId = resolveTenantId(tenantId);
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "冻结金额必须大于 0");
        }
        Member member = memberService.getById(dto.getMemberId());
        if (member == null) throw new BusinessException(BizCode.MEMBER_NOT_FOUND);

        Wallet wallet = ensureWallet(dto.getMemberId());
        BigDecimal available = wallet.getBalance().subtract(
                wallet.getFrozenAmount() == null ? BigDecimal.ZERO : wallet.getFrozenAmount());
        if (dto.getAmount().compareTo(available) > 0) {
            throw new BusinessException(BizCode.WALLET_BALANCE_NOT_ENOUGH,
                    "冻结金额超过可用余额 (balance=" + wallet.getBalance()
                            + ", frozen=" + wallet.getFrozenAmount() + ")");
        }

        int rows = walletMapper.updateFrozenAmount(dto.getMemberId(),
                dto.getAmount(), wallet.getVersion());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "冻结操作并发冲突，请重试");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(dto.getMemberId());
        txn.setType(TYPE_FREEZE);
        txn.setAmount(dto.getAmount());
        txn.setBalanceBefore(wallet.getBalance());
        txn.setBalanceAfter(wallet.getBalance());  // 余额不变
        txn.setOperatorId(dto.getOperatorId());
        txn.setRemark(dto.getRemark() == null ? "余额冻结" : dto.getRemark());
        txnService.save(txn);

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", dto.getMemberId());
        data.put("freezeAmount", dto.getAmount());
        data.put("balance", wallet.getBalance());
        BigDecimal newFrozen = (wallet.getFrozenAmount() == null ? BigDecimal.ZERO : wallet.getFrozenAmount())
                .add(dto.getAmount());
        data.put("frozenAmount", newFrozen);
        data.put("availableBalance", wallet.getBalance().subtract(newFrozen));
        data.put("txnId", txn.getId());
        return Result.success("冻结成功", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doUnfreeze(WalletOpDTO dto, Long tenantId) {
        tenantId = resolveTenantId(tenantId);
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "解冻金额必须大于 0");
        }
        if (memberService.getById(dto.getMemberId()) == null) {
            throw new BusinessException(BizCode.MEMBER_NOT_FOUND);
        }

        Wallet wallet = ensureWallet(dto.getMemberId());
        BigDecimal frozen = wallet.getFrozenAmount() == null ? BigDecimal.ZERO : wallet.getFrozenAmount();
        if (dto.getAmount().compareTo(frozen) > 0) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "解冻金额超过已冻结金额 (frozen=" + frozen + ")");
        }

        int rows = walletMapper.updateFrozenAmount(dto.getMemberId(),
                dto.getAmount().negate(), wallet.getVersion());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "解冻操作并发冲突，请重试");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(dto.getMemberId());
        txn.setType(TYPE_UNFREEZE);
        txn.setAmount(dto.getAmount());
        txn.setBalanceBefore(wallet.getBalance());
        txn.setBalanceAfter(wallet.getBalance());  // 余额不变
        txn.setOperatorId(dto.getOperatorId());
        txn.setRemark(dto.getRemark() == null ? "余额解冻" : dto.getRemark());
        txnService.save(txn);

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", dto.getMemberId());
        data.put("unfreezeAmount", dto.getAmount());
        data.put("balance", wallet.getBalance());
        BigDecimal newFrozen = frozen.subtract(dto.getAmount());
        data.put("frozenAmount", newFrozen);
        data.put("availableBalance", wallet.getBalance().subtract(newFrozen));
        data.put("txnId", txn.getId());
        return Result.success("解冻成功", data);
    }

    @Override
    public void syncMemberBalance(Long memberId) {
        Wallet w = this.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        if (w == null) return;
        Member m = memberService.getById(memberId);
        if (m == null) return;
        if (m.getBalance() == null || m.getBalance().compareTo(w.getBalance()) != 0) {
            m.setBalance(w.getBalance());
            memberService.updateById(m);
            log.info("[WALLET-SSOT] sync crm_member.balance={} from crm_wallet memberId={}",
                    w.getBalance(), memberId);
        }
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    private Wallet ensureWallet(Long memberId) {
        Wallet w = this.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        if (w != null) return w;

        // P0-T03: 创建钱包时必须写入 tenant_id
        Long tenantId = resolveTenantIdForInsert();

        // P0-TEST-001: 50 并发场景下 INSERT IF NOT EXISTS 的 NOT EXISTS 子查询
        // 不持锁，导致多个线程同时判断"不存在"后并发 INSERT → UniqueViolation/Deadlock。
        // 简化：直接 try INSERT（不查 NOT EXISTS），捕获 DuplicateKeyException 后 getOne。
        try {
            walletMapper.insertIfAbsent(memberId, tenantId);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已被其他线程创建，正常情况
        } catch (org.springframework.dao.DeadlockLoserDataAccessException ignore) {
            // 死锁回退：重试一次
            try {
                walletMapper.insertIfAbsent(memberId, tenantId);
            } catch (org.springframework.dao.DuplicateKeyException ignore2) {
                // ok
            }
        }
        // 重新读（其他线程可能刚 INSERT 完还没提交，重试）
        for (int i = 0; i < 10; i++) {
            w = this.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
            if (w != null) return w;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 最终仍拿不到 → 抛错让上层明确失败（不再继续造成 NPE）
        throw new BusinessException(BizCode.SYSTEM_ERROR,
                "钱包初始化失败，memberId=" + memberId);
    }

    private int casUpdate(Long memberId, BigDecimal delta, BigDecimal rechargeDelta,
                          BigDecimal consumeDelta, Integer version) {
        // P0-TEST-001: 50 并发场景下，纯乐观重试不够（CAS 失败概率 = (N-1)/N）。
        // 改为"Pessimistic First"策略：
        //   1) 先 SELECT FOR UPDATE 锁住钱包行（事务内），其他线程阻塞等待
        //   2) 拿到锁后重新读最新 version
        //   3) 单次 updateBalance（version 必须匹配，否则返回 0 = 异常）
        //   4) 事务结束自动释放锁
        Wallet latest = walletMapper.selectByMemberIdForUpdate(memberId);
        if (latest == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "钱包不存在");
        }
        int rows = walletMapper.updateBalance(memberId, delta, rechargeDelta, consumeDelta,
                latest.getVersion());
        if (rows > 0) return rows;
        // rows=0 不一定是冲突，可能是 balance+delta < 0（余额不足）
        if (delta.signum() < 0 && latest.getBalance().add(delta).signum() < 0) {
            throw new BusinessException(BizCode.WALLET_BALANCE_NOT_ENOUGH,
                    "余额不足，当前余额: " + latest.getBalance());
        }
        // 其他原因（version 不一致理论上不可能，因为 FOR UPDATE 已锁行）→ 走一次 CAS 重试
        log.warn("[CAS-PESSIMIST] rows=0 after FOR UPDATE, memberId={}, balance={}, version={}, delta={}",
                memberId, latest.getBalance(), latest.getVersion(), delta);
        return casUpdateRetry(memberId, delta, rechargeDelta, consumeDelta, latest.getVersion());
    }

    /**
     * 纯乐观 CAS 重试（fallback，正常不会走到）
     */
    private int casUpdateRetry(Long memberId, BigDecimal delta, BigDecimal rechargeDelta,
                               BigDecimal consumeDelta, Integer version) {
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < MAX_OPTIMISTIC_RETRY; i++) {
            int rows = walletMapper.updateBalance(memberId, delta, rechargeDelta, consumeDelta, version);
            if (rows > 0) return rows;
            Wallet latest = this.getOne(
                    new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
            if (latest == null) {
                throw new BusinessException(BizCode.NOT_FOUND, "钱包不存在");
            }
            version = latest.getVersion();
            if (delta.signum() < 0 && latest.getBalance().add(delta).signum() < 0) {
                throw new BusinessException(BizCode.WALLET_BALANCE_NOT_ENOUGH,
                        "余额不足，当前余额: " + latest.getBalance());
            }
            try {
                Thread.sleep(1L + rng.nextInt(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(BizCode.SYSTEM_ERROR, "CAS 重试被中断");
            }
        }
        throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "钱包 CAS 失败，已达最大重试次数");
    }

    /**
     * P0-MONEY-004: Tenant Resolution — ThreadLocal 中的 tenantId 永远胜出
     *
     * <p>调用方传入的 tenantId 仅作为"期望值"（用于日志/校验），最终落库的 tenantId
     * <p>由 TenantContextHolder.get().getTenantId() 决定。这防止上层伪造。
     */
    private Long resolveTenantId(Long callerTenantId) {
        Long ctxTenantId = null;
        try {
            if (TenantContextHolder.get() != null) {
                ctxTenantId = TenantContextHolder.get().getTenantId();
            }
        } catch (Exception ignore) {
            // 单元测试或非 Web 上下文，ctx 可能为 null
        }
        if (ctxTenantId != null) {
            return ctxTenantId;
        }
        // 无 ctx 时（极少数），回退到调用方传入值
        return callerTenantId;
    }

    /**
     * P0-T03: 创建钱包时必须从 ctx 取 tenantId，缺失则抛错。
     *
     * <p>与 {@link #resolveTenantId} 关键区别：本方法**禁止 fallback 到调用方传入值或 1L**。
     * <p>因为 crm_wallet 创建是底层 INSERT，ctx 是唯一权威来源。
     */
    private Long resolveTenantIdForInsert() {
        Long ctxTenantId = TenantContextHolder.getTenantIdOrNull();
        if (ctxTenantId == null) {
            throw new BusinessException(BizCode.BAD_REQUEST,
                    "创建钱包需要 tenantId，但当前会话无 ctx（TenantContextHolder 为空）");
        }
        return ctxTenantId;
    }

    /**
     * P0-M05 · 查询"当前生效"的限时充值活动赠送金额
     *
     * <p>命中规则：
     * <ul>
     *   <li>status = 1（启用）</li>
     *   <li>(start_time IS NULL OR start_time &lt;= now())</li>
     *   <li>(end_time   IS NULL OR end_time   &gt;= now())</li>
     *   <li>recharge_amount &lt;= 充值金额（满足档位）</li>
     *   <li>优先租户级规则，其次全局规则</li>
     * </ul>
     * 选档：取 recharge_amount 最大但不超过入参的规则（即最优档）
     */
    private BigDecimal lookupActivePromotionGift(BigDecimal amount, Long tenantId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<WalletPromotion> w = new LambdaQueryWrapper<>();
        w.eq(WalletPromotion::getStatus, 1);
        if (tenantId != null) {
            w.and(q -> q.eq(WalletPromotion::getTenantId, tenantId)
                    .or().isNull(WalletPromotion::getTenantId));
        } else {
            w.isNull(WalletPromotion::getTenantId);
        }
        // 限时活动时间窗口（NULL 表示常驻）
        w.and(q -> q.isNull(WalletPromotion::getStartTime)
                .or().le(WalletPromotion::getStartTime, now));
        w.and(q -> q.isNull(WalletPromotion::getEndTime)
                .or().ge(WalletPromotion::getEndTime, now));
        // 充值金额满足档位
        w.le(WalletPromotion::getRechargeAmount, amount);
        w.orderByDesc(WalletPromotion::getRechargeAmount);
        w.last("LIMIT 1");
        WalletPromotion matched = promotionService.getOne(w);
        if (matched == null) return BigDecimal.ZERO;
        log.info("[WALLET-PROMO] 命中限时活动 rule={}, recharge={}, gift={}",
                matched.getRuleName(), matched.getRechargeAmount(), matched.getGiftAmount());
        return matched.getGiftAmount() == null ? BigDecimal.ZERO : matched.getGiftAmount();
    }
}