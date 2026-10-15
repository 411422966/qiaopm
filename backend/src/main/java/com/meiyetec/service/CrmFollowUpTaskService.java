package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.CrmFollowUpNote;
import com.meiyetec.entity.CrmFollowUpTask;

import java.util.List;
import java.util.Map;

/**
 * R3.0-H P0.5：回访任务 CRUD 与状态流转
 */
public interface CrmFollowUpTaskService extends IService<CrmFollowUpTask> {

    Map<String, Object> pendingCount();

    Page<CrmFollowUpTask> pageTask(int pageNum, int pageSize, Long storeId, Long ownerId,
                                   Integer status, String keyword, String taskType, Integer priority);

    CrmFollowUpTask getById(Long id);

    CrmFollowUpTask saveTask(CrmFollowUpTask task);

    CrmFollowUpTask assign(Long taskId, Long ownerId, String ownerName);

    CrmFollowUpTask start(Long taskId);

    CrmFollowUpTask finish(Long taskId, String resultType, String resultNote, Integer rating);

    CrmFollowUpTask close(Long taskId, String reason);

    CrmFollowUpTask reopen(Long taskId);

    List<CrmFollowUpNote> getNotes(Long taskId);

    List<Map<String, Object>> getMemberTimeline(Long memberId);

    Map<String, Object> kanbanGrouped(Long storeId);

    Map<String, Object> batchAssign(List<Long> ids, Long ownerId, String ownerName);

    Map<String, Object> batchFinish(List<Long> ids);

    Map<String, Object> batchClose(List<Long> ids);

    int seedDemo();
}
