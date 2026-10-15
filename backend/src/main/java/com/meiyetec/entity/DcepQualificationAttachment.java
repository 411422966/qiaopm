package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dcep_qualification_attachment")
public class DcepQualificationAttachment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long qualificationId;

    /** legal_id_front / legal_id_back / business_license / other */
    private String materialType;

    private String fileName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 运行时构造的访问 URL */
    @TableField(exist = false)
    private String url;
}