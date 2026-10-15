package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cv_recognition_log")
public class CvRecognitionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String modelCode;
    private String bizType;
    private String imageUrl;
    private Long memberId;
    private String resultJson;
    private BigDecimal confidence;
    private Integer latencyMs;
    private String status;
    /** 实际调用的提供商（TENCENT/ALIYUN/BAIDU/LOCAL） */
    private String provider;
    /** 推理模式：http/sdk/local/mock */
    private String inferenceMode;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}