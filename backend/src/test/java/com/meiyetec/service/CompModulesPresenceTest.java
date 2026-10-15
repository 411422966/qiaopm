package com.meiyetec.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 4.26 COMP 合规模块的关键 controller / service / entity 完整、DB 表齐全。
 */
class CompModulesPresenceTest {

    @Test
    void comp_002_h5_controller_exposes_sign_link_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.MhConsentH5Controller");
        // 必须暴露两个端点
        assertHasMethod(c, "getByToken");
        assertHasMethod(c, "submit");

        // 注解
        org.springframework.web.bind.annotation.RequestMapping rm =
                (org.springframework.web.bind.annotation.RequestMapping) c.getAnnotation(
                        org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(rm);
        assertArrayEquals(new String[]{"/mh/consent"}, rm.value());
    }

    @Test
    void comp_required_service_classes_present() {
        assertNotNull(findClassIfExists("com.meiyetec.service.ConsentAndDrugDeviceService"));
        assertNotNull(findClassIfExists("com.meiyetec.service.ComplicationService"));
        assertNotNull(findClassIfExists("com.meiyetec.service.GdprService"));
        assertNotNull(findClassIfExists("com.meiyetec.service.HipaaService"));
    }

    @Test
    void comp_006_dcep_controller_exposes_qualification_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.controller.DcepMerchantQualificationController");
        assertHasMethod(c, "current");
        assertHasMethod(c, "save");
        assertHasMethod(c, "upload");
        assertHasMethod(c, "deleteAttachment");
        assertHasMethod(c, "submit");

        org.springframework.web.bind.annotation.RequestMapping rm =
                (org.springframework.web.bind.annotation.RequestMapping) c.getAnnotation(
                        org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(rm);
        assertArrayEquals(new String[]{"/sys/dcep/qualification"}, rm.value());
    }

    @Test
    void comp_required_entities_and_mappers_present() {
        // Consent
        assertNotNull(findClassIfExists("com.meiyetec.entity.MhConsentTemplate"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.MhConsentSignature"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.MhConsentSignLink"));
        // Complication
        assertNotNull(findClassIfExists("com.meiyetec.entity.ComplicationSop"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.ComplicationEvent"));
        // GDPR
        assertNotNull(findClassIfExists("com.meiyetec.entity.GdprConsent"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.GdprDataRequest"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.GdprAuditLog"));
        // HIPAA
        assertNotNull(findClassIfExists("com.meiyetec.entity.HipaaEncryptionKey"));
        assertNotNull(findClassIfExists("com.meiyetec.entity.HipaaPhiAccess"));
        // Dcep
        assertNotNull(findClassIfExists("com.meiyetec.entity.DcepMerchantQualification"));

        // Mapper
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MhConsentSignLinkMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MhConsentSignatureMapper"));
        assertNotNull(findClassIfExists("com.meiyetec.mapper.MhConsentTemplateMapper"));
    }

    @Test
    void comp_required_tables_exist_in_db() {
        String[] requiredTables = {
            "mh_consent_template", "mh_consent_signature", "mh_consent_sign_link",
            "complication_sop", "complication_event",
            "gdpr_consent", "gdpr_data_request", "gdpr_audit_log",
            "hipaa_encryption_key", "hipaa_phi_access",
            "dcep_merchant_qualification", "dcep_qualification_attachment"
        };
        for (String t : requiredTables) {
            assertEquals(true, tableExists(t), "COMP 表不存在: " + t);
        }
    }

    @Test
    void comp_consent_service_exposes_required_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.ConsentAndDrugDeviceService");
        // 必须含既有 endpoint（防止有人误删）
        assertHasMethod(c, "createTemplate");
        assertHasMethod(c, "templateList");
        assertHasMethod(c, "signPage");
        assertHasMethod(c, "revokeSign");
        // 含 mh path
        org.springframework.web.bind.annotation.RequestMapping rm =
                (org.springframework.web.bind.annotation.RequestMapping) c.getAnnotation(
                        org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(rm);
        assertArrayEquals(new String[]{"/mh"}, rm.value());
    }

    @Test
    void comp_complication_service_exposes_required_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.ComplicationService");
        assertHasMethod(c, "createSop");
        assertHasMethod(c, "listSops");
        assertHasMethod(c, "createEvent");
        assertHasMethod(c, "listEvents");
        assertHasMethod(c, "dashboard");
    }

    @Test
    void comp_gdpr_service_exposes_required_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.GdprService");
        assertHasMethod(c, "grantConsent");
        assertHasMethod(c, "revokeConsent");
        assertHasMethod(c, "listConsents");
        assertHasMethod(c, "createRequest");
        assertHasMethod(c, "processRequest");
    }

    @Test
    void comp_hipaa_service_exposes_required_endpoints() throws Exception {
        Class<?> c = Class.forName("com.meiyetec.service.HipaaService");
        assertHasMethod(c, "generateKey");
        assertHasMethod(c, "listKeys");
        assertHasMethod(c, "rotateKey");
        assertHasMethod(c, "logAccess");
        assertHasMethod(c, "encrypt");
        assertHasMethod(c, "decrypt");
    }

    private boolean tableExists(String tableName) {
        try {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'meiyetec' AND table_name = ?",
                Integer.class, tableName);
            return cnt != null && cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:mysql://localhost:3307/meiyetec?useUnicode=true&characterEncoding=utf-8",
                    "root", "root123456"));

    private boolean hasField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) if (f.getName().equals(name)) return true;
        return false;
    }

    private void assertHasMethod(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return;
        }
        fail("缺少方法: " + name + " on " + c.getSimpleName());
    }

    private Class<?> findClassIfExists(String fqn) {
        try { return Class.forName(fqn); } catch (ClassNotFoundException e) { return null; }
    }
}