package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_brand")
public class MktBrand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String brandCode;
    private String brandName;
    /** CHAIN直营 / FRANCHISE加盟 / FLAGSHIP旗舰店 */
    private String brandType;
    private String logoUrl;
    private String contactPerson;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String address;
    /** BRAND品牌共享 / STORE门店独立 */
    private String memberShareScope;
    /** BRAND品牌积分 / STORE门店积分 */
    private String pointsShareScope;
    /** BRAND储值共享 / STORE门店储值 */
    private String balanceShareScope;
    private Integer status;
    private Integer priority;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
