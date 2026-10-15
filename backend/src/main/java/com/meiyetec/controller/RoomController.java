package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.Room;
import com.meiyetec.service.RoomService;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * M06 房间 CRUD
 */
@RestController
@RequestMapping("/room")
@RequiredArgsConstructor
@SysOpLog(module = "房间")
public class RoomController {

    private final RoomService roomService;

    @GetMapping("/list")
    public Result<List<Room>> list(@RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Room> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(Room::getStatus, status);
        qw.orderByAsc(Room::getSortNo);
        return Result.success(roomService.list(qw));
    }

    @GetMapping("/{id:\\d+}")
    public Result<Room> get(@PathVariable Long id) {
        return Result.success(roomService.getById(id));
    }

    @PostMapping
    public Result<Room> create(@RequestBody Room r) {
        if (r.getRoomName() == null || r.getRoomName().isEmpty()) {
            return Result.error("房间名称必填");
        }
        roomService.save(r);
        return Result.success("房间创建成功", r);
    }

    @PutMapping("/{id:\\d+}")
    public Result<Room> update(@PathVariable Long id, @RequestBody Room r) {
        r.setId(id);
        roomService.updateById(r);
        return Result.success("房间更新成功", r);
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        roomService.removeById(id);
        return Result.success("删除成功", null);
    }
}


