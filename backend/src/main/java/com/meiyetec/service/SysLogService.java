package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.SysLog;
import com.meiyetec.util.SysLogAnomalyVO;
import com.meiyetec.util.SysLogEnumsVO;
import com.meiyetec.util.SysLogStatsVO;
import com.meiyetec.util.TrendPointVO;

import java.util.List;

public interface SysLogService extends IService<SysLog> {
    SysLogStatsVO stats();
    List<TrendPointVO> trend(int days);
    SysLogAnomalyVO anomaly(int days, int top);
    int clearBeforeDays(int days);
    SysLogEnumsVO enums(int defaultClearDays);
    /**
     * 上下文日志：按用户 + 时间窗口 ± minutes, 排除目标 id
     */
    List<SysLog> contextByUser(String username, Long centerId, int minutes, int limit);
    /**
     * 同会话：同 username + 相近 IP，相近时间（± minutes）
     */
    List<SysLog> sameSession(String username, String ip, Long centerId, int minutes, int limit);
}
