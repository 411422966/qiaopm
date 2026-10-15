package com.meiyetec.service;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

public interface MarketActivityService {

    IPage<Map<String, Object>> activityPage(int pageNum, int pageSize, String name, Integer status);

    Map<String, Object> getActivity(Long id);

    void createActivity(Map<String, Object> params);

    void updateActivity(Long id, Map<String, Object> params);

    void deleteActivity(Long id);

    Map<String, Object> stats();

    Map<String, Object> detail(Long id);

    /** P0-5 扩展：切换上下线状态 */
    Map<String, Object> toggleStatus(Long id, Map<String, Object> params);

    /** P0-5 扩展：复制活动 */
    Map<String, Object> copyActivity(Long id, Map<String, Object> params);

    /** P0-5 扩展：参与者分页 */
    IPage<Map<String, Object>> participants(Long id, int pageNum, int pageSize);
}
