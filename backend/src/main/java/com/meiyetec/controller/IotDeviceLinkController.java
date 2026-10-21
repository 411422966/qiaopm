package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.annotation.SysOpLog;
import com.meiyetec.entity.IotDeviceLink;
import com.meiyetec.entity.IotDeviceLinkLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.mapper.IotDeviceLinkLogMapper;
import com.meiyetec.service.IotDeviceLinkService;
import com.meiyetec.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IOT-006：设备联动 API（CRUD + 手动触发 + 执行日志查询）
 *
 * <p>端点：
 * <ul>
 *   <li>{@code GET    /iot/device/link/page}            分页查询联动配置</li>
 *   <li>{@code GET    /iot/device/link/list}            列表</li>
 *   <li>{@code GET    /iot/device/link/{id}}            详情</li>
 *   <li>{@code POST   /iot/device/link}                 创建联动</li>
 *   <li>{@code PUT    /iot/device/link/{id}}            更新联动</li>
 *   <li>{@code DELETE /iot/device/link/{id}}            删除联动</li>
 *   <li>{@code POST   /iot/device/link/execute}         手动触发单个联动</li>
 *   <li>{@code POST   /iot/device/link/execute-batch}   按主设备批量触发</li>
 *   <li>{@code GET    /iot/device/link/log/page}        联动执行日志分页</li>
 *   <li>{@code GET    /iot/device/link/log/{id}}        联动日志详情</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/iot/device/link")
@RequiredArgsConstructor
@SysOpLog(module = "IoT Device Link")
public class IotDeviceLinkController {

