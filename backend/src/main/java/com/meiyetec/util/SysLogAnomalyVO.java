package com.meiyetec.util;

import lombok.Data;

import java.util.List;

@Data
public class SysLogAnomalyVO {
    private List<TopEntry> topIps;
    private List<TopEntry> topUrls;

    @Data
    public static class TopEntry {
        private String key;
        private Long failCount;
        private Long totalCount;
        private Double failRate;
    }
}
