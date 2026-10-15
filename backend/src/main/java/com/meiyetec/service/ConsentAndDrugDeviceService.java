package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * P3 · T-P06：医美电子知情同意与药械追溯
 *  - 知情同意书模板（Markdown + 必勾选条款 + 法规依据）
 *  - 电子签署（SHA-256 防篡改 + 医师见证）
 *  - 药械主档 + 批次 + 使用追溯
 *  - 召回 + 不良反应
 */
@RestController
@RequestMapping("/mh")
@RequiredArgsConstructor
public class ConsentAndDrugDeviceService {

    private final MhConsentTemplateMapper consentTplMapper;
    private final MhConsentSignatureMapper consentSigMapper;
    private final MhDrugDeviceMapper drugDeviceMapper;
    private final MhDrugDeviceBatchMapper batchMapper;
    private final MhDrugDeviceUsageMapper usageMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // 知情同意模板
    // ====================================================================

    @PostMapping("/consent/template")
    public Result<MhConsentTemplate> createTemplate(@RequestBody MhConsentTemplate t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (t.getTemplateCode() == null || t.getTemplateName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (t.getContentMd() == null) return Result.error(BizCode.BAD_REQUEST, "内容必填");
        if (t.getEnabled() == null) t.setEnabled(1);
        if (t.getAuditStatus() == null) t.setAuditStatus(1);
        if (t.getVersion() == null) t.setVersion("1.0");
        t.setTenantId(tenantId);
        consentTplMapper.insert(t);
        return Result.success("已创建", t);
    }

    @GetMapping("/consent/template/list")
    public Result<List<Map<String, Object>>> templateList(@RequestParam(required = false) String templateType,
                                                          @RequestParam(required = false) Long projectId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<MhConsentTemplate> q = new LambdaQueryWrapper<MhConsentTemplate>()
                .eq(MhConsentTemplate::getTenantId, tenantId)
                .eq(MhConsentTemplate::getEnabled, 1)
                .orderByAsc(MhConsentTemplate::getTemplateType);
        if (templateType != null && !templateType.isEmpty()) q.eq(MhConsentTemplate::getTemplateType, templateType);
        if (projectId != null) q.eq(MhConsentTemplate::getProjectId, projectId);
        List<MhConsentTemplate> list = consentTplMapper.selectList(q);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MhConsentTemplate t : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", t.getId());
            m.put("templateCode", t.getTemplateCode());
            m.put("templateName", t.getTemplateName());
            m.put("templateType", t.getTemplateType());
            m.put("projectName", t.getProjectName());
            m.put("version", t.getVersion());
            m.put("auditStatus", t.getAuditStatus());
            m.put("enabled", t.getEnabled());
            m.put("legalBasis", t.getLegalBasis());
            rows.add(m);
        }
        return Result.success(rows);
    }

    @GetMapping("/consent/template/{id}")
    public Result<MhConsentTemplate> templateDetail(@PathVariable Long id) {
        MhConsentTemplate t = consentTplMapper.selectById(id);
        if (t == null) return Result.error(BizCode.NOT_FOUND, "模板不存在");
        return Result.success(t);
    }

    // ====================================================================
    // 签署
    // ====================================================================