    private final IotDeviceLinkService linkService;
    private final IotDeviceLinkLogMapper logMapper;

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }

    private Long sid() {
        Long s = TenantContextHolder.getStoreId();
        return s == null ? 1L : s;
    }

    // ====================================================================
    // CRUD
    // ====================================================================

    @GetMapping("/page")
    @RequirePermission("iot:link:list")
    public Result<Page<IotDeviceLink>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long masterDeviceId,
            @RequestParam(required = false) Long slaveDeviceId,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) Integer enabled) {
        Page<IotDeviceLink> page = new Page<>(current, size);
        LambdaQueryWrapper<IotDeviceLink> qw = new LambdaQueryWrapper<>();
        qw.eq(IotDeviceLink::getTenantId, tid());
        if (masterDeviceId != null) qw.eq(IotDeviceLink::getMasterDeviceId, masterDeviceId);
        if (slaveDeviceId != null) qw.eq(IotDeviceLink::getSlaveDeviceId, slaveDeviceId);
        if (triggerType != null && !triggerType.isEmpty()) qw.eq(IotDeviceLink::getTriggerType, triggerType);
        if (enabled != null) qw.eq(IotDeviceLink::getEnabled, enabled);
        qw.orderByDesc(IotDeviceLink::getId);
        return Result.success(linkService.page(page, qw));
    }

    @GetMapping("/list")
    @RequirePermission("iot:link:list")
    public Result<List<IotDeviceLink>> list(
            @RequestParam(required = false) Long masterDeviceId,
            @RequestParam(required = false) Integer enabled) {
        LambdaQueryWrapper<IotDeviceLink> qw = new LambdaQueryWrapper<>();
        qw.eq(IotDeviceLink::getTenantId, tid());
        if (masterDeviceId != null) qw.eq(IotDeviceLink::getMasterDeviceId, masterDeviceId);
        if (enabled != null) qw.eq(IotDeviceLink::getEnabled, enabled);
        qw.orderByDesc(IotDeviceLink::getId);
        return Result.success(linkService.list(qw));
    }

    @GetMapping("/{id:\\d+}")
    @RequirePermission("iot:link:list")
    public Result<IotDeviceLink> getById(@PathVariable Long id) {
        IotDeviceLink link = linkService.getById(id);
        if (link == null || !tid().equals(link.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "联动不存在");
        }
        return Result.success(link);
    }

    @PostMapping
    @RequirePermission("iot:link:edit")
    public Result<IotDeviceLink> create(@Valid @RequestBody IotDeviceLink link) {
        link.setId(null);
        link.setTenantId(tid());
        link.setStoreId(sid());
        if (link.getEnabled() == null) link.setEnabled(1);
        linkService.save(link);
        return Result.success("创建成功", link);
    }

    @PutMapping("/{id:\\d+}")
    @RequirePermission("iot:link:edit")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody IotDeviceLink link) {
        IotDeviceLink exist = linkService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "联动不存在");
        }
        link.setId(id);
        link.setTenantId(tid());
        linkService.updateById(link);
        return Result.success("更新成功", null);
    }

    @DeleteMapping("/{id:\\d+}")
    @RequirePermission("iot:link:edit")
    public Result<Void> delete(@PathVariable Long id) {
        IotDeviceLink exist = linkService.getById(id);
        if (exist == null || !tid().equals(exist.getTenantId())) {
            return Result.error(BizCode.NOT_FOUND, "联动不存在");
        }
        linkService.removeById(id);
        return Result.success("已删除", null);
    }

    // ====================================================================
    // 手动触发（核心 API）
    // ====================================================================

    /**
     * 手动触发单个联动
     *
     * <p>请求体：
     * <pre>
     * {
     *   "linkId": 123,
     *   "triggerData": {
     *     "metrics": { "temperature": 38 },
     *     "state": "idle"
     *   }
     * }
     * </pre>
     */
    @PostMapping("/execute")
    @RequirePermission("iot:link:execute")
    public Result<IotDeviceLinkLog> execute(@RequestBody Map<String, Object> body) {
        Object linkIdObj = body.get("linkId");
        if (linkIdObj == null) {
            return Result.error(BizCode.BAD_REQUEST, "linkId 不能为空");
        }
        Long linkId = linkIdObj instanceof Number n ? n.longValue()
                : Long.parseLong(linkIdObj.toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> triggerData = (Map<String, Object>) body.getOrDefault("triggerData", new HashMap<>());
        IotDeviceLinkLog logRow = linkService.executeLink(linkId, triggerData);
        return Result.success("联动已触发", logRow);
    }

    /**
     * 按主设备批量触发所有启用联动
     *
     * <p>请求体：
     * <pre>
     * {
     *   "masterDeviceId": 100,
     *   "triggerType": "TELEMETRY",   // 可选
     *   "triggerData": { "metrics": { "temperature": 38 } }
     * }
     * </pre>
     */
    @PostMapping("/execute-batch")
    @RequirePermission("iot:link:execute")
    public Result<List<IotDeviceLinkLog>> executeBatch(@RequestBody Map<String, Object> body) {
        Object midObj = body.get("masterDeviceId");
        if (midObj == null) {
            return Result.error(BizCode.BAD_REQUEST, "masterDeviceId 不能为空");
        }
        Long masterDeviceId = midObj instanceof Number n ? n.longValue()
                : Long.parseLong(midObj.toString());
        String triggerType = body.get("triggerType") == null ? null : body.get("triggerType").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> triggerData = (Map<String, Object>) body.getOrDefault("triggerData", new HashMap<>());
        List<IotDeviceLinkLog> logs = linkService.executeLinksByMaster(masterDeviceId, triggerType, triggerData);
        return Result.success("已批量触发", logs);
    }

    // ====================================================================
    // 联动执行日志查询
    // ====================================================================

    @GetMapping("/log/page")
    @RequirePermission("iot:link:list")
    public Result<Page<IotDeviceLinkLog>> logPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long linkId,
            @RequestParam(required = false) Long masterDeviceId,
            @RequestParam(required = false) Long slaveDeviceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String protocol) {
        Page<IotDeviceLinkLog> page = new Page<>(current, size);
        LambdaQueryWrapper<IotDeviceLinkLog> qw = new LambdaQueryWrapper<>();
        qw.eq(IotDeviceLinkLog::getTenantId, tid());
        if (linkId != null) qw.eq(IotDeviceLinkLog::getLinkId, linkId);
        if (masterDeviceId != null) qw.eq(IotDeviceLinkLog::getMasterDeviceId, masterDeviceId);
        if (slaveDeviceId != null) qw.eq(IotDeviceLinkLog::getSlaveDeviceId, slaveDeviceId);
        if (status != null && !status.isEmpty()) qw.eq(IotDeviceLinkLog::getStatus, status);
        if (protocol != null && !protocol.isEmpty()) qw.eq(IotDeviceLinkLog::getProtocol, protocol);
        qw.orderByDesc(IotDeviceLinkLog::getId);
        return Result.success(logMapper.selectPage(page, qw));
    }

    @GetMapping("/log/{id:\\d+}")
    @RequirePermission("iot:link:list")
    public Result<IotDeviceLinkLog> logDetail(@PathVariable Long id) {
        IotDeviceLinkLog row = logMapper.selectById(id);
        if (row == null) return Result.error(BizCode.NOT_FOUND, "日志不存在");
        return Result.success(row);
    }
}