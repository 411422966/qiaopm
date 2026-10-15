package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M07 订单明细实体
 */
@Data
@TableName("pos_order_item")
public class PosOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long serviceId;
    private String serviceName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private BigDecimal discountAmount;
    private BigDecimal finalPrice;
    private Long packageOrderId;
    private Integer packageTimes;
    private Long employeeId;
    private String employeeName;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