    @PostMapping("/consent/sign")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> sign(@RequestBody Map<String, Object> body) {
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Long templateId = body.get("templateId") == null ? null : ((Number) body.get("templateId")).longValue();
        String signerName = (String) body.get("signerName");
        if (memberId == null || templateId == null || signerName == null)
            return Result.error(BizCode.BAD_REQUEST, "memberId / templateId / signerName 必填");

        MhConsentTemplate tpl = consentTplMapper.selectById(templateId);
        if (tpl == null) return Result.error(BizCode.NOT_FOUND, "模板不存在");
        if (tpl.getAuditStatus() != 2) return Result.error(BizCode.CONFLICT, "模板未通过审核");
        if (tpl.getEnabled() != 1) return Result.error(BizCode.CONFLICT, "模板未启用");

        // 验证必勾选条款（Demo：前端传 checked_items）
        String checkedItems = (String) body.get("checkedItems");
        if (tpl.getRequiredItems() != null && !tpl.getRequiredItems().isEmpty()) {
            if (checkedItems == null || checkedItems.length() == 0)
                return Result.error(BizCode.BAD_REQUEST, "必勾选条款未勾选");
        }

        // 计算文档 SHA-256
        String docContent = tpl.getContentMd() + ":" + signerName + ":" + System.currentTimeMillis();
        String docHash = sha256(docContent);

        Long storeId = body.get("storeId") == null ? 1L : ((Number) body.get("storeId")).longValue();
        Long brandId = jdbc.queryForObject("SELECT brand_id FROM pos_store WHERE id = ?", Long.class, storeId);

        MhConsentSignature sig = new MhConsentSignature();
        sig.setTenantId(TenantContextHolder.get().getTenantId());
        sig.setBrandId(brandId);
        sig.setStoreId(storeId);
        sig.setMemberId(memberId);
        sig.setTemplateId(templateId);
        sig.setTemplateCode(tpl.getTemplateCode());
        sig.setTemplateVersion(tpl.getVersion());
        sig.setProjectId(body.get("projectId") == null ? null : ((Number) body.get("projectId")).longValue());
        sig.setProjectName((String) body.get("projectName"));
        sig.setOrderId(body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue());
        sig.setOrderNo((String) body.get("orderNo"));
        sig.setSignatureId("SIG" + System.currentTimeMillis() + new Random().nextInt(1000));
        sig.setSignDocUrl((String) body.get("signDocUrl"));
        sig.setSignDocHash(docHash);
        sig.setSignerName(signerName);
        sig.setSignerIdNo((String) body.get("signerIdNo"));
        sig.setSignerRelation((String) body.getOrDefault("signerRelation", "SELF"));
        sig.setWitnessId(body.get("witnessId") == null ? null : ((Number) body.get("witnessId")).longValue());
        sig.setWitnessName((String) body.get("witnessName"));
        sig.setWitnessLicenseNo((String) body.get("witnessLicenseNo"));
        sig.setCheckedItems(checkedItems);
        sig.setRemark((String) body.get("remark"));
        sig.setEffectiveAt(LocalDateTime.now());
        sig.setStatus(1);
        sig.setSignIp((String) body.get("signIp"));
        sig.setSignDevice((String) body.get("signDevice"));
        sig.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        consentSigMapper.insert(sig);

        Map<String, Object> data = new HashMap<>();
        data.put("id", sig.getId());
        data.put("signatureId", sig.getSignatureId());
        data.put("signDocHash", docHash);
        return Result.success("已签署", data);
    }

    // ====================================================================
    // KPI 统计（/consent/stats）
    // ====================================================================

