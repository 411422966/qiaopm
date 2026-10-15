package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.IotDevice;
import com.meiyetec.entity.IotDeviceLink;
import com.meiyetec.entity.IotDeviceLinkLog;
import com.meiyetec.iot.DeviceCommand;
import com.meiyetec.iot.DeviceCommandDispatcher;
import com.meiyetec.iot.DeviceCommandResult;
import com.meiyetec.iot.LinkConditionEvaluator;
import com.meiyetec.mapper.IotDeviceLinkLogMapper;
import com.meiyetec.mapper.IotDeviceLinkMapper;
import com.meiyetec.mapper.IotDeviceMapper;
import com.meiyetec.service.IotDeviceLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IOT-006：设备联动执行服务
 *
 * <p>设备联动执行流程：
 * <ol>
 *   <li>根据 linkId 查询联动配置</li>
 *   <li>校验联动启用状态（enabled=1）</li>
 *   <li>解析 triggerCondition（DSL 表达式），与 triggerData 比对</li>
 *   <li>条件不满足 → 记录 skipped 日志后返回</li>
 *   <li>条件满足 → 构造 DeviceCommand，从 actionJson 提取参数</li>
 *   <li>调用 {@link DeviceCommandDispatcher} 按从设备协议下发命令</li>
 *   <li>记录 success / failed / timeout 日志（含耗时、错误码）</li>
 * </ol>
 */
