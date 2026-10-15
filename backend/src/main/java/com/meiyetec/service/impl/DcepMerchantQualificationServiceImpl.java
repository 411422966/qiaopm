package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.DcepMerchantQualification;
import com.meiyetec.entity.DcepQualificationAttachment;
import com.meiyetec.entity.DcepConfig;
import com.meiyetec.mapper.DcepMerchantQualificationMapper;
import com.meiyetec.mapper.DcepQualificationAttachmentMapper;
import com.meiyetec.service.DcepConfigService;
import com.meiyetec.service.DcepMerchantQualificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DcepMerchantQualificationServiceImpl
        extends ServiceImpl<DcepMerchantQualificationMapper, DcepMerchantQualification>
        implements DcepMerchantQualificationService {

    private final DcepConfigService configService;
    private final DcepQualificationAttachmentMapper attachmentMapper;

    @Override
    public DcepMerchantQualification currentOrInit() {
        DcepMerchantQualification q = lambdaQuery().last("LIMIT 1").one();
        if (q != null) {
            q.setAttachments(loadAttachments(q.getId()));
            return q;
        }
        // 还没有资质记录：尝试绑定当前 DCEP 配置
        DcepConfig cfg = configService.list().stream().findFirst().orElse(null);
        DcepMerchantQualification empty = new DcepMerchantQualification();
        empty.setConfigId(cfg != null ? cfg.getId() : null);
        empty.setAttachments(Collections.emptyList());
        empty.setAuditStatus(0);
        return empty;
    }

    public List<DcepQualificationAttachment> loadAttachments(Long qualificationId) {
        if (qualificationId == null) return Collections.emptyList();
        return attachmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DcepQualificationAttachment>()
                        .eq("qualification_id", qualificationId)
                        .orderByAsc("material_type, sort_no, id"));
    }

    /**
     * 保存资质：自动绑定到当前 DCEP 配置的 id（首次保存时使用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public DcepMerchantQualification saveQualification(DcepMerchantQualification incoming) {
        // 凭据敏感字段只在用户提交新原文时重新掩码；已掩码值直接保留，避免重复破坏。
        if (incoming.getLegalIdNo() != null && !isMaskedId(incoming.getLegalIdNo())) {
            incoming.setLegalIdNo(maskId(incoming.getLegalIdNo()));
        }
        if (incoming.getBankAccountNo() != null && !isMaskedBank(incoming.getBankAccountNo())) {
            incoming.setBankAccountNo(maskBank(incoming.getBankAccountNo()));
        }
        DcepConfig cfg = configService.list().stream().findFirst().orElse(null);
        if (cfg != null) incoming.setConfigId(cfg.getId());

        DcepMerchantQualification existing = lambdaQuery().last("LIMIT 1").one();
        if (existing != null && Integer.valueOf(1).equals(existing.getAuditStatus())) {
            throw new IllegalStateException("资质审核中，暂不可修改");
        }
        if (existing == null) {
            incoming.setId(null);
            incoming.setUpdatedAt(null);
            save(incoming);
        } else {
            incoming.setId(existing.getId());
            incoming.setUpdatedAt(null);
            updateById(incoming);
        }
        return incoming;
    }

    private static boolean isMaskedId(String value) {
        return value.contains("**********");
    }

    private static boolean isMaskedBank(String value) {
        return value.contains("****");
    }

    private static String maskId(String s) {
        if (s == null || s.length() < 8) return s;
        return s.substring(0, 4) + "**********" + s.substring(s.length() - 4);
    }

    private static String maskBank(String s) {
        if (s == null || s.length() < 8) return s;
        return s.substring(0, 4) + " **** **** " + s.substring(s.length() - 4);
    }
}