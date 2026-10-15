package com.meiyetec.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.CrmFollowUpNote;
import com.meiyetec.entity.CrmFollowUpTask;
import com.meiyetec.service.CrmFollowUpTaskService;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crm/care/follow/task")
@RequiredArgsConstructor
@SysOpLog(module = "Follow-up Task")
public class CrmFollowUpTaskController {

    private final CrmFollowUpTaskService taskService;

    @GetMapping("/pending-count")
    public Result<Map<String, Object>> pendingCount() {
        return Result.success(taskService.pendingCount());
    }

    @GetMapping("/page")
    public Result<Page<CrmFollowUpTask>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) Integer priority) {
        return Result.success(taskService.pageTask(pageNum, pageSize, storeId, ownerId, status, keyword, taskType, priority));
    }

    @GetMapping("/{id:\\d+}")
    public Result<CrmFollowUpTask> getById(@PathVariable Long id) {
        return Result.success(taskService.getById(id));
    }

    @PostMapping
    public Result<CrmFollowUpTask> save(@RequestBody CrmFollowUpTask task) {
        return Result.success(taskService.saveTask(task));
    }

    @PostMapping("/{id:\\d+}/assign")
    public Result<CrmFollowUpTask> assign(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Object ownerIdObj = body.get("ownerId");
        Long ownerId = ownerIdObj == null ? null : Long.valueOf(ownerIdObj.toString());
        Object ownerNameObj = body.get("ownerName");
        String ownerName = ownerNameObj == null ? null : ownerNameObj.toString();
        return Result.success(taskService.assign(id, ownerId, ownerName));
    }

    @PostMapping("/{id:\\d+}/start")
    public Result<CrmFollowUpTask> start(@PathVariable Long id) {
        return Result.success(taskService.start(id));
    }

    @PostMapping("/{id:\\d+}/finish")
    public Result<CrmFollowUpTask> finish(@PathVariable Long id,
                                          @RequestBody Map<String, Object> body) {
        Object resultTypeObj = body.get("resultType");
        String resultType = resultTypeObj == null ? null : resultTypeObj.toString();
        Object resultNoteObj = body.get("resultNote");
        String resultNote = resultNoteObj == null ? null : resultNoteObj.toString();
        Object ratingObj = body.get("rating");
        Integer rating = ratingObj == null ? null : Integer.valueOf(ratingObj.toString());
        return Result.success(taskService.finish(id, resultType, resultNote, rating));
    }

    @PostMapping("/{id:\\d+}/close")
    public Result<CrmFollowUpTask> close(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (String) body.getOrDefault("reason", "运营关闭");
        return Result.success(taskService.close(id, reason));
    }

    @PostMapping("/{id:\\d+}/reopen")
    public Result<CrmFollowUpTask> reopen(@PathVariable Long id) {
        return Result.success(taskService.reopen(id));
    }

    @GetMapping("/{id:\\d+}/notes")
    public Result<List<CrmFollowUpNote>> notes(@PathVariable Long id) {
        return Result.success(taskService.getNotes(id));
    }

    @GetMapping("/member/{memberId}/timeline")
    public Result<List<Map<String, Object>>> timeline(@PathVariable Long memberId) {
        return Result.success(taskService.getMemberTimeline(memberId));
    }

    @GetMapping("/kanban/grouped")
    public Result<Map<String, Object>> kanban(@RequestParam(required = false) Long storeId) {
        return Result.success(taskService.kanbanGrouped(storeId));
    }

    @PostMapping("/batch/assign")
    public Result<Map<String, Object>> batchAssign(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        Object ownerIdObj = body.get("ownerId");
        Long ownerId = ownerIdObj == null ? null : Long.valueOf(ownerIdObj.toString());
        Object ownerNameObj = body.get("ownerName");
        String ownerName = ownerNameObj == null ? null : ownerNameObj.toString();
        List<Long> ids = idsRaw == null ? List.of() : idsRaw.stream().map(Number::longValue).toList();
        return Result.success(taskService.batchAssign(ids, ownerId, ownerName));
    }

    @PostMapping("/batch/finish")
    public Result<Map<String, Object>> batchFinish(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        List<Long> ids = idsRaw == null ? List.of() : idsRaw.stream().map(Number::longValue).toList();
        return Result.success(taskService.batchFinish(ids));
    }

    @PostMapping("/batch/close")
    public Result<Map<String, Object>> batchClose(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> idsRaw = (List<Number>) body.get("ids");
        List<Long> ids = idsRaw == null ? List.of() : idsRaw.stream().map(Number::longValue).toList();
        return Result.success(taskService.batchClose(ids));
    }

    @PostMapping("/demo-seed")
    public Result<Map<String, Object>> demoSeed() {
        int count = taskService.seedDemo();
        Map<String, Object> data = Map.of("insertedCount", count);
        return Result.success("演示数据已插入", data);
    }
}
