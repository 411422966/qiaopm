package com.meiyetec.util;

import lombok.Data;

import java.util.List;

@Data
public class SysLogEnumsVO {
    private List<String> modules;
    private List<String> actions;
    private Integer defaultClearDays;
}
