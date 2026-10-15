package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_points_log")
public class MktPointsLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private String memberName;
    /** CONSUME/RECHARGE/EXCHANGE/MANUAL/REFUND */
    private String changeType;
    private Integer changeValue;
    private Integer pointsAfter;
    private Long relatedId;
    private String relatedNo;
    private String relatedType;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
