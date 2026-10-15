package com.meiyetec.service.impl;

import com.meiyetec.engine.CareSuggestionEngine;
import com.meiyetec.entity.CareSuggestionLog;
import com.meiyetec.entity.CareSuggestionRule;
import com.meiyetec.entity.CareSuggestionTemplate;
import com.meiyetec.entity.MemberSkinProfile;
import com.meiyetec.mapper.CareSuggestionLogMapper;
import com.meiyetec.mapper.CareSuggestionRuleMapper;
import com.meiyetec.mapper.CareSuggestionTemplateMapper;
import com.meiyetec.mapper.MemberSkinProfileMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CareSuggestionEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareSuggestionEngineServiceImpl implements CareSuggestionEngineService {

    private final CareSuggestionRuleMapper ruleMapper;
    private final CareSuggestionTemplateMapper templateMapper;
    private final CareSuggestionLogMapper logMapper;
    private final MemberSkinProfileMapper skinProfileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<CareSuggestionLog> generateForMember(Long memberId, Long operatorId, String operatorName, String triggerType) {
        if (memberId == null) return new ArrayList<>();
        CareSuggestionEngine.MemberContext context = buildContext(memberId, operatorName);
        if (context == null) return new ArrayList<>();
        Long tenantId = tid();
        List<CareSuggestionRule> rules = ruleMapper.findActiveRules(tenantId);
        List<CareSuggestionLog> generatedLogs = new ArrayList<>();
        for (CareSuggestionRule rule : rules) {
            try {
                CareSuggestionTemplate template = templateMapper.selectById(rule.getTemplateId());
                if (template == null || template.getStatus() == 0) continue;
                CareSuggestionLog recent = logMapper.findRecentByRule(tenantId, memberId, rule.getId());
                if (!CareSuggestionEngine.checkCooldown(recent, rule.getCooldownDays())) {
                    log.debug("规则 {} 对客户 {} 处于冷却期", rule.getId(), memberId);
                    continue;
                }
                CareSuggestionEngine.RuleMatchResult match =
                        CareSuggestionEngine.matchRule(rule, template, context);
                if (!match.matched) continue;
                CareSuggestionLog logEntry = CareSuggestionEngine.buildLog(rule, template, context, triggerType);
                logEntry.setOperatorId(operatorId);
                logEntry.setOperatorName(operatorName);
                logEntry.setTriggerReason(match.matchReason);
                String rendered = CareSuggestionEngine.renderTemplate(template.getContent(), match.templateVars);
                logEntry.setContent(rendered);
                logEntry.setTenantId(tenantId);
                logMapper.insert(logEntry);
                generatedLogs.add(logEntry);
            } catch (Exception e) {
                log.error("生成建议失败: rule={}, member={}", rule.getId(), memberId, e);
            }
        }
        return generatedLogs;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateForMembers(List<Long> memberIds, Long operatorId, String operatorName) {
        if (memberIds == null || memberIds.isEmpty()) return 0;
        int total = 0;
        for (Long memberId : memberIds) {
            try {
                List<CareSuggestionLog> logs = generateForMember(memberId, operatorId, operatorName, "auto");
                total += logs.size();
            } catch (Exception e) {
                log.error("批量生成建议失败: member={}", memberId, e);
            }
        }
        return total;
    }

    private CareSuggestionEngine.MemberContext buildContext(Long memberId, String operatorName) {
        CareSuggestionEngine.MemberContext ctx = new CareSuggestionEngine.MemberContext();
        ctx.memberId = memberId;
        ctx.memberName = "客户" + memberId;
        ctx.season = currentSeason();
        MemberSkinProfile profile = skinProfileMapper.findLatestByMember(memberId);
        ctx.skinProfile = profile;
        ctx.tags = new ArrayList<>();
        ctx.age = 30;
        ctx.memberLevel = "VIP";
        return ctx;
    }

    private String currentSeason() {
        int month = LocalDate.now().getMonthValue();
        if (month >= 3 && month <= 5) return "spring";
        if (month >= 6 && month <= 8) return "summer";
        if (month >= 9 && month <= 11) return "autumn";
        return "winter";
    }

    private Long tid() {
        Long t = TenantContextHolder.get().getTenantId();
        return t == null ? 1L : t;
    }
}

