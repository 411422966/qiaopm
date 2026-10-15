package com.meiyetec.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 套餐转赠 DTO
 * 转赠剩余次数给另一个会员
 */
@Data
public class PackageTransferDTO {

    /** 目标会员ID */
    @NotNull(message = "目标会员ID不能为空")
    private Long toMemberId;

    /** 操作人 */
    private Long operatorId;

    /** 备注 */
    private String remark;
}