@Slf4j
@Service
public class IotDeviceLinkServiceImpl extends ServiceImpl<IotDeviceLinkMapper, IotDeviceLink>
        implements IotDeviceLinkService {

    @Autowired private IotDeviceLinkLogMapper linkLogMapper;
    @Autowired private IotDeviceMapper deviceMapper;
    @Autowired private DeviceCommandDispatcher commandDispatcher;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * 执行联动
     *
     * @param linkId      联动关系 ID
     * @param triggerData 触发数据快照（如温度告警、用户指令、设备事件等）
     * @return 联动执行日志（包含成功 / 失败 / skipped 状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public IotDeviceLinkLog executeLink(Long linkId, Map<String, Object> triggerData) {
        long start = System.currentTimeMillis();
        IotDeviceLink link = getById(linkId);
        IotDeviceLinkLog logRow = newLinkLog(linkId, triggerData);

        if (link == null) {
            return finalizeLog(logRow, null, false, "LINK_NOT_FOUND", "联动关系不存在",
                    System.currentTimeMillis() - start);
        }
        // 0) 联动必须启用
        if (link.getEnabled() == null || link.getEnabled() != 1) {
            return finalizeLog(logRow, link, false, "LINK_DISABLED", "联动已停用",
                    System.currentTimeMillis() - start);
        }
        logRow.setMasterDeviceId(link.getMasterDeviceId());
        logRow.setSlaveDeviceId(link.getSlaveDeviceId());
        logRow.setTriggerType(link.getTriggerType());
        logRow.setTenantId(link.getTenantId());
        logRow.setStoreId(link.getStoreId());

        // 1) 条件评估
        boolean matched = LinkConditionEvaluator.matches(link.getTriggerCondition(), triggerData);
        logRow.setTriggerMatched(matched ? 1 : 0);
        if (!matched) {
            return finalizeLog(logRow, link, false, "CONDITION_NOT_MET",
                    "触发条件未满足：" + link.getTriggerCondition(),
                    System.currentTimeMillis() - start);
        }

        // 2) 解析命令
        DeviceCommand command = parseCommand(link);
        // 注入 correlationId
        String correlationId = UUID.randomUUID().toString();
        command.setCorrelationId(correlationId);
        try {
            logRow.setCommandPayload(json.writeValueAsString(command));
        } catch (Exception e) {
            logRow.setCommandPayload("{}");
        }

        // 3) 查询从设备
        IotDevice slaveDevice = deviceMapper.selectById(link.getSlaveDeviceId());
        if (slaveDevice == null) {
            return finalizeLog(logRow, link, false, "SLAVE_DEVICE_NOT_FOUND",
                    "从设备不存在 id=" + link.getSlaveDeviceId(),
                    System.currentTimeMillis() - start);
        }

        // 4) 下发命令
        DeviceCommandResult result = commandDispatcher.dispatch(slaveDevice, command, link.getActionJson());
        long duration = System.currentTimeMillis() - start;

        // 5) 记录日志
        logRow.setProtocol(slaveDevice.getProtocol());
        logRow.setDeviceTopic(result.getDeviceTopic());
        logRow.setDurationMs(duration);
        logRow.setResponseSummary(truncate(result.getDeviceAck(), 500));
        return finalizeLog(logRow, link, result.isSuccess(),
                result.getErrorCode(), result.getErrorMessage(), duration);
    }

    /**
     * 批量执行（同一主设备的全部联动）
     */
    @Transactional(rollbackFor = Exception.class)
    public java.util.List<IotDeviceLinkLog> executeLinksByMaster(Long masterDeviceId,
                                                                   String triggerType,
                                                                   Map<String, Object> triggerData) {
        java.util.List<IotDeviceLink> links = lambdaQuery()
                .eq(IotDeviceLink::getMasterDeviceId, masterDeviceId)
                .eq(IotDeviceLink::getEnabled, 1)
                .list();
        java.util.List<IotDeviceLinkLog> results = new java.util.ArrayList<>();
        for (IotDeviceLink link : links) {
            if (triggerType == null || triggerType.equals(link.getTriggerType())) {
                results.add(executeLink(link.getId(), triggerData));
            }
        }
        return results;
    }

    // ============================================================
    // 私有工具
    // ============================================================

    private IotDeviceLinkLog newLinkLog(Long linkId, Map<String, Object> triggerData) {
        IotDeviceLinkLog logRow = new IotDeviceLinkLog();
        logRow.setLinkId(linkId);
        try {
            logRow.setTriggerSnapshot(triggerData == null ? "{}" : json.writeValueAsString(triggerData));
        } catch (Exception e) {
            logRow.setTriggerSnapshot("{}");
        }
        logRow.setStatus("pending");
        return logRow;
    }

    private DeviceCommand parseCommand(IotDeviceLink link) {
        DeviceCommand cmd = new DeviceCommand();
        cmd.setCommandType("raw");
        cmd.setTimeoutSeconds(5);
        cmd.setParams(new HashMap<>());
        if (link.getActionJson() == null || link.getActionJson().isEmpty()) {
            return cmd;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = json.readValue(link.getActionJson(), Map.class);
            Object t = m.get("commandType");
            if (t != null) cmd.setCommandType(t.toString());
            Object p = m.get("params");
            if (p instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) p;
                cmd.setParams(params);
            }
            Object ts = m.get("timeoutSeconds");
            if (ts instanceof Number) cmd.setTimeoutSeconds(((Number) ts).intValue());
        } catch (Exception e) {
            log.warn("[IOT-006] 解析 actionJson 失败：linkId={} err={}", link.getId(), e.getMessage());
        }
        return cmd;
    }

    private IotDeviceLinkLog finalizeLog(IotDeviceLinkLog logRow, IotDeviceLink link,
                                          boolean success, String errorCode, String errorMessage,
                                          long durationMs) {
        logRow.setStatus(success ? "success" : (errorCode != null && errorCode.contains("TIMEOUT")
                ? "timeout" : "failed"));
        logRow.setErrorCode(errorCode);
        logRow.setErrorMessage(errorMessage);
        if (logRow.getDurationMs() == null) {
            logRow.setDurationMs(durationMs);
        }
        // 仅当 link 真实存在时入库（"联动不存在/已停用"场景不入库，避免脏数据 & 避免外键悬挂）
        if (link != null) {
            try {
                linkLogMapper.insert(logRow);
            } catch (Exception e) {
                log.warn("[IOT-006] 写入联动日志失败：{}", e.getMessage());
            }
        }
        if (success) {
            log.info("[IOT-006] 联动执行成功：linkId={} slave={} cmd={} durationMs={}",
                    link == null ? null : link.getId(),
                    link == null ? null : link.getSlaveDeviceId(),
                    link == null ? null : link.getActionJson(), durationMs);
        } else {
            log.info("[IOT-006] 联动执行{}：linkId={} slave={} code={} msg={} durationMs={}",
                    logRow.getStatus(),
                    link == null ? null : link.getId(),
                    link == null ? null : link.getSlaveDeviceId(),
                    errorCode, errorMessage, durationMs);
        }
        return logRow;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
