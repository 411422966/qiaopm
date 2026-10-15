package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CrmFollowUpPlan;

import java.util.List;
import java.util.Map;

public interface CrmFollowUpPlanService extends IService<CrmFollowUpPlan> {

    /**
     * 统计概览：总数 / 启用数 / 今日生成 / 本月生成
     */
    Map<String, Object> stats();

    /**
     * 最近 7 天触发趋势
     */
    List<Map<String, Object>> weekTrend();

    /**
     * 分页查询
     */
    Page<CrmFollowUpPlan> pagePlan(long pageNum, long pageSize);

    /**
     * 触发计划：扫描源表生成 CrmFollowUpTask
     */
    Map<String, Object> triggerPlan(Long planId);

    /**
     * 预览：扫描源表找出将触发的会员（不写入任务）
     */
    Map<String, Object> previewPlan(Long planId);

    /**
     * 插入演示数据
     */
    int seedDemo();
}
