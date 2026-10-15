package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("crm_package_redemption")
public class PackageRedemption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long orderId;
    private Long memberId;
    /** 核销门店ID（跨店核销场景记录"当前门店"）— R3.0-B 补丁 */
    private Long storeId;
    private Long packageId;
    private Integer times;
    /** 跨店手续费（=0 表示本店核销）— R3.0-B 补丁 */
    private java.math.BigDecimal crossFee;
    /** 命中的跨店规则ID（=NULL 表示本店核销）— R3.0-B 补丁 */
    private Long crossRuleId;
    private String serviceItem;
    private Long beauticianId;
    private String note;
    private Long operatorId;
    private LocalDateTime redeemTime;
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 会员姓名 */
    @TableField(exist = false)
    private String memberName;

    /** 套餐名称 */
    @TableField(exist = false)
    private String packageName;

    /** 美容师姓名 */
    @TableField(exist = false)
    private String beauticianName;
}
