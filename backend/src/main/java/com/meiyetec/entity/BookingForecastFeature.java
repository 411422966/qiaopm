package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("booking_forecast_feature")
public class BookingForecastFeature {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long storeId;
    private LocalDate featureDate;
    private Integer featureHour;
    private Long employeeId;

    // 时序特征
    private Integer bookingCount;
    private Integer checkinCount;
    private Integer completeCount;
    private Integer noshowCount;
    private Integer cancelCount;

    // 派生特征
    private BigDecimal noshowRate;
    private BigDecimal checkinRate;
    private Integer avgServiceDurationMin;
    private Integer peakHour;

    // 时间特征
    private Integer dayOfWeek;
    private Integer isWeekend;
    private Integer isHoliday;
    private Integer isPayday;
    private Integer isMemberDay;
    private Integer daysBeforeHoliday;
    private Integer daysAfterHoliday;

    // 历史同期
    private BigDecimal sameDowAvg7;
    private BigDecimal sameDowAvg30;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
