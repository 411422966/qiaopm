package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("report_snapshot")
public class ReportSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long storeId;
    private LocalDate bizDate;
    private String metricKey;
    private BigDecimal metricValue;
    private String extra;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}