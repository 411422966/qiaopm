package com.meiyetec.util;

import lombok.Data;

@Data
public class TrendPointVO {
    private String date;
    private Long total;
    private Long success;
    private Long fail;
}