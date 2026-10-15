package com.meiyetec.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 套餐拆分核销 DTO
 * 一次操作，多员工次（如：面部清洁1次+肩颈按摩2次）
 */
@Data
public class PackageSplitRedeemDTO {

    /** 套餐订单ID */
    @NotNull(message = "套餐订单ID不能为空")
    private Long orderId;

    /** 操作人 */
    private Long operatorId;

    /** 整体备注 */
    private String note;

    /** 拆分子项 */
    @NotNull(message = "拆分子项不能为空")
    @Valid
    private List<SplitItem> items;

    @Data
    public static class SplitItem {
        private Integer times;
        private String serviceItem;
        private Long beauticianId;
        private String note;
    }
}
