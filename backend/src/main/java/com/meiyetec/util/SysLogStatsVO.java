package com.meiyetec.util;

import lombok.Data;

@Data
public class SysLogStatsVO {
    private Long todayCount;
    private Long totalCount;
    private Double todaySuccessRate;
    private Double totalSuccessRate;
    private Long todayFailCount;
    private Long totalFailCount;
    private String topIp;
    private Long topIpCount;
}