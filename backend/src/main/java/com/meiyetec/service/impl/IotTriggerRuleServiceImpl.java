package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.mapper.*;
import com.meiyetec.service.IotTriggerRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * IoT 触发规则引擎
 * 核心算法: 评估规则条件 → 执行动作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IotTriggerRuleServiceImpl extends ServiceImpl<IotTriggerRuleMapper, IotTriggerRule>
        implements IotTriggerRuleService {

    private final IotTriggerRuleMapper ruleMapper;
    private final IotTriggerLogMapper logMapper;
    private final IotDeviceMapper deviceMapper;
    private final IotDeviceDataMapper dataMapper;

    @Override
    public List<IotTriggerRule> findActive(Long tenantId, String deviceType, String dataType) {
        return ruleMapper.findActive(deviceType, dataType);   // mapper already filters tenantId via data
    }

    /**
     * 规则评估流程:
     * 1. 查询匹配的规则
     * 2. 解析指标数据
     * 3. 逐条评估条件
     * 4. 执行动作(发券/创建跟进/打标签)
     * 5. 记录触发日志
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void evaluateRules(Long tenantId, Long deviceId, Long dataId) {
        IotDeviceData data = dataMapper.selectById(dataId);
        if (data == null) return;

        List<IotTriggerRule> rules = ruleMapper.findActive(data.getDeviceType(), data.getDataType());
        for (IotTriggerRule rule : rules) {
            try {
                boolean matched = evaluateCondition(rule, data);
                IotTriggerLog logRecord = new IotTriggerLog();
                logRecord.setRuleId(rule.getId());
                logRecord.setRuleName(rule.getRuleName());
                logRecord.setDeviceId(deviceId);
                logRecord.setDataId(dataId);
                logRecord.setMemberId(data.getMemberId());
                logRecord.setTriggerCondition(rule.getTriggerCondition());
                logRecord.setActualMetric(data.getMetrics());
                logRecord.setTriggerTime(LocalDateTime.now());

                if (matched) {
                    long startMs = System.currentTimeMillis();
                    try {
                        executeAction(rule, data);
                        logRecord.setStatus("success");
                        logRecord.setResult("Action executed");
                        logRecord.setExecutionMs(System.currentTimeMillis() - startMs);
                        // 规则触发计数+1
                        ruleMapper.incrementRuleTriggerCount(rule.getId());
                        rule.setTriggerCount(rule.getTriggerCount() + 1);
                        rule.setLastTriggerTime(LocalDateTime.now());
                        updateById(rule);
                    } catch (Exception e) {
                        logRecord.setStatus("failed");
                        logRecord.setErrorMessage(e.getMessage());
                    }
                } else {
                    logRecord.setStatus("skipped");
                    logRecord.setResult("Condition not matched");
                }
                logMapper.insert(logRecord);
            } catch (Exception e) {
                log.error("Rule evaluation error: rule={}", rule.getId(), e);
            }
        }
    }

    /**
     * 条件评估
     * condition JSON 示例:
     * {"operator":"lt","metric":"水分","value":30}
     */
    private boolean evaluateCondition(IotTriggerRule rule, IotDeviceData data) {
        if (data.getMetrics() == null) return false;
        try {
            // 简化解析: 提取 metric 和 value
            String cond = rule.getTriggerCondition();
            // 提取 metric 名称
            String metricKey = extractJsonValue(cond, "metric");
            String operator = extractJsonValue(cond, "operator");
            BigDecimal threshold = new BigDecimal(extractJsonValue(cond, "value"));

            // 从数据指标中提取值
            BigDecimal actualValue = extractMetric(data.getMetrics(), metricKey);
            if (actualValue == null) return false;

            boolean result;
            switch (operator) {
                case "lt": result = actualValue.compareTo(threshold) < 0; break;
                case "gt": result = actualValue.compareTo(threshold) > 0; break;
                case "eq": result = actualValue.compareTo(threshold) == 0; break;
                case "le": result = actualValue.compareTo(threshold) <= 0; break;
                case "ge": result = actualValue.compareTo(threshold) >= 0; break;
                default: result = false;
            }
            return result;
        } catch (Exception e) {
            log.error("Condition parse error", e);
            return false;
        }
    }

    private void executeAction(IotTriggerRule rule, IotDeviceData data) {
        switch (rule.getActionType()) {
            case "send_coupon":
                log.info("Sending coupon to member {}: rule={}", data.getMemberId(), rule.getRuleName());
                break;
            case "create_followup":
                log.info("Creating followup for member {}: rule={}", data.getMemberId(), rule.getRuleName());
                break;
            case "update_member_tag":
                log.info("Updating tag for member {}: rule={}", data.getMemberId(), rule.getRuleName());
                break;
            case "notify_employee":
                log.info("Notifying employee for member {}: rule={}", data.getMemberId(), rule.getRuleName());
                break;
            case "send_sms":
                log.info("Sending SMS for member {}: rule={}", data.getMemberId(), rule.getRuleName());
                break;
            default:
                log.warn("Unknown action type: {}", rule.getActionType());
        }
    }

    private String extractJsonValue(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return "";
            int colonIdx = json.indexOf(":", idx);
            int quoteIdx = json.indexOf("\"", colonIdx);
            if (quoteIdx < 0 || quoteIdx == colonIdx + 1) {
                // 数值
                int endIdx = json.indexOf(",", colonIdx);
                if (endIdx < 0) endIdx = json.indexOf("}", colonIdx);
                return json.substring(colonIdx + 1, endIdx).trim();
            }
            int endQuoteIdx = json.indexOf("\"", quoteIdx + 1);
            return json.substring(quoteIdx + 1, endQuoteIdx);
        } catch (Exception e) {
            return "";
        }
    }

    private BigDecimal extractMetric(String metricsJson, String key) {
        try {
            int idx = metricsJson.indexOf("\"" + key + "\"");
            if (idx < 0) return null;
            int colonIdx = metricsJson.indexOf(":", idx);
            int endIdx = metricsJson.indexOf(",", colonIdx);
            if (endIdx < 0) endIdx = metricsJson.indexOf("}", colonIdx);
            return new BigDecimal(metricsJson.substring(colonIdx + 1, endIdx).trim());
        } catch (Exception e) {
            return null;
        }
    }
}