package com.meiyetec.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PackageSaveDTO {

    private Long id;

    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 100, message = "套餐名称不超过100字")
    private String name;

    @Size(max = 50)
    private String code;

    @Size(max = 50)
    private String category;

    @Size(max = 500)
    private String description;

    /** 当 bundleType=0 时必须；当 bundleType=1 时由子项目自动汇总（可选） */
    @Min(value = 1, message = "总次数至少1次")
    private Integer totalTimes;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal price;

    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal costPrice;

    @Min(value = 1, message = "有效天数至少1天")
    private Integer validDays;

    private Integer status;

    private String remark;

    /** M04 bundle: 0=单商品 1=组合（含子项目） */
    private Integer bundleType;
    /** M04 bundle: 子项目列表 */
    private List<com.meiyetec.entity.BundleSubItem> bundleItems;
}
