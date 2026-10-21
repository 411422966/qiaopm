package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.meiyetec.entity.DcepMerchantQualification;
import com.meiyetec.entity.DcepQualificationAttachment;
import com.meiyetec.mapper.DcepQualificationAttachmentMapper;
import com.meiyetec.service.DcepMerchantQualificationService;
import com.meiyetec.service.FileStorageService;
import com.meiyetec.service.impl.DcepMerchantQualificationServiceImpl;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/sys/dcep/qualification")
@RequiredArgsConstructor
public class DcepMerchantQualificationController {

    private final DcepMerchantQualificationService service;
    private final DcepMerchantQualificationServiceImpl serviceImpl;
    private final DcepQualificationAttachmentMapper attachmentMapper;
    private final FileStorageService storage;

    @GetMapping("/current")
    @RequirePermission("dcep:qualification:list")
    public Result<Map<String, Object>> current() {
        DcepMerchantQualification q = service.currentOrInit();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", q.getId());
        view.put("configId", q.getConfigId());
        view.put("legalPerson", q.getLegalPerson());
        view.put("legalIdNo", q.getLegalIdNo());
        view.put("legalIdFront", storage.url(q.getLegalIdFront()));
        view.put("legalIdBack", storage.url(q.getLegalIdBack()));
        view.put("businessLicenseNo", q.getBusinessLicenseNo());
        view.put("businessLicenseImg", storage.url(q.getBusinessLicenseImg()));
        view.put("orgCode", q.getOrgCode());
        view.put("taxNo", q.getTaxNo());
        view.put("bankAccountName", q.getBankAccountName());
        view.put("bankAccountNo", q.getBankAccountNo());
        view.put("bankName", q.getBankName());
        view.put("bankBranch", q.getBankBranch());
        view.put("contactPerson", q.getContactPerson());
        view.put("contactPhone", q.getContactPhone());
        view.put("contactEmail", q.getContactEmail());
        view.put("address", q.getAddress());
        view.put("remark", q.getRemark());
        view.put("auditStatus", q.getAuditStatus());
        view.put("auditRemark", q.getAuditRemark());
        view.put("auditAt", q.getAuditAt());
        view.put("createdAt", q.getCreatedAt());
        view.put("updatedAt", q.getUpdatedAt());

        // 附件统一加 url
        List<Map<String, Object>> atts = new ArrayList<>();
        if (q.getAttachments() != null) {
            for (DcepQualificationAttachment a : q.getAttachments()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.getId());
                m.put("materialType", a.getMaterialType());
                m.put("fileName", a.getFileName());
                m.put("fileSize", a.getFileSize());
                m.put("contentType", a.getContentType());
                m.put("sortNo", a.getSortNo());
                m.put("url", storage.url(a.getFilePath()));
                m.put("createdAt", a.getCreatedAt());
                atts.add(m);
            }
        }
        view.put("attachments", atts);
        return Result.success(view);
    }

    @PostMapping
    @RequirePermission("dcep:qualification:edit")
    public Result<DcepMerchantQualification> save(@RequestBody DcepMerchantQualification body) {
        // 接收端不接收原始敏感号，前端用 mask 提交（如不需要二次确认保护即可在 service 层 decode）
        DcepMerchantQualification saved = serviceImpl.saveQualification(body);
        return Result.success("保存成功", saved);
    }

    /**
     * 上传附件（材料图）
     * @param qualificationId 资质记录 id（可为 null，表示先建空记录再上传）
     * @param materialType legal_id_front / legal_id_back / business_license / other
     */
    @PostMapping("/upload")
    @RequirePermission("dcep:qualification:edit")
    public Result<Map<String, Object>> upload(
            @RequestParam(required = false) Long qualificationId,
            @RequestParam String materialType,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (!Set.of("legal_id_front", "legal_id_back", "business_license", "other").contains(materialType)) {
            return Result.error("不支持的材料类型");
        }
        if (file.getSize() > 10 * 1024 * 1024L) {
            return Result.error("单文件不能超过 10MB");
        }
        DcepMerchantQualification current = service.lambdaQuery().last("LIMIT 1").one();
        if (current != null && Integer.valueOf(1).equals(current.getAuditStatus())) {
            return Result.error("资质审核中，暂不可上传材料");
        }

        String relPath = storage.store(materialType, file);

        // 自动创建资质记录（如未建）
        if (qualificationId == null) {
            DcepMerchantQualification existing = service.lambdaQuery().last("LIMIT 1").one();
            if (existing == null) {
                DcepMerchantQualification blank = new DcepMerchantQualification();
                serviceImpl.saveQualification(blank);
                existing = service.lambdaQuery().last("LIMIT 1").one();
            }
            qualificationId = existing != null ? existing.getId() : null;
        }

        DcepQualificationAttachment att = new DcepQualificationAttachment();
        att.setQualificationId(qualificationId);
        att.setMaterialType(materialType);
        att.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        att.setFilePath(relPath);
        att.setFileSize(file.getSize());
        att.setContentType(file.getContentType());
        att.setSortNo((int) System.currentTimeMillis() / 1000);
        attachmentMapper.insert(att);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", att.getId());
        r.put("url", storage.url(relPath));
        r.put("fileName", att.getFileName());
        r.put("fileSize", att.getFileSize());
        r.put("materialType", materialType);
        r.put("qualificationId", qualificationId);
        return Result.success("上传成功", r);
    }

    @DeleteMapping("/attachment/{id}")
    @RequirePermission("dcep:qualification:edit")
    public Result<Void> deleteAttachment(@PathVariable Long id) {
        DcepMerchantQualification current = service.lambdaQuery().last("LIMIT 1").one();
        if (current != null && Integer.valueOf(1).equals(current.getAuditStatus())) {
            return Result.error("资质审核中，暂不可删除材料");
        }
        DcepQualificationAttachment att = attachmentMapper.selectById(id);
        if (att != null) {
            storage.delete(att.getFilePath());
            attachmentMapper.deleteById(id);
        }
        return Result.success("已删除", null);
    }

    /** 提交审核（待提交→审核中） */
    @PostMapping("/submit")
    @RequirePermission("dcep:qualification:submit")
    public Result<Void> submit() {
        DcepMerchantQualification q = service.lambdaQuery().last("LIMIT 1").one();
        if (q == null) return Result.error("请先填写并保存资质材料");
        if (Integer.valueOf(1).equals(q.getAuditStatus())) return Result.error("资质已在审核中");
        if (Integer.valueOf(2).equals(q.getAuditStatus())) return Result.error("资质已审核通过");
        if (isBlank(q.getLegalPerson()) || isBlank(q.getLegalIdNo())
                || isBlank(q.getBusinessLicenseNo()) || isBlank(q.getBankAccountName())
                || isBlank(q.getBankAccountNo()) || isBlank(q.getContactPerson())
                || isBlank(q.getContactPhone())) {
            return Result.error("请完整填写法人、营业执照、银行账户和联系人必填信息");
        }
        long requiredAttachmentCount = serviceImpl.loadAttachments(q.getId()).stream()
                .map(DcepQualificationAttachment::getMaterialType)
                .filter(Set.of("legal_id_front", "legal_id_back", "business_license")::contains)
                .distinct()
                .count();
        if (requiredAttachmentCount < 3) {
            return Result.error("请上传身份证正反面和营业执照图片");
        }
        q.setAuditStatus(1);
        q.setAuditRemark(null);
        service.updateById(q);
        return Result.success("已提交审核", null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}