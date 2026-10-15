package com.meiyetec.dto;

import lombok.Data;

@Data
public class TenantStatsDTO {
    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private Integer status;
    private String expireAt;
    private Boolean expired;

    private Integer userTotal;
    private Integer userActive;
    private Integer userInactive;
}