    /**
     * 合规记录页 KPI 聚合，供前端 refreshKpi() 调用。
     * 返回：签署总数 / 有效 / 撤回 / 过期 / 近30天新增、模板待审、7天内即将过期、近7天签署趋势。
     * tenantId 从登录态强制注入。
     */
    @GetMapping("/consent/stats")
    public Result<Map<String, Object>> consentStats() {
        Long tenantId = TenantContextHolder.get().getTenantId();

        // ----- 签署聚合 -----
        Map<String, Object> sigAgg = jdbc.queryForMap(
            "SELECT " +
            "  SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END)                            AS sign_total, " +
            "  SUM(CASE WHEN deleted = 0 AND status = 1 THEN 1 ELSE 0 END)             AS sign_valid, " +
            "  SUM(CASE WHEN deleted = 0 AND status = 2 THEN 1 ELSE 0 END)             AS sign_revoked, " +
            "  SUM(CASE WHEN deleted = 0 AND status = 3 THEN 1 ELSE 0 END)             AS sign_expired, " +
            "  SUM(CASE WHEN deleted = 0 AND create_time >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 1 ELSE 0 END) AS sign_new_30d, " +
            "  SUM(CASE WHEN deleted = 0 AND status = 1 " +
            "            AND DATE_ADD(effective_at, INTERVAL 180 DAY) <= DATE_ADD(NOW(), INTERVAL 7 DAY) " +
            "            AND DATE_ADD(effective_at, INTERVAL 180 DAY) >= NOW() THEN 1 ELSE 0 END) AS expiring_soon " +
            "FROM mh_consent_signature WHERE tenant_id = ?",
            tenantId);
        long signTotal   = ((Number) sigAgg.getOrDefault("sign_total", 0)).longValue();
        long signValid   = ((Number) sigAgg.getOrDefault("sign_valid", 0)).longValue();
        long signRevoked = ((Number) sigAgg.getOrDefault("sign_revoked", 0)).longValue();
        long signExpired = ((Number) sigAgg.getOrDefault("sign_expired", 0)).longValue();
        long signNew30d  = ((Number) sigAgg.getOrDefault("sign_new_30d", 0)).longValue();
        long expiringSoon = ((Number) sigAgg.getOrDefault("expiring_soon", 0)).longValue();

        // ----- 模板待审 -----
        Long tplPending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mh_consent_template " +
            "WHERE deleted = 0 AND tenant_id = ? AND audit_status IN (1, 3)",
            Long.class, tenantId);
        if (tplPending == null) tplPending = 0L;

