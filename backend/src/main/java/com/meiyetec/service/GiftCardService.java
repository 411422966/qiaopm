package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.GiftCard;
import com.meiyetec.entity.GiftCardTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.GiftCardMapper;
import com.meiyetec.mapper.GiftCardTransactionMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/gift-card")
@RequiredArgsConstructor
public class GiftCardService {

    private final GiftCardMapper cardMapper;
    private final GiftCardTransactionMapper txMapper;

    /**
     * 购买（生成卡片）
     */
    @PostMapping("/purchase")
    @Transactional(rollbackFor = Exception.class)
    public Result<GiftCard> purchase(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BigDecimal faceValue = new BigDecimal(body.get("faceValue").toString());
        Integer serviceCount = body.get("serviceCount") == null ? 1 : ((Number) body.get("serviceCount")).intValue();

        GiftCard c = new GiftCard();
        c.setTenantId(tenantId);
        c.setCardNo(generateCardNo());
        c.setCardType("FIXED".equals(body.get("cardType")) ? "FIXED" : "CUSTOM");
        c.setFaceValue(faceValue);
        c.setBalance(faceValue);
        c.setServiceCount(serviceCount);
        if (body.get("serviceId") != null) c.setServiceId(((Number) body.get("serviceId")).longValue());
        c.setBuyerId(body.get("buyerId") == null ? null : ((Number) body.get("buyerId")).longValue());
        c.setBuyerName((String) body.get("buyerName"));
        c.setRecipientName((String) body.get("recipientName"));
        c.setRecipientPhone((String) body.get("recipientPhone"));
        c.setGiftMessage((String) body.get("giftMessage"));
        c.setTheme((String) body.getOrDefault("theme", "DEFAULT"));
        c.setCoverImage((String) body.get("coverImage"));
        c.setPurchaseTime(LocalDateTime.now());
        c.setStatus("UNUSED");
        // 3 年有效期
        c.setExpireTime(LocalDateTime.now().plusYears(3));
        cardMapper.insert(c);

        // 流水
        GiftCardTransaction tx = new GiftCardTransaction();
        tx.setTenantId(tenantId);
        tx.setCardId(c.getId());
        tx.setTxType("CONSUME");
        tx.setAmount(faceValue);
        tx.setBalance(faceValue);
        tx.setOperatorId(c.getBuyerId());
        tx.setNotes("购卡");
        txMapper.insert(tx);

        return Result.success("已生成", c);
    }

    /**
     * 赠送（修改卡号接收人）
     */
    @PostMapping("/{id}/gift")
    public Result<GiftCard> gift(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        GiftCard c = cardMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "卡片不存在");
        if (!"UNUSED".equals(c.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "已使用/过期的卡不可赠送");
        c.setRecipientName((String) body.get("recipientName"));
        c.setRecipientPhone((String) body.get("recipientPhone"));
        c.setGiftMessage((String) body.get("giftMessage"));
        cardMapper.updateById(c);
        return Result.success("已转赠", c);
    }

    /**
     * 核销（消费）
     */
    @PostMapping("/redeem")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> redeem(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String cardNo = (String) body.get("cardNo");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        GiftCard c = cardMapper.selectOne(new LambdaQueryWrapper<GiftCard>()
                .eq(GiftCard::getTenantId, tenantId)
                .eq(GiftCard::getCardNo, cardNo));
        if (c == null) return Result.error(BizCode.NOT_FOUND, "卡片不存在");
        if (!"UNUSED".equals(c.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "卡片不可用：状态=" + c.getStatus());
        if (c.getExpireTime() != null && c.getExpireTime().isBefore(LocalDateTime.now())) {
            c.setStatus("EXPIRED");
            cardMapper.updateById(c);
            return Result.error(BizCode.BAD_REQUEST, "卡片已过期");
        }
        if (c.getBalance().compareTo(amount) < 0)
            return Result.error(BizCode.BAD_REQUEST, "余额不足");

        c.setBalance(c.getBalance().subtract(amount));
        if (c.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            c.setStatus("USED");
            c.setUseTime(LocalDateTime.now());
            c.setUseOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        }
        cardMapper.updateById(c);

        GiftCardTransaction tx = new GiftCardTransaction();
        tx.setTenantId(tenantId);
        tx.setCardId(c.getId());
        tx.setTxType("CONSUME");
        tx.setAmount(amount);
        tx.setBalance(c.getBalance());
        tx.setOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        tx.setOperatorId(body.get("operatorId") == null ? null : ((Number) body.get("operatorId")).longValue());
        txMapper.insert(tx);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cardId", c.getId());
        data.put("balance", c.getBalance());
        data.put("status", c.getStatus());
        return Result.success("已核销", data);
    }

    /**
     * 退款
     */
    @PostMapping("/{id}/refund")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> refund(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        GiftCard c = cardMapper.selectById(id);
        if (c == null || !c.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "卡片不存在");
        if ("REFUNDED".equals(c.getStatus()))
            return Result.error(BizCode.BAD_REQUEST, "已退款");
        c.setStatus("REFUNDED");
        cardMapper.updateById(c);

        GiftCardTransaction tx = new GiftCardTransaction();
        tx.setTenantId(tenantId);
        tx.setCardId(c.getId());
        tx.setTxType("REFUND");
        tx.setAmount(c.getBalance());
        tx.setBalance(BigDecimal.ZERO);
        tx.setNotes("退款");
        txMapper.insert(tx);
        return Result.success("已退款", null);
    }

    @GetMapping("/list")
    public Result<List<GiftCard>> list(@RequestParam(required = false) String status,
                                        @RequestParam(required = false) String phone) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<GiftCard> qw = new LambdaQueryWrapper<GiftCard>()
                .eq(GiftCard::getTenantId, tenantId)
                .orderByDesc(GiftCard::getId);
        if (status != null && !status.isEmpty()) qw.eq(GiftCard::getStatus, status);
        if (phone != null && !phone.isEmpty()) qw.eq(GiftCard::getRecipientPhone, phone);
        return Result.success(cardMapper.selectList(qw));
    }

    @GetMapping("/by-card-no/{cardNo}")
    public Result<GiftCard> byCardNo(@PathVariable String cardNo) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        GiftCard c = cardMapper.selectOne(new LambdaQueryWrapper<GiftCard>()
                .eq(GiftCard::getTenantId, tenantId)
                .eq(GiftCard::getCardNo, cardNo));
        if (c == null) return Result.error(BizCode.NOT_FOUND, "卡片不存在");
        return Result.success(c);
    }

    @GetMapping("/{id}/transactions")
    public Result<List<GiftCardTransaction>> transactions(@PathVariable Long id) {
        return Result.success(txMapper.selectList(new LambdaQueryWrapper<GiftCardTransaction>()
                .eq(GiftCardTransaction::getCardId, id)
                .orderByDesc(GiftCardTransaction::getId)));
    }

    private String generateCardNo() {
        return "GC" + System.currentTimeMillis() + (int) (Math.random() * 1000);
    }
}