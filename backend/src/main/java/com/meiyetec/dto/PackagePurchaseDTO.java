package com.meiyetec.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PackagePurchaseDTO {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "套餐ID不能为空")
    private Long packageId;

    @NotNull(message = "购买价格不能为空")
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal price;

    /** 支付方式：微信/支付宝/银行卡/现金 */
    private String payMethod;

    /** 操作人ID */
    private Long operatorId;

    /** 备注 */
    private String remark;
}