        // ----- 近 7 天签署趋势（按 yyyy-MM-dd）-----
        List<Map<String, Object>> trendRaw = jdbc.queryForList(
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS d, " +
            "       SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS c " +
            "FROM mh_consent_signature " +
            "WHERE tenant_id = ? AND create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY d ASC",
            tenantId);
        Map<String, Long> trendMap = new HashMap<>();
        for (Map<String, Object> r : trendRaw) {
            trendMap.put((String) r.get("d"), ((Number) r.getOrDefault("c", 0)).longValue());
        }
        List<Map<String, Object>> trend = new ArrayList<>(7);
        java.time.format.DateTimeFormatter ymd = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 6; i >= 0; i--) {
            String d = LocalDate.now().minusDays(i).format(ymd);
            Map<String, Object> point = new HashMap<>();
            point.put("date", d);
            point.put("value", trendMap.getOrDefault(d, 0L));
            trend.add(point);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("signTotal", signTotal);
        data.put("signValid", signValid);
        data.put("signRevoked", signRevoked);
        data.put("signExpired", signExpired);
        data.put("signNew30d", signNew30d);
        data.put("templatesPending", tplPending);
        data.put("expiringSoon", expiringSoon);
        data.put("signTrend7d", trend);
        return Result.success(data);
    }

    @GetMapping("/consent/sign/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> signPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) String signerName,
            @RequestParam(required = false) Integer status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT s.id, s.signature_id, s.member_id, m.name AS member_name, m.phone AS member_phone, " +
            "       s.template_id, s.template_code, s.template_version, s.project_name, s.order_no, " +
            "       s.signer_name, s.signer_relation, s.witness_name, s.witness_license_no, " +
            "       s.sign_doc_hash, s.sign_ip, s.sign_device, s.status, s.effective_at, " +
            "       s.operator_name, s.create_time " +
            "FROM mh_consent_signature s " +
            "LEFT JOIN crm_member m ON m.id = s.member_id " +
            "WHERE s.deleted = 0 AND s.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (memberId != null) { sql.append("AND s.member_id = ? "); args.add(memberId); }
        if (templateId != null) { sql.append("AND s.template_id = ? "); args.add(templateId); }
        if (signerName != null && !signerName.isEmpty()) { sql.append("AND s.signer_name LIKE ? "); args.add("%" + signerName + "%"); }
        if (status != null) { sql.append("AND s.status = ? "); args.add(status); }
        sql.append("ORDER BY s.create_time DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM mh_consent_signature s WHERE s.deleted = 0 AND s.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (memberId != null) { csql.append("AND s.member_id = ? "); cargs.add(memberId); }
        if (templateId != null) { csql.append("AND s.template_id = ? "); cargs.add(templateId); }
        if (signerName != null && !signerName.isEmpty()) { csql.append("AND s.signer_name LIKE ? "); cargs.add("%" + signerName + "%"); }
        if (status != null) { csql.append("AND s.status = ? "); cargs.add(status); }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/consent/sign/{id}/revoke")
    public Result<Void> revokeSign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhConsentSignature s = consentSigMapper.selectById(id);
        if (s == null) return Result.error(BizCode.NOT_FOUND, "签署不存在");
        if (s.getStatus() != 1) return Result.error(BizCode.CONFLICT, "已撤回");
        s.setStatus(2);
        s.setRevokedAt(LocalDateTime.now());
        s.setRevokedReason((String) body.get("reason"));
        consentSigMapper.updateById(s);
        return Result.success("已撤回", null);
    }

    // ====================================================================
    // 药械主档 + 批次
    // ====================================================================

    @PostMapping("/drug-device")
    public Result<MhDrugDevice> createDrugDevice(@RequestBody MhDrugDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getItemCode() == null || d.getItemName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称 必填");
        if (d.getRegistrationNo() == null) return Result.error(BizCode.BAD_REQUEST, "注册证号必填（药监局）");
        if (d.getEnabled() == null) d.setEnabled(1);
        d.setTenantId(tenantId);
        drugDeviceMapper.insert(d);
        return Result.success("已创建", d);
    }

    @GetMapping("/drug-device/list")
    public Result<List<Map<String, Object>>> drugDeviceList(@RequestParam(required = false) String itemType,
                                                             @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, item_code, item_name, item_type, registration_no, specification, manufacturer, " +
            "       category, storage_condition, shelf_life_days, enabled, create_time " +
            "FROM mh_drug_device WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (itemType != null && !itemType.isEmpty()) { sql.append("AND item_type = ? "); args.add(itemType); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (item_name LIKE ? OR registration_no LIKE ? OR item_code LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY item_type, item_name");
        return Result.success(Keys.camel(jdbc.queryForList(sql.toString(), args.toArray())));
    }

    @PostMapping("/drug-device/batch")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> createBatch(@RequestBody MhDrugDeviceBatch b) {
        if (b.getDrugDeviceId() == null || b.getBatchNo() == null)
            return Result.error(BizCode.BAD_REQUEST, "drugDeviceId / batchNo 必填");
        MhDrugDevice device = drugDeviceMapper.selectById(b.getDrugDeviceId());
        if (device == null) return Result.error(BizCode.NOT_FOUND, "药械不存在");

        // 校验：效期/生产日期
        if (b.getExpireDate() == null || b.getProductionDate() == null)
            return Result.error(BizCode.BAD_REQUEST, "生产/效期必填");
        if (b.getExpireDate().isBefore(b.getProductionDate()))
            return Result.error(BizCode.BAD_REQUEST, "效期早于生产日期");

        // 效期 < 货架寿命
        if (device.getShelfLifeDays() != null) {
            long actualDays = java.time.temporal.ChronoUnit.DAYS.between(b.getProductionDate(), b.getExpireDate());
            if (actualDays > device.getShelfLifeDays())
                return Result.error(BizCode.CONFLICT, "效期超过注册货架寿命");
        }
        b.setTenantId(TenantContextHolder.get().getTenantId());
        b.setUsedQty(0);
        b.setRemainingQty(b.getInboundQty() == null ? 0 : b.getInboundQty());
        b.setStatus(1);
        b.setOperatorName(b.getOperatorName() == null ? "admin" : b.getOperatorName());
        batchMapper.insert(b);

        Map<String, Object> data = new HashMap<>();
        data.put("id", b.getId());
        data.put("batchNo", b.getBatchNo());
        data.put("remainingQty", b.getRemainingQty());
        return Result.success("已入库", data);
    }

    @GetMapping("/drug-device/batch/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> batchPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long drugDeviceId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer expiringSoon) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT b.id, b.drug_device_id, d.item_name, d.item_type, d.registration_no, " +
            "       b.batch_no, b.serial_no, b.production_date, b.expire_date, " +
            "       b.supplier_name, b.purchase_order_no, " +
            "       b.inbound_qty, b.used_qty, b.remaining_qty, " +
            "       b.unit_price, b.status, b.freeze_reason, b.recall_at, " +
            "       b.store_id, s.store_name, b.location, b.create_time " +
            "FROM mh_drug_device_batch b " +
            "JOIN mh_drug_device d ON d.id = b.drug_device_id " +
            "LEFT JOIN pos_store s ON s.id = b.store_id " +
            "WHERE b.deleted = 0 AND b.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (storeId != null) { sql.append("AND b.store_id = ? "); args.add(storeId); }
        if (drugDeviceId != null) { sql.append("AND b.drug_device_id = ? "); args.add(drugDeviceId); }
        if (status != null) { sql.append("AND b.status = ? "); args.add(status); }
        if (expiringSoon != null && expiringSoon == 1) {
            sql.append("AND b.expire_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) AND b.status = 1 ");
        }
        sql.append("ORDER BY b.expire_date ASC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM mh_drug_device_batch b WHERE b.deleted = 0 AND b.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (storeId != null) { csql.append("AND b.store_id = ? "); cargs.add(storeId); }
        if (drugDeviceId != null) { csql.append("AND b.drug_device_id = ? "); cargs.add(drugDeviceId); }
        if (status != null) { csql.append("AND b.status = ? "); cargs.add(status); }
        if (expiringSoon != null && expiringSoon == 1) {
            csql.append("AND b.expire_date <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) AND b.status = 1 ");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/drug-device/batch/{id}/recall")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> recallBatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhDrugDeviceBatch b = batchMapper.selectById(id);
        if (b == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (b.getStatus() == 4) return Result.error(BizCode.CONFLICT, "已召回");
        b.setStatus(4);
        b.setRecallAt(LocalDateTime.now());
        b.setRecallReason((String) body.get("reason"));
        batchMapper.updateById(b);
        return Result.success("已召回", null);
    }

    // ====================================================================
    // 药械使用追溯
    // ====================================================================

    @PostMapping("/drug-device/usage")
    @Transactional(rollbackFor = Exception.class)
    public Result<MhDrugDeviceUsage> recordUsage(@RequestBody Map<String, Object> body) {
        Long batchId = body.get("batchId") == null ? null : ((Number) body.get("batchId")).longValue();
        Long orderId = body.get("orderId") == null ? null : ((Number) body.get("orderId")).longValue();
        Long memberId = body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue();
        Integer qty = body.get("qty") == null ? 1 : ((Number) body.get("qty")).intValue();
        if (batchId == null || orderId == null || memberId == null)
            return Result.error(BizCode.BAD_REQUEST, "batchId / orderId / memberId 必填");

        MhDrugDeviceBatch batch = batchMapper.selectById(batchId);
        if (batch == null) return Result.error(BizCode.NOT_FOUND, "批次不存在");
        if (batch.getStatus() != 1) return Result.error(BizCode.CONFLICT, "批次不可用（" + batch.getStatus() + "）");
        if (batch.getRemainingQty() != null && batch.getRemainingQty() < qty)
            return Result.error(BizCode.CONFLICT, "库存不足");

        MhDrugDevice device = drugDeviceMapper.selectById(batch.getDrugDeviceId());
        Map<String, Object> member = jdbc.queryForList(
            "SELECT id, name FROM crm_member WHERE id = ? AND deleted = 0", memberId)
            .stream().findFirst().orElse(null);
        if (member == null) return Result.error(BizCode.NOT_FOUND, "会员不存在");

        MhDrugDeviceUsage u = new MhDrugDeviceUsage();
        u.setTenantId(TenantContextHolder.get().getTenantId());
        u.setBatchId(batchId);
        u.setDrugDeviceId(batch.getDrugDeviceId());
        u.setItemName(device == null ? null : device.getItemName());
        u.setOrderId(orderId);
        u.setOrderNo((String) body.get("orderNo"));
        u.setMemberId(memberId);
        u.setMemberName((String) member.get("name"));
        u.setDoctorId(body.get("doctorId") == null ? null : ((Number) body.get("doctorId")).longValue());
        u.setDoctorName((String) body.get("doctorName"));
        u.setDoctorLicenseNo((String) body.get("doctorLicenseNo"));
        u.setQty(qty);
        u.setBodyPart((String) body.get("bodyPart"));
        u.setSerialNo((String) body.get("serialNo"));
        u.setUsedAt(LocalDateTime.now());
        u.setStatus(1);
        u.setOperatorName((String) body.getOrDefault("operatorName", "doctor"));
        usageMapper.insert(u);

        // 扣减库存
        jdbc.update("UPDATE mh_drug_device_batch SET used_qty = used_qty + ?, remaining_qty = remaining_qty - ? WHERE id = ?",
                qty, qty, batchId);
        return Result.success("已记录", u);
    }

    @PostMapping("/drug-device/usage/{id}/adverse")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> reportAdverse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        MhDrugDeviceUsage u = usageMapper.selectById(id);
        if (u == null) return Result.error(BizCode.NOT_FOUND, "记录不存在");
        u.setHasAdverse(1);
        u.setAdverseReaction((String) body.get("reaction"));
        usageMapper.updateById(u);
        return Result.success("已上报不良反应", null);
    }

    @GetMapping("/drug-device/usage/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> usagePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String keyword) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT u.id, u.batch_id, u.drug_device_id, u.item_name, " +
            "       u.order_id, u.order_no, u.member_id, u.member_name, " +
            "       u.doctor_id, u.doctor_name, u.doctor_license_no, " +
            "       u.qty, u.body_part, u.serial_no, u.used_at, " +
            "       u.has_adverse, u.adverse_reaction, u.status, " +
            "       b.batch_no, b.expire_date, u.operator_name, u.create_time " +
            "FROM mh_drug_device_usage u " +
            "JOIN mh_drug_device_batch b ON b.id = u.batch_id " +
            "WHERE u.deleted = 0 AND u.tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (memberId != null) { sql.append("AND u.member_id = ? "); args.add(memberId); }
        if (batchId != null) { sql.append("AND u.batch_id = ? "); args.add(batchId); }
        if (doctorId != null) { sql.append("AND u.doctor_id = ? "); args.add(doctorId); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (u.item_name LIKE ? OR u.member_name LIKE ? OR u.batch_no LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY u.used_at DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);
        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM mh_drug_device_usage u WHERE u.deleted = 0 AND u.tenant_id = ? ");
        List<Object> cargs = new ArrayList<>();
        cargs.add(tenantId);
        if (memberId != null) { csql.append("AND u.member_id = ? "); cargs.add(memberId); }
        if (batchId != null) { csql.append("AND u.batch_id = ? "); cargs.add(batchId); }
        if (doctorId != null) { csql.append("AND u.doctor_id = ? "); cargs.add(doctorId); }
        if (keyword != null && !keyword.isEmpty()) {
            csql.append("AND (u.item_name LIKE ? OR u.member_name LIKE ? OR u.batch_no LIKE ?) ");
            cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%"); cargs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    private String sha256(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(src.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "fallback-" + System.currentTimeMillis();
        }
    }
}