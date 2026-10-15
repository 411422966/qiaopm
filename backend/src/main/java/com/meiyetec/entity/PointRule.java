package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("market_point_rule")
public class PointRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long storeId;
    private Long tenantId;
    private String name;
    private Integer type;       // 1消费得积分 2储值得积分 3签到得积分 4兑换消耗积分
    private String eventKey;
    private Integer pointValue;
    private BigDecimal amountLimit;
    private BigDecimal multiplier;
    private Integer status;     // 0禁用 1启用
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
