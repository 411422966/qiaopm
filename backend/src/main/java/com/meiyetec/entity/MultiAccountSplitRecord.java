package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M02-04 分账记录（订单维度）
 * <p>一笔订单触发一次分账，写一行；具体分账金额在 {@link MultiAccountSplitRecordDetail} 中。
 */
@Data
@TableName("m02_split_record")
public class MultiAccountSplitRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long orderId;
    private String orderNo;
    private Long ruleId;
    /** 规则名称（冗余，避免规则删除/改名后无法追溯） */
    private String ruleName;
    private BigDecimal orderAmount;
    private BigDecimal feeAmount;
    private BigDecimal splitTotal;
    /** 1 PENDING / 2 SUCCESS / 3 FAILED / 4 PARTIAL */
    private Integer status;
    private String errorMsg;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime splitTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
