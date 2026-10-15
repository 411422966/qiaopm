package com.meiyetec.service;

import com.meiyetec.entity.DetectionReport;
import com.meiyetec.entity.DetectionSolution;
import com.meiyetec.entity.HairDetection;
import com.meiyetec.entity.NailDetection;
import com.meiyetec.entity.SkinDetection;
import com.meiyetec.mapper.DetectionReportMapper;
import com.meiyetec.mapper.DetectionSolutionMapper;
import com.meiyetec.mapper.HairDetectionMapper;
import com.meiyetec.mapper.NailDetectionMapper;
import com.meiyetec.mapper.SkinDetectionMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1.5 DETECT — 实体字段完整性 + POJO setter/getter 校验
 * 不加载 Spring，避免项目级 ambiguous mapping 影响
 */
class DetectEntityFieldsTest {

    @Test
    void skin_detection_has_tenantId_memberId() {
        assertNotNull(findField(SkinDetection.class, "tenantId"));
        assertNotNull(findField(SkinDetection.class, "memberId"));
        assertNotNull(findField(SkinDetection.class, "skinType"));
        assertNotNull(findField(SkinDetection.class, "deleted"));
    }

    @Test
    void hair_detection_has_tenantId() {
        assertNotNull(findField(HairDetection.class, "tenantId"));
        assertNotNull(findField(HairDetection.class, "memberId"));
        assertNotNull(findField(HairDetection.class, "hairType"));
    }

    @Test
    void nail_detection_has_tenantId() {
        assertNotNull(findField(NailDetection.class, "tenantId"));
        assertNotNull(findField(NailDetection.class, "memberId"));
        assertNotNull(findField(NailDetection.class, "nailType"));
    }

    @Test
    void detection_report_has_tenantId_and_overall_score() {
        assertNotNull(findField(DetectionReport.class, "tenantId"));
        assertNotNull(findField(DetectionReport.class, "reportType"));
        assertNotNull(findField(DetectionReport.class, "overallScore"));
        assertNotNull(findField(DetectionReport.class, "status"));
    }

    @Test
    void detection_solution_has_tenantId_and_apply_type() {
        assertNotNull(findField(DetectionSolution.class, "tenantId"));
        assertNotNull(findField(DetectionSolution.class, "applyType"));
        assertNotNull(findField(DetectionSolution.class, "scoreThreshold"));
    }

    @Test
    void mapper_interfaces_extend_baseMapper() {
        assertNotNull(SkinDetectionMapper.class.getInterfaces());
        assertNotNull(HairDetectionMapper.class.getInterfaces());
        assertNotNull(NailDetectionMapper.class.getInterfaces());
        assertNotNull(DetectionReportMapper.class.getInterfaces());
        assertNotNull(DetectionSolutionMapper.class.getInterfaces());
    }

    @Test
    void pojo_setter_getter_works() {
        SkinDetection s = new SkinDetection();
        s.setTenantId(7L);
        s.setMemberId(123L);
        s.setSkinType("OILY");
        s.setOverallScore(new java.math.BigDecimal("85.50"));
        s.setStatus("confirmed");
        assertEquals(7L, s.getTenantId());
        assertEquals(123L, s.getMemberId());
        assertEquals("OILY", s.getSkinType());
        assertEquals(0, new java.math.BigDecimal("85.50").compareTo(s.getOverallScore()));
        assertEquals("confirmed", s.getStatus());
    }

    @Test
    void all_detection_entities_distinct_table_names() {
        String[] names = {
                SkinDetection.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value(),
                HairDetection.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value(),
                NailDetection.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value(),
                DetectionReport.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value(),
                DetectionSolution.class.getAnnotation(com.baomidou.mybatisplus.annotation.TableName.class).value()
        };
        assertEquals(5, names.length);
        for (String n : names) {
            assertTrue(n.startsWith("biz_"), "表名应 biz_ 前缀: " + n);
        }
        assertEquals("biz_skin_detection", names[0]);
        assertEquals("biz_hair_detection", names[1]);
        assertEquals("biz_nail_detection", names[2]);
        assertEquals("biz_detection_report", names[3]);
        assertEquals("biz_detection_solution", names[4]);
    }

    private Field findField(Class<?> c, String name) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name)) return f;
        }
        return null;
    }
}