package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mem_level")
public class MemberLevel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String levelCode;
    private String levelName;
    private String levelIcon;
    private Integer minPoints;
    private Integer maxPoints;
    private BigDecimal discountRate;
    private String birthdayGift;
    private BigDecimal birthdayDiscount;
    private BigDecimal pointsMultiplier;
    private String levelColor;
    private Integer sortOrder;
    private String description;
    private Integer status;

    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
}
