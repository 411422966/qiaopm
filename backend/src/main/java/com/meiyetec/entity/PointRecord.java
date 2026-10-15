package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("market_point_record")
public class PointRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long storeId;
    private Long tenantId;
    private Long memberId;
    private Integer type;        // 1获得 2消耗
    private Integer pointValue;
    private Integer balanceBefore;
    private Integer balanceAfter;
    private String source;
    private Long refId;
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(exist = false)
    private String memberName;
}
