package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M07 收银订单实体
 */
@Data
@TableName("pos_order")
public class PosOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String orderNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Long bookingId;
    /** 类型:1消费 2充值 3混合 */
    private Integer type;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private BigDecimal paidAmount;
    private Long cashierId;
    private String cashierName;
    private String remark;
    /** 状态:1进行中 2已支付 3已退款 4已撤销 */
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime refundTime;
    private BigDecimal refundAmount;
    private String refundReason;

    /** 累计已退金额（多次部分退款累加） */
    private BigDecimal refundedAmount;
    /** 退款次数（部分退款计数） */
    private Integer refundCount;

    /** 现金实收金额（前端传入，服务端计算找零） */
    private BigDecimal tenderedAmount;
    /** 找零金额 = tenderedAmount - 实际支付额 */
    private BigDecimal changeAmount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private java.util.List<PosOrderItem> items;

    @TableField(exist = false)
    private java.util.List<PosPayment> payments;
}
