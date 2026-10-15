package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.Wallet;
import com.meiyetec.util.Result;

import java.util.Map;

/**
 * P0-MONEY-002 · 钱包服务接口（升级版）
 *
 * <p>SSOT：crm_wallet.balance 是唯一权威源。
 * <p>crm_member.balance 是只读同步字段，由 {@link #syncMemberBalance(Long)} 唯一同步入口维护。
 */
public interface WalletService extends IService<Wallet> {

    /**
     * 钱包扣款（P0-MONEY-002 收敛点：POS 收银台"钱包"支付方式改走此方法）
     *
     * <p>行为：
     * <ul>
     *   <li>CAS 乐观锁更新 crm_wallet.balance</li>
     *   <li>写 crm_wallet_transaction 流水</li>
     *   <li>累加 crm_member.totalConsume</li>
     *   <li>同步 crm_member.balance（只读同步字段）</li>
     * </ul>
     *
     * @param dto 扣款请求
     * @param tenantId 租户 ID
     * @return 扣款结果，含 balanceBefore / balanceAfter / txnId
     */
    Result<Map<String, Object>> doConsume(WalletOpDTO dto, Long tenantId);

    /**
     * 钱包充值（与 doConsume 对称）
     */
    Result<Map<String, Object>> doRecharge(WalletOpDTO dto, Long tenantId);

    /**
     * 钱包退款（与 doConsume 对称）
     */
    Result<Map<String, Object>> doRefund(WalletOpDTO dto, Long tenantId);

    /**
     * P0-M05 · 冻结金额（不减少余额，只把可用余额的一部分"冻结"起来）
     *
     * <p>典型场景：客诉暂挂、纠纷处理期间，避免会员在调查完成前把余额花光。
     * <p>业务规则：
     * <ul>
     *   <li>amount &gt; 0 且 amount &lt;= (balance - frozenAmount)（即冻结后可用余额 &gt;= 0）</li>
     *   <li>frozen_amount 只能单调增加；解冻必须走 {@link #doUnfreeze}</li>
     *   <li>写 crm_wallet_transaction 流水，type=5 冻结</li>
     * </ul>
     */
    Result<Map<String, Object>> doFreeze(WalletOpDTO dto, Long tenantId);

    /**
     * P0-M05 · 解冻金额（frozen_amount → balance）
     *
     * <p>与 {@link #doFreeze} 对称；amount &lt;= frozenAmount。
     * <p>写 crm_wallet_transaction 流水，type=6 解冻。
     */
    Result<Map<String, Object>> doUnfreeze(WalletOpDTO dto, Long tenantId);

    /**
     * P0-MONEY-002 · 同步 crm_member.balance 到 crm_wallet.balance（SSOT 同步）
     *
     * <p>crm_member.balance 是只读视图字段（冗余），crm_wallet.balance 才是 SSOT。
     * 钱包任何写操作完成后必须调用此方法，否则前端列表/详情看到的余额会与真实值漂移。
     *
     * <p>严禁其他任何代码路径直接写 crm_member.balance！如需绕过，先在 SINGLE_SOURCE_OF_TRUTH_WALLET.md 评审。
     */
    void syncMemberBalance(Long memberId);
}