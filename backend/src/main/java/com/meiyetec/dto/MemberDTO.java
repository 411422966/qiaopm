package com.meiyetec.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MemberDTO {
    private Long id;

    private String memberNo;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
    private String phone;

    private Integer gender;
    private LocalDate birthday;
    private Long levelId;
    private Long storeId;
    private BigDecimal balance;
    private Integer points;
    private BigDecimal totalConsume;
    private String avatar;
    private String address;
    private String remark;
    private Integer status;
    private LocalDate joinDate;
    private List<Long> tagIds;
}
