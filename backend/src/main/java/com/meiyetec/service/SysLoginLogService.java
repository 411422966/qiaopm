package com.meiyetec.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.SysLoginLog;

import java.util.List;
import java.util.Map;

public interface SysLoginLogService extends IService<SysLoginLog> {
    void asyncRecord(SysLoginLog log);
    IPage<SysLoginLog> page(int pageNum, int pageSize, String username, Integer status, String ip,
                            String loginType, String startTime, String endTime);
    int clearBeforeDays(int days);
    /** 统计：今日 / 累计 / 失败率 / TopIP */
    Map<String, Object> stats(Integer days);
    /** 失败趋势：近 N 天 */
    List<Map<String, Object>> trend(Integer days);
    /** Top 失败用户 / Top 失败 IP */
    Map<String, Object> anomaly(Integer days, Integer top);
    /** 全文搜索 */
    IPage<SysLoginLog> search(int pageNum, int pageSize, String q, Integer status,
                              String startTime, String endTime);
}
