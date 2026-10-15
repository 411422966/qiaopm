package com.meiyetec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StoreDTO {
    private Long id;
    
    @NotBlank(message = "门店名称不能为空")
    private String storeName;
    
    private String storeCode;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String province;
    private String city;
    private String district;
    private String businessHours;
    private Integer status;
    private String logoUrl;
    private String description;
}
