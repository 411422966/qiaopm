package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MemberTag;
import com.meiyetec.service.MemberTagService;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/member/tag")
@RequiredArgsConstructor
@SysOpLog(module = "会员")
public class MemberTagController {

    private final MemberTagService tagService;

    @GetMapping("/list")
    @RequirePermission("member:tag")
    public Result<List<MemberTag>> list() {
        return Result.success(tagService.list(
            new LambdaQueryWrapper<MemberTag>()
                .eq(MemberTag::getStatus, 1)
                .orderByAsc(MemberTag::getSortOrder)));
    }

    @PostMapping
    @RequirePermission("member:tag")
    public Result<Void> create(@RequestBody MemberTag tag) {
        if (tag.getStatus() == null) tag.setStatus(1);
        tagService.save(tag);
        return Result.success("创建成功", null);
    }

    @PutMapping
    @RequirePermission("member:tag")
    public Result<Void> update(@RequestBody MemberTag tag) {
        tagService.updateById(tag);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("member:tag")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.success("删除成功", null);
    }
}


