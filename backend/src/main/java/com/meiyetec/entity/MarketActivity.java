package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("market_activity")
public class MarketActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long storeId;
    private Long tenantId;
    private String name;
    private Integer type;       // 1满减 2折扣 3赠礼 4双倍积分
    private String ruleConfig;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;     // 0禁用 1启用 2已结束
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
