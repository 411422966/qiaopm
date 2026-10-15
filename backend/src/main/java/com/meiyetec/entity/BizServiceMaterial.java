package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 服务-耗材 BOM
 * 一次服务消耗多少耗材；订单支付成功后会自动按此 BOM 写 stock_record(type=4)
 */
@Data
@TableName("biz_service_material")
public class BizServiceMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long serviceId;
    private Long productId;
    private BigDecimal quantity;
    private String unit;
    /** 1必需 0可选 */
    private Integer isRequired;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
