package com.meiyetec.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class PackageRedeemDTO {

    @NotNull(message = "套餐订单ID不能为空")
    private Long orderId;

    @Min(value = 1, message = "核销次数至少1次")
    private Integer times = 1;

    /** 服务项目 */
    private String serviceItem;

    /** 美容师ID */
    private Long beauticianId;

    /** 操作人ID */
    private Long operatorId;

    /** 备注 */
    private String note;
}
