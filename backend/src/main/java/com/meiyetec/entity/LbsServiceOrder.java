package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lbs_service_order")
public class LbsServiceOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String orderNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Long serviceId;
    private String serviceName;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalDateTime scheduledTime;
    private Long technicianId;
    private String dispatchStatus;
    private LocalDateTime dispatchTime;
    private LocalDateTime acceptTime;
    private BigDecimal amount;
    private BigDecimal travelFee;
    private String notes;
    private BigDecimal dispatchScore;         // 派单综合分
    private String dispatchBreakdown;          // 5 维度 JSON 明细
    private String rejectReason;               // 拒绝原因
    private Integer routeSeq;                  // 路线顺序号
    private Integer etaMinutes;               // 预计到达分钟数
    private LocalDateTime scheduledEnd;        // 预计服务结束时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}