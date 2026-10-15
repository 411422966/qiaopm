package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.MhConsentSignature;
import com.meiyetec.entity.MhConsentSignLink;
import com.meiyetec.entity.MhConsentTemplate;
import com.meiyetec.mapper.MhConsentSignLinkMapper;
import com.meiyetec.mapper.MhConsentSignatureMapper;
import com.meiyetec.mapper.MhConsentTemplateMapper;
import com.meiyetec.exception.BizCode;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * COMP-002 H5 知情同意签署端点
 *  - GET  /mh/consent/sign-link/{token}              查询签署链接（含模板+会员信息）
 *  - POST /mh/consent/sign-link/{token}/submit      提交签署（写入签名、关闭 link）
 *
 * <p>该功能原先由 ConsentAndDrugDeviceService 承载，但未实现 /sign-link/* 端点。
 * 此处独立 controller，避免改动已稳定的 ConsentAndDrugDeviceService。
 */
@Slf4j
@RestController
@RequestMapping("/mh/consent")
@RequiredArgsConstructor
public class MhConsentH5Controller {

    private final MhConsentSignLinkMapper linkMapper;
    private final MhConsentTemplateMapper templateMapper;
    private final MhConsentSignatureMapper signatureMapper;

    /**
     * 通过 token 查询签署链接及模板预览信息
     */
    @GetMapping("/sign-link/{token}")
    public Result<Map<String, Object>> getByToken(@PathVariable String token) {
        if (token == null || token.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "token 必填");

        MhConsentSignLink link = linkMapper.selectOne(
                new LambdaQueryWrapper<MhConsentSignLink>()
                        .eq(MhConsentSignLink::getToken, token)
                        .eq(MhConsentSignLink::getDeleted, 0));
        if (link == null) {
            return Result.error(BizCode.NOT_FOUND, "链接不存在");
        }

        // 状态校验：1=待签 2=已签 3=已过期 4=已撤销
        if (link.getStatus() != null && link.getStatus() == 2) {
            return Result.error(BizCode.CONFLICT, "该链接已被使用");
        }
        if (link.getStatus() != null && link.getStatus() == 4) {
            return Result.error(BizCode.CONFLICT, "该链接已被撤销");
        }
        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            return Result.error(BizCode.CONFLICT, "该链接已过期");
        }

        // 拉模板
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("memberName", link.getMemberName());
        info.put("memberId", link.getMemberId());
        info.put("templateId", link.getTemplateId());
        info.put("templateCode", link.getTemplateCode());
        info.put("templateVersion", link.getTemplateVersion());
        info.put("projectId", link.getProjectId());
        info.put("projectName", link.getProjectName());
        info.put("expireAt", link.getExpireAt());

        if (link.getTemplateId() != null) {
            MhConsentTemplate tpl = templateMapper.selectById(link.getTemplateId());
            if (tpl != null) {
                info.put("templateName", tpl.getTemplateName());
                info.put("contentMd", tpl.getContentMd());
                info.put("requiredItems", tpl.getRequiredItems());
                info.put("legalBasis", tpl.getLegalBasis());
            }
        }
        return Result.success(info);
    }

    /**
     * 提交签署：写入 mh_consent_signature，关闭 link
     */
    @PostMapping("/sign-link/{token}/submit")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> submit(@PathVariable String token,
                                               @RequestBody Map<String, Object> body) {
        if (token == null || token.isEmpty())
            return Result.error(BizCode.BAD_REQUEST, "token 必填");

        MhConsentSignLink link = linkMapper.selectOne(
                new LambdaQueryWrapper<MhConsentSignLink>()
                        .eq(MhConsentSignLink::getToken, token)
                        .eq(MhConsentSignLink::getDeleted, 0));
        if (link == null) {
            return Result.error(BizCode.NOT_FOUND, "链接不存在");
        }
        if (link.getStatus() != null && link.getStatus() == 2) {
            return Result.error(BizCode.CONFLICT, "该链接已被使用");
        }
        if (link.getStatus() != null && link.getStatus() == 4) {
            return Result.error(BizCode.CONFLICT, "该链接已被撤销");
        }
        if (link.getExpireAt() != null && link.getExpireAt().isBefore(LocalDateTime.now())) {
            return Result.error(BizCode.CONFLICT, "该链接已过期");
        }

        // 必填校验
        String signerName = body.get("signerName") == null ? null : body.get("signerName").toString();
        if (signerName == null || signerName.trim().isEmpty()) {
            return Result.error(BizCode.BAD_REQUEST, "签署人姓名必填");
        }

        String signatureId = "SIG-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MhConsentSignature sig = new MhConsentSignature();
        sig.setTenantId(link.getTenantId());
        sig.setBrandId(link.getBrandId());
        sig.setStoreId(link.getStoreId());
        sig.setMemberId(link.getMemberId());
        sig.setTemplateId(link.getTemplateId());
        sig.setTemplateCode(link.getTemplateCode());
        sig.setTemplateVersion(link.getTemplateVersion());
        sig.setProjectId(link.getProjectId());
        sig.setProjectName(link.getProjectName());
        sig.setSignatureId(signatureId);
        sig.setSignerName(signerName);
        sig.setSignerIdNo(body.get("signerIdNo") == null ? null : body.get("signerIdNo").toString());
        String signerRelation = body.get("signerRelation") == null ? "SELF" : body.get("signerRelation").toString();
        sig.setSignerRelation(signerRelation);
        sig.setCheckedItems(body.get("checkedItems") == null ? null : body.get("checkedItems").toString());
        sig.setRemark(body.get("remark") == null ? null : body.get("remark").toString());
        sig.setSignIp(body.get("signIp") == null ? null : body.get("signIp").toString());
        sig.setSignDevice(body.get("signDevice") == null ? null : body.get("signDevice").toString());
        sig.setStatus(2);
        sig.setEffectiveAt(LocalDateTime.now());
        sig.setOperatorId(link.getOperatorId());
        sig.setOperatorName(link.getOperatorName());

        // signDocHash = SHA-256(token|sigId|singerName|signedAt)
        try {
            String raw = token + "|" + signatureId + "|" + signerName + "|" + sig.getEffectiveAt();
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            sig.setSignDocHash(sb.toString());
        } catch (Exception e) {
            log.warn("signDocHash 计算失败: {}", e.getMessage());
        }

        signatureMapper.insert(sig);

        // 关闭链接
        link.setStatus(2);
        link.setSignedAt(LocalDateTime.now());
        link.setSignatureId(sig.getId());
        link.setSignatureCode(signatureId);
        linkMapper.updateById(link);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("signatureId", signatureId);
        out.put("signedAt", sig.getEffectiveAt());
        out.put("signDocHash", sig.getSignDocHash());
        return Result.success("签署成功", out);
    }
}