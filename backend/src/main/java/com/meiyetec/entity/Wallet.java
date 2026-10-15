package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_wallet")
public class Wallet {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** P0-T03: 租户 ID（与 crm_wallet.tenant_id 对齐） */
    private Long tenantId;
    private Long memberId;
    private BigDecimal balance;
    private BigDecimal totalRecharge;
    private BigDecimal totalConsume;
    private BigDecimal frozenAmount;
    /** 乐观锁版本号（每次更新 +1） */
    private Integer version;
    private LocalDateTime updatedAt;
}
