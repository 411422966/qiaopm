package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crm_member_profile")
public class MemberProfile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;
    private String skinType;
    /** 发质类型（美发子行业画像） — 例：油性 / 干性 / 中性 / 混合性 / 受损 / 染烫 / 细软 / 粗硬 */
    private String hairType;
    /** 甲面类型（美甲子行业画像） — 例：薄软甲 / 厚硬甲 / 易脆甲 / 干燥甲 / 湿润甲 / 凹凸不平 / 正常甲 */
    private String nailType;
    private String allergy;
    private String hobby;
    private String job;
    private String wechat;
    private String emergencyName;
    private String emergencyPhone;
    private String consumePref;
    private String visitRecord;
    private Integer deleted;
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
