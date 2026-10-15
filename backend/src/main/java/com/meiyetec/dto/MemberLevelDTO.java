package com.meiyetec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberLevelDTO {
    private Long id;
    
    @NotBlank(message = "等级名称不能为空")
    private String levelName;
    
    private String levelCode;
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
}
