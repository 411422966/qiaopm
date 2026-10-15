package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.CrmFollowUpNote;
import com.meiyetec.entity.CrmFollowUpTask;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.CrmFollowUpNoteMapper;
import com.meiyetec.mapper.CrmFollowUpTaskMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CrmFollowUpTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CrmFollowUpTaskServiceImpl extends ServiceImpl<CrmFollowUpTaskMapper, CrmFollowUpTask>
        implements CrmFollowUpTaskService {

    private final CrmFollowUpTaskMapper taskMapper;
    private final CrmFollowUpNoteMapper noteMapper;

    /** Current tenant (fallback 1L if not authenticated) */
    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    @Override
    public Map<String, Object> pendingCount() {
        Long t = tid();
        Map<String, Object> r = new LinkedHashMap<>();
        long total = taskMapper.selectCount(new LambdaQueryWrapper<CrmFollowUpTask>()
                .eq(CrmFollowUpTask::getTenantId, t)
                .eq(CrmFollowUpTask::getDeleted, 0)
                .in(CrmFollowUpTask::getStatus, 1, 2));
        long overdue = taskMapper.selectCount(new LambdaQueryWrapper<CrmFollowUpTask>()
                .eq(CrmFollowUpTask::getTenantId, t)
                .eq(CrmFollowUpTask::getDeleted, 0)
                .eq(CrmFollowUpTask::getStatus, 1)
                .lt(CrmFollowUpTask::getPlannedAt, LocalDateTime.now()));
        r.put("total", total);
        r.put("overdue", overdue);
        return r;
    }

    @Override
    public Page<CrmFollowUpTask> pageTask(int pageNum, int pageSize,
                                          Long storeId, Long ownerId,
                                          Integer status, String keyword, String taskType, Integer priority) {
        Long t = tid();
        Page<CrmFollowUpTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<CrmFollowUpTask> qw = new LambdaQueryWrapper<>();
        qw.eq(CrmFollowUpTask::getTenantId, t)
          .eq(CrmFollowUpTask::getDeleted, 0);

        if (storeId != null) qw.eq(CrmFollowUpTask::getStoreId, storeId);
        if (ownerId != null) qw.eq(CrmFollowUpTask::getOwnerId, ownerId);
        if (taskType != null && !taskType.isEmpty()) qw.eq(CrmFollowUpTask::getTaskType, taskType);
        if (priority != null) qw.eq(CrmFollowUpTask::getPriority, priority);

        if (status != null) {
            if (status == 5) {
                qw.eq(CrmFollowUpTask::getStatus, 1)
                  .lt(CrmFollowUpTask::getPlannedAt, LocalDateTime.now());
            } else {
                qw.eq(CrmFollowUpTask::getStatus, status);
            }
        }

        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(CrmFollowUpTask::getSubject, keyword)
                    .or()
                    .like(CrmFollowUpTask::getMemberName, keyword)
                    .or()
                    .like(CrmFollowUpTask::getTaskNo, keyword));
        }

        qw.orderByDesc(CrmFollowUpTask::getPlannedAt);
        return taskMapper.selectPage(page, qw);
    }

    @Override
    public CrmFollowUpTask getById(Long id) {
        Long t = tid();
        CrmFollowUpTask task = taskMapper.selectById(id);
        if (task == null) return null;
        if (task.getDeleted() != null && task.getDeleted() != 0) return null;
        if (task.getTenantId() != null && !task.getTenantId().equals(t)) return null;
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmFollowUpTask saveTask(CrmFollowUpTask task) {
        Long t = tid();
        if (task.getTenantId() == null) task.setTenantId(t);
        if (task.getId() == null) {
            task.setTaskNo(generateTaskNo());
            task.setStatus(1);
            if (task.getPlannedAt() == null) {
                task.setPlannedAt(LocalDateTime.now().plusDays(1));
            }
            if (task.getSlaMinutes() == null) task.setSlaMinutes(1440);
            if (task.getSlaExpireAt() == null && task.getPlannedAt() != null && task.getSlaMinutes() != null) {
                task.setSlaExpireAt(task.getPlannedAt().plusMinutes(task.getSlaMinutes()));
            }
            taskMapper.insert(task);
            logNote(task.getId(), null, 1, "create task", null);
        } else {
            CrmFollowUpTask before = taskMapper.selectById(task.getId());
            if (before == null || !t.equals(before.getTenantId())) {
                throw new BusinessException(BizCode.FORBIDDEN, "no permission to update this task");
            }
            task.setTenantId(before.getTenantId());
            taskMapper.updateById(task);
            logNote(task.getId(), null, task.getStatus(), "update task", null);
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmFollowUpTask assign(Long taskId, Long ownerId, String ownerName) {
        Long t = tid();
        CrmFollowUpTask at = taskMapper.selectById(taskId);
        if (at == null || !t.equals(at.getTenantId())) {
            throw new BusinessException(BizCode.FORBIDDEN, "no permission to assign this task");
        }
        CrmFollowUpTask ut = new CrmFollowUpTask();
        ut.setId(taskId);
        ut.setOwnerId(ownerId);
        ut.setOwnerName(ownerName);
        taskMapper.updateById(ut);
        logNote(taskId, at.getStatus(), at.getStatus(),
                ownerId == null ? "unassign" : ("assign to " + ownerName), null);
        return taskMapper.selectById(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmFollowUpTask start(Long taskId) {
        Long t = tid();
        CrmFollowUpTask at = taskMapper.selectById(taskId);
        if (at == null || !t.equals(at.getTenantId())) {
            throw new BusinessException(BizCode.FORBIDDEN, "no permission to start this task");
        }
        if (at.getStatus() != null && at.getStatus() == 1) {
            logNote(taskId, 1, 2, "start", null);
            at.setStatus(2);
            at.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(at);
        }
        return at;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmFollowUpTask finish(Long taskId, String resultType, String resultNote, Integer rating) {
        Long t = tid();
        CrmFollowUpTask at = taskMapper.selectById(taskId);
        if (at == null || !t.equals(at.getTenantId())) {
            throw new BusinessException(BizCode.FORBIDDEN, "no permission to finish this task");
        }
        Integer fromStatus = at.getStatus();
        at.setStatus(3);
        at.setResultType(resultType);
        at.setResultNote(resultNote);
        at.setRating(rating);
        at.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(at);
        logNote(taskId, fromStatus, 3, "finish: " + resultType, null);
        return at;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmFollowUpTask close(Long taskId, String reason) {
        Long t = tid();
        CrmFollowUpTask at = taskMapper.selectById(taskId);
        if (at == null || !t.equals(at.getTenantId())) {
            throw new BusinessException(BizCode.FORBIDDEN, "no permission to close this task");
        }
        Integer fromStatus = at.getStatus();
        at.setStatus(4);
        String prevNote = at.getResultNote() == null ? "" : at.getResultNote();
        at.setResultNote(prevNote + " [close:" + reason + "]");
        at.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(at);
        logNote(taskId, fromStatus, 4, "close: " + reason, null);
        return at;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmFollowUpTask reopen(Long taskId) {
        Long t = tid();
        CrmFollowUpTask at = taskMapper.selectById(taskId);
        if (at == null || !t.equals(at.getTenantId())) {
            throw new BusinessException(BizCode.FORBIDDEN, "no permission to reopen this task");
        }
        Integer fromStatus = at.getStatus();
        at.setStatus(1);
        at.setResultType(null);
        at.setResultNote(null);
        at.setRating(null);
        at.setFinishedAt(null);
        at.setStartedAt(null);
        taskMapper.updateById(at);
        logNote(taskId, fromStatus, 1, "reopen", null);
        return at;
    }

    @Override
    public List<CrmFollowUpNote> getNotes(Long taskId) {
        Long t = tid();
        CrmFollowUpTask at = taskMapper.selectById(taskId);
        if (at == null || !t.equals(at.getTenantId())) return new ArrayList<>();
        return noteMapper.selectList(new LambdaQueryWrapper<CrmFollowUpNote>()
                .eq(CrmFollowUpNote::getTenantId, t)
                .eq(CrmFollowUpNote::getTaskId, taskId)
                .orderByDesc(CrmFollowUpNote::getCreateTime));
    }

    @Override
    public List<Map<String, Object>> getMemberTimeline(Long memberId) {
        Long t = tid();
        List<CrmFollowUpTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<CrmFollowUpTask>()
                        .eq(CrmFollowUpTask::getTenantId, t)
                        .eq(CrmFollowUpTask::getDeleted, 0)
                        .eq(CrmFollowUpTask::getMemberId, memberId)
                        .orderByDesc(CrmFollowUpTask::getCreateTime));
        return tasks.stream().map(x -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "task");
            item.put("id", x.getId());
            item.put("taskNo", x.getTaskNo());
            item.put("subject", x.getSubject());
            item.put("content", x.getContent());
            item.put("status", x.getStatus());
            item.put("resultType", x.getResultType());
            item.put("resultNote", x.getResultNote());
            item.put("rating", x.getRating());
            item.put("plannedAt", x.getPlannedAt());
            item.put("finishedAt", x.getFinishedAt());
            item.put("createTime", x.getCreateTime());
            item.put("ownerName", x.getOwnerName());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> kanbanGrouped(Long storeId) {
        Long t = tid();
        LambdaQueryWrapper<CrmFollowUpTask> qw = new LambdaQueryWrapper<>();
        qw.eq(CrmFollowUpTask::getTenantId, t)
          .eq(CrmFollowUpTask::getDeleted, 0)
          .in(CrmFollowUpTask::getStatus, 1, 2)
          .orderByAsc(CrmFollowUpTask::getPlannedAt);
        if (storeId != null) qw.eq(CrmFollowUpTask::getStoreId, storeId);

        List<CrmFollowUpTask> tasks = taskMapper.selectList(qw);

        Map<String, List<CrmFollowUpTask>> byOwner = tasks.stream()
                .collect(Collectors.groupingBy(
                        x -> x.getOwnerId() == null ? "__unassigned__" : ("owner_" + x.getOwnerId())));

        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map.Entry<String, List<CrmFollowUpTask>> e : byOwner.entrySet()) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("key", e.getKey());
            col.put("ownerId", e.getValue().get(0).getOwnerId());
            col.put("ownerName", e.getValue().get(0).getOwnerName());
            col.put("tasks", e.getValue().stream().map(x -> {
                Map<String, Object> t2 = new LinkedHashMap<>();
                t2.put("id", x.getId());
                t2.put("taskNo", x.getTaskNo());
                t2.put("subject", x.getSubject());
                t2.put("memberName", x.getMemberName());
                t2.put("memberPhone", x.getMemberPhone());
                t2.put("plannedAt", x.getPlannedAt());
                t2.put("priority", x.getPriority());
                t2.put("status", x.getStatus());
                t2.put("touchCount", x.getTouchCount());
                t2.put("lastTouchAt", x.getLastTouchAt());
                return t2;
            }).collect(Collectors.toList()));
            columns.add(col);
        }
        columns.sort(Comparator.comparing(c -> c.get("key").equals("__unassigned__") ? 0 : 1));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("total", tasks.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchAssign(List<Long> ids, Long ownerId, String ownerName) {
        Long t = tid();
        Map<String, Object> r = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            r.put("affected", 0);
            return r;
        }
        List<CrmFollowUpTask> tasks = taskMapper.selectBatchIds(ids);
        int affected = 0;
        for (CrmFollowUpTask task : tasks) {
            if (!t.equals(task.getTenantId())) continue;
            Integer fromStatus = task.getStatus();
            task.setOwnerId(ownerId);
            task.setOwnerName(ownerName);
            taskMapper.updateById(task);
            logNote(task.getId(), fromStatus, fromStatus, "batch assign to " + ownerName, null);
            affected++;
        }
        r.put("affected", affected);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchFinish(List<Long> ids) {
        Long t = tid();
        Map<String, Object> r = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            r.put("affected", 0);
            return r;
        }
        List<CrmFollowUpTask> tasks = taskMapper.selectBatchIds(ids);
        int affected = 0;
        for (CrmFollowUpTask task : tasks) {
            if (!t.equals(task.getTenantId())) continue;
            Integer fromStatus = task.getStatus();
            task.setStatus(3);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            logNote(task.getId(), fromStatus, 3, "batch finish", null);
            affected++;
        }
        r.put("affected", affected);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchClose(List<Long> ids) {
        Long t = tid();
        Map<String, Object> r = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            r.put("affected", 0);
            return r;
        }
        List<CrmFollowUpTask> tasks = taskMapper.selectBatchIds(ids);
        int affected = 0;
        for (CrmFollowUpTask task : tasks) {
            if (!t.equals(task.getTenantId())) continue;
            Integer fromStatus = task.getStatus();
            task.setStatus(4);
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            logNote(task.getId(), fromStatus, 4, "batch close", null);
            affected++;
        }
        r.put("affected", affected);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int seedDemo() {
        long now = System.currentTimeMillis();
        List<CrmFollowUpTask> seeds = Arrays.asList(
            makeTask(++now, 1L, "POST_TREATMENT", 1L, 2L, "Zhang San", "13800001111",
                     "Post-treatment follow-up", "How was your treatment experience?", LocalDateTime.now().minusDays(1), 2, 1),
            makeTask(++now, 1L, "BIRTHDAY", 1L, 2L, "Li Si", "13800002222",
                     "Birthday greeting", "Happy birthday!", LocalDateTime.now().plusDays(1), 2, 1),
            makeTask(++now, 2L, "EXPIRE", 2L, null, "Wang Wu", "13800003333",
                     "Package expiry reminder", "Your package is about to expire", LocalDateTime.now().minusDays(2), 1, 2),
            makeTask(++now, 2L, "CUSTOM", 2L, null, "Zhao Liu", "13800004444",
                     "VIP follow-up", "Thanks for your continued support", LocalDateTime.now().plusHours(2), 3, 1)
        );
        for (CrmFollowUpTask t : seeds) taskMapper.insert(t);
        return seeds.size();
    }

    private CrmFollowUpTask makeTask(long seed, Long storeId, String taskType,
                                     Long ownerId, Long ownerId2, String memberName, String phone,
                                     String subject, String content, LocalDateTime planned, Integer priority, Integer status) {
        CrmFollowUpTask t = new CrmFollowUpTask();
        t.setTenantId(1L);
        t.setStoreId(storeId);
        t.setTaskNo("T" + seed);
        t.setTaskType(taskType);
        t.setMemberId(100L + seed % 10);
        t.setMemberName(memberName);
        t.setMemberPhone(phone);
        t.setSourceType("ORDER");
        t.setSourceId(seed);
        t.setSourceNo("O" + seed);
        t.setSubject(subject);
        t.setContent(content);
        t.setPlannedAt(planned);
        t.setSlaExpireAt(planned.plusMinutes(1440));
        t.setSlaMinutes(1440);
        t.setPriority(priority);
        t.setStatus(status);
        t.setOwnerId(ownerId);
        t.setOwnerName(ownerId2 != null ? "agent-" + (char)('A' + ownerId - 1) : null);
        t.setCreateTime(LocalDateTime.now());
        return t;
    }

    private String generateTaskNo() {
        return "T" + System.currentTimeMillis();
    }

    private void logNote(Long taskId, Integer fromStatus, Integer toStatus, String content, Long operatorId) {
        CrmFollowUpNote note = new CrmFollowUpNote();
        note.setTenantId(1L);
        note.setTaskId(taskId);
        note.setFromStatus(fromStatus);
        note.setToStatus(toStatus);
        note.setContent(content);
        note.setOperatorId(operatorId);
        note.setCreateTime(LocalDateTime.now());
        noteMapper.insert(note);
    }
}
